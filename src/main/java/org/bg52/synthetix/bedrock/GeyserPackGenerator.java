package org.bg52.synthetix.bedrock;

import com.google.gson.*;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.block.CustomBlockDefinition;
import org.bg52.synthetix.entity.model.CustomEntityDefinition;
import org.bukkit.Material;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Universal compiler and deployer for Minecraft Bedrock Edition (Geyser/Floodgate).
 *
 * Automatically generates:
 * 1. Bedrock Resource Pack (Synthetix_Geyser.zip) containing Bedrock format manifest.json,
 *    geometry models, attachables, animations, and textures.
 * 2. Geyser custom mappings JSON (Synthetix_mappings.json) registering CustomModelData overrides.
 * 3. Auto-deploys compiled assets into plugins/Geyser-Spigot/ or plugins/Geyser/ packs and custom_mappings.
 */
public class GeyserPackGenerator {

    private final SynthetixPlugin plugin;
    private final File geyserWorkDir;
    private final File geyserPackZip;
    private final File geyserMappingsFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public GeyserPackGenerator(SynthetixPlugin plugin) {
        this.plugin = plugin;
        this.geyserWorkDir = new File(plugin.getDataFolder(), "geyser");
        this.geyserPackZip = new File(this.geyserWorkDir, "Synthetix_Geyser.zip");
        this.geyserMappingsFile = new File(this.geyserWorkDir, "Synthetix_mappings.json");
    }

    public File getGeyserWorkDir() {
        return geyserWorkDir;
    }

    public File getGeyserPackZip() {
        return geyserPackZip;
    }

    public File getGeyserMappingsFile() {
        return geyserMappingsFile;
    }

    /**
     * Executes resource extraction, dynamic mappings generation, zip compilation, and Geyser deployment asynchronously.
     */
    public CompletableFuture<File> generateAndDeployAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!geyserWorkDir.exists()) {
                    geyserWorkDir.mkdirs();
                }

                // 1. Extract embedded geyser resources from Synthetix JAR
                extractEmbeddedGeyserResources();

                // 2. Dynamically merge registered CustomBlockDefinition & CustomEntityDefinition into mappings
                updateMappingsWithRegisteredDefinitions();

                // 3. Compile Synthetix_Geyser.zip from geyser work directory
                compileBedrockResourcePack();

                // 4. Auto-detect and deploy to Geyser-Spigot / Geyser server directory
                deployToGeyserDirectory();

                return geyserPackZip;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to compile Bedrock/Geyser pack: " + e.getMessage(), e);
                throw new RuntimeException(e);
            }
        });
    }

    private void extractEmbeddedGeyserResources() {
        try {
            CodeSource src = plugin.getClass().getProtectionDomain().getCodeSource();
            if (src == null) return;
            URL jar = src.getLocation();
            try (JarFile jarFile = new JarFile(new File(jar.toURI()))) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (name.startsWith("geyser/") && !entry.isDirectory()) {
                        String relativePath = name.substring("geyser/".length());
                        File outFile = new File(geyserWorkDir, relativePath);
                        if (!outFile.exists() || !relativePath.contains("mappings")) {
                            outFile.getParentFile().mkdirs();
                            try (InputStream in = jarFile.getInputStream(entry)) {
                                Files.copy(in, outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().fine("[GeyserPack] Note during embedded asset extraction: " + e.getMessage());
        }
    }

    private void updateMappingsWithRegisteredDefinitions() {
        JsonObject root = null;
        try (InputStream in = plugin.getResource("geyser/Synthetix_mappings.json")) {
            if (in != null) {
                root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        } catch (Exception ignored) {}
        if (root == null) {
            root = new JsonObject();
        }

        if (geyserMappingsFile.exists()) {
            try (FileReader reader = new FileReader(geyserMappingsFile, StandardCharsets.UTF_8)) {
                JsonObject diskRoot = JsonParser.parseReader(reader).getAsJsonObject();
                if (diskRoot.has("items") && diskRoot.get("items").isJsonObject()) {
                    JsonObject diskItems = diskRoot.getAsJsonObject("items");
                    JsonObject baseItems = root.has("items") && root.get("items").isJsonObject()
                            ? root.getAsJsonObject("items")
                            : new JsonObject();
                    for (String key : diskItems.keySet()) {
                        if (!baseItems.has(key)) {
                            baseItems.add(key, diskItems.get(key));
                        } else if (baseItems.get(key).isJsonArray() && diskItems.get(key).isJsonArray()) {
                            JsonArray baseArr = baseItems.getAsJsonArray(key);
                            JsonArray diskArr = diskItems.getAsJsonArray(key);
                            for (JsonElement el : diskArr) {
                                if (el.isJsonObject() && el.getAsJsonObject().has("custom_model_data")) {
                                    int cmd = el.getAsJsonObject().get("custom_model_data").getAsInt();
                                    if (!containsCmd(baseArr, cmd)) {
                                        baseArr.add(el);
                                    }
                                }
                            }
                        }
                    }
                    root.add("items", baseItems);
                }
            } catch (Exception ignored) {}
        }

        if (!root.has("format_version")) {
            root.addProperty("format_version", "1");
        }
        JsonObject itemsObj = root.has("items") && root.get("items").isJsonObject()
                ? root.getAsJsonObject("items")
                : new JsonObject();

        // 1. Scan registered custom blocks with Bedrock specs
        for (CustomBlockDefinition blockDef : plugin.getCustomBlockRegistry().getAll()) {
            if (blockDef.getOwningPlugin() != null && !blockDef.getOwningPlugin().equalsIgnoreCase("synthetix")) {
                continue;
            }
            BedrockModelSpec spec = blockDef.getBedrockSpec();
            if (spec == null || !spec.isEnabled()) continue;

            Material mat = spec.getDefaultMaterial() != null ? spec.getDefaultMaterial() : blockDef.getItemMaterial();
            int cmd = spec.getDefaultCustomModelData() > 0 ? spec.getDefaultCustomModelData() : blockDef.getCustomModelData();
            if (cmd <= 0 || mat == null) continue;

            String matKey = "minecraft:" + mat.name().toLowerCase(Locale.ROOT);
            JsonArray arr = itemsObj.has(matKey) ? itemsObj.getAsJsonArray(matKey) : new JsonArray();

            String name = spec.getAttachableIdentifier() != null ? spec.getAttachableIdentifier() : "synthetix_block_" + blockDef.getId();
            if (!containsCmd(arr, cmd)) {
                JsonObject entry = new JsonObject();
                entry.addProperty("name", name);
                entry.addProperty("allow_offhand", true);
                entry.addProperty("icon", name);
                entry.addProperty("frame", 0);
                entry.addProperty("custom_model_data", cmd);
                arr.add(entry);
                itemsObj.add(matKey, arr);
            }
        }

        // 2. Scan registered custom entities with Bedrock specs
        for (CustomEntityDefinition entityDef : plugin.getCustomEntityRegistry().getAll()) {
            if (entityDef.getOwningPlugin() != null && !entityDef.getOwningPlugin().equalsIgnoreCase("synthetix")) {
                continue;
            }
            BedrockModelSpec spec = entityDef.getBedrockSpec();
            if (spec == null || !spec.isEnabled()) continue;

            for (BedrockStateSpec stateSpec : spec.getStateSpecs().values()) {
                Material mat = stateSpec.getItemMaterial() != null ? stateSpec.getItemMaterial() : spec.getDefaultMaterial();
                int cmd = stateSpec.getCustomModelData();
                if (cmd <= 0 || mat == null) continue;

                String matKey = "minecraft:" + mat.name().toLowerCase(Locale.ROOT);
                JsonArray arr = itemsObj.has(matKey) ? itemsObj.getAsJsonArray(matKey) : new JsonArray();

                String name = stateSpec.getAttachableIdentifier() != null ? stateSpec.getAttachableIdentifier()
                        : "synthetix_entity_" + entityDef.getId() + "_" + stateSpec.getStateName();
                if (!containsCmd(arr, cmd)) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("name", name);
                    entry.addProperty("allow_offhand", true);
                    entry.addProperty("icon", name);
                    entry.addProperty("custom_model_data", cmd);
                    arr.add(entry);
                    itemsObj.add(matKey, arr);
                }
            }
        }

        root.add("items", itemsObj);

        try (FileWriter writer = new FileWriter(geyserMappingsFile, StandardCharsets.UTF_8)) {
            gson.toJson(root, writer);
        } catch (IOException e) {
            plugin.getLogger().warning("[GeyserPack] Could not save updated mappings: " + e.getMessage());
        }
    }

    private boolean containsCmd(JsonArray arr, int cmd) {
        for (JsonElement el : arr) {
            if (el.isJsonObject()) {
                JsonObject obj = el.getAsJsonObject();
                if (obj.has("custom_model_data") && obj.get("custom_model_data").getAsInt() == cmd) {
                    return true;
                }
            }
        }
        return false;
    }

    private void compileBedrockResourcePack() throws IOException {
        File tempZip = new File(geyserWorkDir, "temp_pack.zip");
        if (tempZip.exists()) tempZip.delete();

        try (FileOutputStream fos = new FileOutputStream(tempZip);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            zipDirectory(geyserWorkDir, geyserWorkDir, zos);
        }

        Files.move(tempZip.toPath(), geyserPackZip.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private void zipDirectory(File rootDir, File sourceDir, ZipOutputStream zos) throws IOException {
        File[] files = sourceDir.listFiles();
        if (files == null) return;

        byte[] buffer = new byte[8192];
        for (File f : files) {
            if (f.getName().endsWith(".zip") || f.getName().endsWith(".json") && f.getName().contains("mappings")) {
                continue; // Do not recursively zip other archives or root mappings
            }

            if (f.isDirectory()) {
                zipDirectory(rootDir, f, zos);
            } else {
                String relative = rootDir.toPath().relativize(f.toPath()).toString().replace('\\', '/');
                ZipEntry entry = new ZipEntry(relative);
                zos.putNextEntry(entry);
                try (FileInputStream fis = new FileInputStream(f)) {
                    int len;
                    while ((len = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                }
                zos.closeEntry();
            }
        }
    }

    private void deployToGeyserDirectory() {
        File pluginsFolder = plugin.getDataFolder().getParentFile();
        if (pluginsFolder == null) return;

        File geyserFolder = new File(pluginsFolder, "Geyser-Spigot");
        if (!geyserFolder.exists()) {
            geyserFolder = new File(pluginsFolder, "Geyser");
        }

        if (geyserFolder.exists() && geyserFolder.isDirectory()) {
            plugin.getLogger().info(plugin.getLanguageManager().get("bedrock.geyser-detected", "folder", geyserFolder.getName()));
            try {
                // 1. Copy mappings
                File customMappingsDir = new File(geyserFolder, "custom_mappings");
                if (!customMappingsDir.exists()) customMappingsDir.mkdirs();
                File destMappings = new File(customMappingsDir, "Synthetix_mappings.json");
                if (geyserMappingsFile.exists()) {
                    Files.copy(geyserMappingsFile.toPath(), destMappings.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().info(plugin.getLanguageManager().get("bedrock.mappings-deployed"));
                }

                // 2. Copy pack
                File packsDir = new File(geyserFolder, "packs");
                if (!packsDir.exists()) packsDir.mkdirs();
                File destPack = new File(packsDir, "Synthetix_Geyser.zip");
                if (geyserPackZip.exists()) {
                    Files.copy(geyserPackZip.toPath(), destPack.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().info(plugin.getLanguageManager().get("bedrock.pack-deployed"));
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to deploy Bedrock assets to Geyser directory: " + e.getMessage(), e);
            }
        }
    }
}

