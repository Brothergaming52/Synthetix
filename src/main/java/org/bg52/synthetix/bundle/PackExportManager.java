package org.bg52.synthetix.bundle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.block.CustomBlockDefinition;
import org.bg52.synthetix.item.ItemDefinition;
import org.bg52.synthetix.recipe.SynthetixRecipe;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Enterprise exporter for packaging custom items, blocks, recipes, distribution rules,
 * and 3D assets into self-contained .synthpack (.zip) bundles.
 */
public class PackExportManager {

    private final SynthetixPlugin plugin;
    private final Gson gson;

    public PackExportManager(SynthetixPlugin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * Exports one or more items or blocks into a single .synthpack bundle archive.
     *
     * @param targetIds List of item or block IDs to export
     * @param bundleName File name for the output bundle
     * @return The created File, or null if failed
     */
    public File exportBundle(List<String> targetIds, String bundleName) {
        if (targetIds == null || targetIds.isEmpty()) return null;

        File exportDir = new File(plugin.getDataFolder(), "exports");
        if (!exportDir.exists()) exportDir.mkdirs();

        String fileName = bundleName != null && !bundleName.trim().isEmpty() ? bundleName.trim() : "Synthetix_Export_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        if (!fileName.endsWith(".synthpack") && !fileName.endsWith(".zip")) {
            fileName += ".synthpack";
        }

        File targetFile = new File(exportDir, fileName);

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(targetFile))) {
            List<String> validItemIds = new ArrayList<>();
            List<String> validBlockIds = new ArrayList<>();

            // 1. Export Item and Block Definitions
            for (String rawId : targetIds) {
                String id = rawId.toLowerCase(Locale.ROOT).trim();

                // Check Custom Block first
                CustomBlockDefinition blockDef = plugin.getCustomBlockRegistry().get(id);
                if (blockDef != null) {
                    validBlockIds.add(id);
                    YamlConfiguration blockConfig = new YamlConfiguration();
                    blockDef.saveToConfig(blockConfig);

                    ZipEntry blockEntry = new ZipEntry("blocks/" + id + ".yml");
                    zos.putNextEntry(blockEntry);
                    zos.write(blockConfig.saveToString().getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                }

                // Check Item Definition
                ItemDefinition itemDef = plugin.getItemRegistry().get(id);
                if (itemDef != null) {
                    validItemIds.add(id);
                    YamlConfiguration itemConfig = new YamlConfiguration();
                    itemDef.saveToConfig(itemConfig.createSection(id));

                    ZipEntry itemEntry = new ZipEntry("items/" + id + ".yml");
                    zos.putNextEntry(itemEntry);
                    zos.write(itemConfig.saveToString().getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                }
            }

            if (validItemIds.isEmpty() && validBlockIds.isEmpty()) {
                targetFile.delete();
                return null;
            }

            Set<String> allExportedIds = new HashSet<>(validItemIds);
            allExportedIds.addAll(validBlockIds);

            // 2. Export Associated Recipes
            List<SynthetixRecipe> attachedRecipes = new ArrayList<>();
            for (SynthetixRecipe recipe : plugin.getRecipeManager().getAllRecipes()) {
                if (allExportedIds.contains(recipe.getResultItemId())) {
                    attachedRecipes.add(recipe);
                }
            }

            if (!attachedRecipes.isEmpty()) {
                YamlConfiguration recipeConfig = new YamlConfiguration();
                for (SynthetixRecipe r : attachedRecipes) {
                    org.bukkit.configuration.ConfigurationSection sec = recipeConfig.createSection(r.getId());
                    r.saveToConfig(sec);
                }
                ZipEntry recEntry = new ZipEntry("recipes/recipes.yml");
                zos.putNextEntry(recEntry);
                zos.write(recipeConfig.saveToString().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }

            // 3. Export Bundled Texture/Model Assets if found in resources/assets
            File assetsDir = new File(plugin.getDataFolder(), "resources/assets");
            if (assetsDir.exists() && assetsDir.isDirectory()) {
                for (String id : allExportedIds) {
                    findAndPackageMatchingAssets(zos, assetsDir, id);
                }
            }

            // 4. Create and write manifest.json
            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("format", 1);
            manifest.put("plugin", "Synthetix");
            manifest.put("version", plugin.getDescription().getVersion());
            manifest.put("timestamp", System.currentTimeMillis());
            manifest.put("items", validItemIds);
            manifest.put("blocks", validBlockIds);
            manifest.put("recipeCount", attachedRecipes.size());

            ZipEntry manifestEntry = new ZipEntry("manifest.json");
            zos.putNextEntry(manifestEntry);
            zos.write(gson.toJson(manifest).getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            return targetFile;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to export bundle " + fileName + ": " + e.getMessage());
            return null;
        }
    }

    private void findAndPackageMatchingAssets(ZipOutputStream zos, File assetsRoot, String prefix) {
        File[] namespaces = assetsRoot.listFiles(File::isDirectory);
        if (namespaces == null) return;

        for (File ns : namespaces) {
            // Check models/item
            File modelsItemDir = new File(ns, "models/item");
            if (modelsItemDir.exists() && modelsItemDir.isDirectory()) {
                File[] matchingModels = modelsItemDir.listFiles((dir, name) -> (name.equals(prefix + ".json") || name.startsWith(prefix + "_")) && name.endsWith(".json"));
                if (matchingModels != null) {
                    for (File m : matchingModels) {
                        packageFile(zos, m, "assets/" + ns.getName() + "/models/item/" + m.getName());
                    }
                }
            }
            // Check textures/item
            File texturesItemDir = new File(ns, "textures/item");
            if (texturesItemDir.exists() && texturesItemDir.isDirectory()) {
                File[] matchingTextures = texturesItemDir.listFiles((dir, name) -> (name.equals(prefix + ".png") || name.startsWith(prefix + "_")) && name.endsWith(".png"));
                if (matchingTextures != null) {
                    for (File t : matchingTextures) {
                        packageFile(zos, t, "assets/" + ns.getName() + "/textures/item/" + t.getName());
                    }
                }
            }
        }
    }

    private void packageFile(ZipOutputStream zos, File file, String zipPath) {
        try {
            ZipEntry entry = new ZipEntry(zipPath.replace('\\', '/'));
            zos.putNextEntry(entry);
            try (FileInputStream fis = new FileInputStream(file)) {
                fis.transferTo(zos);
            }
            zos.closeEntry();
        } catch (Exception ignored) {}
    }
}

