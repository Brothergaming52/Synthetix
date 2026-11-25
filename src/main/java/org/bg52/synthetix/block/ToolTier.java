package org.bg52.synthetix.block;

import org.bukkit.Material;

/**
 * Minimum tool material tier required to harvest a custom block.
 */
public enum ToolTier {
    WOOD(0),
    STONE(1),
    IRON(2),
    DIAMOND(3),
    NETHERITE(4);

    private final int level;

    ToolTier(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public static ToolTier fromMaterial(Material mat) {
        if (mat == null) return WOOD;
        String name = mat.name();
        if (name.startsWith("NETHERITE_")) return NETHERITE;
        if (name.startsWith("DIAMOND_")) return DIAMOND;
        if (name.startsWith("IRON_") || name.startsWith("COPPER_")) return IRON;
        if (name.startsWith("STONE_") || name.startsWith("GOLDEN_")) return STONE;
        return WOOD;
    }

    public boolean isSufficient(Material tool) {
        return fromMaterial(tool).getLevel() >= this.level;
    }
}

