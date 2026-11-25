package org.bg52.synthetix.api.event;

import org.bg52.synthetix.entity.animation.EntityState;
import org.bg52.synthetix.entity.runtime.CustomEntityInstance;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a custom entity changes behavioral/animation state (e.g. IDLE -> WALK -> ATTACK).
 */
public class SynthetixEntityStateChangeEvent extends SynthetixEntityEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;

    private final EntityState oldState;
    private EntityState newState;

    public SynthetixEntityStateChangeEvent(CustomEntityInstance entityInstance, EntityState oldState, EntityState newState) {
        super(entityInstance);
        this.oldState = oldState;
        this.newState = newState;
    }

    public EntityState getOldState() {
        return oldState;
    }

    public EntityState getNewState() {
        return newState;
    }

    public void setNewState(EntityState newState) {
        this.newState = newState;
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

