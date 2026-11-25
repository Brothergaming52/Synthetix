package org.bg52.synthetix.api.event;

import org.bg52.synthetix.block.CustomBlockDefinition;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when an explosion damages or destroys a custom block.
 */
public class SynthetixBlockExplodeEvent extends SynthetixBlockEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;
    private final Entity explosionSource;
    private final float explosionPower;
    private boolean shouldBreak;

    public SynthetixBlockExplodeEvent(CustomBlockDefinition blockDefinition, Location location, Entity explosionSource, float explosionPower, boolean shouldBreak) {
        super(blockDefinition, location, null);
        this.explosionSource = explosionSource;
        this.explosionPower = explosionPower;
        this.shouldBreak = shouldBreak;
    }

    public Entity getExplosionSource() {
        return explosionSource;
    }

    public float getExplosionPower() {
        return explosionPower;
    }

    public boolean isShouldBreak() {
        return shouldBreak;
    }

    public void setShouldBreak(boolean shouldBreak) {
        this.shouldBreak = shouldBreak;
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

