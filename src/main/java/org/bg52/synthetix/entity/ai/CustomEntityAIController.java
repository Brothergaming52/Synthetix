package org.bg52.synthetix.entity.ai;

import org.bg52.synthetix.entity.animation.EntityState;
import org.bg52.synthetix.entity.behavior.EntityBehaviorAction;
import org.bg52.synthetix.entity.behavior.EntityBehaviorChart;
import org.bg52.synthetix.entity.behavior.EntityBehaviorNode;
import org.bg52.synthetix.entity.runtime.CustomEntityInstance;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * High-performance behavioral AI controller coupling Paper mob pathfinding to the entity animation state machine.
 */
public class CustomEntityAIController {

    private enum LeapPhase {
        IDLE,
        PRE_LEAP,
        AIRBORNE,
        POST_LEAP
    }

    private final CustomEntityInstance instance;
    private final EntityAISpec spec;
    private int attackCooldown = 0;

    // Leaping locomotion tracking
    private LeapPhase leapPhase = LeapPhase.IDLE;
    private int leapPhaseTicks = 0;
    private int leapCooldown = 20;
    private double jumpVelX = 0;
    private double jumpVelZ = 0;
    private boolean hasHitThisLeap = false;

    // Generalized Behavior Chart tracking
    private final Map<String, Integer> behaviorCooldowns = new HashMap<>();
    private EntityBehaviorNode activeBehaviorNode = null;
    private int activeBehaviorTicksRemaining = 0;

    public CustomEntityAIController(CustomEntityInstance instance, EntityAISpec spec) {
        this.instance = instance;
        this.spec = spec != null ? spec : new EntityAISpec();
        this.leapCooldown = this.spec.getLeapCooldownTicks();
    }

    public void tick() {
        if (!(instance.getDriverMob() instanceof Mob mob) || !mob.isValid()) return;

        if (attackCooldown > 0) {
            attackCooldown--;
        }

        // Decrement behavior node cooldowns
        if (!behaviorCooldowns.isEmpty()) {
            behaviorCooldowns.entrySet().removeIf(entry -> {
                int next = entry.getValue() - 1;
                if (next <= 0) return true;
                entry.setValue(next);
                return false;
            });
        }

        // Preserve authoritative knockback physics window
        if (instance.getKnockbackTicks() > 0) {
            // Cancel active leap trajectory if knocked back mid-leap
            if (leapPhase == LeapPhase.AIRBORNE || leapPhase == LeapPhase.PRE_LEAP) {
                leapPhase = LeapPhase.IDLE;
                leapPhaseTicks = 0;
                leapCooldown = spec.getLeapCooldownTicks();
                jumpVelX = 0;
                jumpVelZ = 0;
                hasHitThisLeap = false;
            }

            LivingEntity target = mob.getTarget();
            if (target != null && target.isValid()) {
                Location mobLoc = mob.getLocation();
                Location targetLoc = target.getLocation();
                Vector toTarget = targetLoc.toVector().subtract(mobLoc.toVector());
                toTarget.setY(0);
                if (toTarget.lengthSquared() > 1.0E-4) {
                    float jumpYaw = (float) Math.toDegrees(Math.atan2(-toTarget.getX(), toTarget.getZ()));
                    mob.setRotation(jumpYaw, 0.0f);
                    mob.setBodyYaw(jumpYaw);
                }
            }
            return;
        }

        // Active behavior node in progress
        if (activeBehaviorNode != null) {
            activeBehaviorTicksRemaining--;
            if (activeBehaviorTicksRemaining <= 0 || (!activeBehaviorNode.isLoopAnimation() && instance.getAnimationController() != null && instance.getAnimationController().isFinished())) {
                activeBehaviorNode = null;
                instance.getStateMachine().transitionTo(EntityState.IDLE);
            }
            return;
        }

        // Evaluate generalized behavior chart if present
        EntityBehaviorChart chart = instance.getDefinition() != null ? instance.getDefinition().getBehaviorChart() : null;
        if (chart != null && !chart.isEmpty()) {
            if (tickBehaviorChart(mob, chart)) {
                return;
            }
        }

        switch (spec.getAiType()) {
            case HOSTILE_MELEE -> tickHostileMelee(mob);
            case HOSTILE_LEAP -> tickHostileLeap(mob);
            case PASSIVE -> tickPassive(mob);
            case NEUTRAL -> tickNeutral(mob);
            case STATIONARY -> tickStationary(mob);
            case CUSTOM -> {} // Driven externally via API
        }

        // Automatic state coupling for standard movement locomotion: IDLE vs WALK
        // (HOSTILE_LEAP manages its animation state transitions directly in tickHostileLeap)
        if (spec.getAiType() != EntityAIType.HOSTILE_LEAP) {
            if (instance.getStateMachine().getCurrentState() == EntityState.IDLE || instance.getStateMachine().getCurrentState() == EntityState.WALK) {
                boolean isMoving = instance.isMoving();
                if (isMoving && instance.getStateMachine().getCurrentState() != EntityState.WALK) {
                    instance.getStateMachine().transitionTo(EntityState.WALK);
                } else if (!isMoving && instance.getStateMachine().getCurrentState() != EntityState.IDLE) {
                    instance.getStateMachine().transitionTo(EntityState.IDLE);
                }
            }
        }
    }

    private void tickHostileLeap(Mob mob) {
        LivingEntity target = mob.getTarget();

        // Target acquisition
        if (target == null || !target.isValid() || target.isDead()) {
            target = findTarget(mob);
            if (target != null) {
                mob.setTarget(target);
            }
        }

        // If no target, stay completely still on ground (chest 100% closed)
        if (target == null) {
            if (mob.isOnGround()) {
                mob.setVelocity(new Vector(0, Math.min(0, mob.getVelocity().getY()), 0));
            }
            leapPhase = LeapPhase.IDLE;
            leapCooldown = spec.getLeapCooldownTicks();
            if (instance.getStateMachine().getCurrentState() != EntityState.IDLE) {
                instance.getStateMachine().transitionTo(EntityState.IDLE);
            }
            return;
        }

        // Face toward target horizontally
        Location mobLoc = mob.getLocation();
        Location targetLoc = target.getLocation();
        Vector toTarget = targetLoc.toVector().subtract(mobLoc.toVector());
        toTarget.setY(0);
        double distSq = toTarget.lengthSquared();
        if (distSq > 1.0E-4) {
            float jumpYaw = (float) Math.toDegrees(Math.atan2(-toTarget.getX(), toTarget.getZ()));
            mob.setRotation(jumpYaw, 0.0f);
            mob.setBodyYaw(jumpYaw);
        }

        switch (leapPhase) {
            case IDLE -> {
                // Suppress random vanilla hopping while on ground
                if (mob.isOnGround()) {
                    mob.setVelocity(new Vector(0, Math.min(0, mob.getVelocity().getY()), 0));
                }
                if (instance.getStateMachine().getCurrentState() != EntityState.IDLE) {
                    instance.getStateMachine().transitionTo(EntityState.IDLE);
                }
                hasHitThisLeap = false;

                leapCooldown--;
                if (leapCooldown <= 0) {
                    leapPhase = LeapPhase.PRE_LEAP;
                    leapPhaseTicks = spec.getPreLeapTicks();
                    hasHitThisLeap = false;
                }
            }
            case PRE_LEAP -> {
                // Squat preparation: clamp to ground with zero velocity
                if (mob.isOnGround()) {
                    mob.setVelocity(new Vector(0, Math.min(0, mob.getVelocity().getY()), 0));
                }
                // Transition to walk/jump track as squat begins
                instance.getStateMachine().transitionTo(EntityState.WALK);

                leapPhaseTicks--;
                if (leapPhaseTicks <= 0) {
                    // Launch into air toward target
                    double distance = Math.sqrt(distSq);
                    Vector dir = distance > 0.1 ? toTarget.clone().normalize() : new Vector(Math.random() - 0.5, 0, Math.random() - 0.5).normalize();
                    double hSpeed = Math.min(Math.max(0.12, distance * 0.10), spec.getLeapHorizontalSpeed());
                    dir.multiply(hSpeed);
                    dir.setY(spec.getLeapVerticalSpeed());

                    jumpVelX = dir.getX();
                    jumpVelZ = dir.getZ();
                    mob.setVelocity(dir);

                    // Play jump audio (e.g. mimic open)
                    if (instance.getDefinition().getSounds() != null && instance.getDefinition().getSounds().getAmbientSound() != null) {
                        mob.getWorld().playSound(mob.getLocation(), instance.getDefinition().getSounds().getAmbientSound(),
                                SoundCategory.HOSTILE, instance.getDefinition().getSounds().getVolume(), instance.getDefinition().getSounds().getPitch());
                    }

                    leapPhase = LeapPhase.AIRBORNE;
                    leapPhaseTicks = 0;
                    hasHitThisLeap = false;
                }
            }
            case AIRBORNE -> {
                leapPhaseTicks++;

                // Maintain intended jump trajectory with slight air drag
                Vector curVel = mob.getVelocity();
                jumpVelX *= 0.98;
                jumpVelZ *= 0.98;
                mob.setVelocity(new Vector(jumpVelX, curVel.getY(), jumpVelZ));

                // Continuous collision check while airborne: only strike when colliding with the target
                if (!hasHitThisLeap) {
                    LivingEntity colliding = findCollidingTarget(mob, target);
                    if (colliding != null) {
                        applyLeapHit(mob, colliding);
                        hasHitThisLeap = true;
                        // Arrest forward horizontal momentum upon collision bite
                        jumpVelX = 0;
                        jumpVelZ = 0;
                    }
                }

                // Check for ground impact after minimum air ticks
                if (leapPhaseTicks > 3 && mob.isOnGround()) {
                    leapPhase = LeapPhase.POST_LEAP;
                    leapPhaseTicks = spec.getPostLeapTicks();
                    if (!hasHitThisLeap) {
                        LivingEntity colliding = findCollidingTarget(mob, target);
                        if (colliding != null) {
                            applyLeapHit(mob, colliding);
                            hasHitThisLeap = true;
                        }
                    }
                } else if (leapPhaseTicks > 60) {
                    // Failsafe timeout
                    leapPhase = LeapPhase.POST_LEAP;
                    leapPhaseTicks = spec.getPostLeapTicks();
                }
            }
            case POST_LEAP -> {
                // Stop movement on landing
                if (mob.isOnGround()) {
                    mob.setVelocity(new Vector(0, Math.min(0, mob.getVelocity().getY()), 0));
                } else {
                    mob.setVelocity(new Vector(0, mob.getVelocity().getY(), 0));
                }

                leapPhaseTicks--;
                if (leapPhaseTicks <= 0) {
                    leapPhase = LeapPhase.IDLE;
                    leapCooldown = spec.getLeapCooldownTicks();
                    hasHitThisLeap = false;
                    instance.getStateMachine().transitionTo(EntityState.IDLE);

                    // Play close/landing impact audio
                    if (instance.getDefinition().getSounds() != null && instance.getDefinition().getSounds().getStepSound() != null) {
                        mob.getWorld().playSound(mob.getLocation(), instance.getDefinition().getSounds().getStepSound(),
                                SoundCategory.HOSTILE, instance.getDefinition().getSounds().getVolume(), instance.getDefinition().getSounds().getPitch());
                    }
                }
            }
        }
    }

    private LivingEntity findCollidingTarget(Mob mob, LivingEntity primaryTarget) {
        org.bukkit.util.BoundingBox mobBox = mob.getBoundingBox().clone().expand(0.15);

        // Check primary target first for bounding box collision
        if (isValidVictim(primaryTarget) && mobBox.overlaps(primaryTarget.getBoundingBox())) {
            return primaryTarget;
        }

        // Check any nearby entities within attack range for bounding box collision
        double range = Math.max(1.5, spec.getAttackRange());
        for (org.bukkit.entity.Entity e : mob.getNearbyEntities(range, 1.5, range)) {
            if (e instanceof LivingEntity le && isValidVictim(le) && mobBox.overlaps(le.getBoundingBox())) {
                return le;
            }
        }

        return null;
    }

    private boolean isValidVictim(LivingEntity entity) {
        if (entity == null || !entity.isValid() || entity.isDead()) return false;
        if (entity instanceof Player p) {
            return p.getGameMode() != org.bukkit.GameMode.CREATIVE && p.getGameMode() != org.bukkit.GameMode.SPECTATOR;
        }
        return spec.isTargetMonsters() && entity instanceof Monster;
    }

    private void applyLeapHit(Mob mob, LivingEntity victim) {
        double damage = instance.getDefinition().getAttackDamage();
        victim.damage(damage, mob);

        // Trigger attack animation (e.g. snapping maw bite)
        instance.getStateMachine().transitionTo(EntityState.ATTACK, 2.0f);
        attackCooldown = spec.getAttackCooldownTicks();

        // Directional knockback impulse away from the leaping mob
        Vector kb = victim.getLocation().toVector().subtract(mob.getLocation().toVector());
        kb.setY(0);
        if (kb.lengthSquared() > 1.0E-4) {
            kb.normalize().multiply(0.40);
        } else {
            kb = new Vector(0, 0, 0);
        }
        kb.setY(0.20);
        victim.setVelocity(kb);

        Location loc = victim.getLocation();
        if (loc.getWorld() != null) {
            loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 0.9f);
        }
    }

    private void tickHostileMelee(Mob mob) {
        LivingEntity target = mob.getTarget();

        // Target acquisition if none or target invalid/dead
        if (target == null || !target.isValid() || target.isDead()) {
            target = findTarget(mob);
            if (target != null) {
                mob.setTarget(target);
            }
        }

        if (target == null) return;

        double distSq = mob.getLocation().distanceSquared(target.getLocation());
        double meleeRangeSq = spec.getMeleeRange() * spec.getMeleeRange();

        if (distSq <= meleeRangeSq && attackCooldown <= 0) {
            // Melee attack trigger
            attackCooldown = spec.getAttackCooldownTicks();
            instance.getStateMachine().transitionTo(EntityState.ATTACK, 2.0f);

            double damage = instance.getDefinition().getAttackDamage();
            target.damage(damage, mob);

            // Play hit audio
            Location loc = mob.getLocation();
            if (loc.getWorld() != null) {
                loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 0.9f);
            }
        }
    }

    private void tickPassive(Mob mob) {
        // Passive mobs clear targets and look around
        if (mob.getTarget() != null) {
            mob.setTarget(null);
        }
    }

    private void tickNeutral(Mob mob) {
        LivingEntity target = mob.getTarget();
        if (target != null && target.isValid() && !target.isDead()) {
            tickHostileMelee(mob);
        }
    }

    private void tickStationary(Mob mob) {
        mob.setVelocity(new org.bukkit.util.Vector(0, mob.getVelocity().getY(), 0));
        mob.setTarget(null);
    }

    private LivingEntity findTarget(Mob mob) {
        double range = spec.getFollowRange();
        List<org.bukkit.entity.Entity> nearby = mob.getNearbyEntities(range, range / 2.0, range);

        LivingEntity closest = null;
        double closestDistSq = Double.MAX_VALUE;

        for (org.bukkit.entity.Entity e : nearby) {
            if (!(e instanceof LivingEntity le) || le == mob || !le.isValid() || le.isDead()) continue;

            if (spec.isTargetPlayers() && le instanceof Player p) {
                if (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
                double dSq = mob.getLocation().distanceSquared(p.getLocation());
                if (dSq < closestDistSq) {
                    closestDistSq = dSq;
                    closest = p;
                }
            } else if (spec.isTargetMonsters() && le instanceof Monster m && m != mob) {
                double dSq = mob.getLocation().distanceSquared(m.getLocation());
                if (dSq < closestDistSq) {
                    closestDistSq = dSq;
                    closest = m;
                }
            }
        }

        return closest;
    }

    private boolean tickBehaviorChart(Mob mob, EntityBehaviorChart chart) {
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isValid() || target.isDead()) {
            target = findTarget(mob);
            if (target != null) {
                mob.setTarget(target);
            }
        }

        double maxHp = instance.getDefinition().getMaxHealth();
        double curHp = mob.getHealth();
        double healthPercent = maxHp > 0 ? (curHp / maxHp) * 100.0 : 100.0;

        for (EntityBehaviorNode node : chart.getNodesSortedByPriority()) {
            if (behaviorCooldowns.getOrDefault(node.getId(), 0) > 0) {
                continue;
            }

            if (node.isRequiresTarget()) {
                if (target == null || !target.isValid() || target.isDead()) {
                    continue;
                }
                double dist = mob.getLocation().distance(target.getLocation());
                if (dist < node.getMinRange()) continue;
                if (node.getMaxRange() > 0 && dist > node.getMaxRange()) continue;
            }

            if (healthPercent < node.getMinHealthPercent() || healthPercent > node.getMaxHealthPercent()) {
                continue;
            }

            if (node.getChance() < 1.0 && Math.random() > node.getChance()) {
                continue;
            }

            // Trigger behavior
            behaviorCooldowns.put(node.getId(), node.getCooldownTicks());
            activeBehaviorNode = node;
            activeBehaviorTicksRemaining = node.getDurationTicks();

            if (node.getAnimationTrack() != null && !node.getAnimationTrack().isEmpty()) {
                if (instance.getAnimationController() != null && instance.getAnimationController().hasTrack(node.getAnimationTrack())) {
                    instance.getAnimationController().play(node.getAnimationTrack(), node.isLoopAnimation(), node.getBlendTicks());
                }
            }

            for (EntityBehaviorAction action : node.getActions()) {
                action.execute(instance, target);
            }

            return true;
        }

        return false;
    }
}

