package org.bg52.synthetix.entity.runtime;

import org.bg52.synthetix.entity.ai.CustomEntityAIController;
import org.bg52.synthetix.entity.animation.AnimationController;
import org.bg52.synthetix.entity.animation.EntityState;
import org.bg52.synthetix.entity.animation.EntityStateMachine;
import org.bg52.synthetix.entity.model.BoneTransform;
import org.bg52.synthetix.entity.model.CustomEntityDefinition;
import org.bg52.synthetix.entity.model.EntityBoneSpec;
import org.bg52.synthetix.entity.model.EntityHitboxSpec;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/**
 * Active runtime instance of a custom entity in the Minecraft world.
 * Manages the Tri-Layer Architecture: Driver Mob, Virtual Skeletal Bones, and Interaction Hitboxes.
 */
public class CustomEntityInstance {

    private final UUID instanceId = UUID.randomUUID();
    private final CustomEntityDefinition definition;
    private final LivingEntity driverMob;

    private final Map<String, VirtualBoneInstance> bones = new LinkedHashMap<>();
    private final Map<UUID, VirtualHitboxInstance> hitboxes = new HashMap<>();

    private final AnimationController animationController;
    private final EntityStateMachine stateMachine;
    private final CustomEntityAIController aiController;

    private int damageFlashTicks = 0;
    private int knockbackTicks = 0;
    private UUID lastDamagerId = null;
    private long lastDamageTime = 0;
    private Location lastLocation = null;
    private boolean wasOnGround = true;
    private boolean despawned = false;
    private boolean dying = false;
    private int deathTicksRemaining = 0;

    public CustomEntityInstance(CustomEntityDefinition definition, LivingEntity driverMob) {
        this.definition = definition;
        this.driverMob = driverMob;

        this.animationController = new AnimationController();
        for (var track : definition.getAnimations().values()) {
            this.animationController.registerTrack(track);
        }

        this.stateMachine = new EntityStateMachine(this);
        this.aiController = new CustomEntityAIController(this, definition.getAi());

        // Play initial idle animation if available
        if (this.animationController.hasTrack("idle")) {
            this.animationController.play("idle", true, 0.0f);
        }
    }

    public UUID getInstanceId() {
        return instanceId;
    }

    public CustomEntityDefinition getDefinition() {
        return definition;
    }

    public LivingEntity getDriverMob() {
        return driverMob;
    }

    public Map<String, VirtualBoneInstance> getBones() {
        return bones;
    }

    public Map<UUID, VirtualHitboxInstance> getHitboxes() {
        return hitboxes;
    }

    public AnimationController getAnimationController() {
        return animationController;
    }

    public EntityStateMachine getStateMachine() {
        return stateMachine;
    }

    public CustomEntityAIController getAiController() {
        return aiController;
    }

    public boolean isDespawned() {
        return despawned;
    }

    public boolean isDying() {
        return dying;
    }

    public void startDeathAnimation() {
        if (dying || despawned) return;
        dying = true;

        for (VirtualHitboxInstance hb : hitboxes.values()) {
            hb.cleanup();
        }
        hitboxes.clear();

        var deathTrack = animationController != null ? animationController.getTrack("death") : null;
        int duration = deathTrack != null ? (int) Math.ceil(deathTrack.getDurationTicks()) : 16;
        this.deathTicksRemaining = Math.max(12, duration);

        stateMachine.transitionTo(EntityState.DEATH, 2.0f);

        if (definition.getSounds() != null && definition.getSounds().getDeathSound() != null && !definition.getSounds().getDeathSound().isEmpty()) {
            Location loc = driverMob != null ? driverMob.getLocation() : null;
            if (loc != null && loc.getWorld() != null) {
                try {
                    loc.getWorld().playSound(loc, definition.getSounds().getDeathSound(), org.bukkit.SoundCategory.HOSTILE, definition.getSounds().getVolume(), definition.getSounds().getPitch());
                } catch (Exception ignored) {}
            }
        }
    }

    public void registerBone(String name, VirtualBoneInstance bone) {
        if (name != null && bone != null) {
            bones.put(name.toLowerCase(Locale.ROOT), bone);
        }
    }

    public void registerHitbox(UUID interactionUuid, VirtualHitboxInstance hitbox) {
        if (interactionUuid != null && hitbox != null) {
            hitboxes.put(interactionUuid, hitbox);
        }
    }

    public void onStateChange(EntityState newState) {
        org.bukkit.Bukkit.getPluginManager().callEvent(new org.bg52.synthetix.api.event.SynthetixEntityStateChangeEvent(this, null, newState));
    }

    /**
     * Master tick cycle handling physics synchronization, Variable Tick Rate (VTR) LOD,
     * forward kinematics evaluation, and damage flash states.
     */
    public void tick(long currentServerTick) {
        if (despawned) return;

        if (dying) {
            deathTicksRemaining--;
            if (deathTicksRemaining <= 0) {
                despawn();
                return;
            }
            float deltaTicks = 1.0f;
            stateMachine.tick(deltaTicks);
            Map<String, BoneTransform> activeTransforms = new HashMap<>();
            for (Map.Entry<String, VirtualBoneInstance> entry : bones.entrySet()) {
                activeTransforms.put(entry.getKey(), entry.getValue().getLocalTransform());
            }
            animationController.update(deltaTicks, activeTransforms);
            evaluateKinematics(1);
            return;
        }

        if (driverMob == null || !driverMob.isValid() || driverMob.isDead()) {
            despawn();
            return;
        }

        // Safety: prevent custom entities from getting stuck or penetrating inside solid terrain geometry (e.g. cliffs)
        preventTerrainClipping();

        // Special Case: Fluid buoyancy & drowning prevention
        if (driverMob.isInWater()) {
            driverMob.setRemainingAir(driverMob.getMaximumAir());
        }

        // Damage flash decay
        if (damageFlashTicks > 0) {
            damageFlashTicks--;
            if (damageFlashTicks == 0) {
                setBonesDamageFlash(false);
            }
        }

        // Knockback decay
        if (knockbackTicks > 0) {
            knockbackTicks--;
        }

        // Detect landing and air transitions
        boolean onGround = driverMob.isOnGround();
        if (!wasOnGround && onGround) {
            // Landing impact audio
            if (definition.getSounds() != null && definition.getSounds().getStepSound() != null) {
                Location loc = driverMob.getLocation();
                if (loc.getWorld() != null) {
                    loc.getWorld().playSound(loc, definition.getSounds().getStepSound(),
                            org.bukkit.SoundCategory.HOSTILE,
                            definition.getSounds().getVolume(), definition.getSounds().getPitch());
                }
            }
        }
        wasOnGround = onGround;

        // AI behavior tick
        aiController.tick();

        // Calculate Variable Tick Rate (VTR) Level of Detail based on closest player
        double closestDistSq = getClosestPlayerDistanceSquared();
        int updateInterval;
        int interpolationDuration;

        if (closestDistSq <= 16.0 * 16.0) {
            // Combat range: 10 Hz (every 2 ticks)
            updateInterval = 2;
            interpolationDuration = 2;
        } else if (closestDistSq <= 32.0 * 32.0) {
            // Medium range: 5 Hz (every 4 ticks)
            updateInterval = 4;
            interpolationDuration = 4;
        } else if (closestDistSq <= 64.0 * 64.0) {
            // Far range: 2 Hz (every 10 ticks)
            updateInterval = 10;
            interpolationDuration = 10;
        } else {
            // Horizon: Freeze updates to save bandwidth
            return;
        }

        if (currentServerTick % updateInterval != 0) {
            return;
        }

        // Update animation timeline and state machine
        float deltaTicks = (float) updateInterval;
        stateMachine.tick(deltaTicks);

        Map<String, BoneTransform> activeTransforms = new HashMap<>();
        for (Map.Entry<String, VirtualBoneInstance> entry : bones.entrySet()) {
            activeTransforms.put(entry.getKey(), entry.getValue().getLocalTransform());
        }
        animationController.update(deltaTicks, activeTransforms);

        // Compute Forward Kinematics starting from root bone
        evaluateKinematics(interpolationDuration);

        // Store last location for generalized displacement tracking
        this.lastLocation = driverMob.getLocation().clone();
    }

    /**
     * Generalized displacement and motion detection across all mob types.
     * Evaluates actual world displacement and ground contact.
     */
    public boolean isMoving() {
        if (driverMob == null || !driverMob.isValid()) return false;
        if (!driverMob.isOnGround()) return true;
        if (lastLocation != null && driverMob.getWorld().equals(lastLocation.getWorld())) {
            double dx = driverMob.getLocation().getX() - lastLocation.getX();
            double dz = driverMob.getLocation().getZ() - lastLocation.getZ();
            if (dx * dx + dz * dz > 0.0004) return true;
        }
        double vx = driverMob.getVelocity().getX();
        double vz = driverMob.getVelocity().getZ();
        return (vx * vx + vz * vz) > 0.001;
    }

    private void evaluateKinematics(int interpolationDuration) {
        Location mobLoc = driverMob.getLocation();
        float bodyYaw = driverMob.getBodyYaw();
        if (bodyYaw == 0.0f && driverMob.getYaw() != 0.0f) {
            bodyYaw = driverMob.getYaw();
        }

        // Authoritative passenger seat height calculation
        double seatHeight = calculatePassengerSeatHeight();

        // Root matrix aligned with mob yaw and anchored to mob's feet
        // Note: Minecraft's DisplayRenderer applies 180.0F - entityYaw, so we apply -bodyYaw + yawOffset
        // to precisely align the front face of ItemDisplay item models with the mob's authoritative forward direction.
        float yawOffset = definition.getBaseYawOffset();
        Quaternionf rootRot = new Quaternionf().rotateY((float) Math.toRadians(-bodyYaw + yawOffset));
        Matrix4f rootMatrix = new Matrix4f()
                .rotate(rootRot)
                .translate(0.0f, (float) -seatHeight, 0.0f);

        // Traverse hierarchy
        String rootName = definition.getRootBoneName() != null ? definition.getRootBoneName().toLowerCase(Locale.ROOT) : "root";
        VirtualBoneInstance rootBone = bones.get(rootName);

        if (rootBone != null) {
            evaluateBoneRecursive(rootBone, rootMatrix, interpolationDuration, mobLoc);
        } else {
            // Fallback for standalone/flat root bones
            for (VirtualBoneInstance b : bones.values()) {
                if (b.getSpec().getParent() == null || b.getSpec().getParent().isEmpty()) {
                    evaluateBoneRecursive(b, rootMatrix, interpolationDuration, mobLoc);
                }
            }
        }
    }

    private double calculatePassengerSeatHeight() {
        if (driverMob == null) return 0.0;
        if (definition.getSeatHeightOverride() != null) {
            return definition.getSeatHeightOverride();
        }
        // In modern Minecraft (1.20.5+ / 1.20.6 / 1.21+ / 26.3), EntityAttachment.PASSENGER
        // defaults to Fallback.AT_HEIGHT, exactly equal to driverMob.getHeight().
        return driverMob.getHeight();
    }

    private void evaluateBoneRecursive(VirtualBoneInstance bone, Matrix4f parentMatrix, int interpolationDuration, Location mobLoc) {
        EntityBoneSpec spec = bone.getSpec();

        // Procedural IK: Head-look tracking on head bones
        if ("head".equalsIgnoreCase(spec.getName())) {
            applyHeadLookTracking(bone.getLocalTransform());
        }

        bone.updateTransform(parentMatrix, interpolationDuration);

        // Update child hitboxes with full 3D rotation from bone world matrix
        for (VirtualHitboxInstance hb : hitboxes.values()) {
            if (spec.getName().equalsIgnoreCase(hb.getSpec().getBoneName())) {
                hb.updatePosition(mobLoc, bone.getWorldMatrix());
            }
        }

        // Evaluate children of this bone using this bone's computed world matrix
        Matrix4f boneWorldMatrix = bone.getWorldMatrix();
        for (VirtualBoneInstance other : bones.values()) {
            if (spec.getName().equalsIgnoreCase(other.getSpec().getParent())) {
                evaluateBoneRecursive(other, boneWorldMatrix, interpolationDuration, mobLoc);
            }
        }
    }

    private void applyHeadLookTracking(BoneTransform headTransform) {
        float headYaw = driverMob.getYaw();
        float bodyYaw = driverMob.getBodyYaw();
        float deltaYaw = headYaw - bodyYaw;

        // Normalize delta yaw to -180..+180
        while (deltaYaw > 180.0f) deltaYaw -= 360.0f;
        while (deltaYaw < -180.0f) deltaYaw += 360.0f;

        // Clamp head look angle
        float clampedYaw = Math.max(-60.0f, Math.min(60.0f, deltaYaw));
        float pitch = Math.max(-45.0f, Math.min(45.0f, driverMob.getPitch()));

        Quaternionf lookRot = new Quaternionf()
                .rotateY((float) Math.toRadians(-clampedYaw))
                .rotateX((float) Math.toRadians(pitch));

        headTransform.rotation.mul(lookRot);
    }

    /**
     * Intercepts damage applied to the entity or one of its sub-hitboxes.
     */
    public void handleDamage(double incomingDamage, double multiplier, boolean isWeakpoint) {
        handleDamage(incomingDamage, multiplier, isWeakpoint, null);
    }

    /**
     * Handles visual, audio, and state machine reactions for damage applied directly to the Driver mob
     * through Bukkit's native EntityDamageEvent pipeline.
     */
    public void handleDirectDamage(double finalDamage, Entity damager) {
        if (despawned || driverMob == null || !driverMob.isValid() || driverMob.isDead()) return;

        playDamageVisuals(finalDamage, false, 1.0, damager);
        // Note: Driver mob was struck directly in an uncancelled EntityDamageByEntityEvent,
        // so vanilla Minecraft physics natively calculates and applies the authoritative knockback.
        // We set knockbackTicks to maintain AI behavior suppression during the knockback window.
        this.knockbackTicks = 8;

        if (damager != null) {
            Location dLoc = damager.getLocation();
            Location vLoc = driverMob.getLocation();
            double fx = dLoc.getX() - vLoc.getX();
            double fz = dLoc.getZ() - vLoc.getZ();
            if (fx * fx + fz * fz > 1.0E-4) {
                float faceYaw = (float) Math.toDegrees(Math.atan2(-fx, fz));
                driverMob.setRotation(faceYaw, 0.0f);
                driverMob.setBodyYaw(faceYaw);
            }
        }

        if (driverMob.isDead() || driverMob.getHealth() <= 0) {
            stateMachine.transitionTo(EntityState.DEATH, 2.0f);
        }
    }

    /**
     * Authoritative damage application routing damage, authentic sounds/particles,
     * revenge targeting, knockback impulse, and animations to the Driver Mob and visual bones.
     */
    public void handleDamage(double incomingDamage, double multiplier, boolean isWeakpoint, Entity damager) {
        if (despawned || driverMob == null || !driverMob.isValid() || driverMob.isDead()) return;

        double finalDamage = incomingDamage * multiplier;

        playDamageVisuals(finalDamage, isWeakpoint, multiplier, damager);
        applyKnockback(damager);

        // Forward damage to authority Driver mob (without passing damager to prevent duplicate double-knockback)
        driverMob.damage(finalDamage);

        if (driverMob.isDead() || driverMob.getHealth() <= 0) {
            stateMachine.transitionTo(EntityState.DEATH, 2.0f);
        }
    }

    private void playDamageVisuals(double finalDamage, boolean isWeakpoint, double multiplier, Entity damager) {
        // 1. Play authentic hurt audio
        Location loc = driverMob.getLocation().clone().add(0, driverMob.getHeight() / 2.0, 0);
        if (loc.getWorld() != null) {
            String hurtSound = definition.getSounds().getHurtSound();
            if (hurtSound != null && !hurtSound.isEmpty()) {
                try {
                    loc.getWorld().playSound(loc, hurtSound, org.bukkit.SoundCategory.HOSTILE, definition.getSounds().getVolume(), definition.getSounds().getPitch());
                } catch (Exception ignored) {}
            }

            // Authentic damage indicator particles
            try {
                int particleCount = Math.max(1, (int) Math.round(finalDamage / 2.0));
                loc.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, loc, particleCount, 0.25, 0.35, 0.25, 0.05);
            } catch (Throwable ignored) {}

            // Critical strike particles if weakpoint hit
            if (isWeakpoint || multiplier > 1.2) {
                try {
                    loc.getWorld().spawnParticle(Particle.CRIT, loc, 15, 0.3, 0.3, 0.3, 0.15);
                    loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.2f);
                } catch (Throwable ignored) {}
            }
        }

        // 2. Trigger red damage flash on visual bones (authentic tint, never outline glow)
        this.damageFlashTicks = 5;
        setBonesDamageFlash(true);

        // 3. Play authentic Paper hurt animation on driver mob (tilts/recoils)
        try {
            float hurtYaw = damager != null ? damager.getLocation().getYaw() : driverMob.getYaw();
            driverMob.playHurtAnimation(hurtYaw);
        } catch (Throwable ignored) {}

        // 4. Transition to hurt animation state if defined
        stateMachine.transitionTo(EntityState.HURT, 1.0f);

        // 5. Damager tracking & revenge targeting
        if (damager != null) {
            this.lastDamagerId = damager.getUniqueId();
            this.lastDamageTime = System.currentTimeMillis();
            if (damager instanceof LivingEntity livingAttacker && driverMob instanceof Mob mob) {
                if (mob.getTarget() == null || mob.getTarget().isDead() || !(mob.getTarget() instanceof Player)) {
                    mob.setTarget(livingAttacker);
                }
            }
        }
    }

    private void applyKnockback(Entity damager) {
        if (damager == null || driverMob == null || !driverMob.isValid()) return;

        Location damagerLoc = damager.getLocation();
        Location victimLoc = driverMob.getLocation();
        double dx = victimLoc.getX() - damagerLoc.getX();
        double dz = victimLoc.getZ() - damagerLoc.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 1.0E-4) {
            float yawRad = (float) Math.toRadians(damagerLoc.getYaw());
            dx = -Math.sin(yawRad);
            dz = Math.cos(yawRad);
            dist = 1.0;
        }

        dx /= dist;
        dz /= dist;

        double strength = 0.40;
        if (damager instanceof Player player) {
            if (player.isSprinting()) {
                strength += 0.20;
            }
            ItemStack weapon = player.getInventory().getItemInMainHand();
            if (weapon != null && weapon.getType() != Material.AIR) {
                int kbLevel = weapon.getEnchantmentLevel(Enchantment.KNOCKBACK);
                strength += kbLevel * 0.25;
            }
        } else if (damager instanceof Projectile proj) {
            if (proj.getShooter() instanceof Player shooter) {
                ItemStack bow = shooter.getInventory().getItemInMainHand();
                if (bow != null && bow.getType() != Material.AIR) {
                    int punch = bow.getEnchantmentLevel(Enchantment.PUNCH);
                    strength += punch * 0.25;
                }
            }
        }

        // Check knockback resistance attribute
        double resistance = 0.0;
        try {
            AttributeInstance attr = driverMob.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
            if (attr != null) {
                resistance = attr.getValue();
            }
        } catch (Throwable ignored) {}

        strength *= (1.0 - Math.min(1.0, Math.max(0.0, resistance)));

        if (strength > 0.02) {
            Vector current = driverMob.getVelocity();
            double vx = current.getX() * 0.5 + dx * strength;
            double vz = current.getZ() * 0.5 + dz * strength;

            // Clamp horizontal velocity to safe limits to prevent tunneling through cliffs/blocks
            double maxHorizontal = 0.65;
            double hSpeed = Math.sqrt(vx * vx + vz * vz);
            if (hSpeed > maxHorizontal) {
                vx = (vx / hSpeed) * maxHorizontal;
                vz = (vz / hSpeed) * maxHorizontal;
            }

            // Grounded mobs receive vertical hop; airborne mobs don't get boosted upwards into ceilings/cliffs
            double vy;
            if (driverMob.isOnGround()) {
                vy = 0.36;
            } else {
                vy = Math.max(-0.4, current.getY() * 0.5);
            }

            Vector kbVel = new Vector(vx, vy, vz);

            this.knockbackTicks = 8;
            driverMob.setVelocity(kbVel);

            if (damager != null) {
                Location dLoc = damager.getLocation();
                Location vLoc = driverMob.getLocation();
                double fx = dLoc.getX() - vLoc.getX();
                double fz = dLoc.getZ() - vLoc.getZ();
                if (fx * fx + fz * fz > 1.0E-4) {
                    float faceYaw = (float) Math.toDegrees(Math.atan2(-fx, fz));
                    driverMob.setRotation(faceYaw, 0.0f);
                    driverMob.setBodyYaw(faceYaw);
                }
            }
        }
    }

    /**
     * Generalized safety mechanism preventing custom entities from penetrating and getting
     * permanently embedded inside solid terrain geometry (e.g. cliffs, cave walls).
     */
    private void preventTerrainClipping() {
        if (driverMob == null || !driverMob.isValid()) return;
        Location loc = driverMob.getLocation();
        if (loc.getWorld() == null) return;

        org.bukkit.block.Block block = loc.getBlock();
        // If the mob's feet/center are inside an occluding full solid block
        if (block.getType().isSolid() && !block.isPassable()) {
            Location safeLoc = findSafeDePenetrationLocation(loc);
            if (safeLoc != null) {
                driverMob.teleport(safeLoc);
            }
        }
    }

    private Location findSafeDePenetrationLocation(Location stuckLoc) {
        org.bukkit.World world = stuckLoc.getWorld();
        if (world == null) return null;

        int baseX = stuckLoc.getBlockX();
        int baseY = stuckLoc.getBlockY();
        int baseZ = stuckLoc.getBlockZ();

        // 1. Check directly above (often stuck by step-height or floor clipping)
        for (int dy = 1; dy <= 2; dy++) {
            org.bukkit.block.Block b = world.getBlockAt(baseX, baseY + dy, baseZ);
            if (b.isPassable()) {
                Location safe = b.getLocation().add(0.5, 0.0, 0.5);
                safe.setYaw(stuckLoc.getYaw());
                safe.setPitch(stuckLoc.getPitch());
                return safe;
            }
        }

        // 2. Check 4 horizontal neighbors
        int[][] cardinalOffsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] offset : cardinalOffsets) {
            org.bukkit.block.Block feet = world.getBlockAt(baseX + offset[0], baseY, baseZ + offset[1]);
            org.bukkit.block.Block head = world.getBlockAt(baseX + offset[0], baseY + 1, baseZ + offset[1]);
            if (feet.isPassable() && head.isPassable()) {
                Location safe = feet.getLocation().add(0.5, 0.0, 0.5);
                safe.setYaw(stuckLoc.getYaw());
                safe.setPitch(stuckLoc.getPitch());
                return safe;
            }
        }

        // 3. Check 1 block up and horizontal neighbors (sloped cliffs)
        for (int[] offset : cardinalOffsets) {
            org.bukkit.block.Block feet = world.getBlockAt(baseX + offset[0], baseY + 1, baseZ + offset[1]);
            org.bukkit.block.Block head = world.getBlockAt(baseX + offset[0], baseY + 2, baseZ + offset[1]);
            if (feet.isPassable() && head.isPassable()) {
                Location safe = feet.getLocation().add(0.5, 0.0, 0.5);
                safe.setYaw(stuckLoc.getYaw());
                safe.setPitch(stuckLoc.getPitch());
                return safe;
            }
        }

        return null;
    }

    public int getKnockbackTicks() {
        return knockbackTicks;
    }

    public UUID getLastDamagerId() {
        return lastDamagerId;
    }

    public long getLastDamageTime() {
        return lastDamageTime;
    }

    private void setBonesDamageFlash(boolean active) {
        for (VirtualBoneInstance b : bones.values()) {
            b.setDamageFlash(active);
        }
    }

    private double getClosestPlayerDistanceSquared() {
        Location loc = driverMob.getLocation();
        if (loc.getWorld() == null) return Double.MAX_VALUE;

        double closest = Double.MAX_VALUE;
        for (Player p : loc.getWorld().getPlayers()) {
            double dSq = p.getLocation().distanceSquared(loc);
            if (dSq < closest) {
                closest = dSq;
            }
        }
        return closest;
    }

    /**
     * Cleanly detaches and destroys virtual ItemDisplay bones and Interaction hitboxes
     * WITHOUT removing the underlying driver mob (used for chunk unloading and server restarts).
     */
    public void detachVisuals() {
        if (despawned) return;
        despawned = true;

        for (VirtualBoneInstance b : bones.values()) {
            b.cleanup();
        }
        bones.clear();

        for (VirtualHitboxInstance h : hitboxes.values()) {
            h.cleanup();
        }
        hitboxes.clear();

        if (driverMob != null && driverMob.isValid()) {
            for (org.bukkit.entity.Entity pass : new ArrayList<>(driverMob.getPassengers())) {
                if (pass instanceof org.bukkit.entity.ArmorStand) {
                    try {
                        pass.remove();
                    } catch (Throwable ignored) {}
                }
            }
        }

        try {
            var bm = org.bg52.synthetix.SynthetixPlugin.getInstance().getBedrockManager();
            if (bm != null && bm.getEntityVisualManager() != null) {
                bm.getEntityVisualManager().onEntityDespawn(this);
            }
        } catch (Throwable ignored) {}
    }

    public void despawn() {
        if (despawned) return;
        detachVisuals();

        if (driverMob != null && driverMob.isValid() && !driverMob.isDead()) {
            try {
                driverMob.remove();
            } catch (Throwable ignored) {}
        }
    }
}
