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
 * Interactive GUI listing all loot table injections configured for an item.
 * Supports adding, editing, and deleting loot table rules live in-game.
 */
public class LootTableEditorGUI {

    private final SynthetixPlugin plugin;
    private final LanguageManager lang;
    private final ItemDefinition itemDef;
    private final Runnable onBack;

    public LootTableEditorGUI(SynthetixPlugin plugin, ItemDefinition itemDef, Runnable onBack) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
        this.itemDef = itemDef;
        this.onBack = onBack;
    }

    public void open(Player player) {
        BaseEditorCanvas base = new BaseEditorCanvas(plugin, itemDef,
                lang.getComponent("editor.distribution.loot-tables.title"), 5);
        base.setupSideBorders();

        List<LootTableRule> rules = plugin.getDistributionManager().getLootTableInjector().getInjectionsForItem(itemDef.getId());

        if (rules.isEmpty()) {
            ItemStack emptyIcon = new ItemStack(Material.BARRIER);
            ItemMeta eMeta = emptyIcon.getItemMeta();
            if (eMeta != null) {
                eMeta.displayName(TextUtil.parse("<red><bold>No Loot Table Injections</bold></red>"));
                List<Component> l = new ArrayList<>();
                l.add(TextUtil.parse("<gray>No loot tables currently generate this item.</gray>"));
                l.add(Component.empty());
                l.add(TextUtil.parse("<yellow>Click [+ Add Loot Table Injection] below to configure one!</yellow>"));
                eMeta.lore(l);
                emptyIcon.setItemMeta(eMeta);
            }
            base.getCanvas().setButton(22, CanvasButton.of(emptyIcon));
        } else {
            int slot = 10;
            for (LootTableRule rule : rules) {
                ItemStack icon = new ItemStack(Material.CHEST);
                ItemMeta meta = icon.getItemMeta();
                if (meta != null) {
                    meta.displayName(TextUtil.parse("<aqua><bold>Loot Rule: " + rule.getId() + "</bold></aqua>"));
                    List<Component> l = new ArrayList<>();
                    l.add(TextUtil.parse("<gray>Chance: <green>" + (int) (rule.getChance() * 100) + "%</green></gray>"));
                    String amtStr = rule.getMinAmount() == rule.getMaxAmount() ? String.valueOf(rule.getMinAmount()) : rule.getMinAmount() + " - " + rule.getMaxAmount();
                    l.add(TextUtil.parse("<gray>Amount: <white>" + amtStr + "</white></gray>"));
                    l.add(TextUtil.parse("<gray>Rolls: <white>" + rule.getRolls() + "x</white></gray>"));
                    l.add(Component.empty());
                    l.add(TextUtil.parse("<yellow>Target Tables:</yellow>"));
                    for (String pat : rule.getPatterns()) {
                        l.add(TextUtil.parse("<dark_gray>•</dark_gray> <aqua>" + pat + "</aqua>"));
                    }
                    l.add(Component.empty());
                    l.add(TextUtil.parse("<yellow>Left-Click:</yellow> <gray>Edit rule »</gray>"));
                    l.add(TextUtil.parse("<red>Shift-Right-Click:</red> <gray>Delete rule</gray>"));
                    meta.lore(l);
                    icon.setItemMeta(meta);
                }

                LootTableRule currentRule = rule;
                base.getCanvas().setButton(slot++, CanvasButton.of(icon, event -> {
                    if (event.isShiftClick() && event.isRightClick()) {
                        try {
                            plugin.getDistributionManager().getLootTableInjector().deleteRule(currentRule.getId());
                            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                            player.sendMessage(lang.getComponent("editor.distribution.loot-tables.deleted", "%id%", currentRule.getId()));
                            open(player);
                        } catch (Exception e) {
                            player.sendMessage(TextUtil.parse("<red>Failed to delete loot table rule: " + e.getMessage() + "</red>"));
                        }
                    } else {
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                        new LootTableConfigGUI(plugin, itemDef, currentRule, () -> open(player)).open(player);
                    }
                }));

                if (slot == 17) slot = 19;
                if (slot == 26) slot = 28;
                if (slot > 34) break;
            }
        }

        // Add Loot Table Button at slot 41 (Column 5, symmetrical to Back Button at slot 39 / Column 3)
        ItemStack addBtn = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta addMeta = addBtn.getItemMeta();
        if (addMeta != null) {
            addMeta.displayName(lang.getComponent("editor.distribution.loot-tables.add-btn"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<gray>Browse dungeon chests, pyramids,</gray>"));
            l.add(TextUtil.parse("<gray>mines, trial chambers, and archaeology.</gray>"));
            l.add(Component.empty());
            l.add(TextUtil.parse("<yellow>Click to browse loot tables »</yellow>"));
            addMeta.lore(l);
            addBtn.setItemMeta(addMeta);
        }
        base.getCanvas().setButton(41, CanvasButton.of(addBtn, event -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            new LootTableBrowserGUI(plugin, itemDef, () -> open(player)).open(player);
        }));

        base.setBackButton(onBack);
        base.open(player);
    }
}

