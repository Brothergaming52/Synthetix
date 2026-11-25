package org.bg52.synthetix.storage;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Isolated, high-performance data store provided to each addon plugin.
 * Handles async persistence of raw data strings, item lists, and categorized item maps.
 */
public interface AddonDataStore {

    /**
     * Unique identifier for this addon (e.g. "curiospaper", "backpacks").
     */
    String getAddonId();

    /**
     * Saves arbitrary string data for a player under a specific key.
     */
    CompletableFuture<Void> save(UUID playerUUID, String key, String data);

    /**
     * Loads arbitrary string data for a player by key.
     */
    CompletableFuture<String> load(UUID playerUUID, String key);

    /**
     * Saves a list of ItemStacks for a player under a key.
     */
    CompletableFuture<Void> saveItems(UUID playerUUID, String key, List<ItemStack> items);

    /**
     * Loads a list of ItemStacks for a player by key.
     */
    CompletableFuture<List<ItemStack>> loadItems(UUID playerUUID, String key);

    /**
     * Saves a categorized item map (e.g. slot -> items) for a player under a key.
     */
    CompletableFuture<Void> saveItemMap(UUID playerUUID, String key, Map<String, List<ItemStack>> itemMap);

    /**
     * Loads a categorized item map for a player by key.
     */
    CompletableFuture<Map<String, List<ItemStack>>> loadItemMap(UUID playerUUID, String key);

    /**
     * Deletes a data entry for a player by key.
     */
    CompletableFuture<Void> delete(UUID playerUUID, String key);

    /**
     * Retrieves all stored keys for a player.
     */
    CompletableFuture<Set<String>> getAllKeys(UUID playerUUID);
}

