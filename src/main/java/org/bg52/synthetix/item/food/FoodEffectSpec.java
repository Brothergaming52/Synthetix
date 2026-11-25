package org.bg52.synthetix.item.food;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;

/**
 * Represents a status effect applied when consuming a food item.
 */
public class FoodEffectSpec {

    private PotionEffectType effectType;
    private int durationTicks; // 20 ticks = 1 second
    private int amplifier;     // 0 = level 1, 1 = level 2
    private float probability; // 0.0 to 1.0 (1.0 = 100%)

    public FoodEffectSpec(PotionEffectType effectType, int durationTicks, int amplifier, float probability) {
        this.effectType = effectType != null ? effectType : PotionEffectType.REGENERATION;
        this.durationTicks = Math.max(1, durationTicks);
        this.amplifier = Math.max(0, amplifier);
        this.probability = Math.max(0.0f, Math.min(1.0f, probability));
    }

    public PotionEffectType getEffectType() {
        return effectType;
    }

    public void setEffectType(PotionEffectType effectType) {
        this.effectType = effectType;
    }

    public int getDurationTicks() {
        return durationTicks;
    }

    public void setDurationTicks(int durationTicks) {
        this.durationTicks = Math.max(1, durationTicks);
    }

    public int getAmplifier() {
        return amplifier;
    }

    public void setAmplifier(int amplifier) {
        this.amplifier = Math.max(0, amplifier);
    }

    public float getProbability() {
        return probability;
    }

    public void setProbability(float probability) {
        this.probability = Math.max(0.0f, Math.min(1.0f, probability));
    }

    public PotionEffect toPotionEffect() {
        if (effectType == null) return null;
        return new PotionEffect(effectType, durationTicks, amplifier, false, true, true);
    }

    public void saveToConfig(ConfigurationSection sec) {
        if (effectType != null) {
            sec.set("effect", effectType.getKey().asString());
        }
        sec.set("duration", durationTicks);
        sec.set("amplifier", amplifier);
        sec.set("probability", probability);
    }

    public static FoodEffectSpec loadFromConfig(ConfigurationSection sec) {
        if (sec == null) return null;
        String effName = sec.getString("effect");
        PotionEffectType type = parsePotionEffectType(effName);
        int duration = sec.getInt("duration", 200);
        int amp = sec.getInt("amplifier", 0);
        double prob = sec.getDouble("probability", 1.0);
        return new FoodEffectSpec(type, duration, amp, (float) prob);
    }

    public static PotionEffectType parsePotionEffectType(String name) {
        if (name == null || name.isEmpty()) return null;
        String clean = name.trim().toLowerCase(Locale.ROOT);
        NamespacedKey key = clean.contains(":") ? NamespacedKey.fromString(clean) : NamespacedKey.minecraft(clean);
        if (key != null) {
            PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(key);
            if (type != null) return type;
        }
        return PotionEffectType.getByName(clean.toUpperCase(Locale.ROOT));
    }

    public FoodEffectSpec copy() {
        return new FoodEffectSpec(this.effectType, this.durationTicks, this.amplifier, this.probability);
    }
}

