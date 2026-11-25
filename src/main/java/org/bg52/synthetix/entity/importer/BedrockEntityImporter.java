package org.bg52.synthetix.entity.importer;

import com.google.gson.*;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.entity.ai.EntityAIType;
import org.bg52.synthetix.entity.animation.AnimationTrack;
import org.bg52.synthetix.entity.animation.Keyframe;
import org.bg52.synthetix.entity.model.*;
import org.bg52.synthetix.scheduler.PlatformScheduler;
import org.bg52.synthetix.item.component.ComponentHandler;
import org.bg52.synthetix.util.MaterialUtil;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.Level;

/**
 * High-performance, generalized Bedrock and Blockbench model/animation importer.
 * 
 * Capabilities:
 * - Parses Blockbench projects (.bbmodel) including outliner bone trees, elements/cubes,
 *   embedded Base64 textures, and keyframed skeletal animations.
 * - Parses Minecraft Bedrock Geometry (.geo.json, .json) format versions 1.8.0, 1.10.0, 1.12.0, 1.14.0+.
 * - Parses Minecraft Bedrock Animations (.animation.json) with rotation (Euler to Quaternion SLERP),
 *   translation (Bedrock 16-units to meters), scale, and timeline ticks.
 * - Automatically segments 3D meshes per bone, generating standard Java item models
 *   in assets/synthetix/models/entity/<entityId>/<bone>.json with box UV and face UV.
 * - Exports/normalizes textures into assets/synthetix/textures/entity/<entityId>/<name>.png.
 * - Builds and registers CustomEntityDefinition with skeletal hierarchy, relative pivots,
 *   automatic hitboxes, and coupled animation tracks.
 * - Strict generalization without hardcoded model names or part identifiers.
 */
public class BedrockEntityImporter {

    private final SynthetixPlugin plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public BedrockEntityImporter(SynthetixPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Discovers all available Bedrock/Blockbench model files across plugin model directories.
     * Does NOT create empty folders if they do not exist.
     */
    public List<File> listAvailableModelFiles() {
        List<File> list = new ArrayList<>();
        List<File> searchDirs = new ArrayList<>();

        File baseData = plugin.getDataFolder();
        searchDirs.add(new File(baseData, "models"));
        searchDirs.add(new File(baseData, "models/entity"));
        searchDirs.add(new File(baseData, "models/bedrock"));
        searchDirs.add(new File(baseData, "blockbench")); // backward compatibility if user created it

        for (File dir : searchDirs) {
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles((d, name) -> {
                    String lower = name.toLowerCase(Locale.ROOT);
                    return lower.endsWith(".bbmodel") || lower.endsWith(".geo.json") || lower.endsWith(".json");
                });
                if (files != null) {
                    for (File f : files) {
                        if (!list.contains(f)) {
                            list.add(f);
                        }
                    }
                }
            }
        }
        return list;
    }

    /**
     * Lists clean model base names available for import.
     */
    public List<String> listAvailableModelNames() {
        List<String> names = new ArrayList<>();
        for (File f : listAvailableModelFiles()) {
            String name = f.getName();
            int dot = name.indexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            if (!names.contains(base)) {
                names.add(base);
            }
        }
        return names;
    }

    /**
     * Finds a model file by query name across all valid search locations.
     */
    public File findModelFile(String query) {
        if (query == null || query.trim().isEmpty()) return null;
        String clean = query.trim().toLowerCase(Locale.ROOT);

        for (File f : listAvailableModelFiles()) {
            String name = f.getName().toLowerCase(Locale.ROOT);
            if (name.equals(clean) || name.equals(clean + ".bbmodel") || name.equals(clean + ".geo.json") || name.equals(clean + ".json")) {
                return f;
            }
            int dot = name.indexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            if (base.equals(clean)) {
                return f;
            }
        }

        // Direct file path fallback
        File direct = new File(query);
        if (direct.exists() && direct.isFile()) return direct;

        File inModels = new File(new File(plugin.getDataFolder(), "models"), query);
        if (inModels.exists()) return inModels;

        return null;
    }

    /**
     * Automatically imports a model file (.bbmodel, .geo.json, or .json) as a CustomEntityDefinition.
     */
    public CustomEntityDefinition importEntity(File modelFile, String customId) {
        if (modelFile == null || !modelFile.exists()) return null;

        String entityId = sanitizeId(customId != null && !customId.trim().isEmpty() ? customId : getBaseName(modelFile));

        try (FileReader reader = new FileReader(modelFile, StandardCharsets.UTF_8)) {
            JsonObject rootJson = JsonParser.parseReader(reader).getAsJsonObject();
            return importEntityFromJson(entityId, rootJson, modelFile.getParentFile(), modelFile.getName());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to import Bedrock entity model: " + modelFile.getName(), e);
            return null;
        }
    }

    /**
     * Full programmatic import overload allowing downstream plugins to import models from memory or streams.
     */
    public CustomEntityDefinition importEntityFromJson(String entityId, JsonObject rootJson, File parentDir, String sourceFileName) {
        if (entityId == null || rootJson == null) return null;
        String cleanId = sanitizeId(entityId);

        // Detect format: .bbmodel or Bedrock Geometry
        boolean isBBModel = rootJson.has("meta") && rootJson.has("elements");

        CustomEntityDefinition def = new CustomEntityDefinition(cleanId);
        def.setDisplayName("<gold><bold>" + TextUtil.prettify(cleanId) + "</bold></gold>");
        def.setBaseEntityType(detectBestBaseEntityType(cleanId));

        // Prepare destination directories in resource pack
        File rpModelsEntityDir = new File(plugin.getDataFolder(), "resources/assets/synthetix/models/entity/" + cleanId);
        if (!rpModelsEntityDir.exists()) rpModelsEntityDir.mkdirs();

        File rpTexturesEntityDir = new File(plugin.getDataFolder(), "resources/assets/synthetix/textures/entity/" + cleanId);
        if (!rpTexturesEntityDir.exists()) rpTexturesEntityDir.mkdirs();

        if (isBBModel) {
            parseBBModelProject(def, rootJson, rpModelsEntityDir, rpTexturesEntityDir, cleanId);
        } else {
            parseBedrockGeometryFile(def, rootJson, parentDir, rpModelsEntityDir, rpTexturesEntityDir, cleanId);
        }

        // Save definition to plugins/Synthetix/entities/<id>.yml and register
        plugin.getCustomEntityRegistry().save(def);

        // Asynchronously rebuild and redistribute resource pack
        PlatformScheduler.get().runAsync(() -> {
            try {
                plugin.getResourcePackManager().generatePackAsync();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to rebuild resource pack after entity import: " + cleanId, e);
            }
        });

        plugin.getLogger().info("Successfully imported custom Bedrock entity: " + cleanId
                + " (Bones: " + def.getBones().size() + ", Animations: " + def.getAnimations().size() + ")");
        return def;
    }

    // =========================================================================
    // BLOCKBENCH .BBMODEL PARSER
    // =========================================================================

    private void parseBBModelProject(CustomEntityDefinition def, JsonObject root, File modelsDir, File texturesDir, String entityId) {
        int texWidth = 64;
        int texHeight = 64;
        if (root.has("resolution") && root.get("resolution").isJsonObject()) {
            JsonObject res = root.getAsJsonObject("resolution");
            if (res.has("width")) texWidth = res.get("width").getAsInt();
            if (res.has("height")) texHeight = res.get("height").getAsInt();
        }

        // 1. Extract embedded Base64 textures
        Map<String, String> textureIdToName = new HashMap<>();
        String primaryTextureName = "texture";

        if (root.has("textures") && root.get("textures").isJsonArray()) {
            JsonArray texArr = root.getAsJsonArray("textures");
            for (int i = 0; i < texArr.size(); i++) {
                JsonObject texObj = texArr.get(i).getAsJsonObject();
                String rawName = texObj.has("name") ? texObj.get("name").getAsString() : "texture_" + i + ".png";
                String cleanName = sanitizePartId(rawName.replaceAll("\\.png$", ""));
                String texUuid = texObj.has("uuid") ? texObj.get("uuid").getAsString() : String.valueOf(i);
                textureIdToName.put(texUuid, cleanName);
                if (texObj.has("id")) {
                    textureIdToName.put(texObj.get("id").getAsString(), cleanName);
                }
                if (i == 0) primaryTextureName = cleanName;

                // Extract Base64 source data
                if (texObj.has("source")) {
                    String src = texObj.get("source").getAsString();
                    if (src.contains(",")) {
                        src = src.substring(src.indexOf(',') + 1);
                    }
                    try {
                        byte[] decoded = Base64.getDecoder().decode(src);
                        File pngFile = new File(texturesDir, cleanName + ".png");
                        Files.write(pngFile.toPath(), decoded);
                    } catch (Exception ignored) {}
                }
            }
        }

        // 2. Parse elements (cubes)
        Map<String, JsonObject> cubeMap = new HashMap<>();
        if (root.has("elements") && root.get("elements").isJsonArray()) {
            JsonArray elements = root.getAsJsonArray("elements");
            for (int i = 0; i < elements.size(); i++) {
                JsonObject el = elements.get(i).getAsJsonObject();
                String uuid = el.has("uuid") ? el.get("uuid").getAsString() : String.valueOf(i);
                cubeMap.put(uuid, el);
            }
        }

        // 3. Parse outliner bone tree
        Map<String, ParsedBone> parsedBones = new LinkedHashMap<>();
        if (root.has("outliner") && root.get("outliner").isJsonArray()) {
            JsonArray outliner = root.getAsJsonArray("outliner");
            parseBBModelOutlinerRecursive(outliner, null, new Vector3f(0, 0, 0), parsedBones, cubeMap);
        }

        // Fallback root bone if none found
        if (parsedBones.isEmpty()) {
            ParsedBone fallbackRoot = new ParsedBone("root", null, new Vector3f(0, 0, 0), new Vector3f(0, 0, 0));
            fallbackRoot.cubeElements.addAll(cubeMap.values());
            parsedBones.put("root", fallbackRoot);
        }

        // Determine primary root bone
        String rootBoneName = "root";
        for (ParsedBone b : parsedBones.values()) {
            if (b.parent == null) {
                rootBoneName = b.name;
                break;
            }
        }
        def.setRootBoneName(rootBoneName);
        def.getBones().clear();

        // 4. Generate Java Item Models & EntityBoneSpecs
        for (ParsedBone bone : parsedBones.values()) {
            String boneName = bone.name;
            boolean hasCubes = !bone.cubeElements.isEmpty();

            int cmd = hasCubes ? generateCustomModelData(entityId, boneName) : 0;
            String itemModelKey = hasCubes ? "synthetix:entity/" + entityId + "/" + boneName : null;

            EntityBoneSpec spec = new EntityBoneSpec(
                    boneName,
                    bone.parent,
                    new Vector3f(bone.relativePivot),
                    hasCubes ? Material.LEATHER_HORSE_ARMOR : Material.AIR,
                    cmd
            );
            spec.setItemModel(itemModelKey);

            // Auto-configure anatomical hitboxes
            if ("head".equalsIgnoreCase(boneName)) {
                EntityHitboxSpec hitbox = new EntityHitboxSpec(boneName + "_hitbox", boneName, 0.8f, 0.8f, 2.0);
                hitbox.setWeakpoint(true);
                spec.setHitbox(hitbox);
            } else if ("body".equalsIgnoreCase(boneName) || "torso".equalsIgnoreCase(boneName)) {
                spec.setHitbox(new EntityHitboxSpec(boneName + "_hitbox", boneName, 0.9f, 1.2f, 1.0));
            }

            def.addBone(spec);

            // Build Java item model JSON for bone
            if (hasCubes) {
                JsonObject modelJson = buildJavaModelJson(bone.cubeElements, bone.globalPivot, texWidth, texHeight, entityId, primaryTextureName, textureIdToName);
                File modelFile = new File(modelsDir, boneName + ".json");
                try (FileWriter writer = new FileWriter(modelFile, StandardCharsets.UTF_8)) {
                    gson.toJson(modelJson, writer);
                } catch (IOException e) {
                    plugin.getLogger().warning("Failed to write item model for bone: " + boneName);
                }
            }
        }

        // 5. Parse skeletal keyframed animations
        if (root.has("animations") && root.get("animations").isJsonArray()) {
            JsonArray anims = root.getAsJsonArray("animations");
            for (int i = 0; i < anims.size(); i++) {
                JsonObject animObj = anims.get(i).getAsJsonObject();
                AnimationTrack track = parseBBModelAnimation(animObj, parsedBones);
                if (track != null) {
                    def.addAnimation(track);
                }
            }
        }
    }

    private void parseBBModelOutlinerRecursive(JsonArray outliner, String parentName, Vector3f parentGlobalPivot,
                                               Map<String, ParsedBone> outBones, Map<String, JsonObject> cubeMap) {
        for (int i = 0; i < outliner.size(); i++) {
            JsonElement el = outliner.get(i);
            if (el.isJsonObject()) {
                JsonObject group = el.getAsJsonObject();
                String rawName = group.has("name") ? group.get("name").getAsString() : "bone_" + outBones.size();
                String boneName = sanitizePartId(rawName);

                Vector3f globalPivot = new Vector3f(0, 0, 0);
                if (group.has("origin") && group.get("origin").isJsonArray()) {
                    JsonArray orig = group.getAsJsonArray("origin");
                    globalPivot.set(
                            orig.get(0).getAsFloat(),
                            orig.get(1).getAsFloat(),
                            orig.get(2).getAsFloat()
                    );
                }

                // Compute relative pivot in meters (Bedrock units: 16 = 1 block)
                Vector3f relPivot;
                if (parentName == null) {
                    relPivot = new Vector3f(globalPivot.x / 16.0f, globalPivot.y / 16.0f, globalPivot.z / 16.0f);
                } else {
                    relPivot = new Vector3f(
                            (globalPivot.x - parentGlobalPivot.x) / 16.0f,
                            (globalPivot.y - parentGlobalPivot.y) / 16.0f,
                            (globalPivot.z - parentGlobalPivot.z) / 16.0f
                    );
                }

                ParsedBone bone = new ParsedBone(boneName, parentName, relPivot, globalPivot);

                // Collect child cubes and child groups
                if (group.has("children") && group.get("children").isJsonArray()) {
                    JsonArray children = group.getAsJsonArray("children");
                    JsonArray childGroups = new JsonArray();

                    for (int c = 0; c < children.size(); c++) {
                        JsonElement childEl = children.get(c);
                        if (childEl.isJsonPrimitive()) {
                            // Cube UUID reference
                            String cubeUuid = childEl.getAsString();
                            JsonObject cube = cubeMap.get(cubeUuid);
                            if (cube != null) {
                                bone.cubeElements.add(cube);
                            }
                        } else if (childEl.isJsonObject()) {
                            childGroups.add(childEl);
                        }
                    }

                    outBones.put(boneName, bone);
                    // Recurse into nested child bones
                    parseBBModelOutlinerRecursive(childGroups, boneName, globalPivot, outBones, cubeMap);
                } else {
                    outBones.put(boneName, bone);
                }
            } else if (el.isJsonPrimitive() && parentName != null) {
                // Top-level or loose cube attached to parent
                JsonObject cube = cubeMap.get(el.getAsString());
                if (cube != null && outBones.containsKey(parentName)) {
                    outBones.get(parentName).cubeElements.add(cube);
                }
            }
        }
    }

    private AnimationTrack parseBBModelAnimation(JsonObject animObj, Map<String, ParsedBone> bones) {
        String rawName = animObj.has("name") ? animObj.get("name").getAsString() : "idle";
        String cleanName = cleanAnimationTrackName(rawName);

        float lengthSeconds = animObj.has("length") ? animObj.get("length").getAsFloat() : 1.0f;
        float durationTicks = Math.max(1.0f, lengthSeconds * 20.0f);

        boolean loop = true;
        if (animObj.has("loop")) {
            String loopStr = animObj.get("loop").getAsString().toLowerCase(Locale.ROOT);
            loop = "loop".equals(loopStr) || "true".equals(loopStr);
        }

        AnimationTrack track = new AnimationTrack(cleanName, durationTicks, loop);

        if (animObj.has("animators") && animObj.get("animators").isJsonObject()) {
            JsonObject animators = animObj.getAsJsonObject("animators");

            for (String animKey : animators.keySet()) {
                JsonObject boneAnimator = animators.getAsJsonObject(animKey);
                String boneName = boneAnimator.has("name") ? sanitizePartId(boneAnimator.get("name").getAsString()) : animKey;

                if (!boneAnimator.has("keyframes") || !boneAnimator.get("keyframes").isJsonArray()) continue;

                JsonArray kfArray = boneAnimator.getAsJsonArray("keyframes");
                Map<Float, KeyframeBuilder> builders = new TreeMap<>();

                for (int k = 0; k < kfArray.size(); k++) {
                    JsonObject kf = kfArray.get(k).getAsJsonObject();
                    String channel = kf.has("channel") ? kf.get("channel").getAsString().toLowerCase(Locale.ROOT) : "rotation";
                    float timeSec = kf.has("time") ? kf.get("time").getAsFloat() : 0.0f;
                    float tick = timeSec * 20.0f;

                    Keyframe.InterpolationType interp = Keyframe.InterpolationType.LINEAR;
                    if (kf.has("interpolation")) {
                        String interStr = kf.get("interpolation").getAsString().toLowerCase(Locale.ROOT);
                        if (interStr.contains("step")) interp = Keyframe.InterpolationType.STEP;
                        else if (interStr.contains("catmull") || interStr.contains("smooth") || interStr.contains("bezier")) {
                            interp = Keyframe.InterpolationType.EASE_IN_OUT;
                        }
                    }

                    Vector3f vec = parseDataPointVector(kf);
                    final Keyframe.InterpolationType finalInterp = interp;
                    KeyframeBuilder b = builders.computeIfAbsent(tick, t -> new KeyframeBuilder(t, finalInterp));

                    if ("rotation".equals(channel)) {
                        b.setRotationDegrees(vec);
                    } else if ("position".equals(channel)) {
                        // Bedrock 16 units = 1 meter
                        b.setTranslation(new Vector3f(vec.x / 16.0f, vec.y / 16.0f, vec.z / 16.0f));
                    } else if ("scale".equals(channel)) {
                        b.setScale(vec);
                    }
                }

                for (KeyframeBuilder b : builders.values()) {
                    track.addKeyframe(boneName, b.build());
                }
            }
        }

        return track;
    }

    // =========================================================================
    // BEDROCK GEOMETRY JSON PARSER (.geo.json / .json)
    // =========================================================================

    private void parseBedrockGeometryFile(CustomEntityDefinition def, JsonObject root, File parentDir,
                                          File modelsDir, File texturesDir, String entityId) {
        JsonObject geometry = findBedrockGeometryObject(root);
        if (geometry == null) {
            plugin.getLogger().warning("No valid minecraft:geometry or geometry.* found in model JSON.");
            return;
        }

        int texWidth = 64;
        int texHeight = 64;
        if (geometry.has("description") && geometry.get("description").isJsonObject()) {
            JsonObject desc = geometry.getAsJsonObject("description");
            if (desc.has("texture_width")) texWidth = desc.get("texture_width").getAsInt();
            if (desc.has("texture_height")) texHeight = desc.get("texture_height").getAsInt();
        } else if (geometry.has("texturewidth")) {
            texWidth = geometry.get("texturewidth").getAsInt();
            if (geometry.has("textureheight")) texHeight = geometry.get("textureheight").getAsInt();
        }

        // Locate and copy external PNG textures from parent directory
        String primaryTextureName = "texture";
        Map<String, String> textureMap = new HashMap<>();
        if (parentDir != null && parentDir.exists()) {
            File[] pngs = parentDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".png"));
            if (pngs != null && pngs.length > 0) {
                for (File png : pngs) {
                    try {
                        String cleanTex = sanitizePartId(png.getName().replaceAll("\\.png$", ""));
                        Files.copy(png.toPath(), new File(texturesDir, cleanTex + ".png").toPath(), StandardCopyOption.REPLACE_EXISTING);
                        textureMap.put(cleanTex, cleanTex);
                        primaryTextureName = cleanTex;
                    } catch (Exception ignored) {}
                }
            }
        }

        // Parse bones list
        Map<String, ParsedBone> parsedBones = new LinkedHashMap<>();
        if (geometry.has("bones") && geometry.get("bones").isJsonArray()) {
            JsonArray bonesArr = geometry.getAsJsonArray("bones");
            Map<String, Vector3f> boneGlobalPivots = new HashMap<>();

            for (int i = 0; i < bonesArr.size(); i++) {
                JsonObject bObj = bonesArr.get(i).getAsJsonObject();
                String rawName = bObj.has("name") ? bObj.get("name").getAsString() : "bone_" + i;
                String boneName = sanitizePartId(rawName);
                String parentName = bObj.has("parent") ? sanitizePartId(bObj.get("parent").getAsString()) : null;

                Vector3f globalPivot = new Vector3f(0, 0, 0);
                if (bObj.has("pivot") && bObj.get("pivot").isJsonArray()) {
                    JsonArray pArr = bObj.getAsJsonArray("pivot");
                    globalPivot.set(pArr.get(0).getAsFloat(), pArr.get(1).getAsFloat(), pArr.get(2).getAsFloat());
                }
                boneGlobalPivots.put(boneName, globalPivot);

                Vector3f parentGlobal = parentName != null ? boneGlobalPivots.getOrDefault(parentName, new Vector3f(0, 0, 0)) : new Vector3f(0, 0, 0);
                Vector3f relPivot;
                if (parentName == null) {
                    relPivot = new Vector3f(globalPivot.x / 16.0f, globalPivot.y / 16.0f, globalPivot.z / 16.0f);
                } else {
                    relPivot = new Vector3f(
                            (globalPivot.x - parentGlobal.x) / 16.0f,
                            (globalPivot.y - parentGlobal.y) / 16.0f,
                            (globalPivot.z - parentGlobal.z) / 16.0f
                    );
                }

                ParsedBone bone = new ParsedBone(boneName, parentName, relPivot, globalPivot);

                // Collect Bedrock cubes
                if (bObj.has("cubes") && bObj.get("cubes").isJsonArray()) {
                    JsonArray cubes = bObj.getAsJsonArray("cubes");
                    for (int c = 0; c < cubes.size(); c++) {
                        bone.cubeElements.add(cubes.get(c).getAsJsonObject());
                    }
                }
                parsedBones.put(boneName, bone);
            }
        }

        // Determine root bone
        String rootBoneName = "root";
        for (ParsedBone b : parsedBones.values()) {
            if (b.parent == null) {
                rootBoneName = b.name;
                break;
            }
        }
        def.setRootBoneName(rootBoneName);
        def.getBones().clear();

        // Generate Java Item Models & EntityBoneSpecs
        for (ParsedBone bone : parsedBones.values()) {
            String boneName = bone.name;
            boolean hasCubes = !bone.cubeElements.isEmpty();

            int cmd = hasCubes ? generateCustomModelData(entityId, boneName) : 0;
            String itemModelKey = hasCubes ? "synthetix:entity/" + entityId + "/" + boneName : null;

            EntityBoneSpec spec = new EntityBoneSpec(
                    boneName,
                    bone.parent,
                    new Vector3f(bone.relativePivot),
                    hasCubes ? Material.LEATHER_HORSE_ARMOR : Material.AIR,
                    cmd
            );
            spec.setItemModel(itemModelKey);

            if ("head".equalsIgnoreCase(boneName)) {
                EntityHitboxSpec hitbox = new EntityHitboxSpec(boneName + "_hitbox", boneName, 0.8f, 0.8f, 2.0);
                hitbox.setWeakpoint(true);
                spec.setHitbox(hitbox);
            } else if ("body".equalsIgnoreCase(boneName) || "torso".equalsIgnoreCase(boneName)) {
                spec.setHitbox(new EntityHitboxSpec(boneName + "_hitbox", boneName, 0.9f, 1.2f, 1.0));
            }

            def.addBone(spec);

            if (hasCubes) {
                JsonObject modelJson = buildJavaModelJson(bone.cubeElements, bone.globalPivot, texWidth, texHeight, entityId, primaryTextureName, textureMap);
                File modelFile = new File(modelsDir, boneName + ".json");
                try (FileWriter writer = new FileWriter(modelFile, StandardCharsets.UTF_8)) {
                    gson.toJson(modelJson, writer);
                } catch (IOException e) {
                    plugin.getLogger().warning("Failed to write item model for bone: " + boneName);
                }
            }
        }

        // Scan for accompanying Bedrock animation files (*.animation.json)
        if (parentDir != null && parentDir.exists()) {
            File[] animFiles = parentDir.listFiles((dir, name) -> {
                String l = name.toLowerCase(Locale.ROOT);
                return l.endsWith(".animation.json") || l.endsWith(".anim.json");
            });
            if (animFiles != null) {
                for (File af : animFiles) {
                    try (FileReader aReader = new FileReader(af, StandardCharsets.UTF_8)) {
                        JsonObject aRoot = JsonParser.parseReader(aReader).getAsJsonObject();
                        parseBedrockAnimationsJson(def, aRoot, parsedBones);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Could not parse animation file: " + af.getName());
                    }
                }
            }
        }
    }

    private JsonObject findBedrockGeometryObject(JsonObject root) {
        if (root.has("minecraft:geometry") && root.get("minecraft:geometry").isJsonArray()) {
            JsonArray arr = root.getAsJsonArray("minecraft:geometry");
            if (arr.size() > 0) return arr.get(0).getAsJsonObject();
        }
        for (String key : root.keySet()) {
            if (key.startsWith("geometry.") && root.get(key).isJsonObject()) {
                return root.getAsJsonObject(key);
            }
        }
        return null;
    }

    private void parseBedrockAnimationsJson(CustomEntityDefinition def, JsonObject root, Map<String, ParsedBone> bones) {
        if (!root.has("animations") || !root.get("animations").isJsonObject()) return;
        JsonObject anims = root.getAsJsonObject("animations");

        for (String animKey : anims.keySet()) {
            JsonObject aObj = anims.getAsJsonObject(animKey);
            String cleanName = cleanAnimationTrackName(animKey);

            float lengthSeconds = aObj.has("animation_length") ? aObj.get("animation_length").getAsFloat() : 1.0f;
            float durationTicks = Math.max(1.0f, lengthSeconds * 20.0f);

            boolean loop = true;
            if (aObj.has("loop")) {
                JsonElement lEl = aObj.get("loop");
                if (lEl.isJsonPrimitive()) {
                    String s = lEl.getAsString().toLowerCase(Locale.ROOT);
                    loop = "true".equals(s) || "loop".equals(s);
                }
            }

            AnimationTrack track = new AnimationTrack(cleanName, durationTicks, loop);

            if (aObj.has("bones") && aObj.get("bones").isJsonObject()) {
                JsonObject boneMap = aObj.getAsJsonObject("bones");
                for (String bKey : boneMap.keySet()) {
                    String boneName = sanitizePartId(bKey);
                    JsonObject bData = boneMap.getAsJsonObject(bKey);

                    Map<Float, KeyframeBuilder> builders = new TreeMap<>();
                    parseBedrockChannel(bData, "rotation", builders, true);
                    parseBedrockChannel(bData, "position", builders, false);
                    parseBedrockScaleChannel(bData, builders);

                    for (KeyframeBuilder kb : builders.values()) {
                        track.addKeyframe(boneName, kb.build());
                    }
                }
            }
            def.addAnimation(track);
        }
    }

    private void parseBedrockChannel(JsonObject boneData, String channelName, Map<Float, KeyframeBuilder> builders, boolean isRotation) {
        if (!boneData.has(channelName)) return;
        JsonElement el = boneData.get(channelName);

        if (el.isJsonArray()) {
            // Static single vector: [x, y, z]
            Vector3f v = parseVector3f(el.getAsJsonArray());
            KeyframeBuilder kb = builders.computeIfAbsent(0.0f, t -> new KeyframeBuilder(t, Keyframe.InterpolationType.LINEAR));
            if (isRotation) kb.setRotationDegrees(v);
            else kb.setTranslation(new Vector3f(v.x / 16.0f, v.y / 16.0f, v.z / 16.0f));
            return;
        }

        if (el.isJsonObject()) {
            JsonObject map = el.getAsJsonObject();
            for (String timeStr : map.keySet()) {
                try {
                    float timeSec = Float.parseFloat(timeStr);
                    float tick = timeSec * 20.0f;
                    JsonElement val = map.get(timeStr);

                    Vector3f v = new Vector3f(0, 0, 0);
                    Keyframe.InterpolationType interp = Keyframe.InterpolationType.LINEAR;

                    if (val.isJsonArray()) {
                        v = parseVector3f(val.getAsJsonArray());
                    } else if (val.isJsonObject()) {
                        JsonObject obj = val.getAsJsonObject();
                        if (obj.has("post") && obj.get("post").isJsonArray()) {
                            v = parseVector3f(obj.getAsJsonArray("post"));
                        } else if (obj.has("pre") && obj.get("pre").isJsonArray()) {
                            v = parseVector3f(obj.getAsJsonArray("pre"));
                        }
                        if (obj.has("lerp_mode")) {
                            String m = obj.get("lerp_mode").getAsString().toLowerCase(Locale.ROOT);
                            if (m.contains("catmull") || m.contains("smooth") || m.contains("bezier")) {
                                interp = Keyframe.InterpolationType.EASE_IN_OUT;
                            } else if (m.contains("step")) {
                                interp = Keyframe.InterpolationType.STEP;
                            }
                        }
                    }

                    final Keyframe.InterpolationType finalInterp = interp;
                    KeyframeBuilder kb = builders.computeIfAbsent(tick, t -> new KeyframeBuilder(t, finalInterp));
                    if (isRotation) kb.setRotationDegrees(v);
                    else kb.setTranslation(new Vector3f(v.x / 16.0f, v.y / 16.0f, v.z / 16.0f));
                } catch (Exception ignored) {}
            }
        }
    }

    private void parseBedrockScaleChannel(JsonObject boneData, Map<Float, KeyframeBuilder> builders) {
        if (!boneData.has("scale")) return;
        JsonElement el = boneData.get("scale");

        if (el.isJsonArray()) {
            Vector3f v = parseVector3f(el.getAsJsonArray());
            KeyframeBuilder kb = builders.computeIfAbsent(0.0f, t -> new KeyframeBuilder(t, Keyframe.InterpolationType.LINEAR));
            kb.setScale(v);
            return;
        }

        if (el.isJsonObject()) {
            JsonObject map = el.getAsJsonObject();
            for (String timeStr : map.keySet()) {
                try {
                    float timeSec = Float.parseFloat(timeStr);
                    float tick = timeSec * 20.0f;
                    JsonElement val = map.get(timeStr);
                    Vector3f v = val.isJsonArray() ? parseVector3f(val.getAsJsonArray()) : new Vector3f(1, 1, 1);
                    KeyframeBuilder kb = builders.computeIfAbsent(tick, t -> new KeyframeBuilder(t, Keyframe.InterpolationType.LINEAR));
                    kb.setScale(v);
                } catch (Exception ignored) {}
            }
        }
    }

    // =========================================================================
    // JAVA 1.20.6 / 1.21+ ITEM MODEL GENERATOR
    // =========================================================================

    private JsonObject buildJavaModelJson(List<JsonObject> cubes, Vector3f bonePivot, int texWidth, int texHeight,
                                          String entityId, String defaultTexName, Map<String, String> textureMap) {
        JsonObject root = new JsonObject();

        // Textures section
        JsonObject textures = new JsonObject();
        String primaryTexKey = "synthetix:entity/" + entityId + "/" + defaultTexName;
        textures.addProperty("0", primaryTexKey);
        textures.addProperty("particle", primaryTexKey);
        root.add("textures", textures);

        JsonArray elements = new JsonArray();

        for (JsonObject cube : cubes) {
            JsonObject el = new JsonObject();

            Vector3f from = new Vector3f();
            Vector3f to = new Vector3f();

            if (cube.has("from") && cube.has("to")) {
                // Blockbench format
                from = parseVector3f(cube.getAsJsonArray("from"));
                to = parseVector3f(cube.getAsJsonArray("to"));
            } else if (cube.has("origin") && cube.has("size")) {
                // Bedrock geometry format
                from = parseVector3f(cube.getAsJsonArray("origin"));
                Vector3f size = parseVector3f(cube.getAsJsonArray("size"));
                to = new Vector3f(from.x + size.x, from.y + size.y, from.z + size.z);
            }

            // Inflation handling (e.g. outer armor/clothing layers)
            float inflate = cube.has("inflate") ? cube.get("inflate").getAsFloat() : 0.0f;
            if (inflate != 0.0f) {
                from.sub(inflate, inflate, inflate);
                to.add(inflate, inflate, inflate);
            }

            // Re-center mesh relative to bone pivot: P_java = P_bedrock - bonePivot + 8.0
            JsonArray fromArr = new JsonArray();
            fromArr.add(from.x - bonePivot.x + 8.0f);
            fromArr.add(from.y - bonePivot.y + 8.0f);
            fromArr.add(from.z - bonePivot.z + 8.0f);
            el.add("from", fromArr);

            JsonArray toArr = new JsonArray();
            toArr.add(to.x - bonePivot.x + 8.0f);
            toArr.add(to.y - bonePivot.y + 8.0f);
            toArr.add(to.z - bonePivot.z + 8.0f);
            el.add("to", toArr);

            // Cube-level rotation (if specified)
            if (cube.has("rotation") && cube.has("origin")) {
                Vector3f cubeRot = parseVector3f(cube.getAsJsonArray("rotation"));
                Vector3f cubeOrigin = parseVector3f(cube.getAsJsonArray("origin"));

                if (cubeRot.lengthSquared() > 0.001f) {
                    JsonObject rotObj = new JsonObject();
                    JsonArray rotOrigArr = new JsonArray();
                    rotOrigArr.add(cubeOrigin.x - bonePivot.x + 8.0f);
                    rotOrigArr.add(cubeOrigin.y - bonePivot.y + 8.0f);
                    rotOrigArr.add(cubeOrigin.z - bonePivot.z + 8.0f);
                    rotObj.add("origin", rotOrigArr);

                    if (Math.abs(cubeRot.x) >= Math.abs(cubeRot.y) && Math.abs(cubeRot.x) >= Math.abs(cubeRot.z)) {
                        rotObj.addProperty("axis", "x");
                        rotObj.addProperty("angle", clampJavaAngle(cubeRot.x));
                    } else if (Math.abs(cubeRot.y) >= Math.abs(cubeRot.z)) {
                        rotObj.addProperty("axis", "y");
                        rotObj.addProperty("angle", clampJavaAngle(cubeRot.y));
                    } else {
                        rotObj.addProperty("axis", "z");
                        rotObj.addProperty("angle", clampJavaAngle(cubeRot.z));
                    }
                    el.add("rotation", rotObj);
                }
            }

            // Faces & UV unwrapping
            JsonObject faces = buildFacesJson(cube, from, to, texWidth, texHeight);
            el.add("faces", faces);

            elements.add(el);
        }

        root.add("elements", elements);
        return root;
    }

    private JsonObject buildFacesJson(JsonObject cube, Vector3f from, Vector3f to, int texWidth, int texHeight) {
        JsonObject faces = new JsonObject();
        float w = Math.abs(to.x - from.x);
        float h = Math.abs(to.y - from.y);
        float d = Math.abs(to.z - from.z);

        if (cube.has("faces") && cube.get("faces").isJsonObject()) {
            // Per-face UV format
            JsonObject inFaces = cube.getAsJsonObject("faces");
            for (String dir : List.of("north", "south", "east", "west", "up", "down")) {
                if (inFaces.has(dir) && inFaces.get(dir).isJsonObject()) {
                    JsonObject inF = inFaces.getAsJsonObject(dir);
                    JsonObject outF = new JsonObject();
                    outF.addProperty("texture", "#0");

                    if (inF.has("uv") && inF.get("uv").isJsonArray()) {
                        JsonArray uvArr = inF.getAsJsonArray("uv");
                        JsonArray normUv = new JsonArray();
                        for (int k = 0; k < uvArr.size(); k++) {
                            float v = uvArr.get(k).getAsFloat();
                            float scale = (k % 2 == 0) ? (16.0f / texWidth) : (16.0f / texHeight);
                            normUv.add(v * scale);
                        }
                        outF.add("uv", normUv);
                    }
                    faces.add(dir, outF);
                }
            }
        } else {
            // Standard Minecraft box UV unwrapping
            float u = 0.0f;
            float v = 0.0f;
            if (cube.has("uv") && cube.get("uv").isJsonArray()) {
                JsonArray uva = cube.getAsJsonArray("uv");
                u = uva.get(0).getAsFloat();
                v = uva.get(1).getAsFloat();
            } else if (cube.has("uv_offset") && cube.get("uv_offset").isJsonArray()) {
                JsonArray uva = cube.getAsJsonArray("uv_offset");
                u = uva.get(0).getAsFloat();
                v = uva.get(1).getAsFloat();
            }

            faces.add("up", makeFace(u + d, v, u + d + w, v + d, texWidth, texHeight));
            faces.add("down", makeFace(u + d + w, v, u + d + w + w, v + d, texWidth, texHeight));
            faces.add("east", makeFace(u, v + d, u + d, v + d + h, texWidth, texHeight));
            faces.add("north", makeFace(u + d, v + d, u + d + w, v + d + h, texWidth, texHeight));
            faces.add("west", makeFace(u + d + w, v + d, u + d + w + d, v + d + h, texWidth, texHeight));
            faces.add("south", makeFace(u + d + w + d, v + d, u + d + w + d + w, v + d + h, texWidth, texHeight));
        }

        return faces;
    }

    private JsonObject makeFace(float u1, float v1, float u2, float v2, int tw, int th) {
        JsonObject f = new JsonObject();
        f.addProperty("texture", "#0");
        JsonArray uv = new JsonArray();
        uv.add(u1 / tw * 16.0f);
        uv.add(v1 / th * 16.0f);
        uv.add(u2 / tw * 16.0f);
        uv.add(v2 / th * 16.0f);
        f.add("uv", uv);
        return f;
    }

    private float clampJavaAngle(float deg) {
        float[] valid = {-45.0f, -22.5f, 0.0f, 22.5f, 45.0f};
        float best = 0.0f;
        float minDiff = Float.MAX_VALUE;
        for (float v : valid) {
            float diff = Math.abs(deg - v);
            if (diff < minDiff) {
                minDiff = diff;
                best = v;
            }
        }
        return best;
    }

    // =========================================================================
    // UTILITIES & HELPERS
    // =========================================================================

    private Vector3f parseVector3f(JsonArray arr) {
        if (arr == null || arr.size() < 3) return new Vector3f(0, 0, 0);
        return new Vector3f(arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat());
    }

    private Vector3f parseDataPointVector(JsonObject kf) {
        if (kf.has("data_points") && kf.get("data_points").isJsonArray()) {
            JsonArray dp = kf.getAsJsonArray("data_points");
            if (dp.size() > 0 && dp.get(0).isJsonObject()) {
                JsonObject p = dp.get(0).getAsJsonObject();
                float x = p.has("x") ? parseJsonNum(p.get("x")) : 0.0f;
                float y = p.has("y") ? parseJsonNum(p.get("y")) : 0.0f;
                float z = p.has("z") ? parseJsonNum(p.get("z")) : 0.0f;
                return new Vector3f(x, y, z);
            }
        }
        return new Vector3f(0, 0, 0);
    }

    private float parseJsonNum(JsonElement el) {
        try {
            return el.getAsFloat();
        } catch (Exception e) {
            try {
                return Float.parseFloat(el.getAsString().trim());
            } catch (Exception ignored) {
                return 0.0f;
            }
        }
    }

    private String sanitizeId(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "custom_entity";
        return raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }

    /**
     * Imports all models dropped into plugins/Synthetix/import/entities/.
     */
    public int importPendingEntities(boolean deleteSource) {
        File entitiesFolder = new File(plugin.getDataFolder(), "import/entities");
        if (!entitiesFolder.exists() || !entitiesFolder.isDirectory()) return 0;

        File[] files = entitiesFolder.listFiles((dir, name) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            return lower.endsWith(".json") || lower.endsWith(".geo.json") || lower.endsWith(".bbmodel");
        });
        if (files == null || files.length == 0) return 0;

        int count = 0;
        for (File f : files) {
            try {
                String name = f.getName();
                int dot = name.indexOf('.');
                String baseId = dot > 0 ? name.substring(0, dot) : name;
                CustomEntityDefinition def = importEntity(f, baseId);
                if (def != null) {
                    plugin.getCustomEntityRegistry().save(def);
                    count++;
                    if (deleteSource) {
                        f.delete();
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to auto-import entity model: " + f.getName(), e);
            }
        }
        return count;
    }

    private String sanitizePartId(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "bone";
        return raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }

    private String getBaseName(File f) {
        String n = f.getName();
        int d = n.indexOf('.');
        return d > 0 ? n.substring(0, d) : n;
    }

    private String cleanAnimationTrackName(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "idle";
        String s = raw.toLowerCase(Locale.ROOT);
        if (s.startsWith("animation.")) {
            // Strip animation.<entity_name>. prefix
            int secondDot = s.indexOf('.', "animation.".length());
            if (secondDot > 0 && secondDot < s.length() - 1) {
                s = s.substring(secondDot + 1);
            } else {
                s = s.substring("animation.".length());
            }
        }
        return s.replaceAll("[^a-z0-9_]", "_");
    }

    private EntityType detectBestBaseEntityType(String id) {
        String lower = id.toLowerCase(Locale.ROOT);
        if (lower.contains("golem")) return EntityType.IRON_GOLEM;
        if (lower.contains("dragon")) return EntityType.ENDER_DRAGON;
        if (lower.contains("spider")) return EntityType.SPIDER;
        if (lower.contains("wolf") || lower.contains("dog")) return EntityType.WOLF;
        if (lower.contains("slime")) return EntityType.SLIME;
        if (lower.contains("creeper")) return EntityType.CREEPER;
        if (lower.contains("skeleton")) return EntityType.SKELETON;
        return EntityType.ZOMBIE;
    }

    private int generateCustomModelData(String entityId, String boneName) {
        int hash = Math.abs((entityId + ":" + boneName).hashCode());
        return 700000 + (hash % 200000);
    }

    // Temporary record for hierarchical bone assembly
    private static class ParsedBone {
        final String name;
        final String parent;
        final Vector3f relativePivot;
        final Vector3f globalPivot;
        final List<JsonObject> cubeElements = new ArrayList<>();

        ParsedBone(String name, String parent, Vector3f relativePivot, Vector3f globalPivot) {
            this.name = name;
            this.parent = parent;
            this.relativePivot = relativePivot;
            this.globalPivot = globalPivot;
        }
    }

    private static class KeyframeBuilder {
        final float tick;
        final Keyframe.InterpolationType interpolation;
        final BoneTransform transform = new BoneTransform();

        KeyframeBuilder(float tick, Keyframe.InterpolationType interpolation) {
            this.tick = tick;
            this.interpolation = interpolation != null ? interpolation : Keyframe.InterpolationType.LINEAR;
        }

        void setRotationDegrees(Vector3f deg) {
            // Bedrock rotation Euler to Quaternion
            Quaternionf q = new Quaternionf()
                    .rotateZ((float) Math.toRadians(deg.z))
                    .rotateY((float) Math.toRadians(deg.y))
                    .rotateX((float) Math.toRadians(deg.x));
            this.transform.rotation.set(q);
        }

        void setTranslation(Vector3f trans) {
            this.transform.translation.set(trans);
        }

        void setScale(Vector3f s) {
            this.transform.scale.set(s);
        }

        Keyframe build() {
            return new Keyframe(tick, transform.clone(), interpolation);
        }
    }
}
