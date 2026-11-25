package org.bg52.synthetix.entity.model;

import org.bukkit.configuration.ConfigurationSection;
import org.joml.Vector3f;

/**
 * Precision sub-voxel hitbox definition mounted to a skeletal bone.
 * Enables anatomical damage multipliers (e.g. headshot 2.0x, shielded 0.5x, critical weakpoint 3.0x).
 */
public class EntityHitboxSpec {

    private String name;
    private String boneName;
    private Vector3f offset = new Vector3f(0.0f, 0.0f, 0.0f);
    private float width = 0.8f;
    private float height = 0.8f;
    private double damageMultiplier = 1.0;
    private boolean isWeakpoint = false;

    public EntityHitboxSpec() {
    }

    public EntityHitboxSpec(String name, String boneName, float width, float height, double damageMultiplier) {
        this.name = name;
        this.boneName = boneName;
        this.width = width;
        this.height = height;
        this.damageMultiplier = damageMultiplier;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBoneName() {
        return boneName;
    }

    public void setBoneName(String boneName) {
        this.boneName = boneName;
    }

    public Vector3f getOffset() {
        return offset;
    }

    public void setOffset(Vector3f offset) {
        this.offset = offset != null ? offset : new Vector3f(0.0f, 0.0f, 0.0f);
    }

    public float getWidth() {
        return width;
    }

    public void setWidth(float width) {
        this.width = Math.max(0.1f, width);
    }

    public float getHeight() {
        return height;
    }

    public void setHeight(float height) {
        this.height = Math.max(0.1f, height);
    }

    public double getDamageMultiplier() {
        return damageMultiplier;
    }

    public void setDamageMultiplier(double damageMultiplier) {
        this.damageMultiplier = Math.max(0.0, damageMultiplier);
    }

    public boolean isWeakpoint() {
        return isWeakpoint;
    }

    public void setWeakpoint(boolean weakpoint) {
        isWeakpoint = weakpoint;
    }

    public void saveToConfig(ConfigurationSection sec) {
        if (sec == null) return;
        sec.set("name", name);
        sec.set("bone", boneName);
        sec.set("offset.x", offset.x);
        sec.set("offset.y", offset.y);
        sec.set("offset.z", offset.z);
        sec.set("width", width);
        sec.set("height", height);
        sec.set("damage-multiplier", damageMultiplier);
        sec.set("weakpoint", isWeakpoint);
    }

    public static EntityHitboxSpec loadFromConfig(ConfigurationSection sec) {
        if (sec == null) return null;
        EntityHitboxSpec spec = new EntityHitboxSpec();
        spec.setName(sec.getString("name", "hitbox"));
        spec.setBoneName(sec.getString("bone"));
        float ox = (float) sec.getDouble("offset.x", 0.0);
        float oy = (float) sec.getDouble("offset.y", 0.0);
        float oz = (float) sec.getDouble("offset.z", 0.0);
        spec.setOffset(new Vector3f(ox, oy, oz));
        spec.setWidth((float) sec.getDouble("width", 0.8));
        spec.setHeight((float) sec.getDouble("height", 0.8));
        spec.setDamageMultiplier(sec.getDouble("damage-multiplier", 1.0));
        spec.setWeakpoint(sec.getBoolean("weakpoint", false));
        return spec;
    }
}

