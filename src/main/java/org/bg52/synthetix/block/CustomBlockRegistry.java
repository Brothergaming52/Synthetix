package org.bg52.synthetix.block;

import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.item.ItemDefinition;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * High-performance registry managing custom block definitions and disk synchronization.
 * Stored in plugins/Synthetix/blocks/*.yml.
 */
public class CustomBlockRegistry {

    private final SynthetixPlugin plugin;
    private final File blocksFolder;
    private final Map<String, CustomBlockDefinition> blocks;

    public CustomBlockRegistry(SynthetixPlugin plugin) {
        this.plugin = plugin;
        this.blocksFolder = new File(plugin.getDataFolder(), "blocks");
        this.blocks = new ConcurrentHashMap<>();

        if (!blocksFolder.exists()) {
            blocksFolder.mkdirs();
        }
    }

    /**
     * Loads all block YAML files from plugins/Synthetix/blocks/.
     */
    public void loadAll() {
        blocks.clear();
        if (!blocksFolder.exists() || !blocksFolder.isDirectory()) return;

        File[] files = blocksFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().info("No custom blocks found in blocks directory.");
            return;
        }

        int loaded = 0;
        for (File file : files) {
            try {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                CustomBlockDefinition def = CustomBlockDefinition.loadFromConfig(config);
                if (def != null && def.getId() != null) {
                    register(def);
                    loaded++;
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load custom block file " + file.getName(), e);
            }
        }
        plugin.getLogger().info("Successfully loaded " + loaded + " custom block definitions.");
    }

    /**
     * Registers a custom block definition in memory and mirrors an item representation into ItemRegistry.
     */
    public void register(CustomBlockDefinition def) {
        if (def == null || def.getId() == null) return;
        String id = def.getId().toLowerCase(Locale.ROOT);
        blocks.put(id, def);

        // Mirror block item into ItemRegistry for unified give, inventory, drops & crafting compatibility
        if (plugin.getItemRegistry() != null && plugin.getItemRegistry().get(id) == null) {
            ItemDefinition itemDef = new ItemDefinition(id);
            itemDef.setDisplayName(def.getDisplayName());
            itemDef.setMaterial(def.getItemMaterial());
            itemDef.setLore(new ArrayList<>(def.getLore()));
            itemDef.setCustomModelData(def.getCustomModelData() > 0 ? def.getCustomModelData() : null);
            itemDef.getTags().put("synthetix:block_id", id);
            plugin.getItemRegistry().register(itemDef);
        }
    }

    /**
     * Saves a block definition to disk (plugins/Synthetix/blocks/<id>.yml).
     */
    public boolean save(CustomBlockDefinition def) {
        if (def == null || def.getId() == null) return false;
        register(def);

        // Mirror and sync into ItemRegistry
        if (plugin.getItemRegistry() != null) {
            String id = def.getId().toLowerCase(Locale.ROOT);
            ItemDefinition itemDef = plugin.getItemRegistry().get(id);
            if (itemDef == null) {
                itemDef = new ItemDefinition(id);
            }
            itemDef.setDisplayName(def.getDisplayName());
            itemDef.setMaterial(def.getItemMaterial());
            itemDef.setLore(new ArrayList<>(def.getLore()));
            itemDef.setCustomModelData(def.getCustomModelData() > 0 ? def.getCustomModelData() : null);
            itemDef.setItemModel(def.getItemModel());
            itemDef.getTags().put("synthetix:block_id", id);
            plugin.getItemRegistry().register(itemDef);
            File iFile = new File(plugin.getItemRegistry().getItemsFolder(), id + ".yml");
            try {
                YamlConfiguration iConfig = new YamlConfiguration();
                itemDef.saveToConfig(iConfig);
                iConfig.save(iFile);
            } catch (Exception ignored) {}
        }

        File file = new File(blocksFolder, def.getId() + ".yml");
        try {
            YamlConfiguration config = new YamlConfiguration();
            def.saveToConfig(config);
            config.save(file);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save custom block " + def.getId(), e);
            return false;
        }
    }

    /**
     * Deletes a custom block definition from memory and disk.
     */
    public boolean delete(String id) {
        if (id == null) return false;
        String cleanId = id.toLowerCase(Locale.ROOT);
        blocks.remove(cleanId);

        File file = new File(blocksFolder, cleanId + ".yml");
        if (file.exists()) {
            return file.delete();
        }
        return true;
    }

    /**
     * Clones an existing custom block definition under a new ID, copies its source models,
     * assigns new CustomModelData to avoid CMD collisions, slices sub-models, and saves it.
     */
    public CustomBlockDefinition cloneBlock(String sourceId, String newId) {
        if (sourceId == null || newId == null) return null;
        CustomBlockDefinition source = get(sourceId);
        if (source == null) return null;
        String cleanNewId = newId.toLowerCase(Locale.ROOT).trim();
        CustomBlockDefinition copy = source.copy(cleanNewId);

        // Assign fresh CustomModelData to copy to avoid collisions
        copy.setCustomModelData(plugin.getBlockbenchImporter().generateNextCustomModelData());
        copy.setItemModel("synthetix:" + cleanNewId);

        // Copy persistent source model JSON
        File srcModelsDir = plugin.getBlockbenchImporter().getSourceModelsFolder();
        File srcFile = new File(srcModelsDir, source.getId() + "_source.json");
        if (!srcFile.exists()) {
            srcFile = new File(plugin.getDataFolder(), "resources/assets/synthetix/models/item/" + source.getId() + ".json");
            if (!srcFile.exists()) {
                srcFile = new File(plugin.getDataFolder(), "resources/assets/synthetix/models/block/" + source.getId() + ".json");
            }
        }
        if (srcFile.exists()) {
            File targetSource = new File(srcModelsDir, cleanNewId + "_source.json");
            try {
                if (!srcModelsDir.exists()) srcModelsDir.mkdirs();
                java.nio.file.Files.copy(srcFile.toPath(), targetSource.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                plugin.getLogger().warning("Could not copy source model file for cloned block " + cleanNewId + ": " + e.getMessage());
            }
        }

        // Assign fresh CustomModelData and model paths for parts
        for (BlockModelPart part : copy.getModelParts()) {
            part.setCustomModelData(plugin.getBlockbenchImporter().generateNextCustomModelData());
            part.setItemModel("synthetix:" + cleanNewId + "_" + part.getId());
        }

        save(copy);

        // Slice models and compile resource pack definitions for new block
        plugin.getBlockbenchImporter().sliceAndBuildModels(copy);
        plugin.getResourcePackManager().generatePackAsync();
        return copy;
    }

    public CustomBlockDefinition get(String id) {
        if (id == null) return null;
        return blocks.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<CustomBlockDefinition> getAll() {
        return Collections.unmodifiableCollection(blocks.values());
    }

    public boolean has(String id) {
        if (id == null) return false;
        return blocks.containsKey(id.toLowerCase(Locale.ROOT));
    }

    public File getBlocksFolder() {
        return blocksFolder;
    }
}

