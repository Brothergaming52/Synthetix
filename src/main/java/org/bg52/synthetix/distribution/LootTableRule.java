package org.bg52.synthetix.distribution;

import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.item.ItemDefinition;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Encapsulates rules for injecting items into Minecraft server loot tables (dungeons, trials, archaeology, fishing, etc.).
 */
public class LootTableRule {

    private final String id;
    private final String itemId;
    private final Material material;
    private final List<String> patterns;
    private final double chance;
    private final int minAmount;
    private final int maxAmount;
    private final int rolls;

    public LootTableRule(String id, String item, List<String> patterns, double chance, int minAmount, int maxAmount, int rolls) {
        this.id = id;
        this.patterns = patterns != null ? patterns : Collections.emptyList();
        this.chance = Math.max(0.0, Math.min(1.0, chance));
        this.minAmount = Math.max(1, minAmount);
        this.maxAmount = Math.max(this.minAmount, maxAmount);
        this.rolls = Math.max(1, rolls);

        if (item != null && item.toLowerCase(Locale.ROOT).startsWith("synth:")) {
            this.itemId = item.substring(6).toLowerCase(Locale.ROOT);
            this.material = null;
        } else {
            Material m = Material.matchMaterial(item != null ? item : "AIR");
            if (m != null && m != Material.AIR) {
                this.material = m;
                this.itemId = null;
            } else {
                this.itemId = item != null ? item.toLowerCase(Locale.ROOT) : "barrier";
                this.material = null;
            }
        }
    }

    public boolean matches(NamespacedKey tableKey) {
        if (tableKey == null) return false;
        String keyStr = tableKey.asString().toLowerCase(Locale.ROOT);

        for (String p : patterns) {
            String cleanPattern = p.trim().toLowerCase(Locale.ROOT);
            if ("*".equals(cleanPattern)) return true;

            if (cleanPattern.endsWith("/*")) {
                String prefix = cleanPattern.substring(0, cleanPattern.length() - 1); // remove '*'
                if (keyStr.startsWith(prefix)) return true;
            } else if (cleanPattern.endsWith("*")) {
                String prefix = cleanPattern.substring(0, cleanPattern.length() - 1);
                if (keyStr.startsWith(prefix)) return true;
            } else if (keyStr.equalsIgnoreCase(cleanPattern)) {
                return true;
            }
        }
        return false;
    }

    public List<ItemStack> rollLoot(SynthetixPlugin plugin) {
        List<ItemStack> result = new ArrayList<>();
        for (int r = 0; r < rolls; r++) {
            if (ThreadLocalRandom.current().nextDouble() <= chance) {
                int amount = minAmount == maxAmount ? minAmount : ThreadLocalRandom.current().nextInt(minAmount, maxAmount + 1);
                ItemStack item;
                if (itemId != null) {
                    ItemDefinition def = plugin.getItemRegistry().get(itemId);
                    if (def != null) {
                        item = def.buildItemStack();
                    } else {
                        var blockDef = plugin.getCustomBlockRegistry() != null ? plugin.getCustomBlockRegistry().get(itemId) : null;
                        if (blockDef != null) {
                            item = blockDef.buildBlockItemStack(1);
                        } else {
                            continue;
                        }
                    }
                } else if (material != null && material != Material.AIR) {
                    item = new ItemStack(material);
                } else {
                    continue;
                }
                item.setAmount(amount);
                result.add(item);
            }
        }
        return result;
    }

    public String getId() {
        return id;
    }

    public String getItemId() {
        return itemId;
    }

    public Material getMaterial() {
        return material;
    }

    public List<String> getPatterns() {
        return patterns;
    }

    public double getChance() {
        return chance;
    }

    public int getMinAmount() {
        return minAmount;
    }

    public int getMaxAmount() {
        return maxAmount;
    }

    public int getRolls() {
        return rolls;
    }
}

