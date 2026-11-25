package org.bg52.synthetix.keybind;

import org.bg52.synthetix.SynthetixPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;

/**
 * High-performance Bukkit event listener dispatching input events to the KeybindManager.
 */
public class KeybindListener implements Listener {

    private final KeybindManager keybindManager;

    public KeybindListener(KeybindManager keybindManager) {
        this.keybindManager = keybindManager;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        keybindManager.handleSneakToggle(event.getPlayer(), event.isSneaking());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (keybindManager.handleSwapHand(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (keybindManager.handleDropItem(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (keybindManager.handleInteract(event.getPlayer(), event.getAction())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeldChange(PlayerItemHeldEvent event) {
        keybindManager.handleHotbarSwitch(event.getPlayer(), event.getNewSlot());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        keybindManager.clearPlayer(event.getPlayer());
    }
}

