package org.bg52.synthetix.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Universal text and component utility for Adventure & MiniMessage formatting.
 * Handles MiniMessage (including nested tags like gradients with bold), legacy &amp; codes,
 * Bungee Hex, Bracket Hex, and plain text conversions.
 */
public final class TextUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer SECTION_SERIALIZER = LegacyComponentSerializer.legacySection();
    private static final PlainTextComponentSerializer PLAIN_SERIALIZER = PlainTextComponentSerializer.plainText();

    private static final Pattern AMP_HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern BRACKET_HEX_PATTERN = Pattern.compile("\\{#([A-Fa-f0-9]{6})}");
    private static final Pattern BUNGEE_HEX_PATTERN = Pattern.compile("§x§([A-Fa-f0-9])§([A-Fa-f0-9])§([A-Fa-f0-9])§([A-Fa-f0-9])§([A-Fa-f0-9])§([A-Fa-f0-9])", Pattern.CASE_INSENSITIVE);

    private TextUtil() {}

    /**
     * Parses an input string into an Adventure Component.
     * Translates any color format (MiniMessage gradients, nested tags like &lt;bold&gt;,
     * &amp;#hex, {#hex}, &amp;a, §a) into a clean Component with default italic disabled.
     */
    public static Component parse(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }

        try {
            String prepared = toMiniMessage(input);
            return MINI_MESSAGE.deserialize(prepared).decoration(TextDecoration.ITALIC, false);
        } catch (Exception e) {
            // Fallback for non-MiniMessage or malformed strings
            String legacy = ColorUtil.translateLegacyOnly(input);
            return SECTION_SERIALIZER.deserialize(legacy).decoration(TextDecoration.ITALIC, false);
        }
    }

    /**
     * Converts legacy codes (&amp;, §, &amp;#RRGGBB, {#RRGGBB}, §x§r§r§g§g§b§b) into valid MiniMessage tags
     * while preserving existing MiniMessage tags (&lt;gradient:...&gt;, &lt;bold&gt;, &lt;red&gt;, etc.).
     */
    public static String toMiniMessage(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        String result = input;

        // 1. Bungee HEX: §x§r§r§g§g§b§b -> <#rrggbb>
        Matcher bungeeMatcher = BUNGEE_HEX_PATTERN.matcher(result);
        if (bungeeMatcher.find()) {
            result = bungeeMatcher.replaceAll("<#$1$2$3$4$5$6>");
        }

        // 2. Ampersand HEX: &#RRGGBB -> <#RRGGBB>
        Matcher ampHexMatcher = AMP_HEX_PATTERN.matcher(result);
        if (ampHexMatcher.find()) {
            result = ampHexMatcher.replaceAll("<#$1>");
        }

        // 3. Bracket HEX: {#RRGGBB} or &{#RRGGBB} -> <#RRGGBB>
        Matcher bracketHexMatcher = BRACKET_HEX_PATTERN.matcher(result);
        if (bracketHexMatcher.find()) {
            result = bracketHexMatcher.replaceAll("<#$1>");
        }

        // 4. Legacy format codes (& or §)
        StringBuilder sb = new StringBuilder(result.length());
        char[] chars = result.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if ((c == '&' || c == '§') && i + 1 < chars.length) {
                char code = Character.toLowerCase(chars[i + 1]);
                String tag = legacyCodeToMiniTag(code);
                if (tag != null) {
                    sb.append(tag);
                    i++; // skip next char
                    continue;
                }
            }
            sb.append(c);
        }

        return sb.toString();
    }

    private static String legacyCodeToMiniTag(char code) {
        return switch (code) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a' -> "<green>";
            case 'b' -> "<aqua>";
            case 'c' -> "<red>";
            case 'd' -> "<light_purple>";
            case 'e' -> "<yellow>";
            case 'f' -> "<white>";
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset>";
            default -> null;
        };
    }

    /**
     * Parses a list of lines into a List of Components.
     */
    public static List<Component> parseList(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return new ArrayList<>();
        }
        List<Component> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(parse(line));
        }
        return result;
    }

    /**
     * Converts a Component to plain text with no color codes.
     */
    public static String toPlain(Component component) {
        if (component == null) return "";
        return PLAIN_SERIALIZER.serialize(component);
    }

    /**
     * Converts an Adventure Component to legacy section § formatted string.
     */
    public static String toLegacySection(Component component) {
        if (component == null) return "";
        return SECTION_SERIALIZER.serialize(component);
    }

    /**
     * Converts SNAKE_CASE, snake_case, kebab-case, or lower_case identifiers
     * into capitalized Title Case words.
     */
    public static String prettify(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String[] parts = raw.replace('-', '_').split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1).toLowerCase(java.util.Locale.ROOT));
            }
        }
        return sb.toString();
    }

    /**
     * Translates a raw string into colored string with section § codes.
     */
    public static String colorize(String input) {
        if (input == null || input.isEmpty()) return input;
        return toLegacySection(parse(input));
    }
}
