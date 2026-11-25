package org.bg52.synthetix.gui.editor.distribution;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.distribution.MobDropRule;
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
 * Interactive editor for configuring chance, amounts, and conditions of a mob drop rule.
 */
public class MobDropConfigGUI {

    private final SynthetixPlugin plugin;
    private final LanguageManager lang;
    private final ItemDefinition itemDef;
    private final Runnable onBack;

    private final String ruleId;
    private final String entityType;
    private double chance;
    private double lootingBonus;
    private int minAmount;
    private int maxAmount;
    private boolean playerKillOnly;

    public MobDropConfigGUI(SynthetixPlugin plugin, ItemDefinition itemDef, MobDropRule rule, Runnable onBack) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
        this.itemDef = itemDef;
        this.onBack = onBack;

        this.ruleId = rule.getId();
        this.entityType = rule.getEntityType();
        this.chance = rule.getChance();
        this.lootingBonus = rule.getLootingBonus();
        this.minAmount = rule.getMinAmount();
        this.maxAmount = rule.getMaxAmount();
        this.playerKillOnly = rule.isPlayerKillOnly();
    }

    public void open(Player player) {
        Component title = TextUtil.parse("<dark_gray>Mob Drop: <gold>" + TextUtil.prettify(entityType) + "</gold></dark_gray>");
        BaseEditorCanvas base = new BaseEditorCanvas(plugin, itemDef, title, 5);
        base.setupSideBorders();

        // 1. Entity Header Icon (Slot 13)
        ItemStack mobHeader = new ItemStack(MobDropEditorGUI.resolveMobIcon(entityType));
        ItemMeta mhMeta = mobHeader.getItemMeta();
        if (mhMeta != null) {
            mhMeta.displayName(TextUtil.parse("<gold><bold>" + TextUtil.prettify(entityType) + "</bold></gold>"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<dark_gray>Rule ID: " + ruleId + "</dark_gray>"));
            l.add(TextUtil.parse("<gray>Target Item: <white>" + itemDef.getId() + "</white></gray>"));
            mhMeta.lore(l);
            mobHeader.setItemMeta(mhMeta);
        }
        base.getCanvas().setButton(13, CanvasButton.of(mobHeader));

        // 2. Chance Button (Slot 20)
        ItemStack chanceBtn = new ItemStack(Material.SUNFLOWER);
        ItemMeta chMeta = chanceBtn.getItemMeta();
        if (chMeta != null) {
            chMeta.displayName(TextUtil.parse("<yellow><bold>Drop Chance: " + (int) (chance * 100) + "%</bold></yellow>"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<gray>Probability that this mob drops the item.</gray>"));
            l.add(Component.empty());
            l.add(TextUtil.parse("<yellow>Left-Click:</yellow> <gray>Cycle 1%, 5%, 10%, 25%, 50%, 100%</gray>"));
            l.add(TextUtil.parse("<yellow>Right-Click:</yellow> <gray>Custom in Chat (e.g. 0.15)</gray>"));
            chMeta.lore(l);
            chanceBtn.setItemMeta(chMeta);
        }
        base.getCanvas().setButton(20, CanvasButton.of(chanceBtn, event -> {
            if (event.isRightClick()) {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter drop chance as decimal (e.g. <yellow>0.15</yellow> for 15%):</gray>"),
                        input -> {
                            try {
                                this.chance = Math.max(0.0, Math.min(1.0, Double.parseDouble(input.trim())));
                            } catch (Exception ignored) {}
                            open(player);
                        });
            } else {
                double[] presets = {0.01, 0.05, 0.10, 0.25, 0.50, 1.0};
                double next = 0.01;
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
        ItemStack amountBtn = new ItemStack(Material.CHEST);
        ItemMeta amMeta = amountBtn.getItemMeta();
        if (amMeta != null) {
            String amtStr = minAmount == maxAmount ? String.valueOf(minAmount) : minAmount + " - " + maxAmount;
            amMeta.displayName(TextUtil.parse("<aqua><bold>Drop Amount: " + amtStr + "</bold></aqua>"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<gray>Stack size dropped on death.</gray>"));
            l.add(Component.empty());
            l.add(TextUtil.parse("<yellow>Left-Click:</yellow> <gray>Cycle 1, 1-2, 1-3, 2-5</gray>"));
            l.add(TextUtil.parse("<yellow>Right-Click:</yellow> <gray>Custom in Chat (e.g. 1-4)</gray>"));
            amMeta.lore(l);
            amountBtn.setItemMeta(amMeta);
        }
        base.getCanvas().setButton(22, CanvasButton.of(amountBtn, event -> {
            if (event.isRightClick()) {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter drop amount (e.g. <yellow>1</yellow> or <yellow>1-3</yellow>):</gray>"),
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

        // 4. Looting Bonus Button (Slot 24)
        ItemStack lootBtn = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta lMeta = lootBtn.getItemMeta();
        if (lMeta != null) {
            lMeta.displayName(TextUtil.parse("<light_purple><bold>Looting Bonus: +" + (int) (lootingBonus * 100) + "% / lvl</bold></light_purple>"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<gray>Extra chance added per Looting enchantment level.</gray>"));
            l.add(Component.empty());
            l.add(TextUtil.parse("<yellow>Left-Click:</yellow> <gray>Cycle 0%, +1%, +2%, +3%, +5%</gray>"));
            l.add(TextUtil.parse("<yellow>Right-Click:</yellow> <gray>Custom in Chat</gray>"));
            lMeta.lore(l);
            lootBtn.setItemMeta(lMeta);
        }
        base.getCanvas().setButton(24, CanvasButton.of(lootBtn, event -> {
            if (event.isRightClick()) {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter looting bonus per level (e.g. <yellow>0.03</yellow> for +3%):</gray>"),
                        input -> {
                            try {
                                lootingBonus = Math.max(0.0, Double.parseDouble(input.trim()));
                            } catch (Exception ignored) {}
                            open(player);
                        });
            } else {
                double[] presets = {0.0, 0.01, 0.02, 0.03, 0.05};
                double next = 0.0;
                for (double p : presets) {
                    if (p > lootingBonus + 0.005) {
                        next = p;
                        break;
                    }
                }
                lootingBonus = next;
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                open(player);
            }
        }));

        // 5. Player Kill Only Toggle (Slot 31)
        ItemStack pkBtn = new ItemStack(playerKillOnly ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta pkMeta = pkBtn.getItemMeta();
        if (pkMeta != null) {
            pkMeta.displayName(TextUtil.parse("<gold><bold>Player Kill Only: " + (playerKillOnly ? "<green>ENABLED</green>" : "<red>DISABLED</red>") + "</bold></gold>"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<gray>Requires a direct player kill (prevents automated</gray>"));
            l.add(TextUtil.parse("<gray>AFK farm exploitation when enabled).</gray>"));
            l.add(Component.empty());
            l.add(TextUtil.parse("<yellow>Click to toggle »</yellow>"));
            pkMeta.lore(l);
            pkBtn.setItemMeta(pkMeta);
        }
        base.getCanvas().setButton(31, CanvasButton.of(pkBtn, event -> {
            playerKillOnly = !playerKillOnly;
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, playerKillOnly ? 1.2f : 0.8f);
            open(player);
        }));

        // Save Button at slot 41 (Column 5, symmetrical to Back Button at slot 39 / Column 3)
        ItemStack saveBtn = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta sMeta = saveBtn.getItemMeta();
        if (sMeta != null) {
            sMeta.displayName(TextUtil.parse("<green><bold>💾 Save Mob Drop</bold></green>"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<gray>Saves drop rule directly to</gray>"));
            l.add(TextUtil.parse("<yellow>distribution/mob_drops.yml</yellow>"));
            l.add(Component.empty());
            l.add(TextUtil.parse("<yellow>Click to save »</yellow>"));
            sMeta.lore(l);
            saveBtn.setItemMeta(sMeta);
        }
        base.getCanvas().setButton(41, CanvasButton.of(saveBtn, event -> {
            MobDropRule newRule = new MobDropRule(ruleId, "synth:" + itemDef.getId(), entityType,
                    chance, lootingBonus, minAmount, maxAmount, playerKillOnly, null, null);
            try {
                plugin.getDistributionManager().getMobDropManager().saveRule(newRule);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                player.sendMessage(lang.getComponent("editor.distribution.mob-drops.saved", "%id%", ruleId));
                onBack.run();
            } catch (Exception e) {
                player.sendMessage(TextUtil.parse("<red>Failed to save mob drop rule: " + e.getMessage() + "</red>"));
            }
        }));

        base.setBackButton(onBack);
        base.open(player);
    }
}

