package org.bg52.synthetix.recipe;

import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.item.ItemDefinition;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Universal recipe model holding configuration, ingredients, and metadata for all supported recipe types.
 */
public class SynthetixRecipe {

    private final String id;
    private RecipeType type;
    private final String resultItemId;
    private final Material resultMaterial;
    private int resultAmount;
    private boolean enabled = true;

    // Shaped Crafting
    private List<String> shape; // e.g. ["D D", " S ", " S "]
    private Map<Character, IngredientChoice> shapedIngredients;

    // Shapeless Crafting
    private List<IngredientChoice> shapelessIngredients;

    // Cooking (Furnace, Blast Furnace, Smoker, Campfire) & Stonecutter
    private IngredientChoice cookingInput;
    private int cookingTime; // ticks
    private float experience;

    // Smithing
    private IngredientChoice smithingTemplate;
    private IngredientChoice smithingBase;
    private IngredientChoice smithingAddition;

    // Anvil
    private IngredientChoice anvilLeft;
    private IngredientChoice anvilRight;
    private int anvilCost;
    private boolean anvilPreserveName;

    // Brewing
    private IngredientChoice brewingIngredient;
    private IngredientChoice brewingBase;

    public SynthetixRecipe(String id, RecipeType type, String result, int amount) {
        this.id = id.toLowerCase(Locale.ROOT);
        this.type = type;
        this.resultAmount = Math.max(1, amount);

        if (result.toLowerCase(Locale.ROOT).startsWith("synth:")) {
            this.resultItemId = result.substring(6).toLowerCase(Locale.ROOT);
            this.resultMaterial = null;
        } else {
            Material mat = Material.matchMaterial(result);
            if (mat != null) {
                this.resultMaterial = mat;
                this.resultItemId = null;
            } else {
                this.resultItemId = result.toLowerCase(Locale.ROOT);
                this.resultMaterial = null;
            }
        }
    }

    /**
     * Builds the result ItemStack of this recipe.
     * Returns null if the custom item definition or material cannot be resolved.
     */
    public ItemStack buildResult(SynthetixPlugin plugin) {
        ItemStack item = null;
        if (resultItemId != null) {
            ItemDefinition def = plugin.getItemRegistry().get(resultItemId);
            if (def != null) {
                item = def.buildItemStack();
            } else {
                var blockDef = plugin.getCustomBlockRegistry() != null ? plugin.getCustomBlockRegistry().get(resultItemId) : null;
                if (blockDef != null) {
                    item = blockDef.buildBlockItemStack(1);
                } else {
                    return null;
                }
            }
        } else if (resultMaterial != null && resultMaterial != Material.AIR) {
            item = new ItemStack(resultMaterial);
        }

        if (item != null) {
            item.setAmount(resultAmount);
        }
        return item;
    }

    /**
     * Trims empty rows and columns from a shaped recipe grid to conform to standard Minecraft bounding box rules.
     */
    public static List<String> trimShape(List<String> rawShape) {
        if (rawShape == null || rawShape.isEmpty()) return Collections.emptyList();

        int minRow = Integer.MAX_VALUE;
        int maxRow = -1;
        int minCol = Integer.MAX_VALUE;
        int maxCol = -1;

        for (int r = 0; r < rawShape.size(); r++) {
            String row = rawShape.get(r);
            for (int c = 0; c < row.length(); c++) {
                if (row.charAt(c) != ' ') {
                    minRow = Math.min(minRow, r);
                    maxRow = Math.max(maxRow, r);
                    minCol = Math.min(minCol, c);
                    maxCol = Math.max(maxCol, c);
                }
            }
        }

        if (maxRow == -1 || minRow > maxRow || minCol > maxCol) {
            return Collections.emptyList();
        }

        List<String> trimmed = new ArrayList<>();
        for (int r = minRow; r <= maxRow; r++) {
            String row = rawShape.get(r);
            int end = Math.min(row.length(), maxCol + 1);
            if (minCol < row.length()) {
                trimmed.add(row.substring(minCol, end));
            } else {
                trimmed.add("");
            }
        }

        return trimmed;
    }

    /**
     * Sanitizes a key to strictly conform to Minecraft NamespacedKey pattern [a-z0-9/._-].
     */
    public static String sanitizeKey(String key) {
        if (key == null) return "unknown";
        String sanitized = key.toLowerCase(Locale.ROOT).replace(' ', '_').replace(':', '_');
        sanitized = sanitized.replaceAll("[^a-z0-9/._-]", "_");
        while (sanitized.contains("__")) {
            sanitized = sanitized.replace("__", "_");
        }
        if (sanitized.startsWith("_")) {
            sanitized = sanitized.substring(1);
        }
        if (sanitized.endsWith("_")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1);
        }
        if (sanitized.isEmpty()) {
            sanitized = "recipe";
        }
        return sanitized;
    }

    public NamespacedKey toNamespacedKey(SynthetixPlugin plugin) {
        String safeId = sanitizeKey(id);
        if (!safeId.startsWith("recipe_")) {
            safeId = "recipe_" + safeId;
        }
        return new NamespacedKey(plugin, safeId);
    }

    // ========== Load from YAML Configuration ==========

    public static SynthetixRecipe loadFromConfig(String id, ConfigurationSection sec) {
        if (sec == null) return null;

        String typeStr = sec.getString("type", "SHAPED");
        RecipeType type = RecipeType.fromString(typeStr);
        if (type == null) type = RecipeType.SHAPED;

        String resultStr = sec.getString("result", "AIR");
        int amount = sec.getInt("amount", 1);

        SynthetixRecipe recipe = new SynthetixRecipe(id, type, resultStr, amount);
        recipe.enabled = sec.getBoolean("enabled", true);

        switch (type) {
            case SHAPED -> {
                recipe.shape = trimShape(sec.getStringList("shape"));
                recipe.shapedIngredients = new HashMap<>();
                ConfigurationSection ingSec = sec.getConfigurationSection("ingredients");
                if (ingSec != null) {
                    for (String key : ingSec.getKeys(false)) {
                        if (!key.isEmpty()) {
                            recipe.shapedIngredients.put(key.charAt(0), new IngredientChoice(ingSec.getString(key)));
                        }
                    }
                }
            }
            case SHAPELESS -> {
                recipe.shapelessIngredients = new ArrayList<>();
                List<String> list = sec.getStringList("ingredients");
                for (String s : list) {
                    recipe.shapelessIngredients.add(new IngredientChoice(s));
                }
            }
            case FURNACE, BLAST_FURNACE, SMOKER, CAMPFIRE -> {
                recipe.cookingInput = new IngredientChoice(sec.getString("input", "AIR"));
                recipe.cookingTime = sec.getInt("cooking-time", type == RecipeType.CAMPFIRE ? 600 : (type == RecipeType.FURNACE ? 200 : 100));
                recipe.experience = (float) sec.getDouble("experience", 0.7);
            }
            case STONECUTTER -> {
                recipe.cookingInput = new IngredientChoice(sec.getString("input", "AIR"));
            }
            case SMITHING_TRANSFORM -> {
                recipe.smithingTemplate = new IngredientChoice(sec.getString("template", "NETHERITE_UPGRADE_SMITHING_TEMPLATE"));
                recipe.smithingBase = new IngredientChoice(sec.getString("base", "DIAMOND_SWORD"));
                recipe.smithingAddition = new IngredientChoice(sec.getString("addition", "NETHERITE_INGOT"));
            }
            case ANVIL -> {
                recipe.anvilLeft = new IngredientChoice(sec.getString("left", "AIR"));
                recipe.anvilRight = new IngredientChoice(sec.getString("right", "AIR"));
                recipe.anvilCost = sec.getInt("cost", 3);
                recipe.anvilPreserveName = sec.getBoolean("preserve-name", true);
            }
            case BREWING -> {
                recipe.brewingIngredient = new IngredientChoice(sec.getString("ingredient", "BLAZE_POWDER"));
                recipe.brewingBase = new IngredientChoice(sec.getString("base", "POTION"));
            }
        }

        return recipe;
    }

    // ========== Getters and Setters ==========

    public String getId() {
        return id;
    }

    public RecipeType getType() {
        return type;
    }

    public void setType(RecipeType type) {
        this.type = type;
    }

    public String getResultItemId() {
        return resultItemId;
    }

    public Material getResultMaterial() {
        return resultMaterial;
    }

    public int getResultAmount() {
        return resultAmount;
    }

    public void setResultAmount(int resultAmount) {
        this.resultAmount = Math.max(1, resultAmount);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Serializes this recipe into the provided YAML configuration section.
     */
    public void saveToConfig(ConfigurationSection sec) {
        sec.set("type", type.name());
        sec.set("enabled", enabled);
        if (resultItemId != null) {
            sec.set("result", "synth:" + resultItemId);
        } else if (resultMaterial != null) {
            sec.set("result", resultMaterial.name());
        }
        sec.set("amount", resultAmount);

        switch (type) {
            case SHAPED -> {
                sec.set("shape", shape);
                ConfigurationSection ingSec = sec.createSection("ingredients");
                if (shapedIngredients != null) {
                    for (Map.Entry<Character, IngredientChoice> entry : shapedIngredients.entrySet()) {
                        ingSec.set(String.valueOf(entry.getKey()), entry.getValue().getRawInput());
                    }
                }
            }
            case SHAPELESS -> {
                List<String> list = new ArrayList<>();
                if (shapelessIngredients != null) {
                    for (IngredientChoice ing : shapelessIngredients) {
                        list.add(ing.getRawInput());
                    }
                }
                sec.set("ingredients", list);
            }
            case FURNACE, BLAST_FURNACE, SMOKER, CAMPFIRE -> {
                sec.set("input", cookingInput != null ? cookingInput.getRawInput() : "AIR");
                sec.set("cooking-time", cookingTime);
                sec.set("experience", experience);
            }
            case STONECUTTER -> {
                sec.set("input", cookingInput != null ? cookingInput.getRawInput() : "AIR");
            }
            case SMITHING_TRANSFORM -> {
                sec.set("template", smithingTemplate != null ? smithingTemplate.getRawInput() : "NETHERITE_UPGRADE_SMITHING_TEMPLATE");
                sec.set("base", smithingBase != null ? smithingBase.getRawInput() : "DIAMOND_SWORD");
                sec.set("addition", smithingAddition != null ? smithingAddition.getRawInput() : "NETHERITE_INGOT");
            }
            case ANVIL -> {
                sec.set("left", anvilLeft != null ? anvilLeft.getRawInput() : "AIR");
                sec.set("right", anvilRight != null ? anvilRight.getRawInput() : "AIR");
                sec.set("cost", anvilCost);
                sec.set("preserve-name", anvilPreserveName);
            }
            case BREWING -> {
                sec.set("ingredient", brewingIngredient != null ? brewingIngredient.getRawInput() : "BLAZE_POWDER");
                sec.set("base", brewingBase != null ? brewingBase.getRawInput() : "POTION");
            }
        }
    }

    public List<String> getShape() {
        return shape;
    }

    public void setShape(List<String> shape) {
        this.shape = trimShape(shape);
    }

    public Map<Character, IngredientChoice> getShapedIngredients() {
        return shapedIngredients;
    }

    public void setShapedIngredients(Map<Character, IngredientChoice> shapedIngredients) {
        this.shapedIngredients = shapedIngredients;
    }

    public List<IngredientChoice> getShapelessIngredients() {
        return shapelessIngredients;
    }

    public void setShapelessIngredients(List<IngredientChoice> shapelessIngredients) {
        this.shapelessIngredients = shapelessIngredients;
    }

    public IngredientChoice getCookingInput() {
        return cookingInput;
    }

    public void setCookingInput(IngredientChoice cookingInput) {
        this.cookingInput = cookingInput;
    }

    public int getCookingTime() {
        return cookingTime;
    }

    public void setCookingTime(int cookingTime) {
        this.cookingTime = cookingTime;
    }

    public float getExperience() {
        return experience;
    }

    public void setExperience(float experience) {
        this.experience = experience;
    }

    public IngredientChoice getSmithingTemplate() {
        return smithingTemplate;
    }

    public void setSmithingTemplate(IngredientChoice smithingTemplate) {
        this.smithingTemplate = smithingTemplate;
    }

    public IngredientChoice getSmithingBase() {
        return smithingBase;
    }

    public void setSmithingBase(IngredientChoice smithingBase) {
        this.smithingBase = smithingBase;
    }

    public IngredientChoice getSmithingAddition() {
        return smithingAddition;
    }

    public void setSmithingAddition(IngredientChoice smithingAddition) {
        this.smithingAddition = smithingAddition;
    }

    public IngredientChoice getAnvilLeft() {
        return anvilLeft;
    }

    public void setAnvilLeft(IngredientChoice anvilLeft) {
        this.anvilLeft = anvilLeft;
    }

    public IngredientChoice getAnvilRight() {
        return anvilRight;
    }

    public void setAnvilRight(IngredientChoice anvilRight) {
        this.anvilRight = anvilRight;
    }

    public int getAnvilCost() {
        return anvilCost;
    }

    public void setAnvilCost(int anvilCost) {
        this.anvilCost = anvilCost;
    }

    public boolean isAnvilPreserveName() {
        return anvilPreserveName;
    }

    public void setAnvilPreserveName(boolean anvilPreserveName) {
        this.anvilPreserveName = anvilPreserveName;
    }

    public IngredientChoice getBrewingIngredient() {
        return brewingIngredient;
    }

    public void setBrewingIngredient(IngredientChoice brewingIngredient) {
        this.brewingIngredient = brewingIngredient;
    }

    public IngredientChoice getBrewingBase() {
        return brewingBase;
    }

    public void setBrewingBase(IngredientChoice brewingBase) {
        this.brewingBase = brewingBase;
    }
}

