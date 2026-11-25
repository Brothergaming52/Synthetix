package org.bg52.synthetix.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Custom InventoryHolder linking a Bukkit Inventory directly to a Synthetix Canvas.
 */
public class CanvasHolder implements InventoryHolder {

    private final Canvas canvas;
    private Inventory inventory;

    public CanvasHolder(Canvas canvas) {
        this.canvas = canvas;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public Canvas getCanvas() {
        return canvas;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}

