package nc.randomEvents.services;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.utils.MetadataHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;

public class EquipmentManager {
    private final RandomEvents plugin;
    private final Map<String, Map<UUID, StoredEquipment>> storedEquipment;
    private static final String EQUIPMENT_METADATA_PREFIX = "equipment_";

    public EquipmentManager(RandomEvents plugin) {
        this.plugin = plugin;
        this.storedEquipment = new HashMap<>();
    }

    /**
     * Gives a single item to a player
     * @param player The player to give the item to
     * @param item The item to give
     * @param equipmentId Unique identifier for this equipment
     */
    public void giveEquipment(Player player, ItemStack item, String equipmentId) {
        String metadataKey = EQUIPMENT_METADATA_PREFIX + equipmentId;
        
        // Store the item in metadata
        MetadataHelper.setMetadata(player, metadataKey, item, plugin);
        
        // Try to give the item to the player
        if (!giveItemToPlayer(player, item)) {
            // If inventory is full, drop the item at player's feet
            player.getWorld().dropItemNaturally(player.getLocation(), item);
            player.sendMessage("Your inventory was full, so the item was dropped at your feet.");
        }
    }

    /**
     * Gives a full kit to a player
     * @param player The player to give the kit to
     * @param kit Map of slot numbers to items
     * @param kitId Unique identifier for this kit
     */
    public void giveFullKit(Player player, Map<Integer, ItemStack> kit, String kitId) {
        String metadataKey = EQUIPMENT_METADATA_PREFIX + kitId;
        
        // Store the kit in metadata
        MetadataHelper.setMetadata(player, metadataKey, kit, plugin);
        
        // Give each item in the kit
        for (Map.Entry<Integer, ItemStack> entry : kit.entrySet()) {
            if (!giveItemToPlayer(player, entry.getValue(), entry.getKey())) {
                // If slot is occupied, drop the item
                player.getWorld().dropItemNaturally(player.getLocation(), entry.getValue());
                player.sendMessage("Slot " + entry.getKey() + " was occupied, so the item was dropped at your feet.");
            }
        }
    }

    /**
     * Stores the player's current inventory and clears it
     * @param player The player whose inventory to store
     * @param storageId Unique identifier for this stored inventory
     */
    public void storeAndClearInventory(Player player, String storageId) {
        String metadataKey = EQUIPMENT_METADATA_PREFIX + storageId;
        PlayerInventory inventory = player.getInventory();
        
        // Store the inventory
        StoredEquipment stored = new StoredEquipment();
        stored.setInventory(inventory.getContents());
        stored.setArmor(inventory.getArmorContents());
        stored.setOffHand(inventory.getItemInOffHand());
        
        // Store in metadata
        MetadataHelper.setMetadata(player, metadataKey, stored, plugin);
        
        // Clear the inventory
        inventory.clear();
        inventory.setArmorContents(null);
        inventory.setItemInOffHand(null);
    }

    /**
     * Returns a single piece of equipment to the player
     * @param player The player to return the equipment to
     * @param equipmentId The ID of the equipment to return
     */
    public void returnEquipment(Player player, String equipmentId) {
        String metadataKey = EQUIPMENT_METADATA_PREFIX + equipmentId;
        
        if (MetadataHelper.hasMetadata(player, metadataKey)) {
            ItemStack item = (ItemStack) MetadataHelper.getMetadata(player, metadataKey).get(0).value();
            if (item != null) {
                // Remove the item from the player's inventory if they have it
                removeItemFromPlayer(player, item);
                // Remove the metadata
                MetadataHelper.removeMetadata(player, metadataKey, plugin);
            }
        }
    }

    /**
     * Returns a full kit to the player
     * @param player The player to return the kit to
     * @param kitId The ID of the kit to return
     */
    public void returnFullKit(Player player, String kitId) {
        String metadataKey = EQUIPMENT_METADATA_PREFIX + kitId;
        
        if (MetadataHelper.hasMetadata(player, metadataKey)) {
            @SuppressWarnings("unchecked")
            Map<Integer, ItemStack> kit = (Map<Integer, ItemStack>) MetadataHelper.getMetadata(player, metadataKey).get(0).value();
            if (kit != null) {
                // Remove each item from the player's inventory
                for (ItemStack item : kit.values()) {
                    removeItemFromPlayer(player, item);
                }
                // Remove the metadata
                MetadataHelper.removeMetadata(player, metadataKey, plugin);
            }
        }
    }

    /**
     * Restores a player's stored inventory
     * @param player The player to restore the inventory for
     * @param storageId The ID of the stored inventory
     */
    public void restoreInventory(Player player, String storageId) {
        String metadataKey = EQUIPMENT_METADATA_PREFIX + storageId;
        
        if (MetadataHelper.hasMetadata(player, metadataKey)) {
            StoredEquipment stored = (StoredEquipment) MetadataHelper.getMetadata(player, metadataKey).get(0).value();
            if (stored != null) {
                PlayerInventory inventory = player.getInventory();
                
                // Clear current inventory
                inventory.clear();
                inventory.setArmorContents(null);
                inventory.setItemInOffHand(null);
                
                // Restore stored inventory
                inventory.setContents(stored.getInventory());
                inventory.setArmorContents(stored.getArmor());
                inventory.setItemInOffHand(stored.getOffHand());
                
                // Remove the metadata
                MetadataHelper.removeMetadata(player, metadataKey, plugin);
            }
        }
    }

    /**
     * Gives an item to a player in a specific slot
     * @param player The player to give the item to
     * @param item The item to give
     * @param slot The slot to put the item in
     * @return true if successful, false if slot is occupied
     */
    private boolean giveItemToPlayer(Player player, ItemStack item, int slot) {
        PlayerInventory inventory = player.getInventory();
        if (inventory.getItem(slot) == null) {
            inventory.setItem(slot, item);
            return true;
        }
        return false;
    }

    /**
     * Gives an item to a player in the first available slot
     * @param player The player to give the item to
     * @param item The item to give
     * @return true if successful, false if inventory is full
     */
    private boolean giveItemToPlayer(Player player, ItemStack item) {
        PlayerInventory inventory = player.getInventory();
        if (inventory.firstEmpty() != -1) {
            inventory.addItem(item);
            return true;
        }
        return false;
    }

    /**
     * Removes an item from a player's inventory
     * @param player The player to remove the item from
     * @param item The item to remove
     */
    private void removeItemFromPlayer(Player player, ItemStack item) {
        PlayerInventory inventory = player.getInventory();
        inventory.removeItem(item);
    }

    /**
     * Class to store a player's inventory contents
     */
    private static class StoredEquipment {
        private ItemStack[] inventory;
        private ItemStack[] armor;
        private ItemStack offHand;

        public ItemStack[] getInventory() {
            return inventory;
        }

        public void setInventory(ItemStack[] inventory) {
            this.inventory = inventory;
        }

        public ItemStack[] getArmor() {
            return armor;
        }

        public void setArmor(ItemStack[] armor) {
            this.armor = armor;
        }

        public ItemStack getOffHand() {
            return offHand;
        }

        public void setOffHand(ItemStack offHand) {
            this.offHand = offHand;
        }
    }
} 