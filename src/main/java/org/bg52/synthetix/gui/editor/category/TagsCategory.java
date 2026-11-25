package org.bg52.synthetix.gui.editor.category;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.api.editor.ItemEditorCategory;
import org.bg52.synthetix.gui.CanvasButton;
import org.bg52.synthetix.gui.editor.BaseEditorCanvas;
import org.bg52.synthetix.item.ItemDefinition;
import org.bg52.synthetix.lang.LanguageManager;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Core category for Persistent Data Container (PDC) custom metadata and tags.
 * Fully generalized for arbitrary persistent data keys.
 * Third-party addon tags (such as Curios accessory slots) are contributed via the Addon SPI.
 */
public class TagsCategory implements ItemEditorCategory {

    private final SynthetixPlugin plugin;
    private final LanguageManager lang;

    public TagsCategory(SynthetixPlugin plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    @Override
    public String getId() {
        return "tags";
    }

    @Override
    public Component getDisplayName() {
        return lang.getComponent("editor.categories.tags.name");
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public boolean isPluginCategory() {
        return false;
    }

    @Override
    public ItemStack getIcon(ItemDefinition def) {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(getDisplayName());
            List<Component> lore = new ArrayList<>();
            lore.add(lang.getComponent("editor.categories.tags.desc-1"));
            lore.add(lang.getComponent("editor.categories.tags.desc-2"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<dark_gray>•</dark_gray> <gray>Total PDC Tags:</gray> <white>" + def.getTags().size() + "</white>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>Click to open settings »</yellow>"));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void open(Player player, ItemDefinition def, Runnable onBack) {
        BaseEditorCanvas base = new BaseEditorCanvas(plugin, def, getDisplayName(), 4);
        base.setupSideBorders();

        // Symmetrical layout: 2 centered action buttons at slot 12 and 14
        // 1. Add New Custom Tag (Slot 12)
        ItemStack addTagBtn = new ItemStack(Material.ANVIL);
        ItemMeta aMeta = addTagBtn.getItemMeta();
        if (aMeta != null) {
            aMeta.displayName(lang.getComponent("editor.categories.tags.add-tag-btn"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<gray>Add an arbitrary key=value tag to the item.</gray>"));
            l.add(Component.empty());
            l.add(TextUtil.parse("<yellow>Click to type key=value in chat.</yellow>"));
            aMeta.lore(l);
            addTagBtn.setItemMeta(aMeta);
        }
        base.getCanvas().setButton(12, CanvasButton.of(addTagBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            plugin.getChatPromptManager().prompt(player,
                    lang.getComponent("editor.prompts.enter-tag"),
                    input -> {
                        if (input.contains("=")) {
                            String[] parts = input.split("=", 2);
                            String k = parts[0].trim();
                            String v = parts[1].trim();
                            def.setTag(k, v);
                            lang.send(player, "editor.categories.tags.tag-saved", "%key%", k, "%value%", v);
                        } else {
                            player.sendMessage(TextUtil.parse("<red>Invalid format! Must use key=value.</red>"));
                        }
                        open(player, def, onBack);
                    });
        }));

        // 2. View / Delete Active Tags (Slot 14)
        ItemStack listTagsBtn = new ItemStack(Material.BOOK);
        ItemMeta lMeta = listTagsBtn.getItemMeta();
        if (lMeta != null) {
            lMeta.displayName(lang.getComponent("editor.categories.tags.manage-tags-btn", "%count%", String.valueOf(def.getTags().size())));
            List<Component> l = new ArrayList<>();
            if (def.getTags().isEmpty()) {
                l.add(TextUtil.parse("<dark_gray>No custom tags currently applied.</dark_gray>"));
            } else {
                for (Map.Entry<String, String> entry : def.getTags().entrySet()) {
                    l.add(TextUtil.parse("<dark_gray>•</dark_gray> <yellow>" + entry.getKey() + "</yellow> = <white>" + entry.getValue() + "</white>"));
                }
            }
            l.add(Component.empty());
            l.add(TextUtil.parse("<yellow>Click to manage / delete tags »</yellow>"));
            lMeta.lore(l);
            listTagsBtn.setItemMeta(lMeta);
        }
        base.getCanvas().setButton(14, CanvasButton.of(listTagsBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            openTagManager(player, def, () -> open(player, def, onBack));
        }));

        base.setBackButton(onBack);
        base.setSaveButton();
        base.open(player);
    }

    private void openTagManager(Player player, ItemDefinition def, Runnable onBack) {
        List<Map.Entry<String, String>> tagEntries = new ArrayList<>(def.getTags().entrySet());
        BaseEditorCanvas base = new BaseEditorCanvas(plugin, def, lang.getComponent("editor.categories.tags.manage-tags-title"), 5);

        int slot = 9;
        for (Map.Entry<String, String> entry : tagEntries) {
            ItemStack icon = new ItemStack(Material.PAPER);
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.displayName(TextUtil.parse("<yellow>" + entry.getKey() + "</yellow>"));
                List<Component> l = new ArrayList<>();
                l.add(TextUtil.parse("<gray>Value:</gray> <white>" + entry.getValue() + "</white>"));
                l.add(Component.empty());
                l.add(TextUtil.parse("<red>Click to delete this tag.</red>"));
                meta.lore(l);
                icon.setItemMeta(meta);
            }
            base.getCanvas().setButton(slot++, CanvasButton.of(icon, e -> {
                def.getTags().remove(entry.getKey());
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.7f, 1.0f);
                lang.send(player, "editor.categories.tags.tag-deleted", "%key%", entry.getKey());
                openTagManager(player, def, onBack);
            }));
            if (slot > 35) break;
        }

        base.setBackButton(onBack);
        base.open(player);
    }
}
