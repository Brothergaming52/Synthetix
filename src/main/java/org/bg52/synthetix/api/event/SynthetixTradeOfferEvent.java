package org.bg52.synthetix.api.event;

import org.bg52.synthetix.distribution.TradeRule;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.MerchantRecipe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when a custom merchant trade offer is being attached to a villager or wandering trader.
 * Allows modifying the trade ingredients, reward, max uses, or cancelling the offer.
 */
public class SynthetixTradeOfferEvent extends SynthetixEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;

    private final AbstractVillager villager;
    private final Player player;
    private final TradeRule rule;
    private MerchantRecipe merchantRecipe;

    public SynthetixTradeOfferEvent(@NotNull AbstractVillager villager, @Nullable Player player,
                                   @NotNull TradeRule rule, @NotNull MerchantRecipe merchantRecipe) {
        this.villager = villager;
        this.player = player;
        this.rule = rule;
        this.merchantRecipe = merchantRecipe;
    }

    @NotNull
    public AbstractVillager getVillager() {
        return villager;
    }

    @Nullable
    public Player getPlayer() {
        return player;
    }

    @NotNull
    public TradeRule getRule() {
        return rule;
    }

    @NotNull
    public MerchantRecipe getMerchantRecipe() {
        return merchantRecipe;
    }

    public void setMerchantRecipe(@NotNull MerchantRecipe merchantRecipe) {
        this.merchantRecipe = merchantRecipe;
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

