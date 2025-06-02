package nc.randomEvents.events.Test.EntityManagerTest;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.core.BaseEvent;
import nc.randomEvents.services.EntityManager;

import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.attribute.Attribute;

import java.util.*;

public class EntityManagerTest extends BaseEvent {
    private final EntityManager entityManager;
    private final Set<Entity> testEntities = new HashSet<>();

    public EntityManagerTest(RandomEvents plugin) {
        this.entityManager = plugin.getEntityManager();
        
        // Configure event timing
        setTickInterval(20L); // Tick every second
        setDuration(800L); // Run for 20 seconds
    }
    
    @Override
    public void onStart(UUID sessionId, Set<Player> players) {
        players.forEach(player -> {
            player.sendMessage("EntityManagerTest has started!");
            
            // Test different entity scenarios
            testEntityScenarios(player, sessionId);
        });
    }
    
    @Override
    public void onTick(UUID sessionId, Set<Player> players) {
        players.forEach(player -> {
            player.sendMessage("EntityManagerTest is ticking!");
        });
    }
    
    @Override
    public void onEnd(UUID sessionId, Set<Player> players) {
        players.forEach(player -> {
            player.sendMessage("EntityManagerTest has ended!");
        });
    }
    
    @Override
    public String getName() {
        return "EntityManagerTest";
    }
    
    @Override
    public String getDescription() {
        return "A test event for entity management functionality";
    }

    private void testEntityScenarios(Player player, UUID sessionId) {
        Location loc = player.getLocation();

        // Scenario 1: Spawn a zombie with custom attributes
        Zombie zombie = entityManager.spawnTracked(EntityType.ZOMBIE, loc, "test_zombie", sessionId);
        entityManager
            .setMaxHealth(zombie, 40.0)
            .setMovementSpeed(zombie, 0.3)
            .setCustomName(zombie, "Test Zombie")
            .setGlowing(zombie, true)
            .preventItemDrops(zombie);
        testEntities.add(zombie);

        // Scenario 2: Spawn a skeleton archer
        Skeleton skeleton = entityManager.spawnTracked(EntityType.SKELETON, loc, "test_skeleton", sessionId);
        entityManager
            .setMaxHealth(skeleton, 30.0)
            .setMovementSpeed(skeleton, 0.35)
            .setCustomName(skeleton, "Test Skeleton")
            .preventItemDrops(skeleton);
        testEntities.add(skeleton);

        // Scenario 3: Spawn a creeper
        Creeper creeper = entityManager.spawnTracked(EntityType.CREEPER, loc, "test_creeper", sessionId);
        entityManager
            .setMaxHealth(creeper, 20.0)
            .setCustomName(creeper, "Test Creeper")
            .setGlowing(creeper, true);
        testEntities.add(creeper);

        // Scenario 4: Spawn a spider
        Spider spider = entityManager.spawnTracked(EntityType.SPIDER, loc, "test_spider", sessionId);
        entityManager
            .setMaxHealth(spider, 25.0)
            .setMovementSpeed(spider, 0.4)
            .setCustomName(spider, "Test Spider");
        testEntities.add(spider);

        // Scenario 5: Spawn a slime
        Slime slime = entityManager.spawnTracked(EntityType.SLIME, loc, "test_slime", sessionId);
        entityManager
            .setCustomName(slime, "Test Slime")
            .setGlowing(slime, true);
        slime.setSize(3);
        testEntities.add(slime);

        // Scenario 6: Spawn a passive entity (sheep)
        Sheep sheep = entityManager.spawnTracked(EntityType.SHEEP, loc, "test_sheep", sessionId);
        entityManager
            .setCustomName(sheep, "Test Sheep")
            .preventItemDrops(sheep);
        testEntities.add(sheep);

        // Scenario 7: Spawn a flying entity (bat)
        Bat bat = entityManager.spawnTracked(EntityType.BAT, loc, "test_bat", sessionId);
        entityManager
            .setCustomName(bat, "Test Bat")
            .setGlowing(bat, true);
        testEntities.add(bat);

        // Scenario 8: Spawn a water mob (squid)
        Location waterLoc = loc.clone();
        waterLoc.setY(loc.getWorld().getHighestBlockYAt(loc) - 2);
        Squid squid = entityManager.spawnTracked(EntityType.SQUID, waterLoc, "test_squid", sessionId);
        entityManager
            .setCustomName(squid, "Test Squid")
            .setGlowing(squid, true);
        testEntities.add(squid);
    }
} 