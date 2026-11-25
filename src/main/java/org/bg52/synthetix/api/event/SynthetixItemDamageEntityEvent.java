package org.bg52.synthetix.api.event;

import org.bg52.synthetix.item.ItemDefinition;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when a player hits/damages an entity while holding a Synthetix custom weapon or item.
 * Allows plugins to implement custom combat abilities, dynamic damage scaling, or weapon traits.
 */
public class SynthetixItemDamageEntityEvent extends SynthetixItemEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;

    private final Player player;
    private final Entity target;
    private double damage;

    public SynthetixItemDamageEntityEvent(@NotNull Player player, @NotNull Entity target,
                                         @Nullable ItemDefinition itemDefinition, @NotNull ItemStack itemStack,
                                         double damage) {
        super(itemDefinition, itemStack);
        this.player = player;
        this.target = target;
        this.damage = damage;
    }

    @NotNull
    public Player getPlayer() {
        return player;
    }

    @NotNull
    public Entity getTarget() {
        return target;
    }

    public double getDamage() {
        return damage;
    }

    public void setDamage(double damage) {
        this.damage = damage;
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

