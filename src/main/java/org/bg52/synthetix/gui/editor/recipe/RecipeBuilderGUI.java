package org.bg52.synthetix.gui.editor.recipe;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.gui.CanvasButton;
import org.bg52.synthetix.gui.editor.BaseEditorCanvas;
import org.bg52.synthetix.item.ItemDefinition;
import org.bg52.synthetix.lang.LanguageManager;
import org.bg52.synthetix.recipe.IngredientChoice;
import org.bg52.synthetix.recipe.RecipeType;
import org.bg52.synthetix.recipe.SynthetixRecipe;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.function.Consumer;

/**
 * Universal, interactive in-game recipe builder canvas supporting all 10 recipe types.
 */
public class RecipeBuilderGUI {

    private final SynthetixPlugin plugin;
    private final LanguageManager lang;
    private final ItemDefinition itemDef;
    private final SynthetixRecipe recipe;
    private final Runnable onBack;

    // Working state for Shaped/Shapeless 3x3 grid (9 slots: 0 to 8)
    private final IngredientChoice[] grid = new IngredientChoice[9];

    // Slot definitions for 3x3 crafting grid in 6-row canvas
    private static final int[] CRAFTING_GRID_SLOTS = {
            11, 12, 13,
            20, 21, 22,
            29, 30, 31
    };

    public RecipeBuilderGUI(SynthetixPlugin plugin, ItemDefinition itemDef, SynthetixRecipe recipe, Runnable onBack) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
        this.itemDef = itemDef;
        this.recipe = recipe;
        this.onBack = onBack;

        // Initialize grid if existing shaped/shapeless recipe
        Arrays.fill(grid, new IngredientChoice("AIR"));

        if (recipe.getType() == RecipeType.SHAPED && recipe.getShape() != null && recipe.getShapedIngredients() != null) {
            List<String> shape = recipe.getShape();
            Map<Character, IngredientChoice> map = recipe.getShapedIngredients();
            for (int r = 0; r < 3 && r < shape.size(); r++) {
                String row = (shape.get(r) + "   ").substring(0, 3);
                for (int c = 0; c < 3; c++) {
                    char ch = row.charAt(c);
                    if (ch != ' ' && map.containsKey(ch)) {
                        grid[r * 3 + c] = map.get(ch);
                    }
                }
            }
        } else if (recipe.getType() == RecipeType.SHAPELESS && recipe.getShapelessIngredients() != null) {
            List<IngredientChoice> list = recipe.getShapelessIngredients();
            for (int i = 0; i < list.size() && i < 9; i++) {
                grid[i] = list.get(i);
            }
        }
    }

    public void open(Player player) {
        BaseEditorCanvas base = new BaseEditorCanvas(plugin, itemDef,
                lang.getComponent("editor.recipes.builder-title", "%type%", recipe.getType().getDisplayName()), 6);
        base.setupSideBorders();

        // Setup Category-Specific Slots (inputs, layout, arrow, result preview, and yield)
        switch (recipe.getType()) {
            case SHAPED, SHAPELESS -> setupCraftingLayout(base, player);
            case FURNACE, BLAST_FURNACE, SMOKER, CAMPFIRE -> setupCookingLayout(base, player);
            case STONECUTTER -> setupStonecutterLayout(base, player);
            case SMITHING_TRANSFORM -> setupSmithingLayout(base, player);
            case ANVIL -> setupAnvilLayout(base, player);
            case BREWING -> setupBrewingLayout(base, player);
        }

        // Save Button at slot 50 (Column 5, symmetrical to Back Button at slot 48 / Column 3)
        ItemStack saveBtn = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta sMeta = saveBtn.getItemMeta();
        if (sMeta != null) {
            sMeta.displayName(TextUtil.parse("<green><bold>💾 Save Recipe</bold></green>"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<gray>Registers recipe to server and saves</gray>"));
            l.add(TextUtil.parse("<gray>directly to the recipes config folder.</gray>"));
            l.add(Component.empty());
            l.add(TextUtil.parse("<yellow>Click to save »</yellow>"));
            sMeta.lore(l);
            saveBtn.setItemMeta(sMeta);
        }
        base.getCanvas().setButton(50, CanvasButton.of(saveBtn, event -> handleSave(player)));

        // Enable/Disable Toggle Button at slot 52
        boolean isEnabled = recipe.isEnabled();
        ItemStack toggleBtn = new ItemStack(isEnabled ? Material.LIME_DYE : Material.RED_DYE);
        ItemMeta tMeta = toggleBtn.getItemMeta();
        if (tMeta != null) {
            tMeta.displayName(TextUtil.parse(isEnabled ? "<green><bold>● Recipe: ENABLED</bold></green>" : "<red><bold>● Recipe: DISABLED</bold></red>"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<gray>Status: " + (isEnabled ? "<green>Active (Craftable)</green>" : "<red>Inactive (Disabled)</red>") + "</gray>"));
            l.add(Component.empty());
            l.add(TextUtil.parse("<gray>When disabled, players cannot craft or</gray>"));
            l.add(TextUtil.parse("<gray>produce this custom item via this recipe.</gray>"));
            l.add(Component.empty());
            l.add(TextUtil.parse("<yellow>Click to toggle status »</yellow>"));
            tMeta.lore(l);
            toggleBtn.setItemMeta(tMeta);
        }
        base.getCanvas().setButton(52, CanvasButton.of(toggleBtn, event -> {
            recipe.setEnabled(!recipe.isEnabled());
            if (recipe.isEnabled()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            } else {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
            }
            open(player);
        }));

        base.setBackButton(onBack);
        base.open(player);
    }

    private void setupResultAndArrow(BaseEditorCanvas base, Player player, int arrowSlot, int resultSlot, int yieldSlot, String arrowLabel) {
        // Arrow Indicator
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta aMeta = arrow.getItemMeta();
        if (aMeta != null) {
            aMeta.displayName(TextUtil.parse(arrowLabel != null ? arrowLabel : "<yellow>Crafts Into »</yellow>"));
            arrow.setItemMeta(aMeta);
        }
        base.getCanvas().setButton(arrowSlot, CanvasButton.of(arrow));

        // Result Preview Slot
        ItemStack resultItem = itemDef.buildItemStack();
        resultItem.setAmount(recipe.getResultAmount());
        ItemMeta rMeta = resultItem.getItemMeta();
        if (rMeta != null) {
            List<Component> l = rMeta.hasLore() ? new ArrayList<>(rMeta.lore()) : new ArrayList<>();
            l.add(Component.empty());
            l.add(TextUtil.parse("<dark_gray>────── [Recipe Output] ──────</dark_gray>"));
            l.add(TextUtil.parse("<green>Yield Quantity: <white>" + recipe.getResultAmount() + "x</white></green>"));
            l.add(TextUtil.parse("<gray>Product created by this recipe.</gray>"));
            rMeta.lore(l);
            resultItem.setItemMeta(rMeta);
        }
        base.getCanvas().setButton(resultSlot, CanvasButton.of(resultItem));

        // Result Yield Stack Size Button
        ItemStack yieldBtn = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta yMeta = yieldBtn.getItemMeta();
        if (yMeta != null) {
            yMeta.displayName(lang.getComponent("editor.recipes.yield-amount", "%amount%", String.valueOf(recipe.getResultAmount())));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<gray>Adjust batch output amount:</gray>"));
            l.add(TextUtil.parse("<yellow>Left-Click:</yellow> <gray>+1</gray>"));
            l.add(TextUtil.parse("<yellow>Right-Click:</yellow> <gray>-1</gray>"));
            l.add(TextUtil.parse("<yellow>Shift-Left-Click:</yellow> <gray>Cycle 1, 2, 4, 8, 16, 32, 64</gray>"));
            yMeta.lore(l);
            yieldBtn.setItemMeta(yMeta);
        }
        base.getCanvas().setButton(yieldSlot, CanvasButton.of(yieldBtn, event -> {
            if (event.isShiftClick()) {
                int[] presets = {1, 2, 4, 8, 16, 32, 64};
                int next = 1;
                for (int p : presets) {
                    if (p > recipe.getResultAmount()) {
                        next = p;
                        break;
                    }
                }
                recipe.setResultAmount(next);
            } else if (event.isRightClick()) {
                recipe.setResultAmount(Math.max(1, recipe.getResultAmount() - 1));
            } else {
                recipe.setResultAmount(Math.min(64, recipe.getResultAmount() + 1));
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            open(player);
        }));
    }

    private void setupCraftingLayout(BaseEditorCanvas base, Player player) {
        // 3x3 Crafting grid
        for (int i = 0; i < 9; i++) {
            final int index = i;
            int canvasSlot = CRAFTING_GRID_SLOTS[i];
            base.getCanvas().setButton(canvasSlot, createIngredientButton(grid[index], player, choice -> {
                grid[index] = choice;
                open(player);
            }));
        }

        // Result and Arrow
        setupResultAndArrow(base, player, 24, 25, 34, "<yellow>Crafts Into »</yellow>");

        // Toggle Shaped / Shapeless Button (Slot 40)
        boolean isShaped = recipe.getType() == RecipeType.SHAPED;
        ItemStack toggleBtn = new ItemStack(isShaped ? Material.CRAFTING_TABLE : Material.BOOK);
        ItemMeta tMeta = toggleBtn.getItemMeta();
        if (tMeta != null) {
            tMeta.displayName(TextUtil.parse("<gold><bold>Mode: " + (isShaped ? "SHAPED" : "SHAPELESS") + "</bold></gold>"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<gray>Current crafting arrangement style.</gray>"));
            l.add(Component.empty());
            l.add(TextUtil.parse("<yellow>Click to toggle Shaped / Shapeless »</yellow>"));
            tMeta.lore(l);
            toggleBtn.setItemMeta(tMeta);
        }
        base.getCanvas().setButton(40, CanvasButton.of(toggleBtn, event -> {
            recipe.setType(isShaped ? RecipeType.SHAPELESS : RecipeType.SHAPED);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            open(player);
        }));

        // Clear Grid Button (Slot 38)
        ItemStack clearBtn = new ItemStack(Material.BARRIER);
        ItemMeta cMeta = clearBtn.getItemMeta();
        if (cMeta != null) {
            cMeta.displayName(TextUtil.parse("<red><bold>Clear 3x3 Grid</bold></red>"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<gray>Removes all items from the crafting grid.</gray>"));
            cMeta.lore(l);
            clearBtn.setItemMeta(cMeta);
        }
        base.getCanvas().setButton(38, CanvasButton.of(clearBtn, event -> {
            Arrays.fill(grid, new IngredientChoice("AIR"));
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1.0f, 1.0f);
            open(player);
        }));
    }

    private void setupCookingLayout(BaseEditorCanvas base, Player player) {
        // Header Signs
        base.getCanvas().setButton(11, CanvasButton.of(createHeaderItem(Material.HOPPER,
                "<gold><bold>[Slot: Cook Input]</bold></gold>",
                "<gray>Place raw item or ingredient to be cooked.</gray>")));
        base.getCanvas().setButton(15, CanvasButton.of(createHeaderItem(Material.CHEST,
                "<gold><bold>[Slot: Cook Result]</bold></gold>",
                "<gray>Item produced after cooking completes.</gray>")));

        IngredientChoice currentInput = recipe.getCookingInput() != null ? recipe.getCookingInput() : new IngredientChoice("AIR");
        base.getCanvas().setButton(20, createIngredientButton(currentInput, player, choice -> {
            recipe.setCookingInput(choice);
            open(player);
        }));

        // Heat Icon
        Material heatMat = recipe.getType() == RecipeType.CAMPFIRE ? Material.CAMPFIRE : Material.BLAZE_POWDER;
        ItemStack heatIcon = new ItemStack(heatMat);
        ItemMeta hMeta = heatIcon.getItemMeta();
        if (hMeta != null) {
            hMeta.displayName(TextUtil.parse("<gold>Heat Source: " + recipe.getType().getDisplayName() + "</gold>"));
            heatIcon.setItemMeta(hMeta);
        }
        base.getCanvas().setButton(21, CanvasButton.of(heatIcon));

        // Result and Arrow
        setupResultAndArrow(base, player, 23, 24, 25, "<yellow>Cooks Into »</yellow>");

        // Cooking Time Button (Slot 29)
        ItemStack timeBtn = new ItemStack(Material.CLOCK);
        ItemMeta tMeta = timeBtn.getItemMeta();
        if (tMeta != null) {
            int seconds = recipe.getCookingTime() / 20;
            tMeta.displayName(TextUtil.parse("<gold>Cook Time: <white>" + seconds + "s</white> (" + recipe.getCookingTime() + " ticks)</gold>"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<yellow>Left-Click:</yellow> <gray>Cycle 5s, 10s, 15s, 20s, 30s</gray>"));
            l.add(TextUtil.parse("<yellow>Right-Click:</yellow> <gray>Custom in Chat</gray>"));
            tMeta.lore(l);
            timeBtn.setItemMeta(tMeta);
        }
        base.getCanvas().setButton(29, CanvasButton.of(timeBtn, event -> {
            if (event.isRightClick()) {
                promptNumber(player, "<gray>Enter cooking time in seconds (e.g. <yellow>10</yellow>):</gray>", sec -> {
                    recipe.setCookingTime(Math.max(1, (int) (sec * 20)));
                    open(player);
                });
            } else {
                int[] cycle = {100, 200, 300, 400, 600};
                int next = 100;
                for (int c : cycle) {
                    if (c > recipe.getCookingTime()) {
                        next = c;
                        break;
                    }
                }
                recipe.setCookingTime(next);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                open(player);
            }
        }));

        // Experience Button (Slot 31)
        ItemStack xpBtn = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta xpMeta = xpBtn.getItemMeta();
        if (xpMeta != null) {
            xpMeta.displayName(TextUtil.parse("<green>Experience: <white>" + recipe.getExperience() + " XP</white></green>"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<yellow>Left-Click:</yellow> <gray>Cycle 0.2, 0.5, 0.7, 1.0, 2.0</gray>"));
            l.add(TextUtil.parse("<yellow>Right-Click:</yellow> <gray>Custom in Chat</gray>"));
            xpMeta.lore(l);
            xpBtn.setItemMeta(xpMeta);
        }
        base.getCanvas().setButton(31, CanvasButton.of(xpBtn, event -> {
            if (event.isRightClick()) {
                promptNumber(player, "<gray>Enter experience yield (e.g. <yellow>0.7</yellow>):</gray>", xp -> {
                    recipe.setExperience((float) Math.max(0.0, xp));
                    open(player);
                });
            } else {
                float[] cycle = {0.2f, 0.5f, 0.7f, 1.0f, 2.0f};
                float next = 0.2f;
                for (float c : cycle) {
                    if (c > recipe.getExperience() + 0.05f) {
                        next = c;
                        break;
                    }
                }
                recipe.setExperience(next);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                open(player);
            }
        }));
    }

    private void setupStonecutterLayout(BaseEditorCanvas base, Player player) {
        // Header Signs
        base.getCanvas().setButton(11, CanvasButton.of(createHeaderItem(Material.HOPPER,
                "<gold><bold>[Slot: Stonecutter Input]</bold></gold>",
                "<gray>Base block or material to cut.</gray>")));
        base.getCanvas().setButton(15, CanvasButton.of(createHeaderItem(Material.CHEST,
                "<gold><bold>[Slot: Stonecutter Result]</bold></gold>",
                "<gray>Cut product received.</gray>")));

        IngredientChoice currentInput = recipe.getCookingInput() != null ? recipe.getCookingInput() : new IngredientChoice("AIR");
        base.getCanvas().setButton(20, createIngredientButton(currentInput, player, choice -> {
            recipe.setCookingInput(choice);
            open(player);
        }));

        ItemStack stonecutterIcon = new ItemStack(Material.STONECUTTER);
        ItemMeta sMeta = stonecutterIcon.getItemMeta();
        if (sMeta != null) {
            sMeta.displayName(TextUtil.parse("<gray>Stonecutter</gray>"));
            stonecutterIcon.setItemMeta(sMeta);
        }
        base.getCanvas().setButton(21, CanvasButton.of(stonecutterIcon));

        // Result and Arrow
        setupResultAndArrow(base, player, 23, 24, 25, "<yellow>Cuts Into »</yellow>");
    }

    private void setupSmithingLayout(BaseEditorCanvas base, Player player) {
        // Header Signs
        base.getCanvas().setButton(10, CanvasButton.of(createHeaderItem(Material.PAPER,
                "<light_purple><bold>[Slot: Smithing Template]</bold></light_purple>",
                "<gray>Upgrade template (e.g. Netherite Upgrade).</gray>")));
        base.getCanvas().setButton(11, CanvasButton.of(createHeaderItem(Material.ANVIL,
                "<aqua><bold>[Slot: Base Equipment]</bold></aqua>",
                "<gray>Equipment being upgraded (e.g. Diamond Sword).</gray>")));
        base.getCanvas().setButton(12, CanvasButton.of(createHeaderItem(Material.NETHERITE_INGOT,
                "<gold><bold>[Slot: Upgrade Material]</bold></gold>",
                "<gray>Material used to upgrade (e.g. Netherite Ingot).</gray>")));
        base.getCanvas().setButton(15, CanvasButton.of(createHeaderItem(Material.CHEST,
                "<green><bold>[Slot: Upgrade Result]</bold></green>",
                "<gray>Finished upgraded equipment.</gray>")));

        IngredientChoice tChoice = recipe.getSmithingTemplate() != null ? recipe.getSmithingTemplate() : new IngredientChoice("AIR");
        IngredientChoice bChoice = recipe.getSmithingBase() != null ? recipe.getSmithingBase() : new IngredientChoice("AIR");
        IngredientChoice aChoice = recipe.getSmithingAddition() != null ? recipe.getSmithingAddition() : new IngredientChoice("AIR");

        base.getCanvas().setButton(19, createIngredientButton(tChoice, player, choice -> {
            recipe.setSmithingTemplate(choice);
            open(player);
        }));
        base.getCanvas().setButton(20, createIngredientButton(bChoice, player, choice -> {
            recipe.setSmithingBase(choice);
            open(player);
        }));
        base.getCanvas().setButton(21, createIngredientButton(aChoice, player, choice -> {
            recipe.setSmithingAddition(choice);
            open(player);
        }));

        // Result and Arrow
        setupResultAndArrow(base, player, 23, 24, 25, "<yellow>Upgrades Into »</yellow>");
    }

    private void setupAnvilLayout(BaseEditorCanvas base, Player player) {
        // Header Signs
        base.getCanvas().setButton(11, CanvasButton.of(createHeaderItem(Material.BOOK,
                "<aqua><bold>[Slot: Left / Base Item]</bold></aqua>",
                "<gray>Item being repaired, enchanted, or modified.</gray>")));
        base.getCanvas().setButton(13, CanvasButton.of(createHeaderItem(Material.ENCHANTED_BOOK,
                "<gold><bold>[Slot: Right / Material]</bold></gold>",
                "<gray>Sacrificed item, material, or enchanted book.</gray>")));
        base.getCanvas().setButton(15, CanvasButton.of(createHeaderItem(Material.CHEST,
                "<green><bold>[Slot: Anvil Result]</bold></green>",
                "<gray>Final combined item produced.</gray>")));

        IngredientChoice lChoice = recipe.getAnvilLeft() != null ? recipe.getAnvilLeft() : new IngredientChoice("AIR");
        IngredientChoice rChoice = recipe.getAnvilRight() != null ? recipe.getAnvilRight() : new IngredientChoice("AIR");

        base.getCanvas().setButton(20, createIngredientButton(lChoice, player, choice -> {
            recipe.setAnvilLeft(choice);
            open(player);
        }));

        ItemStack anvil = new ItemStack(Material.ANVIL);
        ItemMeta aMeta = anvil.getItemMeta();
        if (aMeta != null) {
            aMeta.displayName(TextUtil.parse("<gray>+</gray>"));
            anvil.setItemMeta(aMeta);
        }
        base.getCanvas().setButton(21, CanvasButton.of(anvil));

        base.getCanvas().setButton(22, createIngredientButton(rChoice, player, choice -> {
            recipe.setAnvilRight(choice);
            open(player);
        }));

        // Result and Arrow
        setupResultAndArrow(base, player, 23, 24, 25, "<yellow>Combines Into »</yellow>");

        // Anvil Cost Button (Slot 31)
        ItemStack costBtn = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta cMeta = costBtn.getItemMeta();
        if (cMeta != null) {
            cMeta.displayName(TextUtil.parse("<green>Level Cost: <white>" + recipe.getAnvilCost() + " Levels</white></green>"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<yellow>Left-Click:</yellow> <gray>Cycle 1, 2, 3, 5, 10, 15</gray>"));
            l.add(TextUtil.parse("<yellow>Right-Click:</yellow> <gray>Custom in Chat</gray>"));
            cMeta.lore(l);
            costBtn.setItemMeta(cMeta);
        }
        base.getCanvas().setButton(31, CanvasButton.of(costBtn, event -> {
            if (event.isRightClick()) {
                promptNumber(player, "<gray>Enter anvil level cost (e.g. <yellow>3</yellow>):</gray>", cost -> {
                    recipe.setAnvilCost(Math.max(1, cost.intValue()));
                    open(player);
                });
            } else {
                int[] cycle = {1, 2, 3, 5, 10, 15};
                int next = 1;
                for (int c : cycle) {
                    if (c > recipe.getAnvilCost()) {
                        next = c;
                        break;
                    }
                }
                recipe.setAnvilCost(next);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                open(player);
            }
        }));
    }

    private void setupBrewingLayout(BaseEditorCanvas base, Player player) {
        // Header Sign
        base.getCanvas().setButton(11, CanvasButton.of(createHeaderItem(Material.BREWING_STAND,
                "<light_purple><bold>[Brewing Stand]</bold></light_purple>",
                "<gray>Place ingredient on top and base potion on bottom.</gray>")));
        base.getCanvas().setButton(15, CanvasButton.of(createHeaderItem(Material.CHEST,
                "<light_purple><bold>[Slot: Brewing Result]</bold></gold>",
                "<gray>Potion or brew produced.</gray>")));

        IngredientChoice ingChoice = recipe.getBrewingIngredient() != null ? recipe.getBrewingIngredient() : new IngredientChoice("AIR");
        IngredientChoice baseChoice = recipe.getBrewingBase() != null ? recipe.getBrewingBase() : new IngredientChoice("AIR");

        // Slot 12: Reagent (Top)
        base.getCanvas().setButton(12, createIngredientButton(ingChoice, player, choice -> {
            recipe.setBrewingIngredient(choice);
            open(player);
        }));

        // Slot 21: Brewing Stand
        ItemStack brewIcon = new ItemStack(Material.BREWING_STAND);
        ItemMeta bMeta = brewIcon.getItemMeta();
        if (bMeta != null) {
            bMeta.displayName(TextUtil.parse("<light_purple>Brewing Stand</light_purple>"));
            brewIcon.setItemMeta(bMeta);
        }
        base.getCanvas().setButton(21, CanvasButton.of(brewIcon));

        // Result and Arrow
        setupResultAndArrow(base, player, 23, 24, 25, "<yellow>Brews Into »</yellow>");

        // Slot 30: Base Potion (Bottom)
        base.getCanvas().setButton(30, createIngredientButton(baseChoice, player, choice -> {
            recipe.setBrewingBase(choice);
            open(player);
        }));
    }

    private ItemStack createHeaderItem(Material mat, String title, String desc) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.parse(title));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse(desc));
            meta.lore(l);
            item.setItemMeta(meta);
        }
        return item;
    }

    private CanvasButton createIngredientButton(IngredientChoice choice, Player player, Consumer<IngredientChoice> onUpdate) {
        boolean isEmpty = choice == null || choice.getRawInput().equalsIgnoreCase("AIR");
        ItemStack display = isEmpty ? null : choice.toDisplayItem(plugin);

        if (display == null) {
            display = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                meta.displayName(TextUtil.parse("<gray><i>[Empty Ingredient Slot]</i></gray>"));
                List<Component> l = new ArrayList<>();
                l.add(TextUtil.parse("<dark_gray>Slot: Currently Empty</dark_gray>"));
                l.add(TextUtil.parse("<dark_gray>────────────────────</dark_gray>"));
                l.add(TextUtil.parse("<yellow>Click with item on cursor:</yellow> <gray>Place ingredient</gray>"));
                l.add(TextUtil.parse("<yellow>Click empty:</yellow> <gray>Set via Chat prompt</gray>"));
                meta.lore(l);
                display.setItemMeta(meta);
            }
        } else {
            display = display.clone();
            display.setAmount(1);
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                if (meta.displayName() == null) {
                    meta.displayName(TextUtil.parse("<aqua>" + choice.getDisplayIdentifier(plugin) + "</aqua>"));
                }
                List<Component> l = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                l.add(Component.empty());
                l.add(TextUtil.parse("<dark_gray>Ingredient: <white>" + choice.getDisplayIdentifier(plugin) + "</white></dark_gray>"));
                l.add(TextUtil.parse("<dark_gray>────────────────────</dark_gray>"));
                l.add(TextUtil.parse("<yellow>Click with item on cursor:</yellow> <gray>Replace ingredient</gray>"));
                l.add(TextUtil.parse("<yellow>Click empty:</yellow> <gray>Set via Chat prompt</gray>"));
                l.add(TextUtil.parse("<red>Right-Click:</red> <gray>Clear slot</gray>"));
                meta.lore(l);
                display.setItemMeta(meta);
            }
        }

        return CanvasButton.of(display, event -> {
            ItemStack cursor = event.getCursor();
            if (cursor != null && cursor.getType() != Material.AIR) {
                // Set from cursor item!
                IngredientChoice fromCursor = IngredientChoice.fromItemStack(cursor);
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
                onUpdate.accept(fromCursor);
            } else if (event.isRightClick()) {
                // Clear slot
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.2f);
                onUpdate.accept(new IngredientChoice("AIR"));
            } else {
                // Chat prompt
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Type Material name (e.g. <yellow>DIAMOND</yellow>) or <yellow>synth:item_id</yellow>:</gray>"),
                        input -> {
                            IngredientChoice parsed = new IngredientChoice(input.trim());
                            onUpdate.accept(parsed);
                        });
            }
        });
    }

    private void handleSave(Player player) {
        // Collect crafting grid if SHAPED or SHAPELESS
        if (recipe.getType() == RecipeType.SHAPED) {
            Map<Character, IngredientChoice> ingredients = new HashMap<>();
            String[] shapeArr = new String[3];
            char nextChar = 'A';

            for (int r = 0; r < 3; r++) {
                StringBuilder row = new StringBuilder();
                for (int c = 0; c < 3; c++) {
                    IngredientChoice ing = grid[r * 3 + c];
                    if (ing == null || ing.getRawInput().equalsIgnoreCase("AIR")) {
                        row.append(' ');
                    } else {
                        // Check if ingredient already assigned a character
                        Character assigned = null;
                        for (Map.Entry<Character, IngredientChoice> entry : ingredients.entrySet()) {
                            if (entry.getValue().getRawInput().equalsIgnoreCase(ing.getRawInput())) {
                                assigned = entry.getKey();
                                break;
                            }
                        }
                        if (assigned != null) {
                            row.append(assigned);
                        } else {
                            ingredients.put(nextChar, ing);
                            row.append(nextChar);
                            nextChar++;
                        }
                    }
                }
                shapeArr[r] = row.toString();
            }

            if (ingredients.isEmpty()) {
                player.sendMessage(TextUtil.parse("<red>Recipe cannot be empty! Please place at least one ingredient.</red>"));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                return;
            }

            recipe.setShape(Arrays.asList(shapeArr));
            recipe.setShapedIngredients(ingredients);
        } else if (recipe.getType() == RecipeType.SHAPELESS) {
            List<IngredientChoice> list = new ArrayList<>();
            for (IngredientChoice ing : grid) {
                if (ing != null && !ing.getRawInput().equalsIgnoreCase("AIR")) {
                    list.add(ing);
                }
            }
            if (list.isEmpty()) {
                player.sendMessage(TextUtil.parse("<red>Recipe cannot be empty! Please place at least one ingredient.</red>"));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                return;
            }
            recipe.setShapelessIngredients(list);
        }

        try {
            // Register item definition in memory so buildResult can immediately resolve it
            plugin.getItemRegistry().register(itemDef);
            plugin.getRecipeManager().saveRecipe(recipe);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            player.sendMessage(lang.getComponent("editor.recipes.saved", "%id%", recipe.getId()));
            onBack.run();
        } catch (Exception e) {
            player.sendMessage(TextUtil.parse("<red>Failed to save recipe: " + e.getMessage() + "</red>"));
            plugin.getLogger().warning("Error saving recipe: " + e.getMessage());
        }
    }

    private void promptNumber(Player player, String message, Consumer<Double> callback) {
        plugin.getChatPromptManager().prompt(player, TextUtil.parse(message), input -> {
            try {
                double val = Double.parseDouble(input.trim());
                callback.accept(val);
            } catch (NumberFormatException e) {
                player.sendMessage(TextUtil.parse("<red>Invalid numeric input: " + input + "</red>"));
                open(player);
            }
        });
    }
}
