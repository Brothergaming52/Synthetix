package org.bg52.synthetix.api.event;

import org.bg52.synthetix.recipe.SynthetixRecipe;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired during crafting preparation when a player forms a valid Synthetix recipe or places items in a crafting grid.
 * Allows modifying the resulting item, changing the output yield, or cancelling crafting entirely.
 */
public class SynthetixPrepareCraftEvent extends SynthetixEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;

    private final Player player;
    private final SynthetixRecipe recipe;
    private final ItemStack[] matrix;
    private ItemStack result;

    public SynthetixPrepareCraftEvent(@NotNull Player player, @NotNull SynthetixRecipe recipe,
                                     @NotNull ItemStack[] matrix, @Nullable ItemStack result) {
        this.player = player;
        this.recipe = recipe;
        this.matrix = matrix;
        this.result = result;
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
    public ItemStack[] getMatrix() {
        return matrix;
    }

    @Nullable
    public ItemStack getResult() {
        return result;
    }

    public void setResult(@Nullable ItemStack result) {
        this.result = result;
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

