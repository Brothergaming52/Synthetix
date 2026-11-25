package org.bg52.synthetix.gui.editor.category;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.api.editor.ItemEditorCategory;
import org.bg52.synthetix.distribution.LootTableRule;
import org.bg52.synthetix.distribution.MobDropRule;
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
 * Core category for inspecting world distribution: mob drops, loot table injections, and villager/trader trades.
 */
public class DistributionCategory implements ItemEditorCategory {

    private final SynthetixPlugin plugin;
    private final LanguageManager lang;

    public DistributionCategory(SynthetixPlugin plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    @Override
    public String getId() {
        return "distribution";
    }

    @Override
    public Component getDisplayName() {
        return lang.getComponent("editor.categories.distribution.name");
    }

    @Override
    public int getPriority() {
        return 70;
    }

    @Override
    public boolean isPluginCategory() {
        return false;
    }

    @Override
    public ItemStack getIcon(ItemDefinition def) {
        ItemStack item = new ItemStack(Material.CHEST_MINECART);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(getDisplayName());
            List<Component> lore = new ArrayList<>();
            lore.add(lang.getComponent("editor.categories.distribution.desc-1"));
            lore.add(lang.getComponent("editor.categories.distribution.desc-2"));
            lore.add(Component.empty());

            int dropsCount = plugin.getDistributionManager().getMobDropManager().getDropsForItem(def.getId()).size();
            int lootsCount = plugin.getDistributionManager().getLootTableInjector().getInjectionsForItem(def.getId()).size();
            int tradesCount = plugin.getDistributionManager().getTradeInjector().getTradesForItem(def.getId()).size();

            lore.add(TextUtil.parse("<dark_gray>•</dark_gray> <gray>Mob Drops:</gray> <white>" + dropsCount + "</white>"));
            lore.add(TextUtil.parse("<dark_gray>•</dark_gray> <gray>Loot Tables:</gray> <white>" + lootsCount + "</white>"));
            lore.add(TextUtil.parse("<dark_gray>•</dark_gray> <gray>Merchant Trades:</gray> <white>" + tradesCount + "</white>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>Click to inspect distribution »</yellow>"));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void open(Player player, ItemDefinition def, Runnable onBack) {
        BaseEditorCanvas base = new BaseEditorCanvas(plugin, def, getDisplayName(), 4);
        base.setupSideBorders();

        List<MobDropRule> drops = plugin.getDistributionManager().getMobDropManager().getDropsForItem(def.getId());
        List<LootTableRule> loots = plugin.getDistributionManager().getLootTableInjector().getInjectionsForItem(def.getId());
        List<TradeRule> trades = plugin.getDistributionManager().getTradeInjector().getTradesForItem(def.getId());

        // 1. Mob Drops Overview Button (Slot 11)
        ItemStack mobBtn = new ItemStack(Material.ZOMBIE_HEAD);
        ItemMeta mMeta = mobBtn.getItemMeta();
        if (mMeta != null) {
            mMeta.displayName(TextUtil.parse("<gold><bold>Mob Drops</bold></gold> <dark_gray>(" + drops.size() + ")</dark_gray>"));
            List<Component> l = new ArrayList<>();
            if (drops.isEmpty()) {
                l.add(TextUtil.parse("<gray>No mobs currently drop this item.</gray>"));
            } else {
                for (MobDropRule r : drops) {
                    String amt = r.getMinAmount() == r.getMaxAmount() ? String.valueOf(r.getMinAmount()) : r.getMinAmount() + "-" + r.getMaxAmount();
                    l.add(TextUtil.parse("<dark_gray>•</dark_gray> <yellow>" + TextUtil.prettify(r.getEntityType()) + "</yellow>: <green>" + (int)(r.getChance() * 100) + "%</green> (Amount: " + amt + ")"));
                }
            }
            l.add(Component.empty());
            l.add(TextUtil.parse("<yellow>Click to manage mob drops »</yellow>"));
            mMeta.lore(l);
            mobBtn.setItemMeta(mMeta);
        }
        base.getCanvas().setButton(11, CanvasButton.of(mobBtn, event -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            new org.bg52.synthetix.gui.editor.distribution.MobDropEditorGUI(plugin, def, () -> open(player, def, onBack)).open(player);
        }));

        // 2. Loot Tables Overview Button (Slot 13)
        ItemStack lootBtn = new ItemStack(Material.CHEST);
        ItemMeta lMeta = lootBtn.getItemMeta();
        if (lMeta != null) {
            lMeta.displayName(TextUtil.parse("<aqua><bold>Loot Tables & Chests</bold></aqua> <dark_gray>(" + loots.size() + ")</dark_gray>"));
            List<Component> l = new ArrayList<>();
            if (loots.isEmpty()) {
                l.add(TextUtil.parse("<gray>No loot tables currently generate this item.</gray>"));
            } else {
                for (LootTableRule r : loots) {
                    l.add(TextUtil.parse("<dark_gray>•</dark_gray> <yellow>" + r.getId() + "</yellow>: <green>" + (int)(r.getChance() * 100) + "%</green> (" + r.getPatterns().size() + " tables)"));
                }
            }
            l.add(Component.empty());
            l.add(TextUtil.parse("<yellow>Click to manage loot injections »</yellow>"));
            lMeta.lore(l);
            lootBtn.setItemMeta(lMeta);
        }
        base.getCanvas().setButton(13, CanvasButton.of(lootBtn, event -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            new org.bg52.synthetix.gui.editor.distribution.LootTableEditorGUI(plugin, def, () -> open(player, def, onBack)).open(player);
        }));

        // 3. Merchant Trades Overview Button (Slot 15)
        ItemStack tradeBtn = new ItemStack(Material.EMERALD);
        ItemMeta tMeta = tradeBtn.getItemMeta();
        if (tMeta != null) {
            tMeta.displayName(TextUtil.parse("<green><bold>Villager & Trader Trades</bold></green> <dark_gray>(" + trades.size() + ")</dark_gray>"));
            List<Component> l = new ArrayList<>();
            if (trades.isEmpty()) {
                l.add(TextUtil.parse("<gray>No villagers or traders currently offer this item.</gray>"));
            } else {
                for (TradeRule r : trades) {
                    l.add(TextUtil.parse("<dark_gray>•</dark_gray> <yellow>" + TextUtil.prettify(r.getProfession()) + "</yellow> (Lvl " + r.getLevel() + "): <green>" + (int)(r.getChance() * 100) + "%</green>"));
                }
            }
            l.add(Component.empty());
            l.add(TextUtil.parse("<yellow>Click to manage merchant trades »</yellow>"));
            tMeta.lore(l);
            tradeBtn.setItemMeta(tMeta);
        }
        base.getCanvas().setButton(15, CanvasButton.of(tradeBtn, event -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            new org.bg52.synthetix.gui.editor.distribution.TradeEditorGUI(plugin, def, () -> open(player, def, onBack)).open(player);
        }));

        base.setCenteredBackButton(onBack);
        base.open(player);
    }
}

