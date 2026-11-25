package org.bg52.synthetix.keybind;

/**
 * Supported trigger input patterns for the universal keybind engine.
 */
public enum KeybindType {
    /** Double-tap sneak (Shift twice within timeout). */
    DOUBLE_SNEAK,
    /** Single quick sneak tap (press and release Shift). */
    SNEAK_TAP,
    /** Press and hold sneak for a minimum duration. */
    SNEAK_HOLD,
    /** Swap hand items key (F). */
    SWAP_HAND,
    /** Shift + Swap hand items key (Shift + F). */
    SNEAK_SWAP_HAND,
    /** Drop item key (Q). */
    DROP_ITEM,
    /** Shift + Drop item key (Shift + Q). */
    SNEAK_DROP_ITEM,
    /** Right click in air or block. */
    RIGHT_CLICK,
    /** Shift + Right click. */
    SNEAK_RIGHT_CLICK,
    /** Left click in air or block. */
    LEFT_CLICK,
    /** Shift + Left click. */
    SNEAK_LEFT_CLICK,
    /** Switching to a specific hotbar slot. */
    HOTBAR_SWITCH
}

