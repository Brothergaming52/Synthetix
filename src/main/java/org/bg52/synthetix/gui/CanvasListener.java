package org.bg52.synthetix.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/**
 * Global event listener managing click, drag, and close interactions for all Canvas instances.
 */
public class CanvasListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        if (inv.getHolder() instanceof CanvasHolder holder) {
            Canvas canvas = holder.getCanvas();
            if (canvas != null) {
                // If clicking outside the inventory window
                if (event.getRawSlot() < 0) {
                    return;
                }

                // If clicking inside the canvas top inventory
                if (event.getRawSlot() < canvas.getSize()) {
                    canvas.handleClick(event);
                } else if (canvas.isCancelClicksByDefault() && event.isShiftClick()) {
                    // Prevent shift clicking into a canvas top inventory if cancelled by default
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory inv = event.getInventory();
        if (inv.getHolder() instanceof CanvasHolder holder) {
            Canvas canvas = holder.getCanvas();
            if (canvas != null && canvas.isCancelClicksByDefault()) {
                // If dragging across any slot within the top canvas
                for (int slot : event.getRawSlots()) {
                    if (slot < canvas.getSize()) {
                        event.setCancelled(true);
                        break;
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        if (inv.getHolder() instanceof CanvasHolder holder) {
            Canvas canvas = holder.getCanvas();
            if (canvas != null) {
                canvas.handleClose(event);
            }
        }
    }
}

