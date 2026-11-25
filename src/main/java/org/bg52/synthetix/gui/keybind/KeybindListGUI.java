package org.bg52.synthetix.gui.keybind;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.gui.Canvas;
import org.bg52.synthetix.gui.CanvasButton;
import org.bg52.synthetix.keybind.CustomKeybind;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Administrative GUI listing all server-configured custom keybinds.
 */
public class KeybindListGUI {

    private final SynthetixPlugin plugin;

    public KeybindListGUI(SynthetixPlugin plugin) {
        this.plugin = plugin;
    }

    public static void open(Player player, SynthetixPlugin plugin) {
        new KeybindListGUI(plugin).open(player, 0);
    }

    public void open(Player player, int page) {
        List<CustomKeybind> keybinds = new ArrayList<>(plugin.getKeybindManager().getCustomKeybinds());
        keybinds.sort((a, b) -> a.getId().compareToIgnoreCase(b.getId()));

        Canvas canvas = new Canvas(TextUtil.parse("<dark_gray>[<gradient:#ff0080:#7928ca>Synthetix</gradient>]</dark_gray> <gray>Custom Keybinds</gray>"), 5);

        // Frame
        ItemStack darkGlass = Canvas.createBorderItem(Material.BLACK_STAINED_GLASS_PANE, Component.empty());
        for (int c = 0; c < 9; c++) {
            canvas.setButton(0, c, CanvasButton.of(darkGlass));
            canvas.setButton(4, c, CanvasButton.of(darkGlass));
        }

        int[] slots = new int[]{
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25
        };

        for (int i = 0; i < keybinds.size() && i < slots.length; i++) {
            CustomKeybind keybind = keybinds.get(i);
            ItemStack icon = new ItemStack(keybind.isEnabled() ? Material.COMPARATOR : Material.REPEATER);
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.displayName(TextUtil.parse("<yellow><bold>" + keybind.getId() + "</bold></yellow>"));
                List<Component> lore = new ArrayList<>();
                lore.add(TextUtil.parse("<gray>Trigger:</gray> <aqua>" + keybind.getType().name() + "</aqua>"));

                String slotText = keybind.getHotbarSlot() >= 0 ? "Slot " + (keybind.getHotbarSlot() + 1) : "Any Slot";
                lore.add(TextUtil.parse("<gray>Hotbar Slot:</gray> <white>" + slotText + "</white>"));

                lore.add(TextUtil.parse("<gray>Command:</gray> <green>/" + keybind.getCommand() + "</green>"));
                lore.add(TextUtil.parse("<gray>Execution:</gray> " + (keybind.isAsPlayer() ? "<gold>[PLAYER]</gold>" : "<red>[CONSOLE]</red>")));
                lore.add(TextUtil.parse("<gray>Cooldown:</gray> <white>" + keybind.getCooldownMs() + "ms</white>"));
                lore.add(TextUtil.parse("<gray>Status:</gray> " + (keybind.isEnabled() ? "<green>Enabled</green>" : "<red>Disabled</red>")));
                lore.add(Component.empty());
                lore.add(TextUtil.parse("<yellow>Left-Click: Edit keybind in GUI »</yellow>"));
                lore.add(TextUtil.parse("<red>Shift-Right-Click: Delete keybind</red>"));
                meta.lore(lore);
                icon.setItemMeta(meta);
            }

            canvas.setButton(slots[i], CanvasButton.of(icon, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
                if (e.isRightClick() && e.isShiftClick()) {
                    plugin.getKeybindManager().deleteCustomKeybind(keybind.getId());
                    player.sendMessage(TextUtil.parse("<red>✓ Deleted custom keybind '" + keybind.getId() + "'.</red>"));
                    open(player, page);
                } else {
                    KeybindEditorGUI.open(player, plugin, keybind, () -> open(player, page));
                }
            }));
        }

        // Create New Keybind Button (Slot 40)
        ItemStack createBtn = new ItemStack(Material.EMERALD);
        ItemMeta crMeta = createBtn.getItemMeta();
        if (crMeta != null) {
            crMeta.displayName(TextUtil.parse("<green><bold>+ Create New Keybind</bold></green>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Bind a player input to run commands automatically.</gray>"));
            crMeta.lore(lore);
            createBtn.setItemMeta(crMeta);
        }

        canvas.setButton(40, CanvasButton.of(createBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            plugin.getChatPromptManager().prompt(player,
                    TextUtil.parse("<gray>Enter unique Keybind ID (e.g. <yellow>curios_menu</yellow>):</gray>"),
                    input -> {
                        String cleanId = input.trim().toLowerCase().replaceAll("[^a-z0-9_.-]", "_");
                        if (cleanId.isEmpty()) {
                            player.sendMessage(TextUtil.parse("<red>Invalid ID provided.</red>"));
                            open(player, page);
                            return;
                        }

                        CustomKeybind existing = plugin.getKeybindManager().getCustomKeybind(cleanId);
                        if (existing != null) {
                            player.sendMessage(TextUtil.parse("<red>A keybind with ID '" + cleanId + "' already exists!</red>"));
                            open(player, page);
                            return;
                        }

                        CustomKeybind newKeybind = new CustomKeybind(cleanId);
                        KeybindEditorGUI.open(player, plugin, newKeybind, () -> open(player, page));
                    });
        }));

        canvas.open(player);
    }
}

