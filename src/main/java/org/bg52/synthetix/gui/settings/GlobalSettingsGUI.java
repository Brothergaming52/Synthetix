package org.bg52.synthetix.gui.settings;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.gui.Canvas;
import org.bg52.synthetix.gui.CanvasButton;
import org.bg52.synthetix.settings.GlobalFeature;
import org.bg52.synthetix.settings.GlobalSettingsManager;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Interactive in-game Global Settings Canvas.
 * Provides 1-click toggles for all core subsystems, master enable/disable controls,
 * and immediate reactive feedback.
 */
public class GlobalSettingsGUI {

    private final SynthetixPlugin plugin;
    private final Runnable onBack;

    public GlobalSettingsGUI(SynthetixPlugin plugin) {
        this(plugin, null);
    }

    public GlobalSettingsGUI(SynthetixPlugin plugin, Runnable onBack) {
        this.plugin = plugin;
        this.onBack = onBack;
    }

    public void open(Player player) {
        GlobalSettingsManager mgr = plugin.getSettingsManager();
        Canvas canvas = new Canvas(TextUtil.parse("<dark_gray>[<gradient:#ff0080:#7928ca>Synthetix</gradient>]</dark_gray> <gray>Global Feature Settings</gray>"), 5);

        // Frame top and bottom borders
        ItemStack darkGlass = Canvas.createBorderItem(Material.BLACK_STAINED_GLASS_PANE, Component.empty());
        ItemStack grayGlass = Canvas.createBorderItem(Material.GRAY_STAINED_GLASS_PANE, Component.empty());

        for (int c = 0; c < 9; c++) {
            canvas.setButton(0, c, CanvasButton.of(c == 0 || c == 8 ? darkGlass : grayGlass));
            canvas.setButton(4, c, CanvasButton.of(c == 0 || c == 8 ? darkGlass : grayGlass));
        }

        // Side borders
        for (int r = 1; r < 4; r++) {
            canvas.setButton(r, 0, CanvasButton.of(grayGlass));
            canvas.setButton(r, 8, CanvasButton.of(grayGlass));
        }

        // Feature Toggle Buttons
        for (GlobalFeature f : GlobalFeature.values()) {
            boolean enabled = mgr.isFeatureEnabled(f);
            ItemStack icon = new ItemStack(f.getIcon());
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                if (enabled) {
                    meta.displayName(TextUtil.parse("<green><bold>✔ " + f.getDisplayName() + "</bold></green>"));
                    List<Component> lore = new ArrayList<>();
                    lore.add(TextUtil.parse("<dark_gray>Feature ID: <white>" + f.getId() + "</white></dark_gray>"));
                    lore.add(TextUtil.parse("<gray>Status: </gray><green><bold>[ENABLED]</bold></green>"));
                    lore.add(Component.empty());
                    lore.add(TextUtil.parse("<gray>" + f.getDescription() + "</gray>"));
                    lore.add(Component.empty());
                    lore.add(TextUtil.parse("<yellow>Click to <red>Disable</red> this feature »</yellow>"));
                    meta.lore(lore);
                    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
                } else {
                    meta.displayName(TextUtil.parse("<red><bold>✖ " + f.getDisplayName() + "</bold></red>"));
                    List<Component> lore = new ArrayList<>();
                    lore.add(TextUtil.parse("<dark_gray>Feature ID: <white>" + f.getId() + "</white></dark_gray>"));
                    lore.add(TextUtil.parse("<gray>Status: </gray><red><bold>[DISABLED]</bold></red>"));
                    lore.add(Component.empty());
                    lore.add(TextUtil.parse("<gray>" + f.getDescription() + "</gray>"));
                    lore.add(Component.empty());
                    lore.add(TextUtil.parse("<yellow>Click to <green>Enable</green> this feature »</yellow>"));
                    meta.lore(lore);
                    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
                }
                icon.setItemMeta(meta);
            }

            canvas.setButton(f.getSlot(), CanvasButton.of(icon, event -> {
                boolean newState = mgr.toggleFeature(f.getId());
                player.playSound(player.getLocation(), newState ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, newState ? 1.5f : 0.6f);
                player.sendMessage(TextUtil.parse(newState
                        ? "<green>[Synthetix] Feature <white>" + f.getDisplayName() + "</white> is now <bold>ENABLED</bold>.</green>"
                        : "<yellow>[Synthetix] Feature <white>" + f.getDisplayName() + "</white> is now <bold>DISABLED</bold>.</yellow>"));
                open(player);
            }));
        }

        // Master Control Button at Slot 40 (Column 4, Bottom Center)
        ItemStack masterBtn = new ItemStack(Material.BEACON);
        ItemMeta mMeta = masterBtn.getItemMeta();
        if (mMeta != null) {
            mMeta.displayName(TextUtil.parse("<gold><bold>⚡ Master Global Controls</bold></gold>"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<gray>Quickly toggle all features across server:</gray>"));
            l.add(Component.empty());
            l.add(TextUtil.parse("<yellow>Left-Click:</yellow> <green>Enable All Features</green>"));
            l.add(TextUtil.parse("<yellow>Right-Click:</yellow> <red>Disable All Features</red>"));
            l.add(TextUtil.parse("<yellow>Shift-Click:</yellow> <aqua>Restore System Defaults</aqua>"));
            mMeta.lore(l);
            masterBtn.setItemMeta(mMeta);
        }
        canvas.setButton(40, CanvasButton.of(masterBtn, event -> {
            if (event.isShiftClick()) {
                mgr.resetToDefaults();
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
                player.sendMessage(TextUtil.parse("<aqua>[Synthetix] Reset all features to default settings.</aqua>"));
            } else if (event.isLeftClick()) {
                mgr.setAllFeatures(true);
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.5f);
                player.sendMessage(TextUtil.parse("<green>[Synthetix] All features have been ENABLED.</green>"));
            } else if (event.isRightClick()) {
                mgr.setAllFeatures(false);
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.8f);
                player.sendMessage(TextUtil.parse("<yellow>[Synthetix] All features have been DISABLED.</yellow>"));
            }
            open(player);
        }));

        // Back / Close button at Slot 36 (Bottom Left)
        ItemStack backBtn = new ItemStack(Material.ARROW);
        ItemMeta bMeta = backBtn.getItemMeta();
        if (bMeta != null) {
            bMeta.displayName(TextUtil.parse(onBack != null ? "<yellow><bold>« Back</bold></yellow>" : "<red><bold>✖ Close</bold></red>"));
            backBtn.setItemMeta(bMeta);
        }
        canvas.setButton(36, CanvasButton.of(backBtn, event -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            if (onBack != null) {
                onBack.run();
            } else {
                player.closeInventory();
            }
        }));

        canvas.open(player);
    }
}
