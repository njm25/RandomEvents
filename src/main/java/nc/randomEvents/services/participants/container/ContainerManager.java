package nc.randomEvents.services.participants.container;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.core.SessionParticipant;
import nc.randomEvents.services.RewardGenerator;
import nc.randomEvents.services.RewardGenerator.Tier;
import nc.randomEvents.utils.PersistentDataHelper;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class ContainerManager implements SessionParticipant {
    private final RandomEvents plugin;
    private final ContainerRegistry registry;
    private static final String CONTAINER_KEY = "container";
    private static final String CONTAINER_ID_KEY = "container_id";
    private static final String CONTAINER_SESSION_KEY = "container_session";
    private static final String CONTAINER_TYPE_KEY = "container_type";
    private static final String CLEAR_AT_END_KEY = "clear_at_end";
    private static final String QUEST_ITEM_KEY = "quest_item";
    private static final String QUEST_ITEM_SESSION_KEY = "quest_item_session";

    public ContainerManager(RandomEvents plugin) {
        this.plugin = plugin;
        this.registry = new ContainerRegistry();
        
        // Initialize the behavior manager (it handles its own events)
        new ContainerBehaviorManager(plugin);
        
        // Load existing containers
        plugin.getDataManager().loadContainers(registry.getAllContainers());
        
        // Register as session participant
        plugin.getSessionRegistry().registerParticipant(this);
    }

    public Container createContainer(Location location, ContainerData.ContainerType type, String containerId, UUID sessionId,
                                   Material containerMaterial, List<ItemStack> questItems, List<ItemStack> nonQuestItems,
                                   Map<Tier, Integer> rewardTiers, Boolean clearAtEnd) {
        Block block = location.getBlock();
        block.setType(containerMaterial != null ? containerMaterial : Material.CHEST);
        
        if (!(block.getState() instanceof Container)) {
            block.setType(Material.AIR);
            return null;
        }

        Container container = (Container) block.getState();
        
        // Add persistent data to the container
        PersistentDataHelper.set(container.getPersistentDataContainer(), plugin, CONTAINER_KEY, 
                               PersistentDataType.BYTE, (byte) 1);
        PersistentDataHelper.set(container.getPersistentDataContainer(), plugin, CONTAINER_ID_KEY, 
                               PersistentDataType.STRING, containerId);
        PersistentDataHelper.set(container.getPersistentDataContainer(), plugin, CONTAINER_SESSION_KEY, 
                               PersistentDataType.STRING, sessionId.toString());
        PersistentDataHelper.set(container.getPersistentDataContainer(), plugin, CONTAINER_TYPE_KEY, 
                               PersistentDataType.STRING, type.name());
        
        // Store clear at end setting
        if (clearAtEnd != null) {
            PersistentDataHelper.set(container.getPersistentDataContainer(), plugin, CLEAR_AT_END_KEY,
                                   PersistentDataType.BYTE, (byte) (clearAtEnd ? 1 : 0));
        }
        
        container.update();

        // Add quest items first
        if (questItems != null && !questItems.isEmpty()) {
            for (ItemStack item : questItems) {
                // Mark item as quest item
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    PersistentDataHelper.set(meta.getPersistentDataContainer(), plugin, QUEST_ITEM_KEY, 
                                           PersistentDataType.BYTE, (byte) 1);
                    PersistentDataHelper.set(meta.getPersistentDataContainer(), plugin, QUEST_ITEM_SESSION_KEY, 
                                           PersistentDataType.STRING, sessionId.toString());
                    item.setItemMeta(meta);
                }
                container.getInventory().addItem(item);
            }
        }

        // Add non-quest items
        if (nonQuestItems != null && !nonQuestItems.isEmpty()) {
            container.getInventory().addItem(nonQuestItems.toArray(new ItemStack[0]));
        }

        // Generate and add random rewards if tiers are provided
        if (rewardTiers != null && !rewardTiers.isEmpty()) {
            RewardGenerator rewardGenerator = plugin.getRewardGenerator();
            if (rewardGenerator != null) {
                List<ItemStack> rewards = rewardGenerator.generateRewards(rewardTiers);
                if (!rewards.isEmpty()) {
                    container.getInventory().addItem(rewards.toArray(new ItemStack[0]));
                }
            }
        }
        
        // Register the container
        ContainerData data = new ContainerData(location, type, containerId, sessionId, 
            clearAtEnd != null ? clearAtEnd : plugin.getSessionRegistry().getSession(sessionId).getEvent().getClearContainerAtEndDefault());
        registry.registerContainer(location, data);
        
        // Save container data
        saveAllContainers();
        
        return container;
    }

    @Override
    public void onSessionStart(UUID sessionId) {
        plugin.getLogger().info("ContainerManager tracking new session: " + sessionId);
    }

    @Override
    public void onSessionEnd(UUID sessionId) {
        plugin.getLogger().info("ContainerManager cleaning up session: " + sessionId);
        cleanupSession(sessionId, false);
    }

    @Override
    public void cleanupSession(UUID sessionId, boolean force) {
        // Clean up containers
        Map<Location, ContainerData> containers = new HashMap<>(registry.getAllContainers());
        for (Map.Entry<Location, ContainerData> entry : containers.entrySet()) {
            Location loc = entry.getKey();
            ContainerData data = entry.getValue();
            
            if (data.getSessionId().equals(sessionId)) {
                Block block = loc.getBlock();
                if (block.getState() instanceof Container) {
                    Container container = (Container) block.getState();
                    
                    // Check if this container should be cleared
                    boolean shouldClear = data.isClearAtEnd();
                    
                    // Always clear quest items
                    for (ItemStack item : container.getInventory().getContents()) {
                        if (item != null && isQuestItem(item, sessionId)) {
                            container.getInventory().remove(item);
                        }
                    }
                    
                    // If container should be cleared, remove it
                    if (shouldClear) {
                        block.setType(Material.AIR);
                        registry.unregisterContainer(loc);
                    }
                }
            }
        }

        // Clean up quest items in player inventories
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            // Clean main inventory
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && isQuestItem(item, sessionId)) {
                    player.getInventory().remove(item);
                }
            }

            // Clean armor
            for (ItemStack item : player.getInventory().getArmorContents()) {
                if (item != null && isQuestItem(item, sessionId)) {
                    player.getInventory().setArmorContents(null);
                    break;
                }
            }

            // Clean offhand
            ItemStack offhand = player.getInventory().getItemInOffHand();
            if (offhand != null && isQuestItem(offhand, sessionId)) {
                player.getInventory().setItemInOffHand(null);
            }

            // Clean cursor
            ItemStack cursorItem = player.getItemOnCursor();
            if (cursorItem != null && isQuestItem(cursorItem, sessionId)) {
                player.setItemOnCursor(null);
            }

            // Clean ender chest
            for (ItemStack item : player.getEnderChest().getContents()) {
                if (item != null && isQuestItem(item, sessionId)) {
                    player.getEnderChest().remove(item);
                }
            }
        }

        // Clean up quest items in the world
        for (World world : plugin.getServer().getWorlds()) {
            // Clean up dropped items
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item) {
                    Item item = (Item) entity;
                    if (isQuestItem(item.getItemStack(), sessionId)) {
                        item.remove();
                    }
                }
            }

            // Clean up block inventories
            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState blockState : chunk.getTileEntities()) {
                    if (blockState instanceof Container) {
                        Container container = (Container) blockState;
                        for (ItemStack item : container.getInventory().getContents()) {
                            if (item != null && isQuestItem(item, sessionId)) {
                                container.getInventory().remove(item);
                            }
                        }
                    }
                }
            }
        }
        
        // Save container data
        saveAllContainers();
    }

    private boolean isQuestItem(ItemStack item, UUID sessionId) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        return PersistentDataHelper.has(meta.getPersistentDataContainer(), plugin, QUEST_ITEM_KEY, PersistentDataType.BYTE) &&
               sessionId.toString().equals(PersistentDataHelper.get(meta.getPersistentDataContainer(), plugin, QUEST_ITEM_SESSION_KEY, 
                                      PersistentDataType.STRING));
    }

    public void saveAllContainers() {
        plugin.getDataManager().saveContainers(registry.getAllContainers());
    }

    public void onContainerRemoved(Location location) {
        // Remove the container from the registry
        registry.unregisterContainer(location);
        
        // Remove from data file
        plugin.getDataManager().removeContainer(location);
    }

    public ContainerRegistry getRegistry() {
        return registry;
    }
} 