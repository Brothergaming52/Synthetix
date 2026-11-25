package org.bg52.synthetix.entity;

import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.entity.ai.EntityAISpec;
import org.bg52.synthetix.entity.ai.EntityAIType;
import org.bg52.synthetix.entity.animation.AnimationTrack;
import org.bg52.synthetix.entity.model.*;
import org.bg52.synthetix.entity.runtime.CustomEntityInstance;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.joml.Vector3f;

import java.io.File;

/**
 * Fluent builder designed for other plugins and developers to construct, configure,
 * and register custom entities effortlessly using Bedrock models and animations.
 */
public class CustomEntityBuilder {

    private final SynthetixPlugin plugin;
    private final CustomEntityDefinition def;

    public CustomEntityBuilder(SynthetixPlugin plugin, String id) {
        this.plugin = plugin;
        this.def = new CustomEntityDefinition(id);
    }

    /**
     * Imports skeletal bones, Java item models, textures, and animations
     * from a Blockbench project (.bbmodel) or Bedrock geometry (.geo.json).
     */
    public CustomEntityBuilder fromBedrockModel(File modelFile) {
        if (modelFile != null && modelFile.exists()) {
            CustomEntityDefinition imported = plugin.getBedrockEntityImporter().importEntity(modelFile, def.getId());
            if (imported != null) {
                // Copy imported skeletal structure and animations over to this builder
                this.def.setRootBoneName(imported.getRootBoneName());
                this.def.getBones().clear();
                this.def.getBones().putAll(imported.getBones());
                this.def.getAnimations().clear();
                this.def.getAnimations().putAll(imported.getAnimations());
            }
        }
        return this;
    }

    public CustomEntityBuilder owningPlugin(String pluginName) {
        def.setOwningPlugin(pluginName);
        return this;
    }

    public CustomEntityBuilder displayName(String displayName) {
        def.setDisplayName(displayName);
        return this;
    }

    public CustomEntityBuilder customNameVisible(boolean visible) {
        def.setCustomNameVisible(visible);
        return this;
    }

    public CustomEntityBuilder baseEntity(EntityType baseEntityType) {
        def.setBaseEntityType(baseEntityType);
        return this;
    }

    public CustomEntityBuilder scale(double scale) {
        def.setScale(scale);
        return this;
    }

    public CustomEntityBuilder stepHeight(double stepHeight) {
        def.setStepHeight(stepHeight);
        return this;
    }

    public CustomEntityBuilder gravity(double gravity) {
        def.setGravity(gravity);
        return this;
    }

    public CustomEntityBuilder movementSpeed(double movementSpeed) {
        def.setMovementSpeed(movementSpeed);
        return this;
    }

    public CustomEntityBuilder maxHealth(double maxHealth) {
        def.setMaxHealth(maxHealth);
        return this;
    }

    public CustomEntityBuilder attackDamage(double attackDamage) {
        def.setAttackDamage(attackDamage);
        return this;
    }

    public CustomEntityBuilder followRange(double followRange) {
        def.setFollowRange(followRange);
        return this;
    }

    public CustomEntityBuilder ai(EntityAIType aiType) {
        def.getAi().setAiType(aiType);
        return this;
    }

    public CustomEntityBuilder ai(EntityAISpec aiSpec) {
        def.setAi(aiSpec);
        return this;
    }

    public CustomEntityBuilder sounds(EntitySoundSpec sounds) {
        def.setSounds(sounds);
        return this;
    }

    public CustomEntityBuilder loot(EntityLootSpec loot) {
        def.setLoot(loot);
        return this;
    }

    public CustomEntityBuilder addBone(EntityBoneSpec bone) {
        def.addBone(bone);
        return this;
    }

    public CustomEntityBuilder addAnimation(AnimationTrack track) {
        def.addAnimation(track);
        return this;
    }

    public CustomEntityBuilder addBehavior(org.bg52.synthetix.entity.behavior.EntityBehaviorNode node) {
        def.getBehaviorChart().addNode(node);
        return this;
    }

    public CustomEntityBuilder behavior(java.util.function.Consumer<org.bg52.synthetix.entity.behavior.EntityBehaviorChart> consumer) {
        if (consumer != null) {
            consumer.accept(def.getBehaviorChart());
        }
        return this;
    }

    /**
     * Builds and returns the CustomEntityDefinition.
     */
    public CustomEntityDefinition build() {
        return def;
    }

    /**
     * Persists the entity definition to plugins/Synthetix/entities/<id>.yml and registers it.
     */
    public CustomEntityDefinition register() {
        plugin.getCustomEntityRegistry().save(def);
        return def;
    }

    /**
     * Automatically registers the entity and immediately spawns an instance at the target location.
     */
    public CustomEntityInstance spawn(Location location) {
        register();
        return plugin.getCustomEntityManager().spawnEntity(def, location);
    }
}

