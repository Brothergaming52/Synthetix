package org.bg52.synthetix.distribution;

import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.api.event.SynthetixTradeOfferEvent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.MerchantRecipe;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Injects custom merchant trades into leveling villagers and wandering traders.
 */
public class TradeInjector implements Listener {

    private final SynthetixPlugin plugin;
    private final Map<String, TradeRule> rules = new ConcurrentHashMap<>();
    private final Set<UUID> initializedTraders = Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<UUID, Set<Integer>> acquiredVillagerLevels = new ConcurrentHashMap<>();

    public TradeInjector(SynthetixPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        rules.clear();
        File file = new File(plugin.getDataFolder(), "distribution/trades.yml");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource("distribution/trades.yml", false);
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection sec = config.getConfigurationSection("trades");
            if (sec != null) {
                for (String key : sec.getKeys(false)) {
                    ConfigurationSection d = sec.getConfigurationSection(key);
                    if (d != null) {
                        TradeRule rule = new TradeRule(
                                key,
                                d.getString("profession", "ANY"),
                                d.getInt("level", 1),
                                d.getString("cost-1.item", "EMERALD"),
                                d.getInt("cost-1.amount", 1),
                                d.getString("cost-2.item", null),
                                d.getInt("cost-2.amount", 1),
                                d.getString("result.item", "AIR"),
                                d.getInt("result.amount", 1),
                                d.getInt("max-uses", 12),
                                d.getInt("villager-xp", 10),
                                (float) d.getDouble("price-multiplier", 0.05),
                                d.getDouble("chance", 0.5)
                        );
                        rules.put(key, rule);
                    }
                }
            }
            plugin.getLogger().info("Loaded " + rules.size() + " custom merchant trade rules.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load distribution/trades.yml", e);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVillagerAcquireTrade(VillagerAcquireTradeEvent event) {
        if (plugin.getSettingsManager() != null && !plugin.getSettingsManager().isFeatureEnabled(org.bg52.synthetix.settings.GlobalFeature.MERCHANT_TRADES)) return;
        if (rules.isEmpty()) return;
        if (!(event.getEntity() instanceof Villager villager)) return;

        int villagerLevel = villager.getVillagerLevel();
        UUID villagerId = villager.getUniqueId();

        // Ensure only 1 custom trade is injected per level to replace 1 vanilla trade,
        // maintaining the standard 2 trades per level without duplication.
        Set<Integer> levels = acquiredVillagerLevels.computeIfAbsent(villagerId, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
        if (levels.contains(villagerLevel)) {
            return;
        }

        Villager.Profession profession = villager.getProfession();

        for (TradeRule rule : rules.values()) {
            if (rule.matches(profession, villagerLevel)) {
                if (ThreadLocalRandom.current().nextDouble() <= rule.getChance()) {
                    MerchantRecipe mRecipe = rule.toMerchantRecipe(plugin);
                    if (mRecipe != null) {
                        SynthetixTradeOfferEvent tradeEvent = new SynthetixTradeOfferEvent(villager, null, rule, mRecipe);
                        Bukkit.getPluginManager().callEvent(tradeEvent);
                        if (!tradeEvent.isCancelled() && tradeEvent.getMerchantRecipe() != null) {
                            // Replace the acquired trade rather than appending, preserving the 2 trades per level limit
                            event.setRecipe(tradeEvent.getMerchantRecipe());
                            levels.add(villagerLevel);
                            return;
                        }
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWanderingTraderInteract(PlayerInteractEntityEvent event) {
        if (plugin.getSettingsManager() != null && !plugin.getSettingsManager().isFeatureEnabled(org.bg52.synthetix.settings.GlobalFeature.MERCHANT_TRADES)) return;
        if (rules.isEmpty()) return;
        if (!(event.getRightClicked() instanceof WanderingTrader trader)) return;

        if (initializedTraders.add(trader.getUniqueId())) {
            List<MerchantRecipe> recipes = new ArrayList<>(trader.getRecipes());
            boolean modified = false;

            for (TradeRule rule : rules.values()) {
                if (rule.matchesWanderingTrader()) {
                    if (ThreadLocalRandom.current().nextDouble() <= rule.getChance()) {
                        MerchantRecipe mRecipe = rule.toMerchantRecipe(plugin);
                        if (mRecipe != null) {
                            SynthetixTradeOfferEvent tradeEvent = new SynthetixTradeOfferEvent(trader, event.getPlayer(), rule, mRecipe);
                            Bukkit.getPluginManager().callEvent(tradeEvent);
                            if (!tradeEvent.isCancelled() && tradeEvent.getMerchantRecipe() != null) {
                                recipes.add(tradeEvent.getMerchantRecipe());
                                modified = true;
                            }
                        }
                    }
                }
            }

            if (modified) {
                trader.setRecipes(recipes);
            }
        }
    }

    public List<TradeRule> getTradesForItem(String itemId) {
        List<TradeRule> list = new ArrayList<>();
        if (itemId == null) return list;
        for (TradeRule rule : rules.values()) {
            if (itemId.equalsIgnoreCase(rule.getResultItemId())) {
                list.add(rule);
            }
        }
        return list;
    }

    public Map<String, TradeRule> getRules() {
        return Collections.unmodifiableMap(rules);
    }

    public TradeRule getRule(String id) {
        if (id == null) return null;
        return rules.get(id);
    }

    public synchronized void saveRule(TradeRule rule) throws Exception {
        File file = new File(plugin.getDataFolder(), "distribution/trades.yml");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = config.getConfigurationSection("trades." + rule.getId());
        if (sec == null) {
            sec = config.createSection("trades." + rule.getId());
        }
        sec.set("profession", rule.getProfession());
        sec.set("level", rule.getLevel());

        // Cost 1
        sec.set("cost-1.item", rule.getCost1().getRawInput());
        sec.set("cost-1.amount", rule.getCost1Amount());

        // Cost 2
        if (rule.getCost2() != null) {
            sec.set("cost-2.item", rule.getCost2().getRawInput());
            sec.set("cost-2.amount", rule.getCost2Amount());
        } else {
            sec.set("cost-2", null);
        }

        // Result
        if (rule.getResultItemId() != null) {
            sec.set("result.item", "synth:" + rule.getResultItemId());
        } else if (rule.getResultMaterial() != null) {
            sec.set("result.item", rule.getResultMaterial().name());
        }
        sec.set("result.amount", rule.getResultAmount());

        sec.set("max-uses", rule.getMaxUses());
        sec.set("villager-xp", rule.getVillagerExperience());
        sec.set("price-multiplier", rule.getPriceMultiplier());
        sec.set("chance", rule.getChance());

        config.save(file);
        load();
    }

    public synchronized boolean deleteRule(String ruleId) throws Exception {
        File file = new File(plugin.getDataFolder(), "distribution/trades.yml");
        if (!file.exists()) return false;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (config.contains("trades." + ruleId)) {
            config.set("trades." + ruleId, null);
            config.save(file);
            load();
            return true;
        }
        return false;
    }
}

