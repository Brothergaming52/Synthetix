package org.bg52.synthetix.gui.editor.distribution;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.distribution.TradeRule;
import org.bg52.synthetix.gui.CanvasButton;
import org.bg52.synthetix.gui.editor.BaseEditorCanvas;
import org.bg52.synthetix.item.ItemDefinition;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Symmetrical profession selector for configuring villager & trader merchant offers.
 */
public class ProfessionSelectorGUI {

    private final SynthetixPlugin plugin;
    private final ItemDefinition itemDef;
    private final Runnable onBack;

    public ProfessionSelectorGUI(SynthetixPlugin plugin, ItemDefinition itemDef, Runnable onBack) {
        this.plugin = plugin;
        this.itemDef = itemDef;
        this.onBack = onBack;
    }

    public void open(Player player) {
        BaseEditorCanvas base = new BaseEditorCanvas(plugin, itemDef,
                TextUtil.parse("<dark_gray>Select Merchant Profession</dark_gray>"), 5);
        base.setupSideBorders();

        // 15 Professions arranged neatly in 3 rows:
        // Row 1: slots 10, 11, 12, 13, 14, 15, 16 (7 items)
        // Row 2: slots 19, 20, 21, 22, 23, 24, 25 (7 items)
        // Row 3: slot 31 (Any/Wildcard)

        ProfessionOption[] options = {
                new ProfessionOption("Armorer", "ARMORER", Material.BLAST_FURNACE),
                new ProfessionOption("Butcher", "BUTCHER", Material.SMOKER),
                new ProfessionOption("Cartographer", "CARTOGRAPHER", Material.CARTOGRAPHY_TABLE),
                new ProfessionOption("Cleric", "CLERIC", Material.BREWING_STAND),
                new ProfessionOption("Farmer", "FARMER", Material.COMPOSTER),
                new ProfessionOption("Fisherman", "FISHERMAN", Material.BARREL),
                new ProfessionOption("Fletcher", "FLETCHER", Material.FLETCHING_TABLE),

                new ProfessionOption("Leatherworker", "LEATHERWORKER", Material.CAULDRON),
                new ProfessionOption("Librarian", "LIBRARIAN", Material.LECTERN),
                new ProfessionOption("Mason", "MASON", Material.STONECUTTER),
                new ProfessionOption("Shepherd", "SHEPHERD", Material.LOOM),
                new ProfessionOption("Toolsmith", "TOOLSMITH", Material.SMITHING_TABLE),
                new ProfessionOption("Weaponsmith", "WEAPONSMITH", Material.GRINDSTONE),
                new ProfessionOption("Wandering Trader", "WANDERING_TRADER", Material.LEAD),

                new ProfessionOption("Any Villager", "ANY", Material.NETHER_STAR)
        };

        int[] slots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                31
        };

        for (int i = 0; i < options.length; i++) {
            ProfessionOption opt = options[i];
            int slot = slots[i];

            ItemStack icon = new ItemStack(opt.icon);
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.displayName(TextUtil.parse("<green><bold>" + opt.displayName + "</bold></green>"));
                List<Component> l = new ArrayList<>();
                l.add(TextUtil.parse("<dark_gray>Profession: " + opt.professionKey + "</dark_gray>"));
                l.add(Component.empty());
                l.add(TextUtil.parse("<yellow>Click to build trade offer »</yellow>"));
                meta.lore(l);
                icon.setItemMeta(meta);
            }

            base.getCanvas().setButton(slot, CanvasButton.of(icon, event -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                String ruleId = itemDef.getId() + "_" + opt.professionKey.toLowerCase(Locale.ROOT) + "_" + (System.currentTimeMillis() % 10000);
                TradeRule newRule = new TradeRule(ruleId, opt.professionKey, 2,
                        "EMERALD", 16, null, 0, "synth:" + itemDef.getId(), 1, 12, 10, 0.05f, 0.50);
                new TradeBuilderGUI(plugin, itemDef, newRule, onBack).open(player);
            }));
        }

        base.setCenteredBackButton(onBack);
        base.open(player);
    }

    private record ProfessionOption(String displayName, String professionKey, Material icon) {}
}

