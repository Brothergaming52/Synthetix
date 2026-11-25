package org.bg52.synthetix.api.event;

import org.bg52.synthetix.item.ItemDefinition;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when a player breaks a block using a Synthetix tool or item.
 * Allows plugins to implement custom mining behaviors (3x3 mining, vein mining, autosmelt, etc.).
 */
public class SynthetixItemBreakBlockEvent extends SynthetixItemEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;

    private final Player player;
    private final Block block;
    private boolean dropItems;

    public SynthetixItemBreakBlockEvent(@NotNull Player player, @NotNull Block block,
                                       @Nullable ItemDefinition itemDefinition, @NotNull ItemStack itemStack,
                                       boolean dropItems) {
        super(itemDefinition, itemStack);
        this.player = player;
        this.block = block;
        this.dropItems = dropItems;
    }

    @NotNull
    public Player getPlayer() {
        return player;
    }

    @NotNull
    public Block getBlock() {
        return block;
    }

    public boolean isDropItems() {
        return dropItems;
    }

    public void setDropItems(boolean dropItems) {
        this.dropItems = dropItems;
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

