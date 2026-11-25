package org.bg52.synthetix.gui.editor.entity;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.entity.ai.EntityAISpec;
import org.bg52.synthetix.entity.ai.EntityAIType;
import org.bg52.synthetix.entity.behavior.EntityBehaviorChart;
import org.bg52.synthetix.entity.model.CustomEntityDefinition;
import org.bg52.synthetix.entity.model.EntityBoneSpec;
import org.bg52.synthetix.entity.model.EntityLootSpec;
import org.bg52.synthetix.entity.model.EntitySoundSpec;
import org.bg52.synthetix.gui.Canvas;
import org.bg52.synthetix.gui.CanvasButton;
import org.bg52.synthetix.lang.LanguageManager;
import org.bg52.synthetix.util.MaterialUtil;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Streamlined, intuitive in-game creator and editor for custom entities.
 * Strictly localized: zero hardcoded messages or strings, all routing through LanguageManager.
 */
public class EntityEditorMenu {

    private static final List<EntityType> COMMON_BASE_TYPES = List.of(
            EntityType.ZOMBIE,
            EntityType.SKELETON,
            EntityType.HUSK,
            EntityType.DROWNED,
            EntityType.STRAY,
            EntityType.WITHER_SKELETON,
            EntityType.PIGLIN,
            EntityType.ZOMBIFIED_PIGLIN,
            EntityType.CREEPER,
            EntityType.SPIDER,
            EntityType.CAVE_SPIDER,
            EntityType.IRON_GOLEM,
            EntityType.RAVAGER,
            EntityType.VILLAGER,
            EntityType.COW,
            EntityType.PIG,
            EntityType.SHEEP,
            EntityType.HORSE,
            EntityType.WOLF,
            EntityType.CAT,
            EntityType.BLAZE,
            EntityType.SLIME
    );

    private static final List<Material> COMMON_VISUAL_MATERIALS = List.of(
            Material.LEATHER_HORSE_ARMOR,
            Material.STICK,
            Material.BONE,
            Material.DIAMOND_SWORD,
            Material.IRON_SWORD,
            Material.BOW,
            Material.CROSSBOW,
            Material.SHIELD,
            Material.CARVED_PUMPKIN,
            Material.PLAYER_HEAD,
            Material.FEATHER,
            Material.AMETHYST_SHARD,
            Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE
    );

    private final SynthetixPlugin plugin;
    private final LanguageManager lang;
    private final CustomEntityDefinition def;

    public EntityEditorMenu(SynthetixPlugin plugin, CustomEntityDefinition def) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
        this.def = def;
    }

    public static void open(Player player, SynthetixPlugin plugin, CustomEntityDefinition def) {
        new EntityEditorMenu(plugin, def).open(player);
    }

    public void open(Player player) {
        Component title = lang.getComponent("gui.entity-editor.title", "%entity%", def.getDisplayName());
        Canvas canvas = new Canvas(title, 6);

        ItemStack darkGlass = Canvas.createBorderItem(Material.BLACK_STAINED_GLASS_PANE, Component.empty());
        ItemStack grayGlass = Canvas.createBorderItem(Material.GRAY_STAINED_GLASS_PANE, Component.empty());

        for (int c = 0; c < 9; c++) {
            if (c != 4) canvas.setButton(0, c, CanvasButton.of(c == 0 || c == 8 ? darkGlass : grayGlass));
            canvas.setButton(5, c, CanvasButton.of(darkGlass));
        }
        for (int r = 1; r < 5; r++) {
            canvas.setButton(r, 0, CanvasButton.of(darkGlass));
            canvas.setButton(r, 8, CanvasButton.of(darkGlass));
        }

        EntityBoneSpec rootBone = def.getBone(def.getRootBoneName());
        if (rootBone == null) {
            rootBone = new EntityBoneSpec(def.getRootBoneName(), null, null, Material.LEATHER_HORSE_ARMOR, 0);
            def.addBone(rootBone);
        }

        // =====================================================================
        // ROW 0: Overview Badge, Visual Model Display, and Quick Test Spawn
        // =====================================================================

        // Slot 0: Entity Overview Badge
        ItemStack badge = createBtn(Material.KNOWLEDGE_BOOK,
                lang.get("gui.entity-editor.overview-title"),
                lang.get("gui.entity-editor.overview-id", "%id%", def.getId()),
                lang.get("gui.entity-editor.overview-display", "%display%", def.getDisplayName()),
                lang.get("gui.entity-editor.overview-base", "%base%", def.getBaseEntityType().name()),
                lang.get("gui.entity-editor.overview-health", "%health%", String.format(Locale.ROOT, "%.1f", def.getMaxHealth())),
                lang.get("gui.entity-editor.overview-damage", "%damage%", String.format(Locale.ROOT, "%.1f", def.getAttackDamage())),
                lang.get("gui.entity-editor.overview-scale", "%scale%", String.format(Locale.ROOT, "%.2f", def.getScale())),
                lang.get("gui.entity-editor.overview-speed", "%speed%", String.format(Locale.ROOT, "%.2f", def.getMovementSpeed())),
                lang.get("gui.entity-editor.overview-archetype", "%archetype%", def.getAi().getAiType().name()),
                lang.get("gui.entity-editor.overview-bones", "%bones%", String.valueOf(def.getBones().size())),
                lang.get("gui.entity-editor.overview-anims", "%anims%", String.valueOf(def.getAnimations().size())));
        canvas.setButton(0, CanvasButton.of(badge));

        // Slot 4: Visual Model Spec (Root Bone Visual Preview)
        Material rootMat = rootBone.getMaterial() != null ? rootBone.getMaterial() : Material.LEATHER_HORSE_ARMOR;
        ItemStack visualBtn = createBtn(rootMat,
                lang.get("gui.entity-editor.visual-title"),
                lang.get("gui.entity-editor.visual-mat", "%mat%", rootMat.name()),
                lang.get("gui.entity-editor.visual-cmd", "%cmd%", rootBone.getCustomModelData() > 0 ? String.valueOf(rootBone.getCustomModelData()) : lang.get("general.not-selected")),
                lang.get("gui.entity-editor.visual-item-model", "%model%", rootBone.getItemModel() != null ? rootBone.getItemModel() : lang.get("general.not-selected")),
                "",
                lang.get("gui.entity-editor.visual-ctrl-mat"),
                lang.get("gui.entity-editor.visual-ctrl-mat-exact"),
                lang.get("gui.entity-editor.visual-ctrl-cmd"),
                lang.get("gui.entity-editor.visual-ctrl-model"));
        EntityBoneSpec finalRootBone = rootBone;
        canvas.setButton(4, CanvasButton.of(visualBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (e.isShiftClick() && e.isLeftClick()) {
                plugin.getChatPromptManager().prompt(player,
                        lang.getComponent("prompts.enter-entity-cmd"),
                        input -> {
                            try {
                                finalRootBone.setCustomModelData(Math.max(0, Integer.parseInt(input.trim())));
                                lang.send(player, "item-editor.identity.cmd-updated");
                            } catch (Exception ex) {
                                lang.send(player, "general.input-error", "%error%", ex.getMessage());
                            }
                            open(player);
                        });
            } else if (e.isShiftClick() && e.isRightClick()) {
                plugin.getChatPromptManager().prompt(player,
                        lang.getComponent("prompts.enter-entity-item-model"),
                        input -> {
                            String trimmed = input.trim();
                            if ("none".equalsIgnoreCase(trimmed) || "clear".equalsIgnoreCase(trimmed) || trimmed.isEmpty()) {
                                finalRootBone.setItemModel(null);
                            } else {
                                finalRootBone.setItemModel(trimmed);
                                lang.send(player, "item-editor.identity.model-updated");
                            }
                            open(player);
                        });
            } else if (e.isRightClick()) {
                plugin.getChatPromptManager().prompt(player,
                        lang.getComponent("prompts.enter-entity-mat"),
                        input -> {
                            try {
                                Material mat = Material.matchMaterial(input.trim());
                                if (mat != null) {
                                    finalRootBone.setMaterial(mat);
                                    lang.send(player, "item-editor.identity.mat-updated", "%mat%", mat.name());
                                } else {
                                    lang.send(player, "general.input-error", "%error%", input);
                                }
                            } catch (Exception ex) {
                                lang.send(player, "general.input-error", "%error%", ex.getMessage());
                            }
                            open(player);
                        });
            } else {
                int currentIndex = COMMON_VISUAL_MATERIALS.indexOf(finalRootBone.getMaterial());
                int nextIndex = (currentIndex + 1) % COMMON_VISUAL_MATERIALS.size();
                finalRootBone.setMaterial(COMMON_VISUAL_MATERIALS.get(nextIndex));
                open(player);
            }
        }));

        // Slot 8: Quick Test Spawn
        ItemStack testSpawnBtn = createBtn(Material.ALLAY_SPAWN_EGG,
                lang.get("gui.entity-editor.test-spawn-title"),
                lang.get("gui.entity-editor.test-spawn-lore-1"),
                lang.get("gui.entity-editor.test-spawn-lore-2"),
                "",
                lang.get("gui.entity-editor.test-spawn-ctrl-1"),
                lang.get("gui.entity-editor.test-spawn-ctrl-5"));
        canvas.setButton(8, CanvasButton.of(testSpawnBtn, e -> {
            int amount = e.isShiftClick() ? 5 : 1;
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.2f);
            for (int k = 0; k < amount; k++) {
                plugin.getCustomEntityManager().spawnEntity(def, player.getLocation());
            }
            lang.send(player, "gui.entity-editor.test-spawned", "%amount%", String.valueOf(amount), "%entity%", def.getId());
        }));

        // =====================================================================
        // ROW 2: Core Base Mob & 1.20.6 Native Attributes (Slots 20, 21, 22, 23, 24)
        // =====================================================================

        // Slot 20: Base Mob Proxy Type
        Material proxyHead = getMobHeadOrEgg(def.getBaseEntityType());
        ItemStack baseTypeBtn = createBtn(proxyHead,
                lang.get("gui.entity-editor.base-type-title"),
                lang.get("gui.entity-editor.base-type-current", "%type%", def.getBaseEntityType().name()),
                lang.get("gui.entity-editor.base-type-lore-1"),
                lang.get("gui.entity-editor.base-type-lore-2"),
                "",
                lang.get("gui.entity-editor.base-type-ctrl-next"),
                lang.get("gui.entity-editor.base-type-ctrl-prev"),
                lang.get("gui.entity-editor.base-type-ctrl-chat"));
        canvas.setButton(20, CanvasButton.of(baseTypeBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (e.isShiftClick()) {
                plugin.getChatPromptManager().prompt(player,
                        lang.getComponent("prompts.enter-entity-base"),
                        input -> {
                            try {
                                EntityType type = EntityType.valueOf(input.trim().toUpperCase(Locale.ROOT));
                                if (type.isAlive()) {
                                    def.setBaseEntityType(type);
                                }
                            } catch (Exception ignored) {}
                            open(player);
                        });
            } else {
                int idx = COMMON_BASE_TYPES.indexOf(def.getBaseEntityType());
                if (idx < 0) idx = 0;
                int nextIdx = e.isRightClick()
                        ? (idx - 1 + COMMON_BASE_TYPES.size()) % COMMON_BASE_TYPES.size()
                        : (idx + 1) % COMMON_BASE_TYPES.size();
                def.setBaseEntityType(COMMON_BASE_TYPES.get(nextIdx));
                open(player);
            }
        }));

        // Slot 21: Display Name
        ItemStack nameBtn = createBtn(Material.NAME_TAG,
                lang.get("gui.entity-editor.display-name-title"),
                lang.get("gui.entity-editor.display-name-formatted", "%display%", def.getDisplayName()),
                lang.get("gui.entity-editor.display-name-raw", "%raw%", def.getDisplayName()),
                "",
                lang.get("gui.entity-editor.display-name-ctrl-edit"),
                lang.get("gui.entity-editor.display-name-ctrl-reset"));
        canvas.setButton(21, CanvasButton.of(nameBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (e.isRightClick()) {
                def.setDisplayName("<gold>" + TextUtil.prettify(def.getId()) + "</gold>");
                open(player);
            } else {
                plugin.getChatPromptManager().prompt(player,
                        lang.getComponent("prompts.enter-entity-name", "%entity%", def.getId()),
                        input -> {
                            if (!input.trim().isEmpty()) {
                                def.setDisplayName(input.trim());
                                lang.send(player, "item-editor.identity.name-updated");
                            }
                            open(player);
                        });
            }
        }));

        // Slot 22: Scale Attribute (1.20.6 GENERIC_SCALE)
        ItemStack scaleBtn = createBtn(Material.SLIME_BALL,
                lang.get("gui.entity-editor.scale-title"),
                lang.get("gui.entity-editor.scale-current", "%scale%", String.format(Locale.ROOT, "%.2f", def.getScale())),
                lang.get("gui.entity-editor.scale-lore-1"),
                lang.get("gui.entity-editor.scale-lore-2"),
                "",
                lang.get("gui.entity-editor.scale-ctrl-add"),
                lang.get("gui.entity-editor.scale-ctrl-sub"),
                lang.get("gui.entity-editor.scale-ctrl-add1"),
                lang.get("gui.entity-editor.scale-ctrl-exact"));
        canvas.setButton(22, CanvasButton.of(scaleBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (e.isShiftClick() && e.isRightClick()) {
                plugin.getChatPromptManager().prompt(player,
                        lang.getComponent("prompts.enter-entity-scale"),
                        input -> {
                            try {
                                def.setScale(Double.parseDouble(input.trim()));
                            } catch (Exception ignored) {}
                            open(player);
                        });
            } else if (e.isShiftClick()) {
                def.setScale(def.getScale() + 1.0);
                open(player);
            } else if (e.isRightClick()) {
                def.setScale(Math.max(0.1, def.getScale() - 0.25));
                open(player);
            } else {
                def.setScale(def.getScale() + 0.25);
                open(player);
            }
        }));

        // Slot 23: Max Health & Attack Damage
        ItemStack combatBtn = createBtn(Material.GLISTERING_MELON_SLICE,
                lang.get("gui.entity-editor.combat-title"),
                lang.get("gui.entity-editor.combat-health", "%health%", String.format(Locale.ROOT, "%.1f", def.getMaxHealth())),
                lang.get("gui.entity-editor.combat-damage", "%damage%", String.format(Locale.ROOT, "%.1f", def.getAttackDamage())),
                "",
                lang.get("gui.entity-editor.combat-ctrl-hp-add"),
                lang.get("gui.entity-editor.combat-ctrl-hp-sub"),
                lang.get("gui.entity-editor.combat-ctrl-dmg-add"),
                lang.get("gui.entity-editor.combat-ctrl-dmg-sub"));
        canvas.setButton(23, CanvasButton.of(combatBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (e.isShiftClick() && e.isRightClick()) {
                def.setAttackDamage(Math.max(0.0, def.getAttackDamage() - 2.0));
            } else if (e.isShiftClick()) {
                def.setAttackDamage(def.getAttackDamage() + 2.0);
            } else if (e.isRightClick()) {
                def.setMaxHealth(Math.max(1.0, def.getMaxHealth() - 10.0));
            } else {
                def.setMaxHealth(def.getMaxHealth() + 10.0);
            }
            open(player);
        }));

        // Slot 24: Movement Speed & Step Height
        ItemStack locoBtn = createBtn(Material.SUGAR,
                lang.get("gui.entity-editor.loco-title"),
                lang.get("gui.entity-editor.loco-speed", "%speed%", String.format(Locale.ROOT, "%.2f", def.getMovementSpeed())),
                lang.get("gui.entity-editor.loco-step", "%step%", String.format(Locale.ROOT, "%.1f", def.getStepHeight())),
                lang.get("gui.entity-editor.loco-gravity", "%gravity%", String.format(Locale.ROOT, "%.2f", def.getGravity())),
                "",
                lang.get("gui.entity-editor.loco-ctrl-speed-add"),
                lang.get("gui.entity-editor.loco-ctrl-speed-sub"),
                lang.get("gui.entity-editor.loco-ctrl-step-add"),
                lang.get("gui.entity-editor.loco-ctrl-step-sub"));
        canvas.setButton(24, CanvasButton.of(locoBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (e.isShiftClick() && e.isRightClick()) {
                def.setStepHeight(Math.max(0.0, def.getStepHeight() - 0.5));
            } else if (e.isShiftClick()) {
                def.setStepHeight(def.getStepHeight() + 0.5);
            } else if (e.isRightClick()) {
                def.setMovementSpeed(Math.max(0.0, def.getMovementSpeed() - 0.05));
            } else {
                def.setMovementSpeed(def.getMovementSpeed() + 0.05);
            }
            open(player);
        }));

        // =====================================================================
        // ROW 3: AI Archetype, Behavior Chart, Audio, and Loot (Slots 29, 30, 31, 32, 33)
        // =====================================================================

        EntityAISpec ai = def.getAi();

        // Slot 29: AI Archetype
        ItemStack aiTypeBtn = createBtn(Material.COMPASS,
                lang.get("gui.entity-editor.ai-archetype-title"),
                lang.get("gui.entity-editor.ai-archetype-current", "%archetype%", ai.getAiType().name()),
                lang.get("gui.entity-editor.ai-archetype-lore"),
                "",
                lang.get("gui.entity-editor.ai-archetype-ctrl-next"),
                lang.get("gui.entity-editor.ai-archetype-ctrl-prev"));
        canvas.setButton(29, CanvasButton.of(aiTypeBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            EntityAIType[] types = EntityAIType.values();
            int cur = ai.getAiType().ordinal();
            int next = e.isRightClick()
                    ? (cur - 1 + types.length) % types.length
                    : (cur + 1) % types.length;
            ai.setAiType(types[next]);
            open(player);
        }));

        // Slot 30: Behavior Chart & Actions Matrix (1-Click Animation & Action Mapper)
        EntityBehaviorChart chart = def.getBehaviorChart();
        ItemStack behaviorChartBtn = createBtn(Material.NETHER_STAR,
                lang.get("gui.entity-editor.behavior-btn-title"),
                lang.get("gui.entity-editor.behavior-btn-nodes", "%count%", String.valueOf(chart.getNodes().size())),
                lang.get("gui.entity-editor.behavior-btn-lore-1"),
                lang.get("gui.entity-editor.behavior-btn-lore-2"),
                "",
                lang.get("gui.entity-editor.behavior-btn-ctrl"));
        canvas.setButton(30, CanvasButton.of(behaviorChartBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            EntityBehaviorChartGUI.open(player, plugin, def);
        }));

        // Slot 31: Follow Range & Attack Cooldown
        ItemStack combatRangeBtn = createBtn(Material.BOW,
                lang.get("gui.entity-editor.follow-range-title"),
                lang.get("gui.entity-editor.follow-range-current", "%range%", String.format(Locale.ROOT, "%.0f", ai.getFollowRange())),
                lang.get("gui.entity-editor.follow-range-reach", "%reach%", String.format(Locale.ROOT, "%.1f", ai.getMeleeRange())),
                lang.get("gui.entity-editor.follow-range-cooldown", "%ticks%", String.valueOf(ai.getAttackCooldownTicks())),
                "",
                lang.get("gui.entity-editor.follow-range-ctrl-range-add"),
                lang.get("gui.entity-editor.follow-range-ctrl-range-sub"),
                lang.get("gui.entity-editor.follow-range-ctrl-cd-add"),
                lang.get("gui.entity-editor.follow-range-ctrl-cd-sub"));
        canvas.setButton(31, CanvasButton.of(combatRangeBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (e.isShiftClick() && e.isRightClick()) {
                ai.setAttackCooldownTicks(Math.max(1, ai.getAttackCooldownTicks() - 5));
            } else if (e.isShiftClick()) {
                ai.setAttackCooldownTicks(ai.getAttackCooldownTicks() + 5);
            } else if (e.isRightClick()) {
                ai.setFollowRange(Math.max(8.0, ai.getFollowRange() - 8.0));
                def.setFollowRange(ai.getFollowRange());
            } else {
                ai.setFollowRange(ai.getFollowRange() + 8.0);
                def.setFollowRange(ai.getFollowRange());
            }
            open(player);
        }));

        // Slot 32: Audio Profile
        EntitySoundSpec sounds = def.getSounds();
        ItemStack soundBtn = createBtn(Material.NOTE_BLOCK,
                lang.get("gui.entity-editor.audio-title"),
                lang.get("gui.entity-editor.audio-hurt", "%sound%", sounds.getHurtSound()),
                lang.get("gui.entity-editor.audio-death", "%sound%", sounds.getDeathSound()),
                lang.get("gui.entity-editor.audio-ambient", "%sound%", sounds.getAmbientSound() != null ? sounds.getAmbientSound() : lang.get("general.not-selected")),
                "",
                lang.get("gui.entity-editor.audio-ctrl-preset"),
                lang.get("gui.entity-editor.audio-ctrl-hurt"),
                lang.get("gui.entity-editor.audio-ctrl-death"));
        canvas.setButton(32, CanvasButton.of(soundBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (e.isShiftClick() && e.isLeftClick()) {
                plugin.getChatPromptManager().prompt(player,
                        lang.getComponent("prompts.enter-entity-hurt-sound"),
                        input -> {
                            sounds.setHurtSound(input.trim());
                            open(player);
                        });
            } else if (e.isShiftClick() && e.isRightClick()) {
                plugin.getChatPromptManager().prompt(player,
                        lang.getComponent("prompts.enter-entity-death-sound"),
                        input -> {
                            sounds.setDeathSound(input.trim());
                            open(player);
                        });
            } else {
                cycleSoundPresets(sounds);
                open(player);
            }
        }));

        // Slot 33: Experience & Drops
        EntityLootSpec loot = def.getLoot();
        ItemStack lootBtn = createBtn(Material.EXPERIENCE_BOTTLE,
                lang.get("gui.entity-editor.drops-title"),
                lang.get("gui.entity-editor.drops-xp", "%xp%", String.valueOf(loot.getExpDrop())),
                lang.get("gui.entity-editor.drops-count", "%count%", String.valueOf(loot.getDrops().size())),
                "",
                lang.get("gui.entity-editor.drops-ctrl-xp-add"),
                lang.get("gui.entity-editor.drops-ctrl-xp-sub"),
                lang.get("gui.entity-editor.drops-ctrl-add"),
                lang.get("gui.entity-editor.drops-ctrl-clear"));
        canvas.setButton(33, CanvasButton.of(lootBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            if (e.isShiftClick() && e.isRightClick()) {
                loot.getDrops().clear();
                open(player);
            } else if (e.isShiftClick() && e.isLeftClick()) {
                plugin.getChatPromptManager().prompt(player,
                        lang.getComponent("prompts.enter-entity-drop"),
                        input -> {
                            String[] parts = input.trim().split("\\s+");
                            if (parts.length >= 1) {
                                String itemId = parts[0].toUpperCase(Locale.ROOT);
                                int amt = parts.length >= 2 ? Math.max(1, Integer.parseInt(parts[1])) : 1;
                                double chance = parts.length >= 3 ? Double.parseDouble(parts[2]) : 1.0;
                                loot.addDrop(new EntityLootSpec.DropEntry(itemId, 1, amt, chance));
                            }
                            open(player);
                        });
            } else if (e.isRightClick()) {
                loot.setExpDrop(Math.max(0, loot.getExpDrop() - 5));
                open(player);
            } else {
                loot.setExpDrop(loot.getExpDrop() + 5);
                open(player);
            }
        }));

        // =====================================================================
        // SKELETAL RIG & ANIMATION STUDIO (Slots 28, 34, 40)
        // =====================================================================

        // Slot 28: Skeletal Bone Rig Inspector
        ItemStack bonesBtn = createBtn(Material.ARMOR_STAND,
                lang.get("gui.entity-editor.bones-title"),
                lang.get("gui.entity-editor.bones-root", "%root%", def.getRootBoneName()),
                lang.get("gui.entity-editor.bones-count", "%bones%", String.valueOf(def.getBones().size())),
                lang.get("gui.entity-editor.bones-hitboxes", "%hitboxes%", String.valueOf(def.getAllHitboxes().size())),
                "",
                lang.get("gui.entity-editor.bones-ctrl"));
        canvas.setButton(28, CanvasButton.of(bonesBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            EntityBoneInspectorGUI.open(player, plugin, def);
        }));

        // Slot 34: Animation Studio & Live Previewer
        ItemStack animStudioBtn = createBtn(Material.FIREWORK_ROCKET,
                lang.get("gui.entity-editor.anim-studio-title"),
                lang.get("gui.entity-editor.anim-studio-count", "%count%", String.valueOf(def.getAnimations().size())),
                lang.get("gui.entity-editor.anim-studio-tracks", "%tracks%", def.getAnimations().isEmpty() ? lang.get("general.not-selected") : String.join(", ", def.getAnimations().keySet())),
                "",
                lang.get("gui.entity-editor.anim-studio-ctrl"));
        canvas.setButton(34, CanvasButton.of(animStudioBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            EntityAnimationMenu.open(player, plugin, def);
        }));

        // Slot 40: Re-link / Import Bedrock Model
        ItemStack importModelBtn = createBtn(Material.ITEM_FRAME,
                lang.get("gui.entity-editor.link-model-title"),
                lang.get("gui.entity-editor.link-model-lore-1"),
                lang.get("gui.entity-editor.link-model-lore-2"),
                "",
                lang.get("gui.entity-editor.link-model-ctrl"));
        canvas.setButton(40, CanvasButton.of(importModelBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            BedrockModelSelectorGUI.open(player, plugin);
        }));

        // =====================================================================
        // ROW 5: Bottom Navigation Bar (Slots 45, 49, 53)
        // =====================================================================

        // Slot 45: Back to Entity Browser
        ItemStack backBtn = createBtn(Material.SPECTRAL_ARROW,
                lang.get("gui.entity-editor.btn-back-browser"),
                lang.get("gui.entity-editor.btn-back-browser-lore-1"),
                lang.get("gui.entity-editor.btn-back-browser-lore-2"));
        canvas.setButton(45, CanvasButton.of(backBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            EntityBrowserGUI.open(player, plugin);
        }));

        // Slot 49: Spawn in World
        ItemStack worldSpawnBtn = createBtn(Material.CREEPER_SPAWN_EGG,
                lang.get("gui.entity-editor.btn-world-spawn"),
                lang.get("gui.entity-editor.btn-world-spawn-lore-1"),
                lang.get("gui.entity-editor.btn-world-spawn-lore-2"),
                "",
                lang.get("gui.entity-editor.btn-world-spawn-ctrl"));
        canvas.setButton(49, CanvasButton.of(worldSpawnBtn, e -> {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.2f);
            plugin.getCustomEntityManager().spawnEntity(def, player.getLocation());
            lang.send(player, "command.entity-spawn-success", "%amount%", "1", "%entity%", def.getId());
        }));

        // Slot 53: Save Entity Definition
        ItemStack saveBtn = createBtn(Material.EMERALD,
                lang.get("gui.entity-editor.btn-save-entity"),
                lang.get("gui.entity-editor.btn-save-entity-lore-1"),
                lang.get("gui.entity-editor.btn-save-entity-lore-2"),
                lang.get("gui.entity-editor.btn-save-entity-file", "%id%", def.getId()),
                "",
                lang.get("gui.entity-editor.btn-save-entity-ctrl"));
        canvas.setButton(53, CanvasButton.of(saveBtn, e -> {
            boolean saved = plugin.getCustomEntityRegistry().save(def);
            if (saved) {
                player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);
                lang.send(player, "gui.entity-editor.saved-success", "%entity%", def.getId());
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                lang.send(player, "gui.entity-editor.saved-failed");
            }
            player.closeInventory();
        }));

        canvas.open(player);
    }

    private void cycleSoundPresets(EntitySoundSpec sounds) {
        String hurt = sounds.getHurtSound();
        if ("entity.generic.hurt".equals(hurt)) {
            sounds.setHurtSound("entity.zombie.hurt");
            sounds.setDeathSound("entity.zombie.death");
            sounds.setAmbientSound("entity.zombie.ambient");
        } else if ("entity.zombie.hurt".equals(hurt)) {
            sounds.setHurtSound("entity.iron_golem.hurt");
            sounds.setDeathSound("entity.iron_golem.death");
            sounds.setAmbientSound(null);
            sounds.setStepSound("entity.iron_golem.step");
        } else if ("entity.iron_golem.hurt".equals(hurt)) {
            sounds.setHurtSound("entity.ravager.hurt");
            sounds.setDeathSound("entity.ravager.death");
            sounds.setAmbientSound("entity.ravager.ambient");
            sounds.setStepSound("entity.ravager.step");
        } else if ("entity.ravager.hurt".equals(hurt)) {
            sounds.setHurtSound("entity.spider.hurt");
            sounds.setDeathSound("entity.spider.death");
            sounds.setAmbientSound("entity.spider.ambient");
            sounds.setStepSound("entity.spider.step");
        } else {
            sounds.setHurtSound("entity.generic.hurt");
            sounds.setDeathSound("entity.generic.death");
            sounds.setAmbientSound(null);
            sounds.setStepSound(null);
        }
    }

    private Material getMobHeadOrEgg(EntityType type) {
        return switch (type) {
            case ZOMBIE -> Material.ZOMBIE_HEAD;
            case SKELETON -> Material.SKELETON_SKULL;
            case CREEPER -> Material.CREEPER_HEAD;
            case PIGLIN -> Material.PIGLIN_HEAD;
            case WITHER_SKELETON -> Material.WITHER_SKELETON_SKULL;
            case COW -> Material.COW_SPAWN_EGG;
            case PIG -> Material.PIG_SPAWN_EGG;
            case SHEEP -> Material.SHEEP_SPAWN_EGG;
            case HORSE -> Material.HORSE_SPAWN_EGG;
            case IRON_GOLEM -> Material.IRON_BLOCK;
            case SPIDER -> Material.SPIDER_SPAWN_EGG;
            case VILLAGER -> Material.VILLAGER_SPAWN_EGG;
            case SLIME -> Material.SLIME_BALL;
            default -> Material.ARMOR_STAND;
        };
    }

    private ItemStack createBtn(Material mat, String name, String... loreLines) {
        Material safeMat = MaterialUtil.toItemMaterial(mat);
        ItemStack item = new ItemStack(safeMat != null ? safeMat : Material.STONE);
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
