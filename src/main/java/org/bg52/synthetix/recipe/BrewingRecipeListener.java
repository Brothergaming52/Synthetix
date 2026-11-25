package org.bg52.synthetix.recipe;

import org.bg52.synthetix.SynthetixPlugin;
import org.bukkit.Material;
import org.bukkit.block.BrewingStand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Handles custom Brewing Stand reagent and bottle transformations.
 */
public class BrewingRecipeListener implements Listener {

    private final SynthetixPlugin plugin;
    private final RecipeManager recipeManager;

    public BrewingRecipeListener(SynthetixPlugin plugin, RecipeManager recipeManager) {
        this.plugin = plugin;
        this.recipeManager = recipeManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        BrewerInventory inv = event.getContents();
        ItemStack reagent = inv.getIngredient();
        if (reagent == null || reagent.getType() == Material.AIR) return;

        List<SynthetixRecipe> brewingRecipes = recipeManager.getRecipesByType(RecipeType.BREWING);
        if (brewingRecipes.isEmpty()) return;

        for (SynthetixRecipe recipe : brewingRecipes) {
            if (!recipe.isEnabled() || !recipeManager.isRecipeAllowedByGlobalSettings(recipe)) continue;
            if (recipe.getBrewingIngredient() == null || recipe.getBrewingBase() == null) continue;

            if (recipe.getBrewingIngredient().test(reagent, plugin)) {
                boolean anyTransformed = false;
                for (int slot = 0; slot < 3; slot++) {
                    ItemStack bottle = inv.getItem(slot);
                    if (bottle != null && bottle.getType() != Material.AIR) {
                        if (recipe.getBrewingBase().test(bottle, plugin)) {
                            inv.setItem(slot, recipe.buildResult(plugin));
                            anyTransformed = true;
                        }
                    }
                }

                if (anyTransformed) {
                    // Consume 1 reagent
                    if (reagent.getAmount() > 1) {
                        reagent.setAmount(reagent.getAmount() - 1);
                        inv.setIngredient(reagent);
                    } else {
                        inv.setIngredient(null);
                    }

                    // Reset brewing time
                    BrewingStand stand = inv.getHolder();
                    if (stand != null) {
                        stand.setBrewingTime(0);
                        stand.update();
                    }
                    break;
                }
            }
        }
    }
}

