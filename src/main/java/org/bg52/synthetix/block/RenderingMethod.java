package org.bg52.synthetix.block;

/**
 * Visual rendering method for a custom block.
 */
public enum RenderingMethod {
    /**
     * Modern Display Entity (ItemDisplay) with CustomModelData and 3D transformation matrices.
     * Unlimited unique models, smooth client interpolation, zero server ticking.
     */
    ITEM_DISPLAY,

    /**
     * BlockDisplay entity for vanilla block state models or textures.
     */
    BLOCK_DISPLAY,

    /**
     * Legacy Note Block instrument/pitch blockstate mapping.
     */
    NOTE_BLOCK,

    /**
     * Legacy Tripwire string blockstate mapping.
     */
    TRIPWIRE,

    /**
     * Composite entity composed of multiple distinct static and moving Display entities.
     */
    MULTI_MODEL_COMPOSITE
}

