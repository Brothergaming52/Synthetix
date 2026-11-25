package org.bg52.synthetix.api.event;

import org.bg52.synthetix.block.CustomBlockDefinition;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player right-clicks or left-clicks an existing custom block in the world.
 */
public class SynthetixBlockInteractEvent extends SynthetixBlockEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;
    private final Action action;
    private final EquipmentSlot hand;
    private final BlockFace clickedFace;
    private final ItemStack itemInHand;

    public SynthetixBlockInteractEvent(CustomBlockDefinition blockDefinition, Location location, Player player, Action action, EquipmentSlot hand, BlockFace clickedFace, ItemStack itemInHand) {
        super(blockDefinition, location, player);
        this.action = action;
        this.hand = hand;
        this.clickedFace = clickedFace;
        this.itemInHand = itemInHand;
    }

    public Action getAction() {
        return action;
    }

    public EquipmentSlot getHand() {
        return hand;
    }

    public BlockFace getClickedFace() {
        return clickedFace;
    }

    public ItemStack getItemInHand() {
        return itemInHand;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

