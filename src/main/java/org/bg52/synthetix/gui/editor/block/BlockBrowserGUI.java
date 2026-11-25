package org.bg52.synthetix.gui.editor.block;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.block.CustomBlockDefinition;
import org.bg52.synthetix.gui.Canvas;
import org.bg52.synthetix.gui.CanvasButton;
import org.bg52.synthetix.gui.recipe.RecipeViewerGUI;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Paginated browser displaying all registered Synthetix custom blocks.
 * Supports instant pickup (1x or 64x), editing via BlockEditorMenu,
 * recipe inspection, keyword searching, and block deletion.
 */
public class BlockBrowserGUI {

    private final SynthetixPlugin plugin;
    private int page = 0;
    private String filterQuery = null;

    public BlockBrowserGUI(SynthetixPlugin plugin) {
        this.plugin = plugin;
    }

    public static void open(Player player, SynthetixPlugin plugin) {
        new BlockBrowserGUI(plugin).open(player, 0, null);
    }

    public static void open(Player player, SynthetixPlugin plugin, int page, String filter) {
        new BlockBrowserGUI(plugin).open(player, page, filter);
    }

    public void open(Player player, int targetPage, String filter) {
        this.filterQuery = filter != null && !filter.trim().isEmpty() ? filter.trim().toLowerCase(Locale.ROOT) : null;

        List<CustomBlockDefinition> allBlocks = new ArrayList<>(plugin.getCustomBlockRegistry().getAll());
        allBlocks.sort(Comparator.comparing(CustomBlockDefinition::getId));

        if (filterQuery != null) {
            allBlocks.removeIf(def -> {
                boolean idMatches = def.getId().toLowerCase(Locale.ROOT).contains(filterQuery);
                boolean nameMatches = def.getDisplayName() != null &&
                        TextUtil.toPlain(TextUtil.parse(def.getDisplayName())).toLowerCase(Locale.ROOT).contains(filterQuery);
                return !idMatches && !nameMatches;
            });
        }

        int pageSize = 45; // 5 rows of 9 blocks
        int totalPages = Math.max(1, (int) Math.ceil((double) allBlocks.size() / pageSize));
        this.page = Math.max(0, Math.min(totalPages - 1, targetPage));

        String titleStr = "<gold><bold>Custom Blocks Browser</bold></gold> <dark_gray>(" + (this.page + 1) + "/" + totalPages + ")</dark_gray>";
        if (filterQuery != null) {
            titleStr += " <gold>[Filtered: " + filterQuery + "]</gold>";
        }

        Canvas canvas = new Canvas(TextUtil.parse(titleStr), 6);

        int start = this.page * pageSize;
        int end = Math.min(start + pageSize, allBlocks.size());

        if (allBlocks.isEmpty()) {
            ItemStack emptyItem = new ItemStack(Material.BARRIER);
            ItemMeta eMeta = emptyItem.getItemMeta();
            if (eMeta != null) {
                eMeta.displayName(plugin.getLanguageManager().getComponent("block-browser.empty-title"));
                List<Component> lore = new ArrayList<>();
                if (filterQuery != null) {
                    lore.add(plugin.getLanguageManager().getComponent("block-browser.empty-filter", "%query%", filterQuery));
                    lore.add(Component.empty());
                    lore.add(plugin.getLanguageManager().getComponent("block-browser.clear-filter"));
                } else {
                    lore.add(plugin.getLanguageManager().getComponent("block-browser.empty-desc"));
                    lore.add(TextUtil.parse("<gray>Use <yellow>/synth create block <id></yellow> to start!</gray>"));
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
                CustomBlockDefinition def = allBlocks.get(i);
                ItemStack displayIcon = def.buildBlockItemStack(1);
                ItemMeta meta = displayIcon.getItemMeta();
                if (meta != null) {
                    List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                    lore.add(Component.empty());
                    lore.add(TextUtil.parse("<dark_gray>────────────────────</dark_gray>"));
                    lore.add(TextUtil.parse("<dark_gray>ID: " + def.getId() + "</dark_gray>"));
                    lore.add(TextUtil.parse("<gray>Base Proxy:</gray> <aqua>" + def.getBaseMaterial().name() + "</aqua>"));
                    lore.add(TextUtil.parse("<gray>Hardness:</gray> <white>" + def.getMining().getHardness() + " (Blast: " + def.getMining().getBlastResistance() + ")</white>"));
                    if (!player.hasPermission("synthetix.admin")) {
                        lore.add(Component.empty());
                        lore.add(plugin.getLanguageManager().getComponent("block-browser.action-view-recipe"));
                    } else {
                        lore.add(Component.empty());
                        lore.add(plugin.getLanguageManager().getComponent("block-browser.action-left"));
                        lore.add(plugin.getLanguageManager().getComponent("block-browser.action-shift-left"));
                        lore.add(plugin.getLanguageManager().getComponent("block-browser.action-right"));
                        lore.add(plugin.getLanguageManager().getComponent("block-browser.action-middle"));
                        lore.add(plugin.getLanguageManager().getComponent("block-browser.action-shift-right"));
                    }
                    meta.lore(lore);
                    displayIcon.setItemMeta(meta);
                }

                int slot = i - start;
                canvas.setButton(slot, CanvasButton.of(displayIcon, e -> {
                    if (!player.hasPermission("synthetix.admin")) {
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                        RecipeViewerGUI.open(player, plugin, def.getId(), () -> open(player, this.page, filterQuery));
                        return;
                    }

                    if (e.getClick() == org.bukkit.event.inventory.ClickType.MIDDLE) {
                        RecipeViewerGUI.open(player, plugin, def.getId(), () -> open(player, this.page, filterQuery));
                        return;
                    }

                    if (e.isLeftClick() && !e.isShiftClick()) {
                        player.getInventory().addItem(def.buildBlockItemStack(1));
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.2f);
                        plugin.getLanguageManager().send(player, "block-browser.added-1x", "%block%", def.getId());
                    } else if (e.isLeftClick() && e.isShiftClick()) {
                        player.getInventory().addItem(def.buildBlockItemStack(64));
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.5f);
                        plugin.getLanguageManager().send(player, "block-browser.added-64x", "%block%", def.getId());
                    } else if (e.isRightClick() && !e.isShiftClick()) {
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                        BlockEditorMenu.open(player, plugin, def);
                    } else if (e.isRightClick() && e.isShiftClick()) {
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.7f, 1.0f);
                        plugin.getChatPromptManager().prompt(player,
                                plugin.getLanguageManager().getComponent("block-browser.delete-confirm-prompt", "%block%", def.getId()),
                                input -> {
                                    if ("confirm".equalsIgnoreCase(input.trim()) || "yes".equalsIgnoreCase(input.trim())) {
                                        plugin.getCustomBlockRegistry().delete(def.getId());
                                        plugin.getLanguageManager().send(player, "block-browser.block-deleted", "%block%", def.getId());
                                    } else {
                                        plugin.getLanguageManager().send(player, "general.operation-cancelled");
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
            ItemStack prev = Canvas.createBorderItem(Material.SPECTRAL_ARROW, plugin.getLanguageManager().getComponent("editor.prev-page-button"));
            canvas.setButton(45, CanvasButton.of(prev, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                open(player, this.page - 1, filterQuery);
            }));
        }

        // Slot 47: Search / Filter
        ItemStack searchBtn = new ItemStack(filterQuery != null ? Material.SPYGLASS : Material.COMPASS);
        ItemMeta sMeta = searchBtn.getItemMeta();
        if (sMeta != null) {
            sMeta.displayName(plugin.getLanguageManager().getComponent("block-browser.search-button"));
            List<Component> l = new ArrayList<>();
            if (filterQuery != null) {
                l.add(TextUtil.parse("<gray>Current Filter: <yellow>\"" + filterQuery + "\"</yellow></gray>"));
                l.add(Component.empty());
                l.add(TextUtil.parse("<yellow>Left-Click: Change search</yellow>"));
                l.add(TextUtil.parse("<red>Right-Click: Clear filter</red>"));
            } else {
                l.add(TextUtil.parse("<gray>Filter blocks by name or ID.</gray>"));
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
                        plugin.getLanguageManager().getComponent("block-browser.search-prompt"),
                        query -> open(player, 0, query.trim()));
            }
        }));

        // Slot 49: Total Counter
        ItemStack infoBtn = new ItemStack(Material.BOOK);
        ItemMeta iMeta = infoBtn.getItemMeta();
        if (iMeta != null) {
            iMeta.displayName(plugin.getLanguageManager().getComponent("block-browser.counter-title", "%count%", String.valueOf(allBlocks.size())));
            iMeta.lore(List.of(
                    TextUtil.parse("<gray>Registered custom blocks on server.</gray>"),
                    TextUtil.parse("<dark_gray>Page " + (this.page + 1) + " of " + totalPages + "</dark_gray>")
            ));
            infoBtn.setItemMeta(iMeta);
        }
        canvas.setButton(49, CanvasButton.of(infoBtn));

        // Slot 51: + Create New Block (Admins only)
        if (player.hasPermission("synthetix.admin")) {
            ItemStack createBtn = new ItemStack(Material.EMERALD);
            ItemMeta cMeta = createBtn.getItemMeta();
            if (cMeta != null) {
                cMeta.displayName(plugin.getLanguageManager().getComponent("block-browser.create-button"));
                cMeta.lore(List.of(
                        TextUtil.parse("<gray>Create and design a new custom block.</gray>"),
                        Component.empty(),
                        TextUtil.parse("<yellow>Click to enter block ID in chat »</yellow>")
                ));
                createBtn.setItemMeta(cMeta);
            }
            canvas.setButton(51, CanvasButton.of(createBtn, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
                plugin.getChatPromptManager().prompt(player,
                        plugin.getLanguageManager().getComponent("command.create-block-prompt"),
                        id -> {
                            String cleanId = id.trim().toLowerCase(Locale.ROOT);
                            CustomBlockDefinition def = plugin.getCustomBlockRegistry().get(cleanId);
                            if (def == null) {
                                def = new CustomBlockDefinition(cleanId);
                                plugin.getCustomBlockRegistry().register(def);
                            }
                            BlockEditorMenu.open(player, plugin, def);
                        });
            }));
        }

        // Slot 53: Next Page
        if (this.page < totalPages - 1) {
            ItemStack next = Canvas.createBorderItem(Material.SPECTRAL_ARROW, plugin.getLanguageManager().getComponent("editor.next-page-button"));
            canvas.setButton(53, CanvasButton.of(next, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                open(player, this.page + 1, filterQuery);
            }));
        }

        canvas.open(player);
    }
}

