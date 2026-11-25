package org.bg52.synthetix.api.event;

import org.bg52.synthetix.block.CustomBlockDefinition;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Base event for actions and interactions involving custom Synthetix blocks.
 */
public abstract class SynthetixBlockEvent extends SynthetixEvent {

    private final CustomBlockDefinition blockDefinition;
    private final Location location;
    private final Player player;

    public SynthetixBlockEvent(CustomBlockDefinition blockDefinition, Location location, Player player) {
        this.blockDefinition = blockDefinition;
        this.location = location;
        this.player = player;
    }

    public CustomBlockDefinition getBlockDefinition() {
        return blockDefinition;
    }

    public String getBlockId() {
        return blockDefinition != null ? blockDefinition.getId() : null;
    }

    public Location getLocation() {
        return location;
    }

    public Player getPlayer() {
        return player;
    }
}

