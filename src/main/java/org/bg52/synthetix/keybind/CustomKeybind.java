package org.bg52.synthetix.keybind;

import java.util.Objects;

/**
 * Model representing a user-configurable custom keybind persisted in keybinds.yml.
 */
public class CustomKeybind {

    private String id;
    private KeybindType type;
    private int hotbarSlot; // -1 for any slot; 0-8 for specific hotbar slot
    private String command;
    private boolean asPlayer;
    private long cooldownMs;
    private boolean enabled;

    public CustomKeybind(String id, KeybindType type, int hotbarSlot, String command, boolean asPlayer, long cooldownMs, boolean enabled) {
        this.id = Objects.requireNonNull(id, "ID cannot be null");
        this.type = type != null ? type : KeybindType.DOUBLE_SNEAK;
        this.hotbarSlot = hotbarSlot;
        this.command = command != null ? command : "";
        this.asPlayer = asPlayer;
        this.cooldownMs = Math.max(0, cooldownMs);
        this.enabled = enabled;
    }

    public CustomKeybind(String id) {
        this(id, KeybindType.DOUBLE_SNEAK, -1, "", true, 500, true);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public KeybindType getType() {
        return type;
    }

    public void setType(KeybindType type) {
        this.type = type != null ? type : KeybindType.DOUBLE_SNEAK;
    }

    public int getHotbarSlot() {
        return hotbarSlot;
    }

    public void setHotbarSlot(int hotbarSlot) {
        this.hotbarSlot = hotbarSlot;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public boolean isAsPlayer() {
        return asPlayer;
    }

    public void setAsPlayer(boolean asPlayer) {
        this.asPlayer = asPlayer;
    }

    public long getCooldownMs() {
        return cooldownMs;
    }

    public void setCooldownMs(long cooldownMs) {
        this.cooldownMs = Math.max(0, cooldownMs);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Converts this CustomKeybind into an executable KeybindAction.
     */
    public KeybindAction toAction() {
        return KeybindAction.builder(id, type)
                .hotbarSlot(hotbarSlot)
                .cooldownMs(cooldownMs)
                .build();
    }
}

