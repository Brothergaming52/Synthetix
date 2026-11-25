package org.bg52.synthetix.entity.animation;

import org.bg52.synthetix.entity.model.BoneTransform;
import org.bukkit.configuration.ConfigurationSection;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/**
 * Named animation timeline containing keyframe channels for individual bones.
 */
public class AnimationTrack {

    private String name;
    private float durationTicks;
    private boolean loop;
    private final Map<String, List<Keyframe>> boneKeyframes = new HashMap<>();

    public AnimationTrack() {
        this.name = "idle";
        this.durationTicks = 20.0f;
        this.loop = true;
    }

    public AnimationTrack(String name, float durationTicks, boolean loop) {
        this.name = name;
        this.durationTicks = Math.max(1.0f, durationTicks);
        this.loop = loop;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public float getDurationTicks() {
        return durationTicks;
    }

    public void setDurationTicks(float durationTicks) {
        this.durationTicks = Math.max(1.0f, durationTicks);
    }

    public boolean isLoop() {
        return loop;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    public Map<String, List<Keyframe>> getBoneKeyframes() {
        return boneKeyframes;
    }

    public void addKeyframe(String boneName, Keyframe keyframe) {
        if (boneName == null || keyframe == null) return;
        List<Keyframe> list = boneKeyframes.computeIfAbsent(boneName, k -> new ArrayList<>());
        list.add(keyframe);
        list.sort(Comparator.comparingDouble(Keyframe::getTick));
        if (keyframe.getTick() > durationTicks) {
            durationTicks = keyframe.getTick();
        }
    }

    /**
     * Evaluates the interpolated transformation of a bone at a specific tick time.
     * @return true if an animation channel exists for this bone, false otherwise.
     */
    public boolean evaluate(String boneName, float tick, BoneTransform outTransform) {
        if (boneName == null || outTransform == null) return false;
        List<Keyframe> frames = boneKeyframes.get(boneName);
        if (frames == null || frames.isEmpty()) return false;

        float clampedTick = loop ? (tick % durationTicks) : Math.min(tick, durationTicks);

        if (frames.size() == 1 || clampedTick <= frames.get(0).getTick()) {
            outTransform.set(frames.get(0).getTransform());
            return true;
        }

        Keyframe lastFrame = frames.get(frames.size() - 1);
        if (clampedTick >= lastFrame.getTick()) {
            outTransform.set(lastFrame.getTransform());
            return true;
        }

        // Binary or linear search between keyframe interval
        Keyframe prev = frames.get(0);
        Keyframe next = lastFrame;
        for (int i = 0; i < frames.size(); i++) {
            Keyframe k = frames.get(i);
            if (k.getTick() <= clampedTick) {
                prev = k;
            } else {
                next = k;
                break;
            }
        }

        float span = next.getTick() - prev.getTick();
        if (span <= 0.0001f) {
            outTransform.set(prev.getTransform());
            return true;
        }

        float alpha = (clampedTick - prev.getTick()) / span;

        if (prev.getInterpolation() == Keyframe.InterpolationType.STEP) {
            outTransform.set(prev.getTransform());
            return true;
        } else if (prev.getInterpolation() == Keyframe.InterpolationType.EASE_IN_OUT) {
            alpha = alpha * alpha * (3.0f - 2.0f * alpha);
        }

        outTransform.set(prev.getTransform());
        outTransform.lerp(next.getTransform(), alpha);
        return true;
    }

    public void saveToConfig(ConfigurationSection sec) {
        if (sec == null) return;
        sec.set("name", name);
        sec.set("duration-ticks", durationTicks);
        sec.set("loop", loop);

        ConfigurationSection bonesSec = sec.createSection("bones");
        for (Map.Entry<String, List<Keyframe>> entry : boneKeyframes.entrySet()) {
            ConfigurationSection bSec = bonesSec.createSection(entry.getKey());
            List<Keyframe> frames = entry.getValue();
            for (int i = 0; i < frames.size(); i++) {
                ConfigurationSection kSec = bSec.createSection("k-" + i);
                Keyframe k = frames.get(i);
                kSec.set("tick", k.getTick());
                kSec.set("interpolation", k.getInterpolation().name());

                BoneTransform bt = k.getTransform();
                kSec.set("translation.x", bt.translation.x);
                kSec.set("translation.y", bt.translation.y);
                kSec.set("translation.z", bt.translation.z);

                kSec.set("rotation.x", bt.rotation.x);
                kSec.set("rotation.y", bt.rotation.y);
                kSec.set("rotation.z", bt.rotation.z);
                kSec.set("rotation.w", bt.rotation.w);

                kSec.set("scale.x", bt.scale.x);
                kSec.set("scale.y", bt.scale.y);
                kSec.set("scale.z", bt.scale.z);
            }
        }
    }

    public static AnimationTrack loadFromConfig(ConfigurationSection sec) {
        if (sec == null) return null;
        AnimationTrack track = new AnimationTrack();
        track.setName(sec.getString("name", sec.getName()));
        track.setDurationTicks((float) sec.getDouble("duration-ticks", 20.0));
        track.setLoop(sec.getBoolean("loop", true));

        ConfigurationSection bonesSec = sec.getConfigurationSection("bones");
        if (bonesSec != null) {
            for (String boneName : bonesSec.getKeys(false)) {
                ConfigurationSection bSec = bonesSec.getConfigurationSection(boneName);
                if (bSec == null) continue;

                for (String kKey : bSec.getKeys(false)) {
                    ConfigurationSection kSec = bSec.getConfigurationSection(kKey);
                    if (kSec == null) continue;

                    float tick = (float) kSec.getDouble("tick", 0.0);
                    String interpStr = kSec.getString("interpolation", "LINEAR");
                    Keyframe.InterpolationType interp;
                    try {
                        interp = Keyframe.InterpolationType.valueOf(interpStr.toUpperCase(Locale.ROOT));
                    } catch (Exception ignored) {
                        interp = Keyframe.InterpolationType.LINEAR;
                    }

                    float tx = (float) kSec.getDouble("translation.x", 0.0);
                    float ty = (float) kSec.getDouble("translation.y", 0.0);
                    float tz = (float) kSec.getDouble("translation.z", 0.0);

                    float rx = (float) kSec.getDouble("rotation.x", 0.0);
                    float ry = (float) kSec.getDouble("rotation.y", 0.0);
                    float rz = (float) kSec.getDouble("rotation.z", 0.0);
                    float rw = (float) kSec.getDouble("rotation.w", 1.0);

                    float sx = (float) kSec.getDouble("scale.x", 1.0);
                    float sy = (float) kSec.getDouble("scale.y", 1.0);
                    float sz = (float) kSec.getDouble("scale.z", 1.0);

                    BoneTransform bt = new BoneTransform(new Vector3f(tx, ty, tz), new Quaternionf(rx, ry, rz, rw), new Vector3f(sx, sy, sz));
                    track.addKeyframe(boneName, new Keyframe(tick, bt, interp));
                }
            }
        }

        return track;
    }
}

