package nc.randomEvents.utils;

import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Particle;

/**
 * Utility class for manipulating entities and their attributes
 */
public class EntityHelper {

    /**
     * Sets the maximum health of a living entity safely using AttributeHelper
     * @param entity The entity to modify
     * @param health The new max health value
     */
    public static void setMaxHealth(LivingEntity entity, double health) {
        AttributeInstance attribute = entity.getAttribute(AttributeHelper.getAttributeSafely("MAX_HEALTH"));
        if (attribute != null) {
            attribute.setBaseValue(health);
            entity.setHealth(health);
        }
    }

    /**
     * Sets the movement speed of a living entity safely using AttributeHelper
     * @param entity The entity to modify
     * @param speed The new movement speed value
     */
    public static void setMovementSpeed(LivingEntity entity, double speed) {
        AttributeInstance attribute = entity.getAttribute(AttributeHelper.getAttributeSafely("MOVEMENT_SPEED"));
        if (attribute != null) {
            attribute.setBaseValue(speed);
        }
    }

    /**
     * Sets the attack damage of a living entity safely using AttributeHelper
     * @param entity The entity to modify
     * @param damage The new attack damage value
     */
    public static void setAttackDamage(LivingEntity entity, double damage) {
        AttributeInstance attribute = entity.getAttribute(AttributeHelper.getAttributeSafely("GENERIC_ATTACK_DAMAGE"));
        if (attribute != null) {
            attribute.setBaseValue(damage);
        }
    }

    /**
     * Sets the knockback resistance of a living entity safely using AttributeHelper
     * @param entity The entity to modify
     * @param resistance The new knockback resistance value (0-1)
     */
    public static void setKnockbackResistance(LivingEntity entity, double resistance) {
        AttributeInstance attribute = entity.getAttribute(AttributeHelper.getAttributeSafely("GENERIC_KNOCKBACK_RESISTANCE"));
        if (attribute != null) {
            attribute.setBaseValue(Math.min(1.0, Math.max(0.0, resistance)));
        }
    }

    /**
     * Launches an entity in a direction with specified power
     * @param entity The entity to launch
     * @param direction The direction vector (will be normalized)
     * @param power The power of the launch
     */
    public static void launchEntity(Entity entity, Vector direction, double power) {
        Vector normalizedDir = direction.normalize();
        entity.setVelocity(normalizedDir.multiply(power));
    }

    /**
     * Makes an entity orbit around a location
     * @param entity The entity to orbit
     * @param center The center location to orbit around
     * @param radius The radius of the orbit
     * @param speed The speed of the orbit (radians per tick)
     */
    public static void orbitAround(Entity entity, Location center, double radius, double speed) {
        Location current = entity.getLocation();
        double angle = Math.atan2(current.getZ() - center.getZ(), current.getX() - center.getX());
        angle += speed;
        
        double nextX = center.getX() + (radius * Math.cos(angle));
        double nextZ = center.getZ() + (radius * Math.sin(angle));
        Location next = new Location(center.getWorld(), nextX, current.getY(), nextZ);
        next.setDirection(next.toVector().subtract(current.toVector()));
        
        entity.teleport(next);
    }

    /**
     * Creates a particle trail that follows an entity
     * @param entity The entity to trail
     * @param particle The particle type to display
     * @param count Number of particles
     * @param offsetX X offset for particle spread
     * @param offsetY Y offset for particle spread
     * @param offsetZ Z offset for particle spread
     * @param speed Particle speed
     */
    public static void createParticleTrail(Entity entity, Particle particle, int count, 
                                         double offsetX, double offsetY, double offsetZ, double speed) {
        Location loc = entity.getLocation();
        loc.getWorld().spawnParticle(particle, loc, count, offsetX, offsetY, offsetZ, speed);
    }

    /**
     * Makes an entity face another entity smoothly
     * @param entity The entity that will face the target
     * @param target The target entity to face
     * @param maxRotationPerTick Maximum rotation per tick in radians
     */
    public static void smoothLookAt(Entity entity, Entity target, double maxRotationPerTick) {
        Location current = entity.getLocation();
        Vector toTarget = target.getLocation().toVector().subtract(current.toVector());
        
        double targetYaw = Math.toDegrees(Math.atan2(-toTarget.getX(), toTarget.getZ()));
        double targetPitch = Math.toDegrees(Math.asin(-toTarget.getY() / toTarget.length()));
        
        double currentYaw = current.getYaw();
        double currentPitch = current.getPitch();
        
        // Calculate shortest angle difference
        double yawDiff = ((targetYaw - currentYaw + 180) % 360) - 180;
        double pitchDiff = targetPitch - currentPitch;
        
        // Limit rotation
        double maxDegPerTick = Math.toDegrees(maxRotationPerTick);
        yawDiff = Math.max(-maxDegPerTick, Math.min(maxDegPerTick, yawDiff));
        pitchDiff = Math.max(-maxDegPerTick, Math.min(maxDegPerTick, pitchDiff));
        
        current.setYaw((float)(currentYaw + yawDiff));
        current.setPitch((float)(currentPitch + pitchDiff));
        entity.teleport(current);
    }

    /**
     * Makes an entity hover at a specific height
     * @param entity The entity to make hover
     * @param targetHeight The desired height above ground
     * @param strength How strongly to maintain the height (0-1)
     */
    public static void hover(Entity entity, double targetHeight, double strength) {
        Location loc = entity.getLocation();
        World world = loc.getWorld();
        
        // Find ground level
        int groundY = world.getHighestBlockYAt(loc);
        double currentHeight = loc.getY() - groundY;
        
        // Calculate required velocity
        double heightDiff = targetHeight - currentHeight;
        Vector velocity = entity.getVelocity();
        velocity.setY(heightDiff * strength);
        
        entity.setVelocity(velocity);
    }

    /**
     * Creates a ring of entities around a center point
     * @param world The world to spawn in
     * @param center The center location
     * @param entityType The type of entity to spawn
     * @param count Number of entities in the ring
     * @param radius Radius of the ring
     * @param yOffset Y offset from center
     * @return Array of spawned entities
     */
    public static Entity[] createEntityRing(World world, Location center, 
                                          org.bukkit.entity.EntityType entityType,
                                          int count, double radius, double yOffset) {
        Entity[] entities = new Entity[count];
        double angleStep = 2 * Math.PI / count;
        
        for (int i = 0; i < count; i++) {
            double angle = angleStep * i;
            double x = center.getX() + (radius * Math.cos(angle));
            double z = center.getZ() + (radius * Math.sin(angle));
            Location spawnLoc = new Location(world, x, center.getY() + yOffset, z);
            spawnLoc.setDirection(spawnLoc.toVector().subtract(center.toVector()));
            
            entities[i] = world.spawnEntity(spawnLoc, entityType);
        }
        
        return entities;
    }

} 