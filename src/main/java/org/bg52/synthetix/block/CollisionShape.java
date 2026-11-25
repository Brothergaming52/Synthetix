package org.bg52.synthetix.block;

/**
 * Collision box geometry definition for custom blocks.
 */
public enum CollisionShape {
    /**
     * Standard 1x1x1 full solid cube collision.
     */
    SOLID_CUBE,

    /**
     * Completely non-collidable; entities and projectiles pass straight through (Structure Void).
     */
    PASS_THROUGH,

    /**
     * 0.5m bottom slab collision.
     */
    SLAB_BOTTOM,

    /**
     * 0.5m top slab collision.
     */
    SLAB_TOP,

    /**
     * Standard ascending staircase step collision.
     */
    STAIRS,

    /**
     * 1.5m tall fence barrier (prevents vanilla jumping over).
     */
    FENCE,

    /**
     * Thin 1-pixel (0.0625m) carpet collision.
     */
    CARPET,

    /**
     * Scaled continuous sub-block bounding box via virtual invisible shulker.
     */
    SHULKER_SCALED
}

