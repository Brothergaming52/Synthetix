package org.bg52.synthetix.gui.editor.category;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.gui.CanvasButton;
import org.bg52.synthetix.gui.editor.BaseEditorCanvas;
import org.bg52.synthetix.item.ItemDefinition;
import org.bg52.synthetix.lang.LanguageManager;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Visual RGB Color Picker GUI for Leather Armor and Potion custom items.
 */
public class ColorPickerGUI {

    private final SynthetixPlugin plugin;
    private final LanguageManager lang;
    private final ItemDefinition itemDef;
    private final Runnable onBack;

    private static final String[][] PRESETS = new String[][]{
            {"Ruby Red", "#E6194B"},
            {"Sapphire Blue", "#4363D8"},
            {"Emerald Green", "#3CB44B"},
            {"Amethyst Purple", "#911EB4"},
            {"Solar Gold", "#FFE119"},
            {"Void Black", "#141414"},
            {"Pure White", "#FFFFFF"},
            {"Cyan Aqua", "#42D4F4"},
            {"Rose Pink", "#FABED4"}
    };

    public ColorPickerGUI(SynthetixPlugin plugin, ItemDefinition itemDef, Runnable onBack) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
        this.itemDef = itemDef;
        this.onBack = onBack;
    }

    public static void open(Player player, SynthetixPlugin plugin, ItemDefinition itemDef, Runnable onBack) {
        new ColorPickerGUI(plugin, itemDef, onBack).open(player);
    }

    public void open(Player player) {
        BaseEditorCanvas base = new BaseEditorCanvas(plugin, itemDef, TextUtil.parse("<gold><bold>Color Picker (Leather / Potion)</bold></gold>"), 5);
        base.setupSideBorders();

        Color curColor = itemDef.getCustomColor();
        String curHex = curColor != null ? String.format("#%02X%02X%02X", curColor.getRed(), curColor.getGreen(), curColor.getBlue()) : "Default / None";

        // Preset Color Buttons (Slots 10..18 / 19..27)
        int[] presetSlots = new int[]{11, 12, 13, 14, 15, 20, 21, 22, 23};
        for (int i = 0; i < PRESETS.length && i < presetSlots.length; i++) {
            String name = PRESETS[i][0];
            String hex = PRESETS[i][1];
            int rgb = Integer.parseInt(hex.substring(1), 16);
            Color col = Color.fromRGB(rgb);

            ItemStack icon = new ItemStack(Material.LEATHER_CHESTPLATE);
            LeatherArmorMeta meta = (LeatherArmorMeta) icon.getItemMeta();
            if (meta != null) {
                meta.setColor(col);
                meta.displayName(TextUtil.parse("<color:" + hex + ">" + name + "</color> <gray>(" + hex + ")</gray>"));
                List<Component> lore = new ArrayList<>();
                if (curColor != null && curColor.asRGB() == rgb) {
                    lore.add(lang.getComponent("general.selected"));
                } else {
                    lore.add(lang.getComponent("general.not-selected"));
                }
                lore.add(Component.empty());
                lore.add(TextUtil.parse("<yellow>Click to apply preset</yellow>"));
                meta.lore(lore);
                icon.setItemMeta(meta);
            }

            base.getCanvas().setButton(presetSlots[i], CanvasButton.of(icon, e -> {
                itemDef.setCustomColor(col);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                open(player);
            }));
        }

        // Custom Hex Prompt Button (Slot 24)
        ItemStack hexBtn = new ItemStack(Material.NAME_TAG);
        ItemMeta hMeta = hexBtn.getItemMeta();
        if (hMeta != null) {
            hMeta.displayName(TextUtil.parse("<gold>Custom Hex Color</gold>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Current:</gray> <white>" + curHex + "</white>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>Click to enter Hex in chat (#RRGGBB)</yellow>"));
            hMeta.lore(lore);
            hexBtn.setItemMeta(hMeta);
        }
        base.getCanvas().setButton(24, CanvasButton.of(hexBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            plugin.getChatPromptManager().prompt(player,
                    TextUtil.parse("<gray>Enter Hex RGB color (e.g. <yellow>#FF5500</yellow>):</gray>"),
                    input -> {
                        String clean = input.trim();
                        if (!clean.startsWith("#")) clean = "#" + clean;
                        try {
                            int rgb = Integer.parseInt(clean.substring(1), 16);
                            itemDef.setCustomColor(Color.fromRGB(rgb));
                            player.sendMessage(TextUtil.parse("<green>✓ Applied color " + clean + "!</green>"));
                        } catch (Exception ignored) {
                            player.sendMessage(TextUtil.parse("<red>Invalid hex format. Use #RRGGBB.</red>"));
                        }
                        open(player);
                    });
        }));

        // Clear Color Button (Slot 31)
        ItemStack clearBtn = new ItemStack(Material.BARRIER);
        ItemMeta cMeta = clearBtn.getItemMeta();
        if (cMeta != null) {
            cMeta.displayName(TextUtil.parse("<red><bold>Clear Custom Color</bold></red>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Reset to vanilla default color.</gray>"));
            cMeta.lore(lore);
            clearBtn.setItemMeta(cMeta);
        }
        base.getCanvas().setButton(31, CanvasButton.of(clearBtn, e -> {
            itemDef.setCustomColor(null);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            open(player);
        }));

        base.setBackButton(onBack);
        base.setSaveButton();
        base.open(player);
    }
}

