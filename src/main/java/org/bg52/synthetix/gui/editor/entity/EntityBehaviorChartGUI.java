package org.bg52.synthetix.gui.editor.entity;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.entity.behavior.EntityBehaviorAction;
import org.bg52.synthetix.entity.behavior.EntityBehaviorChart;
import org.bg52.synthetix.entity.behavior.EntityBehaviorNode;
import org.bg52.synthetix.entity.model.CustomEntityDefinition;
import org.bg52.synthetix.gui.Canvas;
import org.bg52.synthetix.gui.CanvasButton;
import org.bg52.synthetix.lang.LanguageManager;
import org.bg52.synthetix.util.MaterialUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Intuitive, visual in-game editor for custom entity behavior charts.
 * Allows effortless configuration of behavior priorities, trigger conditions,
 * linked skeletal animations, and execution actions.
 * Strictly localizes all user-facing strings through LanguageManager.
 */
public class EntityBehaviorChartGUI {

    private final SynthetixPlugin plugin;
    private final LanguageManager lang;
    private final CustomEntityDefinition def;
    private int page = 0;

    public EntityBehaviorChartGUI(SynthetixPlugin plugin, CustomEntityDefinition def) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
        this.def = def;
    }

    public static void open(Player player, SynthetixPlugin plugin, CustomEntityDefinition def) {
        new EntityBehaviorChartGUI(plugin, def).open(player);
    }

    public void open(Player player) {
        Component title = lang.getComponent("gui.behavior-chart.title", "%entity%", def.getDisplayName());
        Canvas canvas = new Canvas(title, 6);

        ItemStack darkGlass = Canvas.createBorderItem(Material.BLACK_STAINED_GLASS_PANE, Component.empty());
        ItemStack grayGlass = Canvas.createBorderItem(Material.GRAY_STAINED_GLASS_PANE, Component.empty());

        for (int c = 0; c < 9; c++) {
            if (c != 4) canvas.setButton(0, c, CanvasButton.of(c == 0 || c == 8 ? darkGlass : grayGlass));
            canvas.setButton(5, c, CanvasButton.of(darkGlass));
        }
        for (int r = 1; r < 5; r++) {
            canvas.setButton(r, 0, CanvasButton.of(darkGlass));
            canvas.setButton(r, 8, CanvasButton.of(darkGlass));
        }

        EntityBehaviorChart chart = def.getBehaviorChart();
        List<EntityBehaviorNode> nodes = chart.getNodesSortedByPriority();

        // Slot 0: Entity Overview Card
        ItemStack overviewItem = createBtn(Material.KNOWLEDGE_BOOK,
                lang.get("gui.behavior-chart.overview-title"),
                lang.get("gui.behavior-chart.overview-entity", "%entity%", def.getId()),
                lang.get("gui.behavior-chart.overview-archetype", "%archetype%", def.getAi().getAiType().name()),
                lang.get("gui.behavior-chart.overview-nodes", "%count%", String.valueOf(nodes.size())),
                lang.get("gui.behavior-chart.overview-animations", "%count%", String.valueOf(def.getAnimations().size())));
        canvas.setButton(0, CanvasButton.of(overviewItem));

        // Slot 4: Header Info
        ItemStack headerItem = createBtn(Material.COMPASS,
                lang.get("gui.behavior-chart.header-title"),
                lang.get("gui.behavior-chart.header-lore-1"),
                lang.get("gui.behavior-chart.header-lore-2"));
        canvas.setButton(4, CanvasButton.of(headerItem));

        // Center Grid Slots (Row 1-4, Col 1-7 = 28 slots)
        List<Integer> gridSlots = new ArrayList<>();
        for (int r = 1; r <= 4; r++) {
            for (int c = 1; c <= 7; c++) {
                gridSlots.add(r * 9 + c);
            }
        }

        int pageSize = gridSlots.size();
        int maxPages = Math.max(1, (int) Math.ceil((double) nodes.size() / pageSize));
        if (page >= maxPages) page = maxPages - 1;

        int startIndex = page * pageSize;
        int endIndex = Math.min(startIndex + pageSize, nodes.size());

        List<String> availableTracks = new ArrayList<>(def.getAnimations().keySet());
        availableTracks.add("none");

        for (int i = startIndex; i < endIndex; i++) {
            EntityBehaviorNode node = nodes.get(i);
            int slot = gridSlots.get(i - startIndex);

            Material iconMat = pickNodeIcon(node);
            List<String> lore = new ArrayList<>();
            lore.add(lang.get("gui.behavior-chart.node-priority", "%priority%", String.valueOf(node.getPriority())));
            lore.add(lang.get("gui.behavior-chart.node-animation", "%track%", node.getAnimationTrack() != null && !node.getAnimationTrack().isEmpty() ? node.getAnimationTrack() : lang.get("general.not-selected")));
            lore.add(lang.get("gui.behavior-chart.node-range", "%min%", String.format(Locale.ROOT, "%.1f", node.getMinRange()), "%max%", String.format(Locale.ROOT, "%.1f", node.getMaxRange())));
            lore.add(lang.get("gui.behavior-chart.node-health", "%min%", String.format(Locale.ROOT, "%.0f", node.getMinHealthPercent()), "%max%", String.format(Locale.ROOT, "%.0f", node.getMaxHealthPercent())));
            lore.add(lang.get("gui.behavior-chart.node-cooldown", "%ticks%", String.valueOf(node.getCooldownTicks()), "%sec%", String.format(Locale.ROOT, "%.1f", node.getCooldownTicks() / 20.0)));
            lore.add(lang.get("gui.behavior-chart.node-requires-target", "%val%", node.isRequiresTarget() ? lang.get("general.applied") : lang.get("general.not-applied")));
            lore.add(lang.get("gui.behavior-chart.node-actions-header", "%count%", String.valueOf(node.getActions().size())));

            for (EntityBehaviorAction act : node.getActions()) {
                lore.add(lang.get("gui.behavior-chart.node-action-line", "%action%", act.serialize()));
            }

            lore.add("");
            lore.add(lang.get("gui.behavior-chart.ctrl-cycle-animation"));
            lore.add(lang.get("gui.behavior-chart.ctrl-edit-range"));
            lore.add(lang.get("gui.behavior-chart.ctrl-edit-cooldown"));
            lore.add(lang.get("gui.behavior-chart.ctrl-add-action"));
            lore.add(lang.get("gui.behavior-chart.ctrl-delete-node"));

            ItemStack nodeBtn = createBtn(iconMat,
                    lang.get("gui.behavior-chart.node-title", "%name%", node.getName()),
                    lore.toArray(new String[0]));

            canvas.setButton(slot, CanvasButton.of(nodeBtn, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);

                if (e.isRightClick() && e.isShiftClick()) {
                    // Prompt for adding action
                    plugin.getChatPromptManager().prompt(player,
                            lang.getComponent("gui.behavior-chart.prompt-add-action"),
                            input -> {
                                if (!input.trim().isEmpty()) {
                                    EntityBehaviorAction act = EntityBehaviorAction.deserialize(input.trim());
                                    node.addAction(act);
                                    lang.send(player, "gui.behavior-chart.action-added", "%action%", act.serialize());
                                }
                                open(player);
                            });
                } else if (e.isLeftClick() && e.isShiftClick()) {
                    // Prompt for editing cooldown
                    plugin.getChatPromptManager().prompt(player,
                            lang.getComponent("gui.behavior-chart.prompt-edit-cooldown"),
                            input -> {
                                try {
                                    node.setCooldownTicks(Math.max(1, Integer.parseInt(input.trim())));
                                    lang.send(player, "gui.behavior-chart.cooldown-updated", "%ticks%", String.valueOf(node.getCooldownTicks()));
                                } catch (Exception ex) {
                                    lang.send(player, "general.input-error", "%error%", ex.getMessage());
                                }
                                open(player);
                            });
                } else if (e.isRightClick()) {
                    // Prompt for editing range
                    plugin.getChatPromptManager().prompt(player,
                            lang.getComponent("gui.behavior-chart.prompt-edit-range"),
                            input -> {
                                String[] parts = input.trim().split("\\s+");
                                try {
                                    if (parts.length >= 2) {
                                        node.setMinRange(Double.parseDouble(parts[0]));
                                        node.setMaxRange(Double.parseDouble(parts[1]));
                                    } else if (parts.length == 1) {
                                        node.setMaxRange(Double.parseDouble(parts[0]));
                                    }
                                    lang.send(player, "gui.behavior-chart.range-updated", "%min%", String.valueOf(node.getMinRange()), "%max%", String.valueOf(node.getMaxRange()));
                                } catch (Exception ex) {
                                    lang.send(player, "general.input-error", "%error%", ex.getMessage());
                                }
                                open(player);
                            });
                } else if (e.getClick() == org.bukkit.event.inventory.ClickType.DROP || e.getClick() == org.bukkit.event.inventory.ClickType.CONTROL_DROP || e.getClick() == org.bukkit.event.inventory.ClickType.MIDDLE) {
                    // Delete node
                    chart.removeNode(node.getId());
                    lang.send(player, "gui.behavior-chart.node-deleted", "%id%", node.getId());
                    open(player);
                } else {
                    // Left click: cycle animation track
                    if (!availableTracks.isEmpty()) {
                        String current = node.getAnimationTrack() != null ? node.getAnimationTrack() : "none";
                        int curIdx = availableTracks.indexOf(current);
                        int nextIdx = (curIdx + 1) % availableTracks.size();
                        String nextTrack = availableTracks.get(nextIdx);
                        node.setAnimationTrack("none".equalsIgnoreCase(nextTrack) ? null : nextTrack);
                        lang.send(player, "gui.behavior-chart.animation-cycled", "%track%", nextTrack);
                        open(player);
                    }
                }
            }));
        }

        // =====================================================================
        // ROW 5: Bottom Navigation Bar
        // =====================================================================

        // Slot 45: Back to Entity Editor
        ItemStack backBtn = createBtn(Material.SPECTRAL_ARROW,
                lang.get("gui.behavior-chart.btn-back"),
                lang.get("gui.behavior-chart.btn-back-lore"));
        canvas.setButton(45, CanvasButton.of(backBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            EntityEditorMenu.open(player, plugin, def);
        }));

        // Slot 48: Add New Behavior Node
        ItemStack addNodeBtn = createBtn(Material.NETHER_STAR,
                lang.get("gui.behavior-chart.btn-add-node"),
                lang.get("gui.behavior-chart.btn-add-node-lore"));
        canvas.setButton(48, CanvasButton.of(addNodeBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            plugin.getChatPromptManager().prompt(player,
                    lang.getComponent("gui.behavior-chart.prompt-new-node-id"),
                    input -> {
                        String clean = input.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
                        if (!clean.isEmpty()) {
                            int nextPriority = nodes.size() + 1;
                            EntityBehaviorNode newNode = new EntityBehaviorNode(clean, clean, nextPriority);
                            newNode.addAction(new EntityBehaviorAction());
                            chart.addNode(newNode);
                            lang.send(player, "gui.behavior-chart.node-created", "%id%", clean);
                        }
                        open(player);
                    });
        }));

        // Slot 50: Reset to Defaults
        ItemStack resetBtn = createBtn(Material.REDSTONE_BLOCK,
                lang.get("gui.behavior-chart.btn-reset"),
                lang.get("gui.behavior-chart.btn-reset-lore"));
        canvas.setButton(50, CanvasButton.of(resetBtn, e -> {
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.0f);
            chart.populateDefaults(def.getAi().getAiType(), def.getAnimations());
            lang.send(player, "gui.behavior-chart.defaults-restored");
            open(player);
        }));

        // Slot 53: Save Entity Definition
        ItemStack saveBtn = createBtn(Material.EMERALD,
                lang.get("gui.behavior-chart.btn-save"),
                lang.get("gui.behavior-chart.btn-save-lore"));
        canvas.setButton(53, CanvasButton.of(saveBtn, e -> {
            boolean saved = plugin.getCustomEntityRegistry().save(def);
            if (saved) {
                player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);
                lang.send(player, "gui.behavior-chart.saved-success", "%id%", def.getId());
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                lang.send(player, "gui.behavior-chart.saved-failed");
            }
            EntityEditorMenu.open(player, plugin, def);
        }));

        canvas.open(player);
    }

    private Material pickNodeIcon(EntityBehaviorNode node) {
        String id = node.getId().toLowerCase(Locale.ROOT);
        for (EntityBehaviorAction act : node.getActions()) {
            switch (act.getType()) {
                case SHOOT_PROJECTILE -> { return Material.BOW; }
                case LEAP_DASH -> { return Material.FEATHER; }
                case POTION_EFFECT -> { return Material.SPLASH_POTION; }
                case PARTICLE_BURST -> { return Material.BLAZE_POWDER; }
                case PLAY_SOUND -> { return Material.NOTE_BLOCK; }
                case TELEPORT_BEHIND -> { return Material.ENDER_PEARL; }
                case SPAWN_MINIONS -> { return Material.ZOMBIE_HEAD; }
                case HEAL -> { return Material.GOLDEN_APPLE; }
                case RUN_COMMAND -> { return Material.COMMAND_BLOCK; }
                default -> {}
            }
        }
        if (id.contains("shoot") || id.contains("fire") || id.contains("projectile")) return Material.BOW;
        if (id.contains("leap") || id.contains("pounce") || id.contains("jump")) return Material.FEATHER;
        if (id.contains("roar") || id.contains("spell") || id.contains("enrage")) return Material.DRAGON_BREATH;
        return Material.DIAMOND_SWORD;
    }

    private ItemStack createBtn(Material mat, String name, String... loreLines) {
        Material safeMat = MaterialUtil.toItemMaterial(mat);
        ItemStack item = new ItemStack(safeMat != null ? safeMat : Material.STONE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(org.bg52.synthetix.util.TextUtil.parse(name));
            List<Component> lore = new ArrayList<>();
            for (String l : loreLines) {
                lore.add(org.bg52.synthetix.util.TextUtil.parse(l));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}

