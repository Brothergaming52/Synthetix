package org.bg52.synthetix.block;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Natural world generation & ore spawning configuration for custom blocks.
 */
public class BlockWorldGenSpec {

    private boolean enabled;
    private int minHeight;
    private int maxHeight;
    private int veinSize;
    private int veinsPerChunk;
    private Material replaceTarget;
    private List<String> biomes;

    public BlockWorldGenSpec() {
        this.enabled = false;
        this.minHeight = -64;
        this.maxHeight = 128;
        this.veinSize = 8;
        this.veinsPerChunk = 4;
        this.replaceTarget = Material.STONE;
        this.biomes = new ArrayList<>();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMinHeight() {
        return minHeight;
    }

    public void setMinHeight(int minHeight) {
        this.minHeight = minHeight;
    }

    public int getMaxHeight() {
        return maxHeight;
    }

    public void setMaxHeight(int maxHeight) {
        this.maxHeight = maxHeight;
    }

    public int getVeinSize() {
        return veinSize;
    }

    public void setVeinSize(int veinSize) {
        this.veinSize = Math.max(1, veinSize);
    }

    public int getVeinsPerChunk() {
        return veinsPerChunk;
    }

    public void setVeinsPerChunk(int veinsPerChunk) {
        this.veinsPerChunk = Math.max(0, veinsPerChunk);
    }

    public Material getReplaceTarget() {
        return replaceTarget;
    }

    public void setReplaceTarget(Material replaceTarget) {
        this.replaceTarget = replaceTarget != null ? replaceTarget : Material.STONE;
    }

    public List<String> getBiomes() {
        return biomes;
    }

    public void setBiomes(List<String> biomes) {
        this.biomes = biomes != null ? biomes : new ArrayList<>();
    }

    public void saveToConfig(ConfigurationSection sec) {
        sec.set("enabled", enabled);
        sec.set("min-height", minHeight);
        sec.set("max-height", maxHeight);
        sec.set("vein-size", veinSize);
        sec.set("veins-per-chunk", veinsPerChunk);
        sec.set("replace-target", replaceTarget.name());
        if (!biomes.isEmpty()) sec.set("biomes", biomes);
    }

    public static BlockWorldGenSpec loadFromConfig(ConfigurationSection sec) {
        if (sec == null) return new BlockWorldGenSpec();
        BlockWorldGenSpec spec = new BlockWorldGenSpec();
        spec.setEnabled(sec.getBoolean("enabled", false));
        spec.setMinHeight(sec.getInt("min-height", -64));
        spec.setMaxHeight(sec.getInt("max-height", 128));
        spec.setVeinSize(sec.getInt("vein-size", 8));
        spec.setVeinsPerChunk(sec.getInt("veins-per-chunk", 4));

        String repStr = sec.getString("replace-target", "STONE");
        Material repMat = Material.matchMaterial(repStr);
        spec.setReplaceTarget(repMat != null ? repMat : Material.STONE);

        spec.setBiomes(sec.getStringList("biomes"));
        return spec;
    }

    public BlockWorldGenSpec copy() {
        BlockWorldGenSpec copy = new BlockWorldGenSpec();
        copy.setEnabled(this.enabled);
        copy.setMinHeight(this.minHeight);
        copy.setMaxHeight(this.maxHeight);
        copy.setVeinSize(this.veinSize);
        copy.setVeinsPerChunk(this.veinsPerChunk);
        copy.setReplaceTarget(this.replaceTarget);
        copy.setBiomes(new ArrayList<>(this.biomes));
        return copy;
    }
}

