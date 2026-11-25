package org.bg52.synthetix.api.editor;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.item.ItemDefinition;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Extension SPI allowing Synthetix and downstream plugins (e.g. CuriosPaper)
 * to register custom category buttons and sub-menus in the In-Game Item Editor.
 */
public interface ItemEditorCategory {

    /**
     * Unique identifier for this category (e.g. "identity", "enchants", "curios_accessories").
     */
    String getId();

    /**
     * Display name formatted as an Adventure Component.
     */
    Component getDisplayName();

    /**
     * Constructs the button icon displayed on the category selector page.
     *
     * @param def The active ItemDefinition being inspected
     * @return The item icon with title and lore
     */
    ItemStack getIcon(ItemDefinition def);

    /**
     * Sort priority. Lower numbers appear first. Core categories default to 10-100.
     */
    default int getPriority() {
        return 100;
    }

    /**
     * Distinguishes core Synthetix categories (Page 1) from third-party/addon plugin categories (Page 2+).
     *
     * @return true if this category belongs to an external addon/plugin
     */
    boolean isPluginCategory();

    /**
     * Opens the category's settings sub-menu for the player.
     *
     * @param player The player editing the item
     * @param def The active ItemDefinition
     * @param onBack Callback to return to the parent editor page
     */
    void open(Player player, ItemDefinition def, Runnable onBack);
}

