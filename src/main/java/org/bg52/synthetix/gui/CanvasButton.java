package org.bg52.synthetix.gui;

import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/**
 * Represents an interactive button placed within a Canvas slot.
 */
public class CanvasButton {

    private final ItemStack icon;
    private final Consumer<InventoryClickEvent> onClick;

    public CanvasButton(ItemStack icon, Consumer<InventoryClickEvent> onClick) {
        this.icon = icon != null ? icon : new ItemStack(Material.AIR);
        this.onClick = onClick;
    }

    public static CanvasButton of(ItemStack icon) {
        return new CanvasButton(icon, null);
    }

    public static CanvasButton of(ItemStack icon, Consumer<InventoryClickEvent> onClick) {
        return new CanvasButton(icon, onClick);
    }

    public ItemStack getIcon() {
        return icon.clone();
    }

    public void handleClick(InventoryClickEvent event) {
        if (onClick != null) {
            onClick.accept(event);
        }
    }
}

