package org.bg52.synthetix.resourcepack.host;

import org.bg52.synthetix.SynthetixPlugin;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.lang.reflect.Method;

/**
 * Handles injection of Synthetix's final resource pack into ResourcePackManager (RSPM).
 * Registers Synthetix via ResourcePackManagerAPI.registerLocalResourcePack
 * and syncs the compiled resource-pack.zip into plugins/ResourcePackManager/mixer/synthetix.zip.
 */
public class RspmPackInjector implements PackHostInjector {

    private final SynthetixPlugin plugin;
    private final File rspmMixerDir;

    public RspmPackInjector(SynthetixPlugin plugin) {
        this.plugin = plugin;
        File rspmDir = new File(plugin.getDataFolder().getParentFile(), "ResourcePackManager");
        this.rspmMixerDir = new File(rspmDir, "mixer");
    }

    @Override
    public PackHostPlugin getHostPlugin() {
        return PackHostPlugin.RSPM;
    }

    @Override
    public boolean inject(File packZip, File buildDir) {
        boolean success = false;
        try {
            // 1. Copy final zip into RSPM mixer folder for fallback/direct merging
            if (packZip != null && packZip.exists()) {
                if (!rspmMixerDir.exists()) {
                    rspmMixerDir.mkdirs();
                }
                File targetZip = new File(rspmMixerDir, "synthetix.zip");
                PackFileUtils.copyRecursively(packZip, targetZip);
                success = true;
                plugin.getLogger().info("[PackHost-RSPM] Successfully synced final resource pack to " + targetZip.getPath());
            }

            // 2. Register with ResourcePackManagerAPI via reflection
            registerWithApi();

            // 3. Trigger RSPM pack remix
            triggerRspmReload();

        } catch (Exception e) {
            plugin.getLogger().severe("[PackHost-RSPM] Failed to inject pack into ResourcePackManager: " + e.getMessage());
        }
        return success;
    }

    @Override
    public void registerDynamicListeners() {
        registerWithApi();
    }

    private void registerWithApi() {
        try {
            Class<?> apiClass = Class.forName("com.magmaguy.resourcepackmanager.api.ResourcePackManagerAPI");
            Method registerMethod = apiClass.getMethod(
                    "registerLocalResourcePack",
                    String.class,
                    String.class,
                    boolean.class,
                    boolean.class,
                    boolean.class,
                    String.class
            );

            // Relative path from the server's plugins directory
            String localPath = "Synthetix" + File.separator + "resource-pack.zip";
            registerMethod.invoke(null, "Synthetix", localPath, false, true, true, "synth pack reload");
            plugin.getLogger().info("[PackHost-RSPM] Successfully registered Synthetix pack with ResourcePackManagerAPI!");
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable t) {
            plugin.getLogger().warning("[PackHost-RSPM] Could not register with ResourcePackManagerAPI: " + t.getMessage());
        }
    }

    private void triggerRspmReload() {
        try {
            Class<?> apiClass = Class.forName("com.magmaguy.resourcepackmanager.api.ResourcePackManagerAPI");
            Method reloadMethod = apiClass.getMethod("reloadResourcePack");
            reloadMethod.invoke(null);
            plugin.getLogger().info("[PackHost-RSPM] Triggered ResourcePackManager pack remix.");
        } catch (Throwable ignored) {
        }
    }

    @Override
    public boolean triggerHostRedistribution(CommandSender sender, String targetPlayerOrAll) {
        plugin.getLogger().info("[PackHost-RSPM] Triggering ResourcePackManager remix & distribution...");
        triggerRspmReload();
        if (targetPlayerOrAll != null && !targetPlayerOrAll.equalsIgnoreCase("all") && !targetPlayerOrAll.equalsIgnoreCase("*")) {
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), "rspm apply " + targetPlayerOrAll);
        }
        return true;
    }
}

