package org.bg52.synthetix.entity.behavior;

import org.bg52.synthetix.entity.runtime.CustomEntityInstance;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Locale;

/**
 * Generalized, modular action executed during an entity behavior.
 */
public class EntityBehaviorAction {

    private EntityBehaviorActionType type;
    private String parameter;

    public EntityBehaviorAction() {
        this.type = EntityBehaviorActionType.MELEE_DAMAGE;
        this.parameter = "6.0";
    }

    public EntityBehaviorAction(EntityBehaviorActionType type, String parameter) {
        this.type = type != null ? type : EntityBehaviorActionType.MELEE_DAMAGE;
        this.parameter = parameter != null ? parameter : "";
    }

    public EntityBehaviorActionType getType() {
        return type;
    }

    public void setType(EntityBehaviorActionType type) {
        this.type = type != null ? type : EntityBehaviorActionType.MELEE_DAMAGE;
    }

    public String getParameter() {
        return parameter;
    }

    public void setParameter(String parameter) {
        this.parameter = parameter != null ? parameter : "";
    }

    /**
     * Executes this action given the mob instance and optional target.
     */
    public void execute(CustomEntityInstance instance, LivingEntity target) {
        if (instance == null || !(instance.getDriverMob() instanceof Mob mob) || !mob.isValid()) return;

        switch (type) {
            case MELEE_DAMAGE -> {
                double damage = parseDouble(parameter, instance.getDefinition().getAttackDamage());
                if (target != null && target.isValid() && !target.isDead()) {
                    target.damage(damage, mob);
                    playHitSound(target.getLocation());
                } else {
                    // Area of effect damage to nearby opposing living entities
                    double reach = 2.5;
                    for (Entity e : mob.getNearbyEntities(reach, reach, reach)) {
                        if (e instanceof LivingEntity le && le != mob && isValidVictim(mob, le)) {
                            le.damage(damage, mob);
                            playHitSound(le.getLocation());
                            break;
                        }
                    }
                }
            }

            case SHOOT_PROJECTILE -> {
                String[] tokens = parameter.split("\\s+");
                String projName = tokens.length > 0 && !tokens[0].isEmpty() ? tokens[0].toUpperCase(Locale.ROOT) : "FIREBALL";
                double speed = 1.2;
                for (String t : tokens) {
                    if (t.toLowerCase(Locale.ROOT).startsWith("speed:")) {
                        speed = parseDouble(t.substring("speed:".length()), speed);
                    }
                }

                Location eyeLoc = mob.getEyeLocation();
                Vector dir = mob.getLocation().getDirection();
                if (target != null && target.isValid()) {
                    dir = target.getEyeLocation().toVector().subtract(eyeLoc.toVector()).normalize();
                }

                Class<? extends Projectile> projClass = switch (projName) {
                    case "SMALL_FIREBALL" -> SmallFireball.class;
                    case "DRAGON_FIREBALL" -> DragonFireball.class;
                    case "WITHER_SKULL" -> WitherSkull.class;
                    case "SHULKER_BULLET" -> ShulkerBullet.class;
                    case "SNOWBALL" -> Snowball.class;
                    case "ARROW" -> Arrow.class;
                    case "SPECTRAL_ARROW" -> SpectralArrow.class;
                    case "EGG" -> Egg.class;
                    default -> Fireball.class;
                };

                try {
                    Projectile proj = mob.launchProjectile(projClass, dir.multiply(speed));
                    proj.setShooter(mob);
                } catch (Exception ignored) {}
            }

            case LEAP_DASH -> {
                String[] tokens = parameter.split("\\s+");
                double hSpeed = tokens.length > 0 ? parseDouble(tokens[0], 0.5) : 0.5;
                double vSpeed = tokens.length > 1 ? parseDouble(tokens[1], 0.25) : 0.25;
                boolean away = tokens.length > 2 && "away".equalsIgnoreCase(tokens[2]);

                Vector dir = mob.getLocation().getDirection();
                if (target != null && target.isValid()) {
                    dir = target.getLocation().toVector().subtract(mob.getLocation().toVector());
                }
                dir.setY(0);
                if (dir.lengthSquared() > 1.0E-4) {
                    dir.normalize();
                } else {
                    dir = new Vector(1, 0, 0);
                }
                if (away) dir.multiply(-1);

                dir.multiply(hSpeed);
                dir.setY(vSpeed);
                mob.setVelocity(dir);
            }

            case POTION_EFFECT -> {
                String[] tokens = parameter.split("\\s+");
                if (tokens.length >= 1) {
                    PotionEffectType effectType = PotionEffectType.getByName(tokens[0].toUpperCase(Locale.ROOT));
                    if (effectType != null) {
                        int durationSec = tokens.length > 1 ? parseInt(tokens[1], 5) : 5;
                        int amp = tokens.length > 2 ? parseInt(tokens[2], 0) : 0;
                        boolean onSelf = tokens.length > 3 && "self".equalsIgnoreCase(tokens[3]);

                        PotionEffect effect = new PotionEffect(effectType, durationSec * 20, amp, false, true, true);
                        if (onSelf) {
                            mob.addPotionEffect(effect);
                        } else if (target != null && target.isValid()) {
                            target.addPotionEffect(effect);
                        }
                    }
                }
            }

            case PARTICLE_BURST -> {
                String[] tokens = parameter.split("\\s+");
                if (tokens.length >= 1) {
                    try {
                        Particle p = Particle.valueOf(tokens[0].toUpperCase(Locale.ROOT));
                        int count = tokens.length > 1 ? parseInt(tokens[1], 15) : 15;
                        double ox = tokens.length > 2 ? parseDouble(tokens[2], 0.4) : 0.4;
                        double oy = tokens.length > 3 ? parseDouble(tokens[3], 0.4) : 0.4;
                        double oz = tokens.length > 4 ? parseDouble(tokens[4], 0.4) : 0.4;
                        double speed = tokens.length > 5 ? parseDouble(tokens[5], 0.05) : 0.05;
                        mob.getWorld().spawnParticle(p, mob.getLocation().add(0, 0.8, 0), count, ox, oy, oz, speed);
                    } catch (Exception ignored) {}
                }
            }

            case PLAY_SOUND -> {
                String[] tokens = parameter.split("\\s+");
                if (tokens.length >= 1) {
                    float vol = tokens.length > 1 ? (float) parseDouble(tokens[1], 1.0) : 1.0f;
                    float pitch = tokens.length > 2 ? (float) parseDouble(tokens[2], 1.0) : 1.0f;
                    try {
                        Sound s = Sound.valueOf(tokens[0].toUpperCase(Locale.ROOT));
                        mob.getWorld().playSound(mob.getLocation(), s, SoundCategory.HOSTILE, vol, pitch);
                    } catch (Exception e) {
                        mob.getWorld().playSound(mob.getLocation(), tokens[0], SoundCategory.HOSTILE, vol, pitch);
                    }
                }
            }

            case TELEPORT_BEHIND -> {
                if (target != null && target.isValid()) {
                    double dist = parseDouble(parameter, 2.0);
                    Location tLoc = target.getLocation();
                    Vector dir = tLoc.getDirection().setY(0).normalize().multiply(-dist);
                    Location dest = tLoc.clone().add(dir);
                    dest.setDirection(tLoc.toVector().subtract(dest.toVector()).normalize());
                    mob.teleport(dest);
                }
            }

            case SPAWN_MINIONS -> {
                String[] tokens = parameter.split("\\s+");
                String minionType = tokens.length > 0 ? tokens[0] : "ZOMBIE";
                int count = tokens.length > 1 ? parseInt(tokens[1], 2) : 2;
                Location center = mob.getLocation();

                for (int i = 0; i < count; i++) {
                    double angle = (2 * Math.PI / count) * i;
                    Location spawnLoc = center.clone().add(Math.cos(angle) * 2.0, 0, Math.sin(angle) * 2.0);
                    try {
                        EntityType et = EntityType.valueOf(minionType.toUpperCase(Locale.ROOT));
                        mob.getWorld().spawnEntity(spawnLoc, et);
                    } catch (Exception ex) {
                        // Attempt custom entity spawn
                        var def = instance.getDefinition();
                        if (def != null && def.getId().equalsIgnoreCase(minionType)) {
                            // avoid infinite recursion if same id
                            continue;
                        }
                    }
                }
            }

            case HEAL -> {
                double amount = parseDouble(parameter, 10.0);
                double max = instance.getDefinition().getMaxHealth();
                mob.setHealth(Math.min(max, mob.getHealth() + amount));
            }

            case RUN_COMMAND -> {
                String cmd = parameter
                        .replace("%entity_id%", instance.getDefinition().getId())
                        .replace("%target%", target != null ? target.getName() : "")
                        .replace("%x%", String.format(Locale.ROOT, "%.1f", mob.getLocation().getX()))
                        .replace("%y%", String.format(Locale.ROOT, "%.1f", mob.getLocation().getY()))
                        .replace("%z%", String.format(Locale.ROOT, "%.1f", mob.getLocation().getZ()));

                if (cmd.startsWith("console:")) {
                    cmd = cmd.substring("console:".length()).trim();
                }
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }
        }
    }

    private boolean isValidVictim(Mob attacker, LivingEntity target) {
        if (target instanceof Player p) {
            return p.getGameMode() != org.bukkit.GameMode.CREATIVE && p.getGameMode() != org.bukkit.GameMode.SPECTATOR;
        }
        return true;
    }

    private void playHitSound(Location loc) {
        if (loc.getWorld() != null) {
            loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 0.9f);
        }
    }

    private double parseDouble(String str, double def) {
        if (str == null || str.trim().isEmpty()) return def;
        try {
            return Double.parseDouble(str.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private int parseInt(String str, int def) {
        if (str == null || str.trim().isEmpty()) return def;
        try {
            return Integer.parseInt(str.trim());
        } catch (Exception e) {
            return def;
        }
    }

    public String serialize() {
        return type.name().toLowerCase(Locale.ROOT) + (parameter.isEmpty() ? "" : ": " + parameter);
    }

    public static EntityBehaviorAction deserialize(String raw) {
        if (raw == null || raw.trim().isEmpty()) return new EntityBehaviorAction();
        String str = raw.trim();
        int colon = str.indexOf(':');
        if (colon < 0) {
            try {
                EntityBehaviorActionType t = EntityBehaviorActionType.valueOf(str.toUpperCase(Locale.ROOT));
                return new EntityBehaviorAction(t, "");
            } catch (Exception e) {
                return new EntityBehaviorAction(EntityBehaviorActionType.MELEE_DAMAGE, str);
            }
        }

        String typeStr = str.substring(0, colon).trim().toUpperCase(Locale.ROOT);
        String param = str.substring(colon + 1).trim();
        EntityBehaviorActionType type;
        try {
            type = EntityBehaviorActionType.valueOf(typeStr);
        } catch (Exception e) {
            type = EntityBehaviorActionType.MELEE_DAMAGE;
        }
        return new EntityBehaviorAction(type, param);
    }
}

