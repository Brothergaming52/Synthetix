package org.bg52.synthetix.api.event;

import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a custom item definition is removed/unregistered from Synthetix.
 */
public class SynthetixItemUnregisterEvent extends SynthetixEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final String itemId;

    public SynthetixItemUnregisterEvent(@NotNull String itemId) {
        this.itemId = itemId;
    }

    @NotNull
    public String getItemId() {
        return itemId;
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

