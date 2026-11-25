package org.bg52.synthetix.entity.runtime;

import org.bg52.synthetix.entity.model.EntityHitboxSpec;
import org.bukkit.Location;
import org.bukkit.entity.Interaction;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Runtime sub-voxel hitbox instance (Interaction entity) mounted to a specific skeletal bone.
 */
public class VirtualHitboxInstance {

    private final EntityHitboxSpec spec;
    private final Interaction interactionEntity;

    public VirtualHitboxInstance(EntityHitboxSpec spec, Interaction interactionEntity) {
        this.spec = spec;
        this.interactionEntity = interactionEntity;
    }

    public EntityHitboxSpec getSpec() {
        return spec;
    }

    public Interaction getInteractionEntity() {
        return interactionEntity;
    }

    /**
     * Repositions the Interaction hitbox in the world relative to its parent bone's world transform matrix.
     */
    public void updatePosition(Location rootLoc, Matrix4f boneWorldMatrix) {
        if (interactionEntity == null || !interactionEntity.isValid() || rootLoc == null) return;

        Vector3f transformedOffset = new Vector3f();
        if (boneWorldMatrix != null) {
            boneWorldMatrix.transformPosition(spec.getOffset(), transformedOffset);
        } else {
            transformedOffset.set(spec.getOffset());
        }

        double targetX = rootLoc.getX() + transformedOffset.x;
        double targetY = rootLoc.getY() + transformedOffset.y;
        double targetZ = rootLoc.getZ() + transformedOffset.z;

        // Teleport interaction entity smoothly if drifted
        Location cur = interactionEntity.getLocation();
        double distSq = (cur.getX() - targetX) * (cur.getX() - targetX)
                + (cur.getY() - targetY) * (cur.getY() - targetY)
                + (cur.getZ() - targetZ) * (cur.getZ() - targetZ);

        if (distSq > 0.01) {
            interactionEntity.teleport(new Location(rootLoc.getWorld(), targetX, targetY, targetZ, rootLoc.getYaw(), rootLoc.getPitch()));
        }
    }

    public void cleanup() {
        if (interactionEntity != null && interactionEntity.isValid()) {
            try {
                interactionEntity.setResponsive(false);
            } catch (Throwable ignored) {}
            try {
                interactionEntity.remove();
            } catch (Throwable ignored) {}
        }
    }
}

