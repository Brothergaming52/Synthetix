package org.bg52.synthetix.set;

import org.bg52.synthetix.item.ItemDefinition;
import org.bg52.synthetix.item.food.FoodEffectSpec;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a tier bonus unlocked when wearing a specific number of set pieces.
 */
public class SetBonus {

    private int pieceCount;
    private String loreLine;
    private List<ItemDefinition.AttributeSpec> attributes;
    private List<PotionEffect> potionEffects;
    private List<String> actions;

    public SetBonus(int pieceCount) {
        this.pieceCount = Math.max(1, pieceCount);
        this.loreLine = "";
        this.attributes = new ArrayList<>();
        this.potionEffects = new ArrayList<>();
        this.actions = new ArrayList<>();
    }

    public int getPieceCount() {
        return pieceCount;
    }

    public void setPieceCount(int pieceCount) {
        this.pieceCount = Math.max(1, pieceCount);
    }

    public String getLoreLine() {
        return loreLine;
    }

    public void setLoreLine(String loreLine) {
        this.loreLine = loreLine != null ? loreLine : "";
    }

    public List<ItemDefinition.AttributeSpec> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<ItemDefinition.AttributeSpec> attributes) {
        this.attributes = attributes != null ? new ArrayList<>(attributes) : new ArrayList<>();
    }

    public List<PotionEffect> getPotionEffects() {
        return potionEffects;
    }

    public void setPotionEffects(List<PotionEffect> potionEffects) {
        this.potionEffects = potionEffects != null ? new ArrayList<>(potionEffects) : new ArrayList<>();
    }

    public List<String> getActions() {
        return actions;
    }

    public void setActions(List<String> actions) {
        this.actions = actions != null ? new ArrayList<>(actions) : new ArrayList<>();
    }

    public void saveToConfig(ConfigurationSection sec) {
        sec.set("pieces", pieceCount);
        if (!loreLine.isEmpty()) sec.set("lore", loreLine);

        if (attributes != null && !attributes.isEmpty()) {
            ConfigurationSection attrSec = sec.createSection("attributes");
            for (int i = 0; i < attributes.size(); i++) {
                ConfigurationSection aEntry = attrSec.createSection("attr_" + i);
                attributes.get(i).saveToConfig(aEntry);
            }
        }

        if (potionEffects != null && !potionEffects.isEmpty()) {
            List<String> effList = new ArrayList<>();
            for (PotionEffect pe : potionEffects) {
                effList.add(pe.getType().getKey().asString() + " " + pe.getAmplifier());
            }
            sec.set("effects", effList);
        }

        if (actions != null && !actions.isEmpty()) {
            sec.set("actions", actions);
        }
    }

    public static SetBonus loadFromConfig(ConfigurationSection sec) {
        if (sec == null) return null;
        int count = sec.getInt("pieces", 2);
        SetBonus bonus = new SetBonus(count);
        bonus.setLoreLine(sec.getString("lore", ""));

        ConfigurationSection attrSec = sec.getConfigurationSection("attributes");
        if (attrSec != null) {
            List<ItemDefinition.AttributeSpec> list = new ArrayList<>();
            for (String k : attrSec.getKeys(false)) {
                ConfigurationSection aEntry = attrSec.getConfigurationSection(k);
                if (aEntry != null) {
                    ItemDefinition.AttributeSpec spec = ItemDefinition.AttributeSpec.loadFromConfig(aEntry);
                    if (spec != null) list.add(spec);
                }
            }
            bonus.setAttributes(list);
        }

        List<String> effList = sec.getStringList("effects");
        if (effList != null) {
            List<PotionEffect> effects = new ArrayList<>();
            for (String str : effList) {
                String[] parts = str.split("\\s+");
                PotionEffectType type = FoodEffectSpec.parsePotionEffectType(parts[0]);
                if (type != null) {
                    int amp = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                    effects.add(new PotionEffect(type, 60, amp, false, true, true));
                }
            }
            bonus.setPotionEffects(effects);
        }

        bonus.setActions(sec.getStringList("actions"));
        return bonus;
    }

    public SetBonus copy() {
        SetBonus copy = new SetBonus(this.pieceCount);
        copy.setLoreLine(this.loreLine);
        List<ItemDefinition.AttributeSpec> attrCopies = new ArrayList<>();
        for (ItemDefinition.AttributeSpec a : this.attributes) {
            attrCopies.add(new ItemDefinition.AttributeSpec(a.getAttributeName(), a.getName(), a.getAmount(), a.getOperation(), a.getSlot()));
        }
        copy.setAttributes(attrCopies);
        List<PotionEffect> effCopies = new ArrayList<>();
        for (PotionEffect pe : this.potionEffects) {
            effCopies.add(new PotionEffect(pe.getType(), pe.getDuration(), pe.getAmplifier(), false, true, true));
        }
        copy.setPotionEffects(effCopies);
        copy.setActions(new ArrayList<>(this.actions));
        return copy;
    }
}

