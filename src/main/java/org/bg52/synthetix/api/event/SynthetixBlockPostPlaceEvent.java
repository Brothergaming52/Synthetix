package org.bg52.synthetix.api.event;

import org.bg52.synthetix.block.CustomBlockDefinition;
import org.bg52.synthetix.block.PlacedBlockInstance;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Fired immediately after a custom block has been successfully placed into the world.
 * Allows addons and listeners to access or mutate the newly created PlacedBlockInstance,
 * attach custom state data, synchronize visual display entities, or register sub-systems.
 */
public class SynthetixBlockPostPlaceEvent extends SynthetixBlockEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final BlockFace facing;
    private final ItemStack itemInHand;
    private final PlacedBlockInstance instance;

    public SynthetixBlockPostPlaceEvent(CustomBlockDefinition blockDefinition, Location location, Player player, BlockFace facing, ItemStack itemInHand, PlacedBlockInstance instance) {
        super(blockDefinition, location, player);
        this.facing = facing;
        this.itemInHand = itemInHand;
        this.instance = instance;
    }

    public BlockFace getFacing() {
        return facing;
    }

    public ItemStack getItemInHand() {
        return itemInHand;
    }

    public PlacedBlockInstance getInstance() {
        return instance;
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

