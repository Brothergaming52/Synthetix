package org.bg52.synthetix.item.food;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.FoodComponent;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;

/**
 * Super-modular Food & Consumable specification for Synthetix items.
 * Allows turning ANY item into an edible/drinkable consumable with custom eat speeds,
 * nutrition, saturation, status effects, and cooldowns.
 */
public class FoodComponentSpec {

    private int nutrition;
    private float saturation;
    private boolean canAlwaysEat;
    private float eatSeconds;
    private double eatCooldown; // in seconds
    private String cooldownGroup;
    private List<FoodEffectSpec> effects;

    public FoodComponentSpec() {
        this.nutrition = 4;
        this.saturation = 0.6f;
        this.canAlwaysEat = false;
        this.eatSeconds = 1.6f;
        this.eatCooldown = 0.0;
        this.cooldownGroup = null;
        this.effects = new ArrayList<>();
    }

    public int getNutrition() {
        return nutrition;
    }

    public void setNutrition(int nutrition) {
        this.nutrition = Math.max(0, nutrition);
    }

    public float getSaturation() {
        return saturation;
    }

    public void setSaturation(float saturation) {
        this.saturation = Math.max(0.0f, saturation);
    }

    public boolean canAlwaysEat() {
        return canAlwaysEat;
    }

    public void setCanAlwaysEat(boolean canAlwaysEat) {
        this.canAlwaysEat = canAlwaysEat;
    }

    public float getEatSeconds() {
        return eatSeconds;
    }

    public void setEatSeconds(float eatSeconds) {
        this.eatSeconds = Math.max(0.05f, eatSeconds);
    }

    public double getEatCooldown() {
        return eatCooldown;
    }

    public void setEatCooldown(double eatCooldown) {
        this.eatCooldown = Math.max(0.0, eatCooldown);
    }

    public String getCooldownGroup() {
        return cooldownGroup;
    }

    public void setCooldownGroup(String cooldownGroup) {
        this.cooldownGroup = cooldownGroup != null && !cooldownGroup.trim().isEmpty() ? cooldownGroup.trim() : null;
    }

    public List<FoodEffectSpec> getEffects() {
        return effects;
    }

    public void setEffects(List<FoodEffectSpec> effects) {
        this.effects = effects != null ? new ArrayList<>(effects) : new ArrayList<>();
    }

    public void addEffect(FoodEffectSpec effect) {
        if (effect != null) {
            this.effects.add(effect);
        }
    }

    /**
     * Applies this FoodComponent specification to a Paper ItemMeta.
     */
    public void applyTo(ItemMeta meta) {
        if (meta == null) return;
        try {
            FoodComponent fc = meta.getFood();
            if (fc == null) {
                ItemMeta appleMeta = Bukkit.getItemFactory().getItemMeta(Material.APPLE);
                if (appleMeta != null && appleMeta.hasFood()) {
                    fc = appleMeta.getFood();
                }
            }
            if (fc != null) {
                fc.setNutrition(this.nutrition);
                fc.setSaturation(this.saturation);
                fc.setCanAlwaysEat(this.canAlwaysEat);

                // Legacy Paper 1.20.6 methods (moved to Consumable in 1.21.2+)
                try {
                    java.lang.reflect.Method eatSecMethod = fc.getClass().getMethod("setEatSeconds", float.class);
                    eatSecMethod.invoke(fc, this.eatSeconds);
                } catch (Throwable ignored) {}

                try {
                    java.lang.reflect.Method setEffMethod = fc.getClass().getMethod("setEffects", List.class);
                    setEffMethod.invoke(fc, new ArrayList<>());
                    java.lang.reflect.Method addEffMethod = fc.getClass().getMethod("addEffect", PotionEffect.class, float.class);
                    for (FoodEffectSpec spec : this.effects) {
                        PotionEffect pe = spec.toPotionEffect();
                        if (pe != null) {
                            addEffMethod.invoke(fc, pe, (float) spec.getProbability());
                        }
                    }
                } catch (Throwable ignored) {}

                meta.setFood(fc);
            }
        } catch (Throwable ignored) {
        }
    }

    public void saveToConfig(ConfigurationSection sec) {
        sec.set("nutrition", nutrition);
        sec.set("saturation", saturation);
        sec.set("can-always-eat", canAlwaysEat);
        sec.set("eat-seconds", eatSeconds);
        if (eatCooldown > 0) sec.set("eat-cooldown", eatCooldown);
        if (cooldownGroup != null) sec.set("cooldown-group", cooldownGroup);

        if (effects != null && !effects.isEmpty()) {
            ConfigurationSection effSec = sec.createSection("effects");
            for (int i = 0; i < effects.size(); i++) {
                ConfigurationSection eEntry = effSec.createSection("eff_" + i);
                effects.get(i).saveToConfig(eEntry);
            }
        }
    }

    public static FoodComponentSpec loadFromConfig(ConfigurationSection sec) {
        if (sec == null) return null;
        FoodComponentSpec spec = new FoodComponentSpec();
        spec.setNutrition(sec.getInt("nutrition", 4));
        spec.setSaturation((float) sec.getDouble("saturation", 0.6));
        spec.setCanAlwaysEat(sec.getBoolean("can-always-eat", false));
        spec.setEatSeconds((float) sec.getDouble("eat-seconds", 1.6));
        spec.setEatCooldown(sec.getDouble("eat-cooldown", 0.0));
        spec.setCooldownGroup(sec.getString("cooldown-group"));

        ConfigurationSection effSec = sec.getConfigurationSection("effects");
        if (effSec != null) {
            List<FoodEffectSpec> list = new ArrayList<>();
            for (String key : effSec.getKeys(false)) {
                ConfigurationSection eEntry = effSec.getConfigurationSection(key);
                if (eEntry != null) {
                    FoodEffectSpec eff = FoodEffectSpec.loadFromConfig(eEntry);
                    if (eff != null) {
                        list.add(eff);
                    }
                }
            }
            spec.setEffects(list);
        }

        return spec;
    }

    public FoodComponentSpec copy() {
        FoodComponentSpec copy = new FoodComponentSpec();
        copy.setNutrition(this.nutrition);
        copy.setSaturation(this.saturation);
        copy.setCanAlwaysEat(this.canAlwaysEat);
        copy.setEatSeconds(this.eatSeconds);
        copy.setEatCooldown(this.eatCooldown);
        copy.setCooldownGroup(this.cooldownGroup);
        List<FoodEffectSpec> effCopies = new ArrayList<>();
        for (FoodEffectSpec eff : this.effects) {
            effCopies.add(eff.copy());
        }
        copy.setEffects(effCopies);
        return copy;
    }
}

