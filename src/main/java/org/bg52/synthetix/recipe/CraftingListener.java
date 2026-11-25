package org.bg52.synthetix.recipe;

import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.api.event.SynthetixItemCraftEvent;
import org.bg52.synthetix.api.event.SynthetixPrepareCraftEvent;
import org.bg52.synthetix.api.event.SynthetixSmeltEvent;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validates crafting matrix ingredients, enforces custom item isolation,
 * and emits high-level Synthetix crafting events.
 */
public class CraftingListener implements Listener {

    private final SynthetixPlugin plugin;

    public CraftingListener(SynthetixPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        CraftingInventory inv = event.getInventory();
        if (inv == null) return;

        if (recipe == null) {
            // Recipe is null -> ensure result slot is clear
            inv.setResult(null);
            return;
        }

        if (recipe instanceof Keyed keyed) {
            NamespacedKey key = keyed.getKey();
            if (key.getNamespace().equalsIgnoreCase("synthetix")) {
                String fullKey = key.getKey();
                String recipeId = fullKey.startsWith("recipe_") ? fullKey.substring(7) : fullKey;
                SynthetixRecipe synthRecipe = plugin.getRecipeManager().getRecipe(recipeId);

                if (synthRecipe == null || !synthRecipe.isEnabled() || !plugin.getRecipeManager().isRecipeAllowedByGlobalSettings(synthRecipe)) {
                    inv.setResult(null);
                    return;
                }

                // Strict matrix validation
                if (!matchesMatrix(synthRecipe, inv.getMatrix())) {
                    inv.setResult(null);
                    return;
                }

                ItemStack expectedResult = synthRecipe.buildResult(plugin);
                if (expectedResult == null || expectedResult.getType().isAir() || expectedResult.getType() == Material.BARRIER) {
                    inv.setResult(null);
                    return;
                }

                Player player = event.getView().getPlayer() instanceof Player p ? p : null;
                if (player != null) {
                    SynthetixPrepareCraftEvent prepareEvent = new SynthetixPrepareCraftEvent(
                            player, synthRecipe, inv.getMatrix(), expectedResult
                    );
                    Bukkit.getPluginManager().callEvent(prepareEvent);
                    if (prepareEvent.isCancelled()) {
                        inv.setResult(null);
                        return;
                    }
                    inv.setResult(prepareEvent.getResult());
                } else {
                    inv.setResult(expectedResult);
                }
                return;
            }
        }

        // Vanilla or foreign recipe: prevent custom Synthetix items from being accidentally consumed
        for (ItemStack item : inv.getMatrix()) {
            if (item != null && item.getType() != Material.AIR) {
                if (plugin.getItemRegistry().getItemId(item) != null) {
                    inv.setResult(null);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onCraftItem(CraftItemEvent event) {
        Recipe recipe = event.getRecipe();
        if (recipe instanceof Keyed keyed && keyed.getKey().getNamespace().equalsIgnoreCase("synthetix")) {
            String fullKey = keyed.getKey().getKey();
            String recipeId = fullKey.startsWith("recipe_") ? fullKey.substring(7) : fullKey;
            SynthetixRecipe synthRecipe = plugin.getRecipeManager().getRecipe(recipeId);

            if (synthRecipe != null && event.getWhoClicked() instanceof Player player) {
                if (!synthRecipe.isEnabled() || !plugin.getRecipeManager().isRecipeAllowedByGlobalSettings(synthRecipe)) {
                    event.setCancelled(true);
                    return;
                }

                ItemStack result = event.getInventory().getResult();
                if (result == null || result.getType().isAir() || result.getType() == Material.BARRIER) {
                    event.setCancelled(true);
                    return;
                }

                SynthetixItemCraftEvent craftEvent = new SynthetixItemCraftEvent(
                        player, synthRecipe, result,
                        event.getClick(), event.isShiftClick()
                );
                Bukkit.getPluginManager().callEvent(craftEvent);
                if (craftEvent.isCancelled()) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        ItemStack source = event.getSource();
        ItemStack result = event.getResult();
        String sourceId = plugin.getItemRegistry().getItemId(source);
        String resultId = plugin.getItemRegistry().getItemId(result);

        if (sourceId != null || resultId != null) {
            SynthetixRecipe matchingRecipe = null;
            if (resultId != null) {
                for (SynthetixRecipe r : plugin.getRecipeManager().getRecipesFor(resultId)) {
                    if (r.getCookingInput() != null && r.getCookingInput().test(source, plugin)) {
                        matchingRecipe = r;
                        break;
                    }
                }
            }

            if (matchingRecipe != null && (!matchingRecipe.isEnabled() || !plugin.getRecipeManager().isRecipeAllowedByGlobalSettings(matchingRecipe))) {
                event.setCancelled(true);
                return;
            }

            SynthetixSmeltEvent smeltEvent = new SynthetixSmeltEvent(event.getBlock(), source, result, matchingRecipe);
            Bukkit.getPluginManager().callEvent(smeltEvent);
            if (smeltEvent.isCancelled()) {
                event.setCancelled(true);
            } else {
                event.setResult(smeltEvent.getResult());
            }
        }
    }

    private boolean matchesMatrix(SynthetixRecipe recipe, ItemStack[] matrix) {
        if (matrix == null || matrix.length == 0) return false;
        int gridDim = (int) Math.round(Math.sqrt(matrix.length));

        if (recipe.getType() == RecipeType.SHAPELESS) {
            List<IngredientChoice> reqs = recipe.getShapelessIngredients();
            if (reqs == null || reqs.isEmpty()) return false;

            List<ItemStack> nonAirItems = new ArrayList<>();
            for (ItemStack item : matrix) {
                if (item != null && item.getType() != Material.AIR) {
                    nonAirItems.add(item);
                }
            }
            if (nonAirItems.size() != reqs.size()) return false;

            boolean[] matched = new boolean[reqs.size()];
            for (ItemStack item : nonAirItems) {
                boolean found = false;
                for (int i = 0; i < reqs.size(); i++) {
                    if (!matched[i] && reqs.get(i).test(item, plugin)) {
                        matched[i] = true;
                        found = true;
                        break;
                    }
                }
                if (!found) return false;
            }
            return true;
        }

        if (recipe.getType() == RecipeType.SHAPED) {
            List<String> shape = recipe.getShape();
            if (shape == null || shape.isEmpty()) return false;

            int shapeRows = shape.size();
            int shapeCols = shape.get(0).length();

            if (shapeRows > gridDim || shapeCols > gridDim) return false;

            Map<Character, IngredientChoice> map = recipe.getShapedIngredients();

            for (int startR = 0; startR <= gridDim - shapeRows; startR++) {
                for (int startC = 0; startC <= gridDim - shapeCols; startC++) {
                    if (matchesShapedAt(shape, map, matrix, gridDim, startR, startC)) {
                        return true;
                    }
                }
            }
            return false;
        }

        return true;
    }

    private boolean matchesShapedAt(List<String> shape, Map<Character, IngredientChoice> map,
                                    ItemStack[] matrix, int gridDim, int startR, int startC) {
        int shapeRows = shape.size();
        int shapeCols = shape.get(0).length();

        for (int r = 0; r < gridDim; r++) {
            for (int c = 0; c < gridDim; c++) {
                ItemStack item = matrix[r * gridDim + c];
                boolean insideShape = (r >= startR && r < startR + shapeRows && c >= startC && c < startC + shapeCols);

                if (insideShape) {
                    char ch = shape.get(r - startR).charAt(c - startC);
                    if (ch == ' ') {
                        if (item != null && item.getType() != Material.AIR) return false;
                    } else {
                        IngredientChoice choice = map != null ? map.get(ch) : null;
                        if (choice == null || !choice.test(item, plugin)) return false;
                    }
                } else {
                    if (item != null && item.getType() != Material.AIR) return false;
                }
            }
        }
        return true;
    }
}

