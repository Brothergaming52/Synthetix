package org.bg52.synthetix.util;

import org.bukkit.Material;

/**
 * Utility for safe Material and ItemStack conversions across all Paper/Minecraft versions.
 * Prevents "XYZ isn't an item" IllegalArgumentException when creating GUI icons.
 */
public final class MaterialUtil {

    private MaterialUtil() {}

    /**
     * Converts any Material (including block-only materials like TRIPWIRE, WATER,
     * POTTED plants, WALL torches/banners/heads) safely into a valid item Material.
     */
    public static Material toItemMaterial(Material mat) {
        if (mat == null || mat.isAir()) return Material.BARRIER;
        if (mat.isItem()) return mat;

        return switch (mat) {
            case TRIPWIRE -> Material.STRING;
            case REDSTONE_WIRE -> Material.REDSTONE;
            case WATER -> Material.WATER_BUCKET;
            case LAVA -> Material.LAVA_BUCKET;
            case FIRE, SOUL_FIRE -> Material.FLINT_AND_STEEL;
            case PISTON_HEAD, MOVING_PISTON -> Material.PISTON;
            case NETHER_PORTAL, END_PORTAL -> Material.OBSIDIAN;
            case END_GATEWAY -> Material.END_STONE;
            case POWDER_SNOW -> Material.POWDER_SNOW_BUCKET;
            case FROSTED_ICE -> Material.ICE;
            case BUBBLE_COLUMN -> Material.WATER_BUCKET;
            case SWEET_BERRY_BUSH -> Material.SWEET_BERRIES;
            case CAVE_VINES, CAVE_VINES_PLANT -> Material.GLOW_BERRIES;
            case KELP_PLANT -> Material.KELP;
            case BAMBOO_SAPLING -> Material.BAMBOO;
            case BIG_DRIPLEAF_STEM, SMALL_DRIPLEAF -> Material.BIG_DRIPLEAF;
            default -> resolveByPattern(mat);
        };
    }

    private static Material resolveByPattern(Material mat) {
        String name = mat.name();
        if (name.endsWith("_WALL_HEAD")) {
            Material m = Material.matchMaterial(name.replace("_WALL_HEAD", "_HEAD"));
            if (m != null && m.isItem()) return m;
        }
        if (name.endsWith("_WALL_SKULL")) {
            Material m = Material.matchMaterial(name.replace("_WALL_SKULL", "_SKULL"));
            if (m != null && m.isItem()) return m;
        }
        if (name.endsWith("_WALL_BANNER")) {
            Material m = Material.matchMaterial(name.replace("_WALL_BANNER", "_BANNER"));
            if (m != null && m.isItem()) return m;
        }
        if (name.endsWith("_WALL_FAN")) {
            Material m = Material.matchMaterial(name.replace("_WALL_FAN", "_FAN"));
            if (m != null && m.isItem()) return m;
        }
        if (name.endsWith("_WALL_SIGN")) {
            Material m = Material.matchMaterial(name.replace("_WALL_SIGN", "_SIGN"));
            if (m != null && m.isItem()) return m;
        }
        if (name.endsWith("_WALL_HANGING_SIGN")) {
            Material m = Material.matchMaterial(name.replace("_WALL_HANGING_SIGN", "_HANGING_SIGN"));
            if (m != null && m.isItem()) return m;
        }
        if (name.endsWith("_WALL_TORCH")) {
            Material m = Material.matchMaterial(name.replace("_WALL_TORCH", "_TORCH"));
            if (m != null && m.isItem()) return m;
        }
        if (name.startsWith("POTTED_")) {
            return Material.FLOWER_POT;
        }
        return Material.STONE;
    }
}

