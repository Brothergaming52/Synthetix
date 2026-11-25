package org.bg52.synthetix.recipe;

/**
 * Universal recipe types supported by the Synthetix Recipe Engine.
 */
public enum RecipeType {
    SHAPED("Shaped Crafting", "3x3 or smaller crafting table shaped recipe"),
    SHAPELESS("Shapeless Crafting", "Crafting table or inventory grid shapeless recipe"),
    FURNACE("Furnace Smelting", "Standard furnace smelting recipe"),
    BLAST_FURNACE("Blast Furnace", "Fast high-temp ore and armor smelting"),
    SMOKER("Smoker Cooking", "Fast food and meat cooking"),
    CAMPFIRE("Campfire Cooking", "Campfire cooking recipe"),
    STONECUTTER("Stonecutter", "Single ingredient block and stonecutting"),
    SMITHING_TRANSFORM("Smithing Table", "Smithing transform upgrade (template + base + addition)"),
    ANVIL("Anvil Combination", "Anvil combination, repair, and enhancement"),
    BREWING("Brewing Stand", "Brewing stand reagent and potion transformation");

    private final String displayName;
    private final String description;

    RecipeType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public static RecipeType fromString(String name) {
        if (name == null || name.isEmpty()) return null;
        String clean = name.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        try {
            return RecipeType.valueOf(clean);
        } catch (IllegalArgumentException e) {
            return switch (clean) {
                case "CRAFTING_SHAPED", "CRAFTING" -> SHAPED;
                case "CRAFTING_SHAPELESS" -> SHAPELESS;
                case "SMELTING", "COOKING" -> FURNACE;
                case "BLASTING" -> BLAST_FURNACE;
                case "SMOKING" -> SMOKER;
                case "STONE_CUTTER", "STONECUTTING" -> STONECUTTER;
                case "SMITHING" -> SMITHING_TRANSFORM;
                case "BREW" -> BREWING;
                default -> null;
            };
        }
    }
}

