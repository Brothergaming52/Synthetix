package org.bg52.synthetix.storage;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Concrete implementation of AddonDataStore routing to the underlying StorageProvider.
 */
public class AddonDataStoreImpl implements AddonDataStore {

    private final String addonId;
    private final StorageProvider provider;

    public AddonDataStoreImpl(String addonId, StorageProvider provider) {
        this.addonId = addonId.toLowerCase();
        this.provider = provider;
    }

    @Override
    public String getAddonId() {
        return addonId;
    }

    @Override
    public CompletableFuture<Void> save(UUID playerUUID, String key, String data) {
        return provider.saveRaw(addonId, playerUUID, key, data);
    }

    @Override
    public CompletableFuture<String> load(UUID playerUUID, String key) {
        return provider.loadRaw(addonId, playerUUID, key);
    }

    @Override
    public CompletableFuture<Void> saveItems(UUID playerUUID, String key, List<ItemStack> items) {
        String serialized = ItemStackSerializer.serializeItemList(items);
        return provider.saveRaw(addonId, playerUUID, key, serialized);
    }

    @Override
    public CompletableFuture<List<ItemStack>> loadItems(UUID playerUUID, String key) {
        return provider.loadRaw(addonId, playerUUID, key)
                .thenApply(ItemStackSerializer::deserializeItemList);
    }

    @Override
    public CompletableFuture<Void> saveItemMap(UUID playerUUID, String key, Map<String, List<ItemStack>> itemMap) {
        String serialized = ItemStackSerializer.serializeItemMap(itemMap);
        return provider.saveRaw(addonId, playerUUID, key, serialized);
    }

    @Override
    public CompletableFuture<Map<String, List<ItemStack>>> loadItemMap(UUID playerUUID, String key) {
        return provider.loadRaw(addonId, playerUUID, key)
                .thenApply(ItemStackSerializer::deserializeItemMap);
    }

    @Override
    public CompletableFuture<Void> delete(UUID playerUUID, String key) {
        return provider.deleteRaw(addonId, playerUUID, key);
    }

    @Override
    public CompletableFuture<Set<String>> getAllKeys(UUID playerUUID) {
        return provider.getAllKeys(addonId, playerUUID);
    }
}

