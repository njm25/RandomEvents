package nc.randomEvents.services.participants.container;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.utils.PersistentDataHelper;
import nc.randomEvents.utils.SoundHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.*;
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

import java.util.UUID;

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
                                        block.setType(Material.AIR);
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