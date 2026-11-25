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
 * Fired when a custom block's animation speed or rotation multiplier is adjusted by a plugin or kinetic network.
 */
public class SynthetixBlockAnimationSpeedChangeEvent extends SynthetixBlockEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;

    private final PlacedBlockInstance instance;
    private final String partId;
    private final double oldSpeed;
    private double newSpeed;

    public SynthetixBlockAnimationSpeedChangeEvent(CustomBlockDefinition blockDefinition, Location location,
                                                   PlacedBlockInstance instance, @Nullable String partId,
                                                   double oldSpeed, double newSpeed, @Nullable Player player) {
        super(blockDefinition, location, player);
        this.instance = instance;
        this.partId = partId;
        this.oldSpeed = oldSpeed;
        this.newSpeed = newSpeed;
    }

    public PlacedBlockInstance getInstance() {
        return instance;
    }

    @Nullable
    public String getPartId() {
        return partId;
    }

    public double getOldSpeed() {
        return oldSpeed;
    }

    public double getNewSpeed() {
        return newSpeed;
    }

    public void setNewSpeed(double newSpeed) {
        this.newSpeed = newSpeed;
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

