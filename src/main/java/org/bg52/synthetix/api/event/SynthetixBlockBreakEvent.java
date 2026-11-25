package org.bg52.synthetix.api.event;

import org.bg52.synthetix.block.CustomBlockDefinition;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Fired when a custom block is broken by a player.
 */
public class SynthetixBlockBreakEvent extends SynthetixBlockEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;
    private final ItemStack tool;
    private List<ItemStack> drops;
    private int expToDrop;

    public SynthetixBlockBreakEvent(CustomBlockDefinition blockDefinition, Location location, Player player, ItemStack tool, List<ItemStack> drops, int expToDrop) {
        super(blockDefinition, location, player);
        this.tool = tool;
        this.drops = drops;
        this.expToDrop = expToDrop;
    }

    public ItemStack getTool() {
        return tool;
    }

    public List<ItemStack> getDrops() {
        return drops;
    }

    public void setDrops(List<ItemStack> drops) {
        this.drops = drops;
    }

    public int getExpToDrop() {
        return expToDrop;
    }

    public void setExpToDrop(int expToDrop) {
        this.expToDrop = Math.max(0, expToDrop);
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

