package org.bg52.synthetix.storage;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Low-level database provider SPI for SQLite, MySQL, MongoDB, and YAML.
 */
public interface StorageProvider {

    /**
     * Initializes connection, creates pools and tables/collections.
     */
    CompletableFuture<Void> connect();

    /**
     * Synchronously disconnects and flushes pending writes on plugin disable.
     */
    void disconnect();

    /**
     * Saves a raw data string for an addon and player.
     */
    CompletableFuture<Void> saveRaw(String addonId, UUID playerUUID, String key, String data);

    /**
     * Loads a raw data string for an addon and player.
     */
    CompletableFuture<String> loadRaw(String addonId, UUID playerUUID, String key);

    /**
     * Deletes a record.
     */
    CompletableFuture<Void> deleteRaw(String addonId, UUID playerUUID, String key);

    /**
     * Lists all keys stored for an addon and player.
     */
    CompletableFuture<Set<String>> getAllKeys(String addonId, UUID playerUUID);

    // =========================================================================
    // PLACED CUSTOM BLOCK PERSISTENCE
    // =========================================================================

    /**
     * Saves or updates a placed custom block record in the database.
     */
    CompletableFuture<Void> saveBlock(PlacedBlockRecord record);

    /**
     * Deletes a placed custom block record by world coordinates.
     */
    CompletableFuture<Void> deleteBlock(String world, int x, int y, int z);

    /**
     * Loads all placed custom block records from the database.
     */
    CompletableFuture<java.util.List<PlacedBlockRecord>> loadAllBlocks();

    /**
     * Loads a specific placed custom block record by world coordinates.
     */
    CompletableFuture<PlacedBlockRecord> loadBlock(String world, int x, int y, int z);

    /**
     * Loads placed custom block records bounded to a specific chunk.
     */
    CompletableFuture<java.util.List<PlacedBlockRecord>> loadBlocksInChunk(String world, int chunkX, int chunkZ);

    /**
     * Name of the storage provider (e.g. "sqlite", "mysql", "mongodb", "yaml").
     */
    String getName();
}

