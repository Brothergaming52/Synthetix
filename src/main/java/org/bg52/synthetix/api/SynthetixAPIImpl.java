package org.bg52.synthetix.api;

import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.api.editor.ItemEditorRegistry;
import org.bg52.synthetix.gui.editor.MainEditorMenu;
import org.bg52.synthetix.item.ItemDefinition;
import org.bg52.synthetix.lang.LanguageManager;
import org.bg52.synthetix.storage.AddonDataStore;
import org.bg52.synthetix.storage.StorageManager;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.File;

/**
 * Concrete implementation of the SynthetixAPI service.
 */
public class SynthetixAPIImpl implements SynthetixAPI {

    private final SynthetixPlugin plugin;
    private final NamespacedKey itemIdKey;

    public SynthetixAPIImpl(SynthetixPlugin plugin) {
        this.plugin = plugin;
        this.itemIdKey = new NamespacedKey("synthetix", "item_id");
    }

    @Override
    public ItemStack createItemStack(String itemId) {
        if (itemId == null || itemId.isEmpty()) return null;
        return plugin.getItemRegistry().createItemStack(itemId);
    }

    @Override
    public String getItemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(itemIdKey, PersistentDataType.STRING)) {
            return pdc.get(itemIdKey, PersistentDataType.STRING);
        }
        return null;
    }

    @Override
    public boolean isCustomItem(ItemStack item) {
        return getItemId(item) != null;
    }

    @Override
    public ItemEditorRegistry getEditorRegistry() {
        return plugin.getEditorRegistry();
    }

    @Override
    public void openEditor(Player player, ItemDefinition def) {
        if (player != null && def != null) {
            MainEditorMenu.open(player, plugin, def);
        }
    }

    @Override
    public LanguageManager getLanguageManager() {
        return plugin.getLanguageManager();
    }

    @Override
    public StorageManager getStorageManager() {
        return plugin.getStorageManager();
    }

    @Override
    public org.bg52.synthetix.recipe.RecipeManager getRecipeManager() {
        return plugin.getRecipeManager();
    }

    @Override
    public org.bg52.synthetix.distribution.DistributionManager getDistributionManager() {
        return plugin.getDistributionManager();
    }

    @Override
    public org.bg52.synthetix.resourcepack.ResourcePackManager getResourcePackManager() {
        return plugin.getResourcePackManager();
    }

    @Override
    public void registerResourcePack(Plugin plugin, java.io.File folder) {
        this.plugin.getResourcePackManager().registerResource(plugin, folder);
    }

    @Override
    public void registerResourcePackZip(Plugin plugin, java.io.File zipFile) {
        this.plugin.getResourcePackManager().registerResourceZip(plugin, zipFile);
    }

    @Override
    public void registerItemModelOverride(String material, int customModelData, String modelPath) {
        this.plugin.getResourcePackManager().registerItemModelOverride(material, customModelData, modelPath);
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> rebuildResourcePack() {
        return this.plugin.getResourcePackManager().generatePackAsync();
    }

    @Override
    public org.bg52.synthetix.item.ItemRegistry getItemRegistry() {
        return plugin.getItemRegistry();
    }

    @Override
    public org.bg52.synthetix.ability.TriggerManager getTriggerManager() {
        return plugin.getTriggerManager();
    }

    @Override
    public org.bg52.synthetix.set.SetManager getSetManager() {
        return plugin.getSetManager();
    }

    @Override
    public org.bg52.synthetix.ability.AuraManager getAuraManager() {
        return plugin.getAuraManager();
    }

    @Override
    public org.bg52.synthetix.bundle.PackExportManager getExportManager() {
        return plugin.getExportManager();
    }

    @Override
    public org.bg52.synthetix.bundle.PackImportManager getImportManager() {
        return plugin.getImportManager();
    }

    @Override
    public void openRecipeViewer(Player player, String itemId) {
        openRecipeViewer(player, itemId, null);
    }

    @Override
    public void openRecipeViewer(Player player, String itemId, Runnable onBack) {
        if (player != null && itemId != null) {
            org.bg52.synthetix.gui.recipe.RecipeViewerGUI.open(player, plugin, itemId, onBack);
        }
    }

    @Override
    public AddonDataStore getDataStore(String addonId) {
        return plugin.getStorageManager().getDataStore(addonId);
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public org.bg52.synthetix.block.CustomBlockRegistry getCustomBlockRegistry() {
        return plugin.getCustomBlockRegistry();
    }

    @Override
    public org.bg52.synthetix.block.CustomBlockManager getCustomBlockManager() {
        return plugin.getCustomBlockManager();
    }

    @Override
    public org.bg52.synthetix.block.BlockbenchImporter getBlockbenchImporter() {
        return plugin.getBlockbenchImporter();
    }

    @Override
    public void openBlockEditor(Player player, org.bg52.synthetix.block.CustomBlockDefinition def) {
        if (player != null && def != null) {
            org.bg52.synthetix.gui.editor.block.BlockEditorMenu.open(player, plugin, def);
        }
    }

    @Override
    public void openBlockBrowser(Player player) {
        if (player != null) {
            org.bg52.synthetix.gui.editor.block.BlockBrowserGUI.open(player, plugin);
        }
    }

    @Override
    public boolean isCustomBlock(org.bukkit.Location loc) {
        return plugin.getCustomBlockManager().isCustomBlock(loc);
    }

    @Override
    public org.bukkit.Location getRootLocation(org.bukkit.Location loc) {
        return plugin.getCustomBlockManager().getRootLocation(loc);
    }

    @Override
    public org.bg52.synthetix.block.PlacedBlockInstance getPlacedBlock(org.bukkit.Location loc) {
        return plugin.getCustomBlockManager().getPlacedBlock(loc);
    }

    @Override
    public java.util.Collection<org.bg52.synthetix.block.PlacedBlockInstance> getBlocksInChunk(org.bukkit.Chunk chunk) {
        return plugin.getCustomBlockManager().getBlocksInChunk(chunk);
    }

    @Override
    public java.util.Collection<org.bg52.synthetix.block.PlacedBlockInstance> getBlocksInChunk(org.bukkit.World world, int chunkX, int chunkZ) {
        return plugin.getCustomBlockManager().getBlocksInChunk(world, chunkX, chunkZ);
    }

    @Override
    public org.bg52.synthetix.block.PlacedBlockInstance placeCustomBlock(org.bukkit.Location loc, String blockId, org.bukkit.block.BlockFace facing, Player placer) {
        return plugin.getCustomBlockManager().placeBlock(loc, blockId, facing, placer);
    }

    @Override
    public boolean removeCustomBlock(org.bukkit.Location loc, boolean dropItems) {
        return plugin.getCustomBlockManager().removeBlock(loc, dropItems);
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> registerBlockWithModel(Plugin addonPlugin, org.bg52.synthetix.block.CustomBlockDefinition blockDef, String modelJson, java.util.Map<String, byte[]> textures) {
        blockDef.setOwningPlugin(addonPlugin != null ? addonPlugin.getName() : "addon");
        String blockId = blockDef.getId();

        File sourceDir = plugin.getBlockbenchImporter().getSourceModelsFolder();
        File sourceFile = new File(sourceDir, blockId + "_source.json");
        try {
            sourceFile.getParentFile().mkdirs();
            java.nio.file.Files.writeString(sourceFile.toPath(), modelJson, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not write addon source model: " + e.getMessage());
        }

        if (textures != null && !textures.isEmpty()) {
            File rpTexDir = new File(plugin.getDataFolder(), "resources/assets/synthetix/textures/block");
            File rpTexItemDir = new File(plugin.getDataFolder(), "resources/assets/synthetix/textures/item");
            rpTexDir.mkdirs();
            rpTexItemDir.mkdirs();
            for (var entry : textures.entrySet()) {
                File texOut = new File(rpTexDir, entry.getKey());
                File texItemOut = new File(rpTexItemDir, entry.getKey());
                try {
                    java.nio.file.Files.write(texOut.toPath(), entry.getValue());
                    java.nio.file.Files.write(texItemOut.toPath(), entry.getValue());
                } catch (Exception ignored) {}
            }
        }

        plugin.getCustomBlockRegistry().register(blockDef);
        plugin.getBlockbenchImporter().sliceAndBuildModels(blockDef);
        plugin.getCustomBlockRegistry().save(blockDef);
        return plugin.getResourcePackManager().generatePackAsync();
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> registerBlockWithModel(Plugin addonPlugin, org.bg52.synthetix.block.CustomBlockDefinition blockDef, java.io.File modelFile, java.util.List<java.io.File> textureFiles) {
        String modelContent = "";
        try {
            modelContent = java.nio.file.Files.readString(modelFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read addon model file: " + e.getMessage());
        }

        java.util.Map<String, byte[]> textures = new java.util.HashMap<>();
        if (textureFiles != null) {
            for (java.io.File tf : textureFiles) {
                try {
                    textures.put(tf.getName(), java.nio.file.Files.readAllBytes(tf.toPath()));
                } catch (Exception ignored) {}
            }
        }

        return registerBlockWithModel(addonPlugin, blockDef, modelContent, textures);
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> registerItemWithModel(Plugin addonPlugin, ItemDefinition itemDef, String modelJson, java.util.Map<String, byte[]> textures) {
        itemDef.setOwningPlugin(addonPlugin != null ? addonPlugin.getName() : "addon");
        String itemId = itemDef.getId();

        File rpModelDir = new File(plugin.getDataFolder(), "resources/assets/synthetix/models/item");
        File rpModelBlockDir = new File(plugin.getDataFolder(), "resources/assets/synthetix/models/block");
        rpModelDir.mkdirs();
        rpModelBlockDir.mkdirs();
        File itemModelFile = new File(rpModelDir, itemId + ".json");
        File blockModelFile = new File(rpModelBlockDir, itemId + ".json");
        try {
            java.nio.file.Files.writeString(itemModelFile.toPath(), modelJson, java.nio.charset.StandardCharsets.UTF_8);
            java.nio.file.Files.writeString(blockModelFile.toPath(), modelJson, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not write addon item model: " + e.getMessage());
        }

        if (textures != null && !textures.isEmpty()) {
            File rpTexDir = new File(plugin.getDataFolder(), "resources/assets/synthetix/textures/item");
            File rpTexBlockDir = new File(plugin.getDataFolder(), "resources/assets/synthetix/textures/block");
            rpTexDir.mkdirs();
            rpTexBlockDir.mkdirs();
            for (var entry : textures.entrySet()) {
                File texOut = new File(rpTexDir, entry.getKey());
                File texBlockOut = new File(rpTexBlockDir, entry.getKey());
                try {
                    java.nio.file.Files.write(texOut.toPath(), entry.getValue());
                    java.nio.file.Files.write(texBlockOut.toPath(), entry.getValue());
                } catch (Exception ignored) {}
            }
        }

        if (itemDef.getCustomModelData() == null || itemDef.getCustomModelData() <= 0) {
            itemDef.setCustomModelData(plugin.getBlockbenchImporter().generateNextCustomModelData());
        }
        itemDef.setItemModel("synthetix:" + itemId);

        plugin.getItemRegistry().register(itemDef);
        plugin.getItemRegistry().save(itemDef);
        plugin.getResourcePackManager().registerItemModelOverride("paper", itemDef.getCustomModelData(), "synthetix:item/" + itemId);
        return plugin.getResourcePackManager().generatePackAsync();
    }

    @Override
    public boolean updateBlockState(org.bukkit.Location loc, String key, Object value) {
        return updateBlockState(loc, key, value, null);
    }

    @Override
    public boolean updateBlockState(org.bukkit.Location loc, String key, Object value, Player actor) {
        return plugin.getCustomBlockManager().updateBlockState(loc, key, value, actor);
    }

    @Override
    public Object getBlockState(org.bukkit.Location loc, String key) {
        var inst = plugin.getCustomBlockManager().getPlacedBlock(loc);
        return inst != null ? inst.getState(key) : null;
    }

    @Override
    public <T> T getBlockState(org.bukkit.Location loc, String key, Class<T> type) {
        var inst = plugin.getCustomBlockManager().getPlacedBlock(loc);
        return inst != null ? inst.getState(key, type) : null;
    }

    @Override
    public boolean transformBlock(org.bukkit.Location loc, String newBlockId) {
        return transformBlock(loc, newBlockId, null);
    }

    @Override
    public boolean transformBlock(org.bukkit.Location loc, String newBlockId, Player actor) {
        return plugin.getCustomBlockManager().transformBlock(loc, newBlockId, actor);
    }

    @Override
    public void setBlockAnimationSpeed(org.bukkit.Location loc, double speedMultiplier) {
        plugin.getCustomBlockManager().setBlockAnimationSpeed(loc, speedMultiplier, null);
    }

    @Override
    public void setBlockAnimationPaused(org.bukkit.Location loc, boolean paused) {
        plugin.getCustomBlockManager().setBlockAnimationPaused(loc, paused);
    }

    @Override
    public void setPartAnimationSpeed(org.bukkit.Location loc, String partId, double speedMultiplier) {
        plugin.getCustomBlockManager().setPartAnimationSpeed(loc, partId, speedMultiplier, null);
    }

    @Override
    public void setPartAnimationPaused(org.bukkit.Location loc, String partId, boolean paused) {
        plugin.getCustomBlockManager().setPartAnimationPaused(loc, partId, paused);
    }

    @Override
    public boolean setPartModel(org.bukkit.Location loc, String partId, String itemModel) {
        return plugin.getCustomBlockManager().setPartModel(loc, partId, itemModel, null);
    }

    @Override
    public org.bg52.synthetix.keybind.KeybindManager getKeybindManager() {
        return plugin.getKeybindManager();
    }

    @Override
    public org.bg52.synthetix.entity.CustomEntityRegistry getCustomEntityRegistry() {
        return plugin.getCustomEntityRegistry();
    }

    @Override
    public org.bg52.synthetix.entity.CustomEntityManager getCustomEntityManager() {
        return plugin.getCustomEntityManager();
    }

    @Override
    public org.bg52.synthetix.entity.runtime.CustomEntityInstance spawnCustomEntity(String entityId, org.bukkit.Location loc) {
        return plugin.getCustomEntityManager().spawnEntity(entityId, loc);
    }

    @Override
    public org.bg52.synthetix.entity.runtime.CustomEntityInstance spawnCustomEntity(org.bg52.synthetix.entity.model.CustomEntityDefinition def, org.bukkit.Location loc) {
        return plugin.getCustomEntityManager().spawnEntity(def, loc);
    }

    @Override
    public org.bg52.synthetix.entity.runtime.CustomEntityInstance getCustomEntity(org.bukkit.entity.Entity entity) {
        return plugin.getCustomEntityManager().getInstance(entity);
    }

    @Override
    public org.bg52.synthetix.entity.runtime.CustomEntityInstance getCustomEntity(java.util.UUID uuid) {
        return plugin.getCustomEntityManager().getInstance(uuid);
    }

    @Override
    public boolean isCustomEntity(org.bukkit.entity.Entity entity) {
        return plugin.getCustomEntityManager().getInstance(entity) != null;
    }

    @Override
    public String getCustomEntityId(org.bukkit.entity.Entity entity) {
        var inst = plugin.getCustomEntityManager().getInstance(entity);
        return inst != null ? inst.getDefinition().getId() : null;
    }

    @Override
    public org.bg52.synthetix.entity.importer.BedrockEntityImporter getBedrockEntityImporter() {
        return plugin.getBedrockEntityImporter();
    }

    @Override
    public org.bg52.synthetix.entity.model.CustomEntityDefinition importBedrockEntity(String entityId, java.io.File modelFile) {
        return plugin.getBedrockEntityImporter().importEntity(modelFile, entityId);
    }

    @Override
    public org.bg52.synthetix.entity.CustomEntityBuilder createEntity(String entityId) {
        return new org.bg52.synthetix.entity.CustomEntityBuilder(plugin, entityId);
    }

    @Override
    public boolean playAnimation(org.bukkit.entity.Entity entity, String animationName) {
        return plugin.getCustomEntityManager().playAnimation(entity, animationName);
    }

    @Override
    public boolean playAnimation(org.bukkit.entity.Entity entity, String animationName, boolean loop, float blendTicks) {
        return plugin.getCustomEntityManager().playAnimation(entity, animationName, loop, blendTicks);
    }

    @Override
    public void stopAnimation(org.bukkit.entity.Entity entity) {
        plugin.getCustomEntityManager().stopAnimation(entity);
    }

    @Override
    public boolean hasAnimation(org.bukkit.entity.Entity entity, String animationName) {
        return plugin.getCustomEntityManager().hasAnimation(entity, animationName);
    }

    @Override
    public java.util.List<String> getAvailableAnimations(org.bukkit.entity.Entity entity) {
        return plugin.getCustomEntityManager().getAvailableAnimations(entity);
    }

    @Override
    public org.bg52.synthetix.entity.animation.EntityState getEntityState(org.bukkit.entity.Entity entity) {
        return plugin.getCustomEntityManager().getEntityState(entity);
    }

    @Override
    public void setEntityState(org.bukkit.entity.Entity entity, org.bg52.synthetix.entity.animation.EntityState state) {
        plugin.getCustomEntityManager().setEntityState(entity, state);
    }

    @Override
    public org.bg52.synthetix.bedrock.BedrockManager getBedrockManager() {
        return plugin.getBedrockManager();
    }

    @Override
    public org.bg52.synthetix.item.update.ItemUpdateManager getItemUpdateManager() {
        return plugin.getItemUpdateManager();
    }

    @Override
    public ItemStack rebuildItem(ItemStack item) {
        return plugin.getItemUpdateManager() != null ? plugin.getItemUpdateManager().rebuildItem(item) : item;
    }

    @Override
    public boolean isItemOutdated(ItemStack item) {
        return plugin.getItemUpdateManager() != null && plugin.getItemUpdateManager().isItemOutdated(item);
    }

    @Override
    public org.bg52.synthetix.storage.migration.LegacyMigrationManager getLegacyMigrationManager() {
        return plugin.getStorageManager() != null ? plugin.getStorageManager().getMigrationManager() : null;
    }

    @Override
    public org.bg52.synthetix.settings.GlobalSettingsManager getSettingsManager() {
        return plugin.getSettingsManager();
    }
}
