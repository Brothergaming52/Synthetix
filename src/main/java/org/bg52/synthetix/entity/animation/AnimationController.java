package org.bg52.synthetix.entity.animation;

import org.bg52.synthetix.entity.model.BoneTransform;

import java.util.HashMap;
import java.util.Map;

/**
 * High-performance animation runtime controller for an active custom entity.
 * Handles timeline tracking, client-interpolated SLERP cross-fading, blending, and looping.
 */
public class AnimationController {

    private final Map<String, AnimationTrack> tracks = new HashMap<>();
    private AnimationTrack currentTrack = null;
    private float currentTick = 0.0f;
    private float speed = 1.0f;
    private boolean loop = true;

    // Cross-fade blending state
    private boolean inTransition = false;
    private AnimationTrack transitionTarget = null;
    private float transitionDuration = 0.0f;
    private float transitionElapsed = 0.0f;
    private final Map<String, BoneTransform> transitionSnapshot = new HashMap<>();

    public AnimationController() {
    }

    public void registerTrack(AnimationTrack track) {
        if (track != null && track.getName() != null) {
            tracks.put(track.getName().toLowerCase(), track);
        }
    }

    public AnimationTrack getTrack(String name) {
        if (name == null) return null;
        return tracks.get(name.toLowerCase());
    }

    public boolean hasTrack(String name) {
        if (name == null) return false;
        return tracks.containsKey(name.toLowerCase());
    }

    public Map<String, AnimationTrack> getTracks() {
        return tracks;
    }

    public void play(String trackName, boolean loop, float blendDurationTicks) {
        if (trackName == null) return;
        AnimationTrack target = tracks.get(trackName.toLowerCase());
        if (target == null) return;

        // If already playing this track in loop mode, don't restart transition
        if (currentTrack == target && loop && !inTransition) {
            return;
        }

        if (blendDurationTicks > 0.0f && currentTrack != null) {
            // Snapshot current active pose for seamless cross-fading
            this.inTransition = true;
            this.transitionTarget = target;
            this.transitionDuration = Math.max(1.0f, blendDurationTicks);
            this.transitionElapsed = 0.0f;
            this.loop = loop;
        } else {
            this.currentTrack = target;
            this.currentTick = 0.0f;
            this.loop = loop;
            this.inTransition = false;
            this.transitionTarget = null;
        }
    }

    public void stop() {
        this.currentTrack = null;
        this.inTransition = false;
        this.transitionTarget = null;
        this.currentTick = 0.0f;
    }

    /**
     * Ticks the animation timeline and updates bone transformations.
     */
    public void update(float deltaTicks, Map<String, BoneTransform> activeTransforms) {
        if (inTransition && transitionTarget != null) {
            transitionElapsed += deltaTicks * speed;
            float alpha = Math.min(1.0f, transitionElapsed / transitionDuration);

            // Evaluate target track pose
            Map<String, BoneTransform> targetPose = new HashMap<>();
            for (String boneName : activeTransforms.keySet()) {
                BoneTransform bt = new BoneTransform();
                if (transitionTarget.evaluate(boneName, 0.0f, bt)) {
                    targetPose.put(boneName, bt);
                }
            }

            // Lerp from snapshot to target pose
            for (Map.Entry<String, BoneTransform> entry : activeTransforms.entrySet()) {
                String bName = entry.getKey();
                BoneTransform base = transitionSnapshot.get(bName);
                BoneTransform tgt = targetPose.get(bName);
                if (base != null && tgt != null) {
                    BoneTransform result = base.clone();
                    result.lerp(tgt, alpha);
                    entry.getValue().set(result);
                } else if (tgt != null) {
                    entry.getValue().set(tgt);
                }
            }

            if (alpha >= 1.0f) {
                this.inTransition = false;
                this.currentTrack = transitionTarget;
                this.currentTick = 0.0f;
                this.transitionTarget = null;
            }
            return;
        }

        if (currentTrack == null) return;

        currentTick += deltaTicks * speed;

        if (currentTick >= currentTrack.getDurationTicks()) {
            if (loop) {
                currentTick %= currentTrack.getDurationTicks();
            } else {
                currentTick = currentTrack.getDurationTicks();
            }
        }

        for (Map.Entry<String, BoneTransform> entry : activeTransforms.entrySet()) {
            String boneName = entry.getKey();
            BoneTransform out = entry.getValue();
            if (currentTrack.evaluate(boneName, currentTick, out)) {
                // Evaluated successfully into out
                transitionSnapshot.put(boneName, out.clone());
            }
        }
    }

    public AnimationTrack getCurrentTrack() {
        return currentTrack;
    }

    public float getCurrentTick() {
        return currentTick;
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = Math.max(0.01f, speed);
    }

    public boolean isFinished() {
        if (currentTrack == null) return true;
        if (loop) return false;
        return currentTick >= currentTrack.getDurationTicks();
    }
}

