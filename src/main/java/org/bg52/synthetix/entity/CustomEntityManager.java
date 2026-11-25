package org.bg52.synthetix.entity;

import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.api.event.SynthetixEntityDamageEvent;
import org.bg52.synthetix.api.event.SynthetixEntityDeathEvent;
import org.bg52.synthetix.api.event.SynthetixEntitySpawnEvent;
import org.bg52.synthetix.api.event.SynthetixEntitySplitEvent;
import org.bg52.synthetix.entity.ai.EntityAIType;
import org.bg52.synthetix.entity.model.CustomEntityDefinition;
import org.bg52.synthetix.scheduler.PlatformScheduler;
import org.bg52.synthetix.entity.model.EntityBoneSpec;
import org.bg52.synthetix.entity.model.EntityHitboxSpec;
import org.bg52.synthetix.entity.model.EntityLootSpec;
import org.bg52.synthetix.entity.runtime.CustomEntityInstance;
import org.bg52.synthetix.entity.runtime.VirtualBoneInstance;
import org.bg52.synthetix.entity.runtime.VirtualHitboxInstance;
import org.bg52.synthetix.item.ItemDefinition;
import org.bg52.synthetix.item.component.ComponentHandler;
import org.bg52.synthetix.util.MaterialUtil;
import org.bg52.synthetix.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.SlimeSplitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bg52.synthetix.scheduler.PlatformScheduler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Universal manager for custom entities in Synthetix.
 * Coordinates entity ticking, Variable Tick Rate (VTR) LOD, sub-voxel hitbox raycast interception,
 * anatomical damage calculations, and zero-leak non-persistent virtual bones.
 */
public class CustomEntityManager implements Listener {

    private final SynthetixPlugin plugin;
    private final Map<UUID, CustomEntityInstance> activeInstances = new ConcurrentHashMap<>();
    private final Map<UUID, CustomEntityInstance> driverToInstanceMap = new ConcurrentHashMap<>();
    private final Map<UUID, CustomEntityInstance> hitboxToInstanceMap = new ConcurrentHashMap<>();
    private final Set<UUID> processingDamageEntities = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<UUID, CustomEntityDefinition> recentDeadDrivers = new ConcurrentHashMap<>();

    private PlatformScheduler.TaskHandle tickTask;
    private long serverTickCounter = 0;

    public CustomEntityManager(SynthetixPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Run unified entity tick loop every 1 tick
        this.tickTask = PlatformScheduler.get().runGlobalTimer(this::tick, 1L, 1L);

        // Re-bind all existing custom entities across loaded worlds on boot/reload
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity living : world.getLivingEntities()) {
                checkAndBindEntity(living);
            }
        }
    }

    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }

        // Cleanly detach all virtual display and interaction entities without killing driver mobs
        for (CustomEntityInstance instance : new ArrayList<>(activeInstances.values())) {
            detachVisuals(instance);
        }
        activeInstances.clear();
        driverToInstanceMap.clear();
        hitboxToInstanceMap.clear();
    }

    private void tick() {
        serverTickCounter++;
        Iterator<Map.Entry<UUID, CustomEntityInstance>> iterator = activeInstances.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, CustomEntityInstance> entry = iterator.next();
            CustomEntityInstance instance = entry.getValue();

            if (instance.isDespawned() || (!instance.isDying() && (instance.getDriverMob() == null || !instance.getDriverMob().isValid()))) {
                if (instance.getDriverMob() != null) {
                    driverToInstanceMap.remove(instance.getDriverMob().getUniqueId());
                }
                for (UUID hbId : instance.getHitboxes().keySet()) {
                    hitboxToInstanceMap.remove(hbId);
                }
                iterator.remove();
                continue;
            }

            try {
                instance.tick(serverTickCounter);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error ticking custom entity: " + instance.getDefinition().getId(), e);
            }
        }
    }

    /**
     * Spawns a custom entity in the world at the given location.
     */
    public CustomEntityInstance spawnEntity(CustomEntityDefinition def, Location loc) {
        if (def == null || loc == null || loc.getWorld() == null) return null;
        if (plugin.getSettingsManager() != null && !plugin.getSettingsManager().isFeatureEnabled(org.bg52.synthetix.settings.GlobalFeature.CUSTOM_ENTITIES)) {
            return null;
        }
        World world = loc.getWorld();

        // 1. Spawn invisible Driver living mob
        EntityType baseType = def.getBaseEntityType() != null && def.getBaseEntityType().isAlive()
                ? def.getBaseEntityType()
                : EntityType.ZOMBIE;

        LivingEntity driver = (LivingEntity) world.spawnEntity(loc, baseType);
        driver.setInvisible(true);
        driver.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false
        ));
        driver.setSilent(true);
        driver.setRemoveWhenFarAway(false);

        if (driver instanceof org.bukkit.entity.Slime slime) {
            slime.setSize(def.getSlimeSize());
        }

        // Apply 1.20.6 Native Attributes
        applyAttribute(driver, Attribute.GENERIC_SCALE, def.getScale());
        applyAttribute(driver, Attribute.GENERIC_STEP_HEIGHT, def.getStepHeight());
        applyAttribute(driver, Attribute.GENERIC_GRAVITY, def.getGravity());
        applyAttribute(driver, Attribute.GENERIC_MOVEMENT_SPEED, def.getMovementSpeed());
        applyAttribute(driver, Attribute.GENERIC_MAX_HEALTH, def.getMaxHealth());
        driver.setHealth(def.getMaxHealth());
        applyAttribute(driver, Attribute.GENERIC_ATTACK_DAMAGE, def.getAttackDamage());
        applyAttribute(driver, Attribute.GENERIC_FOLLOW_RANGE, def.getFollowRange());

        // Tag driver mob with entity id
        driver.getPersistentDataContainer().set(CustomEntityDefinition.ENTITY_ID_KEY, PersistentDataType.STRING, def.getId());

        return bindEntity(def, driver);
    }

    /**
     * Authoritatively rebinds or mounts virtual bones and hitboxes to an existing driver mob
     * (used on entity spawn, chunk load, and server startup).
     */
    public CustomEntityInstance bindEntity(CustomEntityDefinition def, LivingEntity driver) {
        if (def == null || driver == null || !driver.isValid() || driver.isDead()) return null;

        CustomEntityInstance existing = driverToInstanceMap.get(driver.getUniqueId());
        if (existing != null && !existing.isDespawned()) {
            return existing;
        }

        // Clean up any old orphaned Display or Interaction passengers
        for (Entity passenger : new ArrayList<>(driver.getPassengers())) {
            if (passenger instanceof Display || passenger instanceof Interaction) {
                passenger.remove();
            }
        }

        // Ensure invisibility and persistence settings
        driver.setInvisible(true);
        driver.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false
        ));
        driver.setSilent(true);
        driver.setRemoveWhenFarAway(false);

        // Apply authoritative custom display name and visibility
        Component customName = TextUtil.parse(def.getDisplayName());
        driver.customName(customName);
        driver.setCustomNameVisible(def.isCustomNameVisible());

        // Suppress conflicting vanilla AI goals if driven by Synthetix AI
        if (driver instanceof org.bukkit.entity.Mob mob) {
            try {
                Bukkit.getMobGoals().removeAllGoals(mob);
            } catch (Throwable ignored) {}
        }

        CustomEntityInstance instance = new CustomEntityInstance(def, driver);

        // Attach visual skeletal bones
        attachVisualBones(instance);

        // Attach sub-voxel hitboxes
        attachHitboxes(instance);

        activeInstances.put(instance.getInstanceId(), instance);
        driverToInstanceMap.put(driver.getUniqueId(), instance);

        // Fire custom spawn event
        Bukkit.getPluginManager().callEvent(new SynthetixEntitySpawnEvent(instance));

        return instance;
    }

    private void attachVisualBones(CustomEntityInstance instance) {
        LivingEntity driver = instance.getDriverMob();
        CustomEntityDefinition def = instance.getDefinition();
        Location loc = driver.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        for (EntityBoneSpec boneSpec : def.getBones().values()) {
            boolean isVirtualOnly = (boneSpec.getMaterial() == null || boneSpec.getMaterial().isAir())
                    && (boneSpec.getItemModel() == null || boneSpec.getItemModel().isEmpty() || "none".equalsIgnoreCase(boneSpec.getItemModel()));

            if (isVirtualOnly) {
                VirtualBoneInstance boneInstance = new VirtualBoneInstance(boneSpec, null);
                instance.registerBone(boneSpec.getName(), boneInstance);
                continue;
            }

            ItemDisplay display = world.spawn(loc, ItemDisplay.class, d -> {
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setRotation(0.0f, 0.0f);

                Material safeMat = MaterialUtil.toItemMaterial(boneSpec.getMaterial() != null ? boneSpec.getMaterial() : Material.LEATHER_HORSE_ARMOR);
                ItemStack stack = new ItemStack(safeMat);
                ItemMeta meta = stack.getItemMeta();
                if (meta != null) {
                    if (boneSpec.getItemModel() != null && !boneSpec.getItemModel().isEmpty()) {
                        ComponentHandler.applyItemModel(meta, boneSpec.getItemModel(), boneSpec.getCustomModelData() > 0 ? boneSpec.getCustomModelData() : null);
                    } else if (boneSpec.getCustomModelData() > 0) {
                        meta.setCustomModelData(boneSpec.getCustomModelData());
                    }
                    stack.setItemMeta(meta);
                }
                d.setItemStack(stack);

                if (boneSpec.getBrightness() != null) {
                    d.setBrightness(new Display.Brightness(boneSpec.getBrightness(), boneSpec.getBrightness()));
                }

                if (boneSpec.getGlowColorHex() != null) {
                    d.setGlowing(true);
                    try {
                        String hex = boneSpec.getGlowColorHex().replace("#", "");
                        d.setGlowColorOverride(Color.fromRGB(Integer.parseInt(hex, 16)));
                    } catch (Exception ignored) {}
                }

                d.setPersistent(false); // Never save virtual bones to world region files
                d.setViewRange(1.5f);
            });

            // Mount display onto driver mob so client tracks position with zero packet lag
            driver.addPassenger(display);

            VirtualBoneInstance boneInstance = new VirtualBoneInstance(boneSpec, display);
            instance.registerBone(boneSpec.getName(), boneInstance);
        }
    }

    private void attachHitboxes(CustomEntityInstance instance) {
        LivingEntity driver = instance.getDriverMob();
        CustomEntityDefinition def = instance.getDefinition();
        Location loc = driver.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        for (EntityHitboxSpec hitboxSpec : def.getAllHitboxes()) {
            Interaction interaction = world.spawn(loc, Interaction.class, i -> {
                i.setInteractionWidth(hitboxSpec.getWidth());
                i.setInteractionHeight(hitboxSpec.getHeight());
                i.setResponsive(true);
                i.setPersistent(false);
            });

            VirtualHitboxInstance hbInstance = new VirtualHitboxInstance(hitboxSpec, interaction);
            instance.registerHitbox(interaction.getUniqueId(), hbInstance);
            hitboxToInstanceMap.put(interaction.getUniqueId(), instance);
        }
    }

    /**
     * Checks if a living entity is a registered custom entity and binds it if not currently active.
     */
    public void checkAndBindEntity(LivingEntity living) {
        if (living == null || !living.isValid() || living.isDead()) return;
        if (driverToInstanceMap.containsKey(living.getUniqueId())) return;

        if (living.getPersistentDataContainer().has(CustomEntityDefinition.ENTITY_ID_KEY, PersistentDataType.STRING)) {
            String entityId = living.getPersistentDataContainer().get(CustomEntityDefinition.ENTITY_ID_KEY, PersistentDataType.STRING);
            if (entityId != null) {
                CustomEntityDefinition def = plugin.getCustomEntityRegistry().get(entityId);
                if (def != null) {
                    bindEntity(def, living);
                }
            }
        }
    }

    /**
     * Detaches virtual bones and hitboxes without killing the driver mob.
     */
    public void detachVisuals(CustomEntityInstance instance) {
        if (instance == null) return;
        if (plugin.getBedrockManager() != null && plugin.getBedrockManager().getEntityVisualManager() != null) {
            plugin.getBedrockManager().getEntityVisualManager().onEntityDespawn(instance);
        }
        instance.detachVisuals();
        activeInstances.remove(instance.getInstanceId());
        if (instance.getDriverMob() != null) {
            driverToInstanceMap.remove(instance.getDriverMob().getUniqueId());
        }
        for (UUID hbId : instance.getHitboxes().keySet()) {
            hitboxToInstanceMap.remove(hbId);
        }
    }

    public CustomEntityInstance spawnEntity(String entityId, Location loc) {
        CustomEntityDefinition def = plugin.getCustomEntityRegistry().get(entityId);
        if (def == null) return null;
        return spawnEntity(def, loc);
    }

    private void applyAttribute(LivingEntity entity, Attribute attribute, double value) {
        try {
            AttributeInstance inst = entity.getAttribute(attribute);
            if (inst != null) {
                inst.setBaseValue(value);
            }
        } catch (Exception ignored) {}
    }

    public CustomEntityInstance getInstance(UUID uuid) {
        if (uuid == null) return null;
        CustomEntityInstance inst = activeInstances.get(uuid);
        if (inst != null) return inst;
        inst = driverToInstanceMap.get(uuid);
        if (inst != null) return inst;
        return hitboxToInstanceMap.get(uuid);
    }

    public CustomEntityInstance getInstance(Entity entity) {
        if (entity == null) return null;
        return getInstance(entity.getUniqueId());
    }

    public Collection<CustomEntityInstance> getActiveInstances() {
        return Collections.unmodifiableCollection(activeInstances.values());
    }

    /**
     * Plays an animation on the specified entity with default looping and smooth SLERP blending.
     */
    public boolean playAnimation(Entity entity, String animationName) {
        return playAnimation(entity, animationName, false, 3.0f);
    }

    /**
     * Plays an animation on the specified entity with full parameter controls.
     */
    public boolean playAnimation(Entity entity, String animationName, boolean loop, float blendTicks) {
        if (entity == null || animationName == null) return false;
        return playAnimation(entity.getUniqueId(), animationName, loop, blendTicks);
    }

    /**
     * Plays an animation on an entity resolved by UUID.
     */
    public boolean playAnimation(UUID uuid, String animationName, boolean loop, float blendTicks) {
        CustomEntityInstance inst = getInstance(uuid);
        if (inst == null || inst.getAnimationController() == null) return false;

        String cleanTrack = animationName.toLowerCase(Locale.ROOT);
        if (!inst.getAnimationController().hasTrack(cleanTrack)) {
            // Also check stripped prefix
            if (cleanTrack.startsWith("animation.")) {
                int dot = cleanTrack.indexOf('.', "animation.".length());
                if (dot > 0 && dot < cleanTrack.length() - 1) {
                    cleanTrack = cleanTrack.substring(dot + 1);
                }
            }
        }

        if (inst.getAnimationController().hasTrack(cleanTrack)) {
            inst.getAnimationController().play(cleanTrack, loop, blendTicks);
            return true;
        }
        return false;
    }

    /**
     * Stops any currently playing animation on the custom entity.
     */
    public void stopAnimation(Entity entity) {
        if (entity == null) return;
        CustomEntityInstance inst = getInstance(entity.getUniqueId());
        if (inst != null && inst.getAnimationController() != null) {
            inst.getAnimationController().stop();
        }
    }

    /**
     * Returns all animation track names registered on the given custom entity.
     */
    public List<String> getAvailableAnimations(Entity entity) {
        if (entity == null) return List.of();
        CustomEntityInstance inst = getInstance(entity.getUniqueId());
        if (inst == null || inst.getAnimationController() == null) return List.of();
        return new ArrayList<>(inst.getAnimationController().getTracks().keySet());
    }

    /**
     * Checks if a custom entity has the specified animation track.
     */
    public boolean hasAnimation(Entity entity, String animationName) {
        if (entity == null || animationName == null) return false;
        CustomEntityInstance inst = getInstance(entity.getUniqueId());
        if (inst == null || inst.getAnimationController() == null) return false;
        return inst.getAnimationController().hasTrack(animationName.toLowerCase(Locale.ROOT));
    }

    /**
     * Gets the current high-level state of the custom entity.
     */
    public org.bg52.synthetix.entity.animation.EntityState getEntityState(Entity entity) {
        if (entity == null) return null;
        CustomEntityInstance inst = getInstance(entity.getUniqueId());
        return inst != null && inst.getStateMachine() != null ? inst.getStateMachine().getCurrentState() : null;
    }

    /**
     * Sets the high-level state of the custom entity (triggers coupled animation).
     */
    public void setEntityState(Entity entity, org.bg52.synthetix.entity.animation.EntityState state) {
        if (entity == null || state == null) return;
        CustomEntityInstance inst = getInstance(entity.getUniqueId());
        if (inst != null && inst.getStateMachine() != null) {
            inst.getStateMachine().transitionTo(state);
        }
    }

    // -------------------------------------------------------------------------
    // Event Interceptions: Damage & Raycast Routing
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damaged = event.getEntity();
        if (processingDamageEntities.contains(damaged.getUniqueId())) {
            return;
        }

        // 1. Check if damage struck a sub-voxel Interaction hitbox
        CustomEntityInstance hitboxInstance = hitboxToInstanceMap.get(damaged.getUniqueId());
        if (hitboxInstance != null) {
            event.setCancelled(true);
            VirtualHitboxInstance hb = hitboxInstance.getHitboxes().get(damaged.getUniqueId());
            double multiplier = hb != null ? hb.getSpec().getDamageMultiplier() : 1.0;
            boolean weakpoint = hb != null && hb.getSpec().isWeakpoint();

            SynthetixEntityDamageEvent customEvent = new SynthetixEntityDamageEvent(
                    hitboxInstance, event.getDamager(), hb != null ? hb.getSpec() : null, event.getDamage(), multiplier
            );
            Bukkit.getPluginManager().callEvent(customEvent);
            if (!customEvent.isCancelled()) {
                if (hitboxInstance.getDriverMob() != null) {
                    processingDamageEntities.add(hitboxInstance.getDriverMob().getUniqueId());
                }
                try {
                    hitboxInstance.handleDamage(customEvent.getBaseDamage(), customEvent.getMultiplier(), weakpoint, event.getDamager());
                } finally {
                    if (hitboxInstance.getDriverMob() != null) {
                        processingDamageEntities.remove(hitboxInstance.getDriverMob().getUniqueId());
                    }
                }
            }
            return;
        }

        // 2. Check if damage struck the main Driver mob directly
        CustomEntityInstance driverInstance = driverToInstanceMap.get(damaged.getUniqueId());
        if (driverInstance != null) {
            SynthetixEntityDamageEvent customEvent = new SynthetixEntityDamageEvent(
                    driverInstance, event.getDamager(), null, event.getDamage(), 1.0
            );
            Bukkit.getPluginManager().callEvent(customEvent);
            if (!customEvent.isCancelled()) {
                event.setDamage(customEvent.getBaseDamage() * customEvent.getMultiplier());
                driverInstance.handleDirectDamage(event.getDamage(), event.getDamager());
            } else {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) return;
        CustomEntityInstance driverInstance = driverToInstanceMap.get(event.getEntity().getUniqueId());
        if (driverInstance != null) {
            SynthetixEntityDamageEvent customEvent = new SynthetixEntityDamageEvent(
                    driverInstance, null, null, event.getDamage(), 1.0
            );
            Bukkit.getPluginManager().callEvent(customEvent);
            if (!customEvent.isCancelled()) {
                event.setDamage(customEvent.getBaseDamage() * customEvent.getMultiplier());
                driverInstance.handleDirectDamage(event.getDamage(), null);
            } else {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        EntityDamageEvent lastDamage = player.getLastDamageCause();
        if (lastDamage instanceof EntityDamageByEntityEvent edbe) {
            CustomEntityInstance instance = getInstance(edbe.getDamager());
            if (instance != null) {
                CustomEntityDefinition def = instance.getDefinition();
                Component customName = TextUtil.parse(def.getDisplayName());

                Component originalMsg = event.deathMessage();
                if (originalMsg != null) {
                    String vanillaName = def.getBaseEntityType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
                    String vanillaNameCap = def.getBaseEntityType().name().replace('_', ' ');
                    Component updated = originalMsg
                            .replaceText(b -> b.match("(?i)\\b" + Pattern.quote(def.getBaseEntityType().name()) + "\\b").replacement(customName))
                            .replaceText(b -> b.match("(?i)\\b" + Pattern.quote(vanillaName) + "\\b").replacement(customName))
                            .replaceText(b -> b.match("(?i)\\b" + Pattern.quote(vanillaNameCap) + "\\b").replacement(customName));
                    event.deathMessage(updated);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        CustomEntityInstance instance = driverToInstanceMap.get(entity.getUniqueId());
        if (instance == null) return;

        CustomEntityDefinition def = instance.getDefinition();

        // Clear vanilla default mob drops
        event.getDrops().clear();
        event.setDroppedExp(def.getLoot().getExp());

        // Process custom drops and looting enchantment multiplier
        Player killer = entity.getKiller();
        int lootingLevel = 0;
        if (killer != null && killer.getInventory().getItemInMainHand() != null) {
            lootingLevel = killer.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.LOOTING);
        }

        for (EntityLootSpec.DropEntry drop : def.getLoot().getDrops()) {
            double chance = drop.getChance();
            if (drop.isAffectedByLooting() && lootingLevel > 0) {
                chance = Math.min(1.0, chance + (lootingLevel * 0.05));
            }

            if (ThreadLocalRandom.current().nextDouble() <= chance) {
                int amount = drop.getMinAmount();
                if (drop.getMaxAmount() > drop.getMinAmount()) {
                    amount += ThreadLocalRandom.current().nextInt(drop.getMaxAmount() - drop.getMinAmount() + 1);
                }
                if (drop.isAffectedByLooting() && lootingLevel > 0) {
                    amount += ThreadLocalRandom.current().nextInt(lootingLevel + 1);
                }

                ItemStack stack = buildDropItemStack(drop.getItemId(), amount);
                if (stack != null) {
                    event.getDrops().add(stack);
                }
            }
        }

        // Play custom death audio if configured
        if (def.getSounds().getDeathSound() != null && entity.getLocation().getWorld() != null) {
            try {
                entity.getLocation().getWorld().playSound(
                        entity.getLocation(),
                        def.getSounds().getDeathSound(),
                        def.getSounds().getVolume(),
                        def.getSounds().getPitch()
                );
            } catch (Exception ignored) {}
        }

        Bukkit.getPluginManager().callEvent(new SynthetixEntityDeathEvent(instance, killer, event.getDrops(), event.getDroppedExp()));

        // Trigger death animation track
        instance.startDeathAnimation();

        // Prevent vanilla slime split at the engine level if splitting is disabled
        if (entity instanceof org.bukkit.entity.Slime slime && !def.isAllowSplitting()) {
            slime.setSize(1);
        }

        // Cache recently dead custom driver to intercept subsequent split and spawn events
        recentDeadDrivers.put(entity.getUniqueId(), def);
        PlatformScheduler.get().runGlobalLater(() -> recentDeadDrivers.remove(entity.getUniqueId()), 40L);

        // Cleanup any child slimes spawned near the dying entity
        if (!def.isAllowSplitting()) {
            Location deathLoc = entity.getLocation();
            PlatformScheduler.get().runGlobalLater(() -> {
                if (deathLoc.getWorld() == null) return;
                for (Entity e : deathLoc.getWorld().getNearbyEntities(deathLoc, 5, 5, 5)) {
                    if (e instanceof org.bukkit.entity.Slime childSlime) {
                        if (childSlime.getSize() <= 1 && !driverToInstanceMap.containsKey(childSlime.getUniqueId())) {
                            Component customName = childSlime.customName();
                            String entityId = childSlime.getPersistentDataContainer().get(CustomEntityDefinition.ENTITY_ID_KEY, PersistentDataType.STRING);
                            if (def.getId().equalsIgnoreCase(entityId) || customName != null) {
                                childSlime.remove();
                            }
                        }
                    }
                }
            }, 1L);
        }

        // Clean up hitbox mappings BEFORE despawning visuals
        for (UUID hbId : new ArrayList<>(instance.getHitboxes().keySet())) {
            hitboxToInstanceMap.remove(hbId);
        }

        // If dying animation is playing, allow instance.tick() to play the death track
        // to completion before despawning and removing the instance.
        if (!instance.isDying()) {
            if (plugin.getBedrockManager() != null && plugin.getBedrockManager().getEntityVisualManager() != null) {
                plugin.getBedrockManager().getEntityVisualManager().onEntityDespawn(instance);
            }
            instance.despawn();
            activeInstances.remove(instance.getInstanceId());
            driverToInstanceMap.remove(entity.getUniqueId());
        }
    }

    private ItemStack buildDropItemStack(String itemId, int amount) {
        if (itemId == null || itemId.isEmpty()) return null;

        // Try Synthetix custom item first
        ItemDefinition customItem = plugin.getItemRegistry().get(itemId);
        if (customItem != null) {
            ItemStack is = customItem.buildItemStack();
            is.setAmount(Math.max(1, amount));
            return is;
        }

        // Fallback to vanilla material
        Material mat = Material.matchMaterial(itemId);
        if (mat != null && !mat.isAir()) {
            return new ItemStack(MaterialUtil.toItemMaterial(mat), Math.max(1, amount));
        }

        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityJump(com.destroystokyo.paper.event.entity.EntityJumpEvent event) {
        CustomEntityInstance instance = driverToInstanceMap.get(event.getEntity().getUniqueId());
        if (instance != null) {
            // Cancel vanilla engine jumps for custom entities whose movement is controlled by Synthetix AI
            if (instance.getDefinition().getAi() != null && instance.getDefinition().getAi().getAiType() == EntityAIType.HOSTILE_LEAP) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSlimeSplit(SlimeSplitEvent event) {
        UUID slimeUuid = event.getEntity().getUniqueId();
        CustomEntityInstance instance = driverToInstanceMap.get(slimeUuid);
        CustomEntityDefinition def = instance != null ? instance.getDefinition() : recentDeadDrivers.get(slimeUuid);

        if (def == null) {
            String entityId = event.getEntity().getPersistentDataContainer().get(CustomEntityDefinition.ENTITY_ID_KEY, PersistentDataType.STRING);
            if (entityId != null) {
                def = plugin.getCustomEntityRegistry().get(entityId);
            }
        }

        if (def == null) return;

        boolean allowedByDefault = def.isAllowSplitting();
        int targetCount = def.getSplitCount() > 0 ? def.getSplitCount() : event.getCount();

        SynthetixEntitySplitEvent splitEvent = new SynthetixEntitySplitEvent(
                instance, targetCount, allowedByDefault, def.getChildCustomEntityId()
        );
        Bukkit.getPluginManager().callEvent(splitEvent);

        if (splitEvent.isCancelled() || !splitEvent.isAllowed() || splitEvent.getCount() <= 0) {
            event.setCancelled(true);
            event.setCount(0);
            return;
        }

        event.setCount(splitEvent.getCount());
        String childId = splitEvent.getChildCustomEntityId();
        if (childId != null && !childId.trim().isEmpty()) {
            event.setCancelled(true);
            Location loc = event.getEntity().getLocation();
            for (int i = 0; i < splitEvent.getCount(); i++) {
                spawnEntity(childId, loc);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(org.bukkit.event.entity.CreatureSpawnEvent event) {
        if (event.getSpawnReason() == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SLIME_SPLIT) {
            for (CustomEntityDefinition deadDef : recentDeadDrivers.values()) {
                if (!deadDef.isAllowSplitting()) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntitySpawn(org.bukkit.event.entity.EntitySpawnEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Slime slime) {
            String entityId = slime.getPersistentDataContainer().get(CustomEntityDefinition.ENTITY_ID_KEY, PersistentDataType.STRING);
            if (entityId != null && !driverToInstanceMap.containsKey(slime.getUniqueId())) {
                CustomEntityDefinition def = plugin.getCustomEntityRegistry().get(entityId);
                if (def != null && !def.isAllowSplitting()) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity e : event.getChunk().getEntities()) {
            if (e instanceof LivingEntity living && !e.isDead() && e.isValid()) {
                checkAndBindEntity(living);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Entity e : event.getChunk().getEntities()) {
            CustomEntityInstance inst = driverToInstanceMap.get(e.getUniqueId());
            if (inst != null) {
                detachVisuals(inst);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        for (CustomEntityInstance inst : new ArrayList<>(activeInstances.values())) {
            if (inst.getDriverMob() != null && event.getWorld().equals(inst.getDriverMob().getWorld())) {
                detachVisuals(inst);
            }
        }
    }
}

