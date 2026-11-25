package org.bg52.synthetix.lang;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Enterprise multi-language localization manager.
 * Supports hot-swapping language files from plugins/Synthetix/lang/<lang>.yml
 * with automatic fallback and auto-syncing of newly introduced keys from embedded en_US.yml defaults.
 */
public class LanguageManager {

    private final SynthetixPlugin plugin;
    private final File langFolder;
    private FileConfiguration langConfig;
    private YamlConfiguration embeddedDefaults;
    private String activeLanguage;

    public LanguageManager(SynthetixPlugin plugin) {
        this.plugin = plugin;
        this.langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }
        load();
    }

    /**
     * Loads or reloads the active language configuration.
     */
    public void load() {
        this.activeLanguage = plugin.getConfig().getString("language", "en_US").trim();

        // 1. Load embedded defaults
        InputStream embeddedStream = plugin.getResource("lang/en_US.yml");
        if (embeddedStream != null) {
            this.embeddedDefaults = YamlConfiguration.loadConfiguration(new InputStreamReader(embeddedStream, StandardCharsets.UTF_8));
        }

        // 2. Ensure default en_US.yml exists in the lang folder
        File defaultFile = new File(langFolder, "en_US.yml");
        if (!defaultFile.exists()) {
            plugin.saveResource("lang/en_US.yml", false);
        }

        File activeFile = new File(langFolder, activeLanguage + ".yml");
        if (!activeFile.exists()) {
            plugin.getLogger().warning("Language file '" + activeLanguage + ".yml' not found in lang/ folder. Falling back to en_US.yml.");
            activeFile = defaultFile;
            this.activeLanguage = "en_US";
        }

        this.langConfig = YamlConfiguration.loadConfiguration(activeFile);
        if (this.embeddedDefaults != null) {
            this.langConfig.setDefaults(this.embeddedDefaults);
            this.langConfig.options().copyDefaults(true);

            // Auto-sync missing keys to the active on-disk file
            boolean modified = false;
            for (String key : this.embeddedDefaults.getKeys(true)) {
                if (!this.langConfig.contains(key)) {
                    this.langConfig.set(key, this.embeddedDefaults.get(key));
                    modified = true;
                }
            }
            if (modified) {
                try {
                    this.langConfig.save(activeFile);
                } catch (Exception ignored) {
                }
            }
        }

        plugin.getLogger().info("Loaded language: " + activeLanguage);
    }

    /**
     * Retrieves a translated string with placeholder replacements and color formatting applied.
     *
     * @param key The YAML configuration key path
     * @param placeholders Key-value placeholder pairs (e.g. "%item%", "Ruby Ring")
     * @return Colored and formatted string
     */
    public String get(String key, String... placeholders) {
        String raw = getRaw(key);
        if (raw == null) {
            return key;
        }

        raw = applyPlaceholders(raw, placeholders);
        return TextUtil.colorize(raw);
    }

    /**
     * Retrieves a message formatted as an Adventure Component.
     */
    public Component getComponent(String key, String... placeholders) {
        String raw = getRaw(key);
        if (raw == null) {
            return Component.text(key);
        }

        raw = applyPlaceholders(raw, placeholders);
        return TextUtil.parse(raw);
    }

    private String getRaw(String key) {
        if (langConfig == null) return null;
        String val = langConfig.getString(key);
        if (val == null && embeddedDefaults != null) {
            val = embeddedDefaults.getString(key);
        }
        return val;
    }

    /**
     * Retrieves a list of components from a string list or single string.
     */
    public List<Component> getComponentList(String key, String... placeholders) {
        List<Component> result = new ArrayList<>();
        if (langConfig == null) return result;

        List<String> list = langConfig.getStringList(key);
        if (list.isEmpty() && embeddedDefaults != null) {
            list = embeddedDefaults.getStringList(key);
        }

        if (list.isEmpty()) {
            String single = getRaw(key);
            if (single != null) {
                result.add(TextUtil.parse(applyPlaceholders(single, placeholders)));
            }
            return result;
        }

        for (String line : list) {
            result.add(TextUtil.parse(applyPlaceholders(line, placeholders)));
        }
        return result;
    }

    /**
     * Sends a localized message to a CommandSender with the plugin prefix prepended.
     */
    public void send(CommandSender sender, String key, String... placeholders) {
        if (sender == null) return;
        String prefix = get("prefix");
        String message = get(key, placeholders);
        sender.sendMessage(TextUtil.parse(prefix + message));
    }

    /**
     * Sends a localized message without the plugin prefix.
     */
    public void sendRaw(CommandSender sender, String key, String... placeholders) {
        if (sender == null) return;
        sender.sendMessage(getComponent(key, placeholders));
    }

    public String applyPlaceholders(String text, String... placeholders) {
        if (text == null || placeholders == null || placeholders.length < 2) {
            return text;
        }
        for (int i = 0; i < placeholders.length - 1; i += 2) {
            String placeholder = placeholders[i];
            String value = placeholders[i + 1] != null ? placeholders[i + 1] : "";
            if (placeholder == null) continue;

            String clean = placeholder;
            while (clean.startsWith("%") || clean.startsWith("{")) {
                clean = clean.substring(1);
            }
            while (clean.endsWith("%") || clean.endsWith("}")) {
                clean = clean.substring(0, clean.length() - 1);
            }

            text = text.replace("{" + clean + "}", value)
                       .replace("%" + clean + "%", value);
            if (!placeholder.isEmpty()) {
                text = text.replace(placeholder, value);
            }
        }
        return text;
    }
}
