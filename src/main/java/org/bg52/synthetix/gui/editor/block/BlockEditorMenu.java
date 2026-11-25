package org.bg52.synthetix.gui.editor.block;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.block.CustomBlockDefinition;
import org.bg52.synthetix.gui.Canvas;
import org.bg52.synthetix.gui.CanvasButton;
import org.bg52.synthetix.gui.editor.distribution.LootTableEditorGUI;
import org.bg52.synthetix.gui.editor.distribution.MobDropEditorGUI;
import org.bg52.synthetix.gui.editor.recipe.RecipeEditorGUI;
import org.bg52.synthetix.gui.recipe.RecipeViewerGUI;
import org.bg52.synthetix.util.MaterialUtil;
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
 * Master Hub Editor GUI for Custom Blocks.
 * Clean, streamlined 6-row design providing access to:
 * - Row 0: Live Block Item Preview, Overview Badge, and JEI Recipe Lookup
 * - Row 2: Physical & Visual Foundations (Base Material & Proxy, Model Visuals, Physics, Audio, Mining)
 * - Row 3: Harvesting, Environmental & World Gen (Drops & Loot, Blast Resistance & Fire, Natural World Gen)
 * - Row 4: Crafting Recipes & Loot Distribution (Crafting Recipes, Mob Drops, Loot Tables)
 * - Row 5: Navigation (Block Browser, Export Bundle, Save Custom Block)
 */
public class BlockEditorMenu {

    private final SynthetixPlugin plugin;
    private final CustomBlockDefinition blockDef;

    public BlockEditorMenu(SynthetixPlugin plugin, CustomBlockDefinition blockDef) {
        this.plugin = plugin;
        this.blockDef = blockDef;
    }

    public static void open(Player player, SynthetixPlugin plugin, CustomBlockDefinition blockDef) {
        new BlockEditorMenu(plugin, blockDef).open(player);
    }

    public void open(Player player) {
        Component title = TextUtil.parse("<dark_gray>Block: </dark_gray>" + blockDef.getDisplayName());
        Canvas canvas = new Canvas(title, 6);

        // Standard dark & gray borders
        ItemStack darkGlass = Canvas.createBorderItem(Material.BLACK_STAINED_GLASS_PANE, Component.empty());
        ItemStack grayGlass = Canvas.createBorderItem(Material.GRAY_STAINED_GLASS_PANE, Component.empty());

        for (int c = 0; c < 9; c++) {
            if (c != 4) canvas.setButton(0, c, CanvasButton.of(c == 0 || c == 8 ? darkGlass : grayGlass));
            canvas.setButton(5, c, CanvasButton.of(darkGlass));
        }
        for (int r = 1; r < 5; r++) {
            canvas.setButton(r, 0, CanvasButton.of(darkGlass));
            canvas.setButton(r, 8, CanvasButton.of(darkGlass));
        }

        // ---------------------------------------------------------------------
        // ROW 0: Header Shortcuts & Live Preview
        // ---------------------------------------------------------------------
        // Slot 0: Block Overview Badge
        ItemStack badge = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta bMeta = badge.getItemMeta();
        if (bMeta != null) {
            bMeta.displayName(TextUtil.parse("<gold><bold>Custom Block Overview</bold></gold>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<dark_gray>ID: " + blockDef.getId() + "</dark_gray>"));
            lore.add(TextUtil.parse("<gray>Display:</gray> " + blockDef.getDisplayName()));
            lore.add(TextUtil.parse("<gray>Base Proxy:</gray> <aqua>" + blockDef.getBaseMaterial().name() + "</aqua>"));
            lore.add(TextUtil.parse("<gray>Item Material:</gray> <white>" + blockDef.getItemMaterial().name() + "</white>"));
            if (blockDef.getCustomModelData() > 0) {
                lore.add(TextUtil.parse("<gray>CustomModelData:</gray> <yellow>" + blockDef.getCustomModelData() + "</yellow>"));
            }
            if (blockDef.getItemModel() != null) {
                lore.add(TextUtil.parse("<gray>Item Model:</gray> <aqua>" + blockDef.getItemModel() + "</aqua>"));
            }
            lore.add(TextUtil.parse("<gray>Rendering:</gray> <green>" + blockDef.getRenderingMethod().name() + "</green>"));
            lore.add(TextUtil.parse("<gray>Hardness:</gray> <white>" + blockDef.getMining().getHardness() + " (Blast: " + blockDef.getMining().getBlastResistance() + ")</white>"));
            lore.add(TextUtil.parse("<gray>World Gen:</gray> " + (blockDef.getWorldGen().isEnabled() ? "<green>Active</green>" : "<red>Disabled</red>")));
            bMeta.lore(lore);
            badge.setItemMeta(bMeta);
        }
        canvas.setButton(0, CanvasButton.of(badge));

        // Slot 4: Live Block Item Preview (Left 1x, Shift-Left 64x, Right-Click: Edit Item)
        ItemStack previewStack = blockDef.buildBlockItemStack(1);
        ItemMeta pMeta = previewStack.getItemMeta();
        if (pMeta != null) {
            List<Component> pLore = pMeta.lore() != null ? new ArrayList<>(pMeta.lore()) : new ArrayList<>();
            pLore.add(Component.empty());
            pLore.add(TextUtil.parse("<dark_gray>────────────────────</dark_gray>"));
            pLore.add(TextUtil.parse("<yellow>Left-Click: Get 1x in inventory</yellow>"));
            pLore.add(TextUtil.parse("<yellow>Shift-Left: Get 64x in inventory</yellow>"));
            pLore.add(TextUtil.parse("<aqua>Right-Click: Edit Item Appearance & Components »</aqua>"));
            pMeta.lore(pLore);
            previewStack.setItemMeta(pMeta);
        }
        canvas.setButton(4, CanvasButton.of(previewStack, e -> {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.2f);
            if (e.isRightClick()) {
                var itemDef = blockDef.toItemDefinition();
                plugin.getItemRegistry().register(itemDef);
                org.bg52.synthetix.gui.editor.MainEditorMenu.open(player, plugin, itemDef);
                return;
            }
            int amount = e.isShiftClick() ? 64 : 1;
            player.getInventory().addItem(blockDef.buildBlockItemStack(amount));
            plugin.getLanguageManager().send(player, "block-editor.preview-added", "%amount%", String.valueOf(amount), "%block%", blockDef.getId());
        }));

        // Slot 8: Direct JEI Recipe Viewer Shortcut
        ItemStack recipeBtn = new ItemStack(Material.CRAFTING_TABLE);
        ItemMeta rMeta = recipeBtn.getItemMeta();
        if (rMeta != null) {
            rMeta.displayName(TextUtil.parse("<gold><bold>View Block Recipes (JEI)</bold></gold>"));
            List<Component> rLore = new ArrayList<>();
            rLore.add(TextUtil.parse("<gray>Inspect crafting, smelting, or forging</gray>"));
            rLore.add(TextUtil.parse("<gray>recipes to craft this custom block.</gray>"));
            rLore.add(Component.empty());
            rLore.add(TextUtil.parse("<yellow>Click to view recipes »</yellow>"));
            rMeta.lore(rLore);
            recipeBtn.setItemMeta(rMeta);
        }
        canvas.setButton(8, CanvasButton.of(recipeBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            RecipeViewerGUI.open(player, plugin, blockDef.getId(), () -> open(player));
        }));

        // ---------------------------------------------------------------------
        // ROW 2: Physical & Visual Foundations (Slots 20, 21, 22, 23, 24)
        // ---------------------------------------------------------------------
        // Slot 20: Base Material & Physical Proxy
        ItemStack baseMatBtn = createBtn(blockDef.getBaseMaterial() != Material.AIR ? blockDef.getBaseMaterial() : Material.BARRIER,
                "<gold><bold>Base Material & Proxy</bold></gold>",
                "<gray>Current Proxy:</gray> <aqua>" + blockDef.getBaseMaterial().name() + "</aqua>",
                "<gray>Held Item Mat:</gray> <white>" + blockDef.getItemMaterial().name() + "</white>",
                "",
                "<yellow>Click to select vanilla physical proxy block »</yellow>");
        canvas.setButton(20, CanvasButton.of(baseMatBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            BlockBaseMaterialGUI.open(player, plugin, blockDef, () -> open(player));
        }));

        // Slot 21: Model, CMD & Display Visuals
        ItemStack visualBtn = createBtn(Material.ITEM_FRAME,
                "<gold><bold>Model & Display Visuals</bold></gold>",
                "<gray>CustomModelData:</gray> <yellow>" + (blockDef.getCustomModelData() > 0 ? blockDef.getCustomModelData() : "None") + "</yellow>",
                "<gray>Method:</gray> <green>" + blockDef.getRenderingMethod().name() + "</green>",
                "<gray>Scale:</gray> <white>[" + blockDef.getDisplayScale().x + ", " + blockDef.getDisplayScale().y + ", " + blockDef.getDisplayScale().z + "]</white>",
                "",
                "<yellow>Click to configure 3D transform & visuals »</yellow>");
        canvas.setButton(21, CanvasButton.of(visualBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            BlockVisualsGUI.open(player, plugin, blockDef, () -> open(player));
        }));

        // Slot 22: Placement, Facing & Collisions
        ItemStack physicsBtn = createBtn(Material.COMPASS,
                "<gold><bold>Physics & Collisions</bold></gold>",
                "<gray>Shape:</gray> <aqua>" + blockDef.getPhysics().getCollisionShape().name() + "</aqua>",
                "<gray>Friction:</gray> <white>" + blockDef.getPhysics().getFriction() + "</white>",
                "<gray>Waterloggable:</gray> " + (blockDef.getPhysics().isWaterloggable() ? "<green>Yes</green>" : "<red>No</red>"),
                "",
                "<yellow>Click to configure collision & physics »</yellow>");
        canvas.setButton(22, CanvasButton.of(physicsBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            BlockPhysicsGUI.open(player, plugin, blockDef, () -> open(player));
        }));

        // Slot 23: Sounds & Shatter Particles
        ItemStack soundBtn = createBtn(Material.NOTE_BLOCK,
                "<gold><bold>Audio & Shatter Particles</bold></gold>",
                "<gray>Break Sound:</gray> <white>" + blockDef.getMining().getBreakSound() + "</white>",
                "<gray>Place Sound:</gray> <white>" + blockDef.getMining().getPlaceSound() + "</white>",
                "<gray>Particles:</gray> <green>Authentic ITEM Texture Shatter</green>",
                "",
                "<yellow>Click to configure custom block sounds »</yellow>");
        canvas.setButton(23, CanvasButton.of(soundBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            BlockSoundsGUI.open(player, plugin, blockDef, () -> open(player));
        }));

        // Slot 24: Mining, Hardness & Tool Tiers
        ItemStack miningBtn = createBtn(Material.DIAMOND_PICKAXE,
                "<gold><bold>Mining & Hardness</bold></gold>",
                "<gray>Hardness:</gray> <white>" + blockDef.getMining().getHardness() + "</white>",
                "<gray>Required Tool:</gray> <aqua>" + blockDef.getMining().getRequiredTool().name() + "</aqua>",
                "<gray>Min Tier:</gray> <light_purple>" + blockDef.getMining().getMinimumTier().name() + "</light_purple>",
                "",
                "<yellow>Click to adjust hardness, tools & mining speed »</yellow>");
        canvas.setButton(24, CanvasButton.of(miningBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            BlockMiningGUI.open(player, plugin, blockDef, () -> open(player));
        }));

        // ---------------------------------------------------------------------
        // ROW 3: Harvesting, Environment & World Generation (Slots 29, 31, 33)
        // ---------------------------------------------------------------------
        // Slot 29: Drops & Loot Rules
        ItemStack dropsBtn = createBtn(Material.CHEST,
                "<gold><bold>Block Drops & Loot</bold></gold>",
                "<gray>Configured Drops:</gray> <white>" + blockDef.getMining().getDrops().size() + "</white>",
                "<gray>Exp Drop:</gray> <white>" + blockDef.getMining().getExpDrop() + " XP</white>",
                "",
                "<yellow>Click to manage harvesting drops & fortune »</yellow>");
        canvas.setButton(29, CanvasButton.of(dropsBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            BlockDropsGUI.open(player, plugin, blockDef, () -> open(player));
        }));

        // Slot 31: Blast Resistance & Fire
        ItemStack blastBtn = createBtn(Material.TNT,
                "<gold><bold>Blast Resistance & Fire</bold></gold>",
                "<gray>Blast Resistance:</gray> <white>" + blockDef.getMining().getBlastResistance() + "</white>",
                "<gray>Flammable:</gray> " + (blockDef.getInteraction().isFlammable() ? "<green>Yes</green>" : "<red>No</red>"),
                "<gray>Piston Pushable:</gray> " + (blockDef.getInteraction().isPistonPushable() ? "<green>Yes</green>" : "<red>No</red>"),
                "",
                "<yellow>Click to adjust blast & environmental reactions »</yellow>");
        canvas.setButton(31, CanvasButton.of(blastBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            BlockInteractionGUI.open(player, plugin, blockDef, () -> open(player));
        }));

        // Slot 33: Natural World Generation & Ores
        ItemStack genBtn = createBtn(Material.RAW_GOLD,
                "<gold><bold>Natural World Gen (Ores)</bold></gold>",
                "<gray>Status:</gray> " + (blockDef.getWorldGen().isEnabled() ? "<green>Enabled</green>" : "<red>Disabled</red>"),
                "<gray>Height:</gray> <white>Y " + blockDef.getWorldGen().getMinHeight() + " to Y " + blockDef.getWorldGen().getMaxHeight() + "</white>",
                "<gray>Vein Size:</gray> <white>" + blockDef.getWorldGen().getVeinSize() + " (Veins: " + blockDef.getWorldGen().getVeinsPerChunk() + ")</white>",
                "",
                "<yellow>Click to configure natural ore world generation »</yellow>");
        canvas.setButton(33, CanvasButton.of(genBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            BlockWorldGenGUI.open(player, plugin, blockDef, () -> open(player));
        }));

        // ---------------------------------------------------------------------
        // ROW 4: Crafting Recipes, Mob Drops & Distribution (Slots 38, 40, 42)
        // ---------------------------------------------------------------------
        // Slot 38: Crafting & Cooking Recipes
        ItemStack recipesBtn = createBtn(Material.CRAFTING_TABLE,
                "<gold><bold>Crafting & Cooking Recipes</bold></gold>",
                "<gray>Configure shaped, shapeless, furnace,</gray>",
                "<gray>and forge recipes that craft this block.</gray>",
                "",
                "<yellow>Click to manage recipes »</yellow>");
        canvas.setButton(38, CanvasButton.of(recipesBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            var itemDef = blockDef.toItemDefinition();
            plugin.getItemRegistry().register(itemDef);
            new RecipeEditorGUI(plugin).open(player, itemDef, () -> open(player));
        }));

        // Slot 40: Mob Drops
        ItemStack mobDropsBtn = createBtn(Material.ROTTEN_FLESH,
                "<gold><bold>Mob Drops Distribution</bold></gold>",
                "<gray>Configure which mobs drop this custom</gray>",
                "<gray>block upon death, drop chances & looting.</gray>",
                "",
                "<yellow>Click to manage mob drops »</yellow>");
        canvas.setButton(40, CanvasButton.of(mobDropsBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            var itemDef = blockDef.toItemDefinition();
            plugin.getItemRegistry().register(itemDef);
            new MobDropEditorGUI(plugin, itemDef, () -> open(player)).open(player);
        }));

        // Slot 42: Dungeon Loot Tables
        ItemStack lootBtn = createBtn(Material.CHEST_MINECART,
                "<gold><bold>Dungeon Loot Table Injections</bold></gold>",
                "<gray>Inject this block into vanilla dungeon chests,</gray>",
                "<gray>mineshaft carts, desert pyramids & trial chambers.</gray>",
                "",
                "<yellow>Click to manage loot table spawns »</yellow>");
        canvas.setButton(42, CanvasButton.of(lootBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            var itemDef = blockDef.toItemDefinition();
            plugin.getItemRegistry().register(itemDef);
            new LootTableEditorGUI(plugin, itemDef, () -> open(player)).open(player);
        }));

        // ---------------------------------------------------------------------
        // ROW 5: Navigation & Fast Actions Footer
        // ---------------------------------------------------------------------
        // Slot 45: Return to Block Browser
        ItemStack browserBtn = createBtn(Material.COMPASS,
                "<yellow><bold>« Block Browser</bold></yellow>",
                "<gray>Browse and inspect all custom blocks.</gray>",
                "",
                "<yellow>Click to open block browser »</yellow>");
        canvas.setButton(45, CanvasButton.of(browserBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            BlockBrowserGUI.open(player, plugin);
        }));

        // Slot 47: Export Block Pack
        ItemStack exportBtn = createBtn(Material.BUNDLE,
                "<aqua><bold>Export Block (.synthpack)</bold></aqua>",
                "<gray>Package this custom block into a standalone</gray>",
                "<gray>.synthpack bundle in plugins/Synthetix/exports/.</gray>",
                "",
                "<yellow>Click to export block bundle »</yellow>");
        canvas.setButton(47, CanvasButton.of(exportBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            File exp = plugin.getExportManager().exportBundle(List.of(blockDef.getId()), blockDef.getId() + "_block.synthpack");
            if (exp != null && exp.exists()) {
                plugin.getLanguageManager().send(player, "block-editor.export-success", "%file%", exp.getName());
            } else {
                plugin.getLanguageManager().send(player, "block-editor.export-failed");
            }
        }));

        // Slot 53: Save Button
        ItemStack saveBtn = createBtn(Material.EMERALD,
                "<green><bold>Save Custom Block</bold></green>",
                "<gray>Save all physical, visual, and world</gray>",
                "<gray>generation properties to disk.</gray>",
                "",
                "<yellow>Click to save block »</yellow>");
        canvas.setButton(53, CanvasButton.of(saveBtn, e -> {
            plugin.getCustomBlockRegistry().save(blockDef);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f);
            plugin.getLanguageManager().send(player, "block-editor.save-success", "%block%", blockDef.getId());
            player.closeInventory();
        }));

        canvas.open(player);
    }

    private ItemStack createBtn(Material mat, String name, String... loreLines) {
        Material safeMat = MaterialUtil.toItemMaterial(mat);
        ItemStack item = new ItemStack(safeMat);
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
