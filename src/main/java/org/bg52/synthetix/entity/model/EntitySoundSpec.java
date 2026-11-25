package org.bg52.synthetix.entity.model;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Sound audio profile for custom entities.
 */
public class EntitySoundSpec {

    private String ambientSound = null;
    private String hurtSound = "entity.generic.hurt";
    private String deathSound = "entity.generic.death";
    private String stepSound = null;
    private float volume = 1.0f;
    private float pitch = 1.0f;

    public EntitySoundSpec() {
    }

    public String getAmbientSound() {
        return ambientSound;
    }

    public void setAmbientSound(String ambientSound) {
        this.ambientSound = ambientSound;
    }

    public String getHurtSound() {
        return hurtSound;
    }

    public void setHurtSound(String hurtSound) {
        this.hurtSound = hurtSound != null ? hurtSound : "entity.generic.hurt";
    }

    public String getDeathSound() {
        return deathSound;
    }

    public void setDeathSound(String deathSound) {
        this.deathSound = deathSound != null ? deathSound : "entity.generic.death";
    }

    public String getStepSound() {
        return stepSound;
    }

    public void setStepSound(String stepSound) {
        this.stepSound = stepSound;
    }

    public float getVolume() {
        return volume;
    }

    public void setVolume(float volume) {
        this.volume = Math.max(0.0f, volume);
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = Math.max(0.1f, pitch);
    }

    public void saveToConfig(ConfigurationSection sec) {
        if (sec == null) return;
        if (ambientSound != null) sec.set("ambient", ambientSound);
        if (hurtSound != null) sec.set("hurt", hurtSound);
        if (deathSound != null) sec.set("death", deathSound);
        if (stepSound != null) sec.set("step", stepSound);
        sec.set("volume", volume);
        sec.set("pitch", pitch);
    }

    public static EntitySoundSpec loadFromConfig(ConfigurationSection sec) {
        if (sec == null) return new EntitySoundSpec();
        EntitySoundSpec spec = new EntitySoundSpec();
        spec.setAmbientSound(sec.getString("ambient"));
        spec.setHurtSound(sec.getString("hurt", "entity.generic.hurt"));
        spec.setDeathSound(sec.getString("death", "entity.generic.death"));
        spec.setStepSound(sec.getString("step"));
        spec.setVolume((float) sec.getDouble("volume", 1.0));
        spec.setPitch((float) sec.getDouble("pitch", 1.0));
        return spec;
    }
}

