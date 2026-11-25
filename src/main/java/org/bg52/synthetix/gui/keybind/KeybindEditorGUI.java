package org.bg52.synthetix.gui.keybind;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.gui.Canvas;
import org.bg52.synthetix.gui.CanvasButton;
import org.bg52.synthetix.keybind.CustomKeybind;
import org.bg52.synthetix.keybind.KeybindType;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Visual in-game editor for configuring a custom keybind's trigger, slot requirement,
 * command, cooldown, and execution mode.
 */
public class KeybindEditorGUI {

    private final SynthetixPlugin plugin;
    private final CustomKeybind keybind;
    private final Runnable returnCallback;

    public KeybindEditorGUI(SynthetixPlugin plugin, CustomKeybind keybind, Runnable returnCallback) {
        this.plugin = plugin;
        this.keybind = keybind;
        this.returnCallback = returnCallback;
    }

    public static void open(Player player, SynthetixPlugin plugin, CustomKeybind keybind, Runnable returnCallback) {
        new KeybindEditorGUI(plugin, keybind, returnCallback).open(player);
    }

    public void open(Player player) {
        Canvas canvas = new Canvas(TextUtil.parse("<dark_gray>[<gradient:#ff0080:#7928ca>Keybind</gradient>]</dark_gray> <gray>Edit: " + keybind.getId() + "</gray>"), 5);

        // Frame
        ItemStack darkGlass = Canvas.createBorderItem(Material.BLACK_STAINED_GLASS_PANE, Component.empty());
        for (int c = 0; c < 9; c++) {
            canvas.setButton(0, c, CanvasButton.of(darkGlass));
            canvas.setButton(4, c, CanvasButton.of(darkGlass));
        }

        // Slot 10: Trigger Type
        ItemStack typeItem = new ItemStack(Material.LEVER);
        ItemMeta typeMeta = typeItem.getItemMeta();
        if (typeMeta != null) {
            typeMeta.displayName(TextUtil.parse("<yellow><bold>Trigger Type</bold></yellow>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Current:</gray> <aqua>" + keybind.getType().name() + "</aqua>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<gray>Available types:</gray>"));
            lore.add(TextUtil.parse("<dark_gray>- DOUBLE_SNEAK (Double Tap Shift)</dark_gray>"));
            lore.add(TextUtil.parse("<dark_gray>- SNEAK_SWAP_HAND (Shift + F)</dark_gray>"));
            lore.add(TextUtil.parse("<dark_gray>- SNEAK_DROP_ITEM (Shift + Q)</dark_gray>"));
            lore.add(TextUtil.parse("<dark_gray>- SWAP_HAND (F)</dark_gray>"));
            lore.add(TextUtil.parse("<dark_gray>- DROP_ITEM (Q)</dark_gray>"));
            lore.add(TextUtil.parse("<dark_gray>- SNEAK_RIGHT_CLICK (Shift + Right Click)</dark_gray>"));
            lore.add(TextUtil.parse("<dark_gray>- RIGHT_CLICK / LEFT_CLICK</dark_gray>"));
            lore.add(TextUtil.parse("<dark_gray>- SNEAK_HOLD (Hold Shift)</dark_gray>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>Left-Click: Next type »</yellow>"));
            lore.add(TextUtil.parse("<yellow>Right-Click: Previous type «</yellow>"));
            typeMeta.lore(lore);
            typeItem.setItemMeta(typeMeta);
        }
        canvas.setButton(10, CanvasButton.of(typeItem, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            KeybindType[] allTypes = KeybindType.values();
            int currentIdx = keybind.getType().ordinal();
            if (e.isRightClick()) {
                int prev = (currentIdx - 1 + allTypes.length) % allTypes.length;
                keybind.setType(allTypes[prev]);
            } else {
                int next = (currentIdx + 1) % allTypes.length;
                keybind.setType(allTypes[next]);
            }
            open(player);
        }));

        // Slot 12: Hotbar Slot Requirement
        ItemStack slotItem = new ItemStack(Material.COMPASS);
        ItemMeta slotMeta = slotItem.getItemMeta();
        if (slotMeta != null) {
            slotMeta.displayName(TextUtil.parse("<yellow><bold>Required Hotbar Slot</bold></yellow>"));
            List<Component> lore = new ArrayList<>();
            String slotStr = keybind.getHotbarSlot() >= 0 ? "Slot " + (keybind.getHotbarSlot() + 1) : "Any Slot (-1)";
            lore.add(TextUtil.parse("<gray>Current:</gray> <white>" + slotStr + "</white>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<gray>Requires player to hold a specific slot (1-9) to activate.</gray>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>Click to cycle slot (Any -> 1..9 -> Any) »</yellow>"));
            slotMeta.lore(lore);
            slotItem.setItemMeta(slotMeta);
        }
        canvas.setButton(12, CanvasButton.of(slotItem, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            int currentSlot = keybind.getHotbarSlot();
            if (currentSlot == -1) {
                keybind.setHotbarSlot(0); // Slot 1
            } else if (currentSlot >= 8) {
                keybind.setHotbarSlot(-1); // Back to Any
            } else {
                keybind.setHotbarSlot(currentSlot + 1);
            }
            open(player);
        }));

        // Slot 14: Execution Mode (Player vs Console)
        ItemStack execItem = new ItemStack(keybind.isAsPlayer() ? Material.PLAYER_HEAD : Material.COMMAND_BLOCK);
        ItemMeta execMeta = execItem.getItemMeta();
        if (execMeta != null) {
            execMeta.displayName(TextUtil.parse("<yellow><bold>Execution Mode</bold></yellow>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Current:</gray> " + (keybind.isAsPlayer() ? "<gold>[PLAYER]</gold> (Runs as player)" : "<red>[CONSOLE]</red> (Runs as console)")));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<gray>When player mode is active, commands run with the player's permissions.</gray>"));
            lore.add(TextUtil.parse("<gray>When console mode is active, commands run with elevated console permissions.</gray>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>Click to toggle execution mode »</yellow>"));
            execMeta.lore(lore);
            execItem.setItemMeta(execMeta);
        }
        canvas.setButton(14, CanvasButton.of(execItem, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            keybind.setAsPlayer(!keybind.isAsPlayer());
            open(player);
        }));

        // Slot 16: Cooldown in ms
        ItemStack coolItem = new ItemStack(Material.CLOCK);
        ItemMeta coolMeta = coolItem.getItemMeta();
        if (coolMeta != null) {
            coolMeta.displayName(TextUtil.parse("<yellow><bold>Activation Cooldown</bold></yellow>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Current:</gray> <white>" + keybind.getCooldownMs() + "ms</white>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<gray>Cooldown period to prevent keybind activation spam.</gray>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>Click to set cooldown (milliseconds) »</yellow>"));
            coolMeta.lore(lore);
            coolItem.setItemMeta(coolMeta);
        }
        canvas.setButton(16, CanvasButton.of(coolItem, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            plugin.getChatPromptManager().prompt(player,
                    TextUtil.parse("<gray>Enter cooldown in milliseconds (e.g. <yellow>500</yellow>):</gray>"),
                    input -> {
                        try {
                            long ms = Long.parseLong(input.trim());
                            keybind.setCooldownMs(ms);
                        } catch (NumberFormatException ignored) {
                            player.sendMessage(TextUtil.parse("<red>Invalid number entered.</red>"));
                        }
                        open(player);
                    });
        }));

        // Slot 22: Command to Execute
        ItemStack cmdItem = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta cmdMeta = cmdItem.getItemMeta();
        if (cmdMeta != null) {
            cmdMeta.displayName(TextUtil.parse("<yellow><bold>Command to Execute</bold></yellow>"));
            List<Component> lore = new ArrayList<>();
            String cmdDisplay = keybind.getCommand().isEmpty() ? "<italic><red>None configured</red></italic>" : "<green>/" + keybind.getCommand() + "</green>";
            lore.add(TextUtil.parse("<gray>Current:</gray> " + cmdDisplay));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<gray>Available Placeholders:</gray>"));
            lore.add(TextUtil.parse("<white>{player}</white> <gray>- Player's username</gray>"));
            lore.add(TextUtil.parse("<white>{uuid}</white> <gray>- Player's UUID</gray>"));
            lore.add(TextUtil.parse("<white>{slot}</white> <gray>- Held slot (1-9)</gray>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>Click to set command string »</yellow>"));
            cmdMeta.lore(lore);
            cmdItem.setItemMeta(cmdMeta);
        }
        canvas.setButton(22, CanvasButton.of(cmdItem, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            plugin.getChatPromptManager().prompt(player,
                    TextUtil.parse("<gray>Enter command to run (without leading slash, e.g. <yellow>curios</yellow> or <yellow>spawn</yellow>):</gray>"),
                    input -> {
                        String cleanCmd = input.trim();
                        if (cleanCmd.startsWith("/")) {
                            cleanCmd = cleanCmd.substring(1);
                        }
                        keybind.setCommand(cleanCmd);
                        open(player);
                    });
        }));

        // Slot 24: Enabled / Disabled Toggle
        ItemStack toggleItem = new ItemStack(keybind.isEnabled() ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta toggleMeta = toggleItem.getItemMeta();
        if (toggleMeta != null) {
            toggleMeta.displayName(TextUtil.parse("<yellow><bold>Keybind State</bold></yellow>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Current:</gray> " + (keybind.isEnabled() ? "<green>Enabled</green>" : "<red>Disabled</red>")));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>Click to toggle state »</yellow>"));
            toggleMeta.lore(lore);
            toggleItem.setItemMeta(toggleMeta);
        }
        canvas.setButton(24, CanvasButton.of(toggleItem, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            keybind.setEnabled(!keybind.isEnabled());
            open(player);
        }));

        // Slot 38: Back to List
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        if (backMeta != null) {
            backMeta.displayName(TextUtil.parse("<red>« Back to Keybinds List</red>"));
            backItem.setItemMeta(backMeta);
        }
        canvas.setButton(38, CanvasButton.of(backItem, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            if (returnCallback != null) {
                returnCallback.run();
            }
        }));

        // Slot 42: Save Keybind
        ItemStack saveItem = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta saveMeta = saveItem.getItemMeta();
        if (saveMeta != null) {
            saveMeta.displayName(TextUtil.parse("<green><bold>✔ Save & Apply Keybind</bold></green>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Saves keybind to keybinds.yml and applies immediately.</gray>"));
            saveMeta.lore(lore);
            saveItem.setItemMeta(saveMeta);
        }
        canvas.setButton(42, CanvasButton.of(saveItem, e -> {
            plugin.getKeybindManager().saveCustomKeybind(keybind);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
            player.sendMessage(TextUtil.parse("<green>✓ Keybind '" + keybind.getId() + "' successfully saved and registered!</green>"));
            if (returnCallback != null) {
                returnCallback.run();
            }
        }));

        canvas.open(player);
    }
}

