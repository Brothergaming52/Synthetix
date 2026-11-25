package org.bg52.synthetix.item;

import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.api.event.SynthetixItemRegisterEvent;
import org.bg52.synthetix.api.event.SynthetixItemUnregisterEvent;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance registry managing custom item definitions and disk synchronization.
 */
public class ItemRegistry {

    private final SynthetixPlugin plugin;
    private final File itemsFolder;
    private final Map<String, ItemDefinition> items;

    public ItemRegistry(SynthetixPlugin plugin) {
        this.plugin = plugin;
        this.itemsFolder = new File(plugin.getDataFolder(), "items");
        this.items = new ConcurrentHashMap<>();

        if (!itemsFolder.exists()) {
            itemsFolder.mkdirs();
        }
    }

    /**
     * Loads all item YAML files from plugins/Synthetix/items/.
     */
    public void loadAll() {
        items.clear();
        if (!itemsFolder.exists() || !itemsFolder.isDirectory()) return;

        File[] files = itemsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().info("No custom items found in items directory.");
            return;
        }

        int loaded = 0;
        for (File file : files) {
            try {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                ItemDefinition def = ItemDefinition.loadFromConfig(config);
                if (def != null && def.getId() != null) {
                    items.put(def.getId().toLowerCase(Locale.ROOT), def);
                    loaded++;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load item file " + file.getName() + ": " + e.getMessage());
            }
        }
        plugin.getLogger().info("Successfully loaded " + loaded + " custom item definitions.");
    }

    private static final NamespacedKey SYNTH_ID_KEY = new NamespacedKey("synthetix", "item_id");

    /**
     * Registers an in-memory item definition and fires the register event.
     */
    public void register(ItemDefinition def) {
        if (def != null && def.getId() != null) {
            items.put(def.getId().toLowerCase(Locale.ROOT), def);
            try {
                Bukkit.getPluginManager().callEvent(new SynthetixItemRegisterEvent(def));
            } catch (Throwable ignored) {
                // Ignore if server is during early init before plugin manager ready
            }
        }
    }

    /**
     * Saves an item definition to disk.
     */
    public boolean save(ItemDefinition def) {
        if (def == null || def.getId() == null) return false;
        register(def);

        // Synchronize changes to CustomBlockDefinition if this item represents a custom block
        if (plugin.getCustomBlockRegistry() != null) {
            org.bg52.synthetix.block.CustomBlockDefinition blockDef = plugin.getCustomBlockRegistry().get(def.getId());
            if (blockDef != null) {
                blockDef.syncFromItemDefinition(def);
                File bFile = new File(plugin.getCustomBlockRegistry().getBlocksFolder(), blockDef.getId() + ".yml");
                try {
                    YamlConfiguration bConfig = new YamlConfiguration();
                    blockDef.saveToConfig(bConfig);
                    bConfig.save(bFile);
                } catch (Exception ignored) {}
            }
        }

        File file = new File(itemsFolder, def.getId() + ".yml");
        try {
            YamlConfiguration config = new YamlConfiguration();
            def.saveToConfig(config);
            config.save(file);
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save item " + def.getId() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Clones an existing item definition under a new ID and saves it.
     */
    public ItemDefinition cloneItem(String sourceId, String newId) {
        if (sourceId == null || newId == null) return null;
        ItemDefinition source = get(sourceId);
        if (source == null) return null;
        String cleanNewId = newId.toLowerCase(Locale.ROOT).trim();
        ItemDefinition copy = source.copy(cleanNewId);
        save(copy);
        return copy;
    }

    /**
     * Deletes an item from disk and memory and fires the unregister event.
     */
    public boolean delete(String id) {
        if (id == null) return false;
        String key = id.toLowerCase(Locale.ROOT);
        items.remove(key);

        try {
            Bukkit.getPluginManager().callEvent(new SynthetixItemUnregisterEvent(key));
        } catch (Throwable ignored) {
        }

        File file = new File(itemsFolder, key + ".yml");
        if (file.exists()) {
            return file.delete();
        }
        return true;
    }

    /**
     * Extracts the Synthetix custom item ID from an ItemStack PDC if present.
     */
    public String getItemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.get(SYNTH_ID_KEY, PersistentDataType.STRING);
    }

    /**
     * Looks up the registered ItemDefinition corresponding to a given ItemStack.
     */
    public ItemDefinition getByItemStack(ItemStack item) {
        String id = getItemId(item);
        return id != null ? get(id) : null;
    }

    public File getItemsFolder() {
        return itemsFolder;
    }

    public ItemDefinition get(String id) {
        if (id == null) return null;
        return items.get(id.toLowerCase(Locale.ROOT));
    }

    public boolean has(String id) {
        if (id == null) return false;
        return items.containsKey(id.toLowerCase(Locale.ROOT));
    }

    public Collection<ItemDefinition> getAll() {
        return Collections.unmodifiableCollection(items.values());
    }

    /**
     * Creates an ItemStack from a registered item ID.
     */
    public ItemStack createItemStack(String id) {
        if (id == null) return null;
        ItemDefinition def = get(id);
        if (def != null) {
            return def.buildItemStack();
        }
        return null;
    }
}

