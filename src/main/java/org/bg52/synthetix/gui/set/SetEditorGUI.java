package org.bg52.synthetix.gui.set;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.gui.Canvas;
import org.bg52.synthetix.gui.CanvasButton;
import org.bg52.synthetix.item.ItemDefinition;
import org.bg52.synthetix.item.food.FoodEffectSpec;
import org.bg52.synthetix.lang.LanguageManager;
import org.bg52.synthetix.set.ItemSet;
import org.bg52.synthetix.set.SetBonus;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Visual in-game editor for configuring an ItemSet:
 * Adding pieces, setting display names, configuring tier bonuses, and potion buffs.
 */
public class SetEditorGUI {

    private final SynthetixPlugin plugin;
    private final LanguageManager lang;
    private final ItemSet set;
    private final Runnable onBack;

    public SetEditorGUI(SynthetixPlugin plugin, ItemSet set, Runnable onBack) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
        this.set = set;
        this.onBack = onBack;
    }

    public static void open(Player player, SynthetixPlugin plugin, ItemSet set, Runnable onBack) {
        new SetEditorGUI(plugin, set, onBack).open(player);
    }

    public void open(Player player) {
        Canvas canvas = new Canvas(TextUtil.parse("<dark_gray>Edit Set: <gold>" + set.getDisplayName() + "</gold></dark_gray>"), 5);

        ItemStack darkGlass = Canvas.createBorderItem(Material.BLACK_STAINED_GLASS_PANE, Component.empty());
        for (int c = 0; c < 9; c++) {
            canvas.setButton(0, c, CanvasButton.of(darkGlass));
            canvas.setButton(4, c, CanvasButton.of(darkGlass));
        }

        // Top Preview (Slot 4)
        ItemStack preview = new ItemStack(Material.NETHERITE_CHESTPLATE);
        ItemMeta pMeta = preview.getItemMeta();
        if (pMeta != null) {
            pMeta.displayName(TextUtil.parse(set.getDisplayName()));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>ID:</gray> <white>" + set.getId() + "</white>"));
            lore.add(TextUtil.parse("<gray>Pieces:</gray> <white>" + set.getPieces().size() + "</white>"));
            lore.add(TextUtil.parse("<gray>Bonus Tiers:</gray> <white>" + set.getBonuses().size() + "</white>"));
            pMeta.lore(lore);
            preview.setItemMeta(pMeta);
        }
        canvas.setButton(4, CanvasButton.of(preview));

        // 1. Display Name (Slot 11)
        ItemStack nameBtn = new ItemStack(Material.NAME_TAG);
        ItemMeta nMeta = nameBtn.getItemMeta();
        if (nMeta != null) {
            nMeta.displayName(TextUtil.parse("<gold>Set Display Name</gold>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Current:</gray> " + set.getDisplayName()));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>Click to edit via chat prompt</yellow>"));
            nMeta.lore(lore);
            nameBtn.setItemMeta(nMeta);
        }
        canvas.setButton(11, CanvasButton.of(nameBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            plugin.getChatPromptManager().prompt(player,
                    TextUtil.parse("<gray>Enter new display name for set <white>" + set.getId() + "</white>:</gray>"),
                    input -> {
                        set.setDisplayName(input);
                        plugin.getApi().getSetManager().saveAll();
                        open(player);
                    });
        }));

        // 2. Set Pieces (Slot 13)
        ItemStack piecesBtn = new ItemStack(Material.ARMOR_STAND);
        ItemMeta pcMeta = piecesBtn.getItemMeta();
        if (pcMeta != null) {
            pcMeta.displayName(TextUtil.parse("<gold>Set Pieces (" + set.getPieces().size() + ")</gold>"));
            List<Component> lore = new ArrayList<>();
            for (String p : set.getPieces()) {
                lore.add(TextUtil.parse("<dark_gray>•</dark_gray> <white>" + p + "</white>"));
            }
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>Click to manage pieces »</yellow>"));
            pcMeta.lore(lore);
            piecesBtn.setItemMeta(pcMeta);
        }
        canvas.setButton(13, CanvasButton.of(piecesBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            openPiecesMenu(player);
        }));

        // 3. Bonus Tiers (Slot 15)
        ItemStack bonusBtn = new ItemStack(Material.BEACON);
        ItemMeta bMeta = bonusBtn.getItemMeta();
        if (bMeta != null) {
            bMeta.displayName(TextUtil.parse("<gold>Tier Bonuses (" + set.getBonuses().size() + ")</gold>"));
            List<Component> lore = new ArrayList<>();
            for (Map.Entry<Integer, SetBonus> entry : set.getBonuses().entrySet()) {
                lore.add(TextUtil.parse("<dark_gray>•</dark_gray> <yellow>" + entry.getKey() + " Pieces:</yellow> " + entry.getValue().getLoreLine()));
            }
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>Click to manage bonus tiers »</yellow>"));
            bMeta.lore(lore);
            bonusBtn.setItemMeta(bMeta);
        }
        canvas.setButton(15, CanvasButton.of(bonusBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            openBonusesMenu(player);
        }));

        // Back Button (Slot 36)
        if (onBack != null) {
            ItemStack backBtn = Canvas.createBorderItem(Material.ARROW, lang.getComponent("editor.back-button"));
            canvas.setButton(36, CanvasButton.of(backBtn, e -> onBack.run()));
        }

        canvas.open(player);
    }

    private void openPiecesMenu(Player player) {
        Canvas canvas = new Canvas(TextUtil.parse("<dark_gray>Pieces: " + set.getId() + "</dark_gray>"), 4);

        int[] slots = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 23, 24, 25};
        List<String> pieces = new ArrayList<>(set.getPieces());

        for (int i = 0; i < pieces.size() && i < slots.length; i++) {
            String pId = pieces.get(i);
            ItemDefinition def = plugin.getItemRegistry().get(pId);
            ItemStack icon = def != null ? def.buildItemStack() : new ItemStack(Material.IRON_CHESTPLATE);

            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                lore.add(Component.empty());
                lore.add(TextUtil.parse("<red>Click to remove piece from set</red>"));
                meta.lore(lore);
                icon.setItemMeta(meta);
            }

            canvas.setButton(slots[i], CanvasButton.of(icon, e -> {
                set.removePiece(pId);
                plugin.getApi().getSetManager().saveAll();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                openPiecesMenu(player);
            }));
        }

        // Add Piece Button (Slot 22)
        ItemStack addBtn = new ItemStack(Material.EMERALD);
        ItemMeta aMeta = addBtn.getItemMeta();
        if (aMeta != null) {
            aMeta.displayName(TextUtil.parse("<green><bold>+ Add Piece</bold></green>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Enter an item ID to add to this set.</gray>"));
            aMeta.lore(lore);
            addBtn.setItemMeta(aMeta);
        }
        canvas.setButton(22, CanvasButton.of(addBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            plugin.getChatPromptManager().prompt(player,
                    TextUtil.parse("<gray>Enter Item ID to add as piece (e.g. <yellow>ruby_helmet</yellow>):</gray>"),
                    id -> {
                        String clean = id.trim().toLowerCase();
                        set.addPiece(clean);
                        plugin.getApi().getSetManager().saveAll();
                        openPiecesMenu(player);
                    });
        }));

        ItemStack backBtn = Canvas.createBorderItem(Material.ARROW, lang.getComponent("editor.back-button"));
        canvas.setButton(27, CanvasButton.of(backBtn, e -> open(player)));

        canvas.open(player);
    }

    private void openBonusesMenu(Player player) {
        Canvas canvas = new Canvas(TextUtil.parse("<dark_gray>Bonuses: " + set.getId() + "</dark_gray>"), 4);

        int[] slots = new int[]{10, 11, 12, 13, 14, 15, 16};
        List<Map.Entry<Integer, SetBonus>> entries = new ArrayList<>(set.getBonuses().entrySet());

        for (int i = 0; i < entries.size() && i < slots.length; i++) {
            Map.Entry<Integer, SetBonus> entry = entries.get(i);
            int pieceCount = entry.getKey();
            SetBonus bonus = entry.getValue();

            ItemStack icon = new ItemStack(Material.BEACON);
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.displayName(TextUtil.parse("<gold><bold>" + pieceCount + " Pieces Bonus</bold></gold>"));
                List<Component> lore = new ArrayList<>();
                lore.add(TextUtil.parse("<gray>Lore:</gray> " + bonus.getLoreLine()));
                lore.add(TextUtil.parse("<gray>Effects:</gray> <white>" + bonus.getPotionEffects().size() + "</white>"));
                lore.add(Component.empty());
                lore.add(TextUtil.parse("<yellow>Left-Click: Add potion effect</yellow>"));
                lore.add(TextUtil.parse("<red>Right-Click: Remove tier bonus</red>"));
                meta.lore(lore);
                icon.setItemMeta(meta);
            }

            canvas.setButton(slots[i], CanvasButton.of(icon, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
                if (e.isRightClick()) {
                    set.removeBonus(pieceCount);
                    plugin.getApi().getSetManager().saveAll();
                    openBonusesMenu(player);
                } else {
                    plugin.getChatPromptManager().prompt(player,
                            TextUtil.parse("<gray>Enter effect in format: <yellow><EFFECT_NAME> <level></yellow> (e.g. <yellow>SPEED 1</yellow>, <yellow>FIRE_RESISTANCE 1</yellow>):</gray>"),
                            input -> {
                                String[] parts = input.trim().split("\\s+");
                                if (parts.length >= 2) {
                                    try {
                                        PotionEffectType type = FoodEffectSpec.parsePotionEffectType(parts[0]);
                                        int level = Math.max(1, Integer.parseInt(parts[1]));
                                        if (type != null) {
                                            bonus.getPotionEffects().add(new PotionEffect(type, 60, level - 1, false, false, true));
                                            plugin.getApi().getSetManager().saveAll();
                                        }
                                    } catch (Exception ignored) {}
                                }
                                openBonusesMenu(player);
                            });
                }
            }));
        }

        // Add Tier Button (Slot 22)
        ItemStack addTierBtn = new ItemStack(Material.EMERALD);
        ItemMeta aMeta = addTierBtn.getItemMeta();
        if (aMeta != null) {
            aMeta.displayName(TextUtil.parse("<green><bold>+ Add Tier Bonus</bold></green>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Add a bonus for wearing N pieces.</gray>"));
            aMeta.lore(lore);
            addTierBtn.setItemMeta(aMeta);
        }
        canvas.setButton(22, CanvasButton.of(addTierBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            plugin.getChatPromptManager().prompt(player,
                    TextUtil.parse("<gray>Enter piece requirement and lore description in format: <yellow><pieces> <description></yellow><br>Example: <yellow>2 +15% Speed</yellow></gray>"),
                    input -> {
                        String[] parts = input.trim().split("\\s+", 2);
                        if (parts.length >= 2) {
                            try {
                                int pcs = Integer.parseInt(parts[0]);
                                SetBonus bonus = new SetBonus(pcs);
                                bonus.setLoreLine("<green>" + pcs + "/" + set.getPieces().size() + " Set: " + parts[1] + "</green>");
                                set.addBonus(pcs, bonus);
                                plugin.getApi().getSetManager().saveAll();
                            } catch (Exception ignored) {}
                        }
                        openBonusesMenu(player);
                    });
        }));

        ItemStack backBtn = Canvas.createBorderItem(Material.ARROW, lang.getComponent("editor.back-button"));
        canvas.setButton(27, CanvasButton.of(backBtn, e -> open(player)));

        canvas.open(player);
    }
}

