package org.bg52.synthetix.block;

import org.bukkit.Material;

/**
 * Required tool category for harvesting a custom block.
 */
public enum ToolType {
    PICKAXE,
    AXE,
    SHOVEL,
    HOE,
    SWORD,
    SHEARS,
    NONE,
    ANY;

    public boolean matches(Material toolMaterial) {
        if (this == ANY || this == NONE) return true;
        if (toolMaterial == null || toolMaterial.isAir()) return this == NONE;
        String name = toolMaterial.name();
        return switch (this) {
            case PICKAXE -> name.endsWith("_PICKAXE");
            case AXE -> name.endsWith("_AXE") && !name.endsWith("_PICKAXE");
            case SHOVEL -> name.endsWith("_SHOVEL");
            case HOE -> name.endsWith("_HOE");
            case SWORD -> name.endsWith("_SWORD");
            case SHEARS -> toolMaterial == Material.SHEARS;
            default -> true;
        };
    }
}

