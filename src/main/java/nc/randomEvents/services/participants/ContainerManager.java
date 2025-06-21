package nc.randomEvents.services.participants;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.core.EventSession;
import nc.randomEvents.core.LootContainer;
import nc.randomEvents.core.LootContainer.ContainerType;
import nc.randomEvents.listeners.ContainerListener;
import nc.randomEvents.core.SessionParticipant;
import nc.randomEvents.services.SessionRegistry;
import nc.randomEvents.utils.PersistentDataHelper;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ContainerManager implements SessionParticipant {
    private final RandomEvents plugin;
    private final SessionRegistry sessionRegistry;
    private static final String CONTAINER_KEY = "container";
    private static final String CONTAINER_SESSION_KEY = "container_session";
    private static final String CLEAR_AT_END_KEY = "clear_at_end";
    private static final String QUEST_ITEM_KEY = "quest_item";
    private static final String QUEST_ITEM_SESSION_KEY = "quest_item_session";
    
    // Track containers by session
    private final Map<UUID, Set<Location>> sessionContainers = new ConcurrentHashMap<>();

    public ContainerManager(RandomEvents plugin) {
        this.plugin = plugin;
        this.sessionRegistry = plugin.getSessionRegistry();
        plugin.getSessionRegistry().registerParticipant(this);
        
        // Initialize the behavior manager (it handles its own events)
        new ContainerListener(plugin);
        
        plugin.getLogger().info("ContainerManager initialized");
    }

    /**
     * Creates a new LootContainer for the specified parameters
     * @param location The location where the container will be placed
     * @param sessionId The event session this container belongs to
     * @param type The container type (defaults to REGULAR)
     * @param clearAtEnd Whether the container should be cleared at session end (defaults to false)
     * @return A LootContainer instance
     */
    public LootContainer createContainer(Location location, UUID sessionId, ContainerType type, boolean clearAtEnd) {
        String containerId = generateContainerId();
        return new LootContainer(plugin, this, location, containerId, sessionId, type, clearAtEnd);
    }

    /**
     * Creates a new LootContainer for the specified parameters
     * @param location The location where the container will be placed
     * @param sessionId The event session this container belongs to
     * @param type The container type (defaults to REGULAR)
     * @return A LootContainer instance
     */
    public LootContainer createContainer(Location location, UUID sessionId, ContainerType type) {
        return createContainer(location, sessionId, type, false);
    }

    /**
     * Creates a new LootContainer for the specified parameters
     * @param location The location where the container will be placed
     * @param sessionId The event session this container belongs to
     * @return A LootContainer instance
     */
    public LootContainer createContainer(Location location, UUID sessionId) {
        return createContainer(location, sessionId, ContainerType.REGULAR, false);
    }

    /**
     * Generates a unique container ID
     * @return A unique container ID
     */
    private String generateContainerId() {
        return "container_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Registers a container location for a session
     * @param location The container location
     * @param sessionId The session ID
     */
    public void registerContainer(Location location, UUID sessionId) {
        sessionContainers.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(location);
    }

    /**
     * Unregisters a container location from a session
     * @param location The container location
     * @param sessionId The session ID
     */
    public void unregisterContainer(Location location, UUID sessionId) {
        Set<Location> containers = sessionContainers.get(sessionId);
        if (containers != null) {
            containers.remove(location);
        }
    }

    /**
     * Checks if a location has a registered container for a session
     * @param location The location to check
     * @param sessionId The session ID
     * @return true if the location has a registered container
     */
    public boolean isRegistered(Location location, UUID sessionId) {
        Set<Location> containers = sessionContainers.get(sessionId);
        return containers != null && containers.contains(location);
    }

    /**
     * Checks if a block is an event container
     * @param block The block to check
     * @return true if the block is an event container
     */
    public boolean isEventContainer(Block block) {
        if (!(block.getState() instanceof Container)) return false;
        return PersistentDataHelper.has(((Container) block.getState()).getPersistentDataContainer(), 
                                       plugin, CONTAINER_KEY, PersistentDataType.BYTE);
    }

    /**
     * Gets the session ID for an event container
     * @param block The block to check
     * @return The session ID, or null if not an event container
     */
    public UUID getEventSessionId(Block block) {
        if (!(block.getState() instanceof Container container)) return null;
        String raw = PersistentDataHelper.get(container.getPersistentDataContainer(), plugin, 
                                             CONTAINER_SESSION_KEY, PersistentDataType.STRING);
        try {
            return raw != null ? UUID.fromString(raw) : null;
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid session ID: " + raw);
            return null;
        }
    }

    /**
     * Checks if an item is a quest item for a specific session
     * @param item The item to check
     * @param sessionId The session ID to check against
     * @return true if the item is a quest item for the session
     */
    public boolean isQuestItem(ItemStack item, UUID sessionId) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        return PersistentDataHelper.has(meta.getPersistentDataContainer(), plugin, QUEST_ITEM_KEY, PersistentDataType.BYTE) &&
               sessionId.toString().equals(PersistentDataHelper.get(meta.getPersistentDataContainer(), plugin, 
                                      QUEST_ITEM_SESSION_KEY, PersistentDataType.STRING));
    }

    @Override
    public void onSessionStart(UUID sessionId) {
        plugin.getLogger().info("ContainerManager tracking new session: " + sessionId);
        sessionContainers.put(sessionId, ConcurrentHashMap.newKeySet());
    }

    @Override
    public void onSessionEnd(UUID sessionId) {
        plugin.getLogger().info("ContainerManager cleaning up session: " + sessionId);
        cleanupSession(sessionId, false);
    }

    @Override
    public void cleanupSession(UUID sessionId, boolean force) {
        EventSession session = sessionRegistry.getSession(sessionId);
        
        // Only clean up containers if the session exists and the event wants them cleaned up
        if (!force && session != null) {
            boolean clearContainersAtEnd = session.getEvent().getClearContainerAtEndDefault();
            if (!clearContainersAtEnd) {
                return;
            }
        }

        Set<Location> containers = sessionContainers.remove(sessionId);
        if (containers != null) {
            // Clean up containers
            for (Location location : containers) {
                Block block = location.getBlock();
                if (block.getState() instanceof Container) {
                    Container container = (Container) block.getState();
                    
                    // Check if this container should be cleared
                    boolean shouldClear = true;
                    Byte clearAtEnd = PersistentDataHelper.get(container.getPersistentDataContainer(), 
                                                              plugin, CLEAR_AT_END_KEY, PersistentDataType.BYTE);
                    if (clearAtEnd != null) {
                        shouldClear = clearAtEnd == 1;
                    }
                    
                    // If container should be cleared, remove it and its quest items
                    if (shouldClear) {
                        // Clear quest items from this container before removing it
                        for (ItemStack item : container.getInventory().getContents()) {
                            if (item != null && isQuestItem(item, sessionId)) {
                                container.getInventory().remove(item);
                            }
                        }
                        block.setType(Material.AIR);
                    }
                }
            }
        }

        // Only clean up quest items if we're forcing cleanup or if the event wants containers cleared
        boolean shouldCleanQuestItems = force || (session != null && session.getEvent().getClearContainerAtEndDefault());
        if (!shouldCleanQuestItems) {
            return;
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
    }
}
