package org.bg52.synthetix.entity;

import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.entity.model.CustomEntityDefinition;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Registry managing custom entity definitions and disk persistence in plugins/Synthetix/entities/*.yml.
 */
public class CustomEntityRegistry {

    private final SynthetixPlugin plugin;
    private final File entitiesFolder;
    private final Map<String, CustomEntityDefinition> definitions = new ConcurrentHashMap<>();

    public CustomEntityRegistry(SynthetixPlugin plugin) {
        this.plugin = plugin;
        this.entitiesFolder = new File(plugin.getDataFolder(), "entities");
        if (!entitiesFolder.exists()) {
            entitiesFolder.mkdirs();
        }
    }

    public void loadAll() {
        definitions.clear();
        if (!entitiesFolder.exists() || !entitiesFolder.isDirectory()) return;

        File[] files = entitiesFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().info("No custom entities found in entities directory.");
            return;
        }

        int count = 0;
        for (File file : files) {
            try {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                CustomEntityDefinition def = CustomEntityDefinition.loadFromConfig(config);
                if (def != null && def.getId() != null) {
                    register(def);
                    count++;
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load custom entity file: " + file.getName(), e);
            }
        }
        plugin.getLogger().info("Successfully loaded " + count + " custom entity definitions.");
    }

    public void register(CustomEntityDefinition def) {
        if (def == null || def.getId() == null) return;
        definitions.put(def.getId().toLowerCase(Locale.ROOT), def);
    }

    public CustomEntityDefinition get(String id) {
        if (id == null) return null;
        return definitions.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<CustomEntityDefinition> getAll() {
        return Collections.unmodifiableCollection(definitions.values());
    }

    public boolean delete(String id) {
        if (id == null) return false;
        String cleanId = id.toLowerCase(Locale.ROOT);
        definitions.remove(cleanId);

        File file = new File(entitiesFolder, cleanId + ".yml");
        if (file.exists()) {
            return file.delete();
        }
        return true;
    }

    public boolean save(CustomEntityDefinition def) {
        if (def == null || def.getId() == null) return false;
        register(def);

        File file = new File(entitiesFolder, def.getId().toLowerCase(Locale.ROOT) + ".yml");
        try {
            YamlConfiguration config = new YamlConfiguration();
            def.saveToConfig(config);
            config.save(file);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save custom entity definition: " + def.getId(), e);
            return false;
        }
    }

    public void saveAll() {
        for (CustomEntityDefinition def : definitions.values()) {
            save(def);
        }
    }

    public CustomEntityDefinition cloneEntity(String sourceId, String newId) {
        CustomEntityDefinition source = get(sourceId);
        if (source == null) return null;

        File tempFile = new File(entitiesFolder, "__temp_clone.yml");
        try {
            YamlConfiguration conf = new YamlConfiguration();
            source.saveToConfig(conf);
            conf.set("id", newId.toLowerCase(Locale.ROOT));
            conf.set("display-name", "<gold>" + newId + "</gold>");
            CustomEntityDefinition cloned = CustomEntityDefinition.loadFromConfig(conf);
            if (cloned != null) {
                save(cloned);
                return cloned;
            }
        } finally {
            if (tempFile.exists()) tempFile.delete();
        }
        return null;
    }
}

