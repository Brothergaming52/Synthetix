package org.bg52.synthetix.item;

import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.api.event.SynthetixItemBreakBlockEvent;
import org.bg52.synthetix.api.event.SynthetixItemConsumeEvent;
import org.bg52.synthetix.api.event.SynthetixItemDamageEntityEvent;
import org.bg52.synthetix.api.event.SynthetixItemInteractEvent;
import org.bg52.synthetix.item.food.FoodComponentSpec;
import org.bg52.synthetix.item.food.FoodEffectSpec;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Listens for Minecraft player interactions with Synthetix custom items and emits high-level Synthetix custom events.
 * Relies on Paper's native Food & Consumable Data Components for authentic eating speed, animations, slowdown, and sound.
 */
public class ItemInteractListener implements Listener {

    private final SynthetixPlugin plugin;

    public ItemInteractListener(SynthetixPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null) return;

        String id = plugin.getItemRegistry().getItemId(item);
        if (id == null) return;

        ItemDefinition def = plugin.getItemRegistry().get(id);
        EquipmentSlot hand = event.getHand() != null ? event.getHand() : EquipmentSlot.HAND;

        // Check food cooldown
        if (def != null && def.getFood() != null && def.getFood().getEatCooldown() > 0) {
            String cdKey = def.getFood().getCooldownGroup() != null && !def.getFood().getCooldownGroup().trim().isEmpty()
                    ? def.getFood().getCooldownGroup().trim() : id;
            if (plugin.getTriggerManager().isPlayerOnCooldown(event.getPlayer().getUniqueId(), cdKey)
                    || org.bg52.synthetix.item.component.ComponentHandler.hasItemCooldown(event.getPlayer(), item)) {
                if (event.getAction().name().contains("RIGHT")) {
                    event.setCancelled(true);
                    double rem = plugin.getTriggerManager().getPlayerCooldownRemaining(event.getPlayer().getUniqueId(), cdKey);
                    if (rem > 0) {
                        event.getPlayer().sendActionBar(org.bg52.synthetix.util.TextUtil.parse("<red>Item is on cooldown! (" + String.format(java.util.Locale.ROOT, "%.1f", rem) + "s)</red>"));
                    }
                    return;
                }
            }
        }

        SynthetixItemInteractEvent customEvent = new SynthetixItemInteractEvent(
                event.getPlayer(), def, item, event.getAction(), hand, event.getClickedBlock(), event.getBlockFace()
        );

        Bukkit.getPluginManager().callEvent(customEvent);

        if (customEvent.isCancelled()) {
            event.setCancelled(true);
        } else {
            if (customEvent.useClickedBlock() != org.bukkit.event.Event.Result.DEFAULT) {
                event.setUseInteractedBlock(customEvent.useClickedBlock());
            }
            if (customEvent.useItemInHand() != org.bukkit.event.Event.Result.DEFAULT) {
                event.setUseItemInHand(customEvent.useItemInHand());
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        String id = plugin.getItemRegistry().getItemId(item);
        if (id == null) return;

        ItemDefinition def = plugin.getItemRegistry().get(id);
        SynthetixItemDamageEntityEvent customEvent = new SynthetixItemDamageEntityEvent(
                player, event.getEntity(), def, item, event.getDamage()
        );

        Bukkit.getPluginManager().callEvent(customEvent);

        if (customEvent.isCancelled()) {
            event.setCancelled(true);
        } else {
            event.setDamage(customEvent.getDamage());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        String id = plugin.getItemRegistry().getItemId(item);
        if (id == null) return;

        ItemDefinition def = plugin.getItemRegistry().get(id);
        SynthetixItemBreakBlockEvent customEvent = new SynthetixItemBreakBlockEvent(
                player, event.getBlock(), def, item, event.isDropItems()
        );

        Bukkit.getPluginManager().callEvent(customEvent);

        if (customEvent.isCancelled()) {
            event.setCancelled(true);
        } else {
            event.setDropItems(customEvent.isDropItems());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        String id = plugin.getItemRegistry().getItemId(item);
        if (id == null) return;

        ItemDefinition def = plugin.getItemRegistry().get(id);

        // Check if item is on cooldown
        if (def != null && def.getFood() != null && def.getFood().getEatCooldown() > 0) {
            String cdKey = def.getFood().getCooldownGroup() != null && !def.getFood().getCooldownGroup().trim().isEmpty()
                    ? def.getFood().getCooldownGroup().trim() : id;
            if (plugin.getTriggerManager().isPlayerOnCooldown(event.getPlayer().getUniqueId(), cdKey)
                    || org.bg52.synthetix.item.component.ComponentHandler.hasItemCooldown(event.getPlayer(), item)) {
                event.setCancelled(true);
                return;
            }
        }

        SynthetixItemConsumeEvent customEvent = new SynthetixItemConsumeEvent(
                event.getPlayer(), def, item
        );

        Bukkit.getPluginManager().callEvent(customEvent);

        if (customEvent.isCancelled()) {
            event.setCancelled(true);
            return;
        }

        // Apply custom food effects & cooldown if consumed
        if (def != null && def.getFood() != null) {
            applyFoodEffectsAndCooldown(event.getPlayer(), def.getFood(), item, def.getId());
        }
    }

    private void applyFoodEffectsAndCooldown(Player player, FoodComponentSpec food, ItemStack item, String itemId) {
        if (food.getEffects() != null) {
            for (FoodEffectSpec effectSpec : food.getEffects()) {
                if (ThreadLocalRandom.current().nextDouble() <= effectSpec.getProbability()) {
                    PotionEffect effect = effectSpec.toPotionEffect();
                    if (effect != null) {
                        player.addPotionEffect(effect);
                    }
                }
            }
        }

        if (food.getEatCooldown() > 0) {
            int ticks = (int) (food.getEatCooldown() * 20);
            org.bg52.synthetix.item.component.ComponentHandler.setItemCooldown(player, item, ticks);
            String cdKey = food.getCooldownGroup() != null && !food.getCooldownGroup().trim().isEmpty()
                    ? food.getCooldownGroup().trim() : (itemId != null ? itemId : "item_consume");
            plugin.getTriggerManager().setPlayerCooldown(player.getUniqueId(), cdKey, (long) (food.getEatCooldown() * 1000L));
        }
    }
}
