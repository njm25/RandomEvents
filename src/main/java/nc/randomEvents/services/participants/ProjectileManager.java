package nc.randomEvents.services.participants;

import nc.randomEvents.RandomEvents;    
import nc.randomEvents.core.SessionParticipant;
import nc.randomEvents.listeners.ProjectileListener;
import nc.randomEvents.services.SessionRegistry;
import nc.randomEvents.utils.PersistentDataHelper;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.*;

interface IProjectileManager {
    <T extends Projectile> T spawnTracked
    (
        Class<T> type, 
        Location location, 
        Vector direction, 
        ProjectileSource shooter, 
        UUID sessionId, 
        Double damage, 
        double speed
    );
}

public class ProjectileManager implements SessionParticipant, IProjectileManager {
    private final RandomEvents plugin;
    private final SessionRegistry sessionRegistry;
    private static final String PROJECTILE_KEY = "event_projectile";
    private static final String PROJECTILE_DAMAGE_KEY = "projectile_damage";
    private static final String PROJECTILE_SESSION_KEY = "projectile_session";
    private final Map<UUID, Set<UUID>> sessionProjectiles = new HashMap<>();
    private ProjectileListener projectileListener;
    public ProjectileManager(RandomEvents plugin) {
        this.plugin = plugin;
        this.sessionRegistry = plugin.getSessionRegistry();
        plugin.getSessionRegistry().registerParticipant(this);
        
        projectileListener = new ProjectileListener(plugin);
        projectileListener.registerListener(plugin);
        plugin.getLogger().info("ProjectileManager initialized");
    }

    /**
     * Spawns and tracks a projectile for an event
     * @param type The type of projectile to spawn
     * @param location The location to spawn at
     * @param direction The direction vector
     * @param shooter The entity shooting the projectile
     * @param sessionId The event session this projectile belongs to
     * @param damage The custom damage amount (if null, uses default damage)
     * @param speed The projectile speed multiplier
     * @return The spawned projectile
     */
    public <T extends Projectile> T spawnTracked(Class<T> type, Location location, Vector direction, 
                                               ProjectileSource shooter, UUID sessionId, Double damage, double speed) {
        T projectile = location.getWorld().spawn(location, type);
        if (projectile != null) {
            projectile.setShooter(shooter);
            projectile.setVelocity(direction.multiply(speed));

            // Add persistent data
            PersistentDataHelper.set(projectile.getPersistentDataContainer(), plugin, PROJECTILE_KEY, 
                                   PersistentDataType.BYTE, (byte) 1);
            PersistentDataHelper.set(projectile.getPersistentDataContainer(), plugin, PROJECTILE_SESSION_KEY, 
                                   PersistentDataType.STRING, sessionId.toString());
            
            // Set custom damage if specified
            if (damage != null) {
                PersistentDataHelper.set(projectile.getPersistentDataContainer(), plugin, PROJECTILE_DAMAGE_KEY, 
                                       PersistentDataType.DOUBLE, damage);
            }

            // Track the projectile
            sessionProjectiles.computeIfAbsent(sessionId, k -> new HashSet<>()).add(projectile.getUniqueId());
        }
        return projectile;
    }

    @Override
    public void onSessionStart(UUID sessionId) {
        plugin.getLogger().info("ProjectileManager tracking new session: " + sessionId);
        sessionProjectiles.put(sessionId, new HashSet<>());
    }

    @Override
    public void onSessionEnd(UUID sessionId) {
        plugin.getLogger().info("ProjectileManager cleaning up session: " + sessionId);
        cleanupSession(sessionId, false);
    }

    @Override
    public void cleanupSession(UUID sessionId, boolean force) {
        if (!force) {
            boolean clearProjectilesAtEnd = sessionRegistry.getSession(sessionId).getEvent().clearProjectilesAtEnd();
            if (!clearProjectilesAtEnd) {
                return;
            }
        }
        Set<UUID> projectiles = sessionProjectiles.get(sessionId);
        if (projectiles != null) {
            for (World world : plugin.getServer().getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    // Check both UUID tracking and persistent data
                    if (projectiles.contains(entity.getUniqueId()) || isSessionProjectile(entity, sessionId)) {
                        entity.remove();
                    }
                }
            }
        }
        sessionProjectiles.remove(sessionId);
    }

    private boolean isSessionProjectile(Entity entity, UUID sessionId) {
        if (!(entity instanceof Projectile)) return false;
        
        // First check if it's a tracked projectile at all
        if (!PersistentDataHelper.has(entity.getPersistentDataContainer(), plugin, PROJECTILE_KEY, PersistentDataType.BYTE)) {
            return false;
        }
        
        // Then check if it belongs to this session
        String storedSessionId = PersistentDataHelper.get(entity.getPersistentDataContainer(), plugin, 
                                                        PROJECTILE_SESSION_KEY, PersistentDataType.STRING);
        return storedSessionId != null && storedSessionId.equals(sessionId.toString());
    }

} 