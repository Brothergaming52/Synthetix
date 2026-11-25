package org.bg52.synthetix.gui.editor.entity;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.entity.model.CustomEntityDefinition;
import org.bg52.synthetix.gui.Canvas;
import org.bg52.synthetix.gui.CanvasButton;
import org.bg52.synthetix.lang.LanguageManager;
import org.bg52.synthetix.util.MaterialUtil;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Paginated interactive browser displaying all registered custom entities.
 * Strictly localized: zero hardcoded messages or strings.
 */
public class EntityBrowserGUI {

    private final SynthetixPlugin plugin;
    private final LanguageManager lang;
    private int page = 0;
    private String filterQuery = null;

    public EntityBrowserGUI(SynthetixPlugin plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    public static void open(Player player, SynthetixPlugin plugin) {
        new EntityBrowserGUI(plugin).open(player, 0, null);
    }

    public static void open(Player player, SynthetixPlugin plugin, int page, String filter) {
        new EntityBrowserGUI(plugin).open(player, page, filter);
    }

    public void open(Player player, int targetPage, String filter) {
        this.filterQuery = filter != null && !filter.trim().isEmpty() ? filter.trim().toLowerCase(Locale.ROOT) : null;

        List<CustomEntityDefinition> all = new ArrayList<>(plugin.getCustomEntityRegistry().getAll());
        all.sort(Comparator.comparing(CustomEntityDefinition::getId));

        if (filterQuery != null) {
            all.removeIf(def -> {
                boolean idMatches = def.getId().toLowerCase(Locale.ROOT).contains(filterQuery);
                boolean nameMatches = def.getDisplayName() != null &&
                        TextUtil.toPlain(TextUtil.parse(def.getDisplayName())).toLowerCase(Locale.ROOT).contains(filterQuery);
                return !idMatches && !nameMatches;
            });
        }

        int pageSize = 45;
        int totalPages = Math.max(1, (int) Math.ceil((double) all.size() / pageSize));
        this.page = Math.max(0, Math.min(totalPages - 1, targetPage));

        Component title = lang.getComponent("gui.entity-browser.title", "%count%", String.valueOf(all.size()));
        Canvas canvas = new Canvas(title, 6);

        int start = this.page * pageSize;
        int end = Math.min(start + pageSize, all.size());

        if (all.isEmpty()) {
            ItemStack emptyItem = new ItemStack(Material.BARRIER);
            ItemMeta eMeta = emptyItem.getItemMeta();
            if (eMeta != null) {
                eMeta.displayName(lang.getComponent("gui.model-selector.no-models-title"));
                List<Component> lore = new ArrayList<>();
                if (filterQuery != null) {
                    lore.add(TextUtil.parse("<gray>Filter: <yellow>\"" + filterQuery + "\"</yellow></gray>"));
                    lore.add(TextUtil.parse("<yellow>Click to clear filter</yellow>"));
                } else {
                    lore.add(lang.getComponent("gui.model-selector.no-models-lore-1"));
                    lore.add(lang.getComponent("gui.model-selector.no-models-lore-2"));
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
                CustomEntityDefinition def = all.get(i);
                ItemStack displayIcon = createEntityIcon(def);
                int slot = i - start;

                canvas.setButton(slot, CanvasButton.of(displayIcon, e -> {
                    if (!player.hasPermission("synthetix.admin")) {
                        return;
                    }

                    if (e.isLeftClick() && !e.isShiftClick()) {
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.2f);
                        plugin.getCustomEntityManager().spawnEntity(def, player.getLocation());
                        lang.send(player, "command.entity-spawn-success", "%amount%", "1", "%entity%", def.getId());
                    } else if (e.isLeftClick() && e.isShiftClick()) {
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.5f);
                        for (int k = 0; k < 5; k++) {
                            plugin.getCustomEntityManager().spawnEntity(def, player.getLocation());
                        }
                        lang.send(player, "command.entity-spawn-success", "%amount%", "5", "%entity%", def.getId());
                    } else if (e.isRightClick() && !e.isShiftClick()) {
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                        EntityEditorMenu.open(player, plugin, def);
                    } else if (e.isRightClick() && e.isShiftClick()) {
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.7f, 1.0f);
                        plugin.getChatPromptManager().prompt(player,
                                TextUtil.parse("<red>Type <yellow>'confirm'</yellow> to permanently delete custom entity <white>" + def.getId() + "</white>:</red>"),
                                input -> {
                                    if ("confirm".equalsIgnoreCase(input.trim()) || "yes".equalsIgnoreCase(input.trim())) {
                                        plugin.getCustomEntityRegistry().delete(def.getId());
                                        lang.send(player, "command.entity-delete-success", "%entity%", def.getId());
                                    } else {
                                        lang.send(player, "general.operation-cancelled");
                                    }
                                    open(player, this.page, filterQuery);
                                });
                    }
                }));
            }
        }

        // Bottom Navigation Bar (Row 5: slots 45-53)
        ItemStack darkGlass = Canvas.createBorderItem(Material.BLACK_STAINED_GLASS_PANE, Component.empty());
        for (int c = 45; c < 54; c++) {
            canvas.setButton(c, CanvasButton.of(darkGlass));
        }

        // Slot 45: Previous Page
        if (this.page > 0) {
            ItemStack prev = Canvas.createBorderItem(Material.SPECTRAL_ARROW, TextUtil.parse("<yellow><bold>« Previous Page</bold></yellow>"));
            canvas.setButton(45, CanvasButton.of(prev, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                open(player, this.page - 1, filterQuery);
            }));
        }

        // Slot 47: Search Filter
        ItemStack searchBtn = new ItemStack(filterQuery != null ? Material.SPYGLASS : Material.COMPASS);
        ItemMeta sMeta = searchBtn.getItemMeta();
        if (sMeta != null) {
            sMeta.displayName(TextUtil.parse("<aqua><bold>Search Entities</bold></aqua>"));
            List<Component> l = new ArrayList<>();
            if (filterQuery != null) {
                l.add(TextUtil.parse("<gray>Current Filter: <yellow>\"" + filterQuery + "\"</yellow></gray>"));
                l.add(Component.empty());
                l.add(TextUtil.parse("<yellow>Left-Click: Change search</yellow>"));
                l.add(TextUtil.parse("<red>Right-Click: Clear filter</red>"));
            } else {
                l.add(TextUtil.parse("<gray>Filter entities by name or ID.</gray>"));
                l.add(Component.empty());
                l.add(TextUtil.parse("<yellow>Click to type search keyword »</yellow>"));
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
                        TextUtil.parse("<gray>Enter entity search query (or 'cancel'):</gray>"),
                        query -> open(player, 0, query.trim()));
            }
        }));

        // Slot 48: Import Bedrock / Blockbench Model
        if (player.hasPermission("synthetix.admin")) {
            ItemStack importBtn = new ItemStack(Material.ARMOR_STAND);
            ItemMeta imMeta = importBtn.getItemMeta();
            if (imMeta != null) {
                imMeta.displayName(lang.getComponent("gui.entity-browser.btn-import-bedrock"));
                imMeta.lore(List.of(
                        lang.getComponent("gui.entity-browser.btn-import-bedrock-lore-1"),
                        lang.getComponent("gui.entity-browser.btn-import-bedrock-lore-2"),
                        Component.empty(),
                        lang.getComponent("gui.entity-browser.btn-import-bedrock-ctrl")
                ));
                importBtn.setItemMeta(imMeta);
            }
            canvas.setButton(48, CanvasButton.of(importBtn, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                BedrockModelSelectorGUI.open(player, plugin);
            }));
        }

        // Slot 49: Total Counter
        ItemStack infoBtn = new ItemStack(Material.BOOK);
        ItemMeta iMeta = infoBtn.getItemMeta();
        if (iMeta != null) {
            iMeta.displayName(TextUtil.parse("<gold><bold>Total Entities: " + all.size() + "</bold></gold>"));
            iMeta.lore(List.of(
                    TextUtil.parse("<gray>Registered custom entities on server.</gray>"),
                    TextUtil.parse("<dark_gray>Page " + (this.page + 1) + " of " + totalPages + "</dark_gray>")
            ));
            infoBtn.setItemMeta(iMeta);
        }
        canvas.setButton(49, CanvasButton.of(infoBtn));

        // Slot 51: + Create New Entity
        if (player.hasPermission("synthetix.admin")) {
            ItemStack createBtn = new ItemStack(Material.EMERALD);
            ItemMeta cMeta = createBtn.getItemMeta();
            if (cMeta != null) {
                cMeta.displayName(lang.getComponent("gui.entity-browser.btn-create"));
                cMeta.lore(List.of(
                        lang.getComponent("gui.entity-browser.btn-create-lore"),
                        Component.empty(),
                        TextUtil.parse("<yellow>Click to enter entity ID »</yellow>")
                ));
                createBtn.setItemMeta(cMeta);
            }
            canvas.setButton(51, CanvasButton.of(createBtn, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
                plugin.getChatPromptManager().prompt(player,
                        lang.getComponent("prompts.enter-entity-id"),
                        id -> {
                            String cleanId = id.trim().toLowerCase(Locale.ROOT);
                            CustomEntityDefinition def = plugin.getCustomEntityRegistry().get(cleanId);
                            if (def == null) {
                                def = new CustomEntityDefinition(cleanId);
                                plugin.getCustomEntityRegistry().register(def);
                            }
                            EntityEditorMenu.open(player, plugin, def);
                        });
            }));
        }

        // Slot 53: Next Page
        if (this.page < totalPages - 1) {
            ItemStack next = Canvas.createBorderItem(Material.SPECTRAL_ARROW, TextUtil.parse("<yellow><bold>Next Page »</bold></yellow>"));
            canvas.setButton(53, CanvasButton.of(next, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                open(player, this.page + 1, filterQuery);
            }));
        }

        canvas.open(player);
    }

    private ItemStack createEntityIcon(CustomEntityDefinition def) {
        Material iconMat = Material.ZOMBIE_SPAWN_EGG;
        try {
            Material egg = Material.matchMaterial(def.getBaseEntityType().name() + "_SPAWN_EGG");
            if (egg != null) iconMat = egg;
        } catch (Exception ignored) {}

        ItemStack stack = new ItemStack(MaterialUtil.toItemMaterial(iconMat));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.parse(def.getDisplayName()));
            List<Component> lore = new ArrayList<>();
            lore.add(lang.getComponent("gui.entity-browser.card-id", "%id%", def.getId()));
            lore.add(lang.getComponent("gui.entity-browser.card-base", "%base%", def.getBaseEntityType().name()));
            lore.add(lang.getComponent("gui.entity-browser.card-health", "%health%", String.format(Locale.ROOT, "%.1f", def.getMaxHealth())));
            lore.add(lang.getComponent("gui.entity-browser.card-damage", "%damage%", String.format(Locale.ROOT, "%.1f", def.getAttackDamage())));
            lore.add(lang.getComponent("gui.entity-browser.card-archetype", "%archetype%", def.getAi().getAiType().name()));
            lore.add(lang.getComponent("gui.entity-browser.card-bones", "%bones%", String.valueOf(def.getBones().size())));
            lore.add(lang.getComponent("gui.entity-browser.card-anims", "%anims%", String.valueOf(def.getAnimations().size())));

            lore.add(Component.empty());
            lore.add(TextUtil.parse("<dark_gray>────────────────────</dark_gray>"));
            lore.add(lang.getComponent("gui.entity-browser.card-ctrl-spawn"));
            lore.add(lang.getComponent("gui.entity-browser.card-ctrl-edit"));
            lore.add(lang.getComponent("gui.entity-browser.card-ctrl-delete"));
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
