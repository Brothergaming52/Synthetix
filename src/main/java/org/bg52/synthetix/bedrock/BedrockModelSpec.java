package org.bg52.synthetix.bedrock;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.EquipmentSlot;
import org.joml.Vector3f;

import java.util.*;

/**
 * Generalized Bedrock specification for Custom Blocks and Custom Entities.
 * Configures the Bedrock visual proxy (invisible ArmorStand with head equipment),
 * attachable identifier, offsets, and dynamic state-to-item mapping.
 */
public class BedrockModelSpec {

    private boolean enabled = true;
    private Material defaultMaterial = Material.CARROT_ON_A_STICK;
    private int defaultCustomModelData = 0;
    private String defaultItemModel = null;
    private String attachableIdentifier = null;
    private Vector3f headOffset = new Vector3f(0.0f, 0.0f, 0.0f);
    private float headYawOffset = 0.0f;
    private EquipmentSlot slot = EquipmentSlot.CHEST;
    private boolean small = false;
    private final Map<String, BedrockStateSpec> stateSpecs = new HashMap<>();

    public BedrockModelSpec() {}

    public BedrockModelSpec(Material defaultMaterial, int defaultCustomModelData, String defaultItemModel, String attachableIdentifier) {
        this.defaultMaterial = defaultMaterial != null ? defaultMaterial : Material.CARROT_ON_A_STICK;
        this.defaultCustomModelData = defaultCustomModelData;
        this.defaultItemModel = defaultItemModel;
        this.attachableIdentifier = attachableIdentifier;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Material getDefaultMaterial() {
        return defaultMaterial;
    }

    public void setDefaultMaterial(Material defaultMaterial) {
        this.defaultMaterial = defaultMaterial != null ? defaultMaterial : Material.CARROT_ON_A_STICK;
    }

    public int getDefaultCustomModelData() {
        return defaultCustomModelData;
    }

    public void setDefaultCustomModelData(int defaultCustomModelData) {
        this.defaultCustomModelData = defaultCustomModelData;
    }

    public String getDefaultItemModel() {
        return defaultItemModel;
    }

    public void setDefaultItemModel(String defaultItemModel) {
        this.defaultItemModel = defaultItemModel;
    }

    public String getAttachableIdentifier() {
        return attachableIdentifier;
    }

    public void setAttachableIdentifier(String attachableIdentifier) {
        this.attachableIdentifier = attachableIdentifier;
    }

    public Vector3f getHeadOffset() {
        return headOffset;
    }

    public void setHeadOffset(Vector3f headOffset) {
        this.headOffset = headOffset != null ? headOffset : new Vector3f(0.0f, -0.65f, 0.0f);
    }

    public float getHeadYawOffset() {
        return headYawOffset;
    }

    public void setHeadYawOffset(float headYawOffset) {
        this.headYawOffset = headYawOffset;
    }

    public EquipmentSlot getSlot() {
        return slot;
    }

    public void setSlot(EquipmentSlot slot) {
        this.slot = slot != null ? slot : EquipmentSlot.CHEST;
    }

    public boolean isSmall() {
        return small;
    }

    public void setSmall(boolean small) {
        this.small = small;
    }

    public Vector3f getProxyOffset() {
        return headOffset;
    }

    public void setProxyOffset(Vector3f offset) {
        setHeadOffset(offset);
    }

    public float getProxyYawOffset() {
        return headYawOffset;
    }

    public void setProxyYawOffset(float yaw) {
        setHeadYawOffset(yaw);
    }

    public Map<String, BedrockStateSpec> getStateSpecs() {
        return stateSpecs;
    }

    public BedrockStateSpec getStateSpec(String stateName) {
        if (stateName == null) return null;
        return stateSpecs.get(stateName.toLowerCase(Locale.ROOT));
    }

    public void addStateSpec(BedrockStateSpec spec) {
        if (spec != null) {
            stateSpecs.put(spec.getStateName().toLowerCase(Locale.ROOT), spec);
        }
    }

    public void removeStateSpec(String stateName) {
        if (stateName != null) {
            stateSpecs.remove(stateName.toLowerCase(Locale.ROOT));
        }
    }

    public BedrockStateSpec resolveState(String stateName) {
        if (stateName != null) {
            BedrockStateSpec spec = stateSpecs.get(stateName.toLowerCase(Locale.ROOT));
            if (spec != null) return spec;
        }
        // Fallback to "idle" or "default" or this spec's default values
        BedrockStateSpec idle = stateSpecs.get("idle");
        if (idle != null) return idle;
        BedrockStateSpec def = stateSpecs.get("default");
        if (def != null) return def;

        return new BedrockStateSpec("default", defaultMaterial, defaultCustomModelData, defaultItemModel, attachableIdentifier);
    }

    public void saveToConfig(ConfigurationSection sec) {
        if (sec == null) return;
        sec.set("enabled", enabled);
        sec.set("default-material", defaultMaterial.name());
        sec.set("default-cmd", defaultCustomModelData);
        if (defaultItemModel != null) sec.set("default-item-model", defaultItemModel);
        if (attachableIdentifier != null) sec.set("attachable-identifier", attachableIdentifier);
        sec.set("head-offset.x", headOffset.x);
        sec.set("head-offset.y", headOffset.y);
        sec.set("head-offset.z", headOffset.z);
        sec.set("head-yaw-offset", headYawOffset);
        sec.set("slot", slot.name());
        sec.set("small", small);

        if (!stateSpecs.isEmpty()) {
            ConfigurationSection statesSec = sec.createSection("states");
            for (Map.Entry<String, BedrockStateSpec> entry : stateSpecs.entrySet()) {
                ConfigurationSection sSec = statesSec.createSection(entry.getKey());
                entry.getValue().saveToConfig(sSec);
            }
        }
    }

    public static BedrockModelSpec loadFromConfig(ConfigurationSection sec) {
        if (sec == null) return null;
        BedrockModelSpec spec = new BedrockModelSpec();
        spec.setEnabled(sec.getBoolean("enabled", true));
        String matStr = sec.getString("default-material");
        if (matStr != null) {
            Material m = Material.matchMaterial(matStr);
            if (m != null) spec.setDefaultMaterial(m);
        }
        spec.setDefaultCustomModelData(sec.getInt("default-cmd", 0));
        spec.setDefaultItemModel(sec.getString("default-item-model"));
        spec.setAttachableIdentifier(sec.getString("attachable-identifier"));

        float ox = (float) sec.getDouble("head-offset.x", 0.0);
        float oy = (float) sec.getDouble("head-offset.y", 0.0);
        float oz = (float) sec.getDouble("head-offset.z", 0.0);
        spec.setHeadOffset(new Vector3f(ox, oy, oz));
        spec.setHeadYawOffset((float) sec.getDouble("head-yaw-offset", 0.0));
        String slotStr = sec.getString("slot");
        if (slotStr != null) {
            try {
                spec.setSlot(EquipmentSlot.valueOf(slotStr.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {}
        }
        spec.setSmall(sec.getBoolean("small", false));

        ConfigurationSection statesSec = sec.getConfigurationSection("states");
        if (statesSec != null) {
            for (String key : statesSec.getKeys(false)) {
                ConfigurationSection sSec = statesSec.getConfigurationSection(key);
                if (sSec != null) {
                    BedrockStateSpec stateSpec = BedrockStateSpec.loadFromConfig(key, sSec);
                    if (stateSpec != null) {
                        spec.addStateSpec(stateSpec);
                    }
                }
            }
        }
        return spec;
    }

    public BedrockModelSpec copy() {
        BedrockModelSpec copy = new BedrockModelSpec(defaultMaterial, defaultCustomModelData, defaultItemModel, attachableIdentifier);
        copy.setEnabled(this.enabled);
        copy.setSlot(this.slot);
        copy.setSmall(this.small);
        copy.setHeadOffset(new Vector3f(this.headOffset));
        copy.setHeadYawOffset(this.headYawOffset);
        for (Map.Entry<String, BedrockStateSpec> entry : this.stateSpecs.entrySet()) {
            copy.addStateSpec(entry.getValue().copy());
        }
        return copy;
    }
}

