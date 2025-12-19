package nc.randomEvents.events.tests;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.core.BaseEvent;
import nc.randomEvents.services.participants.EntityManager;
import nc.randomEvents.utils.EntityHelper;
import net.kyori.adventure.text.Component;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.*;
import org.bukkit.util.Vector;
import java.util.*;

public class EntityManagerTest extends BaseEvent {
    private final EntityManager entityManager;
    private final Map<UUID, TestPhase> playerPhases = new HashMap<>();
    private final Map<UUID, List<Entity>> activeEntities = new HashMap<>();
    private int tickCount = 0;

    private enum TestPhase {
        ORBIT,          // Orbiting zombies demonstration
        RING,           // Entity ring demonstration
        HOVER,          // Hovering entities demonstration
        LAUNCH,         // Entity launching demonstration
        COMPLETE        // Test complete
    }

    public EntityManagerTest(RandomEvents plugin) {
        this.entityManager = plugin.getEntityManager();
        
        // Configure event timing
        setTickInterval(1L); // Tick every 5 ticks (0.25 seconds) for smoother animations
        setDuration(1000L);   // Run for 30 seconds
    }
    
    @Override
    public void onStart(UUID sessionId, Set<Player> players) {
        players.forEach(player -> {
            player.sendMessage(Component.text("Starting EntityManager creative demonstrations!"));
            playerPhases.put(player.getUniqueId(), TestPhase.ORBIT);
            activeEntities.put(player.getUniqueId(), new ArrayList<>());
            
            // Initial orbit demonstration
            setupOrbitDemo(player, sessionId);
        });
    }
    
    @Override
    public void onTick(UUID sessionId, Set<Player> players) {
        tickCount++;
        
        players.forEach(player -> {
            UUID playerId = player.getUniqueId();
            TestPhase phase = playerPhases.get(playerId);
            List<Entity> entities = activeEntities.get(playerId);
            
            // Update current phase effects
            switch (phase) {
                case ORBIT:
                    if (entities != null) {
                        Location center = player.getLocation();
                        entities.forEach(entity -> {
                            EntityHelper.orbitAround(entity, center, 5.0, 0.2);
                            EntityHelper.createParticleTrail(entity, Particle.FLAME, 1, 0, 0, 0, 0);
                        });
                    }
                    
                    // After 5 seconds, move to next phase
                    if (tickCount == 100) {
                        cleanupEntities(player);
                        setupRingDemo(player, sessionId);
                        playerPhases.put(playerId, TestPhase.RING);
                    }
                    break;
                    
                case RING:
                    if (entities != null) {
                        entities.forEach(entity -> {
                            EntityHelper.smoothLookAt(entity, player, 0.1);
                            EntityHelper.createParticleTrail(entity, Particle.SOUL_FIRE_FLAME, 1, 0, 0, 0, 0);
                        });
                    }
                    
                    // After 5 more seconds, move to next phase
                    if (tickCount == 200) {
                        cleanupEntities(player);
                        setupHoverDemo(player, sessionId);
                        playerPhases.put(playerId, TestPhase.HOVER);
                    }
                    break;
                    
                case HOVER:
                    if (entities != null) {
                        entities.forEach(entity -> {
                            EntityHelper.hover(entity, 3.0, 0.1);
                            EntityHelper.createParticleTrail(entity, Particle.CLOUD, 1, 0.2, 0.2, 0.2, 0);
                        });
                    }
                    
                    // After 5 more seconds, move to next phase
                    if (tickCount == 300) {
                        cleanupEntities(player);
                        setupLaunchDemo(player, sessionId);
                        playerPhases.put(playerId, TestPhase.LAUNCH);
                    }
                    break;
                    
                case LAUNCH:
                    if (tickCount % 20 == 0 && entities != null && !entities.isEmpty()) { // Launch one every second
                        Entity entity = entities.get(0);
                        Vector direction = player.getLocation().getDirection().multiply(-1); // Away from player
                        EntityHelper.launchEntity(entity, direction, 1.5);
                        entities.remove(0);
                    }
                    
                    // After all launches complete
                    if (entities != null && entities.isEmpty()) {
                        playerPhases.put(playerId, TestPhase.COMPLETE);
                        player.sendMessage(Component.text("EntityManager demonstrations complete!"));
                    }
                    break;
                case COMPLETE:
                    break;
                                    
            }
        });
    }
    
    @Override
    public void onEnd(UUID sessionId, Set<Player> players) {
        players.forEach(player -> {
            cleanupEntities(player);
            player.sendMessage(Component.text("EntityManager test complete!"));
        });
        playerPhases.clear();
        activeEntities.clear();
    }
    
    private void cleanupEntities(Player player) {
        List<Entity> entities = activeEntities.get(player.getUniqueId());
        if (entities != null) {
            entities.forEach(Entity::remove);
            entities.clear();
        }
    }
    
    private void setupOrbitDemo(Player player, UUID sessionId) {
        Location center = player.getLocation();
        List<Entity> entities = activeEntities.get(player.getUniqueId());
        
        // Spawn 4 zombies to orbit the player
        for (int i = 0; i < 4; i++) {
            Zombie zombie = (Zombie)entityManager.spawnTracked(EntityType.ZOMBIE, center.clone().add(5, 0, 0), "orbit_zombie_" + i, sessionId);
            zombie.customName(Component.text("Orbiting Zombie " + (i + 1)));
            zombie.setCustomNameVisible(true);
            EntityHelper.setMaxHealth(zombie, 20.0);
            EntityHelper.setMovementSpeed(zombie, 0.0); // Prevent normal movement
            entities.add(zombie);
        }
        
        player.sendMessage(Component.text("Demonstrating orbiting entities..."));
    }
    
    private void setupRingDemo(Player player, UUID sessionId) {
        Location center = player.getLocation().add(0, 0.5, 0);
        List<Entity> entities = activeEntities.get(player.getUniqueId());
        
        // Create a ring of skeletons
        Entity[] ringEntities = EntityHelper.createEntityRing(
            center.getWorld(), center, EntityType.SKELETON, 6, 4.0, 0
        );
        
        for (int i = 0; i < ringEntities.length; i++) {
            Location loc = ringEntities[i].getLocation();
            // Remove helper-spawned entity and replace with a tracked one
            ringEntities[i].remove();

            Skeleton skeleton = (Skeleton)entityManager.spawnTracked(
                    EntityType.SKELETON, loc, "ring_skeleton_" + i, sessionId);

            skeleton.customName(Component.text("Ring Skeleton"));
            skeleton.setCustomNameVisible(true);
            EntityHelper.setMaxHealth(skeleton, 20.0);
            EntityHelper.setMovementSpeed(skeleton, 0.0);
            entities.add(skeleton);
        }
        
        player.sendMessage(Component.text("Demonstrating entity ring formation..."));
    }
    
    private void setupHoverDemo(Player player, UUID sessionId) {
        Location center = player.getLocation();
        List<Entity> entities = activeEntities.get(player.getUniqueId());
        
        // Spawn some bats that will hover
        for (int i = 0; i < 5; i++) {
            Location spawnLoc = center.clone().add(
                Math.random() * 6 - 3,
                1,
                Math.random() * 6 - 3
            );
            
            Bat bat = (Bat)entityManager.spawnTracked(EntityType.BAT, spawnLoc, "hover_bat_" + i, sessionId);
            bat.customName(Component.text("Hovering Bat " + (i + 1)));
            bat.setCustomNameVisible(true);
            bat.setAwake(true);
            entities.add(bat);
        }
        
        player.sendMessage(Component.text("Demonstrating hovering entities..."));
    }
    
    private void setupLaunchDemo(Player player, UUID sessionId) {
        Location center = player.getLocation();
        List<Entity> entities = activeEntities.get(player.getUniqueId());
        
        // Spawn some slimes to launch
        for (int i = 0; i < 5; i++) {
            Location spawnLoc = center.clone().add(
                Math.random() * 4 - 2,
                0,
                Math.random() * 4 - 2
            );
            
            Slime slime = (Slime)entityManager.spawnTracked(EntityType.SLIME, spawnLoc, "launch_slime_" + i, sessionId);
            slime.customName(Component.text("Launch Slime " + (i + 1)));
            slime.setCustomNameVisible(true);
            slime.setSize(1);
            entities.add(slime);
        }
        
        player.sendMessage(Component.text("Demonstrating entity launching..."));
    }
    
    @Override
    public String getName() {
        return "EntityManagerTest";
    }
    
    @Override
    public String getDescription() {
        return "A test event demonstrating creative entity management features";
    }
} 