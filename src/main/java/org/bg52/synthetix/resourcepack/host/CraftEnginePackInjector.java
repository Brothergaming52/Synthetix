package org.bg52.synthetix.resourcepack.host;

import org.bg52.synthetix.SynthetixPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.nio.file.Path;

/**
 * Handles injection of Synthetix's final resource pack into CraftEngine.
 * Synchronizes assets into plugins/CraftEngine/resources/synthetix/resourcepack/ with pack.yml,
 * and dynamically injects into AsyncResourcePackGenerateEvent.
 */
public class CraftEnginePackInjector implements PackHostInjector, Listener {

    private final SynthetixPlugin plugin;
    private final File craftEnginePackDir;
    private final File craftEnginePackMeta;
    private boolean registered = false;

    public CraftEnginePackInjector(SynthetixPlugin plugin) {
        this.plugin = plugin;
        File ceDir = new File(plugin.getDataFolder().getParentFile(), "CraftEngine");
        File synthCeDir = new File(ceDir, "resources/synthetix");
        this.craftEnginePackDir = new File(synthCeDir, "resourcepack");
        this.craftEnginePackMeta = new File(synthCeDir, "pack.yml");
    }

    @Override
    public PackHostPlugin getHostPlugin() {
        return PackHostPlugin.CRAFTENGINE;
    }

    @Override
    public boolean inject(File packZip, File buildDir) {
        boolean success = false;
        try {
            if (buildDir != null && buildDir.exists() && buildDir.isDirectory()) {
                File assetsSource = new File(buildDir, "assets");
                File assetsTarget = new File(craftEnginePackDir, "assets");
                if (assetsSource.exists() && assetsSource.isDirectory()) {
                    if (!assetsTarget.exists()) {
                        assetsTarget.mkdirs();
                    }
                    PackFileUtils.copyRecursively(assetsSource, assetsTarget);
                }

                if (!craftEnginePackMeta.exists()) {
                    try (FileWriter writer = new FileWriter(craftEnginePackMeta)) {
                        writer.write("enable: true\n");
                        writer.write("namespace: \"synthetix\"\n");
                        writer.write("description: \"Synthetix Resource Pack\"\n");
                    }
                }
                success = true;
                plugin.getLogger().info("[PackHost-CraftEngine] Successfully synced final resource pack assets to " + craftEnginePackDir.getPath());
            }
        } catch (Exception e) {
            plugin.getLogger().severe("[PackHost-CraftEngine] Failed to inject pack into CraftEngine resources: " + e.getMessage());
        }
        return success;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void registerDynamicListeners() {
        if (registered) return;

        try {
            Class<?> clazz = Class.forName("net.momirealms.craftengine.bukkit.api.event.AsyncResourcePackGenerateEvent");
            if (Event.class.isAssignableFrom(clazz)) {
                Class<? extends Event> eventClass = (Class<? extends Event>) clazz;

                EventExecutor executor = (listener, event) -> {
                    try {
                        if (plugin.getResourcePackManager().isDirty() || !plugin.getResourcePackManager().getPackFile().exists()) {
                            plugin.getResourcePackManager().generatePackAsync().join();
                        }
                        Method getFolderMethod = event.getClass().getMethod("resourcePackFolder");
                        Object pathObj = getFolderMethod.invoke(event);
                        if (pathObj instanceof Path path) {
                            File targetFolder = path.toFile();
                            File buildDir = plugin.getResourcePackManager().getResourcePackDir();
                            if (buildDir.exists() && buildDir.isDirectory()) {
                                File assetsSource = new File(buildDir, "assets");
                                File assetsTarget = new File(targetFolder, "assets");
                                if (assetsSource.exists() && assetsSource.isDirectory()) {
                                    PackFileUtils.copyRecursively(assetsSource, assetsTarget);
                                    plugin.getLogger().info("[PackHost-CraftEngine] Dynamically injected Synthetix assets into CraftEngine AsyncResourcePackGenerateEvent!");
                                }
                            }
                        }
                    } catch (Throwable t) {
                        plugin.getLogger().warning("[PackHost-CraftEngine] Error during CraftEngine event injection: " + t.getMessage());
                    }
                };

                plugin.getServer().getPluginManager().registerEvent(eventClass, this, EventPriority.NORMAL, executor, plugin);
                registered = true;
                plugin.getLogger().info("[PackHost-CraftEngine] Successfully hooked CraftEngine AsyncResourcePackGenerateEvent!");
            }
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable t) {
            plugin.getLogger().warning("[PackHost-CraftEngine] Could not register dynamic CraftEngine listener: " + t.getMessage());
        }
    }

    @Override
    public boolean triggerHostRedistribution(CommandSender sender, String targetPlayerOrAll) {
        plugin.getLogger().info("[PackHost-CraftEngine] Triggering CraftEngine pack reload...");
        plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), "ce reload");
        return true;
    }
}

