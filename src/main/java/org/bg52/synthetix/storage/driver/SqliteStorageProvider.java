package org.bg52.synthetix.storage.driver;

import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.storage.PlacedBlockRecord;
import org.bg52.synthetix.storage.StorageProvider;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * High-performance SQLite storage provider with connection caching and async futures.
 */
public class SqliteStorageProvider implements StorageProvider {

    private final SynthetixPlugin plugin;
    private final ExecutorService executor;
    private final File dbFile;
    private Connection connection;

    public SqliteStorageProvider(SynthetixPlugin plugin, ExecutorService executor) {
        this.plugin = plugin;
        this.executor = executor;
        File dataDir = new File(plugin.getDataFolder(), "storage");
        if (!dataDir.exists()) dataDir.mkdirs();
        this.dbFile = new File(dataDir, plugin.getConfig().getString("storage.sqlite.file", "synthetix_data.db"));
    }

    @Override
    public CompletableFuture<Void> connect() {
        return CompletableFuture.runAsync(() -> {
            try {
                Class.forName("org.sqlite.JDBC");
                this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

                try (Statement stmt = connection.createStatement()) {
                    stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS synthetix_addon_data (
                            addon_id VARCHAR(64) NOT NULL,
                            player_uuid VARCHAR(36) NOT NULL,
                            data_key VARCHAR(64) NOT NULL,
                            data_value TEXT,
                            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            PRIMARY KEY (addon_id, player_uuid, data_key)
                        );
                    """);
                    stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_synth_addon ON synthetix_addon_data(addon_id, player_uuid);");

                    stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS synthetix_placed_blocks (
                            world VARCHAR(64) NOT NULL,
                            x INT NOT NULL,
                            y INT NOT NULL,
                            z INT NOT NULL,
                            block_id VARCHAR(64) NOT NULL,
                            placer_uuid VARCHAR(36),
                            facing VARCHAR(16) NOT NULL,
                            data_json TEXT,
                            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            PRIMARY KEY (world, x, y, z)
                        );
                    """);
                    stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_synth_blocks_world ON synthetix_placed_blocks(world);");
                    stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_synth_blocks_chunk ON synthetix_placed_blocks(world, x, z);");
                }
                plugin.getLogger().info("[Storage] Connected to SQLite database: " + dbFile.getName());
            } catch (Exception e) {
                plugin.getLogger().severe("[Storage] Failed to initialize SQLite: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public synchronized void disconnect() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException ignored) {}
            connection = null;
        }
    }

    private synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        }
        return connection;
    }

    @Override
    public CompletableFuture<Void> saveRaw(String addonId, UUID playerUUID, String key, String data) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT OR REPLACE INTO synthetix_addon_data (addon_id, player_uuid, data_key, data_value, updated_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, addonId.toLowerCase());
                ps.setString(2, playerUUID.toString());
                ps.setString(3, key.toLowerCase());
                ps.setString(4, data);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("[Storage] SQLite save error (" + addonId + "/" + playerUUID + "/" + key + "): " + e.getMessage());
            }
        }, executor);
    }

    @Override
    public CompletableFuture<String> loadRaw(String addonId, UUID playerUUID, String key) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT data_value FROM synthetix_addon_data WHERE addon_id = ? AND player_uuid = ? AND data_key = ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, addonId.toLowerCase());
                ps.setString(2, playerUUID.toString());
                ps.setString(3, key.toLowerCase());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("data_value");
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[Storage] SQLite load error (" + addonId + "/" + playerUUID + "/" + key + "): " + e.getMessage());
            }
            return null;
        }, executor);
    }

    @Override
    public CompletableFuture<Void> deleteRaw(String addonId, UUID playerUUID, String key) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM synthetix_addon_data WHERE addon_id = ? AND player_uuid = ? AND data_key = ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, addonId.toLowerCase());
                ps.setString(2, playerUUID.toString());
                ps.setString(3, key.toLowerCase());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("[Storage] SQLite delete error: " + e.getMessage());
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Set<String>> getAllKeys(String addonId, UUID playerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            Set<String> keys = new HashSet<>();
            String sql = "SELECT data_key FROM synthetix_addon_data WHERE addon_id = ? AND player_uuid = ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, addonId.toLowerCase());
                ps.setString(2, playerUUID.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        keys.add(rs.getString("data_key"));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[Storage] SQLite getAllKeys error: " + e.getMessage());
            }
            return keys;
        }, executor);
    }

    @Override
    public CompletableFuture<Void> saveBlock(PlacedBlockRecord record) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT OR REPLACE INTO synthetix_placed_blocks (world, x, y, z, block_id, placer_uuid, facing, data_json, updated_at) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, record.getWorld());
                ps.setInt(2, record.getX());
                ps.setInt(3, record.getY());
                ps.setInt(4, record.getZ());
                ps.setString(5, record.getBlockId());
                ps.setString(6, record.getPlacerUuid() != null ? record.getPlacerUuid().toString() : null);
                ps.setString(7, record.getFacing());
                ps.setString(8, record.toDataJson());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("[Storage] SQLite saveBlock error: " + e.getMessage());
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> deleteBlock(String world, int x, int y, int z) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM synthetix_placed_blocks WHERE world = ? AND x = ? AND y = ? AND z = ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, world);
                ps.setInt(2, x);
                ps.setInt(3, y);
                ps.setInt(4, z);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("[Storage] SQLite deleteBlock error: " + e.getMessage());
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<PlacedBlockRecord>> loadAllBlocks() {
        return CompletableFuture.supplyAsync(() -> {
            List<PlacedBlockRecord> list = new ArrayList<>();
            String sql = "SELECT world, x, y, z, block_id, placer_uuid, facing, data_json FROM synthetix_placed_blocks";
            try (PreparedStatement ps = getConnection().prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String world = rs.getString("world");
                    int x = rs.getInt("x");
                    int y = rs.getInt("y");
                    int z = rs.getInt("z");
                    String blockId = rs.getString("block_id");
                    String placerStr = rs.getString("placer_uuid");
                    UUID placer = placerStr != null ? UUID.fromString(placerStr) : null;
                    String facing = rs.getString("facing");
                    String dataJson = rs.getString("data_json");

                    PlacedBlockRecord r = new PlacedBlockRecord(world, x, y, z, blockId, placer, facing);
                    r.fromDataJson(dataJson);
                    list.add(r);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[Storage] SQLite loadAllBlocks error: " + e.getMessage());
            }
            return list;
        }, executor);
    }

    @Override
    public CompletableFuture<PlacedBlockRecord> loadBlock(String world, int x, int y, int z) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT world, x, y, z, block_id, placer_uuid, facing, data_json FROM synthetix_placed_blocks WHERE world = ? AND x = ? AND y = ? AND z = ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, world);
                ps.setInt(2, x);
                ps.setInt(3, y);
                ps.setInt(4, z);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String blockId = rs.getString("block_id");
                        String placerStr = rs.getString("placer_uuid");
                        UUID placer = placerStr != null ? UUID.fromString(placerStr) : null;
                        String facing = rs.getString("facing");
                        String dataJson = rs.getString("data_json");

                        PlacedBlockRecord r = new PlacedBlockRecord(world, x, y, z, blockId, placer, facing);
                        r.fromDataJson(dataJson);
                        return r;
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[Storage] SQLite loadBlock error: " + e.getMessage());
            }
            return null;
        }, executor);
    }

    @Override
    public CompletableFuture<List<PlacedBlockRecord>> loadBlocksInChunk(String world, int chunkX, int chunkZ) {
        return CompletableFuture.supplyAsync(() -> {
            List<PlacedBlockRecord> list = new ArrayList<>();
            int minX = chunkX << 4;
            int maxX = minX + 15;
            int minZ = chunkZ << 4;
            int maxZ = minZ + 15;
            String sql = "SELECT world, x, y, z, block_id, placer_uuid, facing, data_json FROM synthetix_placed_blocks WHERE world = ? AND (x BETWEEN ? AND ?) AND (z BETWEEN ? AND ?)";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, world);
                ps.setInt(2, minX);
                ps.setInt(3, maxX);
                ps.setInt(4, minZ);
                ps.setInt(5, maxZ);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String blockId = rs.getString("block_id");
                        String placerStr = rs.getString("placer_uuid");
                        UUID placer = placerStr != null ? UUID.fromString(placerStr) : null;
                        String facing = rs.getString("facing");
                        String dataJson = rs.getString("data_json");

                        PlacedBlockRecord r = new PlacedBlockRecord(world, rs.getInt("x"), rs.getInt("y"), rs.getInt("z"), blockId, placer, facing);
                        r.fromDataJson(dataJson);
                        list.add(r);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[Storage] SQLite loadBlocksInChunk error: " + e.getMessage());
            }
            return list;
        }, executor);
    }

    @Override
    public String getName() {
        return "sqlite";
    }
}

