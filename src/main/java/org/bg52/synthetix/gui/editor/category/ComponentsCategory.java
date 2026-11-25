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
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Editor category for Modern Data Components:
 * Durability & Physical Mechanics, Visuals & Cosmetics, and Special Behaviors.
 * Redesigned into distinct themed clusters with clean formatting.
 */
public class ComponentsCategory implements ItemEditorCategory {

    private final SynthetixPlugin plugin;
    private final LanguageManager lang;

    public ComponentsCategory(SynthetixPlugin plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    @Override
    public String getId() {
        return "components";
    }

    @Override
    public Component getDisplayName() {
        return TextUtil.parse("<aqua><bold>Data Components</bold></aqua>");
    }

    @Override
    public int getPriority() {
        return 30;
    }

    @Override
    public boolean isPluginCategory() {
        return false;
    }

    @Override
    public ItemStack getIcon(ItemDefinition def) {
        ItemStack item = new ItemStack(Material.RECOVERY_COMPASS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(getDisplayName());
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Advanced Minecraft components:</gray>"));
            lore.add(TextUtil.parse("<gray>gliders, glint overrides, trims, custom model.</gray>"));
            lore.add(Component.empty());

            lore.add(TextUtil.parse("<dark_gray>•</dark_gray> <gray>Unbreakable:</gray> " + (def.isUnbreakable() ? lang.get("general.enabled") : lang.get("general.disabled"))));
            lore.add(TextUtil.parse("<dark_gray>•</dark_gray> <gray>Glider:</gray> " + (def.isGlider() ? lang.get("general.enabled") : lang.get("general.disabled"))));
            lore.add(TextUtil.parse("<dark_gray>•</dark_gray> <gray>Fire Resistant:</gray> " + (def.isFireResistant() ? lang.get("general.enabled") : lang.get("general.disabled"))));
            if (def.getGlintOverride() != null) {
                lore.add(TextUtil.parse("<dark_gray>•</dark_gray> <gray>Glint Override:</gray> <yellow>" + def.getGlintOverride() + "</yellow>"));
            }
            if (def.getMaxDamage() != null) {
                lore.add(TextUtil.parse("<dark_gray>•</dark_gray> <gray>Max Durability:</gray> <yellow>" + def.getMaxDamage() + "</yellow>"));
            }
            if (def.getTrim() != null) {
                lore.add(TextUtil.parse("<dark_gray>•</dark_gray> <gray>Armor Trim:</gray> <yellow>" + def.getTrim().getPattern() + "</yellow>"));
            }

            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>Click to open settings »</yellow>"));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void open(Player player, ItemDefinition def, Runnable onBack) {
        BaseEditorCanvas base = new BaseEditorCanvas(plugin, def, getDisplayName(), 6);
        base.setupSideBorders();

        // Decorative cluster divider across row 3 (Slots 19..25)
        for (int s = 19; s <= 25; s++) {
            ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta pMeta = pane.getItemMeta();
            if (pMeta != null) {
                pMeta.displayName(Component.empty());
                pane.setItemMeta(pMeta);
            }
            base.getCanvas().setButton(s, CanvasButton.of(pane, e -> {}));
        }

        // =========================================================================
        // CLUSTER 1: DURABILITY & MECHANICS (Row 2: slots 10..13)
        // =========================================================================

        // 1. Unbreakable Toggle (Slot 10)
        ItemStack unbreakBtn = createToggleBtn(Material.ANVIL,
                lang.get("editor.categories.components.unbreakable-btn"),
                def.isUnbreakable(),
                lang.get("editor.categories.components.unbreakable-desc"));
        base.getCanvas().setButton(10, CanvasButton.of(unbreakBtn, e -> {
            def.setUnbreakable(!def.isUnbreakable());
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            open(player, def, onBack);
        }));

        // 2. Max Durability / max_damage (Slot 11)
        ItemStack duraBtn = createOptionBtn(Material.DIAMOND_PICKAXE,
                "<gold>Custom Max Durability</gold>",
                "<gray>Current:</gray> <white>" + (def.getMaxDamage() != null ? def.getMaxDamage() : "Default Material Durability") + "</white>",
                "",
                "<yellow>Left-Click: Enter custom max damage</yellow>",
                "<red>Right-Click: Reset to default</red>");
        base.getCanvas().setButton(11, CanvasButton.of(duraBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            if (e.isRightClick()) {
                def.setMaxDamage(null);
                open(player, def, onBack);
            } else {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter custom max durability integer (e.g. <yellow>2500</yellow>):</gray>"),
                        input -> {
                            try {
                                int val = Integer.parseInt(input.trim());
                                if (val > 0) {
                                    def.setMaxDamage(val);
                                    player.sendMessage(TextUtil.parse("<green>✓ Max durability set to " + val + "!</green>"));
                                }
                            } catch (NumberFormatException ignored) {}
                            open(player, def, onBack);
                        });
            }
        }));

        // 3. Max Stack Size (Slot 12) - Quick Cycle & Custom Input
        ItemStack stackBtn = createOptionBtn(Material.CHEST,
                lang.get("editor.categories.components.stack-size-btn"),
                "<gray>Current:</gray> <white>" + (def.getMaxStackSize() != null ? def.getMaxStackSize() : "Default (64)") + "</white>",
                "",
                "<yellow>Left-Click: Cycle preset (1 -> 16 -> 64 -> 99)</yellow>",
                "<yellow>Shift-Click: Type custom size in chat (1 to 99)</yellow>",
                "<red>Right-Click: Reset to default</red>");
        base.getCanvas().setButton(12, CanvasButton.of(stackBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            if (e.isRightClick() && !e.isShiftClick()) {
                def.setMaxStackSize(null);
                open(player, def, onBack);
            } else if (e.isShiftClick()) {
                plugin.getChatPromptManager().prompt(player,
                        lang.getComponent("editor.prompts.enter-stack-size"),
                        input -> {
                            try {
                                int size = Integer.parseInt(input.trim());
                                if (size >= 1 && size <= 99) {
                                    def.setMaxStackSize(size);
                                    lang.send(player, "editor.categories.components.stack-size-updated", "%size%", String.valueOf(size));
                                }
                            } catch (NumberFormatException ignored) {}
                            open(player, def, onBack);
                        });
            } else {
                int cur = def.getMaxStackSize() != null ? def.getMaxStackSize() : 64;
                if (cur <= 1) def.setMaxStackSize(16);
                else if (cur <= 16) def.setMaxStackSize(64);
                else if (cur <= 64) def.setMaxStackSize(99);
                else def.setMaxStackSize(1);
                open(player, def, onBack);
            }
        }));

        // 4. Fire Resistant Toggle (Slot 13)
        ItemStack fireBtn = createToggleBtn(Material.LAVA_BUCKET,
                lang.get("editor.categories.components.fire-resistant-btn"),
                def.isFireResistant(),
                lang.get("editor.categories.components.fire-resistant-desc"));
        base.getCanvas().setButton(13, CanvasButton.of(fireBtn, e -> {
            def.setFireResistant(!def.isFireResistant());
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            open(player, def, onBack);
        }));

        // =========================================================================
        // CLUSTER 3: SPECIAL POWERS (Slot 16)
        // =========================================================================

        // 5. Glider Elytra Flight Toggle (Slot 16)
        ItemStack gliderBtn = createToggleBtn(Material.ELYTRA,
                lang.get("editor.categories.components.glider-btn"),
                def.isGlider(),
                lang.get("editor.categories.components.glider-desc"));
        base.getCanvas().setButton(16, CanvasButton.of(gliderBtn, e -> {
            def.setGlider(!def.isGlider());
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            open(player, def, onBack);
        }));

        // =========================================================================
        // CLUSTER 2: VISUALS & COSMETICS (Row 4 & 5: slots 28..34)
        // =========================================================================

        // 6. Enchantment Glint Override (Slot 28)
        String glintState;
        if (def.getGlintOverride() == null) {
            glintState = "<gray>Default (Vanilla)</gray>";
        } else if (def.getGlintOverride()) {
            glintState = "<green><bold>FORCED SHINY</bold></green>";
        } else {
            glintState = "<red><bold>FORCED NON-SHINY</bold></red>";
        }
        ItemStack glintBtn = createOptionBtn(Material.NETHER_STAR,
                "<gold>Glint Override</gold>",
                "<gray>State:</gray> " + glintState,
                "",
                "<yellow>Click to cycle:</yellow>",
                "<gray>[Default] -> [Forced Shiny] -> [Forced Non-Shiny]</gray>");
        base.getCanvas().setButton(28, CanvasButton.of(glintBtn, e -> {
            if (def.getGlintOverride() == null) {
                def.setGlintOverride(true);
            } else if (def.getGlintOverride()) {
                def.setGlintOverride(false);
            } else {
                def.setGlintOverride(null);
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            open(player, def, onBack);
        }));

        // 7. Armor Trim Editor (Slot 29)
        ItemStack trimBtn = createOptionBtn(Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE,
                "<gold>Armor Trim Editor</gold>",
                "<gray>Current:</gray> <white>" + (def.getTrim() != null ? def.getTrim().getPattern().toUpperCase() + " / " + def.getTrim().getMaterial().toUpperCase() : "None") + "</white>",
                "",
                "<yellow>Click to configure armor trim pattern & material »</yellow>");
        base.getCanvas().setButton(29, CanvasButton.of(trimBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            ArmorTrimGUI.open(player, plugin, def, () -> open(player, def, onBack));
        }));

        // 8. Custom RGB Color Picker (Slot 30)
        org.bukkit.Color curCol = def.getCustomColor();
        String hexStr = curCol != null ? String.format("#%02X%02X%02X", curCol.getRed(), curCol.getGreen(), curCol.getBlue()) : "None";
        ItemStack colorBtn = createOptionBtn(Material.LEATHER_CHESTPLATE,
                "<gold>Custom RGB Color</gold>",
                "<gray>Current:</gray> <white>" + hexStr + "</white>",
                "",
                "<yellow>Left-Click: Open Color Picker »</yellow>",
                "<red>Right-Click: Clear custom color</red>");
        base.getCanvas().setButton(30, CanvasButton.of(colorBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            if (e.isRightClick()) {
                def.setCustomColor(null);
                open(player, def, onBack);
            } else {
                ColorPickerGUI.open(player, plugin, def, () -> open(player, def, onBack));
            }
        }));

        // 9. Hide Tooltip Toggle (Slot 31)
        ItemStack tooltipBtn = createToggleBtn(Material.MAP,
                "<aqua>Hide Tooltip</aqua>",
                def.isHideTooltip(),
                "Hides the entire item tooltip including name and lore.");
        base.getCanvas().setButton(31, CanvasButton.of(tooltipBtn, e -> {
            def.setHideTooltip(!def.isHideTooltip());
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            open(player, def, onBack);
        }));

        // 10. Hide Enchants Toggle (Slot 32)
        ItemStack hideEnchBtn = createToggleBtn(Material.GLASS_BOTTLE,
                lang.get("editor.categories.components.hide-enchants-btn"),
                def.isHideEnchants(),
                lang.get("editor.categories.components.hide-enchants-desc"));
        base.getCanvas().setButton(32, CanvasButton.of(hideEnchBtn, e -> {
            def.setHideEnchants(!def.isHideEnchants());
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            open(player, def, onBack);
        }));

        // 11. Item Flags Sub-Menu (Slot 33)
        ItemStack flagsBtn = createOptionBtn(Material.WHITE_BANNER,
                lang.get("editor.categories.components.flags-menu-btn"),
                "<gray>Current Active Flags:</gray> <white>" + def.getItemFlags().size() + "</white>",
                "",
                "<yellow>Click to browse all Bukkit item flags »</yellow>");
        base.getCanvas().setButton(33, CanvasButton.of(flagsBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            openItemFlagsMenu(player, def, () -> open(player, def, onBack));
        }));

        // 12. Item Model Component (Slot 34)
        ItemStack modelBtn = createOptionBtn(Material.ITEM_FRAME,
                "<gold>Item Model Key (1.21.2+)</gold>",
                "<gray>Current:</gray> <white>" + (def.getItemModel() != null ? def.getItemModel() : "None") + "</white>",
                "",
                "<yellow>Left-Click: Enter model NamespacedKey</yellow>",
                "<red>Right-Click: Clear model key</red>");
        base.getCanvas().setButton(34, CanvasButton.of(modelBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            if (e.isRightClick()) {
                def.setItemModel(null);
                open(player, def, onBack);
            } else {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter NamespacedKey (e.g. <yellow>synthetix:ruby_sword</yellow>):</gray>"),
                        input -> {
                            if (!input.trim().isEmpty()) def.setItemModel(input.trim());
                            open(player, def, onBack);
                        });
            }
        }));

        base.setBackButton(onBack);
        base.setSaveButton();
        base.open(player);
    }

    private void openItemFlagsMenu(Player player, ItemDefinition def, Runnable onBack) {
        ItemFlag[] allFlags = ItemFlag.values();
        BaseEditorCanvas base = new BaseEditorCanvas(plugin, def, lang.getComponent("editor.categories.components.flags-menu-title"), 4);

        int slot = 10;
        for (ItemFlag flag : allFlags) {
            boolean active = def.getItemFlags().contains(flag);
            ItemStack btn = createToggleBtn(active ? Material.LIME_DYE : Material.GRAY_DYE,
                    "<white>" + flag.name() + "</white>",
                    active,
                    "Toggles " + flag.name());

            base.getCanvas().setButton(slot, CanvasButton.of(btn, e -> {
                if (def.getItemFlags().contains(flag)) {
                    def.getItemFlags().remove(flag);
                } else {
                    def.getItemFlags().add(flag);
                }
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                openItemFlagsMenu(player, def, onBack);
            }));

            slot++;
            if (slot == 17) slot = 19;
            if (slot >= 26) break;
        }

        base.setBackButton(onBack);
        base.open(player);
    }

    private ItemStack createToggleBtn(Material mat, String name, boolean enabled, String desc) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.parse(name));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>" + desc + "</gray>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<gray>Status:</gray> " + (enabled ? lang.get("general.enabled") : lang.get("general.disabled"))));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>Click to toggle »</yellow>"));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createOptionBtn(Material mat, String name, String... loreLines) {
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
