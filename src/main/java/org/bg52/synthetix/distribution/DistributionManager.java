package org.bg52.synthetix.distribution;

import org.bg52.synthetix.SynthetixPlugin;
import org.bukkit.Bukkit;

/**
 * Orchestrator managing world distribution: mob drops, loot table injections, and villager/trader trades.
 */
public class DistributionManager {

    private final SynthetixPlugin plugin;
    private final MobDropManager mobDropManager;
    private final LootTableInjector lootTableInjector;
    private final TradeInjector tradeInjector;

    public DistributionManager(SynthetixPlugin plugin) {
        this.plugin = plugin;
        this.mobDropManager = new MobDropManager(plugin);
        this.lootTableInjector = new LootTableInjector(plugin);
        this.tradeInjector = new TradeInjector(plugin);
    }

    public void initialize() {
        Bukkit.getPluginManager().registerEvents(mobDropManager, plugin);
        Bukkit.getPluginManager().registerEvents(lootTableInjector, plugin);
        Bukkit.getPluginManager().registerEvents(tradeInjector, plugin);
        loadAll();
    }

    public void reload() {
        loadAll();
    }

    public void loadAll() {
        mobDropManager.load();
        lootTableInjector.load();
        tradeInjector.load();
    }

    public MobDropManager getMobDropManager() {
        return mobDropManager;
    }

    public LootTableInjector getLootTableInjector() {
        return lootTableInjector;
    }

    public TradeInjector getTradeInjector() {
        return tradeInjector;
    }
}

