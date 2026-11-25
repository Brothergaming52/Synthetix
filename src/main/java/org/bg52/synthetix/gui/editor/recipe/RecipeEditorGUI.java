package org.bg52.synthetix.gui.editor.recipe;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.gui.Canvas;
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
 * Interactive in-game Recipe Editor listing all recipes for a custom item.
 * Supports creating new recipes and modifying/deleting existing ones.
 */
public class RecipeEditorGUI {

    private final SynthetixPlugin plugin;
    private final LanguageManager lang;

    public RecipeEditorGUI(SynthetixPlugin plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    public void open(Player player, ItemDefinition def, Runnable onBack) {
        List<SynthetixRecipe> recipes = plugin.getRecipeManager().getRecipesFor(def.getId());
        BaseEditorCanvas base = new BaseEditorCanvas(plugin, def, lang.getComponent("editor.recipes.title", "%item%", def.getId()), 5);
        base.setupSideBorders();

        if (recipes.isEmpty()) {
            ItemStack emptyIcon = new ItemStack(Material.BARRIER);
            ItemMeta eMeta = emptyIcon.getItemMeta();
            if (eMeta != null) {
                eMeta.displayName(lang.getComponent("editor.recipes.empty-title"));
                List<Component> l = new ArrayList<>();
                l.add(lang.getComponent("editor.recipes.empty-desc"));
                l.add(Component.empty());
                l.add(TextUtil.parse("<yellow>Click [+ Add Recipe] below to create one!</yellow>"));
                eMeta.lore(l);
                emptyIcon.setItemMeta(eMeta);
            }
            base.getCanvas().setButton(22, CanvasButton.of(emptyIcon));
        } else {
            int slot = 10;
            for (SynthetixRecipe recipe : recipes) {
                Material iconMat = switch (recipe.getType()) {
                    case SHAPED, SHAPELESS -> Material.CRAFTING_TABLE;
                    case FURNACE -> Material.FURNACE;
                    case BLAST_FURNACE -> Material.BLAST_FURNACE;
                    case SMOKER -> Material.SMOKER;
                    case CAMPFIRE -> Material.CAMPFIRE;
                    case STONECUTTER -> Material.STONECUTTER;
                    case SMITHING_TRANSFORM -> Material.SMITHING_TABLE;
                    case ANVIL -> Material.ANVIL;
                    case BREWING -> Material.BREWING_STAND;
                };

                ItemStack icon = new ItemStack(iconMat);
                ItemMeta meta = icon.getItemMeta();
                if (meta != null) {
                    meta.displayName(TextUtil.parse("<gold><bold>" + recipe.getType().getDisplayName() + "</bold></gold>"));
                    List<Component> l = new ArrayList<>();
                    l.add(TextUtil.parse("<dark_gray>Recipe ID: <white>" + recipe.getId() + "</white></dark_gray>"));
                    l.add(TextUtil.parse("<gray>Yield: <green>" + recipe.getResultAmount() + "x</green></gray>"));
                    l.add(TextUtil.parse("<gray>Status: </gray>" + (recipe.isEnabled() ? "<green><bold>● ENABLED</bold></green>" : "<red><bold>● DISABLED</bold></red>")));
                    l.add(Component.empty());

                    // Ingredients summary
                    switch (recipe.getType()) {
                        case SHAPED -> {
                            l.add(TextUtil.parse("<yellow>Ingredients:</yellow>"));
                            if (recipe.getShapedIngredients() != null) {
                                for (Map.Entry<Character, IngredientChoice> e : recipe.getShapedIngredients().entrySet()) {
                                    l.add(TextUtil.parse("<dark_gray>•</dark_gray> <white>" + e.getKey() + "</white> = <aqua>" + e.getValue().getDisplayIdentifier(plugin) + "</aqua>"));
                                }
                            }
                        }
                        case SHAPELESS -> {
                            l.add(TextUtil.parse("<yellow>Ingredients:</yellow>"));
                            if (recipe.getShapelessIngredients() != null) {
                                for (IngredientChoice ing : recipe.getShapelessIngredients()) {
                                    l.add(TextUtil.parse("<dark_gray>•</dark_gray> <aqua>" + ing.getDisplayIdentifier(plugin) + "</aqua>"));
                                }
                            }
                        }
                        case FURNACE, BLAST_FURNACE, SMOKER, CAMPFIRE -> {
                            l.add(TextUtil.parse("<gray>Input:</gray> <aqua>" + (recipe.getCookingInput() != null ? recipe.getCookingInput().getDisplayIdentifier(plugin) : "None") + "</aqua>"));
                            String timeStr = recipe.getCookingTime() % 20 == 0 ? (recipe.getCookingTime() / 20) + "s" : String.format(java.util.Locale.ROOT, "%.1fs", recipe.getCookingTime() / 20.0);
                            l.add(TextUtil.parse("<gray>Cook Time:</gray> <white>" + timeStr + "</white>"));
                            l.add(TextUtil.parse("<gray>XP:</gray> <white>" + recipe.getExperience() + "</white>"));
                        }
                        case STONECUTTER -> {
                            l.add(TextUtil.parse("<gray>Input:</gray> <aqua>" + (recipe.getCookingInput() != null ? recipe.getCookingInput().getDisplayIdentifier(plugin) : "None") + "</aqua>"));
                        }
                        case SMITHING_TRANSFORM -> {
                            l.add(TextUtil.parse("<gray>Template:</gray> <aqua>" + (recipe.getSmithingTemplate() != null ? recipe.getSmithingTemplate().getDisplayIdentifier(plugin) : "None") + "</aqua>"));
                            l.add(TextUtil.parse("<gray>Base:</gray> <aqua>" + (recipe.getSmithingBase() != null ? recipe.getSmithingBase().getDisplayIdentifier(plugin) : "None") + "</aqua>"));
                            l.add(TextUtil.parse("<gray>Addition:</gray> <aqua>" + (recipe.getSmithingAddition() != null ? recipe.getSmithingAddition().getDisplayIdentifier(plugin) : "None") + "</aqua>"));
                        }
                        case ANVIL -> {
                            l.add(TextUtil.parse("<gray>Left:</gray> <aqua>" + (recipe.getAnvilLeft() != null ? recipe.getAnvilLeft().getDisplayIdentifier(plugin) : "None") + "</aqua>"));
                            l.add(TextUtil.parse("<gray>Right:</gray> <aqua>" + (recipe.getAnvilRight() != null ? recipe.getAnvilRight().getDisplayIdentifier(plugin) : "None") + "</aqua>"));
                            l.add(TextUtil.parse("<gray>Cost:</gray> <green>" + recipe.getAnvilCost() + " Levels</green>"));
                        }
                        case BREWING -> {
                            l.add(TextUtil.parse("<gray>Reagent:</gray> <aqua>" + (recipe.getBrewingIngredient() != null ? recipe.getBrewingIngredient().getDisplayIdentifier(plugin) : "None") + "</aqua>"));
                            l.add(TextUtil.parse("<gray>Base:</gray> <aqua>" + (recipe.getBrewingBase() != null ? recipe.getBrewingBase().getDisplayIdentifier(plugin) : "None") + "</aqua>"));
                        }
                    }

                    l.add(Component.empty());
                    l.add(TextUtil.parse("<yellow>Left-Click:</yellow> <gray>Edit in Builder »</gray>"));
                    l.add(TextUtil.parse("<gold>Right-Click:</gold> <gray>Toggle Enable / Disable</gray>"));
                    l.add(TextUtil.parse("<red>Shift-Right-Click:</red> <gray>Delete recipe</gray>"));
                    meta.lore(l);
                    icon.setItemMeta(meta);
                }

                SynthetixRecipe currentRecipe = recipe;
                base.getCanvas().setButton(slot++, CanvasButton.of(icon, event -> {
                    if (event.isShiftClick() && event.isRightClick()) {
                        // Delete recipe
                        try {
                            plugin.getRecipeManager().deleteRecipe(currentRecipe.getId());
                            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                            player.sendMessage(lang.getComponent("editor.recipes.deleted", "%id%", currentRecipe.getId()));
                            open(player, def, onBack);
                        } catch (Exception e) {
                            player.sendMessage(TextUtil.parse("<red>Failed to delete recipe: " + e.getMessage() + "</red>"));
                        }
                    } else if (event.isRightClick()) {
                        // Toggle enable/disable
                        boolean newState = !currentRecipe.isEnabled();
                        try {
                            plugin.getRecipeManager().setRecipeEnabled(currentRecipe.getId(), newState);
                            if (newState) {
                                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                                player.sendMessage(TextUtil.parse("<green>✔ Recipe <white>" + currentRecipe.getId() + "</white> is now <bold>ENABLED</bold>.</green>"));
                            } else {
                                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
                                player.sendMessage(TextUtil.parse("<yellow>✘ Recipe <white>" + currentRecipe.getId() + "</white> is now <bold>DISABLED</bold>.</yellow>"));
                            }
                            open(player, def, onBack);
                        } catch (Exception e) {
                            player.sendMessage(TextUtil.parse("<red>Failed to toggle recipe: " + e.getMessage() + "</red>"));
                        }
                    } else {
                        // Edit in builder
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                        new RecipeBuilderGUI(plugin, def, currentRecipe, () -> open(player, def, onBack)).open(player);
                    }
                }));

                if (slot == 17) slot = 19;
                if (slot == 26) slot = 28;
                if (slot > 34) break;
            }
        }

        // Add Recipe Button at slot 41 (Column 5, symmetrical to Back Button at slot 39 / Column 3)
        ItemStack addBtn = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta addMeta = addBtn.getItemMeta();
        if (addMeta != null) {
            addMeta.displayName(lang.getComponent("editor.recipes.add-btn"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<gray>Construct a new crafting, cooking,</gray>"));
            l.add(TextUtil.parse("<gray>smithing, or brewing recipe.</gray>"));
            l.add(Component.empty());
            l.add(TextUtil.parse("<yellow>Click to select recipe type »</yellow>"));
            addMeta.lore(l);
            addBtn.setItemMeta(addMeta);
        }
        base.getCanvas().setButton(41, CanvasButton.of(addBtn, event -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            new RecipeTypeSelectorGUI(plugin, def, () -> open(player, def, onBack)).open(player);
        }));

        base.setBackButton(onBack);
        base.open(player);
    }
}

