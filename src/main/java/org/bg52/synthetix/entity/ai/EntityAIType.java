package org.bg52.synthetix.entity.ai;

/**
 * Standard behavioral AI archetypes for custom entities.
 */
public enum EntityAIType {
    /**
     * Peaceful entity that wanders, grazes, looks at players, and flees when attacked.
     */
    PASSIVE,

    /**
     * Aggressive entity that targets players or monsters and attacks in melee combat.
     */
    HOSTILE_MELEE,

    /**
     * Aggressive leaping entity that hops/lunges toward targets and deals damage on landing/contact (e.g. Mimic, Slime).
     */
    HOSTILE_LEAP,

    /**
     * Aggressive entity that attacks from a distance using projectiles.
     */
    HOSTILE_RANGED,

    /**
     * Neutral entity that only becomes hostile when attacked or provoked.
     */
    NEUTRAL,

    /**
     * Static boss or sentry entity that does not wander or move from its spawn location.
     */
    STATIONARY,

    /**
     * Fully scripted AI driven exclusively by external plugins via the Synthetix API.
     */
    CUSTOM
}

