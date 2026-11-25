package org.bg52.synthetix.gui.editor.block;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.block.CustomBlockDefinition;
import org.bg52.synthetix.block.RenderingMethod;
import org.bg52.synthetix.gui.Canvas;
import org.bg52.synthetix.gui.CanvasButton;
import org.bg52.synthetix.util.MaterialUtil;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Sub-GUI for 3D visual rendering, CustomModelData, scale, translation, rotation, and brightness.
 */
public class BlockVisualsGUI {

    private final SynthetixPlugin plugin;
    private final CustomBlockDefinition blockDef;
    private final Runnable onBack;

    public BlockVisualsGUI(SynthetixPlugin plugin, CustomBlockDefinition blockDef, Runnable onBack) {
        this.plugin = plugin;
        this.blockDef = blockDef;
        this.onBack = onBack;
    }

    public static void open(Player player, SynthetixPlugin plugin, CustomBlockDefinition blockDef, Runnable onBack) {
        new BlockVisualsGUI(plugin, blockDef, onBack).open(player);
    }

    public void open(Player player) {
        Component title = TextUtil.parse("<gold>Model & Visual Display Settings</gold>");
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

        // 1. CustomModelData (Slot 10) - Quick Clicks & Custom Input
        ItemStack cmdBtn = createBtn(Material.PAINTING,
                "<gold>CustomModelData (CMD)</gold>",
                "<gray>Current:</gray> <white>" + (blockDef.getCustomModelData() > 0 ? blockDef.getCustomModelData() : "None") + "</white>",
                "",
                "<yellow>Left-Click: +1 | Right-Click: -1</yellow>",
                "<yellow>Shift-Left: +100</yellow>",
                "<yellow>Shift-Right: Type exact CMD in chat</yellow>");
        canvas.setButton(10, CanvasButton.of(cmdBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (e.isShiftClick() && e.isRightClick()) {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter CustomModelData integer (e.g. <yellow>10001</yellow>):</gray>"),
                        input -> {
                            try {
                                blockDef.setCustomModelData(Math.max(0, Integer.parseInt(input.trim())));
                            } catch (Exception ignored) {}
                            open(player);
                        });
            } else if (e.isShiftClick()) {
                blockDef.setCustomModelData(blockDef.getCustomModelData() + 100);
                open(player);
            } else if (e.isRightClick()) {
                blockDef.setCustomModelData(Math.max(0, blockDef.getCustomModelData() - 1));
                open(player);
            } else {
                blockDef.setCustomModelData(blockDef.getCustomModelData() + 1);
                open(player);
            }
        }));

        // 1b. Modern item_model Component (Slot 11)
        String curItemModel = blockDef.getItemModel();
        ItemStack modelBtn = createBtn(Material.NAME_TAG,
                "<gold>Item Model Component (<aqua>item_model</aqua>)</gold>",
                "<gray>NamespacedKey:</gray> " + (curItemModel != null ? "<aqua>" + curItemModel + "</aqua>" : "<gray>None (Using CMD)</gray>"),
                "",
                "<yellow>Left-Click: Enter model key in chat (e.g. <white>synthetix:gearbox</white>)</yellow>",
                "<red>Right-Click: Clear item_model</red>");
        canvas.setButton(11, CanvasButton.of(modelBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (e.isRightClick()) {
                blockDef.setItemModel(null);
                plugin.getLanguageManager().send(player, "block-editor.cleared-model");
                open(player);
            } else {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter modern item_model NamespacedKey (e.g. <yellow>synthetix:gearbox</yellow>):</gray>"),
                        input -> {
                            String trimmed = input.trim();
                            blockDef.setItemModel(trimmed.isEmpty() ? null : trimmed);
                            plugin.getLanguageManager().send(player, "block-editor.set-model", "%model%", String.valueOf(blockDef.getItemModel()));
                            open(player);
                        });
            }
        }));

        // 2. Rendering Method Cycle (Slot 12) - Streamlined for in-game editor
        RenderingMethod[] methods = new RenderingMethod[]{RenderingMethod.ITEM_DISPLAY, RenderingMethod.BLOCK_DISPLAY};
        ItemStack methodBtn = createBtn(Material.ARMOR_STAND,
                "<gold>Rendering Method</gold>",
                "<gray>Current:</gray> <aqua>" + blockDef.getRenderingMethod().name() + "</aqua>",
                "",
                "<yellow>Click to cycle rendering method:</yellow>",
                "<dark_gray>•</dark_gray> ITEM_DISPLAY (modern 3D matrix transform)",
                "<dark_gray>•</dark_gray> BLOCK_DISPLAY (block state rendering)");
        canvas.setButton(12, CanvasButton.of(methodBtn, e -> {
            int currentIdx = 0;
            for (int i = 0; i < methods.length; i++) {
                if (methods[i] == blockDef.getRenderingMethod()) {
                    currentIdx = i;
                    break;
                }
            }
            int nextIdx = (currentIdx + 1) % methods.length;
            blockDef.setRenderingMethod(methods[nextIdx]);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            open(player);
        }));

        // 3. Display Scale (Slot 14) - Quick Adjust & Prompt
        Vector3f sc = blockDef.getDisplayScale();
        ItemStack scaleBtn = createBtn(Material.SLIME_BALL,
                "<gold>Display Scale [X, Y, Z]</gold>",
                "<gray>Current Scale:</gray> <white>[" + sc.x + ", " + sc.y + ", " + sc.z + "]</white>",
                "",
                "<yellow>Left-Click: +0.1 uniform scale</yellow>",
                "<yellow>Right-Click: -0.1 uniform scale</yellow>",
                "<yellow>Shift-Right: Type exact [X Y Z] in chat</yellow>");
        canvas.setButton(14, CanvasButton.of(scaleBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (e.isShiftClick() && e.isRightClick()) {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter scale <yellow>&lt;X&gt; &lt;Y&gt; &lt;Z&gt;</yellow> (e.g. <yellow>1.0 1.0 1.0</yellow>):</gray>"),
                        input -> {
                            String[] p = input.trim().split("\\s+");
                            if (p.length >= 3) {
                                try {
                                    blockDef.setDisplayScale(new Vector3f(Float.parseFloat(p[0]), Float.parseFloat(p[1]), Float.parseFloat(p[2])));
                                } catch (Exception ignored) {}
                            }
                            open(player);
                        });
            } else if (e.isRightClick()) {
                blockDef.setDisplayScale(new Vector3f(Math.max(0.1f, sc.x - 0.1f), Math.max(0.1f, sc.y - 0.1f), Math.max(0.1f, sc.z - 0.1f)));
                open(player);
            } else {
                blockDef.setDisplayScale(new Vector3f(sc.x + 0.1f, sc.y + 0.1f, sc.z + 0.1f));
                open(player);
            }
        }));

        // 4. Display Translation (Slot 16)
        Vector3f tr = blockDef.getDisplayTranslation();
        ItemStack transBtn = createBtn(Material.PISTON,
                "<gold>Display Translation [X, Y, Z]</gold>",
                "<gray>Current Offset:</gray> <white>[" + tr.x + ", " + tr.y + ", " + tr.z + "]</white>",
                "",
                "<yellow>Left-Click: Reset to [0, 0, 0]</yellow>",
                "<yellow>Right-Click: Type custom offset in chat</yellow>");
        canvas.setButton(16, CanvasButton.of(transBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            if (e.isRightClick()) {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter translation <yellow>&lt;X&gt; &lt;Y&gt; &lt;Z&gt;</yellow> (e.g. <yellow>0.0 0.5 0.0</yellow>):</gray>"),
                        input -> {
                            String[] p = input.trim().split("\\s+");
                            if (p.length >= 3) {
                                try {
                                    blockDef.setDisplayTranslation(new Vector3f(Float.parseFloat(p[0]), Float.parseFloat(p[1]), Float.parseFloat(p[2])));
                                } catch (Exception ignored) {}
                            }
                            open(player);
                        });
            } else {
                blockDef.setDisplayTranslation(new Vector3f(0.0f, 0.0f, 0.0f));
                open(player);
            }
        }));

        // 5. Brightness Override (Slot 21)
        Integer curBri = blockDef.getBrightness();
        ItemStack briBtn = createBtn(Material.GLOWSTONE,
                "<gold>Block Brightness Override</gold>",
                "<gray>Current:</gray> <white>" + (curBri != null ? curBri + " / 15" : "Ambient World Light") + "</white>",
                "",
                "<yellow>Left-Click: +1 | Right-Click: -1</yellow>",
                "<red>Shift-Right: Reset to Ambient</red>");
        canvas.setButton(21, CanvasButton.of(briBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (e.isShiftClick()) {
                blockDef.setBrightness(null);
            } else if (e.isRightClick()) {
                int b = curBri != null ? Math.max(0, curBri - 1) : 15;
                blockDef.setBrightness(b);
            } else {
                int b = curBri != null ? Math.min(15, curBri + 1) : 0;
                blockDef.setBrightness(b);
            }
            open(player);
        }));

        // 5b. Lock Placement Facing (Slot 22)
        ItemStack lockFacingBtn = createBtn(blockDef.isLockRotation() ? Material.COMPASS : Material.RECOVERY_COMPASS,
                "<gold>Placement Rotation Lock: " + (blockDef.isLockRotation() ? "<green><bold>LOCKED</bold></green>" : "<red><bold>UNLOCKED</bold></red>") + "</gold>",
                "<gray>Current:</gray> " + (blockDef.isLockRotation() ? "<green>Fixed to NORTH</green>" : "<gray>Adapts to player facing</gray>"),
                "",
                "<gray>When locked, this block always places facing NORTH,</gray>",
                "<gray>preventing misalignment when placing from above or sides.</gray>",
                "",
                "<yellow>Click to toggle Lock »</yellow>");
        canvas.setButton(22, CanvasButton.of(lockFacingBtn, e -> {
            blockDef.setLockRotation(!blockDef.isLockRotation());
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, blockDef.isLockRotation() ? 1.4f : 0.9f);
            plugin.getCustomBlockManager().respawnDisplayEntities(blockDef);
            open(player);
        }));

        // 6. Glow Color (Slot 23)
        String curGlow = blockDef.getGlowColorHex();
        ItemStack glowBtn = createBtn(Material.SPECTRAL_ARROW,
                "<gold>Outline Glow Effect</gold>",
                "<gray>Glow Color:</gray> <white>" + (curGlow != null ? curGlow : "None (Disabled)") + "</white>",
                "",
                "<yellow>Left-Click: Type hex color (#FF5500)</yellow>",
                "<red>Right-Click: Disable glow</red>");
        canvas.setButton(23, CanvasButton.of(glowBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            if (e.isRightClick()) {
                blockDef.setGlowColorHex(null);
                open(player);
            } else {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter hex color (e.g. <yellow>#00FFAA</yellow> or <yellow>#FF4400</yellow>):</gray>"),
                        input -> {
                            if (!input.trim().isEmpty()) {
                                blockDef.setGlowColorHex(input.trim());
                            }
                            open(player);
                        });
            }
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
        ItemStack item = new ItemStack(MaterialUtil.toItemMaterial(mat));
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

