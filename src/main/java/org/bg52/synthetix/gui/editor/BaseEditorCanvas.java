package org.bg52.synthetix.gui.editor;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.gui.Canvas;
import org.bg52.synthetix.gui.CanvasButton;
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
 * Universal base canvas enforcing the Synthetix editor visual layout:
 * - Top row: Live Item Preview at center (slot 4) with symmetrical border.
 * - Bottom row: Symmetrical glass pane footer with standard navigation buttons.
 * - Middle rows: Dynamically scaled, centered content area.
 * All texts fully localized via LanguageManager.
 */
public class BaseEditorCanvas {

    private final SynthetixPlugin plugin;
    private final ItemDefinition itemDef;
    private final Canvas canvas;
    private final int rows;
    private final LanguageManager lang;

    public BaseEditorCanvas(SynthetixPlugin plugin, ItemDefinition itemDef, Component title, int rows) {
        this.plugin = plugin;
        this.itemDef = itemDef;
        this.rows = Math.max(3, Math.min(6, rows));
        this.canvas = new Canvas(title, this.rows);
        this.lang = plugin.getLanguageManager();

        setupTopAndBottomRows();
    }

    private void setupTopAndBottomRows() {
        ItemStack darkGlass = Canvas.createBorderItem(Material.BLACK_STAINED_GLASS_PANE, Component.empty());
        ItemStack grayGlass = Canvas.createBorderItem(Material.GRAY_STAINED_GLASS_PANE, Component.empty());

        // 1. Top row decorative border (Row 0)
        for (int c = 0; c < 9; c++) {
            if (c != 4) {
                canvas.setButton(0, c, CanvasButton.of(c == 0 || c == 8 ? darkGlass : grayGlass));
            }
        }

        // 2. Center Live Item Preview (Slot 4)
        canvas.setButton(0, 4, CanvasButton.of(createPreviewIcon(), e -> {
            Player p = (Player) e.getWhoClicked();
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.2f);
            p.getInventory().addItem(itemDef.buildItemStack());
            lang.send(p, "editor.preview-gave", "%item%", itemDef.getId());
        }));

        // 3. Bottom row filler (Last Row)
        int lastRow = rows - 1;
        for (int c = 0; c < 9; c++) {
            canvas.setButton(lastRow, c, CanvasButton.of(darkGlass));
        }
    }

    /**
     * Applies side borders (columns 0 and 8 on intermediate rows) to frame the inner playable grid.
     */
    public void setupSideBorders() {
        ItemStack darkGlass = Canvas.createBorderItem(Material.BLACK_STAINED_GLASS_PANE, Component.empty());
        for (int r = 1; r < rows - 1; r++) {
            canvas.setButton(r, 0, CanvasButton.of(darkGlass));
            canvas.setButton(r, 8, CanvasButton.of(darkGlass));
        }
    }

    /**
     * Calculates symmetrical centered slot indices for N items in an editor canvas.
     */
    public static int[] getCenteredSlots(int count) {
        if (count <= 0) return new int[0];
        if (count == 1) return new int[]{22};
        if (count == 2) return new int[]{21, 23};
        if (count == 3) return new int[]{20, 22, 24};
        if (count == 4) return new int[]{20, 21, 23, 24};
        if (count == 5) return new int[]{20, 21, 22, 23, 24};
        if (count == 6) return new int[]{11, 13, 15, 20, 22, 24};
        if (count == 7) return new int[]{11, 12, 14, 15, 20, 22, 24};
        if (count == 8) return new int[]{11, 12, 14, 15, 20, 21, 23, 24};
        if (count == 9) return new int[]{11, 12, 13, 14, 15, 20, 21, 23, 24};

        // Multi-row symmetrical distribution:
        // Inner slots across 3 rows: row 1 (10..16), row 2 (19..25), row 3 (28..34)
        int[] allInner = new int[]{
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34
        };
        int[] result = new int[Math.min(count, allInner.length)];
        System.arraycopy(allInner, 0, result, 0, result.length);
        return result;
    }

    private ItemStack createPreviewIcon() {
        ItemStack stack = itemDef.buildItemStack();
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<dark_gray>────────────────────</dark_gray>"));
            lore.add(lang.getComponent("editor.preview-header"));
            lore.add(lang.getComponent("editor.preview-lore"));
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /**
     * Sets a navigation Back button on the bottom row (Column 3, paired with Save/Add on Column 5).
     */
    public void setBackButton(Runnable onBack) {
        int slot = (rows - 1) * 9 + 3;
        ItemStack back = Canvas.createBorderItem(Material.ARROW, lang.getComponent("editor.back-button"));
        canvas.setButton(slot, CanvasButton.of(back, e -> {
            Player p = (Player) e.getWhoClicked();
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            if (onBack != null) {
                onBack.run();
            } else {
                p.closeInventory();
            }
        }));
    }

    /**
     * Sets a standalone centered Back button on the bottom row (Column 4).
     */
    public void setCenteredBackButton(Runnable onBack) {
        int slot = (rows - 1) * 9 + 4;
        ItemStack back = Canvas.createBorderItem(Material.ARROW, lang.getComponent("editor.back-button"));
        canvas.setButton(slot, CanvasButton.of(back, e -> {
            Player p = (Player) e.getWhoClicked();
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            if (onBack != null) {
                onBack.run();
            } else {
                p.closeInventory();
            }
        }));
    }

    /**
     * Sets a Save & Apply button on the bottom row.
     */
    public void setSaveButton() {
        int slot = (rows - 1) * 9 + 5;
        ItemStack save = Canvas.createBorderItem(Material.EMERALD, lang.getComponent("editor.save-button"));
        canvas.setButton(slot, CanvasButton.of(save, e -> {
            Player p = (Player) e.getWhoClicked();
            plugin.getItemRegistry().save(itemDef);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f);
            lang.send(p, "editor.save-success", "%item%", itemDef.getId());
            p.closeInventory();
        }));
    }

    /**
     * Sets previous and next page buttons in the bottom row.
     */
    public void setPaginationButtons(boolean hasPrev, Runnable onPrev, boolean hasNext, Runnable onNext) {
        int lastRow = rows - 1;
        if (hasPrev) {
            ItemStack prev = Canvas.createBorderItem(Material.SPECTRAL_ARROW, lang.getComponent("editor.prev-page-button"));
            canvas.setButton(lastRow, 0, CanvasButton.of(prev, e -> {
                Player p = (Player) e.getWhoClicked();
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                if (onPrev != null) onPrev.run();
            }));
        }
        if (hasNext) {
            ItemStack next = Canvas.createBorderItem(Material.SPECTRAL_ARROW, lang.getComponent("editor.next-page-button"));
            canvas.setButton(lastRow, 8, CanvasButton.of(next, e -> {
                Player p = (Player) e.getWhoClicked();
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                if (onNext != null) onNext.run();
            }));
        }
    }

    public Canvas getCanvas() {
        return canvas;
    }

    public int getContentCapacity() {
        return (rows - 2) * 9;
    }

    public int getContentSlot(int index) {
        return 9 + index;
    }

    public void open(Player player) {
        canvas.open(player);
    }
}
