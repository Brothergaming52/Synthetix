package org.bg52.synthetix.recipe;

import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.item.ItemDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

/**
 * Universal recipe ingredient representation supporting:
 * 1. Vanilla Material (e.g. "DIAMOND", "IRON_INGOT")
 * 2. Synthetix Custom Item (e.g. "synth:ruby", "ruby") via RecipeChoice.ExactChoice
 * 3. Bukkit Tag (e.g. "tag:minecraft:planks") via RecipeChoice.MaterialChoice
 */
public class IngredientChoice {

    private final String rawInput;
    private final boolean isCustomItem;
    private final String customItemId;
    private final Material material;
    private final Tag<Material> tag;

    private static final NamespacedKey SYNTH_ID_KEY = new NamespacedKey("synthetix", "item_id");

    public static IngredientChoice fromItemStack(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return new IngredientChoice("AIR");
        }
        if (item.hasItemMeta()) {
            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            String synthId = pdc.get(SYNTH_ID_KEY, PersistentDataType.STRING);
            if (synthId != null) {
                return new IngredientChoice("synth:" + synthId);
            }
        }
        return new IngredientChoice(item.getType().name());
    }

    /**
     * Creates an ItemStack suitable for previewing this ingredient in a GUI slot.
     */
    public ItemStack toDisplayItem(SynthetixPlugin plugin) {
        if (material == Material.AIR || (isCustomItem && "air".equalsIgnoreCase(customItemId))) {
            return null;
        }
        if (isCustomItem) {
            ItemDefinition def = plugin.getItemRegistry().get(customItemId);
            if (def != null) {
                return def.buildItemStack();
            }
            ItemStack barrier = new ItemStack(Material.BARRIER);
            org.bukkit.inventory.meta.ItemMeta meta = barrier.getItemMeta();
            if (meta != null) {
                meta.displayName(org.bg52.synthetix.util.TextUtil.parse("<red>Custom Item: " + customItemId + "</red>"));
                barrier.setItemMeta(meta);
            }
            return barrier;
        }
        if (material != null && material != Material.AIR) {
            return new ItemStack(material);
        }
        if (tag != null) {
            ItemStack paper = new ItemStack(Material.NAME_TAG);
            org.bukkit.inventory.meta.ItemMeta meta = paper.getItemMeta();
            if (meta != null) {
                meta.displayName(org.bg52.synthetix.util.TextUtil.parse("<aqua>Tag: #" + tag.getKey().asString() + "</aqua>"));
                paper.setItemMeta(meta);
            }
            return paper;
        }
        return null;
    }

    public IngredientChoice(String input) {
        this.rawInput = input != null ? input.trim() : "AIR";

        if (this.rawInput.equalsIgnoreCase("AIR") || this.rawInput.equalsIgnoreCase("NONE") || this.rawInput.isEmpty()) {
            this.material = Material.AIR;
            this.isCustomItem = false;
            this.customItemId = null;
            this.tag = null;
        } else if (this.rawInput.startsWith("tag:") || this.rawInput.startsWith("#")) {
            String tagKey = this.rawInput.startsWith("tag:") ? this.rawInput.substring(4) : this.rawInput.substring(1);
            NamespacedKey key = tagKey.contains(":") ? NamespacedKey.fromString(tagKey) : NamespacedKey.minecraft(tagKey);
            this.tag = key != null ? Bukkit.getTag(Tag.REGISTRY_BLOCKS, key, Material.class) : null;
            this.isCustomItem = false;
            this.customItemId = null;
            this.material = null;
        } else if (this.rawInput.toLowerCase(Locale.ROOT).startsWith("synth:")) {
            this.isCustomItem = true;
            this.customItemId = this.rawInput.substring(6).toLowerCase(Locale.ROOT);
            this.material = null;
            this.tag = null;
        } else if (this.rawInput.toLowerCase(Locale.ROOT).startsWith("p_backpack:")) {
            this.isCustomItem = true;
            this.customItemId = this.rawInput.substring(11).toLowerCase(Locale.ROOT);
            this.material = null;
            this.tag = null;
        } else if (this.rawInput.toLowerCase(Locale.ROOT).startsWith("probablybackpacks:")) {
            this.isCustomItem = true;
            this.customItemId = this.rawInput.substring(18).toLowerCase(Locale.ROOT);
            this.material = null;
            this.tag = null;
        } else {
            Material match = Material.matchMaterial(this.rawInput);
            if (match != null) {
                this.material = match;
                this.isCustomItem = false;
                this.customItemId = null;
                this.tag = null;
            } else {
                // If not a vanilla material, treat as custom item ID
                this.isCustomItem = true;
                this.customItemId = this.rawInput.toLowerCase(Locale.ROOT);
                this.material = null;
                this.tag = null;
            }
        }
    }

    /**
     * Converts this choice into a Bukkit RecipeChoice.
     * Returns null if custom item is not found or if the choice is AIR.
     */
    public RecipeChoice toBukkitRecipeChoice(SynthetixPlugin plugin) {
        if (isCustomItem) {
            ItemDefinition def = plugin.getItemRegistry().get(customItemId);
            if (def != null) {
                return new RecipeChoice.ExactChoice(def.buildItemStack());
            }
            return null;
        }

        if (tag != null) {
            return new RecipeChoice.MaterialChoice(tag);
        }

        if (material != null && material != Material.AIR) {
            return new RecipeChoice.MaterialChoice(material);
        }

        return null;
    }

    /**
     * Evaluates if a given ItemStack matches this ingredient choice.
     */
    public boolean test(ItemStack item, SynthetixPlugin plugin) {
        if (item == null || item.getType() == Material.AIR) {
            return material == Material.AIR;
        }

        if (isCustomItem) {
            if (item.hasItemMeta()) {
                PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
                String pdcId = pdc.get(SYNTH_ID_KEY, PersistentDataType.STRING);
                if (pdcId != null && pdcId.equalsIgnoreCase(customItemId)) {
                    return true;
                }
            }

            // Fallback check against definition's built item
            ItemDefinition def = plugin.getItemRegistry().get(customItemId);
            if (def != null) {
                ItemStack sample = def.buildItemStack();
                return item.isSimilar(sample);
            }
            return false;
        }

        if (tag != null) {
            if (!tag.isTagged(item.getType())) return false;
            if (item.hasItemMeta()) {
                PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
                return !pdc.has(SYNTH_ID_KEY, PersistentDataType.STRING);
            }
            return true;
        }

        if (material != null) {
            // Must match material, and not be a Synthetix custom item if expecting plain vanilla
            if (item.getType() != material) return false;

            if (item.hasItemMeta()) {
                PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
                return !pdc.has(SYNTH_ID_KEY, PersistentDataType.STRING);
            }
            return true;
        }

        return false;
    }

    public String getRawInput() {
        return rawInput;
    }

    public boolean isCustomItem() {
        return isCustomItem;
    }

    public String getCustomItemId() {
        return customItemId;
    }

    public Material getMaterial() {
        return material;
    }

    public Tag<Material> getTag() {
        return tag;
    }

    public String getDisplayIdentifier(SynthetixPlugin plugin) {
        if (material == Material.AIR || "air".equalsIgnoreCase(rawInput) || "none".equalsIgnoreCase(rawInput)) {
            return "None";
        }
        if (isCustomItem) {
            if ("air".equalsIgnoreCase(customItemId) || "none".equalsIgnoreCase(customItemId)) {
                return "None";
            }
            if (plugin != null) {
                ItemDefinition def = plugin.getItemRegistry().get(customItemId);
                if (def != null && def.getDisplayName() != null && !def.getDisplayName().isEmpty()) {
                    return org.bg52.synthetix.util.TextUtil.toPlain(org.bg52.synthetix.util.TextUtil.parse(def.getDisplayName()));
                }
            }
            return org.bg52.synthetix.util.TextUtil.prettify(customItemId);
        }
        if (tag != null) {
            return "#" + tag.getKey().getKey();
        }
        if (material != null) {
            return org.bg52.synthetix.util.TextUtil.prettify(material.name());
        }
        return org.bg52.synthetix.util.TextUtil.prettify(rawInput);
    }

    public String getDisplayIdentifier() {
        return getDisplayIdentifier(null);
    }
}

