package org.bg52.synthetix.api.event;

import org.bg52.synthetix.recipe.SynthetixRecipe;
import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when a furnace, blast furnace, smoker, or campfire cooks or smelts a Synthetix recipe or item.
 */
public class SynthetixSmeltEvent extends SynthetixEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;

    private final Block block;
    private final ItemStack source;
    private ItemStack result;
    private final SynthetixRecipe recipe;

    public SynthetixSmeltEvent(@NotNull Block block, @NotNull ItemStack source,
                               @NotNull ItemStack result, @Nullable SynthetixRecipe recipe) {
        this.block = block;
        this.source = source;
        this.result = result;
        this.recipe = recipe;
    }

    @NotNull
    public Block getBlock() {
        return block;
    }

    @NotNull
    public ItemStack getSource() {
        return source;
    }

    @NotNull
    public ItemStack getResult() {
        return result;
    }

    public void setResult(@NotNull ItemStack result) {
        this.result = result;
    }

    @Nullable
    public SynthetixRecipe getRecipe() {
        return recipe;
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

