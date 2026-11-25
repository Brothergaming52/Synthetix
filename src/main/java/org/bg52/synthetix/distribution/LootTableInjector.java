package org.bg52.synthetix.distribution;

import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.api.event.SynthetixLootInjectEvent;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Injects custom items into Minecraft vanilla and custom server loot tables (chests, archaeology, trial chambers, etc.).
 */
public class LootTableInjector implements Listener {

    private final SynthetixPlugin plugin;
    private final Map<String, LootTableRule> rules = new ConcurrentHashMap<>();

    public LootTableInjector(SynthetixPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        rules.clear();
        File file = new File(plugin.getDataFolder(), "distribution/loot_tables.yml");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource("distribution/loot_tables.yml", false);
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection sec = config.getConfigurationSection("injections");
            if (sec != null) {
                for (String key : sec.getKeys(false)) {
                    ConfigurationSection d = sec.getConfigurationSection(key);
                    if (d != null) {
                        LootTableRule rule = new LootTableRule(
                                key,
                                d.getString("item", "AIR"),
                                d.getStringList("loot-tables"),
                                d.getDouble("chance", 0.25),
                                d.getInt("min-amount", 1),
                                d.getInt("max-amount", 1),
                                d.getInt("rolls", 1)
                        );
                        rules.put(key, rule);
                    }
                }
            }
            plugin.getLogger().info("Loaded " + rules.size() + " custom loot table injection rules.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load distribution/loot_tables.yml", e);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLootGenerate(LootGenerateEvent event) {
        if (plugin.getSettingsManager() != null && !plugin.getSettingsManager().isFeatureEnabled(org.bg52.synthetix.settings.GlobalFeature.LOOT_TABLES)) return;
        if (rules.isEmpty() || event.getLootTable() == null) return;

        NamespacedKey tableKey = event.getLootTable().getKey();
        for (LootTableRule rule : rules.values()) {
            if (rule.matches(tableKey)) {
                List<ItemStack> extraLoot = rule.rollLoot(plugin);
                for (ItemStack loot : extraLoot) {
                    SynthetixLootInjectEvent injectEvent = new SynthetixLootInjectEvent(
                            tableKey, rule, loot, loot.getAmount(), rule.getChance(), event.getLootContext()
                    );
                    Bukkit.getPluginManager().callEvent(injectEvent);
                    if (!injectEvent.isCancelled() && injectEvent.getItem() != null) {
                        event.getLoot().add(injectEvent.getItem());
                    }
                }
            }
        }
    }

    public List<LootTableRule> getInjectionsForItem(String itemId) {
        List<LootTableRule> list = new ArrayList<>();
        if (itemId == null) return list;
        for (LootTableRule rule : rules.values()) {
            if (itemId.equalsIgnoreCase(rule.getItemId())) {
                list.add(rule);
            }
        }
        return list;
    }

    public Map<String, LootTableRule> getRules() {
        return Collections.unmodifiableMap(rules);
    }

    public LootTableRule getRule(String id) {
        if (id == null) return null;
        return rules.get(id);
    }

    /**
     * Registers a loot table injection rule in memory. Optionally persists to disk.
     */
    public synchronized void registerRule(LootTableRule rule, boolean saveToDisk) {
        if (rule == null || rule.getId() == null) return;
        if (saveToDisk) {
            try {
                saveRule(rule);
                return;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save loot table rule to disk: " + rule.getId(), e);
            }
        }
        rules.put(rule.getId(), rule);
    }

    public synchronized void saveRule(LootTableRule rule) throws Exception {
        File file = new File(plugin.getDataFolder(), "distribution/loot_tables.yml");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = config.getConfigurationSection("injections." + rule.getId());
        if (sec == null) {
            sec = config.createSection("injections." + rule.getId());
        }
        if (rule.getItemId() != null) {
            sec.set("item", "synth:" + rule.getItemId());
        } else if (rule.getMaterial() != null) {
            sec.set("item", rule.getMaterial().name());
        }
        sec.set("loot-tables", rule.getPatterns());
        sec.set("chance", rule.getChance());
        sec.set("min-amount", rule.getMinAmount());
        sec.set("max-amount", rule.getMaxAmount());
        sec.set("rolls", rule.getRolls());
        config.save(file);
        load();
    }

    public synchronized boolean deleteRule(String ruleId) throws Exception {
        File file = new File(plugin.getDataFolder(), "distribution/loot_tables.yml");
        if (!file.exists()) return false;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (config.contains("injections." + ruleId)) {
            config.set("injections." + ruleId, null);
            config.save(file);
            load();
            return true;
        }
        return false;
    }
}

