package org.bg52.synthetix.api.event;

import org.bg52.synthetix.entity.runtime.CustomEntityInstance;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when a Custom Entity (such as a Slime or custom mob) attempts to split into child entities on death.
 * Plugins can cancel the event, allow/disallow splitting, customize child counts,
 * or specify whether the spawned entities are custom entities or vanilla entities.
 */
public class SynthetixEntitySplitEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;

    private final CustomEntityInstance instance;
    private int count;
    private boolean allowed;
    private String childCustomEntityId;

    public SynthetixEntitySplitEvent(CustomEntityInstance instance, int count, boolean allowed, String childCustomEntityId) {
        this.instance = instance;
        this.count = count;
        this.allowed = allowed;
        this.childCustomEntityId = childCustomEntityId;
    }

    public CustomEntityInstance getInstance() {
        return instance;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = Math.max(0, count);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }

    public String getChildCustomEntityId() {
        return childCustomEntityId;
    }

    public void setChildCustomEntityId(String childCustomEntityId) {
        this.childCustomEntityId = childCustomEntityId;
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

