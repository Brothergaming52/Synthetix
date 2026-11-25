package org.bg52.synthetix.bundle;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bg52.synthetix.SynthetixPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Enterprise importer for unpacking .synthpack and .zip packages dropped into imports/.
 * Automatically extracts item definitions, merges recipes, places resource pack models,
 * and hot-reloads live Bukkit registries immediately.
 */
public class PackImportManager {

    private final SynthetixPlugin plugin;
    private final Gson gson;

    public PackImportManager(SynthetixPlugin plugin) {
        this.plugin = plugin;
        this.gson = new Gson();
    }

    /**
     * Scans plugins/Synthetix/imports/ and processes any pending archives.
     *
     * @return Total number of items imported across all archives
     */
    public int scanAndImportAll() {
        File importDir = new File(plugin.getDataFolder(), "imports");
        if (!importDir.exists()) {
            importDir.mkdirs();
            return 0;
        }

        File[] archives = importDir.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".synthpack") || lower.endsWith(".zip");
        });

        if (archives == null || archives.length == 0) return 0;

        int totalImported = 0;
        for (File archive : archives) {
            int count = importArchive(archive);
            totalImported += count;
        }

        if (totalImported > 0) {
            // Hot reload registries
            plugin.getItemRegistry().loadAll();
            plugin.getCustomBlockRegistry().loadAll();
            plugin.getRecipeManager().reload();
            plugin.getResourcePackManager().generatePackAsync();
            plugin.getLogger().info("Successfully imported " + totalImported + " custom item(s)/block(s) from packages.");
        }

        return totalImported;
    }

    /**
     * Safely unzips and imports a single .synthpack or .zip archive.
     */
    public int importArchive(File archive) {
        if (archive == null || !archive.exists()) return 0;

        File processedDir = new File(plugin.getDataFolder(), "imports/processed");
        if (!processedDir.exists()) processedDir.mkdirs();

        int importedItems = 0;
        boolean hasAssets = false;

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(archive))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];

            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();

                // Prevent ZipSlip vulnerability
                if (entryName.contains("..")) {
                    zis.closeEntry();
                    continue;
                }

                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }

                if (entryName.startsWith("items/") && entryName.endsWith(".yml")) {
                    // Extract item file to plugins/Synthetix/items/
                    File targetFile = new File(plugin.getDataFolder(), entryName);
                    targetFile.getParentFile().mkdirs();
                    writeFile(targetFile, zis, buffer);
                    importedItems++;
                } else if (entryName.startsWith("blocks/") && entryName.endsWith(".yml")) {
                    // Extract block file to plugins/Synthetix/blocks/
                    File targetFile = new File(plugin.getDataFolder(), entryName);
                    targetFile.getParentFile().mkdirs();
                    writeFile(targetFile, zis, buffer);
                    importedItems++;
                } else if (entryName.startsWith("recipes/") && entryName.endsWith(".yml")) {
                    // Extract recipe file with unique name
                    String cleanName = new File(entryName).getName();
                    File targetFile = new File(plugin.getDataFolder(), "recipes/imported_" + System.currentTimeMillis() + "_" + cleanName);
                    targetFile.getParentFile().mkdirs();
                    writeFile(targetFile, zis, buffer);
                } else if (entryName.startsWith("assets/")) {
                    // Extract texture/model asset to resources/assets/
                    File targetFile = new File(plugin.getDataFolder(), "resources/" + entryName);
                    targetFile.getParentFile().mkdirs();
                    writeFile(targetFile, zis, buffer);
                    hasAssets = true;
                }

                zis.closeEntry();
            }

            // Move archive to processed folder
            File destination = new File(processedDir, System.currentTimeMillis() + "_" + archive.getName());
            Files.move(archive.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);

            return importedItems;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to process import package " + archive.getName() + ": " + e.getMessage());
            return 0;
        }
    }

    private void writeFile(File target, InputStream is, byte[] buffer) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(target)) {
            int len;
            while ((len = is.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
        }
    }
}

