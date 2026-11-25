package org.bg52.synthetix.ability;

import org.bg52.synthetix.item.food.FoodEffectSpec;
import org.bg52.synthetix.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Locale;

/**
 * Universal Action Executor for parsing and firing ability actions.
 */
public final class ActionExecutor {

    private ActionExecutor() {}

    public static void executeAction(String rawAction, Player player, Entity targetEntity, String itemId) {
        if (rawAction == null || rawAction.trim().isEmpty()) return;
        String action = rawAction.trim();

        // 1. Sound
        if (action.startsWith("sound:")) {
            handleSound(action.substring(6).trim(), player);
            return;
        }

        // 2. Particle
        if (action.startsWith("particle:")) {
            handleParticle(action.substring(9).trim(), player, targetEntity);
            return;
        }

        // 3. Command
        if (action.startsWith("command:")) {
            handleCommand(action.substring(8).trim(), player, targetEntity, itemId);
            return;
        }

        // 4. Message
        if (action.startsWith("message:")) {
            String msg = action.substring(8).trim()
                    .replace("%player%", player.getName())
                    .replace("%item%", itemId != null ? itemId : "");
            player.sendMessage(TextUtil.parse(msg));
            return;
        }

        // 5. Ignite
        if (action.startsWith("ignite:")) {
            try {
                int sec = Integer.parseInt(action.substring(7).trim());
                Entity toIgnite = targetEntity != null ? targetEntity : player;
                toIgnite.setFireTicks(sec * 20);
            } catch (Exception ignored) {}
            return;
        }

        // 6. Status Effect
        if (action.startsWith("effect:")) {
            handleEffect(action.substring(7).trim(), player);
            return;
        }

        // 7. Projectile
        if (action.startsWith("projectile:")) {
            handleProjectile(action.substring(11).trim(), player);
            return;
        }

        // 8. Heal
        if (action.startsWith("heal:")) {
            try {
                double amount = Double.parseDouble(action.substring(5).trim());
                double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH) != null
                        ? player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue()
                        : 20.0;
                player.setHealth(Math.min(maxHealth, player.getHealth() + amount));
            } catch (Exception ignored) {}
            return;
        }

        // 9. Damage Target
        if (action.startsWith("damage:")) {
            try {
                double amount = Double.parseDouble(action.substring(7).trim());
                if (targetEntity instanceof LivingEntity living) {
                    living.damage(amount, player);
                }
            } catch (Exception ignored) {}
            return;
        }

        // 10. Lightning Strike
        if (action.startsWith("lightning:") || action.equalsIgnoreCase("lightning")) {
            Location strikeLoc = targetEntity != null ? targetEntity.getLocation() : player.getLocation();
            player.getWorld().strikeLightning(strikeLoc);
            return;
        }

        // 11. ActionBar Message
        if (action.startsWith("actionbar:")) {
            String msg = action.substring(10).trim()
                    .replace("%player%", player.getName())
                    .replace("%item%", itemId != null ? itemId : "");
            player.sendActionBar(TextUtil.parse(msg));
            return;
        }

        // 12. Title Message
        if (action.startsWith("title:")) {
            String raw = action.substring(6).trim()
                    .replace("%player%", player.getName())
                    .replace("%item%", itemId != null ? itemId : "");
            String[] parts = raw.split(";", 2);
            String title = parts[0].trim();
            String subtitle = parts.length > 1 ? parts[1].trim() : "";
            player.showTitle(net.kyori.adventure.title.Title.title(
                    TextUtil.parse(title),
                    TextUtil.parse(subtitle)
            ));
            return;
        }

        // 13. Feed
        if (action.startsWith("feed:")) {
            try {
                int amt = Integer.parseInt(action.substring(5).trim());
                player.setFoodLevel(Math.min(20, player.getFoodLevel() + amt));
            } catch (Exception ignored) {}
            return;
        }

        // 14. Velocity / Launch
        if (action.startsWith("launch:") || action.startsWith("velocity:")) {
            try {
                String val = action.contains(":") ? action.substring(action.indexOf(':') + 1).trim() : "1.0";
                double mult = Double.parseDouble(val);
                Vector dir = player.getLocation().getDirection().normalize().multiply(mult);
                player.setVelocity(dir);
            } catch (Exception ignored) {}
        }
    }

    private static void handleSound(String args, Player player) {
        String[] parts = args.split("\\s+");
        if (parts.length == 0) return;
        float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
        float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
        try {
            Sound sound = Sound.valueOf(parts[0].toUpperCase(Locale.ROOT));
            player.getWorld().playSound(player.getLocation(), sound, volume, pitch);
        } catch (Exception e) {
            try {
                player.playSound(player.getLocation(), parts[0].toLowerCase(Locale.ROOT), volume, pitch);
            } catch (Throwable ignored) {}
        }
    }

    private static void handleParticle(String args, Player player, Entity targetEntity) {
        String[] parts = args.split("\\s+");
        if (parts.length == 0) return;
        try {
            Particle particle = Particle.valueOf(parts[0].toUpperCase(Locale.ROOT));
            int count = parts.length > 1 ? Integer.parseInt(parts[1]) : 10;
            double ox = parts.length > 2 ? Double.parseDouble(parts[2]) : 0.5;
            double oy = parts.length > 3 ? Double.parseDouble(parts[3]) : 0.5;
            double oz = parts.length > 4 ? Double.parseDouble(parts[4]) : 0.5;
            double speed = parts.length > 5 ? Double.parseDouble(parts[5]) : 0.05;

            Location loc = targetEntity != null ? targetEntity.getLocation().add(0, 1, 0) : player.getLocation().add(0, 1, 0);
            if (particle == Particle.DUST) {
                player.getWorld().spawnParticle(particle, loc, count, ox, oy, oz, speed, new Particle.DustOptions(org.bukkit.Color.RED, 1.0f));
            } else {
                player.getWorld().spawnParticle(particle, loc, count, ox, oy, oz, speed);
            }
        } catch (Exception ignored) {}
    }

    private static void handleCommand(String cmdStr, Player player, Entity targetEntity, String itemId) {
        boolean console = true;
        String raw = cmdStr;
        if (raw.startsWith("console:")) {
            console = true;
            raw = raw.substring(8).trim();
        } else if (raw.startsWith("player:")) {
            console = false;
            raw = raw.substring(7).trim();
        }

        raw = raw.replace("%player%", player.getName())
                .replace("%uuid%", player.getUniqueId().toString())
                .replace("%item%", itemId != null ? itemId : "")
                .replace("%target%", targetEntity != null ? targetEntity.getName() : player.getName())
                .replace("%target_uuid%", targetEntity != null ? targetEntity.getUniqueId().toString() : player.getUniqueId().toString());

        if (raw.startsWith("/")) {
            raw = raw.substring(1);
        }

        if (console) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), raw);
        } else {
            player.performCommand(raw);
        }
    }

    private static void handleEffect(String args, Player player) {
        String[] parts = args.split("\\s+");
        if (parts.length == 0) return;
        try {
            PotionEffectType type = FoodEffectSpec.parsePotionEffectType(parts[0]);
            if (type == null) return;
            int durationSec = parts.length > 1 ? Integer.parseInt(parts[1]) : 5;
            int amp = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            player.addPotionEffect(new PotionEffect(type, durationSec * 20, amp, false, true, true));
        } catch (Exception ignored) {}
    }

    private static void handleProjectile(String args, Player player) {
        String[] parts = args.split("\\s+");
        if (parts.length == 0) return;
        String typeStr = parts[0].toUpperCase(Locale.ROOT);
        double speed = 1.5;
        for (String p : parts) {
            if (p.startsWith("speed:")) {
                try {
                    speed = Double.parseDouble(p.substring(6));
                } catch (Exception ignored) {}
            }
        }

        Class<? extends Projectile> pClass = switch (typeStr) {
            case "SNOWBALL" -> Snowball.class;
            case "SMALL_FIREBALL" -> SmallFireball.class;
            case "FIREBALL", "LARGE_FIREBALL" -> LargeFireball.class;
            case "DRAGON_FIREBALL" -> DragonFireball.class;
            case "WITHER_SKULL" -> WitherSkull.class;
            case "SPECTRAL_ARROW" -> SpectralArrow.class;
            default -> Arrow.class;
        };

        try {
            Projectile proj = player.launchProjectile(pClass);
            proj.setVelocity(player.getLocation().getDirection().multiply(speed));
        } catch (Exception ignored) {}
    }
}

