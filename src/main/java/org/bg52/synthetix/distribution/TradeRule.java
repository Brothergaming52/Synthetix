package org.bg52.synthetix.distribution;

import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.item.ItemDefinition;
import org.bg52.synthetix.recipe.IngredientChoice;
import org.bukkit.Material;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import java.util.Locale;

/**
 * Encapsulates rules for custom villager profession trades and wandering trader offerings.
 */
public class TradeRule {

    private final String id;
    private final String profession; // e.g. "CLERIC", "WANDERING_TRADER", "ANY"
    private final int level; // 1 to 5 (Novice to Master)
    private final IngredientChoice cost1;
    private final int cost1Amount;
    private final IngredientChoice cost2;
    private final int cost2Amount;
    private final String resultItemId;
    private final Material resultMaterial;
    private final int resultAmount;
    private final int maxUses;
    private final int villagerExperience;
    private final float priceMultiplier;
    private final double chance;

    public TradeRule(String id, String profession, int level,
                     String cost1Str, int cost1Amount,
                     String cost2Str, int cost2Amount,
                     String resultStr, int resultAmount,
                     int maxUses, int villagerExperience, float priceMultiplier, double chance) {
        this.id = id;
        this.profession = profession != null ? profession.toUpperCase(Locale.ROOT) : "ANY";
        this.level = Math.max(1, Math.min(5, level));
        this.cost1 = new IngredientChoice(cost1Str != null ? cost1Str : "EMERALD");
        this.cost1Amount = Math.max(1, cost1Amount);
        this.cost2 = cost2Str != null && !cost2Str.isEmpty() ? new IngredientChoice(cost2Str) : null;
        this.cost2Amount = Math.max(1, cost2Amount);
        this.resultAmount = Math.max(1, resultAmount);
        this.maxUses = Math.max(1, maxUses);
        this.villagerExperience = Math.max(0, villagerExperience);
        this.priceMultiplier = priceMultiplier;
        this.chance = Math.max(0.0, Math.min(1.0, chance));

        if (resultStr != null && resultStr.toLowerCase(Locale.ROOT).startsWith("synth:")) {
            this.resultItemId = resultStr.substring(6).toLowerCase(Locale.ROOT);
            this.resultMaterial = null;
        } else {
            Material m = Material.matchMaterial(resultStr != null ? resultStr : "AIR");
            if (m != null && m != Material.AIR) {
                this.resultMaterial = m;
                this.resultItemId = null;
            } else {
                this.resultItemId = resultStr != null ? resultStr.toLowerCase(Locale.ROOT) : "barrier";
                this.resultMaterial = null;
            }
        }
    }

    public boolean matches(Villager.Profession prof, int villagerLevel) {
        if ("WANDERING_TRADER".equalsIgnoreCase(this.profession)) return false;
        if (!"ANY".equalsIgnoreCase(this.profession)) {
            if (prof == null || !this.profession.equalsIgnoreCase(prof.name())) {
                return false;
            }
        }
        return this.level == villagerLevel;
    }

    public boolean matchesWanderingTrader() {
        return "WANDERING_TRADER".equalsIgnoreCase(this.profession) || "ANY".equalsIgnoreCase(this.profession);
    }

    public MerchantRecipe toMerchantRecipe(SynthetixPlugin plugin) {
        ItemStack result = buildResult(plugin);
        if (result == null || result.getType().isAir() || result.getType() == Material.BARRIER) {
            return null;
        }

        ItemStack c1 = buildCostItem(cost1, cost1Amount, plugin);
        if (c1 == null || c1.getType().isAir() || c1.getType() == Material.BARRIER) {
            return null;
        }

        MerchantRecipe recipe = new MerchantRecipe(result, maxUses);
        recipe.addIngredient(c1);

        if (cost2 != null) {
            ItemStack c2 = buildCostItem(cost2, cost2Amount, plugin);
            if (c2 != null && !c2.getType().isAir() && c2.getType() != Material.BARRIER) {
                recipe.addIngredient(c2);
            }
        }

        recipe.setVillagerExperience(villagerExperience);
        recipe.setPriceMultiplier(priceMultiplier);
        return recipe;
    }

    private ItemStack buildCostItem(IngredientChoice choice, int amount, SynthetixPlugin plugin) {
        if (choice == null) return null;
        ItemStack item = null;
        if (choice.isCustomItem()) {
            ItemDefinition def = plugin.getItemRegistry().get(choice.getCustomItemId());
            if (def != null) {
                item = def.buildItemStack();
            } else {
                return null;
            }
        } else if (choice.getMaterial() != null && choice.getMaterial() != Material.AIR) {
            item = new ItemStack(choice.getMaterial());
        }
        if (item != null) {
            item.setAmount(amount);
        }
        return item;
    }

    private ItemStack buildResult(SynthetixPlugin plugin) {
        ItemStack item = null;
        if (resultItemId != null) {
            ItemDefinition def = plugin.getItemRegistry().get(resultItemId);
            if (def != null) {
                item = def.buildItemStack();
            } else {
                var blockDef = plugin.getCustomBlockRegistry() != null ? plugin.getCustomBlockRegistry().get(resultItemId) : null;
                if (blockDef != null) {
                    item = blockDef.buildBlockItemStack(1);
                } else {
                    return null;
                }
            }
        } else if (resultMaterial != null && resultMaterial != Material.AIR) {
            item = new ItemStack(resultMaterial);
        }
        if (item != null) {
            item.setAmount(resultAmount);
        }
        return item;
    }

    public String getId() {
        return id;
    }

    public String getProfession() {
        return profession;
    }

    public int getLevel() {
        return level;
    }

    public String getResultItemId() {
        return resultItemId;
    }

    public Material getResultMaterial() {
        return resultMaterial;
    }

    public int getResultAmount() {
        return resultAmount;
    }

    public double getChance() {
        return chance;
    }

    public IngredientChoice getCost1() {
        return cost1;
    }

    public int getCost1Amount() {
        return cost1Amount;
    }

    public IngredientChoice getCost2() {
        return cost2;
    }

    public int getCost2Amount() {
        return cost2Amount;
    }

    public int getMaxUses() {
        return maxUses;
    }

    public int getVillagerExperience() {
        return villagerExperience;
    }

    public float getPriceMultiplier() {
        return priceMultiplier;
    }
}

