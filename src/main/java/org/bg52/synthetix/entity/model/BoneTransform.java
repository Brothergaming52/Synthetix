package org.bg52.synthetix.entity.model;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Representation of 3D spatial transformation (translation, rotation, scale)
 * for custom skeletal bones and hitboxes.
 */
public class BoneTransform {

    public final Vector3f translation = new Vector3f(0.0f, 0.0f, 0.0f);
    public final Quaternionf rotation = new Quaternionf();
    public final Vector3f scale = new Vector3f(1.0f, 1.0f, 1.0f);

    public BoneTransform() {
    }

    public BoneTransform(Vector3f translation, Quaternionf rotation, Vector3f scale) {
        if (translation != null) this.translation.set(translation);
        if (rotation != null) this.rotation.set(rotation);
        if (scale != null) this.scale.set(scale);
    }

    public void set(BoneTransform other) {
        if (other == null) return;
        this.translation.set(other.translation);
        this.rotation.set(other.rotation);
        this.scale.set(other.scale);
    }

    public void lerp(BoneTransform target, float alpha) {
        if (target == null) return;
        this.translation.lerp(target.translation, alpha);
        this.rotation.slerp(target.rotation, alpha);
        this.scale.lerp(target.scale, alpha);
    }

    public Matrix4f toMatrix() {
        return new Matrix4f()
                .translate(translation)
                .rotate(rotation)
                .scale(scale);
    }

    public void reset() {
        this.translation.set(0.0f, 0.0f, 0.0f);
        this.rotation.identity();
        this.scale.set(1.0f, 1.0f, 1.0f);
    }

    public BoneTransform clone() {
        return new BoneTransform(new Vector3f(this.translation), new Quaternionf(this.rotation), new Vector3f(this.scale));
    }

    public boolean isApproximatelyEqual(BoneTransform other, float translationTolerance, float rotationTolerance, float scaleTolerance) {
        if (other == null) return false;
        if (this.translation.distanceSquared(other.translation) > translationTolerance * translationTolerance) return false;
        if (Math.abs(this.rotation.angle() - other.rotation.angle()) > rotationTolerance) return false;
        if (this.scale.distanceSquared(other.scale) > scaleTolerance * scaleTolerance) return false;
        return true;
    }
}

