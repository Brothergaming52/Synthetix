package org.bg52.synthetix.gui.editor.distribution;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.distribution.TradeRule;
import org.bg52.synthetix.gui.CanvasButton;
import org.bg52.synthetix.gui.editor.BaseEditorCanvas;
import org.bg52.synthetix.item.ItemDefinition;
import org.bg52.synthetix.lang.LanguageManager;
import org.bg52.synthetix.recipe.IngredientChoice;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Interactive trade builder canvas for configuring primary and secondary costs,
 * villager mastery levels, offer chances, and stock limits.
 */
public class TradeBuilderGUI {

    private final SynthetixPlugin plugin;
    private final LanguageManager lang;
    private final ItemDefinition itemDef;
    private final Runnable onBack;

    private final String ruleId;
    private final String profession;
    private int level;
    private IngredientChoice cost1;
    private int cost1Amount;
    private IngredientChoice cost2;
    private int cost2Amount;
    private int resultAmount;
    private int maxUses;
    private int villagerExperience;
    private float priceMultiplier;
    private double chance;

    public TradeBuilderGUI(SynthetixPlugin plugin, ItemDefinition itemDef, TradeRule rule, Runnable onBack) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
        this.itemDef = itemDef;
        this.onBack = onBack;

        this.ruleId = rule.getId();
        this.profession = rule.getProfession();
        this.level = rule.getLevel();
        this.cost1 = rule.getCost1() != null ? rule.getCost1() : new IngredientChoice("EMERALD");
        this.cost1Amount = rule.getCost1Amount();
        this.cost2 = rule.getCost2();
        this.cost2Amount = rule.getCost2Amount();
        this.resultAmount = rule.getResultAmount();
        this.maxUses = rule.getMaxUses();
        this.villagerExperience = rule.getVillagerExperience();
        this.priceMultiplier = rule.getPriceMultiplier();
        this.chance = rule.getChance();
    }

    public void open(Player player) {
        Component title = TextUtil.parse("<dark_gray>Trade: <green>" + TextUtil.prettify(profession) + "</green></dark_gray>");
        BaseEditorCanvas base = new BaseEditorCanvas(plugin, itemDef, title, 6);
        base.setupSideBorders();

        // 1. Header Signs in Row 1 indicating what each slot represents
        ItemStack h1 = new ItemStack(Material.EMERALD);
        ItemMeta h1Meta = h1.getItemMeta();
        if (h1Meta != null) {
            h1Meta.displayName(TextUtil.parse("<green><bold>[Slot 1] Primary Payment</bold></green>"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<gray>Main currency or payment item that the</gray>"));
            l.add(TextUtil.parse("<gray>player pays to the merchant.</gray>"));
            h1Meta.lore(l);
            h1.setItemMeta(h1Meta);
        }
        base.getCanvas().setButton(11, CanvasButton.of(h1));

        ItemStack h2 = new ItemStack(Material.BOOK);
        ItemMeta h2Meta = h2.getItemMeta();
        if (h2Meta != null) {
            h2Meta.displayName(TextUtil.parse("<yellow><bold>[Slot 2] Secondary Payment (Optional)</bold></yellow>"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<gray>Optional additional payment required</gray>"));
            l.add(TextUtil.parse("<gray>alongside the primary cost.</gray>"));
            h2Meta.lore(l);
            h2.setItemMeta(h2Meta);
        }
        base.getCanvas().setButton(13, CanvasButton.of(h2));

        ItemStack h3 = new ItemStack(Material.CHEST);
        ItemMeta h3Meta = h3.getItemMeta();
        if (h3Meta != null) {
            h3Meta.displayName(TextUtil.parse("<gold><bold>[Slot 3] Trade Offering (Merchant Sells)</bold></gold>"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<gray>The custom item and quantity that the</gray>"));
            l.add(TextUtil.parse("<gray>player receives from this trade.</gray>"));
            h3Meta.lore(l);
            h3.setItemMeta(h3Meta);
        }
        base.getCanvas().setButton(16, CanvasButton.of(h3));

        // 2. Cost 1 Slot (Slot 20)
        ItemStack c1Item = cost1.toDisplayItem(plugin);
        if (c1Item == null) c1Item = new ItemStack(Material.EMERALD);
        c1Item = c1Item.clone();
        c1Item.setAmount(cost1Amount);
        ItemMeta c1Meta = c1Item.getItemMeta();
        if (c1Meta != null) {
            c1Meta.displayName(TextUtil.parse("<green><bold>[Slot 1: Pay] Primary: " + cost1Amount + "x " + cost1.getDisplayIdentifier(plugin) + "</bold></green>"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<dark_gray>Item: <white>" + cost1.getDisplayIdentifier(plugin) + "</white></dark_gray>"));
            l.add(TextUtil.parse("<dark_gray>Amount: <white>" + cost1Amount + "x</white></dark_gray>"));
            l.add(TextUtil.parse("<dark_gray>────────────────────</dark_gray>"));
            l.add(TextUtil.parse("<yellow>Click with item on cursor:</yellow> <gray>Place item & match amount</gray>"));
            l.add(TextUtil.parse("<yellow>Left-Click:</yellow> <gray>+1 Amount (Max 64)</gray>"));
            l.add(TextUtil.parse("<yellow>Right-Click:</yellow> <gray>-1 Amount</gray>"));
            l.add(TextUtil.parse("<yellow>Shift-Left-Click:</yellow> <gray>Set via Chat prompt</gray>"));
            c1Meta.lore(l);
            c1Item.setItemMeta(c1Meta);
        }
        base.getCanvas().setButton(20, CanvasButton.of(c1Item, event -> {
            ItemStack cursor = event.getCursor();
            if (cursor != null && cursor.getType() != Material.AIR) {
                this.cost1 = IngredientChoice.fromItemStack(cursor);
                this.cost1Amount = Math.max(1, Math.min(64, cursor.getAmount()));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
                open(player);
            } else if (event.isShiftClick()) {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Type Material name (e.g. <yellow>EMERALD</yellow>) or <yellow>synth:item_id</yellow>:</gray>"),
                        input -> {
                            this.cost1 = new IngredientChoice(input.trim());
                            open(player);
                        });
            } else if (event.isRightClick()) {
                this.cost1Amount = Math.max(1, this.cost1Amount - 1);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                open(player);
            } else {
                this.cost1Amount = Math.min(64, this.cost1Amount + 1);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                open(player);
            }
        }));

        // Plus Icon (Slot 21)
        ItemStack plusIcon = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta plusMeta = plusIcon.getItemMeta();
        if (plusMeta != null) {
            plusMeta.displayName(TextUtil.parse("<dark_gray>+</dark_gray>"));
            plusIcon.setItemMeta(plusMeta);
        }
        base.getCanvas().setButton(21, CanvasButton.of(plusIcon));

        // 3. Cost 2 Slot (Slot 22) - Optional
        ItemStack c2Item;
        if (cost2 == null || cost2.getRawInput().equalsIgnoreCase("AIR")) {
            c2Item = new ItemStack(Material.BARRIER);
            ItemMeta c2Meta = c2Item.getItemMeta();
            if (c2Meta != null) {
                c2Meta.displayName(TextUtil.parse("<yellow><bold>[Slot 2: Pay] Secondary: <gray><i>None (Optional)</i></gray></bold></yellow>"));
                List<Component> l = new ArrayList<>();
                l.add(TextUtil.parse("<gray>No secondary item required.</gray>"));
                l.add(TextUtil.parse("<dark_gray>────────────────────</dark_gray>"));
                l.add(TextUtil.parse("<yellow>Click with item on cursor:</yellow> <gray>Add second cost</gray>"));
                l.add(TextUtil.parse("<yellow>Click empty:</yellow> <gray>Set via Chat prompt</gray>"));
                c2Meta.lore(l);
                c2Item.setItemMeta(c2Meta);
            }
        } else {
            c2Item = cost2.toDisplayItem(plugin);
            if (c2Item == null) c2Item = new ItemStack(Material.PAPER);
            c2Item = c2Item.clone();
            c2Item.setAmount(cost2Amount);
            ItemMeta c2Meta = c2Item.getItemMeta();
            if (c2Meta != null) {
                c2Meta.displayName(TextUtil.parse("<yellow><bold>[Slot 2: Pay] Secondary: " + cost2Amount + "x " + cost2.getDisplayIdentifier(plugin) + "</bold></yellow>"));
                List<Component> l = new ArrayList<>();
                l.add(TextUtil.parse("<dark_gray>Item: <white>" + cost2.getDisplayIdentifier(plugin) + "</white></dark_gray>"));
                l.add(TextUtil.parse("<dark_gray>Amount: <white>" + cost2Amount + "x</white></dark_gray>"));
                l.add(TextUtil.parse("<dark_gray>────────────────────</dark_gray>"));
                l.add(TextUtil.parse("<yellow>Click with item on cursor:</yellow> <gray>Replace item</gray>"));
                l.add(TextUtil.parse("<yellow>Left-Click:</yellow> <gray>+1 Amount</gray>"));
                l.add(TextUtil.parse("<yellow>Right-Click:</yellow> <gray>-1 Amount</gray>"));
                l.add(TextUtil.parse("<red>Shift-Right-Click:</red> <gray>Remove secondary cost</gray>"));
                c2Meta.lore(l);
                c2Item.setItemMeta(c2Meta);
            }
        }
        base.getCanvas().setButton(22, CanvasButton.of(c2Item, event -> {
            ItemStack cursor = event.getCursor();
            if (cursor != null && cursor.getType() != Material.AIR) {
                this.cost2 = IngredientChoice.fromItemStack(cursor);
                this.cost2Amount = Math.max(1, Math.min(64, cursor.getAmount()));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
                open(player);
            } else if (event.isShiftClick() && event.isRightClick()) {
                this.cost2 = null;
                this.cost2Amount = 1;
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                open(player);
            } else if (cost2 != null) {
                if (event.isRightClick()) {
                    this.cost2Amount = Math.max(1, this.cost2Amount - 1);
                } else {
                    this.cost2Amount = Math.min(64, this.cost2Amount + 1);
                }
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                open(player);
            } else {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Type secondary cost Material (e.g. <yellow>DIAMOND</yellow>) or <yellow>synth:item_id</yellow>:</gray>"),
                        input -> {
                            this.cost2 = new IngredientChoice(input.trim());
                            this.cost2Amount = 1;
                            open(player);
                        });
            }
        }));

        // Arrow Indicator (Slot 24)
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta aMeta = arrow.getItemMeta();
        if (aMeta != null) {
            aMeta.displayName(TextUtil.parse("<yellow>Trade Exchange »</yellow>"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<gray>Player gives payment on left,</gray>"));
            l.add(TextUtil.parse("<gray>receives offering on right.</gray>"));
            aMeta.lore(l);
            arrow.setItemMeta(aMeta);
        }
        base.getCanvas().setButton(24, CanvasButton.of(arrow));

        // 4. Result Preview Slot (Slot 25)
        ItemStack resItem = itemDef.buildItemStack();
        resItem.setAmount(resultAmount);
        ItemMeta rMeta = resItem.getItemMeta();
        if (rMeta != null) {
            String nameStr = itemDef.getDisplayName() != null ? TextUtil.toPlain(TextUtil.parse(itemDef.getDisplayName())) : TextUtil.prettify(itemDef.getId());
            rMeta.displayName(TextUtil.parse("<gold><bold>[Slot 3: Receive] Offering: " + resultAmount + "x " + nameStr + "</bold></gold>"));
            List<Component> l = rMeta.hasLore() ? new ArrayList<>(rMeta.lore()) : new ArrayList<>();
            l.add(Component.empty());
            l.add(TextUtil.parse("<dark_gray>────────────────────</dark_gray>"));
            l.add(TextUtil.parse("<gray>Item received from the merchant.</gray>"));
            l.add(TextUtil.parse("<yellow>Left-Click:</yellow> <gray>+1 Amount</gray>"));
            l.add(TextUtil.parse("<yellow>Right-Click:</yellow> <gray>-1 Amount</gray>"));
            l.add(TextUtil.parse("<yellow>Shift-Left-Click:</yellow> <gray>Cycle 1, 2, 4, 8, 16, 32, 64</gray>"));
            rMeta.lore(l);
            resItem.setItemMeta(rMeta);
        }
        base.getCanvas().setButton(25, CanvasButton.of(resItem, event -> {
            if (event.isShiftClick()) {
                int[] presets = {1, 2, 4, 8, 16, 32, 64};
                int next = 1;
                for (int p : presets) {
                    if (p > resultAmount) {
                        next = p;
                        break;
                    }
                }
                this.resultAmount = next;
            } else if (event.isRightClick()) {
                this.resultAmount = Math.max(1, resultAmount - 1);
            } else {
                this.resultAmount = Math.min(64, resultAmount + 1);
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            open(player);
        }));

        // 4. Villager Mastery Level Button (Slot 29)
        ItemStack lvlBtn = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta lMeta = lvlBtn.getItemMeta();
        if (lMeta != null) {
            String lvlName = switch (level) {
                case 1 -> "Novice (1)";
                case 2 -> "Apprentice (2)";
                case 3 -> "Journeyman (3)";
                case 4 -> "Expert (4)";
                case 5 -> "Master (5)";
                default -> "Level " + level;
            };
            lMeta.displayName(TextUtil.parse("<gold><bold>Required Level: " + lvlName + "</bold></gold>"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<gray>Minimum villager profession level required</gray>"));
            l.add(TextUtil.parse("<gray>for this trade offer to be unlocked.</gray>"));
            l.add(Component.empty());
            l.add(TextUtil.parse("<yellow>Click to cycle 1 -> 5 »</yellow>"));
            lMeta.lore(l);
            lvlBtn.setItemMeta(lMeta);
        }
        base.getCanvas().setButton(29, CanvasButton.of(lvlBtn, event -> {
            level = level >= 5 ? 1 : level + 1;
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            open(player);
        }));

        // 5. Unlock Chance Button (Slot 31)
        ItemStack chBtn = new ItemStack(Material.SUNFLOWER);
        ItemMeta chMeta = chBtn.getItemMeta();
        if (chMeta != null) {
            chMeta.displayName(TextUtil.parse("<yellow><bold>Offer Chance: " + (int) (chance * 100) + "%</bold></yellow>"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<gray>Chance that a leveling villager learns this trade.</gray>"));
            l.add(Component.empty());
            l.add(TextUtil.parse("<yellow>Left-Click:</yellow> <gray>Cycle 25%, 50%, 75%, 100%</gray>"));
            l.add(TextUtil.parse("<yellow>Right-Click:</yellow> <gray>Custom in Chat</gray>"));
            chMeta.lore(l);
            chBtn.setItemMeta(chMeta);
        }
        base.getCanvas().setButton(31, CanvasButton.of(chBtn, event -> {
            if (event.isRightClick()) {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter offer chance as decimal (e.g. <yellow>0.50</yellow> for 50%):</gray>"),
                        input -> {
                            try {
                                this.chance = Math.max(0.0, Math.min(1.0, Double.parseDouble(input.trim())));
                            } catch (Exception ignored) {}
                            open(player);
                        });
            } else {
                double[] cycle = {0.25, 0.50, 0.75, 1.0};
                double next = 0.25;
                for (double c : cycle) {
                    if (c > chance + 0.05) {
                        next = c;
                        break;
                    }
                }
                this.chance = next;
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                open(player);
            }
        }));

        // 6. Max Stock Uses Button (Slot 33)
        ItemStack useBtn = new ItemStack(Material.REPEATER);
        ItemMeta uMeta = useBtn.getItemMeta();
        if (uMeta != null) {
            uMeta.displayName(TextUtil.parse("<aqua><bold>Max Stock Uses: " + maxUses + "</bold></aqua>"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<gray>Trades allowed before the villager must restock.</gray>"));
            l.add(Component.empty());
            l.add(TextUtil.parse("<yellow>Click to cycle 4, 8, 12, 16, 32 »</yellow>"));
            uMeta.lore(l);
            useBtn.setItemMeta(uMeta);
        }
        base.getCanvas().setButton(33, CanvasButton.of(useBtn, event -> {
            int[] cycle = {4, 8, 12, 16, 32};
            int next = 4;
            for (int c : cycle) {
                if (c > maxUses) {
                    next = c;
                    break;
                }
            }
            this.maxUses = next;
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            open(player);
        }));

        // 7. Save Button at slot 50 (Column 5, symmetrical to Back Button at slot 48 / Column 3)
        ItemStack saveBtn = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta sMeta = saveBtn.getItemMeta();
        if (sMeta != null) {
            sMeta.displayName(TextUtil.parse("<green><bold>💾 Save Merchant Trade</bold></green>"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<gray>Saves trade directly to</gray>"));
            l.add(TextUtil.parse("<yellow>distribution/trades.yml</yellow>"));
            l.add(Component.empty());
            l.add(TextUtil.parse("<yellow>Click to save »</yellow>"));
            sMeta.lore(l);
            saveBtn.setItemMeta(sMeta);
        }
        base.getCanvas().setButton(50, CanvasButton.of(saveBtn, event -> {
            String c2Str = (cost2 != null && !cost2.getRawInput().equalsIgnoreCase("AIR")) ? cost2.getRawInput() : null;
            TradeRule newRule = new TradeRule(ruleId, profession, level,
                    cost1.getRawInput(), cost1Amount,
                    c2Str, cost2Amount,
                    "synth:" + itemDef.getId(), resultAmount,
                    maxUses, villagerExperience, priceMultiplier, chance);

            try {
                plugin.getDistributionManager().getTradeInjector().saveRule(newRule);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                player.sendMessage(lang.getComponent("editor.distribution.trades.saved", "%id%", ruleId));
                onBack.run();
            } catch (Exception e) {
                player.sendMessage(TextUtil.parse("<red>Failed to save trade: " + e.getMessage() + "</red>"));
            }
        }));

        base.setBackButton(onBack);
        base.open(player);
    }
}

