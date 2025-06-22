package nc.randomEvents.services.participants;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.core.EventSession;
import nc.randomEvents.core.SessionParticipant;
import nc.randomEvents.listeners.EntityListener;
import nc.randomEvents.services.SessionRegistry;
import nc.randomEvents.utils.PersistentDataHelper;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

interface IEntityManager {
    <T extends Entity> T spawnTracked(EntityType type, Location location, String entityId, UUID sessionId);
}

public class EntityManager implements SessionParticipant, IEntityManager {
    private final RandomEvents plugin;
    private static final String ENTITY_KEY = "entity";
    private static final String ENTITY_ID_KEY = "entity_id";
    private static final String ENTITY_SESSION_KEY = "entity_session";
    private final SessionRegistry sessionRegistry;
    private final Map<UUID, Set<UUID>> sessionEntities = new HashMap<>();
    private EntityListener entityListener;
    public EntityManager(RandomEvents plugin) {
        this.plugin = plugin;
        this.sessionRegistry = plugin.getSessionRegistry();
        plugin.getSessionRegistry().registerParticipant(this);
        entityListener = new EntityListener(plugin);
        entityListener.registerListener(plugin);
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

    @Override
    public void onSessionStart(UUID sessionId) {
        plugin.getLogger().info("EntityManager tracking new session: " + sessionId);
        sessionEntities.put(sessionId, new HashSet<>());
    }

    @Override
    public void onSessionEnd(UUID sessionId) {
        plugin.getLogger().info("EntityManager cleaning up session: " + sessionId);
        cleanupSession(sessionId, false);
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
    @Override
    public void cleanupSession(UUID sessionId, boolean force) {

        EventSession session = sessionRegistry.getSession(sessionId);
        // Only clean up entities if the session exists and the event wants them cleaned up
        if (!force) {
            boolean clearEntitiesAtEnd = session != null && session.getEvent().getClearEntitiesAtEnd();
            if (!clearEntitiesAtEnd) {
                return;
            }
        }

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


    public Map<UUID, Set<UUID>> getSessionEntities() {
        return sessionEntities;
    }
} 