package org.bg52.synthetix.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Stateful, symmetrical GUI canvas for creating clean, modular in-game menus.
 */
public class Canvas {

    private final Component title;
    private final int size; // Multiple of 9 (9 to 54)
    private final int rows;
    private final Map<Integer, CanvasButton> buttons;

    private boolean cancelClicksByDefault = true;
    private Consumer<InventoryCloseEvent> closeHandler;
    private Consumer<Player> openHandler;

    public Canvas(Component title, int rows) {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("Canvas rows must be between 1 and 6!");
        }
        this.title = title;
        this.rows = rows;
        this.size = rows * 9;
        this.buttons = new HashMap<>();
    }

    public static ItemStack createBorderItem(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name != null ? name : Component.empty());
            item.setItemMeta(meta);
        }
        return item;
    }

    public Canvas setButton(int slot, CanvasButton button) {
        if (slot >= 0 && slot < size) {
            buttons.put(slot, button);
        }
        return this;
    }

    public Canvas setButton(int row, int col, CanvasButton button) {
        return setButton(row * 9 + col, button);
    }

    public Canvas removeButton(int slot) {
        buttons.remove(slot);
        return this;
    }

    /**
     * Symmetrically fills the perimeter border of the canvas.
     */
    public Canvas fillBorder(ItemStack borderItem) {
        CanvasButton borderBtn = CanvasButton.of(borderItem);
        for (int c = 0; c < 9; c++) {
            setButton(0, c, borderBtn);              // Top row
            setButton(rows - 1, c, borderBtn);       // Bottom row
        }
        for (int r = 1; r < rows - 1; r++) {
            setButton(r, 0, borderBtn);              // Left col
            setButton(r, 8, borderBtn);              // Right col
        }
        return this;
    }

    /**
     * Fills an entire horizontal row.
     */
    public Canvas fillRow(int row, ItemStack item) {
        if (row >= 0 && row < rows) {
            CanvasButton btn = CanvasButton.of(item);
            for (int col = 0; col < 9; col++) {
                setButton(row, col, btn);
            }
        }
        return this;
    }

    /**
     * Fills all empty slots with a background item.
     */
    public Canvas fillEmpty(ItemStack item) {
        CanvasButton btn = CanvasButton.of(item);
        for (int i = 0; i < size; i++) {
            if (!buttons.containsKey(i)) {
                buttons.put(i, btn);
            }
        }
        return this;
    }

    public Canvas onClose(Consumer<InventoryCloseEvent> handler) {
        this.closeHandler = handler;
        return this;
    }

    public Canvas onOpen(Consumer<Player> handler) {
        this.openHandler = handler;
        return this;
    }

    public Canvas setCancelClicksByDefault(boolean cancel) {
        this.cancelClicksByDefault = cancel;
        return this;
    }

    public boolean isCancelClicksByDefault() {
        return cancelClicksByDefault;
    }

    /**
     * Opens this canvas for the specified player.
     */
    public void open(Player player) {
        CanvasHolder holder = new CanvasHolder(this);
        Inventory inv = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inv);

        // Populate buttons
        render(inv);

        player.openInventory(inv);

        if (openHandler != null) {
            openHandler.accept(player);
        }
    }

    /**
     * Renders or refreshes buttons onto the target inventory.
     */
    public void render(Inventory inv) {
        inv.clear();
        for (Map.Entry<Integer, CanvasButton> entry : buttons.entrySet()) {
            int slot = entry.getKey();
            if (slot >= 0 && slot < inv.getSize()) {
                inv.setItem(slot, entry.getValue().getIcon());
            }
        }
    }

    public void handleClick(InventoryClickEvent event) {
        if (cancelClicksByDefault) {
            event.setCancelled(true);
        }

        int slot = event.getRawSlot();
        if (slot >= 0 && slot < size) {
            CanvasButton button = buttons.get(slot);
            if (button != null) {
                button.handleClick(event);
            }
        }
    }

    public void handleClose(InventoryCloseEvent event) {
        if (closeHandler != null) {
            closeHandler.accept(event);
        }
    }

    public int getSize() {
        return size;
    }

    public int getRows() {
        return rows;
    }

    public Component getTitle() {
        return title;
    }
}

