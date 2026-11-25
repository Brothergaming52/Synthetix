package org.bg52.synthetix.set;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.ability.ActionExecutor;
import org.bg52.synthetix.item.ItemDefinition;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bg52.synthetix.scheduler.PlatformScheduler;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Enterprise manager for Equipment Sets and Set Tier Bonuses.
 * Continuously evaluates equipped armor and items across vanilla and custom accessory slots,
 * dynamically applying status effects, attributes, actions, and formatting dynamic set tooltips.
 */
public class SetManager implements Listener {

    private final SynthetixPlugin plugin;
    private final Map<String, ItemSet> setRegistry;
    private final List<Function<Player, List<ItemStack>>> extraEquipmentProviders;
    // player UUID -> (setId -> active tier counts)
    private final Map<UUID, Map<String, Set<Integer>>> activePlayerSetTiers;
    private PlatformScheduler.TaskHandle buffTask;

    public SetManager(SynthetixPlugin plugin) {
        this.plugin = plugin;
        this.setRegistry = new ConcurrentHashMap<>();
        this.extraEquipmentProviders = new ArrayList<>();
        this.activePlayerSetTiers = new ConcurrentHashMap<>();
    }

    /**
     * Registers an external equipment provider (e.g. from CuriosPaper)
     * so custom accessory slots are automatically evaluated for set piece bonuses.
     */
    public void registerEquipmentProvider(Function<Player, List<ItemStack>> provider) {
        if (provider != null) {
            this.extraEquipmentProviders.add(provider);
        }
    }

    public void registerSet(ItemSet set) {
        if (set != null) {
            this.setRegistry.put(set.getId().toLowerCase(Locale.ROOT), set);
            saveAll();
        }
    }

    public ItemSet getSet(String id) {
        if (id == null) return null;
        return this.setRegistry.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<ItemSet> getAllSets() {
        return Collections.unmodifiableCollection(this.setRegistry.values());
    }

    public boolean deleteSet(String id) {
        if (id == null) return false;
        ItemSet removed = this.setRegistry.remove(id.toLowerCase(Locale.ROOT));
        if (removed != null) {
            saveAll();
            return true;
        }
        return false;
    }

    public ItemSet getSetOfItem(String itemId) {
        if (itemId == null) return null;
        String clean = itemId.toLowerCase(Locale.ROOT);
        for (ItemSet set : setRegistry.values()) {
            if (set.containsPiece(clean)) {
                return set;
            }
        }
        return null;
    }

    public void start() {
        if (buffTask != null) buffTask.cancel();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        buffTask = PlatformScheduler.get().runGlobalTimer(() -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                PlatformScheduler.get().runEntity(player, () -> evaluatePlayerSets(player));
            }
        }, 40L, 20L); // Ticks every 1 second
    }

    public void stop() {
        if (buffTask != null) {
            buffTask.cancel();
            buffTask = null;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            cleanupPlayer(player);
        }
        activePlayerSetTiers.clear();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cleanupPlayer(event.getPlayer());
    }

    /**
     * Gathers all items currently equipped by a player (armor, hands, and downstream curio slots).
     */
    public List<ItemStack> getAllEquippedItems(Player player) {
        List<ItemStack> equipped = new ArrayList<>();
        // Armor
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && !armor.getType().isAir()) {
                equipped.add(armor);
            }
        }
        // Off-hand / Main-hand if relevant
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off != null && !off.getType().isAir()) equipped.add(off);

        // Downstream custom accessory slots (CuriosPaper)
        for (Function<Player, List<ItemStack>> provider : extraEquipmentProviders) {
            try {
                List<ItemStack> extra = provider.apply(player);
                if (extra != null) {
                    for (ItemStack item : extra) {
                        if (item != null && !item.getType().isAir()) {
                            equipped.add(item);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        return equipped;
    }

    /**
     * Calculates the number of pieces a player has equipped for a given set.
     */
    public int countEquippedPieces(Player player, ItemSet set) {
        if (player == null || set == null) return 0;
        List<ItemStack> equipped = getAllEquippedItems(player);
        Set<String> equippedPieces = new HashSet<>();

        for (ItemStack item : equipped) {
            String id = plugin.getItemRegistry().getItemId(item);
            if (id != null && set.containsPiece(id)) {
                equippedPieces.add(id.toLowerCase(Locale.ROOT));
            }
        }

        return equippedPieces.size();
    }

    /**
     * Evaluates a player's equipped items, refreshing continuous potion effects and
     * dynamically granting or removing attribute modifiers and executing tier unlock actions.
     */
    public void evaluatePlayerSets(Player player) {
        if (player == null || !player.isOnline()) return;
        if (plugin.getSettingsManager() != null && !plugin.getSettingsManager().isFeatureEnabled(org.bg52.synthetix.settings.GlobalFeature.EQUIPMENT_SETS)) {
            cleanupPlayer(player);
            activePlayerSetTiers.remove(player.getUniqueId());
            return;
        }

        Map<String, Set<Integer>> playerTiers = activePlayerSetTiers.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());

        for (ItemSet set : setRegistry.values()) {
            int count = countEquippedPieces(player, set);
            Set<Integer> currentActive = new HashSet<>();
            Set<Integer> previousActive = playerTiers.getOrDefault(set.getId(), Collections.emptySet());

            for (Map.Entry<Integer, SetBonus> entry : set.getBonuses().entrySet()) {
                int tier = entry.getKey();
                SetBonus bonus = entry.getValue();

                if (count >= tier) {
                    currentActive.add(tier);

                    // 1. Continuous Potion Effects (duration 60 ticks = 3s so it stays smoothly active)
                    for (PotionEffect effect : bonus.getPotionEffects()) {
                        player.addPotionEffect(new PotionEffect(effect.getType(), 60, effect.getAmplifier(), false, false, true));
                    }

                    // 2. Newly unlocked tier: apply attributes and fire actions
                    if (!previousActive.contains(tier)) {
                        applyTierAttributes(player, set, tier, bonus);
                        if (bonus.getActions() != null) {
                            for (String action : bonus.getActions()) {
                                ActionExecutor.executeAction(action, player, null, set.getId());
                            }
                        }
                    }
                } else {
                    // 3. Deactivated tier: remove attributes
                    if (previousActive.contains(tier)) {
                        removeTierAttributes(player, set, tier, bonus);
                    }
                }
            }

            if (currentActive.isEmpty()) {
                playerTiers.remove(set.getId());
            } else {
                playerTiers.put(set.getId(), currentActive);
            }
        }
    }

    private void applyTierAttributes(Player player, ItemSet set, int tier, SetBonus bonus) {
        if (bonus.getAttributes() == null || bonus.getAttributes().isEmpty()) return;

        for (ItemDefinition.AttributeSpec spec : bonus.getAttributes()) {
            Attribute attr = ItemDefinition.parseAttribute(spec.getAttributeName());
            if (attr == null) continue;

            AttributeInstance inst = player.getAttribute(attr);
            if (inst == null) continue;

            UUID modUuid = generateModifierUUID(set.getId(), tier, spec.getName(), spec.getAttributeName());
            inst.removeModifier(modUuid); // Ensure no duplicate

            AttributeModifier modifier;
            if (spec.getSlot() != null) {
                modifier = new AttributeModifier(modUuid, "synth_set_" + set.getId() + "_" + tier + "_" + spec.getName(),
                        spec.getAmount(), spec.getOperation(), spec.getSlot());
            } else {
                modifier = new AttributeModifier(modUuid, "synth_set_" + set.getId() + "_" + tier + "_" + spec.getName(),
                        spec.getAmount(), spec.getOperation());
            }
            inst.addModifier(modifier);
        }
    }

    private void removeTierAttributes(Player player, ItemSet set, int tier, SetBonus bonus) {
        if (bonus.getAttributes() == null || bonus.getAttributes().isEmpty()) return;

        for (ItemDefinition.AttributeSpec spec : bonus.getAttributes()) {
            Attribute attr = ItemDefinition.parseAttribute(spec.getAttributeName());
            if (attr == null) continue;

            AttributeInstance inst = player.getAttribute(attr);
            if (inst != null) {
                UUID modUuid = generateModifierUUID(set.getId(), tier, spec.getName(), spec.getAttributeName());
                inst.removeModifier(modUuid);
            }
        }
    }

    /**
     * Cleans up all set bonus attribute modifiers applied to a player.
     */
    public void cleanupPlayer(Player player) {
        if (player == null) return;
        Map<String, Set<Integer>> setTiers = activePlayerSetTiers.remove(player.getUniqueId());
        if (setTiers == null) return;

        for (Map.Entry<String, Set<Integer>> entry : setTiers.entrySet()) {
            ItemSet set = getSet(entry.getKey());
            if (set == null) continue;

            for (int tier : entry.getValue()) {
                SetBonus bonus = set.getBonuses().get(tier);
                if (bonus != null) {
                    removeTierAttributes(player, set, tier, bonus);
                }
            }
        }
    }

    private UUID generateModifierUUID(String setId, int tier, String specName, String attrName) {
        String key = "synthetix:set:" + setId + ":tier:" + tier + ":" + specName + ":" + attrName;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generates localized/formatted set tooltip lines for an item.
     */
    public List<Component> formatSetLore(ItemDefinition def, Player viewer) {
        List<Component> lore = new ArrayList<>();
        ItemSet set = getSetOfItem(def.getId());
        if (set == null) return lore;

        int activePieces = viewer != null ? countEquippedPieces(viewer, set) : 0;
        int totalPieces = set.getPieces().size();

        lore.add(Component.empty());
        lore.add(TextUtil.parse("<dark_gray>──────</dark_gray> " + set.getDisplayName() + " <dark_gray>(" + activePieces + "/" + totalPieces + ") ──────</dark_gray>"));

        for (Map.Entry<Integer, SetBonus> entry : set.getBonuses().entrySet()) {
            int tier = entry.getKey();
            SetBonus bonus = entry.getValue();
            boolean active = activePieces >= tier;

            String statusPrefix = active ? "<green>✔ </green>" : "<dark_gray>✖ </dark_gray>";
            String line = bonus.getLoreLine();
            if (line == null || line.isEmpty()) {
                line = tier + " Pieces Bonus";
            }
            lore.add(TextUtil.parse(statusPrefix + (active ? line : "<dark_gray>" + line + "</dark_gray>")));
        }

        return lore;
    }

    // =========================================================================
    // PERSISTENCE (sets.yml)
    // =========================================================================

    public void loadAll() {
        setRegistry.clear();
        File file = new File(plugin.getDataFolder(), "sets.yml");
        if (!file.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection sec = config.getConfigurationSection(key);
            if (sec != null) {
                ItemSet set = ItemSet.loadFromConfig(sec);
                if (set != null) {
                    setRegistry.put(set.getId().toLowerCase(Locale.ROOT), set);
                }
            }
        }
        plugin.getLogger().info("Loaded " + setRegistry.size() + " equipment set(s) from sets.yml.");
    }

    public void saveAll() {
        File file = new File(plugin.getDataFolder(), "sets.yml");
        YamlConfiguration config = new YamlConfiguration();
        for (ItemSet set : setRegistry.values()) {
            org.bukkit.configuration.ConfigurationSection sec = config.createSection(set.getId());
            set.saveToConfig(sec);
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save sets.yml: " + e.getMessage());
        }
    }
}
