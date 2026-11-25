package org.bg52.synthetix.recipe;

import org.bg52.synthetix.SynthetixPlugin;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Handles custom anvil combinations, upgrades, and repair recipes.
 */
public class AnvilRecipeListener implements Listener {

    private final SynthetixPlugin plugin;
    private final RecipeManager recipeManager;

    public AnvilRecipeListener(SynthetixPlugin plugin, RecipeManager recipeManager) {
        this.plugin = plugin;
        this.recipeManager = recipeManager;
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        ItemStack left = inv.getItem(0);
        ItemStack right = inv.getItem(1);

        if (left == null || left.getType() == Material.AIR) return;
        if (right == null || right.getType() == Material.AIR) return;

        List<SynthetixRecipe> anvilRecipes = recipeManager.getRecipesByType(RecipeType.ANVIL);
        for (SynthetixRecipe recipe : anvilRecipes) {
            if (!recipe.isEnabled() || !recipeManager.isRecipeAllowedByGlobalSettings(recipe)) continue;
            if (recipe.getAnvilLeft() == null || recipe.getAnvilRight() == null) continue;

            if (recipe.getAnvilLeft().test(left, plugin) && recipe.getAnvilRight().test(right, plugin)) {
                ItemStack result = recipe.buildResult(plugin);

                // Preserve custom name from left item if configured
                if (recipe.isAnvilPreserveName() && left.hasItemMeta() && left.getItemMeta().hasDisplayName()) {
                    ItemMeta rMeta = result.getItemMeta();
                    if (rMeta != null) {
                        rMeta.displayName(left.getItemMeta().displayName());
                        result.setItemMeta(rMeta);
                    }
                }

                event.setResult(result);
                inv.setRepairCost(recipe.getAnvilCost());
                break;
            }
        }
    }
}

