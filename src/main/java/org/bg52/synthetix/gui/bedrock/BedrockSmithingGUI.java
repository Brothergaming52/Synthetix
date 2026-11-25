package org.bg52.synthetix.gui.bedrock;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.recipe.RecipeType;
import org.bg52.synthetix.recipe.SynthetixRecipe;
import org.bg52.synthetix.scheduler.PlatformScheduler;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Custom 3-row Chest GUI for Bedrock players using Smithing Tables.
 * Prevents Geyser/Floodgate packet drops on complex smithing transforms and supports
 * Synthetix custom smithing recipes as well as vanilla Netherite gear upgrades.
 *
 * Layout:
 * - Slot 10: Template Item [Icon Placeholder]
 * - Slot 11: Base Item [Icon Placeholder]
 * - Slot 12: Addition Item [Icon Placeholder]
 * - Slot 14: Status Arrow / Error Icon
 * - Slot 16: Result Item
 * - Slot 8:  Vanilla Smithing Table Bypass (Armor Trims)
 */
public class BedrockSmithingGUI implements Listener, InventoryHolder {

    private final SynthetixPlugin plugin;
    private final NamespacedKey placeholderKey;
    private final Component title = TextUtil.parse("<dark_gray>Smithing Table (Bedrock Edition)</dark_gray>");
    private final Map<UUID, SmithingState> activeSessions = new HashMap<>();

    public BedrockSmithingGUI(SynthetixPlugin plugin) {
        this.plugin = plugin;
        this.placeholderKey = new NamespacedKey(plugin, "gui_placeholder");
    }

    private static class SmithingState {
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

        // Placeholders in input slots
        inv.setItem(10, getPlaceholderForSlot(10));
        inv.setItem(11, getPlaceholderForSlot(11));
        inv.setItem(12, getPlaceholderForSlot(12));

        // Recipe indicator arrow/error
        inv.setItem(14, createErrorIcon());
        inv.setItem(16, null);

        // Vanilla Smithing Table button in top right corner (Slot 8)
        ItemStack vanillaBtn = createItem(Material.SMITHING_TABLE, "<yellow><b>Open Vanilla Smithing Table</b></yellow>",
                "<gray>Click to open vanilla Smithing Table</gray>",
                "<gray>interface for armor trimming & upgrades.</gray>");
        inv.setItem(8, vanillaBtn);

        activeSessions.put(player.getUniqueId(), new SmithingState());
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
                if (clickedItem == null || clickedItem.getType().isAir() || isPlaceholder(clickedItem)) {
                    return;
                }

                int targetSlot;
                if (isTemplateItem(clickedItem)) {
                    targetSlot = 10;
                } else if (isAdditionItem(clickedItem)) {
                    targetSlot = 12;
                } else {
                    targetSlot = 11;
                }

                ItemStack slotItem = inv.getItem(targetSlot);
                if (slotItem == null || slotItem.getType().isAir() || isPlaceholder(slotItem)) {
                    inv.setItem(targetSlot, clickedItem);
                    event.setCurrentItem(null);
                    player.updateInventory();
                    PlatformScheduler.get().runEntity(player, () -> {
                        updateRecipe(inv, player);
                        player.updateInventory();
                    });
                }
            } else {
                PlatformScheduler.get().runEntity(player, () -> {
                    updateRecipe(inv, player);
                    player.updateInventory();
                });
            }
            return;
        }

        // Top right slot: Open real Smithing GUI
        if (slot == 8) {
            event.setCancelled(true);
            returnItemsAndCloseForVanillaSmithing(player, inv);
            return;
        }

        boolean isInputSlot = (slot == 10 || slot == 11 || slot == 12);
        boolean isResultSlot = (slot == 16);

        if (!isInputSlot && !isResultSlot) {
            event.setCancelled(true);
            return;
        }

        if (isInputSlot) {
            event.setCancelled(false); // Uncancel for Geyser compatibility
            ItemStack currentSlotItem = inv.getItem(slot);
            ItemStack cursorItem = event.getCursor();

            boolean slotHasPlaceholder = isPlaceholder(currentSlotItem);
            boolean cursorHasItem = (cursorItem != null && !cursorItem.getType().isAir());

            if (event.isShiftClick()) {
                if (currentSlotItem != null && !slotHasPlaceholder) {
                    PlatformScheduler.get().runEntity(player, () -> {
                        ItemStack item = inv.getItem(slot);
                        if (item == null || item.getType().isAir()) {
                            inv.setItem(slot, getPlaceholderForSlot(slot));
                        }
                        updateRecipe(inv, player);
                        player.updateInventory();
                    });
                } else {
                    event.setCancelled(true);
                }
                return;
            }

            if (slotHasPlaceholder) {
                boolean isPlacing = cursorHasItem || (event.getClick().isKeyboardClick()
                        && player.getInventory().getItem(event.getHotbarButton()) != null
                        && !player.getInventory().getItem(event.getHotbarButton()).getType().isAir());
                if (isPlacing) {
                    event.setCurrentItem(null);
                    PlatformScheduler.get().runEntity(player, () -> {
                        updateRecipe(inv, player);
                        player.updateInventory();
                    });
                } else {
                    event.setCancelled(true);
                }
            } else {
                PlatformScheduler.get().runEntity(player, () -> {
                    ItemStack item = inv.getItem(slot);
                    if (item == null || item.getType().isAir()) {
                        inv.setItem(slot, getPlaceholderForSlot(slot));
                    }
                    updateRecipe(inv, player);
                    player.updateInventory();
                });
            }
            return;
        }

        if (isResultSlot) {
            event.setCancelled(true);
            SmithingState state = activeSessions.get(player.getUniqueId());
            ItemStack resultItem = inv.getItem(16);

            if (resultItem == null || resultItem.getType().isAir() || isPlaceholder(resultItem) || state == null) {
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

            try {
                Sound sound = Sound.valueOf("BLOCK_SMITHING_TABLE_USE");
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (Throwable e) {
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
            }

            consumeInputAndRestorePlaceholder(inv, 10);
            consumeInputAndRestorePlaceholder(inv, 11);
            consumeInputAndRestorePlaceholder(inv, 12);

            updateRecipe(inv, player);
            player.updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() != this) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < 27 && rawSlot != 10 && rawSlot != 11 && rawSlot != 12) {
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
        ItemStack item2 = event.getInventory().getItem(11);
        ItemStack item3 = event.getInventory().getItem(12);

        event.getInventory().setItem(10, null);
        event.getInventory().setItem(11, null);
        event.getInventory().setItem(12, null);
        event.getInventory().setItem(14, null);
        event.getInventory().setItem(16, null);

        activeSessions.remove(player.getUniqueId());

        returnItemToPlayer(player, item1);
        returnItemToPlayer(player, item2);
        returnItemToPlayer(player, item3);
    }

    private void returnItemsAndCloseForVanillaSmithing(Player player, Inventory inv) {
        ItemStack item1 = inv.getItem(10);
        ItemStack item2 = inv.getItem(11);
        ItemStack item3 = inv.getItem(12);

        inv.setItem(10, null);
        inv.setItem(11, null);
        inv.setItem(12, null);
        inv.setItem(14, null);
        inv.setItem(16, null);

        activeSessions.remove(player.getUniqueId());

        returnItemToPlayer(player, item1);
        returnItemToPlayer(player, item2);
        returnItemToPlayer(player, item3);

        player.closeInventory();
        player.setMetadata("synthetix_bypass_smithing", new FixedMetadataValue(plugin, true));

        PlatformScheduler.get().runEntity(player, () -> {
            try {
                player.openSmithingTable(player.getLocation(), true);
            } catch (Exception e) {
                try {
                    player.openInventory(Bukkit.createInventory(player, InventoryType.SMITHING, TextUtil.parse("<dark_gray>Upgrade Gear</dark_gray>")));
                } catch (Throwable t) {
                    plugin.getLogger().warning("Could not open vanilla smithing GUI: " + t.getMessage());
                }
            }
        });
    }

    private void updateRecipe(Inventory inv, Player player) {
        ItemStack templateSlot = inv.getItem(10);
        ItemStack baseSlot = inv.getItem(11);
        ItemStack additionSlot = inv.getItem(12);

        ItemStack template = !isPlaceholder(templateSlot) ? templateSlot : null;
        ItemStack base = !isPlaceholder(baseSlot) ? baseSlot : null;
        ItemStack addition = !isPlaceholder(additionSlot) ? additionSlot : null;

        SmithingState state = activeSessions.computeIfAbsent(player.getUniqueId(), k -> new SmithingState());

        if (base == null || base.getType().isAir() || addition == null || addition.getType().isAir()) {
            inv.setItem(14, createErrorIcon());
            inv.setItem(16, null);
            state.result = null;
            state.isCustomRecipe = false;
            return;
        }

        // 1. Search Synthetix Custom Smithing Recipes
        if (plugin.getRecipeManager() != null) {
            List<SynthetixRecipe> smithingRecipes = plugin.getRecipeManager().getRecipesByType(RecipeType.SMITHING_TRANSFORM);
            for (SynthetixRecipe recipe : smithingRecipes) {
                if (recipe.getSmithingTemplate() == null || recipe.getSmithingBase() == null || recipe.getSmithingAddition() == null) continue;

                if (recipe.getSmithingTemplate().test(template, plugin)
                        && recipe.getSmithingBase().test(base, plugin)
                        && recipe.getSmithingAddition().test(addition, plugin)) {
                    ItemStack result = recipe.buildResult(plugin);
                    state.result = result;
                    state.isCustomRecipe = true;

                    if (result != null) {
                        inv.setItem(14, createArrowIcon());
                        inv.setItem(16, result);
                    } else {
                        inv.setItem(14, createErrorIcon());
                        inv.setItem(16, null);
                    }
                    return;
                }
            }
        }

        // 2. Fallback to Vanilla Netherite Gear Upgrades
        ItemStack vanillaResult = calculateVanillaNetheriteUpgrade(template, base, addition);
        if (vanillaResult != null) {
            state.result = vanillaResult;
            state.isCustomRecipe = false;
            inv.setItem(14, createArrowIcon());
            inv.setItem(16, vanillaResult);
            return;
        }

        inv.setItem(14, createErrorIcon());
        inv.setItem(16, null);
        state.result = null;
        state.isCustomRecipe = false;
    }

    private ItemStack calculateVanillaNetheriteUpgrade(ItemStack template, ItemStack base, ItemStack addition) {
        if (template == null || template.getType() != Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE) return null;
        if (addition == null || addition.getType() != Material.NETHERITE_INGOT) return null;
        if (base == null || base.getType().isAir()) return null;

        String baseName = base.getType().name();
        if (!baseName.startsWith("DIAMOND_")) return null;

        String netheriteName = baseName.replace("DIAMOND_", "NETHERITE_");
        Material targetMat;
        try {
            targetMat = Material.valueOf(netheriteName);
        } catch (IllegalArgumentException e) {
            return null;
        }

        ItemStack result = base.clone();
        result.setType(targetMat);

        // Preserve damage ratio
        ItemMeta meta = result.getItemMeta();
        if (meta instanceof Damageable damageable) {
            ItemMeta baseMeta = base.getItemMeta();
            if (baseMeta instanceof Damageable baseDamageable) {
                damageable.setDamage(baseDamageable.getDamage());
            }
        }
        result.setItemMeta(meta);

        return result;
    }

    private void consumeInputAndRestorePlaceholder(Inventory inv, int slot) {
        ItemStack item = inv.getItem(slot);
        if (item != null && !item.getType().isAir() && !isPlaceholder(item)) {
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
                inv.setItem(slot, item);
            } else {
                inv.setItem(slot, getPlaceholderForSlot(slot));
            }
        }
    }

    private void returnItemToPlayer(Player player, ItemStack item) {
        if (item != null && !item.getType().isAir() && !isPlaceholder(item)) {
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
    }

    private ItemStack getPlaceholderForSlot(int slot) {
        if (slot == 10) {
            return createModelPlaceholder(10030, "<yellow>Smithing Template Slot</yellow>", "<gray>Place Smithing Template here</gray>");
        } else if (slot == 11) {
            return createModelPlaceholder(10031, "<yellow>Base Item Slot</yellow>", "<gray>Place Equipment / Tool here</gray>");
        } else if (slot == 12) {
            return createModelPlaceholder(10032, "<yellow>Addition Slot</yellow>", "<gray>Place Upgrade Ingredient here</gray>");
        }
        return null;
    }

    private ItemStack createModelPlaceholder(int customModelData, String name, String... lore) {
        ItemStack stack = createItem(Material.PAPER, name, lore);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(customModelData);
            meta.getPersistentDataContainer().set(placeholderKey, PersistentDataType.BYTE, (byte) 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack createErrorIcon() {
        return createIconItem(10034, "<red><b>No Recipe</b></red>", "<gray>Place valid items to craft</gray>");
    }

    private ItemStack createArrowIcon() {
        return createIconItem(10033, "<green><b>Crafting Ready</b></green>", "<gray>Click the result item to craft</gray>");
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

    private boolean isPlaceholder(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(placeholderKey, PersistentDataType.BYTE);
    }

    private boolean isTemplateItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        String name = item.getType().name();
        return name.contains("TEMPLATE") || name.contains("TRIM");
    }

    private boolean isAdditionItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        String name = item.getType().name();
        return name.endsWith("_INGOT") || name.endsWith("_ORE") || name.endsWith("_RAW")
                || name.equals("DIAMOND") || name.equals("EMERALD") || name.equals("COAL")
                || name.equals("REDSTONE") || name.equals("LAPIS_LAZULI") || name.equals("QUARTZ")
                || name.equals("AMETHYST_SHARD") || name.equals("NETHERITE_SCRAP");
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

