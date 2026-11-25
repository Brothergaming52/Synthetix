package org.bg52.synthetix.storage;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Vector3i;

import java.util.*;

/**
 * Data carrier representing a placed custom block instance in the database.
 */
public class PlacedBlockRecord {

    private static final Gson GSON = new Gson();

    private String world;
    private int x;
    private int y;
    private int z;
    private String blockId;
    private UUID placerUuid;
    private String facing;
    private List<UUID> displayEntityUuids;
    private List<Vector3i> multiPartOffsets;
    private Map<String, Object> customData;

    public PlacedBlockRecord() {
        this.displayEntityUuids = new ArrayList<>();
        this.multiPartOffsets = new ArrayList<>();
        this.customData = new HashMap<>();
    }

    public PlacedBlockRecord(String world, int x, int y, int z, String blockId, UUID placerUuid, String facing) {
        this();
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.blockId = blockId;
        this.placerUuid = placerUuid;
        this.facing = facing != null ? facing : "NORTH";
    }

    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getZ() {
        return z;
    }

    public void setZ(int z) {
        this.z = z;
    }

    public String getBlockId() {
        return blockId;
    }

    public void setBlockId(String blockId) {
        this.blockId = blockId;
    }

    public UUID getPlacerUuid() {
        return placerUuid;
    }

    public void setPlacerUuid(UUID placerUuid) {
        this.placerUuid = placerUuid;
    }

    public String getFacing() {
        return facing;
    }

    public void setFacing(String facing) {
        this.facing = facing;
    }

    public List<UUID> getDisplayEntityUuids() {
        return displayEntityUuids;
    }

    public void setDisplayEntityUuids(List<UUID> displayEntityUuids) {
        this.displayEntityUuids = displayEntityUuids != null ? displayEntityUuids : new ArrayList<>();
    }

    public List<Vector3i> getMultiPartOffsets() {
        return multiPartOffsets;
    }

    public void setMultiPartOffsets(List<Vector3i> multiPartOffsets) {
        this.multiPartOffsets = multiPartOffsets != null ? multiPartOffsets : new ArrayList<>();
    }

    public Map<String, Object> getCustomData() {
        return customData;
    }

    public void setCustomData(Map<String, Object> customData) {
        this.customData = customData != null ? customData : new HashMap<>();
    }

    /**
     * Serializes extra payload attributes (display entities, multipart offsets, custom metadata) to JSON.
     */
    public String toDataJson() {
        JsonObject obj = new JsonObject();

        if (!displayEntityUuids.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (UUID u : displayEntityUuids) {
                arr.add(u.toString());
            }
            obj.add("displayEntities", arr);
        }

        if (!multiPartOffsets.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (Vector3i v : multiPartOffsets) {
                arr.add(v.x + "," + v.y + "," + v.z);
            }
            obj.add("multiPartOffsets", arr);
        }

        if (!customData.isEmpty()) {
            obj.add("customData", GSON.toJsonTree(customData));
        }

        return GSON.toJson(obj);
    }

    /**
     * Parses payload attributes from JSON string.
     */
    public void fromDataJson(String json) {
        if (json == null || json.trim().isEmpty()) return;
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

            if (obj.has("displayEntities")) {
                displayEntityUuids.clear();
                for (JsonElement el : obj.getAsJsonArray("displayEntities")) {
                    try {
                        displayEntityUuids.add(UUID.fromString(el.getAsString()));
                    } catch (Exception ignored) {}
                }
            }

            if (obj.has("multiPartOffsets")) {
                multiPartOffsets.clear();
                for (JsonElement el : obj.getAsJsonArray("multiPartOffsets")) {
                    String[] parts = el.getAsString().split(",");
                    if (parts.length == 3) {
                        try {
                            int ox = Integer.parseInt(parts[0]);
                            int oy = Integer.parseInt(parts[1]);
                            int oz = Integer.parseInt(parts[2]);
                            multiPartOffsets.add(new Vector3i(ox, oy, oz));
                        } catch (Exception ignored) {}
                    }
                }
            }

            if (obj.has("customData") && obj.get("customData").isJsonObject()) {
                customData.clear();
                Map<?, ?> map = GSON.fromJson(obj.get("customData"), Map.class);
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    customData.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * Serializes a collection of placed block records into a compact Chunk PDC JSON string.
     */
    public static String serializeChunkRecords(Collection<PlacedBlockRecord> records) {
        if (records == null || records.isEmpty()) return "";
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("timestamp", System.currentTimeMillis());

        JsonArray array = new JsonArray();
        for (PlacedBlockRecord r : records) {
            JsonObject obj = new JsonObject();
            obj.addProperty("x", r.getX());
            obj.addProperty("y", r.getY());
            obj.addProperty("z", r.getZ());
            obj.addProperty("id", r.getBlockId());
            if (r.getPlacerUuid() != null) obj.addProperty("placer", r.getPlacerUuid().toString());
            obj.addProperty("facing", r.getFacing());
            String dataJson = r.toDataJson();
            if (dataJson != null && !dataJson.isEmpty() && !dataJson.equals("{}")) {
                obj.addProperty("data", dataJson);
            }
            array.add(obj);
        }
        root.add("blocks", array);
        return GSON.toJson(root);
    }

    /**
     * Deserializes a collection of placed block records from a Chunk PDC JSON string.
     */
    public static List<PlacedBlockRecord> deserializeChunkRecords(String world, String json) {
        List<PlacedBlockRecord> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return list;
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("blocks") && root.get("blocks").isJsonArray()) {
                for (JsonElement el : root.getAsJsonArray("blocks")) {
                    JsonObject obj = el.getAsJsonObject();
                    int x = obj.get("x").getAsInt();
                    int y = obj.get("y").getAsInt();
                    int z = obj.get("z").getAsInt();
                    String id = obj.get("id").getAsString();
                    UUID placer = obj.has("placer") && !obj.get("placer").isJsonNull() ? UUID.fromString(obj.get("placer").getAsString()) : null;
                    String facing = obj.has("facing") ? obj.get("facing").getAsString() : "NORTH";

                    PlacedBlockRecord r = new PlacedBlockRecord(world, x, y, z, id, placer, facing);
                    if (obj.has("data")) {
                        r.fromDataJson(obj.get("data").getAsString());
                    }
                    list.add(r);
                }
            }
        } catch (Exception ignored) {}
        return list;
    }
}

