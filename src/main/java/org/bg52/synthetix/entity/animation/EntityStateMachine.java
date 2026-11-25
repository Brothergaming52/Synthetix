package org.bg52.synthetix.entity.animation;

import org.bg52.synthetix.entity.runtime.CustomEntityInstance;

/**
 * State machine managing behavioral state transitions and coupling to animation playback.
 */
public class EntityStateMachine {

    private final CustomEntityInstance instance;
    private EntityState currentState = EntityState.IDLE;
    private float stateTimeTicks = 0.0f;

    public EntityStateMachine(CustomEntityInstance instance) {
        this.instance = instance;
    }

    public EntityState getCurrentState() {
        return currentState;
    }

    public float getStateTimeTicks() {
        return stateTimeTicks;
    }

    public void transitionTo(EntityState newState) {
        transitionTo(newState, 3.0f);
    }

    public void transitionTo(EntityState newState, float blendTicks) {
        if (newState == null) return;
        if (this.currentState == newState && newState.isDefaultLooping()) return;

        this.currentState = newState;
        this.stateTimeTicks = 0.0f;

        // Automatically trigger coupled animation track if present
        String trackName = newState.getDefaultTrackName();
        AnimationController anim = instance.getAnimationController();
        if (anim != null && anim.hasTrack(trackName)) {
            anim.play(trackName, newState.isDefaultLooping(), blendTicks);
        }

        instance.onStateChange(newState);
    }

    public void tick(float deltaTicks) {
        this.stateTimeTicks += deltaTicks;

        // Auto-revert non-looping transient states (like ATTACK, HURT, SPAWN) back to IDLE when done
        if (!currentState.isDefaultLooping()) {
            AnimationController anim = instance.getAnimationController();
            if (anim != null && anim.isFinished()) {
                transitionTo(EntityState.IDLE, 4.0f);
            }
        }
    }
}

