package org.bg52.synthetix.gui.editor.block;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.block.BlockPhysicsSpec;
import org.bg52.synthetix.block.CollisionShape;
import org.bg52.synthetix.block.CustomBlockDefinition;
import org.bg52.synthetix.gui.Canvas;
import org.bg52.synthetix.gui.CanvasButton;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Sub-GUI for collision box geometry, friction, light emission, and waterlogging.
 */
public class BlockPhysicsGUI {

    private final SynthetixPlugin plugin;
    private final CustomBlockDefinition blockDef;
    private final Runnable onBack;

    public BlockPhysicsGUI(SynthetixPlugin plugin, CustomBlockDefinition blockDef, Runnable onBack) {
        this.plugin = plugin;
        this.blockDef = blockDef;
        this.onBack = onBack;
    }

    public static void open(Player player, SynthetixPlugin plugin, CustomBlockDefinition blockDef, Runnable onBack) {
        new BlockPhysicsGUI(plugin, blockDef, onBack).open(player);
    }

    public void open(Player player) {
        Component title = TextUtil.parse("<gold>Physics & Collision Settings</gold>");
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

        BlockPhysicsSpec phys = blockDef.getPhysics();

        // 1. Collision Shape Cycle (Slot 10)
        CollisionShape[] shapes = CollisionShape.values();
        ItemStack shapeBtn = createBtn(Material.SHIELD,
                "<gold>Collision Box Geometry</gold>",
                "<gray>Current Shape:</gray> <aqua>" + phys.getCollisionShape().name() + "</aqua>",
                "",
                "<yellow>Click to cycle collision shape:</yellow>",
                "<dark_gray>•</dark_gray> SOLID_CUBE (full 1x1x1 solid)",
                "<dark_gray>•</dark_gray> PASS_THROUGH (intangible void)",
                "<dark_gray>•</dark_gray> SLAB_BOTTOM / SLAB_TOP (half slab)",
                "<dark_gray>•</dark_gray> STAIRS / FENCE / CARPET / SHULKER_SCALED");
        canvas.setButton(10, CanvasButton.of(shapeBtn, e -> {
            int nextIdx = (phys.getCollisionShape().ordinal() + 1) % shapes.length;
            phys.setCollisionShape(shapes[nextIdx]);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            open(player);
        }));

        // 2. Friction / Slipperiness (Slot 12)
        ItemStack fricBtn = createBtn(Material.BLUE_ICE,
                "<gold>Block Friction (Slipperiness)</gold>",
                "<gray>Current:</gray> <white>" + phys.getFriction() + "</white>",
                "",
                "<yellow>Left-Click: Standard Block (0.6f)</yellow>",
                "<yellow>Right-Click: Slippery Ice (0.989f)</yellow>",
                "<yellow>Shift-Right: Type custom friction in chat</yellow>");
        canvas.setButton(12, CanvasButton.of(fricBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (e.isShiftClick() && e.isRightClick()) {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter friction float (0.1 to 1.0, e.g. <yellow>0.989</yellow>):</gray>"),
                        input -> {
                            try {
                                phys.setFriction(Float.parseFloat(input.trim()));
                            } catch (Exception ignored) {}
                            open(player);
                        });
            } else if (e.isRightClick()) {
                phys.setFriction(0.989f);
                open(player);
            } else {
                phys.setFriction(0.6f);
                open(player);
            }
        }));

        // 3. Light Emission (Slot 14)
        ItemStack lightBtn = createBtn(Material.GLOWSTONE,
                "<gold>Block Light Level</gold>",
                "<gray>Emitted Light:</gray> <white>" + phys.getLightEmission() + " / 15</white>",
                "",
                "<yellow>Left-Click: +1 | Right-Click: -1</yellow>",
                "<yellow>Shift-Left: Max 15 | Shift-Right: 0 (Off)</yellow>");
        canvas.setButton(14, CanvasButton.of(lightBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (e.isShiftClick() && e.isLeftClick()) {
                phys.setLightEmission(15);
            } else if (e.isShiftClick() && e.isRightClick()) {
                phys.setLightEmission(0);
            } else if (e.isRightClick()) {
                phys.setLightEmission(Math.max(0, phys.getLightEmission() - 1));
            } else {
                phys.setLightEmission(Math.min(15, phys.getLightEmission() + 1));
            }
            open(player);
        }));

        // 4. Waterloggable Toggle (Slot 16)
        boolean water = phys.isWaterloggable();
        ItemStack waterBtn = createBtn(water ? Material.WATER_BUCKET : Material.BUCKET,
                "<gold>Waterloggable Property</gold>",
                "<gray>Status:</gray> " + (water ? "<green>Enabled</green>" : "<red>Disabled</red>"),
                "",
                "<yellow>Click to toggle waterlogging support »</yellow>");
        canvas.setButton(16, CanvasButton.of(waterBtn, e -> {
            phys.setWaterloggable(!phys.isWaterloggable());
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            open(player);
        }));

        // 5. Solid / Pass-Through Toggle (Slot 22)
        boolean solid = phys.isSolid();
        ItemStack solidBtn = createBtn(solid ? Material.IRON_BLOCK : Material.COBWEB,
                "<gold>Solid Collision Toggle</gold>",
                "<gray>State:</gray> " + (solid ? "<green>Solid Collider</green>" : "<yellow>Intangible (Pass-through)</yellow>"),
                "",
                "<yellow>Click to toggle solid vs pass-through »</yellow>");
        canvas.setButton(22, CanvasButton.of(solidBtn, e -> {
            phys.setSolid(!phys.isSolid());
            phys.setPassThrough(!phys.isSolid());
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            open(player);
        }));

        // Back Button (Slot 36)
        ItemStack back = Canvas.createBorderItem(Material.ARROW, TextUtil.parse("<yellow>« Back</yellow>"));
        canvas.setButton(36, CanvasButton.of(back, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            onBack.run();
        }));

        canvas.open(player);
    }

    private ItemStack createBtn(Material mat, String name, String... loreLines) {
        ItemStack item = new ItemStack(org.bg52.synthetix.util.MaterialUtil.toItemMaterial(mat));
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

