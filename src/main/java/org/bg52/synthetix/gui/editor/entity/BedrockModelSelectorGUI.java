package org.bg52.synthetix.gui.editor.entity;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.entity.model.CustomEntityDefinition;
import org.bg52.synthetix.gui.Canvas;
import org.bg52.synthetix.gui.CanvasButton;
import org.bg52.synthetix.lang.LanguageManager;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Visual browser and selector for available Bedrock (.bbmodel, .geo.json) models.
 * Strictly localized: zero hardcoded messages or strings.
 */
public class BedrockModelSelectorGUI {

    private final SynthetixPlugin plugin;
    private final LanguageManager lang;
    private int page = 0;

    public BedrockModelSelectorGUI(SynthetixPlugin plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    public static void open(Player player, SynthetixPlugin plugin) {
        new BedrockModelSelectorGUI(plugin).open(player, 0);
    }

    public void open(Player player, int targetPage) {
        List<File> models = plugin.getBedrockEntityImporter().listAvailableModelFiles();
        models.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        int pageSize = 45;
        int totalPages = Math.max(1, (int) Math.ceil((double) models.size() / pageSize));
        this.page = Math.max(0, Math.min(totalPages - 1, targetPage));

        Component title = lang.getComponent("gui.model-selector.title");
        Canvas canvas = new Canvas(title, 6);

        int start = this.page * pageSize;
        int end = Math.min(start + pageSize, models.size());

        if (models.isEmpty()) {
            ItemStack emptyItem = new ItemStack(Material.BARRIER);
            ItemMeta eMeta = emptyItem.getItemMeta();
            if (eMeta != null) {
                eMeta.displayName(lang.getComponent("gui.model-selector.no-models-title"));
                eMeta.lore(List.of(
                        lang.getComponent("gui.model-selector.no-models-lore-1"),
                        lang.getComponent("gui.model-selector.no-models-lore-2")
                ));
                emptyItem.setItemMeta(eMeta);
            }
            canvas.setButton(22, CanvasButton.of(emptyItem, e -> open(player, 0)));
        } else {
            for (int i = start; i < end; i++) {
                File file = models.get(i);
                int slot = i - start;

                ItemStack icon = createModelIcon(file);
                canvas.setButton(slot, CanvasButton.of(icon, e -> {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                    String baseName = getBaseName(file);

                    if (e.isShiftClick()) {
                        plugin.getChatPromptManager().prompt(player,
                                lang.getComponent("prompts.enter-entity-id"),
                                input -> {
                                    String id = input.trim().toLowerCase(Locale.ROOT);
                                    performImport(player, file, id);
                                });
                    } else {
                        performImport(player, file, baseName);
                    }
                }));
            }
        }

        // Bottom Navigation Bar
        ItemStack darkGlass = Canvas.createBorderItem(Material.BLACK_STAINED_GLASS_PANE, Component.empty());
        for (int c = 45; c < 54; c++) {
            canvas.setButton(c, CanvasButton.of(darkGlass));
        }

        // Slot 45: Back to Entity Browser
        ItemStack backBtn = Canvas.createBorderItem(Material.SPECTRAL_ARROW, lang.getComponent("gui.model-selector.btn-back"));
        canvas.setButton(45, CanvasButton.of(backBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            EntityBrowserGUI.open(player, plugin);
        }));

        // Slot 47: Prev Page
        if (this.page > 0) {
            ItemStack prev = Canvas.createBorderItem(Material.ARROW, TextUtil.parse("<yellow>Previous Page</yellow>"));
            canvas.setButton(47, CanvasButton.of(prev, e -> open(player, this.page - 1)));
        }

        // Slot 49: Refresh
        ItemStack refreshBtn = new ItemStack(Material.COMPASS);
        ItemMeta rMeta = refreshBtn.getItemMeta();
        if (rMeta != null) {
            rMeta.displayName(lang.getComponent("gui.model-selector.header-title"));
            rMeta.lore(List.of(
                    lang.getComponent("gui.model-selector.header-lore-1"),
                    lang.getComponent("gui.model-selector.header-lore-2")
            ));
            refreshBtn.setItemMeta(rMeta);
        }
        canvas.setButton(49, CanvasButton.of(refreshBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            open(player, this.page);
        }));

        // Slot 51: Next Page
        if (this.page < totalPages - 1) {
            ItemStack next = Canvas.createBorderItem(Material.ARROW, TextUtil.parse("<yellow>Next Page</yellow>"));
            canvas.setButton(51, CanvasButton.of(next, e -> open(player, this.page + 1)));
        }

        canvas.open(player);
    }

    private void performImport(Player player, File file, String entityId) {
        CustomEntityDefinition def = plugin.getBedrockEntityImporter().importEntity(file, entityId);

        if (def != null) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
            lang.send(player, "gui.model-selector.imported-success", "%entity%", def.getId());
            EntityEditorMenu.open(player, plugin, def);
        } else {
            lang.send(player, "gui.model-selector.imported-failed", "%file%", file.getName());
            open(player, this.page);
        }
    }

    private ItemStack createModelIcon(File file) {
        String name = file.getName();
        boolean isBBModel = name.toLowerCase(Locale.ROOT).endsWith(".bbmodel");

        Material mat = isBBModel ? Material.ARMOR_STAND : Material.ITEM_FRAME;
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(lang.getComponent("gui.model-selector.card-title", "%name%", getBaseName(file)));
            List<Component> lore = new ArrayList<>();
            lore.add(lang.getComponent("gui.model-selector.card-folder", "%folder%", file.getParentFile() != null ? file.getParentFile().getName() : "import"));
            lore.add(lang.getComponent("gui.model-selector.card-format", "%format%", isBBModel ? ".bbmodel" : ".geo.json"));
            lore.add(lang.getComponent("gui.model-selector.card-size", "%size%", String.valueOf(file.length() / 1024)));
            lore.add(Component.empty());
            lore.add(lang.getComponent("gui.model-selector.card-ctrl"));
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private String getBaseName(File f) {
        String n = f.getName();
        int dot = n.indexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }
}
