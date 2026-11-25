package org.bg52.synthetix.gui.editor;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.api.editor.ItemEditorCategory;
import org.bg52.synthetix.gui.CanvasButton;
import org.bg52.synthetix.gui.browser.ItemBrowserGUI;
import org.bg52.synthetix.gui.recipe.RecipeViewerGUI;
import org.bg52.synthetix.gui.set.SetListGUI;
import org.bg52.synthetix.item.ItemDefinition;
import org.bg52.synthetix.lang.LanguageManager;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Main category navigation canvas of the in-game item editor.
 * Formatted 6-row Master Hub providing structured access to:
 * - Row 0: Live Item Preview, Item Overview Badge, and JEI Recipe Lookup shortcut
 * - Row 2: Core Item Properties (Identity, Components, Attributes, Enchantments, Abilities)
 * - Row 3: Systems & World Integration (Food, Tags, Recipes, Drops, Equipment Sets)
 * - Row 4: Downstream Plugin Categories (CuriosPaper, etc.)
 * - Row 5: Navigation & Fast Actions (Item Browser, Export Pack, Search, Save)
 */
public class MainEditorMenu {

    private final SynthetixPlugin plugin;
    private final ItemDefinition itemDef;
    private final LanguageManager lang;
    private int page = 0;

    public MainEditorMenu(SynthetixPlugin plugin, ItemDefinition itemDef) {
        this.plugin = plugin;
        this.itemDef = itemDef;
        this.lang = plugin.getLanguageManager();
    }

    public static void open(Player player, SynthetixPlugin plugin, ItemDefinition itemDef) {
        new MainEditorMenu(plugin, itemDef).open(player, 0);
    }

    public void open(Player player, int page) {
        this.page = page;

        Component title = TextUtil.parse(lang.get("editor.title") + " <gold>" + itemDef.getDisplayName() + "</gold>");
        BaseEditorCanvas base = new BaseEditorCanvas(plugin, itemDef, title, 6);
        base.setupSideBorders();

        // ---------------------------------------------------------------------
        // ROW 0: Header Shortcuts & Live Preview
        // ---------------------------------------------------------------------
        // Slot 0: Metadata Badge
        ItemStack badge = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta bMeta = badge.getItemMeta();
        if (bMeta != null) {
            bMeta.displayName(TextUtil.parse("<gold><bold>Item Overview</bold></gold>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<dark_gray>ID: " + itemDef.getId() + "</dark_gray>"));
            lore.add(TextUtil.parse("<gray>Display:</gray> " + itemDef.getDisplayName()));
            lore.add(TextUtil.parse("<gray>Base Material:</gray> <aqua>" + itemDef.getMaterial().name() + "</aqua>"));
            lore.add(TextUtil.parse("<gray>CustomModelData:</gray> <white>" + (itemDef.getCustomModelData() != null ? itemDef.getCustomModelData() : "None") + "</white>"));
            lore.add(TextUtil.parse("<gray>Attributes:</gray> <white>" + itemDef.getAttributes().size() + "</white>"));
            lore.add(TextUtil.parse("<gray>Enchantments:</gray> <white>" + itemDef.getEnchants().size() + "</white>"));
            int abilityCount = itemDef.getAbilities() != null ? itemDef.getAbilities().size() : 0;
            lore.add(TextUtil.parse("<gray>Abilities:</gray> <white>" + abilityCount + "</white>"));
            boolean auraOn = itemDef.getAura() != null && itemDef.getAura().isEnabled();
            lore.add(TextUtil.parse("<gray>Aura:</gray> " + (auraOn ? "<green>" + itemDef.getAura().getShape().name() + "</green>" : "<red>Off</red>")));
            bMeta.lore(lore);
            badge.setItemMeta(bMeta);
        }
        base.getCanvas().setButton(0, CanvasButton.of(badge));

        // Slot 4: Live Item Preview Give Action (Left 1x, Shift-Left 64x)
        ItemStack previewStack = itemDef.buildItemStack();
        ItemMeta pMeta = previewStack.getItemMeta();
        if (pMeta != null) {
            List<Component> pLore = pMeta.lore() != null ? new ArrayList<>(pMeta.lore()) : new ArrayList<>();
            pLore.add(Component.empty());
            pLore.add(TextUtil.parse("<dark_gray>────────────────────</dark_gray>"));
            pLore.add(TextUtil.parse("<yellow>Left-Click: Get 1x in inventory</yellow>"));
            pLore.add(TextUtil.parse("<yellow>Shift-Left: Get 64x in inventory</yellow>"));
            pMeta.lore(pLore);
            previewStack.setItemMeta(pMeta);
        }
        base.getCanvas().setButton(4, CanvasButton.of(previewStack, e -> {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.2f);
            ItemStack give = itemDef.buildItemStack();
            give.setAmount(e.isShiftClick() ? Math.min(64, give.getMaxStackSize() > 0 ? give.getMaxStackSize() * 4 : 64) : 1);
            player.getInventory().addItem(give);
            lang.send(player, "editor.preview-gave", "%item%", itemDef.getId());
        }));

        // Slot 8: Direct JEI Recipe Viewer Shortcut
        ItemStack recipeBtn = new ItemStack(Material.CRAFTING_TABLE);
        ItemMeta rMeta = recipeBtn.getItemMeta();
        if (rMeta != null) {
            rMeta.displayName(TextUtil.parse("<gold><bold>View Recipes (JEI)</bold></gold>"));
            List<Component> rLore = new ArrayList<>();
            rLore.add(TextUtil.parse("<gray>Directly inspect how this item is crafted,</gray>"));
            rLore.add(TextUtil.parse("<gray>smelted, forged, or brewed.</gray>"));
            rLore.add(Component.empty());
            rLore.add(TextUtil.parse("<yellow>Click to open Recipe Viewer »</yellow>"));
            rMeta.lore(rLore);
            recipeBtn.setItemMeta(rMeta);
        }
        base.getCanvas().setButton(8, CanvasButton.of(recipeBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            RecipeViewerGUI.open(player, plugin, itemDef.getId(), () -> open(player, page));
        }));

        // ---------------------------------------------------------------------
        // ROW 2: Core Item Properties (Slots 20, 21, 22, 23, 24)
        // ---------------------------------------------------------------------
        placeCategory(base, player, "identity", 20);
        placeCategory(base, player, "components", 21);
        placeCategory(base, player, "attributes", 22);
        placeCategory(base, player, "enchantments", 23);
        placeCategory(base, player, "abilities", 24);

        // ---------------------------------------------------------------------
        // ROW 3: Systems & World Integration (Slots 29, 30, 31, 32, 33)
        // ---------------------------------------------------------------------
        placeCategory(base, player, "food", 29);
        placeCategory(base, player, "tags", 30);
        placeCategory(base, player, "recipes", 31);
        placeCategory(base, player, "distribution", 32);

        // Slot 33: Equipment Sets Shortcut
        ItemStack setBtn = new ItemStack(Material.ARMOR_STAND);
        ItemMeta sMeta = setBtn.getItemMeta();
        if (sMeta != null) {
            sMeta.displayName(TextUtil.parse("<gold><bold>Equipment Sets</bold></gold>"));
            List<Component> sLore = new ArrayList<>();
            sLore.add(TextUtil.parse("<gray>Configure set bonuses when wearing</gray>"));
            sLore.add(TextUtil.parse("<gray>matching armor sets and weapons.</gray>"));
            sLore.add(Component.empty());
            sLore.add(TextUtil.parse("<yellow>Click to manage Equipment Sets »</yellow>"));
            sMeta.lore(sLore);
            setBtn.setItemMeta(sMeta);
        }
        base.getCanvas().setButton(33, CanvasButton.of(setBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            SetListGUI.open(player, plugin);
        }));

        // ---------------------------------------------------------------------
        // ROW 4: Downstream Plugin Categories (CuriosPaper, etc.)
        // ---------------------------------------------------------------------
        List<ItemEditorCategory> pluginCategories = plugin.getEditorRegistry().getPluginCategories();
        int[] addonSlots = new int[]{38, 39, 40, 41, 42};
        for (int i = 0; i < pluginCategories.size() && i < addonSlots.length; i++) {
            ItemEditorCategory cat = pluginCategories.get(i);
            base.getCanvas().setButton(addonSlots[i], CanvasButton.of(cat.getIcon(itemDef), e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                cat.open(player, itemDef, () -> open(player, page));
            }));
        }

        // ---------------------------------------------------------------------
        // ROW 5: Navigation & Fast Actions Footer
        // ---------------------------------------------------------------------
        // Slot 45: Return to Item Browser
        ItemStack browserBtn = new ItemStack(Material.COMPASS);
        ItemMeta brMeta = browserBtn.getItemMeta();
        if (brMeta != null) {
            brMeta.displayName(TextUtil.parse("<yellow><bold>« Item Browser</bold></yellow>"));
            List<Component> brLore = new ArrayList<>();
            brLore.add(TextUtil.parse("<gray>Browse and search all custom items.</gray>"));
            brLore.add(Component.empty());
            brLore.add(TextUtil.parse("<yellow>Click to open browser »</yellow>"));
            brMeta.lore(brLore);
            browserBtn.setItemMeta(brMeta);
        }
        base.getCanvas().setButton(45, CanvasButton.of(browserBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            ItemBrowserGUI.open(player, plugin);
        }));

        // Slot 47: Export Single Item Archive
        ItemStack exportBtn = new ItemStack(Material.BUNDLE);
        ItemMeta exMeta = exportBtn.getItemMeta();
        if (exMeta != null) {
            exMeta.displayName(TextUtil.parse("<aqua><bold>Export Item File</bold></aqua>"));
            List<Component> exLore = new ArrayList<>();
            exLore.add(TextUtil.parse("<gray>Export this item definition to a standalone</gray>"));
            exLore.add(TextUtil.parse("<gray>.synthpack bundle in plugins/Synthetix/exports/.</gray>"));
            exLore.add(Component.empty());
            exLore.add(TextUtil.parse("<yellow>Click to export item »</yellow>"));
            exMeta.lore(exLore);
            exportBtn.setItemMeta(exMeta);
        }
        base.getCanvas().setButton(47, CanvasButton.of(exportBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            File exp = plugin.getExportManager().exportBundle(List.of(itemDef.getId()), itemDef.getId() + ".synthpack");
            if (exp != null && exp.exists()) {
                player.sendMessage(TextUtil.parse("<green>✓ Successfully exported to <yellow>" + exp.getName() + "</yellow>!</green>"));
            } else {
                player.sendMessage(TextUtil.parse("<red>Failed to export item bundle.</red>"));
            }
        }));

        // Slot 49: Search Custom Items
        ItemStack searchBtn = new ItemStack(Material.SPYGLASS);
        ItemMeta spMeta = searchBtn.getItemMeta();
        if (spMeta != null) {
            spMeta.displayName(TextUtil.parse("<gold><bold>Search Custom Items</bold></gold>"));
            List<Component> spLore = new ArrayList<>();
            spLore.add(TextUtil.parse("<gray>Filter and find items across your server.</gray>"));
            spLore.add(Component.empty());
            spLore.add(TextUtil.parse("<yellow>Click to type search keyword »</yellow>"));
            spMeta.lore(spLore);
            searchBtn.setItemMeta(spMeta);
        }
        base.getCanvas().setButton(49, CanvasButton.of(searchBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            plugin.getChatPromptManager().prompt(player,
                    TextUtil.parse("<gray>Enter keyword to search items (or press enter for all):</gray>"),
                    query -> ItemBrowserGUI.open(player, plugin, 0, query.trim()));
        }));

        // Slot 53: Save Button
        base.setSaveButton();

        base.open(player);
    }

    private void placeCategory(BaseEditorCanvas base, Player player, String categoryId, int slot) {
        ItemEditorCategory cat = plugin.getEditorRegistry().getCategory(categoryId);
        if (cat != null) {
            base.getCanvas().setButton(slot, CanvasButton.of(cat.getIcon(itemDef), e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                cat.open(player, itemDef, () -> open(player, page));
            }));
        }
    }
}
