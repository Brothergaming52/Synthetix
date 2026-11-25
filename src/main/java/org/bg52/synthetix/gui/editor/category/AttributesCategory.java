package org.bg52.synthetix.gui.editor.category;

import net.kyori.adventure.text.Component;
import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.api.editor.ItemEditorCategory;
import org.bg52.synthetix.gui.CanvasButton;
import org.bg52.synthetix.gui.editor.BaseEditorCanvas;
import org.bg52.synthetix.item.ItemDefinition;
import org.bg52.synthetix.lang.LanguageManager;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Core category dynamically querying and modifying all server attributes (health, attack damage, speed, scale, armor).
 * Compatible across Paper 1.20.6 through Paper 26.3+ via Registry.ATTRIBUTE.
 */
public class AttributesCategory implements ItemEditorCategory {

    private final SynthetixPlugin plugin;
    private final LanguageManager lang;

    public AttributesCategory(SynthetixPlugin plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    @Override
    public String getId() {
        return "attributes";
    }

    @Override
    public Component getDisplayName() {
        return lang.getComponent("editor.categories.attributes.name");
    }

    @Override
    public int getPriority() {
        return 30;
    }

    @Override
    public boolean isPluginCategory() {
        return false;
    }

    @Override
    public ItemStack getIcon(ItemDefinition def) {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(getDisplayName());
            List<Component> lore = new ArrayList<>();
            lore.add(lang.getComponent("editor.categories.attributes.desc-1"));
            lore.add(lang.getComponent("editor.categories.attributes.desc-2"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<dark_gray>•</dark_gray> <gray>Active Modifiers:</gray> <white>" + def.getAttributes().size() + "</white>"));
            lore.add(Component.empty());
            lore.add(TextUtil.parse("<yellow>Click to browse all attributes »</yellow>"));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void open(Player player, ItemDefinition def, Runnable onBack) {
        openAttributesPage(player, def, onBack, 0);
    }

    private void openAttributesPage(Player player, ItemDefinition def, Runnable onBack, int page) {
        List<Attribute> allAttributes = new ArrayList<>();
        for (Attribute attr : Registry.ATTRIBUTE) {
            allAttributes.add(attr);
        }
        allAttributes.sort(Comparator.comparing(a -> a.getKey().asString()));

        int pageSize = 28; // 4 rows x 7 columns
        int totalPages = Math.max(1, (int) Math.ceil((double) allAttributes.size() / pageSize));
        int curPage = Math.max(0, Math.min(totalPages - 1, page));

        BaseEditorCanvas base = new BaseEditorCanvas(plugin, def,
                TextUtil.parse(lang.get("editor.categories.attributes.title") + " <dark_gray>(" + (curPage + 1) + "/" + totalPages + ")</dark_gray>"), 6);

        int start = curPage * pageSize;
        int end = Math.min(start + pageSize, allAttributes.size());

        int[] validSlots = new int[]{
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        int slotIdx = 0;
        for (int i = start; i < end; i++) {
            Attribute attr = allAttributes.get(i);
            ItemDefinition.AttributeSpec existing = findSpec(def, attr);
            String cleanName = formatAttributeName(attr);

            boolean applied = existing != null;
            ItemStack icon = new ItemStack(applied ? Material.ENCHANTED_BOOK : Material.BOOK);
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.displayName(TextUtil.parse("<yellow>" + cleanName + "</yellow>"));
                List<Component> lore = new ArrayList<>();
                lore.add(TextUtil.parse("<dark_gray>Key: " + attr.getKey().asString() + "</dark_gray>"));
                lore.add(Component.empty());
                lore.add(TextUtil.parse("<gray>Status:</gray> " + (applied ? lang.get("general.applied") : lang.get("general.not-applied"))));
                if (applied) {
                    lore.add(TextUtil.parse("<gray>Amount:</gray> <gold>" + (existing.getAmount() >= 0 ? "+" : "") + existing.getAmount() + "</gold>"));
                    lore.add(TextUtil.parse("<gray>Operation:</gray> <aqua>" + TextUtil.prettify(existing.getOperation().name()) + "</aqua>"));
                    lore.add(TextUtil.parse("<gray>Slot:</gray> <light_purple>" + (existing.getSlot() != null ? TextUtil.prettify(existing.getSlot().name()) : "Any (Always Active)") + "</light_purple>"));
                }
                lore.add(Component.empty());
                lore.add(TextUtil.parse("<yellow>Click to edit modifier settings »</yellow>"));
                meta.lore(lore);
                icon.setItemMeta(meta);
            }

            int targetSlot = validSlots[slotIdx++];
            base.getCanvas().setButton(targetSlot, CanvasButton.of(icon, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                openAttributeModifierSubmenu(player, def, attr, () -> openAttributesPage(player, def, onBack, curPage));
            }));
        }

        base.setBackButton(onBack);
        base.setPaginationButtons(
                curPage > 0, () -> openAttributesPage(player, def, onBack, curPage - 1),
                curPage < totalPages - 1, () -> openAttributesPage(player, def, onBack, curPage + 1)
        );

        base.open(player);
    }

    private void openAttributeModifierSubmenu(Player player, ItemDefinition def, Attribute attr, Runnable onBack) {
        ItemDefinition.AttributeSpec spec = findSpec(def, attr);
        String cleanName = formatAttributeName(attr);

        if (spec == null) {
            String shortKey = attr.getKey().getKey().replace('.', '_');
            spec = new ItemDefinition.AttributeSpec(attr.getKey().asString(), "synth_" + shortKey, 1.0, AttributeModifier.Operation.ADD_NUMBER, null);
            def.getAttributes().add(spec);
        }

        final ItemDefinition.AttributeSpec activeSpec = spec;

        BaseEditorCanvas base = new BaseEditorCanvas(plugin, def,
                lang.getComponent("editor.categories.attributes.edit-title", "%attribute%", cleanName), 4);
        base.setupSideBorders();

        // 1. Amount Button (Slot 10) - Quick Clicks & Custom Input
        ItemStack amountBtn = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta aMeta = amountBtn.getItemMeta();
        if (aMeta != null) {
            aMeta.displayName(lang.getComponent("editor.categories.attributes.amount-btn"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<gray>Current Value:</gray> <white>" + activeSpec.getAmount() + "</white>"));
            l.add(Component.empty());
            l.add(TextUtil.parse("<yellow>Left-Click: +1.0 | Right-Click: -1.0</yellow>"));
            l.add(TextUtil.parse("<yellow>Shift-Left: +5.0</yellow>"));
            l.add(TextUtil.parse("<yellow>Shift-Right: Type custom amount in chat »</yellow>"));
            aMeta.lore(l);
            amountBtn.setItemMeta(aMeta);
        }
        base.getCanvas().setButton(10, CanvasButton.of(amountBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            if (e.isShiftClick() && e.isRightClick()) {
                plugin.getChatPromptManager().prompt(player,
                        lang.getComponent("editor.prompts.enter-attr-amount"),
                        input -> {
                            try {
                                activeSpec.setAmount(Double.parseDouble(input.trim()));
                                lang.send(player, "editor.categories.attributes.amount-updated");
                            } catch (NumberFormatException ignored) {}
                            openAttributeModifierSubmenu(player, def, attr, onBack);
                        });
            } else if (e.isShiftClick()) {
                activeSpec.setAmount(activeSpec.getAmount() + 5.0);
                openAttributeModifierSubmenu(player, def, attr, onBack);
            } else if (e.isRightClick()) {
                activeSpec.setAmount(activeSpec.getAmount() - 1.0);
                openAttributeModifierSubmenu(player, def, attr, onBack);
            } else {
                activeSpec.setAmount(activeSpec.getAmount() + 1.0);
                openAttributeModifierSubmenu(player, def, attr, onBack);
            }
        }));

        // 2. Operation Cycle Button (Slot 12)
        ItemStack opBtn = new ItemStack(Material.REPEATER);
        ItemMeta opMeta = opBtn.getItemMeta();
        if (opMeta != null) {
            opMeta.displayName(lang.getComponent("editor.categories.attributes.operation-btn"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<gray>Current:</gray> <white>" + TextUtil.prettify(activeSpec.getOperation().name()) + "</white>"));
            l.add(Component.empty());
            l.add(TextUtil.parse("<yellow>Click to cycle operation:</yellow>"));
            l.add(TextUtil.parse("<dark_gray>•</dark_gray> ADD_NUMBER (flat value)"));
            l.add(TextUtil.parse("<dark_gray>•</dark_gray> ADD_SCALAR (% of base)"));
            l.add(TextUtil.parse("<dark_gray>•</dark_gray> MULTIPLY_SCALAR_1 (% compounded)"));
            opMeta.lore(l);
            opBtn.setItemMeta(opMeta);
        }
        base.getCanvas().setButton(12, CanvasButton.of(opBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            AttributeModifier.Operation next = switch (activeSpec.getOperation()) {
                case ADD_NUMBER -> AttributeModifier.Operation.ADD_SCALAR;
                case ADD_SCALAR -> AttributeModifier.Operation.MULTIPLY_SCALAR_1;
                case MULTIPLY_SCALAR_1 -> AttributeModifier.Operation.ADD_NUMBER;
            };
            activeSpec.setOperation(next);
            openAttributeModifierSubmenu(player, def, attr, onBack);
        }));

        // 3. Slot Restriction Cycle Button (Slot 14)
        ItemStack slotBtn = new ItemStack(Material.IRON_CHESTPLATE);
        ItemMeta sMeta = slotBtn.getItemMeta();
        if (sMeta != null) {
            sMeta.displayName(lang.getComponent("editor.categories.attributes.slot-btn"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<gray>Current Slot:</gray> <white>" + (activeSpec.getSlot() != null ? TextUtil.prettify(activeSpec.getSlot().name()) : "Any (Always Active)") + "</white>"));
            l.add(Component.empty());
            l.add(TextUtil.parse("<yellow>Click to cycle equipment slot constraint.</yellow>"));
            sMeta.lore(l);
            slotBtn.setItemMeta(sMeta);
        }
        base.getCanvas().setButton(14, CanvasButton.of(slotBtn, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            EquipmentSlot current = activeSpec.getSlot();
            EquipmentSlot next = null;
            if (current == null) next = EquipmentSlot.HAND;
            else if (current == EquipmentSlot.HAND) next = EquipmentSlot.OFF_HAND;
            else if (current == EquipmentSlot.OFF_HAND) next = EquipmentSlot.HEAD;
            else if (current == EquipmentSlot.HEAD) next = EquipmentSlot.CHEST;
            else if (current == EquipmentSlot.CHEST) next = EquipmentSlot.LEGS;
            else if (current == EquipmentSlot.LEGS) next = EquipmentSlot.FEET;
            else if (current == EquipmentSlot.FEET) next = null; // back to ANY

            activeSpec.setSlot(next);
            openAttributeModifierSubmenu(player, def, attr, onBack);
        }));

        // 4. Delete / Remove Modifier (Slot 16)
        ItemStack delBtn = new ItemStack(Material.BARRIER);
        ItemMeta dMeta = delBtn.getItemMeta();
        if (dMeta != null) {
            dMeta.displayName(lang.getComponent("editor.categories.attributes.remove-btn"));
            List<Component> l = new ArrayList<>();
            l.add(TextUtil.parse("<gray>Click to remove this attribute modifier from the item.</gray>"));
            dMeta.lore(l);
            delBtn.setItemMeta(dMeta);
        }
        base.getCanvas().setButton(16, CanvasButton.of(delBtn, e -> {
            def.getAttributes().remove(activeSpec);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.7f, 1.0f);
            lang.send(player, "editor.categories.attributes.removed", "%attribute%", cleanName);
            onBack.run();
        }));

        base.setBackButton(onBack);
        base.open(player);
    }

    private ItemDefinition.AttributeSpec findSpec(ItemDefinition def, Attribute attr) {
        String fullKey = attr.getKey().asString(); // e.g. "minecraft:generic.attack_damage"
        String simpleKey = attr.getKey().getKey(); // e.g. "generic.attack_damage"
        String stripped = simpleKey.replace("generic.", "").replace("player.", "");

        for (ItemDefinition.AttributeSpec spec : def.getAttributes()) {
            String name = spec.getAttributeName();
            if (name.equalsIgnoreCase(fullKey)
                    || name.equalsIgnoreCase(simpleKey)
                    || name.equalsIgnoreCase(stripped)
                    || name.equalsIgnoreCase(simpleKey.replace('.', '_'))
                    || name.equalsIgnoreCase("GENERIC_" + simpleKey.replace('.', '_'))
                    || name.equalsIgnoreCase(stripped.replace('.', '_'))) {
                return spec;
            }
        }
        return null;
    }

    private String formatAttributeName(Attribute attr) {
        String key = attr.getKey().getKey(); // e.g. "generic.attack_damage"
        String raw = key.replace("generic.", "").replace("player.", "").replace('.', ' ').replace('_', ' ');
        String[] parts = raw.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }
}
