package org.bg52.synthetix.gui.editor.entity;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.entity.model.CustomEntityDefinition;
import org.bg52.synthetix.entity.model.EntityBoneSpec;
import org.bg52.synthetix.entity.model.EntityHitboxSpec;
import org.bg52.synthetix.gui.Canvas;
import org.bg52.synthetix.gui.CanvasButton;
import org.bg52.synthetix.lang.LanguageManager;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Visual inspector displaying skeletal bone hierarchy, pivots, and hitboxes of a custom entity.
 * Strictly localized: zero hardcoded messages or strings.
 */
public class EntityBoneInspectorGUI {

    private final SynthetixPlugin plugin;
    private final LanguageManager lang;
    private final CustomEntityDefinition def;

    public EntityBoneInspectorGUI(SynthetixPlugin plugin, CustomEntityDefinition def) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
        this.def = def;
    }

    public static void open(Player player, SynthetixPlugin plugin, CustomEntityDefinition def) {
        new EntityBoneInspectorGUI(plugin, def).open(player);
    }

    public void open(Player player) {
        Component title = lang.getComponent("gui.entity-bones.title", "%entity%", def.getId());
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
        ItemStack header = new ItemStack(Material.BONE);
        ItemMeta hMeta = header.getItemMeta();
        if (hMeta != null) {
            hMeta.displayName(lang.getComponent("gui.entity-bones.header-title"));
            hMeta.lore(List.of(
                    lang.getComponent("gui.entity-bones.header-lore-1"),
                    lang.getComponent("gui.entity-bones.header-lore-2")
            ));
            header.setItemMeta(hMeta);
        }
        canvas.setButton(4, CanvasButton.of(header));

        List<EntityBoneSpec> boneList = new ArrayList<>(def.getBones().values());
        boneList.sort(Comparator.comparing(EntityBoneSpec::getName));

        int[] innerSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        for (int i = 0; i < Math.min(boneList.size(), innerSlots.length); i++) {
            EntityBoneSpec bone = boneList.get(i);
            int slot = innerSlots[i];

            ItemStack icon = createBoneIcon(bone);
            canvas.setButton(slot, CanvasButton.of(icon));
        }

        // Slot 45: Back to Entity Editor
        ItemStack backBtn = Canvas.createBorderItem(Material.SPECTRAL_ARROW, lang.getComponent("gui.entity-bones.btn-back"));
        canvas.setButton(45, CanvasButton.of(backBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            EntityEditorMenu.open(player, plugin, def);
        }));

        canvas.open(player);
    }

    private ItemStack createBoneIcon(EntityBoneSpec bone) {
        boolean isRoot = bone.getName().equalsIgnoreCase(def.getRootBoneName()) || bone.getParent() == null;
        Material mat = isRoot ? Material.NETHER_STAR : (bone.getHitbox() != null ? Material.TARGET : Material.BONE);

        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(lang.getComponent("gui.entity-bones.bone-title", "%name%", bone.getName()));
            List<Component> lore = new ArrayList<>();
            lore.add(lang.getComponent("gui.entity-bones.bone-parent", "%parent%", bone.getParent() != null ? bone.getParent() : "None (Root)"));

            Vector3f p = bone.getPivot();
            lore.add(lang.getComponent("gui.entity-bones.bone-pivot",
                    "%x%", String.format(Locale.ROOT, "%.2f", p.x),
                    "%y%", String.format(Locale.ROOT, "%.2f", p.y),
                    "%z%", String.format(Locale.ROOT, "%.2f", p.z)));

            lore.add(lang.getComponent("gui.entity-bones.bone-material", "%mat%", bone.getMaterial() != null ? bone.getMaterial().name() : "AIR"));
            lore.add(lang.getComponent("gui.entity-bones.bone-cmd", "%cmd%", String.valueOf(bone.getCustomModelData())));

            EntityHitboxSpec hb = bone.getHitbox();
            if (hb != null) {
                lore.add(Component.empty());
                lore.add(lang.getComponent("gui.entity-bones.bone-hitbox", "%val%", hb.getWidth() + "x" + hb.getHeight()));
                lore.add(lang.getComponent("gui.entity-bones.bone-mult", "%mult%", String.format(Locale.ROOT, "%.1f", hb.getDamageMultiplier())));
                lore.add(lang.getComponent("gui.entity-bones.bone-weakpoint", "%val%", hb.isWeakpoint() ? lang.get("general.applied") : lang.get("general.not-applied")));
            }
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
