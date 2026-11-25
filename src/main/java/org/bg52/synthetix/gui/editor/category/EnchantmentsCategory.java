package org.bg52.synthetix.gui.editor.category;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.api.editor.ItemEditorCategory;
import org.bg52.synthetix.gui.CanvasButton;
import org.bg52.synthetix.gui.editor.BaseEditorCanvas;
import org.bg52.synthetix.item.ItemDefinition;
import org.bg52.synthetix.lang.LanguageManager;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Core category dynamically listing and modifying all server enchantments (vanilla and custom).
 * Fully localized via LanguageManager and compatible with Paper 1.20.6 through Paper 26.3+.
 */
public class EnchantmentsCategory implements ItemEditorCategory {

    private final SynthetixPlugin plugin;
    private final LanguageManager lang;

    public EnchantmentsCategory(SynthetixPlugin plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    @Override
    public String getId() {
        return "enchantments";
    }

    @Override
    public Component getDisplayName() {
        return lang.getComponent("editor.categories.enchantments.name");
    }

    @Override
    public int getPriority() {
        return 40;
    }

    @Override
    public boolean isPluginCategory() {
        return false;
    }

    @Override
    public ItemStack getIcon(ItemDefinition def) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(getDisplayName());
            List<Component> lore = new ArrayList<>();
            lore.add(lang.getComponent("editor.categories.enchantments.desc-1"));
            lore.add(lang.getComponent("editor.categories.enchantments.desc-2"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<dark_gray>•</dark_gray> <gray>Applied Enchants:</gray> <white>" + def.getEnchants().size() + "</white>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>Click to browse all enchantments »</yellow>"));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void open(Player player, ItemDefinition def, Runnable onBack) {
        openEnchantsPage(player, def, onBack, 0);
    }

    private void openEnchantsPage(Player player, ItemDefinition def, Runnable onBack, int page) {
        List<Enchantment> allEnchants = new ArrayList<>();
        for (Enchantment ench : Registry.ENCHANTMENT) {
            allEnchants.add(ench);
        }
        allEnchants.sort(Comparator.comparing(e -> e.getKey().asString()));

        int pageSize = 28; // 4 rows x 7 cols
        int totalPages = Math.max(1, (int) Math.ceil((double) allEnchants.size() / pageSize));
        int curPage = Math.max(0, Math.min(totalPages - 1, page));

        BaseEditorCanvas base = new BaseEditorCanvas(plugin, def,
                TextUtil.parse(lang.get("editor.categories.enchantments.title") + " <dark_gray>(" + (curPage + 1) + "/" + totalPages + ")</dark_gray>"), 6);

        int start = curPage * pageSize;
        int end = Math.min(start + pageSize, allEnchants.size());

        int[] validSlots = new int[]{
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        int slotIdx = 0;
        for (int i = start; i < end; i++) {
            Enchantment ench = allEnchants.get(i);
            String enchKey = ench.getKey().getKey();
            Integer level = def.getEnchants().get(enchKey);
            if (level == null) {
                level = def.getEnchants().get(ench.getKey().asString());
            }

            boolean applied = level != null && level > 0;
            ItemStack icon = new ItemStack(applied ? Material.ENCHANTED_BOOK : Material.BOOK);
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.displayName(TextUtil.parse("<yellow>" + formatName(enchKey) + "</yellow>"));
                List<Component> lore = new ArrayList<>();
                lore.add(TextUtil.parse("<dark_gray>Key: " + ench.getKey().asString() + "</dark_gray>"));
                lore.add(Component.empty());
                lore.add(TextUtil.parse("<gray>Status:</gray> " + (applied ? "<green><bold>LEVEL " + level + "</bold></green>" : lang.get("general.not-applied"))));
                lore.add(Component.empty());
                lore.add(TextUtil.parse("<yellow>Click to set level / remove »</yellow>"));
                meta.lore(lore);
                icon.setItemMeta(meta);
            }

            int targetSlot = validSlots[slotIdx++];
            base.getCanvas().setButton(targetSlot, CanvasButton.of(icon, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                openLevelPicker(player, def, ench, () -> openEnchantsPage(player, def, onBack, curPage));
            }));
        }

        base.setBackButton(onBack);
        base.setPaginationButtons(
                curPage > 0, () -> openEnchantsPage(player, def, onBack, curPage - 1),
                curPage < totalPages - 1, () -> openEnchantsPage(player, def, onBack, curPage + 1)
        );

        base.open(player);
    }

    private void openLevelPicker(Player player, ItemDefinition def, Enchantment ench, Runnable onBack) {
        String enchKey = ench.getKey().getKey();
        String formatted = formatName(enchKey);

        BaseEditorCanvas base = new BaseEditorCanvas(plugin, def,
                lang.getComponent("editor.categories.enchantments.level-title", "%enchant%", formatted), 4);
        base.setupSideBorders();

        // Quick level buttons (Slots 10 to 14)
        for (int lvl = 1; lvl <= 5; lvl++) {
            final int chosen = lvl;
            ItemStack icon = new ItemStack(Material.EXPERIENCE_BOTTLE);
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.displayName(lang.getComponent("editor.categories.enchantments.quick-level", "%level%", String.valueOf(chosen)));
                meta.lore(List.of(TextUtil.parse("<gray>Click to set enchantment to Level " + chosen + "</gray>")));
                icon.setItemMeta(meta);
            }
            base.getCanvas().setButton(9 + lvl, CanvasButton.of(icon, e -> {
                def.getEnchants().put(enchKey, chosen);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f);
                lang.send(player, "editor.categories.enchantments.applied", "%enchant%", formatted, "%level%", String.valueOf(chosen));
                onBack.run();
            }));
        }

        // Custom level prompt (Slot 15)
        ItemStack customBtn = new ItemStack(Material.ANVIL);
        ItemMeta cMeta = customBtn.getItemMeta();
        if (cMeta != null) {
            cMeta.displayName(lang.getComponent("editor.categories.enchantments.custom-level-btn"));
            cMeta.lore(List.of(TextUtil.parse("<gray>Click to type custom level in chat (e.g. 10, 100)</gray>")));
            customBtn.setItemMeta(cMeta);
        }
        base.getCanvas().setButton(15, CanvasButton.of(customBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            plugin.getChatPromptManager().prompt(player,
                    lang.getComponent("editor.prompts.enter-ench-level", "%enchant%", formatted),
                    input -> {
                        try {
                            int customLvl = Integer.parseInt(input.trim());
                            if (customLvl > 0) {
                                def.getEnchants().put(enchKey, customLvl);
                                lang.send(player, "editor.categories.enchantments.applied", "%enchant%", formatted, "%level%", String.valueOf(customLvl));
                            }
                        } catch (NumberFormatException ignored) {}
                        onBack.run();
                    });
        }));

        // Remove enchantment button (Slot 16)
        ItemStack removeBtn = new ItemStack(Material.BARRIER);
        ItemMeta rMeta = removeBtn.getItemMeta();
        if (rMeta != null) {
            rMeta.displayName(lang.getComponent("editor.categories.enchantments.remove-btn"));
            rMeta.lore(List.of(TextUtil.parse("<gray>Click to remove this enchantment completely.</gray>")));
            removeBtn.setItemMeta(rMeta);
        }
        base.getCanvas().setButton(16, CanvasButton.of(removeBtn, e -> {
            def.getEnchants().remove(enchKey);
            def.getEnchants().remove(ench.getKey().asString());
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.7f, 1.0f);
            lang.send(player, "editor.categories.enchantments.removed", "%enchant%", formatted);
            onBack.run();
        }));

        base.setBackButton(onBack);
        base.open(player);
    }

    private String formatName(String raw) {
        return TextUtil.prettify(raw);
    }
}
