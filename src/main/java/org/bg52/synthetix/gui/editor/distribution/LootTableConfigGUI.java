package org.bg52.synthetix.gui.editor.distribution;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.distribution.LootTableRule;
import org.bg52.synthetix.gui.CanvasButton;
import org.bg52.synthetix.gui.editor.BaseEditorCanvas;
import org.bg52.synthetix.item.ItemDefinition;
import org.bg52.synthetix.lang.LanguageManager;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Interactive editor for configuring chance, amounts, and roll count of a loot table injection rule.
 */
public class LootTableConfigGUI {

    private final SynthetixPlugin plugin;
    private final LanguageManager lang;
    private final ItemDefinition itemDef;
    private final Runnable onBack;

    private final String ruleId;
    private final List<String> patterns;
    private double chance;
    private int minAmount;
    private int maxAmount;
    private int rolls;

    public LootTableConfigGUI(SynthetixPlugin plugin, ItemDefinition itemDef, LootTableRule rule, Runnable onBack) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
        this.itemDef = itemDef;
        this.onBack = onBack;

        this.ruleId = rule.getId();
        this.patterns = new ArrayList<>(rule.getPatterns());
        this.chance = rule.getChance();
        this.minAmount = rule.getMinAmount();
        this.maxAmount = rule.getMaxAmount();
        this.rolls = rule.getRolls();
    }

    public void open(Player player) {
        Component title = TextUtil.parse("<dark_gray>Loot Config: <aqua>" + ruleId + "</aqua></dark_gray>");
        BaseEditorCanvas base = new BaseEditorCanvas(plugin, itemDef, title, 5);
        base.setupSideBorders();

        // 1. Target Pattern Header Icon (Slot 13)
        ItemStack header = new ItemStack(Material.CHEST);
        ItemMeta hMeta = header.getItemMeta();
        if (hMeta != null) {
            hMeta.displayName(TextUtil.parse("<aqua><bold>Loot Table Target</bold></aqua>"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<dark_gray>Rule ID: " + ruleId + "</dark_gray>"));
            l.add(TextUtil.parse("<gray>Target Tables:</gray>"));
            for (String p : patterns) {
                l.add(TextUtil.parse("<dark_gray>•</dark_gray> <yellow>" + p + "</yellow>"));
            }
            hMeta.lore(l);
            header.setItemMeta(hMeta);
        }
        base.getCanvas().setButton(13, CanvasButton.of(header));

        // 2. Chance Button (Slot 20)
        ItemStack chanceBtn = new ItemStack(Material.SUNFLOWER);
        ItemMeta chMeta = chanceBtn.getItemMeta();
        if (chMeta != null) {
            chMeta.displayName(TextUtil.parse("<yellow><bold>Injection Chance: " + (int) (chance * 100) + "%</bold></yellow>"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<gray>Probability that the container generates this item.</gray>"));
            l.add(Component.empty());
            l.add(TextUtil.parse("<yellow>Left-Click:</yellow> <gray>Cycle 5%, 10%, 25%, 50%, 100%</gray>"));
            l.add(TextUtil.parse("<yellow>Right-Click:</yellow> <gray>Custom in Chat (e.g. 0.35)</gray>"));
            chMeta.lore(l);
            chanceBtn.setItemMeta(chMeta);
        }
        base.getCanvas().setButton(20, CanvasButton.of(chanceBtn, event -> {
            if (event.isRightClick()) {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter injection chance as decimal (e.g. <yellow>0.30</yellow> for 30%):</gray>"),
                        input -> {
                            try {
                                this.chance = Math.max(0.0, Math.min(1.0, Double.parseDouble(input.trim())));
                            } catch (Exception ignored) {}
                            open(player);
                        });
            } else {
                double[] presets = {0.05, 0.10, 0.25, 0.50, 1.0};
                double next = 0.05;
                for (double p : presets) {
                    if (p > chance + 0.005) {
                        next = p;
                        break;
                    }
                }
                this.chance = next;
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                open(player);
            }
        }));

        // 3. Amount Range Button (Slot 22)
        ItemStack amountBtn = new ItemStack(Material.HOPPER);
        ItemMeta amMeta = amountBtn.getItemMeta();
        if (amMeta != null) {
            String amtStr = minAmount == maxAmount ? String.valueOf(minAmount) : minAmount + " - " + maxAmount;
            amMeta.displayName(TextUtil.parse("<aqua><bold>Stack Amount: " + amtStr + "</bold></aqua>"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<gray>Stack size inserted into the container.</gray>"));
            l.add(Component.empty());
            l.add(TextUtil.parse("<yellow>Left-Click:</yellow> <gray>Cycle 1, 1-2, 1-3, 2-5</gray>"));
            l.add(TextUtil.parse("<yellow>Right-Click:</yellow> <gray>Custom in Chat</gray>"));
            amMeta.lore(l);
            amountBtn.setItemMeta(amMeta);
        }
        base.getCanvas().setButton(22, CanvasButton.of(amountBtn, event -> {
            if (event.isRightClick()) {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter stack amount (e.g. <yellow>1</yellow> or <yellow>1-3</yellow>):</gray>"),
                        input -> {
                            try {
                                String clean = input.trim();
                                if (clean.contains("-")) {
                                    String[] parts = clean.split("-");
                                    minAmount = Math.max(1, Integer.parseInt(parts[0].trim()));
                                    maxAmount = Math.max(minAmount, Integer.parseInt(parts[1].trim()));
                                } else {
                                    int val = Math.max(1, Integer.parseInt(clean));
                                    minAmount = val;
                                    maxAmount = val;
                                }
                            } catch (Exception ignored) {}
                            open(player);
                        });
            } else {
                if (minAmount == 1 && maxAmount == 1) {
                    minAmount = 1; maxAmount = 2;
                } else if (minAmount == 1 && maxAmount == 2) {
                    minAmount = 1; maxAmount = 3;
                } else if (minAmount == 1 && maxAmount == 3) {
                    minAmount = 2; maxAmount = 5;
                } else {
                    minAmount = 1; maxAmount = 1;
                }
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                open(player);
            }
        }));

        // 4. Rolls Button (Slot 24)
        ItemStack rollsBtn = new ItemStack(Material.REPEATER);
        ItemMeta rMeta = rollsBtn.getItemMeta();
        if (rMeta != null) {
            rMeta.displayName(TextUtil.parse("<gold><bold>Roll Attempts: " + rolls + "x</bold></gold>"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<gray>Number of independent roll attempts per chest.</gray>"));
            l.add(Component.empty());
            l.add(TextUtil.parse("<yellow>Click to cycle 1x, 2x, 3x, 4x »</yellow>"));
            rMeta.lore(l);
            rollsBtn.setItemMeta(rMeta);
        }
        base.getCanvas().setButton(24, CanvasButton.of(rollsBtn, event -> {
            rolls = rolls >= 4 ? 1 : rolls + 1;
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            open(player);
        }));

        // Save Button at slot 41 (Column 5, symmetrical to Back Button at slot 39 / Column 3)
        ItemStack saveBtn = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta sMeta = saveBtn.getItemMeta();
        if (sMeta != null) {
            sMeta.displayName(TextUtil.parse("<green><bold>💾 Save Loot Rule</bold></green>"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<gray>Saves rule directly to</gray>"));
            l.add(TextUtil.parse("<yellow>distribution/loot_tables.yml</yellow>"));
            l.add(Component.empty());
            l.add(TextUtil.parse("<yellow>Click to save »</yellow>"));
            sMeta.lore(l);
            saveBtn.setItemMeta(sMeta);
        }
        base.getCanvas().setButton(41, CanvasButton.of(saveBtn, event -> {
            LootTableRule newRule = new LootTableRule(ruleId, "synth:" + itemDef.getId(),
                    patterns, chance, minAmount, maxAmount, rolls);
            try {
                plugin.getDistributionManager().getLootTableInjector().saveRule(newRule);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                player.sendMessage(lang.getComponent("editor.distribution.loot-tables.saved", "%id%", ruleId));
                onBack.run();
            } catch (Exception e) {
                player.sendMessage(TextUtil.parse("<red>Failed to save loot table rule: " + e.getMessage() + "</red>"));
            }
        }));

        base.setBackButton(onBack);
        base.open(player);
    }
}

