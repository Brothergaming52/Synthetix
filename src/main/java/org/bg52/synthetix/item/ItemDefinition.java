package org.bg52.synthetix.item;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.ability.AbilitySpec;
import org.bg52.synthetix.ability.AuraSpec;
import org.bg52.synthetix.item.component.ArmorTrimSpec;
import org.bg52.synthetix.item.component.ComponentHandler;
import org.bg52.synthetix.item.food.FoodComponentSpec;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Super-modular, comprehensive definition of a custom Synthetix item.
 * Supports modern 1.20.6 Data Components, Attribute Modifiers, CustomModelData, item_model, and PDC tags.
 */
public class ItemDefinition {

    private final String id;
    private String owningPlugin;
    private String displayName;
    private Material material;
    private List<String> lore;
    private Integer customModelData;
    private String itemModel;
    private boolean unbreakable;
    private boolean hideEnchants;
    private Integer maxStackSize;
    private boolean glider;
    private boolean fireResistant;
    private Boolean glintOverride;
    private Integer maxDamage;
    private ItemRarity rarity;
    private ArmorTrimSpec trim;
    private Color customColor;
    private boolean hideTooltip;
    private FoodComponentSpec food;
    private AuraSpec aura;
    private Map<String, AbilitySpec> abilities;
    private Map<String, Integer> enchants;
    private Map<String, String> tags;
    private Set<ItemFlag> itemFlags;
    private List<AttributeSpec> attributes;

    public ItemDefinition(String id) {
        this.id = id.toLowerCase(Locale.ROOT);
        this.owningPlugin = "synthetix";
        this.displayName = id;
        this.material = Material.PAPER;
        this.lore = new ArrayList<>();
        this.enchants = new HashMap<>();
        this.tags = new HashMap<>();
        this.itemFlags = new HashSet<>();
        this.attributes = new ArrayList<>();
        this.abilities = new HashMap<>();
    }

    /**
     * Specification for an attribute modifier on this item.
     */
    public static class AttributeSpec {
        private String attributeName; // e.g. "GENERIC_ATTACK_DAMAGE"
        private String name;          // e.g. "synthetix_attack_damage"
        private double amount;
        private AttributeModifier.Operation operation;
        private EquipmentSlot slot;   // null for any slot

        public AttributeSpec(String attributeName, String name, double amount, AttributeModifier.Operation operation, EquipmentSlot slot) {
            this.attributeName = attributeName;
            this.name = name;
            this.amount = amount;
            this.operation = operation != null ? operation : AttributeModifier.Operation.ADD_NUMBER;
            this.slot = slot;
        }

        public String getAttributeName() { return attributeName; }
        public void setAttributeName(String attributeName) { this.attributeName = attributeName; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public double getAmount() { return amount; }
        public void setAmount(double amount) { this.amount = amount; }
        public AttributeModifier.Operation getOperation() { return operation; }
        public void setOperation(AttributeModifier.Operation operation) { this.operation = operation; }
        public EquipmentSlot getSlot() { return slot; }
        public void setSlot(EquipmentSlot slot) { this.slot = slot; }

        public void saveToConfig(ConfigurationSection sec) {
            sec.set("attribute", attributeName);
            sec.set("name", name);
            sec.set("amount", amount);
            sec.set("operation", operation.name());
            if (slot != null) {
                sec.set("slot", slot.name());
            }
        }

        public static AttributeSpec loadFromConfig(ConfigurationSection sec) {
            String attr = sec.getString("attribute");
            if (attr == null) return null;
            String name = sec.getString("name", "synthetix_modifier");
            double amount = sec.getDouble("amount", 0.0);
            String opStr = sec.getString("operation", "ADD_NUMBER");
            AttributeModifier.Operation op;
            try {
                op = AttributeModifier.Operation.valueOf(opStr);
            } catch (Exception e) {
                op = AttributeModifier.Operation.ADD_NUMBER;
            }

            EquipmentSlot slot = null;
            String slotStr = sec.getString("slot");
            if (slotStr != null) {
                try {
                    slot = EquipmentSlot.valueOf(slotStr);
                } catch (Exception ignored) {}
            }

            return new AttributeSpec(attr, name, amount, op, slot);
        }
    }

    /**
     * Constructs a live Minecraft Paper ItemStack from this definition.
     */
    public ItemStack buildItemStack() {
        Material mat = this.material != null ? this.material : Material.PAPER;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Display Name
            if (displayName != null && !displayName.isEmpty()) {
                meta.displayName(TextUtil.parse(displayName));
            }

            // Lore
            if (lore != null && !lore.isEmpty()) {
                meta.lore(TextUtil.parseList(lore));
            }

            // Model Data (item_model and/or custom_model_data)
            ComponentHandler.applyItemModel(meta, itemModel, customModelData);

            // Unbreakable
            if (unbreakable) {
                meta.setUnbreakable(true);
            }

            // Max Stack Size
            if (maxStackSize != null) {
                ComponentHandler.setMaxStackSize(meta, maxStackSize);
            }

            // Glint Override
            if (glintOverride != null) {
                ComponentHandler.applyGlintOverride(meta, glintOverride);
            }

            // Max Damage / Custom Durability
            if (maxDamage != null) {
                ComponentHandler.applyMaxDamage(meta, maxDamage);
            }

            // Item Rarity
            if (rarity != null) {
                ComponentHandler.applyRarity(meta, rarity);
            }

            // Armor Trim
            if (trim != null) {
                ComponentHandler.applyArmorTrim(meta, trim);
            }

            // Custom Color
            if (customColor != null) {
                ComponentHandler.applyCustomColor(meta, customColor);
            }

            // Hide Tooltip
            if (hideTooltip) {
                ComponentHandler.applyHideTooltip(meta, true);
            }

            // Fire Resistant
            if (fireResistant) {
                try {
                    meta.setFireResistant(true);
                } catch (Throwable ignored) {}
            }

            // Food & Consumable Component
            if (food != null) {
                food.applyTo(meta);
            }

            // Enchantments
            if (enchants != null && !enchants.isEmpty()) {
                for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
                    Enchantment ench = parseEnchantment(entry.getKey());
                    if (ench != null) {
                        meta.addEnchant(ench, entry.getValue(), true);
                    }
                }
            }

            // Item Flags
            if (hideEnchants) {
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            if (itemFlags != null && !itemFlags.isEmpty()) {
                for (ItemFlag flag : itemFlags) {
                    meta.addItemFlags(flag);
                }
            }

            // Attribute Modifiers
            if (attributes != null && !attributes.isEmpty()) {
                for (AttributeSpec spec : attributes) {
                    Attribute attr = parseAttribute(spec.getAttributeName());
                    if (attr != null) {
                        AttributeModifier modifier;
                        if (spec.getSlot() != null) {
                            modifier = new AttributeModifier(UUID.randomUUID(), spec.getName(), spec.getAmount(), spec.getOperation(), spec.getSlot());
                        } else {
                            modifier = new AttributeModifier(UUID.randomUUID(), spec.getName(), spec.getAmount(), spec.getOperation());
                        }
                        meta.addAttributeModifier(attr, modifier);
                    }
                }
            }

            // Track Item ID in PDC
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            NamespacedKey idKey = new NamespacedKey("synthetix", "item_id");
            pdc.set(idKey, PersistentDataType.STRING, this.id);

            // Track Custom Tags in PDC
            if (tags != null && !tags.isEmpty()) {
                for (Map.Entry<String, String> entry : tags.entrySet()) {
                    NamespacedKey key = ComponentHandler.parseNamespacedKey(entry.getKey());
                    if (key != null) {
                        pdc.set(key, PersistentDataType.STRING, entry.getValue());
                    }
                }
            }

            item.setItemMeta(meta);
        }

        // Glider (Elytra flight) component
        if (glider) {
            ComponentHandler.setGlider(item, true);
        }

        // Consumable & Food component (Paper 1.20.5+ / 1.21.2+)
        if (food != null) {
            ComponentHandler.applyConsumable(item, food, this.id);
        }

        return item;
    }

    public static Attribute parseAttribute(String name) {
        if (name == null || name.isEmpty()) return null;
        String raw = name.trim().toLowerCase(Locale.ROOT);

        // 1. Try exact namespaced key if contains ':'
        if (raw.contains(":")) {
            NamespacedKey key = ComponentHandler.parseNamespacedKey(raw);
            if (key != null) {
                Attribute attr = Registry.ATTRIBUTE.get(key);
                if (attr != null) return attr;
            }
        }

        // 2. Try minecraft:generic.<name> or minecraft:<name>
        String dotted = raw.replace("generic_", "").replace('_', '.');
        Attribute direct = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(dotted));
        if (direct != null) return direct;

        Attribute genericDirect = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic." + dotted));
        if (genericDirect != null) return genericDirect;

        // 3. Fallback: Search all attributes in Registry.ATTRIBUTE
        for (Attribute attr : Registry.ATTRIBUTE) {
            String key = attr.getKey().getKey();
            if (key.equalsIgnoreCase(raw)
                    || key.equalsIgnoreCase(dotted)
                    || key.replace("generic.", "").equalsIgnoreCase(dotted)
                    || key.replace('.', '_').equalsIgnoreCase(raw)) {
                return attr;
            }
        }
        return null;
    }

    private Enchantment parseEnchantment(String name) {
        if (name == null || name.isEmpty()) return null;
        String raw = name.trim().toLowerCase(Locale.ROOT);
        if (raw.contains(":")) {
            NamespacedKey key = ComponentHandler.parseNamespacedKey(raw);
            if (key != null) {
                Enchantment ench = Registry.ENCHANTMENT.get(key);
                if (ench != null) return ench;
            }
        }
        Enchantment direct = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(raw));
        if (direct != null) return direct;

        for (Enchantment ench : Registry.ENCHANTMENT) {
            if (ench.getKey().getKey().equalsIgnoreCase(raw) || ench.getKey().asString().equalsIgnoreCase(raw)) {
                return ench;
            }
        }
        return null;
    }

    // ========== Serialization ==========

    public void saveToConfig(ConfigurationSection sec) {
        sec.set("id", id);
        sec.set("owning-plugin", owningPlugin);
        sec.set("display-name", displayName);
        sec.set("material", material != null ? material.name() : "PAPER");
        sec.set("lore", lore);

        if (customModelData != null) sec.set("custom-model-data", customModelData);
        if (itemModel != null) sec.set("item-model", itemModel);
        if (unbreakable) sec.set("unbreakable", true);
        if (hideEnchants) sec.set("hide-enchants", true);
        if (maxStackSize != null) sec.set("max-stack-size", maxStackSize);
        if (glider) sec.set("glider", true);
        if (fireResistant) sec.set("fire-resistant", true);
        if (glintOverride != null) sec.set("glint-override", glintOverride);
        if (maxDamage != null) sec.set("max-damage", maxDamage);
        if (rarity != null) sec.set("rarity", rarity.name());
        if (trim != null) {
            ConfigurationSection trimSec = sec.createSection("trim");
            trim.saveToConfig(trimSec);
        }
        if (customColor != null) {
            sec.set("color", String.format(Locale.ROOT, "#%02x%02x%02x", customColor.getRed(), customColor.getGreen(), customColor.getBlue()));
        }
        if (hideTooltip) sec.set("hide-tooltip", true);
        if (food != null) {
            ConfigurationSection foodSec = sec.createSection("food");
            food.saveToConfig(foodSec);
        }
        if (aura != null) {
            ConfigurationSection auraSec = sec.createSection("aura");
            aura.saveToConfig(auraSec);
        }
        if (abilities != null && !abilities.isEmpty()) {
            ConfigurationSection abilSec = sec.createSection("abilities");
            abilities.forEach((k, v) -> {
                ConfigurationSection aEntry = abilSec.createSection(k);
                v.saveToConfig(aEntry);
            });
        }

        if (enchants != null && !enchants.isEmpty()) {
            ConfigurationSection enchSec = sec.createSection("enchants");
            enchants.forEach(enchSec::set);
        }

        if (tags != null && !tags.isEmpty()) {
            ConfigurationSection tagsSec = sec.createSection("tags");
            tags.forEach(tagsSec::set);
        }

        if (itemFlags != null && !itemFlags.isEmpty()) {
            List<String> flags = new ArrayList<>();
            for (ItemFlag flag : itemFlags) {
                flags.add(flag.name());
            }
            sec.set("flags", flags);
        }

        if (attributes != null && !attributes.isEmpty()) {
            ConfigurationSection attrSec = sec.createSection("attributes");
            for (int i = 0; i < attributes.size(); i++) {
                ConfigurationSection entry = attrSec.createSection("attr_" + i);
                attributes.get(i).saveToConfig(entry);
            }
        }
    }

    public static ItemDefinition loadFromConfig(ConfigurationSection sec) {
        String itemId = sec.getString("id");
        if (itemId == null || itemId.isEmpty()) {
            itemId = sec.getName();
        }

        ItemDefinition def = new ItemDefinition(itemId);
        def.setOwningPlugin(sec.getString("owning-plugin", "synthetix"));
        def.setDisplayName(sec.getString("display-name", itemId));

        String matStr = sec.getString("material", "PAPER");
        try {
            def.setMaterial(Material.valueOf(matStr.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            def.setMaterial(Material.PAPER);
        }

        def.setLore(sec.getStringList("lore"));
        if (sec.contains("custom-model-data")) {
            def.setCustomModelData(sec.getInt("custom-model-data"));
        }
        def.setItemModel(sec.getString("item-model"));
        def.setUnbreakable(sec.getBoolean("unbreakable", false));
        def.setHideEnchants(sec.getBoolean("hide-enchants", false));

        if (sec.contains("max-stack-size")) {
            def.setMaxStackSize(sec.getInt("max-stack-size"));
        }
        def.setGlider(sec.getBoolean("glider", false));
        if (sec.contains("fire-resistant")) {
            def.setFireResistant(sec.getBoolean("fire-resistant"));
        }
        if (sec.contains("glint-override")) {
            def.setGlintOverride(sec.getBoolean("glint-override"));
        }
        if (sec.contains("max-damage")) {
            def.setMaxDamage(sec.getInt("max-damage"));
        }
        String rarityStr = sec.getString("rarity");
        if (rarityStr != null) {
            try {
                def.setRarity(ItemRarity.valueOf(rarityStr.toUpperCase(Locale.ROOT)));
            } catch (Exception ignored) {}
        }
        if (sec.contains("trim")) {
            def.setTrim(ArmorTrimSpec.loadFromConfig(sec.getConfigurationSection("trim")));
        }
        String colorHex = sec.getString("color");
        if (colorHex != null && colorHex.startsWith("#")) {
            try {
                int rgb = Integer.parseInt(colorHex.substring(1), 16);
                def.setCustomColor(Color.fromRGB(rgb));
            } catch (Exception ignored) {}
        }
        def.setHideTooltip(sec.getBoolean("hide-tooltip", false));
        if (sec.contains("food")) {
            def.setFood(FoodComponentSpec.loadFromConfig(sec.getConfigurationSection("food")));
        }
        if (sec.contains("aura")) {
            def.setAura(AuraSpec.loadFromConfig(sec.getConfigurationSection("aura")));
        }
        if (sec.contains("abilities")) {
            ConfigurationSection abilSec = sec.getConfigurationSection("abilities");
            if (abilSec != null) {
                Map<String, AbilitySpec> map = new HashMap<>();
                for (String k : abilSec.getKeys(false)) {
                    ConfigurationSection aEntry = abilSec.getConfigurationSection(k);
                    if (aEntry != null) {
                        AbilitySpec spec = AbilitySpec.loadFromConfig(aEntry, k);
                        if (spec != null) {
                            map.put(k.toLowerCase(Locale.ROOT), spec);
                        }
                    }
                }
                def.setAbilities(map);
            }
        }

        // Load enchants
        ConfigurationSection enchSec = sec.getConfigurationSection("enchants");
        if (enchSec != null) {
            Map<String, Integer> map = new HashMap<>();
            for (String key : enchSec.getKeys(false)) {
                map.put(key, enchSec.getInt(key));
            }
            def.setEnchants(map);
        }

        // Load tags
        ConfigurationSection tagsSec = sec.getConfigurationSection("tags");
        if (tagsSec != null) {
            Map<String, String> map = new HashMap<>();
            for (String key : tagsSec.getKeys(false)) {
                map.put(key, tagsSec.getString(key));
            }
            def.setTags(map);
        }

        // Load item flags
        List<String> flagList = sec.getStringList("flags");
        if (flagList != null && !flagList.isEmpty()) {
            Set<ItemFlag> flags = new HashSet<>();
            for (String f : flagList) {
                try {
                    flags.add(ItemFlag.valueOf(f));
                } catch (Exception ignored) {}
            }
            def.setItemFlags(flags);
        }

        // Load attributes
        ConfigurationSection attrSec = sec.getConfigurationSection("attributes");
        if (attrSec != null) {
            List<AttributeSpec> list = new ArrayList<>();
            for (String key : attrSec.getKeys(false)) {
                ConfigurationSection entry = attrSec.getConfigurationSection(key);
                if (entry != null) {
                    AttributeSpec spec = AttributeSpec.loadFromConfig(entry);
                    if (spec != null) {
                        list.add(spec);
                    }
                }
            }
            def.setAttributes(list);
        }

        return def;
    }

    // ========== Getters & Setters ==========

    public String getId() { return id; }
    public String getOwningPlugin() { return owningPlugin; }
    public void setOwningPlugin(String owningPlugin) { this.owningPlugin = owningPlugin; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public Material getMaterial() { return material; }
    public void setMaterial(Material material) { this.material = material; }
    public List<String> getLore() { return lore; }
    public void setLore(List<String> lore) { this.lore = lore != null ? new ArrayList<>(lore) : new ArrayList<>(); }
    public Integer getCustomModelData() { return customModelData; }
    public void setCustomModelData(Integer customModelData) { this.customModelData = customModelData; }
    public boolean hasCustomModelData() { return customModelData != null && customModelData > 0; }
    public String getItemModel() { return itemModel; }
    public void setItemModel(String itemModel) {
        if (itemModel != null && !itemModel.trim().isEmpty()) {
            this.itemModel = org.bg52.synthetix.block.BlockModelPart.normalizeModelKey(itemModel);
        } else {
            this.itemModel = null;
        }
    }
    public boolean isUnbreakable() { return unbreakable; }
    public void setUnbreakable(boolean unbreakable) { this.unbreakable = unbreakable; }
    public boolean isHideEnchants() { return hideEnchants; }
    public void setHideEnchants(boolean hideEnchants) { this.hideEnchants = hideEnchants; }
    public Integer getMaxStackSize() { return maxStackSize; }
    public void setMaxStackSize(Integer maxStackSize) { this.maxStackSize = maxStackSize; }
    public boolean isGlider() { return glider; }
    public void setGlider(boolean glider) { this.glider = glider; }
    public boolean isFireResistant() { return fireResistant; }
    public void setFireResistant(boolean fireResistant) { this.fireResistant = fireResistant; }
    public Map<String, Integer> getEnchants() { return enchants; }
    public void setEnchants(Map<String, Integer> enchants) { this.enchants = enchants != null ? new HashMap<>(enchants) : new HashMap<>(); }
    public Map<String, String> getTags() { return tags; }
    public void setTags(Map<String, String> tags) { this.tags = tags != null ? new HashMap<>(tags) : new HashMap<>(); }
    public void setTag(String key, String value) { this.tags.put(key, value); }
    public String getTag(String key) { return this.tags.get(key); }
    public Set<ItemFlag> getItemFlags() { return itemFlags; }
    public void setItemFlags(Set<ItemFlag> itemFlags) { this.itemFlags = itemFlags != null ? new HashSet<>(itemFlags) : new HashSet<>(); }
    public List<AttributeSpec> getAttributes() { return attributes; }
    public void setAttributes(List<AttributeSpec> attributes) { this.attributes = attributes != null ? new ArrayList<>(attributes) : new ArrayList<>(); }

    public Boolean getGlintOverride() { return glintOverride; }
    public void setGlintOverride(Boolean glintOverride) { this.glintOverride = glintOverride; }
    public Integer getMaxDamage() { return maxDamage; }
    public void setMaxDamage(Integer maxDamage) { this.maxDamage = maxDamage; }
    public ItemRarity getRarity() { return rarity; }
    public void setRarity(ItemRarity rarity) { this.rarity = rarity; }
    public ArmorTrimSpec getTrim() { return trim; }
    public void setTrim(ArmorTrimSpec trim) { this.trim = trim; }
    public Color getCustomColor() { return customColor; }
    public void setCustomColor(Color customColor) { this.customColor = customColor; }
    public boolean isHideTooltip() { return hideTooltip; }
    public void setHideTooltip(boolean hideTooltip) { this.hideTooltip = hideTooltip; }
    public FoodComponentSpec getFood() { return food; }
    public void setFood(FoodComponentSpec food) { this.food = food; }
    public AuraSpec getAura() { return aura; }
    public void setAura(AuraSpec aura) { this.aura = aura; }
    public Map<String, AbilitySpec> getAbilities() { return abilities; }
    public void setAbilities(Map<String, AbilitySpec> abilities) { this.abilities = abilities != null ? new HashMap<>(abilities) : new HashMap<>(); }

    /**
     * Creates a deep copy of this definition under a new ID.
     */
    public ItemDefinition copy(String newId) {
        ItemDefinition copy = new ItemDefinition(newId);
        copy.setOwningPlugin(this.owningPlugin);
        copy.setDisplayName(this.displayName);
        copy.setMaterial(this.material);
        copy.setLore(new ArrayList<>(this.lore));
        copy.setCustomModelData(this.customModelData);
        copy.setItemModel(this.itemModel);
        copy.setUnbreakable(this.unbreakable);
        copy.setHideEnchants(this.hideEnchants);
        copy.setMaxStackSize(this.maxStackSize);
        copy.setGlider(this.glider);
        copy.setFireResistant(this.fireResistant);
        copy.setGlintOverride(this.glintOverride);
        copy.setMaxDamage(this.maxDamage);
        copy.setRarity(this.rarity);
        copy.setTrim(this.trim != null ? this.trim.copy() : null);
        copy.setCustomColor(this.customColor);
        copy.setHideTooltip(this.hideTooltip);
        copy.setFood(this.food != null ? this.food.copy() : null);
        copy.setAura(this.aura != null ? this.aura.copy() : null);
        if (this.abilities != null) {
            Map<String, AbilitySpec> aCopies = new HashMap<>();
            this.abilities.forEach((k, v) -> aCopies.put(k, v.copy()));
            copy.setAbilities(aCopies);
        }
        copy.setEnchants(new HashMap<>(this.enchants));
        copy.setTags(new HashMap<>(this.tags));
        copy.setItemFlags(new HashSet<>(this.itemFlags));
        List<AttributeSpec> attrCopies = new ArrayList<>();
        for (AttributeSpec spec : this.attributes) {
            attrCopies.add(new AttributeSpec(spec.getAttributeName(), spec.getName(), spec.getAmount(), spec.getOperation(), spec.getSlot()));
        }
        copy.setAttributes(attrCopies);
        return copy;
    }
}
