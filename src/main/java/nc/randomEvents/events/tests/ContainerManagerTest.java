package nc.randomEvents.events.tests;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.core.BaseEvent;
import nc.randomEvents.services.participants.ContainerManager;
import nc.randomEvents.services.participants.ContainerManager.ContainerType;
import nc.randomEvents.utils.LocationHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class ContainerManagerTest extends BaseEvent {
    private final ContainerManager containerManager;
    private final Map<UUID, Set<Location>> sessionContainers = new HashMap<>();

    public ContainerManagerTest(RandomEvents plugin) {
        this.containerManager = plugin.getContainerManager();
        
        // Configure event timing
        setTickInterval(20L); // Tick every second
        setDuration(800L); // Run for 40 seconds
    }
    
    @Override
    public void onStart(UUID sessionId, Set<Player> players) {
        players.forEach(player -> {
            player.sendMessage(Component.text("ContainerManagerTest has started!", NamedTextColor.GOLD));
        });

        // Create containers for each player
        for (Player player : players) {
            // Create an instant reward container
            Location instantRewardLoc = findSafeLocation(player.getLocation());
            if (instantRewardLoc != null) {
                Container container = containerManager.createContainer(
                    instantRewardLoc,
                    ContainerType.INSTANT_REWARD,
                    "instant_reward_" + player.getName(),
                    sessionId,
                    null // No initial items for instant reward container
                );
                if (container != null) {
                    sessionContainers.computeIfAbsent(sessionId, k -> new HashSet<>()).add(instantRewardLoc);
                    player.sendMessage(Component.text("An instant reward container has appeared nearby!", 
                        NamedTextColor.GREEN));
                }
            }

            // Create a regular container with some items
            Location regularLoc = findSafeLocation(player.getLocation());
            if (regularLoc != null) {
                List<ItemStack> initialItems = Arrays.asList(
                    new ItemStack(Material.DIAMOND, 5),
                    new ItemStack(Material.GOLD_INGOT, 10),
                    new ItemStack(Material.IRON_INGOT, 15)
                );

                Container container = containerManager.createContainer(
                    regularLoc,
                    ContainerType.REGULAR,
                    "regular_" + player.getName(),
                    sessionId,
                    Material.BARREL, // Use a barrel instead of a chest
                    initialItems
                );
                if (container != null) {
                    sessionContainers.computeIfAbsent(sessionId, k -> new HashSet<>()).add(regularLoc);
                    player.sendMessage(Component.text("A regular container with items has appeared nearby!", 
                        NamedTextColor.GREEN));
                }
            }
        }
    }
    
    @Override
    public void onTick(UUID sessionId, Set<Player> players) {
        // No tick behavior needed for this test
    }
    
    @Override
    public void onEnd(UUID sessionId, Set<Player> players) {
        players.forEach(player -> {
            player.sendMessage(Component.text("ContainerManagerTest has ended!", NamedTextColor.GOLD));
        });

        // Clean up containers
        Set<Location> containers = sessionContainers.remove(sessionId);
        if (containers != null) {
            for (Location loc : containers) {
                loc.getBlock().setType(Material.AIR);
            }
        }
    }
    
    @Override
    public String getName() {
        return "ContainerManagerTest";
    }
    
    @Override
    public String getDescription() {
        return "A test event for container management functionality";
    }

    private Location findSafeLocation(Location center) {
        // Try to find a safe location within 10 blocks of the center
        for (int attempts = 0; attempts < 10; attempts++) {
            double angle = Math.random() * 2 * Math.PI;
            double distance = 5 + Math.random() * 5; // Between 5 and 10 blocks away
            
            double x = center.getX() + (Math.cos(angle) * distance);
            double z = center.getZ() + (Math.sin(angle) * distance);
            
            Location potentialLoc = new Location(center.getWorld(), x, 0, z);
            int highestY = center.getWorld().getHighestBlockYAt(potentialLoc);
            potentialLoc.setY(highestY + 1);

            if (isSafeLocation(potentialLoc)) {
                return potentialLoc;
            }
        }
        return null;
    }

    private boolean isSafeLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        
        // Check block at feet level
        if (!loc.getBlock().getType().isAir()) return false;
        
        // Check block above (head level)
        Location headLoc = loc.clone().add(0, 1, 0);
        if (!headLoc.getBlock().getType().isAir()) return false;
        
        // Check block below (ground)
        Location groundLoc = loc.clone().add(0, -1, 0);
        if (!groundLoc.getBlock().getType().isSolid()) return false;
        
        // Make sure we're not spawning in water or lava
        if (loc.getBlock().isLiquid() || headLoc.getBlock().isLiquid()) return false;
        
        return true;
    }
} 