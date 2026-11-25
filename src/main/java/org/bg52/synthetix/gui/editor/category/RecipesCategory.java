package org.bg52.synthetix.gui.editor.category;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.api.editor.ItemEditorCategory;
import org.bg52.synthetix.gui.CanvasButton;
import org.bg52.synthetix.gui.editor.BaseEditorCanvas;
import org.bg52.synthetix.item.ItemDefinition;
import org.bg52.synthetix.lang.LanguageManager;
import org.bg52.synthetix.recipe.IngredientChoice;
import org.bg52.synthetix.recipe.SynthetixRecipe;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Core category for viewing and inspecting all crafting and cooking recipes producing this item.
 */
public class RecipesCategory implements ItemEditorCategory {

    private final SynthetixPlugin plugin;
    private final LanguageManager lang;

    public RecipesCategory(SynthetixPlugin plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    @Override
    public String getId() {
        return "recipes";
    }

    @Override
    public Component getDisplayName() {
        return lang.getComponent("editor.categories.recipes.name");
    }

    @Override
    public int getPriority() {
        return 60;
    }

    @Override
    public boolean isPluginCategory() {
        return false;
    }

    @Override
    public ItemStack getIcon(ItemDefinition def) {
        ItemStack item = new ItemStack(Material.CRAFTING_TABLE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(getDisplayName());
            List<Component> lore = new ArrayList<>();
            lore.add(lang.getComponent("editor.categories.recipes.desc-1"));
            lore.add(lang.getComponent("editor.categories.recipes.desc-2"));
            lore.add(Component.empty());
            List<SynthetixRecipe> recipes = plugin.getRecipeManager().getRecipesFor(def.getId());
            lore.add(TextUtil.parse("<dark_gray>•</dark_gray> <gray>Active Recipes:</gray> <white>" + recipes.size() + "</white>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>Click to inspect recipes »</yellow>"));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void open(Player player, ItemDefinition def, Runnable onBack) {
        new org.bg52.synthetix.gui.editor.recipe.RecipeEditorGUI(plugin).open(player, def, onBack);
    }
}

