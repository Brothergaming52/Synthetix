package org.bg52.synthetix.gui.recipe;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.gui.Canvas;
import org.bg52.synthetix.gui.CanvasButton;
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
import java.util.Locale;
import java.util.Map;

/**
 * Visual in-game recipe lookup canvas (JEI/NEI style).
 * Displays full 3x3 crafting grids, smelting arrows, smithing combinations,
 * anvil requirements, stonecutting, and brewing stands for any custom item.
 * Supports interactive JEI-style ingredient clicking to navigate nested recipes.
 */
public class RecipeViewerGUI {

    private final SynthetixPlugin plugin;
    private final LanguageManager lang;
    private final String itemId;
    private final List<SynthetixRecipe> recipes;
    private int currentIndex = 0;
    private final Runnable onBack;

    public RecipeViewerGUI(SynthetixPlugin plugin, String itemId, Runnable onBack) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
        this.itemId = itemId != null ? itemId.toLowerCase(Locale.ROOT) : "";
        this.onBack = onBack;

        // Query all recipes producing this item
        List<SynthetixRecipe> found = new ArrayList<>();
        for (SynthetixRecipe r : plugin.getRecipeManager().getAllRecipes()) {
            if (this.itemId.equalsIgnoreCase(r.getResultItemId())) {
                if (r.isEnabled() && plugin.getRecipeManager().isRecipeAllowedByGlobalSettings(r)) {
                    found.add(r);
                }
            }
        }
        this.recipes = found;
    }

    public static void open(Player player, SynthetixPlugin plugin, String itemId, Runnable onBack) {
        new RecipeViewerGUI(plugin, itemId, onBack).open(player, 0);
    }

    public void open(Player player, int index) {
        if (recipes.isEmpty()) {
            openEmptyView(player);
            return;
        }

        this.currentIndex = Math.max(0, Math.min(recipes.size() - 1, index));
        SynthetixRecipe recipe = recipes.get(this.currentIndex);

        ItemDefinition def = plugin.getItemRegistry().get(itemId);
        String name = def != null ? def.getDisplayName() : itemId;

        Component title = TextUtil.parse("<dark_gray>Recipe: <gold>" + name + "</gold> <dark_gray>(" + (currentIndex + 1) + "/" + recipes.size() + ")</dark_gray>");
        Canvas canvas = new Canvas(title, 6);

        // Frame borders
        ItemStack darkGlass = Canvas.createBorderItem(Material.BLACK_STAINED_GLASS_PANE, Component.empty());
        ItemStack grayGlass = Canvas.createBorderItem(Material.GRAY_STAINED_GLASS_PANE, Component.empty());

        for (int c = 0; c < 9; c++) {
            canvas.setButton(0, c, CanvasButton.of(c == 0 || c == 8 ? darkGlass : grayGlass));
            canvas.setButton(5, c, CanvasButton.of(darkGlass));
        }
        for (int r = 1; r < 5; r++) {
            canvas.setButton(r, 0, CanvasButton.of(darkGlass));
            canvas.setButton(r, 8, CanvasButton.of(darkGlass));
        }

        // Top center slot 4: Result Item Preview
        ItemStack resultStack = recipe.buildResult(plugin);
        if (resultStack == null && def != null) {
            resultStack = def.buildItemStack();
        }
        if (resultStack != null) {
            ItemStack preview = resultStack.clone();
            ItemMeta pMeta = preview.getItemMeta();
            if (pMeta != null) {
                List<Component> lore = pMeta.lore() != null ? new ArrayList<>(pMeta.lore()) : new ArrayList<>();
                lore.add(Component.empty());
                lore.add(TextUtil.parse("<yellow>Type:</yellow> <white>" + recipe.getType().name() + "</white>"));
                lore.add(TextUtil.parse("<yellow>Yield:</yellow> <white>" + recipe.getResultAmount() + "x</white>"));
                pMeta.lore(lore);
                preview.setItemMeta(pMeta);
            }
            canvas.setButton(4, CanvasButton.of(preview));
        }

        // Render recipe type specifics with interactive ingredient traversal
        switch (recipe.getType()) {
            case SHAPED -> renderShaped(player, canvas, recipe);
            case SHAPELESS -> renderShapeless(player, canvas, recipe);
            case FURNACE, BLAST_FURNACE, SMOKER, CAMPFIRE -> renderCooking(player, canvas, recipe);
            case STONECUTTER -> renderStonecutter(player, canvas, recipe);
            case SMITHING_TRANSFORM -> renderSmithing(player, canvas, recipe);
            case ANVIL -> renderAnvil(player, canvas, recipe);
            case BREWING -> renderBrewing(player, canvas, recipe);
        }

        // Navigation Footer (Row 5: slots 45-53)
        // Back Button
        if (onBack != null) {
            ItemStack backBtn = Canvas.createBorderItem(Material.ARROW, lang.getComponent("editor.back-button"));
            canvas.setButton(45, CanvasButton.of(backBtn, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
                onBack.run();
            }));
        }

        // Symmetrical Variant Pagination
        if (recipes.size() > 1) {
            if (currentIndex > 0) {
                ItemStack prevBtn = Canvas.createBorderItem(Material.ARROW, TextUtil.parse("<yellow>« Previous Recipe</yellow>"));
                canvas.setButton(48, CanvasButton.of(prevBtn, e -> {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                    open(player, currentIndex - 1);
                }));
            }

            ItemStack infoBtn = Canvas.createBorderItem(Material.BOOK,
                    TextUtil.parse("<gold><bold>Recipe " + (currentIndex + 1) + " of " + recipes.size() + "</bold></gold>"));
            canvas.setButton(49, CanvasButton.of(infoBtn));

            if (currentIndex < recipes.size() - 1) {
                ItemStack nextBtn = Canvas.createBorderItem(Material.ARROW, TextUtil.parse("<yellow>Next Recipe »</yellow>"));
                canvas.setButton(50, CanvasButton.of(nextBtn, e -> {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                    open(player, currentIndex + 1);
                }));
            }
        }

        canvas.open(player);
    }

    private CanvasButton createIngredientButton(Player player, IngredientChoice choice) {
        if (choice == null) return null;
        ItemStack icon = choice.toDisplayItem(plugin);
        if (icon == null) return null;

        if (choice.isCustomItem() && choice.getCustomItemId() != null) {
            String custId = choice.getCustomItemId();
            ItemStack clickIcon = icon.clone();
            ItemMeta meta = clickIcon.getItemMeta();
            if (meta != null) {
                List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                lore.add(Component.empty());
                lore.add(TextUtil.parse("<yellow>Click to view ingredient recipe »</yellow>"));
                meta.lore(lore);
                clickIcon.setItemMeta(meta);
            }
            return CanvasButton.of(clickIcon, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                RecipeViewerGUI.open(player, plugin, custId, () -> open(player, currentIndex));
            });
        }

        return CanvasButton.of(icon);
    }

    private void renderShaped(Player player, Canvas canvas, SynthetixRecipe recipe) {
        int[] gridSlots = new int[]{
                19, 20, 21,
                28, 29, 30,
                37, 38, 39
        };

        // Render 3x3 empty placeholders first
        ItemStack slotBg = Canvas.createBorderItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, Component.empty());
        for (int slot : gridSlots) {
            canvas.setButton(slot, CanvasButton.of(slotBg));
        }

        List<String> shape = recipe.getShape();
        Map<Character, IngredientChoice> ingredients = recipe.getShapedIngredients();

        if (shape != null && ingredients != null) {
            for (int r = 0; r < shape.size() && r < 3; r++) {
                String row = shape.get(r);
                for (int c = 0; c < row.length() && c < 3; c++) {
                    char ch = row.charAt(c);
                    IngredientChoice choice = ingredients.get(ch);
                    if (choice != null) {
                        CanvasButton btn = createIngredientButton(player, choice);
                        if (btn != null) {
                            int slotIndex = r * 3 + c;
                            canvas.setButton(gridSlots[slotIndex], btn);
                        }
                    }
                }
            }
        }

        // Crafting table icon + arrow
        ItemStack ct = Canvas.createBorderItem(Material.CRAFTING_TABLE, TextUtil.parse("<gold>Shaped Crafting Table</gold>"));
        canvas.setButton(23, CanvasButton.of(ct));

        ItemStack arrow = Canvas.createBorderItem(Material.ARROW, TextUtil.parse("<green>Crafts »</green>"));
        canvas.setButton(24, CanvasButton.of(arrow));

        ItemStack result = recipe.buildResult(plugin);
        if (result != null) {
            canvas.setButton(25, CanvasButton.of(result));
        }
    }

    private void renderShapeless(Player player, Canvas canvas, SynthetixRecipe recipe) {
        int[] gridSlots = new int[]{
                19, 20, 21,
                28, 29, 30,
                37, 38, 39
        };

        ItemStack slotBg = Canvas.createBorderItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, Component.empty());
        for (int slot : gridSlots) {
            canvas.setButton(slot, CanvasButton.of(slotBg));
        }

        List<IngredientChoice> ingredients = recipe.getShapelessIngredients();
        if (ingredients != null) {
            for (int i = 0; i < ingredients.size() && i < gridSlots.length; i++) {
                CanvasButton btn = createIngredientButton(player, ingredients.get(i));
                if (btn != null) {
                    canvas.setButton(gridSlots[i], btn);
                }
            }
        }

        ItemStack ct = Canvas.createBorderItem(Material.CRAFTING_TABLE, TextUtil.parse("<gold>Shapeless Crafting</gold>"));
        canvas.setButton(23, CanvasButton.of(ct));

        ItemStack arrow = Canvas.createBorderItem(Material.ARROW, TextUtil.parse("<green>Crafts »</green>"));
        canvas.setButton(24, CanvasButton.of(arrow));

        ItemStack result = recipe.buildResult(plugin);
        if (result != null) {
            canvas.setButton(25, CanvasButton.of(result));
        }
    }

    private void renderCooking(Player player, Canvas canvas, SynthetixRecipe recipe) {
        // Input slot: 20
        if (recipe.getCookingInput() != null) {
            CanvasButton in = createIngredientButton(player, recipe.getCookingInput());
            if (in != null) canvas.setButton(20, in);
        }

        // Flame fuel icon: 29
        ItemStack flame = Canvas.createBorderItem(Material.CAMPFIRE, TextUtil.parse("<gold>Heat Source</gold>"));
        canvas.setButton(29, CanvasButton.of(flame));

        // Arrow with cook time & XP: 22
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta meta = arrow.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.parse("<green>Cooks into »</green>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Time:</gray> <white>" + (recipe.getCookingTime() / 20.0) + "s</white>"));
            lore.add(TextUtil.parse("<gray>Experience:</gray> <white>" + recipe.getExperience() + " XP</white>"));
            meta.lore(lore);
            arrow.setItemMeta(meta);
        }
        canvas.setButton(22, CanvasButton.of(arrow));

        // Output slot: 24
        ItemStack result = recipe.buildResult(plugin);
        if (result != null) canvas.setButton(24, CanvasButton.of(result));
    }

    private void renderStonecutter(Player player, Canvas canvas, SynthetixRecipe recipe) {
        if (recipe.getCookingInput() != null) {
            CanvasButton in = createIngredientButton(player, recipe.getCookingInput());
            if (in != null) canvas.setButton(20, in);
        }

        ItemStack cutter = Canvas.createBorderItem(Material.STONECUTTER, TextUtil.parse("<gold>Stonecutter</gold>"));
        canvas.setButton(22, CanvasButton.of(cutter));

        ItemStack result = recipe.buildResult(plugin);
        if (result != null) canvas.setButton(24, CanvasButton.of(result));
    }

    private void renderSmithing(Player player, Canvas canvas, SynthetixRecipe recipe) {
        if (recipe.getSmithingTemplate() != null) {
            CanvasButton t = createIngredientButton(player, recipe.getSmithingTemplate());
            if (t != null) canvas.setButton(19, t);
        }
        if (recipe.getSmithingBase() != null) {
            CanvasButton b = createIngredientButton(player, recipe.getSmithingBase());
            if (b != null) canvas.setButton(20, b);
        }
        if (recipe.getSmithingAddition() != null) {
            CanvasButton a = createIngredientButton(player, recipe.getSmithingAddition());
            if (a != null) canvas.setButton(21, a);
        }

        ItemStack table = Canvas.createBorderItem(Material.SMITHING_TABLE, TextUtil.parse("<gold>Smithing Table</gold>"));
        canvas.setButton(23, CanvasButton.of(table));

        ItemStack result = recipe.buildResult(plugin);
        if (result != null) canvas.setButton(25, CanvasButton.of(result));
    }

    private void renderAnvil(Player player, Canvas canvas, SynthetixRecipe recipe) {
        if (recipe.getAnvilLeft() != null) {
            CanvasButton l = createIngredientButton(player, recipe.getAnvilLeft());
            if (l != null) canvas.setButton(20, l);
        }
        if (recipe.getAnvilRight() != null) {
            CanvasButton r = createIngredientButton(player, recipe.getAnvilRight());
            if (r != null) canvas.setButton(21, r);
        }

        ItemStack anvil = new ItemStack(Material.ANVIL);
        ItemMeta meta = anvil.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.parse("<gold>Anvil Combination</gold>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Level Cost:</gray> <green>" + recipe.getAnvilCost() + " Levels</green>"));
            meta.lore(lore);
            anvil.setItemMeta(meta);
        }
        canvas.setButton(23, CanvasButton.of(anvil));

        ItemStack result = recipe.buildResult(plugin);
        if (result != null) canvas.setButton(25, CanvasButton.of(result));
    }

    private void renderBrewing(Player player, Canvas canvas, SynthetixRecipe recipe) {
        // Ingredient on top: 11
        if (recipe.getBrewingIngredient() != null) {
            CanvasButton ing = createIngredientButton(player, recipe.getBrewingIngredient());
            if (ing != null) canvas.setButton(11, ing);
        }

        // Stand icon: 20
        ItemStack stand = Canvas.createBorderItem(Material.BREWING_STAND, TextUtil.parse("<gold>Brewing Stand</gold>"));
        canvas.setButton(20, CanvasButton.of(stand));

        // Base items on bottom: 29, 30, 31
        if (recipe.getBrewingBase() != null) {
            CanvasButton base = createIngredientButton(player, recipe.getBrewingBase());
            if (base != null) {
                canvas.setButton(29, base);
                canvas.setButton(30, createIngredientButton(player, recipe.getBrewingBase()));
                canvas.setButton(31, createIngredientButton(player, recipe.getBrewingBase()));
            }
        }

        ItemStack arrow = Canvas.createBorderItem(Material.ARROW, TextUtil.parse("<green>Brews into »</green>"));
        canvas.setButton(22, CanvasButton.of(arrow));

        ItemStack result = recipe.buildResult(plugin);
        if (result != null) canvas.setButton(24, CanvasButton.of(result));
    }

    private void openEmptyView(Player player) {
        Canvas canvas = new Canvas(TextUtil.parse("<red>No Recipes Found</red>"), 3);
        ItemStack empty = Canvas.createBorderItem(Material.BARRIER, TextUtil.parse("<red>No recipes found for " + itemId + "</red>"));
        canvas.setButton(13, CanvasButton.of(empty));

        if (onBack != null) {
            ItemStack backBtn = Canvas.createBorderItem(Material.ARROW, lang.getComponent("editor.back-button"));
            canvas.setButton(22, CanvasButton.of(backBtn, e -> onBack.run()));
        }
        canvas.open(player);
    }
}
