package org.bg52.synthetix.api.event;

import org.bg52.synthetix.item.ItemDefinition;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when a player interacts (left-click, right-click, physical pressure) while holding a Synthetix custom item.
 * Downstream plugins (like CuriosPaper or RPG skill plugins) can hook in to execute abilities, spells, or open menus.
 */
public class SynthetixItemInteractEvent extends SynthetixItemEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;

    private final Player player;
    private final Action action;
    private final EquipmentSlot hand;
    private final Block clickedBlock;
    private final BlockFace blockFace;

    private Event.Result useClickedBlock = Event.Result.DEFAULT;
    private Event.Result useItemInHand = Event.Result.DEFAULT;

    public SynthetixItemInteractEvent(@NotNull Player player, @Nullable ItemDefinition itemDefinition,
                                     @NotNull ItemStack itemStack, @NotNull Action action,
                                     @NotNull EquipmentSlot hand, @Nullable Block clickedBlock,
                                     @Nullable BlockFace blockFace) {
        super(itemDefinition, itemStack);
        this.player = player;
        this.action = action;
        this.hand = hand;
        this.clickedBlock = clickedBlock;
        this.blockFace = blockFace;
    }

    @NotNull
    public Player getPlayer() {
        return player;
    }

    @NotNull
    public Action getAction() {
        return action;
    }

    @NotNull
    public EquipmentSlot getHand() {
        return hand;
    }

    @Nullable
    public Block getClickedBlock() {
        return clickedBlock;
    }

    @Nullable
    public BlockFace getBlockFace() {
        return blockFace;
    }

    @NotNull
    public Event.Result useClickedBlock() {
        return useClickedBlock;
    }

    public void setUseClickedBlock(@NotNull Event.Result useClickedBlock) {
        this.useClickedBlock = useClickedBlock;
    }

    @NotNull
    public Event.Result useItemInHand() {
        return useItemInHand;
    }

    public void setUseItemInHand(@NotNull Event.Result useItemInHand) {
        this.useItemInHand = useItemInHand;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
        this.useClickedBlock = cancel ? Event.Result.DENY : Event.Result.ALLOW;
        this.useItemInHand = cancel ? Event.Result.DENY : Event.Result.ALLOW;
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

