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
 * Interactive GUI listing all mob drop rules configured for an item.
 * Supports adding, editing, and deleting mob drops live in-game.
 */
public class MobDropEditorGUI {

    private final SynthetixPlugin plugin;
    private final LanguageManager lang;
    private final ItemDefinition itemDef;
    private final Runnable onBack;

    public MobDropEditorGUI(SynthetixPlugin plugin, ItemDefinition itemDef, Runnable onBack) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
        this.itemDef = itemDef;
        this.onBack = onBack;
    }

    public void open(Player player) {
        BaseEditorCanvas base = new BaseEditorCanvas(plugin, itemDef,
                lang.getComponent("editor.distribution.mob-drops.title"), 5);
        base.setupSideBorders();

        List<MobDropRule> drops = plugin.getDistributionManager().getMobDropManager().getDropsForItem(itemDef.getId());

        if (drops.isEmpty()) {
            ItemStack emptyIcon = new ItemStack(Material.BARRIER);
            ItemMeta eMeta = emptyIcon.getItemMeta();
            if (eMeta != null) {
                eMeta.displayName(TextUtil.parse("<red><bold>No Mob Drops Configured</bold></red>"));
                List<Component> l = new ArrayList<>();
                l.add(TextUtil.parse("<gray>No mobs currently drop this item.</gray>"));
                l.add(Component.empty());
                l.add(TextUtil.parse("<yellow>Click [+ Add Mob Drop] below to configure one!</yellow>"));
                eMeta.lore(l);
                emptyIcon.setItemMeta(eMeta);
            }
            base.getCanvas().setButton(22, CanvasButton.of(emptyIcon));
        } else {
            int slot = 10;
            for (MobDropRule rule : drops) {
                Material iconMat = resolveMobIcon(rule.getEntityType());
                ItemStack icon = new ItemStack(iconMat);
                ItemMeta meta = icon.getItemMeta();
                if (meta != null) {
                    meta.displayName(TextUtil.parse("<gold><bold>" + TextUtil.prettify(rule.getEntityType()) + "</bold></gold>"));
                    List<Component> l = new ArrayList<>();
                    l.add(TextUtil.parse("<dark_gray>Rule ID: " + rule.getId() + "</dark_gray>"));
                    l.add(TextUtil.parse("<gray>Chance: <green>" + (int) (rule.getChance() * 100) + "%</green></gray>"));
                    String amtStr = rule.getMinAmount() == rule.getMaxAmount() ? String.valueOf(rule.getMinAmount()) : rule.getMinAmount() + " - " + rule.getMaxAmount();
                    l.add(TextUtil.parse("<gray>Amount: <white>" + amtStr + "</white></gray>"));
                    if (rule.getLootingBonus() > 0) {
                        l.add(TextUtil.parse("<gray>Looting Bonus: <aqua>+" + (int) (rule.getLootingBonus() * 100) + "% / lvl</aqua></gray>"));
                    }
                    l.add(TextUtil.parse("<gray>Player Kill Only: " + (rule.isPlayerKillOnly() ? "<green>Yes</green>" : "<red>No</red>") + "</gray>"));
                    l.add(Component.empty());
                    l.add(TextUtil.parse("<yellow>Left-Click:</yellow> <gray>Edit rule »</gray>"));
                    l.add(TextUtil.parse("<red>Shift-Right-Click:</red> <gray>Delete rule</gray>"));
                    meta.lore(l);
                    icon.setItemMeta(meta);
                }

                MobDropRule currentRule = rule;
                base.getCanvas().setButton(slot++, CanvasButton.of(icon, event -> {
                    if (event.isShiftClick() && event.isRightClick()) {
                        try {
                            plugin.getDistributionManager().getMobDropManager().deleteRule(currentRule.getId());
                            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                            player.sendMessage(lang.getComponent("editor.distribution.mob-drops.deleted", "%id%", currentRule.getId()));
                            open(player);
                        } catch (Exception e) {
                            player.sendMessage(TextUtil.parse("<red>Failed to delete mob drop: " + e.getMessage() + "</red>"));
                        }
                    } else {
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                        new MobDropConfigGUI(plugin, itemDef, currentRule, () -> open(player)).open(player);
                    }
                }));

                if (slot == 17) slot = 19;
                if (slot == 26) slot = 28;
                if (slot > 34) break;
            }
        }

        // Add Mob Drop Button at slot 41 (Column 5, symmetrical to Back Button at slot 39 / Column 3)
        ItemStack addBtn = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta addMeta = addBtn.getItemMeta();
        if (addMeta != null) {
            addMeta.displayName(lang.getComponent("editor.distribution.mob-drops.add-btn"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<gray>Select a mob from the server</gray>"));
            l.add(TextUtil.parse("<gray>and configure drop chance and amounts.</gray>"));
            l.add(Component.empty());
            l.add(TextUtil.parse("<yellow>Click to browse mobs »</yellow>"));
            addMeta.lore(l);
            addBtn.setItemMeta(addMeta);
        }
        base.getCanvas().setButton(41, CanvasButton.of(addBtn, event -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            new MobSelectorGUI(plugin, itemDef, () -> open(player)).open(player);
        }));

        base.setBackButton(onBack);
        base.open(player);
    }

    public static Material resolveMobIcon(String entityType) {
        if ("ALL_HOSTILE".equalsIgnoreCase(entityType)) return Material.ZOMBIE_HEAD;
        if ("ALL_PASSIVE".equalsIgnoreCase(entityType)) return Material.PIG_SPAWN_EGG;
        if ("ANY".equalsIgnoreCase(entityType)) return Material.NETHER_STAR;
        if ("ENDER_DRAGON".equalsIgnoreCase(entityType)) return Material.DRAGON_HEAD;
        if ("WITHER".equalsIgnoreCase(entityType) || "WITHER_SKELETON".equalsIgnoreCase(entityType)) return Material.WITHER_SKELETON_SKULL;

        String eggName = entityType.toUpperCase() + "_SPAWN_EGG";
        Material m = Material.matchMaterial(eggName);
        if (m != null) return m;

        Material direct = Material.matchMaterial(entityType.toUpperCase());
        if (direct != null) return direct;

        return Material.ROTTEN_FLESH;
    }
}

