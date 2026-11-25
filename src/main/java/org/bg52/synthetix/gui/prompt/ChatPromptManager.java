package org.bg52.synthetix.gui.prompt;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.scheduler.PlatformScheduler;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Handles asynchronous chat prompt inputs for GUI editors (names, lore, numbers, IDs).
 * Fully localized via LanguageManager.
 */
public class ChatPromptManager implements Listener {

    private final SynthetixPlugin plugin;
    private final Map<UUID, Consumer<String>> activePrompts;

    public ChatPromptManager(SynthetixPlugin plugin) {
        this.plugin = plugin;
        this.activePrompts = new ConcurrentHashMap<>();
    }

    /**
     * Prompts a player to type an input in chat. Closes any open inventory first.
     *
     * @param player The player to prompt
     * @param promptMessage MiniMessage or formatted message explaining what to type
     * @param onResponse Callback executed on the Bukkit main thread with the player's trimmed response
     */
    public void prompt(Player player, Component promptMessage, Consumer<String> onResponse) {
        player.closeInventory();
        activePrompts.put(player.getUniqueId(), onResponse);

        player.sendMessage(Component.empty());
        player.sendMessage(plugin.getLanguageManager().getComponent("editor.prompts.prompt-header"));
        player.sendMessage(promptMessage);
        player.sendMessage(plugin.getLanguageManager().getComponent("editor.prompts.prompt-cancel-hint"));
        player.sendMessage(Component.empty());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Consumer<String> callback = activePrompts.remove(player.getUniqueId());

        if (callback != null) {
            event.setCancelled(true);
            String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

            if (message.equalsIgnoreCase("cancel")) {
                plugin.getLanguageManager().send(player, "general.operation-cancelled");
                return;
            }

            // Execute callback safely on the main server thread
            PlatformScheduler.get().runEntity(player, () -> {
                try {
                    callback.accept(message);
                } catch (Throwable t) {
                    plugin.getLanguageManager().send(player, "general.input-error", "%error%", t.getMessage());
                    plugin.getLogger().warning("Error in chat prompt callback for " + player.getName() + ": " + t.getMessage());
                }
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        activePrompts.remove(event.getPlayer().getUniqueId());
    }

    public void cancel(Player player) {
        activePrompts.remove(player.getUniqueId());
    }

    public boolean isPromptActive(Player player) {
        return activePrompts.containsKey(player.getUniqueId());
    }
}
