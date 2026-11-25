package org.bg52.synthetix.api.event;

import org.bg52.synthetix.block.CustomBlockDefinition;
import org.bg52.synthetix.block.PlacedBlockInstance;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when a custom block's internal state data changes.
 * Allows addon plugins to monitor machine states (e.g. temperature, RPM, recipe progress).
 */
public class SynthetixBlockStateChangeEvent extends SynthetixBlockEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;

    private final PlacedBlockInstance instance;
    private final String stateKey;
    private final Object oldValue;
    private Object newValue;

    public SynthetixBlockStateChangeEvent(CustomBlockDefinition blockDefinition, Location location,
                                         PlacedBlockInstance instance, String stateKey,
                                         Object oldValue, Object newValue, @Nullable Player player) {
        super(blockDefinition, location, player);
        this.instance = instance;
        this.stateKey = stateKey;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public PlacedBlockInstance getInstance() {
        return instance;
    }

    public String getStateKey() {
        return stateKey;
    }

    public Object getOldValue() {
        return oldValue;
    }

    public Object getNewValue() {
        return newValue;
    }

    public void setNewValue(Object newValue) {
        this.newValue = newValue;
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

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

