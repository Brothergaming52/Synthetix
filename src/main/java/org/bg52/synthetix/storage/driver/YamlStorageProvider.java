package org.bg52.synthetix.storage.driver;

import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.storage.PlacedBlockRecord;
import org.bg52.synthetix.storage.StorageProvider;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * FlatFile YAML storage provider.
 * Stores data partitioned by addon: plugins/Synthetix/storage/<addonId>/<playerUUID>.yml
 */
public class YamlStorageProvider implements StorageProvider {

    private final SynthetixPlugin plugin;
    private final ExecutorService executor;
    private final File baseStorageDir;

    public YamlStorageProvider(SynthetixPlugin plugin, ExecutorService executor) {
        this.plugin = plugin;
        this.executor = executor;
        this.baseStorageDir = new File(plugin.getDataFolder(), "storage");
        if (!baseStorageDir.exists()) {
            baseStorageDir.mkdirs();
        }
    }

    @Override
    public CompletableFuture<Void> connect() {
        return CompletableFuture.runAsync(() -> {
            plugin.getLogger().info("[Storage] Initialized YAML flatfile storage at: " + baseStorageDir.getPath());
        }, executor);
    }

    @Override
    public void disconnect() {
        // Flatfile flush
    }

    private File getPlayerFile(String addonId, UUID playerUUID) {
        File addonFolder = new File(baseStorageDir, addonId.toLowerCase());
        if (!addonFolder.exists()) {
            addonFolder.mkdirs();
        }
        return new File(addonFolder, playerUUID.toString() + ".yml");
    }

    @Override
    public CompletableFuture<Void> saveRaw(String addonId, UUID playerUUID, String key, String data) {
        return CompletableFuture.runAsync(() -> {
            File file = getPlayerFile(addonId, playerUUID);
            try {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                config.set("data." + key.toLowerCase(), data);
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().severe("[Storage] YAML save error: " + e.getMessage());
            }
        }, executor);
    }

    @Override
    public CompletableFuture<String> loadRaw(String addonId, UUID playerUUID, String key) {
        return CompletableFuture.supplyAsync(() -> {
            File file = getPlayerFile(addonId, playerUUID);
            if (!file.exists()) return null;
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            return config.getString("data." + key.toLowerCase());
        }, executor);
    }

    @Override
    public CompletableFuture<Void> deleteRaw(String addonId, UUID playerUUID, String key) {
        return CompletableFuture.runAsync(() -> {
            File file = getPlayerFile(addonId, playerUUID);
            if (!file.exists()) return;
            try {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                config.set("data." + key.toLowerCase(), null);
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().severe("[Storage] YAML delete error: " + e.getMessage());
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Set<String>> getAllKeys(String addonId, UUID playerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            Set<String> keys = new HashSet<>();
            File file = getPlayerFile(addonId, playerUUID);
            if (!file.exists()) return keys;
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            org.bukkit.configuration.ConfigurationSection sec = config.getConfigurationSection("data");
            if (sec != null) {
                keys.addAll(sec.getKeys(false));
            }
            return keys;
        }, executor);
    }

    private synchronized File getPlacedBlocksFile() {
        return new File(baseStorageDir, "synthetix_placed_blocks.yml");
    }

    @Override
    public CompletableFuture<Void> saveBlock(PlacedBlockRecord record) {
        return CompletableFuture.runAsync(() -> {
            if (record == null) return;
            synchronized (this) {
                File file = getPlacedBlocksFile();
                try {
                    YamlConfiguration config = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
                    String key = "blocks." + record.getWorld() + "_" + record.getX() + "_" + record.getY() + "_" + record.getZ();
                    config.set(key + ".world", record.getWorld());
                    config.set(key + ".x", record.getX());
                    config.set(key + ".y", record.getY());
                    config.set(key + ".z", record.getZ());
                    config.set(key + ".block-id", record.getBlockId());
                    config.set(key + ".placer", record.getPlacerUuid() != null ? record.getPlacerUuid().toString() : null);
                    config.set(key + ".facing", record.getFacing());
                    config.set(key + ".data-json", record.toDataJson());
                    config.save(file);
                } catch (IOException e) {
                    plugin.getLogger().severe("[Storage] YAML saveBlock error: " + e.getMessage());
                }
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> deleteBlock(String world, int x, int y, int z) {
        return CompletableFuture.runAsync(() -> {
            synchronized (this) {
                File file = getPlacedBlocksFile();
                if (!file.exists()) return;
                try {
                    YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                    String key = "blocks." + world + "_" + x + "_" + y + "_" + z;
                    config.set(key, null);
                    config.save(file);
                } catch (IOException e) {
                    plugin.getLogger().severe("[Storage] YAML deleteBlock error: " + e.getMessage());
                }
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<PlacedBlockRecord>> loadAllBlocks() {
        return CompletableFuture.supplyAsync(() -> {
            List<PlacedBlockRecord> list = new ArrayList<>();
            synchronized (this) {
                File file = getPlacedBlocksFile();
                if (!file.exists()) return list;
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                var sec = config.getConfigurationSection("blocks");
                if (sec != null) {
                    for (String k : sec.getKeys(false)) {
                        var bSec = sec.getConfigurationSection(k);
                        if (bSec == null) continue;
                        String world = bSec.getString("world");
                        int x = bSec.getInt("x");
                        int y = bSec.getInt("y");
                        int z = bSec.getInt("z");
                        String blockId = bSec.getString("block-id");
                        String placerStr = bSec.getString("placer");
                        UUID placer = placerStr != null ? UUID.fromString(placerStr) : null;
                        String facing = bSec.getString("facing", "NORTH");
                        String dataJson = bSec.getString("data-json");

                        PlacedBlockRecord r = new PlacedBlockRecord(world, x, y, z, blockId, placer, facing);
                        r.fromDataJson(dataJson);
                        list.add(r);
                    }
                }
            }
            return list;
        }, executor);
    }

    @Override
    public CompletableFuture<PlacedBlockRecord> loadBlock(String world, int x, int y, int z) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (this) {
                File file = getPlacedBlocksFile();
                if (!file.exists()) return null;
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                String key = "blocks." + world + "_" + x + "_" + y + "_" + z;
                var bSec = config.getConfigurationSection(key);
                if (bSec == null) return null;
                String blockId = bSec.getString("block-id");
                String placerStr = bSec.getString("placer");
                UUID placer = placerStr != null ? UUID.fromString(placerStr) : null;
                String facing = bSec.getString("facing", "NORTH");
                String dataJson = bSec.getString("data-json");

                PlacedBlockRecord r = new PlacedBlockRecord(world, x, y, z, blockId, placer, facing);
                r.fromDataJson(dataJson);
                return r;
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<PlacedBlockRecord>> loadBlocksInChunk(String world, int chunkX, int chunkZ) {
        return CompletableFuture.supplyAsync(() -> {
            List<PlacedBlockRecord> list = new ArrayList<>();
            synchronized (this) {
                File file = getPlacedBlocksFile();
                if (!file.exists()) return list;
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                var sec = config.getConfigurationSection("blocks");
                if (sec != null) {
                    for (String k : sec.getKeys(false)) {
                        var bSec = sec.getConfigurationSection(k);
                        if (bSec == null) continue;
                        String recWorld = bSec.getString("world");
                        if (!world.equalsIgnoreCase(recWorld)) continue;
                        int x = bSec.getInt("x");
                        int z = bSec.getInt("z");
                        if ((x >> 4) != chunkX || (z >> 4) != chunkZ) continue;
                        int y = bSec.getInt("y");
                        String blockId = bSec.getString("block-id");
                        String placerStr = bSec.getString("placer");
                        UUID placer = placerStr != null ? UUID.fromString(placerStr) : null;
                        String facing = bSec.getString("facing", "NORTH");
                        String dataJson = bSec.getString("data-json");

                        PlacedBlockRecord r = new PlacedBlockRecord(world, x, y, z, blockId, placer, facing);
                        r.fromDataJson(dataJson);
                        list.add(r);
                    }
                }
            }
            return list;
        }, executor);
    }

    @Override
    public String getName() {
        return "yaml";
    }
}

