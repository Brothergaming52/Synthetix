package org.bg52.synthetix.api.event;

import org.bg52.synthetix.entity.model.EntityHitboxSpec;
import org.bg52.synthetix.entity.runtime.CustomEntityInstance;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when a custom Synthetix entity is struck, either on its primary driver body
 * or on a precision sub-voxel interaction hitbox.
 */
public class SynthetixEntityDamageEvent extends SynthetixEntityEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;

    private final Entity damager;
    private final EntityHitboxSpec hitboxSpec; // null if hit driver directly
    private double baseDamage;
    private double multiplier;

    public SynthetixEntityDamageEvent(CustomEntityInstance entityInstance, Entity damager, @Nullable EntityHitboxSpec hitboxSpec, double baseDamage, double multiplier) {
        super(entityInstance);
        this.damager = damager;
        this.hitboxSpec = hitboxSpec;
        this.baseDamage = baseDamage;
        this.multiplier = multiplier;
    }

    public Entity getDamager() {
        return damager;
    }

    @Nullable
    public EntityHitboxSpec getHitboxSpec() {
        return hitboxSpec;
    }

    public double getBaseDamage() {
        return baseDamage;
    }

    public void setBaseDamage(double baseDamage) {
        this.baseDamage = baseDamage;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(double multiplier) {
        this.multiplier = multiplier;
    }

    public double getFinalDamage() {
        return baseDamage * multiplier;
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

