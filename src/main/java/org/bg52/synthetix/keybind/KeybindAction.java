package org.bg52.synthetix.keybind;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Definition of a keybind action registered by Synthetix or downstream addon plugins.
 */
public class KeybindAction {

    private final String id;
    private final KeybindType type;
    private int requiredHotbarSlot = -1; // -1 for any slot; 0-8 for specific hotbar slot
    private Boolean requireSneaking = null; // null = ignore, true = must sneak, false = must not sneak
    private Boolean requireEmptyHand = null; // null = ignore, true = hand empty, false = must hold item
    private long timeoutMs = 450; // Timeout window for multi-actions like DOUBLE_SNEAK
    private long holdDurationMs = 500; // Hold threshold for SNEAK_HOLD
    private long cooldownMs = 500; // Cooldown after successful execution
    private Consumer<KeybindContext> handler;

    public KeybindAction(String id, KeybindType type) {
        this.id = Objects.requireNonNull(id, "Keybind ID cannot be null");
        this.type = Objects.requireNonNull(type, "KeybindType cannot be null");
    }

    public static Builder builder(String id, KeybindType type) {
        return new Builder(id, type);
    }

    /**
     * Parses a KeybindAction from a Bukkit ConfigurationSection.
     * Compatible with custom plugin configurations.
     *
     * @param id The unique identifier
     * @param section The configuration section
     * @param handler Action callback
     * @return Built KeybindAction
     */
    public static KeybindAction fromConfig(String id, ConfigurationSection section, Consumer<KeybindContext> handler) {
        if (section == null) return null;

        String typeStr = section.getString("type", "DOUBLE_SNEAK");
        KeybindType type;
        try {
            type = KeybindType.valueOf(typeStr.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            type = KeybindType.DOUBLE_SNEAK;
        }

        KeybindAction action = new KeybindAction(id, type);

        if (section.contains("hotbar-slot")) {
            int slot = section.getInt("hotbar-slot", -1);
            // Convert 1-9 to 0-8 if player-facing 1-indexed notation is used
            if (slot >= 1 && slot <= 9) {
                action.setRequiredHotbarSlot(slot - 1);
            } else {
                action.setRequiredHotbarSlot(slot);
            }
        }

        if (section.contains("require-sneaking")) {
            action.setRequireSneaking(section.getBoolean("require-sneaking"));
        }

        if (section.contains("require-empty-hand")) {
            action.setRequireEmptyHand(section.getBoolean("require-empty-hand"));
        }

        if (section.contains("timeout-ms")) {
            action.setTimeoutMs(section.getLong("timeout-ms", 450));
        }

        if (section.contains("hold-duration-ms")) {
            action.setHoldDurationMs(section.getLong("hold-duration-ms", 500));
        }

        if (section.contains("cooldown-ms")) {
            action.setCooldownMs(section.getLong("cooldown-ms", 500));
        }

        action.setHandler(handler);
        return action;
    }

    public String getId() {
        return id;
    }

    public KeybindType getType() {
        return type;
    }

    public int getRequiredHotbarSlot() {
        return requiredHotbarSlot;
    }

    public KeybindAction setRequiredHotbarSlot(int requiredHotbarSlot) {
        this.requiredHotbarSlot = requiredHotbarSlot;
        return this;
    }

    public Boolean getRequireSneaking() {
        return requireSneaking;
    }

    public KeybindAction setRequireSneaking(Boolean requireSneaking) {
        this.requireSneaking = requireSneaking;
        return this;
    }

    public Boolean getRequireEmptyHand() {
        return requireEmptyHand;
    }

    public KeybindAction setRequireEmptyHand(Boolean requireEmptyHand) {
        this.requireEmptyHand = requireEmptyHand;
        return this;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public KeybindAction setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
        return this;
    }

    public long getHoldDurationMs() {
        return holdDurationMs;
    }

    public KeybindAction setHoldDurationMs(long holdDurationMs) {
        this.holdDurationMs = holdDurationMs;
        return this;
    }

    public long getCooldownMs() {
        return cooldownMs;
    }

    public KeybindAction setCooldownMs(long cooldownMs) {
        this.cooldownMs = cooldownMs;
        return this;
    }

    public Consumer<KeybindContext> getHandler() {
        return handler;
    }

    public KeybindAction setHandler(Consumer<KeybindContext> handler) {
        this.handler = handler;
        return this;
    }

    public static class Builder {
        private final String id;
        private final KeybindType type;
        private int requiredHotbarSlot = -1;
        private Boolean requireSneaking = null;
        private Boolean requireEmptyHand = null;
        private long timeoutMs = 450;
        private long holdDurationMs = 500;
        private long cooldownMs = 500;
        private Consumer<KeybindContext> handler;

        public Builder(String id, KeybindType type) {
            this.id = id;
            this.type = type;
        }

        public Builder hotbarSlot(int slot) {
            this.requiredHotbarSlot = slot;
            return this;
        }

        public Builder requireSneaking(Boolean sneaking) {
            this.requireSneaking = sneaking;
            return this;
        }

        public Builder requireEmptyHand(Boolean emptyHand) {
            this.requireEmptyHand = emptyHand;
            return this;
        }

        public Builder timeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public Builder holdDurationMs(long holdDurationMs) {
            this.holdDurationMs = holdDurationMs;
            return this;
        }

        public Builder cooldownMs(long cooldownMs) {
            this.cooldownMs = cooldownMs;
            return this;
        }

        public Builder handler(Consumer<KeybindContext> handler) {
            this.handler = handler;
            return this;
        }

        public KeybindAction build() {
            KeybindAction action = new KeybindAction(id, type);
            action.setRequiredHotbarSlot(requiredHotbarSlot);
            action.setRequireSneaking(requireSneaking);
            action.setRequireEmptyHand(requireEmptyHand);
            action.setTimeoutMs(timeoutMs);
            action.setHoldDurationMs(holdDurationMs);
            action.setCooldownMs(cooldownMs);
            action.setHandler(handler);
            return action;
        }
    }
}

