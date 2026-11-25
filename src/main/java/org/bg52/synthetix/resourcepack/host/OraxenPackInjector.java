package org.bg52.synthetix.resourcepack.host;

import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.scheduler.PlatformScheduler;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles injection of Synthetix's final resource pack into Oraxen.
 * Supports uploads folder synchronization (plugins/Oraxen/pack/uploads/synthetix.zip)
 * and dynamic injection into OraxenPackGeneratedEvent.
 */
public class OraxenPackInjector implements PackHostInjector, Listener {

    private final SynthetixPlugin plugin;
    private final File oraxenUploadsDir;
    private boolean registered = false;
    private volatile boolean inPackGenerate = false;
    private volatile boolean serverLoaded = false;

    public OraxenPackInjector(SynthetixPlugin plugin) {
        this.plugin = plugin;
        File oraxenDir = new File(plugin.getDataFolder().getParentFile(), "Oraxen");
        this.oraxenUploadsDir = new File(oraxenDir, "pack/uploads");

        PlatformScheduler.get().runGlobal(() -> {
            this.serverLoaded = true;
        });
    }

    @Override
    public PackHostPlugin getHostPlugin() {
        return PackHostPlugin.ORAXEN;
    }

    @Override
    public boolean inject(File packZip, File buildDir) {
        boolean success = false;
        try {
            if ((packZip != null && packZip.exists()) || (buildDir != null && buildDir.exists())) {
                if (!oraxenUploadsDir.exists()) {
                    oraxenUploadsDir.mkdirs();
                }
                File targetZip = new File(oraxenUploadsDir, "synthetix.zip");
                PackFileUtils.writeHostCleanZip(packZip, buildDir, targetZip);
                success = true;
                plugin.getLogger().info("[PackHost-Oraxen] Successfully synced final resource pack to " + targetZip.getPath());
            }

            if (!inPackGenerate && serverLoaded) {
                requestOraxenPackReload();
            }
        } catch (Exception e) {
            plugin.getLogger().severe("[PackHost-Oraxen] Failed to inject pack into Oraxen uploads: " + e.getMessage());
        }
        return success;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void registerDynamicListeners() {
        if (registered) return;

        try {
            Class<?> clazz = Class.forName("io.th0rgal.oraxen.api.events.OraxenPackGeneratedEvent");
            if (Event.class.isAssignableFrom(clazz)) {
                Class<? extends Event> eventClass = (Class<? extends Event>) clazz;

                EventExecutor executor = (listener, event) -> {
                    inPackGenerate = true;
                    try {
                        if (plugin.getResourcePackManager().isBuilding()) return;

                        if (plugin.getResourcePackManager().isDirty() || !plugin.getResourcePackManager().getPackFile().exists()) {
                            plugin.getResourcePackManager().generatePackAsync().join();
                        }

                        Method getOutputMethod = event.getClass().getMethod("getOutput");
                        Object outputList = getOutputMethod.invoke(event);
                        if (outputList instanceof List list) {
                            Class<?> virtualFileClass = Class.forName("io.th0rgal.oraxen.utils.VirtualFile");

                            File zip = plugin.getResourcePackManager().getPackFile();
                            if (zip != null && zip.exists()) {
                                Set<String> existingPaths = new HashSet<>();
                                for (Object item : list) {
                                    if (item != null) {
                                        try {
                                            Method getPath = item.getClass().getMethod("getPath");
                                            Object p = getPath.invoke(item);
                                            if (p != null) {
                                                existingPaths.add(p.toString().replace('\\', '/'));
                                            }
                                        } catch (Throwable ignored) {}
                                    }
                                }

                                Map<String, byte[]> entries = PackFileUtils.readZipEntries(zip);
                                int injected = 0;
                                for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                                    String path = entry.getKey();
                                    if (path.equalsIgnoreCase("pack.png") || path.equalsIgnoreCase("pack.mcmeta")) {
                                        continue;
                                    }
                                    String normalized = path.replace('\\', '/');
                                    while (normalized.startsWith("/")) {
                                        normalized = normalized.substring(1);
                                    }
                                    if (existingPaths.contains(normalized)) {
                                        continue;
                                    }
                                    Object vf = createVirtualFile(virtualFileClass, normalized, entry.getValue());
                                    if (vf != null) {
                                        list.add(vf);
                                        injected++;
                                    }
                                }
                                if (injected > 0) {
                                    plugin.getLogger().info("[PackHost-Oraxen] Dynamically injected " + injected
                                            + " Synthetix virtual files into OraxenPackGeneratedEvent!");
                                }
                            }
                        }
                    } catch (Throwable t) {
                        plugin.getLogger().warning("[PackHost-Oraxen] Error during Oraxen event injection: " + t.getMessage());
                    } finally {
                        inPackGenerate = false;
                    }
                };

                plugin.getServer().getPluginManager().registerEvent(eventClass, this, EventPriority.NORMAL, executor, plugin);
                registered = true;
                plugin.getLogger().info("[PackHost-Oraxen] Successfully hooked OraxenPackGeneratedEvent!");
            }
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable t) {
            plugin.getLogger().warning("[PackHost-Oraxen] Could not register dynamic Oraxen listener: " + t.getMessage());
        }
    }

    private Object createVirtualFile(Class<?> vfClass, String path, byte[] data) {
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        String parentFolder = "";
        String fileName = normalized;
        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash >= 0) {
            parentFolder = normalized.substring(0, lastSlash);
            fileName = normalized.substring(lastSlash + 1);
        }

        // 1. Modern Oraxen (parentFolder, name, InputStream)
        try {
            Constructor<?> c = vfClass.getConstructor(String.class, String.class, InputStream.class);
            return c.newInstance(parentFolder, fileName, new ByteArrayInputStream(data));
        } catch (NoSuchMethodException ignored) {}
        catch (Throwable ignored) {}

        // 2. Modern Oraxen (parentFolder, name, Supplier<InputStream>)
        try {
            Constructor<?> c = vfClass.getConstructor(String.class, String.class, java.util.function.Supplier.class);
            java.util.function.Supplier<InputStream> supplier = () -> new ByteArrayInputStream(data);
            return c.newInstance(parentFolder, fileName, supplier);
        } catch (NoSuchMethodException ignored) {}
        catch (Throwable ignored) {}

        // 3. Legacy Oraxen (String, byte[])
        try {
            Constructor<?> c = vfClass.getConstructor(String.class, byte[].class);
            return c.newInstance(normalized, data);
        } catch (NoSuchMethodException ignored) {}
        catch (Throwable ignored) {}

        // 4. Legacy Oraxen (String, InputStream)
        try {
            Constructor<?> c = vfClass.getConstructor(String.class, InputStream.class);
            return c.newInstance(normalized, new ByteArrayInputStream(data));
        } catch (NoSuchMethodException ignored) {}
        catch (Throwable ignored) {}

        return null;
    }

    private void requestOraxenPackReload() {
        PlatformScheduler.get().runGlobal(() -> {
            try {
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), "oraxen reload pack");
            } catch (Throwable ignored) {
            }
        });
    }

    @Override
    public boolean triggerHostRedistribution(CommandSender sender, String targetPlayerOrAll) {
        plugin.getLogger().info("[PackHost-Oraxen] Triggering Oraxen pack reload & rebuild...");
        plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), "oraxen reload pack");
        if (targetPlayerOrAll != null && !targetPlayerOrAll.equalsIgnoreCase("all") && !targetPlayerOrAll.equalsIgnoreCase("*")) {
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), "oraxen pack send " + targetPlayerOrAll);
        }
        return true;
    }
}

