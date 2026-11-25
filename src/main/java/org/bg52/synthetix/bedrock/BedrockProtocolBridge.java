package org.bg52.synthetix.bedrock;

import org.bg52.synthetix.SynthetixPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Universal protocol bridge for Bedrock / Geyser / Floodgate detection.
 * Uses resilient reflection to integrate with Floodgate API and Geyser API
 * without compile-time hard dependencies, preventing ClassNotFoundException.
 */
public class BedrockProtocolBridge implements Listener {

    private final SynthetixPlugin plugin;
    private final Set<UUID> bedrockPlayers = ConcurrentHashMap.newKeySet();

    private boolean floodgateDetected = false;
    private boolean geyserDetected = false;

    // Reflection handles
    private Object floodgateApiInstance = null;
    private Method floodgateIsPlayerMethod = null;

    private Object geyserApiInstance = null;
    private Method geyserIsPlayerMethod = null;

    public BedrockProtocolBridge(SynthetixPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        detectAPIs();

        // Scan any already connected players (e.g. on plugin reload)
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (testIsBedrockPlayer(player)) {
                bedrockPlayers.add(player.getUniqueId());
            }
        }

        plugin.getLogger().info("[BedrockBridge] Initialized. Floodgate: " + floodgateDetected + ", Geyser: " + geyserDetected
                + ", Online Bedrock players: " + bedrockPlayers.size());
    }

    public void shutdown() {
        bedrockPlayers.clear();
    }

    private void detectAPIs() {
        // 1. Try Floodgate API
        if (!floodgateDetected) {
            try {
                if (Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
                    Class<?> floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                    Method getInstanceMethod = floodgateApiClass.getMethod("getInstance");
                    this.floodgateApiInstance = getInstanceMethod.invoke(null);
                    this.floodgateIsPlayerMethod = floodgateApiClass.getMethod("isFloodgatePlayer", UUID.class);
                    this.floodgateDetected = true;
                }
            } catch (Throwable t) {
                this.floodgateDetected = false;
            }
        }

        // 2. Try Geyser API
        if (!geyserDetected) {
            try {
                if (Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot") || Bukkit.getPluginManager().isPluginEnabled("Geyser")) {
                    Class<?> geyserApiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
                    Method apiMethod = geyserApiClass.getMethod("api");
                    this.geyserApiInstance = apiMethod.invoke(null);
                    this.geyserIsPlayerMethod = geyserApiClass.getMethod("isBedrockPlayer", UUID.class);
                    this.geyserDetected = true;
                }
            } catch (Throwable t) {
                this.geyserDetected = false;
            }
        }
    }

    public boolean isFloodgatePresent() {
        if (!floodgateDetected) detectAPIs();
        return floodgateDetected;
    }

    public boolean isGeyserPresent() {
        if (!geyserDetected) detectAPIs();
        return geyserDetected;
    }

    public boolean isBedrockPlayer(Player player) {
        if (player == null) return false;
        return isBedrockPlayer(player.getUniqueId());
    }

    public boolean isBedrockPlayer(UUID uuid) {
        if (uuid == null) return false;
        if (bedrockPlayers.contains(uuid)) return true;

        Player player = Bukkit.getPlayer(uuid);
        if (player != null && testIsBedrockPlayer(player)) {
            bedrockPlayers.add(uuid);
            return true;
        }
        return false;
    }

    private boolean testIsBedrockPlayer(Player player) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();

        // Lazy re-detection if APIs weren't available during early init
        if (!floodgateDetected || !geyserDetected) {
            detectAPIs();
        }

        // 1. Floodgate Reflection Check
        if (floodgateDetected && floodgateApiInstance != null && floodgateIsPlayerMethod != null) {
            try {
                Object result = floodgateIsPlayerMethod.invoke(floodgateApiInstance, uuid);
                if (result instanceof Boolean b && b) {
                    return true;
                }
            } catch (Exception ignored) {}
        }

        // 2. Geyser Reflection Check
        if (geyserDetected && geyserApiInstance != null && geyserIsPlayerMethod != null) {
            try {
                Object result = geyserIsPlayerMethod.invoke(geyserApiInstance, uuid);
                if (result instanceof Boolean b && b) {
                    return true;
                }
            } catch (Exception ignored) {}
        }

        // 3. Heuristic / Offline UUID Check (Floodgate standard UUID prefix for Bedrock: 00000000-0000-0000-)
        if (uuid.getMostSignificantBits() == 0L) {
            return true;
        }

        // 4. Floodgate Username Prefix Check (default prefix is '.' or '*')
        String name = player.getName();
        if (name != null && (name.startsWith(".") || name.startsWith("*"))) {
            return true;
        }

        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (testIsBedrockPlayer(player)) {
            bedrockPlayers.add(player.getUniqueId());
            plugin.getLogger().fine("[BedrockBridge] Detected Bedrock player connection: " + player.getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        bedrockPlayers.remove(event.getPlayer().getUniqueId());
    }

    public Set<UUID> getBedrockPlayerUuids() {
        return Collections.unmodifiableSet(bedrockPlayers);
    }
}

