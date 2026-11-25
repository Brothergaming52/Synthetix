package org.bg52.synthetix.ability;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/**
 * Base class for extensible 3rd-party triggers.
 * Downstream plugins (such as CuriosPaper) extend this class to hook custom triggers
 * (e.g. on-curio-tick, on-curio-equip, on-curio-unequip) into the Synthetix Ability Engine.
 */
public abstract class CustomItemTrigger {

    private final String id;

    public CustomItemTrigger(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Trigger ID cannot be null or empty!");
        }
        this.id = id.trim().toLowerCase(Locale.ROOT);
    }

    public String getId() {
        return id;
    }

    /**
     * Evaluates whether this trigger should activate for the given context.
     *
     * @param player  The acting player
     * @param item    The custom item involved
     * @param context The Bukkit Event or context object (may be null)
     * @return true if the conditions of this trigger are fulfilled
     */
    public abstract boolean evaluate(Player player, ItemStack item, Event context);
}

