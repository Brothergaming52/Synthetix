package org.bg52.synthetix.block;

import com.google.gson.*;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.item.ItemDefinition;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Material;
import org.joml.Vector3f;
import org.joml.Vector3i;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.Level;

/**
 * Universal, generalized importer and dynamic slicing engine for Blockbench models (.json & .bbmodel).
 * Supports:
 * - Direct block import (/synth blockbench block import <name>)
 * - Direct item import (/synth blockbench item import <name>)
 * - Automated model part generation and dynamic model slicing on demand
 * - Texture extraction and path normalization
 * - Consumes and removes imported files from the blockbench folder upon completion
 * - Strict generalization without hardcoded part names or block models.
 */
public class BlockbenchImporter {

    private final SynthetixPlugin plugin;
    private final File importFolder;
    private final File itemsFolder;
    private final File blocksFolder;
    private final File entitiesFolder;
    private final File blockbenchFolder;
    private final File sourceModelsFolder;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public BlockbenchImporter(SynthetixPlugin plugin) {
        this.plugin = plugin;
        this.importFolder = new File(plugin.getDataFolder(), "import");
        this.itemsFolder = new File(importFolder, "items");
        this.blocksFolder = new File(importFolder, "blocks");
        this.entitiesFolder = new File(importFolder, "entities");
        this.blockbenchFolder = new File(plugin.getDataFolder(), "blockbench");
        this.sourceModelsFolder = new File(plugin.getDataFolder(), "models/source");
        ensureDirectoriesExist();
    }

    /**
     * Ensures that import/items, import/blocks, and import/entities exist on disk.
     */
    public void ensureDirectoriesExist() {
        if (!itemsFolder.exists()) itemsFolder.mkdirs();
        if (!blocksFolder.exists()) blocksFolder.mkdirs();
        if (!entitiesFolder.exists()) entitiesFolder.mkdirs();
    }

    /**
     * Lists all model base names available across import folders.
     */
    public List<String> listAvailableModels() {
        List<String> list = new ArrayList<>();
        List<File> searchDirs = List.of(blocksFolder, itemsFolder, importFolder, blockbenchFolder);

        for (File dir : searchDirs) {
            if (!dir.exists() || !dir.isDirectory()) continue;
            File[] files = dir.listFiles((d, name) -> {
                String lower = name.toLowerCase(Locale.ROOT);
                return lower.endsWith(".json") || lower.endsWith(".bbmodel");
            });

            if (files != null) {
                for (File f : files) {
                    String name = f.getName();
                    int dot = name.lastIndexOf('.');
                    String base = dot > 0 ? name.substring(0, dot) : name;
                    if (!list.contains(base)) {
                        list.add(base);
                    }
                }
            }
        }
        return list;
    }

    /**
     * Finds a model file across import folders by query name.
     */
    public File findModelFile(String query) {
        if (query == null || query.trim().isEmpty()) return null;
        String clean = query.trim().toLowerCase(Locale.ROOT);
        List<File> searchDirs = List.of(blocksFolder, itemsFolder, importFolder, blockbenchFolder);

        for (File dir : searchDirs) {
            if (!dir.exists() || !dir.isDirectory()) continue;

            File exactJson = new File(dir, clean + ".json");
            if (exactJson.exists()) return exactJson;

            File exactBb = new File(dir, clean + ".bbmodel");
            if (exactBb.exists()) return exactBb;

            File exact = new File(dir, clean);
            if (exact.exists()) return exact;

            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    String n = f.getName().toLowerCase(Locale.ROOT);
                    if (n.equals(clean + ".json") || n.equals(clean + ".bbmodel") || n.equals(clean)) {
                        return f;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Imports a Blockbench JSON model as a custom block.
     * Sets 1x1x1 dimensions by default, extracts parts, slices models, and deletes source files.
     */
    public boolean importBlockModelFile(File file, String customBlockId, boolean deleteSource) {
        if (file == null || !file.exists()) return false;

        String rawName = file.getName();
        String blockId = (customBlockId != null && !customBlockId.trim().isEmpty()) ?
                customBlockId.trim().toLowerCase(Locale.ROOT).replace(' ', '_') :
                rawName.substring(0, rawName.lastIndexOf('.')).toLowerCase(Locale.ROOT).replace(' ', '_');

        CustomBlockDefinition def = plugin.getCustomBlockRegistry().get(blockId);
        boolean isNew = def == null;
        if (isNew) {
            def = new CustomBlockDefinition(blockId);
            def.setDisplayName("<gold><bold>" + TextUtil.prettify(blockId) + "</bold></gold>");
            def.setBaseMaterial(Material.BARRIER);
            def.setItemMaterial(Material.PAPER);
            def.setRenderingMethod(RenderingMethod.ITEM_DISPLAY);
            def.setMultiPartSize(new Vector3i(1, 1, 1));
            def.getPhysics().setCollisionShape(CollisionShape.SOLID_CUBE);
            def.getPhysics().setSolid(true);
            def.getMining().setHardness(1.5f);
            def.getMining().setBlastResistance(6.0f);
        }

        // Copy textures to resource pack assets/synthetix/textures/item/
        File parentDir = file.getParentFile();
        List<String> textureNames = new ArrayList<>();
        List<File> copiedTextureFiles = new ArrayList<>();

        if (parentDir != null && parentDir.exists()) {
            File rpTexturesItemDir = new File(plugin.getDataFolder(), "resources/assets/synthetix/textures/item");
            if (!rpTexturesItemDir.exists()) rpTexturesItemDir.mkdirs();

            File[] pngs = parentDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".png"));
            if (pngs != null) {
                for (File png : pngs) {
                    try {
                        String cleanName = png.getName().toLowerCase(Locale.ROOT);
                        Files.copy(png.toPath(), new File(rpTexturesItemDir, cleanName).toPath(), StandardCopyOption.REPLACE_EXISTING);
                        textureNames.add(cleanName.substring(0, cleanName.lastIndexOf('.')));
                        copiedTextureFiles.add(png);
                    } catch (Exception ignored) {}
                }
            }
        }

        if (def.getCustomModelData() <= 0) {
            def.setCustomModelData(generateNextCustomModelData());
        }

        // Save a permanent copy of the source JSON for dynamic slicing
        File persistentSource = new File(sourceModelsFolder, blockId + "_source.json");
        JsonObject rootJson;
        try (FileReader reader = new FileReader(file)) {
            rootJson = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to parse model JSON: " + file.getName(), e);
            return false;
        }

        // Default display transformations: scale 1.0
        def.setDisplayScale(new Vector3f(1.0f, 1.0f, 1.0f));
        def.setDisplayTranslation(new Vector3f(0.0f, 0.0f, 0.0f));
        def.setDisplayRotation(new Vector3f(0.0f, 0.0f, 0.0f));

        // Normalize texture references and ensure all referenced textures exist on disk
        if (rootJson.has("textures")) {
            JsonObject textures = rootJson.getAsJsonObject("textures");
            File rpTexturesItemDir = new File(plugin.getDataFolder(), "resources/assets/synthetix/textures/item");
            if (!rpTexturesItemDir.exists()) rpTexturesItemDir.mkdirs();

            for (String key : textures.keySet()) {
                String val = textures.get(key).getAsString();
                if (!val.contains(":")) {
                    String targetTex = sanitizeTexturePath(val);
                    if (!textureNames.contains(targetTex) && !textureNames.isEmpty()) {
                        try {
                            File primaryPng = copiedTextureFiles.get(0);
                            Files.copy(primaryPng.toPath(), new File(rpTexturesItemDir, targetTex + ".png").toPath(), StandardCopyOption.REPLACE_EXISTING);
                            textureNames.add(targetTex);
                        } catch (Exception ignored) {
                            targetTex = textureNames.get(0);
                        }
                    }
                    textures.addProperty(key, "synthetix:item/" + targetTex);
                } else if (val.startsWith("custom:")) {
                    String sub = val.substring("custom:".length());
                    if (sub.startsWith("block/")) sub = sub.substring("block/".length());
                    textures.addProperty(key, "synthetix:item/" + sub);
                } else if (val.contains(":block/")) {
                    textures.addProperty(key, val.replace(":block/", ":item/"));
                }
            }
        }

        // Save normalized source JSON
        if (persistentSource.getParentFile() != null && !persistentSource.getParentFile().exists()) {
            persistentSource.getParentFile().mkdirs();
        }
        try (FileWriter writer = new FileWriter(persistentSource)) {
            gson.toJson(rootJson, writer);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not save persistent source model: " + e.getMessage());
        }

        // Generate generalized model parts list from elements
        if (rootJson.has("elements")) {
            JsonArray elements = rootJson.getAsJsonArray("elements");

            // First pass: count name occurrences to disambiguate duplicate element names
            Map<String, Integer> nameCounts = new HashMap<>();
            for (int i = 0; i < elements.size(); i++) {
                JsonObject el = elements.get(i).getAsJsonObject();
                if (el.has("name")) {
                    String raw = el.get("name").getAsString().trim();
                    if (!raw.isEmpty()) {
                        String s = sanitizePartId(raw);
                        nameCounts.put(s, nameCounts.getOrDefault(s, 0) + 1);
                    }
                }
            }

            Map<String, Integer> currentOccurrenceMap = new HashMap<>();
            List<BlockModelPart> detectedParts = new ArrayList<>();

            for (int i = 0; i < elements.size(); i++) {
                JsonObject el = elements.get(i).getAsJsonObject();
                String rawElementName = el.has("name") ? el.get("name").getAsString().trim() : "";
                if (rawElementName.isEmpty()) {
                    // Unnamed elements remain in static casing by default
                    continue;
                }
                String baseId = sanitizePartId(rawElementName);
                int totalWithName = nameCounts.getOrDefault(baseId, 0);
                int occurrence = currentOccurrenceMap.getOrDefault(baseId, 0) + 1;
                currentOccurrenceMap.put(baseId, occurrence);

                String partId = totalWithName > 1 ? baseId + "_" + occurrence : baseId;
                BlockModelPart part = new BlockModelPart(partId);
                part.getElementIndices().add(i);

                BlockAnimationType suggested = detectSuggestedAnimation(el);
                part.setAnimationType(suggested);
                part.setAnimated(suggested != BlockAnimationType.STATIC);
                part.setAnimationSpeed(1.5);
                part.setInterpolationDuration(2);
                detectedParts.add(part);
            }

            // Only overwrite parts if new definition or empty
            if (def.getModelParts().isEmpty()) {
                def.setModelParts(detectedParts);
                if (!detectedParts.isEmpty()) {
                    def.setMultiModel(true);
                }
            }
        }

        // Perform initial slice and model build
        sliceAndBuildModels(def);
        plugin.getCustomBlockRegistry().save(def);

        // Delete source model file and associated textures from blockbench directory if requested
        if (deleteSource) {
            try {
                file.delete();
                for (File texFile : copiedTextureFiles) {
                    if (texFile.exists() && (texFile.getParentFile().equals(blockbenchFolder) || texFile.getParentFile().equals(blocksFolder) || texFile.getParentFile().equals(itemsFolder))) {
                        texFile.delete();
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Could not delete imported source files: " + e.getMessage());
            }
        }

        return true;
    }

    /**
     * Imports a Blockbench JSON model as a custom item.
     * Places model in assets/synthetix/models/item/, registers ItemDefinition, and deletes source files.
     */
    public boolean importItemModelFile(File file, String customItemId, boolean deleteSource) {
        if (file == null || !file.exists()) return false;

        String rawName = file.getName();
        String itemId = (customItemId != null && !customItemId.trim().isEmpty()) ?
                customItemId.trim().toLowerCase(Locale.ROOT).replace(' ', '_') :
                rawName.substring(0, rawName.lastIndexOf('.')).toLowerCase(Locale.ROOT).replace(' ', '_');

        // Copy textures to resource pack assets/synthetix/textures/item/ and textures/block/
        File parentDir = file.getParentFile();
        List<String> textureNames = new ArrayList<>();
        List<File> copiedTextureFiles = new ArrayList<>();

        if (parentDir != null && parentDir.exists()) {
            File rpTexturesDir = new File(plugin.getDataFolder(), "resources/assets/synthetix/textures/item");
            File rpTexturesBlockDir = new File(plugin.getDataFolder(), "resources/assets/synthetix/textures/block");
            if (!rpTexturesDir.exists()) rpTexturesDir.mkdirs();
            if (!rpTexturesBlockDir.exists()) rpTexturesBlockDir.mkdirs();

            File[] pngs = parentDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".png"));
            if (pngs != null) {
                for (File png : pngs) {
                    try {
                        String cleanName = png.getName().toLowerCase(Locale.ROOT);
                        Files.copy(png.toPath(), new File(rpTexturesDir, cleanName).toPath(), StandardCopyOption.REPLACE_EXISTING);
                        Files.copy(png.toPath(), new File(rpTexturesBlockDir, cleanName).toPath(), StandardCopyOption.REPLACE_EXISTING);
                        textureNames.add(cleanName.substring(0, cleanName.lastIndexOf('.')));
                        copiedTextureFiles.add(png);
                    } catch (Exception ignored) {}
                }
            }
        }

        JsonObject rootJson;
        try (FileReader reader = new FileReader(file)) {
            rootJson = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to parse item model JSON: " + file.getName(), e);
            return false;
        }

        // Normalize textures for item namespace
        if (rootJson.has("textures")) {
            JsonObject textures = rootJson.getAsJsonObject("textures");
            File rpTexturesDir = new File(plugin.getDataFolder(), "resources/assets/synthetix/textures/item");
            File rpTexturesBlockDir = new File(plugin.getDataFolder(), "resources/assets/synthetix/textures/block");

            for (String key : textures.keySet()) {
                String val = textures.get(key).getAsString();
                if (!val.contains(":")) {
                    String targetTex = sanitizeTexturePath(val);
                    if (!textureNames.contains(targetTex) && !textureNames.isEmpty()) {
                        try {
                            File primaryPng = copiedTextureFiles.get(0);
                            Files.copy(primaryPng.toPath(), new File(rpTexturesDir, targetTex + ".png").toPath(), StandardCopyOption.REPLACE_EXISTING);
                            Files.copy(primaryPng.toPath(), new File(rpTexturesBlockDir, targetTex + ".png").toPath(), StandardCopyOption.REPLACE_EXISTING);
                            textureNames.add(targetTex);
                        } catch (Exception ignored) {
                            targetTex = textureNames.get(0);
                        }
                    }
                    textures.addProperty(key, "synthetix:item/" + targetTex);
                } else if (val.startsWith("custom:")) {
                    textures.addProperty(key, "synthetix:" + val.substring("custom:".length()));
                }
            }
        }

        // Save item model JSON into resource pack models/item/
        File rpModelsDir = new File(plugin.getDataFolder(), "resources/assets/synthetix/models/item");
        if (!rpModelsDir.exists()) rpModelsDir.mkdirs();

        File itemModelFile = new File(rpModelsDir, itemId + ".json");
        try (FileWriter writer = new FileWriter(itemModelFile)) {
            gson.toJson(rootJson, writer);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not save item model JSON: " + e.getMessage());
            return false;
        }

        // Generate modern item definition at items/<itemId>.json
        File rpItemsDir = new File(plugin.getDataFolder(), "resources/assets/synthetix/items");
        if (!rpItemsDir.exists()) rpItemsDir.mkdirs();
        File itemJsonFile = new File(rpItemsDir, itemId + ".json");
        try (FileWriter writer = new FileWriter(itemJsonFile, StandardCharsets.UTF_8)) {
            JsonObject itemObj = new JsonObject();
            JsonObject modelObj = new JsonObject();
            modelObj.addProperty("type", "minecraft:model");
            modelObj.addProperty("model", "synthetix:item/" + itemId);
            itemObj.add("model", modelObj);
            gson.toJson(itemObj, writer);
        } catch (Exception ignored) {}

        // Create or update ItemDefinition
        ItemDefinition def = plugin.getItemRegistry().get(itemId);
        if (def == null) {
            def = new ItemDefinition(itemId);
            def.setDisplayName("<gold><bold>" + TextUtil.prettify(itemId) + "</bold></gold>");
            def.setMaterial(Material.PAPER);
        }

        int cmd = def.getCustomModelData() != null && def.getCustomModelData() > 0 ?
                def.getCustomModelData() : generateNextCustomModelData();
        def.setCustomModelData(cmd);
        def.setItemModel("synthetix:" + itemId);

        plugin.getItemRegistry().register(def);
        plugin.getItemRegistry().save(def);

        // Register override & rebuild pack
        plugin.getResourcePackManager().registerItemModelOverride("paper", cmd, "synthetix:item/" + itemId);
        plugin.getResourcePackManager().generatePackAsync();

        // Delete source files if requested
        if (deleteSource) {
            try {
                file.delete();
                for (File texFile : copiedTextureFiles) {
                    if (texFile.exists() && (texFile.getParentFile().equals(blockbenchFolder) || texFile.getParentFile().equals(blocksFolder) || texFile.getParentFile().equals(itemsFolder))) {
                        texFile.delete();
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Could not delete imported item files: " + e.getMessage());
            }
        }

        return true;
    }

    /**
     * Slices the block source model into static casing and independent animated parts
     * automatically whenever parts are modified by the administrator or API.
     */
    public boolean sliceAndBuildModels(CustomBlockDefinition def) {
        if (def == null) return false;
        String blockId = def.getId();

        File persistentSource = new File(sourceModelsFolder, blockId + "_source.json");
        if (!persistentSource.exists()) {
            // Check fallback in resource pack
            persistentSource = new File(plugin.getDataFolder(), "resources/assets/synthetix/models/block/" + blockId + ".json");
            if (!persistentSource.exists()) {
                persistentSource = new File(plugin.getDataFolder(), "resources/assets/synthetix/models/block/" + blockId + "_casing.json");
            }
        }

        if (!persistentSource.exists()) {
            plugin.getLogger().warning("Source model for block " + blockId + " not found to slice.");
            return false;
        }

        JsonObject sourceJson;
        try (FileReader reader = new FileReader(persistentSource)) {
            sourceJson = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read source model JSON: " + e.getMessage());
            return false;
        }

        File rpModelsItemDir = new File(plugin.getDataFolder(), "resources/assets/synthetix/models/item");
        if (!rpModelsItemDir.exists()) rpModelsItemDir.mkdirs();

        File rpItemsDir = new File(plugin.getDataFolder(), "resources/assets/synthetix/items");
        if (!rpItemsDir.exists()) rpItemsDir.mkdirs();

        boolean hasElements = sourceJson.has("elements");
        JsonArray elements = hasElements ? sourceJson.getAsJsonArray("elements") : new JsonArray();

        // Check if multi-model composite should be active:
        // Active when def.isMultiModel() is explicitly true and parts exist, OR any part has movement
        boolean compositeActive = (def.isMultiModel() || def.getModelParts().stream().anyMatch(BlockModelPart::isAnimated))
                && !def.getModelParts().isEmpty() && hasElements;

        // 1. ALWAYS write the complete, intact original source model to models/item/<blockId>.json
        File fullItemFile = new File(rpModelsItemDir, blockId + ".json");
        try (FileWriter writer = new FileWriter(fullItemFile)) {
            gson.toJson(sourceJson, writer);
        } catch (Exception ignored) {}

        // ALWAYS keep def.getItemModel() as the complete original block model!
        def.setItemModel("synthetix:" + blockId);
        writeItemModelDefinition(rpItemsDir, blockId, "synthetix:" + blockId);
        plugin.getResourcePackManager().registerItemModelOverride("paper", def.getCustomModelData(), "synthetix:item/" + blockId);

        if (compositeActive) {
            // Composite multi-model mode!
            def.setMultiModel(true);
            def.setRenderingMethod(RenderingMethod.MULTI_MODEL_COMPOSITE);

            Map<BlockModelPart, JsonArray> partElementsMap = new LinkedHashMap<>();
            Set<Integer> claimedIndices = new HashSet<>();

            // 1. Assign elements by elementIndices
            for (BlockModelPart part : def.getModelParts()) {
                JsonArray pElems = new JsonArray();
                partElementsMap.put(part, pElems);
                for (int idx : part.getElementIndices()) {
                    if (idx >= 0 && idx < elements.size()) {
                        pElems.add(elements.get(idx));
                        claimedIndices.add(idx);
                    }
                }
            }

            // 2. Fallback for parts with empty elementIndices: match by name
            for (BlockModelPart part : def.getModelParts()) {
                if (partElementsMap.get(part).isEmpty()) {
                    String cleanPartId = sanitizePartId(part.getId());
                    for (int i = 0; i < elements.size(); i++) {
                        if (claimedIndices.contains(i)) continue;
                        JsonObject el = elements.get(i).getAsJsonObject();
                        String rawName = el.has("name") ? el.get("name").getAsString() : "";
                        String cleanName = sanitizePartId(rawName);
                        if (cleanName.equalsIgnoreCase(cleanPartId) || cleanName.startsWith(cleanPartId) || cleanPartId.startsWith(cleanName)) {
                            partElementsMap.get(part).add(el);
                            claimedIndices.add(i);
                            part.getElementIndices().add(i);
                            break;
                        }
                    }
                }
            }

            // 3. Static casing gets any remaining unclaimed elements
            JsonArray staticElements = new JsonArray();
            for (int i = 0; i < elements.size(); i++) {
                if (!claimedIndices.contains(i)) {
                    staticElements.add(elements.get(i));
                }
            }

            // Save static casing model into models/item/<blockId>_casing.json
            JsonObject casingJson = sourceJson.deepCopy();
            casingJson.add("elements", staticElements);
            File casingItemFile = new File(rpModelsItemDir, blockId + "_casing.json");
            try (FileWriter writer = new FileWriter(casingItemFile)) {
                gson.toJson(casingJson, writer);
            } catch (Exception ignored) {}

            writeItemModelDefinition(rpItemsDir, blockId + "_casing", "synthetix:" + blockId + "_casing");

            // Save sub-models for all parts
            for (BlockModelPart part : def.getModelParts()) {
                String cleanPartId = sanitizePartId(part.getId());
                JsonArray partElems = partElementsMap.get(part);
                if (partElems == null || partElems.isEmpty()) {
                    partElems = elements;
                }

                JsonObject partJson = sourceJson.deepCopy();
                partJson.add("elements", partElems);

                File partItemFile = new File(rpModelsItemDir, blockId + "_" + cleanPartId + ".json");
                try (FileWriter writer = new FileWriter(partItemFile)) {
                    gson.toJson(partJson, writer);
                } catch (Exception ignored) {}

                part.setItemModel("synthetix:" + blockId + "_" + cleanPartId);
                writeItemModelDefinition(rpItemsDir, blockId + "_" + cleanPartId, part.getItemModel());
                if (part.getCustomModelData() <= 0) {
                    part.setCustomModelData(generateNextCustomModelData());
                }
                plugin.getResourcePackManager().registerItemModelOverride("paper", part.getCustomModelData(), "synthetix:item/" + blockId + "_" + cleanPartId);
            }
        } else {
            // Single unified model
            def.setMultiModel(false);
            def.setRenderingMethod(RenderingMethod.ITEM_DISPLAY);
        }

        // Save definition updates
        plugin.getCustomBlockRegistry().save(def);
        plugin.getResourcePackManager().generatePackAsync();
        return true;
    }

    /**
     * Automatically groups parts that share the same animation motion, speed, and rotation axis lock
     * into a single unified BlockModelPart and 3D model file, reducing active entity count from 5-6 down to 1.
     *
     * @return number of redundant parts eliminated
     */
    public int autoGroupPartsByMotion(CustomBlockDefinition def) {
        if (def == null || def.getModelParts().size() <= 1) return 0;

        List<BlockModelPart> parts = def.getModelParts();
        Map<String, List<BlockModelPart>> motionGroups = new LinkedHashMap<>();

        for (BlockModelPart part : parts) {
            if (!part.isAnimated() || part.getAnimationType() == BlockAnimationType.STATIC) {
                continue; // Do not auto-group static parts
            }
            // Unique signature for identical animation kinematics
            String sig = part.getAnimationType().name() + "_" +
                    Math.round(part.getAnimationSpeed() * 100) + "_" +
                    part.getInterpolationDuration() + "_" +
                    part.isLockRotationAxis();
            motionGroups.computeIfAbsent(sig, k -> new ArrayList<>()).add(part);
        }

        int eliminated = 0;
        List<BlockModelPart> toRemove = new ArrayList<>();

        for (List<BlockModelPart> group : motionGroups.values()) {
            if (group.size() <= 1) continue;

            // First part in group becomes master
            BlockModelPart master = group.get(0);
            for (int i = 1; i < group.size(); i++) {
                BlockModelPart slave = group.get(i);
                for (int elemIdx : slave.getElementIndices()) {
                    if (!master.getElementIndices().contains(elemIdx)) {
                        master.getElementIndices().add(elemIdx);
                    }
                }
                toRemove.add(slave);
                eliminated++;
            }
        }

        if (eliminated > 0) {
            parts.removeAll(toRemove);
            sliceAndBuildModels(def);
            plugin.getCustomBlockManager().respawnDisplayEntities(def);
        }

        return eliminated;
    }

    /**
     * Imports all models dropped into plugins/Synthetix/import/items/.
     */
    public int importPendingItems(boolean deleteSource) {
        if (!itemsFolder.exists() || !itemsFolder.isDirectory()) return 0;
        File[] files = itemsFolder.listFiles((dir, name) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            return lower.endsWith(".json") || lower.endsWith(".bbmodel");
        });
        if (files == null || files.length == 0) return 0;

        int count = 0;
        for (File f : files) {
            try {
                if (importItemModelFile(f, null, deleteSource)) {
                    count++;
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to import item model: " + f.getName(), e);
            }
        }
        return count;
    }

    /**
     * Imports all models dropped into plugins/Synthetix/import/blocks/.
     */
    public int importPendingBlocks(boolean deleteSource) {
        List<File> toScan = new ArrayList<>();
        if (blocksFolder.exists() && blocksFolder.isDirectory()) {
            File[] files = blocksFolder.listFiles((dir, name) -> {
                String lower = name.toLowerCase(Locale.ROOT);
                return lower.endsWith(".json") || lower.endsWith(".bbmodel");
            });
            if (files != null) toScan.addAll(List.of(files));
        }

        // Backward-compatible check for legacy blockbench directory
        if (blockbenchFolder.exists() && blockbenchFolder.isDirectory()) {
            File[] files = blockbenchFolder.listFiles((dir, name) -> {
                String lower = name.toLowerCase(Locale.ROOT);
                return lower.endsWith(".json") || lower.endsWith(".bbmodel");
            });
            if (files != null) toScan.addAll(List.of(files));
        }

        if (toScan.isEmpty()) return 0;

        int count = 0;
        for (File file : toScan) {
            try {
                if (importBlockModelFile(file, null, deleteSource)) {
                    count++;
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to import block model: " + file.getName(), e);
            }
        }
        return count;
    }

    /**
     * Batch scanner for startup / auto-import.
     */
    public int scanAndImportAll() {
        int items = importPendingItems(true);
        int blocks = importPendingBlocks(true);
        return items + blocks;
    }

    private void writeItemModelDefinition(File itemsDir, String id, String targetModel) {
        try {
            String cleanId = id;
            if (cleanId.contains(":")) {
                cleanId = cleanId.substring(cleanId.indexOf(':') + 1);
            }
            while (cleanId.startsWith("block/") || cleanId.startsWith("blocks/") ||
                   cleanId.startsWith("item/") || cleanId.startsWith("items/")) {
                cleanId = cleanId.substring(cleanId.indexOf('/') + 1);
            }

            File itemJsonFile = new File(itemsDir, cleanId + ".json");
            String actualModel = BlockModelPart.normalizeModelKey(targetModel);
            if (actualModel == null) actualModel = "synthetix:" + cleanId;
            String[] parts = actualModel.split(":", 2);
            String modelPath = parts[0] + ":item/" + parts[1];

            JsonObject itemObj = new JsonObject();
            JsonObject modelObj = new JsonObject();
            modelObj.addProperty("type", "minecraft:model");
            modelObj.addProperty("model", modelPath);
            itemObj.add("model", modelObj);
            try (FileWriter writer = new FileWriter(itemJsonFile, StandardCharsets.UTF_8)) {
                gson.toJson(itemObj, writer);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Generalized heuristic to detect the most natural rotation motion and axis
     * from a Blockbench element definition, handling Format A (axis) and Format B (Euler rotation).
     */
    public static BlockAnimationType detectSuggestedAnimation(JsonObject el) {
        if (el == null) return BlockAnimationType.ROTATION_Y;

        double dx = 0, dy = 0, dz = 0;
        if (el.has("from") && el.has("to")) {
            JsonArray from = el.getAsJsonArray("from");
            JsonArray to = el.getAsJsonArray("to");
            if (from.size() >= 3 && to.size() >= 3) {
                dx = Math.abs(to.get(0).getAsDouble() - from.get(0).getAsDouble());
                dy = Math.abs(to.get(1).getAsDouble() - from.get(1).getAsDouble());
                dz = Math.abs(to.get(2).getAsDouble() - from.get(2).getAsDouble());
            }
        }

        if (el.has("rotation")) {
            JsonObject rot = el.getAsJsonObject("rotation");
            if (rot.has("axis")) {
                String axis = rot.get("axis").getAsString().toLowerCase(Locale.ROOT);
                return switch (axis) {
                    case "x" -> BlockAnimationType.ROTATION_X;
                    case "z" -> BlockAnimationType.ROTATION_Z;
                    default -> BlockAnimationType.ROTATION_Y;
                };
            }
            double rx = rot.has("x") ? Math.abs(rot.get("x").getAsDouble()) : 0;
            double ry = rot.has("y") ? Math.abs(rot.get("y").getAsDouble()) : 0;
            double rz = rot.has("z") ? Math.abs(rot.get("z").getAsDouble()) : 0;

            if (rz >= 45 && rz <= 135) {
                return BlockAnimationType.ROTATION_X;
            }
            if (rx >= 45 && rx <= 135) {
                return BlockAnimationType.ROTATION_Z;
            }
            if (rx > 0 && rx >= ry && rx >= rz) return BlockAnimationType.ROTATION_X;
            if (rz > 0 && rz >= rx && rz >= ry) return BlockAnimationType.ROTATION_Z;
            if (ry > 0) return BlockAnimationType.ROTATION_Y;
        }

        if (dx > dy && dx > dz) return BlockAnimationType.ROTATION_X;
        if (dz > dy && dz > dx) return BlockAnimationType.ROTATION_Z;
        return BlockAnimationType.ROTATION_Y;
    }

    private String sanitizeTexturePath(String raw) {
        if (raw == null) return "texture";
        String s = raw.toLowerCase(Locale.ROOT).trim();
        int slash = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        if (slash >= 0) s = s.substring(slash + 1);
        int dot = s.lastIndexOf('.');
        if (dot >= 0) s = s.substring(0, dot);
        s = s.replace(' ', '_').replaceAll("[^a-z0-9_.-]", "");
        return s.isEmpty() ? "texture" : s;
    }

    private String sanitizePartId(String raw) {
        if (raw == null) return "part";
        String s = raw.toLowerCase(Locale.ROOT).trim().replace(' ', '_').replaceAll("[^a-z0-9_]", "");
        return s.isEmpty() ? "part" : s;
    }

    public int generateNextCustomModelData() {
        int max = 20000;
        for (CustomBlockDefinition b : plugin.getCustomBlockRegistry().getAll()) {
            if (b.getCustomModelData() > max) {
                max = b.getCustomModelData();
            }
            for (BlockModelPart p : b.getModelParts()) {
                if (p.getCustomModelData() > max) {
                    max = p.getCustomModelData();
                }
            }
        }
        for (ItemDefinition item : plugin.getItemRegistry().getAll()) {
            if (item.getCustomModelData() != null && item.getCustomModelData() > max) {
                max = item.getCustomModelData();
            }
        }
        return max + 1;
    }

    public File getImportFolder() {
        return importFolder;
    }

    public File getItemsFolder() {
        return itemsFolder;
    }

    public File getBlocksFolder() {
        return blocksFolder;
    }

    public File getEntitiesFolder() {
        return entitiesFolder;
    }

    public File getBlockbenchFolder() {
        return blockbenchFolder;
    }

    public File getSourceModelsFolder() {
        return sourceModelsFolder;
    }
}
