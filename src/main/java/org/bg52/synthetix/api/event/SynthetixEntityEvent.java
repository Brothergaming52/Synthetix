package org.bg52.synthetix.api.event;

import org.bg52.synthetix.entity.model.CustomEntityDefinition;
import org.bg52.synthetix.entity.runtime.CustomEntityInstance;

/**
 * Base event for actions involving custom Synthetix entities.
 */
public abstract class SynthetixEntityEvent extends SynthetixEvent {

    private final CustomEntityInstance entityInstance;

    public SynthetixEntityEvent(CustomEntityInstance entityInstance) {
        this.entityInstance = entityInstance;
    }

    public CustomEntityInstance getEntityInstance() {
        return entityInstance;
    }

    public CustomEntityDefinition getDefinition() {
        return entityInstance != null ? entityInstance.getDefinition() : null;
    }

    public String getEntityId() {
        return getDefinition() != null ? getDefinition().getId() : null;
    }
}

