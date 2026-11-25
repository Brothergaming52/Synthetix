package org.bg52.synthetix.keybind;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Contextual payload provided when a keybind is successfully matched and fired.
 */
public class KeybindContext {

    private final Player player;
    private final KeybindAction keybind;
    private final int hotbarSlot;
    private final ItemStack heldItem;
    private boolean cancelled = false;

    public KeybindContext(Player player, KeybindAction keybind, int hotbarSlot, ItemStack heldItem) {
        this.player = player;
        this.keybind = keybind;
        this.hotbarSlot = hotbarSlot;
        this.heldItem = heldItem;
    }

    public Player getPlayer() {
        return player;
    }

    public KeybindAction getKeybind() {
        return keybind;
    }

    public int getHotbarSlot() {
        return hotbarSlot;
    }

    public int getSlot() {
        return hotbarSlot;
    }

    public ItemStack getHeldItem() {
        return heldItem;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}

