package org.bg52.synthetix.storage;

import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.storage.driver.MongoStorageProvider;
import org.bg52.synthetix.storage.driver.MysqlStorageProvider;
import org.bg52.synthetix.storage.driver.SqliteStorageProvider;
import org.bg52.synthetix.storage.driver.YamlStorageProvider;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Enterprise storage manager providing isolated, harmonious persistence stores to all addon plugins.
 */
public class StorageManager {

    private final SynthetixPlugin plugin;
    private final ExecutorService executor;
    private final StorageProvider provider;
    private final Map<String, AddonDataStore> stores;
    private final String storageType;
    private final org.bg52.synthetix.storage.migration.LegacyMigrationManager migrationManager;

    public StorageManager(SynthetixPlugin plugin) {
        this.plugin = plugin;
        this.stores = new ConcurrentHashMap<>();
        this.storageType = plugin.getConfig().getString("storage.type", "sqlite").toLowerCase(Locale.ROOT).trim();
        this.executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "Synthetix-Storage-Worker");
            t.setDaemon(true);
            return t;
        });

        this.provider = createProvider();
        this.migrationManager = new org.bg52.synthetix.storage.migration.LegacyMigrationManager(plugin);
    }

    private StorageProvider createProvider() {
        return switch (storageType) {
            case "mysql" -> new MysqlStorageProvider(plugin, executor);
            case "mongodb", "mongo" -> new MongoStorageProvider(plugin, executor);
            case "yaml", "flatfile" -> new YamlStorageProvider(plugin, executor);
            default -> new SqliteStorageProvider(plugin, executor);
        };
    }

    /**
     * Initializes the storage provider asynchronously.
     */
    public void initialize() {
        provider.connect().thenRun(() -> {
            plugin.getLogger().info("[Storage] Storage engine initialized using provider: " + provider.getName().toUpperCase());
            // Automatically run legacy migration asynchronously after connecting
            migrationManager.runMigration();
        }).exceptionally(ex -> {
            plugin.getLogger().severe("[Storage] Failed to connect storage engine: " + ex.getMessage());
            return null;
        });
    }

    /**
     * Obtains an isolated data store for a specific addon identifier.
     * Multiple addons can call this to have their own tables and separate systems coexist in complete harmony.
     *
     * @param addonId Unique ID of the addon (e.g. "curiospaper", "backpacks")
     * @return AddonDataStore for storing strings, item lists, and item maps
     */
    public AddonDataStore getDataStore(String addonId) {
        if (addonId == null || addonId.trim().isEmpty()) {
            throw new IllegalArgumentException("addonId cannot be null or empty!");
        }
        String cleanId = addonId.toLowerCase(Locale.ROOT).trim();
        return stores.computeIfAbsent(cleanId, id -> new AddonDataStoreImpl(id, provider));
    }

    /**
     * Obtains an isolated data store for a Bukkit Plugin instance.
     */
    public AddonDataStore getDataStore(Plugin addonPlugin) {
        if (addonPlugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null!");
        }
        return getDataStore(addonPlugin.getName());
    }

    /**
     * Shuts down connection pools and waits for pending writes to finish.
     */
    public void shutdown() {
        try {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }

        if (provider != null) {
            provider.disconnect();
        }
        plugin.getLogger().info("[Storage] Storage engine shut down cleanly.");
    }

    public org.bg52.synthetix.storage.migration.LegacyMigrationManager getMigrationManager() {
        return migrationManager;
    }

    public StorageProvider getProvider() {
        return provider;
    }

    public String getStorageType() {
        return storageType;
    }

    public boolean isDatabaseMode() {
        return !storageType.equals("yaml") && !storageType.equals("flatfile");
    }
}

