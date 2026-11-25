package org.bg52.synthetix.util;

import org.bukkit.Bukkit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance version and capability detector for Minecraft Paper servers.
 * Supports legacy 1.20.6, modern 1.21.x, and future calendar-versioned releases (e.g., Paper 26.3+).
 */
public final class VersionChecker {

    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");
    private static final int majorVersion;
    private static final int minorVersion;
    private static final int patchVersion;

    private static final boolean supportsDataComponents;
    private static final boolean supportsItemModel;
    private static final boolean supportsAdventure;

    static {
        int maj = 1;
        int min = 20;
        int patch = 6;

        try {
            String vString = Bukkit.getBukkitVersion();
            Matcher matcher = VERSION_PATTERN.matcher(vString);
            if (matcher.find()) {
                maj = Integer.parseInt(matcher.group(1));
                min = Integer.parseInt(matcher.group(2));
                patch = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
            }
        } catch (Throwable ignored) {
            // Keep safe fallback to 1.20.6
        }

        majorVersion = maj;
        minorVersion = min;
        patchVersion = patch;

        // Detect Paper DataComponentTypes
        boolean dc = false;
        try {
            Class<?> clazz = Class.forName("io.papermc.paper.datacomponent.DataComponentTypes");
            dc = (clazz != null);
        } catch (Throwable ignored) {
        }
        supportsDataComponents = dc;

        // Detect 1.21.2+ / 26.x ItemMeta#setItemModel
        boolean im = false;
        try {
            Class<?> metaClass = Class.forName("org.bukkit.inventory.meta.ItemMeta");
            metaClass.getMethod("setItemModel", org.bukkit.NamespacedKey.class);
            im = true;
        } catch (Throwable ignored) {
        }
        supportsItemModel = im;

        // Detect Kyori Adventure
        boolean adv = false;
        try {
            Class<?> comp = Class.forName("net.kyori.adventure.text.Component");
            adv = (comp != null);
        } catch (Throwable ignored) {
        }
        supportsAdventure = adv;
    }

    private VersionChecker() {}

    public static int getMajor() {
        return majorVersion;
    }

    public static int getMinor() {
        return minorVersion;
    }

    public static int getPatch() {
        return patchVersion;
    }

    public static boolean isAtLeast(int minMajor, int minMinor, int minPatch) {
        if (majorVersion > minMajor) return true;
        if (majorVersion < minMajor) return false;
        if (minorVersion > minMinor) return true;
        if (minorVersion < minMinor) return false;
        return patchVersion >= minPatch;
    }

    public static boolean isAtLeast(int minMajor, int minMinor) {
        return isAtLeast(minMajor, minMinor, 0);
    }

    /**
     * Checks if server is Paper 26.x or newer (calendar versioning).
     */
    public static boolean isPaperModernEra() {
        return majorVersion >= 26 || (majorVersion == 1 && minorVersion >= 21);
    }

    public static boolean supportsDataComponents() {
        return supportsDataComponents;
    }

    public static boolean supportsItemModel() {
        return supportsItemModel;
    }

    public static boolean supportsAdventure() {
        return supportsAdventure;
    }

    public static String getFullVersionString() {
        return majorVersion + "." + minorVersion + "." + patchVersion;
    }
}

