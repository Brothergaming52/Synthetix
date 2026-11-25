package org.bg52.synthetix.util;

import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Color translation and stripping utility supporting ALL Minecraft color and gradient formats.
 * Leverages Kyori Adventure serializer pipelines to prevent character-by-character tag corruption.
 */
public final class ColorUtil {

    private static final Pattern AMP_HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern BRACKET_HEX_PATTERN = Pattern.compile("\\{#([A-Fa-f0-9]{6})}");

    private ColorUtil() {}

    /**
     * Translates all supported color formats (legacy, hex, bracket, MiniMessage, gradients)
     * into a clean Minecraft § color-coded string without corrupting inner formatting tags.
     */
    public static String translate(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return TextUtil.toLegacySection(TextUtil.parse(text));
    }

    /**
     * Translates legacy &amp; codes and &#hex codes into § codes only.
     */
    public static String translateLegacyOnly(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // 1. Process &#RRGGBB
        Matcher matcher = AMP_HEX_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, toBungeeHex(matcher.group(1)));
        }
        matcher.appendTail(sb);
        text = sb.toString();

        // 2. Process {#RRGGBB}
        matcher = BRACKET_HEX_PATTERN.matcher(text);
        sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, toBungeeHex(matcher.group(1)));
        }
        matcher.appendTail(sb);
        text = sb.toString();

        // 3. Process & codes
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && isColorChar(chars[i + 1])) {
                chars[i] = '§';
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }

    /**
     * Strips all formatting and color codes from text.
     */
    public static String stripAll(String text) {
        if (text == null) return null;
        return TextUtil.toPlain(TextUtil.parse(text));
    }

    public static String toBungeeHex(String hex) {
        StringBuilder sb = new StringBuilder("§x");
        for (char c : hex.toCharArray()) {
            sb.append('§').append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    public static String toBungeeHex(Color color) {
        return toBungeeHex(String.format("%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue()));
    }

    private static boolean isColorChar(char c) {
        return "0123456789AaBbCcDdEeFfKkLlMmNnOoRr".indexOf(c) != -1;
    }
}
