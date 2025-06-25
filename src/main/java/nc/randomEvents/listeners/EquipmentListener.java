package nc.randomEvents.listeners;

import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.core.ServiceListener;
import nc.randomEvents.services.participants.EquipmentManager;

public class EquipmentListener implements ServiceListener {

    private final RandomEvents plugin;
    
    public EquipmentListener(RandomEvents plugin) {
        this.plugin = plugin;
    }

    @Override
    public void registerListener(RandomEvents plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        
        ItemStack item = event.getItem().getItemStack();
        if (EquipmentManager.isEventEquipment(item, plugin)) {
            event.setCancelled(true);
            event.getItem().remove();
        }
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        // If it's not event equipment, allow it
        if (!EquipmentManager.isEventEquipment(event.getEntity().getItemStack(), plugin)) {
            return;
        }
        
        // Allow drops from inventory overflow (which have a default pickup delay)
        // and player drops (which have a thrower)
        if (event.getEntity().getPickupDelay() == 10 || event.getEntity().getThrower() != null) {
            return;
        }
        
        // Cancel other event item spawns
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getItem() != null && EquipmentManager.isEventEquipment(event.getItem(), plugin)) {
            // Only cancel specific block interactions
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                Block block = event.getClickedBlock();
                if (block != null) {
                    Material type = block.getType();
                    if (type == Material.COMPOSTER ||
                        type == Material.CAMPFIRE ||
                        type == Material.SOUL_CAMPFIRE ||
                        type == Material.LECTERN ||
                        type == Material.JUKEBOX) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        ItemStack draggedItem = event.getOldCursor();
        Inventory topInventory = event.getView().getTopInventory();
        
        // If we have an event item being dragged
        if (draggedItem != null && EquipmentManager.isEventEquipment(draggedItem, plugin)) {
            // Check if any of the slots being dragged to are in the top inventory
            for (int slot : event.getRawSlots()) {
                if (slot < topInventory.getSize()) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        // Allow dropping event items
        // No cancellation needed
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        
        Player player = (Player) event.getPlayer();
        Inventory topInventory = event.getView().getTopInventory();
        
        // Check top inventory for any event items that might have slipped through
        if (topInventory != null) {
            for (ItemStack item : topInventory.getContents()) {
                if (item != null && EquipmentManager.isEventEquipment(item, plugin)) {
                    topInventory.remove(item);
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Handle inventory restoration if player leaves during a session   
        for (Map.Entry<UUID, Map<UUID, EquipmentManager.StoredInventory>> sessionEntry : plugin.getEquipmentManager().getStrippedInventories().entrySet()) {
            UUID sessionId = sessionEntry.getKey();
            Map<UUID, EquipmentManager.StoredInventory> sessionInventories = sessionEntry.getValue();
            
            EquipmentManager.StoredInventory storedInventory = sessionInventories.get(player.getUniqueId());
            if (storedInventory != null) {
                // Clean any event items
                plugin.getEquipmentManager().cleanupPlayerInventory(player, sessionId);
                
                // Restore their inventory
                storedInventory.restore(player);
                sessionInventories.remove(player.getUniqueId());
                
                plugin.getLogger().info("Restored inventory for player " + player.getName() + " who left during session " + sessionId);
            }
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getPlayer().getInventory().getItemInMainHand() != null && 
              EquipmentManager.isEventEquipment(event.getPlayer().getInventory().getItemInMainHand(), plugin)) &&
            !(event.getPlayer().getInventory().getItemInOffHand() != null && 
              EquipmentManager.isEventEquipment(event.getPlayer().getInventory().getItemInOffHand(), plugin))) {
            return; // No event items in either hand, allow interaction
        }

        // Only cancel specific entity interactions
        if (event.getRightClicked() instanceof ArmorStand ||
            event.getRightClicked() instanceof ItemFrame) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHopperPickup(InventoryPickupItemEvent event) {
        ItemStack item = event.getItem().getItemStack();
        if (EquipmentManager.isEventEquipment(item, plugin)) {
            event.setCancelled(true);
            event.getItem().remove(); // Delete the item instead of just cancelling
            plugin.getLogger().info("Deleted event item attempted to be picked up by hopper: " + item.getType());
        }
    }

    @EventHandler
    public void onHopperTransfer(InventoryMoveItemEvent event) {
        ItemStack item = event.getItem();
        if (EquipmentManager.isEventEquipment(item, plugin)) {
            event.setCancelled(true);
            // Find and remove the item from the source inventory
            Inventory source = event.getSource();
            source.remove(item);
            plugin.getLogger().info("Deleted event item attempted to be transferred by hopper: " + item.getType());
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();
        Inventory clickedInventory = event.getClickedInventory();
        
        // Check if any event items have inactive sessions and clean them up
        if (cursorItem != null && EquipmentManager.isEventEquipment(cursorItem, plugin)) {
            UUID sessionId = plugin.getEquipmentManager().getEventSessionId(cursorItem);
            if (sessionId != null && !plugin.getSessionRegistry().isActive(sessionId)) {
                event.setCancelled(true);
                player.setItemOnCursor(null);
                return;
            }
        }
        
        if (clickedItem != null && EquipmentManager.isEventEquipment(clickedItem, plugin)) {
            UUID sessionId = plugin.getEquipmentManager().getEventSessionId(clickedItem);
            if (sessionId != null && !plugin.getSessionRegistry().isActive(sessionId)) {
                event.setCancelled(true);
                if (clickedInventory != null) {
                    clickedInventory.remove(clickedItem);
                }
                return;
            }
        }
        
        // Handle shift-clicking
        if (event.isShiftClick()) {
            if (clickedItem != null && EquipmentManager.isEventEquipment(clickedItem, plugin)) {
                if (clickedInventory == player.getInventory() && event.getView().getType() == InventoryType.CRAFTING) {
                    return;
                }
                Inventory destinationInventory = (clickedInventory == player.getInventory()) ? 
                    event.getView().getTopInventory() : player.getInventory();
                
                // Only allow if destination is player inventory
                if (destinationInventory != player.getInventory()) {
                    event.setCancelled(true);
                }
                return;
            }
        }
        
        // Handle number key clicks (1-9) and drops
        if (event.getClick() == ClickType.NUMBER_KEY || 
            event.getClick() == ClickType.DROP || 
            event.getClick() == ClickType.CONTROL_DROP) {
            
            if (clickedItem != null && EquipmentManager.isEventEquipment(clickedItem, plugin)) {
                // For number key clicks and drops, check if the destination is the player's inventory
                Inventory destinationInventory = (clickedInventory == player.getInventory()) ? 
                    event.getView().getTopInventory() : player.getInventory();
                
                // Cancel if destination is not player's inventory
                if (destinationInventory != player.getInventory()) {
                    event.setCancelled(true);
                }
                return;
            }
        }
        
        // Handle even splits and drag-splits
        if (cursorItem != null && EquipmentManager.isEventEquipment(cursorItem, plugin)) {
            // If we're in a crafting table or other container
            if (event.getView().getType() != InventoryType.CRAFTING) {
                // Only allow splits within the player inventory
                if (clickedInventory != player.getInventory()) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
        
        // Check if we're placing an event item into a container inventory
        if (event.getAction().name().contains("PLACE") || event.getAction().name().contains("SWAP")) {
            if (cursorItem != null && EquipmentManager.isEventEquipment(cursorItem, plugin)) {
                // Check if we're placing into a container (not player inventory)
                if (clickedInventory != null && clickedInventory != player.getInventory()) {
                    UUID sessionId = plugin.getEquipmentManager().getEventSessionId(cursorItem);
                    if (sessionId != null && !plugin.getSessionRegistry().isActive(sessionId)) {
                        event.setCancelled(true);
                        player.setItemOnCursor(null);
                        return;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        // Block any armor stand manipulation with event items
        if (EquipmentManager.isEventEquipment(event.getPlayerItem(), plugin )) {
            event.setCancelled(true);
        }
    }

    /**
     * Handles inventory drag events to check if event items are being dragged into inventories
     * and clean them up if their related session is no longer active
     */
    @EventHandler
    public void onInventoryDragPlace(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        ItemStack draggedItem = event.getOldCursor();
        
        if (draggedItem != null && EquipmentManager.isEventEquipment(draggedItem, plugin)) {
            UUID sessionId = plugin.getEquipmentManager().getEventSessionId(draggedItem);
            if (sessionId != null && !plugin.getSessionRegistry().isActive(sessionId)) {
                event.setCancelled(true);
                player.setItemOnCursor(null);
                return;
            }
        }
    }

    /**
     * Handles inventory close events to check for any event items that might have been placed
     * and clean them up if their related session is no longer active
     */
    @EventHandler
    public void onInventoryCloseCheck(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        
        Inventory topInventory = event.getView().getTopInventory();
        
        // Check top inventory for any event items with inactive sessions
        if (topInventory != null) {
            for (ItemStack item : topInventory.getContents()) {
                if (item != null && EquipmentManager.isEventEquipment(item, plugin)) {
                    UUID sessionId = plugin.getEquipmentManager().getEventSessionId(item);
                    if (sessionId != null && !plugin.getSessionRegistry().isActive(sessionId)) {
                        topInventory.remove(item);
                    }
                }
            }
        }
    }

}
