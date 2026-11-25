package org.bg52.synthetix.block;

import org.bukkit.configuration.ConfigurationSection;
import org.joml.Vector3f;

import java.util.Locale;

/**
 * Physical properties and collision metrics of a custom block.
 */
public class BlockPhysicsSpec {

    private CollisionShape collisionShape;
    private Vector3f customHitboxSize;
    private float friction;
    private int lightEmission; // 0 to 15
    private boolean passThrough;
    private boolean waterloggable;
    private boolean solid;

    public BlockPhysicsSpec() {
        this.collisionShape = CollisionShape.SOLID_CUBE;
        this.customHitboxSize = new Vector3f(1.0f, 1.0f, 1.0f);
        this.friction = 0.6f;
        this.lightEmission = 0;
        this.passThrough = false;
        this.waterloggable = false;
        this.solid = true;
    }

    public CollisionShape getCollisionShape() {
        return collisionShape;
    }

    public void setCollisionShape(CollisionShape collisionShape) {
        this.collisionShape = collisionShape != null ? collisionShape : CollisionShape.SOLID_CUBE;
    }

    public Vector3f getCustomHitboxSize() {
        return customHitboxSize;
    }

    public void setCustomHitboxSize(Vector3f customHitboxSize) {
        this.customHitboxSize = customHitboxSize != null ? customHitboxSize : new Vector3f(1.0f, 1.0f, 1.0f);
    }

    public float getFriction() {
        return friction;
    }

    public void setFriction(float friction) {
        this.friction = Math.max(0.01f, Math.min(1.0f, friction));
    }

    public int getLightEmission() {
        return lightEmission;
    }

    public void setLightEmission(int lightEmission) {
        this.lightEmission = Math.max(0, Math.min(15, lightEmission));
    }

    public boolean isPassThrough() {
        return passThrough;
    }

    public void setPassThrough(boolean passThrough) {
        this.passThrough = passThrough;
    }

    public boolean isWaterloggable() {
        return waterloggable;
    }

    public void setWaterloggable(boolean waterloggable) {
        this.waterloggable = waterloggable;
    }

    public boolean isSolid() {
        return solid;
    }

    public void setSolid(boolean solid) {
        this.solid = solid;
    }

    public void saveToConfig(ConfigurationSection sec) {
        sec.set("collision-shape", collisionShape.name());
        sec.set("custom-hitbox.x", customHitboxSize.x);
        sec.set("custom-hitbox.y", customHitboxSize.y);
        sec.set("custom-hitbox.z", customHitboxSize.z);
        sec.set("friction", friction);
        sec.set("light-emission", lightEmission);
        sec.set("pass-through", passThrough);
        sec.set("waterloggable", waterloggable);
        sec.set("solid", solid);
    }

    public static BlockPhysicsSpec loadFromConfig(ConfigurationSection sec) {
        if (sec == null) return new BlockPhysicsSpec();
        BlockPhysicsSpec spec = new BlockPhysicsSpec();

        String shapeStr = sec.getString("collision-shape", "SOLID_CUBE");
        try {
            spec.setCollisionShape(CollisionShape.valueOf(shapeStr.toUpperCase(Locale.ROOT)));
        } catch (Exception ignored) {
            spec.setCollisionShape(CollisionShape.SOLID_CUBE);
        }

        float hx = (float) sec.getDouble("custom-hitbox.x", 1.0);
        float hy = (float) sec.getDouble("custom-hitbox.y", 1.0);
        float hz = (float) sec.getDouble("custom-hitbox.z", 1.0);
        spec.setCustomHitboxSize(new Vector3f(hx, hy, hz));

        spec.setFriction((float) sec.getDouble("friction", 0.6));
        spec.setLightEmission(sec.getInt("light-emission", 0));
        spec.setPassThrough(sec.getBoolean("pass-through", false));
        spec.setWaterloggable(sec.getBoolean("waterloggable", false));
        spec.setSolid(sec.getBoolean("solid", true));
        return spec;
    }

    public BlockPhysicsSpec copy() {
        BlockPhysicsSpec copy = new BlockPhysicsSpec();
        copy.setCollisionShape(this.collisionShape);
        copy.setCustomHitboxSize(new Vector3f(this.customHitboxSize));
        copy.setFriction(this.friction);
        copy.setLightEmission(this.lightEmission);
        copy.setPassThrough(this.passThrough);
        copy.setWaterloggable(this.waterloggable);
        copy.setSolid(this.solid);
        return copy;
    }
}

