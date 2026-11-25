package org.bg52.synthetix.block;

import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mining, harvesting, sounds, audio, and explosion metrics for a custom block.
 */
public class BlockMiningSpec {

    private float hardness;
    private float blastResistance;
    private ToolType requiredTool;
    private ToolTier minimumTier;
    private boolean requiresCorrectToolForDrops;
    private String breakSound;
    private String placeSound;
    private String stepSound;
    private String hitSound;
    private String breakParticles; // Material name or "ITEM"
    private List<BlockDropSpec> drops;
    private int expDrop;
    private double breakSpeedModifier;

    public BlockMiningSpec() {
        this.hardness = 1.5f;
        this.blastResistance = 6.0f;
        this.requiredTool = ToolType.PICKAXE;
        this.minimumTier = ToolTier.WOOD;
        this.requiresCorrectToolForDrops = true;
        this.breakSound = "BLOCK_STONE_BREAK";
        this.placeSound = "BLOCK_STONE_PLACE";
        this.stepSound = "BLOCK_STONE_STEP";
        this.hitSound = "BLOCK_STONE_HIT";
        this.breakParticles = "ITEM";
        this.drops = new ArrayList<>();
        this.drops.add(new BlockDropSpec("self", 1, 1, 1.0));
        this.expDrop = 0;
        this.breakSpeedModifier = 1.0;
    }

    public float getHardness() {
        return hardness;
    }

    public void setHardness(float hardness) {
        this.hardness = Math.max(0.0f, hardness);
    }

    public float getBlastResistance() {
        return blastResistance;
    }

    public void setBlastResistance(float blastResistance) {
        this.blastResistance = Math.max(0.0f, blastResistance);
    }

    public ToolType getRequiredTool() {
        return requiredTool;
    }

    public void setRequiredTool(ToolType requiredTool) {
        this.requiredTool = requiredTool != null ? requiredTool : ToolType.PICKAXE;
    }

    public ToolTier getMinimumTier() {
        return minimumTier;
    }

    public void setMinimumTier(ToolTier minimumTier) {
        this.minimumTier = minimumTier != null ? minimumTier : ToolTier.WOOD;
    }

    public boolean isRequiresCorrectToolForDrops() {
        return requiresCorrectToolForDrops;
    }

    public void setRequiresCorrectToolForDrops(boolean requiresCorrectToolForDrops) {
        this.requiresCorrectToolForDrops = requiresCorrectToolForDrops;
    }

    public String getBreakSound() {
        return breakSound;
    }

    public void setBreakSound(String breakSound) {
        this.breakSound = breakSound != null ? breakSound : "BLOCK_STONE_BREAK";
    }

    public String getPlaceSound() {
        return placeSound;
    }

    public void setPlaceSound(String placeSound) {
        this.placeSound = placeSound != null ? placeSound : "BLOCK_STONE_PLACE";
    }

    public String getStepSound() {
        return stepSound;
    }

    public void setStepSound(String stepSound) {
        this.stepSound = stepSound != null ? stepSound : "BLOCK_STONE_STEP";
    }

    public String getHitSound() {
        return hitSound;
    }

    public void setHitSound(String hitSound) {
        this.hitSound = hitSound != null ? hitSound : "BLOCK_STONE_HIT";
    }

    public String getBreakParticles() {
        return breakParticles;
    }

    public void setBreakParticles(String breakParticles) {
        this.breakParticles = breakParticles != null ? breakParticles : "ITEM";
    }

    public List<BlockDropSpec> getDrops() {
        return drops;
    }

    public void setDrops(List<BlockDropSpec> drops) {
        this.drops = drops != null ? drops : new ArrayList<>();
    }

    public int getExpDrop() {
        return expDrop;
    }

    public void setExpDrop(int expDrop) {
        this.expDrop = Math.max(0, expDrop);
    }

    public double getBreakSpeedModifier() {
        return breakSpeedModifier;
    }

    public void setBreakSpeedModifier(double breakSpeedModifier) {
        this.breakSpeedModifier = Math.max(0.1, breakSpeedModifier);
    }

    public Sound resolveBreakSound() {
        return resolveSound(breakSound, Sound.BLOCK_STONE_BREAK);
    }

    public Sound resolvePlaceSound() {
        return resolveSound(placeSound, Sound.BLOCK_STONE_PLACE);
    }

    public Sound resolveStepSound() {
        return resolveSound(stepSound, Sound.BLOCK_STONE_STEP);
    }

    public Sound resolveHitSound() {
        return resolveSound(hitSound, Sound.BLOCK_STONE_HIT);
    }

    private Sound resolveSound(String name, Sound fallback) {
        if (name == null || name.isEmpty()) return fallback;
        try {
            return Sound.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public void saveToConfig(ConfigurationSection sec) {
        sec.set("hardness", hardness);
        sec.set("blast-resistance", blastResistance);
        sec.set("required-tool", requiredTool.name());
        sec.set("minimum-tier", minimumTier.name());
        sec.set("requires-correct-tool", requiresCorrectToolForDrops);
        sec.set("break-sound", breakSound);
        sec.set("place-sound", placeSound);
        sec.set("step-sound", stepSound);
        sec.set("hit-sound", hitSound);
        sec.set("break-particles", breakParticles);
        sec.set("exp-drop", expDrop);
        sec.set("break-speed-modifier", breakSpeedModifier);

        ConfigurationSection dropsSec = sec.createSection("drops");
        for (int i = 0; i < drops.size(); i++) {
            drops.get(i).saveToConfig(dropsSec.createSection("drop-" + i));
        }
    }

    public static BlockMiningSpec loadFromConfig(ConfigurationSection sec) {
        if (sec == null) return new BlockMiningSpec();
        BlockMiningSpec spec = new BlockMiningSpec();
        spec.setHardness((float) sec.getDouble("hardness", 1.5));
        spec.setBlastResistance((float) sec.getDouble("blast-resistance", 6.0));

        String toolStr = sec.getString("required-tool", "PICKAXE");
        try {
            spec.setRequiredTool(ToolType.valueOf(toolStr.toUpperCase(Locale.ROOT)));
        } catch (Exception ignored) {
            spec.setRequiredTool(ToolType.PICKAXE);
        }

        String tierStr = sec.getString("minimum-tier", "WOOD");
        try {
            spec.setMinimumTier(ToolTier.valueOf(tierStr.toUpperCase(Locale.ROOT)));
        } catch (Exception ignored) {
            spec.setMinimumTier(ToolTier.WOOD);
        }

        spec.setRequiresCorrectToolForDrops(sec.getBoolean("requires-correct-tool", true));
        spec.setBreakSound(sec.getString("break-sound", "BLOCK_STONE_BREAK"));
        spec.setPlaceSound(sec.getString("place-sound", "BLOCK_STONE_PLACE"));
        spec.setStepSound(sec.getString("step-sound", "BLOCK_STONE_STEP"));
        spec.setHitSound(sec.getString("hit-sound", "BLOCK_STONE_HIT"));
        spec.setBreakParticles(sec.getString("break-particles", "ITEM"));
        spec.setExpDrop(sec.getInt("exp-drop", 0));
        spec.setBreakSpeedModifier(sec.getDouble("break-speed-modifier", 1.0));

        ConfigurationSection dropsSec = sec.getConfigurationSection("drops");
        if (dropsSec != null) {
            List<BlockDropSpec> list = new ArrayList<>();
            for (String key : dropsSec.getKeys(false)) {
                ConfigurationSection d = dropsSec.getConfigurationSection(key);
                if (d != null) {
                    BlockDropSpec drop = BlockDropSpec.loadFromConfig(d);
                    if (drop != null) list.add(drop);
                }
            }
            spec.setDrops(list);
        }
        return spec;
    }

    public BlockMiningSpec copy() {
        BlockMiningSpec copy = new BlockMiningSpec();
        copy.setHardness(this.hardness);
        copy.setBlastResistance(this.blastResistance);
        copy.setRequiredTool(this.requiredTool);
        copy.setMinimumTier(this.minimumTier);
        copy.setRequiresCorrectToolForDrops(this.requiresCorrectToolForDrops);
        copy.setBreakSound(this.breakSound);
        copy.setPlaceSound(this.placeSound);
        copy.setStepSound(this.stepSound);
        copy.setHitSound(this.hitSound);
        copy.setBreakParticles(this.breakParticles);
        copy.setExpDrop(this.expDrop);
        copy.setBreakSpeedModifier(this.breakSpeedModifier);

        List<BlockDropSpec> dropsCopy = new ArrayList<>();
        for (BlockDropSpec d : this.drops) {
            dropsCopy.add(d.copy());
        }
        copy.setDrops(dropsCopy);
        return copy;
    }
}

