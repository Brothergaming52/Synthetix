package org.bg52.synthetix;

import org.bg52.synthetix.api.SynthetixAPI;
import org.bg52.synthetix.api.SynthetixAPIImpl;
import org.bg52.synthetix.api.editor.ItemEditorRegistry;
import org.bg52.synthetix.command.SynthCommand;
import org.bg52.synthetix.distribution.DistributionManager;
import org.bg52.synthetix.gui.CanvasListener;
import org.bg52.synthetix.gui.editor.category.*;
import org.bg52.synthetix.gui.prompt.ChatPromptManager;
import org.bg52.synthetix.item.ItemInteractListener;
import org.bg52.synthetix.item.ItemRegistry;
import org.bg52.synthetix.lang.LanguageManager;
import org.bg52.synthetix.recipe.RecipeManager;
import org.bg52.synthetix.resourcepack.PackPromptListener;
import org.bg52.synthetix.resourcepack.ResourcePackManager;
import org.bg52.synthetix.storage.StorageManager;
import org.bg52.synthetix.util.VersionChecker;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Synthetix (synth) - Core Foundation Plugin for Paper 1.20.6 through 26.3+.
 */
public final class SynthetixPlugin extends JavaPlugin {

    private static SynthetixPlugin instance;
    private LanguageManager languageManager;
    private StorageManager storageManager;
    private SynthetixAPI api;
    private ItemRegistry itemRegistry;
    private ItemEditorRegistry editorRegistry;
    private ChatPromptManager chatPromptManager;
    private RecipeManager recipeManager;
    private DistributionManager distributionManager;
    private ResourcePackManager resourcePackManager;
    private org.bg52.synthetix.ability.TriggerManager triggerManager;
    private org.bg52.synthetix.set.SetManager setManager;
    private org.bg52.synthetix.ability.AuraManager auraManager;
    private org.bg52.synthetix.bundle.PackExportManager exportManager;
    private org.bg52.synthetix.bundle.PackImportManager importManager;
    private org.bg52.synthetix.block.CustomBlockRegistry customBlockRegistry;
    private org.bg52.synthetix.block.CustomBlockManager customBlockManager;
    private org.bg52.synthetix.block.BlockbenchImporter blockbenchImporter;
    private org.bg52.synthetix.entity.importer.BedrockEntityImporter bedrockEntityImporter;
    private org.bg52.synthetix.entity.CustomEntityRegistry customEntityRegistry;
    private org.bg52.synthetix.entity.CustomEntityManager customEntityManager;
    private org.bg52.synthetix.keybind.KeybindManager keybindManager;
    private org.bg52.synthetix.bedrock.BedrockManager bedrockManager;
    private org.bg52.synthetix.gui.bedrock.BedrockAnvilGUI bedrockAnvilGUI;
    private org.bg52.synthetix.gui.bedrock.BedrockSmithingGUI bedrockSmithingGUI;
    private org.bg52.synthetix.item.update.ItemUpdateManager itemUpdateManager;
    private org.bg52.synthetix.settings.GlobalSettingsManager settingsManager;

    @Override
    public void onEnable() {
        instance = this;

        // Initialize unified Folia/Paper cross-platform scheduler
        new org.bg52.synthetix.scheduler.PlatformScheduler(this);

        // Save default configuration and language assets
        saveDefaultConfig();

        // 1. Initialize Localization Engine first so all subsystems can fetch messages
        this.languageManager = new LanguageManager(this);

        getLogger().info("Initializing Synthetix Core Engine...");
        getLogger().info("Target Server Version: " + VersionChecker.getFullVersionString()
                + " | DataComponents: " + (VersionChecker.supportsDataComponents() ? "Active" : "Inactive")
                + " | Modern Era: " + (VersionChecker.isPaperModernEra() ? "Yes" : "No"));

        // 2. Initialize Enterprise Storage Engine
        this.storageManager = new StorageManager(this);
        this.storageManager.initialize();

        // 2b. Initialize Quick Global Settings Manager
        this.settingsManager = new org.bg52.synthetix.settings.GlobalSettingsManager(this);
        this.settingsManager.initialize();

        // 3. Initialize Item Registry
        this.itemRegistry = new ItemRegistry(this);
        this.itemRegistry.loadAll();

        // 4. Initialize Universal Recipe Engine
        this.recipeManager = new RecipeManager(this);
        this.recipeManager.initialize();

        // 5. Initialize World Distribution Engine (Mob Drops, Loot Tables, Trades)
        this.distributionManager = new DistributionManager(this);
        this.distributionManager.initialize();

        // 6. Initialize Unified Resource Pack Engine (combining, auto IP detection, self & plugin hosting)
        this.resourcePackManager = new ResourcePackManager(this);
        this.resourcePackManager.initialize();

        // 7. Initialize Editor Category Registry and register built-in Core categories
        this.editorRegistry = new ItemEditorRegistry();
        this.editorRegistry.registerCategory(new IdentityCategory(this));
        this.editorRegistry.registerCategory(new ComponentsCategory(this));
        this.editorRegistry.registerCategory(new FoodCategory(this));
        this.editorRegistry.registerCategory(new AttributesCategory(this));
        this.editorRegistry.registerCategory(new EnchantmentsCategory(this));
        this.editorRegistry.registerCategory(new AbilitiesCategory(this));
        this.editorRegistry.registerCategory(new TagsCategory(this));
        this.editorRegistry.registerCategory(new RecipesCategory(this));
        this.editorRegistry.registerCategory(new DistributionCategory(this));

        // 8. Initialize Interactive Ability & Trigger Engine
        this.triggerManager = new org.bg52.synthetix.ability.TriggerManager(this);
        getServer().getPluginManager().registerEvents(triggerManager, this);

        // 9. Initialize Continuous Ambient Particle Aura Engine
        this.auraManager = new org.bg52.synthetix.ability.AuraManager(this);
        this.auraManager.start();

        // 10. Initialize Equipment Sets & Tier Bonus Engine
        this.setManager = new org.bg52.synthetix.set.SetManager(this);
        this.setManager.loadAll();
        this.setManager.start();

        // 11. Initialize Bundle Packaging Engine (.synthpack export & auto-import)
        this.exportManager = new org.bg52.synthetix.bundle.PackExportManager(this);
        this.importManager = new org.bg52.synthetix.bundle.PackImportManager(this);
        this.importManager.scanAndImportAll();

        // 12. Initialize Dual-Layer Custom Block Engine & Blockbench Auto-Importer
        this.customBlockRegistry = new org.bg52.synthetix.block.CustomBlockRegistry(this);
        this.customBlockRegistry.loadAll();
        this.customBlockManager = new org.bg52.synthetix.block.CustomBlockManager(this);
        this.customBlockManager.initialize();
        this.blockbenchImporter = new org.bg52.synthetix.block.BlockbenchImporter(this);
        this.blockbenchImporter.scanAndImportAll();
        getServer().getPluginManager().registerEvents(new org.bg52.synthetix.block.BlockWorldGenListener(this), this);

        // 12b. Initialize Tri-Layer Virtual Custom Entity Engine & Bedrock Importer
        this.bedrockEntityImporter = new org.bg52.synthetix.entity.importer.BedrockEntityImporter(this);
        this.bedrockEntityImporter.importPendingEntities(true);
        this.customEntityRegistry = new org.bg52.synthetix.entity.CustomEntityRegistry(this);
        this.customEntityRegistry.loadAll();
        this.customEntityManager = new org.bg52.synthetix.entity.CustomEntityManager(this);
        this.customEntityManager.initialize();

        // 11c. Initialize Bedrock / Geyser Protocol & Visual Proxy Engine
        this.bedrockManager = new org.bg52.synthetix.bedrock.BedrockManager(this);
        this.bedrockManager.initialize();

        // 12. Initialize Chat Prompt Manager, GUI listener, Item Interaction listener & Resource Pack prompt listener
        this.chatPromptManager = new ChatPromptManager(this);
        getServer().getPluginManager().registerEvents(chatPromptManager, this);
        getServer().getPluginManager().registerEvents(new CanvasListener(), this);
        getServer().getPluginManager().registerEvents(new ItemInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new PackPromptListener(this), this);

        // 13. Initialize Universal Keybind Engine
        this.keybindManager = new org.bg52.synthetix.keybind.KeybindManager(this);
        getServer().getPluginManager().registerEvents(new org.bg52.synthetix.keybind.KeybindListener(this.keybindManager), this);

        // 14. Initialize Bedrock/Geyser Compatibility GUIs and Listener
        this.bedrockAnvilGUI = new org.bg52.synthetix.gui.bedrock.BedrockAnvilGUI(this);
        this.bedrockSmithingGUI = new org.bg52.synthetix.gui.bedrock.BedrockSmithingGUI(this);
        getServer().getPluginManager().registerEvents(this.bedrockAnvilGUI, this);
        getServer().getPluginManager().registerEvents(this.bedrockSmithingGUI, this);
        getServer().getPluginManager().registerEvents(new org.bg52.synthetix.listener.BedrockContainerListener(this, this.bedrockAnvilGUI, this.bedrockSmithingGUI), this);

        // 14b. Initialize Generalized Item Modernization & Rebuilding Engine
        this.itemUpdateManager = new org.bg52.synthetix.item.update.ItemUpdateManager(this);
        getServer().getPluginManager().registerEvents(new org.bg52.synthetix.item.update.ItemUpdateListener(this, this.itemUpdateManager), this);

        // 15. Register Public API Service
        this.api = new SynthetixAPIImpl(this);
        getServer().getServicesManager().register(SynthetixAPI.class, this.api, this, ServicePriority.Normal);

        // 16. Register Commands
        PluginCommand cmd = getCommand("synth");
        if (cmd != null) {
            SynthCommand executor = new SynthCommand(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        int pluginId = 35485;
        Metrics metrics = new Metrics(this, pluginId);

        getLogger().info("Synthetix v" + getDescription().getVersion() + " successfully enabled!");
    }

    @Override
    public void onDisable() {
        // Unregister service
        getServer().getServicesManager().unregisterAll(this);
        org.bg52.synthetix.scheduler.PlatformScheduler.get().cancelTasks();

        // Stop continuous auras and equipment sets
        if (auraManager != null) {
            auraManager.stop();
        }
        if (setManager != null) {
            setManager.stop();
            setManager.saveAll();
        }

        // Unregister recipes
        if (recipeManager != null) {
            recipeManager.unregisterAll();
        }

        // Shutdown Resource Pack hosting engine
        if (resourcePackManager != null) {
            resourcePackManager.shutdown();
        }

        // Shutdown Custom Block Engine (persist active placed blocks)
        if (customBlockManager != null) {
            customBlockManager.shutdown();
        }

        // Shutdown Custom Entity Engine (cleanly despawn virtual bones & hitboxes)
        if (customEntityManager != null) {
            customEntityManager.shutdown();
        }
        if (customEntityRegistry != null) {
            customEntityRegistry.saveAll();
        }

        // Shutdown Bedrock Compatibility Layer
        if (bedrockManager != null) {
            bedrockManager.shutdown();
        }

        // Shutdown Keybind Engine
        if (keybindManager != null) {
            keybindManager.shutdown();
        }

        // Shutdown Storage Engine safely (flush pending writes and disconnect pools)
        if (storageManager != null) {
            storageManager.shutdown();
        }

        getLogger().info("Synthetix Core Engine disabled cleanly.");
    }

    public void reloadPluginConfig() {
        reloadConfig();
        if (settingsManager != null) {
            settingsManager.reload();
        }
        if (languageManager != null) {
            languageManager.load();
        }
        if (itemRegistry != null) {
            itemRegistry.loadAll();
        }
        if (recipeManager != null) {
            recipeManager.reload();
        }
        if (distributionManager != null) {
            distributionManager.reload();
        }
        if (setManager != null) {
            setManager.loadAll();
        }
        if (resourcePackManager != null) {
            resourcePackManager.reload();
        }
        if (customBlockRegistry != null) {
            customBlockRegistry.loadAll();
        }
        if (customBlockManager != null) {
            customBlockManager.reloadConfig();
        }
        if (customEntityRegistry != null) {
            customEntityRegistry.loadAll();
        }
    }

    public static SynthetixPlugin getInstance() {
        return instance;
    }

    public static SynthetixPlugin get() {
        return instance;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public StorageManager getStorageManager() {
        return storageManager;
    }

    public SynthetixAPI getApi() {
        return api;
    }

    public ItemRegistry getItemRegistry() {
        return itemRegistry;
    }

    public ItemEditorRegistry getEditorRegistry() {
        return editorRegistry;
    }

    public ChatPromptManager getChatPromptManager() {
        return chatPromptManager;
    }

    public RecipeManager getRecipeManager() {
        return recipeManager;
    }

    public DistributionManager getDistributionManager() {
        return distributionManager;
    }

    public ResourcePackManager getResourcePackManager() {
        return resourcePackManager;
    }

    public org.bg52.synthetix.ability.TriggerManager getTriggerManager() {
        return triggerManager;
    }

    public org.bg52.synthetix.set.SetManager getSetManager() {
        return setManager;
    }

    public org.bg52.synthetix.ability.AuraManager getAuraManager() {
        return auraManager;
    }

    public org.bg52.synthetix.bundle.PackExportManager getExportManager() {
        return exportManager;
    }

    public org.bg52.synthetix.bundle.PackImportManager getImportManager() {
        return importManager;
    }

    public org.bg52.synthetix.block.CustomBlockRegistry getCustomBlockRegistry() {
        return customBlockRegistry;
    }

    public org.bg52.synthetix.block.CustomBlockManager getCustomBlockManager() {
        return customBlockManager;
    }

    public org.bg52.synthetix.block.BlockbenchImporter getBlockbenchImporter() {
        return blockbenchImporter;
    }

    public org.bg52.synthetix.entity.importer.BedrockEntityImporter getBedrockEntityImporter() {
        return bedrockEntityImporter;
    }

    public org.bg52.synthetix.entity.CustomEntityRegistry getCustomEntityRegistry() {
        return customEntityRegistry;
    }

    public org.bg52.synthetix.entity.CustomEntityManager getCustomEntityManager() {
        return customEntityManager;
    }

    public org.bg52.synthetix.keybind.KeybindManager getKeybindManager() {
        return keybindManager;
    }

    public org.bg52.synthetix.bedrock.BedrockManager getBedrockManager() {
        return bedrockManager;
    }

    public org.bg52.synthetix.gui.bedrock.BedrockAnvilGUI getBedrockAnvilGUI() {
        return bedrockAnvilGUI;
    }

    public org.bg52.synthetix.gui.bedrock.BedrockSmithingGUI getBedrockSmithingGUI() {
        return bedrockSmithingGUI;
    }

    public org.bg52.synthetix.item.update.ItemUpdateManager getItemUpdateManager() {
        return itemUpdateManager;
    }

    public org.bg52.synthetix.settings.GlobalSettingsManager getSettingsManager() {
        return settingsManager;
    }
}
