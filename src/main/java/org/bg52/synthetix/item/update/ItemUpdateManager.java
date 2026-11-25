package org.bg52.synthetix.item.update;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.item.ItemDefinition;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Generalized Item Modernization and Rebuilding Engine.
 * Ensures items from earlier plugin versions, legacy CuriosPaper, or older item definitions
 * are transparently rebuilt to modern standards while completely safeguarding all player customizations
 * (anvil renames, player enchantments, durability/damage, repair costs, and dynamic container UUIDs).
 */
public class ItemUpdateManager {

    private final SynthetixPlugin plugin;
    private final List<ItemRebuilder> customRebuilders = new CopyOnWriteArrayList<>();

    private static final NamespacedKey SYNTH_ITEM_ID = new NamespacedKey("synthetix", "item_id");
    private static final NamespacedKey SYNTH_BLOCK_ID = new NamespacedKey("synthetix", "block_id");
    private static final NamespacedKey CURIOS_ITEM_ID = new NamespacedKey("curiospaper", "item_id");
    private static final NamespacedKey PB_ITEM_ID = new NamespacedKey("probablybackpacks", "item_id");
    private static final NamespacedKey P_BACKPACK_ID = new NamespacedKey("p_backpack", "item_id");

    public ItemUpdateManager(SynthetixPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers a custom rebuilder extension (e.g. for backpacks or custom accessories).
     */
    public void registerRebuilder(ItemRebuilder rebuilder) {
        if (rebuilder != null && !customRebuilders.contains(rebuilder)) {
            customRebuilders.add(rebuilder);
        }
    }

    public List<ItemRebuilder> getCustomRebuilders() {
        return Collections.unmodifiableList(customRebuilders);
    }

    /**
     * Resolves the custom item ID from an ItemStack, checking native Synthetix PDC,
     * legacy CuriosPaper/ProbablyBackpacks PDC tags, or fallback model/CMD matching.
     */
    public String resolveItemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();

        if (pdc.has(SYNTH_ITEM_ID, PersistentDataType.STRING)) {
            return pdc.get(SYNTH_ITEM_ID, PersistentDataType.STRING);
        }
        if (pdc.has(CURIOS_ITEM_ID, PersistentDataType.STRING)) {
            return pdc.get(CURIOS_ITEM_ID, PersistentDataType.STRING);
        }
        if (pdc.has(PB_ITEM_ID, PersistentDataType.STRING)) {
            return pdc.get(PB_ITEM_ID, PersistentDataType.STRING);
        }
        if (pdc.has(P_BACKPACK_ID, PersistentDataType.STRING)) {
            return pdc.get(P_BACKPACK_ID, PersistentDataType.STRING);
        }

        // Fallback: Match against registered ItemDefinitions by custom_model_data or item_model
        ItemMeta meta = item.getItemMeta();
        if (meta.hasCustomModelData()) {
            int cmd = meta.getCustomModelData();
            for (ItemDefinition def : plugin.getItemRegistry().getAll()) {
                if (def.getCustomModelData() != null && def.getCustomModelData() == cmd && def.getMaterial() == item.getType()) {
                    return def.getId();
                }
            }
        }

        String itemModel = getItemModelSafe(meta);
        if (itemModel != null && !itemModel.isEmpty()) {
            for (ItemDefinition def : plugin.getItemRegistry().getAll()) {
                if (def.getItemModel() != null && itemModel.equalsIgnoreCase(def.getItemModel())) {
                    return def.getId();
                }
            }
        }

        return null;
    }

    /**
     * Checks if the item stack is outdated and needs rebuilding.
     */
    public boolean isItemOutdated(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }

        // 1. Check custom rebuilders first (e.g. Backpacks)
        for (ItemRebuilder rebuilder : customRebuilders) {
            if (rebuilder.canRebuild(item)) {
                return rebuilder.isOutdated(item);
            }
        }

        // 2. Generalized check against ItemDefinition
        String itemId = resolveItemId(item);
        if (itemId == null) return false;

        ItemDefinition def = plugin.getItemRegistry().get(itemId);
        if (def == null) return false;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Check if material differs
        if (item.getType() != def.getMaterial()) {
            return true;
        }

        // Check missing native Synthetix tags
        if (!pdc.has(SYNTH_ITEM_ID, PersistentDataType.STRING)) {
            return true;
        }

        // Check if item is a custom block and missing block_id tag
        if (plugin.getCustomBlockRegistry() != null && plugin.getCustomBlockRegistry().has(itemId)) {
            if (!pdc.has(SYNTH_BLOCK_ID, PersistentDataType.STRING)) {
                return true;
            }
        }

        // Check CustomModelData
        if (def.getCustomModelData() != null && def.getCustomModelData() > 0) {
            if (!meta.hasCustomModelData() || meta.getCustomModelData() != def.getCustomModelData().intValue()) {
                return true;
            }
        } else if (meta.hasCustomModelData()) {
            return true;
        }

        // Check ItemModel (1.21.2+)
        String currentModel = getItemModelSafe(meta);
        if (def.getItemModel() != null && !def.getItemModel().isEmpty()) {
            if (!def.getItemModel().equalsIgnoreCase(currentModel)) {
                return true;
            }
        } else if (currentModel != null && !currentModel.isEmpty()) {
            return true;
        }

        // Check required tags
        if (def.getTags() != null) {
            for (Map.Entry<String, String> tag : def.getTags().entrySet()) {
                NamespacedKey k = org.bg52.synthetix.item.component.ComponentHandler.parseNamespacedKey(tag.getKey());
                if (k != null && !pdc.has(k, PersistentDataType.STRING)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Rebuilds the item stack to modern definition standards while preserving player customizations.
     */
    public ItemStack rebuildItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return item;
        }

        // 1. Let custom rebuilders handle specialized items first
        for (ItemRebuilder rebuilder : customRebuilders) {
            if (rebuilder.canRebuild(item)) {
                return rebuilder.rebuild(item);
            }
        }

        // 2. Generalized ItemDefinition rebuilding
        String itemId = resolveItemId(item);
        if (itemId == null) return item;

        ItemDefinition def = plugin.getItemRegistry().get(itemId);
        if (def == null) return item;

        int originalAmount = item.getAmount();
        ItemMeta oldMeta = item.getItemMeta();

        // Detect if item had a custom player rename (anvil)
        boolean hasCustomName = false;
        Component customDisplayNameComp = null;
        String customName = null;
        if (oldMeta != null && oldMeta.hasDisplayName()) {
            customDisplayNameComp = oldMeta.displayName();
            String currentDisplayName = oldMeta.getDisplayName();
            String defaultClean = def.getDisplayName() != null
                    ? def.getDisplayName().replaceAll("<[^>]*>", "").replaceAll("§[0-9a-fk-or]", "").trim()
                    : "";
            String currentClean = currentDisplayName.replaceAll("§[0-9a-fk-or]", "").trim();

            if (!currentClean.equalsIgnoreCase(defaultClean)) {
                hasCustomName = true;
                customName = currentDisplayName;
            }
        }

        // Extract player enchantments
        Map<Enchantment, Integer> enchants = oldMeta != null ? new HashMap<>(oldMeta.getEnchants()) : new HashMap<>();

        // Extract damage / durability
        int damage = 0;
        if (oldMeta instanceof Damageable dmg && dmg.hasDamage()) {
            damage = dmg.getDamage();
        }

        // Extract repair cost
        int repairCost = 0;
        if (oldMeta instanceof Repairable rep && rep.hasRepairCost()) {
            repairCost = rep.getRepairCost();
        }

        // Extract dynamic non-template PDC keys
        Map<NamespacedKey, String> preservedStrings = new HashMap<>();
        Map<NamespacedKey, Integer> preservedInts = new HashMap<>();
        if (oldMeta != null) {
            PersistentDataContainer oldPdc = oldMeta.getPersistentDataContainer();
            for (NamespacedKey key : oldPdc.getKeys()) {
                // Skip template keys that are rebuilt
                if (key.getNamespace().equalsIgnoreCase("synthetix") && key.getKey().equals("item_id")) continue;
                if (key.getNamespace().equalsIgnoreCase("synthetix") && key.getKey().equals("block_id")) continue;

                if (oldPdc.has(key, PersistentDataType.STRING)) {
                    preservedStrings.put(key, oldPdc.get(key, PersistentDataType.STRING));
                } else if (oldPdc.has(key, PersistentDataType.INTEGER)) {
                    preservedInts.put(key, oldPdc.get(key, PersistentDataType.INTEGER));
                }
            }
        }

        // Construct fresh modern base from definition
        ItemStack fresh = def.buildItemStack();
        fresh.setAmount(originalAmount);
        ItemMeta freshMeta = fresh.getItemMeta();

        if (freshMeta != null) {
            // Re-apply anvil custom name if customized
            if (hasCustomName) {
                if (customDisplayNameComp != null) {
                    freshMeta.displayName(customDisplayNameComp);
                } else if (customName != null) {
                    freshMeta.setDisplayName(customName);
                }
            }

            // Merge player enchants
            for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
                freshMeta.addEnchant(e.getKey(), e.getValue(), true);
            }

            // Re-apply damage
            if (damage > 0 && freshMeta instanceof Damageable dmg) {
                dmg.setDamage(damage);
            }

            // Re-apply repair cost
            if (repairCost > 0 && freshMeta instanceof Repairable rep) {
                rep.setRepairCost(repairCost);
            }

            // Re-apply dynamic PDC keys
            PersistentDataContainer newPdc = freshMeta.getPersistentDataContainer();
            for (Map.Entry<NamespacedKey, String> e : preservedStrings.entrySet()) {
                newPdc.set(e.getKey(), PersistentDataType.STRING, e.getValue());
            }
            for (Map.Entry<NamespacedKey, Integer> e : preservedInts.entrySet()) {
                newPdc.set(e.getKey(), PersistentDataType.INTEGER, e.getValue());
            }

            // Ensure block_id is set if item represents a custom block
            if (plugin.getCustomBlockRegistry() != null && plugin.getCustomBlockRegistry().has(def.getId())) {
                newPdc.set(SYNTH_BLOCK_ID, PersistentDataType.STRING, def.getId());
            }

            fresh.setItemMeta(freshMeta);
        }

        // Mutate original item in-place
        item.setType(fresh.getType());
        item.setItemMeta(fresh.getItemMeta());
        return item;
    }

    private static String getItemModelSafe(ItemMeta meta) {
        if (meta == null) return null;
        try {
            java.lang.reflect.Method m = meta.getClass().getMethod("getItemModel");
            Object result = m.invoke(meta);
            return result != null ? result.toString() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}

