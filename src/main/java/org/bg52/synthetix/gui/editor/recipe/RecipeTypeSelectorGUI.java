package org.bg52.synthetix.gui.editor.recipe;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.gui.CanvasButton;
import org.bg52.synthetix.gui.editor.BaseEditorCanvas;
import org.bg52.synthetix.item.ItemDefinition;
import org.bg52.synthetix.recipe.RecipeType;
import org.bg52.synthetix.recipe.SynthetixRecipe;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Symmetrical GUI allowing the player to select the type of recipe to create.
 */
public class RecipeTypeSelectorGUI {

    private final SynthetixPlugin plugin;
    private final ItemDefinition itemDef;
    private final Runnable onBack;

    public RecipeTypeSelectorGUI(SynthetixPlugin plugin, ItemDefinition itemDef, Runnable onBack) {
        this.plugin = plugin;
        this.itemDef = itemDef;
        this.onBack = onBack;
    }

    public void open(Player player) {
        BaseEditorCanvas base = new BaseEditorCanvas(plugin, itemDef,
                plugin.getLanguageManager().getComponent("editor.recipes.select-type-title"), 4);
        base.setupSideBorders();

        // 10 Recipe Types arranged in 2 perfectly symmetrical centered rows:
        // Row 1: slots 11, 12, 13, 14, 15 (columns 2 to 6)
        // Row 2: slots 20, 21, 22, 23, 24 (columns 2 to 6)

        RecipeType[] types = {
                RecipeType.SHAPED,
                RecipeType.SHAPELESS,
                RecipeType.FURNACE,
                RecipeType.BLAST_FURNACE,
                RecipeType.SMOKER,
                RecipeType.CAMPFIRE,
                RecipeType.STONECUTTER,
                RecipeType.SMITHING_TRANSFORM,
                RecipeType.ANVIL,
                RecipeType.BREWING
        };

        int[] slots = { 11, 12, 13, 14, 15, 20, 21, 22, 23, 24 };

        for (int i = 0; i < types.length; i++) {
            RecipeType type = types[i];
            int slot = slots[i];

            Material mat = switch (type) {
                case SHAPED -> Material.CRAFTING_TABLE;
                case SHAPELESS -> Material.BOOK;
                case FURNACE -> Material.FURNACE;
                case BLAST_FURNACE -> Material.BLAST_FURNACE;
                case SMOKER -> Material.SMOKER;
                case CAMPFIRE -> Material.CAMPFIRE;
                case STONECUTTER -> Material.STONECUTTER;
                case SMITHING_TRANSFORM -> Material.SMITHING_TABLE;
                case ANVIL -> Material.ANVIL;
                case BREWING -> Material.BREWING_STAND;
            };

            ItemStack icon = new ItemStack(mat);
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.displayName(TextUtil.parse("<gold><bold>" + type.getDisplayName() + "</bold></gold>"));
                List<Component> lore = new ArrayList<>();
                lore.add(TextUtil.parse("<gray>Construct a new " + type.getDisplayName().toLowerCase(Locale.ROOT) + " recipe.</gray>"));
                lore.add(Component.empty());
                lore.add(TextUtil.parse("<yellow>Click to build »</yellow>"));
                meta.lore(lore);
                icon.setItemMeta(meta);
            }

            base.getCanvas().setButton(slot, CanvasButton.of(icon, event -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                // Generate a persistent, deterministic recipe id
                String recipeId = plugin.getRecipeManager().generateRecipeId(itemDef.getId(), type);
                SynthetixRecipe newRecipe = new SynthetixRecipe(recipeId, type, "synth:" + itemDef.getId(), 1);
                new RecipeBuilderGUI(plugin, itemDef, newRecipe, onBack).open(player);
            }));
        }

        base.setCenteredBackButton(onBack);
        base.open(player);
    }
}

