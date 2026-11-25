package org.bg52.synthetix.gui.editor.category;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.ability.AbilitySpec;
import org.bg52.synthetix.gui.CanvasButton;
import org.bg52.synthetix.gui.editor.BaseEditorCanvas;
import org.bg52.synthetix.item.ItemDefinition;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

/**
 * Interactive visual Action Builder GUI for Synthetix abilities.
 * Replaces raw text commands with interactive preset buttons and wizards for
 * Sounds, Particles, Potion Effects, Projectiles, Lightning, Ignite, Velocity, Messages, and Commands.
 */
public class ActionBuilderGUI {

    private final SynthetixPlugin plugin;
    private final ItemDefinition def;
    private final String triggerId;
    private final AbilitySpec spec;
    private final Runnable onBack;

    public ActionBuilderGUI(SynthetixPlugin plugin, ItemDefinition def, String triggerId, AbilitySpec spec, Runnable onBack) {
        this.plugin = plugin;
        this.def = def;
        this.triggerId = triggerId;
        this.spec = spec;
        this.onBack = onBack;
    }

    public void open(Player player) {
        BaseEditorCanvas base = new BaseEditorCanvas(plugin, def,
                TextUtil.parse("<gold><bold>Actions:</bold> <yellow>" + triggerId + "</yellow></gold>"), 6);
        base.setupSideBorders();

        // 1. Display Current Actions (Slots 10..16, 19..25)
        int[] actionSlots = new int[]{
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25
        };

        List<String> actions = spec.getActions();
        for (int i = 0; i < actions.size() && i < actionSlots.length; i++) {
            int actionIndex = i;
            String act = actions.get(i);
            ItemStack icon = createActionIcon(act);

            base.getCanvas().setButton(actionSlots[i], CanvasButton.of(icon, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
                if (e.isRightClick()) {
                    spec.getActions().remove(actionIndex);
                    player.sendMessage(TextUtil.parse("<green>Removed action:</green> <gray>" + act + "</gray>"));
                    open(player);
                }
            }));
        }

        // Empty state indicator
        if (actions.isEmpty()) {
            ItemStack emptyItem = new ItemStack(Material.STRUCTURE_VOID);
            ItemMeta em = emptyItem.getItemMeta();
            if (em != null) {
                em.displayName(TextUtil.parse("<gray><i>No actions configured yet</i></gray>"));
                List<Component> lore = new ArrayList<>();
                lore.add(TextUtil.parse("<yellow>Click any preset button below to attach actions!</yellow>"));
                em.lore(lore);
                emptyItem.setItemMeta(em);
            }
            base.getCanvas().setButton(22, CanvasButton.of(emptyItem, e -> {}));
        }

        // 2. Action Category Presets (Row 5: slots 37..43)
        // 🔊 Sound
        base.getCanvas().setButton(37, CanvasButton.of(createPresetBtn(Material.NOTE_BLOCK, "<gold>+ Sound Effect</gold>", "Add sound with volume & pitch"),
                e -> openSoundPicker(player)));

        // ✨ Particle
        base.getCanvas().setButton(38, CanvasButton.of(createPresetBtn(Material.FIREWORK_STAR, "<aqua>+ Particle Burst</aqua>", "Add particle burst around player or target"),
                e -> openParticlePicker(player)));

        // 🧪 Potion Effect
        base.getCanvas().setButton(39, CanvasButton.of(createPresetBtn(Material.POTION, "<light_purple>+ Potion Effect</light_purple>", "Buff bearer or debuff target"),
                e -> openEffectPicker(player)));

        // 🏹 Projectile
        base.getCanvas().setButton(40, CanvasButton.of(createPresetBtn(Material.BOW, "<green>+ Launch Projectile</green>", "Shoot fireball, arrow, or skull"),
                e -> openProjectilePicker(player)));

        // ⚡ Lightning
        base.getCanvas().setButton(41, CanvasButton.of(createPresetBtn(Material.LIGHTNING_ROD, "<yellow>+ Strike Lightning</yellow>", "Strike target or bearer with lightning"),
                e -> {
                    spec.getActions().add("lightning: target");
                    player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.5f);
                    player.sendMessage(TextUtil.parse("<green>Added action:</green> <yellow>lightning</yellow>"));
                    open(player);
                }));

        // 🔥 Ignite
        base.getCanvas().setButton(42, CanvasButton.of(createPresetBtn(Material.BLAZE_POWDER, "<gold>+ Ignite Target</gold>", "Set target on fire for X seconds"),
                e -> openIgnitePicker(player)));

        // 💨 Velocity / Dash
        base.getCanvas().setButton(43, CanvasButton.of(createPresetBtn(Material.FEATHER, "<white>+ Dash / Velocity</white>", "Forward dash or leap"),
                e -> openVelocityPicker(player)));

        // ⚙️ Command & Message (Row 6)
        base.getCanvas().setButton(28, CanvasButton.of(createPresetBtn(Material.PAPER, "<yellow>+ Message / Actionbar</yellow>", "Send player feedback"),
                e -> openMessagePicker(player)));

        base.getCanvas().setButton(34, CanvasButton.of(createPresetBtn(Material.COMMAND_BLOCK, "<red>+ Run Command</red>", "Console or player command wizard"),
                e -> openCommandPrompt(player)));

        base.setBackButton(onBack);
        base.open(player);
    }

    private void openSoundPicker(Player player) {
        BaseEditorCanvas sub = new BaseEditorCanvas(plugin, def, TextUtil.parse("<gold>Select Sound Preset</gold>"), 4);
        sub.setupSideBorders();

        String[][] sounds = {
                {"ENTITY_EXPERIENCE_ORB_PICKUP", "Ding / Level Ping", "1.0", "1.2"},
                {"ENTITY_PLAYER_LEVELUP", "Level Up Chime", "1.0", "1.0"},
                {"ENTITY_GENERIC_EXPLODE", "Explosion", "1.0", "1.0"},
                {"ENTITY_BLAZE_SHOOT", "Blaze Fireball", "1.0", "1.2"},
                {"ENTITY_ENDER_DRAGON_GROWL", "Dragon Roar", "1.0", "1.0"},
                {"ITEM_TOTEM_USE", "Totem Resurrection", "1.0", "1.0"},
                {"BLOCK_BEACON_ACTIVATE", "Beacon Chime", "1.0", "1.2"},
                {"BLOCK_ANVIL_USE", "Anvil Clang", "1.0", "1.0"},
                {"BLOCK_NOTE_BLOCK_BELL", "Bell Chime", "1.0", "1.0"}
        };

        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20};
        for (int i = 0; i < sounds.length && i < slots.length; i++) {
            String soundName = sounds[i][0];
            String label = sounds[i][1];
            String vol = sounds[i][2];
            String pitch = sounds[i][3];

            ItemStack icon = createPresetBtn(Material.NOTE_BLOCK, "<yellow>" + label + "</yellow>", soundName);
            sub.getCanvas().setButton(slots[i], CanvasButton.of(icon, e -> {
                spec.getActions().add("sound: " + soundName + " " + vol + " " + pitch);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                open(player);
            }));
        }

        // Custom Sound button
        sub.getCanvas().setButton(25, CanvasButton.of(createPresetBtn(Material.WRITABLE_BOOK, "<gold>Custom Sound...</gold>", "Enter sound enum or custom key"), e -> {
            plugin.getChatPromptManager().prompt(player,
                    TextUtil.parse("<gray>Enter sound name (e.g. <yellow>ENTITY_WITHER_SPAWN 1.0 1.0</yellow> or <yellow>custom.sound</yellow>):</gray>"),
                    input -> {
                        if (!input.trim().isEmpty()) {
                            spec.getActions().add("sound: " + input.trim());
                        }
                        open(player);
                    });
        }));

        sub.setBackButton(() -> open(player));
        sub.open(player);
    }

    private void openParticlePicker(Player player) {
        BaseEditorCanvas sub = new BaseEditorCanvas(plugin, def, TextUtil.parse("<aqua>Select Particle Preset</aqua>"), 4);
        sub.setupSideBorders();

        String[][] particles = {
                {"FLAME", "Flames", "20", "0.5"},
                {"SOUL_FIRE_FLAME", "Soul Flame", "20", "0.5"},
                {"DRAGON_BREATH", "Dragon Breath", "25", "0.6"},
                {"HEART", "Hearts", "8", "0.4"},
                {"ENCHANTMENT_TABLE", "Magic Glyphs", "30", "0.6"},
                {"EXPLOSION_NORMAL", "Smoke Cloud", "15", "0.4"},
                {"PORTAL", "Nether Portal", "35", "0.5"},
                {"WITCH", "Witch Sparkles", "20", "0.5"},
                {"CRIT", "Critical Hits", "20", "0.5"}
        };

        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20};
        for (int i = 0; i < particles.length && i < slots.length; i++) {
            String pName = particles[i][0];
            String label = particles[i][1];
            String count = particles[i][2];
            String spread = particles[i][3];

            ItemStack icon = createPresetBtn(Material.FIREWORK_STAR, "<aqua>" + label + "</aqua>", pName + " x" + count);
            sub.getCanvas().setButton(slots[i], CanvasButton.of(icon, e -> {
                spec.getActions().add("particle: " + pName + " " + count + " " + spread + " " + spread + " " + spread + " 0.05");
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                open(player);
            }));
        }

        sub.setBackButton(() -> open(player));
        sub.open(player);
    }

    private void openEffectPicker(Player player) {
        BaseEditorCanvas sub = new BaseEditorCanvas(plugin, def, TextUtil.parse("<light_purple>Select Status Effect</light_purple>"), 4);
        sub.setupSideBorders();

        String[][] effects = {
                {"SPEED", "Speed II (6s)", "6", "1"},
                {"INCREASE_DAMAGE", "Strength I (5s)", "5", "0"},
                {"REGENERATION", "Regeneration II (5s)", "5", "1"},
                {"DAMAGE_RESISTANCE", "Resistance I (6s)", "6", "0"},
                {"JUMP", "Jump Boost II (8s)", "8", "1"},
                {"FIRE_RESISTANCE", "Fire Resistance (15s)", "15", "0"},
                {"INVISIBILITY", "Invisibility (10s)", "10", "0"},
                {"GLOWING", "Glowing (8s)", "8", "0"},
                {"ABSORPTION", "Absorption II (12s)", "12", "1"}
        };

        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20};
        for (int i = 0; i < effects.length && i < slots.length; i++) {
            String effName = effects[i][0];
            String label = effects[i][1];
            String duration = effects[i][2];
            String amp = effects[i][3];

            ItemStack icon = createPresetBtn(Material.POTION, "<light_purple>" + label + "</light_purple>", "effect: " + effName);
            sub.getCanvas().setButton(slots[i], CanvasButton.of(icon, e -> {
                spec.getActions().add("effect: " + effName + " " + duration + " " + amp);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                open(player);
            }));
        }

        sub.setBackButton(() -> open(player));
        sub.open(player);
    }

    private void openProjectilePicker(Player player) {
        BaseEditorCanvas sub = new BaseEditorCanvas(plugin, def, TextUtil.parse("<green>Select Projectile</green>"), 4);
        sub.setupSideBorders();

        String[][] projs = {
                {"SMALL_FIREBALL", "Small Fireball (Blaze)", "speed:1.5", "FIRE_CHARGE"},
                {"LARGE_FIREBALL", "Ghast Fireball", "speed:1.2", "FIRE_CHARGE"},
                {"ARROW", "High Speed Arrow", "speed:2.5", "ARROW"},
                {"SPECTRAL_ARROW", "Spectral Arrow", "speed:2.0", "SPECTRAL_ARROW"},
                {"SNOWBALL", "Snowball", "speed:1.6", "SNOWBALL"},
                {"WITHER_SKULL", "Wither Skull", "speed:1.2", "WITHER_SKELETON_SKULL"},
                {"DRAGON_FIREBALL", "Dragon Fireball", "speed:1.0", "DRAGON_BREATH"}
        };

        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < projs.length && i < slots.length; i++) {
            String pType = projs[i][0];
            String label = projs[i][1];
            String speed = projs[i][2];
            Material mat;
            try {
                mat = Material.valueOf(projs[i][3]);
            } catch (Exception ex) {
                mat = Material.ARROW;
            }

            ItemStack icon = createPresetBtn(mat, "<green>" + label + "</green>", "Shoots " + pType);
            sub.getCanvas().setButton(slots[i], CanvasButton.of(icon, e -> {
                spec.getActions().add("projectile: " + pType + " " + speed);
                player.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.7f, 1.2f);
                open(player);
            }));
        }

        sub.setBackButton(() -> open(player));
        sub.open(player);
    }

    private void openIgnitePicker(Player player) {
        BaseEditorCanvas sub = new BaseEditorCanvas(plugin, def, TextUtil.parse("<gold>Select Fire Duration</gold>"), 3);
        sub.setupSideBorders();

        int[] times = {3, 5, 8, 12};
        int[] slots = {11, 12, 14, 15};
        for (int i = 0; i < times.length; i++) {
            int sec = times[i];
            ItemStack icon = createPresetBtn(Material.BLAZE_POWDER, "<gold>" + sec + " Seconds Fire</gold>", "Ignite target for " + sec + "s");
            sub.getCanvas().setButton(slots[i], CanvasButton.of(icon, e -> {
                spec.getActions().add("ignite: " + sec);
                player.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.7f, 1.0f);
                open(player);
            }));
        }

        sub.setBackButton(() -> open(player));
        sub.open(player);
    }

    private void openVelocityPicker(Player player) {
        BaseEditorCanvas sub = new BaseEditorCanvas(plugin, def, TextUtil.parse("<white>Select Dash / Velocity</white>"), 3);
        sub.setupSideBorders();

        String[][] dashes = {
                {"Forward Dash", "1.2"},
                {"Strong Dash", "2.0"},
                {"Super Leap", "3.0"}
        };

        int[] slots = {11, 13, 15};
        for (int i = 0; i < dashes.length; i++) {
            String label = dashes[i][0];
            String mult = dashes[i][1];
            ItemStack icon = createPresetBtn(Material.FEATHER, "<white>" + label + "</white>", "Launches player forward (" + mult + "x)");
            sub.getCanvas().setButton(slots[i], CanvasButton.of(icon, e -> {
                spec.getActions().add("velocity: " + mult);
                player.playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 0.8f, 1.2f);
                open(player);
            }));
        }

        sub.setBackButton(() -> open(player));
        sub.open(player);
    }

    private void openMessagePicker(Player player) {
        BaseEditorCanvas sub = new BaseEditorCanvas(plugin, def, TextUtil.parse("<yellow>Select Message Type</yellow>"), 3);
        sub.setupSideBorders();

        sub.getCanvas().setButton(11, CanvasButton.of(createPresetBtn(Material.PAPER, "<yellow>Chat Message</yellow>", "Sends chat message to bearer"), e -> {
            plugin.getChatPromptManager().prompt(player,
                    TextUtil.parse("<gray>Enter message text (supports MiniMessage &amp; <yellow>%player%</yellow>):</gray>"),
                    input -> {
                        if (!input.trim().isEmpty()) spec.getActions().add("message: " + input.trim());
                        open(player);
                    });
        }));

        sub.getCanvas().setButton(13, CanvasButton.of(createPresetBtn(Material.GOLD_NUGGET, "<gold>ActionBar Message</gold>", "Displays message above hotbar"), e -> {
            plugin.getChatPromptManager().prompt(player,
                    TextUtil.parse("<gray>Enter actionbar text (e.g. <yellow>&lt;green&gt;Ability Ready!&lt;/green&gt;</yellow>):</gray>"),
                    input -> {
                        if (!input.trim().isEmpty()) spec.getActions().add("actionbar: " + input.trim());
                        open(player);
                    });
        }));

        sub.getCanvas().setButton(15, CanvasButton.of(createPresetBtn(Material.NAME_TAG, "<aqua>Title Message</aqua>", "Shows title on screen (Title;Subtitle)"), e -> {
            plugin.getChatPromptManager().prompt(player,
                    TextUtil.parse("<gray>Enter title text (Format: <yellow>Main Title;Subtitle</yellow>):</gray>"),
                    input -> {
                        if (!input.trim().isEmpty()) spec.getActions().add("title: " + input.trim());
                        open(player);
                    });
        }));

        sub.setBackButton(() -> open(player));
        sub.open(player);
    }

    private void openCommandPrompt(Player player) {
        plugin.getChatPromptManager().prompt(player,
                TextUtil.parse("<gray>Enter command (e.g. <yellow>console: effect give %player% speed 5 1</yellow> or <yellow>player: spawn</yellow>):</gray>"),
                input -> {
                    if (!input.trim().isEmpty()) {
                        String cmd = input.trim();
                        if (!cmd.startsWith("console:") && !cmd.startsWith("player:")) {
                            cmd = "console: " + cmd;
                        }
                        spec.getActions().add("command: " + cmd);
                        player.sendMessage(TextUtil.parse("<green>Added command action!</green>"));
                    }
                    open(player);
                });
    }

    private ItemStack createActionIcon(String action) {
        Material mat = Material.PAPER;
        String title = action;

        if (action.startsWith("sound:")) {
            mat = Material.NOTE_BLOCK;
            title = "<gold>Sound:</gold> <white>" + action.substring(6).trim() + "</white>";
        } else if (action.startsWith("particle:")) {
            mat = Material.FIREWORK_STAR;
            title = "<aqua>Particle:</aqua> <white>" + action.substring(9).trim() + "</white>";
        } else if (action.startsWith("effect:")) {
            mat = Material.POTION;
            title = "<light_purple>Effect:</light_purple> <white>" + action.substring(7).trim() + "</white>";
        } else if (action.startsWith("projectile:")) {
            mat = Material.BOW;
            title = "<green>Projectile:</green> <white>" + action.substring(11).trim() + "</white>";
        } else if (action.startsWith("lightning")) {
            mat = Material.LIGHTNING_ROD;
            title = "<yellow>Lightning Strike</yellow>";
        } else if (action.startsWith("ignite:")) {
            mat = Material.BLAZE_POWDER;
            title = "<gold>Ignite:</gold> <white>" + action.substring(7).trim() + "s</white>";
        } else if (action.startsWith("velocity:") || action.startsWith("launch:")) {
            mat = Material.FEATHER;
            title = "<white>Velocity Dash</white>";
        } else if (action.startsWith("command:")) {
            mat = Material.COMMAND_BLOCK;
            title = "<red>Command:</red> <white>" + action.substring(8).trim() + "</white>";
        } else if (action.startsWith("actionbar:")) {
            mat = Material.GOLD_NUGGET;
            title = "<gold>ActionBar:</gold> <white>" + action.substring(10).trim() + "</white>";
        } else if (action.startsWith("message:")) {
            mat = Material.PAPER;
            title = "<yellow>Message:</yellow> <white>" + action.substring(8).trim() + "</white>";
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.parse(title));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Raw:</gray> <dark_gray>" + action + "</dark_gray>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<red>Right-Click to delete action</red>"));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createPresetBtn(Material mat, String name, String desc) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.parse(name));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>" + desc + "</gray>"));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}

