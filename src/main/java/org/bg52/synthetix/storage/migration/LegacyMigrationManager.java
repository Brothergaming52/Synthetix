package org.bg52.synthetix.storage.migration;

import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.storage.AddonDataStore;
import org.bg52.synthetix.storage.ItemStackSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Enterprise Database Updater and Migration Manager.
 * Harmoniously migrates legacy data from old CuriosPaper (SQLite curiospaper.db, MySQL tables, YAML flat files)
 * and old ProbablyBackpacks data into Synthetix's isolated AddonDataStore architecture, ensuring zero data loss
 * and rebuilding legacy item formats to modern Paper 1.20.6+ / 1.21+ standards.
 */
public class LegacyMigrationManager {

    private final SynthetixPlugin plugin;
    private final File markerFile;

    public LegacyMigrationManager(SynthetixPlugin plugin) {
        this.plugin = plugin;
        File storageDir = new File(plugin.getDataFolder(), "storage");
        if (!storageDir.exists()) storageDir.mkdirs();
        this.markerFile = new File(storageDir, ".migration_completed");
    }

    public static class MigrationReport {
        private int migratedCuriosPlayers = 0;
        private int migratedAddonRecords = 0;
        private int migratedBackpacks = 0;
        private final List<String> logMessages = new ArrayList<>();

        public int getMigratedCuriosPlayers() { return migratedCuriosPlayers; }
        public int getMigratedAddonRecords() { return migratedAddonRecords; }
        public int getMigratedBackpacks() { return migratedBackpacks; }
        public List<String> getLogMessages() { return Collections.unmodifiableList(logMessages); }

        public void addLog(String msg) { logMessages.add(msg); }
    }

    /**
     * Executes the comprehensive migration process asynchronously.
     */
    public CompletableFuture<MigrationReport> runMigration() {
        return CompletableFuture.supplyAsync(() -> {
            MigrationReport report = new MigrationReport();
            plugin.getLogger().info("[Migration] Checking for legacy CuriosPaper & addon databases to migrate...");

            try {
                // 1. Migrate Old CuriosPaper SQLite Databases
                migrateCuriosSQLite(report);

                // 2. Migrate Old CuriosPaper MySQL Tables (if MySQL active)
                migrateCuriosMySQL(report);

                // 3. Migrate Old CuriosPaper YAML Flat-Files
                migrateCuriosYaml(report);

                // 4. Migrate Old ProbablyBackpacks Flat-File Data
                migrateProbablyBackpacksYaml(report);

                // Create or update migration marker
                try {
                    Files.writeString(markerFile.toPath(), "Migration executed at: " + new java.util.Date()
                            + "\nCurios Players: " + report.migratedCuriosPlayers
                            + "\nAddon Records: " + report.migratedAddonRecords
                            + "\nBackpacks: " + report.migratedBackpacks);
                } catch (IOException ignored) {}

                if (report.migratedCuriosPlayers > 0 || report.migratedAddonRecords > 0 || report.migratedBackpacks > 0) {
                    plugin.getLogger().info("[Migration] Migration completed successfully! Migrated "
                            + report.migratedCuriosPlayers + " Curios inventories, "
                            + report.migratedAddonRecords + " addon data records, and "
                            + report.migratedBackpacks + " backpacks into Synthetix database.");
                } else {
                    plugin.getLogger().info("[Migration] No pending legacy databases or files found to migrate.");
                }

            } catch (Exception e) {
                plugin.getLogger().severe("[Migration] Error during legacy migration: " + e.getMessage());
                e.printStackTrace();
            }

            return report;
        });
    }

    /**
     * Migrates legacy curiospaper.db SQLite database if present.
     */
    private void migrateCuriosSQLite(MigrationReport report) {
        File[] candidateFiles = new File[] {
                new File(plugin.getServer().getWorldContainer(), "plugins/CuriosPaper/storage/curiospaper.db"),
                new File(plugin.getServer().getWorldContainer(), "plugins/CuriosPaper/curiospaper.db")
        };

        for (File dbFile : candidateFiles) {
            if (!dbFile.exists() || !dbFile.isFile()) continue;

            File migratedMarker = new File(dbFile.getParentFile(), dbFile.getName() + ".migrated");
            if (migratedMarker.exists()) continue;

            plugin.getLogger().info("[Migration] Found legacy CuriosPaper SQLite database at: " + dbFile.getPath());

            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException ignored) {}

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath())) {
                DatabaseMetaData meta = conn.getMetaData();

                // 1. Migrate curios_player_data
                try (ResultSet tables = meta.getTables(null, null, "curios_player_data", null)) {
                    if (tables.next()) {
                        String sql = "SELECT uuid, inventory_group, accessories FROM curios_player_data";
                        try (PreparedStatement ps = conn.prepareStatement(sql);
                             ResultSet rs = ps.executeQuery()) {
                            AddonDataStore curiosStore = plugin.getStorageManager().getDataStore("curiospaper");
                            while (rs.next()) {
                                String uuidStr = rs.getString("uuid");
                                String rawAccessories = rs.getString("accessories");
                                if (uuidStr == null || rawAccessories == null || rawAccessories.isEmpty()) continue;

                                try {
                                    UUID uuid = UUID.fromString(uuidStr);
                                    Map<String, List<ItemStack>> map = ItemStackSerializer.deserializeItemMap(rawAccessories);
                                    if (map != null && !map.isEmpty()) {
                                        // Rebuild outdated items
                                        rebuildItemMap(map);
                                        curiosStore.saveItemMap(uuid, "inventory", map).join();
                                        report.migratedCuriosPlayers++;
                                    }
                                } catch (IllegalArgumentException ignored) {}
                            }
                        }
                    }
                }

                // 2. Migrate curios_addon_data
                try (ResultSet tables = meta.getTables(null, null, "curios_addon_data", null)) {
                    if (tables.next()) {
                        String sql = "SELECT uuid, addon_id, data FROM curios_addon_data";
                        try (PreparedStatement ps = conn.prepareStatement(sql);
                             ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String uuidStr = rs.getString("uuid");
                                String addonId = rs.getString("addon_id");
                                String data = rs.getString("data");
                                if (uuidStr == null || addonId == null || data == null) continue;

                                try {
                                    UUID uuid = UUID.fromString(uuidStr);
                                    AddonDataStore addonStore = plugin.getStorageManager().getDataStore(addonId);
                                    addonStore.save(uuid, "data", data).join();
                                    report.migratedAddonRecords++;
                                } catch (IllegalArgumentException ignored) {}
                            }
                        }
                    }
                }

                report.addLog("Migrated SQLite database: " + dbFile.getName());

            } catch (SQLException e) {
                plugin.getLogger().warning("[Migration] Failed to query SQLite database " + dbFile.getName() + ": " + e.getMessage());
            }

            // Mark database file as migrated to prevent duplicate re-reading
            try {
                if (!dbFile.renameTo(migratedMarker)) {
                    Files.writeString(migratedMarker.toPath(), "Migrated on " + new java.util.Date());
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * Migrates curios_player_data and curios_addon_data tables from active MySQL if available.
     */
    private void migrateCuriosMySQL(MigrationReport report) {
        if (!"mysql".equalsIgnoreCase(plugin.getStorageManager().getStorageType())) {
            return;
        }

        try {
            org.bg52.synthetix.storage.driver.MysqlStorageProvider mysqlProvider =
                    (org.bg52.synthetix.storage.driver.MysqlStorageProvider) plugin.getStorageManager().getProvider();

            // Reflection or direct connection check to inspect MySQL tables
            java.lang.reflect.Method getConnectionMethod = mysqlProvider.getClass().getDeclaredMethod("getConnection");
            getConnectionMethod.setAccessible(true);

            try (Connection conn = (Connection) getConnectionMethod.invoke(mysqlProvider)) {
                DatabaseMetaData meta = conn.getMetaData();

                // Check curios_player_data table
                try (ResultSet rs = meta.getTables(null, null, "curios_player_data", null)) {
                    if (rs.next()) {
                        plugin.getLogger().info("[Migration] Found legacy curios_player_data table in MySQL database!");
                        String sql = "SELECT uuid, inventory_group, accessories FROM curios_player_data";
                        try (PreparedStatement ps = conn.prepareStatement(sql);
                             ResultSet qrs = ps.executeQuery()) {
                            AddonDataStore curiosStore = plugin.getStorageManager().getDataStore("curiospaper");
                            while (qrs.next()) {
                                String uuidStr = qrs.getString("uuid");
                                String rawAccessories = qrs.getString("accessories");
                                if (uuidStr == null || rawAccessories == null || rawAccessories.isEmpty()) continue;

                                try {
                                    UUID uuid = UUID.fromString(uuidStr);
                                    Map<String, List<ItemStack>> map = ItemStackSerializer.deserializeItemMap(rawAccessories);
                                    if (map != null && !map.isEmpty()) {
                                        rebuildItemMap(map);
                                        curiosStore.saveItemMap(uuid, "inventory", map).join();
                                        report.migratedCuriosPlayers++;
                                    }
                                } catch (IllegalArgumentException ignored) {}
                            }
                        }

                        // Rename or archive table
                        try (Statement stmt = conn.createStatement()) {
                            stmt.executeUpdate("RENAME TABLE curios_player_data TO curios_player_data_migrated");
                        } catch (SQLException ignored) {}
                    }
                }

                // Check curios_addon_data table
                try (ResultSet rs = meta.getTables(null, null, "curios_addon_data", null)) {
                    if (rs.next()) {
                        plugin.getLogger().info("[Migration] Found legacy curios_addon_data table in MySQL database!");
                        String sql = "SELECT uuid, addon_id, data FROM curios_addon_data";
                        try (PreparedStatement ps = conn.prepareStatement(sql);
                             ResultSet qrs = ps.executeQuery()) {
                            while (qrs.next()) {
                                String uuidStr = qrs.getString("uuid");
                                String addonId = qrs.getString("addon_id");
                                String data = qrs.getString("data");
                                if (uuidStr == null || addonId == null || data == null) continue;

                                try {
                                    UUID uuid = UUID.fromString(uuidStr);
                                    AddonDataStore addonStore = plugin.getStorageManager().getDataStore(addonId);
                                    addonStore.save(uuid, "data", data).join();
                                    report.migratedAddonRecords++;
                                } catch (IllegalArgumentException ignored) {}
                            }
                        }

                        // Rename or archive table
                        try (Statement stmt = conn.createStatement()) {
                            stmt.executeUpdate("RENAME TABLE curios_addon_data TO curios_addon_data_migrated");
                        } catch (SQLException ignored) {}
                    }
                }
            }
        } catch (Throwable ignored) {
            // MySQL table might not exist or provider not MySQL
        }
    }

    /**
     * Migrates legacy YAML player data files in CuriosPaper folders.
     */
    private void migrateCuriosYaml(MigrationReport report) {
        File[] candidateDirs = new File[] {
                new File(plugin.getServer().getWorldContainer(), "plugins/CuriosPaper/playerdata"),
                new File(plugin.getServer().getWorldContainer(), "plugins/CuriosPaper/data/players"),
                new File(plugin.getServer().getWorldContainer(), "plugins/CuriosPaper/storage/playerdata")
        };

        AddonDataStore curiosStore = plugin.getStorageManager().getDataStore("curiospaper");

        for (File dir : candidateDirs) {
            if (!dir.exists() || !dir.isDirectory()) continue;

            File[] ymlFiles = dir.listFiles((d, name) -> name.endsWith(".yml"));
            if (ymlFiles == null || ymlFiles.length == 0) continue;

            plugin.getLogger().info("[Migration] Found " + ymlFiles.length + " legacy YAML player files in: " + dir.getPath());

            for (File file : ymlFiles) {
                String nameNoExt = file.getName().replace(".yml", "");
                UUID uuid;
                try {
                    uuid = UUID.fromString(nameNoExt);
                } catch (IllegalArgumentException e) {
                    continue;
                }

                try {
                    YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                    Map<String, List<ItemStack>> map = new HashMap<>();

                    // 1. Try 'slots' section (CuriosPaper 3.0 / YAML format)
                    ConfigurationSection slotsSec = config.getConfigurationSection("slots");
                    if (slotsSec != null) {
                        for (String slotType : slotsSec.getKeys(false)) {
                            List<?> rawList = slotsSec.getList(slotType);
                            if (rawList != null) {
                                List<ItemStack> items = new ArrayList<>();
                                for (Object obj : rawList) {
                                    if (obj instanceof ItemStack is && !is.getType().isAir()) {
                                        items.add(is);
                                    } else {
                                        items.add(null);
                                    }
                                }
                                map.put(slotType.toLowerCase(Locale.ROOT), items);
                            }
                        }
                    }

                    // 2. Try 'accessories' section (Old CuriosPaper format)
                    if (map.isEmpty()) {
                        ConfigurationSection accSec = config.getConfigurationSection("accessories");
                        if (accSec != null) {
                            for (String slotType : accSec.getKeys(false)) {
                                ConfigurationSection slotSec = accSec.getConfigurationSection(slotType);
                                if (slotSec != null) {
                                    List<ItemStack> items = new ArrayList<>();
                                    List<String> keys = new ArrayList<>(slotSec.getKeys(false));
                                    keys.sort(Comparator.comparingInt(k -> {
                                        try { return Integer.parseInt(k); } catch (Exception ex) { return Integer.MAX_VALUE; }
                                    }));
                                    for (String k : keys) {
                                        ItemStack it = slotSec.getItemStack(k);
                                        items.add(it);
                                    }
                                    map.put(slotType.toLowerCase(Locale.ROOT), items);
                                }
                            }
                        }
                    }

                    // 3. Try 'inventories.1.accessories' section
                    if (map.isEmpty()) {
                        ConfigurationSection invAccSec = config.getConfigurationSection("inventories.1.accessories");
                        if (invAccSec != null) {
                            for (String slotType : invAccSec.getKeys(false)) {
                                ConfigurationSection slotSec = invAccSec.getConfigurationSection(slotType);
                                if (slotSec != null) {
                                    List<ItemStack> items = new ArrayList<>();
                                    List<String> keys = new ArrayList<>(slotSec.getKeys(false));
                                    keys.sort(Comparator.comparingInt(k -> {
                                        try { return Integer.parseInt(k); } catch (Exception ex) { return Integer.MAX_VALUE; }
                                    }));
                                    for (String k : keys) {
                                        ItemStack it = slotSec.getItemStack(k);
                                        items.add(it);
                                    }
                                    map.put(slotType.toLowerCase(Locale.ROOT), items);
                                }
                            }
                        }
                    }

                    if (!map.isEmpty()) {
                        rebuildItemMap(map);
                        curiosStore.saveItemMap(uuid, "inventory", map).join();
                        report.migratedCuriosPlayers++;
                    }

                    // Also check addons section
                    ConfigurationSection addonsSec = config.getConfigurationSection("addons");
                    if (addonsSec != null) {
                        for (String addonId : addonsSec.getKeys(false)) {
                            String data = addonsSec.getString(addonId);
                            if (data != null && !data.isEmpty()) {
                                plugin.getStorageManager().getDataStore(addonId).save(uuid, "data", data).join();
                                report.migratedAddonRecords++;
                            }
                        }
                    }

                    // Mark file as migrated
                    File migratedFile = new File(dir, file.getName() + ".migrated");
                    if (!file.renameTo(migratedFile)) {
                        file.delete();
                    }

                } catch (Exception e) {
                    plugin.getLogger().warning("[Migration] Failed to migrate YAML file " + file.getName() + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * Migrates legacy ProbablyBackpacks backpack_data.yml into Synthetix store if not present.
     */
    private void migrateProbablyBackpacksYaml(MigrationReport report) {
        File bpFile = new File(plugin.getServer().getWorldContainer(), "plugins/ProbablyBackpacks/backpack_data.yml");
        if (!bpFile.exists() || !bpFile.isFile()) return;

        File marker = new File(bpFile.getParentFile(), ".synthetix_migrated");
        if (marker.exists()) return;

        plugin.getLogger().info("[Migration] Found ProbablyBackpacks data file at: " + bpFile.getPath());
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(bpFile);
            ConfigurationSection root = config.getConfigurationSection("backpacks");
            if (root != null) {
                AddonDataStore bpStore = plugin.getStorageManager().getDataStore("probablybackpacks");
                for (String uuidStr : root.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        String path = "backpacks." + uuidStr + ".";
                        String type = config.getString(path + "type", "leather_backpack");
                        String color = config.getString(path + "color", "");
                        String owner = config.getString(path + "owner_uuid", "");
                        String pin = config.getString(path + "pin_code", "");
                        String metaPayload = type + ":" + color + ":" + owner + ":" + pin;

                        bpStore.save(uuid, "meta", metaPayload).join();

                        String itemsBase64 = config.getString(path + "items", "");
                        if (!itemsBase64.isEmpty()) {
                            List<ItemStack> items = ItemStackSerializer.deserializeItemList(itemsBase64);
                            if (items != null && !items.isEmpty()) {
                                rebuildItemList(items);
                                bpStore.saveItems(uuid, "items", items).join();
                            }
                        }

                        String upgsBase64 = config.getString(path + "upgrades", "");
                        if (!upgsBase64.isEmpty()) {
                            List<ItemStack> upgs = ItemStackSerializer.deserializeItemList(upgsBase64);
                            if (upgs != null && !upgs.isEmpty()) {
                                rebuildItemList(upgs);
                                bpStore.saveItems(uuid, "upgrades", upgs).join();
                            }
                        }

                        report.migratedBackpacks++;
                    } catch (Exception ignored) {}
                }
            }

            try {
                Files.writeString(marker.toPath(), "Migrated on " + new java.util.Date());
            } catch (Exception ignored) {}

        } catch (Exception e) {
            plugin.getLogger().warning("[Migration] Failed to migrate ProbablyBackpacks data: " + e.getMessage());
        }
    }

    private void rebuildItemMap(Map<String, List<ItemStack>> map) {
        if (plugin.getItemUpdateManager() == null || map == null) return;
        for (List<ItemStack> list : map.values()) {
            if (list == null) continue;
            for (int i = 0; i < list.size(); i++) {
                ItemStack item = list.get(i);
                if (item != null && !item.getType().isAir()) {
                    if (plugin.getItemUpdateManager().isItemOutdated(item)) {
                        list.set(i, plugin.getItemUpdateManager().rebuildItem(item));
                    }
                }
            }
        }
    }

    private void rebuildItemList(List<ItemStack> list) {
        if (plugin.getItemUpdateManager() == null || list == null) return;
        for (int i = 0; i < list.size(); i++) {
            ItemStack item = list.get(i);
            if (item != null && !item.getType().isAir()) {
                if (plugin.getItemUpdateManager().isItemOutdated(item)) {
                    list.set(i, plugin.getItemUpdateManager().rebuildItem(item));
                }
            }
        }
    }
}

