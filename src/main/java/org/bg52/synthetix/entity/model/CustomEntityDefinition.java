package org.bg52.synthetix.entity.model;

import org.bg52.synthetix.entity.ai.EntityAISpec;
import org.bg52.synthetix.entity.animation.AnimationTrack;
import org.bg52.synthetix.entity.behavior.EntityBehaviorChart;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.joml.Vector3f;

import java.util.*;

/**
 * Super-modular, comprehensive definition of a custom Synthetix Entity.
 * Implements the Tri-Layer Virtual Entity Architecture:
 * - Driver Layer: Base living mob proxy providing authoritative physics, navigation, and 1.20.6 attributes.
 * - Visual Skeletal Rig: Zero-ticking virtual ItemDisplay bones with client-side SLERP matrix interpolation.
 * - Spatial Interaction Matrix: Sub-voxel Interaction hitboxes with anatomical damage multipliers.
 */
public class CustomEntityDefinition {

    public static final NamespacedKey ENTITY_ID_KEY = new NamespacedKey("synthetix", "entity_id");

    private final String id;
    private String owningPlugin = "synthetix";
    private String displayName;
    private boolean customNameVisible = false;
    private EntityType baseEntityType = EntityType.ZOMBIE;
    private int slimeSize = 2;

    // 1.20.6 Engine Attributes
    private double scale = 1.0;
    private double stepHeight = 0.6;
    private double gravity = 0.08;
    private double movementSpeed = 0.25;
    private double maxHealth = 40.0;
    private double attackDamage = 6.0;
    private double followRange = 32.0;

    // Skeletal Rig Hierarchy (BoneName -> EntityBoneSpec)
    private String rootBoneName = "root";
    private final Map<String, EntityBoneSpec> bones = new LinkedHashMap<>();

    // Animation Tracks (TrackName -> AnimationTrack)
    private final Map<String, AnimationTrack> animations = new HashMap<>();

    // Subsystems
    private EntityAISpec ai = new EntityAISpec();
    private EntityBehaviorChart behaviorChart = new EntityBehaviorChart();
    private EntitySoundSpec sounds = new EntitySoundSpec();
    private EntityLootSpec loot = new EntityLootSpec();
    private org.bg52.synthetix.bedrock.BedrockModelSpec bedrockSpec;

    // Spawning & Extensibility Controls
    private boolean allowSplitting = false;
    private int splitCount = 0;
    private String childCustomEntityId = null;
    private Double seatHeightOverride = null;
    private float baseYawOffset = 0.0f;

    public CustomEntityDefinition(String id) {
        this.id = id != null ? id.toLowerCase(Locale.ROOT) : "custom_entity";
        this.displayName = "<gold>" + TextUtil.prettify(this.id) + "</gold>";

        // Initialize with standard root bone
        EntityBoneSpec rootBone = new EntityBoneSpec(this.rootBoneName, null, new Vector3f(0, 0, 0), Material.LEATHER_HORSE_ARMOR, 0);
        this.bones.put(this.rootBoneName, rootBone);
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

    public boolean isCustomNameVisible() {
        return customNameVisible;
    }

    public void setCustomNameVisible(boolean customNameVisible) {
        this.customNameVisible = customNameVisible;
    }

    public EntityType getBaseEntityType() {
        return baseEntityType;
    }

    public void setBaseEntityType(EntityType baseEntityType) {
        this.baseEntityType = baseEntityType != null && baseEntityType.isAlive() ? baseEntityType : EntityType.ZOMBIE;
    }

    public int getSlimeSize() {
        return slimeSize;
    }

    public void setSlimeSize(int slimeSize) {
        this.slimeSize = Math.max(1, slimeSize);
    }

    public double getScale() {
        return scale;
    }

    public void setScale(double scale) {
        this.scale = Math.max(0.0625, Math.min(16.0, scale));
    }

    public double getStepHeight() {
        return stepHeight;
    }

    public void setStepHeight(double stepHeight) {
        this.stepHeight = Math.max(0.0, Math.min(10.0, stepHeight));
    }

    public double getGravity() {
        return gravity;
    }

    public void setGravity(double gravity) {
        this.gravity = Math.max(-1.0, Math.min(1.0, gravity));
    }

    public double getMovementSpeed() {
        return movementSpeed;
    }

    public void setMovementSpeed(double movementSpeed) {
        this.movementSpeed = Math.max(0.0, movementSpeed);
    }

    public double getMaxHealth() {
        return maxHealth;
    }

    public void setMaxHealth(double maxHealth) {
        this.maxHealth = Math.max(1.0, maxHealth);
    }

    public double getAttackDamage() {
        return attackDamage;
    }

    public void setAttackDamage(double attackDamage) {
        this.attackDamage = Math.max(0.0, attackDamage);
    }

    public double getFollowRange() {
        return followRange;
    }

    public void setFollowRange(double followRange) {
        this.followRange = Math.max(4.0, followRange);
    }

    public String getRootBoneName() {
        return rootBoneName;
    }

    public void setRootBoneName(String rootBoneName) {
        this.rootBoneName = rootBoneName;
    }

    public Map<String, EntityBoneSpec> getBones() {
        return bones;
    }

    public EntityBoneSpec getBone(String name) {
        if (name == null) return null;
        return bones.get(name.toLowerCase(Locale.ROOT));
    }

    public void addBone(EntityBoneSpec bone) {
        if (bone != null && bone.getName() != null) {
            bones.put(bone.getName().toLowerCase(Locale.ROOT), bone);
        }
    }

    public void removeBone(String name) {
        if (name != null) {
            bones.remove(name.toLowerCase(Locale.ROOT));
        }
    }

    public Map<String, AnimationTrack> getAnimations() {
        return animations;
    }

    public AnimationTrack getAnimation(String name) {
        if (name == null) return null;
        return animations.get(name.toLowerCase(Locale.ROOT));
    }

    public void addAnimation(AnimationTrack track) {
        if (track != null && track.getName() != null) {
            animations.put(track.getName().toLowerCase(Locale.ROOT), track);
        }
    }

    public EntityAISpec getAi() {
        return ai;
    }

    public void setAi(EntityAISpec ai) {
        this.ai = ai != null ? ai : new EntityAISpec();
    }

    public EntityBehaviorChart getBehaviorChart() {
        return behaviorChart;
    }

    public void setBehaviorChart(EntityBehaviorChart behaviorChart) {
        this.behaviorChart = behaviorChart != null ? behaviorChart : new EntityBehaviorChart();
    }

    public EntitySoundSpec getSounds() {
        return sounds;
    }

    public void setSounds(EntitySoundSpec sounds) {
        this.sounds = sounds != null ? sounds : new EntitySoundSpec();
    }

    public EntityLootSpec getLoot() {
        return loot;
    }

    public void setLoot(EntityLootSpec loot) {
        this.loot = loot != null ? loot : new EntityLootSpec();
    }

    public boolean isAllowSplitting() {
        return allowSplitting;
    }

    public void setAllowSplitting(boolean allowSplitting) {
        this.allowSplitting = allowSplitting;
    }

    public int getSplitCount() {
        return splitCount;
    }

    public void setSplitCount(int splitCount) {
        this.splitCount = Math.max(0, splitCount);
    }

    public String getChildCustomEntityId() {
        return childCustomEntityId;
    }

    public void setChildCustomEntityId(String childCustomEntityId) {
        this.childCustomEntityId = childCustomEntityId;
    }

    public Double getSeatHeightOverride() {
        return seatHeightOverride;
    }

    public void setSeatHeightOverride(Double seatHeightOverride) {
        this.seatHeightOverride = seatHeightOverride;
    }

    public float getBaseYawOffset() {
        return baseYawOffset;
    }

    public void setBaseYawOffset(float baseYawOffset) {
        this.baseYawOffset = baseYawOffset;
    }

    public List<EntityHitboxSpec> getAllHitboxes() {
        List<EntityHitboxSpec> list = new ArrayList<>();
        for (EntityBoneSpec b : bones.values()) {
            if (b.getHitbox() != null) {
                list.add(b.getHitbox());
            }
        }
        return list;
    }

    public org.bg52.synthetix.bedrock.BedrockModelSpec getBedrockSpec() {
        return bedrockSpec;
    }

    public void setBedrockSpec(org.bg52.synthetix.bedrock.BedrockModelSpec bedrockSpec) {
        this.bedrockSpec = bedrockSpec;
    }

    public void saveToConfig(ConfigurationSection sec) {
        if (sec == null) return;
        sec.set("id", id);
        sec.set("owning-plugin", owningPlugin);
        sec.set("display-name", displayName);
        sec.set("custom-name-visible", customNameVisible);
        sec.set("base-entity", baseEntityType.name());

        ConfigurationSection attrSec = sec.createSection("attributes");
        attrSec.set("scale", scale);
        attrSec.set("step-height", stepHeight);
        attrSec.set("gravity", gravity);
        attrSec.set("movement-speed", movementSpeed);
        attrSec.set("max-health", maxHealth);
        attrSec.set("attack-damage", attackDamage);
        attrSec.set("follow-range", followRange);

        sec.set("root-bone", rootBoneName);
        if (!bones.isEmpty()) {
            ConfigurationSection bonesSec = sec.createSection("bones");
            for (Map.Entry<String, EntityBoneSpec> entry : bones.entrySet()) {
                entry.getValue().saveToConfig(bonesSec.createSection(entry.getKey()));
            }
        }

        if (!animations.isEmpty()) {
            ConfigurationSection animSec = sec.createSection("animations");
            for (Map.Entry<String, AnimationTrack> entry : animations.entrySet()) {
                entry.getValue().saveToConfig(animSec.createSection(entry.getKey()));
            }
        }

        sec.set("allow-splitting", allowSplitting);
        sec.set("split-count", splitCount);
        if (childCustomEntityId != null) sec.set("child-custom-entity-id", childCustomEntityId);
        if (seatHeightOverride != null) sec.set("seat-height-override", seatHeightOverride);

        ai.saveToConfig(sec.createSection("ai"));
        if (behaviorChart != null && !behaviorChart.isEmpty()) {
            behaviorChart.saveToConfig(sec.createSection("behavior-chart"));
        }
        sounds.saveToConfig(sec.createSection("sounds"));
        loot.saveToConfig(sec.createSection("loot"));
        if (bedrockSpec != null) {
            bedrockSpec.saveToConfig(sec.createSection("bedrock"));
        }
    }

    public static CustomEntityDefinition loadFromConfig(ConfigurationSection sec) {
        if (sec == null) return null;
        String id = sec.getString("id");
        if (id == null) return null;

        CustomEntityDefinition def = new CustomEntityDefinition(id);
        def.setOwningPlugin(sec.getString("owning-plugin", "synthetix"));
        def.setDisplayName(sec.getString("display-name", id));
        def.setCustomNameVisible(sec.getBoolean("custom-name-visible", false));

        String baseTypeStr = sec.getString("base-entity", "ZOMBIE");
        try {
            def.setBaseEntityType(EntityType.valueOf(baseTypeStr.toUpperCase(Locale.ROOT)));
        } catch (Exception ignored) {
            def.setBaseEntityType(EntityType.ZOMBIE);
        }

        ConfigurationSection attrSec = sec.getConfigurationSection("attributes");
        if (attrSec != null) {
            def.setScale(attrSec.getDouble("scale", 1.0));
            def.setStepHeight(attrSec.getDouble("step-height", 0.6));
            def.setGravity(attrSec.getDouble("gravity", 0.08));
            def.setMovementSpeed(attrSec.getDouble("movement-speed", 0.25));
            def.setMaxHealth(attrSec.getDouble("max-health", 40.0));
            def.setAttackDamage(attrSec.getDouble("attack-damage", 6.0));
            def.setFollowRange(attrSec.getDouble("follow-range", 32.0));
        }

        def.setRootBoneName(sec.getString("root-bone", "root"));

        ConfigurationSection bonesSec = sec.getConfigurationSection("bones");
        if (bonesSec != null) {
            def.getBones().clear();
            for (String boneKey : bonesSec.getKeys(false)) {
                ConfigurationSection bSec = bonesSec.getConfigurationSection(boneKey);
                if (bSec != null) {
                    EntityBoneSpec bone = EntityBoneSpec.loadFromConfig(bSec);
                    if (bone != null) {
                        def.addBone(bone);
                    }
                }
            }
        }

        ConfigurationSection animSec = sec.getConfigurationSection("animations");
        if (animSec != null) {
            def.getAnimations().clear();
            for (String animKey : animSec.getKeys(false)) {
                ConfigurationSection aSec = animSec.getConfigurationSection(animKey);
                if (aSec != null) {
                    AnimationTrack track = AnimationTrack.loadFromConfig(aSec);
                    if (track != null) {
                        def.addAnimation(track);
                    }
                }
            }
        }

        def.setAllowSplitting(sec.getBoolean("allow-splitting", false));
        def.setSplitCount(sec.getInt("split-count", 0));
        def.setChildCustomEntityId(sec.getString("child-custom-entity-id", null));
        if (sec.contains("seat-height-override")) {
            def.setSeatHeightOverride(sec.getDouble("seat-height-override"));
        }

        ConfigurationSection aiSec = sec.getConfigurationSection("ai");
        if (aiSec != null) def.setAi(EntityAISpec.loadFromConfig(aiSec));

        ConfigurationSection chartSec = sec.getConfigurationSection("behavior-chart");
        if (chartSec != null) {
            def.setBehaviorChart(EntityBehaviorChart.loadFromConfig(chartSec));
        } else {
            def.getBehaviorChart().populateDefaults(def.getAi().getAiType(), def.getAnimations());
        }

        ConfigurationSection soundsSec = sec.getConfigurationSection("sounds");
        if (soundsSec != null) def.setSounds(EntitySoundSpec.loadFromConfig(soundsSec));

        ConfigurationSection lootSec = sec.getConfigurationSection("loot");
        if (lootSec != null) def.setLoot(EntityLootSpec.loadFromConfig(lootSec));

        ConfigurationSection bedrockSec = sec.getConfigurationSection("bedrock");
        if (bedrockSec != null) def.setBedrockSpec(org.bg52.synthetix.bedrock.BedrockModelSpec.loadFromConfig(bedrockSec));

        return def;
    }
}

