package org.bg52.synthetix.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.gui.browser.ItemBrowserGUI;
import org.bg52.synthetix.gui.editor.MainEditorMenu;
import org.bg52.synthetix.gui.settings.GlobalSettingsGUI;
import org.bg52.synthetix.item.ItemDefinition;
import org.bg52.synthetix.lang.LanguageManager;
import org.bg52.synthetix.recipe.SynthetixRecipe;
import org.bg52.synthetix.settings.GlobalFeature;
import org.bg52.synthetix.settings.GlobalSettingsManager;
import org.bg52.synthetix.util.TextUtil;
import org.bg52.synthetix.util.VersionChecker;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * Main command executor and tab completer for /synth (/synthetix).
 * Dedicated subcommands:
 * - /synth create [item_id] : Create a new item
 * - /synth edit [item_id]   : Edit an existing item
 * - /synth items            : Open full interactive in-game items browser
 * - /synth give <player> <item_id> [amount]
 * - /synth delete <item_id> : Delete an item
 * - /synth list             : Chat list
 * - /synth reload           : Reload configs, items, recipes, drops
 * - /synth info             : Diagnostics
 */
public class SynthCommand implements CommandExecutor, TabCompleter {

    private final SynthetixPlugin plugin;
    private final LanguageManager lang;

    public SynthCommand(SynthetixPlugin plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    public boolean hasSubcommandPermission(CommandSender sender, String sub) {
        if (sender == null) return false;
        if (sender.hasPermission("synthetix.admin")) return true;
        String s = sub.toLowerCase(Locale.ROOT);
        return switch (s) {
            case "settings", "setting", "toggle", "toggles" ->
                    sender.hasPermission("synthetix.settings") || sender.hasPermission("synthetix.admin");
            case "recipe", "recipes" -> sender.hasPermission("synthetix.recipe");
            case "items", "browse", "menu", "listgui", "gui" ->
                    sender.hasPermission("synthetix.recipe") || sender.hasPermission("synthetix.command.gui");
            case "blocks", "blockbrowser" ->
                    sender.hasPermission("synthetix.recipe") || sender.hasPermission("synthetix.command.gui");
            case "sets", "set" -> sender.hasPermission("synthetix.sets");
            case "export" -> sender.hasPermission("synthetix.export");
            case "import" -> sender.hasPermission("synthetix.import");
            case "give" -> sender.hasPermission("synthetix.command.give");
            case "create", "edit" -> sender.hasPermission("synthetix.command.gui");
            case "migrate", "updatedatabase", "update-database", "rebuild", "rebuild-items", "rebuilditems" -> sender.hasPermission("synthetix.admin");
            default -> false;
        };
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (label.equalsIgnoreCase("synthpack")) {
            if (!sender.hasPermission("synthetix.admin")) {
                lang.send(sender, "general.no-permission");
                return true;
            }
            String[] packArgs = new String[args.length + 1];
            packArgs[0] = "pack";
            System.arraycopy(args, 0, packArgs, 1, args.length);
            handlePack(sender, packArgs);
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!hasSubcommandPermission(sender, sub)) {
            lang.send(sender, "general.no-permission");
            return true;
        }

        switch (sub) {
            case "settings", "setting", "toggle", "toggles" -> handleSettings(sender, args);
            case "create" -> handleCreate(sender, args);
            case "edit" -> handleEdit(sender, args);
            case "clone", "duplicate" -> handleClone(sender, args);
            case "items", "browse", "menu", "listgui", "gui" -> handleItems(sender);
            case "block", "blocks", "blockbrowser" -> handleBlockCommand(sender, args);
            case "entity", "entities", "mobs", "mob" -> handleEntityCommand(sender, args);
            case "spawn" -> handleSpawnCommand(sender, args);
            case "blockbench", "bb" -> handleBlockbench(sender, args);
            case "give" -> handleGive(sender, args);
            case "delete", "remove" -> handleDelete(sender, args);
            case "list" -> handleList(sender);
            case "recipe", "recipes" -> handleRecipe(sender, args);
            case "sets", "set" -> handleSets(sender);
            case "keybinds", "keybind", "keys" -> handleKeybinds(sender);
            case "export" -> handleExport(sender, args);
            case "import" -> handleImport(sender, args);
            case "pack", "rp", "resourcepack" -> handlePack(sender, args);
            case "geyser", "bedrock" -> handleGeyser(sender, args);
            case "migrate", "updatedatabase", "update-database" -> handleMigrate(sender);
            case "rebuild", "rebuild-items", "rebuilditems" -> handleRebuildItems(sender, args);
            case "reload" -> handleReload(sender);
            case "info", "version" -> handleInfo(sender);
            default -> sendHelp(sender, label);
        }

        return true;
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            lang.send(sender, "general.player-only");
            return;
        }

        // Support:
        // /synth create item <name>
        // /synth create block <name>
        // /synth create <name> -> default item (or prompt)
        if (args.length < 2) {
            lang.send(player, "command.create-usage", "%label%", "synth");
            plugin.getChatPromptManager().prompt(player,
                    lang.getComponent("command.create-type-prompt"),
                    input -> {
                        String[] split = input.trim().split("\\s+", 2);
                        if (split.length == 2 && split[0].equalsIgnoreCase("block")) {
                            openCreateBlockMenu(player, split[1]);
                        } else if (split.length == 2 && split[0].equalsIgnoreCase("item")) {
                            openCreateItemMenu(player, split[1]);
                        } else if (split.length == 1 && !split[0].isEmpty()) {
                            openCreateItemMenu(player, split[0]);
                        }
                    });
            return;
        }

        if (args[1].equalsIgnoreCase("block")) {
            if (args.length < 3) {
                plugin.getChatPromptManager().prompt(player,
                        lang.getComponent("command.create-block-prompt"),
                        id -> openCreateBlockMenu(player, id.trim()));
                return;
            }
            openCreateBlockMenu(player, args[2].trim());
            return;
        }

        if (args[1].equalsIgnoreCase("entity") || args[1].equalsIgnoreCase("mob")) {
            if (args.length < 3) {
                plugin.getChatPromptManager().prompt(player,
                        lang.getComponent("command.entity-create-prompt"),
                        id -> openCreateEntityMenu(player, id.trim()));
                return;
            }
            openCreateEntityMenu(player, args[2].trim());
            return;
        }

        if (args[1].equalsIgnoreCase("item")) {
            if (args.length < 3) {
                plugin.getChatPromptManager().prompt(player,
                        lang.getComponent("command.create-prompt"),
                        id -> openCreateItemMenu(player, id.trim()));
                return;
            }
            openCreateItemMenu(player, args[2].trim());
            return;
        }

        // Fallback backward-compatible: /synth create <name>
        openCreateItemMenu(player, args[1].trim());
    }

    private void openCreateItemMenu(Player player, String rawId) {
        String cleanId = rawId.toLowerCase(Locale.ROOT);
        ItemDefinition existing = plugin.getItemRegistry().get(cleanId);
        if (existing != null) {
            lang.send(player, "command.item-already-exists", "%item%", cleanId);
            MainEditorMenu.open(player, plugin, existing);
            return;
        }

        ItemDefinition newDef = new ItemDefinition(cleanId);
        plugin.getItemRegistry().register(newDef);
        MainEditorMenu.open(player, plugin, newDef);
    }

    private void openCreateBlockMenu(Player player, String rawId) {
        String cleanId = rawId.toLowerCase(Locale.ROOT);
        org.bg52.synthetix.block.CustomBlockDefinition existing = plugin.getCustomBlockRegistry().get(cleanId);
        if (existing != null) {
            lang.send(player, "command.block-already-exists", "%block%", cleanId);
            org.bg52.synthetix.gui.editor.block.BlockEditorMenu.open(player, plugin, existing);
            return;
        }

        org.bg52.synthetix.block.CustomBlockDefinition newBlock = new org.bg52.synthetix.block.CustomBlockDefinition(cleanId);
        plugin.getCustomBlockRegistry().register(newBlock);
        lang.send(player, "command.block-created", "%block%", cleanId);
        org.bg52.synthetix.gui.editor.block.BlockEditorMenu.open(player, plugin, newBlock);
    }

    private void openCreateEntityMenu(Player player, String rawId) {
        String cleanId = rawId.toLowerCase(Locale.ROOT);
        org.bg52.synthetix.entity.model.CustomEntityDefinition existing = plugin.getCustomEntityRegistry().get(cleanId);
        if (existing != null) {
            lang.send(player, "command.entity-already-exists", "%entity%", cleanId);
            org.bg52.synthetix.gui.editor.entity.EntityEditorMenu.open(player, plugin, existing);
            return;
        }

        org.bg52.synthetix.entity.model.CustomEntityDefinition newDef = new org.bg52.synthetix.entity.model.CustomEntityDefinition(cleanId);
        plugin.getCustomEntityRegistry().register(newDef);
        lang.send(player, "command.entity-created", "%entity%", cleanId);
        org.bg52.synthetix.gui.editor.entity.EntityEditorMenu.open(player, plugin, newDef);
    }

    private void handleEdit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            lang.send(sender, "general.player-only");
            return;
        }

        if (args.length < 2) {
            ItemBrowserGUI.open(player, plugin);
            return;
        }

        if (args[1].equalsIgnoreCase("block")) {
            if (args.length < 3) {
                org.bg52.synthetix.gui.editor.block.BlockBrowserGUI.open(player, plugin);
                return;
            }
            String blockId = args[2].trim().toLowerCase(Locale.ROOT);
            org.bg52.synthetix.block.CustomBlockDefinition bDef = plugin.getCustomBlockRegistry().get(blockId);
            if (bDef == null) {
                lang.send(player, "command.block-not-found-to-edit", "%block%", blockId);
                return;
            }
            org.bg52.synthetix.gui.editor.block.BlockEditorMenu.open(player, plugin, bDef);
            return;
        }

        if (args[1].equalsIgnoreCase("entity") || args[1].equalsIgnoreCase("mob")) {
            if (args.length < 3) {
                org.bg52.synthetix.gui.editor.entity.EntityBrowserGUI.open(player, plugin);
                return;
            }
            String entityId = args[2].trim().toLowerCase(Locale.ROOT);
            var eDef = plugin.getCustomEntityRegistry().get(entityId);
            if (eDef == null) {
                player.sendMessage(TextUtil.parse("<red>Custom entity <white>" + entityId + "</white> not found.</red>"));
                return;
            }
            org.bg52.synthetix.gui.editor.entity.EntityEditorMenu.open(player, plugin, eDef);
            return;
        }

        if (args[1].equalsIgnoreCase("item")) {
            if (args.length < 3) {
                ItemBrowserGUI.open(player, plugin);
                return;
            }
            String itemId = args[2].trim().toLowerCase(Locale.ROOT);
            ItemDefinition def = plugin.getItemRegistry().get(itemId);
            if (def == null) {
                lang.send(player, "command.item-not-found-to-edit", "%item%", itemId);
                return;
            }
            MainEditorMenu.open(player, plugin, def);
            return;
        }

        String cleanId = args[1].trim().toLowerCase(Locale.ROOT);
        org.bg52.synthetix.block.CustomBlockDefinition bDef = plugin.getCustomBlockRegistry().get(cleanId);
        if (bDef != null) {
            org.bg52.synthetix.gui.editor.block.BlockEditorMenu.open(player, plugin, bDef);
            return;
        }

        var entityDef = plugin.getCustomEntityRegistry().get(cleanId);
        if (entityDef != null) {
            org.bg52.synthetix.gui.editor.entity.EntityEditorMenu.open(player, plugin, entityDef);
            return;
        }

        ItemDefinition def = plugin.getItemRegistry().get(cleanId);
        if (def != null) {
            MainEditorMenu.open(player, plugin, def);
            return;
        }

        lang.send(player, "command.item-not-found-to-edit", "%item%", cleanId);
    }

    private void handleBlocks(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            lang.send(sender, "general.player-only");
            return;
        }
        org.bg52.synthetix.gui.editor.block.BlockBrowserGUI.open(player, plugin);
    }

    private void handleBlockbench(CommandSender sender, String[] args) {
        if (args.length == 1) {
            lang.sendRaw(sender, "blockbench.header");
            lang.sendRaw(sender, "blockbench.help-item");
            lang.sendRaw(sender, "blockbench.help-block");
            lang.sendRaw(sender, "blockbench.help-list");
            lang.sendRaw(sender, "blockbench.help-scan");
            return;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);

        if (sub.equals("list")) {
            List<String> available = plugin.getBlockbenchImporter().listAvailableModels();
            if (available.isEmpty()) {
                lang.send(sender, "blockbench.empty-list");
            } else {
                lang.sendRaw(sender, "blockbench.available-header", "%count%", String.valueOf(available.size()));
                for (String m : available) {
                    sender.sendMessage(TextUtil.parse("<gray>• </gray><aqua>" + m + "</aqua>"));
                }
            }
            return;
        }

        if (sub.equals("scan") || sub.equals("all")) {
            lang.send(sender, "blockbench.scanning");
            int imported = plugin.getBlockbenchImporter().scanAndImportAll();
            lang.send(sender, "blockbench.scan-complete", "%count%", String.valueOf(imported));
            return;
        }

        boolean isItem = false;
        boolean isBlock = false;
        String modelName = null;

        if (sub.equals("item")) {
            isItem = true;
            if (args.length > 2 && args[2].equalsIgnoreCase("import")) {
                if (args.length > 3) modelName = args[3];
            } else if (args.length > 2) {
                modelName = args[2];
            }
        } else if (sub.equals("block")) {
            isBlock = true;
            if (args.length > 2 && args[2].equalsIgnoreCase("import")) {
                if (args.length > 3) modelName = args[3];
            } else if (args.length > 2) {
                modelName = args[2];
            }
        } else if (sub.equals("import")) {
            if (args.length > 2 && args[2].equalsIgnoreCase("item")) {
                isItem = true;
                if (args.length > 3) modelName = args[3];
            } else if (args.length > 2 && args[2].equalsIgnoreCase("block")) {
                isBlock = true;
                if (args.length > 3) modelName = args[3];
            } else if (args.length > 2) {
                isBlock = true;
                modelName = args[2];
            }
        }

        if (modelName == null || modelName.trim().isEmpty()) {
            lang.send(sender, "blockbench.usage");
            return;
        }

        File modelFile = plugin.getBlockbenchImporter().findModelFile(modelName);
        if (modelFile == null || !modelFile.exists()) {
            lang.send(sender, "blockbench.not-found", "%name%", modelName);
            return;
        }

        if (isItem) {
            lang.send(sender, "blockbench.importing-item", "%file%", modelFile.getName());
            boolean ok = plugin.getBlockbenchImporter().importItemModelFile(modelFile, modelName, true);
            if (ok) {
                lang.send(sender, "blockbench.import-item-success", "%name%", modelName);
            } else {
                lang.send(sender, "blockbench.import-item-failed");
            }
        } else {
            lang.send(sender, "blockbench.importing-block", "%file%", modelFile.getName());
            boolean ok = plugin.getBlockbenchImporter().importBlockModelFile(modelFile, modelName, true);
            if (ok) {
                lang.send(sender, "blockbench.import-block-success", "%name%", modelName);
            } else {
                lang.send(sender, "blockbench.import-block-failed");
            }
        }
    }

    private void handleClone(CommandSender sender, String[] args) {
        // Supports:
        // /synth clone <sourceId> <newId>
        // /synth clone block <sourceId> <newId>
        // /synth clone item <sourceId> <newId>
        String typeFilter = null;
        String sourceId;
        String newId;

        if (args.length >= 4 && (args[1].equalsIgnoreCase("block") || args[1].equalsIgnoreCase("item"))) {
            typeFilter = args[1].toLowerCase(Locale.ROOT);
            sourceId = args[2].toLowerCase(Locale.ROOT).trim();
            newId = args[3].toLowerCase(Locale.ROOT).trim();
        } else if (args.length >= 3) {
            sourceId = args[1].toLowerCase(Locale.ROOT).trim();
            newId = args[2].toLowerCase(Locale.ROOT).trim();
        } else {
            lang.send(sender, "command.clone-usage");
            return;
        }

        // If explicitly block or if found in blocks
        if (!"item".equals(typeFilter)) {
            org.bg52.synthetix.block.CustomBlockDefinition sourceBlock = plugin.getCustomBlockRegistry().get(sourceId);
            if (sourceBlock != null) {
                if (plugin.getCustomBlockRegistry().get(newId) != null) {
                    lang.send(sender, "command.clone-target-block-exists", "%block%", newId);
                    return;
                }
                org.bg52.synthetix.block.CustomBlockDefinition clonedBlock = plugin.getCustomBlockRegistry().cloneBlock(sourceId, newId);
                if (clonedBlock != null) {
                    lang.send(sender, "command.clone-block-success", "%source%", sourceId, "%new%", newId);
                    if (sender instanceof Player player) {
                        org.bg52.synthetix.gui.editor.block.BlockEditorMenu.open(player, plugin, clonedBlock);
                    }
                }
                return;
            } else if ("block".equals(typeFilter)) {
                lang.send(sender, "command.clone-source-not-found", "%item%", sourceId);
                return;
            }
        }

        ItemDefinition source = plugin.getItemRegistry().get(sourceId);
        if (source == null) {
            lang.send(sender, "command.clone-source-not-found", "%item%", sourceId);
            return;
        }

        if (plugin.getItemRegistry().get(newId) != null) {
            lang.send(sender, "command.clone-target-exists", "%item%", newId);
            return;
        }

        ItemDefinition cloned = plugin.getItemRegistry().cloneItem(sourceId, newId);
        if (cloned != null) {
            lang.send(sender, "command.clone-success", "%source%", sourceId, "%new%", newId);
            if (sender instanceof Player player) {
                MainEditorMenu.open(player, plugin, cloned);
            }
        }
    }

    private void handleBlockCommand(CommandSender sender, String[] args) {
        if (args.length <= 1 || !sender.hasPermission("synthetix.admin")) {
            handleBlocks(sender);
            return;
        }
        String subAction = args[1].toLowerCase(Locale.ROOT);
        switch (subAction) {
            case "browse", "menu", "gui", "listgui" -> handleBlocks(sender);
            case "clone", "duplicate" -> {
                // /synth block clone <sourceId> <newId>
                if (args.length < 4) {
                    sender.sendMessage(TextUtil.parse("<red>Usage: /synth block clone <sourceBlockId> <newBlockId></red>"));
                    return;
                }
                handleClone(sender, new String[]{"clone", "block", args[2], args[3]});
            }
            case "create" -> {
                if (!(sender instanceof Player player)) {
                    lang.send(sender, "general.player-only");
                    return;
                }
                if (args.length < 3) {
                    sender.sendMessage(TextUtil.parse("<red>Usage: /synth block create <blockId></red>"));
                    return;
                }
                openCreateBlockMenu(player, args[2]);
            }
            case "edit" -> {
                if (!(sender instanceof Player player)) {
                    lang.send(sender, "general.player-only");
                    return;
                }
                if (args.length < 3) {
                    org.bg52.synthetix.gui.editor.block.BlockBrowserGUI.open(player, plugin);
                    return;
                }
                String blockId = args[2].trim().toLowerCase(Locale.ROOT);
                var bDef = plugin.getCustomBlockRegistry().get(blockId);
                if (bDef == null) {
                    lang.send(player, "command.block-not-found", "%block%", blockId);
                    return;
                }
                org.bg52.synthetix.gui.editor.block.BlockEditorMenu.open(player, plugin, bDef);
            }
            case "delete", "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage(TextUtil.parse("<red>Usage: /synth block delete <blockId></red>"));
                    return;
                }
                handleDelete(sender, new String[]{"delete", "block", args[2]});
            }
            case "give" -> {
                // /synth block give [player] <id> [amount]
                List<String> giveArgs = new ArrayList<>();
                giveArgs.add("give");
                giveArgs.add("block");
                for (int i = 2; i < args.length; i++) giveArgs.add(args[i]);
                handleGive(sender, giveArgs.toArray(new String[0]));
            }
            case "list" -> handleList(sender);
            case "culling" -> {
                var cm = plugin.getCustomBlockManager().getCullingManager();
                if (cm == null) {
                    sender.sendMessage(TextUtil.parse("<red>Block culling manager is not initialized.</red>"));
                    return;
                }
                sender.sendMessage(TextUtil.parse("<gradient:#FFAA00:#FF5500><bold>=== Synthetix Block Culling Status ===</bold></gradient>"));
                sender.sendMessage(TextUtil.parse("<gray>Enabled:</gray> " + (cm.isEnabled() ? "<green>true</green>" : "<red>false</red>")));
                sender.sendMessage(TextUtil.parse("<gray>Max Render Limit / Player:</gray> <yellow>" + cm.getMaxRenderedPerPlayer() + " blocks</yellow>"));
                sender.sendMessage(TextUtil.parse("<gray>Evaluation Distance:</gray> <aqua>" + (int) cm.getMaxDistance() + " blocks</aqua> <dark_gray>(Near Zone: " + cm.getNearRadius() + " blocks)</dark_gray>"));
                sender.sendMessage(TextUtil.parse("<gray>Tick Interval:</gray> <white>" + cm.getCheckIntervalTicks() + " ticks</white>"));
                sender.sendMessage(TextUtil.parse("<gray>Globally Rendered Blocks:</gray> <gold>" + cm.getGloballyRenderedBlockCount() + " / " + plugin.getCustomBlockManager().getActiveBlockCount() + "</gold>"));
                if (sender instanceof Player p) {
                    sender.sendMessage(TextUtil.parse("<gray>Rendered For You:</gray> <green>" + cm.getRenderedBlockCountFor(p) + " blocks</green>"));
                }
            }
            case "animation", "animations", "network" -> {
                var bm = plugin.getCustomBlockManager();
                sender.sendMessage(TextUtil.parse("<gradient:#FFAA00:#FF5500><bold>=== Synthetix Animation & Packet Optimization ===</bold></gradient>"));
                sender.sendMessage(TextUtil.parse("<gray>Keyframe Optimization:</gray> " + (bm.isKeyframeOptimization() ? "<green>ACTIVE (87% Packet Reduction)</green>" : "<red>DISABLED</red>")));
                sender.sendMessage(TextUtil.parse("<gray>Hardware Interpolation:</gray> <yellow>" + bm.getKeyframeInterpolationDuration() + " ticks</yellow> <dark_gray>(" + (bm.getKeyframeInterpolationDuration() * 50) + "ms smooth client GPU glide)</dark_gray>"));
                sender.sendMessage(TextUtil.parse("<gray>Dispatch Interval:</gray> <aqua>Every " + bm.getKeyframeDispatchInterval() + " ticks</aqua> <dark_gray>(" + (bm.getKeyframeDispatchInterval() * 50) + "ms advance)</dark_gray>"));
                sender.sendMessage(TextUtil.parse("<gray>Zero-Packet Idle Gating:</gray> " + (bm.isIdleZeroPackets() ? "<green>ENABLED (Stopped/Idle = 0 Packets)</green>" : "<red>DISABLED</red>")));
                sender.sendMessage(TextUtil.parse("<gray>Active Placed Blocks:</gray> <gold>" + bm.getActiveBlockCount() + " blocks</gold>"));
            }
            case "group", "autogroup", "optimize" -> {
                if (args.length < 3) {
                    sender.sendMessage(TextUtil.parse("<red>Usage: /synth block group <blockId></red>"));
                    return;
                }
                String blockId = args[2].trim().toLowerCase(Locale.ROOT);
                var bDef = plugin.getCustomBlockRegistry().get(blockId);
                if (bDef == null) {
                    lang.send(sender, "command.block-not-found", "%block%", blockId);
                    return;
                }
                int merged = plugin.getBlockbenchImporter().autoGroupPartsByMotion(bDef);
                if (merged > 0) {
                    sender.sendMessage(TextUtil.parse("<green>Optimized block <gold>" + bDef.getId() + "</gold>: Grouped into single models, eliminated <yellow>" + merged + "</yellow> entity/entities!</green>"));
                } else {
                    sender.sendMessage(TextUtil.parse("<yellow>No additional parts to group in block <gold>" + bDef.getId() + "</gold>. All parts already optimal.</yellow>"));
                }
            }
            default -> handleBlocks(sender);
        }
    }

    private void handleEntityCommand(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            if (sender instanceof Player player) {
                org.bg52.synthetix.gui.editor.entity.EntityBrowserGUI.open(player, plugin);
            } else {
                lang.send(sender, "command.entity-list-header", "%count%", String.valueOf(plugin.getCustomEntityRegistry().getAll().size()));
                for (var def : plugin.getCustomEntityRegistry().getAll()) {
                    sender.sendMessage(TextUtil.parse("<gray>• </gray><aqua>" + def.getId() + "</aqua> <dark_gray>(" + def.getBaseEntityType() + ")</dark_gray>"));
                }
            }
            return;
        }

        String subAction = args[1].toLowerCase(Locale.ROOT);
        switch (subAction) {
            case "browse", "menu", "gui", "listgui" -> {
                if (sender instanceof Player player) {
                    org.bg52.synthetix.gui.editor.entity.EntityBrowserGUI.open(player, plugin);
                }
            }
            case "create" -> {
                if (!(sender instanceof Player player)) {
                    lang.send(sender, "general.player-only");
                    return;
                }
                if (args.length < 3) {
                    plugin.getChatPromptManager().prompt(player,
                            lang.getComponent("command.entity-create-prompt"),
                            id -> openCreateEntityMenu(player, id.trim()));
                    return;
                }
                String entityId = args[2].trim();
                if (args.length >= 4) {
                    String modelQuery = args[3].trim();
                    File modelFile = plugin.getBedrockEntityImporter().findModelFile(modelQuery);
                    if (modelFile != null) {
                        lang.send(player, "command.entity-importing", "%file%", modelFile.getName());
                        var def = plugin.getBedrockEntityImporter().importEntity(modelFile, entityId);
                        if (def != null) {
                            lang.send(player, "command.entity-created", "%entity%", def.getId());
                            org.bg52.synthetix.gui.editor.entity.EntityEditorMenu.open(player, plugin, def);
                            return;
                        }
                    }
                }
                openCreateEntityMenu(player, entityId);
            }
            case "import", "bedrock", "bb" -> {
                if (args.length < 3) {
                    if (sender instanceof Player player) {
                        org.bg52.synthetix.gui.editor.entity.BedrockModelSelectorGUI.open(player, plugin);
                    } else {
                        lang.send(sender, "command.entity-import-usage", "%label%", "synth");
                    }
                    return;
                }
                String modelQuery = args[2].trim();
                File modelFile = plugin.getBedrockEntityImporter().findModelFile(modelQuery);
                if (modelFile == null) {
                    lang.send(sender, "command.entity-import-not-found", "%model%", modelQuery);
                    return;
                }
                String targetId = args.length >= 4 ? args[3].trim() : null;
                lang.send(sender, "command.entity-importing", "%file%", modelFile.getName());
                var def = plugin.getBedrockEntityImporter().importEntity(modelFile, targetId);
                if (def != null) {
                    lang.send(sender, "command.entity-created", "%entity%", def.getId());
                    if (sender instanceof Player player) {
                        org.bg52.synthetix.gui.editor.entity.EntityEditorMenu.open(player, plugin, def);
                    }
                } else {
                    lang.send(sender, "command.entity-import-failed");
                }
            }
            case "animate", "anim", "play" -> {
                if (args.length < 3) {
                    lang.send(sender, "command.entity-anim-usage", "%label%", "synth");
                    return;
                }
                String animName = args[2].trim().toLowerCase(Locale.ROOT);
                if (args.length >= 4) {
                    String targetId = args[3].trim().toLowerCase(Locale.ROOT);
                    int count = 0;
                    for (var inst : plugin.getCustomEntityManager().getActiveInstances()) {
                        if (inst.getDefinition().getId().equalsIgnoreCase(targetId)) {
                            inst.getAnimationController().play(animName, false, 2.0f);
                            count++;
                        }
                    }
                    lang.send(sender, "command.entity-anim-triggered", "%anim%", animName, "%count%", String.valueOf(count));
                } else if (sender instanceof Player player) {
                    var active = plugin.getCustomEntityManager().getActiveInstances();
                    var nearest = active.stream()
                            .filter(i -> i.getDriverMob() != null && i.getDriverMob().isValid())
                            .min(Comparator.comparingDouble(i -> i.getDriverMob().getLocation().distanceSquared(player.getLocation())))
                            .orElse(null);
                    if (nearest != null) {
                        nearest.getAnimationController().play(animName, false, 2.0f);
                        lang.send(sender, "command.entity-anim-triggered", "%anim%", animName, "%count%", "1");
                    }
                }
            }
            case "edit" -> {
                if (!(sender instanceof Player player)) {
                    lang.send(sender, "general.player-only");
                    return;
                }
                if (args.length < 3) {
                    org.bg52.synthetix.gui.editor.entity.EntityBrowserGUI.open(player, plugin);
                    return;
                }
                String entityId = args[2].trim().toLowerCase(Locale.ROOT);
                var def = plugin.getCustomEntityRegistry().get(entityId);
                if (def == null) {
                    lang.send(player, "command.entity-not-found-to-edit", "%entity%", entityId);
                    return;
                }
                org.bg52.synthetix.gui.editor.entity.EntityEditorMenu.open(player, plugin, def);
            }
            case "delete", "remove" -> {
                if (args.length < 3) {
                    lang.send(sender, "command.entity-delete-usage", "%label%", "synth");
                    return;
                }
                String entityId = args[2].trim().toLowerCase(Locale.ROOT);
                boolean deleted = plugin.getCustomEntityRegistry().delete(entityId);
                if (deleted) {
                    lang.send(sender, "command.entity-delete-success", "%entity%", entityId);
                } else {
                    lang.send(sender, "command.entity-delete-failed", "%entity%", entityId);
                }
            }
            case "spawn" -> {
                String[] spawnArgs = new String[args.length - 1];
                spawnArgs[0] = "spawn";
                System.arraycopy(args, 2, spawnArgs, 1, args.length - 2);
                handleSpawnCommand(sender, spawnArgs);
            }
            case "list" -> {
                lang.send(sender, "command.entity-list-header", "%count%", String.valueOf(plugin.getCustomEntityRegistry().getAll().size()));
                for (var def : plugin.getCustomEntityRegistry().getAll()) {
                    sender.sendMessage(TextUtil.parse("<gray>• </gray><aqua>" + def.getId() + "</aqua> <dark_gray>(" + def.getBaseEntityType() + ")</dark_gray>"));
                }
            }
            default -> {
                var def = plugin.getCustomEntityRegistry().get(subAction);
                if (def != null && sender instanceof Player player) {
                    org.bg52.synthetix.gui.editor.entity.EntityEditorMenu.open(player, plugin, def);
                } else {
                    lang.send(sender, "command.entity-unknown-sub", "%label%", "synth");
                }
            }
        }
    }

    private void handleSpawnCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            lang.send(sender, "command.entity-spawn-usage", "%label%", "synth");
            return;
        }
        String entityId = args[1].toLowerCase(Locale.ROOT);
        var def = plugin.getCustomEntityRegistry().get(entityId);
        if (def == null) {
            lang.send(sender, "command.entity-not-found", "%entity%", entityId);
            return;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Math.min(100, Integer.parseInt(args[2])));
            } catch (NumberFormatException ignored) {}
        }

        org.bukkit.Location loc;
        if (sender instanceof Player player) {
            loc = player.getLocation();
        } else {
            lang.send(sender, "general.player-only");
            return;
        }

        for (int i = 0; i < amount; i++) {
            plugin.getCustomEntityManager().spawnEntity(def, loc);
        }
        lang.send(sender, "command.entity-spawn-success", "%amount%", String.valueOf(amount), "%entity%", def.getId());
    }

    private void handleItems(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            lang.send(sender, "general.player-only");
            return;
        }
        ItemBrowserGUI.open(player, plugin);
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            lang.send(sender, "command.give-usage", "%label%", "synth");
            return;
        }

        int offset = 0;
        String typeFilter = null;
        if (args[1].equalsIgnoreCase("block") || args[1].equalsIgnoreCase("item")) {
            typeFilter = args[1].toLowerCase(Locale.ROOT);
            offset = 1;
        }

        if (args.length < 3 + offset) {
            lang.send(sender, "command.give-usage", "%label%", "synth");
            return;
        }

        Player target = Bukkit.getPlayer(args[1 + offset]);
        if (target == null) {
            lang.send(sender, "command.player-not-found", "%player%", args[1 + offset]);
            return;
        }

        String id = args[2 + offset].toLowerCase(Locale.ROOT);
        int amount = 1;
        if (args.length >= 4 + offset) {
            try {
                amount = Math.max(1, Integer.parseInt(args[3 + offset]));
            } catch (NumberFormatException ignored) {}
        }

        if ("block".equals(typeFilter)) {
            org.bg52.synthetix.block.CustomBlockDefinition bDef = plugin.getCustomBlockRegistry().get(id);
            if (bDef != null) {
                target.getInventory().addItem(bDef.buildBlockItemStack(amount));
                lang.send(sender, "command.give-block-success", "%amount%", String.valueOf(amount), "%block%", id, "%player%", target.getName());
                lang.send(target, "command.received-block", "%amount%", String.valueOf(amount), "%block%", id);
                return;
            }
        }

        // Check custom block registry
        org.bg52.synthetix.block.CustomBlockDefinition bDef = plugin.getCustomBlockRegistry().get(id);
        if (bDef != null && !"item".equals(typeFilter)) {
            target.getInventory().addItem(bDef.buildBlockItemStack(amount));
            lang.send(sender, "command.give-block-success", "%amount%", String.valueOf(amount), "%block%", id, "%player%", target.getName());
            lang.send(target, "command.received-block", "%amount%", String.valueOf(amount), "%block%", id);
            return;
        }

        // Check custom item registry
        ItemDefinition def = plugin.getItemRegistry().get(id);
        if (def != null) {
            ItemStack item = def.buildItemStack();
            item.setAmount(amount);
            target.getInventory().addItem(item);
            lang.send(sender, "command.give-success", "%amount%", String.valueOf(amount), "%item%", id, "%player%", target.getName());
            lang.send(target, "command.received-item", "%amount%", String.valueOf(amount), "%item%", id);
            return;
        }

        lang.send(sender, "command.unknown-item", "%item%", id);
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            lang.send(sender, "command.delete-usage", "%label%", "synth");
            return;
        }

        if (args[1].equalsIgnoreCase("block") && args.length >= 3) {
            String blockId = args[2].toLowerCase(Locale.ROOT);
            boolean ok = plugin.getCustomBlockRegistry().delete(blockId);
            if (ok) {
                lang.send(sender, "command.delete-block-success", "%block%", blockId);
            } else {
                lang.send(sender, "command.block-not-found", "%block%", blockId);
            }
            return;
        }

        if ((args[1].equalsIgnoreCase("entity") || args[1].equalsIgnoreCase("mob")) && args.length >= 3) {
            String entityId = args[2].toLowerCase(Locale.ROOT);
            boolean ok = plugin.getCustomEntityRegistry().delete(entityId);
            if (ok) {
                sender.sendMessage(TextUtil.parse("<green>✓ Deleted custom entity <white>" + entityId + "</white>.</green>"));
            } else {
                sender.sendMessage(TextUtil.parse("<red>Custom entity <white>" + entityId + "</white> not found.</red>"));
            }
            return;
        }

        if (args[1].equalsIgnoreCase("item") && args.length >= 3) {
            String itemId = args[2].toLowerCase(Locale.ROOT);
            boolean ok = plugin.getItemRegistry().delete(itemId);
            if (ok) {
                lang.send(sender, "command.delete-success", "%item%", itemId);
            } else {
                lang.send(sender, "command.unknown-item", "%item%", itemId);
            }
            return;
        }

        String cleanId = args[1].trim().toLowerCase(Locale.ROOT);
        if (plugin.getCustomBlockRegistry().get(cleanId) != null) {
            plugin.getCustomBlockRegistry().delete(cleanId);
            lang.send(sender, "command.delete-block-success", "%block%", cleanId);
            return;
        }

        if (plugin.getCustomEntityRegistry().get(cleanId) != null) {
            plugin.getCustomEntityRegistry().delete(cleanId);
            sender.sendMessage(TextUtil.parse("<green>✓ Deleted custom entity <white>" + cleanId + "</white>.</green>"));
            return;
        }

        ItemDefinition def = plugin.getItemRegistry().get(cleanId);
        if (def != null) {
            plugin.getItemRegistry().delete(cleanId);
            lang.send(sender, "command.delete-success", "%item%", cleanId);
            return;
        }

        lang.send(sender, "command.unknown-item", "%item%", cleanId);
    }

    private void handleList(CommandSender sender) {
        var items = plugin.getItemRegistry().getAll();
        lang.sendRaw(sender, "command.list-header", "%count%", String.valueOf(items.size()));
        if (items.isEmpty()) {
            lang.sendRaw(sender, "command.list-empty");
        } else {
            Component listComp = Component.empty();
            boolean first = true;
            for (ItemDefinition def : items) {
                if (!first) {
                    listComp = listComp.append(TextUtil.parse("<gray>, </gray>"));
                }
                first = false;

                Component itemNode = TextUtil.parse("<yellow>" + def.getId() + "</yellow>")
                        .hoverEvent(HoverEvent.showText(TextUtil.parse("<gold>" + def.getDisplayName() + "</gold><br><gray>Click to edit in GUI</gray>")))
                        .clickEvent(ClickEvent.runCommand("/synth edit item " + def.getId()));
                listComp = listComp.append(itemNode);
            }
            sender.sendMessage(listComp);
        }

        var blocks = plugin.getCustomBlockRegistry().getAll();
        lang.sendRaw(sender, "command.list-blocks-header", "%count%", String.valueOf(blocks.size()));
        if (blocks.isEmpty()) {
            lang.sendRaw(sender, "command.list-blocks-empty");
        } else {
            Component blockListComp = Component.empty();
            boolean firstBlock = true;
            for (var bDef : blocks) {
                if (!firstBlock) {
                    blockListComp = blockListComp.append(TextUtil.parse("<gray>, </gray>"));
                }
                firstBlock = false;

                Component blockNode = TextUtil.parse("<yellow>" + bDef.getId() + "</yellow>")
                        .hoverEvent(HoverEvent.showText(TextUtil.parse("<gold>" + bDef.getDisplayName() + "</gold><br><gray>Click to edit in Block Editor</gray>")))
                        .clickEvent(ClickEvent.runCommand("/synth edit block " + bDef.getId()));
                blockListComp = blockListComp.append(blockNode);
            }
            sender.sendMessage(blockListComp);
        }
    }

    private void handleRecipe(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            lang.send(sender, "general.player-only");
            return;
        }

        String targetId = null;
        if (args.length >= 2) {
            targetId = args[1].trim().toLowerCase(Locale.ROOT);
        } else {
            // Check held item
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand != null && !hand.getType().isAir()) {
                targetId = plugin.getItemRegistry().getItemId(hand);
            }
        }

        if (targetId == null) {
            ItemBrowserGUI.open(player, plugin);
            return;
        }

        org.bg52.synthetix.gui.recipe.RecipeViewerGUI.open(player, plugin, targetId, null);
    }

    private void handleSets(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            lang.send(sender, "general.player-only");
            return;
        }
        org.bg52.synthetix.gui.set.SetListGUI.open(player, plugin);
    }

    private void handleKeybinds(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            lang.send(sender, "general.player-only");
            return;
        }
        org.bg52.synthetix.gui.keybind.KeybindListGUI.open(player, plugin);
    }

    private void handleExport(CommandSender sender, String[] args) {
        if (args.length < 2) {
            lang.send(sender, "command.export-usage", "%label%", "synth");
            return;
        }

        if ("geyser".equalsIgnoreCase(args[1]) || "bedrock".equalsIgnoreCase(args[1])) {
            handleGeyser(sender, new String[]{"geyser", "export"});
            return;
        }

        List<String> targetIds = new ArrayList<>();
        String targetArg = args[1].trim();

        if ("all".equalsIgnoreCase(targetArg)) {
            for (ItemDefinition def : plugin.getItemRegistry().getAll()) {
                targetIds.add(def.getId());
            }
            for (var bDef : plugin.getCustomBlockRegistry().getAll()) {
                if (!targetIds.contains(bDef.getId())) {
                    targetIds.add(bDef.getId());
                }
            }
        } else if (targetArg.contains(",")) {
            for (String part : targetArg.split(",")) {
                if (!part.trim().isEmpty()) targetIds.add(part.trim().toLowerCase(Locale.ROOT));
            }
        } else {
            targetIds.add(targetArg.toLowerCase(Locale.ROOT));
        }

        lang.send(sender, "command.export-packaging", "%count%", String.valueOf(targetIds.size()));
        java.io.File bundle = plugin.getExportManager().exportBundle(targetIds, null);
        if (bundle != null && bundle.exists()) {
            lang.send(sender, "command.export-success", "%file%", bundle.getName(), "%size%", String.valueOf(bundle.length() / 1024));
            lang.send(sender, "command.export-location", "%file%", bundle.getName());
        } else {
            lang.send(sender, "command.export-failed");
        }
    }

    private void handleImport(CommandSender sender, String[] args) {
        String category = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "all";
        lang.send(sender, "command.import-scanning", "%category%", category);

        int items = 0;
        int blocks = 0;
        int entities = 0;
        int bundles = 0;

        switch (category) {
            case "items", "item" -> {
                items = plugin.getBlockbenchImporter().importPendingItems(true);
            }
            case "blocks", "block" -> {
                blocks = plugin.getBlockbenchImporter().importPendingBlocks(true);
            }
            case "entities", "entity", "mobs", "mob" -> {
                entities = plugin.getBedrockEntityImporter().importPendingEntities(true);
            }
            case "all" -> {
                items = plugin.getBlockbenchImporter().importPendingItems(true);
                blocks = plugin.getBlockbenchImporter().importPendingBlocks(true);
                entities = plugin.getBedrockEntityImporter().importPendingEntities(true);
                bundles = plugin.getImportManager().scanAndImportAll();
            }
            default -> {
                lang.send(sender, "command.import-usage", "%label%", "synth");
                return;
            }
        }

        int total = items + blocks + entities + bundles;
        if (total > 0) {
            // Hot reload registries
            plugin.getItemRegistry().loadAll();
            plugin.getCustomBlockRegistry().loadAll();
            plugin.getCustomEntityRegistry().loadAll();
            plugin.getResourcePackManager().generatePackAsync();
            lang.send(sender, "command.import-success",
                    "%total%", String.valueOf(total),
                    "%items%", String.valueOf(items),
                    "%blocks%", String.valueOf(blocks),
                    "%entities%", String.valueOf(entities));
        } else {
            lang.send(sender, "command.import-none", "%category%", category);
        }
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadPluginConfig();
        lang.send(sender, "general.reloaded");
    }

    private void handlePack(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendPackInfo(sender);
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "rebuild", "build", "compile" -> {
                lang.send(sender, "command.pack-rebuilding");
                plugin.getResourcePackManager().generatePackAsync().thenRun(() -> {
                    lang.send(sender, "command.pack-rebuild-success", "%hash%", plugin.getResourcePackManager().getPackHash());
                }).exceptionally(ex -> {
                    lang.send(sender, "command.pack-rebuild-failed", "%error%", ex.getMessage());
                    return null;
                });
            }
            case "distribute", "redistribute" -> {
                String target = args.length >= 3 ? args[2] : "all";
                lang.send(sender, "command.pack-distributing");
                plugin.getResourcePackManager().redistributePack(sender, target).thenAccept(ok -> {
                    if (ok) {
                        if (plugin.getResourcePackManager().getPackHostManager().isActive()) {
                            lang.send(sender, "command.pack-distribute-host-success", "%host%", plugin.getResourcePackManager().getPackHostManager().getActiveHost().getDisplayName());
                        } else {
                            lang.send(sender, "command.pack-distribute-success", "%target%", target);
                        }
                    } else {
                        lang.send(sender, "command.pack-distribute-failed", "%error%", "Could not deliver to target");
                    }
                });
            }
            case "reload" -> {
                plugin.getResourcePackManager().reload();
                lang.send(sender, "command.pack-reload-success");
            }
            case "prompt", "send", "apply" -> {
                String target = args.length >= 3 ? args[2] : (sender instanceof Player p ? p.getName() : "all");
                plugin.getResourcePackManager().redistributePack(sender, target);
            }
            case "info", "status" -> sendPackInfo(sender);
            default -> sendPackInfo(sender);
        }
    }

    private void sendPackInfo(CommandSender sender) {
        var rpm = plugin.getResourcePackManager();
        lang.sendRaw(sender, "command.pack-info-header");
        lang.sendRaw(sender, "command.pack-info-hosting", "%mode%", rpm.getHostingMode().name() + " (Effective: " + rpm.getEffectiveHostingMode().name() + ")");

        if (rpm.getPackHostManager().isActive()) {
            sender.sendMessage(TextUtil.parse("<gray>Delegated Host:</gray> <green>" + rpm.getPackHostManager().getActiveHost().getDisplayName() + "</green>"));
            if (rpm.getPackHostManager().getLastInjectionStatus() != null) {
                sender.sendMessage(TextUtil.parse("<gray>Last Injection:</gray> <white>" + rpm.getPackHostManager().getLastInjectionStatus() + "</white>"));
            }
        } else if (rpm.getEffectiveHostingMode() == org.bg52.synthetix.resourcepack.ResourcePackManager.HostingMode.SELF) {
            sender.sendMessage(TextUtil.parse("<gray>Server URL:</gray> <aqua>" + rpm.getPackUrl() + "</aqua>"));
            sender.sendMessage(TextUtil.parse("<gray>Resolved IP:</gray> <white>" + rpm.getIpDetector().resolveExternalIp() + "</white>"));
        } else if (rpm.getEffectiveHostingMode() == org.bg52.synthetix.resourcepack.ResourcePackManager.HostingMode.LINK) {
            sender.sendMessage(TextUtil.parse("<gray>Direct Link:</gray> <aqua>" + rpm.getPackUrl() + "</aqua>"));
        }

        lang.sendRaw(sender, "command.pack-info-hash", "%hash%", (rpm.getPackHash() != null ? rpm.getPackHash() : "Not compiled yet"));
        java.io.File zip = rpm.getPackFile();
        lang.sendRaw(sender, "command.pack-info-size", "%size%", (zip.exists() ? (zip.length() / 1024) + " KB" : "0 KB"));
        lang.sendRaw(sender, "command.pack-info-cmd-rebuild");
        lang.sendRaw(sender, "command.pack-info-cmd-distribute");
        lang.sendRaw(sender, "command.pack-info-cmd-reload");
        lang.sendRaw(sender, "command.pack-info-cmd-prompt");
    }

    private void handleGeyser(CommandSender sender, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "status";
        var bm = plugin.getBedrockManager();
        if (bm == null) {
            lang.send(sender, "bedrock.not-initialized");
            return;
        }

        switch (action) {
            case "export", "rebuild", "build", "generate" -> {
                lang.send(sender, "bedrock.compiling");
                bm.getPackGenerator().generateAndDeployAsync().thenAccept(file -> {
                    lang.send(sender, "bedrock.compile-success", "%file%", file.getName(), "%size%", String.valueOf(file.length() / 1024));
                }).exceptionally(ex -> {
                    lang.send(sender, "bedrock.compile-failed", "%error%", ex.getMessage() != null ? ex.getMessage() : "Unknown");
                    return null;
                });
            }
            case "status", "info" -> {
                lang.sendRaw(sender, "bedrock.status-header");
                lang.sendRaw(sender, "bedrock.status-floodgate", "%status%", bm.getBridge().isFloodgatePresent() ? "<green>ACTIVE</green>" : "<dark_gray>NOT DETECTED</dark_gray>");
                lang.sendRaw(sender, "bedrock.status-geyser", "%status%", bm.getBridge().isGeyserPresent() ? "<green>ACTIVE</green>" : "<dark_gray>NOT DETECTED</dark_gray>");
                lang.sendRaw(sender, "bedrock.status-players", "%count%", String.valueOf(bm.getBridge().getBedrockPlayerUuids().size()));
                java.io.File pack = bm.getPackGenerator().getGeyserPackZip();
                lang.sendRaw(sender, "bedrock.status-pack", "%size%", pack.exists() ? (pack.length() / 1024) + " KB" : "<dark_gray>Not built</dark_gray>");
                java.io.File map = bm.getPackGenerator().getGeyserMappingsFile();
                lang.sendRaw(sender, "bedrock.status-mappings", "%status%", map.exists() ? "<green>GENERATED</green>" : "<dark_gray>Missing</dark_gray>");
                lang.sendRaw(sender, "bedrock.status-hint");
            }
            default -> {
                lang.send(sender, "bedrock.usage");
            }
        }
    }

    private void handleInfo(CommandSender sender) {
        lang.sendRaw(sender, "command.info-header");
        lang.sendRaw(sender, "command.info-version", "%version%", plugin.getDescription().getVersion());
        lang.sendRaw(sender, "command.info-target", "%target%", VersionChecker.getFullVersionString());
        lang.sendRaw(sender, "command.info-modern", "%modern%", VersionChecker.isPaperModernEra() ? "<green>Yes</green>" : "<red>No</red>");
        lang.sendRaw(sender, "command.info-components", "%components%", VersionChecker.supportsDataComponents() ? "<green>Supported</green>" : "<red>Unavailable</red>");
        lang.sendRaw(sender, "command.info-item-model", "%item_model%", VersionChecker.supportsItemModel() ? "<green>Supported</green>" : "<red>Unavailable</red>");
        lang.sendRaw(sender, "command.info-items", "%count%", String.valueOf(plugin.getItemRegistry().getAll().size()));
        lang.sendRaw(sender, "command.info-blocks", "%count%", String.valueOf(plugin.getCustomBlockRegistry().getAll().size()));
        sender.sendMessage(TextUtil.parse("<dark_gray>•</dark_gray> <gray>Custom Entities:</gray> <yellow>" + plugin.getCustomEntityRegistry().getAll().size() + "</yellow> <gray>(Active: <white>" + plugin.getCustomEntityManager().getActiveInstances().size() + "</white>)</gray>"));
        lang.sendRaw(sender, "command.info-placed-blocks", "%count%", String.valueOf(plugin.getCustomBlockManager().getActiveBlockCount()));
        if (plugin.getCustomBlockManager() != null && plugin.getCustomBlockManager().getCullingManager() != null) {
            var cm = plugin.getCustomBlockManager().getCullingManager();
            String cullingStatus = cm.isEnabled() ? "<green>Active (Limit: " + cm.getMaxRenderedPerPlayer() + " blocks/player, Dist: " + (int) cm.getMaxDistance() + "m)</green>" : "<red>Disabled</red>";
            sender.sendMessage(TextUtil.parse("<dark_gray>•</dark_gray> <gray>Block Culling:</gray> " + cullingStatus));
        }
        lang.sendRaw(sender, "command.info-recipes", "%count%", String.valueOf(plugin.getRecipeManager().getRecipes().size()));
        lang.sendRaw(sender, "command.info-mob-drops", "%count%", String.valueOf(plugin.getDistributionManager().getMobDropManager().getRules().size()));
        lang.sendRaw(sender, "command.info-loot-tables", "%count%", String.valueOf(plugin.getDistributionManager().getLootTableInjector().getRules().size()));
        lang.sendRaw(sender, "command.info-trades", "%count%", String.valueOf(plugin.getDistributionManager().getTradeInjector().getRules().size()));
    }

    private void handleMigrate(CommandSender sender) {
        sender.sendMessage(TextUtil.parse("<yellow>[Synthetix]</yellow> <gray>Scanning and migrating legacy CuriosPaper & addon databases...</gray>"));
        plugin.getStorageManager().getMigrationManager().runMigration().thenAccept(report -> {
            sender.sendMessage(TextUtil.parse("<green>[Synthetix]</green> <white>Migration completed!</white>"));
            sender.sendMessage(TextUtil.parse("<dark_gray>•</dark_gray> <gray>Curios Inventories Migrated:</gray> <yellow>" + report.getMigratedCuriosPlayers() + "</yellow>"));
            sender.sendMessage(TextUtil.parse("<dark_gray>•</dark_gray> <gray>Addon Records Migrated:</gray> <yellow>" + report.getMigratedAddonRecords() + "</yellow>"));
            sender.sendMessage(TextUtil.parse("<dark_gray>•</dark_gray> <gray>Backpacks Migrated:</gray> <yellow>" + report.getMigratedBackpacks() + "</yellow>"));
            for (String msg : report.getLogMessages()) {
                sender.sendMessage(TextUtil.parse("<dark_gray>  *</dark_gray> <gray>" + msg + "</gray>"));
            }
        }).exceptionally(ex -> {
            sender.sendMessage(TextUtil.parse("<red>[Synthetix] Migration error: " + ex.getMessage() + "</red>"));
            return null;
        });
    }

    private void handleRebuildItems(CommandSender sender, String[] args) {
        sender.sendMessage(TextUtil.parse("<yellow>[Synthetix]</yellow> <gray>Scanning online players' inventories and rebuilding outdated items...</gray>"));
        var updateMgr = plugin.getItemUpdateManager();
        if (updateMgr == null) {
            sender.sendMessage(TextUtil.parse("<red>[Synthetix] ItemUpdateManager is not initialized.</red>"));
            return;
        }

        int inspected = 0;
        int rebuilt = 0;

        for (Player p : Bukkit.getOnlinePlayers()) {
            ItemStack[] contents = p.getInventory().getContents();
            boolean changed = false;
            for (int i = 0; i < contents.length; i++) {
                ItemStack st = contents[i];
                if (st != null && st.getType() != Material.AIR) {
                    inspected++;
                    if (updateMgr.isItemOutdated(st)) {
                        updateMgr.rebuildItem(st);
                        rebuilt++;
                        changed = true;
                    }
                }
            }
            if (changed) {
                p.getInventory().setContents(contents);
            }

            ItemStack[] ec = p.getEnderChest().getContents();
            boolean ecChanged = false;
            for (int i = 0; i < ec.length; i++) {
                ItemStack st = ec[i];
                if (st != null && st.getType() != Material.AIR) {
                    inspected++;
                    if (updateMgr.isItemOutdated(st)) {
                        updateMgr.rebuildItem(st);
                        rebuilt++;
                        ecChanged = true;
                    }
                }
            }
            if (ecChanged) {
                p.getEnderChest().setContents(ec);
            }
        }

        sender.sendMessage(TextUtil.parse("<green>[Synthetix]</green> <white>Item rebuild scan finished!</white>"));
        sender.sendMessage(TextUtil.parse("<dark_gray>•</dark_gray> <gray>Items Inspected:</gray> <yellow>" + inspected + "</yellow>"));
        sender.sendMessage(TextUtil.parse("<dark_gray>•</dark_gray> <gray>Outdated Items Rebuilt:</gray> <green>" + rebuilt + "</green>"));
    }

    private void handleSettings(CommandSender sender, String[] args) {
        GlobalSettingsManager sm = plugin.getSettingsManager();
        if (sm == null) {
            sender.sendMessage(TextUtil.parse("<red>GlobalSettingsManager is not available.</red>"));
            return;
        }

        // 1. /synth settings OR /synth settings gui
        if (args.length == 1 || (args.length == 2 && args[1].equalsIgnoreCase("gui"))) {
            if (sender instanceof Player player) {
                new GlobalSettingsGUI(plugin).open(player);
            } else {
                handleSettingsList(sender);
            }
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list" -> handleSettingsList(sender);
            case "toggle" -> {
                if (args.length < 3) {
                    sender.sendMessage(TextUtil.parse("<yellow>Usage:</yellow> <gray>/synth settings toggle <feature-id></gray>"));
                    sender.sendMessage(TextUtil.parse("<dark_gray>Run <yellow>/synth settings list</yellow> to view all feature IDs.</dark_gray>"));
                    return;
                }
                String featKey = args[2].toLowerCase(Locale.ROOT);
                GlobalFeature feat = GlobalFeature.fromConfigKey(featKey);
                if (feat == null) {
                    try {
                        feat = GlobalFeature.valueOf(featKey.toUpperCase(Locale.ROOT).replace('-', '_'));
                    } catch (IllegalArgumentException ignored) {}
                }
                if (feat == null) {
                    sender.sendMessage(TextUtil.parse("<red>Unknown feature: <white>" + featKey + "</white>. Use <yellow>/synth settings list</yellow> to see valid keys.</red>"));
                    return;
                }
                boolean newState = sm.toggleFeature(feat);
                sender.sendMessage(TextUtil.parse("<dark_gray>[<gradient:#ff0080:#7928ca>Synthetix</gradient>]</dark_gray> Feature <aqua>" + feat.getConfigKey() + "</aqua> is now " + (newState ? "<green><bold>ENABLED</bold></green>" : "<red><bold>DISABLED</bold></red>") + "."));
                if (sender instanceof Player p) {
                    p.playSound(p.getLocation(), newState ? org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING : org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, newState ? 1.5f : 0.8f);
                }
            }
            case "set" -> {
                if (args.length < 4) {
                    sender.sendMessage(TextUtil.parse("<yellow>Usage:</yellow> <gray>/synth settings set <feature-id> <true|false></gray>"));
                    return;
                }
                String featKey = args[2].toLowerCase(Locale.ROOT);
                GlobalFeature feat = GlobalFeature.fromConfigKey(featKey);
                if (feat == null) {
                    try {
                        feat = GlobalFeature.valueOf(featKey.toUpperCase(Locale.ROOT).replace('-', '_'));
                    } catch (IllegalArgumentException ignored) {}
                }
                if (feat == null) {
                    sender.sendMessage(TextUtil.parse("<red>Unknown feature: <white>" + featKey + "</white>. Use <yellow>/synth settings list</yellow> to see valid keys.</red>"));
                    return;
                }
                String valStr = args[3].toLowerCase(Locale.ROOT);
                boolean enabled = valStr.equals("true") || valStr.equals("on") || valStr.equals("yes") || valStr.equals("1") || valStr.equals("enable") || valStr.equals("enabled");
                sm.setFeatureEnabled(feat, enabled);
                sender.sendMessage(TextUtil.parse("<dark_gray>[<gradient:#ff0080:#7928ca>Synthetix</gradient>]</dark_gray> Feature <aqua>" + feat.getConfigKey() + "</aqua> set to " + (enabled ? "<green><bold>ENABLED</bold></green>" : "<red><bold>DISABLED</bold></red>") + "."));
                if (sender instanceof Player p) {
                    p.playSound(p.getLocation(), enabled ? org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING : org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, enabled ? 1.5f : 0.8f);
                }
            }
            case "recipe", "recipes" -> {
                if (args.length < 3) {
                    sender.sendMessage(TextUtil.parse("<yellow>Usage:</yellow> <gray>/synth settings recipe <recipeId> [enable|disable|toggle]</gray>"));
                    return;
                }
                String recipeId = args[2];
                SynthetixRecipe recipe = plugin.getRecipeManager().getRecipe(recipeId);
                if (recipe == null) {
                    sender.sendMessage(TextUtil.parse("<red>Recipe with ID '<white>" + recipeId + "</white>' not found.</red>"));
                    return;
                }
                String recAction = args.length >= 4 ? args[3].toLowerCase(Locale.ROOT) : "toggle";
                boolean targetState = switch (recAction) {
                    case "enable", "on", "true" -> true;
                    case "disable", "off", "false" -> false;
                    default -> !recipe.isEnabled();
                };
                try {
                    plugin.getRecipeManager().setRecipeEnabled(recipe.getId(), targetState);
                    sender.sendMessage(TextUtil.parse("<dark_gray>[<gradient:#ff0080:#7928ca>Synthetix</gradient>]</dark_gray> Recipe <aqua>" + recipe.getId() + "</aqua> is now " + (targetState ? "<green><bold>ENABLED</bold></green>" : "<red><bold>DISABLED</bold></red>") + " (saved persistently)."));
                    if (sender instanceof Player p) {
                        p.playSound(p.getLocation(), targetState ? org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING : org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, targetState ? 1.5f : 0.8f);
                    }
                } catch (Exception e) {
                    sender.sendMessage(TextUtil.parse("<red>Failed to update recipe: " + e.getMessage() + "</red>"));
                }
            }
            default -> {
                sender.sendMessage(TextUtil.parse("<yellow>Usage:</yellow> <gray>/synth settings [gui|list|toggle|set|recipe]</gray>"));
            }
        }
    }

    private void handleSettingsList(CommandSender sender) {
        GlobalSettingsManager sm = plugin.getSettingsManager();
        if (sm == null) return;
        sender.sendMessage(TextUtil.parse("<dark_gray>======== [<gradient:#ff0080:#7928ca>Synthetix Global Settings</gradient>] ========</dark_gray>"));
        for (GlobalFeature f : GlobalFeature.values()) {
            boolean enabled = sm.isFeatureEnabled(f);
            String status = enabled ? "<green><bold>[ENABLED]</bold></green>" : "<red><bold>[DISABLED]</bold></red>";
            sender.sendMessage(TextUtil.parse("<dark_gray>•</dark_gray> <aqua>" + f.getConfigKey() + "</aqua> " + status + " <dark_gray>-</dark_gray> <gray>" + f.getDescription() + "</gray>"));
        }
        sender.sendMessage(TextUtil.parse("<dark_gray>Use <yellow>/synth settings toggle &lt;feature&gt;</yellow> or <yellow>/synth settings gui</yellow> to change toggles.</dark_gray>"));
    }

    private void sendHelp(CommandSender sender, String label) {
        lang.sendRaw(sender, "command.help-header");
        if (hasSubcommandPermission(sender, "settings")) {
            sender.sendMessage(TextUtil.parse("<yellow>/" + label + " settings [gui|list|toggle|set|recipe]</yellow> <dark_gray>-</dark_gray> <gray>Quick global settings & recipe controls</gray>"));
        }
        if (hasSubcommandPermission(sender, "items")) {
            lang.sendRaw(sender, "command.help-items", "%label%", label);
        }
        if (hasSubcommandPermission(sender, "blocks")) {
            lang.sendRaw(sender, "command.help-blocks", "%label%", label);
        }
        if (hasSubcommandPermission(sender, "recipe")) {
            lang.sendRaw(sender, "command.help-recipe", "%label%", label);
        }
        if (hasSubcommandPermission(sender, "sets")) {
            lang.sendRaw(sender, "command.help-sets", "%label%", label);
        }
        if (hasSubcommandPermission(sender, "give")) {
            lang.sendRaw(sender, "command.help-give", "%label%", label);
        }
        if (hasSubcommandPermission(sender, "create")) {
            lang.sendRaw(sender, "command.help-create", "%label%", label);
            lang.sendRaw(sender, "command.help-create-block", "%label%", label);
        }
        if (hasSubcommandPermission(sender, "edit")) {
            lang.sendRaw(sender, "command.help-edit", "%label%", label);
            lang.sendRaw(sender, "command.help-edit-block", "%label%", label);
        }
        if (sender.hasPermission("synthetix.admin")) {
            sender.sendMessage(TextUtil.parse("<yellow>/" + label + " entity</yellow> <gray>- Open custom entities browser / editor</gray>"));
            sender.sendMessage(TextUtil.parse("<yellow>/" + label + " entity import &lt;model&gt; [id]</yellow> <gray>- Import Bedrock (.bbmodel, .geo.json) with animations</gray>"));
            sender.sendMessage(TextUtil.parse("<yellow>/" + label + " entity animate &lt;anim&gt; [id]</yellow> <gray>- Play animation on entity</gray>"));
            sender.sendMessage(TextUtil.parse("<yellow>/" + label + " spawn &lt;id&gt; [amount]</yellow> <gray>- Spawn custom entity</gray>"));
            lang.sendRaw(sender, "command.help-clone", "%label%", label);
            lang.sendRaw(sender, "command.help-delete", "%label%", label);
            lang.sendRaw(sender, "command.help-list", "%label%", label);
            lang.sendRaw(sender, "command.help-blockbench", "%label%", label);
            lang.sendRaw(sender, "command.help-export", "%label%", label);
            lang.sendRaw(sender, "command.help-import", "%label%", label);
            lang.sendRaw(sender, "command.help-pack", "%label%", label);
            sender.sendMessage(TextUtil.parse("<yellow>/" + label + " geyser [status|export|rebuild]</yellow> <gray>- Bedrock / Geyser compatibility pack & protocol status</gray>"));
            sender.sendMessage(TextUtil.parse("<yellow>/" + label + " update-database</yellow> <gray>- Migrate legacy CuriosPaper & addon databases to Synthetix</gray>"));
            sender.sendMessage(TextUtil.parse("<yellow>/" + label + " rebuild-items</yellow> <gray>- Scan and rebuild outdated items to modern definitions</gray>"));
            lang.sendRaw(sender, "command.help-reload", "%label%", label);
            lang.sendRaw(sender, "command.help-info", "%label%", label);
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (alias.equalsIgnoreCase("synthpack")) {
            if (!sender.hasPermission("synthetix.admin")) return List.of();
            if (args.length == 1) {
                List<String> packSubs = List.of("rebuild", "distribute", "redistribute", "reload", "info", "status", "prompt", "send");
                String prefix = args[0].toLowerCase(Locale.ROOT);
                List<String> result = new ArrayList<>();
                for (String s : packSubs) {
                    if (s.startsWith(prefix)) result.add(s);
                }
                return result;
            }
            if (args.length == 2) {
                String sub = args[0].toLowerCase(Locale.ROOT);
                if ("prompt".equals(sub) || "send".equals(sub) || "distribute".equals(sub) || "redistribute".equals(sub) || "apply".equals(sub)) {
                    String prefix = args[1].toLowerCase(Locale.ROOT);
                    List<String> result = new ArrayList<>();
                    if ("all".startsWith(prefix)) result.add("all");
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) result.add(p.getName());
                    }
                    return result;
                }
            }
            return List.of();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subs = List.of("recipe", "items", "blocks", "sets", "settings", "toggle", "give", "create", "edit", "block", "entity", "entities", "mobs", "spawn", "blockbench", "clone", "delete", "list", "keybinds", "export", "import", "pack", "geyser", "bedrock", "update-database", "rebuild-items", "reload", "info");
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String s : subs) {
                if (s.startsWith(prefix) && hasSubcommandPermission(sender, s)) {
                    completions.add(s);
                }
            }
            Collections.sort(completions);
            return completions;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!hasSubcommandPermission(sender, sub)) {
            return List.of();
        }

        if ("settings".equals(sub) || "setting".equals(sub) || "toggle".equals(sub) || "toggles".equals(sub)) {
            if (args.length == 2) {
                List<String> options = List.of("gui", "list", "toggle", "set", "recipe");
                String prefix = args[1].toLowerCase(Locale.ROOT);
                return options.stream().filter(o -> o.startsWith(prefix)).toList();
            }
            if (args.length == 3) {
                String action = args[1].toLowerCase(Locale.ROOT);
                if ("toggle".equals(action) || "set".equals(action)) {
                    String prefix = args[2].toLowerCase(Locale.ROOT);
                    return Arrays.stream(GlobalFeature.values())
                            .map(GlobalFeature::getConfigKey)
                            .filter(k -> k.startsWith(prefix))
                            .toList();
                } else if ("recipe".equals(action) || "recipes".equals(action)) {
                    String prefix = args[2].toLowerCase(Locale.ROOT);
                    return plugin.getRecipeManager().getAllRecipes().stream()
                            .map(SynthetixRecipe::getId)
                            .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(prefix))
                            .toList();
                }
            }
            if (args.length == 4) {
                String action = args[1].toLowerCase(Locale.ROOT);
                if ("set".equals(action)) {
                    String prefix = args[3].toLowerCase(Locale.ROOT);
                    return List.of("true", "false").stream().filter(s -> s.startsWith(prefix)).toList();
                } else if ("recipe".equals(action) || "recipes".equals(action)) {
                    String prefix = args[3].toLowerCase(Locale.ROOT);
                    return List.of("enable", "disable", "toggle").stream().filter(s -> s.startsWith(prefix)).toList();
                }
            }
            return List.of();
        }

        if (args.length == 2) {
            if ("geyser".equals(sub) || "bedrock".equals(sub)) {
                List<String> gActions = List.of("status", "export", "rebuild");
                String prefix = args[1].toLowerCase(Locale.ROOT);
                for (String a : gActions) {
                    if (a.startsWith(prefix)) completions.add(a);
                }
                return completions;
            }
            if ("entity".equals(sub) || "entities".equals(sub) || "mobs".equals(sub) || "mob".equals(sub)) {
                List<String> eActions = List.of("browse", "create", "edit", "delete", "spawn", "list", "import", "bedrock", "animate");
                String prefix = args[1].toLowerCase(Locale.ROOT);
                for (String a : eActions) {
                    if (a.startsWith(prefix)) completions.add(a);
                }
                for (var def : plugin.getCustomEntityRegistry().getAll()) {
                    if (def.getId().toLowerCase(Locale.ROOT).startsWith(prefix)) completions.add(def.getId());
                }
                return completions;
            }
            if ("spawn".equals(sub)) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                for (var def : plugin.getCustomEntityRegistry().getAll()) {
                    if (def.getId().toLowerCase(Locale.ROOT).startsWith(prefix)) completions.add(def.getId());
                }
                return completions;
            }
            if ("block".equals(sub)) {
                List<String> bActions = List.of("browse", "clone", "create", "edit", "delete", "give", "list", "culling", "animation", "group");
                String prefix = args[1].toLowerCase(Locale.ROOT);
                for (String a : bActions) {
                    if (a.startsWith(prefix)) completions.add(a);
                }
                return completions;
            }
            if ("create".equals(sub)) {
                List<String> types = List.of("item", "block", "entity");
                String prefix = args[1].toLowerCase(Locale.ROOT);
                for (String t : types) {
                    if (t.startsWith(prefix)) completions.add(t);
                }
                return completions;
            }
            if ("blockbench".equals(sub) || "bb".equals(sub)) {
                List<String> bbSubs = List.of("item", "block", "import", "list", "scan");
                String prefix = args[1].toLowerCase(Locale.ROOT);
                for (String s : bbSubs) {
                    if (s.startsWith(prefix)) completions.add(s);
                }
                return completions;
            }
            if ("import".equals(sub)) {
                List<String> importCategories = List.of("items", "blocks", "entities", "all");
                String prefix = args[1].toLowerCase(Locale.ROOT);
                for (String cat : importCategories) {
                    if (cat.startsWith(prefix)) completions.add(cat);
                }
                return completions;
            }
            if ("export".equals(sub)) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                if ("all".startsWith(prefix)) completions.add("all");
                if ("geyser".startsWith(prefix)) completions.add("geyser");
                for (ItemDefinition def : plugin.getItemRegistry().getAll()) {
                    if (def.getId().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        completions.add(def.getId());
                    }
                }
                for (var bDef : plugin.getCustomBlockRegistry().getAll()) {
                    if (bDef.getId().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        completions.add(bDef.getId());
                    }
                }
                return completions;
            }
            if ("recipe".equals(sub) || "recipes".equals(sub)) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                for (ItemDefinition def : plugin.getItemRegistry().getAll()) {
                    if (def.getId().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        completions.add(def.getId());
                    }
                }
                for (var bDef : plugin.getCustomBlockRegistry().getAll()) {
                    if (bDef.getId().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        completions.add(bDef.getId());
                    }
                }
                return completions;
            }
            if ("pack".equals(sub) || "rp".equals(sub)) {
                List<String> packSubs = List.of("rebuild", "distribute", "redistribute", "reload", "info", "status", "prompt", "send");
                String prefix = args[1].toLowerCase(Locale.ROOT);
                for (String s : packSubs) {
                    if (s.startsWith(prefix)) completions.add(s);
                }
                return completions;
            }
            if ("edit".equals(sub) || "delete".equals(sub) || "remove".equals(sub)) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                if ("item".startsWith(prefix)) completions.add("item");
                if ("block".startsWith(prefix)) completions.add("block");
                if ("entity".startsWith(prefix)) completions.add("entity");
                for (ItemDefinition def : plugin.getItemRegistry().getAll()) {
                    if (def.getId().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        completions.add(def.getId());
                    }
                }
                for (var bDef : plugin.getCustomBlockRegistry().getAll()) {
                    if (bDef.getId().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        completions.add(bDef.getId());
                    }
                }
                for (var eDef : plugin.getCustomEntityRegistry().getAll()) {
                    if (eDef.getId().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        completions.add(eDef.getId());
                    }
                }
                return completions;
            }
            if ("clone".equals(sub) || "duplicate".equals(sub)) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                if ("block".startsWith(prefix)) completions.add("block");
                if ("item".startsWith(prefix)) completions.add("item");
                for (ItemDefinition def : plugin.getItemRegistry().getAll()) {
                    if (def.getId().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        completions.add(def.getId());
                    }
                }
                for (var bDef : plugin.getCustomBlockRegistry().getAll()) {
                    if (bDef.getId().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        completions.add(bDef.getId());
                    }
                }
                return completions;
            }
            if ("give".equals(sub)) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                if ("item".startsWith(prefix)) completions.add("item");
                if ("block".startsWith(prefix)) completions.add("block");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        completions.add(p.getName());
                    }
                }
                return completions;
            }
        }

        if (args.length == 3) {
            if ("entity".equals(sub) || "mob".equals(sub)) {
                String action = args[1].toLowerCase(Locale.ROOT);
                String prefix = args[2].toLowerCase(Locale.ROOT);
                if ("edit".equals(action) || "delete".equals(action) || "remove".equals(action) || "spawn".equals(action)) {
                    for (var eDef : plugin.getCustomEntityRegistry().getAll()) {
                        if (eDef.getId().toLowerCase(Locale.ROOT).startsWith(prefix)) completions.add(eDef.getId());
                    }
                    return completions;
                }
                if ("import".equals(action) || "bedrock".equals(action) || "bb".equals(action)) {
                    for (String mName : plugin.getBedrockEntityImporter().listAvailableModelNames()) {
                        if (mName.toLowerCase(Locale.ROOT).startsWith(prefix)) completions.add(mName);
                    }
                    return completions;
                }
                if ("animate".equals(action) || "anim".equals(action) || "play".equals(action)) {
                    Set<String> animNames = new LinkedHashSet<>();
                    for (var eDef : plugin.getCustomEntityRegistry().getAll()) {
                        animNames.addAll(eDef.getAnimations().keySet());
                    }
                    if (animNames.isEmpty()) {
                        animNames.addAll(List.of("idle", "walk", "attack", "death", "hurt", "spawn"));
                    }
                    for (String an : animNames) {
                        if (an.toLowerCase(Locale.ROOT).startsWith(prefix)) completions.add(an);
                    }
                    return completions;
                }
            }
            if ("block".equals(sub)) {
                String action = args[1].toLowerCase(Locale.ROOT);
                if ("clone".equals(action) || "edit".equals(action) || "delete".equals(action) || "remove".equals(action) || "group".equals(action) || "autogroup".equals(action) || "optimize".equals(action)) {
                    String prefix = args[2].toLowerCase(Locale.ROOT);
                    for (var bDef : plugin.getCustomBlockRegistry().getAll()) {
                        if (bDef.getId().toLowerCase(Locale.ROOT).startsWith(prefix)) completions.add(bDef.getId());
                    }
                    return completions;
                }
            }
            if ("clone".equals(sub) || "duplicate".equals(sub)) {
                if ("block".equalsIgnoreCase(args[1])) {
                    String prefix = args[2].toLowerCase(Locale.ROOT);
                    for (var bDef : plugin.getCustomBlockRegistry().getAll()) {
                        if (bDef.getId().toLowerCase(Locale.ROOT).startsWith(prefix)) completions.add(bDef.getId());
                    }
                    return completions;
                } else if ("item".equalsIgnoreCase(args[1])) {
                    String prefix = args[2].toLowerCase(Locale.ROOT);
                    for (ItemDefinition def : plugin.getItemRegistry().getAll()) {
                        if (def.getId().toLowerCase(Locale.ROOT).startsWith(prefix)) completions.add(def.getId());
                    }
                    return completions;
                }
            }
        }

        if (args.length == 3 && ("blockbench".equalsIgnoreCase(args[0]) || "bb".equalsIgnoreCase(args[0]))) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            if ("item".equalsIgnoreCase(args[1]) || "block".equalsIgnoreCase(args[1])) {
                if ("import".startsWith(prefix)) completions.add("import");
                return completions;
            }
            if ("import".equalsIgnoreCase(args[1])) {
                if ("item".startsWith(prefix)) completions.add("item");
                if ("block".startsWith(prefix)) completions.add("block");
                return completions;
            }
        }

        if (args.length == 4 && ("entity".equalsIgnoreCase(args[0]) || "mob".equalsIgnoreCase(args[0]))) {
            String action = args[1].toLowerCase(Locale.ROOT);
            String prefix = args[3].toLowerCase(Locale.ROOT);
            if ("create".equals(action)) {
                for (String mName : plugin.getBedrockEntityImporter().listAvailableModelNames()) {
                    if (mName.toLowerCase(Locale.ROOT).startsWith(prefix)) completions.add(mName);
                }
                return completions;
            }
            if ("animate".equals(action) || "anim".equals(action) || "play".equals(action)) {
                for (var eDef : plugin.getCustomEntityRegistry().getAll()) {
                    if (eDef.getId().toLowerCase(Locale.ROOT).startsWith(prefix)) completions.add(eDef.getId());
                }
                return completions;
            }
        }

        if (args.length == 4 && ("blockbench".equalsIgnoreCase(args[0]) || "bb".equalsIgnoreCase(args[0]))) {
            String prefix = args[3].toLowerCase(Locale.ROOT);
            for (String modelName : plugin.getBlockbenchImporter().listAvailableModels()) {
                if (modelName.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    completions.add(modelName);
                }
            }
            Collections.sort(completions);
            return completions;
        }

        if (args.length == 3 && ("pack".equalsIgnoreCase(args[0]) || "rp".equalsIgnoreCase(args[0]))) {
            if ("prompt".equalsIgnoreCase(args[1]) || "send".equalsIgnoreCase(args[1])
                    || "distribute".equalsIgnoreCase(args[1]) || "redistribute".equalsIgnoreCase(args[1]) || "apply".equalsIgnoreCase(args[1])) {
                String prefix = args[2].toLowerCase(Locale.ROOT);
                if ("all".startsWith(prefix)) completions.add("all");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        completions.add(p.getName());
                    }
                }
                return completions;
            }
        }

        if (args.length == 3 && ("edit".equalsIgnoreCase(args[0]) || "delete".equalsIgnoreCase(args[0]))) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            if ("block".equalsIgnoreCase(args[1])) {
                for (var bDef : plugin.getCustomBlockRegistry().getAll()) {
                    if (bDef.getId().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        completions.add(bDef.getId());
                    }
                }
                return completions;
            }
            if ("entity".equalsIgnoreCase(args[1]) || "mob".equalsIgnoreCase(args[1])) {
                for (var eDef : plugin.getCustomEntityRegistry().getAll()) {
                    if (eDef.getId().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        completions.add(eDef.getId());
                    }
                }
                return completions;
            }
            if ("item".equalsIgnoreCase(args[1])) {
                for (ItemDefinition def : plugin.getItemRegistry().getAll()) {
                    if (def.getId().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        completions.add(def.getId());
                    }
                }
                return completions;
            }
        }

        if (args.length == 3 && "give".equalsIgnoreCase(args[0])) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            if ("item".equalsIgnoreCase(args[1]) || "block".equalsIgnoreCase(args[1])) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        completions.add(p.getName());
                    }
                }
                return completions;
            }
            for (ItemDefinition def : plugin.getItemRegistry().getAll()) {
                if (def.getId().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    completions.add(def.getId());
                }
            }
            for (var bDef : plugin.getCustomBlockRegistry().getAll()) {
                if (bDef.getId().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    completions.add(bDef.getId());
                }
            }
            Collections.sort(completions);
            return completions;
        }

        if (args.length == 4 && "give".equalsIgnoreCase(args[0])) {
            String prefix = args[3].toLowerCase(Locale.ROOT);
            if ("block".equalsIgnoreCase(args[1])) {
                for (var bDef : plugin.getCustomBlockRegistry().getAll()) {
                    if (bDef.getId().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        completions.add(bDef.getId());
                    }
                }
                return completions;
            }
            if ("item".equalsIgnoreCase(args[1])) {
                for (ItemDefinition def : plugin.getItemRegistry().getAll()) {
                    if (def.getId().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        completions.add(def.getId());
                    }
                }
                return completions;
            }
            return List.of("1", "16", "64");
        }

        if (args.length == 5 && "give".equalsIgnoreCase(args[0])) {
            return List.of("1", "16", "64");
        }

        return completions;
    }
}
