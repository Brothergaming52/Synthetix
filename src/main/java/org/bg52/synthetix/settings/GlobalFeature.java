package org.bg52.synthetix.settings;

import org.bukkit.Material;

/**
 * Universal registry of all toggleable global features and subsystems across Synthetix.
 */
public enum GlobalFeature {
    RECIPES_ALL("recipes-all", "Global Custom Recipes", "Master toggle for all custom crafting, cooking, and station recipes.", Material.CRAFTING_TABLE, 10, true),
    RECIPES_ITEMS("recipes-items", "Custom Item Recipes", "Toggles crafting and smelting recipes for custom items specifically.", Material.DIAMOND_SWORD, 11, true),
    RECIPES_BLOCKS("recipes-blocks", "Custom Block Recipes", "Toggles crafting and smelting recipes for custom blocks specifically.", Material.BRICKS, 12, true),
    CUSTOM_BLOCKS("custom-blocks", "Virtual Block Engine", "Toggles custom block placement, interactions, mining, and display entities.", Material.NOTE_BLOCK, 13, true),
    CUSTOM_ENTITIES("custom-entities", "Custom Entities Engine", "Toggles custom Bedrock entity spawning, AI behavior charts, and animations.", Material.ZOMBIE_HEAD, 14, true),
    ITEM_ABILITIES("item-abilities", "Item Abilities & Triggers", "Toggles all 15+ interaction triggers (right-click, hit, consume, etc.).", Material.BLAZE_ROD, 15, true),
    PARTICLE_AURAS("particle-auras", "Ambient Particle Auras", "Toggles continuous ambient particle trails, wings, and halos.", Material.NETHER_STAR, 16, true),
    EQUIPMENT_SETS("equipment-sets", "Equipment Sets Engine", "Toggles armor and curio set bonuses and dynamic lore formatting.", Material.NETHERITE_CHESTPLATE, 19, true),
    MOB_DROPS("mob-drops", "World Mob Drops", "Toggles custom item drop rules from vanilla and custom mobs.", Material.ROTTEN_FLESH, 20, true),
    LOOT_TABLES("loot-tables", "Dungeon Loot Tables", "Toggles injection of custom items into dungeon, trial chamber, and pot loot.", Material.CHEST, 21, true),
    MERCHANT_TRADES("merchant-trades", "Merchant Trades", "Toggles custom trade offers for Villagers and Wandering Traders.", Material.EMERALD, 22, true),
    RESOURCE_PACK_PROMPT("resource-pack-prompt", "Resource Pack Join Prompt", "Toggles automatic resource pack prompt dispatch on player join.", Material.FILLED_MAP, 23, true),
    GEYSER_CONTAINERS("geyser-custom-containers", "Bedrock Container Bridge", "Toggles custom 3-row Anvil and Smithing GUIs for Bedrock players.", Material.ANVIL, 24, true);

    private final String id;
    private final String displayName;
    private final String description;
    private final Material icon;
    private final int slot;
    private final boolean defaultState;

    GlobalFeature(String id, String displayName, String description, Material icon, int slot, boolean defaultState) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
        this.slot = slot;
        this.defaultState = defaultState;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public Material getIcon() {
        return icon;
    }

    public int getSlot() {
        return slot;
    }

    public boolean isDefaultState() {
        return defaultState;
    }

    public String getConfigKey() {
        return id;
    }

    public static GlobalFeature fromConfigKey(String key) {
        return fromId(key);
    }

    public static GlobalFeature fromId(String id) {
        if (id == null) return null;
        for (GlobalFeature f : values()) {
            if (f.id.equalsIgnoreCase(id) || f.name().equalsIgnoreCase(id)) {
                return f;
            }
        }
        return null;
    }
}
