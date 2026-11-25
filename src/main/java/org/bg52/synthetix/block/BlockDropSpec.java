package org.bg52.synthetix.block;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Drop item rule for harvesting a custom block.
 */
public class BlockDropSpec {

    private String itemId; // "self", "synth:<id>", or vanilla Material name
    private int minAmount;
    private int maxAmount;
    private double chance; // 0.0 to 1.0
    private boolean requiresSilkTouch;
    private boolean fortuneAffected;

    public BlockDropSpec(String itemId, int minAmount, int maxAmount, double chance) {
        this.itemId = itemId != null ? itemId : "self";
        this.minAmount = Math.max(1, minAmount);
        this.maxAmount = Math.max(this.minAmount, maxAmount);
        this.chance = Math.max(0.0, Math.min(1.0, chance));
        this.requiresSilkTouch = false;
        this.fortuneAffected = true;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId != null ? itemId : "self";
    }

    public int getMinAmount() {
        return minAmount;
    }

    public void setMinAmount(int minAmount) {
        this.minAmount = Math.max(0, minAmount);
    }

    public int getMaxAmount() {
        return maxAmount;
    }

    public void setMaxAmount(int maxAmount) {
        this.maxAmount = Math.max(minAmount, maxAmount);
    }

    public double getChance() {
        return chance;
    }

    public void setChance(double chance) {
        this.chance = Math.max(0.0, Math.min(1.0, chance));
    }

    public boolean isRequiresSilkTouch() {
        return requiresSilkTouch;
    }

    public void setRequiresSilkTouch(boolean requiresSilkTouch) {
        this.requiresSilkTouch = requiresSilkTouch;
    }

    public boolean isFortuneAffected() {
        return fortuneAffected;
    }

    public void setFortuneAffected(boolean fortuneAffected) {
        this.fortuneAffected = fortuneAffected;
    }

    public void saveToConfig(ConfigurationSection sec) {
        sec.set("item", itemId);
        sec.set("min-amount", minAmount);
        sec.set("max-amount", maxAmount);
        sec.set("chance", chance);
        sec.set("requires-silk-touch", requiresSilkTouch);
        sec.set("fortune-affected", fortuneAffected);
    }

    public static BlockDropSpec loadFromConfig(ConfigurationSection sec) {
        if (sec == null) return null;
        String item = sec.getString("item", "self");
        int min = sec.getInt("min-amount", 1);
        int max = sec.getInt("max-amount", 1);
        double chance = sec.getDouble("chance", 1.0);
        BlockDropSpec drop = new BlockDropSpec(item, min, max, chance);
        drop.setRequiresSilkTouch(sec.getBoolean("requires-silk-touch", false));
        drop.setFortuneAffected(sec.getBoolean("fortune-affected", true));
        return drop;
    }

    public BlockDropSpec copy() {
        BlockDropSpec copy = new BlockDropSpec(this.itemId, this.minAmount, this.maxAmount, this.chance);
        copy.setRequiresSilkTouch(this.requiresSilkTouch);
        copy.setFortuneAffected(this.fortuneAffected);
        return copy;
    }
}

