package org.bg52.synthetix.block;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.joml.Vector3f;

import java.util.Locale;

/**
 * Definition of an individual 3D model part within a multi-model or animated custom block.
 * Allows composite blocks where some elements are static while others continuously spin, bob, or stroke.
 */
public class BlockModelPart {

    private String id;
    private Material material;
    private int customModelData;
    private String itemModel;
    private Vector3f scale;
    private Vector3f translation;
    private Vector3f rotation; // Degrees: pitch (X), yaw (Y), roll (Z)
    private boolean animated;
    private BlockAnimationType animationType;
    private double animationSpeed;
    private int interpolationDuration;
    private String colorHex;
    private java.util.List<Integer> elementIndices = new java.util.ArrayList<>();
    private boolean lockRotationAxis = false;

    public BlockModelPart(String id) {
        this.id = id != null ? id.toLowerCase(Locale.ROOT) : "part";
        this.material = Material.PAPER;
        this.customModelData = 0;
        this.scale = new Vector3f(1.0f, 1.0f, 1.0f);
        this.translation = new Vector3f(0.0f, 0.0f, 0.0f);
        this.rotation = new Vector3f(0.0f, 0.0f, 0.0f);
        this.animated = false;
        this.animationType = BlockAnimationType.STATIC;
        this.animationSpeed = 1.0;
        this.interpolationDuration = 10;
        this.colorHex = null;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id != null ? id.toLowerCase(Locale.ROOT) : "part";
    }

    public Material getMaterial() {
        return material;
    }

    public void setMaterial(Material material) {
        this.material = material != null ? material : Material.PAPER;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public void setCustomModelData(int customModelData) {
        this.customModelData = customModelData;
    }

    public String getItemModel() {
        return itemModel != null && !itemModel.trim().isEmpty() ? itemModel : "synthetix:" + id;
    }

    public void setItemModel(String itemModel) {
        if (itemModel != null && !itemModel.trim().isEmpty()) {
            this.itemModel = normalizeModelKey(itemModel);
        } else {
            this.itemModel = null;
        }
    }

    /**
     * Generalized helper to normalize item model keys for modern Minecraft 1.21.2+.
     * Strips leading path folders (block/, blocks/, item/, items/, model/, models/)
     * ensuring the key references assets/<namespace>/items/<id>.json directly.
     */
    public static String normalizeModelKey(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String clean = raw.trim().toLowerCase(Locale.ROOT);
        String namespace = "synthetix";
        String path = clean;
        if (clean.contains(":")) {
            String[] parts = clean.split(":", 2);
            namespace = parts[0];
            path = parts[1];
        }
        while (path.startsWith("block/") || path.startsWith("blocks/") ||
               path.startsWith("item/") || path.startsWith("items/") ||
               path.startsWith("model/") || path.startsWith("models/")) {
            int slash = path.indexOf('/');
            path = path.substring(slash + 1);
        }
        return namespace + ":" + path;
    }

    public java.util.List<Integer> getElementIndices() {
        return elementIndices;
    }

    public void setElementIndices(java.util.List<Integer> elementIndices) {
        this.elementIndices = elementIndices != null ? elementIndices : new java.util.ArrayList<>();
    }

    public Vector3f getScale() {
        return scale;
    }

    public void setScale(Vector3f scale) {
        this.scale = scale != null ? scale : new Vector3f(1.1f, 1.1f, 1.1f);
    }

    public Vector3f getTranslation() {
        return translation;
    }

    public void setTranslation(Vector3f translation) {
        this.translation = translation != null ? translation : new Vector3f(0.0f, 0.0f, 0.0f);
    }

    public Vector3f getRotation() {
        return rotation;
    }

    public void setRotation(Vector3f rotation) {
        this.rotation = rotation != null ? rotation : new Vector3f(0.0f, 0.0f, 0.0f);
    }

    public boolean isAnimated() {
        return animated;
    }

    public void setAnimated(boolean animated) {
        this.animated = animated;
    }

    public BlockAnimationType getAnimationType() {
        return animationType;
    }

    public void setAnimationType(BlockAnimationType animationType) {
        this.animationType = animationType != null ? animationType : BlockAnimationType.STATIC;
    }

    public double getAnimationSpeed() {
        return animationSpeed;
    }

    public void setAnimationSpeed(double animationSpeed) {
        this.animationSpeed = animationSpeed;
    }

    public int getInterpolationDuration() {
        return interpolationDuration;
    }

    public void setInterpolationDuration(int interpolationDuration) {
        this.interpolationDuration = Math.max(1, interpolationDuration);
    }

    public String getColorHex() {
        return colorHex;
    }

    public void setColorHex(String colorHex) {
        this.colorHex = colorHex;
    }

    public boolean isLockRotationAxis() {
        return lockRotationAxis;
    }

    public void setLockRotationAxis(boolean lockRotationAxis) {
        this.lockRotationAxis = lockRotationAxis;
    }

    public void saveToConfig(ConfigurationSection sec) {
        sec.set("id", id);
        sec.set("material", material.name());
        sec.set("custom-model-data", customModelData);
        if (itemModel != null) sec.set("item-model", itemModel);
        sec.set("scale.x", scale.x);
        sec.set("scale.y", scale.y);
        sec.set("scale.z", scale.z);
        sec.set("translation.x", translation.x);
        sec.set("translation.y", translation.y);
        sec.set("translation.z", translation.z);
        sec.set("rotation.pitch", rotation.x);
        sec.set("rotation.yaw", rotation.y);
        sec.set("rotation.roll", rotation.z);
        sec.set("animated", animated);
        sec.set("animation-type", animationType.name());
        sec.set("animation-speed", animationSpeed);
        sec.set("interpolation-duration", interpolationDuration);
        if (colorHex != null) sec.set("color", colorHex);
        if (elementIndices != null && !elementIndices.isEmpty()) {
            sec.set("element-indices", elementIndices);
        }
        sec.set("lock-rotation-axis", lockRotationAxis);
    }

    public static BlockModelPart loadFromConfig(ConfigurationSection sec) {
        if (sec == null) return null;
        String id = sec.getString("id", "part");
        BlockModelPart part = new BlockModelPart(id);

        String matStr = sec.getString("material", "PAPER");
        part.setMaterial(Material.matchMaterial(matStr) != null ? Material.matchMaterial(matStr) : Material.PAPER);
        part.setCustomModelData(sec.getInt("custom-model-data", 0));
        part.setItemModel(sec.getString("item-model"));

        float sx = (float) sec.getDouble("scale.x", 1.0);
        float sy = (float) sec.getDouble("scale.y", 1.0);
        float sz = (float) sec.getDouble("scale.z", 1.0);
        part.setScale(new Vector3f(sx, sy, sz));

        float tx = (float) sec.getDouble("translation.x", 0.0);
        float ty = (float) sec.getDouble("translation.y", 0.0);
        float tz = (float) sec.getDouble("translation.z", 0.0);
        part.setTranslation(new Vector3f(tx, ty, tz));

        float rx = (float) sec.getDouble("rotation.pitch", 0.0);
        float ry = (float) sec.getDouble("rotation.yaw", 0.0);
        float rz = (float) sec.getDouble("rotation.roll", 0.0);
        part.setRotation(new Vector3f(rx, ry, rz));

        part.setAnimated(sec.getBoolean("animated", false));
        String animStr = sec.getString("animation-type", "STATIC");
        try {
            part.setAnimationType(BlockAnimationType.valueOf(animStr.toUpperCase(Locale.ROOT)));
        } catch (Exception ignored) {
            part.setAnimationType(BlockAnimationType.STATIC);
        }

        part.setAnimationSpeed(sec.getDouble("animation-speed", 1.0));
        part.setInterpolationDuration(sec.getInt("interpolation-duration", 10));
        part.setColorHex(sec.getString("color"));
        if (sec.contains("element-indices")) {
            part.setElementIndices(sec.getIntegerList("element-indices"));
        }
        part.setLockRotationAxis(sec.getBoolean("lock-rotation-axis", false));
        return part;
    }

    public BlockModelPart copy() {
        BlockModelPart copy = new BlockModelPart(this.id);
        copy.setMaterial(this.material);
        copy.setCustomModelData(this.customModelData);
        copy.setItemModel(this.itemModel);
        copy.setScale(new Vector3f(this.scale));
        copy.setTranslation(new Vector3f(this.translation));
        copy.setRotation(new Vector3f(this.rotation));
        copy.setAnimated(this.animated);
        copy.setAnimationType(this.animationType);
        copy.setAnimationSpeed(this.animationSpeed);
        copy.setInterpolationDuration(this.interpolationDuration);
        copy.setColorHex(this.colorHex);
        copy.setElementIndices(new java.util.ArrayList<>(this.elementIndices));
        copy.setLockRotationAxis(this.lockRotationAxis);
        return copy;
    }
}

