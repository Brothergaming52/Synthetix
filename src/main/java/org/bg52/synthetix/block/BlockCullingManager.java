package org.bg52.synthetix.block;

import org.bg52.synthetix.SynthetixPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bg52.synthetix.scheduler.PlatformScheduler;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance client-side occlusion culling and view-frustum prioritized rendering limiter.
 * <p>
 * Responsibilities:
 * 1. Restricts client rendering to a hard limit of 50 custom blocks simultaneously, prioritizing blocks
 *    directly in front of the player (forward FOV crosshairs) and closest to the player.
 * 2. Employs 3D Digital Differential Analyzer (DDA) raycasting to detect solid, opaque occluders
 *    between the player's eyes and custom blocks, culling blocks hidden behind solid walls/floors.
 * 3. Hides/shows entities using Paper's native packet-level {@link Player#hideEntity} / {@link Player#showEntity},
 *    completely cutting off client GPU/CPU draw calls, vertex transforms, and server packet floods.
 * 4. Tracks global visibility so custom block animation loops can skip matrix calculations for blocks
 *    not currently visible to any online player.
 */
public class BlockCullingManager implements Listener {

    private final SynthetixPlugin plugin;
    private final CustomBlockManager blockManager;

    private PlatformScheduler.TaskHandle cullingTask;
    private boolean enabled = true;
    private int maxRenderedPerPlayer = 50;
    private double maxDistance = 48.0;
    private double nearRadius = 5.5; // 5 blocks in each axis (ahead, behind, left, right, up, down = 10 block diameter)
    private int checkIntervalTicks = 4;
    private double fovPriorityWeight = 100.0;
    private double distanceWeight = 1.5;

    // Per-player set of display entity UUIDs currently rendered (visible) to that player
    private final Map<UUID, Set<UUID>> playerVisibleDisplays = new ConcurrentHashMap<>();

    // Per-player set of display entity UUIDs currently hidden from that player via player.hideEntity()
    private final Map<UUID, Set<UUID>> playerHiddenDisplays = new ConcurrentHashMap<>();

    // UUIDs of all display entities visible to at least one online player
    private final Set<UUID> globallyVisibleDisplays = ConcurrentHashMap.newKeySet();

    public BlockCullingManager(SynthetixPlugin plugin, CustomBlockManager blockManager) {
        this.plugin = plugin;
        this.blockManager = blockManager;
    }

    public void start() {
        reloadConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        if (!enabled) return;

        startTask();
    }

    public void stop() {
        if (cullingTask != null) {
            cullingTask.cancel();
            cullingTask = null;
        }

        // Restore visibility for all hidden entities across all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline()) continue;
            Set<UUID> hiddenSet = playerHiddenDisplays.remove(player.getUniqueId());
            if (hiddenSet != null && !hiddenSet.isEmpty()) {
                PlatformScheduler.get().runEntity(player, () -> {
                    for (UUID uuid : hiddenSet) {
                        Entity e = Bukkit.getEntity(uuid);
                        if (e != null) {
                            try {
                                player.showEntity(plugin, e);
                            } catch (Throwable ignored) {}
                        }
                    }
                });
            }
        }

        playerVisibleDisplays.clear();
        playerHiddenDisplays.clear();
        globallyVisibleDisplays.clear();
    }

    public void reloadConfig() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("custom-blocks.culling");
        if (sec != null) {
            this.enabled = sec.getBoolean("enabled", true);
            this.maxRenderedPerPlayer = Math.max(1, sec.getInt("max-rendered-per-player", 50));
            this.maxDistance = Math.max(8.0, sec.getDouble("max-distance", 48.0));
            this.nearRadius = Math.max(1.0, sec.getDouble("near-radius", 5.5));
            this.checkIntervalTicks = Math.max(1, sec.getInt("check-interval-ticks", 4));
            this.fovPriorityWeight = sec.getDouble("fov-priority-weight", 100.0);
            this.distanceWeight = sec.getDouble("distance-weight", 1.5);
        } else {
            this.enabled = true;
            this.maxRenderedPerPlayer = 50;
            this.maxDistance = 48.0;
            this.nearRadius = 5.5;
            this.checkIntervalTicks = 4;
            this.fovPriorityWeight = 100.0;
            this.distanceWeight = 1.5;
        }

        if (cullingTask != null) {
            cullingTask.cancel();
            cullingTask = null;
        }
        if (enabled) {
            startTask();
        }
    }

    private void startTask() {
        cullingTask = PlatformScheduler.get().runGlobalTimer(() -> {
            try {
                update();
            } catch (Throwable ignored) {
            }
        }, 10L, checkIntervalTicks);
    }

    /**
     * Main culling tick routine executed every check-interval-ticks (default 4 ticks = ~200ms).
     * On Folia, dispatches per-player candidate evaluation and packet hiding/showing on the player's
     * owning entity thread, completely eliminating cross-region thread-check failures.
     */
    public void update() {
        if (!enabled) return;

        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        if (onlinePlayers.isEmpty()) {
            globallyVisibleDisplays.clear();
            playerVisibleDisplays.clear();
            playerHiddenDisplays.clear();
            return;
        }

        Set<UUID> onlinePlayerIds = new HashSet<>();
        for (Player player : onlinePlayers) {
            onlinePlayerIds.add(player.getUniqueId());
        }
        playerVisibleDisplays.keySet().removeIf(id -> !onlinePlayerIds.contains(id));
        playerHiddenDisplays.keySet().removeIf(id -> !onlinePlayerIds.contains(id));

        for (Player player : onlinePlayers) {
            if (!player.isOnline() || player.isDead()) continue;
            PlatformScheduler.get().runEntity(player, () -> {
                try {
                    updatePlayer(player);
                } catch (Throwable ignored) {}
            });
        }

        // Recompute globally visible displays across all active players
        Set<UUID> allVisible = new HashSet<>();
        for (Set<UUID> visibleSet : playerVisibleDisplays.values()) {
            allVisible.addAll(visibleSet);
        }
        globallyVisibleDisplays.clear();
        globallyVisibleDisplays.addAll(allVisible);
    }

    /**
     * Evaluates candidate blocks and updates packet visibility for a single player on their owning thread.
     */
    public void updatePlayer(Player player) {
        if (!player.isOnline() || player.isDead()) return;

        Location eyeLoc = player.getEyeLocation();
        Vector eyeDir = eyeLoc.getDirection().normalize();
        World world = player.getWorld();

        int pChunkX = eyeLoc.getBlockX() >> 4;
        int pChunkZ = eyeLoc.getBlockZ() >> 4;
        int chunkRadius = (int) Math.ceil(maxDistance / 16.0);

        List<CandidateBlock> nearCandidates = new ArrayList<>();
        List<CandidateBlock> farCandidates = new ArrayList<>();
        List<PlacedBlockInstance> allNearbyInstances = new ArrayList<>();

        // 1. Query loaded chunks around player within max distance
        for (int cx = pChunkX - chunkRadius; cx <= pChunkX + chunkRadius; cx++) {
            for (int cz = pChunkZ - chunkRadius; cz <= pChunkZ + chunkRadius; cz++) {
                Collection<PlacedBlockInstance> chunkBlocks = blockManager.getBlocksInChunk(world, cx, cz);
                for (PlacedBlockInstance inst : chunkBlocks) {
                    allNearbyInstances.add(inst);

                    Location centerLoc = inst.getLocation().clone().add(0.5, 0.5, 0.5);
                    double dx = Math.abs(centerLoc.getX() - eyeLoc.getX());
                    double dy = Math.abs(centerLoc.getY() - eyeLoc.getY());
                    double dz = Math.abs(centerLoc.getZ() - eyeLoc.getZ());
                    double dist = centerLoc.distance(eyeLoc);
                    if (dist > maxDistance) continue;

                    Vector toBlock = centerLoc.toVector().subtract(eyeLoc.toVector());
                    Vector dir = dist > 0.0001 ? toBlock.clone().multiply(1.0 / dist) : eyeDir;
                    double dot = eyeDir.dot(dir);

                    // Frustum priority score: blocks in crosshairs (dot ~ 1.0) have highest score
                    // Deprioritizes blocks behind player (dot < 0) and distant blocks
                    double score = (dot >= 0.0 ? dot * fovPriorityWeight : dot * (fovPriorityWeight * 0.3)) - (dist * distanceWeight);

                    CandidateBlock cand = new CandidateBlock(inst, centerLoc, dist, score);

                    // 10-block diameter zone (5 blocks in each axis: ahead, behind, left, right, up, down)
                    if (dx <= nearRadius && dy <= nearRadius && dz <= nearRadius) {
                        nearCandidates.add(cand);
                    } else {
                        farCandidates.add(cand);
                    }
                }
            }
        }

        // 2. Sort near candidates by distance ascending (closest to player first)
        nearCandidates.sort(Comparator.comparingDouble(CandidateBlock::dist));

        // 3. Sort far candidates by priority score descending (in gaze / front first)
        farCandidates.sort((a, b) -> Double.compare(b.score, a.score));

        // 4. Selection: First satisfy up to maxRenderedPerPlayer from near bubble (NO occlusion culling, never disappears on head movement)
        Set<UUID> neededEntityUuids = new HashSet<>();
        int renderedCount = 0;

        for (CandidateBlock cand : nearCandidates) {
            if (renderedCount >= maxRenderedPerPlayer) {
                break;
            }
            renderedCount++;
            for (UUID entityUuid : cand.instance.getDisplayEntityUuids()) {
                neededEntityUuids.add(entityUuid);
            }
        }

        // 5. Fill remaining slots with far blocks (occlusion culling and frustum priority applied)
        if (renderedCount < maxRenderedPerPlayer) {
            for (CandidateBlock cand : farCandidates) {
                if (renderedCount >= maxRenderedPerPlayer) {
                    break;
                }

                // Check occlusion against solid opaque blocks
                if (isOccluded(world, eyeLoc, cand.centerLoc, cand.instance, cand.dist)) {
                    continue; // Culled by solid block obstacle
                }

                renderedCount++;
                for (UUID entityUuid : cand.instance.getDisplayEntityUuids()) {
                    neededEntityUuids.add(entityUuid);
                }
            }
        }

        // 6. Update player entity visibility diffs using tracked hidden set (ZERO calls to player.canSee to prevent Folia thread crashes)
        Set<UUID> hiddenDisplays = playerHiddenDisplays.computeIfAbsent(player.getUniqueId(), k -> ConcurrentHashMap.newKeySet());

        // Hide entities that are in candidate chunks but not in the prioritized visible set
        for (PlacedBlockInstance inst : allNearbyInstances) {
            for (UUID uuid : inst.getDisplayEntityUuids()) {
                if (!neededEntityUuids.contains(uuid)) {
                    if (hiddenDisplays.add(uuid)) {
                        Entity e = Bukkit.getEntity(uuid);
                        if (e != null) {
                            try {
                                player.hideEntity(plugin, e);
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            }
        }

        // Show entities that are needed and currently hidden
        for (UUID uuid : neededEntityUuids) {
            if (hiddenDisplays.remove(uuid)) {
                Entity e = Bukkit.getEntity(uuid);
                if (e != null) {
                    try {
                        player.showEntity(plugin, e);
                    } catch (Throwable ignored) {}
                }
            }
        }

        // Record visible entities for this player
        playerVisibleDisplays.put(player.getUniqueId(), neededEntityUuids);
    }

    /**
     * Determines whether a custom block is occluded from the player's perspective.
     */
    public boolean isOccluded(World world, Location eyeLoc, Location centerLoc, PlacedBlockInstance instance, double dist) {
        try {
            // Immediate close-proximity bypass: never cull blocks within the 5-block near bubble
            double dx = Math.abs(centerLoc.getX() - eyeLoc.getX());
            double dy = Math.abs(centerLoc.getY() - eyeLoc.getY());
            double dz = Math.abs(centerLoc.getZ() - eyeLoc.getZ());
            if (dx <= nearRadius && dy <= nearRadius && dz <= nearRadius) {
                return false;
            }
            if (dist <= nearRadius) {
                return false;
            }

            // On Folia, only evaluate occlusion if the block location is owned by the current region thread.
            // Reading world blocks across region boundaries triggers Moonrise "Cannot read world asynchronously".
            Location rootLoc = instance.getLocation();
            if (PlatformScheduler.isFolia() && !PlatformScheduler.isOwnedByCurrentRegion(rootLoc)) {
                return false; // Fail open: consider visible rather than violating thread bounds
            }

            Block rootBlock = rootLoc.getBlock();

            // Level 1: Full 6-face cavity check (surrounded completely by solid opaque cubes)
            if (isCompletelyEnclosed(rootBlock)) {
                return true;
            }

            // Occupied positions of this custom block (root + multi-part offsets) to prevent self-occlusion
            List<Location> targets = instance.getAllOccupiedLocations();

            // Level 2: Fast 3D DDA Raycast Test 1: Block Center
            if (!isRayOccluded(world, eyeLoc.getX(), eyeLoc.getY(), eyeLoc.getZ(),
                    centerLoc.getX(), centerLoc.getY(), centerLoc.getZ(),
                    rootBlock.getX(), rootBlock.getY(), rootBlock.getZ(), targets)) {
                return false; // Center is visible
            }

            // Level 2: Fast 3D DDA Raycast Test 2: Block Top Edge (peeking over ledges/slabs)
            if (!isRayOccluded(world, eyeLoc.getX(), eyeLoc.getY(), eyeLoc.getZ(),
                    centerLoc.getX(), centerLoc.getY() + 0.4, centerLoc.getZ(),
                    rootBlock.getX(), rootBlock.getY(), rootBlock.getZ(), targets)) {
                return false; // Top edge is visible
            }

            return true; // Both center and top are occluded by opaque solid blocks
        } catch (Throwable ignored) {
            return false; // Fail open on cross-region or unloaded block read
        }
    }

    /**
     * Checks if a block is enclosed on all 6 cardinal directions by solid, opaque occluding blocks.
     */
    private boolean isCompletelyEnclosed(Block block) {
        if (block == null) return false;
        try {
            if (PlatformScheduler.isFolia() && !PlatformScheduler.isOwnedByCurrentRegion(block.getLocation())) {
                return false;
            }
            BlockFace[] faces = new BlockFace[]{
                    BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH,
                    BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
            };
            for (BlockFace face : faces) {
                Block neighbor = block.getRelative(face);
                if (PlatformScheduler.isFolia() && !PlatformScheduler.isOwnedByCurrentRegion(neighbor.getLocation())) {
                    return false;
                }
                if (!neighbor.getType().isOccluding()) {
                    return false; // Exposed to air, glass, water, or transparent blocks
                }
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Fast 3D Digital Differential Analyzer (DDA) raycast through voxel grid.
     * Evaluates whether an opaque solid block intervenes between eye and target.
     *
     * @return true if ray is blocked by an occluding block before reaching target; false if clear line of sight
     */
    private boolean isRayOccluded(World world, double x0, double y0, double z0,
                                  double x1, double y1, double z1,
                                  int targetX, int targetY, int targetZ,
                                  Collection<Location> allowedTargets) {
        try {
            int gx0 = (int) Math.floor(x0);
            int gy0 = (int) Math.floor(y0);
            int gz0 = (int) Math.floor(z0);

            if (gx0 == targetX && gy0 == targetY && gz0 == targetZ) {
                return false;
            }

            double dx = x1 - x0;
            double dy = y1 - y0;
            double dz = z1 - z0;

            int stepX = dx > 0 ? 1 : (dx < 0 ? -1 : 0);
            int stepY = dy > 0 ? 1 : (dy < 0 ? -1 : 0);
            int stepZ = dz > 0 ? 1 : (dz < 0 ? -1 : 0);

            double tDeltaX = (stepX != 0) ? Math.abs(1.0 / dx) : Double.MAX_VALUE;
            double tDeltaY = (stepY != 0) ? Math.abs(1.0 / dy) : Double.MAX_VALUE;
            double tDeltaZ = (stepZ != 0) ? Math.abs(1.0 / dz) : Double.MAX_VALUE;

            double tMaxX = (stepX > 0) ? ((gx0 + 1.0) - x0) * tDeltaX : ((stepX < 0) ? (x0 - gx0) * tDeltaX : Double.MAX_VALUE);
            double tMaxY = (stepY > 0) ? ((gy0 + 1.0) - y0) * tDeltaY : ((stepY < 0) ? (y0 - gy0) * tDeltaY : Double.MAX_VALUE);
            double tMaxZ = (stepZ > 0) ? ((gz0 + 1.0) - z0) * tDeltaZ : ((stepZ < 0) ? (z0 - gz0) * tDeltaZ : Double.MAX_VALUE);

            int currentX = gx0;
            int currentY = gy0;
            int currentZ = gz0;

            int maxSteps = (int) Math.ceil(maxDistance * 2.0);
            while (maxSteps-- > 0) {
                if (tMaxX < tMaxY) {
                    if (tMaxX < tMaxZ) {
                        currentX += stepX;
                        tMaxX += tDeltaX;
                    } else {
                        currentZ += stepZ;
                        tMaxZ += tDeltaZ;
                    }
                } else {
                    if (tMaxY < tMaxZ) {
                        currentY += stepY;
                        tMaxY += tDeltaY;
                    } else {
                        currentZ += stepZ;
                        tMaxZ += tDeltaZ;
                    }
                }

                // Successfully reached the target block
                if (currentX == targetX && currentY == targetY && currentZ == targetZ) {
                    return false;
                }
                if (allowedTargets != null && allowedTargets.contains(new Location(world, currentX, currentY, currentZ))) {
                    return false;
                }

                // On Folia, ensure voxel position is owned by the current region thread before reading world blocks
                if (PlatformScheduler.isFolia()) {
                    Location voxelLoc = new Location(world, currentX, currentY, currentZ);
                    if (!PlatformScheduler.isOwnedByCurrentRegion(voxelLoc)) {
                        return false; // Ray crossed region boundary; fail-open (consider not occluded)
                    }
                }

                // If chunk unloaded, consider occluded
                if (!world.isChunkLoaded(currentX >> 4, currentZ >> 4)) {
                    return true;
                }

                Block block = world.getBlockAt(currentX, currentY, currentZ);
                Material mat = block.getType();
                if (mat.isOccluding()) {
                    return true; // Occluded by solid, non-transparent block!
                }
            }

            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Checks whether any online player currently has this block instance rendered.
     * Used by animation tick loops to skip quaternion matrix updates for non-visible blocks.
     */
    public boolean isBlockVisibleToAnyPlayer(PlacedBlockInstance instance) {
        if (!enabled) return true;
        if (instance == null || instance.getDisplayEntityUuids().isEmpty()) return false;
        for (UUID uuid : instance.getDisplayEntityUuids()) {
            if (globallyVisibleDisplays.contains(uuid)) {
                return true;
            }
        }
        return false;
    }

    public void onBlockRemoved(PlacedBlockInstance instance) {
        if (instance == null) return;
        for (UUID uuid : instance.getDisplayEntityUuids()) {
            globallyVisibleDisplays.remove(uuid);
            for (Set<UUID> set : playerVisibleDisplays.values()) {
                set.remove(uuid);
            }
            for (Set<UUID> set : playerHiddenDisplays.values()) {
                set.remove(uuid);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        playerVisibleDisplays.remove(playerId);
        playerHiddenDisplays.remove(playerId);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        playerVisibleDisplays.remove(playerId);
        playerHiddenDisplays.remove(playerId);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getFrom().getWorld() != null && !event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            UUID playerId = event.getPlayer().getUniqueId();
            playerVisibleDisplays.remove(playerId);
            playerHiddenDisplays.remove(playerId);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getMaxRenderedPerPlayer() {
        return maxRenderedPerPlayer;
    }

    public double getMaxDistance() {
        return maxDistance;
    }

    public double getNearRadius() {
        return nearRadius;
    }

    public int getCheckIntervalTicks() {
        return checkIntervalTicks;
    }

    public int getRenderedBlockCountFor(Player player) {
        if (player == null) return 0;
        Set<UUID> visible = playerVisibleDisplays.get(player.getUniqueId());
        if (visible == null || visible.isEmpty()) return 0;
        int count = 0;
        for (PlacedBlockInstance inst : blockManager.getPlacedBlocks().values()) {
            for (UUID uuid : inst.getDisplayEntityUuids()) {
                if (visible.contains(uuid)) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    public int getGloballyRenderedBlockCount() {
        if (globallyVisibleDisplays.isEmpty()) return 0;
        int count = 0;
        for (PlacedBlockInstance inst : blockManager.getPlacedBlocks().values()) {
            for (UUID uuid : inst.getDisplayEntityUuids()) {
                if (globallyVisibleDisplays.contains(uuid)) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    private record CandidateBlock(PlacedBlockInstance instance, Location centerLoc, double dist, double score) {}
}
