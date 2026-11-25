package org.bg52.synthetix.listener;

import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.gui.bedrock.BedrockAnvilGUI;
import org.bg52.synthetix.gui.bedrock.BedrockSmithingGUI;
import org.bg52.synthetix.scheduler.PlatformScheduler;
import org.bg52.synthetix.util.BedrockUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Intercepts Anvil and Smithing Table interactions for Bedrock Edition players
 * and opens dedicated 3-row chest GUIs to avoid Geyser client desyncs and ghost items.
 */
public class BedrockContainerListener implements Listener {

    private final SynthetixPlugin plugin;
    private final BedrockAnvilGUI anvilGUI;
    private final BedrockSmithingGUI smithingGUI;

    public BedrockContainerListener(SynthetixPlugin plugin, BedrockAnvilGUI anvilGUI, BedrockSmithingGUI smithingGUI) {
        this.plugin = plugin;
        this.anvilGUI = anvilGUI;
        this.smithingGUI = smithingGUI;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        Player player = event.getPlayer();

        if (!BedrockUtil.isBedrockPlayer(player)) {
            return;
        }

        Material type = block.getType();
        if (isAnvilBlock(type)) {
            if (plugin.getSettingsManager() != null && !plugin.getSettingsManager().isFeatureEnabled(org.bg52.synthetix.settings.GlobalFeature.GEYSER_CONTAINERS)) {
                return;
            }
            if (plugin.getConfig().getBoolean("geyser.custom-anvil-gui", true)) {
                event.setCancelled(true);
                anvilGUI.open(player);
            }
        } else if (type == Material.SMITHING_TABLE) {
            if (plugin.getSettingsManager() != null && !plugin.getSettingsManager().isFeatureEnabled(org.bg52.synthetix.settings.GlobalFeature.GEYSER_CONTAINERS)) {
                return;
            }
            if (plugin.getConfig().getBoolean("geyser.custom-smithing-gui", true)) {
                event.setCancelled(true);
                smithingGUI.open(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        if (!BedrockUtil.isBedrockPlayer(player)) {
            return;
        }

        InventoryType type = event.getInventory().getType();
        if (type == InventoryType.ANVIL) {
            if (plugin.getSettingsManager() != null && !plugin.getSettingsManager().isFeatureEnabled(org.bg52.synthetix.settings.GlobalFeature.GEYSER_CONTAINERS)) {
                return;
            }
            if (!plugin.getConfig().getBoolean("geyser.custom-anvil-gui", true)) {
                return;
            }
            if (player.hasMetadata("synthetix_bypass_anvil")) {
                player.removeMetadata("synthetix_bypass_anvil", plugin);
                return;
            }
            if (event.getInventory().getHolder() != anvilGUI) {
                event.setCancelled(true);
                PlatformScheduler.get().runEntity(player, () -> anvilGUI.open(player));
            }
        } else if ("SMITHING".equals(type.name())) {
            if (plugin.getSettingsManager() != null && !plugin.getSettingsManager().isFeatureEnabled(org.bg52.synthetix.settings.GlobalFeature.GEYSER_CONTAINERS)) {
                return;
            }
            if (!plugin.getConfig().getBoolean("geyser.custom-smithing-gui", true)) {
                return;
            }
            if (player.hasMetadata("synthetix_bypass_smithing")) {
                player.removeMetadata("synthetix_bypass_smithing", plugin);
                return;
            }
            if (event.getInventory().getHolder() != smithingGUI) {
                event.setCancelled(true);
                PlatformScheduler.get().runEntity(player, () -> smithingGUI.open(player));
            }
        }
    }

    private boolean isAnvilBlock(Material material) {
        if (material == Material.ANVIL) return true;
        try {
            if (material == Material.valueOf("CHIPPED_ANVIL") || material == Material.valueOf("DAMAGED_ANVIL")) {
                return true;
            }
        } catch (IllegalArgumentException ignored) {
        }
        return false;
    }
}

