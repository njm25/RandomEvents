package nc.randomEvents.utils;

import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import net.kyori.adventure.text.Component;

/**
 * Utility class for manipulating entities and their attributes
 */
public class EntityHelper {

    /**
     * Sets the maximum health of a living entity
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
     * Sets the movement speed of a living entity
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
     * Sets a custom name for an entity
     * @param entity The entity to modify
     * @param name The name to set
     */
    public static void setCustomName(Entity entity, String name) {
        entity.customName(Component.text(name));
        entity.setCustomNameVisible(true);
    }

    /**
     * Sets whether an entity is glowing
     * @param entity The entity to modify
     * @param glowing Whether the entity should glow
     */
    public static void setGlowing(Entity entity, boolean glowing) {
        entity.setGlowing(glowing);
    }

    /**
     * Prevents an entity from picking up items
     * @param entity The entity to modify
     */
    public static void preventItemPickup(LivingEntity entity) {
        entity.setCanPickupItems(false);
    }

    /**
     * Applies a potion effect to a living entity
     * @param entity The entity to modify
     * @param type The type of potion effect
     * @param durationTicks Duration in ticks
     * @param amplifier Effect amplifier (level - 1)
     * @param ambient Whether particles should be ambient
     * @param particles Whether to show particles
     */
    public static void applyEffect(LivingEntity entity, PotionEffectType type, int durationTicks, 
                                 int amplifier, boolean ambient, boolean particles) {
        entity.addPotionEffect(new PotionEffect(type, durationTicks, amplifier, ambient, particles));
    }

    /**
     * Removes a specific potion effect from a living entity
     * @param entity The entity to modify
     * @param type The type of potion effect to remove
     */
    public static void removeEffect(LivingEntity entity, PotionEffectType type) {
        entity.removePotionEffect(type);
    }

    /**
     * Removes all potion effects from a living entity
     * @param entity The entity to modify
     */
    public static void clearEffects(LivingEntity entity) {
        entity.getActivePotionEffects().forEach(effect -> entity.removePotionEffect(effect.getType()));
    }

    /**
     * Sets whether an entity is invulnerable
     * @param entity The entity to modify
     * @param invulnerable Whether the entity should be invulnerable
     */
    public static void setInvulnerable(Entity entity, boolean invulnerable) {
        entity.setInvulnerable(invulnerable);
    }

    /**
     * Sets whether an entity has AI
     * @param entity The entity to modify
     * @param hasAI Whether the entity should have AI
     */
    public static void setAI(LivingEntity entity, boolean hasAI) {
        entity.setAI(hasAI);
    }

    /**
     * Sets whether an entity is silent
     * @param entity The entity to modify
     * @param silent Whether the entity should be silent
     */
    public static void setSilent(Entity entity, boolean silent) {
        entity.setSilent(silent);
    }
} 