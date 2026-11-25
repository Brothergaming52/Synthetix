package org.bg52.synthetix.item.update;

import org.bukkit.inventory.ItemStack;

/**
 * Extension hook allowing addon plugins (such as ProbablyBackpacks or PaperArtifacts)
 * to register custom item rebuild and modernization logic with Synthetix.
 */
public interface ItemRebuilder {

    /**
     * Checks if this rebuilder handles the given item stack.
     *
     * @param item The ItemStack to inspect
     * @return true if this rebuilder can process the item
     */
    boolean canRebuild(ItemStack item);

    /**
     * Checks if the item is outdated and needs to be rebuilt.
     *
     * @param item The ItemStack to inspect
     * @return true if the item needs rebuilding
     */
    boolean isOutdated(ItemStack item);

    /**
     * Rebuilds the item to the latest definition and visual standard while preserving
     * all dynamic and player-added attributes (UUID, enchants, custom name, damage, contents).
     *
     * @param item The item to rebuild
     * @return The updated ItemStack
     */
    ItemStack rebuild(ItemStack item);
}

