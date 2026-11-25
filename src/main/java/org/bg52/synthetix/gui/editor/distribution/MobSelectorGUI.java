package org.bg52.synthetix.gui.editor.distribution;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.distribution.MobDropRule;
import org.bg52.synthetix.gui.Canvas;
import org.bg52.synthetix.gui.CanvasButton;
import org.bg52.synthetix.gui.editor.BaseEditorCanvas;
import org.bg52.synthetix.item.ItemDefinition;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Symmetrical, paginated living entity browser with search filtering.
 */
public class MobSelectorGUI {

    private final SynthetixPlugin plugin;
    private final ItemDefinition itemDef;
    private final Runnable onBack;

    private int page = 0;
    private String filter = "";
    private static final int ITEMS_PER_PAGE = 28; // 4 rows x 7 cols (slots 10-16, 19-25, 28-34, 37-43)

    public MobSelectorGUI(SynthetixPlugin plugin, ItemDefinition itemDef, Runnable onBack) {
        this.plugin = plugin;
        this.itemDef = itemDef;
        this.onBack = onBack;
    }

    public void open(Player player) {
        List<MobOption> allOptions = getAvailableMobs();
        if (!filter.isEmpty()) {
            String f = filter.toLowerCase(Locale.ROOT);
            allOptions = allOptions.stream()
                    .filter(opt -> opt.name.toLowerCase(Locale.ROOT).contains(f) || opt.entityKey.toLowerCase(Locale.ROOT).contains(f))
                    .collect(Collectors.toList());
        }

        int totalPages = Math.max(1, (int) Math.ceil((double) allOptions.size() / ITEMS_PER_PAGE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        Component title = TextUtil.parse("<dark_gray>Select Mob (" + (page + 1) + "/" + totalPages + ")</dark_gray>");
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
            MobOption opt = allOptions.get(i);
            int slot = displaySlots[i - start];

            ItemStack icon = new ItemStack(opt.icon);
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.displayName(TextUtil.parse("<gold><bold>" + opt.name + "</bold></gold>"));
                List<Component> l = new ArrayList<>();
                l.add(TextUtil.parse("<dark_gray>Entity: " + opt.entityKey + "</dark_gray>"));
                l.add(Component.empty());
                l.add(TextUtil.parse("<yellow>Click to configure drop »</yellow>"));
                meta.lore(l);
                icon.setItemMeta(meta);
            }

            base.getCanvas().setButton(slot, CanvasButton.of(icon, event -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                String ruleId = itemDef.getId() + "_" + opt.entityKey.toLowerCase(Locale.ROOT) + "_" + (System.currentTimeMillis() % 10000);
                MobDropRule newRule = new MobDropRule(ruleId, "synth:" + itemDef.getId(), opt.entityKey, 0.15, 0.02, 1, 1, true, null, null);
                new MobDropConfigGUI(plugin, itemDef, newRule, onBack).open(player);
            }));
        }

        // Search Button (Slot 47)
        ItemStack searchBtn = new ItemStack(Material.COMPASS);
        ItemMeta sMeta = searchBtn.getItemMeta();
        if (sMeta != null) {
            sMeta.displayName(TextUtil.parse("<aqua><bold>Search Mobs</bold></aqua>"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<gray>Filter: <white>" + (filter.isEmpty() ? "None (All Mobs)" : filter) + "</white></gray>"));
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
                        TextUtil.parse("<gray>Type mob name or keyword to filter (e.g. <yellow>zombie</yellow>, <yellow>dragon</yellow>):</gray>"),
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

    private List<MobOption> getAvailableMobs() {
        List<MobOption> list = new ArrayList<>();

        // Generic Categories at the top
        list.add(new MobOption("All Hostile Mobs", "ALL_HOSTILE", Material.ZOMBIE_HEAD));
        list.add(new MobOption("All Passive Mobs", "ALL_PASSIVE", Material.PIG_SPAWN_EGG));
        list.add(new MobOption("Any Living Entity", "ANY", Material.NETHER_STAR));

        // Bosses
        list.add(new MobOption("Ender Dragon", "ENDER_DRAGON", Material.DRAGON_HEAD));
        list.add(new MobOption("Wither", "WITHER", Material.WITHER_SKELETON_SKULL));

        // Auto-discover all living entities with spawn eggs
        List<Material> eggs = Arrays.stream(Material.values())
                .filter(m -> m.name().endsWith("_SPAWN_EGG"))
                .sorted(Comparator.comparing(Material::name))
                .toList();

        for (Material m : eggs) {
            String mobName = m.name().replace("_SPAWN_EGG", "");
            String pretty = Arrays.stream(mobName.split("_"))
                    .map(word -> word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1).toLowerCase(Locale.ROOT))
                    .collect(Collectors.joining(" "));
            list.add(new MobOption(pretty, mobName, m));
        }

        return list;
    }

    private record MobOption(String name, String entityKey, Material icon) {}
}

