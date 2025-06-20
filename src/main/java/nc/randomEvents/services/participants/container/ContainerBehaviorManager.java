package nc.randomEvents.services.participants.container;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.utils.PersistentDataHelper;
import nc.randomEvents.utils.SoundHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;
import java.util.Iterator;

public class ContainerBehaviorManager implements Listener {
    private final RandomEvents plugin;
    private static final String CONTAINER_KEY = "container";
    private static final String CONTAINER_ID_KEY = "container_id";
    private static final String CONTAINER_SESSION_KEY = "container_session";
    private static final String CONTAINER_TYPE_KEY = "container_type";

    public ContainerBehaviorManager(RandomEvents plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isEventContainer(block)) return;

        // Cancel the break event - containers can only be removed by emptying them
        event.setCancelled(true);
        
        // Notify the player
        event.getPlayer().sendMessage(Component.text("This container can only be removed by emptying it.", 
            NamedTextColor.RED));
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        // Check all tile entities in the chunk for event containers
        for (BlockState blockState : event.getChunk().getTileEntities()) {
            if (blockState instanceof Container) {
                Container container = (Container) blockState;
                Block block = container.getBlock();
                
                // Check if it has event container persistent data
                if (isEventContainer(block)) {
                    UUID sessionId = getEventSessionId(block);
                    ContainerData.ContainerType type = getContainerType(block);
                    String containerId = PersistentDataHelper.get(
                        container.getPersistentDataContainer(),
                        plugin,
                        CONTAINER_ID_KEY,
                        PersistentDataType.STRING
                    );
                    
                    if (sessionId == null || type == null || containerId == null) {
                        // Container has some persistent data but it's corrupted
                        plugin.getLogger().warning("Found corrupted event container at " + block.getLocation() + 
                            " - missing required persistent data. Removing container.");
                        block.setType(Material.AIR);
                        continue;
                    }
                    
                    // Check if container exists in registry
                    ContainerData existingData = plugin.getContainerManager().getRegistry().getContainer(block.getLocation());
                    if (existingData == null) {
                        // Container exists in world but not in registry - try to recover
                        plugin.getLogger().info("Found event container at " + block.getLocation() + 
                            " that wasn't in registry. Attempting to recover...");
                        
                        // Get clear at end setting from persistent data
                        Boolean clearAtEnd = null;
                        Byte clearAtEndByte = PersistentDataHelper.get(
                            container.getPersistentDataContainer(),
                            plugin,
                            "clear_at_end",
                            PersistentDataType.BYTE
                        );
                        if (clearAtEndByte != null) {
                            clearAtEnd = clearAtEndByte == 1;
                        }
                        
                        // Create new container data and register it
                        ContainerData recoveredData = new ContainerData(
                            block.getLocation(),
                            type,
                            containerId,
                            sessionId,
                            clearAtEnd != null ? clearAtEnd : 
                                plugin.getSessionRegistry().getSession(sessionId).getEvent().getClearContainerAtEndDefault()
                        );
                        plugin.getContainerManager().getRegistry().registerContainer(block.getLocation(), recoveredData);
                        plugin.getContainerManager().saveAllContainers();
                        
                        plugin.getLogger().info("Successfully recovered container data for " + block.getLocation());
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        
        Block block = event.getClickedBlock();
        if (!isEventContainer(block)) return;
        
        UUID sessionId = getEventSessionId(block);
        if (sessionId == null) return;
        
        ContainerData.ContainerType type = getContainerType(block);
        if (type == null) return;
        
        if (type == ContainerData.ContainerType.INSTANT_REWARD) {
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
                        // Check if this will empty the container
                        ItemStack clickedItem = event.getCurrentItem();
                        if (clickedItem != null) {
                            int itemCount = clickedItem.getAmount();
                            int emptySlots = 0;
                            for (ItemStack invItem : player.getInventory().getStorageContents()) {
                                if (invItem == null) emptySlots++;
                            }
                            
                            // If this is the last item and there's enough space, check if container will be empty
                            if (itemCount <= emptySlots * 64) {
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    if (topInventory.isEmpty()) {
                                        handleContainerEmpty(block);
                                    }
                                });
                            }
                        }
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
                                handleContainerEmpty(block);
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

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        
        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder() instanceof Container)) return;
        
        Container container = (Container) inventory.getHolder();
        Block block = container.getBlock();
        
        if (!isEventContainer(block)) return;
        
        // Check if container is empty
        if (inventory.isEmpty()) {
            ContainerData.ContainerType type = getContainerType(block);
            if (type != null && (type == ContainerData.ContainerType.REGULAR || 
                               type == ContainerData.ContainerType.INSTANT_REWARD)) {
                // Remove empty regular/instant containers
                handleContainerEmpty(block);
            }
        }
    }

    @EventHandler
    public void onBlockFromTo(BlockFromToEvent event) {
        // Prevent liquids from breaking containers
        Block toBlock = event.getToBlock();
        if (isEventContainer(toBlock)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBurn(BlockBurnEvent event) {
        // Prevent fire from breaking containers
        Block block = event.getBlock();
        if (isEventContainer(block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockIgnite(BlockIgniteEvent event) {
        // Prevent fire from starting near containers
        Block block = event.getBlock();
        if (isEventContainer(block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPistonExtend(BlockPistonExtendEvent event) {
        // Check if any of the blocks being pushed are containers
        for (Block block : event.getBlocks()) {
            if (isEventContainer(block)) {
                // Remove the container if it's being pushed
                handleContainerEmpty(block);
            }
        }
    }

    @EventHandler
    public void onBlockPistonRetract(BlockPistonRetractEvent event) {
        // Check if any of the blocks being pulled are containers
        for (Block block : event.getBlocks()) {
            if (isEventContainer(block)) {
                // Remove the container if it's being pulled
                handleContainerEmpty(block);
            }
        }
    }

    @EventHandler
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        // Prevent endermen from picking up containers
        if (event.getEntity() instanceof Enderman && isEventContainer(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        // Remove containers that would be exploded
        Iterator<Block> iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (isEventContainer(block)) {
                handleContainerEmpty(block);
                iterator.remove(); // Remove from explosion list
            }
        }
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        // Remove containers that would be exploded
        Iterator<Block> iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (isEventContainer(block)) {
                handleContainerEmpty(block);
                iterator.remove(); // Remove from explosion list
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
        
        // Remove the container and notify plugin
        //plugin.getLogger().info("Removing instant reward container at " + block.getLocation());
        block.setType(Material.AIR);
        plugin.getContainerManager().onContainerRemoved(block.getLocation());
    }

    private void handleContainerEmpty(Block block) {
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
        
        // Log the removal
        //plugin.getLogger().info("Removing empty container at " + block.getLocation() + " (type: " + getContainerType(block) + ", session: " + sessionId + ")");
        
        // Remove the container
        block.setType(Material.AIR);
        
        // Notify the plugin that the container was removed
        plugin.getContainerManager().onContainerRemoved(block.getLocation());
    }

    private boolean isEventContainer(Block block) {
        if (!(block.getState() instanceof Container)) return false;
        
        Container container = (Container) block.getState();
        return PersistentDataHelper.has(container.getPersistentDataContainer(), plugin, CONTAINER_KEY, 
                                      PersistentDataType.BYTE);
    }

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

    private ContainerData.ContainerType getContainerType(Block block) {
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
                return ContainerData.ContainerType.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid container type in container: " + typeStr);
                return null;
            }
        }
        return null;
    }
} 