package org.bg52.synthetix.api.event;

import org.bg52.synthetix.keybind.KeybindAction;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Fired when a registered keybind action is triggered by a player.
 */
public class SynthetixKeybindTriggerEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final KeybindAction keybind;
    private final int hotbarSlot;
    private final ItemStack heldItem;
    private boolean cancelled = false;

    public SynthetixKeybindTriggerEvent(Player player, KeybindAction keybind, int hotbarSlot, ItemStack heldItem) {
        super(player);
        this.keybind = keybind;
        this.hotbarSlot = hotbarSlot;
        this.heldItem = heldItem;
    }

    public KeybindAction getKeybind() {
        return keybind;
    }

    public int getHotbarSlot() {
        return hotbarSlot;
    }

    public ItemStack getHeldItem() {
        return heldItem;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

