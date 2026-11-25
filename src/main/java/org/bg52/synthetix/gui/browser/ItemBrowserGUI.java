package org.bg52.synthetix.gui.browser;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.gui.Canvas;
import org.bg52.synthetix.gui.CanvasButton;
import org.bg52.synthetix.gui.editor.MainEditorMenu;
import org.bg52.synthetix.item.ItemDefinition;
import org.bg52.synthetix.lang.LanguageManager;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Paginated in-game browser displaying all registered Synthetix custom items.
 * Allows instant pickup (1x or 64x), editing via MainEditorMenu, keyword filtering, and item deletion.
 */
public class ItemBrowserGUI {

    private final SynthetixPlugin plugin;
    private final LanguageManager lang;
    private int page = 0;
    private String filterQuery = null;

    public ItemBrowserGUI(SynthetixPlugin plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    public static void open(Player player, SynthetixPlugin plugin) {
        new ItemBrowserGUI(plugin).open(player, 0, null);
    }

    public static void open(Player player, SynthetixPlugin plugin, int page, String filter) {
        new ItemBrowserGUI(plugin).open(player, page, filter);
    }

    public void open(Player player, int targetPage, String filter) {
        this.filterQuery = filter != null && !filter.trim().isEmpty() ? filter.trim().toLowerCase(Locale.ROOT) : null;

        List<ItemDefinition> allItems = new ArrayList<>(plugin.getItemRegistry().getAll());
        allItems.sort(Comparator.comparing(ItemDefinition::getId));

        // Apply filter if specified
        if (filterQuery != null) {
            allItems.removeIf(def -> {
                boolean idMatches = def.getId().toLowerCase(Locale.ROOT).contains(filterQuery);
                boolean nameMatches = def.getDisplayName() != null &&
                        TextUtil.toPlain(TextUtil.parse(def.getDisplayName())).toLowerCase(Locale.ROOT).contains(filterQuery);
                return !idMatches && !nameMatches;
            });
        }

        int pageSize = 45; // 5 rows of 9 items
        int totalPages = Math.max(1, (int) Math.ceil((double) allItems.size() / pageSize));
        this.page = Math.max(0, Math.min(totalPages - 1, targetPage));

        String titleStr = lang.get("browser.title") + " <dark_gray>(" + (this.page + 1) + "/" + totalPages + ")</dark_gray>";
        if (filterQuery != null) {
            titleStr += " <gold>[Filtered: " + filterQuery + "]</gold>";
        }

        Canvas canvas = new Canvas(TextUtil.parse(titleStr), 6);

        int start = this.page * pageSize;
        int end = Math.min(start + pageSize, allItems.size());

        if (allItems.isEmpty()) {
            ItemStack emptyItem = new ItemStack(Material.BARRIER);
            ItemMeta eMeta = emptyItem.getItemMeta();
            if (eMeta != null) {
                eMeta.displayName(lang.getComponent("browser.empty-title"));
                List<Component> lore = new ArrayList<>();
                if (filterQuery != null) {
                    lore.add(TextUtil.parse("<gray>No custom items match: <yellow>\"" + filterQuery + "\"</yellow></gray>"));
                    lore.add(Component.empty());
                    lore.add(TextUtil.parse("<yellow>Click below to clear filter.</yellow>"));
                } else {
                    lore.add(lang.getComponent("browser.empty-desc"));
                }
                eMeta.lore(lore);
                emptyItem.setItemMeta(eMeta);
            }
            canvas.setButton(22, CanvasButton.of(emptyItem, e -> {
                if (filterQuery != null) {
                    open(player, 0, null);
                }
            }));
        } else {
            for (int i = start; i < end; i++) {
                ItemDefinition def = allItems.get(i);
                ItemStack sample = def.buildItemStack();

                // Clone to append action lore without mutating item definition
                ItemStack displayIcon = sample.clone();
                ItemMeta meta = displayIcon.getItemMeta();
                if (meta != null) {
                    List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                    lore.add(Component.empty());
                    lore.add(TextUtil.parse("<dark_gray>────────────────────</dark_gray>"));
                    lore.add(TextUtil.parse("<dark_gray>ID: " + def.getId() + "</dark_gray>"));
                    if (!player.hasPermission("synthetix.admin")) {
                        lore.add(Component.empty());
                        lore.add(TextUtil.parse("<yellow><bold>Click to view recipe »</bold></yellow>"));
                    } else {
                        lore.add(Component.empty());
                        lore.add(lang.getComponent("browser.action-left"));
                        lore.add(lang.getComponent("browser.action-shift-left"));
                        lore.add(lang.getComponent("browser.action-right"));
                        lore.add(TextUtil.parse("<aqua>Middle-Click:</aqua> <gray>View Recipes (JEI) »</gray>"));
                        lore.add(lang.getComponent("browser.action-shift-right"));
                    }
                    meta.lore(lore);
                    displayIcon.setItemMeta(meta);
                }

                int slot = i - start;
                canvas.setButton(slot, CanvasButton.of(displayIcon, e -> {
                    if (!player.hasPermission("synthetix.admin")) {
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                        org.bg52.synthetix.gui.recipe.RecipeViewerGUI.open(player, plugin, def.getId(), () -> open(player, this.page, filterQuery));
                        return;
                    }

                    if (e.getClick() == org.bukkit.event.inventory.ClickType.MIDDLE) {
                        org.bg52.synthetix.gui.recipe.RecipeViewerGUI.open(player, plugin, def.getId(), () -> open(player, this.page, filterQuery));
                        return;
                    }
                    if (e.isLeftClick() && !e.isShiftClick()) {
                        // Get 1x
                        ItemStack toGive = def.buildItemStack();
                        toGive.setAmount(1);
                        player.getInventory().addItem(toGive);
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.2f);
                        lang.send(player, "command.received-item", "%amount%", "1", "%item%", def.getId());
                    } else if (e.isLeftClick() && e.isShiftClick()) {
                        // Get 64x
                        ItemStack toGive = def.buildItemStack();
                        toGive.setAmount(Math.min(64, toGive.getMaxStackSize() > 0 ? toGive.getMaxStackSize() * 4 : 64));
                        player.getInventory().addItem(toGive);
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.5f);
                        lang.send(player, "command.received-item", "%amount%", String.valueOf(toGive.getAmount()), "%item%", def.getId());
                    } else if (e.isRightClick() && !e.isShiftClick()) {
                        // Open Editor
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                        MainEditorMenu.open(player, plugin, def);
                    } else if (e.isRightClick() && e.isShiftClick()) {
                        // Delete Item Prompt
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.7f, 1.0f);
                        plugin.getChatPromptManager().prompt(player,
                                lang.getComponent("browser.delete-confirm-prompt", "%item%", def.getId()),
                                input -> {
                                    if ("confirm".equalsIgnoreCase(input.trim()) || "yes".equalsIgnoreCase(input.trim())) {
                                        plugin.getItemRegistry().delete(def.getId());
                                        lang.send(player, "browser.item-deleted", "%item%", def.getId());
                                    } else {
                                        lang.send(player, "general.operation-cancelled");
                                    }
                                    open(player, this.page, filterQuery);
                                });
                    }
                }));
            }
        }

        // Setup Bottom Navigation Bar (Row 5: slots 45-53)
        ItemStack darkGlass = Canvas.createBorderItem(Material.BLACK_STAINED_GLASS_PANE, Component.empty());
        for (int c = 45; c < 54; c++) {
            canvas.setButton(c, CanvasButton.of(darkGlass));
        }

        // Slot 45: Previous Page
        if (this.page > 0) {
            ItemStack prev = Canvas.createBorderItem(Material.SPECTRAL_ARROW, lang.getComponent("editor.prev-page-button"));
            canvas.setButton(45, CanvasButton.of(prev, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                open(player, this.page - 1, filterQuery);
            }));
        }

        // Slot 47: Search / Filter
        ItemStack searchBtn = new ItemStack(filterQuery != null ? Material.SPYGLASS : Material.COMPASS);
        ItemMeta sMeta = searchBtn.getItemMeta();
        if (sMeta != null) {
            sMeta.displayName(lang.getComponent("browser.search-button"));
            List<Component> l = new ArrayList<>();
            if (filterQuery != null) {
                l.add(TextUtil.parse("<gray>Current Filter: <yellow>\"" + filterQuery + "\"</yellow></gray>"));
                l.add(Component.empty());
                l.add(TextUtil.parse("<yellow>Left-Click: Change search</yellow>"));
                l.add(TextUtil.parse("<red>Right-Click: Clear filter</red>"));
            } else {
                l.add(TextUtil.parse("<gray>Filter items by name or ID.</gray>"));
                l.add(Component.empty());
                l.add(TextUtil.parse("<yellow>Click to type search keyword in chat »</yellow>"));
            }
            sMeta.lore(l);
            searchBtn.setItemMeta(sMeta);
        }
        canvas.setButton(47, CanvasButton.of(searchBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            if (e.isRightClick() && filterQuery != null) {
                open(player, 0, null);
            } else {
                plugin.getChatPromptManager().prompt(player,
                        lang.getComponent("browser.search-prompt"),
                        query -> open(player, 0, query.trim()));
            }
        }));

        // Slot 49: Total Counter
        ItemStack infoBtn = new ItemStack(Material.BOOK);
        ItemMeta iMeta = infoBtn.getItemMeta();
        if (iMeta != null) {
            iMeta.displayName(lang.getComponent("browser.counter-title", "%count%", String.valueOf(allItems.size())));
            iMeta.lore(List.of(
                    TextUtil.parse("<gray>Total registered items on server.</gray>"),
                    TextUtil.parse("<dark_gray>Page " + (this.page + 1) + " of " + totalPages + "</dark_gray>")
            ));
            infoBtn.setItemMeta(iMeta);
        }
        canvas.setButton(49, CanvasButton.of(infoBtn));

        // Slot 51: + Create New Item (Admins only)
        if (player.hasPermission("synthetix.admin")) {
            ItemStack createBtn = new ItemStack(Material.EMERALD);
            ItemMeta cMeta = createBtn.getItemMeta();
            if (cMeta != null) {
                cMeta.displayName(lang.getComponent("browser.create-button"));
                cMeta.lore(List.of(
                        TextUtil.parse("<gray>Create and design a new custom item.</gray>"),
                        Component.empty(),
                        TextUtil.parse("<yellow>Click to enter item ID in chat »</yellow>")
                ));
                createBtn.setItemMeta(cMeta);
            }
            canvas.setButton(51, CanvasButton.of(createBtn, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
                plugin.getChatPromptManager().prompt(player,
                        lang.getComponent("editor.prompts.enter-item-id"),
                        id -> {
                            String cleanId = id.trim().toLowerCase(Locale.ROOT);
                            ItemDefinition def = plugin.getItemRegistry().get(cleanId);
                            if (def == null) {
                                def = new ItemDefinition(cleanId);
                            }
                            MainEditorMenu.open(player, plugin, def);
                        });
            }));
        }

        // Slot 53: Next Page
        if (this.page < totalPages - 1) {
            ItemStack next = Canvas.createBorderItem(Material.SPECTRAL_ARROW, lang.getComponent("editor.next-page-button"));
            canvas.setButton(53, CanvasButton.of(next, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                open(player, this.page + 1, filterQuery);
            }));
        }

        canvas.open(player);
    }
}

