package nc.randomEvents.services.participants;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.core.SessionParticipant;
import nc.randomEvents.services.SessionRegistry;
import nc.randomEvents.services.RewardGenerator;
import nc.randomEvents.services.RewardGenerator.Tier;
import nc.randomEvents.utils.PersistentDataHelper;
import nc.randomEvents.utils.SoundHelper;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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
    private static final String CLEAR_AT_END_KEY = "clear_at_end";
    private final SessionRegistry sessionRegistry;
    private final Map<UUID, Set<Location>> sessionContainers = new HashMap<>();

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
     * @param questItems Optional list of quest items to add to the container (will be cleared at end)
     * @param nonQuestItems Optional list of non-quest items to add to the container
     * @param rewardTiers Optional map of reward tiers to generate random rewards
     * @param clearAtEnd Whether to clear this container at end (null = use event default)
     * @return The created container block state, or null if creation failed
     */
    public Container createContainer(Location location, ContainerType type, String containerId, UUID sessionId, 
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

        // Track the container
        sessionContainers.computeIfAbsent(sessionId, k -> new HashSet<>()).add(location);
        
        return container;
    }

    /**
     * Creates and tracks a container for a session with default chest material
     */
    public Container createContainer(Location location, ContainerType type, String containerId, UUID sessionId,
                                   List<ItemStack> questItems, List<ItemStack> nonQuestItems,
                                   Map<Tier, Integer> rewardTiers, Boolean clearAtEnd) {
        return createContainer(location, type, containerId, sessionId, null, questItems, nonQuestItems, rewardTiers, clearAtEnd);
    }

    /**
     * Checks if a block is an event container
     */
    private boolean isEventContainer(Block block) {
        if (!(block.getState() instanceof Container)) return false;
        
        Container container = (Container) block.getState();
        return PersistentDataHelper.has(container.getPersistentDataContainer(), plugin, CONTAINER_KEY, 
                                      PersistentDataType.BYTE);
    }

    /**
     * Gets the session ID for an event container
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

    /**
     * Gets whether a container should be cleared at end
     */
    private Boolean getClearAtEnd(Block block) {
        if (!(block.getState() instanceof Container)) return null;
        
        Container container = (Container) block.getState();
        Byte clearAtEnd = PersistentDataHelper.get(
            container.getPersistentDataContainer(),
            plugin,
            CLEAR_AT_END_KEY,
            PersistentDataType.BYTE
        );
        
        return clearAtEnd != null ? clearAtEnd == 1 : null;
    }

    /**
     * Checks if an item is a quest item for a specific session
     * @param item The item to check
     * @param sessionId The session ID to check against
     * @return true if the item is a quest item for the session
     */
    private boolean isQuestItem(ItemStack item, UUID sessionId) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        return PersistentDataHelper.has(meta.getPersistentDataContainer(), plugin, QUEST_ITEM_KEY, PersistentDataType.BYTE) &&
               sessionId.toString().equals(PersistentDataHelper.get(meta.getPersistentDataContainer(), plugin, QUEST_ITEM_SESSION_KEY, 
                                      PersistentDataType.STRING));
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
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        Inventory clickedInventory = event.getClickedInventory();
        Inventory topInventory = event.getView().getTopInventory();
        
        // Check if the top inventory is a container
        if (topInventory.getHolder() instanceof Container) {
            Container container = (Container) topInventory.getHolder();
            Block block = container.getBlock();
            
            if (isEventContainer(block)) {
                // Handle shift-clicking
                if (event.isShiftClick()) {
                    // If clicking in player inventory, block shift-click to container
                    if (clickedInventory == player.getInventory()) {
                        event.setCancelled(true);
                        return;
                    }
                    
                    // If clicking in container, allow shift-click to player inventory
                    if (clickedInventory == topInventory) {
                        return;
                    }
                }
                
                // If clicking in the container
                if (clickedInventory == topInventory) {
                    // Allow taking items out
                    if (event.getAction().name().contains("PICKUP")) {
                        // Check if container is empty after this click
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (topInventory.isEmpty()) {
                                block.setType(Material.AIR);
                            }
                        });
                        return;
                    }
                    
                    // Cancel all other actions in the container
                    event.setCancelled(true);
                    return;
                }
                
                // If clicking in player inventory
                if (clickedInventory == player.getInventory()) {
                    // Allow all actions in player inventory
                    return;
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
                // Check if any of the slots being dragged to are in the container
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
        
        // Give items to player
        for (ItemStack item : container.getInventory().getContents()) {
            if (item != null) {
                player.getInventory().addItem(item).forEach((index, remainingItem) -> {
                    player.getWorld().dropItemNaturally(player.getLocation(), remainingItem);
                    player.sendMessage(Component.text("Your inventory was full! Some items were dropped at your feet.", 
                        NamedTextColor.RED));
                });
            }
        }
        
        // Remove the container
        block.setType(Material.AIR);
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
        // Get the event's default clear setting
        boolean defaultClear = sessionRegistry.getSession(sessionId).getEvent().getClearContainerAtEndDefault();

        // Clean up containers
        Set<Location> containers = sessionContainers.remove(sessionId);
        if (containers != null) {
            for (Location loc : containers) {
                Block block = loc.getBlock();
                if (block.getState() instanceof Container) {
                    Container container = (Container) block.getState();
                    
                    // Check if this container should be cleared
                    Boolean clearAtEnd = getClearAtEnd(block);
                    boolean shouldClear = clearAtEnd != null ? clearAtEnd : defaultClear;
                    
                    // Always clear quest items
                    for (ItemStack item : container.getInventory().getContents()) {
                        if (item != null && isQuestItem(item, sessionId)) {
                            container.getInventory().remove(item);
                        }
                    }
                    
                    // If container should be cleared, remove it
                    if (shouldClear) {
                        block.setType(Material.AIR);
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
    }
} 