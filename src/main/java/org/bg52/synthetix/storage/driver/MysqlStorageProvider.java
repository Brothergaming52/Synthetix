package org.bg52.synthetix.storage.driver;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.storage.PlacedBlockRecord;
import org.bg52.synthetix.storage.StorageProvider;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Enterprise MySQL/MariaDB storage provider powered by HikariCP connection pooling.
 */
public class MysqlStorageProvider implements StorageProvider {

    private final SynthetixPlugin plugin;
    private final ExecutorService executor;
    private HikariDataSource dataSource;

    public MysqlStorageProvider(SynthetixPlugin plugin, ExecutorService executor) {
        this.plugin = plugin;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Void> connect() {
        return CompletableFuture.runAsync(() -> {
            try {
                ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("storage.mysql");
                if (cfg == null) throw new IllegalStateException("Missing storage.mysql configuration section in config.yml!");

                String host = cfg.getString("host", "localhost");
                int port = cfg.getInt("port", 3306);
                String database = cfg.getString("database", "synthetix");
                String username = cfg.getString("username", "root");
                String password = cfg.getString("password", "");

                HikariConfig hikari = new HikariConfig();
                hikari.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8");
                hikari.setUsername(username);
                hikari.setPassword(password);
                hikari.setMaximumPoolSize(cfg.getInt("maximum-pool-size", 10));
                hikari.setMinimumIdle(cfg.getInt("minimum-idle", 2));
                hikari.setConnectionTimeout(cfg.getLong("connection-timeout-ms", 30000));
                hikari.setPoolName("Synthetix-HikariPool");

                this.dataSource = new HikariDataSource(hikari);

                try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS synthetix_addon_data (
                            addon_id VARCHAR(64) NOT NULL,
                            player_uuid VARCHAR(36) NOT NULL,
                            data_key VARCHAR(64) NOT NULL,
                            data_value LONGTEXT,
                            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                            PRIMARY KEY (addon_id, player_uuid, data_key)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);

                    stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS synthetix_placed_blocks (
                            world VARCHAR(64) NOT NULL,
                            x INT NOT NULL,
                            y INT NOT NULL,
                            z INT NOT NULL,
                            block_id VARCHAR(64) NOT NULL,
                            placer_uuid VARCHAR(36),
                            facing VARCHAR(16) NOT NULL,
                            data_json LONGTEXT,
                            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                            PRIMARY KEY (world, x, y, z)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """);
                }
                plugin.getLogger().info("[Storage] Connected to MySQL database: " + database + "@" + host);
            } catch (Exception e) {
                plugin.getLogger().severe("[Storage] Failed to initialize MySQL: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            dataSource = null;
        }
    }

    @Override
    public CompletableFuture<Void> saveRaw(String addonId, UUID playerUUID, String key, String data) {
        return CompletableFuture.runAsync(() -> {
            if (dataSource == null) return;
            String sql = """
                INSERT INTO synthetix_addon_data (addon_id, player_uuid, data_key, data_value)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE data_value = VALUES(data_value), updated_at = CURRENT_TIMESTAMP;
            """;
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, addonId.toLowerCase());
                ps.setString(2, playerUUID.toString());
                ps.setString(3, key.toLowerCase());
                ps.setString(4, data);
                ps.executeUpdate();
            } catch (Exception e) {
                plugin.getLogger().severe("[Storage] MySQL save error: " + e.getMessage());
            }
        }, executor);
    }

    @Override
    public CompletableFuture<String> loadRaw(String addonId, UUID playerUUID, String key) {
        return CompletableFuture.supplyAsync(() -> {
            if (dataSource == null) return null;
            String sql = "SELECT data_value FROM synthetix_addon_data WHERE addon_id = ? AND player_uuid = ? AND data_key = ?";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, addonId.toLowerCase());
                ps.setString(2, playerUUID.toString());
                ps.setString(3, key.toLowerCase());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("data_value");
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().severe("[Storage] MySQL load error: " + e.getMessage());
            }
            return null;
        }, executor);
    }

    @Override
    public CompletableFuture<Void> deleteRaw(String addonId, UUID playerUUID, String key) {
        return CompletableFuture.runAsync(() -> {
            if (dataSource == null) return;
            String sql = "DELETE FROM synthetix_addon_data WHERE addon_id = ? AND player_uuid = ? AND data_key = ?";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, addonId.toLowerCase());
                ps.setString(2, playerUUID.toString());
                ps.setString(3, key.toLowerCase());
                ps.executeUpdate();
            } catch (Exception e) {
                plugin.getLogger().severe("[Storage] MySQL delete error: " + e.getMessage());
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Set<String>> getAllKeys(String addonId, UUID playerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            Set<String> keys = new HashSet<>();
            if (dataSource == null) return keys;
            String sql = "SELECT data_key FROM synthetix_addon_data WHERE addon_id = ? AND player_uuid = ?";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, addonId.toLowerCase());
                ps.setString(2, playerUUID.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        keys.add(rs.getString("data_key"));
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().severe("[Storage] MySQL getAllKeys error: " + e.getMessage());
            }
            return keys;
        }, executor);
    }

    @Override
    public CompletableFuture<Void> saveBlock(PlacedBlockRecord record) {
        return CompletableFuture.runAsync(() -> {
            if (dataSource == null) return;
            String sql = "INSERT INTO synthetix_placed_blocks (world, x, y, z, block_id, placer_uuid, facing, data_json) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                         "ON DUPLICATE KEY UPDATE block_id = VALUES(block_id), placer_uuid = VALUES(placer_uuid), " +
                         "facing = VALUES(facing), data_json = VALUES(data_json), updated_at = CURRENT_TIMESTAMP";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, record.getWorld());
                ps.setInt(2, record.getX());
                ps.setInt(3, record.getY());
                ps.setInt(4, record.getZ());
                ps.setString(5, record.getBlockId());
                ps.setString(6, record.getPlacerUuid() != null ? record.getPlacerUuid().toString() : null);
                ps.setString(7, record.getFacing());
                ps.setString(8, record.toDataJson());
                ps.executeUpdate();
            } catch (Exception e) {
                plugin.getLogger().severe("[Storage] MySQL saveBlock error: " + e.getMessage());
            }
        }, executor);
    }

    @Override
    public CompletableFuture<Void> deleteBlock(String world, int x, int y, int z) {
        return CompletableFuture.runAsync(() -> {
            if (dataSource == null) return;
            String sql = "DELETE FROM synthetix_placed_blocks WHERE world = ? AND x = ? AND y = ? AND z = ?";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, world);
                ps.setInt(2, x);
                ps.setInt(3, y);
                ps.setInt(4, z);
                ps.executeUpdate();
            } catch (Exception e) {
                plugin.getLogger().severe("[Storage] MySQL deleteBlock error: " + e.getMessage());
            }
        }, executor);
    }

    @Override
    public CompletableFuture<List<PlacedBlockRecord>> loadAllBlocks() {
        return CompletableFuture.supplyAsync(() -> {
            List<PlacedBlockRecord> list = new ArrayList<>();
            if (dataSource == null) return list;
            String sql = "SELECT world, x, y, z, block_id, placer_uuid, facing, data_json FROM synthetix_placed_blocks";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
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
            } catch (Exception e) {
                plugin.getLogger().severe("[Storage] MySQL loadAllBlocks error: " + e.getMessage());
            }
            return list;
        }, executor);
    }

    @Override
    public CompletableFuture<PlacedBlockRecord> loadBlock(String world, int x, int y, int z) {
        return CompletableFuture.supplyAsync(() -> {
            if (dataSource == null) return null;
            String sql = "SELECT world, x, y, z, block_id, placer_uuid, facing, data_json FROM synthetix_placed_blocks WHERE world = ? AND x = ? AND y = ? AND z = ?";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
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
            } catch (Exception e) {
                plugin.getLogger().severe("[Storage] MySQL loadBlock error: " + e.getMessage());
            }
            return null;
        }, executor);
    }

    @Override
    public CompletableFuture<List<PlacedBlockRecord>> loadBlocksInChunk(String world, int chunkX, int chunkZ) {
        return CompletableFuture.supplyAsync(() -> {
            List<PlacedBlockRecord> list = new ArrayList<>();
            if (dataSource == null) return list;
            int minX = chunkX << 4;
            int maxX = minX + 15;
            int minZ = chunkZ << 4;
            int maxZ = minZ + 15;
            String sql = "SELECT world, x, y, z, block_id, placer_uuid, facing, data_json FROM synthetix_placed_blocks WHERE world = ? AND (x BETWEEN ? AND ?) AND (z BETWEEN ? AND ?)";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
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
            } catch (Exception e) {
                plugin.getLogger().severe("[Storage] MySQL loadBlocksInChunk error: " + e.getMessage());
            }
            return list;
        }, executor);
    }

    @Override
    public String getName() {
        return "mysql";
    }
}

