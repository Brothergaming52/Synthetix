package org.bg52.synthetix.gui.editor.block;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.block.BlockDropSpec;
import org.bg52.synthetix.block.BlockMiningSpec;
import org.bg52.synthetix.block.CustomBlockDefinition;
import org.bg52.synthetix.gui.Canvas;
import org.bg52.synthetix.gui.CanvasButton;
import org.bg52.synthetix.item.ItemDefinition;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Sub-GUI for managing custom block harvesting drops, drop quantities, drop chances,
 * silk-touch requirements, fortune multipliers, and experience drops.
 */
public class BlockDropsGUI {

    private final SynthetixPlugin plugin;
    private final CustomBlockDefinition blockDef;
    private final Runnable onBack;

    public BlockDropsGUI(SynthetixPlugin plugin, CustomBlockDefinition blockDef, Runnable onBack) {
        this.plugin = plugin;
        this.blockDef = blockDef;
        this.onBack = onBack;
    }

    public static void open(Player player, SynthetixPlugin plugin, CustomBlockDefinition blockDef, Runnable onBack) {
        new BlockDropsGUI(plugin, blockDef, onBack).open(player);
    }

    public void open(Player player) {
        Component title = TextUtil.parse("<gold>Block Harvesting & Loot Drops</gold>");
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
        // ROW 0: Experience Drop
        // ---------------------------------------------------------------------
        ItemStack xpBtn = createBtn(Material.EXPERIENCE_BOTTLE,
                "<green><bold>Mining Experience Drop</bold></green>",
                "<gray>Current XP:</gray> <white>" + mining.getExpDrop() + " XP</white>",
                "",
                "<yellow>Left-Click: +1 XP | Right-Click: -1 XP</yellow>",
                "<yellow>Shift-Left: +5 XP | Shift-Right: Type custom</yellow>");
        canvas.setButton(4, CanvasButton.of(xpBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (e.isShiftClick() && e.isRightClick()) {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter experience drop amount (0 - 100):</gray>"),
                        input -> {
                            try {
                                mining.setExpDrop(Integer.parseInt(input.trim()));
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

        // ---------------------------------------------------------------------
        // DROPS GRID (Rows 1 to 3)
        // ---------------------------------------------------------------------
        List<BlockDropSpec> drops = mining.getDrops();
        int[] dropSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34
        };

        for (int i = 0; i < drops.size() && i < dropSlots.length; i++) {
            final int index = i;
            BlockDropSpec drop = drops.get(index);
            ItemStack icon = resolveDropIcon(drop);
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.displayName(TextUtil.parse("<gold><bold>Drop #" + (index + 1) + ": </bold><yellow>" + drop.getItemId() + "</yellow></gold>"));
                List<Component> lore = new ArrayList<>();
                lore.add(TextUtil.parse("<gray>Amount Range:</gray> <white>" + drop.getMinAmount() + " - " + drop.getMaxAmount() + "</white>"));
                lore.add(TextUtil.parse("<gray>Drop Chance:</gray> <aqua>" + (int)(drop.getChance() * 100) + "%</aqua>"));
                lore.add(TextUtil.parse("<gray>Silk Touch Only:</gray> " + (drop.isRequiresSilkTouch() ? "<green>Yes</green>" : "<red>No</red>")));
                lore.add(TextUtil.parse("<gray>Fortune Scaling:</gray> " + (drop.isFortuneAffected() ? "<green>Enabled</green>" : "<red>Disabled</red>")));
                lore.add(Component.empty());
                lore.add(TextUtil.parse("<dark_gray>────────────────────</dark_gray>"));
                lore.add(TextUtil.parse("<yellow>Left-Click: Increase Max Amount (+1)</yellow>"));
                lore.add(TextUtil.parse("<yellow>Right-Click: Cycle Chance (-25% / +25%)</yellow>"));
                lore.add(TextUtil.parse("<yellow>Shift-Left: Toggle Silk Touch Requirement</yellow>"));
                lore.add(TextUtil.parse("<yellow>Shift-Right: Toggle Fortune Scaling</yellow>"));
                lore.add(TextUtil.parse("<red>Drop-Key (Q): Remove this drop</red>"));
                meta.lore(lore);
                icon.setItemMeta(meta);
            }

            canvas.setButton(dropSlots[i], CanvasButton.of(icon, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                if (e.getClick().name().contains("DROP")) {
                    drops.remove(index);
                    player.sendMessage(TextUtil.parse("<red>✓ Removed drop #" + (index + 1) + "</red>"));
                    open(player);
                } else if (e.isShiftClick() && e.isRightClick()) {
                    drop.setFortuneAffected(!drop.isFortuneAffected());
                    open(player);
                } else if (e.isShiftClick()) {
                    drop.setRequiresSilkTouch(!drop.isRequiresSilkTouch());
                    open(player);
                } else if (e.isRightClick()) {
                    double newChance = drop.getChance() - 0.25;
                    if (newChance < 0.05) newChance = 1.0;
                    drop.setChance(newChance);
                    open(player);
                } else {
                    int max = drop.getMaxAmount() + 1;
                    if (max > 64) max = 1;
                    drop.setMaxAmount(max);
                    if (drop.getMinAmount() > max) drop.setMinAmount(max);
                    open(player);
                }
            }));
        }

        // ---------------------------------------------------------------------
        // ROW 5: Controls Footer
        // ---------------------------------------------------------------------
        // Slot 45: Back Button
        ItemStack backBtn = createBtn(Material.ARROW,
                "<yellow><bold>« Back</bold></yellow>",
                "<gray>Return to Block Editor Menu.</gray>");
        canvas.setButton(45, CanvasButton.of(backBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            if (onBack != null) onBack.run();
        }));

        // Slot 49: Add New Drop Rule
        ItemStack addBtn = createBtn(Material.EMERALD,
                "<green><bold>+ Add New Drop</bold></green>",
                "<gray>Add another item drop upon block breaking.</gray>",
                "",
                "<yellow>Click to specify item ID (self, Material, synth:id) »</yellow>");
        canvas.setButton(49, CanvasButton.of(addBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            plugin.getChatPromptManager().prompt(player,
                    TextUtil.parse("<gray>Enter Drop Item ID (<yellow>self</yellow>, <yellow>DIAMOND</yellow>, or <yellow>synth:item_id</yellow>):</gray>"),
                    input -> {
                        String id = input.trim();
                        if (!id.isEmpty()) {
                            drops.add(new BlockDropSpec(id, 1, 1, 1.0));
                            player.sendMessage(TextUtil.parse("<green>✓ Added drop rule for <yellow>" + id + "</yellow>!</green>"));
                        }
                        open(player);
                    });
        }));

        // Slot 53: Reset to Self
        ItemStack resetBtn = createBtn(Material.REDSTONE,
                "<red><bold>Reset Drops to Default (Self)</bold></red>",
                "<gray>Clear all custom drops and restore default</gray>",
                "<gray>behavior (drops 1x of this custom block).</gray>",
                "",
                "<yellow>Click to reset drops</yellow>");
        canvas.setButton(53, CanvasButton.of(resetBtn, e -> {
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.0f);
            drops.clear();
            drops.add(new BlockDropSpec("self", 1, 1, 1.0));
            player.sendMessage(TextUtil.parse("<yellow>✓ Drops reset to default self drop.</yellow>"));
            open(player);
        }));

        canvas.open(player);
    }

    private ItemStack resolveDropIcon(BlockDropSpec drop) {
        String id = drop.getItemId();
        if ("self".equalsIgnoreCase(id)) {
            return blockDef.buildBlockItemStack(1);
        }
        if (id.startsWith("synth:")) {
            String subId = id.substring("synth:".length());
            ItemDefinition cDef = plugin.getItemRegistry().get(subId);
            if (cDef != null) {
                return cDef.buildItemStack();
            }
            CustomBlockDefinition bDef = plugin.getCustomBlockRegistry().get(subId);
            if (bDef != null) {
                return bDef.buildBlockItemStack(1);
            }
        }
        try {
            Material mat = Material.matchMaterial(id);
            if (mat != null && !mat.isAir()) {
                return new ItemStack(mat);
            }
        } catch (Exception ignored) {}
        return new ItemStack(Material.CHEST);
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
