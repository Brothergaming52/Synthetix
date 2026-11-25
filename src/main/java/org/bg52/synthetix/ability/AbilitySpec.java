package org.bg52.synthetix.ability;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines an interactive ability tied to an interaction trigger.
 */
public class AbilitySpec {

    private String triggerId;
    private double cooldown; // seconds
    private String cooldownMessage;
    private double chance; // 0.0 to 1.0 (1.0 = 100%)
    private int costDurability;
    private List<String> conditions;
    private List<String> actions;

    public AbilitySpec(String triggerId) {
        this.triggerId = triggerId != null ? triggerId.toLowerCase() : "on-right-click";
        this.cooldown = 0.0;
        this.cooldownMessage = "<red>This ability is cooling down! (%remaining%s)</red>";
        this.chance = 1.0;
        this.costDurability = 0;
        this.conditions = new ArrayList<>();
        this.actions = new ArrayList<>();
    }

    public String getTriggerId() {
        return triggerId;
    }

    public void setTriggerId(String triggerId) {
        this.triggerId = triggerId != null ? triggerId.toLowerCase() : "on-right-click";
    }

    public double getCooldown() {
        return cooldown;
    }

    public void setCooldown(double cooldown) {
        this.cooldown = Math.max(0.0, cooldown);
    }

    public String getCooldownMessage() {
        return cooldownMessage;
    }

    public void setCooldownMessage(String cooldownMessage) {
        this.cooldownMessage = cooldownMessage;
    }

    public double getChance() {
        return chance;
    }

    public void setChance(double chance) {
        this.chance = Math.max(0.0, Math.min(1.0, chance));
    }

    public int getCostDurability() {
        return costDurability;
    }

    public void setCostDurability(int costDurability) {
        this.costDurability = Math.max(0, costDurability);
    }

    public List<String> getConditions() {
        return conditions;
    }

    public void setConditions(List<String> conditions) {
        this.conditions = conditions != null ? new ArrayList<>(conditions) : new ArrayList<>();
    }

    public List<String> getActions() {
        return actions;
    }

    public void setActions(List<String> actions) {
        this.actions = actions != null ? new ArrayList<>(actions) : new ArrayList<>();
    }

    public void saveToConfig(ConfigurationSection sec) {
        sec.set("trigger", triggerId);
        if (cooldown > 0) sec.set("cooldown", cooldown);
        if (cooldownMessage != null) sec.set("cooldown-message", cooldownMessage);
        if (chance < 1.0) sec.set("chance", chance);
        if (costDurability > 0) sec.set("cost-durability", costDurability);
        if (conditions != null && !conditions.isEmpty()) sec.set("conditions", conditions);
        if (actions != null && !actions.isEmpty()) sec.set("actions", actions);
    }

    public static AbilitySpec loadFromConfig(ConfigurationSection sec, String defaultTrigger) {
        if (sec == null) return null;
        String trigger = sec.getString("trigger", defaultTrigger);
        AbilitySpec spec = new AbilitySpec(trigger);
        spec.setCooldown(sec.getDouble("cooldown", 0.0));
        spec.setCooldownMessage(sec.getString("cooldown-message", "<red>This ability is cooling down! (%remaining%s)</red>"));
        spec.setChance(sec.getDouble("chance", 1.0));
        spec.setCostDurability(sec.getInt("cost-durability", 0));
        spec.setConditions(sec.getStringList("conditions"));
        spec.setActions(sec.getStringList("actions"));
        return spec;
    }

    public AbilitySpec copy() {
        AbilitySpec copy = new AbilitySpec(this.triggerId);
        copy.setCooldown(this.cooldown);
        copy.setCooldownMessage(this.cooldownMessage);
        copy.setChance(this.chance);
        copy.setCostDurability(this.costDurability);
        copy.setConditions(new ArrayList<>(this.conditions));
        copy.setActions(new ArrayList<>(this.actions));
        return copy;
    }
}

