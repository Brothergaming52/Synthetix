package org.bg52.synthetix.entity.runtime;

import org.bg52.synthetix.entity.model.BoneTransform;
import org.bg52.synthetix.entity.model.EntityBoneSpec;
import org.bg52.synthetix.item.component.ComponentHandler;
import org.bg52.synthetix.util.MaterialUtil;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Runtime active instance of a virtual ItemDisplay bone projected in the Minecraft world.
 * Never saved to disk (.mca files) - zero server entity ticking penalty.
 */
public class VirtualBoneInstance {

    private final EntityBoneSpec spec;
    private final ItemDisplay displayEntity;
    private final BoneTransform localTransform = new BoneTransform();
    private final BoneTransform worldTransform = new BoneTransform();
    private final BoneTransform lastSentTransform = new BoneTransform();
    private final Matrix4f worldMatrix = new Matrix4f();

    private boolean redFlashActive = false;
    private Color originalLeatherColor = null;

    public VirtualBoneInstance(EntityBoneSpec spec, ItemDisplay displayEntity) {
        this.spec = spec;
        this.displayEntity = displayEntity;
        if (spec.getInitialTransform() != null) {
            this.localTransform.set(spec.getInitialTransform());
        }
        captureOriginalColor();
    }

    private void captureOriginalColor() {
        if (displayEntity != null && displayEntity.getItemStack() != null) {
            ItemMeta meta = displayEntity.getItemStack().getItemMeta();
            if (meta instanceof LeatherArmorMeta lm && lm.getColor() != null) {
                this.originalLeatherColor = lm.getColor();
            }
        }
    }

    public EntityBoneSpec getSpec() {
        return spec;
    }

    public ItemDisplay getDisplayEntity() {
        return displayEntity;
    }

    public BoneTransform getLocalTransform() {
        return localTransform;
    }

    public BoneTransform getWorldTransform() {
        return worldTransform;
    }

    /**
     * Recomputes world transform from parent matrix and local bone transform,
     * then dispatches a GPU-interpolated client transformation update if delta exceeds threshold.
     */
    public boolean updateTransform(Matrix4f parentMatrix, int interpolationDurationTicks) {
        // Forward Kinematics: M_world = M_parent * T(pivot) * M_local * T(-pivot)
        Vector3f pivot = spec.getPivot();
        Matrix4f localMatrix = new Matrix4f();
        if (pivot != null && (pivot.x != 0 || pivot.y != 0 || pivot.z != 0)) {
            localMatrix.translate(pivot)
                    .mul(localTransform.toMatrix())
                    .translate(-pivot.x, -pivot.y, -pivot.z);
        } else {
            localMatrix.set(localTransform.toMatrix());
        }

        this.worldMatrix.set(parentMatrix).mul(localMatrix);

        // Decompose world matrix
        this.worldMatrix.getTranslation(worldTransform.translation);
        this.worldMatrix.getUnnormalizedRotation(worldTransform.rotation);
        this.worldMatrix.getScale(worldTransform.scale);

        if (displayEntity == null || !displayEntity.isValid()) return false;

        // Delta compression: skip network packet dispatch if transform change is negligible
        if (worldTransform.isApproximatelyEqual(lastSentTransform, 0.005f, 0.001f, 0.005f)) {
            return false;
        }

        // Apply GPU SLERP transformation to Display Entity
        displayEntity.setInterpolationDuration(Math.max(1, interpolationDurationTicks));
        displayEntity.setInterpolationDelay(0);
        displayEntity.setTransformation(new Transformation(
                new Vector3f(worldTransform.translation),
                new Quaternionf(worldTransform.rotation),
                new Vector3f(worldTransform.scale),
                new Quaternionf()
        ));

        lastSentTransform.set(worldTransform);
        return true;
    }

    public Matrix4f getWorldMatrix() {
        return worldMatrix;
    }

    /**
     * Special-case visual effect: Activates or restores red damage flash on this bone.
     */
    public void setDamageFlash(boolean active) {
        if (displayEntity == null || !displayEntity.isValid()) return;
        if (this.redFlashActive == active) return;
        this.redFlashActive = active;

        // Apply authentic red damage tint if item is tintable leather
        ItemStack stack = displayEntity.getItemStack();
        if (stack != null && stack.getItemMeta() instanceof LeatherArmorMeta lm) {
            if (active) {
                if (lm.getColor() != null && originalLeatherColor == null) {
                    originalLeatherColor = lm.getColor();
                }
                lm.setColor(Color.fromRGB(255, 60, 60));
            } else {
                lm.setColor(originalLeatherColor);
            }
            stack.setItemMeta(lm);
            displayEntity.setItemStack(stack);
        }

        // Maintain designer-defined glowing outlines only — never use spectral outline as hit effect!
        if (spec.getGlowColorHex() != null) {
            try {
                String hex = spec.getGlowColorHex().replace("#", "");
                displayEntity.setGlowColorOverride(Color.fromRGB(Integer.parseInt(hex, 16)));
                displayEntity.setGlowing(true);
            } catch (Exception ignored) {
                displayEntity.setGlowing(false);
            }
        } else {
            displayEntity.setGlowing(false);
            displayEntity.setGlowColorOverride(null);
        }
    }

    public void cleanup() {
        if (displayEntity != null && displayEntity.isValid()) {
            try {
                displayEntity.remove();
            } catch (Throwable ignored) {}
        }
    }
}

