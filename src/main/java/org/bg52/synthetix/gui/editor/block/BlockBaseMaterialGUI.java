package org.bg52.synthetix.gui.editor.block;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.block.CustomBlockDefinition;
import org.bg52.synthetix.gui.Canvas;
import org.bg52.synthetix.gui.CanvasButton;
import org.bg52.synthetix.util.MaterialUtil;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sub-GUI for selecting the physical proxy block and inventory representation material.
 */
public class BlockBaseMaterialGUI {

    private final SynthetixPlugin plugin;
    private final CustomBlockDefinition blockDef;
    private final Runnable onBack;

    private static final Material[] PRESET_PROXIES = new Material[]{
            Material.BARRIER, Material.STRUCTURE_VOID, Material.NOTE_BLOCK, Material.TRIPWIRE,
            Material.STONE, Material.SMOOTH_STONE_SLAB, Material.IRON_BLOCK, Material.GLASS,
            Material.OAK_PLANKS, Material.OAK_SLAB, Material.OAK_FENCE, Material.OAK_TRAPDOOR
    };

    public BlockBaseMaterialGUI(SynthetixPlugin plugin, CustomBlockDefinition blockDef, Runnable onBack) {
        this.plugin = plugin;
        this.blockDef = blockDef;
        this.onBack = onBack;
    }

    public static void open(Player player, SynthetixPlugin plugin, CustomBlockDefinition blockDef, Runnable onBack) {
        new BlockBaseMaterialGUI(plugin, blockDef, onBack).open(player);
    }

    public void open(Player player) {
        Component title = TextUtil.parse("<gold>Base Material & Proxy Selection</gold>");
        Canvas canvas = new Canvas(title, 5);

        ItemStack darkGlass = Canvas.createBorderItem(Material.BLACK_STAINED_GLASS_PANE, Component.empty());
        for (int c = 0; c < 9; c++) {
            canvas.setButton(0, c, CanvasButton.of(darkGlass));
            canvas.setButton(4, c, CanvasButton.of(darkGlass));
        }
        for (int r = 1; r < 4; r++) {
            canvas.setButton(r, 0, CanvasButton.of(darkGlass));
            canvas.setButton(r, 8, CanvasButton.of(darkGlass));
        }

        // Current Proxy Summary (Slot 4)
        ItemStack curIcon = new ItemStack(MaterialUtil.toItemMaterial(blockDef.getBaseMaterial()));
        ItemMeta cMeta = curIcon.getItemMeta();
        if (cMeta != null) {
            cMeta.displayName(TextUtil.parse("<gold><bold>Current Proxy: " + blockDef.getBaseMaterial().name() + "</bold></gold>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Physical block placed in the Minecraft world.</gray>"));
            lore.add(TextUtil.parse("<gray>Held Item Representation:</gray> <white>" + blockDef.getItemMaterial().name() + "</white>"));
            cMeta.lore(lore);
            curIcon.setItemMeta(cMeta);
        }
        canvas.setButton(4, CanvasButton.of(curIcon));

        // Preset proxy buttons in slots 10..16, 19..23
        int[] slots = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23};
        for (int i = 0; i < PRESET_PROXIES.length && i < slots.length; i++) {
            Material mat = PRESET_PROXIES[i];
            ItemStack icon = new ItemStack(MaterialUtil.toItemMaterial(mat));
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                boolean active = mat == blockDef.getBaseMaterial();
                meta.displayName(TextUtil.parse((active ? "<green>" : "<yellow>") + mat.name() + "</yellow>"));
                List<Component> lore = new ArrayList<>();
                lore.add(TextUtil.parse(active ? "<green>✓ Currently Selected</green>" : "<gray>Click to select as physical proxy</gray>"));
                meta.lore(lore);
                icon.setItemMeta(meta);
            }
            canvas.setButton(slots[i], CanvasButton.of(icon, e -> {
                blockDef.setBaseMaterial(mat);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                open(player);
            }));
        }

        // Custom Material Prompt (Slot 24)
        ItemStack promptBtn = new ItemStack(Material.ANVIL);
        ItemMeta pMeta = promptBtn.getItemMeta();
        if (pMeta != null) {
            pMeta.displayName(TextUtil.parse("<aqua><bold>Type Custom Material</bold></aqua>"));
            pMeta.lore(List.of(TextUtil.parse("<gray>Type any vanilla Material enum name in chat.</gray>")));
            promptBtn.setItemMeta(pMeta);
        }
        canvas.setButton(24, CanvasButton.of(promptBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            plugin.getChatPromptManager().prompt(player,
                    TextUtil.parse("<gray>Enter vanilla Material (e.g. <yellow>BARRIER</yellow>, <yellow>COPPER_BLOCK</yellow>, <yellow>MUSHROOM_STEM</yellow>):</gray>"),
                    input -> {
                        Material m = Material.matchMaterial(input.trim());
                        if (m != null && m.isBlock()) {
                            blockDef.setBaseMaterial(m);
                            player.sendMessage(TextUtil.parse("<green>✓ Base material set to " + m.name() + "!</green>"));
                        }
                        open(player);
                    });
        }));

        // Slot 25: Item Representation Material
        ItemStack itemMatBtn = new ItemStack(MaterialUtil.toItemMaterial(blockDef.getItemMaterial()));
        ItemMeta iMeta = itemMatBtn.getItemMeta();
        if (iMeta != null) {
            iMeta.displayName(TextUtil.parse("<light_purple><bold>Held Item Material</bold></light_purple>"));
            iMeta.lore(List.of(
                    TextUtil.parse("<gray>Current:</gray> <white>" + blockDef.getItemMaterial().name() + "</white>"),
                    Component.empty(),
                    TextUtil.parse("<yellow>Click to change inventory item material »</yellow>")
            ));
            itemMatBtn.setItemMeta(iMeta);
        }
        canvas.setButton(25, CanvasButton.of(itemMatBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            plugin.getChatPromptManager().prompt(player,
                    TextUtil.parse("<gray>Enter inventory Item Material (e.g. <yellow>PAPER</yellow>, <yellow>IRON_INGOT</yellow>, <yellow>BRICK</yellow>):</gray>"),
                    input -> {
                        Material m = Material.matchMaterial(input.trim());
                        if (m != null && !m.isAir()) {
                            blockDef.setItemMaterial(m);
                            player.sendMessage(TextUtil.parse("<green>✓ Inventory item material set to " + m.name() + "!</green>"));
                        }
                        open(player);
                    });
        }));

        // Back Button (Slot 36)
        ItemStack back = Canvas.createBorderItem(Material.ARROW, TextUtil.parse("<yellow>« Back</yellow>"));
        canvas.setButton(36, CanvasButton.of(back, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            onBack.run();
        }));

        canvas.open(player);
    }
}

