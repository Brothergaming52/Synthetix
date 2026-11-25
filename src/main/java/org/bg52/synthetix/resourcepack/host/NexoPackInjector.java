package org.bg52.synthetix.resourcepack.host;

import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.scheduler.PlatformScheduler;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import java.io.File;
import java.lang.reflect.Method;

/**
 * Handles injection of Synthetix's final resource pack into Nexo.
 * Supports pre-generation synchronization into plugins/Nexo/pack/external_packs/,
 * dynamic injection during NexoPrePackGenerateEvent, runtime merging via
 * NexoPack.mergePackFromZip, and automatic '/nexo reload pack' invocation.
 */
public class NexoPackInjector implements PackHostInjector, Listener {

    private final SynthetixPlugin plugin;
    private final File nexoExternalPacksDir;
    private boolean registered = false;
    private volatile boolean inPrePackGenerate = false;
    private volatile boolean serverLoaded = false;

    public NexoPackInjector(SynthetixPlugin plugin) {
        this.plugin = plugin;
        File nexoDir = new File(plugin.getDataFolder().getParentFile(), "Nexo");
        this.nexoExternalPacksDir = new File(nexoDir, "pack/external_packs");

        PlatformScheduler.get().runGlobal(() -> {
            this.serverLoaded = true;
        });
    }

    @Override
    public PackHostPlugin getHostPlugin() {
        return PackHostPlugin.NEXO;
    }

    @Override
    public boolean inject(File packZip, File buildDir) {
        boolean success = false;
        try {
            if ((packZip != null && packZip.exists()) || (buildDir != null && buildDir.exists())) {
                if (!nexoExternalPacksDir.exists()) {
                    nexoExternalPacksDir.mkdirs();
                }
                File targetZip = new File(nexoExternalPacksDir, "synthetix.zip");
                PackFileUtils.writeHostCleanZip(packZip, buildDir, targetZip);
                success = true;
                plugin.getLogger().info("[PackHost-Nexo] Successfully synced final resource pack to " + targetZip.getPath());
            }

            // Runtime dynamic merge via NexoPack.mergePackFromZip(File)
            if (packZip != null && packZip.exists()) {
                try {
                    Class<?> nexoPackClass = Class.forName("com.nexomc.nexo.api.NexoPack");
                    Method mergeMethod = nexoPackClass.getMethod("mergePackFromZip", File.class);
                    mergeMethod.invoke(null, packZip);
                    plugin.getLogger().info("[PackHost-Nexo] Successfully merged Synthetix pack into Nexo runtime pack!");
                } catch (Throwable ignored) {
                }
            }

            if (!inPrePackGenerate && serverLoaded) {
                requestNexoPackReload();
            }
        } catch (Exception e) {
            plugin.getLogger().severe("[PackHost-Nexo] Failed to inject pack into Nexo: " + e.getMessage());
        }
        return success;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void registerDynamicListeners() {
        if (registered) return;

        try {
            Class<?> clazz = Class.forName("com.nexomc.nexo.api.events.resourcepack.NexoPrePackGenerateEvent");
            if (Event.class.isAssignableFrom(clazz)) {
                Class<? extends Event> eventClass = (Class<? extends Event>) clazz;

                EventExecutor executor = (listener, event) -> {
                    inPrePackGenerate = true;
                    try {
                        if (plugin.getResourcePackManager().isDirty() || !plugin.getResourcePackManager().getPackFile().exists()) {
                            plugin.getLogger().info("[PackHost-Nexo] NexoPrePackGenerateEvent detected: compiling Synthetix pack...");
                            plugin.getResourcePackManager().generatePackAsync().join();
                        }
                        inject(plugin.getResourcePackManager().getPackFile(), plugin.getResourcePackManager().getResourcePackDir());
                    } catch (Throwable t) {
                        plugin.getLogger().warning("[PackHost-Nexo] Error during NexoPrePackGenerateEvent sync: " + t.getMessage());
                    } finally {
                        inPrePackGenerate = false;
                    }
                };

                plugin.getServer().getPluginManager().registerEvent(eventClass, this, EventPriority.LOWEST, executor, plugin);
                registered = true;
                plugin.getLogger().info("[PackHost-Nexo] Successfully hooked NexoPrePackGenerateEvent!");
            }
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable t) {
            plugin.getLogger().warning("[PackHost-Nexo] Could not register dynamic Nexo listener: " + t.getMessage());
        }
    }

    private void requestNexoPackReload() {
        PlatformScheduler.get().runGlobal(() -> {
            try {
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), "nexo reload pack");
            } catch (Throwable ignored) {
            }
        });
    }

    @Override
    public boolean triggerHostRedistribution(CommandSender sender, String targetPlayerOrAll) {
        plugin.getLogger().info("[PackHost-Nexo] Triggering Nexo pack rebuild & reload...");
        plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), "nexo pack build");
        if (targetPlayerOrAll != null && !targetPlayerOrAll.equalsIgnoreCase("all") && !targetPlayerOrAll.equalsIgnoreCase("*")) {
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), "nexo pack send " + targetPlayerOrAll);
        }
        return true;
    }
}

