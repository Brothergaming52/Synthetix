package org.bg52.synthetix.resourcepack.host;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.bg52.synthetix.SynthetixPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Handles injection of Synthetix's final resource pack into ItemsAdder.
 * Operates primarily via runtime memory injection using ItemsAdderPackCompressedEvent,
 * bypassing heavy disk I/O and preventing ItemsAdder model parse errors.
 * Also includes compatibility sanitization for modern Renderpearl shader compilers (Minecraft 26.3+).
 */
public class ItemsAdderPackInjector implements PackHostInjector, Listener {

    private final SynthetixPlugin plugin;
    private final File iaContentPackDir;
    private final File iaSynthetixRootDir;
    private final Gson gson = new Gson();
    private boolean registered = false;

    public ItemsAdderPackInjector(SynthetixPlugin plugin) {
        this.plugin = plugin;
        File iaDir = new File(plugin.getDataFolder().getParentFile(), "ItemsAdder");
        this.iaSynthetixRootDir = new File(iaDir, "contents/synthetix");
        this.iaContentPackDir = new File(this.iaSynthetixRootDir, "resourcepack");
        cleanLegacyContentDirectory();
    }

    private void cleanLegacyContentDirectory() {
        try {
            if (iaSynthetixRootDir.exists()) {
                PackFileUtils.deleteDirectory(iaSynthetixRootDir);
                plugin.getLogger().info("[PackHost-ItemsAdder] Cleaned legacy plugins/ItemsAdder/contents/synthetix directory "
                        + "to prevent duplicate file scanning, model parsing errors, and main thread freezes.");
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[PackHost-ItemsAdder] Could not clean legacy content folder: " + t.getMessage());
        }
    }

    @Override
    public PackHostPlugin getHostPlugin() {
        return PackHostPlugin.ITEMSADDER;
    }

    @Override
    public boolean inject(File packZip, File buildDir) {
        // If event injection is supported and registered, do not write files into contents/synthetix.
        // Copying hundreds of files into contents/ causes ItemsAdder to parse vanilla model overrides as
        // custom item configs, logs unresolved model errors, and blocks the main server thread for minutes.
        if (registered) {
            plugin.getLogger().info("[PackHost-ItemsAdder] Pack injection managed dynamically via ItemsAdderPackCompressedEvent.");
            return true;
        }

        // Fallback only if dynamic event hook is not supported
        boolean success = false;
        try {
            if (buildDir != null && buildDir.exists() && buildDir.isDirectory()) {
                File assetsSource = new File(buildDir, "assets");
                File assetsTarget = new File(iaContentPackDir, "assets");
                if (assetsSource.exists() && assetsSource.isDirectory()) {
                    if (!assetsTarget.exists()) {
                        assetsTarget.mkdirs();
                    }
                    // Avoid copying assets/minecraft to prevent ItemsAdder model validator errors
                    File[] namespaces = assetsSource.listFiles(File::isDirectory);
                    if (namespaces != null) {
                        for (File ns : namespaces) {
                            if (ns.getName().equalsIgnoreCase("minecraft")) continue;
                            PackFileUtils.copyRecursively(ns, new File(assetsTarget, ns.getName()));
                        }
                    }
                    success = true;
                    plugin.getLogger().info("[PackHost-ItemsAdder] Fallback synced non-vanilla assets to " + assetsTarget.getPath());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("[PackHost-ItemsAdder] Failed to inject pack into ItemsAdder contents: " + e.getMessage());
        }
        return success;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void registerDynamicListeners() {
        if (registered) return;

        try {
            Class<?> clazz = Class.forName("dev.lone.itemsadder.api.Events.ItemsAdderPackCompressedEvent");
            if (Event.class.isAssignableFrom(clazz)) {
                Class<? extends Event> eventClass = (Class<? extends Event>) clazz;

                EventExecutor executor = (listener, event) -> {
                    try {
                        if (plugin.getResourcePackManager().isBuilding()) {
                            return;
                        }

                        if (plugin.getResourcePackManager().isDirty() || !plugin.getResourcePackManager().getPackFile().exists()) {
                            plugin.getResourcePackManager().generatePackAsync().join();
                        }

                        Method setEntryMethod = event.getClass().getMethod("setEntry", String.class, byte[].class);

                        // Special-case compatibility for modern clients (e.g. Minecraft 26.3 ViaVersion):
                        // ItemsAdder injects legacy GLSL 150/330 core shaders (rendertype_text, rendertype_armor_cutout_no_cull)
                        // with overlay formats [88, 9999]. In Minecraft 26.3, Mojang replaced GLSL with Renderpearl (SPIR-V),
                        // which rejects legacy shaders without explicit layout qualifiers, throwing
                        // "Failed to load required shader programs: rendertype_text" and causing the client to drop all packs.
                        // Stripping these incompatible core shaders lets modern clients use vanilla Renderpearl shaders
                        // while keeping all models, textures, sounds, and item definitions.
                        sanitizeIncompatibleShadersAndMetadata(event, setEntryMethod);

                        File zip = plugin.getResourcePackManager().getPackFile();
                        if (zip != null && zip.exists()) {
                            Map<String, byte[]> entries = PackFileUtils.readZipEntries(zip);
                            int injectedCount = 0;
                            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                                String path = entry.getKey();
                                // Skip metadata files
                                if (path.equalsIgnoreCase("pack.png") || path.equalsIgnoreCase("pack.mcmeta")) {
                                    continue;
                                }
                                setEntryMethod.invoke(event, path, entry.getValue());
                                injectedCount++;
                            }
                            plugin.getLogger().info("[PackHost-ItemsAdder] Dynamically injected " + injectedCount
                                    + " Synthetix assets into ItemsAdderPackCompressedEvent!");
                        }
                    } catch (Throwable t) {
                        plugin.getLogger().warning("[PackHost-ItemsAdder] Error during ItemsAdder event injection: " + t.getMessage());
                    }
                };

                plugin.getServer().getPluginManager().registerEvent(eventClass, this, EventPriority.NORMAL, executor, plugin);
                registered = true;
                plugin.getLogger().info("[PackHost-ItemsAdder] Successfully hooked ItemsAdderPackCompressedEvent!");
            }
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable t) {
            plugin.getLogger().warning("[PackHost-ItemsAdder] Could not register dynamic ItemsAdder listener: " + t.getMessage());
        }
    }

    private void sanitizeIncompatibleShadersAndMetadata(Object event, Method setEntryMethod) {
        try {
            Method getEntriesMethod = event.getClass().getMethod("getEntries");
            Method removeEntryMethod = event.getClass().getMethod("removeEntry", String.class);
            Map<String, ?> iaEntries = (Map<String, ?>) getEntriesMethod.invoke(event);
            if (iaEntries == null) return;

            List<String> toRemove = new ArrayList<>();
            for (String path : iaEntries.keySet()) {
                if (isRenderpearlIncompatibleShader(path)) {
                    toRemove.add(path);
                }
            }

            for (String path : toRemove) {
                removeEntryMethod.invoke(event, path);
            }

            if (!toRemove.isEmpty()) {
                plugin.getLogger().info("[PackHost-ItemsAdder] Sanitized " + toRemove.size()
                        + " incompatible legacy core shaders for Minecraft 26.3+ Renderpearl compatibility.");
            }

            // Update pack.mcmeta supported_formats in the event entries
            sanitizePackMcmeta(event, iaEntries, setEntryMethod);
        } catch (Throwable t) {
            plugin.getLogger().warning("[PackHost-ItemsAdder] Note: Could not sanitize ItemsAdder shaders/metadata: " + t.getMessage());
        }
    }

    private boolean isRenderpearlIncompatibleShader(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        // Match core shaders in root and in overlays (e.g. ia_overlay_26_2_plus/...)
        if (lower.contains("shaders/core/")) {
            return true;
        }
        if (lower.contains("shaders/") && (lower.endsWith(".vsh") || lower.endsWith(".fsh") || lower.endsWith(".json"))) {
            return true;
        }
        return false;
    }

    private void sanitizePackMcmeta(Object event, Map<String, ?> iaEntries, Method setEntryMethod) {
        try {
            Object entryObj = iaEntries.get("pack.mcmeta");
            if (entryObj == null) return;

            byte[] bytes = null;
            if (entryObj instanceof byte[]) {
                bytes = (byte[]) entryObj;
            } else {
                try {
                    Method getBytesMethod = entryObj.getClass().getMethod("getBytes");
                    bytes = (byte[]) getBytesMethod.invoke(entryObj);
                } catch (NoSuchMethodException ignored) {}
            }

            if (bytes != null && bytes.length > 0) {
                String jsonStr = new String(bytes, StandardCharsets.UTF_8);
                JsonObject root = JsonParser.parseString(jsonStr).getAsJsonObject();
                boolean modified = false;

                if (root.has("pack") && root.get("pack").isJsonObject()) {
                    JsonObject pack = root.getAsJsonObject("pack");
                    if (pack.has("supported_formats")) {
                        JsonElement sf = pack.get("supported_formats");
                        if (sf.isJsonArray()) {
                            JsonArray arr = sf.getAsJsonArray();
                            if (arr.size() == 2 && arr.get(1).getAsInt() < 120) {
                                arr.set(1, new JsonPrimitive(120));
                                modified = true;
                            }
                        } else if (sf.isJsonObject()) {
                            JsonObject obj = sf.getAsJsonObject();
                            if (obj.has("max_inclusive") && obj.get("max_inclusive").getAsInt() < 120) {
                                obj.addProperty("max_inclusive", 120);
                                modified = true;
                            }
                        }
                    }
                    if (pack.has("pack_format") && pack.get("pack_format").getAsInt() < 75) {
                        pack.addProperty("pack_format", 75);
                        modified = true;
                    }
                }

                if (modified) {
                    byte[] updatedBytes = gson.toJson(root).getBytes(StandardCharsets.UTF_8);
                    setEntryMethod.invoke(event, "pack.mcmeta", updatedBytes);
                    plugin.getLogger().info("[PackHost-ItemsAdder] Expanded pack.mcmeta supported_formats for Minecraft 26.3+ compatibility.");
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[PackHost-ItemsAdder] Could not update pack.mcmeta: " + t.getMessage());
        }
    }

    @Override
    public boolean triggerHostRedistribution(CommandSender sender, String targetPlayerOrAll) {
        plugin.getLogger().info("[PackHost-ItemsAdder] Triggering ItemsAdder pack compilation (iazip)...");
        plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), "iazip");
        return true;
    }
}
