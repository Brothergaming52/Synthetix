package org.bg52.synthetix.distribution;

import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.api.event.SynthetixMobDropEvent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages mob drop rules and listens to entity death events.
 */
public class MobDropManager implements Listener {

    private final SynthetixPlugin plugin;
    private final Map<String, MobDropRule> rules = new ConcurrentHashMap<>();

    public MobDropManager(SynthetixPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        rules.clear();
        File file = new File(plugin.getDataFolder(), "distribution/mob_drops.yml");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource("distribution/mob_drops.yml", false);
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection sec = config.getConfigurationSection("drops");
            if (sec != null) {
                for (String key : sec.getKeys(false)) {
                    ConfigurationSection d = sec.getConfigurationSection(key);
                    if (d != null) {
                        MobDropRule rule = new MobDropRule(
                                key,
                                d.getString("item", "AIR"),
                                d.getString("entity", "ANY"),
                                d.getDouble("chance", 0.1),
                                d.getDouble("looting-bonus", 0.0),
                                d.getInt("min-amount", 1),
                                d.getInt("max-amount", 1),
                                d.getBoolean("player-kill-only", true),
                                d.getStringList("biomes"),
                                d.getStringList("worlds")
                        );
                        rules.put(key, rule);
                    }
                }
            }
            plugin.getLogger().info("Loaded " + rules.size() + " custom mob drop rules.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load distribution/mob_drops.yml", e);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (plugin.getSettingsManager() != null && !plugin.getSettingsManager().isFeatureEnabled(org.bg52.synthetix.settings.GlobalFeature.MOB_DROPS)) return;
        if (rules.isEmpty()) return;

        for (MobDropRule rule : rules.values()) {
            if (rule.matches(event)) {
                ItemStack drop = rule.rollDrop(event, plugin);
                if (drop != null) {
                    SynthetixMobDropEvent dropEvent = new SynthetixMobDropEvent(
                            event.getEntity(), event.getEntity().getKiller(), rule, drop, drop.getAmount(), rule.getChance()
                    );
                    Bukkit.getPluginManager().callEvent(dropEvent);
                    if (!dropEvent.isCancelled() && dropEvent.getDropItem() != null) {
                        event.getDrops().add(dropEvent.getDropItem());
                    }
                }
            }
        }
    }

    public List<MobDropRule> getDropsForItem(String itemId) {
        List<MobDropRule> list = new ArrayList<>();
        if (itemId == null) return list;
        for (MobDropRule rule : rules.values()) {
            if (itemId.equalsIgnoreCase(rule.getItemId())) {
                list.add(rule);
            }
        }
        return list;
    }

    public Map<String, MobDropRule> getRules() {
        return Collections.unmodifiableMap(rules);
    }

    public MobDropRule getRule(String id) {
        if (id == null) return null;
        return rules.get(id);
    }

    /**
     * Registers a mob drop rule in memory. Optionally persists to disk.
     */
    public synchronized void registerRule(MobDropRule rule, boolean saveToDisk) {
        if (rule == null || rule.getId() == null) return;
        if (saveToDisk) {
            try {
                saveRule(rule);
                return;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save mob drop rule to disk: " + rule.getId(), e);
            }
        }
        rules.put(rule.getId(), rule);
    }

    public synchronized void saveRule(MobDropRule rule) throws Exception {
        File file = new File(plugin.getDataFolder(), "distribution/mob_drops.yml");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = config.getConfigurationSection("drops." + rule.getId());
        if (sec == null) {
            sec = config.createSection("drops." + rule.getId());
        }
        if (rule.getItemId() != null) {
            sec.set("item", "synth:" + rule.getItemId());
        } else if (rule.getMaterial() != null) {
            sec.set("item", rule.getMaterial().name());
        }
        sec.set("entity", rule.getEntityType());
        sec.set("chance", rule.getChance());
        sec.set("looting-bonus", rule.getLootingBonus());
        sec.set("min-amount", rule.getMinAmount());
        sec.set("max-amount", rule.getMaxAmount());
        sec.set("player-kill-only", rule.isPlayerKillOnly());
        sec.set("biomes", new ArrayList<>(rule.getBiomes()));
        sec.set("worlds", new ArrayList<>(rule.getWorlds()));
        config.save(file);
        load();
    }

    public synchronized boolean deleteRule(String ruleId) throws Exception {
        File file = new File(plugin.getDataFolder(), "distribution/mob_drops.yml");
        if (!file.exists()) return false;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (config.contains("drops." + ruleId)) {
            config.set("drops." + ruleId, null);
            config.save(file);
            load();
            return true;
        }
        return false;
    }
}

