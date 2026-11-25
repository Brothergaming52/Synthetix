package org.bg52.synthetix.item.component;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

import java.util.Locale;

/**
 * Specification for Armor Trim on custom items.
 */
public class ArmorTrimSpec {

    private String pattern;
    private String material;

    public ArmorTrimSpec(String pattern, String material) {
        this.pattern = pattern != null ? pattern.toLowerCase(Locale.ROOT) : "silence";
        this.material = material != null ? material.toLowerCase(Locale.ROOT) : "netherite";
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern != null ? pattern.toLowerCase(Locale.ROOT) : "silence";
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material != null ? material.toLowerCase(Locale.ROOT) : "netherite";
    }

    public void applyTo(ArmorMeta meta) {
        if (meta == null) return;
        try {
            TrimPattern p = resolvePattern(this.pattern);
            TrimMaterial m = resolveMaterial(this.material);
            if (p != null && m != null) {
                meta.setTrim(new ArmorTrim(m, p));
            }
        } catch (Throwable ignored) {
        }
    }

    public static TrimPattern resolvePattern(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String clean = raw.trim().toLowerCase(Locale.ROOT);
        NamespacedKey key = clean.contains(":") ? ComponentHandler.parseNamespacedKey(clean) : NamespacedKey.minecraft(clean);
        if (key != null) {
            TrimPattern p = Registry.TRIM_PATTERN.get(key);
            if (p != null) return p;
        }
        for (TrimPattern p : Registry.TRIM_PATTERN) {
            if (p.key().value().equalsIgnoreCase(clean)) {
                return p;
            }
        }
        return null;
    }

    public static TrimMaterial resolveMaterial(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String clean = raw.trim().toLowerCase(Locale.ROOT);
        NamespacedKey key = clean.contains(":") ? ComponentHandler.parseNamespacedKey(clean) : NamespacedKey.minecraft(clean);
        if (key != null) {
            TrimMaterial m = Registry.TRIM_MATERIAL.get(key);
            if (m != null) return m;
        }
        for (TrimMaterial m : Registry.TRIM_MATERIAL) {
            if (m.key().value().equalsIgnoreCase(clean)) {
                return m;
            }
        }
        return null;
    }

    public void saveToConfig(ConfigurationSection sec) {
        sec.set("pattern", pattern);
        sec.set("material", material);
    }

    public static ArmorTrimSpec loadFromConfig(ConfigurationSection sec) {
        if (sec == null) return null;
        String p = sec.getString("pattern");
        String m = sec.getString("material");
        if (p == null && m == null) return null;
        return new ArmorTrimSpec(p, m);
    }

    public ArmorTrimSpec copy() {
        return new ArmorTrimSpec(this.pattern, this.material);
    }
}

