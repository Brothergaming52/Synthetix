package org.bg52.synthetix.entity.animation;

/**
 * High-level behavioral and animation state of a custom entity.
 */
public enum EntityState {
    IDLE("idle", true),
    WALK("walk", true),
    ATTACK("attack", false),
    HURT("hurt", false),
    DEATH("death", false),
    SPAWN("spawn", false),
    CUSTOM("custom", false);

    private final String defaultTrackName;
    private final boolean defaultLooping;

    EntityState(String defaultTrackName, boolean defaultLooping) {
        this.defaultTrackName = defaultTrackName;
        this.defaultLooping = defaultLooping;
    }

    public String getDefaultTrackName() {
        return defaultTrackName;
    }

    public boolean isDefaultLooping() {
        return defaultLooping;
    }
}

