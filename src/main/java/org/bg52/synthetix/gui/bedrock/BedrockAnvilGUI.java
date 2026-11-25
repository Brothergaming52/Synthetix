package org.bg52.synthetix.gui.bedrock;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.recipe.RecipeType;
import org.bg52.synthetix.recipe.SynthetixRecipe;
import org.bg52.synthetix.scheduler.PlatformScheduler;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.*;

/**
 * Custom 3-row Chest GUI for Bedrock players using Anvils.
 * Eliminates Geyser/Floodgate packet desyncs and supports both custom Synthetix anvil recipes
 * and vanilla repair/enchant combinations with full touch friendly feedback.
 *
 * Symmetrical Layout:
 * - Slot 10: Input 1 (Base item)
 * - Slot 11: Plus Icon [+]
 * - Slot 12: Input 2 (Sacrifice item)
 * - Slot 14: Recipe Status Icon (Error / Arrow)
 * - Slot 16: Result item
 * - Slot 25: XP Cost Indicator (Lime = Affordable, Red = Expensive/Invalid)
 * - Slot 8:  Rename Button (Bypasses to vanilla Anvil GUI for renaming)
 */
public class BedrockAnvilGUI implements Listener, InventoryHolder {

    private final SynthetixPlugin plugin;
    private final Component title = TextUtil.parse("<dark_gray>Anvil (Bedrock Edition)</dark_gray>");
    private final Map<UUID, AnvilState> activeSessions = new HashMap<>();

    public BedrockAnvilGUI(SynthetixPlugin plugin) {
        this.plugin = plugin;
    }

    private static class AnvilState {
        int cost = 0;
        ItemStack result = null;
        boolean isCustomRecipe = false;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(this, 27, title);

        // Fill decorative background
        ItemStack filler = createItem(Material.BLACK_STAINED_GLASS_PANE, "<gray> </gray>");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        // Interactive input & output slots
        inv.setItem(10, null);
        inv.setItem(12, null);
        inv.setItem(16, null);

        // Plus icon
        inv.setItem(11, createIconItem(10035, "<yellow><b>+</b></yellow>", "<gray>Place sacrifice item or book in next slot</gray>"));

        // Status arrow/error in slot 14
        inv.setItem(14, createErrorIcon());

        // Rename button in top right corner (Slot 8)
        ItemStack renameBtn = createItem(Material.ANVIL, "<yellow><b>Rename Item</b></yellow>",
                "<gray>Click to open vanilla Anvil</gray>",
                "<gray>interface for item renaming.</gray>");
        inv.setItem(8, renameBtn);

        // Cost indicator in slot 25
        updateCostIndicator(inv, player, 0, false, null);

        activeSessions.put(player.getUniqueId(), new AnvilState());
        player.openInventory(inv);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getInventory().getHolder() != this) return;

        Inventory inv = event.getInventory();
        int slot = event.getRawSlot();

        // Player Inventory Interactivity (slot >= 27)
        if (slot >= 27) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem == null || clickedItem.getType().isAir()) {
                    return;
                }

                int[] targetSlots = new int[]{10, 12};
                for (int tSlot : targetSlots) {
                    ItemStack slotItem = inv.getItem(tSlot);
                    if (slotItem == null || slotItem.getType().isAir()) {
                        inv.setItem(tSlot, clickedItem);
                        event.setCurrentItem(null);
                        player.updateInventory();
                        PlatformScheduler.get().runEntity(player, () -> {
                            updateRecipe(inv, player);
                            player.updateInventory();
                        });
                        return;
                    }
                }
            } else {
                PlatformScheduler.get().runEntity(player, () -> {
                    updateRecipe(inv, player);
                    player.updateInventory();
                });
            }
            return;
        }

        // Top right slot: Open real Anvil GUI for renaming
        if (slot == 8) {
            event.setCancelled(true);
            returnItemsAndCloseForRename(player, inv);
            return;
        }

        boolean isInputSlot = (slot == 10 || slot == 12);
        boolean isResultSlot = (slot == 16);

        if (!isInputSlot && !isResultSlot) {
            event.setCancelled(true);
            return;
        }

        if (isInputSlot) {
            event.setCancelled(false); // Uncancel for Geyser compatibility
            ItemStack currentSlotItem = inv.getItem(slot);
            if (event.isShiftClick()) {
                if (currentSlotItem != null && !currentSlotItem.getType().isAir()) {
                    PlatformScheduler.get().runEntity(player, () -> {
                        updateRecipe(inv, player);
                        player.updateInventory();
                    });
                } else {
                    event.setCancelled(true);
                }
                return;
            }
            PlatformScheduler.get().runEntity(player, () -> {
                updateRecipe(inv, player);
                player.updateInventory();
            });
            return;
        }

        if (isResultSlot) {
            event.setCancelled(true); // Always cancel original event to prevent ghost duplication
            AnvilState state = activeSessions.get(player.getUniqueId());
            ItemStack resultItem = inv.getItem(16);

            if (resultItem == null || resultItem.getType().isAir() || state == null) {
                return;
            }

            int cost = state.cost;
            if (player.getGameMode() != GameMode.CREATIVE && player.getLevel() < cost) {
                player.sendMessage(TextUtil.parse("<red>You do not have enough experience levels! Required: " + cost + "</red>"));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            // Transfer result to player
            if (event.isShiftClick()) {
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(resultItem.clone());
                if (!leftover.isEmpty()) {
                    player.sendMessage(TextUtil.parse("<red>Your inventory is full!</red>"));
                    return;
                }
            } else {
                ItemStack cursor = player.getItemOnCursor();
                if (cursor != null && !cursor.getType().isAir()) {
                    return;
                }
                player.setItemOnCursor(resultItem.clone());
            }

            // Deduct cost
            if (player.getGameMode() != GameMode.CREATIVE && cost > 0) {
                player.setLevel(player.getLevel() - cost);
            }

            // Consume inputs
            consumeInput(inv, 10);
            consumeInput(inv, 12);

            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);

            updateRecipe(inv, player);
            player.updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() != this) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < 27 && rawSlot != 10 && rawSlot != 12) {
                event.setCancelled(true);
                return;
            }
        }

        PlatformScheduler.get().runEntity(player, () -> {
            updateRecipe(event.getInventory(), player);
            player.updateInventory();
        });
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() != this) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        ItemStack item1 = event.getInventory().getItem(10);
        ItemStack item2 = event.getInventory().getItem(12);

        event.getInventory().setItem(10, null);
        event.getInventory().setItem(12, null);
        event.getInventory().setItem(14, null);
        event.getInventory().setItem(16, null);

        activeSessions.remove(player.getUniqueId());

        returnItemToPlayer(player, item1);
        returnItemToPlayer(player, item2);
    }

    private void updateRecipe(Inventory inv, Player player) {
        ItemStack left = inv.getItem(10);
        ItemStack right = inv.getItem(12);

        AnvilState state = activeSessions.computeIfAbsent(player.getUniqueId(), k -> new AnvilState());

        if (left == null || left.getType().isAir()) {
            inv.setItem(14, createErrorIcon());
            inv.setItem(16, null);
            state.cost = 0;
            state.result = null;
            state.isCustomRecipe = false;
            updateCostIndicator(inv, player, 0, false, null);
            return;
        }

        // 1. Check Synthetix Custom Anvil Recipes
        if (plugin.getRecipeManager() != null) {
            List<SynthetixRecipe> anvilRecipes = plugin.getRecipeManager().getRecipesByType(RecipeType.ANVIL);
            for (SynthetixRecipe recipe : anvilRecipes) {
                if (recipe.getAnvilLeft() == null || recipe.getAnvilRight() == null) continue;

                if (recipe.getAnvilLeft().test(left, plugin) && recipe.getAnvilRight().test(right, plugin)) {
                    ItemStack result = recipe.buildResult(plugin);

                    if (recipe.isAnvilPreserveName() && left.hasItemMeta() && left.getItemMeta().hasDisplayName()) {
                        ItemMeta rMeta = result.getItemMeta();
                        if (rMeta != null) {
                            rMeta.displayName(left.getItemMeta().displayName());
                            result.setItemMeta(rMeta);
                        }
                    }

                    int cost = recipe.getAnvilCost();
                    state.cost = cost;
                    state.result = result;
                    state.isCustomRecipe = true;

                    inv.setItem(14, createArrowIcon());
                    inv.setItem(16, result);
                    updateCostIndicator(inv, player, cost, true, result);
                    return;
                }
            }
        }

        // 2. Fallback to Vanilla Anvil Combination
        AnvilResult vanillaResult = calculateVanillaAnvil(left, right);
        if (vanillaResult != null && vanillaResult.result != null) {
            ItemStack result = vanillaResult.result;
            state.cost = vanillaResult.cost;
            state.result = result;
            state.isCustomRecipe = false;

            inv.setItem(14, createArrowIcon());
            inv.setItem(16, result);
            updateCostIndicator(inv, player, vanillaResult.cost, true, result);
        } else {
            inv.setItem(14, createErrorIcon());
            inv.setItem(16, null);
            state.cost = 0;
            state.result = null;
            state.isCustomRecipe = false;
            updateCostIndicator(inv, player, 0, false, null);
        }
    }

    private void updateCostIndicator(Inventory inv, Player player, int cost, boolean validRecipe, ItemStack result) {
        if (!validRecipe || result == null) {
            ItemStack indicator = createItem(Material.RED_STAINED_GLASS_PANE,
                    "<red><b>Invalid Combination</b></red>",
                    "<gray>Place compatible items in the slots above.</gray>");
            inv.setItem(25, indicator);
            return;
        }

        if (cost <= 0) {
            cost = 1;
        }

        boolean canAfford = player.getGameMode() == GameMode.CREATIVE || player.getLevel() >= cost;

        if (canAfford) {
            ItemStack indicator = createItem(Material.LIME_STAINED_GLASS_PANE,
                    "<green><b>Enchantment Cost: " + cost + " Levels</b></green>",
                    "<gray>Your Level: </gray><yellow>" + player.getLevel() + "</yellow>",
                    "<green>Click the result item to combine!</green>");
            inv.setItem(25, indicator);
        } else {
            ItemStack indicator = createItem(Material.RED_STAINED_GLASS_PANE,
                    "<red><b>Too Expensive! Cost: " + cost + " Levels</b></red>",
                    "<gray>Your Level: </gray><red>" + player.getLevel() + "</red>",
                    "<red>You need " + (cost - player.getLevel()) + " more levels.</red>");
            inv.setItem(25, indicator);
        }
    }

    private void returnItemsAndCloseForRename(Player player, Inventory inv) {
        ItemStack item1 = inv.getItem(10);
        ItemStack item2 = inv.getItem(12);

        inv.setItem(10, null);
        inv.setItem(12, null);
        inv.setItem(14, null);
        inv.setItem(16, null);

        activeSessions.remove(player.getUniqueId());

        returnItemToPlayer(player, item1);
        returnItemToPlayer(player, item2);

        player.closeInventory();
        player.setMetadata("synthetix_bypass_anvil", new FixedMetadataValue(plugin, true));

        PlatformScheduler.get().runEntity(player, () -> {
            try {
                player.openAnvil(player.getLocation(), true);
            } catch (Exception e) {
                player.openInventory(Bukkit.createInventory(player, InventoryType.ANVIL, TextUtil.parse("<dark_gray>Repair & Name</dark_gray>")));
            }
        });
    }

    private static class AnvilResult {
        final ItemStack result;
        final int cost;

        AnvilResult(ItemStack result, int cost) {
            this.result = result;
            this.cost = cost;
        }
    }

    private AnvilResult calculateVanillaAnvil(ItemStack left, ItemStack right) {
        if (left == null || left.getType().isAir()) return null;

        ItemStack result = left.clone();
        int baseCost = 0;
        int enchantmentCost = 0;

        ItemMeta meta = result.getItemMeta();

        if (right != null && !right.getType().isAir()) {
            // Repairing with identical item or material
            if (meta instanceof Damageable damageable) {
                if (damageable.hasDamage() && right.getType() == left.getType()) {
                    int maxDurability = left.getType().getMaxDurability();
                    Damageable rightDamageable = (Damageable) (right.hasItemMeta() ? right.getItemMeta() : null);
                    int rightDamage = rightDamageable != null ? rightDamageable.getDamage() : 0;
                    int repairDurability = (maxDurability - rightDamage) + (int) (maxDurability * 0.12);
                    int newDamage = Math.max(0, damageable.getDamage() - repairDurability);
                    damageable.setDamage(newDamage);
                    baseCost += 2;
                }
            }

            // Combining enchantments from right item or enchanted book
            Map<Enchantment, Integer> targetEnchants = new HashMap<>(result.getEnchantments());
            Map<Enchantment, Integer> sourceEnchants;

            if (right.getType() == Material.ENCHANTED_BOOK && right.hasItemMeta()) {
                EnchantmentStorageMeta bookMeta = (EnchantmentStorageMeta) right.getItemMeta();
                sourceEnchants = bookMeta != null ? bookMeta.getStoredEnchants() : Collections.emptyMap();
            } else {
                sourceEnchants = right.getEnchantments();
            }

            boolean enchantmentAdded = false;
            for (Map.Entry<Enchantment, Integer> entry : sourceEnchants.entrySet()) {
                Enchantment ench = entry.getKey();
                int sourceLevel = entry.getValue();

                if (right.getType() == Material.ENCHANTED_BOOK || ench.canEnchantItem(result)) {
                    int currentLevel = targetEnchants.getOrDefault(ench, 0);
                    int newLevel;

                    if (currentLevel == sourceLevel) {
                        newLevel = Math.min(sourceLevel + 1, ench.getMaxLevel());
                    } else {
                        newLevel = Math.max(currentLevel, sourceLevel);
                    }

                    if (newLevel > currentLevel) {
                        meta.addEnchant(ench, newLevel, true);
                        enchantmentCost += newLevel * 2;
                        enchantmentAdded = true;
                    }
                }
            }

            if (!enchantmentAdded && baseCost == 0) {
                return null;
            }
        } else {
            return null;
        }

        result.setItemMeta(meta);
        return new AnvilResult(result, Math.max(1, baseCost + enchantmentCost));
    }

    private void consumeInput(Inventory inv, int slot) {
        ItemStack item = inv.getItem(slot);
        if (item != null && !item.getType().isAir()) {
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
                inv.setItem(slot, item);
            } else {
                inv.setItem(slot, null);
            }
        }
    }

    private void returnItemToPlayer(Player player, ItemStack item) {
        if (item != null && !item.getType().isAir()) {
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
    }

    private ItemStack createErrorIcon() {
        return createIconItem(10034, "<red><b>Invalid Combination</b></red>", "<gray>Place compatible items in slots 10 & 12.</gray>");
    }

    private ItemStack createArrowIcon() {
        return createIconItem(10033, "<green><b>Combination Ready</b></green>", "<gray>Click the result item to combine!</gray>");
    }

    private ItemStack createIconItem(int customModelData, String name, String... lore) {
        ItemStack stack = createItem(Material.PAPER, name, lore);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(customModelData);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.parse(name));
            if (lore.length > 0) {
                List<Component> loreList = new ArrayList<>();
                for (String line : lore) {
                    loreList.add(TextUtil.parse(line));
                }
                meta.lore(loreList);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }
}

