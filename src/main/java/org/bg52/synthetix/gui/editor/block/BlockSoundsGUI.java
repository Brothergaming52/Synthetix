package org.bg52.synthetix.gui.editor.block;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.block.BlockMiningSpec;
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
 * Sub-GUI for custom block audio (break, place, step, hit sounds)
 * and authentic shatter particles.
 */
public class BlockSoundsGUI {

    private final SynthetixPlugin plugin;
    private final CustomBlockDefinition blockDef;
    private final Runnable onBack;

    public BlockSoundsGUI(SynthetixPlugin plugin, CustomBlockDefinition blockDef, Runnable onBack) {
        this.plugin = plugin;
        this.blockDef = blockDef;
        this.onBack = onBack;
    }

    public static void open(Player player, SynthetixPlugin plugin, CustomBlockDefinition blockDef, Runnable onBack) {
        new BlockSoundsGUI(plugin, blockDef, onBack).open(player);
    }

    public void open(Player player) {
        Component title = TextUtil.parse("<gold>Block Audio & Shatter Particles</gold>");
        Canvas canvas = new Canvas(title, 6);

        ItemStack darkGlass = Canvas.createBorderItem(Material.BLACK_STAINED_GLASS_PANE, Component.empty());
        for (int c = 0; c < 9; c++) {
            canvas.setButton(0, c, CanvasButton.of(darkGlass));
            canvas.setButton(5, c, CanvasButton.of(darkGlass));
        }
        for (int r = 1; r < 5; r++) {
            canvas.setButton(r, 0, CanvasButton.of(darkGlass));
            canvas.setButton(r, 8, CanvasButton.of(darkGlass));
        }

        BlockMiningSpec mining = blockDef.getMining();

        // ---------------------------------------------------------------------
        // ROW 1: Preset Sound Themes (Stone, Wood, Metal, Glass, Grass, Deepslate, Netherite)
        // ---------------------------------------------------------------------
        addSoundPreset(canvas, 10, Material.STONE, "Stone Palette", "BLOCK_STONE", player);
        addSoundPreset(canvas, 11, Material.OAK_PLANKS, "Wood Palette", "BLOCK_WOOD", player);
        addSoundPreset(canvas, 12, Material.IRON_BLOCK, "Metal Palette", "BLOCK_METAL", player);
        addSoundPreset(canvas, 13, Material.GLASS, "Glass Palette", "BLOCK_GLASS", player);
        addSoundPreset(canvas, 14, Material.GRASS_BLOCK, "Grass / Plant Palette", "BLOCK_GRASS", player);
        addSoundPreset(canvas, 15, Material.DEEPSLATE, "Deepslate Palette", "BLOCK_DEEPSLATE", player);
        addSoundPreset(canvas, 16, Material.NETHERITE_BLOCK, "Heavy Anvil Palette", "BLOCK_ANVIL", player);

        // ---------------------------------------------------------------------
        // ROW 3: Granular Sound Channels
        // ---------------------------------------------------------------------
        // Slot 28: Break Sound
        ItemStack breakBtn = createBtn(Material.IRON_PICKAXE,
                "<gold>Break Sound</gold>",
                "<gray>Current:</gray> <white>" + mining.getBreakSound() + "</white>",
                "",
                "<yellow>Left-Click: Test playback sound</yellow>",
                "<yellow>Right-Click / Shift-Click: Enter custom sound ID</yellow>");
        canvas.setButton(28, CanvasButton.of(breakBtn, e -> {
            if (e.isRightClick() || e.isShiftClick()) {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter sound name (e.g. <yellow>BLOCK_STONE_BREAK</yellow> or custom key):</gray>"),
                        input -> {
                            mining.setBreakSound(input.trim().toUpperCase());
                            open(player);
                        });
            } else {
                playTestSound(player, mining.getBreakSound());
            }
        }));

        // Slot 29: Place Sound
        ItemStack placeBtn = createBtn(Material.BRICKS,
                "<gold>Place Sound</gold>",
                "<gray>Current:</gray> <white>" + mining.getPlaceSound() + "</white>",
                "",
                "<yellow>Left-Click: Test playback sound</yellow>",
                "<yellow>Right-Click / Shift-Click: Enter custom sound ID</yellow>");
        canvas.setButton(29, CanvasButton.of(placeBtn, e -> {
            if (e.isRightClick() || e.isShiftClick()) {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter sound name (e.g. <yellow>BLOCK_STONE_PLACE</yellow>):</gray>"),
                        input -> {
                            mining.setPlaceSound(input.trim().toUpperCase());
                            open(player);
                        });
            } else {
                playTestSound(player, mining.getPlaceSound());
            }
        }));

        // Slot 30: Step Sound
        ItemStack stepBtn = createBtn(Material.LEATHER_BOOTS,
                "<gold>Step Sound</gold>",
                "<gray>Current:</gray> <white>" + mining.getStepSound() + "</white>",
                "",
                "<yellow>Left-Click: Test playback sound</yellow>",
                "<yellow>Right-Click / Shift-Click: Enter custom sound ID</yellow>");
        canvas.setButton(30, CanvasButton.of(stepBtn, e -> {
            if (e.isRightClick() || e.isShiftClick()) {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter sound name (e.g. <yellow>BLOCK_STONE_STEP</yellow>):</gray>"),
                        input -> {
                            mining.setStepSound(input.trim().toUpperCase());
                            open(player);
                        });
            } else {
                playTestSound(player, mining.getStepSound());
            }
        }));

        // Slot 31: Hit / Digging Sound
        ItemStack hitBtn = createBtn(Material.WOODEN_PICKAXE,
                "<gold>Hit Sound</gold>",
                "<gray>Current:</gray> <white>" + mining.getHitSound() + "</white>",
                "",
                "<yellow>Left-Click: Test playback sound</yellow>",
                "<yellow>Right-Click / Shift-Click: Enter custom sound ID</yellow>");
        canvas.setButton(31, CanvasButton.of(hitBtn, e -> {
            if (e.isRightClick() || e.isShiftClick()) {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter sound name (e.g. <yellow>BLOCK_STONE_HIT</yellow>):</gray>"),
                        input -> {
                            mining.setHitSound(input.trim().toUpperCase());
                            open(player);
                        });
            } else {
                playTestSound(player, mining.getHitSound());
            }
        }));

        // Slot 33: Shatter Particles
        boolean isItemTexture = "ITEM".equalsIgnoreCase(mining.getBreakParticles());
        ItemStack particleBtn = createBtn(isItemTexture ? Material.AMETHYST_SHARD : Material.STONE,
                "<gold>Break Shatter Particles</gold>",
                "<gray>Mode:</gray> " + (isItemTexture ? "<green>Authentic Model/CMD Texture Particles</green>" : "<aqua>" + mining.getBreakParticles() + "</aqua>"),
                "",
                "<yellow>Left-Click: Toggle ITEM vs Material particle</yellow>",
                "<yellow>Right-Click: Type custom Material name</yellow>");
        canvas.setButton(33, CanvasButton.of(particleBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (e.isRightClick()) {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter particle material name (or <yellow>ITEM</yellow> for model texture):</gray>"),
                        input -> {
                            String trimmed = input.trim().toUpperCase();
                            mining.setBreakParticles(trimmed.isEmpty() ? "ITEM" : trimmed);
                            open(player);
                        });
            } else {
                if (isItemTexture) {
                    mining.setBreakParticles(blockDef.getBaseMaterial().name());
                } else {
                    mining.setBreakParticles("ITEM");
                }
                open(player);
            }
        }));

        // Slot 45: Back Button
        ItemStack backBtn = createBtn(Material.ARROW,
                "<yellow><bold>« Back</bold></yellow>",
                "<gray>Return to Block Editor Menu.</gray>");
        canvas.setButton(45, CanvasButton.of(backBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            if (onBack != null) onBack.run();
        }));

        canvas.open(player);
    }

    private void addSoundPreset(Canvas canvas, int slot, Material icon, String name, String prefix, Player player) {
        ItemStack btn = createBtn(icon,
                "<aqua>" + name + "</aqua>",
                "<gray>Apply all sounds from this palette:</gray>",
                "<dark_gray>• Break: " + prefix + "_BREAK</dark_gray>",
                "<dark_gray>• Place: " + prefix + "_PLACE</dark_gray>",
                "<dark_gray>• Step: " + prefix + "_STEP</dark_gray>",
                "<dark_gray>• Hit: " + prefix + "_HIT</dark_gray>",
                "",
                "<yellow>Click to apply preset</yellow>");
        canvas.setButton(slot, CanvasButton.of(btn, e -> {
            BlockMiningSpec m = blockDef.getMining();
            m.setBreakSound(prefix + "_BREAK");
            m.setPlaceSound(prefix + "_PLACE");
            m.setStepSound(prefix + "_STEP");
            m.setHitSound(prefix + "_HIT");
            playTestSound(player, m.getBreakSound());
            player.sendMessage(TextUtil.parse("<green>✓ Applied <yellow>" + name + "</yellow> sound palette!</green>"));
            open(player);
        }));
    }

    private void playTestSound(Player player, String soundKey) {
        if (soundKey == null || soundKey.isEmpty()) return;
        try {
            Sound s = Sound.valueOf(soundKey.toUpperCase());
            player.playSound(player.getLocation(), s, 1.0f, 1.0f);
        } catch (IllegalArgumentException ex) {
            // Might be a custom resource pack sound key
            player.playSound(player.getLocation(), soundKey.toLowerCase(), 1.0f, 1.0f);
        }
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

