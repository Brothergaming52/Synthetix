package org.bg52.synthetix.ability;

import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/**
 * Configuration for continuous ambient particle auras emitted around an item's bearer.
 */
public class AuraSpec {

    public enum Shape {
        SPIRAL,
        HALO,
        FOOTSTEPS,
        ORBITING_SPHERES,
        WINGS,
        TRAIL,
        BURST
    }

    private boolean enabled;
    private Shape shape;
    private String particle;
    private String color; // Hex string for Colorable particles like DUST
    private double speed;
    private int count;
    private double radius;
    private String condition;

    public AuraSpec() {
        this.enabled = false;
        this.shape = Shape.SPIRAL;
        this.particle = "FLAME";
        this.color = null;
        this.speed = 0.05;
        this.count = 2;
        this.radius = 0.8;
        this.condition = "while-held-or-worn";
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Shape getShape() {
        return shape;
    }

    public void setShape(Shape shape) {
        this.shape = shape != null ? shape : Shape.SPIRAL;
    }

    public String getParticle() {
        return particle;
    }

    public void setParticle(String particle) {
        this.particle = particle != null ? particle.toUpperCase(Locale.ROOT) : "FLAME";
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = Math.max(0.0, speed);
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = Math.max(1, count);
    }

    public double getRadius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = Math.max(0.1, radius);
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition != null ? condition.toLowerCase(Locale.ROOT) : "while-holding-mainhand";
    }

    public Particle resolveParticle() {
        if (particle == null || particle.isEmpty()) return Particle.FLAME;
        String pName = particle.toUpperCase(Locale.ROOT).trim();
        // Cross-version Bukkit particle mappings
        pName = switch (pName) {
            case "ENCHANTMENT_TABLE" -> "ENCHANT";
            case "EXPLOSION_NORMAL" -> "POOF";
            case "FIREWORKS_SPARK" -> "FIREWORK";
            case "TOTEM" -> "TOTEM_OF_UNDYING";
            case "REDSTONE" -> "DUST";
            case "SMOKE_NORMAL" -> "SMOKE";
            case "PORTAL_REVERSE" -> "REVERSE_PORTAL";
            default -> pName;
        };
        try {
            return Particle.valueOf(pName);
        } catch (Exception ignored) {
            try {
                return Particle.valueOf(particle.toUpperCase(Locale.ROOT).trim());
            } catch (Exception e) {
                return Particle.FLAME;
            }
        }
    }

    public void saveToConfig(ConfigurationSection sec) {
        sec.set("enabled", enabled);
        sec.set("shape", shape.name());
        sec.set("particle", particle);
        if (color != null) sec.set("color", color);
        sec.set("speed", speed);
        sec.set("count", count);
        sec.set("radius", radius);
        sec.set("condition", condition);
    }

    public static AuraSpec loadFromConfig(ConfigurationSection sec) {
        if (sec == null) return null;
        AuraSpec spec = new AuraSpec();
        spec.setEnabled(sec.getBoolean("enabled", true));
        String shapeStr = sec.getString("shape", "SPIRAL");
        try {
            spec.setShape(Shape.valueOf(shapeStr.toUpperCase(Locale.ROOT)));
        } catch (Exception ignored) {
            spec.setShape(Shape.SPIRAL);
        }
        spec.setParticle(sec.getString("particle", "FLAME"));
        spec.setColor(sec.getString("color"));
        spec.setSpeed(sec.getDouble("speed", 0.05));
        spec.setCount(sec.getInt("count", 2));
        spec.setRadius(sec.getDouble("radius", 0.8));
        spec.setCondition(sec.getString("condition", "while-held-or-worn"));
        return spec;
    }

    public AuraSpec copy() {
        AuraSpec copy = new AuraSpec();
        copy.setEnabled(this.enabled);
        copy.setShape(this.shape);
        copy.setParticle(this.particle);
        copy.setColor(this.color);
        copy.setSpeed(this.speed);
        copy.setCount(this.count);
        copy.setRadius(this.radius);
        copy.setCondition(this.condition);
        return copy;
    }
}

