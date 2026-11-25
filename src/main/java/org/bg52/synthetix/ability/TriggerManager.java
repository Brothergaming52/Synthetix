package org.bg52.synthetix.ability;

import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.item.ItemDefinition;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Universal Trigger and Ability Manager.
 * Handles interaction detection, cooldown tracking, durability costs,
 * permission checks, and provides the public API for 3rd-party custom triggers.
 */
public class TriggerManager implements Listener {

    private final SynthetixPlugin plugin;
    private final Map<String, CustomItemTrigger> customTriggers;
    // Map: Player UUID -> (CooldownKey -> ExpireTimestampMs)
    private final Map<UUID, Map<String, Long>> cooldownMap;
    // Track recent unequip triggers to prevent double firing between click & armor change
    private final Map<String, Long> recentUnequips;

    public TriggerManager(SynthetixPlugin plugin) {
        this.plugin = plugin;
        this.customTriggers = new ConcurrentHashMap<>();
        this.cooldownMap = new ConcurrentHashMap<>();
        this.recentUnequips = new ConcurrentHashMap<>();
    }

    /**
     * Registers a custom trigger from an external plugin (e.g. CuriosPaper).
     */
    public void registerTrigger(CustomItemTrigger trigger) {
        if (trigger != null) {
            this.customTriggers.put(trigger.getId().toLowerCase(Locale.ROOT), trigger);
        }
    }

    public CustomItemTrigger getTrigger(String id) {
        if (id == null) return null;
        return this.customTriggers.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<String> getRegisteredTriggerIds() {
        Set<String> set = new LinkedHashSet<>();
        set.add("on-right-click");
        set.add("on-left-click");
        set.add("on-shift-right-click");
        set.add("on-shift-left-click");
        set.add("on-hit-entity");
        set.add("on-take-damage");
        set.add("on-kill-entity");
        set.add("on-break-block");
        set.add("on-consume");
        set.add("on-shoot");
        set.add("on-sneak-toggle");
        set.add("on-drop");
        set.add("on-swap-hand");
        set.add("on-equip");
        set.add("on-unequip");
        set.add("on-jump");
        set.add("on-fish");
        set.add("on-respawn");
        set.addAll(this.customTriggers.keySet());
        return set;
    }

    /**
     * Helper to manually trigger an equip event (e.g. from CuriosPaper accessory slots).
     */
    public boolean fireEquip(Player player, ItemStack item) {
        return fireTrigger("on-equip", player, item, null, null);
    }

    /**
     * Helper to manually trigger an unequip event (e.g. from CuriosPaper accessory slots).
     */
    public boolean fireUnequip(Player player, ItemStack item) {
        return fireTrigger("on-unequip", player, item, null, null);
    }

    /**
     * Fires a trigger for a player and item.
     *
     * @return true if an ability activated
     */
    public boolean fireTrigger(String triggerId, Player player, ItemStack item, Event context, org.bukkit.entity.Entity targetEntity) {
        if (player == null || item == null || triggerId == null) return false;
        if (plugin.getSettingsManager() != null && !plugin.getSettingsManager().isFeatureEnabled(org.bg52.synthetix.settings.GlobalFeature.ITEM_ABILITIES)) {
            return false;
        }

        String itemId = plugin.getItemRegistry().getItemId(item);
        if (itemId == null) return false;

        ItemDefinition def = plugin.getItemRegistry().get(itemId);
        if (def == null || def.getAbilities() == null) return false;

        AbilitySpec ability = def.getAbilities().get(triggerId.toLowerCase(Locale.ROOT));
        if (ability == null) return false;

        // Prevent rapid double-firing of on-unequip within 500ms
        if ("on-unequip".equalsIgnoreCase(triggerId)) {
            String uKey = player.getUniqueId() + ":" + itemId;
            long now = System.currentTimeMillis();
            if (now - recentUnequips.getOrDefault(uKey, 0L) < 500L) {
                return false;
            }
            recentUnequips.put(uKey, now);
        }

        // 1. Evaluate custom trigger condition if registered
        CustomItemTrigger custom = customTriggers.get(triggerId.toLowerCase(Locale.ROOT));
        if (custom != null && !custom.evaluate(player, item, context)) {
            return false;
        }

        // 2. Evaluate built-in conditions (e.g. permission: synthetix.ability.xxx)
        if (ability.getConditions() != null) {
            for (String cond : ability.getConditions()) {
                String c = cond.trim();
                if (c.startsWith("permission:")) {
                    String perm = c.substring(11).trim();
                    if (!player.hasPermission(perm)) return false;
                } else if (c.equalsIgnoreCase("sneaking:true") && !player.isSneaking()) {
                    return false;
                } else if (c.equalsIgnoreCase("sneaking:false") && player.isSneaking()) {
                    return false;
                }
            }
        }

        // 3. Cooldown check
        long now = System.currentTimeMillis();
        String cdKey = itemId + ":" + triggerId;
        Map<String, Long> pCds = cooldownMap.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());
        Long expireTime = pCds.get(cdKey);

        if (expireTime != null && expireTime > now) {
            double remainingSec = (expireTime - now) / 1000.0;
            if (ability.getCooldownMessage() != null && !ability.getCooldownMessage().isEmpty()) {
                String msg = ability.getCooldownMessage().replace("%remaining%", String.format(Locale.ROOT, "%.1f", remainingSec));
                player.sendMessage(TextUtil.parse(msg));
            }
            return false;
        }

        // 4. Probability / Chance check
        if (ability.getChance() < 1.0) {
            if (ThreadLocalRandom.current().nextDouble() > ability.getChance()) {
                return false;
            }
        }

        // 5. Cooldown apply
        if (ability.getCooldown() > 0) {
            pCds.put(cdKey, now + (long) (ability.getCooldown() * 1000L));
        }

        // 6. Cost Durability
        if (ability.getCostDurability() > 0 && !def.isUnbreakable()) {
            if (item.getItemMeta() instanceof Damageable damageable) {
                int newDamage = damageable.getDamage() + ability.getCostDurability();
                int maxDamage = damageable.hasMaxDamage() ? damageable.getMaxDamage() : item.getType().getMaxDurability();
                if (maxDamage > 0 && newDamage >= maxDamage) {
                    item.setAmount(item.getAmount() - 1);
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                } else {
                    damageable.setDamage(newDamage);
                    item.setItemMeta(damageable);
                }
            }
        }

        // 7. Execute Actions
        if (ability.getActions() != null) {
            for (String action : ability.getActions()) {
                ActionExecutor.executeAction(action, player, targetEntity, def.getId());
            }
        }

        return true;
    }

    // =========================================================================
    // BUILT-IN BUKKIT INTERACTION EVENT HANDLERS
    // =========================================================================

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null) {
            item = event.getPlayer().getInventory().getItemInMainHand();
        }
        if (item == null || item.getType().isAir()) return;
        Player player = event.getPlayer();

        boolean shift = player.isSneaking();
        Action action = event.getAction();

        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            if (shift) {
                if (fireTrigger("on-shift-right-click", player, item, event, null)) {
                    event.setCancelled(true);
                    return;
                }
            }
            if (fireTrigger("on-right-click", player, item, event, null)) {
                event.setCancelled(true);
            }
        } else if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            if (shift) {
                if (fireTrigger("on-shift-left-click", player, item, event, null)) {
                    event.setCancelled(true);
                    return;
                }
            }
            if (fireTrigger("on-left-click", player, item, event, null)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Bearer hits an entity
        if (event.getDamager() instanceof Player player) {
            ItemStack item = player.getInventory().getItemInMainHand();
            fireTrigger("on-hit-entity", player, item, event, event.getEntity());
        }

        // Bearer takes damage
        if (event.getEntity() instanceof Player bearer) {
            ItemStack main = bearer.getInventory().getItemInMainHand();
            fireTrigger("on-take-damage", bearer, main, event, event.getDamager());

            // Also check worn armor pieces
            for (ItemStack armor : bearer.getInventory().getArmorContents()) {
                if (armor != null) {
                    fireTrigger("on-take-damage", bearer, armor, event, event.getDamager());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer != null) {
            ItemStack item = killer.getInventory().getItemInMainHand();
            fireTrigger("on-kill-entity", killer, item, event, entity);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        fireTrigger("on-break-block", player, item, event, null);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        fireTrigger("on-consume", player, item, event, null);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player) {
            ItemStack bow = event.getBow();
            if (bow != null) {
                fireTrigger("on-shoot", player, bow, event, event.getProjectile());
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onSneakToggle(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        fireTrigger("on-sneak-toggle", player, item, event, null);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemDrop().getItemStack();
        fireTrigger("on-drop", player, item, event, null);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack main = event.getMainHandItem();
        if (main != null) {
            fireTrigger("on-swap-hand", player, main, event, null);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Player clicking their armor slot to unequip armor
        if (event.getSlotType() == org.bukkit.event.inventory.InventoryType.SlotType.ARMOR) {
            ItemStack currentArmor = event.getCurrentItem();
            if (currentArmor != null && !currentArmor.getType().isAir()) {
                fireTrigger("on-unequip", player, currentArmor, event, null);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onArmorChange(com.destroystokyo.paper.event.player.PlayerArmorChangeEvent event) {
        Player player = event.getPlayer();
        ItemStack oldItem = event.getOldItem();
        ItemStack newItem = event.getNewItem();

        if (oldItem != null && !oldItem.getType().isAir()) {
            fireTrigger("on-unequip", player, oldItem, event, null);
        }
        if (newItem != null && !newItem.getType().isAir()) {
            fireTrigger("on-equip", player, newItem, event, null);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onJump(com.destroystokyo.paper.event.player.PlayerJumpEvent event) {
        Player player = event.getPlayer();
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main != null && !main.getType().isAir()) {
            fireTrigger("on-jump", player, main, event, null);
        }
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && !armor.getType().isAir()) {
                fireTrigger("on-jump", player, armor, event, null);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onFish(org.bukkit.event.player.PlayerFishEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item != null && !item.getType().isAir()) {
            fireTrigger("on-fish", player, item, event, event.getCaught());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && !armor.getType().isAir()) {
                fireTrigger("on-respawn", player, armor, event, null);
            }
        }
    }

    public boolean isPlayerOnCooldown(UUID playerUuid, String cooldownKey) {
        if (playerUuid == null || cooldownKey == null) return false;
        Map<String, Long> pCds = cooldownMap.get(playerUuid);
        if (pCds == null) return false;
        Long expire = pCds.get(cooldownKey.toLowerCase(Locale.ROOT));
        return expire != null && expire > System.currentTimeMillis();
    }

    public void setPlayerCooldown(UUID playerUuid, String cooldownKey, long durationMs) {
        if (playerUuid == null || cooldownKey == null || durationMs <= 0) return;
        Map<String, Long> pCds = cooldownMap.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>());
        pCds.put(cooldownKey.toLowerCase(Locale.ROOT), System.currentTimeMillis() + durationMs);
    }

    public double getPlayerCooldownRemaining(UUID playerUuid, String cooldownKey) {
        if (playerUuid == null || cooldownKey == null) return 0.0;
        Map<String, Long> pCds = cooldownMap.get(playerUuid);
        if (pCds == null) return 0.0;
        Long expire = pCds.get(cooldownKey.toLowerCase(Locale.ROOT));
        if (expire == null) return 0.0;
        long diff = expire - System.currentTimeMillis();
        return diff > 0 ? diff / 1000.0 : 0.0;
    }
}

