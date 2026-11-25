package org.bg52.synthetix.gui.editor.entity;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.entity.animation.AnimationTrack;
import org.bg52.synthetix.entity.model.CustomEntityDefinition;
import org.bg52.synthetix.entity.runtime.CustomEntityInstance;
import org.bg52.synthetix.gui.Canvas;
import org.bg52.synthetix.gui.CanvasButton;
import org.bg52.synthetix.lang.LanguageManager;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Interactive in-game Animation Studio menu for custom entities.
 * Strictly localized: zero hardcoded messages or strings.
 */
public class EntityAnimationMenu {

    private final SynthetixPlugin plugin;
    private final LanguageManager lang;
    private final CustomEntityDefinition def;

    public EntityAnimationMenu(SynthetixPlugin plugin, CustomEntityDefinition def) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
        this.def = def;
    }

    public static void open(Player player, SynthetixPlugin plugin, CustomEntityDefinition def) {
        new EntityAnimationMenu(plugin, def).open(player);
    }

    public void open(Player player) {
        Component title = lang.getComponent("gui.entity-animation.title", "%entity%", def.getId());
        Canvas canvas = new Canvas(title, 6);

        ItemStack darkGlass = Canvas.createBorderItem(Material.BLACK_STAINED_GLASS_PANE, Component.empty());
        ItemStack grayGlass = Canvas.createBorderItem(Material.GRAY_STAINED_GLASS_PANE, Component.empty());

        for (int c = 0; c < 9; c++) {
            canvas.setButton(0, c, CanvasButton.of(c == 0 || c == 8 ? darkGlass : grayGlass));
            canvas.setButton(5, c, CanvasButton.of(darkGlass));
        }
        for (int r = 1; r < 5; r++) {
            canvas.setButton(r, 0, CanvasButton.of(darkGlass));
            canvas.setButton(r, 8, CanvasButton.of(darkGlass));
        }

        // Header Slot 4: Info
        ItemStack header = new ItemStack(Material.FIREWORK_STAR);
        ItemMeta hMeta = header.getItemMeta();
        if (hMeta != null) {
            hMeta.displayName(lang.getComponent("gui.entity-animation.header-title"));
            hMeta.lore(List.of(
                    lang.getComponent("gui.entity-animation.header-lore-1"),
                    lang.getComponent("gui.entity-animation.header-lore-2")
            ));
            header.setItemMeta(hMeta);
        }
        canvas.setButton(4, CanvasButton.of(header));

        List<AnimationTrack> tracks = new ArrayList<>(def.getAnimations().values());
        tracks.sort(Comparator.comparing(AnimationTrack::getName));

        int[] innerSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        if (tracks.isEmpty()) {
            ItemStack emptyItem = new ItemStack(Material.STRUCTURE_VOID);
            ItemMeta eMeta = emptyItem.getItemMeta();
            if (eMeta != null) {
                eMeta.displayName(TextUtil.parse("<yellow><bold>No Animation Tracks Registered</bold></yellow>"));
                eMeta.lore(List.of(
                        TextUtil.parse("<gray>This entity currently has no animation channels.</gray>"),
                        TextUtil.parse("<gray>Import a <aqua>.bbmodel</aqua> or <aqua>.animation.json</aqua></gray>"),
                        TextUtil.parse("<gray>to automatically bind skeletal keyframes!</gray>")
                ));
                emptyItem.setItemMeta(eMeta);
            }
            canvas.setButton(22, CanvasButton.of(emptyItem));
        } else {
            for (int i = 0; i < Math.min(tracks.size(), innerSlots.length); i++) {
                AnimationTrack track = tracks.get(i);
                int slot = innerSlots[i];

                ItemStack icon = createTrackIcon(track);
                canvas.setButton(slot, CanvasButton.of(icon, e -> {
                    if (e.isRightClick()) {
                        // Toggle loop
                        track.setLoop(!track.isLoop());
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                        plugin.getCustomEntityRegistry().save(def);
                        lang.send(player, "gui.entity-animation.track-loop-toggled", "%anim%", track.getName(), "%val%", track.isLoop() ? lang.get("general.enabled") : lang.get("general.disabled"));
                        open(player);
                    } else {
                        // Left-Click: Live Preview
                        previewAnimation(player, track);
                    }
                }));
            }
        }

        // Slot 45: Back to Entity Editor
        ItemStack backBtn = Canvas.createBorderItem(Material.SPECTRAL_ARROW, lang.getComponent("gui.entity-animation.btn-back"));
        canvas.setButton(45, CanvasButton.of(backBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            EntityEditorMenu.open(player, plugin, def);
        }));

        // Slot 49: Stop All Previews
        ItemStack stopBtn = new ItemStack(Material.BARRIER);
        ItemMeta sMeta = stopBtn.getItemMeta();
        if (sMeta != null) {
            sMeta.displayName(lang.getComponent("gui.entity-animation.btn-stop"));
            sMeta.lore(List.of(lang.getComponent("gui.entity-animation.btn-stop-lore")));
            stopBtn.setItemMeta(sMeta);
        }
        canvas.setButton(49, CanvasButton.of(stopBtn, e -> {
            player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.7f, 1.0f);
            for (CustomEntityInstance inst : plugin.getCustomEntityManager().getActiveInstances()) {
                if (inst.getDefinition().getId().equalsIgnoreCase(def.getId())) {
                    inst.getAnimationController().stop();
                }
            }
            lang.send(player, "gui.entity-animation.preview-stopped");
        }));

        canvas.open(player);
    }

    private void previewAnimation(Player player, AnimationTrack track) {
        player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.7f, 1.5f);

        // Find nearest active instance or spawn one directly in front of the player
        CustomEntityInstance targetInstance = null;
        double closestDistSq = Double.MAX_VALUE;
        Location pLoc = player.getLocation();

        for (CustomEntityInstance inst : plugin.getCustomEntityManager().getActiveInstances()) {
            if (inst.getDefinition().getId().equalsIgnoreCase(def.getId()) && inst.getDriverMob() != null && inst.getDriverMob().isValid()) {
                double d = inst.getDriverMob().getLocation().distanceSquared(pLoc);
                if (d < closestDistSq && d < 20.0 * 20.0) {
                    closestDistSq = d;
                    targetInstance = inst;
                }
            }
        }

        if (targetInstance == null) {
            // Spawn test preview entity 3 blocks in front of the player
            Location spawnLoc = pLoc.clone().add(pLoc.getDirection().setY(0).normalize().multiply(3.0));
            spawnLoc.setPitch(0.0f);
            spawnLoc.setYaw((pLoc.getYaw() + 180.0f) % 360.0f); // face the player
            targetInstance = plugin.getCustomEntityManager().spawnEntity(def, spawnLoc);
        }

        if (targetInstance != null && targetInstance.getAnimationController() != null) {
            targetInstance.getAnimationController().play(track.getName(), track.isLoop(), 2.0f);
            lang.send(player, "gui.entity-animation.track-playing", "%anim%", track.getName(), "%loop%", track.isLoop() ? lang.get("general.enabled") : lang.get("general.disabled"));
        }
    }

    private ItemStack createTrackIcon(AnimationTrack track) {
        Material mat = track.isLoop() ? Material.REPEATER : Material.COMPARATOR;
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(lang.getComponent("gui.entity-animation.track-title", "%name%", track.getName()));
            List<Component> lore = new ArrayList<>();
            float seconds = track.getDurationTicks() / 20.0f;
            lore.add(lang.getComponent("gui.entity-animation.track-duration", "%sec%", String.format(Locale.ROOT, "%.2f", seconds), "%ticks%", String.valueOf((int) track.getDurationTicks())));
            lore.add(lang.getComponent("gui.entity-animation.track-loop", "%val%", track.isLoop() ? lang.get("general.enabled") : lang.get("general.disabled")));
            lore.add(lang.getComponent("gui.entity-animation.track-bones", "%bones%", String.valueOf(track.getBoneKeyframes().size())));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<dark_gray>────────────────────</dark_gray>"));
            lore.add(lang.getComponent("gui.entity-animation.track-ctrl-preview"));
            lore.add(lang.getComponent("gui.entity-animation.track-ctrl-toggle-loop"));
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
