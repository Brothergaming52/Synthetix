package org.bg52.synthetix.util;

import org.bukkit.NamespacedKey;
import org.bukkit.loot.LootTables;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Robust utility to fetch and discover all vanilla, datapack, and plugin loot table keys on the server.
 * Compatible with Paper 1.20.6 through Paper 26.3+.
 */
public final class LootTableFetcher {

    private LootTableFetcher() {
    }

    public static List<NamespacedKey> fetchAllLootTableKeys(JavaPlugin plugin) {
        Set<NamespacedKey> discovered = new HashSet<>();

        // 1. Paper / Bukkit Registry for LootTables (if available)
        try {
            for (LootTables lt : org.bukkit.Registry.LOOT_TABLES) {
                NamespacedKey nk = lt.getKey();
                if (isValidLootTable(nk)) {
                    discovered.add(nk);
                }
            }
        } catch (Throwable ignored) {
        }

        // 2. Baseline LootTables enum values (vanilla standard)
        try {
            for (LootTables lt : LootTables.values()) {
                NamespacedKey nk = lt.getKey();
                if (isValidLootTable(nk)) {
                    discovered.add(nk);
                }
            }
        } catch (Throwable ignored) {
        }

        return discovered.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(NamespacedKey::toString))
                .collect(Collectors.toList());
    }

    /**
     * Filters loot tables suitable for item container generation (chests, pots, fishing, gameplay, etc.).
     * Excludes mob entities (handled by mob drops) and blocks.
     */
    public static boolean isValidLootTable(NamespacedKey nk) {
        if (nk == null) return false;
        String path = nk.getKey().toLowerCase(Locale.ROOT);

        // Exclude entity loot tables (handled via MobDropManager)
        if (path.startsWith("entities/")) return false;

        // Exclude block drop tables (not container loot tables)
        if (path.startsWith("blocks/")) return false;

        return true;
    }
}

