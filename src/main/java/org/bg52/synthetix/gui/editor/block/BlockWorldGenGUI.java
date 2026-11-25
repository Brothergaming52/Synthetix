package org.bg52.synthetix.gui.editor.block;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.block.BlockWorldGenSpec;
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
 * Sub-GUI for natural world generation, ore distribution, vein sizes,
 * height limits, replacement targets, and biome filtering.
 */
public class BlockWorldGenGUI {

    private final SynthetixPlugin plugin;
    private final CustomBlockDefinition blockDef;
    private final Runnable onBack;

    private static final Material[] TARGET_PRESETS = {
            Material.STONE,
            Material.DEEPSLATE,
            Material.NETHERRACK,
            Material.END_STONE,
            Material.DIRT,
            Material.GRAVEL,
            Material.SAND,
            Material.BLACKSTONE
    };

    public BlockWorldGenGUI(SynthetixPlugin plugin, CustomBlockDefinition blockDef, Runnable onBack) {
        this.plugin = plugin;
        this.blockDef = blockDef;
        this.onBack = onBack;
    }

    public static void open(Player player, SynthetixPlugin plugin, CustomBlockDefinition blockDef, Runnable onBack) {
        new BlockWorldGenGUI(plugin, blockDef, onBack).open(player);
    }

    public void open(Player player) {
        Component title = TextUtil.parse("<gold>Natural World Generation (Ores)</gold>");
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

        BlockWorldGenSpec gen = blockDef.getWorldGen();

        // 1. Generation Active Toggle (Slot 10)
        ItemStack toggleBtn = createBtn(gen.isEnabled() ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK,
                "<gold>Ore Generation Status</gold>",
                "<gray>Natural Generation:</gray> " + (gen.isEnabled() ? "<green>ACTIVE (Spawns in world)</green>" : "<red>DISABLED</red>"),
                "",
                "<yellow>Click to toggle natural ore generation</yellow>");
        canvas.setButton(10, CanvasButton.of(toggleBtn, e -> {
            gen.setEnabled(!gen.isEnabled());
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            open(player);
        }));

        // 2. Vein Size (Slot 12)
        ItemStack veinBtn = createBtn(Material.RAW_IRON,
                "<gold>Vein Size (Ore Count)</gold>",
                "<gray>Current Size:</gray> <white>" + gen.getVeinSize() + " blocks / vein</white>",
                "<dark_gray>(Coal: ~17, Iron: ~9, Diamond: ~4-8)</dark_gray>",
                "",
                "<yellow>Left-Click: +1 | Right-Click: -1</yellow>",
                "<yellow>Shift-Left: +5 | Shift-Right: Type custom</yellow>");
        canvas.setButton(12, CanvasButton.of(veinBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (e.isShiftClick() && e.isRightClick()) {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter vein size (1 - 64):</gray>"),
                        input -> {
                            try {
                                gen.setVeinSize(Integer.parseInt(input.trim()));
                            } catch (Exception ignored) {}
                            open(player);
                        });
            } else if (e.isShiftClick()) {
                gen.setVeinSize(gen.getVeinSize() + 5);
                open(player);
            } else if (e.isRightClick()) {
                gen.setVeinSize(Math.max(1, gen.getVeinSize() - 1));
                open(player);
            } else {
                gen.setVeinSize(gen.getVeinSize() + 1);
                open(player);
            }
        }));

        // 3. Veins Per Chunk (Slot 13)
        ItemStack chunkBtn = createBtn(Material.RAW_GOLD,
                "<gold>Veins Per Chunk (Frequency)</gold>",
                "<gray>Current Frequency:</gray> <white>" + gen.getVeinsPerChunk() + " veins / chunk</white>",
                "",
                "<yellow>Left-Click: +1 | Right-Click: -1</yellow>",
                "<yellow>Shift-Left: +5 | Shift-Right: Type custom</yellow>");
        canvas.setButton(13, CanvasButton.of(chunkBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (e.isShiftClick() && e.isRightClick()) {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter veins per chunk (0 - 32):</gray>"),
                        input -> {
                            try {
                                gen.setVeinsPerChunk(Integer.parseInt(input.trim()));
                            } catch (Exception ignored) {}
                            open(player);
                        });
            } else if (e.isShiftClick()) {
                gen.setVeinsPerChunk(gen.getVeinsPerChunk() + 5);
                open(player);
            } else if (e.isRightClick()) {
                gen.setVeinsPerChunk(Math.max(0, gen.getVeinsPerChunk() - 1));
                open(player);
            } else {
                gen.setVeinsPerChunk(gen.getVeinsPerChunk() + 1);
                open(player);
            }
        }));

        // 4. Height Range Y (Slot 14)
        ItemStack heightBtn = createBtn(Material.LADDER,
                "<gold>Y Height Range</gold>",
                "<gray>Min Height (Bottom):</gray> <white>Y = " + gen.getMinHeight() + "</white>",
                "<gray>Max Height (Top):</gray> <white>Y = " + gen.getMaxHeight() + "</white>",
                "",
                "<yellow>Left-Click: Enter Min Y (e.g. -64)</yellow>",
                "<yellow>Right-Click: Enter Max Y (e.g. 64)</yellow>");
        canvas.setButton(14, CanvasButton.of(heightBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (e.isRightClick()) {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter Max Y Height (-64 to 320):</gray>"),
                        input -> {
                            try {
                                gen.setMaxHeight(Integer.parseInt(input.trim()));
                            } catch (Exception ignored) {}
                            open(player);
                        });
            } else {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter Min Y Height (-64 to 320):</gray>"),
                        input -> {
                            try {
                                gen.setMinHeight(Integer.parseInt(input.trim()));
                            } catch (Exception ignored) {}
                            open(player);
                        });
            }
        }));

        // 5. Replace Target Material (Slot 15)
        ItemStack targetBtn = createBtn(gen.getReplaceTarget(),
                "<gold>Replace Target Material</gold>",
                "<gray>Current Target:</gray> <aqua>" + gen.getReplaceTarget().name() + "</aqua>",
                "",
                "<yellow>Left-Click: Cycle common preset targets</yellow>",
                "<yellow>Right-Click: Type custom material name</yellow>");
        canvas.setButton(15, CanvasButton.of(targetBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (e.isRightClick()) {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter vanilla Material to replace (e.g. <yellow>DEEPSLATE</yellow>):</gray>"),
                        input -> {
                            Material m = Material.matchMaterial(input.trim().toUpperCase());
                            if (m != null && m.isBlock()) {
                                gen.setReplaceTarget(m);
                            }
                            open(player);
                        });
            } else {
                int curIdx = 0;
                for (int i = 0; i < TARGET_PRESETS.length; i++) {
                    if (TARGET_PRESETS[i] == gen.getReplaceTarget()) {
                        curIdx = i;
                        break;
                    }
                }
                gen.setReplaceTarget(TARGET_PRESETS[(curIdx + 1) % TARGET_PRESETS.length]);
                open(player);
            }
        }));

        // 6. Biome Filter (Slot 16)
        List<String> biomes = gen.getBiomes();
        ItemStack biomeBtn = createBtn(Material.OAK_SAPLING,
                "<gold>Biome Restrictions</gold>",
                "<gray>Filter Mode:</gray> " + (biomes.isEmpty() ? "<green>All Biomes (Global)</green>" : "<aqua>" + biomes.size() + " Biomes</aqua>"),
                biomes.isEmpty() ? "<dark_gray>Spawns in every biome</dark_gray>" : "<dark_gray>" + String.join(", ", biomes) + "</dark_gray>",
                "",
                "<yellow>Left-Click: Add biome key</yellow>",
                "<yellow>Right-Click: Clear biome filter (allow all)</yellow>");
        canvas.setButton(16, CanvasButton.of(biomeBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (e.isRightClick()) {
                biomes.clear();
                player.sendMessage(TextUtil.parse("<yellow>✓ Cleared biome filter: ores spawn in all biomes.</yellow>"));
                open(player);
            } else {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter biome name (e.g. <yellow>PLAINS</yellow>, <yellow>DESERT</yellow>, <yellow>JAGGED_PEAKS</yellow>):</gray>"),
                        input -> {
                            String b = input.trim().toUpperCase();
                            if (!b.isEmpty() && !biomes.contains(b)) {
                                biomes.add(b);
                                player.sendMessage(TextUtil.parse("<green>✓ Added biome filter: <yellow>" + b + "</yellow></green>"));
                            }
                            open(player);
                        });
            }
        }));

        // Slot 36: Back Button
        ItemStack backBtn = createBtn(Material.ARROW,
                "<yellow><bold>« Back</bold></yellow>",
                "<gray>Return to Block Editor Menu.</gray>");
        canvas.setButton(36, CanvasButton.of(backBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            if (onBack != null) onBack.run();
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

