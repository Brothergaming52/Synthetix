package org.bg52.synthetix.gui.editor.distribution;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.distribution.LootTableRule;
import org.bg52.synthetix.gui.CanvasButton;
import org.bg52.synthetix.gui.editor.BaseEditorCanvas;
import org.bg52.synthetix.item.ItemDefinition;
import org.bg52.synthetix.util.LootTableFetcher;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Paginated browser showing all server loot tables with search filtering and presets.
 */
public class LootTableBrowserGUI {

    private final SynthetixPlugin plugin;
    private final ItemDefinition itemDef;
    private final Runnable onBack;

    private int page = 0;
    private String filter = "";
    private static final int ITEMS_PER_PAGE = 28;

    public LootTableBrowserGUI(SynthetixPlugin plugin, ItemDefinition itemDef, Runnable onBack) {
        this.plugin = plugin;
        this.itemDef = itemDef;
        this.onBack = onBack;
    }

    public void open(Player player) {
        List<LootTableOption> allOptions = getAvailableLootTables();
        if (!filter.isEmpty()) {
            String f = filter.toLowerCase(Locale.ROOT);
            allOptions = allOptions.stream()
                    .filter(opt -> opt.display.toLowerCase(Locale.ROOT).contains(f) || opt.pattern.toLowerCase(Locale.ROOT).contains(f))
                    .collect(Collectors.toList());
        }

        int totalPages = Math.max(1, (int) Math.ceil((double) allOptions.size() / ITEMS_PER_PAGE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        Component title = TextUtil.parse("<dark_gray>Loot Tables (" + (page + 1) + "/" + totalPages + ")</dark_gray>");
        BaseEditorCanvas base = new BaseEditorCanvas(plugin, itemDef, title, 6);
        base.setupSideBorders();

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, allOptions.size());

        int[] displaySlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        for (int i = start; i < end; i++) {
            LootTableOption opt = allOptions.get(i);
            int slot = displaySlots[i - start];

            ItemStack icon = new ItemStack(opt.icon);
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.displayName(TextUtil.parse("<gold><bold>" + opt.display + "</bold></gold>"));
                List<Component> l = new ArrayList<>();
                l.add(TextUtil.parse("<dark_gray>Key: " + opt.pattern + "</dark_gray>"));
                l.add(Component.empty());
                l.add(TextUtil.parse("<yellow>Click to configure injection »</yellow>"));
                meta.lore(l);
                icon.setItemMeta(meta);
            }

            base.getCanvas().setButton(slot, CanvasButton.of(icon, event -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                String cleanKey = opt.pattern.replace(":", "_").replace("/", "_").replace("*", "all");
                String ruleId = itemDef.getId() + "_" + cleanKey + "_" + (System.currentTimeMillis() % 10000);
                LootTableRule newRule = new LootTableRule(ruleId, "synth:" + itemDef.getId(),
                        Collections.singletonList(opt.pattern), 0.25, 1, 2, 1);
                new LootTableConfigGUI(plugin, itemDef, newRule, onBack).open(player);
            }));
        }

        // Search Button (Slot 47)
        ItemStack searchBtn = new ItemStack(Material.COMPASS);
        ItemMeta sMeta = searchBtn.getItemMeta();
        if (sMeta != null) {
            sMeta.displayName(TextUtil.parse("<aqua><bold>Search Loot Tables</bold></aqua>"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<gray>Filter: <white>" + (filter.isEmpty() ? "None (All Tables)" : filter) + "</white></gray>"));
            l.add(Component.empty());
            l.add(TextUtil.parse("<yellow>Left-Click:</yellow> <gray>Search via Chat</gray>"));
            if (!filter.isEmpty()) {
                l.add(TextUtil.parse("<red>Right-Click:</red> <gray>Clear filter</gray>"));
            }
            sMeta.lore(l);
            searchBtn.setItemMeta(sMeta);
        }
        base.getCanvas().setButton(47, CanvasButton.of(searchBtn, event -> {
            if (event.isRightClick() && !filter.isEmpty()) {
                filter = "";
                page = 0;
                open(player);
            } else {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Type loot table keyword (e.g. <yellow>dungeon</yellow>, <yellow>pyramid</yellow>, <yellow>trial</yellow>):</gray>"),
                        input -> {
                            this.filter = input.trim();
                            this.page = 0;
                            open(player);
                        });
            }
        }));

        // Pagination buttons in bottom bar
        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta pMeta = prev.getItemMeta();
            if (pMeta != null) {
                pMeta.displayName(TextUtil.parse("<yellow>« Previous Page (" + page + "/" + totalPages + ")</yellow>"));
                prev.setItemMeta(pMeta);
            }
            base.getCanvas().setButton(45, CanvasButton.of(prev, event -> {
                page--;
                open(player);
            }));
        }

        if (page < totalPages - 1) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nMeta = next.getItemMeta();
            if (nMeta != null) {
                nMeta.displayName(TextUtil.parse("<yellow>Next Page » (" + (page + 2) + "/" + totalPages + ")</yellow>"));
                next.setItemMeta(nMeta);
            }
            base.getCanvas().setButton(53, CanvasButton.of(next, event -> {
                page++;
                open(player);
            }));
        }

        base.setBackButton(onBack);
        base.open(player);
    }

    private List<LootTableOption> getAvailableLootTables() {
        List<LootTableOption> list = new ArrayList<>();

        // Wildcard presets at top
        list.add(new LootTableOption("All Chests Wildcard", "minecraft:chests/*", Material.ENDER_CHEST));
        list.add(new LootTableOption("All Archaeology Pots & Sand", "minecraft:archaeology/*", Material.DECORATED_POT));
        list.add(new LootTableOption("Trial Chambers Chests", "minecraft:chests/trial_chambers/*", Material.TRIAL_KEY));

        // Discover all server loot tables
        List<NamespacedKey> keys = LootTableFetcher.fetchAllLootTableKeys(plugin);
        for (NamespacedKey k : keys) {
            String path = k.asString();
            String label = k.getKey().replace("chests/", "").replace("archaeology/", "").replace("_", " ");
            label = Arrays.stream(label.split(" "))
                    .map(w -> w.isEmpty() ? "" : w.substring(0, 1).toUpperCase(Locale.ROOT) + w.substring(1).toLowerCase(Locale.ROOT))
                    .collect(Collectors.joining(" "));

            Material icon = Material.CHEST;
            if (path.contains("barrel")) icon = Material.BARREL;
            else if (path.contains("archaeology")) icon = Material.DECORATED_POT;
            else if (path.contains("shulker")) icon = Material.SHULKER_BOX;
            else if (path.contains("dispenser")) icon = Material.DISPENSER;

            list.add(new LootTableOption(label, path, icon));
        }

        return list;
    }

    private record LootTableOption(String display, String pattern, Material icon) {}
}

