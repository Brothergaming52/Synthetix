package org.bg52.synthetix.recipe;

import org.bg52.synthetix.SynthetixPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.*;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Orchestrates recipe loading, Bukkit recipe registry management, and custom event handlers.
 */
public class RecipeManager {

    private final SynthetixPlugin plugin;
    private final Map<String, SynthetixRecipe> recipes = new ConcurrentHashMap<>();
    private final Set<NamespacedKey> registeredKeys = ConcurrentHashMap.newKeySet();

    public RecipeManager(SynthetixPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        Bukkit.getPluginManager().registerEvents(new CraftingListener(plugin), plugin);
        Bukkit.getPluginManager().registerEvents(new AnvilRecipeListener(plugin, this), plugin);
        Bukkit.getPluginManager().registerEvents(new BrewingRecipeListener(plugin, this), plugin);
        loadAll();
    }

    public synchronized void reload() {
        unregisterAll();
        recipes.clear();
        loadAll();
    }

    public synchronized void unregisterAll() {
        for (NamespacedKey key : registeredKeys) {
            try {
                Bukkit.removeRecipe(key);
            } catch (Throwable ignored) {
            }
        }
        registeredKeys.clear();
    }

    public void loadAll() {
        File folder = new File(plugin.getDataFolder(), "recipes");
        if (!folder.exists()) {
            folder.mkdirs();
            generateDefaultFiles(folder);
        }

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null || files.length == 0) {
            generateDefaultFiles(folder);
            files = folder.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        }

        if (files != null) {
            for (File file : files) {
                loadFile(file);
            }
        }

        plugin.getLogger().info("Loaded and registered " + recipes.size() + " custom recipes.");
    }

    private void loadFile(File file) {
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            for (String key : config.getKeys(false)) {
                SynthetixRecipe recipe = SynthetixRecipe.loadFromConfig(key, config.getConfigurationSection(key));
                if (recipe != null) {
                    recipes.put(recipe.getId().toLowerCase(Locale.ROOT), recipe);
                    registerToBukkit(recipe);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load recipe file: " + file.getName(), e);
        }
    }

    private void registerToBukkit(SynthetixRecipe recipe) {
        if (!recipe.isEnabled() || !isRecipeAllowedByGlobalSettings(recipe)) {
            return;
        }

        NamespacedKey key = recipe.toNamespacedKey(plugin);
        ItemStack result = recipe.buildResult(plugin);

        if (result == null || result.getType().isAir()) {
            plugin.getLogger().warning("Skipping Bukkit registration for recipe '" + recipe.getId()
                    + "': result item (" + (recipe.getResultItemId() != null ? recipe.getResultItemId() : recipe.getResultMaterial())
                    + ") cannot be resolved.");
            return;
        }

        if (result.getType() == Material.BARRIER) {
            String resId = recipe.getResultItemId();
            boolean knownItem = resId != null && ((plugin.getItemRegistry() != null && plugin.getItemRegistry().has(resId))
                    || (plugin.getCustomBlockRegistry() != null && plugin.getCustomBlockRegistry().has(resId)));
            if (!knownItem) {
                plugin.getLogger().warning("Skipping Bukkit registration for recipe '" + recipe.getId()
                        + "': result item (" + (resId != null ? resId : recipe.getResultMaterial())
                        + ") cannot be resolved.");
                return;
            }
        }

        try {
            // Unregister pre-existing recipe with this key to avoid duplicate registration error
            try {
                Bukkit.removeRecipe(key);
            } catch (Throwable ignored) {
            }
            switch (recipe.getType()) {
                case SHAPED -> {
                    if (recipe.getShape() == null || recipe.getShape().isEmpty()) return;
                    ShapedRecipe shaped = new ShapedRecipe(key, result);
                    shaped.shape(recipe.getShape().toArray(new String[0]));
                    if (recipe.getShapedIngredients() != null) {
                        for (Map.Entry<Character, IngredientChoice> entry : recipe.getShapedIngredients().entrySet()) {
                            RecipeChoice rc = entry.getValue().toBukkitRecipeChoice(plugin);
                            if (rc == null) {
                                plugin.getLogger().warning("Skipping shaped recipe '" + recipe.getId()
                                        + "': ingredient '" + entry.getKey() + "' (" + entry.getValue().getRawInput() + ") is unresolved.");
                                return;
                            }
                            shaped.setIngredient(entry.getKey(), rc);
                        }
                    }
                    Bukkit.addRecipe(shaped);
                    registeredKeys.add(key);
                }
                case SHAPELESS -> {
                    if (recipe.getShapelessIngredients() == null || recipe.getShapelessIngredients().isEmpty()) return;
                    ShapelessRecipe shapeless = new ShapelessRecipe(key, result);
                    for (IngredientChoice choice : recipe.getShapelessIngredients()) {
                        RecipeChoice rc = choice.toBukkitRecipeChoice(plugin);
                        if (rc == null) {
                            plugin.getLogger().warning("Skipping shapeless recipe '" + recipe.getId()
                                    + "': ingredient (" + choice.getRawInput() + ") is unresolved.");
                            return;
                        }
                        shapeless.addIngredient(rc);
                    }
                    Bukkit.addRecipe(shapeless);
                    registeredKeys.add(key);
                }
                case FURNACE -> {
                    if (recipe.getCookingInput() == null) return;
                    RecipeChoice rc = recipe.getCookingInput().toBukkitRecipeChoice(plugin);
                    if (rc == null) return;
                    FurnaceRecipe furnace = new FurnaceRecipe(key, result, rc, recipe.getExperience(), recipe.getCookingTime());
                    Bukkit.addRecipe(furnace);
                    registeredKeys.add(key);
                }
                case BLAST_FURNACE -> {
                    if (recipe.getCookingInput() == null) return;
                    RecipeChoice rc = recipe.getCookingInput().toBukkitRecipeChoice(plugin);
                    if (rc == null) return;
                    BlastingRecipe blasting = new BlastingRecipe(key, result, rc, recipe.getExperience(), recipe.getCookingTime());
                    Bukkit.addRecipe(blasting);
                    registeredKeys.add(key);
                }
                case SMOKER -> {
                    if (recipe.getCookingInput() == null) return;
                    RecipeChoice rc = recipe.getCookingInput().toBukkitRecipeChoice(plugin);
                    if (rc == null) return;
                    SmokingRecipe smoking = new SmokingRecipe(key, result, rc, recipe.getExperience(), recipe.getCookingTime());
                    Bukkit.addRecipe(smoking);
                    registeredKeys.add(key);
                }
                case CAMPFIRE -> {
                    if (recipe.getCookingInput() == null) return;
                    RecipeChoice rc = recipe.getCookingInput().toBukkitRecipeChoice(plugin);
                    if (rc == null) return;
                    CampfireRecipe campfire = new CampfireRecipe(key, result, rc, recipe.getExperience(), recipe.getCookingTime());
                    Bukkit.addRecipe(campfire);
                    registeredKeys.add(key);
                }
                case STONECUTTER -> {
                    if (recipe.getCookingInput() == null) return;
                    RecipeChoice rc = recipe.getCookingInput().toBukkitRecipeChoice(plugin);
                    if (rc == null) return;
                    StonecuttingRecipe stonecutter = new StonecuttingRecipe(key, result, rc);
                    Bukkit.addRecipe(stonecutter);
                    registeredKeys.add(key);
                }
                case SMITHING_TRANSFORM -> {
                    if (recipe.getSmithingTemplate() == null || recipe.getSmithingBase() == null || recipe.getSmithingAddition() == null) return;
                    RecipeChoice tmpl = recipe.getSmithingTemplate().toBukkitRecipeChoice(plugin);
                    RecipeChoice base = recipe.getSmithingBase().toBukkitRecipeChoice(plugin);
                    RecipeChoice add = recipe.getSmithingAddition().toBukkitRecipeChoice(plugin);
                    if (tmpl == null || base == null || add == null) return;
                    SmithingTransformRecipe smithing = new SmithingTransformRecipe(key, result, tmpl, base, add);
                    Bukkit.addRecipe(smithing);
                    registeredKeys.add(key);
                }
                case ANVIL, BREWING -> {
                    // Handled dynamically via registered event listeners!
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Could not register Bukkit recipe for '" + recipe.getId() + "': " + t.getMessage());
        }
    }

    public List<SynthetixRecipe> getRecipesFor(String itemId) {
        List<SynthetixRecipe> list = new ArrayList<>();
        if (itemId == null) return list;
        String cleanId = itemId.toLowerCase(Locale.ROOT);
        if (cleanId.startsWith("synth:")) {
            cleanId = cleanId.substring(6);
        }
        for (SynthetixRecipe r : recipes.values()) {
            if (cleanId.equalsIgnoreCase(r.getResultItemId())) {
                list.add(r);
            }
        }
        return list;
    }

    public List<SynthetixRecipe> getRecipesByType(RecipeType type) {
        List<SynthetixRecipe> list = new ArrayList<>();
        for (SynthetixRecipe r : recipes.values()) {
            if (r.getType() == type) {
                list.add(r);
            }
        }
        return list;
    }

    public Map<String, SynthetixRecipe> getRecipes() {
        return Collections.unmodifiableMap(recipes);
    }

    public Collection<SynthetixRecipe> getAllRecipes() {
        return Collections.unmodifiableCollection(recipes.values());
    }

    public SynthetixRecipe getRecipe(String id) {
        if (id == null) return null;
        SynthetixRecipe r = recipes.get(id.toLowerCase(Locale.ROOT));
        if (r != null) return r;
        if (id.startsWith("recipe_")) {
            return recipes.get(id.substring(7).toLowerCase(Locale.ROOT));
        } else {
            return recipes.get(("recipe_" + id).toLowerCase(Locale.ROOT));
        }
    }

    public boolean hasRecipe(String id) {
        if (id == null) return false;
        return recipes.containsKey(id.toLowerCase(Locale.ROOT));
    }

    /**
     * Registers a recipe in memory and with Bukkit. Optionally persists to disk.
     */
    public synchronized void registerRecipe(SynthetixRecipe recipe, boolean saveToDisk) {
        if (recipe == null || recipe.getId() == null) return;
        if (saveToDisk) {
            try {
                saveRecipe(recipe);
                return;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save recipe to disk: " + recipe.getId(), e);
            }
        }
        recipes.put(recipe.getId().toLowerCase(Locale.ROOT), recipe);
        registerToBukkit(recipe);
    }

    /**
     * Generates a stable, deterministic, persistent recipe ID for an item and recipe type.
     * Follows the format: <itemId>_<recipeType>[_variant<N>]
     * Example: 'ruby_sword_shaped' (first shaped recipe for 'ruby_sword'), 'ruby_sword_shaped_variant2' (second)
     */
    public synchronized String generateRecipeId(String itemId, RecipeType type) {
        String cleanItemId = itemId != null ? itemId.toLowerCase(Locale.ROOT) : "item";
        if (cleanItemId.startsWith("synth:")) {
            cleanItemId = cleanItemId.substring(6);
        }
        String safeItemId = SynthetixRecipe.sanitizeKey(cleanItemId);
        String typeSuffix = type != null ? type.name().toLowerCase(Locale.ROOT) : "recipe";
        String baseId = safeItemId + "_" + typeSuffix;

        if (!recipes.containsKey(baseId)) {
            return baseId;
        }

        int variant = 2;
        while (recipes.containsKey(baseId + "_variant" + variant)) {
            variant++;
        }
        return baseId + "_variant" + variant;
    }

    /**
     * Saves a recipe to its corresponding category YAML file and reloads the live recipe registry.
     */
    public synchronized void saveRecipe(SynthetixRecipe recipe) throws Exception {
        String filename = switch (recipe.getType()) {
            case SHAPED, SHAPELESS -> "crafting.yml";
            case FURNACE, BLAST_FURNACE, SMOKER, CAMPFIRE -> "cooking.yml";
            case STONECUTTER -> "stonecutter.yml";
            case SMITHING_TRANSFORM -> "smithing.yml";
            case ANVIL -> "anvil.yml";
            case BREWING -> "brewing.yml";
        };
        File file = new File(plugin.getDataFolder(), "recipes/" + filename);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = config.getConfigurationSection(recipe.getId());
        if (sec == null) {
            sec = config.createSection(recipe.getId());
        }
        recipe.saveToConfig(sec);
        config.save(file);
        reload();
    }

    /**
     * Toggles or sets the enabled status of a recipe, updates its configuration on disk, and reloads registry.
     */
    public synchronized boolean setRecipeEnabled(String recipeId, boolean enabled) throws Exception {
        SynthetixRecipe recipe = getRecipe(recipeId);
        if (recipe == null) return false;
        recipe.setEnabled(enabled);
        saveRecipe(recipe);
        return true;
    }

    /**
     * Re-evaluates live Bukkit recipe registrations when global settings are modified.
     */
    public synchronized void onGlobalSettingsChanged() {
        unregisterAll();
        for (SynthetixRecipe recipe : recipes.values()) {
            if (recipe.isEnabled() && isRecipeAllowedByGlobalSettings(recipe)) {
                registerToBukkit(recipe);
            }
        }
    }

    /**
     * Checks if a recipe is permitted by the global feature settings.
     */
    public boolean isRecipeAllowedByGlobalSettings(SynthetixRecipe recipe) {
        if (recipe == null) return false;
        if (plugin.getSettingsManager() == null) return true;

        if (!plugin.getSettingsManager().isFeatureEnabled(org.bg52.synthetix.settings.GlobalFeature.RECIPES_ALL)) {
            return false;
        }

        String resId = recipe.getResultItemId();
        if (resId != null) {
            boolean isBlock = plugin.getCustomBlockRegistry() != null && plugin.getCustomBlockRegistry().has(resId);
            if (isBlock && !plugin.getSettingsManager().isFeatureEnabled(org.bg52.synthetix.settings.GlobalFeature.RECIPES_BLOCKS)) {
                return false;
            }

            boolean isItem = plugin.getItemRegistry() != null && plugin.getItemRegistry().has(resId);
            if (isItem && !plugin.getSettingsManager().isFeatureEnabled(org.bg52.synthetix.settings.GlobalFeature.RECIPES_ITEMS)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Removes a recipe by ID from all recipe YAML configuration files and reloads the live registry.
     */
    public synchronized boolean deleteRecipe(String recipeId) throws Exception {
        File folder = new File(plugin.getDataFolder(), "recipes");
        if (!folder.exists()) return false;
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null) return false;
        boolean found = false;
        for (File f : files) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(f);
            if (config.contains(recipeId)) {
                config.set(recipeId, null);
                config.save(f);
                found = true;
                break;
            }
        }
        if (found) {
            String safeKey = SynthetixRecipe.sanitizeKey(recipeId);
            if (!safeKey.startsWith("recipe_")) {
                safeKey = "recipe_" + safeKey;
            }
            NamespacedKey key = new NamespacedKey(plugin, safeKey);
            try {
                Bukkit.removeRecipe(key);
            } catch (Throwable ignored) {
            }
            for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                try {
                    p.undiscoverRecipe(key);
                } catch (Throwable ignored) {
                }
            }
            reload();
        }
        return found;
    }

    private void generateDefaultFiles(File folder) {
        saveResourceIfNotExists("recipes/crafting.yml");
        saveResourceIfNotExists("recipes/cooking.yml");
        saveResourceIfNotExists("recipes/anvil.yml");
        saveResourceIfNotExists("recipes/brewing.yml");
        saveResourceIfNotExists("recipes/stonecutter.yml");
        saveResourceIfNotExists("recipes/smithing.yml");
    }

    private void saveResourceIfNotExists(String resourcePath) {
        File target = new File(plugin.getDataFolder(), resourcePath);
        if (!target.exists()) {
            plugin.saveResource(resourcePath, false);
        }
    }
}

