package org.bg52.synthetix.storage.driver;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.storage.PlacedBlockRecord;
import org.bg52.synthetix.storage.StorageProvider;
import org.bson.Document;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * MongoDB document storage provider.
 * Automatically partitions addon data into dedicated MongoDB collections.
 */
public class MongoStorageProvider implements StorageProvider {

    private final SynthetixPlugin plugin;
    private final ExecutorService executor;
    private MongoClient mongoClient;
    private MongoDatabase database;

    public MongoStorageProvider(SynthetixPlugin plugin, ExecutorService executor) {
        this.plugin = plugin;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Void> connect() {
        return CompletableFuture.runAsync(() -> {
            try {
                ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("storage.mongodb");
                if (cfg == null) throw new IllegalStateException("Missing storage.mongodb configuration section!");

                String uri = cfg.getString("uri", "mongodb://localhost:27017");
                String dbName = cfg.getString("database", "synthetix");

                this.mongoClient = MongoClients.create(uri);
                this.database = mongoClient.getDatabase(dbName);

                plugin.getLogger().info("[Storage] Connected to MongoDB database: " + dbName);
            } catch (Exception e) {
                plugin.getLogger().severe("[Storage] Failed to initialize MongoDB: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    public void disconnect() {
        if (mongoClient != null) {
            mongoClient.close();
            mongoClient = null;
        }
    }

    private MongoCollection<Document> getCollection(String addonId) {
        return database.getCollection("addon_" + addonId.toLowerCase());
    }

    @Override
    public CompletableFuture<Void> saveRaw(String addonId, UUID playerUUID, String key, String data) {
        return CompletableFuture.runAsync(() -> {
            if (database == null) return;
            String docId = playerUUID.toString() + ":" + key.toLowerCase();
            Document doc = new Document("_id", docId)
                    .append("player_uuid", playerUUID.toString())
                    .append("key", key.toLowerCase())
                    .append("data", data)
                    .append("updated_at", System.currentTimeMillis());

            getCollection(addonId).replaceOne(Filters.eq("_id", docId), doc, new ReplaceOptions().upsert(true));
        }, executor);
    }

    @Override
    public CompletableFuture<String> loadRaw(String addonId, UUID playerUUID, String key) {
        return CompletableFuture.supplyAsync(() -> {
            if (database == null) return null;
            String docId = playerUUID.toString() + ":" + key.toLowerCase();
            Document doc = getCollection(addonId).find(Filters.eq("_id", docId)).first();
            return doc != null ? doc.getString("data") : null;
        }, executor);
    }

    @Override
    public CompletableFuture<Void> deleteRaw(String addonId, UUID playerUUID, String key) {
        return CompletableFuture.runAsync(() -> {
            if (database == null) return;
            String docId = playerUUID.toString() + ":" + key.toLowerCase();
            getCollection(addonId).deleteOne(Filters.eq("_id", docId));
        }, executor);
    }

    @Override
    public CompletableFuture<Set<String>> getAllKeys(String addonId, UUID playerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            Set<String> keys = new HashSet<>();
            if (database == null) return keys;
            for (Document doc : getCollection(addonId).find(Filters.eq("player_uuid", playerUUID.toString()))) {
                String k = doc.getString("key");
                if (k != null) keys.add(k);
            }
            return keys;
        }, executor);
    }

    private MongoCollection<Document> getBlockCollection() {
        return database.getCollection("synthetix_placed_blocks");
    }

    @Override
    public CompletableFuture<Void> saveBlock(PlacedBlockRecord record) {
        return CompletableFuture.runAsync(() -> {
            if (database == null || record == null) return;
            String docId = record.getWorld() + ":" + record.getX() + ":" + record.getY() + ":" + record.getZ();
            Document doc = new Document("_id", docId)
                    .append("world", record.getWorld())
                    .append("x", record.getX())
                    .append("y", record.getY())
                    .append("z", record.getZ())
                    .append("block_id", record.getBlockId())
                    .append("placer_uuid", record.getPlacerUuid() != null ? record.getPlacerUuid().toString() : null)
                    .append("facing", record.getFacing())
                    .append("data_json", record.toDataJson())
                    .append("updated_at", System.currentTimeMillis());

            getBlockCollection().replaceOne(Filters.eq("_id", docId), doc, new ReplaceOptions().upsert(true));
        }, executor);
    }

    @Override
    public CompletableFuture<Void> deleteBlock(String world, int x, int y, int z) {
        return CompletableFuture.runAsync(() -> {
            if (database == null) return;
            String docId = world + ":" + x + ":" + y + ":" + z;
            getBlockCollection().deleteOne(Filters.eq("_id", docId));
        }, executor);
    }

    @Override
    public CompletableFuture<List<PlacedBlockRecord>> loadAllBlocks() {
        return CompletableFuture.supplyAsync(() -> {
            List<PlacedBlockRecord> list = new ArrayList<>();
            if (database == null) return list;
            for (Document doc : getBlockCollection().find()) {
                String world = doc.getString("world");
                Integer x = doc.getInteger("x");
                Integer y = doc.getInteger("y");
                Integer z = doc.getInteger("z");
                String blockId = doc.getString("block_id");
                String placerStr = doc.getString("placer_uuid");
                UUID placer = placerStr != null ? UUID.fromString(placerStr) : null;
                String facing = doc.getString("facing");
                String dataJson = doc.getString("data_json");

                if (world != null && x != null && y != null && z != null && blockId != null) {
                    PlacedBlockRecord r = new PlacedBlockRecord(world, x, y, z, blockId, placer, facing);
                    r.fromDataJson(dataJson);
                    list.add(r);
                }
            }
            return list;
        }, executor);
    }

    @Override
    public CompletableFuture<PlacedBlockRecord> loadBlock(String world, int x, int y, int z) {
        return CompletableFuture.supplyAsync(() -> {
            if (database == null) return null;
            String docId = world + ":" + x + ":" + y + ":" + z;
            Document doc = getBlockCollection().find(Filters.eq("_id", docId)).first();
            if (doc != null) {
                String blockId = doc.getString("block_id");
                String placerStr = doc.getString("placer_uuid");
                UUID placer = placerStr != null ? UUID.fromString(placerStr) : null;
                String facing = doc.getString("facing");
                String dataJson = doc.getString("data_json");

                PlacedBlockRecord r = new PlacedBlockRecord(world, x, y, z, blockId, placer, facing);
                r.fromDataJson(dataJson);
                return r;
            }
            return null;
        }, executor);
    }

    @Override
    public CompletableFuture<List<PlacedBlockRecord>> loadBlocksInChunk(String world, int chunkX, int chunkZ) {
        return CompletableFuture.supplyAsync(() -> {
            List<PlacedBlockRecord> list = new ArrayList<>();
            if (database == null) return list;
            int minX = chunkX << 4;
            int maxX = minX + 15;
            int minZ = chunkZ << 4;
            int maxZ = minZ + 15;
            var filter = Filters.and(
                    Filters.eq("world", world),
                    Filters.gte("x", minX),
                    Filters.lte("x", maxX),
                    Filters.gte("z", minZ),
                    Filters.lte("z", maxZ)
            );
            for (Document doc : getBlockCollection().find(filter)) {
                Integer x = doc.getInteger("x");
                Integer y = doc.getInteger("y");
                Integer z = doc.getInteger("z");
                String blockId = doc.getString("block_id");
                String placerStr = doc.getString("placer_uuid");
                UUID placer = placerStr != null ? UUID.fromString(placerStr) : null;
                String facing = doc.getString("facing");
                String dataJson = doc.getString("data_json");

                if (x != null && y != null && z != null && blockId != null) {
                    PlacedBlockRecord r = new PlacedBlockRecord(world, x, y, z, blockId, placer, facing);
                    r.fromDataJson(dataJson);
                    list.add(r);
                }
            }
            return list;
        }, executor);
    }

    @Override
    public String getName() {
        return "mongodb";
    }
}

