package org.bg52.synthetix.api.event;

import org.bg52.synthetix.distribution.LootTableRule;
import org.bukkit.NamespacedKey;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when items are injected into a generated container or world loot table.
 * Allows altering or cancelling injected custom loot.
 */
public class SynthetixLootInjectEvent extends SynthetixEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;

    private final NamespacedKey lootTableKey;
    private final LootTableRule rule;
    private ItemStack item;
    private int amount;
    private final double chance;
    private final LootContext context;

    public SynthetixLootInjectEvent(@NotNull NamespacedKey lootTableKey, @NotNull LootTableRule rule,
                                   @NotNull ItemStack item, int amount, double chance,
                                   @Nullable LootContext context) {
        this.lootTableKey = lootTableKey;
        this.rule = rule;
        this.item = item;
        this.amount = amount;
        this.chance = chance;
        this.context = context;
    }

    @NotNull
    public NamespacedKey getLootTableKey() {
        return lootTableKey;
    }

    @NotNull
    public LootTableRule getRule() {
        return rule;
    }

    @NotNull
    public ItemStack getItem() {
        return item;
    }

    public void setItem(@NotNull ItemStack item) {
        this.item = item;
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

    @Nullable
    public LootContext getContext() {
        return context;
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

