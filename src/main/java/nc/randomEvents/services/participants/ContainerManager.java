package nc.randomEvents.services.participants;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.core.SessionParticipant;
import nc.randomEvents.services.SessionRegistry;
import nc.randomEvents.services.RewardGenerator;
import nc.randomEvents.services.RewardGenerator.Tier;
import nc.randomEvents.services.RewardGenerator.TierQuantity;
import nc.randomEvents.utils.PersistentDataHelper;
import nc.randomEvents.utils.SoundHelper;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.*;

public class ContainerManager implements Listener, SessionParticipant {
    private final RandomEvents plugin;
    private static final String CONTAINER_KEY = "container";
    private static final String CONTAINER_ID_KEY = "container_id";
    private static final String CONTAINER_SESSION_KEY = "container_session";
    private static final String CONTAINER_TYPE_KEY = "container_type";
    private static final String QUEST_ITEM_KEY = "quest_item";
    private static final String QUEST_ITEM_SESSION_KEY = "quest_item_session";
    private final SessionRegistry sessionRegistry;
    private final Map<UUID, Set<Location>> sessionContainers = new HashMap<>();
    private final Map<UUID, Set<Location>> sessionQuestItems = new HashMap<>();

    public enum ContainerType {
        INSTANT_REWARD,
        REGULAR
    }

    public ContainerManager(RandomEvents plugin) {
        this.plugin = plugin;
        this.sessionRegistry = plugin.getSessionRegistry();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getSessionRegistry().registerParticipant(this);
        plugin.getLogger().info("ContainerManager initialized");
    }

    /**
     * Creates and tracks a container for a session
     * @param location The location to place the container
     * @param type The type of container to create
     * @param containerId Unique identifier for this container
     * @param sessionId The event session this container belongs to
     * @param containerMaterial The material type for the container (defaults to CHEST if null)
     * @param initialItems Optional list of items to add to the container
     * @param isQuestItem Whether the items should be marked as quest items (will be cleared on session end)
     * @return The created container block state, or null if creation failed
     */
    public Container createContainer(Location location, ContainerType type, String containerId, UUID sessionId, 
                                   Material containerMaterial, List<ItemStack> initialItems, boolean isQuestItem) {
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
        container.update();

        // Add initial items if provided
        if (initialItems != null && !initialItems.isEmpty()) {
            for (ItemStack item : initialItems) {
                if (isQuestItem) {
                    // Mark item as quest item
                    PersistentDataHelper.set(item.getItemMeta().getPersistentDataContainer(), plugin, QUEST_ITEM_KEY, 
                                           PersistentDataType.BYTE, (byte) 1);
                    PersistentDataHelper.set(item.getItemMeta().getPersistentDataContainer(), plugin, QUEST_ITEM_SESSION_KEY, 
                                           PersistentDataType.STRING, sessionId.toString());
                }
                container.getInventory().addItem(item);
            }
        }

        // Track the container
        sessionContainers.computeIfAbsent(sessionId, k -> new HashSet<>()).add(location);
        
        return container;
    }

    /**
     * Creates and tracks a container for a session with default chest material
     * @param location The location to place the container
     * @param type The type of container to create
     * @param containerId Unique identifier for this container
     * @param sessionId The event session this container belongs to
     * @param initialItems Optional list of items to add to the container
     * @param isQuestItem Whether the items should be marked as quest items (will be cleared on session end)
     * @return The created container block state, or null if creation failed
     */
    public Container createContainer(Location location, ContainerType type, String containerId, UUID sessionId, 
                                   List<ItemStack> initialItems, boolean isQuestItem) {
        return createContainer(location, type, containerId, sessionId, null, initialItems, isQuestItem);
    }

    /**
     * Checks if a block is an event container
     * @param block The block to check
     * @return true if the block is an event container
     */
    private boolean isEventContainer(Block block) {
        if (!(block.getState() instanceof Container)) return false;
        
        Container container = (Container) block.getState();
        return PersistentDataHelper.has(container.getPersistentDataContainer(), plugin, CONTAINER_KEY, 
                                      PersistentDataType.BYTE);
    }

    /**
     * Gets the session ID for an event container
     * @param block The block to check
     * @return The session ID, or null if not an event container
     */
    private UUID getEventSessionId(Block block) {
        if (!(block.getState() instanceof Container)) return null;
        
        Container container = (Container) block.getState();
        String sessionIdStr = PersistentDataHelper.get(
            container.getPersistentDataContainer(),
            plugin,
            CONTAINER_SESSION_KEY,
            PersistentDataType.STRING
        );
        
        if (sessionIdStr != null) {
            try {
                return UUID.fromString(sessionIdStr);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid session ID format in container: " + sessionIdStr);
                return null;
            }
        }
        return null;
    }

    /**
     * Gets the container type for an event container
     * @param block The block to check
     * @return The container type, or null if not an event container
     */
    private ContainerType getContainerType(Block block) {
        if (!(block.getState() instanceof Container)) return null;
        
        Container container = (Container) block.getState();
        String typeStr = PersistentDataHelper.get(
            container.getPersistentDataContainer(),
            plugin,
            CONTAINER_TYPE_KEY,
            PersistentDataType.STRING
        );
        
        if (typeStr != null) {
            try {
                return ContainerType.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid container type in container: " + typeStr);
                return null;
            }
        }
        return null;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        
        Block block = event.getClickedBlock();
        if (!isEventContainer(block)) return;
        
        UUID sessionId = getEventSessionId(block);
        if (sessionId == null || !sessionRegistry.isActive(sessionId)) return;
        
        ContainerType type = getContainerType(block);
        if (type == null) return;
        
        if (type == ContainerType.INSTANT_REWARD) {
            event.setCancelled(true);
            handleInstantRewardContainer(event.getPlayer(), block);
        }
        // For REGULAR containers, we don't need to do anything special
        // They will behave like normal containers
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null) return;
        
        // Check if this is a container inventory
        if (clickedInventory.getHolder() instanceof Container) {
            Container container = (Container) clickedInventory.getHolder();
            Block block = container.getBlock();
            
            if (isEventContainer(block)) {
                // Only allow taking items out
                if (event.getAction().name().contains("PLACE") || 
                    event.getAction().name().contains("SWAP") ||
                    event.getAction().name().contains("MOVE_TO_OTHER_INVENTORY")) {
                    event.setCancelled(true);
                }
                
                // Check if container is empty after this click
                if (event.getAction().name().contains("PICKUP")) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (clickedInventory.isEmpty()) {
                            block.setType(Material.AIR);
                        }
                    });
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Inventory topInventory = event.getView().getTopInventory();
        if (topInventory.getHolder() instanceof Container) {
            Container container = (Container) topInventory.getHolder();
            Block block = container.getBlock();
            
            if (isEventContainer(block)) {
                // Cancel any drag that would place items in the container
                for (int slot : event.getRawSlots()) {
                    if (slot < topInventory.getSize()) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }

    private void handleInstantRewardContainer(Player player, Block block) {
        // Get the container's session ID
        UUID sessionId = getEventSessionId(block);
        if (sessionId == null) return;
        
        // Get the container's ID
        Container container = (Container) block.getState();
        String containerId = PersistentDataHelper.get(
            container.getPersistentDataContainer(),
            plugin,
            CONTAINER_ID_KEY,
            PersistentDataType.STRING
        );
        
        if (containerId == null) return;
        
        // Play effects
        Location effectLoc = block.getLocation().add(0.5, 0.5, 0.5);
        player.getWorld().spawnParticle(Particle.PORTAL, effectLoc, 50, 0.5, 0.5, 0.5, 1);
        player.getWorld().spawnParticle(Particle.END_ROD, effectLoc, 20, 0.3, 0.3, 0.3, 0.1);
        SoundHelper.playWorldSoundSafely(player.getWorld(), "block.enchantment_table.use", effectLoc, 1.0f, 1.0f);
        SoundHelper.playWorldSoundSafely(player.getWorld(), "entity.player.levelup", effectLoc, 1.0f, 0.5f);
        
        // Remove the container
        block.setType(Material.AIR);
        
        // Generate and give rewards
        RewardGenerator rewardGenerator = plugin.getRewardGenerator();
        if (rewardGenerator != null) {
            List<ItemStack> rewards = rewardGenerator.generateRewards(
                new TierQuantity()
                    .add(Tier.RARE, 2)
                    .add(Tier.COMMON, 4)
                    .build()
            );
            
            if (rewards.isEmpty()) {
                player.sendMessage(Component.text("The container was surprisingly empty... better luck next time!", 
                    NamedTextColor.YELLOW));
                plugin.getLogger().warning("ContainerManager: No rewards generated for container " + containerId);
            } else {
                player.sendMessage(Component.text("You received your rewards from the container!", 
                    NamedTextColor.GOLD));
                for (ItemStack reward : rewards) {
                    player.getInventory().addItem(reward).forEach((index, item) -> {
                        player.getWorld().dropItemNaturally(player.getLocation(), item);
                        player.sendMessage(Component.text("Your inventory was full! Some items were dropped at your feet.", 
                            NamedTextColor.RED));
                    });
                }
            }
        }
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
        Set<Location> containers = sessionContainers.remove(sessionId);
        if (containers != null) {
            for (Location loc : containers) {
                Block block = loc.getBlock();
                if (block.getState() instanceof Container) {
                    Container container = (Container) block.getState();
                    // Clear the container's inventory
                    container.getInventory().clear();
                    // Remove the container
                    block.setType(Material.AIR);
                }
            }
        }

        // Clean up quest items
        Set<Location> questItems = sessionQuestItems.remove(sessionId);
        if (questItems != null) {
            for (Location loc : questItems) {
                Block block = loc.getBlock();
                if (block.getState() instanceof Container) {
                    Container container = (Container) block.getState();
                    container.getInventory().clear();
                }
            }
        }
    }
} 