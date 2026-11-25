package org.bg52.synthetix.resourcepack.host;

import org.bg52.synthetix.SynthetixPlugin;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Coordinates external resource pack host detection, capability verification,
 * dynamic event hooks, and pack injection.
 */
public class PackHostManager {

    private final SynthetixPlugin plugin;
    private final PackHostDetector detector;
    private final Map<PackHostPlugin, PackHostInjector> injectors;

    private PackHostPlugin desiredHost = PackHostPlugin.AUTO;
    private PackHostPlugin activeHost = null;

    private String lastInjectionStatus = null;
    private long lastInjectionTime = 0L;

    public PackHostManager(SynthetixPlugin plugin) {
        this.plugin = plugin;
        this.detector = new PackHostDetector(plugin);
        this.injectors = new EnumMap<>(PackHostPlugin.class);

        // Register built-in injectors
        this.injectors.put(PackHostPlugin.RSPM, new RspmPackInjector(plugin));
        this.injectors.put(PackHostPlugin.NEXO, new NexoPackInjector(plugin));
        this.injectors.put(PackHostPlugin.ITEMSADDER, new ItemsAdderPackInjector(plugin));
        this.injectors.put(PackHostPlugin.ORAXEN, new OraxenPackInjector(plugin));
        this.injectors.put(PackHostPlugin.CRAFTENGINE, new CraftEnginePackInjector(plugin));
    }

    public void initialize() {
        loadConfiguration();

        if (shouldAttemptInjection()) {
            this.activeHost = detector.detectActiveHost(desiredHost);

            if (this.activeHost != null && this.activeHost != PackHostPlugin.NONE) {
                PackHostInjector injector = injectors.get(this.activeHost);
                if (injector != null) {
                    injector.registerDynamicListeners();
                    plugin.getLogger().info("[PackHost] Resource pack hosting delegated to "
                            + activeHost.getDisplayName() + ". Embedded HTTP server will remain disabled.");
                }
            } else {
                if (desiredHost != PackHostPlugin.AUTO && desiredHost != PackHostPlugin.NONE) {
                    plugin.getLogger().warning("[PackHost] Desired host plugin '"
                            + desiredHost.getDisplayName() + "' was not detected. Falling back to self-hosting.");
                }
            }
        } else {
            this.activeHost = null;
        }
    }

    public void shutdown() {
        this.activeHost = null;
        this.lastInjectionStatus = null;
    }

    public void reload() {
        shutdown();
        initialize();
    }

    public boolean injectFinalPack(File packZip, File buildDir) {
        if (!isActive()) {
            return false;
        }

        PackHostInjector injector = injectors.get(activeHost);
        if (injector == null) {
            return false;
        }

        plugin.getLogger().info("[PackHost] Injecting final resource pack into " + activeHost.getDisplayName() + "...");
        boolean ok = injector.inject(packZip, buildDir);
        this.lastInjectionTime = System.currentTimeMillis();
        if (ok) {
            this.lastInjectionStatus = "Successfully injected into " + activeHost.getDisplayName();
            plugin.getLogger().info("[PackHost] Pack injection into " + activeHost.getDisplayName() + " completed successfully.");
        } else {
            this.lastInjectionStatus = "Failed to inject into " + activeHost.getDisplayName();
            plugin.getLogger().severe("[PackHost] Pack injection into " + activeHost.getDisplayName() + " encountered errors.");
        }
        return ok;
    }

    public boolean forceInject(PackHostPlugin target, File packZip, File buildDir) {
        if (target == null || target == PackHostPlugin.NONE || target == PackHostPlugin.AUTO) {
            return false;
        }
        PackHostInjector injector = injectors.get(target);
        if (injector == null) return false;
        return injector.inject(packZip, buildDir);
    }

    public boolean triggerActiveHostRedistribution(CommandSender sender, String targetPlayerOrAll) {
        if (!isActive()) {
            return false;
        }
        PackHostInjector injector = injectors.get(activeHost);
        if (injector == null) {
            return false;
        }
        return injector.triggerHostRedistribution(sender, targetPlayerOrAll);
    }

    public boolean isActive() {
        return activeHost != null && activeHost != PackHostPlugin.NONE;
    }

    public PackHostPlugin getActiveHost() {
        return activeHost != null ? activeHost : PackHostPlugin.NONE;
    }

    public PackHostPlugin getDesiredHost() {
        return desiredHost;
    }

    public String getLastInjectionStatus() {
        return lastInjectionStatus;
    }

    public long getLastInjectionTime() {
        return lastInjectionTime;
    }

    private void loadConfiguration() {
        String modeStr = plugin.getConfig().getString("resource-pack.hosting-mode", "auto");
        this.desiredHost = PackHostPlugin.fromString(modeStr);
    }

    private boolean shouldAttemptInjection() {
        String mode = plugin.getConfig().getString("resource-pack.hosting-mode", "auto").toUpperCase(Locale.ROOT);
        return mode.equals("AUTO") || mode.equals("PLUGIN")
                || mode.equals("RSPM") || mode.equals("NEXO")
                || mode.equals("ITEMSADDER") || mode.equals("ORAXEN")
                || mode.equals("CRAFTENGINE");
    }
}

