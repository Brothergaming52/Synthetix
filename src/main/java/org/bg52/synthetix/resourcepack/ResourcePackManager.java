package org.bg52.synthetix.resourcepack;

import com.google.gson.*;
import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.block.BlockModelPart;
import org.bg52.synthetix.block.CustomBlockDefinition;
import org.bg52.synthetix.item.ItemDefinition;
import org.bg52.synthetix.resourcepack.host.PackFileUtils;
import org.bg52.synthetix.resourcepack.host.PackHostManager;
import org.bg52.synthetix.resourcepack.host.PackHostPlugin;
import org.bg52.synthetix.scheduler.PlatformScheduler;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Universal, high-performance resource pack orchestrator.
 * Handles embedded asset extraction, multi-pack merging, dynamic JSON model merging,
 * SHA-1 digest calculation, HTTP self-hosting with IP auto-detection, and external host delegation.
 */
public class ResourcePackManager {

    public enum HostingMode {
        SELF,
        LINK,
        PLUGIN,
        AUTO,
        NONE
    }

    public static class SourceEntry {
        public final Plugin plugin;
        public final File folder;

        public SourceEntry(Plugin plugin, File folder) {
            this.plugin = plugin;
            this.folder = folder;
        }
    }

    private final SynthetixPlugin plugin;
    private final File resourcePackDir;
    private final File packFile;
    private final File externalPacksDir;
    private final File tempDir;
    private final PackHostManager packHostManager;
    private final IpDetector ipDetector;

    private final List<SourceEntry> registeredSources = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Map<Integer, String>> customModelOverrides = new ConcurrentHashMap<>();
    private final AtomicBoolean isBuilding = new AtomicBoolean(false);
    private volatile boolean dirty = false;

    private ResourcePackServer server;
    private volatile String packHash = null;
    private volatile byte[] packHashBytes = null;

    private final Set<String> reservedNamespaces = new HashSet<>(Arrays.asList("synthetix"));
    private final Map<String, Plugin> namespaceOwners = new ConcurrentHashMap<>();
    private final List<String> conflictLog = Collections.synchronizedList(new ArrayList<>());

    private boolean allowMinecraftNamespace = true;
    private boolean allowNamespaceConflicts = true;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public ResourcePackManager(SynthetixPlugin plugin) {
        this.plugin = plugin;
        this.resourcePackDir = new File(plugin.getDataFolder(), "resource-pack-build");
        this.packFile = new File(plugin.getDataFolder(), "resource-pack.zip");
        this.externalPacksDir = new File(plugin.getDataFolder(), "external-resource-packs");
        this.tempDir = new File(this.externalPacksDir, "temp");
        this.packHostManager = new PackHostManager(plugin);
        this.ipDetector = new IpDetector(plugin);
    }

    public void initialize() {
        this.allowMinecraftNamespace = plugin.getConfig().getBoolean("resource-pack.allow-minecraft-namespace", true);
        this.allowNamespaceConflicts = plugin.getConfig().getBoolean("resource-pack.allow-namespace-conflicts", true);

        File ownResources = new File(plugin.getDataFolder(), "resources");
        if (!ownResources.exists()) {
            ownResources.mkdirs();
        }

        // 1. Extract embedded default assets from plugin JAR
        try {
            extractEmbeddedResources("resources/", ownResources);
        } catch (Exception e) {
            plugin.getLogger().warning("[PackManager] Failed to extract embedded resources: " + e.getMessage());
        }

        // 2. Ensure default pack.mcmeta exists
        File mcmeta = new File(ownResources, "pack.mcmeta");
        if (!mcmeta.exists()) {
            createDefaultMcmeta(ownResources);
        }

        // 2b. Ensure default survival mining crack animation textures & models exist
        ensureDefaultMiningCrackAssets(ownResources);

        // 3. Register Synthetix core assets only if an /assets/ folder exists
        File ownAssets = new File(ownResources, "assets");
        if (!ownAssets.exists()) {
            ownAssets.mkdirs();
        }
        registerResource(plugin, ownResources);

        File legacyRp = new File(plugin.getDataFolder(), "resourcepack");
        if (legacyRp.exists() && legacyRp.isDirectory()) {
            registerResource(plugin, legacyRp);
        }

        // 4. Process any external zip packs in external-resource-packs/
        processExternalPacks();

        // 5. Initialize external pack host manager
        packHostManager.initialize();

        // 6. Start HTTP server if self-hosting is active and has content
        if (getEffectiveHostingMode() == HostingMode.SELF && !packHostManager.isActive() && hasContent()) {
            startHttpServer();
        }

        // 7. Schedule async pack compilation shortly after startup (2 seconds)
        PlatformScheduler.get().runAsyncLater(() -> {
            if (hasContent() && (dirty || !packFile.exists())) {
                generatePackAsync();
            }
        }, 40L);
    }

    public void shutdown() {
        if (server != null) {
            server.stop();
            server = null;
        }
        if (packHostManager != null) {
            packHostManager.shutdown();
        }
    }

    public void reload() {
        shutdown();
        this.allowMinecraftNamespace = plugin.getConfig().getBoolean("resource-pack.allow-minecraft-namespace", true);
        this.allowNamespaceConflicts = plugin.getConfig().getBoolean("resource-pack.allow-namespace-conflicts", true);
        if (packHostManager != null) {
            packHostManager.reload();
        }
        if (getEffectiveHostingMode() == HostingMode.SELF && !packHostManager.isActive() && hasContent()) {
            startHttpServer();
        }
        if (hasContent()) {
            generatePackAsync();
        }
    }

    private synchronized void startHttpServer() {
        if (this.server != null) return;
        int port = plugin.getConfig().getInt("resource-pack.server.port", 8085);
        String bind = plugin.getConfig().getString("resource-pack.server.bind-address", "0.0.0.0");
        this.server = new ResourcePackServer(plugin, bind, port, packFile);
        this.server.start();
    }

    // ==========================================
    // Public API & Source Registration
    // ==========================================

    /**
     * Checks if there are any custom resources, models, or packs to compile/host.
     */
    public boolean hasContent() {
        synchronized (registeredSources) {
            if (!registeredSources.isEmpty()) return true;
        }
        if (!customModelOverrides.isEmpty()) return true;
        if (hasAnyItemModels()) return true;
        if (externalPacksDir.exists()) {
            File[] zips = externalPacksDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".zip"));
            if (zips != null && zips.length > 0) return true;
        }
        File ownResources = new File(plugin.getDataFolder(), "resources");
        File ownAssets = new File(ownResources, "assets");
        if (ownAssets.exists() && ownAssets.isDirectory()) {
            File[] subs = ownAssets.listFiles(File::isDirectory);
            if (subs != null && subs.length > 0) return true;
        }
        return false;
    }

    private boolean hasAnyItemModels() {
        if (plugin.getItemRegistry() == null) return false;
        for (ItemDefinition def : plugin.getItemRegistry().getAll()) {
            if (def.getCustomModelData() != null || (def.getItemModel() != null && !def.getItemModel().trim().isEmpty())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Registers a resource pack folder from another plugin.
     */
    public void registerResource(Plugin sourcePlugin, File sourceFolder) {
        if (sourceFolder == null || !sourceFolder.exists() || !sourceFolder.isDirectory()) {
            return;
        }

        File assets = new File(sourceFolder, "assets");
        if (!assets.exists() || !assets.isDirectory()) {
            if (sourcePlugin != null && !sourcePlugin.getName().equalsIgnoreCase(plugin.getName())) {
                plugin.getLogger().warning("[PackManager] Plugin " + sourcePlugin.getName()
                        + " registered a resource pack without an /assets/ folder. Ignored.");
            }
            return;
        }

        File[] namespaces = assets.listFiles(File::isDirectory);
        if (namespaces != null) {
            for (File nsFolder : namespaces) {
                String namespace = nsFolder.getName().toLowerCase(Locale.ROOT);

                if (reservedNamespaces.contains(namespace) && sourcePlugin != this.plugin) {
                    plugin.getLogger().severe("[PackManager] Plugin " + sourcePlugin.getName()
                            + " attempted to use reserved namespace '" + namespace + "'. Registration skipped.");
                    conflictLog.add("RESERVED NAMESPACE: " + namespace + " by " + sourcePlugin.getName());
                    return;
                }

                if (namespace.equals("minecraft")) {
                    if (sourcePlugin != this.plugin && !allowMinecraftNamespace) {
                        plugin.getLogger().warning("[PackManager] Skipping 'minecraft' namespace from "
                                + sourcePlugin.getName() + " (disallowed by config).");
                        continue;
                    }
                }

                if (namespaceOwners.containsKey(namespace)) {
                    Plugin owner = namespaceOwners.get(namespace);
                    if (owner != sourcePlugin && !allowNamespaceConflicts && !namespace.equals("minecraft")) {
                        plugin.getLogger().severe("[PackManager] Namespace conflict on '" + namespace
                                + "' between " + owner.getName() + " and " + sourcePlugin.getName() + ". Skipped.");
                        conflictLog.add("NAMESPACE CONFLICT: " + namespace + " (" + owner.getName() + " vs " + sourcePlugin.getName() + ")");
                        return;
                    }
                } else {
                    namespaceOwners.put(namespace, sourcePlugin);
                }
            }
        }

        registeredSources.add(new SourceEntry(sourcePlugin, sourceFolder));
        this.dirty = true;
    }

    /**
     * Registers an external zip pack by extracting it to temp storage.
     */
    public void registerResourceZip(Plugin sourcePlugin, File zipFile) {
        if (zipFile == null || !zipFile.exists() || !zipFile.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            return;
        }
        try {
            String folderName = zipFile.getName().substring(0, zipFile.getName().length() - 4);
            File extractDir = new File(tempDir, sourcePlugin.getName() + "_" + folderName);
            PackFileUtils.unzip(zipFile, extractDir);
            registerResource(sourcePlugin, extractDir);
        } catch (IOException e) {
            plugin.getLogger().warning("[PackManager] Failed to extract zip pack from " + sourcePlugin.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Registers a custom model data override into minecraft/models/item/<material>.json.
     */
    public void registerItemModelOverride(String material, int customModelData, String modelPath) {
        if (material == null || modelPath == null) return;
        String matKey = material.toUpperCase(Locale.ROOT).trim();
        Map<Integer, String> overrides = customModelOverrides.computeIfAbsent(matKey, k -> new ConcurrentHashMap<>());
        overrides.put(customModelData, modelPath);
        this.dirty = true;
    }

    // ==========================================
    // External Pack Combiner
    // ==========================================

    private void processExternalPacks() {
        if (!plugin.getConfig().getBoolean("resource-pack.combine-external-rp", true)) {
            return;
        }

        if (!externalPacksDir.exists()) {
            externalPacksDir.mkdirs();
            return;
        }

        File[] zipFiles = externalPacksDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".zip"));
        if (zipFiles == null || zipFiles.length == 0) return;

        plugin.getLogger().info("[PackManager] Found " + zipFiles.length + " external resource pack(s). Processing...");

        if (tempDir.exists()) {
            PackFileUtils.deleteDirectory(tempDir);
        }
        tempDir.mkdirs();

        for (File zipFile : zipFiles) {
            try {
                String folderName = zipFile.getName().substring(0, zipFile.getName().length() - 4);
                File extractDir = new File(tempDir, folderName);
                PackFileUtils.unzip(zipFile, extractDir);
                registerResource(plugin, extractDir);
                plugin.getLogger().info("[PackManager] Successfully extracted and merged external pack: " + zipFile.getName());
            } catch (Exception e) {
                plugin.getLogger().warning("[PackManager] Failed to process external pack " + zipFile.getName() + ": " + e.getMessage());
            }
        }
    }

    // ==========================================
    // Async Pack Building Pipeline
    // ==========================================

    public CompletableFuture<Void> generatePackAsync() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (!hasContent()) {
            future.complete(null);
            return future;
        }

        if (isBuilding.get()) {
            future.complete(null);
            return future;
        }

        isBuilding.set(true);
        PlatformScheduler.get().runAsync(() -> {
            try {
                plugin.getLogger().info("[PackManager] Starting resource pack compilation...");
                long start = System.currentTimeMillis();

                // 1. Prepare clean build directory
                if (resourcePackDir.exists()) {
                    PackFileUtils.deleteDirectory(resourcePackDir);
                }
                resourcePackDir.mkdirs();

                // 2. Merge all registered sources into build directory
                synchronized (registeredSources) {
                    for (SourceEntry source : registeredSources) {
                        mergeSourceFolder(source.folder);
                    }
                }

                // 3. Scan ItemRegistry & CustomBlockRegistry, mirror assets, and generate modern 1.21.4 item models
                syncItemDefinitionsToModels();

                // 4. Apply custom model data overrides into minecraft/models/item/*.json
                applyCustomModelOverrides();

                // 4b. Sanitize and prune orphaned model overrides to protect clients and host plugins
                sanitizeModelOverrides();

                // 5. Ensure valid pack.mcmeta in build root
                File buildMcmeta = new File(resourcePackDir, "pack.mcmeta");
                if (!buildMcmeta.exists()) {
                    createDefaultMcmeta(resourcePackDir);
                } else {
                    ensureMcmetaModernFormat(buildMcmeta);
                }

                // 6. Zip compilation
                if (packFile.exists()) {
                    packFile.delete();
                }
                zipDirectory(resourcePackDir, packFile);

                // 7. Calculate SHA-1 digest
                this.packHashBytes = calculateSha1Bytes(packFile);
                this.packHash = bytesToHex(packHashBytes);

                long elapsed = System.currentTimeMillis() - start;
                plugin.getLogger().info("[PackManager] Resource pack built successfully in " + elapsed + "ms. SHA-1: " + packHash);

                // 8. Trigger Host Injection if active, or start self HTTP server if needed
                if (packHostManager != null && packHostManager.isActive()) {
                    packHostManager.injectFinalPack(packFile, resourcePackDir);
                } else if (getEffectiveHostingMode() == HostingMode.SELF) {
                    startHttpServer();
                }

                this.dirty = false;
                future.complete(null);
            } catch (Exception e) {
                plugin.getLogger().severe("[PackManager] Failed to build resource pack: " + e.getMessage());
                e.printStackTrace();
                future.completeExceptionally(e);
            } finally {
                isBuilding.set(false);
            }
        });

        return future;
    }

    private void syncItemDefinitionsToModels() {
        // 1. Custom Items from ItemRegistry
        for (ItemDefinition def : plugin.getItemRegistry().getAll()) {
            if (def.getCustomModelData() != null && def.getCustomModelData() > 0) {
                Material mat = def.getMaterial() != null ? def.getMaterial() : Material.PAPER;
                String modelPath = def.getItemModel() != null && !def.getItemModel().isEmpty()
                        ? def.getItemModel() : "synthetix:" + def.getId();
                String internalModelPath = toInternalModelPath(modelPath, def.getId());
                registerItemModelOverride(mat.name(), def.getCustomModelData(), internalModelPath);
            }

            if (def.getItemModel() != null && !def.getItemModel().isEmpty()) {
                generateModernItemDefinition(def.getItemModel(), def.getId());
            }
        }

        // 2. Custom Blocks and Composite Parts from CustomBlockRegistry
        for (CustomBlockDefinition def : plugin.getCustomBlockRegistry().getAll()) {
            if (def.getCustomModelData() > 0) {
                Material mat = def.getItemMaterial() != null ? def.getItemMaterial() : Material.PAPER;
                String modelPath = def.getItemModel() != null && !def.getItemModel().isEmpty()
                        ? def.getItemModel() : "synthetix:" + def.getId();
                String internalModelPath = toInternalModelPath(modelPath, def.getId());
                registerItemModelOverride(mat.name(), def.getCustomModelData(), internalModelPath);
            }

            if (def.getItemModel() != null && !def.getItemModel().isEmpty()) {
                generateModernItemDefinition(def.getItemModel(), def.getId());
            }

            if (def.getModelParts() != null) {
                for (BlockModelPart part : def.getModelParts()) {
                    if (part.getCustomModelData() > 0) {
                        Material mat = part.getMaterial() != null ? part.getMaterial() : Material.PAPER;
                        String modelPath = part.getItemModel() != null && !part.getItemModel().isEmpty()
                                ? part.getItemModel() : "synthetix:" + def.getId() + "_" + part.getId();
                        String internalModelPath = toInternalModelPath(modelPath, def.getId() + "_" + part.getId());
                        registerItemModelOverride(mat.name(), part.getCustomModelData(), internalModelPath);
                    }
                    if (part.getItemModel() != null && !part.getItemModel().isEmpty()) {
                        generateModernItemDefinition(part.getItemModel(), def.getId() + "_" + part.getId());
                    }
                }
            }
        }

        // 3. Mirror textures/models across block & item and ensure all existing models have item definitions
        mirrorAndNormalizeAssets();
    }

    /**
     * Resolves an item model identifier to its internal geometry file location under models/item/.
     * E.g. 'synthetix:gearbox' -> 'synthetix:item/gearbox'.
     */
    public String toInternalModelPath(String raw, String fallbackId) {
        String normalized = org.bg52.synthetix.block.BlockModelPart.normalizeModelKey(raw);
        if (normalized == null) {
            String cleanFallback = org.bg52.synthetix.block.BlockModelPart.normalizeModelKey(fallbackId);
            if (cleanFallback == null) cleanFallback = "synthetix:unknown";
            normalized = cleanFallback;
        }
        String[] parts = normalized.split(":", 2);
        return parts[0] + ":item/" + parts[1];
    }

    public void generateModernItemDefinition(String itemModelKey, String fallbackId) {
        if (itemModelKey == null || itemModelKey.trim().isEmpty()) return;

        String normalized = org.bg52.synthetix.block.BlockModelPart.normalizeModelKey(itemModelKey);
        if (normalized == null) return;

        String[] parts = normalized.split(":", 2);
        String namespace = parts[0];
        String cleanId = parts[1];
        String targetModel = toInternalModelPath(itemModelKey, fallbackId);

        writeItemModelJson(namespace, cleanId, targetModel);

        if (fallbackId != null && !fallbackId.isEmpty()) {
            String normFallback = org.bg52.synthetix.block.BlockModelPart.normalizeModelKey(fallbackId);
            if (normFallback != null) {
                String[] fbParts = normFallback.split(":", 2);
                if (!fbParts[1].equalsIgnoreCase(cleanId)) {
                    writeItemModelJson(fbParts[0], fbParts[1], targetModel);
                }
            }
        }
    }

    private void writeItemModelJson(String namespace, String path, String targetModel) {
        try {
            File itemsDir = new File(resourcePackDir, "assets/" + namespace + "/items");
            File itemJson = new File(itemsDir, path + ".json");
            // Preserve rich pre-existing item definitions (such as 3D umbrella, multi-state models, etc.)
            if (itemJson.exists()) {
                return;
            }
            File parent = itemJson.getParentFile();
            if (!parent.exists()) parent.mkdirs();

            JsonObject obj = new JsonObject();
            JsonObject modelObj = new JsonObject();
            modelObj.addProperty("type", "minecraft:model");
            modelObj.addProperty("model", targetModel);
            obj.add("model", modelObj);

            try (Writer writer = new FileWriter(itemJson, StandardCharsets.UTF_8)) {
                gson.toJson(obj, writer);
            }
        } catch (Throwable ignored) {}
    }

    private void mirrorAndNormalizeAssets() {
        File assetsDir = new File(resourcePackDir, "assets");
        if (!assetsDir.exists()) return;

        File[] namespaces = assetsDir.listFiles(File::isDirectory);
        if (namespaces == null) return;

        for (File nsDir : namespaces) {
            String ns = nsDir.getName().toLowerCase(Locale.ROOT);
            if (ns.equals("minecraft")) continue;

            // Migrate any legacy block/ directories to item/ and clean up
            File texBlock = new File(nsDir, "textures/block");
            File texItem = new File(nsDir, "textures/item");
            if (texBlock.exists() && texBlock.isDirectory()) {
                if (!texItem.exists()) texItem.mkdirs();
                File[] blockPngs = texBlock.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".png"));
                if (blockPngs != null) {
                    for (File png : blockPngs) {
                        File dest = new File(texItem, png.getName());
                        if (!dest.exists()) {
                            try {
                                Files.copy(png.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            } catch (Exception ignored) {}
                        }
                        png.delete();
                    }
                }
                texBlock.delete();
            }

            File modBlock = new File(nsDir, "models/block");
            File modItem = new File(nsDir, "models/item");
            if (modBlock.exists() && modBlock.isDirectory()) {
                if (!modItem.exists()) modItem.mkdirs();
                File[] blockJsons = modBlock.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".json"));
                if (blockJsons != null) {
                    for (File json : blockJsons) {
                        File dest = new File(modItem, json.getName());
                        if (!dest.exists()) {
                            try {
                                Files.copy(json.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            } catch (Exception ignored) {}
                        }
                        json.delete();
                    }
                }
                modBlock.delete();
            }

            // Ensure all models in models/item/*.json have an items/<name>.json definition
            if (modItem.exists() && modItem.isDirectory()) {
                File[] itemJsons = modItem.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".json"));
                if (itemJsons != null) {
                    for (File json : itemJsons) {
                        String modelName = json.getName().substring(0, json.getName().lastIndexOf('.'));
                        writeItemModelJson(ns, modelName, ns + ":item/" + modelName);
                    }
                }
            }
        }
    }

    private void mergeSourceFolder(File source) throws IOException {
        if (source == null || !source.exists()) return;

        File[] files = source.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.getName().equalsIgnoreCase("assets")) {
                mergeAssetsDirectory(file, new File(resourcePackDir, "assets"));
            } else if (file.getName().equalsIgnoreCase("pack.mcmeta")) {
                File targetMcmeta = new File(resourcePackDir, "pack.mcmeta");
                if (!targetMcmeta.exists()) {
                    Files.copy(file.toPath(), targetMcmeta.toPath());
                }
            } else if (file.getName().equalsIgnoreCase("pack.png")) {
                File targetPng = new File(resourcePackDir, "pack.png");
                if (!targetPng.exists()) {
                    Files.copy(file.toPath(), targetPng.toPath());
                }
            }
        }
    }

    private void mergeAssetsDirectory(File sourceAssets, File targetAssets) throws IOException {
        if (!sourceAssets.exists()) return;
        if (!targetAssets.exists()) targetAssets.mkdirs();

        File[] namespaces = sourceAssets.listFiles(File::isDirectory);
        if (namespaces == null) return;

        for (File nsDir : namespaces) {
            String ns = nsDir.getName().toLowerCase(Locale.ROOT);
            File targetNsDir = new File(targetAssets, ns);

            if (ns.equals("minecraft")) {
                mergeMinecraftNamespace(nsDir, targetNsDir);
            } else {
                PackFileUtils.copyRecursively(nsDir, targetNsDir);
            }
        }
    }

    private void mergeMinecraftNamespace(File sourceNs, File targetNs) throws IOException {
        File sourceModels = new File(sourceNs, "models/item");
        File targetModels = new File(targetNs, "models/item");

        if (sourceModels.exists() && sourceModels.isDirectory()) {
            if (!targetModels.exists()) targetModels.mkdirs();
            File[] jsonFiles = sourceModels.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".json"));
            if (jsonFiles != null) {
                for (File sourceJson : jsonFiles) {
                    File targetJson = new File(targetModels, sourceJson.getName());
                    if (targetJson.exists()) {
                        mergeItemModelJson(sourceJson, targetJson);
                    } else {
                        Files.copy(sourceJson.toPath(), targetJson.toPath());
                    }
                }
            }
        }

        // Copy all other subdirectories under minecraft/
        File[] otherDirs = sourceNs.listFiles();
        if (otherDirs != null) {
            for (File sub : otherDirs) {
                if (sub.getName().equalsIgnoreCase("models")) continue;
                if (sub.getName().equalsIgnoreCase("shaders")) {
                    // Filter out legacy core shaders that crash 1.21.4+ / 26.3 clients with GLSL compilation errors
                    continue;
                }
                File targetSub = new File(targetNs, sub.getName());
                PackFileUtils.copyRecursively(sub, targetSub);
            }
        }
    }

    private void mergeItemModelJson(File sourceJson, File targetJson) {
        try {
            JsonObject sourceObj = JsonParser.parseReader(new FileReader(sourceJson, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject targetObj = JsonParser.parseReader(new FileReader(targetJson, StandardCharsets.UTF_8)).getAsJsonObject();

            if (sourceObj.has("overrides") && sourceObj.get("overrides").isJsonArray()) {
                JsonArray sourceOverrides = sourceObj.getAsJsonArray("overrides");
                JsonArray targetOverrides = targetObj.has("overrides") && targetObj.get("overrides").isJsonArray()
                        ? targetObj.getAsJsonArray("overrides") : new JsonArray();

                Map<String, JsonObject> mergedMap = new LinkedHashMap<>();
                for (JsonElement el : targetOverrides) {
                    if (el.isJsonObject()) {
                        JsonObject ov = el.getAsJsonObject();
                        String key = ov.has("predicate") ? ov.get("predicate").toString() : ov.toString();
                        mergedMap.put(key, ov);
                    }
                }
                for (JsonElement el : sourceOverrides) {
                    if (el.isJsonObject()) {
                        JsonObject ov = el.getAsJsonObject();
                        String key = ov.has("predicate") ? ov.get("predicate").toString() : ov.toString();
                        mergedMap.put(key, ov);
                    }
                }

                List<JsonObject> overrideList = new ArrayList<>(mergedMap.values());
                overrideList.sort(this::compareOverrides);

                JsonArray dedupedOverrides = new JsonArray();
                for (JsonObject ov : overrideList) {
                    dedupedOverrides.add(ov);
                }
                targetObj.add("overrides", dedupedOverrides);

                try (Writer writer = new FileWriter(targetJson, StandardCharsets.UTF_8)) {
                    gson.toJson(targetObj, writer);
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[PackManager] Failed to merge model JSON: " + sourceJson.getName());
        }
    }

    private int compareOverrides(JsonObject a, JsonObject b) {
        JsonObject predA = a.has("predicate") && a.get("predicate").isJsonObject() ? a.getAsJsonObject("predicate") : null;
        JsonObject predB = b.has("predicate") && b.get("predicate").isJsonObject() ? b.getAsJsonObject("predicate") : null;

        int cmdA = 0;
        int cmdB = 0;
        if (predA != null && predA.has("custom_model_data")) {
            try { cmdA = predA.get("custom_model_data").getAsInt(); } catch (Exception ignored) {}
        }
        if (predB != null && predB.has("custom_model_data")) {
            try { cmdB = predB.get("custom_model_data").getAsInt(); } catch (Exception ignored) {}
        }

        if (cmdA != cmdB) {
            return Integer.compare(cmdA, cmdB);
        }

        int sizeA = predA != null ? predA.size() : 0;
        int sizeB = predB != null ? predB.size() : 0;
        return Integer.compare(sizeA, sizeB);
    }

    private void applyCustomModelOverrides() {
        if (customModelOverrides.isEmpty()) return;

        File modelsItemDir = new File(resourcePackDir, "assets/minecraft/models/item");
        if (!modelsItemDir.exists()) modelsItemDir.mkdirs();

        for (Map.Entry<String, Map<Integer, String>> entry : customModelOverrides.entrySet()) {
            String materialName = entry.getKey().toLowerCase(Locale.ROOT);
            File modelFile = new File(modelsItemDir, materialName + ".json");
            JsonObject modelObj;

            if (modelFile.exists()) {
                try {
                    modelObj = JsonParser.parseReader(new FileReader(modelFile, StandardCharsets.UTF_8)).getAsJsonObject();
                } catch (Exception e) {
                    modelObj = createDefaultItemModelJson(materialName);
                }
            } else {
                modelObj = createDefaultItemModelJson(materialName);
            }

            JsonArray overrides = modelObj.has("overrides") && modelObj.get("overrides").isJsonArray()
                    ? modelObj.getAsJsonArray("overrides") : new JsonArray();

            Map<String, JsonObject> mergedMap = new LinkedHashMap<>();
            for (JsonElement el : overrides) {
                if (el.isJsonObject()) {
                    JsonObject ov = el.getAsJsonObject();
                    String key = ov.has("predicate") ? ov.get("predicate").toString() : ov.toString();
                    mergedMap.put(key, ov);
                }
            }

            for (Map.Entry<Integer, String> overrideEntry : entry.getValue().entrySet()) {
                int cmd = overrideEntry.getKey();
                String path = overrideEntry.getValue();

                JsonObject predicate = new JsonObject();
                predicate.addProperty("custom_model_data", cmd);
                String predKey = predicate.toString();

                if (!mergedMap.containsKey(predKey)) {
                    JsonObject ov = new JsonObject();
                    ov.add("predicate", predicate);
                    ov.addProperty("model", path);
                    mergedMap.put(predKey, ov);
                }
            }

            List<JsonObject> overrideList = new ArrayList<>(mergedMap.values());
            overrideList.sort(this::compareOverrides);

            JsonArray dedupedOverrides = new JsonArray();
            for (JsonObject ov : overrideList) {
                dedupedOverrides.add(ov);
            }

            modelObj.add("overrides", dedupedOverrides);

            try (Writer writer = new FileWriter(modelFile, StandardCharsets.UTF_8)) {
                gson.toJson(modelObj, writer);
            } catch (IOException e) {
                plugin.getLogger().warning("[PackManager] Could not write override model for " + materialName + ": " + e.getMessage());
            }
        }
    }

    private JsonObject createDefaultItemModelJson(String materialName) {
        JsonObject obj = new JsonObject();
        obj.addProperty("parent", "minecraft:item/generated");
        JsonObject textures = new JsonObject();
        textures.addProperty("layer0", "minecraft:item/" + materialName);
        obj.add("textures", textures);
        obj.add("overrides", new JsonArray());
        return obj;
    }

    private void zipDirectory(File sourceDir, File zipFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile), 65536))) {
            zos.setLevel(java.util.zip.Deflater.BEST_SPEED);
            byte[] buffer = new byte[65536];
            Path sourcePath = sourceDir.toPath();

            Files.walk(sourcePath)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        String zipEntryName = sourcePath.relativize(path).toString().replace('\\', '/');
                        ZipEntry zipEntry = new ZipEntry(zipEntryName);
                        try {
                            zos.putNextEntry(zipEntry);
                            try (InputStream is = new BufferedInputStream(Files.newInputStream(path), 65536)) {
                                int len;
                                while ((len = is.read(buffer)) > 0) {
                                    zos.write(buffer, 0, len);
                                }
                            }
                            zos.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    private byte[] calculateSha1Bytes(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (InputStream fis = new BufferedInputStream(new FileInputStream(file), 65536)) {
            byte[] buffer = new byte[65536];
            int n;
            while ((n = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, n);
            }
        }
        return digest.digest();
    }

    private static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // ==========================================
    // Client Delivery & Prompting
    // ==========================================

    public void sendPackToPlayer(Player player) {
        if (player == null || !player.isOnline()) return;
        if (!shouldSendPackToPlayer()) return;

        String url = getPackUrl();
        if (url == null || url.trim().isEmpty() || url.startsWith("(Delegated")) {
            return;
        }

        String rawPrompt = plugin.getConfig().getString("resource-pack.prompt-message",
                "<gradient:#FF5555:#FFAA00>Please accept the server resource pack for the full visual experience!</gradient>");
        Component promptComp = TextUtil.parse(rawPrompt);
        boolean force = plugin.getConfig().getBoolean("resource-pack.force-pack", false);

        try {
            UUID packId = UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8));
            player.setResourcePack(packId, url, packHashBytes, promptComp, force);
        } catch (Throwable t) {
            plugin.getLogger().warning("[PackManager] Failed to prompt player " + player.getName() + " with resource pack: " + t.getMessage());
        }
    }

    public void forceSendPackToPlayer(Player player) {
        if (player == null || !player.isOnline()) return;

        String url = getDirectPackUrl();
        if (url == null || url.trim().isEmpty()) {
            return;
        }

        String rawPrompt = plugin.getConfig().getString("resource-pack.prompt-message",
                "<gradient:#FF5555:#FFAA00>Please accept the server resource pack for the full visual experience!</gradient>");
        Component promptComp = TextUtil.parse(rawPrompt);
        boolean force = plugin.getConfig().getBoolean("resource-pack.force-pack", false);

        try {
            UUID packId = UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8));
            byte[] hash = packHashBytes != null ? packHashBytes : (packFile.exists() ? calculateSha1Bytes(packFile) : null);
            player.setResourcePack(packId, url, hash, promptComp, force);
        } catch (Throwable t) {
            plugin.getLogger().warning("[PackManager] Failed to prompt player " + player.getName() + " with resource pack: " + t.getMessage());
        }
    }

    /**
     * Rebuilds and redistributes the resource pack across online players.
     * Works seamlessly across all hosting modes (Self, Link, External Host Plugins).
     */
    public CompletableFuture<Boolean> redistributePack(CommandSender sender, String target) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        plugin.getLogger().info("[PackManager] Resource pack redistribution requested by " + (sender != null ? sender.getName() : "CONSOLE"));

        generatePackAsync().thenRun(() -> {
            PlatformScheduler.get().runGlobal(() -> {
                try {
                    if (packHostManager != null && packHostManager.isActive()) {
                        boolean hostOk = packHostManager.triggerActiveHostRedistribution(sender, target);
                        if (hostOk) {
                            future.complete(true);
                            return;
                        }
                    }

                    int count = 0;
                    if (target == null || target.equalsIgnoreCase("all") || target.equalsIgnoreCase("*")) {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            forceSendPackToPlayer(p);
                            count++;
                        }
                    } else {
                        Player targetPlayer = Bukkit.getPlayer(target);
                        if (targetPlayer != null && targetPlayer.isOnline()) {
                            forceSendPackToPlayer(targetPlayer);
                            count = 1;
                        } else {
                            if (sender != null) {
                                sender.sendMessage(TextUtil.parse("<red>Player '" + target + "' not found or offline.</red>"));
                            }
                            future.complete(false);
                            return;
                        }
                    }

                    if (sender != null) {
                        sender.sendMessage(TextUtil.parse("<green>Resource pack sent to <yellow>" + count + "</yellow> player(s)!</green>"));
                    }
                    future.complete(true);
                } catch (Exception ex) {
                    plugin.getLogger().severe("[PackManager] Error during pack redistribution: " + ex.getMessage());
                    future.complete(false);
                }
            });
        }).exceptionally(ex -> {
            if (sender != null) {
                sender.sendMessage(TextUtil.parse("<red>Failed to rebuild pack before redistribution: " + ex.getMessage() + "</red>"));
            }
            future.complete(false);
            return null;
        });

        return future;
    }

    public boolean shouldSendPackToPlayer() {
        if (!plugin.getConfig().getBoolean("resource-pack.enabled", true)) {
            return false;
        }
        if (packHostManager != null && packHostManager.isActive()) {
            return false;
        }
        HostingMode mode = getEffectiveHostingMode();
        if (mode == HostingMode.NONE) {
            return false;
        }
        if (mode == HostingMode.SELF && !hasContent()) {
            return false;
        }
        if (mode == HostingMode.LINK) {
            String url = plugin.getConfig().getString("resource-pack.link.url", "");
            if (url == null || url.trim().isEmpty()) {
                return false;
            }
        }
        return mode == HostingMode.SELF || mode == HostingMode.LINK;
    }

    public String getPackUrl() {
        if (packHostManager != null && packHostManager.isActive()) {
            return "(Delegated to " + packHostManager.getActiveHost().getDisplayName() + ")";
        }

        return getDirectPackUrl();
    }

    public String getDirectPackUrl() {
        HostingMode mode = getEffectiveHostingMode();
        if (mode == HostingMode.LINK) {
            return plugin.getConfig().getString("resource-pack.link.url", "");
        }

        int port = plugin.getConfig().getInt("resource-pack.server.port", 8085);
        if (server != null && server.getPort() > 0) {
            port = server.getPort();
        }

        String ip = ipDetector.resolveExternalIp();
        return "http://" + ip + ":" + port + "/pack.zip";
    }

    public HostingMode getHostingMode() {
        String modeStr = plugin.getConfig().getString("resource-pack.hosting-mode", "auto");
        try {
            return HostingMode.valueOf(modeStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            PackHostPlugin php = PackHostPlugin.fromString(modeStr);
            if (php != PackHostPlugin.NONE && php != PackHostPlugin.AUTO) {
                return HostingMode.PLUGIN;
            }
        }
        boolean enabled = plugin.getConfig().getBoolean("resource-pack.enabled", true);
        return enabled ? HostingMode.AUTO : HostingMode.NONE;
    }

    public HostingMode getEffectiveHostingMode() {
        HostingMode mode = getHostingMode();
        if (mode == HostingMode.AUTO) {
            if (packHostManager != null && packHostManager.isActive()) {
                return HostingMode.PLUGIN;
            }
            return HostingMode.SELF;
        }
        return mode;
    }

    public boolean isBuilding() {
        return isBuilding.get();
    }

    public boolean isDirty() {
        return dirty;
    }

    public File getPackFile() {
        return packFile;
    }

    public File getResourcePackDir() {
        return resourcePackDir;
    }

    public String getPackHash() {
        return packHash;
    }

    public PackHostManager getPackHostManager() {
        return packHostManager;
    }

    public IpDetector getIpDetector() {
        return ipDetector;
    }

    public List<String> getConflictLog() {
        return Collections.unmodifiableList(conflictLog);
    }

    // ==========================================
    // Internal File Helpers
    // ==========================================

    private void createDefaultMcmeta(File targetDir) {
        File mcmeta = new File(targetDir, "pack.mcmeta");
        String content = "{\n" +
                "  \"pack\": {\n" +
                "    \"pack_format\": 75,\n" +
                "    \"supported_formats\": {\n" +
                "      \"min_inclusive\": 15,\n" +
                "      \"max_inclusive\": 120\n" +
                "    },\n" +
                "    \"description\": \"Synthetix Unified Resource Pack\"\n" +
                "  }\n" +
                "}";
        try (FileWriter writer = new FileWriter(mcmeta, StandardCharsets.UTF_8)) {
            writer.write(content);
        } catch (IOException e) {
            plugin.getLogger().warning("[PackManager] Could not create default pack.mcmeta: " + e.getMessage());
        }
    }

    private void sanitizeModelOverrides() {
        File modelsItemDir = new File(resourcePackDir, "assets/minecraft/models/item");
        if (!modelsItemDir.exists() || !modelsItemDir.isDirectory()) return;

        File[] jsonFiles = modelsItemDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".json"));
        if (jsonFiles == null) return;

        int totalPruned = 0;
        for (File jsonFile : jsonFiles) {
            try {
                JsonObject obj;
                try (Reader reader = new FileReader(jsonFile, StandardCharsets.UTF_8)) {
                    obj = JsonParser.parseReader(reader).getAsJsonObject();
                }

                if (!obj.has("overrides") || !obj.get("overrides").isJsonArray()) {
                    continue;
                }

                JsonArray originalOverrides = obj.getAsJsonArray("overrides");
                JsonArray validOverrides = new JsonArray();
                boolean pruned = false;

                for (JsonElement el : originalOverrides) {
                    if (!el.isJsonObject()) continue;
                    JsonObject ov = el.getAsJsonObject();
                    if (!ov.has("model") || !ov.get("model").isJsonPrimitive()) {
                        continue;
                    }
                    String modelPath = ov.get("model").getAsString();

                    // Vanilla models are always valid
                    if (modelPath.startsWith("minecraft:") || !modelPath.contains(":")) {
                        validOverrides.add(ov);
                        continue;
                    }

                    int colon = modelPath.indexOf(':');
                    String ns = modelPath.substring(0, colon);
                    String subPath = modelPath.substring(colon + 1);

                    File targetModelJson = new File(resourcePackDir, "assets/" + ns + "/models/" + subPath + ".json");
                    File targetItemModelJson = new File(resourcePackDir, "assets/" + ns + "/models/item/" + subPath + ".json");
                    File targetItemJson = new File(resourcePackDir, "assets/" + ns + "/items/" + subPath + ".json");

                    if (targetModelJson.exists() || targetItemModelJson.exists() || targetItemJson.exists()) {
                        validOverrides.add(ov);
                    } else {
                        pruned = true;
                        totalPruned++;
                        plugin.getLogger().warning("[PackManager] Pruned orphaned model override in "
                                + jsonFile.getName() + " -> " + modelPath + " (model file does not exist)");
                    }
                }

                if (pruned) {
                    obj.add("overrides", validOverrides);
                    try (Writer writer = new FileWriter(jsonFile, StandardCharsets.UTF_8)) {
                        gson.toJson(obj, writer);
                    }
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("[PackManager] Error validating overrides in " + jsonFile.getName() + ": " + t.getMessage());
            }
        }

        if (totalPruned > 0) {
            plugin.getLogger().info("[PackManager] Generalized model validation sanitized " + totalPruned + " orphaned model override(s).");
        }
    }

    private void ensureMcmetaModernFormat(File mcmetaFile) {
        try {
            JsonObject root;
            try (Reader reader = new FileReader(mcmetaFile, StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            }
            if (root.has("pack") && root.get("pack").isJsonObject()) {
                JsonObject pack = root.getAsJsonObject("pack");
                boolean modified = false;
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
                if (modified) {
                    try (Writer writer = new FileWriter(mcmetaFile, StandardCharsets.UTF_8)) {
                        gson.toJson(root, writer);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    public void ensureDefaultMiningCrackAssets(File ownResources) {
        File itemTexDir = new File(ownResources, "assets/synthetix/textures/item");
        File itemModelDir = new File(ownResources, "assets/synthetix/models/item");
        File itemDir = new File(ownResources, "assets/synthetix/items");

        itemTexDir.mkdirs();
        itemModelDir.mkdirs();
        itemDir.mkdirs();

        for (int i = 0; i <= 9; i++) {
            File itemTexFile = new File(itemTexDir, "destroy_stage_" + i + ".png");
            File itemModelFile = new File(itemModelDir, "destroy_stage_" + i + ".json");
            File itemJsonFile = new File(itemDir, "destroy_stage_" + i + ".json");

            // Extract from embedded JAR resources if not present on disk
            if (!itemTexFile.exists()) {
                try (InputStream in = plugin.getResource("resources/assets/synthetix/textures/item/destroy_stage_" + i + ".png")) {
                    if (in != null) Files.copy(in, itemTexFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {}
            }
            if (!itemModelFile.exists()) {
                try (InputStream in = plugin.getResource("resources/assets/synthetix/models/item/destroy_stage_" + i + ".json")) {
                    if (in != null) Files.copy(in, itemModelFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {}
            }
            if (!itemJsonFile.exists()) {
                try (InputStream in = plugin.getResource("resources/assets/synthetix/items/destroy_stage_" + i + ".json")) {
                    if (in != null) Files.copy(in, itemJsonFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {}
            }

            // Register CustomModelData fallback (19000-19009)
            registerItemModelOverride("paper", 19000 + i, "synthetix:item/destroy_stage_" + i);
            registerItemModelOverride("barrier", 19000 + i, "synthetix:item/destroy_stage_" + i);
        }
    }

    private void extractEmbeddedResources(String jarPrefix, File targetRoot) throws Exception {
        URL jarUrl = plugin.getClass().getProtectionDomain().getCodeSource().getLocation();
        if (jarUrl == null) return;

        File jarFile = new File(jarUrl.toURI());
        if (!jarFile.isFile()) return;

        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith(jarPrefix)) continue;

                String relativePath = name.substring(jarPrefix.length());
                if (relativePath.isEmpty()) continue;

                File outFile = new File(targetRoot, relativePath);
                if (entry.isDirectory()) {
                    if (!outFile.exists()) outFile.mkdirs();
                    continue;
                }

                if (outFile.exists()) continue;

                File parent = outFile.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();

                try (InputStream in = jar.getInputStream(entry);
                     OutputStream out = new FileOutputStream(outFile)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                }
            }
        }
    }
}

