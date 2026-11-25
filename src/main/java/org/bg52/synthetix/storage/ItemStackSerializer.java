package org.bg52.synthetix.storage;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * High-performance, full-fidelity serializer for Minecraft Paper ItemStacks.
 * Preserves all 1.20.6+ Data Components, CustomModelData, PDC metadata, enchantments, and lore.
 */
public final class ItemStackSerializer {

    private ItemStackSerializer() {}

    /**
     * Serializes a single ItemStack into a Base64 string.
     */
    public static String serializeItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }

        YamlConfiguration config = new YamlConfiguration();
        config.set("item", item);
        String yaml = config.saveToString();
        return Base64.getEncoder().encodeToString(yaml.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Deserializes a Base64 string back into an ItemStack.
     */
    public static ItemStack deserializeItem(String base64) {
        if (base64 == null || base64.trim().isEmpty()) {
            return null;
        }

        try {
            String yaml = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
            YamlConfiguration config = new YamlConfiguration();
            config.load(new StringReader(yaml));
            return config.getItemStack("item");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Serializes a list of ItemStacks to Base64.
     */
    public static String serializeItemList(List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }

        YamlConfiguration config = new YamlConfiguration();
        for (int i = 0; i < items.size(); i++) {
            ItemStack item = items.get(i);
            if (item != null && item.getType() != Material.AIR) {
                config.set("items." + i, item);
            }
        }

        String yaml = config.saveToString();
        if (yaml.trim().isEmpty()) {
            return null;
        }
        return Base64.getEncoder().encodeToString(yaml.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Deserializes a Base64 string back into a list of ItemStacks.
     */
    public static List<ItemStack> deserializeItemList(String base64) {
        List<ItemStack> result = new ArrayList<>();
        if (base64 == null || base64.trim().isEmpty()) {
            return result;
        }

        try {
            String yaml = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
            YamlConfiguration config = new YamlConfiguration();
            config.load(new StringReader(yaml));

            ConfigurationSection sec = config.getConfigurationSection("items");
            if (sec == null) return result;

            List<String> keys = new ArrayList<>(sec.getKeys(false));
            keys.sort(Comparator.comparingInt(k -> {
                try {
                    return Integer.parseInt(k);
                } catch (NumberFormatException e) {
                    return Integer.MAX_VALUE;
                }
            }));

            for (String key : keys) {
                ItemStack item = sec.getItemStack(key);
                result.add(item != null ? item : new ItemStack(Material.AIR));
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    /**
     * Serializes an arbitrary categorized item map (e.g. Curios slot -> items) to Base64.
     */
    public static String serializeItemMap(Map<String, List<ItemStack>> itemMap) {
        if (itemMap == null || itemMap.isEmpty()) {
            return null;
        }

        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<String, List<ItemStack>> entry : itemMap.entrySet()) {
            String category = entry.getKey();
            List<ItemStack> items = entry.getValue();
            if (items == null) continue;

            for (int i = 0; i < items.size(); i++) {
                ItemStack item = items.get(i);
                if (item != null && item.getType() != Material.AIR) {
                    config.set("data." + category + "." + i, item);
                }
            }
        }

        String yaml = config.saveToString();
        if (yaml.trim().isEmpty()) {
            return null;
        }
        return Base64.getEncoder().encodeToString(yaml.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Deserializes a Base64 string back into a categorized item map.
     * Supports Synthetix ("data"), old CuriosPaper ("accessories"), and CuriosPaper 3.0 ("slots") formats.
     */
    public static Map<String, List<ItemStack>> deserializeItemMap(String base64) {
        Map<String, List<ItemStack>> map = new HashMap<>();
        if (base64 == null || base64.trim().isEmpty()) {
            return map;
        }

        try {
            String yaml = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
            YamlConfiguration config = new YamlConfiguration();
            config.load(new StringReader(yaml));

            ConfigurationSection dataSec = config.getConfigurationSection("data");
            if (dataSec == null) {
                dataSec = config.getConfigurationSection("accessories");
            }
            if (dataSec == null) {
                dataSec = config.getConfigurationSection("slots");
            }
            if (dataSec == null) return map;

            for (String category : dataSec.getKeys(false)) {
                List<ItemStack> items = new ArrayList<>();
                ConfigurationSection catSec = dataSec.getConfigurationSection(category);
                if (catSec != null) {
                    List<String> keys = new ArrayList<>(catSec.getKeys(false));
                    keys.sort(Comparator.comparingInt(k -> {
                        try {
                            return Integer.parseInt(k);
                        } catch (NumberFormatException e) {
                            return Integer.MAX_VALUE;
                        }
                    }));

                    for (String key : keys) {
                        ItemStack item = catSec.getItemStack(key);
                        items.add(item != null ? item : new ItemStack(Material.AIR));
                    }
                } else if (dataSec.isList(category)) {
                    List<?> rawList = dataSec.getList(category);
                    if (rawList != null) {
                        for (Object obj : rawList) {
                            if (obj instanceof ItemStack is && is.getType() != Material.AIR) {
                                items.add(is);
                            } else {
                                items.add(new ItemStack(Material.AIR));
                            }
                        }
                    }
                }
                map.put(category.toLowerCase(Locale.ROOT), items);
            }
        } catch (Exception ignored) {
        }
        return map;
    }
}

