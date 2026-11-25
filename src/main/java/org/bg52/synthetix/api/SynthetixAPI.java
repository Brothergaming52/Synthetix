package org.bg52.synthetix.api;

import org.bg52.synthetix.api.editor.ItemEditorRegistry;
import org.bg52.synthetix.item.ItemDefinition;
import org.bg52.synthetix.lang.LanguageManager;
import org.bg52.synthetix.storage.AddonDataStore;
import org.bg52.synthetix.storage.StorageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Optional;

/**
 * Public developer API entry point for Synthetix.
 * Downstream plugins (such as CuriosPaper) access all core services through this interface.
 */
public interface SynthetixAPI {

    /**
     * Retrieves the active SynthetixAPI instance from Bukkit's ServicesManager.
     *
     * @return The active SynthetixAPI instance
     * @throws IllegalStateException if Synthetix is not enabled
     */
    static SynthetixAPI get() {
        RegisteredServiceProvider<SynthetixAPI> provider = Bukkit.getServicesManager().getRegistration(SynthetixAPI.class);
        if (provider == null || provider.getProvider() == null) {
            throw new IllegalStateException("SynthetixAPI is not registered! Is Synthetix installed and enabled?");
        }
        return provider.getProvider();
    }

    /**
     * Safely retrieves the active SynthetixAPI instance if available.
     */
    static Optional<SynthetixAPI> getOptional() {
        RegisteredServiceProvider<SynthetixAPI> provider = Bukkit.getServicesManager().getRegistration(SynthetixAPI.class);
        return provider != null ? Optional.ofNullable(provider.getProvider()) : Optional.empty();
    }

    /**
     * Creates an ItemStack from a registered item ID (native Synthetix item or bridged external item).
     *
     * @param itemId The unique item identifier (e.g. "ruby_ring", "nexo:custom_sword")
     * @return The constructed ItemStack, or null if the item is not registered
     */
    ItemStack createItemStack(String itemId);

    /**
     * Resolves the custom item ID from an existing ItemStack if present.
     *
     * @param item The ItemStack to inspect
     * @return The item ID, or null if not a custom item
     */
    String getItemId(ItemStack item);

    /**
     * Checks if the given ItemStack is a custom Synthetix or bridged engine item.
     *
     * @param item The ItemStack to check
     * @return true if it is a recognized custom item
     */
    boolean isCustomItem(ItemStack item);

    /**
     * Retrieves the ItemEditorRegistry for adding custom categories and sub-menus to the in-game item editor.
     */
    ItemEditorRegistry getEditorRegistry();

    /**
     * Opens the in-game item editor GUI for a player.
     *
     * @param player The player
     * @param def The ItemDefinition to edit
     */
    void openEditor(Player player, ItemDefinition def);

    /**
     * Retrieves the custom item registry.
     */
    org.bg52.synthetix.item.ItemRegistry getItemRegistry();

    /**
     * Retrieves the universal interactive trigger & ability manager.
     */
    org.bg52.synthetix.ability.TriggerManager getTriggerManager();

    /**
     * Retrieves the equipment sets & tier bonus manager.
     */
    org.bg52.synthetix.set.SetManager getSetManager();

    /**
     * Retrieves the continuous ambient particle aura manager.
     */
    org.bg52.synthetix.ability.AuraManager getAuraManager();

    /**
     * Retrieves the .synthpack export manager.
     */
    org.bg52.synthetix.bundle.PackExportManager getExportManager();

    /**
     * Retrieves the package import manager.
     */
    org.bg52.synthetix.bundle.PackImportManager getImportManager();

    /**
     * Opens the in-game recipe lookup canvas (JEI) for a player.
     */
    void openRecipeViewer(Player player, String itemId);

    /**
     * Opens the in-game recipe lookup canvas (JEI) for a player with a return navigation callback.
     */
    void openRecipeViewer(Player player, String itemId, Runnable onBack);

    /**
     * Retrieves the centralized multi-language localization manager.
     */
    LanguageManager getLanguageManager();

    /**
     * Retrieves the enterprise StorageManager.
     */
    StorageManager getStorageManager();

    /**
     * Retrieves the Universal Recipe Manager.
     */
    org.bg52.synthetix.recipe.RecipeManager getRecipeManager();

    /**
     * Retrieves the World Distribution Manager (Mob Drops, Loot Tables, Trades).
     */
    org.bg52.synthetix.distribution.DistributionManager getDistributionManager();

    /**
     * Retrieves the ResourcePackManager for controlling resource pack generation, merging, and hosting.
     */
    org.bg52.synthetix.resourcepack.ResourcePackManager getResourcePackManager();

    /**
     * Registers a resource pack folder from another plugin (must contain /assets/<namespace>/).
     */
    void registerResourcePack(Plugin plugin, java.io.File folder);

    /**
     * Registers an external zip resource pack from another plugin.
     */
    void registerResourcePackZip(Plugin plugin, java.io.File zipFile);

    /**
     * Registers a custom model data override into minecraft/models/item/<material>.json.
     */
    void registerItemModelOverride(String material, int customModelData, String modelPath);

    /**
     * Asynchronously rebuilds the unified server resource pack and re-injects into active host.
     */
    java.util.concurrent.CompletableFuture<Void> rebuildResourcePack();

    /**
     * Obtains an isolated AddonDataStore for an addon plugin to store player items and state in harmony.
     *
     * @param addonId Unique ID of the addon (e.g. "curiospaper", "backpacks")
     * @return The isolated AddonDataStore instance
     */
    AddonDataStore getDataStore(String addonId);

    /**
     * Obtains an isolated AddonDataStore for a Bukkit Plugin instance.
     */
    default AddonDataStore getDataStore(Plugin addonPlugin) {
        return getDataStore(addonPlugin.getName());
    }

    /**
     * Gets the plugin version.
     */
    String getVersion();

    /**
     * Retrieves the custom block registry.
     */
    org.bg52.synthetix.block.CustomBlockRegistry getCustomBlockRegistry();

    /**
     * Retrieves the custom block manager engine.
     */
    org.bg52.synthetix.block.CustomBlockManager getCustomBlockManager();

    /**
     * Retrieves the Blockbench auto-importer.
     */
    org.bg52.synthetix.block.BlockbenchImporter getBlockbenchImporter();

    /**
     * Opens the in-game custom block editor GUI for a player.
     */
    void openBlockEditor(Player player, org.bg52.synthetix.block.CustomBlockDefinition def);

    /**
     * Opens the in-game custom block browser GUI for a player.
     */
    void openBlockBrowser(Player player);

    /**
     * Checks if the given world block location holds a custom block.
     */
    boolean isCustomBlock(org.bukkit.Location loc);

    /**
     * Resolves the primary root location for any given block or multi-part sub-part location.
     */
    org.bukkit.Location getRootLocation(org.bukkit.Location loc);

    /**
     * Retrieves the active PlacedBlockInstance at a location if present.
     */
    org.bg52.synthetix.block.PlacedBlockInstance getPlacedBlock(org.bukkit.Location loc);

    /**
     * Retrieves all active custom blocks located within a chunk.
     */
    java.util.Collection<org.bg52.synthetix.block.PlacedBlockInstance> getBlocksInChunk(org.bukkit.Chunk chunk);

    /**
     * Retrieves all active custom blocks located within chunk coordinates.
     */
    java.util.Collection<org.bg52.synthetix.block.PlacedBlockInstance> getBlocksInChunk(org.bukkit.World world, int chunkX, int chunkZ);

    /**
     * Places a custom block in the world programmatically.
     */
    org.bg52.synthetix.block.PlacedBlockInstance placeCustomBlock(org.bukkit.Location loc, String blockId, org.bukkit.block.BlockFace facing, Player placer);

    /**
     * Removes a custom block at a location, optionally triggering loot drops.
     */
    boolean removeCustomBlock(org.bukkit.Location loc, boolean dropItems);

    /**
     * Registers a custom block definition along with its 3D model JSON and textures provided by an addon plugin.
     * Synthetix parses the model, extracts/slices composite parts, generates resource pack models,
     * wires modern item_model and CustomModelData overrides, and triggers pack compilation before hosting.
     *
     * @param plugin The registering addon plugin
     * @param blockDef The custom block definition
     * @param modelJson Raw Blockbench model JSON string
     * @param textures Map of texture filename (e.g. "gearbox.png") to raw byte array
     * @return CompletableFuture that completes when the block is registered and pack is compiled
     */
    java.util.concurrent.CompletableFuture<Void> registerBlockWithModel(Plugin plugin, org.bg52.synthetix.block.CustomBlockDefinition blockDef, String modelJson, java.util.Map<String, byte[]> textures);

    /**
     * Registers a custom block definition along with model JSON file and texture files from disk.
     */
    java.util.concurrent.CompletableFuture<Void> registerBlockWithModel(Plugin plugin, org.bg52.synthetix.block.CustomBlockDefinition blockDef, java.io.File modelFile, java.util.List<java.io.File> textureFiles);

    /**
     * Registers a custom item definition along with its 3D model JSON and textures provided by an addon plugin.
     */
    java.util.concurrent.CompletableFuture<Void> registerItemWithModel(Plugin plugin, ItemDefinition itemDef, String modelJson, java.util.Map<String, byte[]> textures);

    /**
     * Updates an internal state property for a custom block instance, firing SynthetixBlockStateChangeEvent
     * and automatically scheduling asynchronous chunk/database persistence.
     *
     * @param loc The world location of the custom block
     * @param key The state property key (e.g. "temperature", "rpm", "active")
     * @param value The value to store
     * @return true if updated successfully, false if the location does not hold a custom block or event was cancelled
     */
    boolean updateBlockState(org.bukkit.Location loc, String key, Object value);

    /**
     * Updates an internal state property for a custom block instance with an explicit player actor.
     */
    boolean updateBlockState(org.bukkit.Location loc, String key, Object value, Player actor);

    /**
     * Retrieves an internal state property from a custom block instance.
     */
    Object getBlockState(org.bukkit.Location loc, String key);

    /**
     * Retrieves an internal state property from a custom block instance typed.
     */
    <T> T getBlockState(org.bukkit.Location loc, String key, Class<T> type);

    /**
     * Dynamically morphs/transforms a custom block in-place into another custom block definition,
     * swapping underlying carrier block if necessary, respawning composite display entities,
     * firing SynthetixBlockTransformEvent, and updating persistence.
     *
     * @param loc The world location of the custom block
     * @param newBlockId Target block ID to transform into
     * @return true if transformed successfully
     */
    boolean transformBlock(org.bukkit.Location loc, String newBlockId);

    /**
     * Dynamically morphs/transforms a custom block in-place with an explicit player actor.
     */
    boolean transformBlock(org.bukkit.Location loc, String newBlockId, Player actor);

    /**
     * Adjusts the animation speed multiplier for an active custom block instance.
     *
     * @param loc The world location of the custom block
     * @param speedMultiplier Speed multiplier (1.0 = normal, 2.0 = 2x, -1.0 = reverse, 0.0 = stopped)
     */
    void setBlockAnimationSpeed(org.bukkit.Location loc, double speedMultiplier);

    /**
     * Pauses or unpauses animation for an active custom block instance.
     */
    void setBlockAnimationPaused(org.bukkit.Location loc, boolean paused);

    /**
     * Adjusts the animation speed multiplier for a specific composite part of a custom block.
     */
    void setPartAnimationSpeed(org.bukkit.Location loc, String partId, double speedMultiplier);

    /**
     * Pauses or unpauses animation for a specific composite part of a custom block.
     */
    void setPartAnimationPaused(org.bukkit.Location loc, String partId, boolean paused);

    /**
     * Dynamically swaps the 3D item model for a specific composite part (or "casing"/"root")
     * of a placed block on the fly without respawning entities.
     *
     * @param loc The world location of the custom block
     * @param partId Part ID or "root"/"casing"
     * @param itemModel Modern item_model NamespacedKey (e.g. "synthetix:gearbox_fast")
     * @return true if successfully swapped
     */
    boolean setPartModel(org.bukkit.Location loc, String partId, String itemModel);

    /**
     * Retrieves the universal interactive KeybindManager for registering input combinations.
     */
    org.bg52.synthetix.keybind.KeybindManager getKeybindManager();

    /**
     * Retrieves the CustomEntityRegistry managing custom entity definitions.
     */
    org.bg52.synthetix.entity.CustomEntityRegistry getCustomEntityRegistry();

    /**
     * Retrieves the CustomEntityManager managing active instances and physics/hitbox raycasts.
     */
    org.bg52.synthetix.entity.CustomEntityManager getCustomEntityManager();

    /**
     * Spawns a custom entity at the given location by its registered identifier.
     *
     * @param entityId Registered entity ID
     * @param loc World spawn location
     * @return The spawned CustomEntityInstance, or null if entity not registered
     */
    org.bg52.synthetix.entity.runtime.CustomEntityInstance spawnCustomEntity(String entityId, org.bukkit.Location loc);

    /**
     * Spawns a custom entity dynamically from a runtime CustomEntityDefinition.
     */
    org.bg52.synthetix.entity.runtime.CustomEntityInstance spawnCustomEntity(org.bg52.synthetix.entity.model.CustomEntityDefinition def, org.bukkit.Location loc);

    /**
     * Resolves the active CustomEntityInstance from a Bukkit Entity (either driver mob or interaction hitbox).
     */
    org.bg52.synthetix.entity.runtime.CustomEntityInstance getCustomEntity(org.bukkit.entity.Entity entity);

    /**
     * Resolves the active CustomEntityInstance from an entity UUID.
     */
    org.bg52.synthetix.entity.runtime.CustomEntityInstance getCustomEntity(java.util.UUID uuid);

    /**
     * Checks if a Bukkit Entity is part of a custom Synthetix entity.
     */
    boolean isCustomEntity(org.bukkit.entity.Entity entity);

    /**
     * Resolves the custom entity ID from a Bukkit Entity if present.
     */
    String getCustomEntityId(org.bukkit.entity.Entity entity);

    /**
     * Retrieves the Bedrock/Blockbench entity importer engine.
     */
    org.bg52.synthetix.entity.importer.BedrockEntityImporter getBedrockEntityImporter();

    /**
     * Imports a Bedrock/Blockbench entity model (.bbmodel or .geo.json),
     * automatically generating skeletal bones, 3D item models, textures, and animation tracks.
     */
    org.bg52.synthetix.entity.model.CustomEntityDefinition importBedrockEntity(String entityId, java.io.File modelFile);

    /**
     * Starts building a custom entity via a fluent, chained builder.
     * Supports chaining .fromBedrockModel(file), .displayName(...), .maxHealth(...), and .register().
     */
    org.bg52.synthetix.entity.CustomEntityBuilder createEntity(String entityId);

    /**
     * Plays an animation on a custom entity with default parameters.
     */
    boolean playAnimation(org.bukkit.entity.Entity entity, String animationName);

    /**
     * Plays an animation on a custom entity with loop and cross-fade parameters.
     */
    boolean playAnimation(org.bukkit.entity.Entity entity, String animationName, boolean loop, float blendTicks);

    /**
     * Stops the currently playing animation on a custom entity.
     */
    void stopAnimation(org.bukkit.entity.Entity entity);

    /**
     * Checks if a custom entity has the specified animation track.
     */
    boolean hasAnimation(org.bukkit.entity.Entity entity, String animationName);

    /**
     * Lists all animation tracks available on the custom entity.
     */
    java.util.List<String> getAvailableAnimations(org.bukkit.entity.Entity entity);

    /**
     * Gets the current high-level behavioral state of the custom entity.
     */
    org.bg52.synthetix.entity.animation.EntityState getEntityState(org.bukkit.entity.Entity entity);

    /**
     * Sets the behavioral state of the custom entity and triggers the coupled animation.
     */
    void setEntityState(org.bukkit.entity.Entity entity, org.bg52.synthetix.entity.animation.EntityState state);

    /**
     * Retrieves the Bedrock Edition (Geyser/Floodgate) compatibility manager.
     */
    org.bg52.synthetix.bedrock.BedrockManager getBedrockManager();

    /**
     * Retrieves the item modernization and update manager.
     */
    org.bg52.synthetix.item.update.ItemUpdateManager getItemUpdateManager();

    /**
     * Rebuilds the given ItemStack to modern definition and visual standards while preserving player customizations.
     */
    default ItemStack rebuildItem(ItemStack item) {
        return getItemUpdateManager() != null ? getItemUpdateManager().rebuildItem(item) : item;
    }

    /**
     * Checks if the given item stack is outdated and needs rebuilding.
     */
    default boolean isItemOutdated(ItemStack item) {
        return getItemUpdateManager() != null && getItemUpdateManager().isItemOutdated(item);
    }

    /**
     * Retrieves the legacy migration and database updater manager.
     */
    default org.bg52.synthetix.storage.migration.LegacyMigrationManager getLegacyMigrationManager() {
        return getStorageManager() != null ? getStorageManager().getMigrationManager() : null;
    }

    /**
     * Retrieves the Quick Global Settings Manager.
     */
    default org.bg52.synthetix.settings.GlobalSettingsManager getSettingsManager() {
        return null;
    }

    /**
     * Checks if a global feature toggle is currently enabled.
     */
    default boolean isFeatureEnabled(String feature) {
        return getSettingsManager() != null && getSettingsManager().isFeatureEnabled(feature);
    }

    /**
     * Sets a global feature toggle persistently.
     */
    default void setFeatureEnabled(String feature, boolean enabled) {
        if (getSettingsManager() != null) {
            getSettingsManager().setFeatureEnabled(feature, enabled);
        }
    }
}
