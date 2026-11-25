package org.bg52.synthetix.block;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.item.ItemDefinition;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Vector3f;
import org.joml.Vector3i;

import java.util.*;

/**
 * Super-modular, comprehensive definition of a custom Synthetix Block.
 * Implements the Dual-Layer Virtual Block Engine:
 * - Visual Layer: Unlimited CustomModelData, ItemDisplay / BlockDisplay, multi-model animated composite parts.
 * - Physics Layer: Vanilla collision proxy blocks (Barrier, Void, Slabs, Shulkers) and multi-part 3x3 footprints.
 * - Mining, Destruction, Blast, Flammability, World Generation, and Audio pipeline.
 */
public class CustomBlockDefinition {

    public static final NamespacedKey BLOCK_ID_KEY = new NamespacedKey("synthetix", "block_id");
    public static final NamespacedKey ITEM_ID_KEY = new NamespacedKey("synthetix", "item_id");

    private final String id;
    private String owningPlugin;
    private String displayName;
    private List<String> lore;
    private Material baseMaterial; // Physical proxy block placed in world (BARRIER, STRUCTURE_VOID, NOTE_BLOCK, etc.)
    private Material itemMaterial; // Item representation held in player inventory
    private int customModelData;
    private String itemModel; // Modern Data Component item_model (e.g. "synthetix:gearbox" or "synthetix:block/gearbox")
    private RenderingMethod renderingMethod;
    private Vector3f displayScale;
    private Vector3f displayTranslation;
    private Vector3f displayRotation; // Euler angles in degrees
    private Integer brightness; // 0 to 15, or null
    private String glowColorHex;
    private boolean lockRotation; // When true, placement facing is fixed to NORTH (unaffected by player look angle)

    // Multi-Model & Animation
    private boolean multiModel;
    private List<BlockModelPart> modelParts;

    // Multi-Part Large Block (e.g. 1x1, 2x2, 3x3 footprints)
    private Vector3i multiPartSize;
    private Map<String, Material> partBaseBlocks; // "dx,dy,dz" -> Material

    // Bedrock / Geyser Compatibility
    private org.bg52.synthetix.bedrock.BedrockModelSpec bedrockSpec;

    // Sub-systems
    private BlockPhysicsSpec physics;
    private BlockMiningSpec mining;
    private BlockInteractionSpec interaction;
    private BlockWorldGenSpec worldGen;
    private Map<String, Object> customData;

    public CustomBlockDefinition(String id) {
        this.id = id != null ? id.toLowerCase(Locale.ROOT) : "custom_block";
        this.owningPlugin = "synthetix";
        this.displayName = "<gold>" + TextUtil.prettify(this.id) + "</gold>";
        this.lore = new ArrayList<>();
        this.baseMaterial = Material.BARRIER;
        this.itemMaterial = Material.PAPER;
        this.customModelData = 0;
        this.renderingMethod = RenderingMethod.ITEM_DISPLAY;
        this.displayScale = new Vector3f(1.0f, 1.0f, 1.0f);
        this.displayTranslation = new Vector3f(0.0f, 0.0f, 0.0f);
        this.displayRotation = new Vector3f(0.0f, 0.0f, 0.0f);
        this.brightness = null;
        this.glowColorHex = null;
        this.lockRotation = false;

        this.multiModel = false;
        this.modelParts = new ArrayList<>();

        this.multiPartSize = new Vector3i(1, 1, 1);
        this.partBaseBlocks = new HashMap<>();

        this.physics = new BlockPhysicsSpec();
        this.mining = new BlockMiningSpec();
        this.interaction = new BlockInteractionSpec();
        this.worldGen = new BlockWorldGenSpec();
        this.customData = new HashMap<>();
    }

    public String getId() {
        return id;
    }

    public String getOwningPlugin() {
        return owningPlugin;
    }

    public void setOwningPlugin(String owningPlugin) {
        this.owningPlugin = owningPlugin != null ? owningPlugin : "synthetix";
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName != null ? displayName : id;
    }

    public List<String> getLore() {
        return lore;
    }

    public void setLore(List<String> lore) {
        this.lore = lore != null ? lore : new ArrayList<>();
    }

    public Material getBaseMaterial() {
        return baseMaterial;
    }

    public void setBaseMaterial(Material baseMaterial) {
        this.baseMaterial = baseMaterial != null ? baseMaterial : Material.BARRIER;
    }

    public Material getItemMaterial() {
        return itemMaterial;
    }

    public void setItemMaterial(Material itemMaterial) {
        this.itemMaterial = itemMaterial != null ? itemMaterial : Material.PAPER;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public void setCustomModelData(int customModelData) {
        this.customModelData = customModelData;
    }

    public String getItemModel() {
        return itemModel != null && !itemModel.trim().isEmpty() ? itemModel : "synthetix:" + id;
    }

    public void setItemModel(String itemModel) {
        if (itemModel != null && !itemModel.trim().isEmpty()) {
            this.itemModel = BlockModelPart.normalizeModelKey(itemModel);
        } else {
            this.itemModel = null;
        }
    }

    public RenderingMethod getRenderingMethod() {
        return renderingMethod;
    }

    public void setRenderingMethod(RenderingMethod renderingMethod) {
        this.renderingMethod = renderingMethod != null ? renderingMethod : RenderingMethod.ITEM_DISPLAY;
    }

    public Vector3f getDisplayScale() {
        return displayScale;
    }

    public void setDisplayScale(Vector3f displayScale) {
        this.displayScale = displayScale != null ? displayScale : new Vector3f(1.1f, 1.1f, 1.1f);
    }

    public Vector3f getDisplayTranslation() {
        return displayTranslation;
    }

    public void setDisplayTranslation(Vector3f displayTranslation) {
        this.displayTranslation = displayTranslation != null ? displayTranslation : new Vector3f(0.0f, 0.0f, 0.0f);
    }

    public Vector3f getDisplayRotation() {
        return displayRotation;
    }

    public void setDisplayRotation(Vector3f displayRotation) {
        this.displayRotation = displayRotation != null ? displayRotation : new Vector3f(0.0f, 0.0f, 0.0f);
    }

    public Integer getBrightness() {
        return brightness;
    }

    public void setBrightness(Integer brightness) {
        this.brightness = brightness != null ? Math.max(0, Math.min(15, brightness)) : null;
    }

    public String getGlowColorHex() {
        return glowColorHex;
    }

    public void setGlowColorHex(String glowColorHex) {
        this.glowColorHex = glowColorHex;
    }

    public boolean isLockRotation() {
        return lockRotation;
    }

    public void setLockRotation(boolean lockRotation) {
        this.lockRotation = lockRotation;
    }

    public boolean isMultiModel() {
        return multiModel;
    }

    public void setMultiModel(boolean multiModel) {
        this.multiModel = multiModel;
    }

    public List<BlockModelPart> getModelParts() {
        return modelParts;
    }

    public void setModelParts(List<BlockModelPart> modelParts) {
        this.modelParts = modelParts != null ? modelParts : new ArrayList<>();
    }

    public Vector3i getMultiPartSize() {
        return multiPartSize;
    }

    public void setMultiPartSize(Vector3i multiPartSize) {
        this.multiPartSize = multiPartSize != null ? multiPartSize : new Vector3i(1, 1, 1);
    }

    public boolean isMultiPart() {
        return multiPartSize.x > 1 || multiPartSize.y > 1 || multiPartSize.z > 1;
    }

    public Map<String, Material> getPartBaseBlocks() {
        return partBaseBlocks;
    }

    public void setPartBaseBlocks(Map<String, Material> partBaseBlocks) {
        this.partBaseBlocks = partBaseBlocks != null ? partBaseBlocks : new HashMap<>();
    }

    public Material getPartBaseBlock(int dx, int dy, int dz) {
        String key = dx + "," + dy + "," + dz;
        Material mat = partBaseBlocks.get(key);
        return mat != null ? mat : baseMaterial;
    }

    public void setPartBaseBlock(int dx, int dy, int dz, Material mat) {
        String key = dx + "," + dy + "," + dz;
        if (mat == null || mat == baseMaterial) {
            partBaseBlocks.remove(key);
        } else {
            partBaseBlocks.put(key, mat);
        }
    }

    public BlockPhysicsSpec getPhysics() {
        return physics;
    }

    public void setPhysics(BlockPhysicsSpec physics) {
        this.physics = physics != null ? physics : new BlockPhysicsSpec();
    }

    public BlockMiningSpec getMining() {
        return mining;
    }

    public void setMining(BlockMiningSpec mining) {
        this.mining = mining != null ? mining : new BlockMiningSpec();
    }

    public BlockInteractionSpec getInteraction() {
        return interaction;
    }

    public void setInteraction(BlockInteractionSpec interaction) {
        this.interaction = interaction != null ? interaction : new BlockInteractionSpec();
    }

    public BlockWorldGenSpec getWorldGen() {
        return worldGen;
    }

    public void setWorldGen(BlockWorldGenSpec worldGen) {
        this.worldGen = worldGen != null ? worldGen : new BlockWorldGenSpec();
    }

    public Map<String, Object> getCustomData() {
        return customData;
    }

    public void setCustomData(Map<String, Object> customData) {
        this.customData = customData != null ? customData : new HashMap<>();
    }

    /**
     * Constructs a physical ItemStack representation of this custom block
     * suitable for inventory holding, crafting, giving, or world placement.
     */
    /**
     * Constructs a physical ItemStack representation of this custom block,
     * delegating to the mirrored ItemDefinition if present for rich components, lore and glint.
     */
    public ItemStack buildBlockItemStack(int amount) {
        SynthetixPlugin plugin = SynthetixPlugin.getPlugin(SynthetixPlugin.class);
        if (plugin != null && plugin.getItemRegistry() != null) {
            ItemDefinition itemDef = plugin.getItemRegistry().get(this.id);
            if (itemDef != null) {
                syncFromItemDefinition(itemDef);
                ItemStack stack = itemDef.buildItemStack();
                stack.setAmount(Math.max(1, amount));
                ItemMeta meta = stack.getItemMeta();
                if (meta != null) {
                    PersistentDataContainer pdc = meta.getPersistentDataContainer();
                    pdc.set(BLOCK_ID_KEY, PersistentDataType.STRING, this.id);
                    pdc.set(ITEM_ID_KEY, PersistentDataType.STRING, this.id);
                    stack.setItemMeta(meta);
                }
                return stack;
            }
        }

        Material mat = org.bg52.synthetix.util.MaterialUtil.toItemMaterial(itemMaterial);
        ItemStack stack = new ItemStack(mat, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.parse(displayName));
            List<Component> compLore = new ArrayList<>();
            for (String l : lore) {
                compLore.add(TextUtil.parse(l));
            }
            compLore.add(Component.empty());
            compLore.add(TextUtil.parse("<dark_gray>Custom Block: " + id + "</dark_gray>"));
            meta.lore(compLore);

            // Modern item_model component (1.21.2+ / 26.x) and/or customModelData (1.20.6)
            org.bg52.synthetix.item.component.ComponentHandler.applyItemModel(meta, itemModel, customModelData > 0 ? customModelData : null);

            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(BLOCK_ID_KEY, PersistentDataType.STRING, id);
            pdc.set(ITEM_ID_KEY, PersistentDataType.STRING, id);

            stack.setItemMeta(meta);
        }
        return stack;
    }

    public void syncFromItemDefinition(ItemDefinition itemDef) {
        if (itemDef == null) return;
        if (itemDef.getDisplayName() != null) {
            this.displayName = itemDef.getDisplayName();
        }
        if (itemDef.getMaterial() != null) {
            this.itemMaterial = itemDef.getMaterial();
        }
        if (itemDef.getLore() != null) {
            this.lore = new ArrayList<>(itemDef.getLore());
        }
        if (itemDef.getCustomModelData() != null) {
            this.customModelData = itemDef.getCustomModelData();
        }
        if (itemDef.getItemModel() != null) {
            this.itemModel = itemDef.getItemModel();
        }
    }

    public org.bg52.synthetix.bedrock.BedrockModelSpec getBedrockSpec() {
        return bedrockSpec;
    }

    public void setBedrockSpec(org.bg52.synthetix.bedrock.BedrockModelSpec bedrockSpec) {
        this.bedrockSpec = bedrockSpec;
    }

    public void saveToConfig(ConfigurationSection sec) {
        sec.set("id", id);
        sec.set("owning-plugin", owningPlugin);
        sec.set("display-name", displayName);
        sec.set("lore", lore);
        sec.set("base-material", baseMaterial.name());
        sec.set("item-material", itemMaterial.name());
        sec.set("custom-model-data", customModelData);
        if (itemModel != null) sec.set("item-model", itemModel);
        sec.set("rendering-method", renderingMethod.name());
        sec.set("display-scale.x", displayScale.x);
        sec.set("display-scale.y", displayScale.y);
        sec.set("display-scale.z", displayScale.z);
        sec.set("display-translation.x", displayTranslation.x);
        sec.set("display-translation.y", displayTranslation.y);
        sec.set("display-translation.z", displayTranslation.z);
        sec.set("display-rotation.pitch", displayRotation.x);
        sec.set("display-rotation.yaw", displayRotation.y);
        sec.set("display-rotation.roll", displayRotation.z);
        if (brightness != null) sec.set("brightness", brightness);
        if (glowColorHex != null) sec.set("glow-color", glowColorHex);
        sec.set("lock-rotation", lockRotation);

        sec.set("multi-model", multiModel);
        if (!modelParts.isEmpty()) {
            ConfigurationSection partsSec = sec.createSection("model-parts");
            for (int i = 0; i < modelParts.size(); i++) {
                modelParts.get(i).saveToConfig(partsSec.createSection("part-" + i));
            }
        }

        sec.set("multi-part-size.x", multiPartSize.x);
        sec.set("multi-part-size.y", multiPartSize.y);
        sec.set("multi-part-size.z", multiPartSize.z);
        if (!partBaseBlocks.isEmpty()) {
            ConfigurationSection pbbSec = sec.createSection("part-base-blocks");
            for (Map.Entry<String, Material> entry : partBaseBlocks.entrySet()) {
                pbbSec.set(entry.getKey(), entry.getValue().name());
            }
        }

        physics.saveToConfig(sec.createSection("physics"));
        mining.saveToConfig(sec.createSection("mining"));
        interaction.saveToConfig(sec.createSection("interaction"));
        worldGen.saveToConfig(sec.createSection("world-gen"));
        if (bedrockSpec != null) {
            bedrockSpec.saveToConfig(sec.createSection("bedrock"));
        }
    }

    public static CustomBlockDefinition loadFromConfig(ConfigurationSection sec) {
        if (sec == null) return null;
        String id = sec.getString("id");
        if (id == null) return null;

        CustomBlockDefinition def = new CustomBlockDefinition(id);
        def.setOwningPlugin(sec.getString("owning-plugin", "synthetix"));
        def.setDisplayName(sec.getString("display-name", id));
        def.setLore(sec.getStringList("lore"));

        String baseMatStr = sec.getString("base-material", "BARRIER");
        Material baseMat = Material.matchMaterial(baseMatStr);
        def.setBaseMaterial(baseMat != null ? baseMat : Material.BARRIER);

        String itemMatStr = sec.getString("item-material", "PAPER");
        Material itemMat = Material.matchMaterial(itemMatStr);
        def.setItemMaterial(itemMat != null ? itemMat : Material.PAPER);

        def.setCustomModelData(sec.getInt("custom-model-data", 0));
        def.setItemModel(sec.getString("item-model"));

        String methodStr = sec.getString("rendering-method", "ITEM_DISPLAY");
        try {
            def.setRenderingMethod(RenderingMethod.valueOf(methodStr.toUpperCase(Locale.ROOT)));
        } catch (Exception ignored) {
            def.setRenderingMethod(RenderingMethod.ITEM_DISPLAY);
        }

        float sx = (float) sec.getDouble("display-scale.x", 1.0);
        float sy = (float) sec.getDouble("display-scale.y", 1.0);
        float sz = (float) sec.getDouble("display-scale.z", 1.0);
        def.setDisplayScale(new Vector3f(sx, sy, sz));

        float tx = (float) sec.getDouble("display-translation.x", 0.0);
        float ty = (float) sec.getDouble("display-translation.y", 0.0);
        float tz = (float) sec.getDouble("display-translation.z", 0.0);
        def.setDisplayTranslation(new Vector3f(tx, ty, tz));

        float rx = (float) sec.getDouble("display-rotation.pitch", 0.0);
        float ry = (float) sec.getDouble("display-rotation.yaw", 0.0);
        float rz = (float) sec.getDouble("display-rotation.roll", 0.0);
        def.setDisplayRotation(new Vector3f(rx, ry, rz));

        if (sec.contains("brightness")) def.setBrightness(sec.getInt("brightness"));
        def.setGlowColorHex(sec.getString("glow-color"));
        def.setLockRotation(sec.getBoolean("lock-rotation", false));

        def.setMultiModel(sec.getBoolean("multi-model", false));
        ConfigurationSection partsSec = sec.getConfigurationSection("model-parts");
        if (partsSec != null) {
            List<BlockModelPart> parts = new ArrayList<>();
            for (String key : partsSec.getKeys(false)) {
                ConfigurationSection pSec = partsSec.getConfigurationSection(key);
                if (pSec != null) {
                    BlockModelPart part = BlockModelPart.loadFromConfig(pSec);
                    if (part != null) parts.add(part);
                }
            }
            def.setModelParts(parts);
        }

        int mpx = Math.max(1, sec.getInt("multi-part-size.x", 1));
        int mpy = Math.max(1, sec.getInt("multi-part-size.y", 1));
        int mpz = Math.max(1, sec.getInt("multi-part-size.z", 1));
        def.setMultiPartSize(new Vector3i(mpx, mpy, mpz));

        ConfigurationSection pbbSec = sec.getConfigurationSection("part-base-blocks");
        if (pbbSec != null) {
            Map<String, Material> map = new HashMap<>();
            for (String key : pbbSec.getKeys(false)) {
                String matStr = pbbSec.getString(key);
                if (matStr != null) {
                    Material m = Material.matchMaterial(matStr);
                    if (m != null) map.put(key, m);
                }
            }
            def.setPartBaseBlocks(map);
        }

        if (sec.contains("physics")) def.setPhysics(BlockPhysicsSpec.loadFromConfig(sec.getConfigurationSection("physics")));
        if (sec.contains("mining")) def.setMining(BlockMiningSpec.loadFromConfig(sec.getConfigurationSection("mining")));
        if (sec.contains("interaction")) def.setInteraction(BlockInteractionSpec.loadFromConfig(sec.getConfigurationSection("interaction")));
        if (sec.contains("world-gen")) def.setWorldGen(BlockWorldGenSpec.loadFromConfig(sec.getConfigurationSection("world-gen")));
        if (sec.contains("bedrock")) def.setBedrockSpec(org.bg52.synthetix.bedrock.BedrockModelSpec.loadFromConfig(sec.getConfigurationSection("bedrock")));

        return def;
    }

    public ItemDefinition toItemDefinition() {
        ItemDefinition itemDef = new ItemDefinition(this.id);
        itemDef.setOwningPlugin(this.owningPlugin);
        itemDef.setDisplayName(this.displayName);
        itemDef.setMaterial(this.itemMaterial != null ? this.itemMaterial : Material.PAPER);
        itemDef.setLore(new ArrayList<>(this.lore));
        itemDef.setCustomModelData(this.customModelData > 0 ? this.customModelData : null);
        itemDef.setItemModel(this.itemModel);
        itemDef.getTags().put("synthetix:block_id", this.id);
        return itemDef;
    }

    public CustomBlockDefinition copy(String newId) {
        CustomBlockDefinition copy = new CustomBlockDefinition(newId);
        copy.setOwningPlugin(this.owningPlugin);
        copy.setDisplayName(this.displayName);
        copy.setLore(new ArrayList<>(this.lore));
        copy.setBaseMaterial(this.baseMaterial);
        copy.setItemMaterial(this.itemMaterial);
        copy.setCustomModelData(this.customModelData);
        copy.setItemModel(this.itemModel);
        copy.setRenderingMethod(this.renderingMethod);
        copy.setDisplayScale(new Vector3f(this.displayScale));
        copy.setDisplayTranslation(new Vector3f(this.displayTranslation));
        copy.setDisplayRotation(new Vector3f(this.displayRotation));
        copy.setBrightness(this.brightness);
        copy.setGlowColorHex(this.glowColorHex);
        copy.setMultiModel(this.multiModel);

        List<BlockModelPart> partsCopy = new ArrayList<>();
        for (BlockModelPart part : this.modelParts) {
            partsCopy.add(part.copy());
        }
        copy.setModelParts(partsCopy);

        copy.setMultiPartSize(new Vector3i(this.multiPartSize));
        copy.setPartBaseBlocks(new HashMap<>(this.partBaseBlocks));

        copy.setPhysics(this.physics.copy());
        copy.setMining(this.mining.copy());
        if (this.bedrockSpec != null) {
            copy.setBedrockSpec(this.bedrockSpec.copy());
        }
        copy.setInteraction(this.interaction.copy());
        copy.setWorldGen(this.worldGen.copy());
        copy.setCustomData(new HashMap<>(this.customData));
        return copy;
    }
}

