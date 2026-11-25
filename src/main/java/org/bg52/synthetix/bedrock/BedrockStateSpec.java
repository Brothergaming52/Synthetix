package org.bg52.synthetix.bedrock;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Locale;

/**
 * Encapsulates a single Bedrock animation/visual state specification.
 * Used for dynamic state swapping on Bedrock entity proxies and blocks.
 */
public class BedrockStateSpec {

    private String stateName;
    private Material itemMaterial = Material.CARROT_ON_A_STICK;
    private int customModelData = 0;
    private String itemModel = null;
    private String attachableIdentifier = null;
    private EquipmentSlot slot = null;

    public BedrockStateSpec(String stateName) {
        this.stateName = stateName != null ? stateName.toLowerCase(Locale.ROOT) : "default";
    }

    public BedrockStateSpec(String stateName, Material itemMaterial, int customModelData, String itemModel, String attachableIdentifier) {
        this.stateName = stateName != null ? stateName.toLowerCase(Locale.ROOT) : "default";
        this.itemMaterial = itemMaterial != null ? itemMaterial : Material.CARROT_ON_A_STICK;
        this.customModelData = customModelData;
        this.itemModel = itemModel;
        this.attachableIdentifier = attachableIdentifier;
    }

    public String getStateName() {
        return stateName;
    }

    public void setStateName(String stateName) {
        this.stateName = stateName != null ? stateName.toLowerCase(Locale.ROOT) : "default";
    }

    public Material getItemMaterial() {
        return itemMaterial;
    }

    public void setItemMaterial(Material itemMaterial) {
        this.itemMaterial = itemMaterial != null ? itemMaterial : Material.CARROT_ON_A_STICK;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public void setCustomModelData(int customModelData) {
        this.customModelData = customModelData;
    }

    public String getItemModel() {
        return itemModel;
    }

    public void setItemModel(String itemModel) {
        this.itemModel = itemModel;
    }

    public String getAttachableIdentifier() {
        return attachableIdentifier;
    }

    public void setAttachableIdentifier(String attachableIdentifier) {
        this.attachableIdentifier = attachableIdentifier;
    }

    public EquipmentSlot getSlot() {
        return slot;
    }

    public void setSlot(EquipmentSlot slot) {
        this.slot = slot;
    }

    public void saveToConfig(ConfigurationSection sec) {
        if (sec == null) return;
        sec.set("item-material", itemMaterial.name());
        sec.set("custom-model-data", customModelData);
        if (itemModel != null) sec.set("item-model", itemModel);
        if (attachableIdentifier != null) sec.set("attachable-identifier", attachableIdentifier);
        if (slot != null) sec.set("slot", slot.name());
    }

    public static BedrockStateSpec loadFromConfig(String stateName, ConfigurationSection sec) {
        if (sec == null) return null;
        BedrockStateSpec spec = new BedrockStateSpec(stateName);
        String matStr = sec.getString("item-material");
        if (matStr != null) {
            Material m = Material.matchMaterial(matStr);
            if (m != null) spec.setItemMaterial(m);
        }
        spec.setCustomModelData(sec.getInt("custom-model-data", 0));
        spec.setItemModel(sec.getString("item-model"));
        spec.setAttachableIdentifier(sec.getString("attachable-identifier"));
        String slotStr = sec.getString("slot");
        if (slotStr != null) {
            try {
                spec.setSlot(EquipmentSlot.valueOf(slotStr.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {}
        }
        return spec;
    }

    public BedrockStateSpec copy() {
        BedrockStateSpec cp = new BedrockStateSpec(stateName, itemMaterial, customModelData, itemModel, attachableIdentifier);
        cp.setSlot(this.slot);
        return cp;
    }
}

