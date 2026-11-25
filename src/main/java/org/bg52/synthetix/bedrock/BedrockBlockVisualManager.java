package org.bg52.synthetix.bedrock;

import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.block.CustomBlockDefinition;
import org.bg52.synthetix.block.PlacedBlockInstance;
import org.bg52.synthetix.block.RenderingMethod;
import org.bg52.synthetix.item.component.ComponentHandler;
import org.bg52.synthetix.util.MaterialUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages physical proxy entities for custom blocks to provide 100% visual fidelity
 * on Minecraft Bedrock Edition (via Geyser/Floodgate).
 *
 * Because Bedrock Edition lacks ItemDisplay/BlockDisplay entities, this manager projects
 * non-persistent, marker ArmorStand proxies equipped with Bedrock attachable head items.
 * Java players are isolated from these proxies via player.hideEntity().
 */
public class BedrockBlockVisualManager implements Listener {

    private final SynthetixPlugin plugin;
    private final BedrockProtocolBridge bridge;

    // Block Location (normalized) -> ArmorStand UUID
    private final Map<Location, UUID> blockToProxyMap = new ConcurrentHashMap<>();
    // ArmorStand UUID -> Block Location (normalized)
    private final Map<UUID, Location> proxyToBlockMap = new ConcurrentHashMap<>();
    // Block Location (normalized) -> Block Crack Proxy ArmorStand UUID
    private final Map<Location, UUID> blockToCrackProxyMap = new ConcurrentHashMap<>();

    public BedrockBlockVisualManager(SynthetixPlugin plugin, BedrockProtocolBridge bridge) {
        this.plugin = plugin;
        this.bridge = bridge;
    }

    public void initialize() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void shutdown() {
        for (UUID uuid : blockToProxyMap.values()) {
            try {
                Entity entity = Bukkit.getEntity(uuid);
                if (entity != null) {
                    entity.remove();
                }
            } catch (Throwable ignored) {}
        }
        blockToProxyMap.clear();
        proxyToBlockMap.clear();

        for (UUID uuid : blockToCrackProxyMap.values()) {
            try {
                Entity entity = Bukkit.getEntity(uuid);
                if (entity != null) {
                    entity.remove();
                }
            } catch (Throwable ignored) {}
        }
        blockToCrackProxyMap.clear();
    }

    public static Location normalize(Location loc) {
        if (loc == null || loc.getWorld() == null) return loc;
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /**
     * Spawns or updates a Bedrock visual proxy entity for a placed block instance.
     */
    public void onBlockSpawn(PlacedBlockInstance instance, CustomBlockDefinition def) {
        if (instance == null || def == null) return;
        // Baked state blocks (NoteBlock / Tripwire) don't need visual entities
        if (def.getRenderingMethod() == RenderingMethod.NOTE_BLOCK || def.getRenderingMethod() == RenderingMethod.TRIPWIRE) {
            return;
        }

        Location normLoc = normalize(instance.getLocation());
        removeBedrockProxy(normLoc);

        BedrockModelSpec spec = def.getBedrockSpec();
        if (spec == null) {
            // Auto-derive generalized Bedrock spec from block definition
            spec = new BedrockModelSpec(
                    def.getItemMaterial() != null ? def.getItemMaterial() : Material.CARROT_ON_A_STICK,
                    def.getCustomModelData(),
                    def.getItemModel(),
                    "synthetix:" + def.getId()
            );
        }

        if (!spec.isEnabled()) return;

        Vector3f offset = spec.getHeadOffset() != null ? spec.getHeadOffset() : new Vector3f(0.0f, 0.0f, 0.0f);
        float yaw = getFacingYaw(instance.getFacing()) + spec.getHeadYawOffset();

        // Rotate horizontal local offset (offset.x, offset.z) by block facing yaw
        double rad = Math.toRadians(yaw);
        double dx = offset.x * Math.cos(rad) - offset.z * Math.sin(rad);
        double dz = offset.x * Math.sin(rad) + offset.z * Math.cos(rad);
        Location spawnLoc = normLoc.clone().add(0.5 + dx, offset.y, 0.5 + dz);
        spawnLoc.setYaw(yaw);
        spawnLoc.setPitch(0.0f);

        if (spawnLoc.getWorld() == null) return;

        final BedrockModelSpec finalSpec = spec;
        ArmorStand stand = spawnLoc.getWorld().spawn(spawnLoc, ArmorStand.class, s -> {
            s.setInvisible(true);
            s.setMarker(true);
            s.setSmall(finalSpec.isSmall());
            s.setArms(false);
            s.setBasePlate(false);
            s.setGravity(false);
            s.setPersistent(false); // Clean memory, never save to world chunks
            s.setCanPickupItems(false);
            s.setCollidable(false);
            s.setInvulnerable(true);

            // Construct head item stack
            Material safeMat = MaterialUtil.toItemMaterial(finalSpec.getDefaultMaterial());
            ItemStack stack = new ItemStack(safeMat);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                if (finalSpec.getDefaultItemModel() != null && !finalSpec.getDefaultItemModel().isEmpty()) {
                    ComponentHandler.applyItemModel(meta, finalSpec.getDefaultItemModel(), finalSpec.getDefaultCustomModelData() > 0 ? finalSpec.getDefaultCustomModelData() : null);
                } else if (finalSpec.getDefaultCustomModelData() > 0) {
                    meta.setCustomModelData(finalSpec.getDefaultCustomModelData());
                }
                stack.setItemMeta(meta);
            }

            // If placed block instance already has an ItemDisplay with a custom item stack, mirror it
            if (!instance.getDisplayEntityUuids().isEmpty()) {
                for (UUID displayId : instance.getDisplayEntityUuids()) {
                    Entity e = Bukkit.getEntity(displayId);
                    if (e instanceof org.bukkit.entity.ItemDisplay id && id.getItemStack() != null && id.getItemStack().getType() != Material.AIR) {
                        stack = id.getItemStack().clone();
                        break;
                    }
                }
            }

            equipItem(s, finalSpec.getSlot(), stack);
        });

        // Isolate from Java players - only Bedrock players should see the proxy stand
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!bridge.isBedrockPlayer(online)) {
                online.hideEntity(plugin, stand);
            } else {
                online.showEntity(plugin, stand);
            }
        }

        blockToProxyMap.put(normLoc, stand.getUniqueId());
        proxyToBlockMap.put(stand.getUniqueId(), normLoc);
    }

    /**
     * Cleans up the Bedrock proxy entity associated with a custom block location.
     */
    public void onBlockCleanup(PlacedBlockInstance instance) {
        if (instance == null) return;
        removeBedrockProxy(normalize(instance.getLocation()));
    }

    public void removeBedrockProxy(Location normLoc) {
        if (normLoc == null) return;
        try {
            cleanupCrackVisual(normLoc);
        } catch (Throwable ignored) {}
        UUID uuid = blockToProxyMap.remove(normLoc);
        if (uuid != null) {
            proxyToBlockMap.remove(uuid);
            try {
                Entity entity = Bukkit.getEntity(uuid);
                if (entity != null) {
                    entity.remove();
                }
            } catch (Throwable ignored) {}
        }
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

    public void updateProxyVisual(Location loc, ItemStack item) {
        if (loc == null || item == null) return;
        Location normLoc = normalize(loc);
        UUID uuid = blockToProxyMap.get(normLoc);
        if (uuid == null) return;
        Entity entity = Bukkit.getEntity(uuid);
        if (entity instanceof ArmorStand stand) {
            PlacedBlockInstance instance = plugin.getCustomBlockManager().getPlacedBlock(normLoc);
            CustomBlockDefinition def = instance != null ? plugin.getCustomBlockRegistry().get(instance.getBlockId()) : null;
            EquipmentSlot slot = (def != null && def.getBedrockSpec() != null) ? def.getBedrockSpec().getSlot() : EquipmentSlot.CHEST;
            equipItem(stand, slot, item);
        }
    }

    public void updateProxyVisual(Location loc, EquipmentSlot slot, ItemStack item) {
        if (loc == null || item == null) return;
        Location normLoc = normalize(loc);
        UUID uuid = blockToProxyMap.get(normLoc);
        if (uuid == null) {
            // Lazy-spawn proxy if not yet created (e.g. during custom placement pipeline)
            PlacedBlockInstance instance = plugin.getCustomBlockManager() != null ? plugin.getCustomBlockManager().getPlacedBlock(normLoc) : null;
            if (instance != null) {
                CustomBlockDefinition def = plugin.getCustomBlockRegistry() != null ? plugin.getCustomBlockRegistry().get(instance.getBlockId()) : null;
                if (def != null) {
                    onBlockSpawn(instance, def);
                    uuid = blockToProxyMap.get(normLoc);
                }
            }
        }
        if (uuid == null) return;
        Entity entity = Bukkit.getEntity(uuid);
        if (entity instanceof ArmorStand stand) {
            equipItem(stand, slot, item);
        }
    }

    public ArmorStand getProxyStand(Location loc) {
        if (loc == null) return null;
        UUID uuid = blockToProxyMap.get(normalize(loc));
        if (uuid == null) return null;
        Entity entity = Bukkit.getEntity(uuid);
        return entity instanceof ArmorStand stand ? stand : null;
    }

    private float getFacingYaw(BlockFace facing) {
        if (facing == null) return 0.0f;
        return switch (facing) {
            case NORTH -> 180.0f;
            case SOUTH -> 0.0f;
            case WEST -> 90.0f;
            case EAST -> 270.0f;
            default -> 0.0f;
        };
    }

    public void updateCrackVisual(Location loc, int stage) {
        if (loc == null || loc.getWorld() == null) return;
        Location normLoc = normalize(loc);
        if (stage < 0 || stage > 9) {
            cleanupCrackVisual(normLoc);
            return;
        }

        UUID uuid = blockToCrackProxyMap.get(normLoc);
        Entity entity = uuid != null ? Bukkit.getEntity(uuid) : null;
        ArmorStand stand = (entity instanceof ArmorStand as && as.isValid()) ? as : null;

        if (stand == null) {
            Location spawnLoc;
            UUID blockProxyUuid = blockToProxyMap.get(normLoc);
            Entity blockProxy = blockProxyUuid != null ? Bukkit.getEntity(blockProxyUuid) : null;
            if (blockProxy != null && blockProxy.isValid()) {
                spawnLoc = blockProxy.getLocation().clone();
            } else {
                spawnLoc = normLoc.clone().add(0.5, -1.4, 0.5);
            }
            stand = loc.getWorld().spawn(spawnLoc, ArmorStand.class, s -> {
                s.setInvisible(true);
                s.setMarker(true);
                s.setSmall(false);
                s.setArms(false);
                s.setBasePlate(false);
                s.setGravity(false);
                s.setPersistent(false);
                s.setCanPickupItems(false);
                s.setCollidable(false);
                s.setInvulnerable(true);
            });

            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!bridge.isBedrockPlayer(online)) {
                    online.hideEntity(plugin, stand);
                } else {
                    online.showEntity(plugin, stand);
                }
            }
            blockToCrackProxyMap.put(normLoc, stand.getUniqueId());
        }

        ItemStack crackStack = new ItemStack(Material.PAPER);
        ItemMeta meta = crackStack.getItemMeta();
        if (meta != null) {
            ComponentHandler.applyItemModel(meta, "synthetix:destroy_stage_" + stage, 19000 + stage);
            crackStack.setItemMeta(meta);
        }
        stand.getEquipment().setHelmet(crackStack);
    }

    public void cleanupCrackVisual(Location loc) {
        if (loc == null) return;
        Location normLoc = normalize(loc);
        UUID uuid = blockToCrackProxyMap.remove(normLoc);
        if (uuid != null) {
            try {
                Entity entity = Bukkit.getEntity(uuid);
                if (entity != null) {
                    entity.remove();
                }
            } catch (Throwable ignored) {}
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPostPlace(org.bg52.synthetix.api.event.SynthetixBlockPostPlaceEvent event) {
        if (event.getInstance() != null && event.getBlockDefinition() != null) {
            onBlockSpawn(event.getInstance(), event.getBlockDefinition());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(org.bg52.synthetix.api.event.SynthetixBlockBreakEvent event) {
        removeBedrockProxy(normalize(event.getLocation()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(org.bg52.synthetix.api.event.SynthetixBlockExplodeEvent event) {
        removeBedrockProxy(normalize(event.getLocation()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean isBedrock = bridge.isBedrockPlayer(player);

        // Update visibility of all active Bedrock block proxies for the joining player
        for (UUID proxyId : blockToProxyMap.values()) {
            Entity stand = Bukkit.getEntity(proxyId);
            if (stand != null && stand.isValid()) {
                if (isBedrock) {
                    player.showEntity(plugin, stand);
                } else {
                    player.hideEntity(plugin, stand);
                }
            }
        }
        for (UUID proxyId : blockToCrackProxyMap.values()) {
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
        if (proxyToBlockMap.containsKey(event.getRightClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteractAtProxy(PlayerInteractAtEntityEvent event) {
        UUID entityId = event.getRightClicked().getUniqueId();
        Location blockLoc = proxyToBlockMap.get(entityId);
        if (blockLoc != null) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            PlacedBlockInstance instance = plugin.getCustomBlockManager().getPlacedBlock(blockLoc);
            if (instance != null) {
                CustomBlockDefinition targetDef = plugin.getCustomBlockRegistry().get(instance.getBlockId());
                if (targetDef != null) {
                    ItemStack item = event.getHand() == EquipmentSlot.HAND ? player.getInventory().getItemInMainHand() : player.getInventory().getItemInOffHand();
                    org.bg52.synthetix.api.event.SynthetixBlockInteractEvent interactEvent = new org.bg52.synthetix.api.event.SynthetixBlockInteractEvent(
                            targetDef, instance.getLocation(), player, org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK, event.getHand(), BlockFace.UP, item);
                    Bukkit.getPluginManager().callEvent(interactEvent);
                    if (!interactEvent.isCancelled() && targetDef.getInteraction() != null
                            && targetDef.getInteraction().getAbilityId() != null
                            && !targetDef.getInteraction().getAbilityId().trim().isEmpty()) {
                        plugin.getTriggerManager().fireTrigger(targetDef.getInteraction().getAbilityId(), player, item, null, null);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProxyDamage(EntityDamageByEntityEvent event) {
        UUID entityId = event.getEntity().getUniqueId();
        if (proxyToBlockMap.containsKey(entityId)) {
            event.setCancelled(true);
            if (event.getDamager() instanceof Player player) {
                Location blockLoc = proxyToBlockMap.get(entityId);
                if (blockLoc != null && plugin.getCustomBlockManager() != null) {
                    PlacedBlockInstance instance = plugin.getCustomBlockManager().getPlacedBlock(blockLoc);
                    if (instance != null) {
                        CustomBlockDefinition def = plugin.getCustomBlockRegistry() != null ? plugin.getCustomBlockRegistry().get(instance.getBlockId()) : null;
                        if (def != null) {
                            if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
                                plugin.getCustomBlockManager().breakCustomBlock(blockLoc, player, false);
                            } else if (player.getGameMode() == org.bukkit.GameMode.SURVIVAL || player.getGameMode() == org.bukkit.GameMode.ADVENTURE) {
                                plugin.getCustomBlockManager().startOrContinueMining(player, blockLoc, def);
                            }
                        }
                    }
                }
            }
        }
    }

    public Map<Location, UUID> getBlockToProxyMap() {
        return Collections.unmodifiableMap(blockToProxyMap);
    }
}
