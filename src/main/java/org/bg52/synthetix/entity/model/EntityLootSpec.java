package org.bg52.synthetix.entity.model;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Loot and experience specification for custom entity death drops.
 */
public class EntityLootSpec {

    public static class DropEntry {
        private String itemId = "DIAMOND";
        private int minAmount = 1;
        private int maxAmount = 1;
        private double chance = 1.0;
        private boolean affectedByLooting = true;

        public DropEntry() {
        }

        public DropEntry(String itemId, int minAmount, int maxAmount, double chance) {
            this.itemId = itemId;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
            this.chance = chance;
        }

        public String getItemId() {
            return itemId;
        }

        public void setItemId(String itemId) {
            this.itemId = itemId != null ? itemId : "DIAMOND";
        }

        public int getMinAmount() {
            return minAmount;
        }

        public void setMinAmount(int minAmount) {
            this.minAmount = Math.max(1, minAmount);
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

        public boolean isAffectedByLooting() {
            return affectedByLooting;
        }

        public void setAffectedByLooting(boolean affectedByLooting) {
            this.affectedByLooting = affectedByLooting;
        }
    }

    private int exp = 5;
    private final List<DropEntry> drops = new ArrayList<>();

    public EntityLootSpec() {
    }

    public int getExp() {
        return exp;
    }

    public int getExpDrop() {
        return exp;
    }

    public void setExp(int exp) {
        this.exp = Math.max(0, exp);
    }

    public void setExpDrop(int exp) {
        setExp(exp);
    }

    public List<DropEntry> getDrops() {
        return drops;
    }

    public void addDrop(DropEntry drop) {
        if (drop != null) drops.add(drop);
    }

    public void saveToConfig(ConfigurationSection sec) {
        if (sec == null) return;
        sec.set("exp", exp);
        if (!drops.isEmpty()) {
            ConfigurationSection dSec = sec.createSection("drops");
            for (int i = 0; i < drops.size(); i++) {
                ConfigurationSection entrySec = dSec.createSection("drop-" + i);
                DropEntry de = drops.get(i);
                entrySec.set("item", de.getItemId());
                entrySec.set("min", de.getMinAmount());
                entrySec.set("max", de.getMaxAmount());
                entrySec.set("chance", de.getChance());
                entrySec.set("looting", de.isAffectedByLooting());
            }
        }
    }

    public static EntityLootSpec loadFromConfig(ConfigurationSection sec) {
        if (sec == null) return new EntityLootSpec();
        EntityLootSpec spec = new EntityLootSpec();
        spec.setExp(sec.getInt("exp", 5));

        ConfigurationSection dSec = sec.getConfigurationSection("drops");
        if (dSec != null) {
            for (String key : dSec.getKeys(false)) {
                ConfigurationSection entrySec = dSec.getConfigurationSection(key);
                if (entrySec == null) continue;
                DropEntry de = new DropEntry();
                de.setItemId(entrySec.getString("item", "DIAMOND"));
                de.setMinAmount(entrySec.getInt("min", 1));
                de.setMaxAmount(entrySec.getInt("max", 1));
                de.setChance(entrySec.getDouble("chance", 1.0));
                de.setAffectedByLooting(entrySec.getBoolean("looting", true));
                spec.addDrop(de);
            }
        }

        return spec;
    }
}

