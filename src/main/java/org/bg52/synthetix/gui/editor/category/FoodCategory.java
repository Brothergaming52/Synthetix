package org.bg52.synthetix.gui.editor.category;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.api.editor.ItemEditorCategory;
import org.bg52.synthetix.gui.CanvasButton;
import org.bg52.synthetix.gui.editor.BaseEditorCanvas;
import org.bg52.synthetix.item.ItemDefinition;
import org.bg52.synthetix.item.food.FoodComponentSpec;
import org.bg52.synthetix.item.food.FoodEffectSpec;
import org.bg52.synthetix.lang.LanguageManager;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Core editor category for configuring Food & Consumable properties:
 * Nutrition, Saturation, Can Always Eat, Eat Speeds, Cooldowns, and Status Effects.
 * Redesigned into intuitive clusters with custom value inputs and a full Potion Effect Configurator.
 */
public class FoodCategory implements ItemEditorCategory {

    private final SynthetixPlugin plugin;
    private final LanguageManager lang;

    public FoodCategory(SynthetixPlugin plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    @Override
    public String getId() {
        return "food";
    }

    @Override
    public Component getDisplayName() {
        return TextUtil.parse("<gold><bold>Food & Consumables</bold></gold>");
    }

    @Override
    public int getPriority() {
        return 25;
    }

    @Override
    public boolean isPluginCategory() {
        return false;
    }

    @Override
    public ItemStack getIcon(ItemDefinition def) {
        ItemStack item = new ItemStack(Material.GOLDEN_APPLE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(getDisplayName());
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Turn any item into a consumable with</gray>"));
            lore.add(TextUtil.parse("<gray>nutrition, speeds, cooldowns & effects.</gray>"));
            lore.add(Component.empty());

            FoodComponentSpec food = def.getFood();
            boolean enabled = food != null;
            lore.add(TextUtil.parse("<dark_gray>•</dark_gray> <gray>Consumable:</gray> " + (enabled ? lang.get("general.enabled") : lang.get("general.disabled"))));
            if (enabled) {
                lore.add(TextUtil.parse("<dark_gray>•</dark_gray> <gray>Nutrition:</gray> <white>" + food.getNutrition() + "</white>"));
                lore.add(TextUtil.parse("<dark_gray>•</dark_gray> <gray>Eat Seconds:</gray> <white>" + food.getEatSeconds() + "s</white>"));
                lore.add(TextUtil.parse("<dark_gray>•</dark_gray> <gray>Eat Cooldown:</gray> <white>" + food.getEatCooldown() + "s</white>"));
                lore.add(TextUtil.parse("<dark_gray>•</dark_gray> <gray>Effects:</gray> <white>" + food.getEffects().size() + "</white>"));
            }
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>Click to open settings »</yellow>"));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void open(Player player, ItemDefinition def, Runnable onBack) {
        BaseEditorCanvas base = new BaseEditorCanvas(plugin, def, getDisplayName(), 5);
        base.setupSideBorders();

        FoodComponentSpec food = def.getFood();
        boolean enabled = food != null;

        // 1. Enable / Disable Consumable Toggle (Slot 10)
        ItemStack toggleBtn = createToggleBtn(Material.GOLDEN_APPLE,
                "<gold>Consumable Status</gold>",
                enabled,
                "Enables food consumption behavior on this item.");
        base.getCanvas().setButton(10, CanvasButton.of(toggleBtn, e -> {
            if (def.getFood() == null) {
                def.setFood(new FoodComponentSpec());
            } else {
                def.setFood(null);
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            open(player, def, onBack);
        }));

        if (food != null) {
            // 2. Nutrition Points (Slot 11) - Quick Clicks + Custom Input
            ItemStack nutrBtn = createOptionBtn(Material.BREAD,
                    "<gold>Nutrition (Hunger)</gold>",
                    "<gray>Restores:</gray> <white>" + food.getNutrition() + " points (" + (food.getNutrition() / 2.0) + " shanks)</white>",
                    "",
                    "<yellow>Left-Click: +1 | Right-Click: -1</yellow>",
                    "<yellow>Shift-Left: +4 | Shift-Right: Type custom</yellow>");
            base.getCanvas().setButton(11, CanvasButton.of(nutrBtn, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                if (e.isShiftClick() && e.isRightClick()) {
                    plugin.getChatPromptManager().prompt(player,
                            TextUtil.parse("<gray>Enter nutrition points integer (0 to 100):</gray>"),
                            input -> {
                                try {
                                    int n = Integer.parseInt(input.trim());
                                    food.setNutrition(Math.max(0, n));
                                } catch (Exception ignored) {}
                                open(player, def, onBack);
                            });
                } else if (e.isShiftClick()) {
                    food.setNutrition(Math.min(100, food.getNutrition() + 4));
                    open(player, def, onBack);
                } else if (e.isRightClick()) {
                    food.setNutrition(Math.max(0, food.getNutrition() - 1));
                    open(player, def, onBack);
                } else {
                    food.setNutrition(Math.min(100, food.getNutrition() + 1));
                    open(player, def, onBack);
                }
            }));

            // 3. Saturation (Slot 12) - Quick Clicks + Custom Input
            ItemStack satBtn = createOptionBtn(Material.COOKED_BEEF,
                    "<gold>Saturation Ratio</gold>",
                    "<gray>Current:</gray> <white>" + String.format(Locale.ROOT, "%.1f", food.getSaturation()) + "</white>",
                    "",
                    "<yellow>Left-Click: +0.2 | Right-Click: -0.2</yellow>",
                    "<yellow>Shift-Right: Type custom saturation</yellow>");
            base.getCanvas().setButton(12, CanvasButton.of(satBtn, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                if (e.isShiftClick() && e.isRightClick()) {
                    plugin.getChatPromptManager().prompt(player,
                            TextUtil.parse("<gray>Enter saturation ratio float (e.g. <yellow>0.6</yellow>, <yellow>1.5</yellow>):</gray>"),
                            input -> {
                                try {
                                    float s = Float.parseFloat(input.trim());
                                    food.setSaturation(Math.max(0.0f, s));
                                } catch (Exception ignored) {}
                                open(player, def, onBack);
                            });
                } else if (e.isRightClick()) {
                    food.setSaturation(Math.max(0.0f, food.getSaturation() - 0.2f));
                    open(player, def, onBack);
                } else {
                    food.setSaturation(Math.min(10.0f, food.getSaturation() + 0.2f));
                    open(player, def, onBack);
                }
            }));

            // 4. Can Always Eat Toggle (Slot 14)
            ItemStack alwaysEatBtn = createToggleBtn(Material.POTION,
                    "<gold>Can Always Eat</gold>",
                    food.canAlwaysEat(),
                    "Allow eating even when player hunger bar is completely full.");
            base.getCanvas().setButton(14, CanvasButton.of(alwaysEatBtn, e -> {
                food.setCanAlwaysEat(!food.canAlwaysEat());
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                open(player, def, onBack);
            }));

            // 5. Eat Duration in Seconds (Slot 15) - Quick Cycle + Custom Input
            ItemStack eatSecBtn = createOptionBtn(Material.CLOCK,
                    "<gold>Eat Duration (Seconds)</gold>",
                    "<gray>Current:</gray> <white>" + String.format(Locale.ROOT, "%.1fs", food.getEatSeconds()) + "</white>",
                    "",
                    "<yellow>Left-Click: Cycle speeds (0.8s, 1.6s, 2.4s, 0.1s)</yellow>",
                    "<yellow>Right-Click / Shift-Click: Type custom seconds</yellow>");
            base.getCanvas().setButton(15, CanvasButton.of(eatSecBtn, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                if (e.isRightClick() || e.isShiftClick()) {
                    plugin.getChatPromptManager().prompt(player,
                            TextUtil.parse("<gray>Enter eat duration in seconds (e.g. <yellow>1.6</yellow>, <yellow>2.5</yellow>, <yellow>0.1</yellow> for instant):</gray>"),
                            input -> {
                                try {
                                    float sec = Float.parseFloat(input.trim());
                                    food.setEatSeconds(Math.max(0.05f, sec));
                                } catch (Exception ignored) {}
                                open(player, def, onBack);
                            });
                } else {
                    float cur = food.getEatSeconds();
                    if (cur <= 0.2f) food.setEatSeconds(0.8f);
                    else if (cur <= 1.0f) food.setEatSeconds(1.6f);
                    else if (cur <= 1.8f) food.setEatSeconds(2.4f);
                    else if (cur <= 2.6f) food.setEatSeconds(0.1f);
                    else food.setEatSeconds(1.6f);
                    open(player, def, onBack);
                }
            }));

            // 6. Eat Cooldown (Slot 16) - Quick Cycle + Custom Input
            String currentGroup = food.getCooldownGroup() != null && !food.getCooldownGroup().trim().isEmpty()
                    ? food.getCooldownGroup() : (def.getId() + " (Item Specific)");
            ItemStack cdBtn = createOptionBtn(Material.HONEY_BOTTLE,
                    "<gold>Consumption Cooldown</gold>",
                    "<gray>Duration:</gray> <white>" + food.getEatCooldown() + "s</white>",
                    "<gray>Cooldown Group:</gray> <aqua>" + currentGroup + "</aqua>",
                    "",
                    "<yellow>Left-Click: Cycle (0s, 3s, 5s, 10s, 30s)</yellow>",
                    "<yellow>Right-Click: Type custom cooldown duration</yellow>",
                    "<yellow>Shift-Right-Click: Change cooldown group</yellow>");
            base.getCanvas().setButton(16, CanvasButton.of(cdBtn, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                if (e.isShiftClick() && e.isRightClick()) {
                    plugin.getChatPromptManager().prompt(player,
                            TextUtil.parse("<gray>Enter cooldown group identifier (type <yellow>'default'</yellow> to isolate to this item only):</gray>"),
                            input -> {
                                String val = input.trim();
                                if (val.equalsIgnoreCase("default") || val.equalsIgnoreCase("none") || val.isEmpty()) {
                                    food.setCooldownGroup(null);
                                } else {
                                    food.setCooldownGroup(val);
                                }
                                open(player, def, onBack);
                            });
                } else if (e.isRightClick() || e.isShiftClick()) {
                    plugin.getChatPromptManager().prompt(player,
                            TextUtil.parse("<gray>Enter consumption cooldown seconds (e.g. <yellow>5.0</yellow>):</gray>"),
                            input -> {
                                try {
                                    double c = Double.parseDouble(input.trim());
                                    food.setEatCooldown(Math.max(0.0, c));
                                } catch (Exception ignored) {}
                                open(player, def, onBack);
                            });
                } else {
                    double cur = food.getEatCooldown();
                    if (cur < 1.0) food.setEatCooldown(3.0);
                    else if (cur < 4.0) food.setEatCooldown(5.0);
                    else if (cur < 7.0) food.setEatCooldown(10.0);
                    else if (cur < 15.0) food.setEatCooldown(30.0);
                    else food.setEatCooldown(0.0);
                    open(player, def, onBack);
                }
            }));

            // 7. Status Effects Sub-Menu (Slot 22)
            ItemStack effBtn = createOptionBtn(Material.BREWING_STAND,
                    "<gold><bold>Food Status Effects (" + food.getEffects().size() + ")</bold></gold>",
                    "<gray>Configure custom potion effects, duration & level.</gray>",
                    "",
                    "<yellow>Click to open Status Effects Manager »</yellow>");
            base.getCanvas().setButton(22, CanvasButton.of(effBtn, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
                openEffectsMenu(player, def, () -> open(player, def, onBack));
            }));
        }

        base.setBackButton(onBack);
        base.setSaveButton();
        base.open(player);
    }

    private void openEffectsMenu(Player player, ItemDefinition def, Runnable onBack) {
        BaseEditorCanvas base = new BaseEditorCanvas(plugin, def, TextUtil.parse("<gold>Food Status Effects</gold>"), 5);
        base.setupSideBorders();

        FoodComponentSpec food = def.getFood();
        if (food == null) {
            onBack.run();
            return;
        }

        // 1. Browse All Potion Effects (Slot 11)
        ItemStack browseBtn = createOptionBtn(Material.POTION,
                "<green><bold>+ Select Effect From All Types</bold></green>",
                "<gray>Pick Speed, Strength, Absorption, Invisibility,</gray>",
                "<gray>Regeneration, Resistance, Conduit Power, etc.</gray>",
                "",
                "<yellow>Click to browse all PotionEffectTypes »</yellow>");
        base.getCanvas().setButton(11, CanvasButton.of(browseBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            openPotionEffectTypeSelector(player, def, () -> openEffectsMenu(player, def, onBack), 0);
        }));

        // 2. Custom Prompt Input (Slot 15)
        ItemStack promptBtn = createOptionBtn(Material.WRITABLE_BOOK,
                "<aqua><bold>+ Type Custom Effect String</bold></aqua>",
                "<gray>Direct input: type, seconds, amplifier, chance.</gray>",
                "",
                "<yellow>Click to type custom effect in chat »</yellow>");
        base.getCanvas().setButton(15, CanvasButton.of(promptBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            plugin.getChatPromptManager().prompt(player,
                    TextUtil.parse("<gray>Enter effect: <yellow>&lt;type&gt; &lt;duration_sec&gt; &lt;level&gt; [chance 0-1]</yellow><br>(e.g. <yellow>SPEED 30 2 1.0</yellow> or <yellow>REGENERATION 15 1</yellow>):</gray>"),
                    input -> {
                        String[] parts = input.trim().split("\\s+");
                        if (parts.length >= 2) {
                            PotionEffectType type = FoodEffectSpec.parsePotionEffectType(parts[0]);
                            if (type != null) {
                                try {
                                    int dur = Integer.parseInt(parts[1]);
                                    int level = parts.length > 2 ? Integer.parseInt(parts[2]) : 1;
                                    float prob = parts.length > 3 ? Float.parseFloat(parts[3]) : 1.0f;
                                    food.addEffect(new FoodEffectSpec(type, dur * 20, Math.max(0, level - 1), prob));
                                    player.sendMessage(TextUtil.parse("<green>✓ Added effect " + type.getName() + " level " + level + " for " + dur + "s!</green>"));
                                } catch (Exception ignored) {}
                            }
                        }
                        openEffectsMenu(player, def, onBack);
                    });
        }));

        // Display existing effects (Row 3: Slots 20..24)
        int[] slots = new int[]{20, 21, 22, 23, 24};
        List<FoodEffectSpec> effects = food.getEffects();
        for (int i = 0; i < effects.size() && i < slots.length; i++) {
            int idx = i;
            FoodEffectSpec eff = effects.get(i);
            String effName = eff.getEffectType() != null ? eff.getEffectType().getName() : "EFFECT";
            ItemStack icon = createOptionBtn(Material.SPLASH_POTION,
                    "<light_purple><bold>" + effName + " " + (eff.getAmplifier() + 1) + "</bold></light_purple>",
                    "<gray>Duration:</gray> <white>" + (eff.getDurationTicks() / 20) + "s</white>",
                    "<gray>Amplifier:</gray> <white>" + eff.getAmplifier() + " (Level " + (eff.getAmplifier() + 1) + ")</white>",
                    "<gray>Chance:</gray> <white>" + (int) (eff.getProbability() * 100) + "%</white>",
                    "",
                    "<red>Click to remove effect</red>");
            base.getCanvas().setButton(slots[i], CanvasButton.of(icon, ev -> {
                food.getEffects().remove(idx);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
                openEffectsMenu(player, def, onBack);
            }));
        }

        base.setBackButton(onBack);
        base.open(player);
    }

    private void openPotionEffectTypeSelector(Player player, ItemDefinition def, Runnable onBack, int page) {
        List<PotionEffectType> types = Arrays.stream(PotionEffectType.values())
                .filter(java.util.Objects::nonNull)
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .toList();

        int pageSize = 28;
        int totalPages = Math.max(1, (int) Math.ceil((double) types.size() / pageSize));
        int curPage = Math.max(0, Math.min(totalPages - 1, page));

        BaseEditorCanvas base = new BaseEditorCanvas(plugin, def,
                TextUtil.parse("<gold>Select Potion Effect (" + (curPage + 1) + "/" + totalPages + ")</gold>"), 6);

        int start = curPage * pageSize;
        int end = Math.min(start + pageSize, types.size());

        int[] validSlots = new int[]{
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        int slotIdx = 0;
        for (int i = start; i < end; i++) {
            PotionEffectType type = types.get(i);
            ItemStack icon = createOptionBtn(Material.POTION,
                    "<light_purple>" + type.getName() + "</light_purple>",
                    "<gray>Click to configure duration, level, & chance »</gray>");

            base.getCanvas().setButton(validSlots[slotIdx++], CanvasButton.of(icon, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                openEffectConfigurator(player, def, type, () -> openPotionEffectTypeSelector(player, def, onBack, curPage));
            }));
        }

        base.setBackButton(onBack);
        base.setPaginationButtons(
                curPage > 0, () -> openPotionEffectTypeSelector(player, def, onBack, curPage - 1),
                curPage < totalPages - 1, () -> openPotionEffectTypeSelector(player, def, onBack, curPage + 1)
        );

        base.open(player);
    }

    private void openEffectConfigurator(Player player, ItemDefinition def, PotionEffectType type, Runnable onBack) {
        // Local state holders: duration 10s, level 1 (amp 0), chance 1.0
        int[] config = new int[]{10, 0}; // [durationSec, amplifier]
        float[] prob = new float[]{1.0f};

        openConfiguratorStep(player, def, type, config, prob, onBack);
    }

    private void openConfiguratorStep(Player player, ItemDefinition def, PotionEffectType type, int[] config, float[] prob, Runnable onBack) {
        BaseEditorCanvas base = new BaseEditorCanvas(plugin, def, TextUtil.parse("<gold>Config: " + type.getName() + "</gold>"), 4);
        base.setupSideBorders();

        int dur = config[0];
        int amp = config[1];
        float p = prob[0];

        // 1. Duration Button (Slot 10)
        ItemStack durBtn = createOptionBtn(Material.CLOCK,
                "<gold>Duration (Seconds)</gold>",
                "<gray>Current:</gray> <white>" + dur + "s</white>",
                "",
                "<yellow>Left-Click: +5s | Right-Click: -5s</yellow>",
                "<yellow>Shift-Left: +30s | Shift-Right: Type custom</yellow>");
        base.getCanvas().setButton(10, CanvasButton.of(durBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (e.isShiftClick() && e.isRightClick()) {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter effect duration in seconds (1 to 1000):</gray>"),
                        input -> {
                            try {
                                config[0] = Math.max(1, Integer.parseInt(input.trim()));
                            } catch (Exception ignored) {}
                            openConfiguratorStep(player, def, type, config, prob, onBack);
                        });
            } else if (e.isShiftClick()) {
                config[0] = Math.min(3600, dur + 30);
                openConfiguratorStep(player, def, type, config, prob, onBack);
            } else if (e.isRightClick()) {
                config[0] = Math.max(1, dur - 5);
                openConfiguratorStep(player, def, type, config, prob, onBack);
            } else {
                config[0] = Math.min(3600, dur + 5);
                openConfiguratorStep(player, def, type, config, prob, onBack);
            }
        }));

        // 2. Amplifier / Level Button (Slot 12)
        ItemStack ampBtn = createOptionBtn(Material.EXPERIENCE_BOTTLE,
                "<gold>Amplifier (Level)</gold>",
                "<gray>Current Level:</gray> <white>" + (amp + 1) + " (Amp: " + amp + ")</white>",
                "",
                "<yellow>Left-Click: +1 | Right-Click: -1</yellow>",
                "<yellow>Shift-Right: Type custom level</yellow>");
        base.getCanvas().setButton(12, CanvasButton.of(ampBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (e.isShiftClick() && e.isRightClick()) {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter effect level (1 to 10):</gray>"),
                        input -> {
                            try {
                                config[1] = Math.max(0, Integer.parseInt(input.trim()) - 1);
                            } catch (Exception ignored) {}
                            openConfiguratorStep(player, def, type, config, prob, onBack);
                        });
            } else if (e.isRightClick()) {
                config[1] = Math.max(0, amp - 1);
                openConfiguratorStep(player, def, type, config, prob, onBack);
            } else {
                config[1] = Math.min(255, amp + 1);
                openConfiguratorStep(player, def, type, config, prob, onBack);
            }
        }));

        // 3. Probability Button (Slot 14)
        ItemStack probBtn = createOptionBtn(Material.PRISMARINE_CRYSTALS,
                "<aqua>Activation Probability</aqua>",
                "<gray>Current:</gray> <white>" + (int) (p * 100) + "%</white>",
                "",
                "<yellow>Click to cycle (100%, 75%, 50%, 25%, 10%)</yellow>");
        base.getCanvas().setButton(14, CanvasButton.of(probBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (p >= 0.99f) prob[0] = 0.75f;
            else if (p >= 0.70f) prob[0] = 0.50f;
            else if (p >= 0.45f) prob[0] = 0.25f;
            else if (p >= 0.20f) prob[0] = 0.10f;
            else prob[0] = 1.0f;
            openConfiguratorStep(player, def, type, config, prob, onBack);
        }));

        // 4. Confirm & Add Button (Slot 16)
        ItemStack confirmBtn = createOptionBtn(Material.EMERALD,
                "<green><bold>✓ Add This Status Effect</bold></green>",
                "<gray>Type:</gray> <white>" + type.getName() + "</white>",
                "<gray>Duration:</gray> <white>" + config[0] + "s</white>",
                "<gray>Level:</gray> <white>" + (config[1] + 1) + "</white>",
                "<gray>Chance:</gray> <white>" + (int) (prob[0] * 100) + "%</white>",
                "",
                "<yellow>Click to attach to food »</yellow>");
        base.getCanvas().setButton(16, CanvasButton.of(confirmBtn, e -> {
            def.getFood().addEffect(new FoodEffectSpec(type, config[0] * 20, config[1], prob[0]));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f);
            player.sendMessage(TextUtil.parse("<green>✓ Added " + type.getName() + " " + (config[1] + 1) + " (" + config[0] + "s) to food!</green>"));
            openEffectsMenu(player, def, () -> open(player, def, onBack));
        }));

        base.setBackButton(onBack);
        base.open(player);
    }

    private ItemStack createToggleBtn(Material mat, String name, boolean enabled, String desc) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.parse(name));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>" + desc + "</gray>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<gray>Status:</gray> " + (enabled ? lang.get("general.enabled") : lang.get("general.disabled"))));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>Click to toggle »</yellow>"));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createOptionBtn(Material mat, String name, String... loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.parse(name));
            List<Component> lore = new ArrayList<>();
            for (String l : loreLines) {
                lore.add(TextUtil.parse(l));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
