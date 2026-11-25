package org.bg52.synthetix.entity.behavior;

import org.bg52.synthetix.entity.ai.EntityAIType;
import org.bg52.synthetix.entity.animation.AnimationTrack;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

/**
 * Priority-ordered behavior chart managing all actions, conditions, and animation bindings for an entity.
 */
public class EntityBehaviorChart {

    private final List<EntityBehaviorNode> nodes = new ArrayList<>();

    public EntityBehaviorChart() {
    }

    public List<EntityBehaviorNode> getNodes() {
        return nodes;
    }

    public List<EntityBehaviorNode> getNodesSortedByPriority() {
        List<EntityBehaviorNode> sorted = new ArrayList<>(nodes);
        sorted.sort(Comparator.comparingInt(EntityBehaviorNode::getPriority));
        return sorted;
    }

    public EntityBehaviorNode getNode(String id) {
        if (id == null) return null;
        for (EntityBehaviorNode node : nodes) {
            if (id.equalsIgnoreCase(node.getId())) {
                return node;
            }
        }
        return null;
    }

    public void addNode(EntityBehaviorNode node) {
        if (node == null) return;
        removeNode(node.getId());
        nodes.add(node);
    }

    public boolean removeNode(String id) {
        if (id == null) return false;
        return nodes.removeIf(n -> id.equalsIgnoreCase(n.getId()));
    }

    public void clear() {
        nodes.clear();
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    /**
     * Initializes sensible default behaviors matching the mob archetype and available animation tracks.
     */
    public void populateDefaults(EntityAIType archetype, Map<String, AnimationTrack> availableTracks) {
        clear();

        String attackAnim = findBestTrack(availableTracks, "attack", "bite", "strike", "swing", "shoot");
        String walkAnim = findBestTrack(availableTracks, "walk", "run", "hop", "move");
        String roarAnim = findBestTrack(availableTracks, "roar", "cast", "spell", "special");

        if (archetype == EntityAIType.HOSTILE_LEAP) {
            EntityBehaviorNode leap = new EntityBehaviorNode("leap_attack", "Pounce Leap", 1);
            leap.setMinRange(1.8);
            leap.setMaxRange(10.0);
            leap.setCooldownTicks(30);
            leap.setDurationTicks(18);
            leap.setAnimationTrack(walkAnim != null ? walkAnim : attackAnim);
            leap.addAction(new EntityBehaviorAction(EntityBehaviorActionType.LEAP_DASH, "0.55 0.28 towards"));
            leap.addAction(new EntityBehaviorAction(EntityBehaviorActionType.MELEE_DAMAGE, "8.0"));
            leap.addAction(new EntityBehaviorAction(EntityBehaviorActionType.PLAY_SOUND, "entity.generic.small_fall 1.0 1.2"));
            addNode(leap);

            EntityBehaviorNode melee = new EntityBehaviorNode("close_bite", "Close Strike", 2);
            melee.setMinRange(0.0);
            melee.setMaxRange(2.0);
            melee.setCooldownTicks(18);
            melee.setDurationTicks(12);
            melee.setAnimationTrack(attackAnim);
            melee.addAction(new EntityBehaviorAction(EntityBehaviorActionType.MELEE_DAMAGE, "6.0"));
            addNode(melee);
        } else {
            // Standard Hostile Melee
            EntityBehaviorNode melee = new EntityBehaviorNode("primary_attack", "Primary Melee", 1);
            melee.setMinRange(0.0);
            melee.setMaxRange(2.4);
            melee.setCooldownTicks(20);
            melee.setDurationTicks(14);
            melee.setAnimationTrack(attackAnim);
            melee.addAction(new EntityBehaviorAction(EntityBehaviorActionType.MELEE_DAMAGE, "6.0"));
            addNode(melee);

            if (roarAnim != null) {
                EntityBehaviorNode enrage = new EntityBehaviorNode("enrage_roar", "Enrage Roar", 2);
                enrage.setMinHealthPercent(0.0);
                enrage.setMaxHealthPercent(40.0);
                enrage.setCooldownTicks(200);
                enrage.setDurationTicks(25);
                enrage.setAnimationTrack(roarAnim);
                enrage.addAction(new EntityBehaviorAction(EntityBehaviorActionType.POTION_EFFECT, "SPEED 10 1 self"));
                enrage.addAction(new EntityBehaviorAction(EntityBehaviorActionType.POTION_EFFECT, "STRENGTH 10 0 self"));
                enrage.addAction(new EntityBehaviorAction(EntityBehaviorActionType.PLAY_SOUND, "entity.ender_dragon.growl 1.0 1.2"));
                enrage.addAction(new EntityBehaviorAction(EntityBehaviorActionType.PARTICLE_BURST, "ANGRY_VILLAGER 15 0.5 0.8 0.5 0.1"));
                addNode(enrage);
            }
        }
    }

    private String findBestTrack(Map<String, AnimationTrack> tracks, String... candidates) {
        if (tracks == null || tracks.isEmpty()) return null;
        for (String c : candidates) {
            for (String key : tracks.keySet()) {
                if (key.equalsIgnoreCase(c) || key.toLowerCase(Locale.ROOT).contains(c)) {
                    return key;
                }
            }
        }
        return tracks.keySet().iterator().next();
    }

    public void saveToConfig(ConfigurationSection sec) {
        if (sec == null) return;
        for (EntityBehaviorNode node : nodes) {
            ConfigurationSection nSec = sec.createSection(node.getId());
            node.saveToConfig(nSec);
        }
    }

    public static EntityBehaviorChart loadFromConfig(ConfigurationSection sec) {
        EntityBehaviorChart chart = new EntityBehaviorChart();
        if (sec == null) return chart;

        for (String key : sec.getKeys(false)) {
            ConfigurationSection nSec = sec.getConfigurationSection(key);
            if (nSec != null) {
                EntityBehaviorNode node = EntityBehaviorNode.loadFromConfig(nSec);
                if (node != null) {
                    chart.addNode(node);
                }
            }
        }
        return chart;
    }
}

