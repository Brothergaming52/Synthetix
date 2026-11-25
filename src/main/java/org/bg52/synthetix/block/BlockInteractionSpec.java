package org.bg52.synthetix.block;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Environmental reactions, flammability, piston behavior, and redstone properties.
 */
public class BlockInteractionSpec {

    private boolean flammable;
    private int burnSpeed;
    private int fireSpreadSpeed;
    private boolean pistonPushable;
    private boolean hasGravity;
    private int redstonePower; // 0 to 15
    private String abilityId; // Optional interactive trigger

    public BlockInteractionSpec() {
        this.flammable = false;
        this.burnSpeed = 0;
        this.fireSpreadSpeed = 0;
        this.pistonPushable = false;
        this.hasGravity = false;
        this.redstonePower = 0;
        this.abilityId = null;
    }

    public boolean isFlammable() {
        return flammable;
    }

    public void setFlammable(boolean flammable) {
        this.flammable = flammable;
    }

    public int getBurnSpeed() {
        return burnSpeed;
    }

    public void setBurnSpeed(int burnSpeed) {
        this.burnSpeed = Math.max(0, burnSpeed);
    }

    public int getFireSpreadSpeed() {
        return fireSpreadSpeed;
    }

    public void setFireSpreadSpeed(int fireSpreadSpeed) {
        this.fireSpreadSpeed = Math.max(0, fireSpreadSpeed);
    }

    public boolean isPistonPushable() {
        return pistonPushable;
    }

    public void setPistonPushable(boolean pistonPushable) {
        this.pistonPushable = pistonPushable;
    }

    public boolean isHasGravity() {
        return hasGravity;
    }

    public void setHasGravity(boolean hasGravity) {
        this.hasGravity = hasGravity;
    }

    public int getRedstonePower() {
        return redstonePower;
    }

    public void setRedstonePower(int redstonePower) {
        this.redstonePower = Math.max(0, Math.min(15, redstonePower));
    }

    public String getAbilityId() {
        return abilityId;
    }

    public void setAbilityId(String abilityId) {
        this.abilityId = abilityId;
    }

    public void saveToConfig(ConfigurationSection sec) {
        sec.set("flammable", flammable);
        sec.set("burn-speed", burnSpeed);
        sec.set("fire-spread-speed", fireSpreadSpeed);
        sec.set("piston-pushable", pistonPushable);
        sec.set("gravity", hasGravity);
        sec.set("redstone-power", redstonePower);
        if (abilityId != null) sec.set("ability", abilityId);
    }

    public static BlockInteractionSpec loadFromConfig(ConfigurationSection sec) {
        if (sec == null) return new BlockInteractionSpec();
        BlockInteractionSpec spec = new BlockInteractionSpec();
        spec.setFlammable(sec.getBoolean("flammable", false));
        spec.setBurnSpeed(sec.getInt("burn-speed", 0));
        spec.setFireSpreadSpeed(sec.getInt("fire-spread-speed", 0));
        spec.setPistonPushable(sec.getBoolean("piston-pushable", false));
        spec.setHasGravity(sec.getBoolean("gravity", false));
        spec.setRedstonePower(sec.getInt("redstone-power", 0));
        spec.setAbilityId(sec.getString("ability"));
        return spec;
    }

    public BlockInteractionSpec copy() {
        BlockInteractionSpec copy = new BlockInteractionSpec();
        copy.setFlammable(this.flammable);
        copy.setBurnSpeed(this.burnSpeed);
        copy.setFireSpreadSpeed(this.fireSpreadSpeed);
        copy.setPistonPushable(this.pistonPushable);
        copy.setHasGravity(this.hasGravity);
        copy.setRedstonePower(this.redstonePower);
        copy.setAbilityId(this.abilityId);
        return copy;
    }
}

