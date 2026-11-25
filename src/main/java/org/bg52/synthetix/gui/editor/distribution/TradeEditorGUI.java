package org.bg52.synthetix.gui.editor.distribution;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.distribution.TradeRule;
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
 * Interactive GUI listing all merchant and villager trades configured for an item.
 * Supports adding, editing, and deleting trades live in-game.
 */
public class TradeEditorGUI {

    private final SynthetixPlugin plugin;
    private final LanguageManager lang;
    private final ItemDefinition itemDef;
    private final Runnable onBack;

    public TradeEditorGUI(SynthetixPlugin plugin, ItemDefinition itemDef, Runnable onBack) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
        this.itemDef = itemDef;
        this.onBack = onBack;
    }

    public void open(Player player) {
        BaseEditorCanvas base = new BaseEditorCanvas(plugin, itemDef,
                lang.getComponent("editor.distribution.trades.title"), 5);
        base.setupSideBorders();

        List<TradeRule> trades = plugin.getDistributionManager().getTradeInjector().getTradesForItem(itemDef.getId());

        if (trades.isEmpty()) {
            ItemStack emptyIcon = new ItemStack(Material.BARRIER);
            ItemMeta eMeta = emptyIcon.getItemMeta();
            if (eMeta != null) {
                eMeta.displayName(TextUtil.parse("<red><bold>No Merchant Trades Configured</bold></red>"));
                List<Component> l = new ArrayList<>();
                l.add(TextUtil.parse("<gray>No villagers or traders currently offer this item.</gray>"));
                l.add(Component.empty());
                l.add(TextUtil.parse("<yellow>Click [+ Add Merchant Trade] below to configure one!</yellow>"));
                eMeta.lore(l);
                emptyIcon.setItemMeta(eMeta);
            }
            base.getCanvas().setButton(22, CanvasButton.of(emptyIcon));
        } else {
            int slot = 10;
            for (TradeRule trade : trades) {
                ItemStack icon = new ItemStack(Material.EMERALD);
                ItemMeta meta = icon.getItemMeta();
                if (meta != null) {
                    meta.displayName(TextUtil.parse("<green><bold>Trade: " + TextUtil.prettify(trade.getProfession()) + "</bold></green>"));
                    List<Component> l = new ArrayList<>();
                    l.add(TextUtil.parse("<dark_gray>Rule ID: " + trade.getId() + "</dark_gray>"));
                    l.add(TextUtil.parse("<gray>Villager Level: <gold>" + trade.getLevel() + "</gold></gray>"));
                    l.add(TextUtil.parse("<dark_gray>────────────────────</dark_gray>"));
                    l.add(TextUtil.parse("<green><bold>[Slot 1: Pay]</bold></green> <white>" + trade.getCost1Amount() + "x " + trade.getCost1().getDisplayIdentifier(plugin) + "</white>"));
                    if (trade.getCost2() != null && !trade.getCost2().getRawInput().equalsIgnoreCase("AIR")) {
                        l.add(TextUtil.parse("<yellow><bold>[Slot 2: Pay]</bold></yellow> <white>" + trade.getCost2Amount() + "x " + trade.getCost2().getDisplayIdentifier(plugin) + "</white>"));
                    }
                    String resName = itemDef.getDisplayName() != null ? TextUtil.toPlain(TextUtil.parse(itemDef.getDisplayName())) : TextUtil.prettify(itemDef.getId());
                    l.add(TextUtil.parse("<gold><bold>[Slot 3: Receive]</bold></gold> <white>" + trade.getResultAmount() + "x " + resName + "</white>"));
                    l.add(TextUtil.parse("<dark_gray>────────────────────</dark_gray>"));
                    l.add(TextUtil.parse("<gray>Unlock Chance: <green>" + (int) (trade.getChance() * 100) + "%</green></gray>"));
                    l.add(TextUtil.parse("<gray>Max Stock Uses: <white>" + trade.getMaxUses() + "</white></gray>"));
                    l.add(Component.empty());
                    l.add(TextUtil.parse("<yellow>Left-Click:</yellow> <gray>Edit trade »</gray>"));
                    l.add(TextUtil.parse("<red>Shift-Right-Click:</red> <gray>Delete trade</gray>"));
                    meta.lore(l);
                    icon.setItemMeta(meta);
                }

                TradeRule currentTrade = trade;
                base.getCanvas().setButton(slot++, CanvasButton.of(icon, event -> {
                    if (event.isShiftClick() && event.isRightClick()) {
                        try {
                            plugin.getDistributionManager().getTradeInjector().deleteRule(currentTrade.getId());
                            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                            player.sendMessage(lang.getComponent("editor.distribution.trades.deleted", "%id%", currentTrade.getId()));
                            open(player);
                        } catch (Exception e) {
                            player.sendMessage(TextUtil.parse("<red>Failed to delete trade: " + e.getMessage() + "</red>"));
                        }
                    } else {
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                        new TradeBuilderGUI(plugin, itemDef, currentTrade, () -> open(player)).open(player);
                    }
                }));

                if (slot == 17) slot = 19;
                if (slot == 26) slot = 28;
                if (slot > 34) break;
            }
        }

        // Add Trade Button at slot 41 (Column 5, symmetrical to Back Button at slot 39 / Column 3)
        ItemStack addBtn = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta addMeta = addBtn.getItemMeta();
        if (addMeta != null) {
            addMeta.displayName(lang.getComponent("editor.distribution.trades.add-btn"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<gray>Configure a villager profession or wandering</gray>"));
            l.add(TextUtil.parse("<gray>trader to sell this item for custom prices.</gray>"));
            l.add(Component.empty());
            l.add(TextUtil.parse("<yellow>Click to select profession »</yellow>"));
            addMeta.lore(l);
            addBtn.setItemMeta(addMeta);
        }
        base.getCanvas().setButton(41, CanvasButton.of(addBtn, event -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            new ProfessionSelectorGUI(plugin, itemDef, () -> open(player)).open(player);
        }));

        base.setBackButton(onBack);
        base.open(player);
    }
}

