package org.bg52.synthetix.gui.set;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.gui.Canvas;
import org.bg52.synthetix.gui.CanvasButton;
import org.bg52.synthetix.lang.LanguageManager;
import org.bg52.synthetix.set.ItemSet;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Visual in-game browser and manager for all registered Equipment Sets.
 */
public class SetListGUI {

    private final SynthetixPlugin plugin;
    private final LanguageManager lang;

    public SetListGUI(SynthetixPlugin plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    public static void open(Player player, SynthetixPlugin plugin) {
        new SetListGUI(plugin).open(player, 0);
    }

    public void open(Player player, int page) {
        List<ItemSet> allSets = new ArrayList<>(plugin.getApi().getSetManager().getAllSets());
        allSets.sort((a, b) -> a.getId().compareToIgnoreCase(b.getId()));

        Canvas canvas = new Canvas(TextUtil.parse("<dark_gray>[<gradient:#ff0080:#7928ca>Synthetix</gradient>]</dark_gray> <gray>Equipment Sets</gray>"), 5);

        // Frame top and bottom
        ItemStack darkGlass = Canvas.createBorderItem(Material.BLACK_STAINED_GLASS_PANE, Component.empty());
        for (int c = 0; c < 9; c++) {
            canvas.setButton(0, c, CanvasButton.of(darkGlass));
            canvas.setButton(4, c, CanvasButton.of(darkGlass));
        }

        // Inner playable slots
        int[] slots = new int[]{
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25
        };

        for (int i = 0; i < allSets.size() && i < slots.length; i++) {
            ItemSet set = allSets.get(i);
            ItemStack icon = new ItemStack(Material.NETHERITE_CHESTPLATE);
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.displayName(TextUtil.parse(set.getDisplayName()));
                List<Component> lore = new ArrayList<>();
                lore.add(TextUtil.parse("<gray>ID:</gray> <white>" + set.getId() + "</white>"));
                lore.add(TextUtil.parse("<gray>Pieces (" + set.getPieces().size() + "):</gray> <white>" + String.join(", ", set.getPieces()) + "</white>"));
                lore.add(TextUtil.parse("<gray>Bonus Tiers:</gray> <white>" + set.getBonuses().size() + "</white>"));
                lore.add(Component.empty());
                lore.add(TextUtil.parse("<yellow>Left-Click: Edit set in GUI »</yellow>"));
                lore.add(TextUtil.parse("<red>Shift-Right-Click: Delete set</red>"));
                meta.lore(lore);
                icon.setItemMeta(meta);
            }

            canvas.setButton(slots[i], CanvasButton.of(icon, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
                if (e.isRightClick() && e.isShiftClick()) {
                    plugin.getApi().getSetManager().deleteSet(set.getId());
                    player.sendMessage(TextUtil.parse("<red>✓ Deleted equipment set " + set.getId() + ".</red>"));
                    open(player, page);
                } else {
                    SetEditorGUI.open(player, plugin, set, () -> open(player, page));
                }
            }));
        }

        // Create New Set Button (Slot 40)
        ItemStack createBtn = new ItemStack(Material.EMERALD);
        ItemMeta crMeta = createBtn.getItemMeta();
        if (crMeta != null) {
            crMeta.displayName(TextUtil.parse("<green><bold>+ Create New Equipment Set</bold></green>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Create an armor/equipment set with tier bonuses.</gray>"));
            crMeta.lore(lore);
            createBtn.setItemMeta(crMeta);
        }
        canvas.setButton(40, CanvasButton.of(createBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            plugin.getChatPromptManager().prompt(player,
                    TextUtil.parse("<gray>Enter unique Set ID (e.g. <yellow>ruby_set</yellow>):</gray>"),
                    id -> {
                        String cleanId = id.trim().toLowerCase();
                        if (plugin.getApi().getSetManager().getSet(cleanId) != null) {
                            player.sendMessage(TextUtil.parse("<red>Set ID already exists.</red>"));
                            open(player, page);
                            return;
                        }
                        ItemSet newSet = new ItemSet(cleanId, cleanId);
                        plugin.getApi().getSetManager().registerSet(newSet);
                        player.sendMessage(TextUtil.parse("<green>✓ Created set " + cleanId + "!</green>"));
                        SetEditorGUI.open(player, plugin, newSet, () -> open(player, page));
                    });
        }));

        canvas.open(player);
    }
}

