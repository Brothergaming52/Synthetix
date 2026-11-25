package org.bg52.synthetix.gui.editor.block;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.block.BlockMiningSpec;
import org.bg52.synthetix.block.CustomBlockDefinition;
import org.bg52.synthetix.block.ToolTier;
import org.bg52.synthetix.block.ToolType;
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
 * Sub-GUI for hardness, tool requirements, harvest tiers, and experience drops.
 */
public class BlockMiningGUI {

    private final SynthetixPlugin plugin;
    private final CustomBlockDefinition blockDef;
    private final Runnable onBack;

    public BlockMiningGUI(SynthetixPlugin plugin, CustomBlockDefinition blockDef, Runnable onBack) {
        this.plugin = plugin;
        this.blockDef = blockDef;
        this.onBack = onBack;
    }

    public static void open(Player player, SynthetixPlugin plugin, CustomBlockDefinition blockDef, Runnable onBack) {
        new BlockMiningGUI(plugin, blockDef, onBack).open(player);
    }

    public void open(Player player) {
        Component title = TextUtil.parse("<gold>Mining & Hardness Configuration</gold>");
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

        BlockMiningSpec mining = blockDef.getMining();

        // 1. Block Hardness (Slot 10) - Quick Clicks & Custom Prompt
        ItemStack hardBtn = createBtn(Material.DIAMOND_PICKAXE,
                "<gold>Block Hardness</gold>",
                "<gray>Current Hardness:</gray> <white>" + mining.getHardness() + "</white>",
                "<dark_gray>(Stone: 1.5, Iron Block: 5.0, Obsidian: 50.0)</dark_gray>",
                "",
                "<yellow>Left-Click: +0.5 | Right-Click: -0.5</yellow>",
                "<yellow>Shift-Left: +5.0 | Shift-Right: Type custom</yellow>");
        canvas.setButton(10, CanvasButton.of(hardBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (e.isShiftClick() && e.isRightClick()) {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter hardness float (e.g. <yellow>3.0</yellow>):</gray>"),
                        input -> {
                            try {
                                mining.setHardness(Float.parseFloat(input.trim()));
                            } catch (Exception ignored) {}
                            open(player);
                        });
            } else if (e.isShiftClick()) {
                mining.setHardness(mining.getHardness() + 5.0f);
                open(player);
            } else if (e.isRightClick()) {
                mining.setHardness(Math.max(0.0f, mining.getHardness() - 0.5f));
                open(player);
            } else {
                mining.setHardness(mining.getHardness() + 0.5f);
                open(player);
            }
        }));

        // 2. Required Tool Type (Slot 12)
        ToolType[] tools = ToolType.values();
        ItemStack toolBtn = createBtn(Material.IRON_AXE,
                "<gold>Required Tool Type</gold>",
                "<gray>Current Tool:</gray> <aqua>" + mining.getRequiredTool().name() + "</aqua>",
                "",
                "<yellow>Click to cycle tool:</yellow>",
                "<gray>PICKAXE -> AXE -> SHOVEL -> HOE -> SWORD -> NONE -> ANY</gray>");
        canvas.setButton(12, CanvasButton.of(toolBtn, e -> {
            int nextIdx = (mining.getRequiredTool().ordinal() + 1) % tools.length;
            mining.setRequiredTool(tools[nextIdx]);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            open(player);
        }));

        // 3. Minimum Tool Tier (Slot 14)
        ToolTier[] tiers = ToolTier.values();
        ItemStack tierBtn = createBtn(Material.DIAMOND,
                "<gold>Minimum Tool Tier</gold>",
                "<gray>Current Tier:</gray> <light_purple>" + mining.getMinimumTier().name() + "</light_purple>",
                "",
                "<yellow>Click to cycle tier:</yellow>",
                "<gray>WOOD -> STONE -> IRON -> DIAMOND -> NETHERITE</gray>");
        canvas.setButton(14, CanvasButton.of(tierBtn, e -> {
            int nextIdx = (mining.getMinimumTier().ordinal() + 1) % tiers.length;
            mining.setMinimumTier(tiers[nextIdx]);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            open(player);
        }));

        // 4. Requires Correct Tool for Drops (Slot 16)
        boolean req = mining.isRequiresCorrectToolForDrops();
        ItemStack reqBtn = createBtn(req ? Material.NAME_TAG : Material.BARRIER,
                "<gold>Enforce Correct Tool for Drops</gold>",
                "<gray>Requirement:</gray> " + (req ? "<green>Enforced (Drops only with tool)</green>" : "<yellow>Lenient (Drops with anything)</yellow>"),
                "",
                "<yellow>Click to toggle requirement »</yellow>");
        canvas.setButton(16, CanvasButton.of(reqBtn, e -> {
            mining.setRequiresCorrectToolForDrops(!mining.isRequiresCorrectToolForDrops());
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            open(player);
        }));

        // 5. Experience Drop (Slot 22)
        ItemStack expBtn = createBtn(Material.EXPERIENCE_BOTTLE,
                "<gold>Mining Experience Drop</gold>",
                "<gray>Dropped XP:</gray> <green>" + mining.getExpDrop() + " XP</green>",
                "",
                "<yellow>Left-Click: +1 XP | Right-Click: -1 XP</yellow>",
                "<yellow>Shift-Left: +5 XP | Shift-Right: Type in chat</yellow>");
        canvas.setButton(22, CanvasButton.of(expBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (e.isShiftClick() && e.isRightClick()) {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter XP amount integer (e.g. <yellow>5</yellow>):</gray>"),
                        input -> {
                            try {
                                mining.setExpDrop(Math.max(0, Integer.parseInt(input.trim())));
                            } catch (Exception ignored) {}
                            open(player);
                        });
            } else if (e.isShiftClick()) {
                mining.setExpDrop(mining.getExpDrop() + 5);
                open(player);
            } else if (e.isRightClick()) {
                mining.setExpDrop(Math.max(0, mining.getExpDrop() - 1));
                open(player);
            } else {
                mining.setExpDrop(mining.getExpDrop() + 1);
                open(player);
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

