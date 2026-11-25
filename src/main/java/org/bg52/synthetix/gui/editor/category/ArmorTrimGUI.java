package org.bg52.synthetix.gui.editor.category;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.gui.CanvasButton;
import org.bg52.synthetix.gui.editor.BaseEditorCanvas;
import org.bg52.synthetix.item.ItemDefinition;
import org.bg52.synthetix.item.component.ArmorTrimSpec;
import org.bg52.synthetix.lang.LanguageManager;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Visual Armor Trim selector GUI for custom items.
 * Allows selecting trim patterns and trim materials with real-time preview.
 */
public class ArmorTrimGUI {

    private final SynthetixPlugin plugin;
    private final LanguageManager lang;
    private final ItemDefinition itemDef;
    private final Runnable onBack;

    private static final String[] PATTERNS = new String[]{
            "sentry", "vex", "wild", "coast", "dune", "wayfinder",
            "raiser", "shaper", "host", "ward", "silence", "tide",
            "snout", "rib", "eye", "spire", "flow", "bolt"
    };

    private static final String[] MATERIALS = new String[]{
            "netherite", "diamond", "gold", "emerald", "amethyst",
            "iron", "copper", "redstone", "lapis", "quartz"
    };

    public ArmorTrimGUI(SynthetixPlugin plugin, ItemDefinition itemDef, Runnable onBack) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
        this.itemDef = itemDef;
        this.onBack = onBack;
    }

    public static void open(Player player, SynthetixPlugin plugin, ItemDefinition itemDef, Runnable onBack) {
        new ArmorTrimGUI(plugin, itemDef, onBack).open(player);
    }

    public void open(Player player) {
        BaseEditorCanvas base = new BaseEditorCanvas(plugin, itemDef, TextUtil.parse("<gold><bold>Armor Trim Settings</bold></gold>"), 6);
        base.setupSideBorders();

        ArmorTrimSpec current = itemDef.getTrim();

        // 1. Select Pattern Button (Slot 20)
        ItemStack patBtn = new ItemStack(Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE);
        ItemMeta pMeta = patBtn.getItemMeta();
        if (pMeta != null) {
            pMeta.displayName(TextUtil.parse("<gold>Trim Pattern</gold>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Current:</gray> <white>" + (current != null ? current.getPattern().toUpperCase() : "None") + "</white>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>Click to browse all 18 patterns »</yellow>"));
            pMeta.lore(lore);
            patBtn.setItemMeta(pMeta);
        }
        base.getCanvas().setButton(20, CanvasButton.of(patBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            openPatternSelector(player);
        }));

        // 2. Select Material Button (Slot 24)
        ItemStack matBtn = new ItemStack(Material.NETHERITE_INGOT);
        ItemMeta mMeta = matBtn.getItemMeta();
        if (mMeta != null) {
            mMeta.displayName(TextUtil.parse("<gold>Trim Material</gold>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Current:</gray> <white>" + (current != null ? current.getMaterial().toUpperCase() : "None") + "</white>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>Click to browse all 10 materials »</yellow>"));
            mMeta.lore(lore);
            matBtn.setItemMeta(mMeta);
        }
        base.getCanvas().setButton(24, CanvasButton.of(matBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            openMaterialSelector(player);
        }));

        // 3. Clear Trim Button (Slot 31)
        ItemStack clearBtn = new ItemStack(Material.BARRIER);
        ItemMeta cMeta = clearBtn.getItemMeta();
        if (cMeta != null) {
            cMeta.displayName(TextUtil.parse("<red><bold>Clear Armor Trim</bold></red>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Removes armor trim from this item.</gray>"));
            cMeta.lore(lore);
            clearBtn.setItemMeta(cMeta);
        }
        base.getCanvas().setButton(31, CanvasButton.of(clearBtn, e -> {
            itemDef.setTrim(null);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            open(player);
        }));

        base.setBackButton(onBack);
        base.setSaveButton();
        base.open(player);
    }

    private void openPatternSelector(Player player) {
        BaseEditorCanvas base = new BaseEditorCanvas(plugin, itemDef, TextUtil.parse("<gold>Select Trim Pattern</gold>"), 5);
        base.setupSideBorders();

        ArmorTrimSpec cur = itemDef.getTrim();
        String currentPattern = cur != null ? cur.getPattern() : "";

        int[] slots = new int[]{
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                29, 30, 31, 32
        };

        for (int i = 0; i < PATTERNS.length && i < slots.length; i++) {
            String pat = PATTERNS[i];
            Material templateMat = getTrimTemplateMaterial(pat);
            ItemStack icon = new ItemStack(templateMat);
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                boolean active = pat.equalsIgnoreCase(currentPattern);
                meta.displayName(TextUtil.parse((active ? "<green>" : "<yellow>") + pat.toUpperCase() + "</yellow>"));
                List<Component> lore = new ArrayList<>();
                lore.add(active ? lang.getComponent("general.selected") : lang.getComponent("general.not-selected"));
                lore.add(Component.empty());
                lore.add(TextUtil.parse("<yellow>Click to select pattern</yellow>"));
                meta.lore(lore);
                icon.setItemMeta(meta);
            }

            base.getCanvas().setButton(slots[i], CanvasButton.of(icon, e -> {
                ArmorTrimSpec spec = itemDef.getTrim();
                if (spec == null) {
                    spec = new ArmorTrimSpec(pat, "netherite");
                } else {
                    spec.setPattern(pat);
                }
                itemDef.setTrim(spec);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                open(player);
            }));
        }

        base.setBackButton(() -> open(player));
        base.open(player);
    }

    private void openMaterialSelector(Player player) {
        BaseEditorCanvas base = new BaseEditorCanvas(plugin, itemDef, TextUtil.parse("<gold>Select Trim Material</gold>"), 4);
        base.setupSideBorders();

        ArmorTrimSpec cur = itemDef.getTrim();
        String currentMaterial = cur != null ? cur.getMaterial() : "";

        int[] slots = new int[]{
                11, 12, 13, 14, 15,
                20, 21, 22, 23, 24
        };

        Material[] sampleMats = new Material[]{
                Material.NETHERITE_INGOT, Material.DIAMOND, Material.GOLD_INGOT, Material.EMERALD, Material.AMETHYST_SHARD,
                Material.IRON_INGOT, Material.COPPER_INGOT, Material.REDSTONE, Material.LAPIS_LAZULI, Material.QUARTZ
        };

        for (int i = 0; i < MATERIALS.length && i < slots.length; i++) {
            String mat = MATERIALS[i];
            ItemStack icon = new ItemStack(sampleMats[i]);
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                boolean active = mat.equalsIgnoreCase(currentMaterial);
                meta.displayName(TextUtil.parse((active ? "<green>" : "<yellow>") + mat.toUpperCase() + "</yellow>"));
                List<Component> lore = new ArrayList<>();
                lore.add(active ? lang.getComponent("general.selected") : lang.getComponent("general.not-selected"));
                lore.add(Component.empty());
                lore.add(TextUtil.parse("<yellow>Click to select material</yellow>"));
                meta.lore(lore);
                icon.setItemMeta(meta);
            }

            base.getCanvas().setButton(slots[i], CanvasButton.of(icon, e -> {
                ArmorTrimSpec spec = itemDef.getTrim();
                if (spec == null) {
                    spec = new ArmorTrimSpec("silence", mat);
                } else {
                    spec.setMaterial(mat);
                }
                itemDef.setTrim(spec);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                open(player);
            }));
        }

        base.setBackButton(() -> open(player));
        base.open(player);
    }

    private Material getTrimTemplateMaterial(String pattern) {
        if (pattern == null) return Material.COAST_ARMOR_TRIM_SMITHING_TEMPLATE;
        String matName = pattern.toUpperCase(java.util.Locale.ROOT) + "_ARMOR_TRIM_SMITHING_TEMPLATE";
        try {
            Material mat = Material.matchMaterial(matName);
            if (mat != null) return mat;
        } catch (Exception ignored) {}
        return Material.COAST_ARMOR_TRIM_SMITHING_TEMPLATE;
    }
}

