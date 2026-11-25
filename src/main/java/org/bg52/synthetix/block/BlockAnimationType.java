package org.bg52.synthetix.block;

/**
 * Types of dynamic client-interpolated motion for custom block model parts.
 */
public enum BlockAnimationType {
    /**
     * Completely static part.
     */
    STATIC,

    /**
     * Continuous continuous yaw rotation (e.g. windmills, fans, radar).
     */
    ROTATION_Y,

    /**
     * Continuous pitch rotation (e.g. wheels, rollers, gears).
     */
    ROTATION_X,

    /**
     * Continuous roll rotation (e.g. turbines).
     */
    ROTATION_Z,

    /**
     * Floating bobbing and spinning (e.g. enchanted crystals, mystical artifacts).
     */
    BOB_AND_SPIN,

    /**
     * Linear forward and back translation stroke (e.g. industrial pistons, stampers).
     */
    PISTON_STROKE,

    /**
     * Smooth quaternion interpolated transitions triggered on events (doors, levers).
     */
    INTERPOLATED
}

