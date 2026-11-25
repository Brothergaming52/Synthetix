package org.bg52.synthetix.gui.editor.category;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.ability.AbilitySpec;
import org.bg52.synthetix.ability.AuraSpec;
import org.bg52.synthetix.api.editor.ItemEditorCategory;
import org.bg52.synthetix.gui.CanvasButton;
import org.bg52.synthetix.gui.editor.BaseEditorCanvas;
import org.bg52.synthetix.item.ItemDefinition;
import org.bg52.synthetix.lang.LanguageManager;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Core editor category for Interactive Trigger Abilities and continuous Ambient Particle Auras.
 */
public class AbilitiesCategory implements ItemEditorCategory {

    private final SynthetixPlugin plugin;
    private final LanguageManager lang;

    public AbilitiesCategory(SynthetixPlugin plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    @Override
    public String getId() {
        return "abilities";
    }

    @Override
    public Component getDisplayName() {
        return TextUtil.parse("<gradient:#FF4500:#FFD700><bold>Abilities & Particle Auras</bold></gradient>");
    }

    @Override
    public int getPriority() {
        return 35;
    }

    @Override
    public boolean isPluginCategory() {
        return false;
    }

    @Override
    public ItemStack getIcon(ItemDefinition def) {
        ItemStack item = new ItemStack(Material.BLAZE_POWDER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(getDisplayName());
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Attach complex actions, click combos,</gray>"));
            lore.add(TextUtil.parse("<gray>and continuous particle auras.</gray>"));
            lore.add(Component.empty());

            int count = def.getAbilities() != null ? def.getAbilities().size() : 0;
            lore.add(TextUtil.parse("<dark_gray>•</dark_gray> <gray>Active Abilities:</gray> <white>" + count + "</white>"));

            AuraSpec aura = def.getAura();
            boolean auraOn = aura != null && aura.isEnabled();
            String auraText = auraOn ? "<green>" + aura.getShape().name() + " (" + aura.getParticle() + ")</green>" : "<red>Disabled</red>";
            lore.add(TextUtil.parse("<dark_gray>•</dark_gray> <gray>Particle Aura:</gray> " + auraText));

            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>Click to open settings »</yellow>"));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void open(Player player, ItemDefinition def, Runnable onBack) {
        BaseEditorCanvas base = new BaseEditorCanvas(plugin, def, getDisplayName(), 6);
        base.setupSideBorders();

        // 1. Particle Aura Master Control (Slot 11)
        AuraSpec aura = def.getAura();
        boolean auraEnabled = aura != null && aura.isEnabled();

        Material auraMat = auraEnabled ? Material.NETHER_STAR : Material.GRAY_DYE;
        String auraTitle = auraEnabled ? "<green><bold>[Particle Aura: ENABLED]</bold></green>" : "<red><bold>[Particle Aura: DISABLED]</bold></red>";

        ItemStack auraBtn = new ItemStack(auraMat);
        ItemMeta aMeta = auraBtn.getItemMeta();
        if (aMeta != null) {
            aMeta.displayName(TextUtil.parse(auraTitle));
            List<Component> lore = new ArrayList<>();
            if (auraEnabled) {
                lore.add(TextUtil.parse("<gray>Shape:</gray> <white>" + aura.getShape().name() + "</white>"));
                lore.add(TextUtil.parse("<gray>Particle:</gray> <white>" + aura.getParticle() + "</white>"));
                lore.add(TextUtil.parse("<gray>Condition:</gray> <white>" + aura.getCondition() + "</white>"));
                lore.add(Component.empty());
                lore.add(TextUtil.parse("<yellow>Left-Click: Configure Aura Settings »</yellow>"));
                lore.add(TextUtil.parse("<red>Right-Click: Turn OFF Aura</red>"));
            } else {
                lore.add(TextUtil.parse("<gray>Aura particles are currently disabled.</gray>"));
                lore.add(Component.empty());
                lore.add(TextUtil.parse("<green>Click to Turn ON Particle Aura »</green>"));
            }
            aMeta.lore(lore);
            auraBtn.setItemMeta(aMeta);
        }

        base.getCanvas().setButton(11, CanvasButton.of(auraBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            if (!auraEnabled) {
                if (def.getAura() == null) {
                    def.setAura(new AuraSpec());
                }
                def.getAura().setEnabled(true);
                open(player, def, onBack);
            } else {
                if (e.isRightClick()) {
                    def.getAura().setEnabled(false);
                    open(player, def, onBack);
                } else {
                    openAuraSettings(player, def, () -> open(player, def, onBack));
                }
            }
        }));

        // 2. Add New Ability Button (Slot 15)
        ItemStack addBtn = new ItemStack(Material.EMERALD);
        ItemMeta adMeta = addBtn.getItemMeta();
        if (adMeta != null) {
            adMeta.displayName(TextUtil.parse("<green><bold>+ Add Interactive Ability</bold></green>"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse("<gray>Configure triggers like on-right-click,</gray>"));
            lore.add(TextUtil.parse("<gray>on-hit-entity, on-shoot, on-equip, on-unequip etc.</gray>"));
            adMeta.lore(lore);
            addBtn.setItemMeta(adMeta);
        }
        base.getCanvas().setButton(15, CanvasButton.of(addBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            openTriggerSelector(player, def, () -> open(player, def, onBack));
        }));

        // 3. Display Existing Abilities (Rows 3 & 4: up to 14 abilities)
        int[] abilitySlots = new int[]{
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34
        };
        if (def.getAbilities() != null) {
            int slotIdx = 0;
            for (Map.Entry<String, AbilitySpec> entry : def.getAbilities().entrySet()) {
                if (slotIdx >= abilitySlots.length) break;

                String triggerId = entry.getKey();
                AbilitySpec spec = entry.getValue();

                ItemStack icon = new ItemStack(Material.ENCHANTED_BOOK);
                ItemMeta meta = icon.getItemMeta();
                if (meta != null) {
                    meta.displayName(TextUtil.parse("<gold><bold>" + triggerId + "</bold></gold>"));
                    List<Component> lore = new ArrayList<>();
                    lore.add(TextUtil.parse("<gray>Cooldown:</gray> <white>" + spec.getCooldown() + "s</white>"));
                    lore.add(TextUtil.parse("<gray>Chance:</gray> <white>" + (int) (spec.getChance() * 100) + "%</white>"));
                    lore.add(TextUtil.parse("<gray>Durability Cost:</gray> <white>" + spec.getCostDurability() + "</white>"));
                    lore.add(TextUtil.parse("<gray>Actions:</gray> <white>" + spec.getActions().size() + "</white>"));
                    lore.add(Component.empty());
                    lore.add(TextUtil.parse("<yellow>Left-Click: Configure Ability & Actions »</yellow>"));
                    lore.add(TextUtil.parse("<red>Right-Click: Remove Ability</red>"));
                    meta.lore(lore);
                    icon.setItemMeta(meta);
                }

                base.getCanvas().setButton(abilitySlots[slotIdx++], CanvasButton.of(icon, e -> {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
                    if (e.isRightClick()) {
                        def.getAbilities().remove(triggerId);
                        open(player, def, onBack);
                    } else {
                        openAbilityEditor(player, def, triggerId, spec, () -> open(player, def, onBack));
                    }
                }));
            }
        }

        base.setBackButton(onBack);
        base.setSaveButton();
        base.open(player);
    }

    private void openTriggerSelector(Player player, ItemDefinition def, Runnable onBack) {
        BaseEditorCanvas base = new BaseEditorCanvas(plugin, def, TextUtil.parse("<gold>Select Trigger Type</gold>"), 6);
        base.setupSideBorders();

        int[] slots = new int[]{
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        var triggerIds = plugin.getApi().getTriggerManager().getRegisteredTriggerIds().stream().toList();
        for (int i = 0; i < triggerIds.size() && i < slots.length; i++) {
            String trg = triggerIds.get(i);
            Material mat = getTriggerMaterial(trg);

            ItemStack icon = new ItemStack(mat);
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.displayName(TextUtil.parse("<yellow><bold>" + trg + "</bold></yellow>"));
                List<Component> lore = new ArrayList<>();
                lore.add(TextUtil.parse("<gray>Click to attach this interaction trigger.</gray>"));
                meta.lore(lore);
                icon.setItemMeta(meta);
            }

            base.getCanvas().setButton(slots[i], CanvasButton.of(icon, e -> {
                if (def.getAbilities() == null) def.setAbilities(new java.util.HashMap<>());
                AbilitySpec newSpec = new AbilitySpec(trg);
                def.getAbilities().put(trg, newSpec);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                openAbilityEditor(player, def, trg, newSpec, onBack);
            }));
        }

        base.setBackButton(onBack);
        base.open(player);
    }

    private void openAbilityEditor(Player player, ItemDefinition def, String triggerId, AbilitySpec spec, Runnable onBack) {
        BaseEditorCanvas base = new BaseEditorCanvas(plugin, def, TextUtil.parse("<gold>Edit: " + triggerId + "</gold>"), 5);
        base.setupSideBorders();

        // 1. Cooldown Button (Slot 10) - Interactive Quick Clicks + Custom Input
        ItemStack cdBtn = createOptionBtn(Material.CLOCK,
                "<gold>Cooldown</gold>",
                "<gray>Current:</gray> <white>" + spec.getCooldown() + "s</white>",
                "",
                "<yellow>Left-Click: +1s | Right-Click: -1s</yellow>",
                "<yellow>Shift-Left: +5s</yellow>",
                "<yellow>Shift-Right: Type custom seconds in chat</yellow>");
        base.getCanvas().setButton(10, CanvasButton.of(cdBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (e.isShiftClick() && e.isRightClick()) {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter custom cooldown in seconds (e.g. <yellow>2.5</yellow>):</gray>"),
                        input -> {
                            try {
                                double cd = Double.parseDouble(input.trim());
                                spec.setCooldown(Math.max(0.0, cd));
                            } catch (Exception ignored) {}
                            openAbilityEditor(player, def, triggerId, spec, onBack);
                        });
            } else if (e.isShiftClick()) {
                spec.setCooldown(spec.getCooldown() + 5.0);
                openAbilityEditor(player, def, triggerId, spec, onBack);
            } else if (e.isRightClick()) {
                spec.setCooldown(Math.max(0.0, spec.getCooldown() - 1.0));
                openAbilityEditor(player, def, triggerId, spec, onBack);
            } else if (e.isLeftClick()) {
                spec.setCooldown(spec.getCooldown() + 1.0);
                openAbilityEditor(player, def, triggerId, spec, onBack);
            }
        }));

        // 2. Chance / Probability Button (Slot 12) - Interactive Cycle + Custom Input
        ItemStack chanceBtn = createOptionBtn(Material.PRISMARINE_CRYSTALS,
                "<aqua>Activation Chance</aqua>",
                "<gray>Current:</gray> <white>" + (int) (spec.getChance() * 100) + "%</white>",
                "",
                "<yellow>Left-Click: Cycle preset chance</yellow>",
                "<gray>[100%] -> [75%] -> [50%] -> [25%] -> [10%]</gray>",
                "<yellow>Right-Click / Shift-Click: Type custom % in chat</yellow>");
        base.getCanvas().setButton(12, CanvasButton.of(chanceBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (e.isRightClick() || e.isShiftClick()) {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter activation chance (e.g. <yellow>0.33</yellow> or <yellow>33%</yellow>):</gray>"),
                        input -> {
                            try {
                                String clean = input.trim().replace("%", "");
                                double ch = Double.parseDouble(clean);
                                if (ch > 1.0) ch /= 100.0;
                                spec.setChance(Math.max(0.01, Math.min(1.0, ch)));
                            } catch (Exception ignored) {}
                            openAbilityEditor(player, def, triggerId, spec, onBack);
                        });
            } else {
                double cur = spec.getChance();
                if (cur >= 0.99) spec.setChance(0.75);
                else if (cur >= 0.70) spec.setChance(0.50);
                else if (cur >= 0.45) spec.setChance(0.25);
                else if (cur >= 0.20) spec.setChance(0.10);
                else spec.setChance(1.0);
                openAbilityEditor(player, def, triggerId, spec, onBack);
            }
        }));

        // 3. Durability Cost Button (Slot 14) - Quick Clicks + Custom Input
        ItemStack duraBtn = createOptionBtn(Material.ANVIL,
                "<gold>Durability Cost</gold>",
                "<gray>Current:</gray> <white>" + spec.getCostDurability() + "</white>",
                "",
                "<yellow>Left-Click: +1 | Right-Click: -1</yellow>",
                "<yellow>Shift-Left: +5</yellow>",
                "<yellow>Shift-Right: Type custom durability cost</yellow>");
        base.getCanvas().setButton(14, CanvasButton.of(duraBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (e.isShiftClick() && e.isRightClick()) {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter custom durability cost (e.g. <yellow>10</yellow>):</gray>"),
                        input -> {
                            try {
                                int cost = Integer.parseInt(input.trim());
                                spec.setCostDurability(Math.max(0, cost));
                            } catch (Exception ignored) {}
                            openAbilityEditor(player, def, triggerId, spec, onBack);
                        });
            } else if (e.isShiftClick()) {
                spec.setCostDurability(spec.getCostDurability() + 5);
                openAbilityEditor(player, def, triggerId, spec, onBack);
            } else if (e.isRightClick()) {
                spec.setCostDurability(Math.max(0, spec.getCostDurability() - 1));
                openAbilityEditor(player, def, triggerId, spec, onBack);
            } else {
                spec.setCostDurability(spec.getCostDurability() + 1);
                openAbilityEditor(player, def, triggerId, spec, onBack);
            }
        }));

        // 4. Interactive Action Builder GUI (Slot 16)
        ItemStack actBtn = createOptionBtn(Material.COMMAND_BLOCK,
                "<light_purple><bold>Ability Actions (" + spec.getActions().size() + ")</bold></light_purple>",
                "<gray>Visually add and manage sounds, particles,</gray>",
                "<gray>effects, projectiles, lightning, velocity & commands.</gray>",
                "",
                "<yellow>Click to open Action Builder GUI »</yellow>");
        base.getCanvas().setButton(16, CanvasButton.of(actBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            new ActionBuilderGUI(plugin, def, triggerId, spec, () -> openAbilityEditor(player, def, triggerId, spec, onBack)).open(player);
        }));

        base.setBackButton(onBack);
        base.open(player);
    }

    private void openAuraSettings(Player player, ItemDefinition def, Runnable onBack) {
        AuraSpec aura = def.getAura();
        if (aura == null) {
            aura = new AuraSpec();
            aura.setEnabled(true);
            def.setAura(aura);
        }

        BaseEditorCanvas base = new BaseEditorCanvas(plugin, def, TextUtil.parse("<gold>Particle Aura Settings</gold>"), 5);
        base.setupSideBorders();

        // 1. Shape Cycle Button (Slot 10)
        AuraSpec.Shape[] shapes = AuraSpec.Shape.values();
        ItemStack shapeBtn = createOptionBtn(Material.FIREWORK_STAR,
                "<gold>Aura Shape</gold>",
                "<gray>Current:</gray> <white>" + aura.getShape().name() + "</white>",
                "",
                "<yellow>Click to cycle shape (SPIRAL, HALO, WINGS, ORBITING_SPHERES, FOOTSTEPS)</yellow>");
        AuraSpec finalAura = aura;
        base.getCanvas().setButton(10, CanvasButton.of(shapeBtn, e -> {
            int nextIdx = (finalAura.getShape().ordinal() + 1) % shapes.length;
            finalAura.setShape(shapes[nextIdx]);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            openAuraSettings(player, def, onBack);
        }));

        // 2. Quick Particle Presets (Slot 12)
        String[] particlePresets = {"FLAME", "SOUL_FIRE_FLAME", "END_ROD", "CHERRY_LEAVES", "PORTAL", "DRAGON_BREATH", "ENCHANTMENT_TABLE", "DUST"};
        ItemStack partBtn = createOptionBtn(Material.BLAZE_POWDER,
                "<gold>Particle Type</gold>",
                "<gray>Current:</gray> <white>" + aura.getParticle() + "</white>",
                "",
                "<yellow>Left-Click: Cycle popular particles</yellow>",
                "<gray>Right-Click: Type custom particle enum</gray>");
        base.getCanvas().setButton(12, CanvasButton.of(partBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            if (e.isRightClick()) {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter particle enum (e.g. <yellow>FLAME</yellow>, <yellow>TOTEM</yellow>, <yellow>DUST</yellow>):</gray>"),
                        input -> {
                            if (!input.trim().isEmpty()) finalAura.setParticle(input.trim());
                            openAuraSettings(player, def, onBack);
                        });
            } else {
                int curIdx = 0;
                for (int i = 0; i < particlePresets.length; i++) {
                    if (particlePresets[i].equalsIgnoreCase(finalAura.getParticle())) {
                        curIdx = i;
                        break;
                    }
                }
                finalAura.setParticle(particlePresets[(curIdx + 1) % particlePresets.length]);
                openAuraSettings(player, def, onBack);
            }
        }));

        // 3. Condition Cycle Button (Slot 14)
        String[] conditions = new String[]{
                "while-held-or-worn",
                "while-worn-armor",
                "while-holding-mainhand",
                "while-holding-offhand",
                "while-held",
                "only-while-sneaking",
                "always"
        };
        ItemStack condBtn = createOptionBtn(Material.COMPASS,
                "<gold>Activation Condition</gold>",
                "<gray>Current:</gray> <white>" + aura.getCondition() + "</white>",
                "",
                "<yellow>Left-Click: Cycle preset conditions</yellow>",
                "<yellow>Right-Click: Type custom condition in chat</yellow>");
        base.getCanvas().setButton(14, CanvasButton.of(condBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (e.isRightClick()) {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter condition (<yellow>while-held-or-worn</yellow>, <yellow>while-worn-armor</yellow>, <yellow>while-holding-mainhand</yellow>, <yellow>always</yellow>):</gray>"),
                        input -> {
                            if (!input.trim().isEmpty()) finalAura.setCondition(input.trim().toLowerCase(java.util.Locale.ROOT));
                            openAuraSettings(player, def, onBack);
                        });
            } else {
                int curIdx = 0;
                for (int i = 0; i < conditions.length; i++) {
                    if (conditions[i].equalsIgnoreCase(finalAura.getCondition())) {
                        curIdx = i;
                        break;
                    }
                }
                finalAura.setCondition(conditions[(curIdx + 1) % conditions.length]);
                openAuraSettings(player, def, onBack);
            }
        }));

        // 4. Radius & Particle Count (Slot 16)
        ItemStack radBtn = createOptionBtn(Material.SLIME_BALL,
                "<gold>Radius & Density</gold>",
                "<gray>Radius:</gray> <white>" + aura.getRadius() + "</white>",
                "<gray>Count:</gray> <white>" + aura.getCount() + "</white>",
                "",
                "<yellow>Left-Click: Set radius</yellow>",
                "<red>Right-Click: Set particle count</red>");
        base.getCanvas().setButton(16, CanvasButton.of(radBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            if (e.isRightClick()) {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter particle count (1 to 10):</gray>"),
                        input -> {
                            try {
                                finalAura.setCount(Integer.parseInt(input.trim()));
                            } catch (Exception ignored) {}
                            openAuraSettings(player, def, onBack);
                        });
            } else {
                plugin.getChatPromptManager().prompt(player,
                        TextUtil.parse("<gray>Enter radius (0.2 to 2.5):</gray>"),
                        input -> {
                            try {
                                finalAura.setRadius(Double.parseDouble(input.trim()));
                            } catch (Exception ignored) {}
                            openAuraSettings(player, def, onBack);
                        });
            }
        }));

        base.setBackButton(onBack);
        base.open(player);
    }

    private Material getTriggerMaterial(String trigger) {
        return switch (trigger.toLowerCase()) {
            case "on-right-click", "on-left-click", "on-shift-right-click", "on-shift-left-click" -> Material.STICK;
            case "on-hit-entity" -> Material.DIAMOND_SWORD;
            case "on-take-damage" -> Material.SHIELD;
            case "on-kill-entity" -> Material.SKELETON_SKULL;
            case "on-break-block" -> Material.DIAMOND_PICKAXE;
            case "on-consume" -> Material.GOLDEN_APPLE;
            case "on-shoot" -> Material.BOW;
            case "on-sneak-toggle" -> Material.LEATHER_BOOTS;
            case "on-drop" -> Material.HOPPER;
            case "on-swap-hand" -> Material.TRIPWIRE_HOOK;
            case "on-equip", "on-unequip" -> Material.ARMOR_STAND;
            case "on-jump" -> Material.RABBIT_FOOT;
            case "on-fish" -> Material.FISHING_ROD;
            case "on-respawn" -> Material.TOTEM_OF_UNDYING;
            default -> Material.PAPER;
        };
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
