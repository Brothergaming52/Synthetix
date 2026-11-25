package org.bg52.synthetix.api.event;

import org.bg52.synthetix.entity.runtime.CustomEntityInstance;
import org.bukkit.Location;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a custom Synthetix entity is spawned into the world.
 */
public class SynthetixEntitySpawnEvent extends SynthetixEntityEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;

    public SynthetixEntitySpawnEvent(CustomEntityInstance entityInstance) {
        super(entityInstance);
    }

    public Location getLocation() {
        return getEntityInstance() != null && getEntityInstance().getDriverMob() != null
                ? getEntityInstance().getDriverMob().getLocation()
                : null;
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

