package org.bg52.synthetix.ability;

import org.bg52.synthetix.SynthetixPlugin;
import org.bg52.synthetix.item.ItemDefinition;
import org.bg52.synthetix.scheduler.PlatformScheduler;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;

/**
 * Manages continuous ambient particle auras and trails for items.
 * Uses high-efficiency mathematical geometry to render smooth spirals, halos, wings, and orbiting orbs.
 */
public class AuraManager {

    private final SynthetixPlugin plugin;
    private PlatformScheduler.TaskHandle task;
    private double tickCounter = 0;

    public AuraManager(SynthetixPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) task.cancel();
        task = PlatformScheduler.get().runGlobalTimer(() -> {
            try {
                tickCounter += 0.2;
                if (tickCounter > Math.PI * 200) {
                    tickCounter = 0;
                }
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    PlatformScheduler.get().runEntity(player, () -> {
                        if (player.isOnline() && player.isValid()) {
                            processPlayerAuras(player);
                        }
                    });
                }
            } catch (Throwable ignored) {
            }
        }, 10L, 4L); // Ticks every 4 ticks (~0.2s)
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void processPlayerAuras(Player player) {
        if (plugin.getSettingsManager() != null && !plugin.getSettingsManager().isFeatureEnabled(org.bg52.synthetix.settings.GlobalFeature.PARTICLE_AURAS)) {
            return;
        }
        Set<String> processedAuras = new HashSet<>();

        // 1. Main Hand
        ItemStack main = player.getInventory().getItemInMainHand();
        checkAndRender(player, main, "while-holding-mainhand", processedAuras);

        // 2. Off Hand
        ItemStack off = player.getInventory().getItemInOffHand();
        checkAndRender(player, off, "while-holding-offhand", processedAuras);

        // 3. Armor Pieces
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && !armor.getType().isAir()) {
                checkAndRender(player, armor, "while-worn-armor", processedAuras);
            }
        }

        // 4. Inventory items with "always" or "in-inventory" condition
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && !item.getType().isAir()) {
                checkAndRender(player, item, "in-inventory", processedAuras);
            }
        }
    }

    private void checkAndRender(Player player, ItemStack item, String currentSlotCondition, Set<String> processed) {
        if (item == null || item.getType().isAir()) return;

        String id = plugin.getItemRegistry().getItemId(item);
        if (id == null) return;

        ItemDefinition def = plugin.getItemRegistry().get(id);
        if (def == null || def.getAura() == null || !def.getAura().isEnabled()) return;

        AuraSpec aura = def.getAura();
        String cond = aura.getCondition() != null ? aura.getCondition().toLowerCase(java.util.Locale.ROOT) : "while-held-or-worn";

        // Condition matching evaluated BEFORE deduplication
        if (cond.contains("sneaking") && !player.isSneaking()) return;

        boolean matchesCondition = switch (cond) {
            case "while-holding-mainhand", "mainhand" -> currentSlotCondition.equals("while-holding-mainhand");
            case "while-holding-offhand", "offhand" -> currentSlotCondition.equals("while-holding-offhand");
            case "while-held", "held" -> currentSlotCondition.startsWith("while-holding");
            case "while-worn-armor", "armor", "worn" -> currentSlotCondition.equals("while-worn-armor");
            case "while-held-or-worn", "held-or-worn" -> currentSlotCondition.startsWith("while-holding") || currentSlotCondition.equals("while-worn-armor");
            case "always", "in-inventory" -> true;
            case "only-while-sneaking", "sneaking" -> player.isSneaking() && (currentSlotCondition.startsWith("while-holding") || currentSlotCondition.equals("while-worn-armor"));
            default -> currentSlotCondition.startsWith("while-holding") || currentSlotCondition.equals("while-worn-armor");
        };

        if (!matchesCondition) return;

        // Avoid rendering identical item auras multiple times in the same tick
        if (!processed.add(id + ":" + aura.getShape().name())) return;

        renderAura(player, aura);
    }

    private void renderAura(Player player, AuraSpec aura) {
        Location base = player.getLocation();
        if (base.getWorld() == null) return;

        Particle particle = aura.resolveParticle();
        if (particle == null) return;
        int count = Math.max(1, aura.getCount());
        double radius = aura.getRadius();

        Object data = null;
        Class<?> dataType = particle.getDataType();
        if (dataType == Particle.DustOptions.class) {
            Color dustColor = Color.RED;
            if (aura.getColor() != null) {
                try {
                    String hex = aura.getColor().replace("#", "");
                    int rgb = Integer.parseInt(hex, 16);
                    dustColor = Color.fromRGB(rgb);
                } catch (Exception ignored) {}
            }
            data = new Particle.DustOptions(dustColor, 1.0f);
        } else if (dataType == Particle.DustTransition.class) {
            Color dustColor = Color.RED;
            if (aura.getColor() != null) {
                try {
                    String hex = aura.getColor().replace("#", "");
                    int rgb = Integer.parseInt(hex, 16);
                    dustColor = Color.fromRGB(rgb);
                } catch (Exception ignored) {}
            }
            data = new Particle.DustTransition(dustColor, Color.WHITE, 1.0f);
        }

        switch (aura.getShape()) {
            case HALO -> {
                // Circle floating above player head
                double yOffset = 2.05;
                for (int i = 0; i < 8; i++) {
                    double angle = (i * (Math.PI / 4)) + (tickCounter * 0.5);
                    double x = radius * 0.5 * Math.cos(angle);
                    double z = radius * 0.5 * Math.sin(angle);
                    spawn(player, particle, base.clone().add(x, yOffset, z), data, count);
                }
            }
            case SPIRAL -> {
                // Helix swirling from feet to head
                double height = 2.0;
                for (int i = 0; i < 3; i++) {
                    double t = (tickCounter + (i * (Math.PI * 2 / 3))) % (Math.PI * 2);
                    double y = (t / (Math.PI * 2)) * height;
                    double x = radius * Math.cos(t * 3);
                    double z = radius * Math.sin(t * 3);
                    spawn(player, particle, base.clone().add(x, y, z), data, count);
                }
            }
            case ORBITING_SPHERES -> {
                // 3 orbs orbiting waist height
                double waist = 1.0;
                for (int i = 0; i < 3; i++) {
                    double angle = tickCounter + (i * (Math.PI * 2 / 3));
                    double x = radius * Math.cos(angle);
                    double z = radius * Math.sin(angle);
                    spawn(player, particle, base.clone().add(x, waist, z), data, count);
                }
            }
            case FOOTSTEPS -> {
                // Ground footprints
                spawn(player, particle, base.clone().add(0, 0.05, 0), data, count);
            }
            case WINGS -> {
                // Particle wings rendered behind player back
                Vector dir = base.getDirection().setY(0).normalize();
                Vector perp = new Vector(-dir.getZ(), 0, dir.getX()); // Left/right perpendicular
                Location back = base.clone().add(0, 1.2, 0).subtract(dir.clone().multiply(0.3));

                for (double side : new double[]{-1.0, 1.0}) {
                    for (int pt = 1; pt <= 4; pt++) {
                        Vector wingPt = perp.clone().multiply(side * pt * 0.25).add(new Vector(0, pt * 0.15, 0));
                        spawn(player, particle, back.clone().add(wingPt), data, 1);
                    }
                }
            }
            case TRAIL, BURST -> {
                spawn(player, particle, base.clone().add(0, 0.9, 0), data, count);
            }
        }
    }

    private void spawn(Player player, Particle particle, Location loc, Object data, int count) {
        if (particle == null || loc == null || loc.getWorld() == null) return;
        try {
            int cnt = Math.max(1, count);
            if (data != null) {
                loc.getWorld().spawnParticle(particle, loc, cnt, 0.02, 0.02, 0.02, 0.0, data);
            } else {
                loc.getWorld().spawnParticle(particle, loc, cnt, 0.02, 0.02, 0.02, 0.01);
            }
        } catch (Throwable t) {
            try {
                loc.getWorld().spawnParticle(Particle.FLAME, loc, 1, 0.01, 0.01, 0.01, 0.01);
            } catch (Throwable ignored) {}
        }
    }
}

