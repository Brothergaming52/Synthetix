package org.bg52.synthetix.item.update;

import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.scheduler.PlatformScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Automatically inspects and rebuilds outdated items on the fly during player interactions,
 * logins, and inventory operations.
 */
public class ItemUpdateListener implements Listener {

    private final SynthetixPlugin plugin;
    private final ItemUpdateManager updateManager;

    public ItemUpdateListener(SynthetixPlugin plugin, ItemUpdateManager updateManager) {
        this.plugin = plugin;
        this.updateManager = updateManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlatformScheduler.get().runEntityLater(player, () -> {
            if (player.isOnline()) {
                scanAndRebuildInventory(player.getInventory());
            }
        }, 5L);
    }

    private boolean isSynthetixGUI(Inventory inv) {
        if (inv == null) return false;
        org.bukkit.inventory.InventoryHolder holder = inv.getHolder();
        return holder instanceof org.bg52.synthetix.gui.CanvasHolder
            || holder instanceof org.bg52.synthetix.gui.bedrock.BedrockAnvilGUI
            || holder instanceof org.bg52.synthetix.gui.bedrock.BedrockSmithingGUI;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        Inventory inv = event.getInventory();
        if (!isSynthetixGUI(inv)) {
            scanAndRebuildInventory(inv);
        }
        if (event.getPlayer() instanceof Player player) {
            scanAndRebuildInventory(player.getInventory());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (isSynthetixGUI(event.getClickedInventory())) {
            return;
        }

        ItemStack current = event.getCurrentItem();
        if (current != null && current.getType() != Material.AIR) {
            try {
                if (updateManager.isItemOutdated(current)) {
                    updateManager.rebuildItem(current);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to evaluate/rebuild current item on click: " + e.getMessage());
            }
        }

        ItemStack cursor = event.getCursor();
        if (cursor != null && cursor.getType() != Material.AIR) {
            try {
                if (updateManager.isItemOutdated(cursor)) {
                    updateManager.rebuildItem(cursor);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to evaluate/rebuild cursor item on click: " + e.getMessage());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        ItemStack stack = event.getItem().getItemStack();
        if (stack != null && stack.getType() != Material.AIR) {
            try {
                if (updateManager.isItemOutdated(stack)) {
                    updateManager.rebuildItem(stack);
                    event.getItem().setItemStack(stack);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to evaluate/rebuild picked up item: " + e.getMessage());
            }
        }
    }

    private void scanAndRebuildInventory(Inventory inv) {
        if (inv == null) return;
        ItemStack[] contents = inv.getContents();
        boolean changed = false;

        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack != null && stack.getType() != Material.AIR) {
                try {
                    if (updateManager.isItemOutdated(stack)) {
                        updateManager.rebuildItem(stack);
                        changed = true;
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to evaluate/rebuild item in slot " + i + ": " + e.getMessage());
                }
            }
        }

        if (changed) {
            inv.setContents(contents);
        }
    }
}

