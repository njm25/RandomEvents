package nc.randomEvents.services;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.utils.PersistentDataHelper;
import nc.randomEvents.utils.AttributeHelper;
import nc.randomEvents.core.SessionParticipant;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.attribute.AttributeInstance;
import net.kyori.adventure.text.Component;

import java.util.*;

public class EntityManager implements Listener, SessionParticipant {
    private final RandomEvents plugin;
    private static final String ENTITY_KEY = "entity";
    private static final String ENTITY_ID_KEY = "entity_id";
    private static final String ENTITY_SESSION_KEY = "entity_session";
    private final SessionRegistry sessionRegistry;
    private final Map<UUID, Set<UUID>> sessionEntities = new HashMap<>();

    public EntityManager(RandomEvents plugin) {
        this.plugin = plugin;
        this.sessionRegistry = plugin.getSessionRegistry();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getSessionRegistry().registerParticipant(this);
        plugin.getLogger().info("EntityManager initialized");
    }

    /**
     * Spawns and tracks an entity for a session
     * @param type The type of entity to spawn
     * @param location The location to spawn at
     * @param entityId Unique identifier for this entity
     * @param sessionId The event session this entity belongs to
     * @param <T> The specific entity type to spawn, must extend Entity
     * @return The spawned entity, or null if spawn failed
     */
    @SuppressWarnings("unchecked") // Safe cast due to EntityType matching T
    public <T extends Entity> T spawnTracked(EntityType type, Location location, String entityId, UUID sessionId) {
        Entity entity = location.getWorld().spawnEntity(location, type);
        if (entity != null) {
            // Add persistent data
            PersistentDataHelper.set(entity.getPersistentDataContainer(), plugin, ENTITY_KEY, 
                                   PersistentDataType.BYTE, (byte) 1);
            PersistentDataHelper.set(entity.getPersistentDataContainer(), plugin, ENTITY_ID_KEY, 
                                   PersistentDataType.STRING, entityId);
            PersistentDataHelper.set(entity.getPersistentDataContainer(), plugin, ENTITY_SESSION_KEY, 
                                   PersistentDataType.STRING, sessionId.toString());

            // Track the entity
            sessionEntities.computeIfAbsent(sessionId, k -> new HashSet<>()).add(entity.getUniqueId());

            // Since EntityType.getEntityClass() returns the correct class for the entity type,
            // and we spawn the entity using that same type, this cast is safe
            return (T) entity;
        }
        return null;
    }

    /**
     * Fluent helper to set common attributes
     */
    public EntityManager setMaxHealth(LivingEntity entity, double health) {
        AttributeInstance attribute = entity.getAttribute(AttributeHelper.getAttributeSafely("MAX_HEALTH"));
        if (attribute != null) {
            attribute.setBaseValue(health);
            entity.setHealth(health);
        }
        return this;
    }

    public EntityManager setMovementSpeed(LivingEntity entity, double speed) {
        AttributeInstance attribute = entity.getAttribute(AttributeHelper.getAttributeSafely("MOVEMENT_SPEED"));
        if (attribute != null) {
            attribute.setBaseValue(speed);
        }
        return this;
    }

    public EntityManager setCustomName(Entity entity, String name) {
        entity.customName(Component.text(name));
        entity.setCustomNameVisible(true);
        return this;
    }

    public EntityManager setGlowing(Entity entity, boolean glowing) {
        entity.setGlowing(glowing);
        return this;
    }

    public EntityManager preventItemDrops(LivingEntity entity) {
        entity.setCanPickupItems(false);
        return this;
    }

    @Override
    public void onSessionStart(UUID sessionId) {
        plugin.getLogger().info("EntityManager tracking new session: " + sessionId);
        sessionEntities.put(sessionId, new HashSet<>());
    }

    @Override
    public void onSessionEnd(UUID sessionId) {
        plugin.getLogger().info("EntityManager cleaning up session: " + sessionId);
        cleanupSession(sessionId);
    }

    /**
     * Checks if an entity is a session entity
     * @param entity The entity to check
     * @return true if the entity is a session entity
     */
    private boolean isSessionEntity(Entity entity) {
        if (entity == null) return false;
        return PersistentDataHelper.has(entity.getPersistentDataContainer(), plugin, ENTITY_KEY, PersistentDataType.BYTE);
    }

    /**
     * Gets the session ID for a session entity
     * @param entity The entity to check
     * @return The session ID, or null if not a session entity
     */
    private UUID getEntitySessionId(Entity entity) {
        if (entity == null) return null;

        String sessionIdStr = PersistentDataHelper.get(
            entity.getPersistentDataContainer(),
            plugin,
            ENTITY_SESSION_KEY,
            PersistentDataType.STRING
        );
        
        if (sessionIdStr != null) {
            try {
                return UUID.fromString(sessionIdStr);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid session ID format in entity: " + sessionIdStr);
                return null;
            }
        }
        return null;
    }

    /**
     * Checks if an entity belongs to a specific session
     * @param entity The entity to check
     * @param sessionId The session ID to check against
     * @return true if the entity belongs to the session
     */
    private boolean isSessionEntity(Entity entity, UUID sessionId) {
        if (entity == null) return false;
        return PersistentDataHelper.has(entity.getPersistentDataContainer(), plugin, ENTITY_KEY, PersistentDataType.BYTE) &&
               sessionId.toString().equals(PersistentDataHelper.get(entity.getPersistentDataContainer(), plugin, ENTITY_SESSION_KEY, 
                                      PersistentDataType.STRING));
    }

    /**
     * Cleans up all entities for a specific session
     * @param sessionId The session ID to clean up
     */
    private void cleanupSession(UUID sessionId) {
        Set<UUID> entities = sessionEntities.remove(sessionId);
        if (entities != null) {
            for (World world : plugin.getServer().getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (entities.contains(entity.getUniqueId()) || isSessionEntity(entity, sessionId)) {
                        entity.remove();
                    }
                }
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (isSessionEntity(entity)) {
            UUID sessionId = getEntitySessionId(entity);
            if (sessionId != null) {
                Set<UUID> entities = sessionEntities.get(sessionId);
                if (entities != null) {
                    entities.remove(entity.getUniqueId());
                }
                // Clear drops if session is no longer active
                if (!sessionRegistry.isActive(sessionId)) {
                    event.getDrops().clear();
                    event.setDroppedExp(0);
                }
            }
        }
    }

    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        // Monitor entity spawns for logging/debugging if needed
        Entity entity = event.getEntity();
        if (isSessionEntity(entity)) {
            UUID sessionId = getEntitySessionId(entity);
            if (sessionId != null && !sessionRegistry.isActive(sessionId)) {
                event.setCancelled(true);
            }
        }
    }
} 