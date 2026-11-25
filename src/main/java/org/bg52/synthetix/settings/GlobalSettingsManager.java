package org.bg52.synthetix.settings;

import org.bg52.synthetix.SynthetixPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Universal, persistent settings manager for global feature toggles across Synthetix.
 * Stores settings in plugins/Synthetix/settings.yml with immediate reactive dispatch to subsystems.
 */
public class GlobalSettingsManager {

    private final SynthetixPlugin plugin;
    private final Map<String, Boolean> features = new ConcurrentHashMap<>();
    private final File settingsFile;

    public GlobalSettingsManager(SynthetixPlugin plugin) {
        this.plugin = plugin;
        this.settingsFile = new File(plugin.getDataFolder(), "settings.yml");
    }

    public void initialize() {
        load();
    }

    public void reload() {
        load();
    }

    public synchronized void load() {
        features.clear();
        // 1. Populate defaults
        for (GlobalFeature f : GlobalFeature.values()) {
            features.put(f.getId(), f.isDefaultState());
        }

        // 2. Read from disk if present
        if (settingsFile.exists()) {
            try {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(settingsFile);
                ConfigurationSection sec = config.getConfigurationSection("features");
                if (sec != null) {
                    for (String key : sec.getKeys(false)) {
                        features.put(key.toLowerCase(Locale.ROOT), sec.getBoolean(key));
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load settings.yml", e);
            }
        } else {
            save();
        }
    }

    public synchronized void save() {
        try {
            if (!settingsFile.getParentFile().exists()) {
                settingsFile.getParentFile().mkdirs();
            }
            YamlConfiguration config = new YamlConfiguration();
            config.options().header("""
==============================================================================
                 Synthetix Global Feature Settings & Toggles
==============================================================================
Managed in-game via /synth settings or edited manually.
All settings persist across server restarts and plugin updates.
""");

            ConfigurationSection sec = config.createSection("features");
            for (Map.Entry<String, Boolean> entry : features.entrySet()) {
                sec.set(entry.getKey(), entry.getValue());
            }
            config.save(settingsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save settings.yml", e);
        }
    }

    public boolean isFeatureEnabled(String featureId) {
        if (featureId == null) return true;
        return features.getOrDefault(featureId.toLowerCase(Locale.ROOT), true);
    }

    public boolean isFeatureEnabled(GlobalFeature feature) {
        if (feature == null) return true;
        return isFeatureEnabled(feature.getId());
    }

    public synchronized void setFeatureEnabled(String featureId, boolean enabled) {
        if (featureId == null) return;
        String key = featureId.toLowerCase(Locale.ROOT);
        features.put(key, enabled);
        save();
        onFeatureChanged(key, enabled);
    }

    public synchronized void setFeatureEnabled(GlobalFeature feature, boolean enabled) {
        if (feature == null) return;
        setFeatureEnabled(feature.getId(), enabled);
    }

    public synchronized boolean toggleFeature(String featureId) {
        boolean newState = !isFeatureEnabled(featureId);
        setFeatureEnabled(featureId, newState);
        return newState;
    }

    public synchronized boolean toggleFeature(GlobalFeature feature) {
        if (feature == null) return true;
        return toggleFeature(feature.getId());
    }

    public Map<String, Boolean> getAllFeatures() {
        return Collections.unmodifiableMap(features);
    }

    public synchronized void setAllFeatures(boolean enabled) {
        for (GlobalFeature f : GlobalFeature.values()) {
            features.put(f.getId(), enabled);
        }
        save();
        if (plugin.getRecipeManager() != null) {
            plugin.getRecipeManager().onGlobalSettingsChanged();
        }
    }

    public synchronized void resetToDefaults() {
        for (GlobalFeature f : GlobalFeature.values()) {
            features.put(f.getId(), f.isDefaultState());
        }
        save();
        if (plugin.getRecipeManager() != null) {
            plugin.getRecipeManager().onGlobalSettingsChanged();
        }
    }

    private void onFeatureChanged(String key, boolean enabled) {
        if (key.startsWith("recipes-")) {
            if (plugin.getRecipeManager() != null) {
                plugin.getRecipeManager().onGlobalSettingsChanged();
            }
        }
    }
}
