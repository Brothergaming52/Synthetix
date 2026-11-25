package org.bg52.synthetix.keybind;

import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.api.event.SynthetixKeybindTriggerEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bg52.synthetix.scheduler.PlatformScheduler;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Universal Keybind Engine for registering, persisting, and detecting custom player input combinations.
 */
public class KeybindManager {

    private final SynthetixPlugin plugin;
    private final File keybindsFile;
    private final Map<String, KeybindAction> keybinds = new ConcurrentHashMap<>();
    private final Map<String, CustomKeybind> customKeybinds = new ConcurrentHashMap<>();

    // Per-player tracking state
    private final Map<UUID, Long> lastSneakStart = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSneakRelease = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastSneakReleaseSlot = new ConcurrentHashMap<>();
    private final Map<UUID, List<PlatformScheduler.TaskHandle>> activeHoldTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    public KeybindManager(SynthetixPlugin plugin) {
        this.plugin = plugin;
        this.keybindsFile = new File(plugin.getDataFolder(), "keybinds.yml");
        loadConfigKeybinds();
    }

    /**
     * Loads custom server keybinds from keybinds.yml.
     */
    public void loadConfigKeybinds() {
        if (!keybindsFile.exists()) {
            createDefaultKeybindsFile();
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(keybindsFile);
        ConfigurationSection root = config.getConfigurationSection("keybinds");
        if (root == null) return;

        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) continue;

            String typeStr = sec.getString("type", "DOUBLE_SNEAK");
            KeybindType type;
            try {
                type = KeybindType.valueOf(typeStr.toUpperCase().replace("-", "_"));
            } catch (IllegalArgumentException e) {
                type = KeybindType.DOUBLE_SNEAK;
            }

            int slot = sec.getInt("hotbar-slot", -1);
            if (key.equalsIgnoreCase("curios_open") && slot == -1) {
                // If curios_open has -1 from old default keybinds.yml, enforce default slot 8
                slot = 8;
            }
            int zeroIndexSlot = (slot >= 1 && slot <= 9) ? slot - 1 : slot;
            String command = sec.getString("command", "");
            boolean asPlayer = sec.getBoolean("as-player", true);
            long cooldownMs = sec.getLong("cooldown-ms", 500);
            boolean enabled = sec.getBoolean("enabled", true);

            CustomKeybind custom = new CustomKeybind(key, type, zeroIndexSlot, command, asPlayer, cooldownMs, enabled);
            customKeybinds.put(key.toLowerCase(Locale.ROOT), custom);

            if (enabled) {
                registerCustomKeybindAction(custom);
            }
        }
    }

    private void createDefaultKeybindsFile() {
        try {
            if (!keybindsFile.getParentFile().exists()) {
                keybindsFile.getParentFile().mkdirs();
            }
            YamlConfiguration config = new YamlConfiguration();
            config.set("keybinds.curios_open.type", "DOUBLE_SNEAK");
            config.set("keybinds.curios_open.hotbar-slot", 8);
            config.set("keybinds.curios_open.command", "curios");
            config.set("keybinds.curios_open.as-player", true);
            config.set("keybinds.curios_open.cooldown-ms", 500);
            config.set("keybinds.curios_open.enabled", true);
            config.save(keybindsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to create default keybinds.yml: " + e.getMessage());
        }
    }

    private void registerCustomKeybindAction(CustomKeybind custom) {
        KeybindAction action = KeybindAction.builder("custom:" + custom.getId().toLowerCase(Locale.ROOT), custom.getType())
                .hotbarSlot(custom.getHotbarSlot())
                .cooldownMs(custom.getCooldownMs())
                .handler(ctx -> {
                    String cmd = custom.getCommand();
                    if (cmd == null || cmd.trim().isEmpty()) return;

                    Player p = ctx.getPlayer();
                    String parsed = cmd.replace("{player}", p.getName())
                            .replace("{uuid}", p.getUniqueId().toString())
                            .replace("{slot}", String.valueOf(ctx.getSlot() + 1));
                    if (parsed.startsWith("/")) {
                        parsed = parsed.substring(1);
                    }

                    if (custom.isAsPlayer()) {
                        p.performCommand(parsed);
                    } else {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
                    }
                })
                .build();

        registerKeybind(action);
    }

    public void saveCustomKeybind(CustomKeybind custom) {
        customKeybinds.put(custom.getId().toLowerCase(Locale.ROOT), custom);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(keybindsFile);
        String path = "keybinds." + custom.getId() + ".";
        config.set(path + "type", custom.getType().name());
        config.set(path + "hotbar-slot", custom.getHotbarSlot() >= 0 ? custom.getHotbarSlot() + 1 : -1);
        config.set(path + "command", custom.getCommand());
        config.set(path + "as-player", custom.isAsPlayer());
        config.set(path + "cooldown-ms", custom.getCooldownMs());
        config.set(path + "enabled", custom.isEnabled());

        try {
            config.save(keybindsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save keybinds.yml: " + e.getMessage());
        }

        unregisterKeybind("custom:" + custom.getId().toLowerCase(Locale.ROOT));
        if (custom.isEnabled()) {
            registerCustomKeybindAction(custom);
        }
    }

    public void deleteCustomKeybind(String id) {
        String cleanId = id.toLowerCase(Locale.ROOT);
        customKeybinds.remove(cleanId);
        unregisterKeybind("custom:" + cleanId);

        YamlConfiguration config = YamlConfiguration.loadConfiguration(keybindsFile);
        config.set("keybinds." + id, null);
        try {
            config.save(keybindsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save keybinds.yml: " + e.getMessage());
        }
    }

    public void removeCustomKeybind(String id) {
        if (id == null) return;
        String cleanId = id.toLowerCase(Locale.ROOT);
        customKeybinds.remove(cleanId);
        unregisterKeybind("custom:" + cleanId);
    }

    public Collection<CustomKeybind> getCustomKeybinds() {
        return Collections.unmodifiableCollection(customKeybinds.values());
    }

    public CustomKeybind getCustomKeybind(String id) {
        if (id == null) return null;
        return customKeybinds.get(id.toLowerCase(Locale.ROOT));
    }

    /**
     * Registers a keybind action.
     */
    public void registerKeybind(KeybindAction action) {
        if (action != null) {
            keybinds.put(action.getId().toLowerCase(Locale.ROOT), action);
        }
    }

    /**
     * Unregisters a keybind by ID.
     */
    public void unregisterKeybind(String id) {
        if (id != null) {
            keybinds.remove(id.toLowerCase(Locale.ROOT));
        }
    }

    /**
     * Retrieves a registered keybind by ID.
     */
    public KeybindAction getKeybind(String id) {
        if (id == null) return null;
        return keybinds.get(id.toLowerCase(Locale.ROOT));
    }

    /**
     * Retrieves all registered keybinds.
     */
    public Collection<KeybindAction> getAllKeybinds() {
        return Collections.unmodifiableCollection(keybinds.values());
    }

    /**
     * Helper to parse and register a keybind directly from a config section.
     */
    public KeybindAction registerFromConfig(String id, ConfigurationSection section, Consumer<KeybindContext> handler) {
        KeybindAction action = KeybindAction.fromConfig(id, section, handler);
        if (action != null) {
            registerKeybind(action);
        }
        return action;
    }

    /**
     * Evaluates whether an action meets conditions and executes its handler.
     */
    public boolean tryExecute(KeybindAction action, Player player, KeybindType triggeredType) {
        if (action.getType() != triggeredType) {
            return false;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        // 1. Cooldown check
        Map<String, Long> playerCooldowns = cooldowns.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        Long expireTime = playerCooldowns.get(action.getId());
        if (expireTime != null && now < expireTime) {
            return false;
        }

        // 2. Hotbar slot check
        int currentSlot = player.getInventory().getHeldItemSlot();
        if (action.getRequiredHotbarSlot() >= 0 && action.getRequiredHotbarSlot() != currentSlot) {
            return false;
        }

        // 3. Sneaking condition check
        if (action.getRequireSneaking() != null) {
            if (action.getRequireSneaking() != player.isSneaking()) {
                return false;
            }
        }

        // 4. Empty hand condition check
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        boolean isAir = (heldItem == null || heldItem.getType().isAir());
        if (action.getRequireEmptyHand() != null) {
            if (action.getRequireEmptyHand() && !isAir) {
                return false;
            }
            if (!action.getRequireEmptyHand() && isAir) {
                return false;
            }
        }

        // 5. Fire Bukkit Event
        SynthetixKeybindTriggerEvent event = new SynthetixKeybindTriggerEvent(player, action, currentSlot, heldItem);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }

        // 6. Record cooldown
        if (action.getCooldownMs() > 0) {
            playerCooldowns.put(action.getId(), now + action.getCooldownMs());
        }

        // 7. Invoke action callback
        if (action.getHandler() != null) {
            KeybindContext context = new KeybindContext(player, action, currentSlot, heldItem);
            action.getHandler().accept(context);
        }

        return true;
    }

    /**
     * Handles sneak toggle events.
     */
    public void handleSneakToggle(Player player, boolean isSneaking) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (isSneaking) {
            // Check DOUBLE_SNEAK
            Long lastRelease = lastSneakRelease.get(uuid);
            Integer releaseSlot = lastSneakReleaseSlot.get(uuid);
            int currentSlot = player.getInventory().getHeldItemSlot();
            boolean doubleSneakFired = false;
            if (lastRelease != null) {
                for (KeybindAction action : keybinds.values()) {
                    if (action.getType() == KeybindType.DOUBLE_SNEAK) {
                        long window = action.getTimeoutMs();
                        if ((now - lastRelease) <= window) {
                            // If action requires a specific hotbar slot, ensure both sneaks occurred on that slot
                            if (action.getRequiredHotbarSlot() >= 0) {
                                if (currentSlot != action.getRequiredHotbarSlot() || releaseSlot == null || releaseSlot != action.getRequiredHotbarSlot()) {
                                    continue;
                                }
                            }
                            if (tryExecute(action, player, KeybindType.DOUBLE_SNEAK)) {
                                doubleSneakFired = true;
                                break;
                            }
                        }
                    }
                }
            }

            if (doubleSneakFired) {
                lastSneakRelease.remove(uuid);
                lastSneakReleaseSlot.remove(uuid);
                lastSneakStart.remove(uuid);
            } else {
                lastSneakStart.put(uuid, now);

                // Schedule SNEAK_HOLD tasks
                for (KeybindAction action : keybinds.values()) {
                    if (action.getType() == KeybindType.SNEAK_HOLD) {
                        long delayTicks = Math.max(1, action.getHoldDurationMs() / 50);
                        int initialSlot = player.getInventory().getHeldItemSlot();

                        PlatformScheduler.TaskHandle task = PlatformScheduler.get().runEntityLater(player, () -> {
                            if (player.isOnline() && player.isSneaking() && player.getInventory().getHeldItemSlot() == initialSlot) {
                                tryExecute(action, player, KeybindType.SNEAK_HOLD);
                            }
                        }, delayTicks);

                        activeHoldTasks.computeIfAbsent(uuid, k -> new ArrayList<>()).add(task);
                    }
                }
            }
        } else {
            // Cancel pending hold tasks
            cancelHoldTasks(uuid);

            // Check SNEAK_TAP
            Long start = lastSneakStart.get(uuid);
            if (start != null && (now - start) < 350) {
                for (KeybindAction action : keybinds.values()) {
                    if (action.getType() == KeybindType.SNEAK_TAP) {
                        tryExecute(action, player, KeybindType.SNEAK_TAP);
                    }
                }
            }

            lastSneakRelease.put(uuid, now);
            lastSneakReleaseSlot.put(uuid, player.getInventory().getHeldItemSlot());
        }
    }

    /**
     * Handles swap hand events (F key).
     */
    public boolean handleSwapHand(Player player) {
        boolean consumed = false;
        boolean sneaking = player.isSneaking();

        for (KeybindAction action : keybinds.values()) {
            if (sneaking && action.getType() == KeybindType.SNEAK_SWAP_HAND) {
                if (tryExecute(action, player, KeybindType.SNEAK_SWAP_HAND)) {
                    consumed = true;
                }
            } else if (!sneaking && action.getType() == KeybindType.SWAP_HAND) {
                if (tryExecute(action, player, KeybindType.SWAP_HAND)) {
                    consumed = true;
                }
            }
        }
        return consumed;
    }

    /**
     * Handles drop item events (Q key).
     */
    public boolean handleDropItem(Player player) {
        boolean consumed = false;
        boolean sneaking = player.isSneaking();

        for (KeybindAction action : keybinds.values()) {
            if (sneaking && action.getType() == KeybindType.SNEAK_DROP_ITEM) {
                if (tryExecute(action, player, KeybindType.SNEAK_DROP_ITEM)) {
                    consumed = true;
                }
            } else if (!sneaking && action.getType() == KeybindType.DROP_ITEM) {
                if (tryExecute(action, player, KeybindType.DROP_ITEM)) {
                    consumed = true;
                }
            }
        }
        return consumed;
    }

    /**
     * Handles player interaction events (Left or Right Click).
     */
    public boolean handleInteract(Player player, Action action) {
        boolean sneaking = player.isSneaking();
        boolean rightClick = (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK);
        boolean leftClick = (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK);
        boolean consumed = false;

        for (KeybindAction keybind : keybinds.values()) {
            if (rightClick) {
                if (sneaking && keybind.getType() == KeybindType.SNEAK_RIGHT_CLICK) {
                    if (tryExecute(keybind, player, KeybindType.SNEAK_RIGHT_CLICK)) {
                        consumed = true;
                    }
                } else if (!sneaking && keybind.getType() == KeybindType.RIGHT_CLICK) {
                    if (tryExecute(keybind, player, KeybindType.RIGHT_CLICK)) {
                        consumed = true;
                    }
                }
            } else if (leftClick) {
                if (sneaking && keybind.getType() == KeybindType.SNEAK_LEFT_CLICK) {
                    if (tryExecute(keybind, player, KeybindType.SNEAK_LEFT_CLICK)) {
                        consumed = true;
                    }
                } else if (!sneaking && keybind.getType() == KeybindType.LEFT_CLICK) {
                    if (tryExecute(keybind, player, KeybindType.LEFT_CLICK)) {
                        consumed = true;
                    }
                }
            }
        }
        return consumed;
    }

    /**
     * Handles hotbar slot change.
     */
    public void handleHotbarSwitch(Player player, int newSlot) {
        for (KeybindAction action : keybinds.values()) {
            if (action.getType() == KeybindType.HOTBAR_SWITCH) {
                tryExecute(action, player, KeybindType.HOTBAR_SWITCH);
            }
        }
    }

    private void cancelHoldTasks(UUID uuid) {
        List<PlatformScheduler.TaskHandle> tasks = activeHoldTasks.remove(uuid);
        if (tasks != null) {
            for (PlatformScheduler.TaskHandle task : tasks) {
                task.cancel();
            }
        }
    }

    public void clearPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        cancelHoldTasks(uuid);
        lastSneakStart.remove(uuid);
        lastSneakRelease.remove(uuid);
        lastSneakReleaseSlot.remove(uuid);
        cooldowns.remove(uuid);
    }

    public void shutdown() {
        for (UUID uuid : activeHoldTasks.keySet()) {
            cancelHoldTasks(uuid);
        }
        keybinds.clear();
        customKeybinds.clear();
        cooldowns.clear();
        lastSneakStart.clear();
        lastSneakRelease.clear();
        lastSneakReleaseSlot.clear();
    }
}
