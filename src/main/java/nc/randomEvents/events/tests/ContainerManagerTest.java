package nc.randomEvents.events.tests;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.core.BaseEvent;
import nc.randomEvents.services.participants.ContainerManager;
import nc.randomEvents.services.participants.ContainerManager.ContainerType;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.*;

public class ContainerManagerTest extends BaseEvent {
    private final ContainerManager containerManager;
    private final Map<UUID, List<Location>> playerContainers = new HashMap<>();

    public ContainerManagerTest(RandomEvents plugin) {
        this.containerManager = plugin.getContainerManager();
        setTickInterval(20L); // 1 second
        setDuration(800L); // 40 seconds
    }

    @Override
    public String getName() {
        return "ContainerManagerTest";
    }

    @Override
    public String getDescription() {
        return "A test event for container management functionality";
    }

    @Override
    public void onStart(UUID sessionId, Set<Player> players) {
        // Send message to all players
        for (Player player : players) {
            player.sendMessage(Component.text("Container Manager Test Event Started!", NamedTextColor.GREEN));
            
            // Create containers for each player
            List<Location> containers = new ArrayList<>();
            
            // Create an instant reward container
            Location instantRewardLoc = findSafeLocation(player.getLocation());
            if (instantRewardLoc != null) {
                Container container = containerManager.createContainer(
                    instantRewardLoc,
                    ContainerType.INSTANT_REWARD,
                    "instant_reward_" + player.getName(),
                    sessionId,
                    null,
                    false
                );
                if (container != null) {
                    containers.add(instantRewardLoc);
                }
            }
            
            // Create a regular container with some items
            Location regularLoc = findSafeLocation(player.getLocation());
            if (regularLoc != null) {
                List<ItemStack> items = new ArrayList<>();
                
                // Add some test items
                ItemStack diamond = new ItemStack(Material.DIAMOND, 1);
                ItemMeta meta = diamond.getItemMeta();
                meta.displayName(Component.text("Test Diamond", NamedTextColor.AQUA));
                diamond.setItemMeta(meta);
                items.add(diamond);
                
                ItemStack emerald = new ItemStack(Material.EMERALD, 2);
                meta = emerald.getItemMeta();
                meta.displayName(Component.text("Test Emerald", NamedTextColor.GREEN));
                emerald.setItemMeta(meta);
                items.add(emerald);
                
                Container container = containerManager.createContainer(
                    regularLoc,
                    ContainerType.REGULAR,
                    "regular_" + player.getName(),
                    sessionId,
                    Material.BARREL,
                    items,
                    true // Mark as quest items
                );
                if (container != null) {
                    containers.add(regularLoc);
                }
            }
            
            playerContainers.put(player.getUniqueId(), containers);
        }
    }

    @Override
    public void onTick(UUID sessionId, Set<Player> players) {
        // Nothing to do on tick
    }

    @Override
    public void onEnd(UUID sessionId, Set<Player> players) {
        // Send message to all players
        for (Player player : players) {
            player.sendMessage(Component.text("Container Manager Test Event Ended!", NamedTextColor.RED));
            
            // Clean up containers
            List<Location> containers = playerContainers.remove(player.getUniqueId());
            if (containers != null) {
                for (Location loc : containers) {
                    loc.getBlock().setType(Material.AIR);
                }
            }
        }
    }

    private Location findSafeLocation(Location center) {
        World world = center.getWorld();
        if (world == null) return null;
        
        // Try to find a safe location within 10 blocks
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                Location loc = center.clone().add(x, 0, z);
                if (isSafeLocation(loc)) {
                    return loc;
                }
            }
        }
        return null;
    }

    private boolean isSafeLocation(Location loc) {
        Block block = loc.getBlock();
        Block above = loc.clone().add(0, 1, 0).getBlock();
        Block below = loc.clone().subtract(0, 1, 0).getBlock();
        
        return block.getType().isAir() && 
               above.getType().isAir() && 
               !below.getType().isAir() && 
               below.getType() != Material.WATER && 
               below.getType() != Material.LAVA && 
               below.getType().isSolid();
    }
} 