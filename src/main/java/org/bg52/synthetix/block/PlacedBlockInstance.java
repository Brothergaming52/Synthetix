package org.bg52.synthetix.block;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.joml.Vector3i;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents an active, physically placed custom block instance in a world.
 * Holds location, facing, persistent state data, and dynamic animation/transformation controls.
 */
public class PlacedBlockInstance {

    private final Location location;
    private String blockId;
    private final UUID placerUuid;
    private BlockFace facing;
    private final List<UUID> displayEntityUuids;
    private final List<Vector3i> multiPartOffsets;
    private final Map<String, Object> stateData;
    private final long placedTimestamp;

    // Dynamic Animation & Kinematic Controls (per-instance)
    private volatile boolean animationPaused = false;
    private volatile double speedMultiplier = 1.0;
    private final Map<String, Double> partSpeedMultipliers = new ConcurrentHashMap<>();
    private final Map<String, Boolean> partPausedStates = new ConcurrentHashMap<>();
    private final Map<String, Float> partAngles = new ConcurrentHashMap<>();
    private final Map<String, Long> lastDispatchTicks = new ConcurrentHashMap<>();
    private final Set<String> restingParts = ConcurrentHashMap.newKeySet();
    private final Map<String, String> partModelOverrides = new ConcurrentHashMap<>();

    public PlacedBlockInstance(Location location, String blockId, UUID placerUuid, BlockFace facing) {
        this.location = location;
        this.blockId = blockId != null ? blockId.toLowerCase(Locale.ROOT) : "custom_block";
        this.placerUuid = placerUuid;
        this.facing = facing != null ? facing : BlockFace.NORTH;
        this.displayEntityUuids = new ArrayList<>();
        this.multiPartOffsets = new ArrayList<>();
        this.stateData = new ConcurrentHashMap<>();
        this.placedTimestamp = System.currentTimeMillis();
    }

    public Location getLocation() {
        return location;
    }

    public String getBlockId() {
        return blockId;
    }

    public void setBlockId(String blockId) {
        if (blockId != null) {
            this.blockId = blockId.toLowerCase(Locale.ROOT);
        }
    }

    public UUID getPlacerUuid() {
        return placerUuid;
    }

    public BlockFace getFacing() {
        return facing;
    }

    public void setFacing(BlockFace facing) {
        this.facing = facing != null ? facing : BlockFace.NORTH;
    }

    public List<UUID> getDisplayEntityUuids() {
        return displayEntityUuids;
    }

    public List<Vector3i> getMultiPartOffsets() {
        return multiPartOffsets;
    }

    public Map<String, Object> getStateData() {
        return stateData;
    }

    public long getPlacedTimestamp() {
        return placedTimestamp;
    }

    public int getChunkX() {
        return location.getBlockX() >> 4;
    }

    public int getChunkZ() {
        return location.getBlockZ() >> 4;
    }

    public long getChunkKey() {
        return (((long) getChunkX()) << 32) | (((long) getChunkZ()) & 0xFFFFFFFFL);
    }

    public void setState(String key, Object value) {
        if (key == null) return;
        if (value == null) {
            stateData.remove(key);
        } else {
            stateData.put(key, value);
        }
    }

    public Object getState(String key) {
        return key != null ? stateData.get(key) : null;
    }

    public <T> T getState(String key, Class<T> type) {
        if (key == null || type == null) return null;
        Object val = stateData.get(key);
        if (val != null && type.isInstance(val)) {
            return type.cast(val);
        }
        return null;
    }

    public boolean hasState(String key) {
        return key != null && stateData.containsKey(key);
    }

    // =========================================================================
    // DYNAMIC ANIMATION & KINEMATICS API
    // =========================================================================

    public boolean isAnimationPaused() {
        return animationPaused;
    }

    public void setAnimationPaused(boolean animationPaused) {
        this.animationPaused = animationPaused;
        if (!animationPaused) {
            restingParts.clear();
        }
    }

    public double getSpeedMultiplier() {
        return speedMultiplier;
    }

    public void setSpeedMultiplier(double speedMultiplier) {
        this.speedMultiplier = speedMultiplier;
        if (Math.abs(speedMultiplier) > 0.0001) {
            restingParts.clear();
        }
    }

    public double getPartSpeedMultiplier(String partId) {
        if (partId == null) return 1.0;
        return partSpeedMultipliers.getOrDefault(partId.toLowerCase(Locale.ROOT), 1.0);
    }

    public void setPartSpeedMultiplier(String partId, double multiplier) {
        if (partId == null) return;
        partSpeedMultipliers.put(partId.toLowerCase(Locale.ROOT), multiplier);
        if (Math.abs(multiplier) > 0.0001) {
            restingParts.remove(partId.toLowerCase(Locale.ROOT));
        }
    }

    public boolean isPartPaused(String partId) {
        if (partId == null) return false;
        return partPausedStates.getOrDefault(partId.toLowerCase(Locale.ROOT), false);
    }

    public void setPartPaused(String partId, boolean paused) {
        if (partId == null) return;
        partPausedStates.put(partId.toLowerCase(Locale.ROOT), paused);
        if (!paused) {
            restingParts.remove(partId.toLowerCase(Locale.ROOT));
        }
    }

    public float getPartAngle(String partId) {
        if (partId == null) return 0.0f;
        return partAngles.getOrDefault(partId.toLowerCase(Locale.ROOT), 0.0f);
    }

    public void setPartAngle(String partId, float angle) {
        if (partId == null) return;
        partAngles.put(partId.toLowerCase(Locale.ROOT), angle);
    }

    public long getLastDispatchTick(String partId) {
        if (partId == null) return 0L;
        return lastDispatchTicks.getOrDefault(partId.toLowerCase(Locale.ROOT), 0L);
    }

    public void setLastDispatchTick(String partId, long tick) {
        if (partId == null) return;
        lastDispatchTicks.put(partId.toLowerCase(Locale.ROOT), tick);
    }

    public boolean isPartResting(String partId) {
        if (partId == null) return false;
        return restingParts.contains(partId.toLowerCase(Locale.ROOT));
    }

    public void setPartResting(String partId, boolean resting) {
        if (partId == null) return;
        String key = partId.toLowerCase(Locale.ROOT);
        if (resting) {
            restingParts.add(key);
        } else {
            restingParts.remove(key);
        }
    }

    public String getPartModelOverride(String partId) {
        if (partId == null) return null;
        return partModelOverrides.get(partId.toLowerCase(Locale.ROOT));
    }

    public void setPartModelOverride(String partId, String itemModel) {
        if (partId == null) return;
        if (itemModel == null) {
            partModelOverrides.remove(partId.toLowerCase(Locale.ROOT));
        } else {
            partModelOverrides.put(partId.toLowerCase(Locale.ROOT), itemModel);
        }
    }

    public void clearAnimationState() {
        restingParts.clear();
        partAngles.clear();
        lastDispatchTicks.clear();
    }

    /**
     * Resolves all world block locations occupied by this block (including multi-part footprints).
     */
    public List<Location> getAllOccupiedLocations() {
        List<Location> list = new ArrayList<>();
        list.add(location);
        for (Vector3i offset : multiPartOffsets) {
            list.add(location.clone().add(offset.x, offset.y, offset.z));
        }
        return list;
    }
}
