package org.bg52.synthetix.api.event;

import org.bg52.synthetix.distribution.MobDropRule;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when an entity dies and a custom Synthetix mob drop rule rolls a drop.
 * Allows modifying the dropped item, altering drop amounts, or cancelling the drop.
 */
public class SynthetixMobDropEvent extends SynthetixEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;

    private final LivingEntity entity;
    private final Player killer;
    private final MobDropRule rule;
    private ItemStack dropItem;
    private int amount;
    private final double chance;

    public SynthetixMobDropEvent(@NotNull LivingEntity entity, @Nullable Player killer,
                                 @NotNull MobDropRule rule, @NotNull ItemStack dropItem,
                                 int amount, double chance) {
        this.entity = entity;
        this.killer = killer;
        this.rule = rule;
        this.dropItem = dropItem;
        this.amount = amount;
        this.chance = chance;
    }

    @NotNull
    public LivingEntity getEntity() {
        return entity;
    }

    @Nullable
    public Player getKiller() {
        return killer;
    }

    @NotNull
    public MobDropRule getRule() {
        return rule;
    }

    @NotNull
    public ItemStack getDropItem() {
        return dropItem;
    }

    public void setDropItem(@NotNull ItemStack dropItem) {
        this.dropItem = dropItem;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public double getChance() {
        return chance;
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

