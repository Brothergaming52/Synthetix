package org.bg52.synthetix.entity.model;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.joml.Vector3f;

/**
 * Skeletal Bone specification for custom entities.
 * Defines bone hierarchy, pivot points, 3D model resources, and optional hitboxes.
 */
public class EntityBoneSpec {

    private String name;
    private String parent; // null if root
    private Vector3f pivot = new Vector3f(0.0f, 0.0f, 0.0f);
    private Material material = Material.LEATHER_HORSE_ARMOR;
    private int customModelData = 0;
    private String itemModel = null;
    private BoneTransform initialTransform = new BoneTransform();
    private EntityHitboxSpec hitbox = null;
    private Integer brightness = null; // null for ambient, 0-15
    private String glowColorHex = null;
    private String tintColorHex = null; // Hex color for leather armor tinting

    public EntityBoneSpec() {
    }

    public EntityBoneSpec(String name, String parent, Vector3f pivot, Material material, int customModelData) {
        this.name = name;
        this.parent = parent;
        this.pivot = pivot != null ? pivot : new Vector3f(0, 0, 0);
        this.material = material != null ? material : Material.LEATHER_HORSE_ARMOR;
        this.customModelData = customModelData;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getParent() {
        return parent;
    }

    public void setParent(String parent) {
        this.parent = parent;
    }

    public Vector3f getPivot() {
        return pivot;
    }

    public void setPivot(Vector3f pivot) {
        this.pivot = pivot != null ? pivot : new Vector3f(0, 0, 0);
    }

    public Material getMaterial() {
        return material;
    }

    public void setMaterial(Material material) {
        this.material = material != null ? material : Material.LEATHER_HORSE_ARMOR;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public void setCustomModelData(int customModelData) {
        this.customModelData = Math.max(0, customModelData);
    }

    public String getItemModel() {
        return itemModel;
    }

    public void setItemModel(String itemModel) {
        this.itemModel = itemModel;
    }

    public BoneTransform getInitialTransform() {
        return initialTransform;
    }

    public void setInitialTransform(BoneTransform initialTransform) {
        this.initialTransform = initialTransform != null ? initialTransform : new BoneTransform();
    }

    public EntityHitboxSpec getHitbox() {
        return hitbox;
    }

    public void setHitbox(EntityHitboxSpec hitbox) {
        this.hitbox = hitbox;
    }

    public Integer getBrightness() {
        return brightness;
    }

    public void setBrightness(Integer brightness) {
        this.brightness = brightness != null ? Math.max(0, Math.min(15, brightness)) : null;
    }

    public String getGlowColorHex() {
        return glowColorHex;
    }

    public void setGlowColorHex(String glowColorHex) {
        this.glowColorHex = glowColorHex;
    }

    public String getTintColorHex() {
        return tintColorHex;
    }

    public void setTintColorHex(String tintColorHex) {
        this.tintColorHex = tintColorHex;
    }

    public void saveToConfig(ConfigurationSection sec) {
        if (sec == null) return;
        sec.set("name", name);
        if (parent != null) sec.set("parent", parent);
        sec.set("pivot.x", pivot.x);
        sec.set("pivot.y", pivot.y);
        sec.set("pivot.z", pivot.z);
        sec.set("material", material.name());
        if (customModelData > 0) sec.set("custom-model-data", customModelData);
        if (itemModel != null) sec.set("item-model", itemModel);
        if (brightness != null) sec.set("brightness", brightness);
        if (glowColorHex != null) sec.set("glow-color", glowColorHex);
        if (tintColorHex != null) sec.set("tint-color", tintColorHex);

        if (initialTransform != null) {
            ConfigurationSection tSec = sec.createSection("transform");
            tSec.set("translation.x", initialTransform.translation.x);
            tSec.set("translation.y", initialTransform.translation.y);
            tSec.set("translation.z", initialTransform.translation.z);
            tSec.set("scale.x", initialTransform.scale.x);
            tSec.set("scale.y", initialTransform.scale.y);
            tSec.set("scale.z", initialTransform.scale.z);
        }

        if (hitbox != null) {
            hitbox.saveToConfig(sec.createSection("hitbox"));
        }
    }

    public static EntityBoneSpec loadFromConfig(ConfigurationSection sec) {
        if (sec == null) return null;
        EntityBoneSpec bone = new EntityBoneSpec();
        bone.setName(sec.getString("name", "bone"));
        bone.setParent(sec.getString("parent"));

        float px = (float) sec.getDouble("pivot.x", 0.0);
        float py = (float) sec.getDouble("pivot.y", 0.0);
        float pz = (float) sec.getDouble("pivot.z", 0.0);
        bone.setPivot(new Vector3f(px, py, pz));

        String matStr = sec.getString("material", "LEATHER_HORSE_ARMOR");
        Material m = Material.matchMaterial(matStr);
        bone.setMaterial(m != null ? m : Material.LEATHER_HORSE_ARMOR);

        bone.setCustomModelData(sec.getInt("custom-model-data", 0));
        bone.setItemModel(sec.getString("item-model"));
        if (sec.contains("brightness")) bone.setBrightness(sec.getInt("brightness"));
        bone.setGlowColorHex(sec.getString("glow-color"));
        bone.setTintColorHex(sec.getString("tint-color"));

        ConfigurationSection tSec = sec.getConfigurationSection("transform");
        if (tSec != null) {
            float tx = (float) tSec.getDouble("translation.x", 0.0);
            float ty = (float) tSec.getDouble("translation.y", 0.0);
            float tz = (float) tSec.getDouble("translation.z", 0.0);
            float sx = (float) tSec.getDouble("scale.x", 1.0);
            float sy = (float) tSec.getDouble("scale.y", 1.0);
            float sz = (float) tSec.getDouble("scale.z", 1.0);
            BoneTransform bt = new BoneTransform();
            bt.translation.set(tx, ty, tz);
            bt.scale.set(sx, sy, sz);
            bone.setInitialTransform(bt);
        }

        ConfigurationSection hSec = sec.getConfigurationSection("hitbox");
        if (hSec != null) {
            bone.setHitbox(EntityHitboxSpec.loadFromConfig(hSec));
        }

        return bone;
    }
}

