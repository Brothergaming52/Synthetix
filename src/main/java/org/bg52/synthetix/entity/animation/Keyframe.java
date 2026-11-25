package org.bg52.synthetix.entity.animation;

import org.bg52.synthetix.entity.model.BoneTransform;

/**
 * Keyframe point on an animation timeline for a specific bone.
 */
public class Keyframe {

    public enum InterpolationType {
        LINEAR,
        STEP,
        EASE_IN_OUT
    }

    private float tick;
    private BoneTransform transform;
    private InterpolationType interpolation;

    public Keyframe() {
        this.tick = 0;
        this.transform = new BoneTransform();
        this.interpolation = InterpolationType.LINEAR;
    }

    public Keyframe(float tick, BoneTransform transform) {
        this(tick, transform, InterpolationType.LINEAR);
    }

    public Keyframe(float tick, BoneTransform transform, InterpolationType interpolation) {
        this.tick = tick;
        this.transform = transform != null ? transform : new BoneTransform();
        this.interpolation = interpolation != null ? interpolation : InterpolationType.LINEAR;
    }

    public float getTick() {
        return tick;
    }

    public void setTick(float tick) {
        this.tick = tick;
    }

    public BoneTransform getTransform() {
        return transform;
    }

    public void setTransform(BoneTransform transform) {
        this.transform = transform != null ? transform : new BoneTransform();
    }

    public InterpolationType getInterpolation() {
        return interpolation;
    }

    public void setInterpolation(InterpolationType interpolation) {
        this.interpolation = interpolation != null ? interpolation : InterpolationType.LINEAR;
    }
}

