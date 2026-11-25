package org.bg52.synthetix.api.event;

import org.bg52.synthetix.item.ItemDefinition;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when a player consumes (eats or drinks) a custom Synthetix food, potion, or consumable item.
 */
public class SynthetixItemConsumeEvent extends SynthetixItemEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;

    private final Player player;

    public SynthetixItemConsumeEvent(@NotNull Player player, @Nullable ItemDefinition itemDefinition,
                                    @NotNull ItemStack itemStack) {
        super(itemDefinition, itemStack);
        this.player = player;
    }

    @NotNull
    public Player getPlayer() {
        return player;
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

