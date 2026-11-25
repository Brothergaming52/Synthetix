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
 * Fired when an existing custom block morphs or transforms into another custom block type.
 * Useful for machine state transitions (e.g. idle -> running gearbox, engaged clutch, deployed drill).
 */
public class SynthetixBlockTransformEvent extends SynthetixBlockEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;

    private final PlacedBlockInstance instance;
    private final CustomBlockDefinition oldDefinition;
    private CustomBlockDefinition newDefinition;
    private final String oldBlockId;
    private String newBlockId;

    public SynthetixBlockTransformEvent(CustomBlockDefinition oldDefinition, CustomBlockDefinition newDefinition,
                                       Location location, PlacedBlockInstance instance,
                                       String oldBlockId, String newBlockId, @Nullable Player player) {
        super(oldDefinition, location, player);
        this.instance = instance;
        this.oldDefinition = oldDefinition;
        this.newDefinition = newDefinition;
        this.oldBlockId = oldBlockId;
        this.newBlockId = newBlockId;
    }

    public PlacedBlockInstance getInstance() {
        return instance;
    }

    public CustomBlockDefinition getOldDefinition() {
        return oldDefinition;
    }

    public CustomBlockDefinition getNewDefinition() {
        return newDefinition;
    }

    public void setNewDefinition(CustomBlockDefinition newDefinition) {
        this.newDefinition = newDefinition;
        if (newDefinition != null) {
            this.newBlockId = newDefinition.getId();
        }
    }

    public String getOldBlockId() {
        return oldBlockId;
    }

    public String getNewBlockId() {
        return newBlockId;
    }

    public void setNewBlockId(String newBlockId) {
        this.newBlockId = newBlockId;
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

