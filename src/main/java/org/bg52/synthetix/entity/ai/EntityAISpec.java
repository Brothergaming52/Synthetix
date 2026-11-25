package org.bg52.synthetix.entity.ai;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/**
 * Declarative AI configuration specification for custom entities.
 */
public class EntityAISpec {

    private EntityAIType aiType = EntityAIType.HOSTILE_MELEE;
    private boolean targetPlayers = true;
    private boolean targetMonsters = false;
    private boolean wander = true;
    private boolean lookAtPlayer = true;
    private int attackCooldownTicks = 20;
    private double meleeRange = 2.2;
    private double followRange = 32.0;

    // Leaping / hopping attack locomotion parameters (for HOSTILE_LEAP)
    private double leapHorizontalSpeed = 0.45;
    private double leapVerticalSpeed = 0.25;
    private int leapCooldownTicks = 25;
    private int preLeapTicks = 3;
    private int postLeapTicks = 3;
    private double attackRange = 1.5;

    public EntityAISpec() {
    }

    public EntityAIType getAiType() {
        return aiType;
    }

    public void setAiType(EntityAIType aiType) {
        this.aiType = aiType != null ? aiType : EntityAIType.HOSTILE_MELEE;
    }

    public boolean isTargetPlayers() {
        return targetPlayers;
    }

    public void setTargetPlayers(boolean targetPlayers) {
        this.targetPlayers = targetPlayers;
    }

    public boolean isTargetMonsters() {
        return targetMonsters;
    }

    public void setTargetMonsters(boolean targetMonsters) {
        this.targetMonsters = targetMonsters;
    }

    public boolean isWander() {
        return wander;
    }

    public void setWander(boolean wander) {
        this.wander = wander;
    }

    public boolean isLookAtPlayer() {
        return lookAtPlayer;
    }

    public void setLookAtPlayer(boolean lookAtPlayer) {
        this.lookAtPlayer = lookAtPlayer;
    }

    public int getAttackCooldownTicks() {
        return attackCooldownTicks;
    }

    public void setAttackCooldownTicks(int attackCooldownTicks) {
        this.attackCooldownTicks = Math.max(1, attackCooldownTicks);
    }

    public double getMeleeRange() {
        return meleeRange;
    }

    public void setMeleeRange(double meleeRange) {
        this.meleeRange = Math.max(0.5, meleeRange);
    }

    public double getFollowRange() {
        return followRange;
    }

    public void setFollowRange(double followRange) {
        this.followRange = Math.max(4.0, followRange);
    }

    public double getLeapHorizontalSpeed() {
        return leapHorizontalSpeed;
    }

    public void setLeapHorizontalSpeed(double leapHorizontalSpeed) {
        this.leapHorizontalSpeed = Math.max(0.05, leapHorizontalSpeed);
    }

    public double getLeapVerticalSpeed() {
        return leapVerticalSpeed;
    }

    public void setLeapVerticalSpeed(double leapVerticalSpeed) {
        this.leapVerticalSpeed = Math.max(0.05, leapVerticalSpeed);
    }

    public int getLeapCooldownTicks() {
        return leapCooldownTicks;
    }

    public void setLeapCooldownTicks(int leapCooldownTicks) {
        this.leapCooldownTicks = Math.max(1, leapCooldownTicks);
    }

    public int getPreLeapTicks() {
        return preLeapTicks;
    }

    public void setPreLeapTicks(int preLeapTicks) {
        this.preLeapTicks = Math.max(0, preLeapTicks);
    }

    public int getPostLeapTicks() {
        return postLeapTicks;
    }

    public void setPostLeapTicks(int postLeapTicks) {
        this.postLeapTicks = Math.max(0, postLeapTicks);
    }

    public double getAttackRange() {
        return attackRange;
    }

    public void setAttackRange(double attackRange) {
        this.attackRange = Math.max(0.2, attackRange);
    }

    public void saveToConfig(ConfigurationSection sec) {
        if (sec == null) return;
        sec.set("type", aiType.name());
        sec.set("target-players", targetPlayers);
        sec.set("target-monsters", targetMonsters);
        sec.set("wander", wander);
        sec.set("look-at-player", lookAtPlayer);
        sec.set("attack-cooldown-ticks", attackCooldownTicks);
        sec.set("melee-range", meleeRange);
        sec.set("follow-range", followRange);
        sec.set("leap-horizontal-speed", leapHorizontalSpeed);
        sec.set("leap-vertical-speed", leapVerticalSpeed);
        sec.set("leap-cooldown-ticks", leapCooldownTicks);
        sec.set("pre-leap-ticks", preLeapTicks);
        sec.set("post-leap-ticks", postLeapTicks);
        sec.set("attack-range", attackRange);
    }

    public static EntityAISpec loadFromConfig(ConfigurationSection sec) {
        if (sec == null) return new EntityAISpec();
        EntityAISpec spec = new EntityAISpec();
        String typeStr = sec.getString("type", "HOSTILE_MELEE");
        try {
            spec.setAiType(EntityAIType.valueOf(typeStr.toUpperCase(Locale.ROOT)));
        } catch (Exception ignored) {
            spec.setAiType(EntityAIType.HOSTILE_MELEE);
        }
        spec.setTargetPlayers(sec.getBoolean("target-players", true));
        spec.setTargetMonsters(sec.getBoolean("target-monsters", false));
        spec.setWander(sec.getBoolean("wander", true));
        spec.setLookAtPlayer(sec.getBoolean("look-at-player", true));
        spec.setAttackCooldownTicks(sec.getInt("attack-cooldown-ticks", 20));
        spec.setMeleeRange(sec.getDouble("melee-range", 2.2));
        spec.setFollowRange(sec.getDouble("follow-range", 32.0));
        spec.setLeapHorizontalSpeed(sec.getDouble("leap-horizontal-speed", 0.45));
        spec.setLeapVerticalSpeed(sec.getDouble("leap-vertical-speed", 0.25));
        spec.setLeapCooldownTicks(sec.getInt("leap-cooldown-ticks", 25));
        spec.setPreLeapTicks(sec.getInt("pre-leap-ticks", 3));
        spec.setPostLeapTicks(sec.getInt("post-leap-ticks", 3));
        spec.setAttackRange(sec.getDouble("attack-range", 1.5));
        return spec;
    }
}

