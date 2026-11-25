package org.bg52.synthetix.api.event;

import org.bg52.synthetix.recipe.SynthetixRecipe;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player takes a crafted Synthetix item out of the crafting result slot.
 */
public class SynthetixItemCraftEvent extends SynthetixEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;

    private final Player player;
    private final SynthetixRecipe recipe;
    private final ItemStack result;
    private final ClickType clickType;
    private final boolean isShiftClick;

    public SynthetixItemCraftEvent(@NotNull Player player, @NotNull SynthetixRecipe recipe,
                                   @NotNull ItemStack result, @NotNull ClickType clickType, boolean isShiftClick) {
        this.player = player;
        this.recipe = recipe;
        this.result = result;
        this.clickType = clickType;
        this.isShiftClick = isShiftClick;
    }

    @NotNull
    public Player getPlayer() {
        return player;
    }

    @NotNull
    public SynthetixRecipe getRecipe() {
        return recipe;
    }

    @NotNull
    public ItemStack getResult() {
        return result;
    }

    @NotNull
    public ClickType getClickType() {
        return clickType;
    }

    public boolean isShiftClick() {
        return isShiftClick;
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

