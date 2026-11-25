package org.bg52.synthetix.gui.editor.block;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.block.BlockInteractionSpec;
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
 * Sub-GUI for block interaction, flammability, blast resistance, piston pushability,
 * gravity, redstone emission, and interactive ability hooks.
 */
public class BlockInteractionGUI {

    private final SynthetixPlugin plugin;
    private final CustomBlockDefinition blockDef;
    private final Runnable onBack;

    public BlockInteractionGUI(SynthetixPlugin plugin, CustomBlockDefinition blockDef, Runnable onBack) {
        this.plugin = plugin;
        this.blockDef = blockDef;
        this.onBack = onBack;
    }

    public static void open(Player player, SynthetixPlugin plugin, CustomBlockDefinition blockDef, Runnable onBack) {
        new BlockInteractionGUI(plugin, blockDef, onBack).open(player);
    }

    public void open(Player player) {
        Component title = TextUtil.parse("<gold>Blast, Fire & Interaction Spec</gold>");
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

        BlockInteractionSpec interact = blockDef.getInteraction();

        // 1. Blast Resistance (Slot 10)
        float blast = blockDef.getMining().getBlastResistance();
        ItemStack blastBtn = createBtn(Material.TNT,
                "<gold>Blast Resistance</gold>",
                "<gray>Current Resistance:</gray> <white>" + blast + "</white>",
                "<dark_gray>(Dirt: 0.5, Wood: 2.0, Stone: 6.0, Obsidian: 1200.0)</dark_gray>",
                "",
                "<yellow>Left-Click: +1.0 | Right-Click: -1.0</yellow>",
                "<yellow>Shift-Left: +10.0 | Shift-Right: Type custom</yellow>");
        canvas.setButton(10, CanvasButton.of(blastBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (e.isShiftClick() && e.isRightClick()) {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter blast resistance (e.g. <yellow>6.0</yellow>):</gray>"),
                        input -> {
                            try {
                                blockDef.getMining().setBlastResistance(Float.parseFloat(input.trim()));
                            } catch (Exception ignored) {}
                            open(player);
                        });
            } else if (e.isShiftClick()) {
                blockDef.getMining().setBlastResistance(blast + 10.0f);
                open(player);
            } else if (e.isRightClick()) {
                blockDef.getMining().setBlastResistance(Math.max(0.0f, blast - 1.0f));
                open(player);
            } else {
                blockDef.getMining().setBlastResistance(blast + 1.0f);
                open(player);
            }
        }));

        // 2. Flammable Toggle (Slot 11)
        ItemStack flamBtn = createBtn(interact.isFlammable() ? Material.FIRE_CHARGE : Material.CHARCOAL,
                "<gold>Flammability</gold>",
                "<gray>Flammable:</gray> " + (interact.isFlammable() ? "<green>Yes (Can catch fire)</green>" : "<red>No (Immune to fire)</red>"),
                "",
                "<yellow>Click to toggle flammability</yellow>");
        canvas.setButton(11, CanvasButton.of(flamBtn, e -> {
            interact.setFlammable(!interact.isFlammable());
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            open(player);
        }));

        // 3. Burn Speed & Spread (Slot 12)
        ItemStack burnBtn = createBtn(Material.CAMPFIRE,
                "<gold>Burn & Fire Spread Rate</gold>",
                "<gray>Burn Speed:</gray> <white>" + interact.getBurnSpeed() + "</white>",
                "<gray>Spread Speed:</gray> <white>" + interact.getFireSpreadSpeed() + "</white>",
                "",
                "<yellow>Left-Click: Increase Burn Speed (+5)</yellow>",
                "<yellow>Right-Click: Decrease Burn Speed (-5)</yellow>",
                "<yellow>Shift-Left: Increase Fire Spread (+5)</yellow>",
                "<yellow>Shift-Right: Decrease Fire Spread (-5)</yellow>");
        canvas.setButton(12, CanvasButton.of(burnBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (e.isShiftClick() && e.isRightClick()) {
                interact.setFireSpreadSpeed(Math.max(0, interact.getFireSpreadSpeed() - 5));
            } else if (e.isShiftClick()) {
                interact.setFireSpreadSpeed(interact.getFireSpreadSpeed() + 5);
            } else if (e.isRightClick()) {
                interact.setBurnSpeed(Math.max(0, interact.getBurnSpeed() - 5));
            } else {
                interact.setBurnSpeed(interact.getBurnSpeed() + 5);
            }
            open(player);
        }));

        // 4. Piston Pushable (Slot 14)
        ItemStack pistonBtn = createBtn(interact.isPistonPushable() ? Material.PISTON : Material.STICKY_PISTON,
                "<gold>Piston Pushable</gold>",
                "<gray>Pushable:</gray> " + (interact.isPistonPushable() ? "<green>Yes</green>" : "<red>No (Locked / Obsidian behavior)</red>"),
                "",
                "<yellow>Click to toggle piston pushability</yellow>");
        canvas.setButton(14, CanvasButton.of(pistonBtn, e -> {
            interact.setPistonPushable(!interact.isPistonPushable());
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            open(player);
        }));

        // 5. Gravity (Slot 15)
        ItemStack gravBtn = createBtn(interact.isHasGravity() ? Material.SAND : Material.SMOOTH_STONE,
                "<gold>Gravity</gold>",
                "<gray>Affected by Gravity:</gray> " + (interact.isHasGravity() ? "<green>Yes (Falls like sand)</green>" : "<red>No (Floats)</red>"),
                "",
                "<yellow>Click to toggle gravity</yellow>");
        canvas.setButton(15, CanvasButton.of(gravBtn, e -> {
            interact.setHasGravity(!interact.isHasGravity());
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            open(player);
        }));

        // 6. Redstone Signal Power (Slot 16)
        ItemStack redstoneBtn = createBtn(interact.getRedstonePower() > 0 ? Material.REDSTONE_TORCH : Material.REDSTONE,
                "<gold>Redstone Power Output</gold>",
                "<gray>Signal Strength:</gray> <red>" + interact.getRedstonePower() + "</red> <gray>/ 15</gray>",
                "",
                "<yellow>Left-Click: +1 | Right-Click: -1</yellow>",
                "<yellow>Shift-Click: Type custom (0 - 15)</yellow>");
        canvas.setButton(16, CanvasButton.of(redstoneBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (e.isShiftClick()) {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter redstone power (0 - 15):</gray>"),
                        input -> {
                            try {
                                interact.setRedstonePower(Integer.parseInt(input.trim()));
                            } catch (Exception ignored) {}
                            open(player);
                        });
            } else if (e.isRightClick()) {
                interact.setRedstonePower(Math.max(0, interact.getRedstonePower() - 1));
                open(player);
            } else {
                interact.setRedstonePower(Math.min(15, interact.getRedstonePower() + 1));
                open(player);
            }
        }));

        // 7. Interactive Ability Hook (Slot 22)
        ItemStack abilityBtn = createBtn(Material.NETHER_STAR,
                "<gold>Interactive Ability Hook</gold>",
                "<gray>Triggered Ability:</gray> " + (interact.getAbilityId() != null ? "<light_purple>" + interact.getAbilityId() + "</light_purple>" : "<gray>None</gray>"),
                "",
                "<yellow>Left-Click: Type Ability ID to trigger on right-click</yellow>",
                "<yellow>Right-Click: Clear ability</yellow>");
        canvas.setButton(22, CanvasButton.of(abilityBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (e.isRightClick()) {
                interact.setAbilityId(null);
                open(player);
            } else {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter Synthetix ability ID to trigger on block interaction:</gray>"),
                        input -> {
                            String trimmed = input.trim();
                            interact.setAbilityId(trimmed.isEmpty() ? null : trimmed);
                            open(player);
                        });
            }
        }));

        // Back Button (Slot 36)
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

