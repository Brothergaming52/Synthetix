package org.bg52.synthetix.block;

import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.api.event.*;
import org.bg52.synthetix.storage.PlacedBlockRecord;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.FluidCollisionMode;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bg52.synthetix.scheduler.PlatformScheduler;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3i;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Universal Dual-Layer Virtual Block Engine for Synthetix.
 * Manages physical proxy blocks, network-synchronized ItemDisplay visuals,
 * multi-model animated composite parts, 3x3 multi-part footprints, custom mining,
 * blast resistance, audio, and authentic item shatter particles.
 */
public class CustomBlockManager implements Listener {

    public static final NamespacedKey CHUNK_BLOCKS_KEY = new NamespacedKey("synthetix", "chunk_blocks");

    private final SynthetixPlugin plugin;
    // Spatial Chunk Index: WorldName -> (ChunkKey -> (Root Location -> PlacedBlockInstance))
    private final Map<String, Map<Long, Map<Location, PlacedBlockInstance>>> worldChunkBlocks = new ConcurrentHashMap<>();
    // Flat Root Block Location -> PlacedBlockInstance
    private final Map<Location, PlacedBlockInstance> placedBlocks = new ConcurrentHashMap<>();
    // Map: Any Sub-Part Location -> Root Block Location
    private final Map<Location, Location> partToRootMap = new ConcurrentHashMap<>();

    // Active survival mining sessions: Player UUID -> MiningSession
    private final Map<UUID, MiningSession> activeMiningSessions = new ConcurrentHashMap<>();
    private PlatformScheduler.TaskHandle miningTask;

    private PlatformScheduler.TaskHandle animationTask;
    private double animationTick = 0;

    // Network Packet Optimization for Block Animations
    private boolean keyframeOptimization = true;
    private int keyframeInterpolationDuration = 8; // 8 ticks = 0.4s client-side interpolation
    private int keyframeDispatchInterval = 6; // 6 ticks = 0.3s dispatch advance
    private boolean idleZeroPackets = true;
    private long serverTickCounter = 0;

    private BlockCullingManager cullingManager;

    public CustomBlockManager(SynthetixPlugin plugin) {
        this.plugin = plugin;
    }

    public static long getChunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) | (((long) chunkZ) & 0xFFFFFFFFL);
    }

    public void registerInstanceInMemory(PlacedBlockInstance instance) {
        Location loc = normalize(instance.getLocation());
        placedBlocks.put(loc, instance);
        partToRootMap.put(loc, loc);
        for (Vector3i offset : instance.getMultiPartOffsets()) {
            partToRootMap.put(loc.clone().add(offset.x, offset.y, offset.z), loc);
        }
        String worldName = loc.getWorld().getName();
        long chunkKey = getChunkKey(instance.getChunkX(), instance.getChunkZ());
        worldChunkBlocks.computeIfAbsent(worldName, w -> new ConcurrentHashMap<>())
                .computeIfAbsent(chunkKey, k -> new ConcurrentHashMap<>())
                .put(loc, instance);
    }

    public void unregisterInstanceFromMemory(PlacedBlockInstance instance) {
        Location loc = normalize(instance.getLocation());
        placedBlocks.remove(loc);
        partToRootMap.remove(loc);
        for (Vector3i offset : instance.getMultiPartOffsets()) {
            partToRootMap.remove(loc.clone().add(offset.x, offset.y, offset.z));
        }
        String worldName = loc.getWorld().getName();
        long chunkKey = getChunkKey(instance.getChunkX(), instance.getChunkZ());
        Map<Long, Map<Location, PlacedBlockInstance>> worldMap = worldChunkBlocks.get(worldName);
        if (worldMap != null) {
            Map<Location, PlacedBlockInstance> chunkMap = worldMap.get(chunkKey);
            if (chunkMap != null) {
                chunkMap.remove(loc);
                if (chunkMap.isEmpty()) {
                    worldMap.remove(chunkKey);
                }
            }
        }
    }

    public void initialize() {
        reloadConfig();
        migrateLegacyYamlFile();
        startAnimationLoop();
        startMiningLoop();
        this.cullingManager = new BlockCullingManager(plugin, this);
        this.cullingManager.start();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Pre-populate all currently active loaded chunks across all worlds
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                loadChunkBlocks(chunk);
            }
        }
    }

    public void shutdown() {
        if (cullingManager != null) {
            cullingManager.stop();
            cullingManager = null;
        }
        if (animationTask != null) {
            animationTask.cancel();
            animationTask = null;
        }
        if (miningTask != null) {
            miningTask.cancel();
            miningTask = null;
        }
        for (MiningSession session : activeMiningSessions.values()) {
            cleanupCrackDisplay(session);
        }
        activeMiningSessions.clear();

        // Despawn all active visual display entities cleanly so world region files stay 100% clean
        for (PlacedBlockInstance instance : placedBlocks.values()) {
            cleanupDisplayEntities(instance);
        }
        placedBlocks.clear();
        partToRootMap.clear();
        worldChunkBlocks.clear();
    }

    public BlockCullingManager getCullingManager() {
        return cullingManager;
    }

    public void reloadConfig() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("custom-blocks.animations");
        if (sec != null) {
            this.keyframeOptimization = sec.getBoolean("keyframe-optimization", true);
            this.keyframeInterpolationDuration = Math.max(4, sec.getInt("interpolation-duration-ticks", 8));
            this.keyframeDispatchInterval = Math.max(2, sec.getInt("keyframe-dispatch-interval", 6));
            this.idleZeroPackets = sec.getBoolean("idle-zero-packets", true);
        }
        if (cullingManager != null) {
            cullingManager.reloadConfig();
        }
    }

    private void startAnimationLoop() {
        if (animationTask != null) animationTask.cancel();
        animationTask = PlatformScheduler.get().runGlobalTimer(() -> {
            try {
                serverTickCounter += 2;
                animationTick += 0.1;
                if (animationTick > Math.PI * 200) animationTick = 0;

                for (PlacedBlockInstance instance : placedBlocks.values()) {
                    CustomBlockDefinition def = plugin.getCustomBlockRegistry().get(instance.getBlockId());
                    if (def == null || !def.isMultiModel() || def.getModelParts().isEmpty()) continue;

                    Location loc = instance.getLocation();
                    if (!loc.isWorldLoaded() || !loc.getChunk().isLoaded()) continue;

                    // Performance optimization: skip matrix math if block is culled for all online players
                    if (cullingManager != null && !cullingManager.isBlockVisibleToAnyPlayer(instance)) continue;

                    List<UUID> entityUuids = instance.getDisplayEntityUuids();
                    // Display entity 0 is base/root casing. Subsequent entities correspond to modelParts.
                    for (int i = 0; i < def.getModelParts().size(); i++) {
                        BlockModelPart part = def.getModelParts().get(i);
                        if (!part.isAnimated()) continue;

                        int entityIdx = i + 1; // offset by root
                        if (entityIdx >= entityUuids.size()) break;

                        UUID uuid = entityUuids.get(entityIdx);
                        Entity entity = Bukkit.getEntity(uuid);
                        if (entity instanceof ItemDisplay display && display.isValid()) {
                            updatePartAnimation(display, part, instance, animationTick);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }, 20L, 2L); // Runs every 2 ticks (~0.1s)
    }

    private void updatePartAnimation(ItemDisplay display, BlockModelPart part, PlacedBlockInstance instance, double time) {
        String partId = part.getId();
        double baseSpeed = part.getAnimationSpeed();
        double instSpeedMult = instance.getSpeedMultiplier();
        double partSpeedMult = instance.getPartSpeedMultiplier(partId);
        double effectiveSpeed = baseSpeed * instSpeedMult * partSpeedMult;
        boolean isPaused = instance.isAnimationPaused() || instance.isPartPaused(partId);
        boolean isIdle = Math.abs(effectiveSpeed) < 0.0001 || isPaused;

        BlockFace facing = instance.getFacing();

        // 1. ZERO-PACKET IDLE GATE: Completely halts network packet updates when speed is 0 or paused
        if (idleZeroPackets && isIdle) {
            if (instance.isPartResting(partId)) {
                // Machine/part is resting; absolutely ZERO packets transmitted
                return;
            }
            // Send one final resting keyframe with 2-tick interpolation so it halts gracefully
            applyRestingTransformation(display, part, facing);
            instance.setPartResting(partId, true);
            return;
        }

        instance.setPartResting(partId, false);

        Vector3f baseScale = (part.getScale() != null && part.getScale().x != 0 && part.getScale().y != 0 && part.getScale().z != 0)
                ? new Vector3f(part.getScale()) : new Vector3f(1.0f, 1.0f, 1.0f);
        Vector3f baseTrans = part.getTranslation() != null ? new Vector3f(part.getTranslation()) : new Vector3f(0.0f, 0.0f, 0.0f);
        Vector3f rotEuler = part.getRotation() != null ? new Vector3f(part.getRotation()) : new Vector3f(0.0f, 0.0f, 0.0f);

        // 2. CONTINUOUS ROTATION OPTIMIZED KEYFRAME DISPATCH
        // Sends rotation steps safely under 45° so SLERP never reverses even under packet loss
        if (keyframeOptimization && (part.getAnimationType() == BlockAnimationType.ROTATION_Y
                || part.getAnimationType() == BlockAnimationType.ROTATION_X
                || part.getAnimationType() == BlockAnimationType.ROTATION_Z)) {

            double radPerSec = effectiveSpeed * Math.PI; // at 1.0x speed, 0.5 rev/sec = 180 deg/s
            // Dynamically scale dispatch interval down if speed is fast so single-step arc remains small
            int dynamicDispatchInterval = keyframeDispatchInterval;
            if (Math.abs(radPerSec) > 0.001) {
                int intervalFor45Deg = (int) Math.floor((Math.toRadians(45.0) / Math.abs(radPerSec)) * 20.0);
                dynamicDispatchInterval = Math.max(2, Math.min(keyframeDispatchInterval, intervalFor45Deg));
            }

            long lastDispatch = instance.getLastDispatchTick(partId);
            long elapsed = serverTickCounter - lastDispatch;

            // If within the keyframe window, let the client GPU interpolate without sending packets!
            if (lastDispatch > 0 && elapsed < dynamicDispatchInterval) {
                return;
            }

            instance.setLastDispatchTick(partId, serverTickCounter);

            // Compute step based on the actual elapsed time since last dispatch
            double secondsElapsed = (lastDispatch > 0 && elapsed > 0) ? (elapsed / 20.0) : (dynamicDispatchInterval / 20.0);
            float step = (float) (radPerSec * secondsElapsed);

            // Cap maximum single-step arc to 45 degrees so even 2 consecutive lost packets (< 135 deg) never invert SLERP
            float maxArc = (float) Math.toRadians(45.0);
            if (step > maxArc) step = maxArc;
            if (step < -maxArc) step = -maxArc;

            float currentAngle = instance.getPartAngle(partId);
            float targetAngle = currentAngle + step;
            if (targetAngle > (float) (Math.PI * 2)) targetAngle -= (float) (Math.PI * 2);
            if (targetAngle < (float) (-Math.PI * 2)) targetAngle += (float) (Math.PI * 2);
            instance.setPartAngle(partId, targetAngle);

            Quaternionf q = new Quaternionf();
            if (!part.isLockRotationAxis()) {
                applyFacingRotation(q, facing);
            }
            q.rotateY((float) Math.toRadians(rotEuler.y));
            q.rotateX((float) Math.toRadians(rotEuler.x));
            q.rotateZ((float) Math.toRadians(rotEuler.z));

            switch (part.getAnimationType()) {
                case ROTATION_Y -> q.rotateLocalY(targetAngle);
                case ROTATION_X -> q.rotateLocalX(targetAngle);
                case ROTATION_Z -> q.rotateLocalZ(targetAngle);
                default -> {}
            }

            // Client interpolation duration has a 2-tick jitter buffer beyond the dispatch interval to ensure smooth playback
            int clientDuration = Math.max(4, dynamicDispatchInterval + 2);
            display.setInterpolationDuration(clientDuration);
            display.setInterpolationDelay(0);
            display.setTransformation(new Transformation(baseTrans, q, baseScale, new Quaternionf()));
            return;
        }

        // 3. CYCLICAL / HIGH-FREQUENCY MOTIONS (BOB_AND_SPIN, PISTON_STROKE, or keyframeOptimization=false)
        Quaternionf q = new Quaternionf();
        if (!part.isLockRotationAxis()) {
            applyFacingRotation(q, facing);
        }
        q.rotateY((float) Math.toRadians(rotEuler.y));
        q.rotateX((float) Math.toRadians(rotEuler.x));
        q.rotateZ((float) Math.toRadians(rotEuler.z));

        switch (part.getAnimationType()) {
            case ROTATION_Y -> {
                float angle = (float) ((time * effectiveSpeed * 2.0) % (Math.PI * 2));
                q.rotateLocalY(angle);
            }
            case ROTATION_X -> {
                float angle = (float) ((time * effectiveSpeed * 2.0) % (Math.PI * 2));
                q.rotateLocalX(angle);
            }
            case ROTATION_Z -> {
                float angle = (float) ((time * effectiveSpeed * 2.0) % (Math.PI * 2));
                q.rotateLocalZ(angle);
            }
            case BOB_AND_SPIN -> {
                float bobY = (float) (Math.sin(time * effectiveSpeed * 2.0) * 0.1);
                baseTrans.y += bobY;
                float angle = (float) ((time * effectiveSpeed * 1.5) % (Math.PI * 2));
                q.rotateLocalY(angle);
            }
            case PISTON_STROKE -> {
                float stroke = (float) ((Math.sin(time * effectiveSpeed * 3.0) + 1.0) * 0.3);
                baseTrans.z += stroke;
            }
            default -> {}
        }

        display.setInterpolationDuration(Math.max(2, part.getInterpolationDuration()));
        display.setInterpolationDelay(0);
        display.setTransformation(new Transformation(baseTrans, q, baseScale, new Quaternionf()));
    }

    private void applyRestingTransformation(ItemDisplay display, BlockModelPart part, BlockFace facing) {
        Vector3f baseScale = (part.getScale() != null && part.getScale().x != 0 && part.getScale().y != 0 && part.getScale().z != 0)
                ? new Vector3f(part.getScale()) : new Vector3f(1.0f, 1.0f, 1.0f);
        Vector3f baseTrans = part.getTranslation() != null ? new Vector3f(part.getTranslation()) : new Vector3f(0.0f, 0.0f, 0.0f);
        Vector3f rotEuler = part.getRotation() != null ? new Vector3f(part.getRotation()) : new Vector3f(0.0f, 0.0f, 0.0f);

        Quaternionf q = new Quaternionf();
        if (!part.isLockRotationAxis()) {
            applyFacingRotation(q, facing);
        }
        q.rotateY((float) Math.toRadians(rotEuler.y));
        q.rotateX((float) Math.toRadians(rotEuler.x));
        q.rotateZ((float) Math.toRadians(rotEuler.z));

        display.setInterpolationDuration(2);
        display.setInterpolationDelay(0);
        display.setTransformation(new Transformation(baseTrans, q, baseScale, new Quaternionf()));
    }

    // =========================================================================
    // BLOCK STATE, MORPHING / TRANSFORMATION & DYNAMIC ANIMATION CONTROLLER
    // =========================================================================

    public boolean updateBlockState(Location loc, String key, Object value, Player actor) {
        PlacedBlockInstance inst = getPlacedBlock(loc);
        if (inst == null || key == null) return false;

        CustomBlockDefinition def = plugin.getCustomBlockRegistry().get(inst.getBlockId());
        Object oldVal = inst.getState(key);

        var event = new org.bg52.synthetix.api.event.SynthetixBlockStateChangeEvent(
                def, inst.getLocation(), inst, key, oldVal, value, actor);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;

        inst.setState(key, event.getNewValue());
        persistBlockAsync(inst);
        return true;
    }

    public boolean transformBlock(Location loc, String newBlockId, Player actor) {
        PlacedBlockInstance inst = getPlacedBlock(loc);
        if (inst == null || newBlockId == null) return false;

        CustomBlockDefinition oldDef = plugin.getCustomBlockRegistry().get(inst.getBlockId());
        CustomBlockDefinition newDef = plugin.getCustomBlockRegistry().get(newBlockId.toLowerCase(Locale.ROOT));
        if (newDef == null) return false;

        var event = new org.bg52.synthetix.api.event.SynthetixBlockTransformEvent(
                oldDef, newDef, inst.getLocation(), inst, inst.getBlockId(), newDef.getId(), actor);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;

        String finalBlockId = event.getNewBlockId();
        CustomBlockDefinition finalDef = plugin.getCustomBlockRegistry().get(finalBlockId);
        if (finalDef == null) return false;

        Location rootLoc = inst.getLocation();
        if (oldDef != null && finalDef.getBaseMaterial() != oldDef.getBaseMaterial()) {
            rootLoc.getBlock().setType(finalDef.getBaseMaterial(), false);
        }

        inst.setBlockId(finalBlockId);
        inst.clearAnimationState();
        spawnDisplayEntities(inst, finalDef);
        persistBlockAsync(inst);

        Chunk chunk = rootLoc.getChunk();
        if (chunk.isLoaded()) {
            saveChunkPdc(chunk);
        }
        return true;
    }

    public void setBlockAnimationSpeed(Location loc, double speedMultiplier, Player actor) {
        PlacedBlockInstance inst = getPlacedBlock(loc);
        if (inst == null) return;

        CustomBlockDefinition def = plugin.getCustomBlockRegistry().get(inst.getBlockId());
        double oldSpeed = inst.getSpeedMultiplier();

        var event = new org.bg52.synthetix.api.event.SynthetixBlockAnimationSpeedChangeEvent(
                def, inst.getLocation(), inst, null, oldSpeed, speedMultiplier, actor);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        inst.setSpeedMultiplier(event.getNewSpeed());
        inst.clearAnimationState(); // Trigger immediate keyframe update on speed change
    }

    public void setBlockAnimationPaused(Location loc, boolean paused) {
        PlacedBlockInstance inst = getPlacedBlock(loc);
        if (inst != null) {
            inst.setAnimationPaused(paused);
        }
    }

    public void setPartAnimationSpeed(Location loc, String partId, double speedMultiplier, Player actor) {
        PlacedBlockInstance inst = getPlacedBlock(loc);
        if (inst == null || partId == null) return;

        CustomBlockDefinition def = plugin.getCustomBlockRegistry().get(inst.getBlockId());
        double oldSpeed = inst.getPartSpeedMultiplier(partId);

        var event = new org.bg52.synthetix.api.event.SynthetixBlockAnimationSpeedChangeEvent(
                def, inst.getLocation(), inst, partId, oldSpeed, speedMultiplier, actor);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        inst.setPartSpeedMultiplier(partId, event.getNewSpeed());
        inst.setLastDispatchTick(partId, 0L);
    }

    public void setPartAnimationPaused(Location loc, String partId, boolean paused) {
        PlacedBlockInstance inst = getPlacedBlock(loc);
        if (inst != null && partId != null) {
            inst.setPartPaused(partId, paused);
        }
    }

    public boolean setPartModel(Location loc, String partId, String itemModel, Integer cmd) {
        PlacedBlockInstance inst = getPlacedBlock(loc);
        if (inst == null || partId == null) return false;

        CustomBlockDefinition def = plugin.getCustomBlockRegistry().get(inst.getBlockId());
        if (def == null) return false;

        int entityIdx = -1;
        if (partId.equalsIgnoreCase("root") || partId.equalsIgnoreCase("casing")) {
            entityIdx = 0;
        } else {
            for (int i = 0; i < def.getModelParts().size(); i++) {
                if (def.getModelParts().get(i).getId().equalsIgnoreCase(partId)) {
                    entityIdx = i + 1;
                    break;
                }
            }
        }

        if (entityIdx < 0 || entityIdx >= inst.getDisplayEntityUuids().size()) return false;

        UUID uuid = inst.getDisplayEntityUuids().get(entityIdx);
        Entity entity = Bukkit.getEntity(uuid);
        if (entity instanceof ItemDisplay display && display.isValid()) {
            ItemStack stack = display.getItemStack();
            if (stack == null || stack.getType().isAir()) {
                stack = new ItemStack(Material.PAPER);
            }
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                if (itemModel != null) {
                    org.bg52.synthetix.item.component.ComponentHandler.applyItemModel(meta, itemModel, cmd != null && cmd > 0 ? cmd : null);
                } else if (cmd != null && cmd > 0) {
                    meta.setCustomModelData(cmd);
                }
                stack.setItemMeta(meta);
            }
            display.setItemStack(stack);
            inst.setPartModelOverride(partId, itemModel);
            return true;
        }
        return false;
    }

    // =========================================================================
    // PLACEMENT, INTERACTION & WATERLOGGING HANDLING
    // =========================================================================

    public record TargetBlockResult(Block block, PlacedBlockInstance instance) {}

    /**
     * Resolves the custom block targeted by the player with robust handling for:
     * 1) Direct hit on custom block.
     * 2) Survival players looking through non-solid/barrier collision boxes onto adjacent floor/wall faces.
     * 3) Server-side raytrace with FluidCollisionMode.ALWAYS for waterlogged blocks.
     */
    public TargetBlockResult resolveTargetCustomBlock(Player player, Block clickedBlock, BlockFace clickedFace) {
        if (player == null) return null;

        // 1. Direct clicked block
        if (clickedBlock != null && isCustomBlock(clickedBlock.getLocation())) {
            PlacedBlockInstance inst = getPlacedBlock(clickedBlock.getLocation());
            if (inst != null) {
                return new TargetBlockResult(clickedBlock, inst);
            }
        }

        // 2. Adjacent block on clicked face (e.g., survival client clicked floor/wall through barrier)
        if (clickedBlock != null && clickedFace != null) {
            Block adjacent = clickedBlock.getRelative(clickedFace);
            if (isCustomBlock(adjacent.getLocation())) {
                PlacedBlockInstance inst = getPlacedBlock(adjacent.getLocation());
                if (inst != null) {
                    return new TargetBlockResult(adjacent, inst);
                }
            }
        }

        // 3. Mathematical 3D AABB raycast from player's eyes against nearby custom blocks
        Location eye = player.getEyeLocation();
        Vector eyeVec = eye.toVector();
        Vector eyeDir = eye.getDirection().normalize();
        World world = player.getWorld();

        int pChunkX = eye.getBlockX() >> 4;
        int pChunkZ = eye.getBlockZ() >> 4;

        TargetBlockResult closestHit = null;
        double closestDist = Double.MAX_VALUE;

        for (int cx = pChunkX - 1; cx <= pChunkX + 1; cx++) {
            for (int cz = pChunkZ - 1; cz <= pChunkZ + 1; cz++) {
                Collection<PlacedBlockInstance> chunkBlocks = getBlocksInChunk(world, cx, cz);
                for (PlacedBlockInstance inst : chunkBlocks) {
                    for (Location occ : inst.getAllOccupiedLocations()) {
                        BoundingBox box = new BoundingBox(
                                occ.getBlockX(), occ.getBlockY(), occ.getBlockZ(),
                                occ.getBlockX() + 1.0, occ.getBlockY() + 1.0, occ.getBlockZ() + 1.0
                        );
                        org.bukkit.util.RayTraceResult hit = box.rayTrace(eyeVec, eyeDir, 6.0);
                        if (hit != null) {
                            double dist = hit.getHitPosition().distance(eyeVec);
                            if (dist < closestDist) {
                                closestDist = dist;
                                closestHit = new TargetBlockResult(occ.getBlock(), inst);
                            }
                        }
                    }
                }
            }
        }

        return closestHit;
    }

    public void setBlockWaterlogged(Block block, PlacedBlockInstance instance, CustomBlockDefinition def, boolean waterlogged) {
        if (block == null || instance == null || def == null) return;

        instance.setState("waterlogged", waterlogged);
        persistBlockAsync(instance);
        if (block.getChunk().isLoaded()) {
            saveChunkPdc(block.getChunk());
        }

        // Apply to all occupied blocks (1x1 or multi-part)
        for (Location occ : instance.getAllOccupiedLocations()) {
            Block b = occ.getBlock();
            if (b.getBlockData() instanceof Waterlogged w && def.getBaseMaterial() != Material.BARRIER) {
                w.setWaterlogged(waterlogged);
                b.setBlockData(w, true);
            } else {
                if (waterlogged) {
                    b.setType(Material.WATER, true);
                } else {
                    int ox = occ.getBlockX() - instance.getLocation().getBlockX();
                    int oy = occ.getBlockY() - instance.getLocation().getBlockY();
                    int oz = occ.getBlockZ() - instance.getLocation().getBlockZ();
                    Material base = (ox == 0 && oy == 0 && oz == 0) ? def.getBaseMaterial() : def.getPartBaseBlock(ox, oy, oz);
                    b.setType(base, true);
                }
            }
        }
    }

    public boolean handleWaterlogging(Player player, Block block, PlacedBlockInstance instance, CustomBlockDefinition def, boolean waterlog, EquipmentSlot hand) {
        if (block == null || instance == null || def == null) return false;
        if (!def.getPhysics().isWaterloggable()) return false;

        boolean currentlyWaterlogged = isBlockWaterlogged(instance, block);
        if (waterlog == currentlyWaterlogged) {
            return false;
        }

        setBlockWaterlogged(block, instance, def, waterlog);

        Sound sound = waterlog ? Sound.ITEM_BUCKET_EMPTY : Sound.ITEM_BUCKET_FILL;
        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), sound, 1.0f, 1.0f);

        player.sendBlockChange(block.getLocation(), block.getBlockData());

        if (player.getGameMode() != GameMode.CREATIVE && hand != null) {
            Material expectedItem = waterlog ? Material.WATER_BUCKET : Material.BUCKET;
            Material replacementItem = waterlog ? Material.BUCKET : Material.WATER_BUCKET;

            PlatformScheduler.get().runEntity(player, () -> {
                ItemStack held = player.getInventory().getItem(hand);
                if (held != null && held.getType() == expectedItem) {
                    if (held.getAmount() <= 1) {
                        player.getInventory().setItem(hand, new ItemStack(replacementItem));
                    } else {
                        held.setAmount(held.getAmount() - 1);
                        player.getInventory().setItem(hand, held);
                        player.getInventory().addItem(new ItemStack(replacementItem)).values()
                                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
                    }
                    player.updateInventory();
                }
                player.sendBlockChange(block.getLocation(), block.getBlockData());
            });
        }

        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.useInteractedBlock() == org.bukkit.event.Event.Result.DENY && event.useItemInHand() == org.bukkit.event.Event.Result.DENY) return;
        if (event.isCancelled() && (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR)) return;

        Player player = event.getPlayer();
        Action action = event.getAction();
        Block clickedBlock = event.getClickedBlock();
        EquipmentSlot hand = event.getHand();
        ItemStack item = event.getItem();

        // 1. Survival & Creative Left-Click Mining
        if (action == Action.LEFT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR) {
            TargetBlockResult target = resolveTargetCustomBlock(player, clickedBlock, event.getBlockFace());
            if (target != null) {
                Location rootLoc = partToRootMap.get(normalize(target.block.getLocation()));
                if (rootLoc == null) rootLoc = normalize(target.block.getLocation());

                PlacedBlockInstance instance = placedBlocks.get(rootLoc);
                if (instance != null) {
                    CustomBlockDefinition def = plugin.getCustomBlockRegistry().get(instance.getBlockId());
                    if (def != null) {
                        event.setCancelled(true);
                        if (player.getGameMode() == GameMode.CREATIVE) {
                            breakCustomBlock(rootLoc, player, false);
                            return;
                        }
                        if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
                            startOrContinueMining(player, rootLoc, def);
                            return;
                        }
                    }
                }
            }
            if (action == Action.LEFT_CLICK_AIR) return;
        }

        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) return;

        // 2. Waterlogging Handling (Creative & Survival, with Bucket or Water Bucket)
        if (item != null && (item.getType() == Material.WATER_BUCKET || item.getType() == Material.BUCKET)) {
            TargetBlockResult target = resolveTargetCustomBlock(player, clickedBlock, event.getBlockFace());
            if (target != null) {
                CustomBlockDefinition targetDef = plugin.getCustomBlockRegistry().get(target.instance.getBlockId());
                if (targetDef != null && targetDef.getPhysics().isWaterloggable()) {
                    boolean hasInteraction = targetDef.getInteraction() != null
                            && targetDef.getInteraction().getAbilityId() != null
                            && !targetDef.getInteraction().getAbilityId().trim().isEmpty();

                    if (!hasInteraction || player.isSneaking()) {
                        event.setCancelled(true);
                        boolean waterlog = item.getType() == Material.WATER_BUCKET;
                        handleWaterlogging(player, target.block, target.instance, targetDef, waterlog, hand);
                        return;
                    }
                }
            }
        }

        if (action == Action.RIGHT_CLICK_AIR) return;
        if (clickedBlock == null) return;

        // Right-clicking an existing custom block
        if (isCustomBlock(clickedBlock.getLocation())) {
            // Prevent note block carrier from tuning note pitch
            if (clickedBlock.getType() == Material.NOTE_BLOCK) {
                event.setCancelled(true);
            }

            PlacedBlockInstance targetInstance = getPlacedBlock(clickedBlock.getLocation());
            if (targetInstance != null) {
                CustomBlockDefinition targetDef = plugin.getCustomBlockRegistry().get(targetInstance.getBlockId());
                if (targetDef != null) {
                    boolean hasInteraction = targetDef.getInteraction() != null
                            && targetDef.getInteraction().getAbilityId() != null
                            && !targetDef.getInteraction().getAbilityId().trim().isEmpty();

                    // If block has an interaction ability and player is not sneaking: trigger interaction
                    if (hasInteraction && !player.isSneaking()) {
                        event.setCancelled(true);
                        SynthetixBlockInteractEvent interactEvent = new SynthetixBlockInteractEvent(
                                targetDef, targetInstance.getLocation(), player, Action.RIGHT_CLICK_BLOCK, event.getHand(), event.getBlockFace(), item);
                        Bukkit.getPluginManager().callEvent(interactEvent);
                        if (!interactEvent.isCancelled()) {
                            plugin.getTriggerManager().fireTrigger(targetDef.getInteraction().getAbilityId(), player, item, event, null);
                        }
                        return;
                    }
                }
            }
        }

        // Prevent off-hand processing from double-firing block placement
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;

        if (item == null || item.getType().isAir()) return;

        // Check if item is a custom block item
        String blockId = getBlockIdFromItem(item);
        if (blockId == null) return;

        CustomBlockDefinition def = plugin.getCustomBlockRegistry().get(blockId);
        if (def == null) return;

        Location targetLoc;
        if (isBlockReplaceable(clickedBlock)) {
            // Replace the clicked block directly (vanilla behavior for grass, snow layers, ferns, flowers, etc.)
            targetLoc = clickedBlock.getLocation();
        } else {
            // Place against the clicked face of the solid block
            BlockFace face = event.getBlockFace();
            targetLoc = clickedBlock.getRelative(face).getLocation();
        }

        // Multi-part block clearance check
        List<Location> requiredLocations = calculateMultiPartLocations(targetLoc, def.getMultiPartSize(), player.getFacing());
        for (Location loc : requiredLocations) {
            Block b = loc.getBlock();
            if (!isBlockReplaceable(b)) {
                return; // Solid obstacle prevents placement
            }
        }

        // Entity collision clearance check (prevents placing blocks inside players/entities without jumping)
        if (def.getPhysics().getCollisionShape() != CollisionShape.PASS_THROUGH && !def.getPhysics().isPassThrough()) {
            for (Location loc : requiredLocations) {
                org.bukkit.util.BoundingBox blockBox = new org.bukkit.util.BoundingBox(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                        loc.getBlockX() + 1.0, loc.getBlockY() + 1.0, loc.getBlockZ() + 1.0);
                for (Entity entity : loc.getWorld().getNearbyEntities(blockBox)) {
                    if (entity instanceof org.bukkit.entity.LivingEntity && !(entity instanceof org.bukkit.entity.ArmorStand)) {
                        if (entity.getBoundingBox().overlaps(blockBox)) {
                            return; // Cannot place inside living entity / player!
                        }
                    }
                }
            }
        }

        // Fire custom placement event
        BlockFace placedFacing = def.isLockRotation() ? BlockFace.NORTH : player.getFacing().getOppositeFace();
        SynthetixBlockPlaceEvent placeEvent = new SynthetixBlockPlaceEvent(def, targetLoc, player, placedFacing, item);
        Bukkit.getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled()) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        // Place custom block instance
        PlacedBlockInstance instance = placeCustomBlock(targetLoc, def, placedFacing, player.getUniqueId());
        if (instance != null) {
            SynthetixBlockPostPlaceEvent postPlaceEvent = new SynthetixBlockPostPlaceEvent(def, targetLoc, player, placedFacing, item, instance);
            Bukkit.getPluginManager().callEvent(postPlaceEvent);
        }

        // Decrement item in survival
        if (player.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }

        // Play custom placement audio
        Sound placeSound = def.getMining().resolvePlaceSound();
        targetLoc.getWorld().playSound(targetLoc.clone().add(0.5, 0.5, 0.5), placeSound, 1.0f, 1.0f);
    }

    /**
     * Places a custom block at the target location, spawning visual display entities
     * and establishing physical proxy blocks.
     */
    public PlacedBlockInstance placeCustomBlock(Location rootLoc, CustomBlockDefinition def, BlockFace facing, UUID placerUuid) {
        if (plugin.getSettingsManager() != null && !plugin.getSettingsManager().isFeatureEnabled(org.bg52.synthetix.settings.GlobalFeature.CUSTOM_BLOCKS)) {
            return null;
        }
        Location normalizedRoot = normalize(rootLoc);
        PlacedBlockInstance instance = new PlacedBlockInstance(normalizedRoot, def.getId(), placerUuid, facing);

        boolean rootWasWater = normalizedRoot.getBlock().getType() == Material.WATER || isBlockWaterlogged(normalizedRoot.getBlock());

        // 1. Establish Physical Proxy Blocks
        if (rootWasWater && def.getPhysics().isWaterloggable()) {
            setBlockWaterlogged(normalizedRoot.getBlock(), instance, def, true);
        } else {
            normalizedRoot.getBlock().setType(def.getBaseMaterial(), false);
        }

        // Multi-part footprint
        Vector3i size = def.getMultiPartSize();
        if (size.x > 1 || size.y > 1 || size.z > 1) {
            for (int dx = 0; dx < size.x; dx++) {
                for (int dy = 0; dy < size.y; dy++) {
                    for (int dz = 0; dz < size.z; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Vector3i offset = rotateOffset(dx, dy, dz, facing);
                        instance.getMultiPartOffsets().add(offset);

                        Location partLoc = normalizedRoot.clone().add(offset.x, offset.y, offset.z);
                        boolean partWasWater = partLoc.getBlock().getType() == Material.WATER || isBlockWaterlogged(partLoc.getBlock());
                        Material partMat = def.getPartBaseBlock(dx, dy, dz);
                        if (partWasWater && def.getPhysics().isWaterloggable()) {
                            setBlockWaterlogged(partLoc.getBlock(), instance, def, true);
                        } else {
                            partLoc.getBlock().setType(partMat, false);
                        }
                    }
                }
            }
        }

        // 2. Establish Visual Layer (ItemDisplay / BlockDisplay Entities)
        // If NOTE_BLOCK or TRIPWIRE (Tier A), zero entities are spawned!
        spawnDisplayEntities(instance, def);
        if (plugin.getBedrockManager() != null && plugin.getBedrockManager().getBlockVisualManager() != null) {
            plugin.getBedrockManager().getBlockVisualManager().onBlockSpawn(instance, def);
        }

        // 3. Register into memory
        registerInstanceInMemory(instance);

        // 4. Update Chunk PDC (guarantees persistence across rollbacks, backups, and restarts)
        saveChunkPdc(normalizedRoot.getChunk());

        // 5. Asynchronous secondary database backup
        persistBlockAsync(instance);
        return instance;
    }

    public void spawnDisplayEntities(PlacedBlockInstance instance, CustomBlockDefinition def) {
        if (def == null || instance == null) return;
        // Tier A: Baked state models or NoteBlock/Tripwire carriers require ZERO entities!
        if (def.getRenderingMethod() == RenderingMethod.NOTE_BLOCK || def.getRenderingMethod() == RenderingMethod.TRIPWIRE) {
            return;
        }

        cleanupDisplayEntities(instance);

        Location displayLoc = instance.getLocation().clone().add(0.5, 0.5, 0.5); // Center anchor at true block center

        // Root / Primary Model (Renders casing if multi-model composite, otherwise def.getItemModel())
        String primaryModel = def.isMultiModel() ? "synthetix:" + def.getId() + "_casing" : def.getItemModel();
        ItemDisplay rootDisplay = spawnModelDisplay(displayLoc, def.getItemMaterial(), def.getCustomModelData(), primaryModel,
                def.getDisplayScale(), def.getDisplayTranslation(), def.getDisplayRotation(), instance.getFacing(), def.getBrightness(), def.getGlowColorHex());
        if (rootDisplay != null) {
            instance.getDisplayEntityUuids().add(rootDisplay.getUniqueId());
        }

        // Secondary Composite Model Parts
        if (def.isMultiModel() && !def.getModelParts().isEmpty()) {
            for (BlockModelPart part : def.getModelParts()) {
                BlockFace effectiveFacing = part.isLockRotationAxis() ? BlockFace.NORTH : instance.getFacing();
                ItemDisplay partDisplay = spawnModelDisplay(displayLoc, part.getMaterial(), part.getCustomModelData(), part.getItemModel(),
                        part.getScale(), part.getTranslation(), part.getRotation(), effectiveFacing, def.getBrightness(), part.getColorHex());
                if (partDisplay != null) {
                    instance.getDisplayEntityUuids().add(partDisplay.getUniqueId());
                }
            }
        }

        // Synchronize Bedrock Edition visual proxy for Bedrock/Geyser players
        if (plugin.getBedrockManager() != null && plugin.getBedrockManager().getBlockVisualManager() != null) {
            plugin.getBedrockManager().getBlockVisualManager().onBlockSpawn(instance, def);
        }
    }

    public void cleanupDisplayEntities(PlacedBlockInstance instance) {
        if (instance == null) return;
        if (cullingManager != null) {
            cullingManager.onBlockRemoved(instance);
        }
        if (plugin.getBedrockManager() != null && plugin.getBedrockManager().getBlockVisualManager() != null) {
            try {
                plugin.getBedrockManager().getBlockVisualManager().onBlockCleanup(instance);
            } catch (Throwable ignored) {}
        }
        for (UUID uuid : instance.getDisplayEntityUuids()) {
            try {
                Entity e = Bukkit.getEntity(uuid);
                if (e != null) {
                    e.remove();
                }
            } catch (Throwable ignored) {}
        }
        instance.getDisplayEntityUuids().clear();
    }

    private ItemDisplay spawnModelDisplay(Location loc, Material mat, int cmd, String itemModel, Vector3f scale, Vector3f translation, Vector3f rotEuler, BlockFace facing, Integer brightness, String glowColor) {
        World world = loc.getWorld();
        if (world == null) return null;

        return world.spawn(loc, ItemDisplay.class, display -> {
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);

            Material safeMat = org.bg52.synthetix.util.MaterialUtil.toItemMaterial(mat != null && !mat.isAir() ? mat : Material.PAPER);
            ItemStack stack = new ItemStack(safeMat);
            var meta = stack.getItemMeta();
            if (meta != null) {
                if (itemModel != null && !itemModel.isEmpty()) {
                    org.bg52.synthetix.item.component.ComponentHandler.applyItemModel(meta, itemModel, cmd > 0 ? cmd : null);
                } else if (cmd > 0) {
                    meta.setCustomModelData(cmd);
                }
                stack.setItemMeta(meta);
            }
            display.setItemStack(stack);

            Quaternionf q = new Quaternionf();
            applyFacingRotation(q, facing);
            if (rotEuler != null) {
                q.rotateY((float) Math.toRadians(rotEuler.y));
                q.rotateX((float) Math.toRadians(rotEuler.x));
                q.rotateZ((float) Math.toRadians(rotEuler.z));
            }

            Vector3f effectiveScale = (scale != null && scale.x != 0 && scale.y != 0 && scale.z != 0) ? scale : new Vector3f(1.0f, 1.0f, 1.0f);
            Vector3f effectiveTrans = translation != null ? translation : new Vector3f(0, 0, 0);

            display.setTransformation(new Transformation(effectiveTrans, q, effectiveScale, new Quaternionf()));

            Display.Brightness effBrightness = resolveEffectiveBrightness(loc, brightness);
            display.setBrightness(effBrightness);

            if (glowColor != null) {
                display.setGlowing(true);
                try {
                    String hex = glowColor.replace("#", "");
                    int rgb = Integer.parseInt(hex, 16);
                    display.setGlowColorOverride(Color.fromRGB(rgb));
                } catch (Exception ignored) {}
            }

            display.setPersistent(false); // CRITICAL: Dynamic runtime projection only, never serialized to region .mca files!
            display.setViewRange(1.2f);
        });
    }

    private void applyFacingRotation(Quaternionf q, BlockFace facing) {
        if (facing == null) return;
        switch (facing) {
            case SOUTH -> q.rotateY((float) Math.PI);
            case WEST -> q.rotateY((float) (Math.PI / 2.0));
            case EAST -> q.rotateY((float) (-Math.PI / 2.0));
            case UP -> q.rotateX((float) (Math.PI / 2.0));
            case DOWN -> q.rotateX((float) (-Math.PI / 2.0));
            default -> {} // NORTH is default 0
        }
    }

    /**
     * Resolves the ambient display brightness for custom blocks placed inside physical proxy blocks.
     * Samples real light (block + sky) from adjacent non-occluding faces with a configured minimum floor.
     */
    public Display.Brightness resolveEffectiveBrightness(Location loc, Integer explicitBrightness) {
        if (explicitBrightness != null) {
            int val = Math.max(0, Math.min(15, explicitBrightness));
            return new Display.Brightness(val, val);
        }

        String mode = plugin.getConfig().getString("custom-blocks.default-brightness", "ambient");
        if (!"ambient".equalsIgnoreCase(mode)) {
            try {
                int fixed = Integer.parseInt(mode.trim());
                fixed = Math.max(0, Math.min(15, fixed));
                return new Display.Brightness(fixed, fixed);
            } catch (Exception ignored) {}
        }

        int minFloor = plugin.getConfig().getInt("custom-blocks.minimum-ambient-light", 5);
        minFloor = Math.max(0, Math.min(15, minFloor));

        int maxBlockLight = minFloor;
        int maxSkyLight = minFloor;

        Block root = loc.getBlock();
        BlockFace[] faces = new BlockFace[]{
                BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH,
                BlockFace.EAST, BlockFace.WEST, BlockFace.DOWN
        };

        boolean foundExposed = false;
        for (BlockFace face : faces) {
            Block neighbor = root.getRelative(face);
            if (!neighbor.getType().isOccluding() || neighbor.isPassable()) {
                foundExposed = true;
                int bLight = neighbor.getLightFromBlocks();
                int sLight = neighbor.getLightFromSky();
                if (bLight > maxBlockLight) maxBlockLight = bLight;
                if (sLight > maxSkyLight) maxSkyLight = sLight;
            }
        }

        if (!foundExposed) {
            maxBlockLight = Math.max(maxBlockLight, root.getLightFromBlocks());
            maxSkyLight = Math.max(maxSkyLight, root.getLightFromSky());
        }

        return new Display.Brightness(Math.min(15, maxBlockLight), Math.min(15, maxSkyLight));
    }

    // =========================================================================
    // MINING, BREAKING & DROPS
    // =========================================================================

    public static class MiningSession {
        final UUID playerUuid;
        final Location blockLoc;
        final CustomBlockDefinition def;
        double progress = 0.0;
        long lastSwingTime = System.currentTimeMillis();
        int soundTick = 0;
        UUID crackDisplayUuid = null;

        public MiningSession(UUID playerUuid, Location blockLoc, CustomBlockDefinition def) {
            this.playerUuid = playerUuid;
            this.blockLoc = blockLoc;
            this.def = def;
        }

        public void recordArmSwing() {
            this.lastSwingTime = System.currentTimeMillis();
        }
    }

    private void startMiningLoop() {
        if (miningTask != null) miningTask.cancel();
        miningTask = PlatformScheduler.get().runGlobalTimer(() -> {
            try {
                if (activeMiningSessions.isEmpty()) return;
                long now = System.currentTimeMillis();

                for (MiningSession session : new ArrayList<>(activeMiningSessions.values())) {
                    Player player = Bukkit.getPlayer(session.playerUuid);
                    if (player == null || !player.isOnline() || player.isDead() ||
                            (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE)) {
                        activeMiningSessions.remove(session.playerUuid);
                        if (player != null && player.isOnline()) {
                            PlatformScheduler.get().runEntity(player, () -> cleanupCrackDisplay(session));
                        }
                        continue;
                    }

                    // Dispatch execution to the player's owning thread (100% Folia thread-safe)
                    PlatformScheduler.get().runEntity(player, () -> {
                        try {
                            tickMiningSession(player, session, now);
                        } catch (Throwable ignored) {}
                    });
                }
            } catch (Throwable ignored) {}
        }, 1L, 1L);
    }

    private void tickMiningSession(Player player, MiningSession session, long now) {
        if (!player.isOnline() || player.isDead() ||
                (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE)) {
            cleanupCrackDisplay(session);
            player.sendBlockDamage(session.blockLoc, 0.0f);
            activeMiningSessions.remove(player.getUniqueId());
            return;
        }

        if (!player.getWorld().equals(session.blockLoc.getWorld()) || player.getLocation().distanceSquared(session.blockLoc) > 36.0) {
            cleanupCrackDisplay(session);
            player.sendBlockDamage(session.blockLoc, 0.0f);
            activeMiningSessions.remove(player.getUniqueId());
            return;
        }

        // Verify the custom block still exists at target location
        PlacedBlockInstance inst = placedBlocks.get(session.blockLoc);
        if (inst == null) {
            cleanupCrackDisplay(session);
            player.sendBlockDamage(session.blockLoc, 0.0f);
            activeMiningSessions.remove(player.getUniqueId());
            return;
        }

        Location eye = player.getEyeLocation();
        Vector eyeVec = eye.toVector();
        Vector eyeDir = eye.getDirection().normalize();

        // 3D mathematical ray-box intersection to test if player's gaze is still on the block
        boolean lookingAtBlock = false;
        for (Location occ : inst.getAllOccupiedLocations()) {
            BoundingBox box = new BoundingBox(
                    occ.getBlockX(), occ.getBlockY(), occ.getBlockZ(),
                    occ.getBlockX() + 1.0, occ.getBlockY() + 1.0, occ.getBlockZ() + 1.0
            );
            if (box.expand(0.2).rayTrace(eyeVec, eyeDir, 6.0) != null) {
                lookingAtBlock = true;
                break;
            }
        }

        if (!lookingAtBlock) {
            cleanupCrackDisplay(session);
            player.sendBlockDamage(session.blockLoc, 0.0f);
            activeMiningSessions.remove(player.getUniqueId());
            return;
        }

        // Stop mining if arm swing stopped for > 750ms (allows for ping jitter and swing cadence)
        if (now - session.lastSwingTime > 750) {
            cleanupCrackDisplay(session);
            player.sendBlockDamage(session.blockLoc, 0.0f);
            activeMiningSessions.remove(player.getUniqueId());
            return;
        }

        float hardness = session.def.getMining().getHardness();
        if (hardness <= 0.0f) {
            finishMining(session, player);
            activeMiningSessions.remove(player.getUniqueId());
            return;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();
        ToolType requiredTool = session.def.getMining().getRequiredTool();
        boolean correctTool = (requiredTool == ToolType.NONE || requiredTool == ToolType.ANY) ||
                (requiredTool.matches(tool.getType()) && session.def.getMining().getMinimumTier().isSufficient(tool.getType()));

        double speed = calculateToolSpeedMultiplier(tool, correctTool, requiredTool);

        if (player.hasPotionEffect(org.bukkit.potion.PotionEffectType.HASTE)) {
            int amp = player.getPotionEffect(org.bukkit.potion.PotionEffectType.HASTE).getAmplifier() + 1;
            speed *= (1.0 + 0.2 * amp);
        }
        if (player.hasPotionEffect(org.bukkit.potion.PotionEffectType.MINING_FATIGUE)) {
            int amp = player.getPotionEffect(org.bukkit.potion.PotionEffectType.MINING_FATIGUE).getAmplifier() + 1;
            speed *= Math.pow(0.3, Math.min(amp, 4));
        }
        if (player.isInWater()) {
            speed /= 5.0;
        }
        if (!player.isOnGround()) {
            speed /= 5.0;
        }
        speed *= session.def.getMining().getBreakSpeedModifier();

        double damagePerTick = correctTool ? (speed / (hardness * 30.0)) : (speed / (hardness * 100.0));
        session.progress += damagePerTick;

        session.soundTick++;
        if (session.soundTick % 4 == 0) {
            Sound hitSound = session.def.getMining().resolveHitSound();
            session.blockLoc.getWorld().playSound(session.blockLoc.clone().add(0.5, 0.5, 0.5), hitSound, 0.35f, 0.8f + (float) (session.progress * 0.4));
        }

        if (session.progress >= 1.0) {
            finishMining(session, player);
            activeMiningSessions.remove(player.getUniqueId());
        } else {
            int stage = Math.min(9, Math.max(0, (int) (session.progress * 10.0)));
            updateCrackDisplay(session, stage);
            player.sendBlockDamage(session.blockLoc, (float) session.progress);
        }
    }

    public void startOrContinueMining(Player player, Location rootLoc, CustomBlockDefinition def) {
        MiningSession session = activeMiningSessions.get(player.getUniqueId());
        if (session != null && session.blockLoc.equals(rootLoc)) {
            session.recordArmSwing();
        } else {
            if (session != null) {
                cleanupCrackDisplay(session);
                player.sendBlockDamage(session.blockLoc, 0.0f);
            }
            MiningSession newSession = new MiningSession(player.getUniqueId(), rootLoc, def);
            activeMiningSessions.put(player.getUniqueId(), newSession);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerAnimation(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player player = event.getPlayer();
        MiningSession session = activeMiningSessions.get(player.getUniqueId());
        if (session != null) {
            session.recordArmSwing();
        } else if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
            TargetBlockResult target = resolveTargetCustomBlock(player, null, null);
            if (target != null) {
                Location rootLoc = partToRootMap.get(normalize(target.block.getLocation()));
                if (rootLoc == null) rootLoc = normalize(target.block.getLocation());
                PlacedBlockInstance inst = placedBlocks.get(rootLoc);
                if (inst != null) {
                    CustomBlockDefinition def = plugin.getCustomBlockRegistry().get(inst.getBlockId());
                    if (def != null) {
                        startOrContinueMining(player, rootLoc, def);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockDamage(BlockDamageEvent event) {
        Location clickedLoc = normalize(event.getBlock().getLocation());
        if (isCustomBlock(clickedLoc)) {
            event.setCancelled(true);
            Location rootLoc = partToRootMap.get(clickedLoc);
            if (rootLoc == null) rootLoc = clickedLoc;
            PlacedBlockInstance instance = placedBlocks.get(rootLoc);
            if (instance != null) {
                CustomBlockDefinition def = plugin.getCustomBlockRegistry().get(instance.getBlockId());
                if (def != null) {
                    Player player = event.getPlayer();
                    if (player.getGameMode() == GameMode.CREATIVE) {
                        breakCustomBlock(rootLoc, player, false);
                    } else if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
                        startOrContinueMining(player, rootLoc, def);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockDamageAbort(BlockDamageAbortEvent event) {
        Location clickedLoc = normalize(event.getBlock().getLocation());
        if (isCustomBlock(clickedLoc)) {
            Player player = event.getPlayer();
            MiningSession session = activeMiningSessions.remove(player.getUniqueId());
            if (session != null) {
                cleanupCrackDisplay(session);
                player.sendBlockDamage(session.blockLoc, 0.0f);
            }
        }
    }

    private void updateCrackDisplay(MiningSession session, int stage) {
        World world = session.blockLoc.getWorld();
        if (world == null) return;

        ItemDisplay display = null;
        if (session.crackDisplayUuid != null) {
            Entity e = Bukkit.getEntity(session.crackDisplayUuid);
            if (e instanceof ItemDisplay id && id.isValid()) {
                display = id;
            }
        }

        if (display == null) {
            Location spawnLoc = session.blockLoc.clone().add(0.5, 0.5, 0.5);
            display = world.spawn(spawnLoc, ItemDisplay.class, d -> {
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setTransformation(new Transformation(new Vector3f(0, 0, 0), new Quaternionf(), new Vector3f(1.0f, 1.0f, 1.0f), new Quaternionf()));
                d.setBrightness(new Display.Brightness(15, 15));
                d.setPersistent(false);
                d.setViewRange(0.8f);
            });
            session.crackDisplayUuid = display.getUniqueId();
        }

        ItemStack crackStack = new ItemStack(Material.PAPER);
        var meta = crackStack.getItemMeta();
        if (meta != null) {
            org.bg52.synthetix.item.component.ComponentHandler.applyItemModel(meta, "synthetix:destroy_stage_" + stage, 19000 + stage);
            crackStack.setItemMeta(meta);
        }
        display.setItemStack(crackStack);

        if (plugin.getBedrockManager() != null && plugin.getBedrockManager().getBlockVisualManager() != null) {
            plugin.getBedrockManager().getBlockVisualManager().updateCrackVisual(session.blockLoc, stage);
        }
    }

    private void cleanupCrackDisplay(MiningSession session) {
        if (session.crackDisplayUuid != null) {
            try {
                Entity e = Bukkit.getEntity(session.crackDisplayUuid);
                if (e != null) {
                    e.remove();
                }
            } catch (Throwable ignored) {}
            session.crackDisplayUuid = null;
        }
        if (plugin.getBedrockManager() != null && plugin.getBedrockManager().getBlockVisualManager() != null) {
            try {
                plugin.getBedrockManager().getBlockVisualManager().cleanupCrackVisual(session.blockLoc);
            } catch (Throwable ignored) {}
        }
    }

    private void finishMining(MiningSession session, Player player) {
        cleanupCrackDisplay(session);
        player.sendBlockDamage(session.blockLoc, 0.0f);
        breakCustomBlock(session.blockLoc, player, true);
    }

    private double calculateToolSpeedMultiplier(ItemStack tool, boolean correctTool, ToolType requiredTool) {
        if (requiredTool == ToolType.NONE) {
            // Block does not have a dedicated tool: mining with any tool, item, or bare hand
            // has the uniform base mining speed equivalent to an iron pickaxe (6.0)
            double base = 6.0;
            if (tool != null && !tool.getType().isAir()) {
                int eff = tool.getEnchantmentLevel(Enchantment.EFFICIENCY);
                if (eff > 0) {
                    base += (eff * eff + 1);
                }
            }
            return base;
        }

        if (tool == null || tool.getType().isAir()) return 1.0;
        double base = 1.0;
        if (correctTool) {
            String name = tool.getType().name();
            if (name.contains("WOODEN_") || name.contains("GOLDEN_")) base = name.contains("GOLDEN_") ? 12.0 : 2.0;
            else if (name.contains("STONE_")) base = 4.0;
            else if (name.contains("IRON_")) base = 6.0;
            else if (name.contains("DIAMOND_")) base = 8.0;
            else if (name.contains("NETHERITE_")) base = 9.0;
            else base = 2.0;

            int eff = tool.getEnchantmentLevel(Enchantment.EFFICIENCY);
            if (eff > 0) {
                base += (eff * eff + 1);
            }
        }
        return base;
    }

    private void damageToolDurability(Player player, ItemStack tool) {
        if (tool == null || tool.getType().isAir()) return;
        int maxDurability = tool.getType().getMaxDurability();
        if (maxDurability <= 0) return; // Non-damageable items (e.g. rotten flesh, sticks, food, blocks) never lose durability!

        if (tool.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable dmgMeta) {
            int unbreaking = tool.getEnchantmentLevel(Enchantment.UNBREAKING);
            if (unbreaking == 0 || ThreadLocalRandom.current().nextInt(unbreaking + 1) == 0) {
                int current = dmgMeta.getDamage() + 1;
                if (current >= maxDurability) {
                    player.getInventory().setItemInMainHand(null);
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                } else {
                    dmgMeta.setDamage(current);
                    tool.setItemMeta(dmgMeta);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Location loc = normalize(event.getBlock().getLocation());
        Location rootLoc = partToRootMap.get(loc);
        if (rootLoc == null) rootLoc = loc;

        PlacedBlockInstance instance = placedBlocks.get(rootLoc);
        if (instance == null) return;

        event.setCancelled(true);
        breakCustomBlock(rootLoc, event.getPlayer(), true);
    }

    public void breakCustomBlock(Location rootLoc, Player player, boolean dropItems) {
        Location normalizedRoot = normalize(rootLoc);
        PlacedBlockInstance instance = placedBlocks.get(normalizedRoot);
        if (instance == null) return;

        CustomBlockDefinition def = plugin.getCustomBlockRegistry().get(instance.getBlockId());
        if (def == null) return;

        ItemStack tool = player != null ? player.getInventory().getItemInMainHand() : new ItemStack(Material.AIR);

        boolean correctTool = def.getMining().getRequiredTool().matches(tool.getType()) &&
                def.getMining().getMinimumTier().isSufficient(tool.getType());

        List<ItemStack> drops = new ArrayList<>();
        if (dropItems && (player == null || player.getGameMode() != GameMode.CREATIVE)) {
            if (!def.getMining().isRequiresCorrectToolForDrops() || correctTool) {
                drops = calculateDrops(def, tool);
            }
        }

        SynthetixBlockBreakEvent breakEvent = new SynthetixBlockBreakEvent(def, normalizedRoot, player, tool, drops, def.getMining().getExpDrop());
        Bukkit.getPluginManager().callEvent(breakEvent);
        if (breakEvent.isCancelled()) return;

        if (player != null && player.getGameMode() == GameMode.SURVIVAL && tool.getType() != Material.AIR) {
            damageToolDurability(player, tool);
        }

        removeCustomBlock(normalizedRoot, false);

        Location center = normalizedRoot.clone().add(0.5, 0.5, 0.5);
        ItemStack particleItem = def.buildBlockItemStack(1);
        normalizedRoot.getWorld().spawnParticle(Particle.ITEM, center, 35, 0.25, 0.25, 0.25, 0.05, particleItem);

        Sound breakSound = def.getMining().resolveBreakSound();
        normalizedRoot.getWorld().playSound(center, breakSound, 1.0f, 1.0f);

        if (breakEvent.getExpToDrop() > 0) {
            org.bukkit.entity.ExperienceOrb orb = normalizedRoot.getWorld().spawn(center, org.bukkit.entity.ExperienceOrb.class);
            orb.setExperience(breakEvent.getExpToDrop());
        }

        for (ItemStack drop : breakEvent.getDrops()) {
            if (drop != null && !drop.getType().isAir()) {
                normalizedRoot.getWorld().dropItemNaturally(center, drop);
            }
        }
    }

    /**
     * Completely removes a custom block from the world, cleaning up visual display entities,
     * resetting all multi-part physical proxy blocks, and removing from registry.
     */
    public boolean removeCustomBlock(Location rootLoc, boolean dropItems) {
        Location normalizedRoot = normalize(rootLoc);
        PlacedBlockInstance instance = placedBlocks.get(normalizedRoot);
        if (instance == null) return false;

        CustomBlockDefinition def = plugin.getCustomBlockRegistry().get(instance.getBlockId());

        // Remove all visual display entities
        cleanupDisplayEntities(instance);

        // Clear all physical proxy blocks (root and sub-parts)
        for (Location occLoc : instance.getAllOccupiedLocations()) {
            boolean wasWaterlogged = isBlockWaterlogged(instance, occLoc.getBlock());
            occLoc.getBlock().setType(wasWaterlogged ? Material.WATER : Material.AIR, true);
        }

        // Unregister from memory
        unregisterInstanceFromMemory(instance);

        // Update Chunk PDC
        if (normalizedRoot.getWorld() != null && normalizedRoot.getChunk().isLoaded()) {
            saveChunkPdc(normalizedRoot.getChunk());
        }

        // Drop items if requested
        if (dropItems && def != null) {
            Location center = normalizedRoot.clone().add(0.5, 0.5, 0.5);
            List<ItemStack> drops = calculateDrops(def, new ItemStack(Material.AIR));
            for (ItemStack d : drops) {
                normalizedRoot.getWorld().dropItemNaturally(center, d);
            }
        }

        deleteBlockAsync(normalizedRoot);
        return true;
    }

    private List<ItemStack> calculateDrops(CustomBlockDefinition def, ItemStack tool) {
        List<ItemStack> list = new ArrayList<>();
        boolean hasSilkTouch = tool != null && tool.containsEnchantment(Enchantment.SILK_TOUCH);
        int fortuneLevel = tool != null ? tool.getEnchantmentLevel(Enchantment.FORTUNE) : 0;

        for (BlockDropSpec drop : def.getMining().getDrops()) {
            if (drop.isRequiresSilkTouch() && !hasSilkTouch) continue;
            if (ThreadLocalRandom.current().nextDouble() > drop.getChance()) continue;

            int min = drop.getMinAmount();
            int max = drop.getMaxAmount();
            int amount = min == max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);

            if (drop.isFortuneAffected() && fortuneLevel > 0) {
                amount += ThreadLocalRandom.current().nextInt(0, fortuneLevel + 1);
            }

            if (amount <= 0) continue;

            if ("self".equalsIgnoreCase(drop.getItemId())) {
                list.add(def.buildBlockItemStack(amount));
            } else if (drop.getItemId().toLowerCase(Locale.ROOT).startsWith("synth:")) {
                String subId = drop.getItemId().substring(6);
                var subItem = plugin.getItemRegistry().get(subId);
                if (subItem != null) {
                    ItemStack st = subItem.buildItemStack();
                    st.setAmount(amount);
                    list.add(st);
                } else {
                    var subBlock = plugin.getCustomBlockRegistry().get(subId);
                    if (subBlock != null) {
                        list.add(subBlock.buildBlockItemStack(amount));
                    }
                }
            } else {
                Material m = Material.matchMaterial(drop.getItemId());
                if (m != null && !m.isAir()) {
                    list.add(new ItemStack(m, amount));
                }
            }
        }
        return list;
    }

    // =========================================================================
    // EXPLOSIONS, FLAMMABILITY & PISTONS
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Location center = event.getLocation();
        if (center.getWorld() == null) return;

        // Mob griefing check
        if (event.getEntity() != null && !(event.getEntity() instanceof org.bukkit.entity.TNTPrimed) && !(event.getEntity() instanceof org.bukkit.entity.minecart.ExplosiveMinecart)) {
            Boolean mobGriefing = center.getWorld().getGameRuleValue(GameRule.MOB_GRIEFING);
            if (Boolean.FALSE.equals(mobGriefing)) {
                return; // Mob griefing is disabled, do not destroy custom blocks
            }
        }

        float explosionPower = resolveExplosionPower(event.getEntity(), event.getYield());
        float radius = (float) Math.max(explosionPower * 1.5f, 3.0f);
        double maxD = 0;
        for (Block b : event.blockList()) {
            maxD = Math.max(maxD, b.getLocation().clone().add(0.5, 0.5, 0.5).distance(center));
        }
        radius = (float) Math.max(radius, maxD + 0.5);

        handleExplosion(center, explosionPower, radius, event.blockList(), event.getYield(), event.getEntity());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        Location center = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        if (center.getWorld() == null) return;

        float explosionPower = event.getYield() > 0 ? event.getYield() : 4.0f;
        float radius = (float) Math.max(explosionPower * 1.5f, 3.5f);
        double maxD = 0;
        for (Block b : event.blockList()) {
            maxD = Math.max(maxD, b.getLocation().clone().add(0.5, 0.5, 0.5).distance(center));
        }
        radius = (float) Math.max(radius, maxD + 0.5);

        handleExplosion(center, explosionPower, radius, event.blockList(), event.getYield(), null);
    }

    private float resolveExplosionPower(Entity entity, float fallbackYield) {
        if (entity instanceof org.bukkit.entity.Creeper creeper) {
            return creeper.isPowered() ? 6.0f : 3.0f;
        }
        if (entity instanceof org.bukkit.entity.TNTPrimed || entity instanceof org.bukkit.entity.minecart.ExplosiveMinecart) {
            return 4.0f;
        }
        if (entity instanceof org.bukkit.entity.EnderCrystal) {
            return 6.0f;
        }
        if (entity instanceof org.bukkit.entity.WitherSkull skull) {
            return skull.isCharged() ? 2.5f : 1.5f;
        }
        if (entity instanceof org.bukkit.entity.Fireball fireball) {
            float y = fireball.getYield();
            return y > 0 ? y : 1.5f;
        }
        if (entity instanceof org.bukkit.entity.Explosive explosive) {
            float y = explosive.getYield();
            if (y > 0) return y;
        }
        if (fallbackYield > 0) {
            return fallbackYield;
        }
        return 4.0f;
    }

    private void handleExplosion(Location center, float explosionPower, float radius, List<Block> blocks, float yield, Entity source) {
        World world = center.getWorld();
        if (world == null) return;

        Set<Location> candidates = new HashSet<>();
        Set<Location> vanillaCandidates = new HashSet<>();

        // 1. Check blocks from vanilla blockList
        Iterator<Block> it = blocks.iterator();
        while (it.hasNext()) {
            Block b = it.next();
            Location loc = normalize(b.getLocation());
            Location root = partToRootMap.get(loc);
            if (root != null) {
                candidates.add(root);
                vanillaCandidates.add(root);
                it.remove(); // Prevent vanilla from processing carrier block
            }
        }

        // 2. Spatial chunk query for custom blocks within blast radius
        int minChunkX = (center.getBlockX() - (int) Math.ceil(radius)) >> 4;
        int maxChunkX = (center.getBlockX() + (int) Math.ceil(radius)) >> 4;
        int minChunkZ = (center.getBlockZ() - (int) Math.ceil(radius)) >> 4;
        int maxChunkZ = (center.getBlockZ() + (int) Math.ceil(radius)) >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                for (PlacedBlockInstance instance : getBlocksInChunk(world, cx, cz)) {
                    Location root = instance.getLocation();
                    double dist = root.clone().add(0.5, 0.5, 0.5).distance(center);
                    if (dist <= radius) {
                        candidates.add(root);
                    }
                }
            }
        }

        // 3. Process candidate custom blocks
        for (Location root : candidates) {
            PlacedBlockInstance instance = placedBlocks.get(root);
            if (instance == null) continue;

            CustomBlockDefinition def = plugin.getCustomBlockRegistry().get(instance.getBlockId());
            if (def == null) continue;

            double dist = root.clone().add(0.5, 0.5, 0.5).distance(center);
            if (dist > radius) continue;

            // Blast resistance check: blocks with blast resistance >= 1200 resist explosions
            float blastRes = def.getMining().getBlastResistance();
            if (blastRes >= 1200.0f) continue;

            boolean destroyed;
            if (vanillaCandidates.contains(root)) {
                // Vanilla explosion rays explicitly determined this block was destroyed
                destroyed = true;
            } else {
                // Physical explosive force decay formula matching vanilla Minecraft
                // Effective force at distance: force = (explosionPower * 2.0) * (1 - dist / radius)
                // In vanilla, resistance threshold is blastRes / 5.0
                float force = (explosionPower * 2.0f) * (1.0f - (float) (dist / radius));
                destroyed = force >= (blastRes / 5.0f);
            }

            if (!destroyed) continue;

            SynthetixBlockExplodeEvent explodeEvent = new SynthetixBlockExplodeEvent(def, root, source, explosionPower, true);
            Bukkit.getPluginManager().callEvent(explodeEvent);

            if (explodeEvent.isCancelled() || !explodeEvent.isShouldBreak()) {
                continue;
            }

            // Determine if block drops item:
            // Blocks farther from epicenter (dist > 1.2) drop; point-blank epicenter blocks vaporize unless yield check succeeds
            boolean dropItem = dist > 1.2 || (yield > 0 && ThreadLocalRandom.current().nextFloat() < yield);

            // Spawn particle burst and sound
            Location bCenter = root.clone().add(0.5, 0.5, 0.5);
            ItemStack pItem = def.buildBlockItemStack(1);
            world.spawnParticle(org.bukkit.Particle.ITEM, bCenter, 30, 0.25, 0.25, 0.25, 0.05, pItem);
            world.playSound(bCenter, def.getMining().resolveBreakSound(), 1.0f, 1.0f);

            removeCustomBlock(root, dropItem);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        Location loc = normalize(event.getBlock().getLocation());
        Location root = partToRootMap.get(loc);
        if (root == null) return;

        PlacedBlockInstance instance = placedBlocks.get(root);
        if (instance == null) return;

        CustomBlockDefinition def = plugin.getCustomBlockRegistry().get(instance.getBlockId());
        if (def == null || !def.getInteraction().isFlammable()) {
            event.setCancelled(true); // Fire cannot consume non-flammable custom blocks
        } else {
            removeCustomBlock(root, false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            if (isCustomBlock(b.getLocation())) {
                Location root = partToRootMap.get(normalize(b.getLocation()));
                PlacedBlockInstance instance = root != null ? placedBlocks.get(root) : null;
                CustomBlockDefinition def = instance != null ? plugin.getCustomBlockRegistry().get(instance.getBlockId()) : null;
                if (def == null || !def.getInteraction().isPistonPushable()) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            if (isCustomBlock(b.getLocation())) {
                Location root = partToRootMap.get(normalize(b.getLocation()));
                PlacedBlockInstance instance = root != null ? placedBlocks.get(root) : null;
                CustomBlockDefinition def = instance != null ? plugin.getCustomBlockRegistry().get(instance.getBlockId()) : null;
                if (def == null || !def.getInteraction().isPistonPushable()) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    // =========================================================================
    // CHUNK STREAMING, PDC DUAL-SYNC & PROTECTIONS
    // =========================================================================

    @EventHandler(priority = EventPriority.NORMAL)
    public void onChunkLoad(ChunkLoadEvent event) {
        loadChunkBlocks(event.getChunk());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onChunkUnload(ChunkUnloadEvent event) {
        unloadChunkBlocks(event.getChunk());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNotePlay(NotePlayEvent event) {
        if (isCustomBlock(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (isCustomBlock(block.getLocation())) {
            // Allow physics on waterlogged custom blocks so fluids can flow and update
            if (isBlockWaterlogged(block)) {
                return;
            }
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFluidFlow(BlockFromToEvent event) {
        Block toBlock = event.getToBlock();
        if (isCustomBlock(toBlock.getLocation())) {
            PlacedBlockInstance inst = getPlacedBlock(toBlock.getLocation());
            CustomBlockDefinition def = inst != null ? plugin.getCustomBlockRegistry().get(inst.getBlockId()) : null;
            if (def != null && def.getPhysics().isWaterloggable()) {
                if (!isBlockWaterlogged(inst, toBlock)) {
                    setBlockWaterlogged(toBlock, inst, def, true);
                    return;
                }
            }
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        Block clickedBlock = event.getBlockClicked();
        TargetBlockResult target = resolveTargetCustomBlock(player, clickedBlock, event.getBlockFace());
        if (target != null) {
            CustomBlockDefinition def = plugin.getCustomBlockRegistry().get(target.instance.getBlockId());
            if (def != null && def.getPhysics().isWaterloggable()) {
                event.setCancelled(true);
                handleWaterlogging(player, target.block, target.instance, def, true, event.getHand());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        Block clickedBlock = event.getBlockClicked();
        TargetBlockResult target = resolveTargetCustomBlock(player, clickedBlock, event.getBlockFace());
        if (target != null) {
            CustomBlockDefinition def = plugin.getCustomBlockRegistry().get(target.instance.getBlockId());
            if (def != null && def.getPhysics().isWaterloggable()) {
                if (isBlockWaterlogged(target.instance, target.block)) {
                    event.setCancelled(true);
                    handleWaterlogging(player, target.block, target.instance, def, false, event.getHand());
                }
            }
        }
    }

    public void loadChunkBlocks(Chunk chunk) {
        if (chunk == null) return;
        String worldName = chunk.getWorld().getName();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        long chunkKey = getChunkKey(chunkX, chunkZ);

        Map<Long, Map<Location, PlacedBlockInstance>> worldMap = worldChunkBlocks.get(worldName);
        if (worldMap != null && worldMap.containsKey(chunkKey)) {
            return; // Already loaded
        }

        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        String pdcJson = pdc.get(CHUNK_BLOCKS_KEY, PersistentDataType.STRING);

        if (pdcJson != null && !pdcJson.trim().isEmpty()) {
            List<PlacedBlockRecord> records = PlacedBlockRecord.deserializeChunkRecords(worldName, pdcJson);
            boolean dirty = false;
            for (PlacedBlockRecord record : records) {
                Location loc = new Location(chunk.getWorld(), record.getX(), record.getY(), record.getZ());
                CustomBlockDefinition def = plugin.getCustomBlockRegistry().get(record.getBlockId());
                Block b = chunk.getWorld().getBlockAt(loc);

                // World reconciliation check (detect WorldEdit, //set 0, or rollback)
                if (def != null && isBlockMatchingDefinition(b, def, record)) {
                    if (!placedBlocks.containsKey(loc)) {
                        PlacedBlockInstance instance = fromRecord(record);
                        registerInstanceInMemory(instance);
                        spawnDisplayEntities(instance, def);
                    }
                } else {
                    deleteBlockAsync(loc);
                    dirty = true;
                }
            }
            if (dirty) {
                saveChunkPdc(chunk);
            }
        } else {
            // Secondary DB fallback: query database for this chunk
            plugin.getStorageManager().getProvider().loadBlocksInChunk(worldName, chunkX, chunkZ).thenAccept(records -> {
                if (records == null || records.isEmpty()) return;
                Location chunkCenter = new Location(chunk.getWorld(), (chunkX << 4) + 8, 64, (chunkZ << 4) + 8);
                PlatformScheduler.get().runLocation(chunkCenter, () -> {
                    if (!chunk.isLoaded()) return;
                    boolean foundValid = false;
                    for (PlacedBlockRecord r : records) {
                        Location loc = new Location(chunk.getWorld(), r.getX(), r.getY(), r.getZ());
                        CustomBlockDefinition def = plugin.getCustomBlockRegistry().get(r.getBlockId());
                        Block b = chunk.getWorld().getBlockAt(loc);
                        if (def != null && isBlockMatchingDefinition(b, def, r)) {
                            if (!placedBlocks.containsKey(loc)) {
                                PlacedBlockInstance instance = fromRecord(r);
                                registerInstanceInMemory(instance);
                                spawnDisplayEntities(instance, def);
                                if (plugin.getBedrockManager() != null && plugin.getBedrockManager().getBlockVisualManager() != null) {
                                    plugin.getBedrockManager().getBlockVisualManager().onBlockSpawn(instance, def);
                                }
                                foundValid = true;
                            }
                        } else {
                            deleteBlockAsync(loc);
                        }
                    }
                    if (foundValid) {
                        saveChunkPdc(chunk);
                    }
                });
            });
        }
    }

    public void unloadChunkBlocks(Chunk chunk) {
        if (chunk == null) return;
        String worldName = chunk.getWorld().getName();
        long chunkKey = getChunkKey(chunk.getX(), chunk.getZ());
        Map<Long, Map<Location, PlacedBlockInstance>> worldMap = worldChunkBlocks.get(worldName);
        if (worldMap == null) return;

        Map<Location, PlacedBlockInstance> chunkInstances = worldMap.remove(chunkKey);
        if (chunkInstances == null || chunkInstances.isEmpty()) return;

        for (PlacedBlockInstance instance : chunkInstances.values()) {
            Location rootLoc = instance.getLocation();

            // Cancel any active mining session on this block
            for (Iterator<MiningSession> it = activeMiningSessions.values().iterator(); it.hasNext(); ) {
                MiningSession s = it.next();
                if (s.blockLoc.equals(rootLoc)) {
                    cleanupCrackDisplay(s);
                    it.remove();
                }
            }

            // Despawn non-persistent visual entities to free client & server memory
            cleanupDisplayEntities(instance);
            if (plugin.getBedrockManager() != null && plugin.getBedrockManager().getBlockVisualManager() != null) {
                plugin.getBedrockManager().getBlockVisualManager().onBlockCleanup(instance);
            }

            // Remove from memory
            placedBlocks.remove(rootLoc);
            for (Location occLoc : instance.getAllOccupiedLocations()) {
                partToRootMap.remove(occLoc);
            }
        }
    }

    public void saveChunkPdc(Chunk chunk) {
        if (chunk == null) return;
        long chunkKey = getChunkKey(chunk.getX(), chunk.getZ());
        Map<Long, Map<Location, PlacedBlockInstance>> worldMap = worldChunkBlocks.get(chunk.getWorld().getName());
        Map<Location, PlacedBlockInstance> chunkInstances = worldMap != null ? worldMap.get(chunkKey) : null;

        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        if (chunkInstances == null || chunkInstances.isEmpty()) {
            pdc.remove(CHUNK_BLOCKS_KEY);
            return;
        }

        List<PlacedBlockRecord> records = new ArrayList<>(chunkInstances.size());
        for (PlacedBlockInstance inst : chunkInstances.values()) {
            records.add(toRecord(inst));
        }
        String json = PlacedBlockRecord.serializeChunkRecords(records);
        pdc.set(CHUNK_BLOCKS_KEY, PersistentDataType.STRING, json);
    }

    public void respawnDisplayEntities(CustomBlockDefinition def) {
        if (def == null) return;
        for (PlacedBlockInstance instance : placedBlocks.values()) {
            if (instance.getBlockId().equalsIgnoreCase(def.getId())) {
                spawnDisplayEntities(instance, def);
            }
        }
    }

    public void respawnDisplayEntities(PlacedBlockInstance instance, CustomBlockDefinition def) {
        spawnDisplayEntities(instance, def);
    }

    // =========================================================================
    // DATABASE PERSISTENCE
    // =========================================================================

    public void persistBlockAsync(PlacedBlockInstance instance) {
        if (instance == null || instance.getLocation() == null || instance.getLocation().getWorld() == null) return;
        PlacedBlockRecord record = toRecord(instance);
        plugin.getStorageManager().getProvider().saveBlock(record).exceptionally(ex -> {
            plugin.getLogger().warning("[BlockStorage] Failed to persist block at " + instance.getLocation() + ": " + ex.getMessage());
            return null;
        });
    }

    public void deleteBlockAsync(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        plugin.getStorageManager().getProvider().deleteBlock(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())
            .exceptionally(ex -> {
                plugin.getLogger().warning("[BlockStorage] Failed to delete block at " + loc + ": " + ex.getMessage());
                return null;
            });
    }

    private void migrateLegacyYamlFile() {
        File file = new File(plugin.getDataFolder(), "data/placed_blocks.yml");
        if (!file.exists()) return;

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection sec = config.getConfigurationSection("blocks");
            if (sec == null) return;

            int count = 0;
            for (String key : sec.getKeys(false)) {
                ConfigurationSection bSec = sec.getConfigurationSection(key);
                if (bSec == null) continue;

                String worldName = bSec.getString("world");
                World world = worldName != null ? Bukkit.getWorld(worldName) : null;
                if (world == null) continue;

                int x = bSec.getInt("x");
                int y = bSec.getInt("y");
                int z = bSec.getInt("z");
                Location loc = new Location(world, x, y, z);

                String blockId = bSec.getString("block-id");
                String placerStr = bSec.getString("placer");
                UUID placerUuid = placerStr != null ? UUID.fromString(placerStr) : null;
                String faceStr = bSec.getString("facing", "NORTH");
                BlockFace facing = BlockFace.valueOf(faceStr);

                PlacedBlockInstance instance = new PlacedBlockInstance(loc, blockId, placerUuid, facing);
                for (String uStr : bSec.getStringList("display-entities")) {
                    try {
                        instance.getDisplayEntityUuids().add(UUID.fromString(uStr));
                    } catch (Exception ignored) {}
                }

                List<String> offsets = bSec.getStringList("multi-part-offsets");
                for (String offStr : offsets) {
                    String[] parts = offStr.split(",");
                    if (parts.length == 3) {
                        int ox = Integer.parseInt(parts[0]);
                        int oy = Integer.parseInt(parts[1]);
                        int oz = Integer.parseInt(parts[2]);
                        instance.getMultiPartOffsets().add(new Vector3i(ox, oy, oz));
                        partToRootMap.put(loc.clone().add(ox, oy, oz), loc);
                    }
                }

                persistBlockAsync(instance);
                count++;
            }

            plugin.getLogger().info("[BlockStorage] Successfully migrated " + count + " placed custom blocks from legacy placed_blocks.yml to " +
                    plugin.getStorageManager().getProvider().getName().toUpperCase() + " database!");

            File backup = new File(plugin.getDataFolder(), "data/placed_blocks.yml.migrated");
            file.renameTo(backup);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[BlockStorage] Failed to migrate legacy placed_blocks.yml", e);
        }
    }

    public static PlacedBlockRecord toRecord(PlacedBlockInstance instance) {
        Location loc = instance.getLocation();
        PlacedBlockRecord record = new PlacedBlockRecord(
                loc.getWorld().getName(),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ(),
                instance.getBlockId(),
                instance.getPlacerUuid(),
                instance.getFacing().name()
        );
        record.getDisplayEntityUuids().addAll(instance.getDisplayEntityUuids());
        record.getMultiPartOffsets().addAll(instance.getMultiPartOffsets());
        record.getCustomData().putAll(instance.getStateData());
        return record;
    }

    public static PlacedBlockInstance fromRecord(PlacedBlockRecord record) {
        World world = Bukkit.getWorld(record.getWorld());
        if (world == null) return null;
        Location loc = new Location(world, record.getX(), record.getY(), record.getZ());
        BlockFace facing = BlockFace.NORTH;
        try {
            if (record.getFacing() != null) facing = BlockFace.valueOf(record.getFacing());
        } catch (Exception ignored) {}

        PlacedBlockInstance instance = new PlacedBlockInstance(loc, record.getBlockId(), record.getPlacerUuid(), facing);
        instance.getDisplayEntityUuids().addAll(record.getDisplayEntityUuids());
        instance.getMultiPartOffsets().addAll(record.getMultiPartOffsets());
        instance.getStateData().putAll(record.getCustomData());
        return instance;
    }

    // =========================================================================
    // HELPERS & API QUERIES
    // =========================================================================

    public boolean isCustomBlock(Location loc) {
        if (loc == null) return false;
        return partToRootMap.containsKey(normalize(loc));
    }

    public Location getRootLocation(Location loc) {
        if (loc == null) return null;
        return partToRootMap.get(normalize(loc));
    }

    public PlacedBlockInstance getPlacedBlock(Location loc) {
        if (loc == null) return null;
        Location root = partToRootMap.get(normalize(loc));
        return root != null ? placedBlocks.get(root) : null;
    }

    public Collection<PlacedBlockInstance> getBlocksInChunk(Chunk chunk) {
        if (chunk == null) return Collections.emptyList();
        return getBlocksInChunk(chunk.getWorld(), chunk.getX(), chunk.getZ());
    }

    public Collection<PlacedBlockInstance> getBlocksInChunk(World world, int chunkX, int chunkZ) {
        if (world == null) return Collections.emptyList();
        Map<Long, Map<Location, PlacedBlockInstance>> worldMap = worldChunkBlocks.get(world.getName());
        if (worldMap == null) return Collections.emptyList();
        Map<Location, PlacedBlockInstance> chunkInstances = worldMap.get(getChunkKey(chunkX, chunkZ));
        if (chunkInstances == null) return Collections.emptyList();
        return Collections.unmodifiableCollection(chunkInstances.values());
    }

    public Map<Location, PlacedBlockInstance> getPlacedBlocks() {
        return Collections.unmodifiableMap(placedBlocks);
    }

    public int getActiveBlockCount() {
        return placedBlocks.size();
    }

    public boolean isKeyframeOptimization() {
        return keyframeOptimization;
    }

    public int getKeyframeInterpolationDuration() {
        return keyframeInterpolationDuration;
    }

    public int getKeyframeDispatchInterval() {
        return keyframeDispatchInterval;
    }

    public boolean isIdleZeroPackets() {
        return idleZeroPackets;
    }

    public PlacedBlockInstance placeBlock(Location loc, String blockId, BlockFace facing, Player placer) {
        CustomBlockDefinition def = plugin.getCustomBlockRegistry().get(blockId);
        if (def == null) return null;
        UUID uuid = placer != null ? placer.getUniqueId() : null;
        BlockFace face = facing != null ? facing : BlockFace.NORTH;
        return placeCustomBlock(loc, def, face, uuid);
    }

    public boolean removeBlock(Location loc, boolean dropItems) {
        return removeCustomBlock(loc, dropItems);
    }

    private static final Set<Material> REPLACEABLE_MATERIALS = EnumSet.noneOf(Material.class);

    static {
        addReplaceableMaterial("SHORT_GRASS");
        addReplaceableMaterial("GRASS"); // Legacy name in Minecraft <= 1.20.2
        addReplaceableMaterial("TALL_GRASS");
        addReplaceableMaterial("FERN");
        addReplaceableMaterial("LARGE_FERN");
        addReplaceableMaterial("SEAGRASS");
        addReplaceableMaterial("TALL_SEAGRASS");
        addReplaceableMaterial("VINE");
        addReplaceableMaterial("GLOW_LICHEN");
        addReplaceableMaterial("DEAD_BUSH");
        addReplaceableMaterial("HANGING_ROOTS"); // Only hanging roots, never mangrove roots or muddy mangrove roots
        addReplaceableMaterial("FIRE");
        addReplaceableMaterial("SOUL_FIRE");
        addReplaceableMaterial("STRUCTURE_VOID");
        addReplaceableMaterial("LIGHT");
        addReplaceableMaterial("FROGSPAWN");
        addReplaceableMaterial("CAVE_VINES");
        addReplaceableMaterial("CAVE_VINES_PLANT");
        addReplaceableMaterial("TWISTING_VINES");
        addReplaceableMaterial("TWISTING_VINES_PLANT");
        addReplaceableMaterial("WEEPING_VINES");
        addReplaceableMaterial("WEEPING_VINES_PLANT");
    }

    private static void addReplaceableMaterial(String name) {
        Material mat = Material.getMaterial(name);
        if (mat != null) {
            REPLACEABLE_MATERIALS.add(mat);
        }
    }

    public boolean isBlockReplaceable(Block b) {
        if (b == null) return false;
        Location loc = normalize(b.getLocation());
        if (isCustomBlock(loc)) return false;

        Material m = b.getType();
        if (m.isAir()) return true;
        if (b.isLiquid()) return true;

        if (REPLACEABLE_MATERIALS.contains(m)) {
            return true;
        }

        // Special case: Snow layer is only replaceable if it is a single layer (layers == 1)
        if (m == Material.SNOW) {
            if (b.getBlockData() instanceof org.bukkit.block.data.type.Snow snow) {
                return snow.getLayers() == 1;
            }
            return true;
        }

        return false;
    }

    public boolean isBlockWaterlogged(Block block) {
        if (block == null) return false;
        if (block.getType() == Material.WATER) return true;
        if (block.getBlockData() instanceof Waterlogged w) {
            return w.isWaterlogged();
        }
        PlacedBlockInstance inst = getPlacedBlock(block.getLocation());
        if (inst != null && Boolean.TRUE.equals(inst.getState("waterlogged"))) {
            return true;
        }
        return false;
    }

    public boolean isBlockWaterlogged(PlacedBlockInstance inst, Block block) {
        if (inst != null && Boolean.TRUE.equals(inst.getState("waterlogged"))) {
            return true;
        }
        if (block != null) {
            if (block.getType() == Material.WATER) return true;
            if (block.getBlockData() instanceof Waterlogged w) {
                return w.isWaterlogged();
            }
        }
        return false;
    }

    public boolean isBlockWaterloggable(Block block, CustomBlockDefinition def) {
        if (def != null && def.getPhysics().isWaterloggable()) return true;
        if (block != null && block.getBlockData() instanceof Waterlogged) return true;
        return false;
    }

    public boolean isBlockMatchingDefinition(Block block, CustomBlockDefinition def, PlacedBlockRecord record) {
        if (block == null || def == null) return false;
        Material current = block.getType();
        if (current == def.getBaseMaterial()) return true;
        if (def.getPhysics().isWaterloggable()) {
            if (current == Material.WATER) return true;
            if (block.getBlockData() instanceof Waterlogged w && w.isWaterlogged()) return true;
            if (record != null && Boolean.TRUE.equals(record.getCustomData().get("waterlogged"))) return true;
        }
        return false;
    }

    public void propagateWaterFlow(Block sourceBlock) {
        // Native Minecraft fluid engine manages fluid dynamics when proxy is Material.WATER or native Waterlogged
    }

    public void cleanWaterFlow(Block sourceBlock) {
        // Native Minecraft fluid engine manages fluid recession when proxy is set back to baseMaterial
    }

    public boolean canWaterFlowInto(Block block) {
        if (block == null) return false;
        Location loc = normalize(block.getLocation());
        if (isCustomBlock(loc)) {
            PlacedBlockInstance inst = getPlacedBlock(loc);
            CustomBlockDefinition def = inst != null ? plugin.getCustomBlockRegistry().get(inst.getBlockId()) : null;
            return def != null && def.getPhysics().isWaterloggable() && !isBlockWaterlogged(inst, block);
        }
        Material m = block.getType();
        if (m.isAir()) return true;
        if (m == Material.WATER) return false;
        return isBlockReplaceable(block);
    }

    public static String getBlockIdFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String blockId = pdc.get(CustomBlockDefinition.BLOCK_ID_KEY, PersistentDataType.STRING);
        if (blockId == null) {
            String itemId = pdc.get(new NamespacedKey("synthetix", "item_id"), PersistentDataType.STRING);
            if (itemId != null) {
                try {
                    SynthetixPlugin plugin = SynthetixPlugin.getPlugin(SynthetixPlugin.class);
                    if (plugin != null && plugin.getCustomBlockRegistry() != null && plugin.getCustomBlockRegistry().has(itemId)) {
                        blockId = itemId;
                    }
                } catch (Throwable ignored) {}
            }
        }
        return blockId;
    }

    private Location normalize(Location loc) {
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    private List<Location> calculateMultiPartLocations(Location root, Vector3i size, BlockFace facing) {
        List<Location> list = new ArrayList<>();
        list.add(root);
        if (size.x <= 1 && size.y <= 1 && size.z <= 1) return list;

        for (int dx = 0; dx < size.x; dx++) {
            for (int dy = 0; dy < size.y; dy++) {
                for (int dz = 0; dz < size.z; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    Vector3i rot = rotateOffset(dx, dy, dz, facing);
                    list.add(root.clone().add(rot.x, rot.y, rot.z));
                }
            }
        }
        return list;
    }

    private Vector3i rotateOffset(int x, int y, int z, BlockFace facing) {
        return switch (facing) {
            case SOUTH -> new Vector3i(-x, y, -z);
            case WEST -> new Vector3i(-z, y, x);
            case EAST -> new Vector3i(z, y, -x);
            default -> new Vector3i(x, y, z); // NORTH
        };
    }
}
