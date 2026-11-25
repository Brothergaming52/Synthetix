package org.bg52.synthetix.api.event;

import org.bg52.synthetix.block.CustomBlockDefinition;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player attempts to place a custom block in the world.
 */
public class SynthetixBlockPlaceEvent extends SynthetixBlockEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;
    private final BlockFace facing;
    private final ItemStack itemInHand;

    public SynthetixBlockPlaceEvent(CustomBlockDefinition blockDefinition, Location location, Player player, BlockFace facing, ItemStack itemInHand) {
        super(blockDefinition, location, player);
        this.facing = facing;
        this.itemInHand = itemInHand;
    }

    public BlockFace getFacing() {
        return facing;
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

