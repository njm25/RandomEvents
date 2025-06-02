package nc.randomEvents.events.Test.EntityManagerTest;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.core.BaseEvent;
import nc.randomEvents.services.EntityManager;
import nc.randomEvents.utils.EntityHelper;

import org.bukkit.Location;
import org.bukkit.entity.*;
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
        EntityHelper.setMaxHealth(zombie, 40.0);
        EntityHelper.setMovementSpeed(zombie, 0.3);
        EntityHelper.setCustomName(zombie, "Test Zombie");
        EntityHelper.setGlowing(zombie, true);
        EntityHelper.preventItemPickup(zombie);
        testEntities.add(zombie);

        // Scenario 2: Spawn a skeleton archer
        Skeleton skeleton = entityManager.spawnTracked(EntityType.SKELETON, loc, "test_skeleton", sessionId);
        EntityHelper.setMaxHealth(skeleton, 30.0);
        EntityHelper.setMovementSpeed(skeleton, 0.35);
        EntityHelper.setCustomName(skeleton, "Test Skeleton");
        EntityHelper.preventItemPickup(skeleton);
        testEntities.add(skeleton);

        // Scenario 3: Spawn a creeper
        Creeper creeper = entityManager.spawnTracked(EntityType.CREEPER, loc, "test_creeper", sessionId);
        EntityHelper.setMaxHealth(creeper, 20.0);
        EntityHelper.setCustomName(creeper, "Test Creeper");
        EntityHelper.setGlowing(creeper, true);
        testEntities.add(creeper);

        // Scenario 4: Spawn a spider
        Spider spider = entityManager.spawnTracked(EntityType.SPIDER, loc, "test_spider", sessionId);
        EntityHelper.setMaxHealth(spider, 25.0);
        EntityHelper.setMovementSpeed(spider, 0.4);
        EntityHelper.setCustomName(spider, "Test Spider");
        testEntities.add(spider);

        // Scenario 5: Spawn a slime
        Slime slime = entityManager.spawnTracked(EntityType.SLIME, loc, "test_slime", sessionId);
        EntityHelper.setCustomName(slime, "Test Slime");
        EntityHelper.setGlowing(slime, true);
        slime.setSize(3);
        testEntities.add(slime);

        // Scenario 6: Spawn a passive entity (sheep)
        Sheep sheep = entityManager.spawnTracked(EntityType.SHEEP, loc, "test_sheep", sessionId);
        EntityHelper.setCustomName(sheep, "Test Sheep");
        EntityHelper.preventItemPickup(sheep);
        testEntities.add(sheep);

        // Scenario 7: Spawn a flying entity (bat)
        Bat bat = entityManager.spawnTracked(EntityType.BAT, loc, "test_bat", sessionId);
        EntityHelper.setCustomName(bat, "Test Bat");
        EntityHelper.setGlowing(bat, true);
        testEntities.add(bat);

        // Scenario 8: Spawn a water mob (squid)
        Location waterLoc = loc.clone();
        waterLoc.setY(loc.getWorld().getHighestBlockYAt(loc) - 2);
        Squid squid = entityManager.spawnTracked(EntityType.SQUID, waterLoc, "test_squid", sessionId);
        EntityHelper.setCustomName(squid, "Test Squid");
        EntityHelper.setGlowing(squid, true);
        testEntities.add(squid);
    }
} 