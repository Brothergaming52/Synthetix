package org.bg52.synthetix.distribution;

import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.item.ItemDefinition;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Encapsulates rules and conditions for mob death item drops.
 */
public class MobDropRule {

    private final String id;
    private final String itemId;
    private final Material material;
    private final String entityType; // e.g. "WITHER_SKELETON", "ALL_HOSTILE", "ANY"
    private final double chance; // 0.0 to 1.0
    private final double lootingBonus; // per level
    private final int minAmount;
    private final int maxAmount;
    private final boolean playerKillOnly;
    private final Set<String> biomes;
    private final Set<String> worlds;

    public MobDropRule(String id, String item, String entityType, double chance, double lootingBonus,
                       int minAmount, int maxAmount, boolean playerKillOnly,
                       List<String> biomes, List<String> worlds) {
        this.id = id;
        this.entityType = entityType != null ? entityType.toUpperCase(Locale.ROOT) : "ANY";
        this.chance = Math.max(0.0, Math.min(1.0, chance));
        this.lootingBonus = Math.max(0.0, lootingBonus);
        this.minAmount = Math.max(1, minAmount);
        this.maxAmount = Math.max(this.minAmount, maxAmount);
        this.playerKillOnly = playerKillOnly;

        this.biomes = new HashSet<>();
        if (biomes != null) {
            for (String b : biomes) this.biomes.add(b.toLowerCase(Locale.ROOT));
        }

        this.worlds = new HashSet<>();
        if (worlds != null) {
            for (String w : worlds) this.worlds.add(w.toLowerCase(Locale.ROOT));
        }

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

    public boolean matches(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();

        // 1. Player kill check
        if (playerKillOnly && entity.getKiller() == null) {
            return false;
        }

        // 2. World check
        if (!worlds.isEmpty() && !worlds.contains(entity.getWorld().getName().toLowerCase(Locale.ROOT))) {
            return false;
        }

        // 3. Biome check
        if (!biomes.isEmpty()) {
            String currentBiome = entity.getLocation().getBlock().getBiome().name().toLowerCase(Locale.ROOT);
            if (!biomes.contains(currentBiome)) return false;
        }

        // 4. Entity type match
        if ("ANY".equals(entityType)) return true;
        if ("ALL_HOSTILE".equals(entityType)) return entity instanceof Monster;
        if ("ALL_PASSIVE".equals(entityType)) return !(entity instanceof Monster);

        try {
            EntityType et = EntityType.valueOf(entityType);
            return entity.getType() == et;
        } catch (IllegalArgumentException e) {
            return entityType.equalsIgnoreCase(entity.getType().name());
        }
    }

    public ItemStack rollDrop(EntityDeathEvent event, SynthetixPlugin plugin) {
        int lootingLevel = 0;
        Player killer = event.getEntity().getKiller();
        if (killer != null && killer.getInventory().getItemInMainHand().getType() != Material.AIR) {
            lootingLevel = killer.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.LOOTING);
        }

        double totalChance = Math.min(1.0, chance + (lootingBonus * lootingLevel));
        if (ThreadLocalRandom.current().nextDouble() > totalChance) {
            return null;
        }

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
                    return null;
                }
            }
        } else if (material != null && material != Material.AIR) {
            item = new ItemStack(material);
        } else {
            return null;
        }
        item.setAmount(amount);
        return item;
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

    public String getEntityType() {
        return entityType;
    }

    public double getChance() {
        return chance;
    }

    public double getLootingBonus() {
        return lootingBonus;
    }

    public int getMinAmount() {
        return minAmount;
    }

    public int getMaxAmount() {
        return maxAmount;
    }

    public boolean isPlayerKillOnly() {
        return playerKillOnly;
    }

    public Set<String> getBiomes() {
        return Collections.unmodifiableSet(biomes);
    }

    public Set<String> getWorlds() {
        return Collections.unmodifiableSet(worlds);
    }
}

