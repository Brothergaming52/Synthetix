package org.bg52.synthetix.api.event;

import org.bg52.synthetix.item.ItemDefinition;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a new custom item definition is registered into the Synthetix item registry.
 */
public class SynthetixItemRegisterEvent extends SynthetixEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final ItemDefinition itemDefinition;

    public SynthetixItemRegisterEvent(@NotNull ItemDefinition itemDefinition) {
        this.itemDefinition = itemDefinition;
    }

    @NotNull
    public ItemDefinition getItemDefinition() {
        return itemDefinition;
    }

    public String getItemId() {
        return itemDefinition.getId();
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

