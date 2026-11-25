package org.bg52.synthetix.bedrock;

import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.entity.animation.EntityState;
import org.bg52.synthetix.entity.model.CustomEntityDefinition;
import org.bg52.synthetix.entity.runtime.CustomEntityInstance;
import org.bg52.synthetix.item.component.ComponentHandler;
import org.bg52.synthetix.util.MaterialUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bg52.synthetix.scheduler.PlatformScheduler;
import org.bukkit.inventory.meta.ItemMeta;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages physical Bedrock proxy rigs for active CustomEntityInstances.
 *
 * Because Bedrock Edition cannot render virtual ItemDisplay skeletal rigs,
 * this manager mounts an invisible marker ArmorStand passenger equipped with
 * Bedrock attachables on the driver mob. As the entity's state machine changes
 * (idle, walk/hop, attack, hurt, death), the head equipment is seamlessly updated.
 * Java players are isolated via player.hideEntity().
 */
public class BedrockEntityVisualManager implements Listener {

    private final SynthetixPlugin plugin;
    private final BedrockProtocolBridge bridge;

    // Instance ID -> Bedrock Proxy ArmorStand UUID
    private final Map<UUID, UUID> instanceToProxyMap = new ConcurrentHashMap<>();
    // ArmorStand UUID -> Instance ID
    private final Map<UUID, UUID> proxyToInstanceMap = new ConcurrentHashMap<>();

    private PlatformScheduler.TaskHandle tickTask = null;

    public BedrockEntityVisualManager(SynthetixPlugin plugin, BedrockProtocolBridge bridge) {
        this.plugin = plugin;
        this.bridge = bridge;
    }

    public void initialize() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        this.tickTask = PlatformScheduler.get().runGlobalTimer(this::tickProxies, 1L, 1L);
    }

    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        for (UUID proxyId : instanceToProxyMap.values()) {
            try {
                Entity stand = Bukkit.getEntity(proxyId);
                if (stand != null) {
                    stand.remove();
                }
            } catch (Throwable ignored) {}
        }
        instanceToProxyMap.clear();
        proxyToInstanceMap.clear();
    }

    private void tickProxies() {
        if (instanceToProxyMap.isEmpty()) return;
        for (Map.Entry<UUID, UUID> entry : instanceToProxyMap.entrySet()) {
            UUID instanceId = entry.getKey();
            UUID proxyId = entry.getValue();

            CustomEntityInstance instance = plugin.getCustomEntityManager() != null ? plugin.getCustomEntityManager().getInstance(instanceId) : null;
            if (instance == null || instance.isDespawned() || instance.getDriverMob() == null || !instance.getDriverMob().isValid()) {
                continue;
            }

            LivingEntity driver = instance.getDriverMob();
            Entity proxy = Bukkit.getEntity(proxyId);
            if (!(proxy instanceof ArmorStand stand) || !stand.isValid()) continue;

            // Ensure passenger mount remains intact across teleports/world changes
            if (!driver.getPassengers().contains(stand)) {
                driver.addPassenger(stand);
            }

            // Synchronize facing yaw, pitch, and rotation with the driver mob
            float yaw = driver.getYaw();
            float pitch = driver.getPitch();
            stand.setRotation(yaw, pitch);
            stand.setBodyYaw(yaw);
            stand.setHeadPose(new org.bukkit.util.EulerAngle(Math.toRadians(pitch), 0, 0));
        }
    }

    /**
     * Spawns a Bedrock proxy visual rig for a spawned custom entity instance.
     */
    public void onEntitySpawn(CustomEntityInstance instance) {
        if (instance == null || instance.getDriverMob() == null) return;
        LivingEntity driver = instance.getDriverMob();
        CustomEntityDefinition def = instance.getDefinition();

        // Idempotency check: Never spawn duplicate Bedrock proxies for the same instance
        if (instanceToProxyMap.containsKey(instance.getInstanceId())) {
            UUID existingId = instanceToProxyMap.get(instance.getInstanceId());
            Entity existing = Bukkit.getEntity(existingId);
            if (existing != null && existing.isValid()) {
                return;
            }
            instanceToProxyMap.remove(instance.getInstanceId());
            if (existingId != null) proxyToInstanceMap.remove(existingId);
        }

        BedrockModelSpec spec = def.getBedrockSpec();
        if (spec == null) {
            // Auto-derive from root bone if available
            var rootBone = def.getBone(def.getRootBoneName());
            Material mat = rootBone != null && rootBone.getMaterial() != null ? rootBone.getMaterial() : Material.CARROT_ON_A_STICK;
            int cmd = rootBone != null ? rootBone.getCustomModelData() : 0;
            String model = rootBone != null ? rootBone.getItemModel() : null;
            spec = new BedrockModelSpec(mat, cmd, model, "synthetix:" + def.getId());
        }

        if (!spec.isEnabled()) return;

        Location loc = driver.getLocation();
        if (loc.getWorld() == null) return;

        final BedrockModelSpec finalSpec = spec;
        Vector3f offset = finalSpec.getHeadOffset() != null ? finalSpec.getHeadOffset() : new Vector3f(0.0f, 0.0f, 0.0f);
        Location spawnLoc = loc.clone().add(offset.x, offset.y, offset.z);

        ArmorStand stand = loc.getWorld().spawn(spawnLoc, ArmorStand.class, s -> {
            s.setInvisible(true);
            s.setMarker(true);
            s.setSmall(finalSpec.isSmall());
            s.setArms(false);
            s.setBasePlate(false);
            s.setGravity(false);
            s.setPersistent(false); // Never save to region chunks
            s.setCanPickupItems(false);
            s.setCollidable(false);
            s.setInvulnerable(true);

            // Initial equipment from "idle" or default state
            BedrockStateSpec idleSpec = finalSpec.resolveState("idle");
            applyStateToStand(s, idleSpec, finalSpec);
        });

        // Mount proxy stand as passenger on driver mob for client-side synchronous movement
        driver.addPassenger(stand);

        // Hide proxy from Java players
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!bridge.isBedrockPlayer(online)) {
                online.hideEntity(plugin, stand);
            } else {
                online.showEntity(plugin, stand);
            }
        }

        instanceToProxyMap.put(instance.getInstanceId(), stand.getUniqueId());
        proxyToInstanceMap.put(stand.getUniqueId(), instance.getInstanceId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitySpawnEvent(org.bg52.synthetix.api.event.SynthetixEntitySpawnEvent event) {
        if (event.getEntityInstance() != null) {
            onEntitySpawn(event.getEntityInstance());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeathEvent(org.bg52.synthetix.api.event.SynthetixEntityDeathEvent event) {
        if (event.getEntityInstance() != null) {
            CustomEntityInstance instance = event.getEntityInstance();
            if (instance.isDying()) {
                onEntityStateChange(instance, org.bg52.synthetix.entity.animation.EntityState.DEATH);
            } else {
                onEntityDespawn(instance);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageEvent(org.bg52.synthetix.api.event.SynthetixEntityDamageEvent event) {
        if (event.getEntityInstance() != null) {
            onEntityHurt(event.getEntityInstance());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityStateChangeEvent(org.bg52.synthetix.api.event.SynthetixEntityStateChangeEvent event) {
        if (event.getEntityInstance() != null && event.getNewState() != null) {
            onEntityStateChange(event.getEntityInstance(), event.getNewState());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemoveFromWorld(com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent event) {
        Entity entity = event.getEntity();
        if (proxyToInstanceMap.containsKey(entity.getUniqueId())) {
            UUID instId = proxyToInstanceMap.remove(entity.getUniqueId());
            if (instId != null) {
                instanceToProxyMap.remove(instId);
            }
        } else if (entity instanceof LivingEntity living) {
            for (Entity pass : living.getPassengers()) {
                if (proxyToInstanceMap.containsKey(pass.getUniqueId())) {
                    UUID instId = proxyToInstanceMap.remove(pass.getUniqueId());
                    if (instId != null) {
                        instanceToProxyMap.remove(instId);
                    }
                    try {
                        pass.remove();
                    } catch (Throwable ignored) {}
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVanillaEntityDeath(org.bukkit.event.entity.EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        for (Entity pass : entity.getPassengers()) {
            if (proxyToInstanceMap.containsKey(pass.getUniqueId())) {
                UUID instId = proxyToInstanceMap.get(pass.getUniqueId());
                CustomEntityInstance instance = instId != null ? plugin.getCustomEntityManager().getInstance(instId) : null;
                if (instance != null && instance.isDying()) {
                    entity.removePassenger(pass);
                    Location deathLoc = entity.getLocation();
                    pass.teleport(deathLoc);
                    BedrockModelSpec spec = instance.getDefinition().getBedrockSpec();
                    if (spec != null && pass instanceof ArmorStand stand) {
                        BedrockStateSpec deathSpec = spec.resolveState("death");
                        applyStateToStand(stand, deathSpec, spec);
                    }
                    continue;
                }
                if (instId != null) {
                    instanceToProxyMap.remove(instId);
                }
                proxyToInstanceMap.remove(pass.getUniqueId());
                try {
                    pass.remove();
                } catch (Throwable ignored) {}
            }
        }
    }

    /**
     * Updates the Bedrock proxy's head equipment when the custom entity transitions state.
     */
    public void onEntityStateChange(CustomEntityInstance instance, EntityState newState) {
        if (instance == null || newState == null) return;
        UUID proxyId = instanceToProxyMap.get(instance.getInstanceId());
        if (proxyId == null) return;

        Entity entity = Bukkit.getEntity(proxyId);
        if (!(entity instanceof ArmorStand stand) || !stand.isValid()) return;

        BedrockModelSpec spec = instance.getDefinition().getBedrockSpec();
        if (spec == null || !spec.isEnabled()) return;

        String stateName = mapStateName(newState);
        BedrockStateSpec stateSpec = spec.resolveState(stateName);
        applyStateToStand(stand, stateSpec, spec);
    }

    /**
     * Temporarily triggers the hurt Bedrock visual model on damage flash.
     */
    public void onEntityHurt(CustomEntityInstance instance) {
        if (instance == null) return;
        UUID proxyId = instanceToProxyMap.get(instance.getInstanceId());
        if (proxyId == null) return;

        Entity entity = Bukkit.getEntity(proxyId);
        if (!(entity instanceof ArmorStand stand) || !stand.isValid()) return;

        BedrockModelSpec spec = instance.getDefinition().getBedrockSpec();
        if (spec == null || !spec.isEnabled()) return;

        BedrockStateSpec hurtSpec = spec.getStateSpec("hurt");
        if (hurtSpec != null) {
            applyStateToStand(stand, hurtSpec, spec);

            // Revert after hurt flash ticks (approx 6 ticks)
            PlatformScheduler.get().runGlobalLater(() -> {
                if (stand.isValid() && !instance.isDespawned()) {
                    EntityState currentState = instance.getStateMachine() != null ? instance.getStateMachine().getCurrentState() : EntityState.IDLE;
                    onEntityStateChange(instance, currentState);
                }
            }, 6L);
        }
    }

    /**
     * Cleans up the Bedrock proxy entity when a custom entity despawns or dies.
     */
    public void onEntityDespawn(CustomEntityInstance instance) {
        if (instance == null) return;
        UUID proxyId = instanceToProxyMap.remove(instance.getInstanceId());
        if (proxyId != null) {
            proxyToInstanceMap.remove(proxyId);
            try {
                Entity stand = Bukkit.getEntity(proxyId);
                if (stand != null) {
                    stand.remove();
                }
            } catch (Throwable ignored) {}
        }
    }

    private void applyStateToStand(ArmorStand stand, BedrockStateSpec stateSpec, BedrockModelSpec parentSpec) {
        if (stand == null || stateSpec == null) return;
        Material safeMat = MaterialUtil.toItemMaterial(stateSpec.getItemMaterial());
        ItemStack stack = new ItemStack(safeMat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (stateSpec.getItemModel() != null && !stateSpec.getItemModel().isEmpty()) {
                ComponentHandler.applyItemModel(meta, stateSpec.getItemModel(), stateSpec.getCustomModelData() > 0 ? stateSpec.getCustomModelData() : null);
            } else if (stateSpec.getCustomModelData() > 0) {
                meta.setCustomModelData(stateSpec.getCustomModelData());
            }
            stack.setItemMeta(meta);
        }
        EquipmentSlot slot = stateSpec.getSlot() != null ? stateSpec.getSlot() : (parentSpec != null ? parentSpec.getSlot() : EquipmentSlot.HEAD);
        equipItem(stand, slot, stack);
    }

    public void equipItem(ArmorStand stand, EquipmentSlot slot, ItemStack stack) {
        if (stand == null || stack == null) return;
        EquipmentSlot targetSlot = slot != null ? slot : EquipmentSlot.HEAD;
        switch (targetSlot) {
            case CHEST -> stand.getEquipment().setChestplate(stack);
            case HAND -> {
                stand.setArms(true);
                stand.getEquipment().setItemInMainHand(stack);
            }
            case OFF_HAND -> {
                stand.setArms(true);
                stand.getEquipment().setItemInOffHand(stack);
            }
            case FEET -> stand.getEquipment().setBoots(stack);
            case LEGS -> stand.getEquipment().setLeggings(stack);
            case HEAD -> stand.getEquipment().setHelmet(stack);
            default -> stand.getEquipment().setHelmet(stack);
        }
    }

    private String mapStateName(EntityState state) {
        return switch (state) {
            case IDLE -> "idle";
            case WALK -> "walk";
            case ATTACK -> "attack";
            case HURT -> "hurt";
            case DEATH -> "death";
            case SPAWN -> "idle";
            default -> state.name().toLowerCase(Locale.ROOT);
        };
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean isBedrock = bridge.isBedrockPlayer(player);

        for (UUID proxyId : instanceToProxyMap.values()) {
            Entity stand = Bukkit.getEntity(proxyId);
            if (stand != null && stand.isValid()) {
                if (isBedrock) {
                    player.showEntity(plugin, stand);
                } else {
                    player.hideEntity(plugin, stand);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (proxyToInstanceMap.containsKey(event.getRightClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    public UUID getProxyForInstance(UUID instanceId) {
        return instanceToProxyMap.get(instanceId);
    }
}
