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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Core category for item identity, names, lore, materials, and model data.
 * Fully localized via LanguageManager.
 */
public class IdentityCategory implements ItemEditorCategory {

    private final SynthetixPlugin plugin;
    private final LanguageManager lang;

    public IdentityCategory(SynthetixPlugin plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    @Override
    public String getId() {
        return "identity";
    }

    @Override
    public Component getDisplayName() {
        return lang.getComponent("editor.categories.identity.name");
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public boolean isPluginCategory() {
        return false;
    }

    @Override
    public ItemStack getIcon(ItemDefinition def) {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(getDisplayName());
            List<Component> lore = new ArrayList<>();
            lore.add(lang.getComponent("editor.categories.identity.desc-1"));
            lore.add(lang.getComponent("editor.categories.identity.desc-2"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<dark_gray>•</dark_gray> <gray>Name:</gray> " + def.getDisplayName()));
            lore.add(TextUtil.parse("<dark_gray>•</dark_gray> <gray>Material:</gray> <white>" + def.getMaterial().name() + "</white>"));
            lore.add(TextUtil.parse("<dark_gray>•</dark_gray> <gray>Lore lines:</gray> <white>" + def.getLore().size() + "</white>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>Click to open settings »</yellow>"));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void open(Player player, ItemDefinition def, Runnable onBack) {
        BaseEditorCanvas base = new BaseEditorCanvas(plugin, def, getDisplayName(), 5);
        base.setupSideBorders();

        // 1. Display Name (Slot 10)
        ItemStack nameBtn = createBtn(Material.NAME_TAG,
                lang.get("editor.categories.identity.display-name-btn"),
                "<gray>Current:</gray> " + def.getDisplayName(),
                "",
                "<yellow>Click to edit via chat prompt.</yellow>");
        base.getCanvas().setButton(10, CanvasButton.of(nameBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            plugin.getChatPromptManager().prompt(player,
                    lang.getComponent("editor.prompts.enter-name", "%item%", def.getId()),
                    input -> {
                        def.setDisplayName(input);
                        lang.send(player, "editor.categories.identity.name-updated");
                        open(player, def, onBack);
                    });
        }));

        // 2. Native Item Rarity (Slot 11)
        org.bukkit.inventory.ItemRarity curRarity = def.getRarity();
        String rarityColor = switch (curRarity != null ? curRarity : org.bukkit.inventory.ItemRarity.COMMON) {
            case COMMON -> "<white>COMMON</white>";
            case UNCOMMON -> "<yellow>UNCOMMON</yellow>";
            case RARE -> "<aqua>RARE</aqua>";
            case EPIC -> "<light_purple>EPIC</light_purple>";
        };
        ItemStack rarityBtn = createBtn(Material.AMETHYST_SHARD,
                "<gold>Item Rarity</gold>",
                "<gray>Current:</gray> " + (curRarity != null ? rarityColor : "<gray>Default (COMMON)</gray>"),
                "",
                "<yellow>Click to cycle rarity:</yellow>",
                "<gray>[Default] -> [COMMON] -> [UNCOMMON] -> [RARE] -> [EPIC]</gray>");
        base.getCanvas().setButton(11, CanvasButton.of(rarityBtn, e -> {
            if (def.getRarity() == null) {
                def.setRarity(org.bukkit.inventory.ItemRarity.COMMON);
            } else if (def.getRarity() == org.bukkit.inventory.ItemRarity.COMMON) {
                def.setRarity(org.bukkit.inventory.ItemRarity.UNCOMMON);
            } else if (def.getRarity() == org.bukkit.inventory.ItemRarity.UNCOMMON) {
                def.setRarity(org.bukkit.inventory.ItemRarity.RARE);
            } else if (def.getRarity() == org.bukkit.inventory.ItemRarity.RARE) {
                def.setRarity(org.bukkit.inventory.ItemRarity.EPIC);
            } else {
                def.setRarity(null);
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            open(player, def, onBack);
        }));

        // 3. Material Selector (Slot 12)
        ItemStack matBtn = createBtn(Material.GRASS_BLOCK,
                lang.get("editor.categories.identity.material-btn"),
                "<gray>Current:</gray> <white>" + def.getMaterial().name() + "</white>",
                "",
                "<yellow>Click to browse all materials »</yellow>");
        base.getCanvas().setButton(12, CanvasButton.of(matBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            openMaterialSelector(player, def, () -> open(player, def, onBack), 0, null);
        }));

        // 4. Custom RGB Color Picker (Slot 13)
        org.bukkit.Color curCol = def.getCustomColor();
        String hexStr = curCol != null ? String.format("#%02X%02X%02X", curCol.getRed(), curCol.getGreen(), curCol.getBlue()) : "None";
        ItemStack colorBtn = createBtn(Material.LEATHER_CHESTPLATE,
                "<gold>Leather / Potion Color</gold>",
                "<gray>Current:</gray> <white>" + hexStr + "</white>",
                "",
                "<yellow>Click to open Color Picker GUI »</yellow>");
        base.getCanvas().setButton(13, CanvasButton.of(colorBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            ColorPickerGUI.open(player, plugin, def, () -> open(player, def, onBack));
        }));

        // 5. Lore Editor (Slot 14)
        ItemStack loreBtn = createBtn(Material.FEATHER,
                lang.get("editor.categories.identity.lore-btn"),
                "<gray>Total Lines:</gray> <white>" + def.getLore().size() + "</white>",
                "",
                "<yellow>Left-Click: Add line</yellow>",
                "<red>Right-Click: Clear all lore</red>");
        base.getCanvas().setButton(14, CanvasButton.of(loreBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            if (e.isRightClick()) {
                def.getLore().clear();
                lang.send(player, "editor.categories.identity.lore-cleared");
                open(player, def, onBack);
            } else {
                plugin.getChatPromptManager().prompt(player,
                        lang.getComponent("editor.prompts.enter-lore"),
                        line -> {
                            def.getLore().add(line);
                            lang.send(player, "editor.categories.identity.lore-added");
                            open(player, def, onBack);
                        });
            }
        }));

        // 6. Custom Model Data (Slot 15)
        ItemStack cmdBtn = createBtn(Material.PAINTING,
                lang.get("editor.categories.identity.cmd-btn"),
                "<gray>Current:</gray> <white>" + (def.getCustomModelData() != null ? def.getCustomModelData() : "None") + "</white>",
                "",
                "<yellow>Left-Click: Set integer</yellow>",
                "<red>Right-Click: Clear</red>");
        base.getCanvas().setButton(15, CanvasButton.of(cmdBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            if (e.isRightClick()) {
                def.setCustomModelData(null);
                open(player, def, onBack);
            } else {
                plugin.getChatPromptManager().prompt(player,
                        lang.getComponent("editor.prompts.enter-cmd"),
                        input -> {
                            try {
                                def.setCustomModelData(Integer.parseInt(input.trim()));
                                lang.send(player, "editor.categories.identity.cmd-updated");
                            } catch (NumberFormatException ignored) {}
                            open(player, def, onBack);
                        });
            }
        }));

        // 7. Item Model NamespacedKey (Slot 16)
        ItemStack itemModelBtn = createBtn(Material.ITEM_FRAME,
                lang.get("editor.categories.identity.item-model-btn"),
                "<gray>Current:</gray> <white>" + (def.getItemModel() != null ? def.getItemModel() : "None") + "</white>",
                "",
                "<yellow>Left-Click: Set NamespacedKey</yellow>",
                "<red>Right-Click: Clear</red>");
        base.getCanvas().setButton(16, CanvasButton.of(itemModelBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            if (e.isRightClick()) {
                def.setItemModel(null);
                open(player, def, onBack);
            } else {
                plugin.getChatPromptManager().prompt(player,
                        lang.getComponent("editor.prompts.enter-model-key"),
                        input -> {
                            def.setItemModel(input.trim());
                            lang.send(player, "editor.categories.identity.model-updated");
                            open(player, def, onBack);
                        });
            }
        }));

        base.setBackButton(onBack);
        base.setSaveButton();
        base.open(player);
    }

    private void openMaterialSelector(Player player, ItemDefinition def, Runnable onBack, int page, String searchQuery) {
        var stream = Arrays.stream(Material.values())
                .filter(Material::isItem)
                .filter(m -> m != Material.AIR);

        if (searchQuery != null && !searchQuery.trim().isEmpty()) {
            String query = searchQuery.trim().toUpperCase(Locale.ROOT);
            stream = stream.filter(m -> m.name().contains(query));
        }

        List<Material> itemMaterials = stream.sorted().toList();

        int pageSize = 28;
        int totalPages = Math.max(1, (int) Math.ceil((double) itemMaterials.size() / pageSize));
        int curPage = Math.max(0, Math.min(totalPages - 1, page));

        String title = lang.get("editor.categories.identity.material-title") + " <dark_gray>(" + (curPage + 1) + "/" + totalPages + ")</dark_gray>";
        if (searchQuery != null && !searchQuery.isEmpty()) {
            title += " <yellow>[\"" + searchQuery + "\"]</yellow>";
        }

        BaseEditorCanvas base = new BaseEditorCanvas(plugin, def, TextUtil.parse(title), 6);

        int start = curPage * pageSize;
        int end = Math.min(start + pageSize, itemMaterials.size());

        int slotIndex = 0;
        int[] validSlots = new int[]{
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        for (int i = start; i < end; i++) {
            Material mat = itemMaterials.get(i);
            ItemStack icon = new ItemStack(mat);
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.displayName(TextUtil.parse("<yellow>" + mat.name() + "</yellow>"));
                List<Component> l = new ArrayList<>();
                if (mat == def.getMaterial()) {
                    l.add(lang.getComponent("general.selected"));
                } else {
                    l.add(lang.getComponent("general.not-selected"));
                }
                meta.lore(l);
                icon.setItemMeta(meta);
            }

            int targetSlot = validSlots[slotIndex++];
            base.getCanvas().setButton(targetSlot, CanvasButton.of(icon, e -> {
                def.setMaterial(mat);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                lang.send(player, "editor.categories.identity.mat-updated", "%mat%", mat.name());
                onBack.run();
            }));
        }

        // Search Button (Slot 49)
        ItemStack searchBtn = createBtn(Material.COMPASS,
                "<yellow><bold>Search Materials</bold></yellow>",
                searchQuery != null ? "<gray>Filter:</gray> <white>" + searchQuery + "</white>" : "<gray>Filter: None</gray>",
                "",
                "<yellow>Click to type search keyword »</yellow>");
        base.getCanvas().setButton(49, CanvasButton.of(searchBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            plugin.getChatPromptManager().prompt(player,
                    TextUtil.parse("<gray>Enter material name or keyword (e.g. <yellow>sword</yellow>, <yellow>netherite</yellow>, <yellow>bow</yellow>):</gray>"),
                    input -> openMaterialSelector(player, def, onBack, 0, input.trim()));
        }));

        // Clear Search Button (Slot 50 if active)
        if (searchQuery != null && !searchQuery.isEmpty()) {
            ItemStack clearBtn = createBtn(Material.BARRIER,
                    "<red><bold>Clear Search Filter</bold></red>",
                    "<gray>Resets filter to show all materials.</gray>");
            base.getCanvas().setButton(50, CanvasButton.of(clearBtn, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
                openMaterialSelector(player, def, onBack, 0, null);
            }));
        }

        base.setBackButton(onBack);
        base.setPaginationButtons(
                curPage > 0, () -> openMaterialSelector(player, def, onBack, curPage - 1, searchQuery),
                curPage < totalPages - 1, () -> openMaterialSelector(player, def, onBack, curPage + 1, searchQuery)
        );

        base.open(player);
    }

    private ItemStack createBtn(Material mat, String name, String... loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.parse(name));
            List<Component> lore = new ArrayList<>();
            for (String l : loreLines) {
                lore.add(TextUtil.parse(l));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
