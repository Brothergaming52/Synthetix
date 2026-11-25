package org.bg52.synthetix.api.event;

import org.bg52.synthetix.item.ItemDefinition;
import org.bukkit.inventory.ItemStack;

/**
 * Base event for actions and interactions involving a Synthetix item definition or ItemStack.
 */
public abstract class SynthetixItemEvent extends SynthetixEvent {

    private final ItemDefinition itemDefinition;
    private final ItemStack itemStack;

    public SynthetixItemEvent(ItemDefinition itemDefinition, ItemStack itemStack) {
        this.itemDefinition = itemDefinition;
        this.itemStack = itemStack;
    }

    public SynthetixItemEvent(boolean isAsync, ItemDefinition itemDefinition, ItemStack itemStack) {
        super(isAsync);
        this.itemDefinition = itemDefinition;
        this.itemStack = itemStack;
    }

    public ItemDefinition getItemDefinition() {
        return itemDefinition;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public String getItemId() {
        return itemDefinition != null ? itemDefinition.getId() : null;
    }
}

