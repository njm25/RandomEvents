package nc.randomEvents.events.tests;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.core.BaseEvent;
import nc.randomEvents.services.participants.container.ContainerManager;
import nc.randomEvents.services.participants.container.ContainerData.ContainerType;
import nc.randomEvents.services.RewardGenerator.Tier;
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
        setDuration(400L); // 40 seconds
        setClearContainerAtEndDefault(true); // Test container clearing
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
            
            // Test Case 1: Empty instant reward green shulker box (should be cleared)
            Location instantRewardGreenLoc = findSafeLocation(player.getLocation());
            if (instantRewardGreenLoc != null) {
                Container container = ContainerManager.createContainer(
                    instantRewardGreenLoc,
                    ContainerType.INSTANT_REWARD,
                    "instant_reward_green_" + player.getName(),
                    sessionId
                )
                .material(ContainerManager.ContainerMaterial.GREEN_SHULKER_BOX)
                .clearAtEnd(true)
                .build(containerManager);
                if (container != null) {
                    containers.add(instantRewardGreenLoc);
                }
            }

            // Test Case 2: Empty regular green shulker box (should be cleared)
            Location regularGreenLoc = findSafeLocation(player.getLocation());
            if (regularGreenLoc != null) {
                Container container = ContainerManager.createContainer(
                    regularGreenLoc,
                    ContainerType.REGULAR,
                    "regular_green_" + player.getName(),
                    sessionId
                )
                .material(ContainerManager.ContainerMaterial.GREEN_SHULKER_BOX)
                .clearAtEnd(true)
                .build(containerManager);
                if (container != null) {
                    containers.add(regularGreenLoc);
                }
            }

            // Test Case 3: Empty instant reward red shulker box (should not be cleared)
            Location instantRewardRedLoc = findSafeLocation(player.getLocation());
            if (instantRewardRedLoc != null) {
                Container container = ContainerManager.createContainer(
                    instantRewardRedLoc,
                    ContainerType.INSTANT_REWARD,
                    "instant_reward_red_" + player.getName(),
                    sessionId
                )
                .material(ContainerManager.ContainerMaterial.RED_SHULKER_BOX)
                .clearAtEnd(false)
                .build(containerManager);
                if (container != null) {
                    containers.add(instantRewardRedLoc);
                }
            }

            // Test Case 4: Empty regular red shulker box (should not be cleared)
            Location regularRedLoc = findSafeLocation(player.getLocation());
            if (regularRedLoc != null) {
                Container container = ContainerManager.createContainer(
                    regularRedLoc,
                    ContainerType.REGULAR,
                    "regular_red_" + player.getName(),
                    sessionId
                )
                .material(ContainerManager.ContainerMaterial.RED_SHULKER_BOX)
                .clearAtEnd(false)
                .build(containerManager);
                if (container != null) {
                    containers.add(regularRedLoc);
                }
            }

            // Test Case 5: Regular purple shulker box with quest items (should be cleared despite setting)
            Location questPurpleLoc = findSafeLocation(player.getLocation());
            if (questPurpleLoc != null) {
                // Create some random quest items
                List<ItemStack> questItems = new ArrayList<>();
                questItems.add(new ItemStack(Material.GOLDEN_APPLE, 3));
                questItems.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1));
                questItems.add(new ItemStack(Material.NETHERITE_INGOT, 1));
                
                Container container = ContainerManager.createContainer(
                    questPurpleLoc,
                    ContainerType.REGULAR,
                    "quest_purple_" + player.getName(),
                    sessionId
                )
                .material(ContainerManager.ContainerMaterial.PURPLE_SHULKER_BOX)
                .questItems(questItems)
                .clearAtEnd(false)
                .build(containerManager);
                if (container != null) {
                    containers.add(questPurpleLoc);
                }
            }

            // Test Case 6: Regular cyan shulker box with mixed items and rewards
            Location mixedCyanLoc = findSafeLocation(player.getLocation());
            if (mixedCyanLoc != null) {
                // Create quest item (diamond sword)
                List<ItemStack> questItems = new ArrayList<>();
                ItemStack sword = new ItemStack(Material.DIAMOND_SWORD, 1);
                ItemMeta meta = sword.getItemMeta();
                meta.displayName(Component.text("Test Sword (Quest Item)", NamedTextColor.AQUA));
                sword.setItemMeta(meta);
                questItems.add(sword);

                // Create non-quest item (steak)
                List<ItemStack> nonQuestItems = new ArrayList<>();
                ItemStack steak = new ItemStack(Material.COOKED_BEEF, 32);
                meta = steak.getItemMeta();
                meta.displayName(Component.text("Test Steak (Gift)", NamedTextColor.GREEN));
                steak.setItemMeta(meta);
                nonQuestItems.add(steak);

                // Create reward tiers
                Map<Tier, Integer> rewardTiers = new HashMap<>();
                rewardTiers.put(Tier.COMMON, 3);
                rewardTiers.put(Tier.BASIC, 5);

                Container container = ContainerManager.createContainer(
                    mixedCyanLoc,
                    ContainerType.REGULAR,
                    "mixed_cyan_" + player.getName(),
                    sessionId
                )
                .material(ContainerManager.ContainerMaterial.CYAN_SHULKER_BOX)
                .questItems(questItems)
                .nonQuestItems(nonQuestItems)
                .rewardTiers(rewardTiers)
                .clearAtEnd(false)
                .build(containerManager);
                if (container != null) {
                    containers.add(mixedCyanLoc);
                }
            }

            // Test Case 7: Regular chest with custom inventory name
            Location customNameLoc = findSafeLocation(player.getLocation());
            if (customNameLoc != null) {
                Container container = ContainerManager.createContainer(
                    customNameLoc,
                    ContainerType.REGULAR,
                    "custom_name_" + player.getName(),
                    sessionId
                )
                .material(ContainerManager.ContainerMaterial.CHEST)
                .inventoryName("§6§lCustom Treasure Chest")
                .clearAtEnd(true)
                .build(containerManager);
                if (container != null) {
                    containers.add(customNameLoc);
                    player.sendMessage(Component.text("Created custom named chest! Right-click to see the custom title.", NamedTextColor.GREEN));
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