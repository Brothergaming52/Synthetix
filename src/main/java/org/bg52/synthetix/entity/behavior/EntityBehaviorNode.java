package org.bg52.synthetix.entity.behavior;

import org.bg52.synthetix.util.TextUtil;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * High-level behavioral node within an entity's behavior chart.
 * Couples trigger conditions, priorities, timing, linked animation tracks, and actions.
 */
public class EntityBehaviorNode {

    private String id;
    private String name;
    private int priority = 1;
    private String animationTrack = "attack";
    private boolean loopAnimation = false;
    private float blendTicks = 2.0f;

    // Conditions
    private double minRange = 0.0;
    private double maxRange = 2.5;
    private double minHealthPercent = 0.0;
    private double maxHealthPercent = 100.0;
    private boolean requiresTarget = true;
    private double chance = 1.0;

    // Execution timing
    private int cooldownTicks = 20;
    private int durationTicks = 15;

    // Actions
    private final List<EntityBehaviorAction> actions = new ArrayList<>();

    public EntityBehaviorNode() {
        this.id = "behavior_" + System.currentTimeMillis();
        this.name = "Custom Behavior";
    }

    public EntityBehaviorNode(String id, String name, int priority) {
        this.id = id != null ? id.toLowerCase(Locale.ROOT) : "behavior";
        this.name = name != null ? name : TextUtil.prettify(this.id);
        this.priority = priority;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id != null ? id.toLowerCase(Locale.ROOT) : "behavior";
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name : id;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = Math.max(1, priority);
    }

    public String getAnimationTrack() {
        return animationTrack;
    }

    public void setAnimationTrack(String animationTrack) {
        this.animationTrack = animationTrack;
    }

    public boolean isLoopAnimation() {
        return loopAnimation;
    }

    public void setLoopAnimation(boolean loopAnimation) {
        this.loopAnimation = loopAnimation;
    }

    public float getBlendTicks() {
        return blendTicks;
    }

    public void setBlendTicks(float blendTicks) {
        this.blendTicks = Math.max(0.0f, blendTicks);
    }

    public double getMinRange() {
        return minRange;
    }

    public void setMinRange(double minRange) {
        this.minRange = Math.max(0.0, minRange);
    }

    public double getMaxRange() {
        return maxRange;
    }

    public void setMaxRange(double maxRange) {
        this.maxRange = Math.max(0.0, maxRange);
    }

    public double getMinHealthPercent() {
        return minHealthPercent;
    }

    public void setMinHealthPercent(double minHealthPercent) {
        this.minHealthPercent = Math.max(0.0, Math.min(100.0, minHealthPercent));
    }

    public double getMaxHealthPercent() {
        return maxHealthPercent;
    }

    public void setMaxHealthPercent(double maxHealthPercent) {
        this.maxHealthPercent = Math.max(0.0, Math.min(100.0, maxHealthPercent));
    }

    public boolean isRequiresTarget() {
        return requiresTarget;
    }

    public void setRequiresTarget(boolean requiresTarget) {
        this.requiresTarget = requiresTarget;
    }

    public double getChance() {
        return chance;
    }

    public void setChance(double chance) {
        this.chance = Math.max(0.0, Math.min(1.0, chance));
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    public void setCooldownTicks(int cooldownTicks) {
        this.cooldownTicks = Math.max(1, cooldownTicks);
    }

    public int getDurationTicks() {
        return durationTicks;
    }

    public void setDurationTicks(int durationTicks) {
        this.durationTicks = Math.max(1, durationTicks);
    }

    public List<EntityBehaviorAction> getActions() {
        return actions;
    }

    public void addAction(EntityBehaviorAction action) {
        if (action != null) {
            this.actions.add(action);
        }
    }

    public void saveToConfig(ConfigurationSection sec) {
        if (sec == null) return;
        sec.set("id", id);
        sec.set("name", name);
        sec.set("priority", priority);
        sec.set("animation-track", animationTrack);
        sec.set("loop-animation", loopAnimation);
        sec.set("blend-ticks", blendTicks);

        sec.set("min-range", minRange);
        sec.set("max-range", maxRange);
        sec.set("min-health-percent", minHealthPercent);
        sec.set("max-health-percent", maxHealthPercent);
        sec.set("requires-target", requiresTarget);
        sec.set("chance", chance);

        sec.set("cooldown-ticks", cooldownTicks);
        sec.set("duration-ticks", durationTicks);

        List<String> actionStrings = new ArrayList<>();
        for (EntityBehaviorAction a : actions) {
            actionStrings.add(a.serialize());
        }
        sec.set("actions", actionStrings);
    }

    public static EntityBehaviorNode loadFromConfig(ConfigurationSection sec) {
        if (sec == null) return null;
        String id = sec.getString("id", sec.getName());
        String name = sec.getString("name", id);
        int priority = sec.getInt("priority", 1);

        EntityBehaviorNode node = new EntityBehaviorNode(id, name, priority);
        node.setAnimationTrack(sec.getString("animation-track", "attack"));
        node.setLoopAnimation(sec.getBoolean("loop-animation", false));
        node.setBlendTicks((float) sec.getDouble("blend-ticks", 2.0));

        node.setMinRange(sec.getDouble("min-range", 0.0));
        node.setMaxRange(sec.getDouble("max-range", 2.5));
        node.setMinHealthPercent(sec.getDouble("min-health-percent", 0.0));
        node.setMaxHealthPercent(sec.getDouble("max-health-percent", 100.0));
        node.setRequiresTarget(sec.getBoolean("requires-target", true));
        node.setChance(sec.getDouble("chance", 1.0));

        node.setCooldownTicks(sec.getInt("cooldown-ticks", 20));
        node.setDurationTicks(sec.getInt("duration-ticks", 15));

        List<String> actionStrings = sec.getStringList("actions");
        for (String raw : actionStrings) {
            node.addAction(EntityBehaviorAction.deserialize(raw));
        }

        return node;
    }
}

