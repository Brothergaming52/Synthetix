package org.bg52.synthetix.resourcepack;

import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.scheduler.PlatformScheduler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Prompts connecting players to download the server resource pack on join.
 */
public class PackPromptListener implements Listener {

    private final SynthetixPlugin plugin;

    public PackPromptListener(SynthetixPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (plugin.getSettingsManager() != null && !plugin.getSettingsManager().isFeatureEnabled(org.bg52.synthetix.settings.GlobalFeature.RESOURCE_PACK_PROMPT)) {
            return;
        }
        if (!plugin.getConfig().getBoolean("resource-pack.prompt-on-join", true)) {
            return;
        }

        Player player = event.getPlayer();
        PlatformScheduler.get().runEntityLater(player, () -> {
            if (player.isOnline()) {
                plugin.getResourcePackManager().sendPackToPlayer(player);
            }
        }, 20L); // 1 second delay for client connection stabilization
    }
}

