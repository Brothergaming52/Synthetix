package org.bg52.synthetix.api.event;

import org.bukkit.event.Event;

/**
 * Base class for all custom events emitted by the Synthetix core engine.
 */
public abstract class SynthetixEvent extends Event {

    public SynthetixEvent() {
        super();
    }

    public SynthetixEvent(boolean isAsync) {
        super(isAsync);
    }
}

