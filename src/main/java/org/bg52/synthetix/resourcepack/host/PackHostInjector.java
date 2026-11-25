package org.bg52.synthetix.resourcepack.host;

import org.bukkit.command.CommandSender;

import java.io.File;

/**
 * Common contract for external resource pack hosting and injection bridges.
 */
public interface PackHostInjector {

    /**
     * Identifies the external plugin supported by this injector.
     */
    PackHostPlugin getHostPlugin();

    /**
     * Injects the compiled final resource pack into the target host plugin.
     *
     * @param packZip  The compiled resource-pack.zip.
     * @param buildDir The unzipped final resource pack build directory.
     * @return true if injection succeeded.
     */
    boolean inject(File packZip, File buildDir);

    /**
     * Registers dynamic Bukkit event listeners for reactive recompilation/re-merging.
     */
    void registerDynamicListeners();

    /**
     * Triggers the host plugin's internal pack rebuilding, reload, and player redistribution.
     *
     * @param sender            The command sender requesting redistribution, or null.
     * @param targetPlayerOrAll The target player username, or "all" / "*".
     * @return true if successfully initiated.
     */
    default boolean triggerHostRedistribution(CommandSender sender, String targetPlayerOrAll) {
        return false;
    }
}

