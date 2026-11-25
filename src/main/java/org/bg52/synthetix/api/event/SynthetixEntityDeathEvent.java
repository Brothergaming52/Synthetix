package org.bg52.synthetix.api.event;

import org.bg52.synthetix.entity.runtime.CustomEntityInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Fired when a custom Synthetix entity is killed.
 */
public class SynthetixEntityDeathEvent extends SynthetixEntityEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player killer;
    private final List<ItemStack> drops;
    private int droppedExp;

    public SynthetixEntityDeathEvent(CustomEntityInstance entityInstance, @Nullable Player killer, List<ItemStack> drops, int droppedExp) {
        super(entityInstance);
        this.killer = killer;
        this.drops = drops;
        this.droppedExp = droppedExp;
    }

    @Nullable
    public Player getKiller() {
        return killer;
    }

    public List<ItemStack> getDrops() {
        return drops;
    }

    public int getDroppedExp() {
        return droppedExp;
    }

    public void setDroppedExp(int droppedExp) {
        this.droppedExp = Math.max(0, droppedExp);
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

