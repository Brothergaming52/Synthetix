package org.bg52.synthetix.item.component;

import org.bg52.synthetix.util.VersionChecker;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;

/**
 * Version-resilient wrapper for Minecraft Paper Data Components (1.20.6 through 26.3+).
 * Uses compile-time safe Paper 1.20.6 APIs and dynamic reflection for 1.21+/26.x DataComponent features.
 */
public final class ComponentHandler {

    private static Method setItemModelMethod;
    private static Method getItemModelMethod;
    private static Method setMaxStackSizeMethod;

    // DataComponentTypes reflection cache (1.21+ / 26.x)
    private static Object typeGlider;
    private static Object typeConsumable;
    private static Object typeFood;
    private static Object typeUseCooldown;
    private static Method hasDataMethod;
    private static Method setDataNonValuedMethod;
    private static Method setDataValuedMethod;
    private static Method unsetDataMethod;

    // Consumable & Food builders (1.21.2+ / 26.x)
    private static Method consumableBuilderMethod;
    private static Method consumeSecondsMethod;
    private static Method animationMethod;
    private static Object animEat;
    private static Method hasConsumeParticlesMethod;
    private static Method addConsumeEffectMethod;
    private static Method applyStatusEffectsMethod;
    private static Method buildConsumableMethod;

    private static Method useCooldownBuilderMethod;
    private static Method cooldownGroupMethod;
    private static Method buildUseCooldownMethod;

    private static Method foodPropertiesBuilderMethod;
    private static Method nutritionMethod;
    private static Method saturationMethod;
    private static Method canAlwaysEatMethod;
    private static Method buildFoodPropertiesMethod;

    // Per-ItemStack cooldown reflection (Paper 1.21.2+ / 26.x)
    private static Method setItemStackCooldownMethod;
    private static Method hasItemStackCooldownMethod;
    private static Method getItemStackCooldownMethod;

    static {
        // ItemMeta#setItemModel & getItemModel (1.21.2+ / 26.x)
        try {
            Class<?> metaClass = Class.forName("org.bukkit.inventory.meta.ItemMeta");
            setItemModelMethod = metaClass.getMethod("setItemModel", NamespacedKey.class);
            getItemModelMethod = metaClass.getMethod("getItemModel");
        } catch (Throwable ignored) {
            setItemModelMethod = null;
            getItemModelMethod = null;
        }

        // ItemMeta#setMaxStackSize (1.20.5+)
        try {
            Class<?> metaClass = Class.forName("org.bukkit.inventory.meta.ItemMeta");
            setMaxStackSizeMethod = metaClass.getMethod("setMaxStackSize", Integer.class);
        } catch (Throwable ignored) {
            setMaxStackSizeMethod = null;
        }

        // Paper DataComponentTypes (1.21+ / 26.x)
        if (VersionChecker.supportsDataComponents()) {
            try {
                Class<?> dctClass = Class.forName("io.papermc.paper.datacomponent.DataComponentTypes");
                typeGlider = dctClass.getField("GLIDER").get(null);

                try {
                    typeConsumable = dctClass.getField("CONSUMABLE").get(null);
                } catch (Throwable ignored) {}

                try {
                    typeFood = dctClass.getField("FOOD").get(null);
                } catch (Throwable ignored) {}

                try {
                    typeUseCooldown = dctClass.getField("USE_COOLDOWN").get(null);
                } catch (Throwable ignored) {}

                Class<?> dctTypeClass = Class.forName("io.papermc.paper.datacomponent.DataComponentType");
                Class<?> dctNonValuedClass = Class.forName("io.papermc.paper.datacomponent.DataComponentType$NonValued");
                Class<?> dctValuedClass = Class.forName("io.papermc.paper.datacomponent.DataComponentType$Valued");

                Class<?> isClass = ItemStack.class;
                hasDataMethod = isClass.getMethod("hasData", dctTypeClass);
                unsetDataMethod = isClass.getMethod("unsetData", dctTypeClass);
                setDataNonValuedMethod = isClass.getMethod("setData", dctNonValuedClass);
                setDataValuedMethod = isClass.getMethod("setData", dctValuedClass, Object.class);
            } catch (Throwable ignored) {
                typeGlider = null;
                typeConsumable = null;
                typeFood = null;
                typeUseCooldown = null;
                hasDataMethod = null;
                setDataNonValuedMethod = null;
                setDataValuedMethod = null;
                unsetDataMethod = null;
            }

            // Consumable Builder Reflection (1.21.2+)
            try {
                Class<?> consumableClass = Class.forName("io.papermc.paper.datacomponent.item.Consumable");
                consumableBuilderMethod = consumableClass.getMethod("consumable");
                Class<?> cBuilderClass = Class.forName("io.papermc.paper.datacomponent.item.Consumable$Builder");
                consumeSecondsMethod = cBuilderClass.getMethod("consumeSeconds", float.class);
                buildConsumableMethod = cBuilderClass.getMethod("build");

                try {
                    hasConsumeParticlesMethod = cBuilderClass.getMethod("hasConsumeParticles", boolean.class);
                } catch (Throwable ignored) {}

                try {
                    Class<?> animClass = Class.forName("io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation");
                    animationMethod = cBuilderClass.getMethod("animation", animClass);
                    animEat = Enum.valueOf((Class<Enum>) animClass.asSubclass(Enum.class), "EAT");
                } catch (Throwable ignored) {}

                try {
                    Class<?> ceClass = Class.forName("io.papermc.paper.datacomponent.item.consumable.ConsumeEffect");
                    addConsumeEffectMethod = cBuilderClass.getMethod("addEffect", ceClass);
                    applyStatusEffectsMethod = ceClass.getMethod("applyStatusEffects", java.util.List.class, float.class);
                } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}

            // UseCooldown Builder Reflection (1.21.2+)
            try {
                Class<?> ucClass = Class.forName("io.papermc.paper.datacomponent.item.UseCooldown");
                useCooldownBuilderMethod = ucClass.getMethod("useCooldown", float.class);
                Class<?> ucBuilderClass = Class.forName("io.papermc.paper.datacomponent.item.UseCooldown$Builder");
                cooldownGroupMethod = ucBuilderClass.getMethod("cooldownGroup", net.kyori.adventure.key.Key.class);
                buildUseCooldownMethod = ucBuilderClass.getMethod("build");
            } catch (Throwable ignored) {}

            // FoodProperties Builder Reflection (1.20.5+ / 1.21+)
            try {
                Class<?> foodClass = Class.forName("io.papermc.paper.datacomponent.item.FoodProperties");
                foodPropertiesBuilderMethod = foodClass.getMethod("food");
                Class<?> fBuilderClass = Class.forName("io.papermc.paper.datacomponent.item.FoodProperties$Builder");
                nutritionMethod = fBuilderClass.getMethod("nutrition", int.class);
                saturationMethod = fBuilderClass.getMethod("saturation", float.class);
                canAlwaysEatMethod = fBuilderClass.getMethod("canAlwaysEat", boolean.class);
                buildFoodPropertiesMethod = fBuilderClass.getMethod("build");
            } catch (Throwable ignored) {}
        }

        // Per-ItemStack cooldown reflection on HumanEntity (Paper 1.21.2+ / 26.x)
        try {
            setItemStackCooldownMethod = org.bukkit.entity.HumanEntity.class.getMethod("setCooldown", ItemStack.class, int.class);
            hasItemStackCooldownMethod = org.bukkit.entity.HumanEntity.class.getMethod("hasCooldown", ItemStack.class);
            getItemStackCooldownMethod = org.bukkit.entity.HumanEntity.class.getMethod("getCooldown", ItemStack.class);
        } catch (Throwable ignored) {
            setItemStackCooldownMethod = null;
            hasItemStackCooldownMethod = null;
            getItemStackCooldownMethod = null;
        }
    }

    private ComponentHandler() {}

    /**
     * Applies item_model (NamespacedKey) if supported (1.21.2+ / 26.x),
     * otherwise falls back gracefully to customModelData (1.20.6).
     */
    public static void applyItemModel(ItemMeta meta, String modelKey, Integer customModelData) {
        if (meta == null) return;

        // Try modern item_model component first
        if (modelKey != null && !modelKey.isEmpty() && setItemModelMethod != null) {
            try {
                NamespacedKey key = parseItemModelKey(modelKey);
                if (key != null) {
                    setItemModelMethod.invoke(meta, key);
                }
            } catch (Throwable ignored) {
            }
        }

        // Apply customModelData if set or as numeric fallback
        if (customModelData != null) {
            try {
                meta.setCustomModelData(customModelData);
            } catch (Throwable ignored) {
            }
        } else if (modelKey != null && modelKey.matches("\\d+")) {
            try {
                meta.setCustomModelData(Integer.parseInt(modelKey));
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Reads the item_model string from ItemMeta if set.
     */
    public static String readItemModel(ItemMeta meta) {
        if (meta == null || getItemModelMethod == null) return null;
        try {
            Object res = getItemModelMethod.invoke(meta);
            return res != null ? res.toString() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Applies Glider data component (enables elytra flight) on 1.20.6+ Paper.
     */
    public static void setGlider(ItemStack item, boolean enabled) {
        if (item == null || typeGlider == null) return;
        try {
            if (enabled && setDataNonValuedMethod != null) {
                setDataNonValuedMethod.invoke(item, typeGlider);
            } else if (!enabled && unsetDataMethod != null) {
                unsetDataMethod.invoke(item, typeGlider);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Checks if the item has the Glider component.
     */
    public static boolean hasGlider(ItemStack item) {
        if (item == null || typeGlider == null || hasDataMethod == null) return false;
        try {
            return (boolean) hasDataMethod.invoke(item, typeGlider);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Applies modern 1.21.2+ CONSUMABLE and 1.20.5+ FOOD Data Components directly to the ItemStack.
     */
    public static void applyConsumable(ItemStack item, org.bg52.synthetix.item.food.FoodComponentSpec food) {
        applyConsumable(item, food, null);
    }

    /**
     * Applies modern 1.21.2+ CONSUMABLE and 1.20.5+ FOOD Data Components directly to the ItemStack,
     * ensuring the UseCooldown component is strictly bound to this unique item's cooldown group
     * so that items sharing the same base material are never unintentionally cooled down together.
     */
    public static void applyConsumable(ItemStack item, org.bg52.synthetix.item.food.FoodComponentSpec food, String itemId) {
        if (item == null || food == null) return;

        // 1. Apply FoodProperties component (1.20.5+)
        if (typeFood != null && setDataValuedMethod != null && foodPropertiesBuilderMethod != null) {
            try {
                Object fBuilder = foodPropertiesBuilderMethod.invoke(null);
                if (nutritionMethod != null) nutritionMethod.invoke(fBuilder, food.getNutrition());
                if (saturationMethod != null) saturationMethod.invoke(fBuilder, food.getSaturation());
                if (canAlwaysEatMethod != null) canAlwaysEatMethod.invoke(fBuilder, food.canAlwaysEat());
                if (buildFoodPropertiesMethod != null) {
                    Object foodProps = buildFoodPropertiesMethod.invoke(fBuilder);
                    setDataValuedMethod.invoke(item, typeFood, foodProps);
                }
            } catch (Throwable ignored) {
            }
        }

        // 2. Apply Consumable component (1.21.2+ / 26.x)
        if (typeConsumable != null && setDataValuedMethod != null && consumableBuilderMethod != null) {
            try {
                Object cBuilder = consumableBuilderMethod.invoke(null);
                if (consumeSecondsMethod != null) {
                    consumeSecondsMethod.invoke(cBuilder, food.getEatSeconds());
                }
                if (animationMethod != null && animEat != null) {
                    animationMethod.invoke(cBuilder, animEat);
                }
                if (hasConsumeParticlesMethod != null) {
                    hasConsumeParticlesMethod.invoke(cBuilder, true);
                }

                // Apply effects to consumable component natively
                if (addConsumeEffectMethod != null && applyStatusEffectsMethod != null && food.getEffects() != null) {
                    for (org.bg52.synthetix.item.food.FoodEffectSpec effectSpec : food.getEffects()) {
                        org.bukkit.potion.PotionEffect pe = effectSpec.toPotionEffect();
                        if (pe != null) {
                            Object ce = applyStatusEffectsMethod.invoke(null, java.util.List.of(pe), (float) effectSpec.getProbability());
                            if (ce != null) {
                                addConsumeEffectMethod.invoke(cBuilder, ce);
                            }
                        }
                    }
                }

                if (buildConsumableMethod != null) {
                    Object consumable = buildConsumableMethod.invoke(cBuilder);
                    setDataValuedMethod.invoke(item, typeConsumable, consumable);
                }
            } catch (Throwable ignored) {
            }
        }

        // 3. Apply UseCooldown component (1.21.2+ / 26.x)
        if (food.getEatCooldown() > 0 && typeUseCooldown != null && setDataValuedMethod != null && useCooldownBuilderMethod != null) {
            try {
                Object ucBuilder = useCooldownBuilderMethod.invoke(null, (float) food.getEatCooldown());

                // Resolve unique cooldown group:
                // 1. Explicit cooldown-group configured on food
                // 2. Fallback to itemId (from param or PDC)
                // 3. Guaranteed isolated fallback: "synthetix:consumable"
                String groupKey = null;
                if (food.getCooldownGroup() != null && !food.getCooldownGroup().trim().isEmpty()) {
                    groupKey = food.getCooldownGroup().trim();
                } else if (itemId != null && !itemId.trim().isEmpty()) {
                    groupKey = itemId.trim();
                } else if (item.hasItemMeta()) {
                    org.bukkit.persistence.PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
                    NamespacedKey idKey = new NamespacedKey("synthetix", "item_id");
                    String pdcId = pdc.get(idKey, org.bukkit.persistence.PersistentDataType.STRING);
                    if (pdcId != null && !pdcId.trim().isEmpty()) {
                        groupKey = pdcId.trim();
                    }
                }

                if (groupKey == null || groupKey.trim().isEmpty()) {
                    groupKey = "synthetix:consumable";
                } else if (!groupKey.contains(":")) {
                    groupKey = "synthetix:" + groupKey;
                }

                if (cooldownGroupMethod != null) {
                    cooldownGroupMethod.invoke(ucBuilder, net.kyori.adventure.key.Key.key(groupKey.toLowerCase(java.util.Locale.ROOT)));
                }

                if (buildUseCooldownMethod != null) {
                    Object cooldown = buildUseCooldownMethod.invoke(ucBuilder);
                    setDataValuedMethod.invoke(item, typeUseCooldown, cooldown);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Sets cooldown on a specific ItemStack (or its UseCooldown group) without affecting other items of the same Material.
     */
    public static void setItemCooldown(org.bukkit.entity.Player player, ItemStack item, int ticks) {
        if (player == null || item == null || ticks <= 0) return;
        if (setItemStackCooldownMethod != null) {
            try {
                setItemStackCooldownMethod.invoke(player, item, ticks);
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Checks if a player has an active cooldown for a specific ItemStack (or its UseCooldown group).
     */
    public static boolean hasItemCooldown(org.bukkit.entity.Player player, ItemStack item) {
        if (player == null || item == null) return false;
        if (hasItemStackCooldownMethod != null) {
            try {
                return (boolean) hasItemStackCooldownMethod.invoke(player, item);
            } catch (Throwable ignored) {}
        }
        return false;
    }

    /**
     * Safely sets max stack size (1.20.5+ / 1.20.6+ feature).
     */
    public static void setMaxStackSize(ItemMeta meta, Integer maxStackSize) {
        if (meta == null || maxStackSize == null || setMaxStackSizeMethod == null) return;
        try {
            setMaxStackSizeMethod.invoke(meta, maxStackSize);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Applies enchantment glint override (1.20.5+ / 1.20.6+ Paper feature).
     * true = forced shiny, false = forced no shiny, null = vanilla default.
     */
    public static void applyGlintOverride(ItemMeta meta, Boolean glintOverride) {
        if (meta == null) return;
        try {
            meta.setEnchantmentGlintOverride(glintOverride);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Overrides max durability on Damageable items (1.20.5+ / 1.20.6+ feature).
     */
    public static void applyMaxDamage(ItemMeta meta, Integer maxDamage) {
        if (meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
            try {
                damageable.setMaxDamage(maxDamage);
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Applies native Minecraft Item Rarity (1.20.5+ / 1.20.6+ feature).
     */
    public static void applyRarity(ItemMeta meta, org.bukkit.inventory.ItemRarity rarity) {
        if (meta == null) return;
        try {
            meta.setRarity(rarity);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Applies ArmorTrim specification if this meta is ArmorMeta.
     */
    public static void applyArmorTrim(ItemMeta meta, ArmorTrimSpec trim) {
        if (meta instanceof org.bukkit.inventory.meta.ArmorMeta armorMeta && trim != null) {
            trim.applyTo(armorMeta);
        }
    }

    /**
     * Applies custom RGB color to Leather Armor or Potion meta.
     */
    public static void applyCustomColor(ItemMeta meta, org.bukkit.Color color) {
        if (meta == null || color == null) return;
        if (meta instanceof org.bukkit.inventory.meta.LeatherArmorMeta leatherMeta) {
            leatherMeta.setColor(color);
        } else if (meta instanceof org.bukkit.inventory.meta.PotionMeta potionMeta) {
            potionMeta.setColor(color);
        }
    }

    /**
     * Sets hide tooltip component (1.20.5+ / 1.20.6+ feature).
     */
    public static void applyHideTooltip(ItemMeta meta, boolean hideTooltip) {
        if (meta == null) return;
        try {
            meta.setHideTooltip(hideTooltip);
        } catch (Throwable ignored) {
        }
    }

    public static NamespacedKey parseNamespacedKey(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String clean = raw.trim().toLowerCase();
        if (clean.contains(":")) {
            String[] parts = clean.split(":", 2);
            return new NamespacedKey(parts[0], parts[1]);
        }
        return NamespacedKey.minecraft(clean);
    }

    /**
     * Parses and normalizes a model key for the modern 1.21.2+ item_model component.
     * Ensures prefixes like 'item/' or 'block/' in the path are stripped so the key
     * correctly resolves to assets/<namespace>/items/<path>.json.
     */
    public static NamespacedKey parseItemModelKey(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String normalized = org.bg52.synthetix.block.BlockModelPart.normalizeModelKey(raw);
        if (normalized == null) return null;
        String[] parts = normalized.split(":", 2);
        return new NamespacedKey(parts[0], parts[1]);
    }
}
