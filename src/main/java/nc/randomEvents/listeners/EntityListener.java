package nc.randomEvents.listeners;

import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.persistence.PersistentDataType;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.core.ServiceListener;
import nc.randomEvents.services.SessionRegistry;
import nc.randomEvents.utils.PersistentDataHelper;

public class EntityListener implements ServiceListener {
    private final SessionRegistry sessionRegistry;
    private final RandomEvents plugin;
    private static final String ENTITY_KEY = "entity";
    private static final String ENTITY_SESSION_KEY = "entity_session";

    public EntityListener(RandomEvents plugin) {
        this.sessionRegistry = plugin.getSessionRegistry();
        this.plugin = plugin;
    }

    @Override
    public void registerListener(RandomEvents plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (isSessionEntity(entity)) {
            UUID sessionId = getEntitySessionId(entity);
            if (sessionId != null) {
                Set<UUID> entities = plugin.getEntityManager().getSessionEntities().get(sessionId);
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
}
