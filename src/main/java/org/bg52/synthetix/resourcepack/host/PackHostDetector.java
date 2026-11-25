package org.bg52.synthetix.resourcepack.host;

import org.bg52.synthetix.SynthetixPlugin;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/**
 * Detects presence and capabilities of external resource pack host plugins.
 */
public class PackHostDetector {

    private final SynthetixPlugin plugin;

    public PackHostDetector(SynthetixPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Detects the best active external pack host plugin.
     * Prioritizes desired host if specified and available.
     * In AUTO mode, checks RSPM, Nexo, ItemsAdder, Oraxen, CraftEngine in order.
     */
    public PackHostPlugin detectActiveHost(PackHostPlugin desired) {
        PluginManager pm = plugin.getServer().getPluginManager();

        if (desired != null && desired != PackHostPlugin.AUTO && desired != PackHostPlugin.NONE) {
            if (isHostAvailable(desired, pm)) {
                return desired;
            }
        }

        if (desired == PackHostPlugin.NONE) {
            return PackHostPlugin.NONE;
        }

        // Automatic priority detection
        PackHostPlugin[] order = {
                PackHostPlugin.RSPM,
                PackHostPlugin.NEXO,
                PackHostPlugin.ITEMSADDER,
                PackHostPlugin.ORAXEN,
                PackHostPlugin.CRAFTENGINE
        };

        for (PackHostPlugin candidate : order) {
            if (isHostAvailable(candidate, pm)) {
                return candidate;
            }
        }

        return PackHostPlugin.NONE;
    }

    public boolean isHostAvailable(PackHostPlugin host, PluginManager pm) {
        if (host == null || host == PackHostPlugin.NONE || host == PackHostPlugin.AUTO) {
            return false;
        }

        Plugin p = pm.getPlugin(host.getPluginName());
        return p != null && p.isEnabled();
    }
}

