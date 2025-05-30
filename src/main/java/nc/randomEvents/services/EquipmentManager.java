package nc.randomEvents.services;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.utils.PersistentDataHelper;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class EquipmentManager implements Listener {
    private final RandomEvents plugin;
    private static final String EQUIPMENT_KEY = "equipment";
    private static final String EQUIPMENT_ID_KEY = "equipment_id";
    private static final String EQUIPMENT_SESSION_KEY = "equipment_session";
    private static final int CHUNK_SCAN_RADIUS = 8; // Scan 8 chunks in each direction (16 chunks total)
    private final Set<String> activeSessions;

    public EquipmentManager(RandomEvents plugin) {
        this.plugin = plugin;
        this.activeSessions = new HashSet<>();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("EquipmentManager initialized with " + activeSessions.size() + " active sessions");
    }

    /**
     * Gives equipment to a player with persistent data
     * @param player The player to give the equipment to
     * @param item The item to give
     * @param equipmentId Unique identifier for this equipment
     * @param sessionId The event session this equipment belongs to
     */
    public void giveEquipment(Player player, ItemStack item, String equipmentId, String sessionId) {
        // Add persistent data to the item
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataHelper.set(meta.getPersistentDataContainer(), plugin, EQUIPMENT_KEY, 
                                   PersistentDataType.BYTE, (byte) 1);
            PersistentDataHelper.set(meta.getPersistentDataContainer(), plugin, EQUIPMENT_ID_KEY, 
                                   PersistentDataType.STRING, equipmentId);
            PersistentDataHelper.set(meta.getPersistentDataContainer(), plugin, EQUIPMENT_SESSION_KEY, 
                                   PersistentDataType.STRING, sessionId);
            item.setItemMeta(meta);
        }

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
     * @param sessionId The event session this kit belongs to
     */
    public void giveFullKit(Player player, Map<Integer, ItemStack> kit, String kitId, String sessionId) {
        for (Map.Entry<Integer, ItemStack> entry : kit.entrySet()) {
            ItemStack item = entry.getValue();
            // Add persistent data to each item
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                PersistentDataHelper.set(meta.getPersistentDataContainer(), plugin, EQUIPMENT_KEY, 
                                       PersistentDataType.BYTE, (byte) 1);
                PersistentDataHelper.set(meta.getPersistentDataContainer(), plugin, EQUIPMENT_ID_KEY, 
                                       PersistentDataType.STRING, kitId);
                PersistentDataHelper.set(meta.getPersistentDataContainer(), plugin, EQUIPMENT_SESSION_KEY, 
                                       PersistentDataType.STRING, sessionId);
                item.setItemMeta(meta);
            }

            if (!giveItemToPlayer(player, item, entry.getKey())) {
                // If slot is occupied, drop the item
                player.getWorld().dropItemNaturally(player.getLocation(), item);
                player.sendMessage("Slot " + entry.getKey() + " was occupied, so the item was dropped at your feet.");
            }
        }
    }

    /**
     * Starts tracking a session
     * @param sessionId The session ID to track
     */
    public void startSession(String sessionId) {
        activeSessions.add(sessionId);
        plugin.getLogger().info("Started tracking session: " + sessionId);
        plugin.getLogger().info("Active sessions: " + activeSessions);
    }

    /**
     * Stops tracking a session
     * @param sessionId The session ID to stop tracking
     */
    public void endSession(String sessionId) {
        activeSessions.remove(sessionId);
        plugin.getLogger().info("Stopped tracking session: " + sessionId);
        plugin.getLogger().info("Remaining active sessions: " + activeSessions);
    }

    /**
     * Checks if an item is event equipment for any active session
     * @param item The item to check
     * @return true if the item is event equipment
     */
    private boolean isEventEquipment(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        return PersistentDataHelper.has(meta.getPersistentDataContainer(), plugin, EQUIPMENT_KEY, PersistentDataType.BYTE);
    }

    /**
     * Gets the session ID for an event item
     * @param item The item to check
     * @return The session ID, or null if not an event item
     */
    private String getEventSessionId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        String sessionId = PersistentDataHelper.get(
            meta.getPersistentDataContainer(),
            plugin,
            EQUIPMENT_SESSION_KEY,
            PersistentDataType.STRING
        );
        
        if (sessionId != null) {
            plugin.getLogger().info("Found event item with session ID: " + sessionId);
        }
        return sessionId;
    }

    /**
     * Checks if an item is event equipment for a specific session
     * @param item The item to check
     * @param sessionId The session ID to check against
     * @return true if the item is event equipment for the session
     */
    private boolean isEventEquipment(ItemStack item, String sessionId) {
        if (item == null || !item.hasItemMeta()) return false;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        return PersistentDataHelper.has(meta.getPersistentDataContainer(), plugin, EQUIPMENT_KEY, PersistentDataType.BYTE) &&
               PersistentDataHelper.get(meta.getPersistentDataContainer(), plugin, EQUIPMENT_SESSION_KEY, 
                                      PersistentDataType.STRING).equals(sessionId);
    }

    /**
     * Cleans up all equipment for a specific session
     * @param sessionId The session ID to clean up
     */
    public void cleanupSession(String sessionId) {
        // Clean up items in player inventories
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            cleanupPlayerInventory(player, sessionId);
        }

        // Clean up items in the world
        for (World world : plugin.getServer().getWorlds()) {
            // Clean up dropped items and entities
            for (Entity entity : world.getEntities()) {
                cleanupEntity(entity, sessionId);
            }

            // Clean up blocks with inventories
            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState blockState : chunk.getTileEntities()) {
                    cleanupBlockInventory(blockState, sessionId);
                }
            }
        }

        // End the session
        endSession(sessionId);
    }

    /**
     * Gets all loaded chunks in a world
     * @param world The world to get chunks from
     * @return Set of loaded chunks
     */
    private Set<Chunk> getLoadedChunks(World world) {
        Set<Chunk> chunks = new HashSet<>();
        for (Chunk chunk : world.getLoadedChunks()) {
            chunks.add(chunk);
        }
        return chunks;
    }

    /**
     * Cleans up equipment from an entity
     * @param entity The entity to clean up
     * @param sessionId The session ID to clean up
     */
    private void cleanupEntity(Entity entity, String sessionId) {
        if (entity instanceof Item) {
            Item item = (Item) entity;
            if (isEventEquipment(item.getItemStack(), sessionId)) {
                item.remove();
            }
        } else if (entity instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) entity;
            if (living.getEquipment() != null) {
                // Clean main hand
                ItemStack mainHand = living.getEquipment().getItemInMainHand();
                if (isEventEquipment(mainHand, sessionId)) {
                    living.getEquipment().setItemInMainHand(null);
                }

                // Clean off hand
                ItemStack offHand = living.getEquipment().getItemInOffHand();
                if (isEventEquipment(offHand, sessionId)) {
                    living.getEquipment().setItemInOffHand(null);
                }

                // Clean armor
                ItemStack[] armor = living.getEquipment().getArmorContents();
                boolean hasEventArmor = false;
                for (ItemStack item : armor) {
                    if (isEventEquipment(item, sessionId)) {
                        hasEventArmor = true;
                        break;
                    }
                }
                if (hasEventArmor) {
                    living.getEquipment().setArmorContents(null);
                }
            }
        } else if (entity instanceof ItemFrame) {
            ItemFrame frame = (ItemFrame) entity;
            if (isEventEquipment(frame.getItem(), sessionId)) {
                frame.setItem(null);
            }
        } else if (entity instanceof ArmorStand) {
            ArmorStand stand = (ArmorStand) entity;
            // Clean armor
            ItemStack[] armor = stand.getEquipment().getArmorContents();
            boolean hasEventArmor = false;
            for (ItemStack item : armor) {
                if (isEventEquipment(item, sessionId)) {
                    hasEventArmor = true;
                    break;
                }
            }
            if (hasEventArmor) {
                stand.getEquipment().setArmorContents(null);
            }
        } else if (entity instanceof Minecart) {
            if (entity instanceof StorageMinecart) {
                StorageMinecart minecart = (StorageMinecart) entity;
                cleanupInventory(minecart.getInventory(), sessionId);
            }
        } else if (entity instanceof Boat) {
            if (entity instanceof ChestBoat) {
                ChestBoat boat = (ChestBoat) entity;
                cleanupInventory(boat.getInventory(), sessionId);
            }
        } else if (entity instanceof AbstractHorse) {
            if (entity instanceof ChestedHorse) {
                ChestedHorse horse = (ChestedHorse) entity;
                cleanupInventory(horse.getInventory(), sessionId);
            }
        }
    }

    /**
     * Cleans up equipment from a block inventory
     * @param blockState The block state to clean up
     * @param sessionId The session ID to clean up
     */
    private void cleanupBlockInventory(BlockState blockState, String sessionId) {
        if (blockState instanceof Container) {
            Container container = (Container) blockState;
            cleanupInventory(container.getInventory(), sessionId);
            
            // Special handling for shulker boxes
            if (blockState instanceof ShulkerBox) {
                ShulkerBox shulker = (ShulkerBox) blockState;
                // Check if the shulker box itself is an event item
                if (isEventEquipment(shulker.getInventory().getItem(0), sessionId)) {
                    shulker.getInventory().setItem(0, null);
                }
            }
        } else if (blockState instanceof Lectern) {
            Lectern lectern = (Lectern) blockState;
            if (isEventEquipment(lectern.getInventory().getItem(0), sessionId)) {
                lectern.getInventory().setItem(0, null);
            }
        } else if (blockState instanceof Jukebox) {
            Jukebox jukebox = (Jukebox) blockState;
            if (isEventEquipment(jukebox.getRecord(), sessionId)) {
                jukebox.setRecord(null);
            }
        } else if (blockState instanceof Campfire) {
            Campfire campfire = (Campfire) blockState;
            for (int i = 0; i < campfire.getSize(); i++) {
                if (isEventEquipment(campfire.getItem(i), sessionId)) {
                    campfire.setItem(i, null);
                }
            }
        } else if (blockState instanceof BrewingStand) {
            BrewingStand brewingStand = (BrewingStand) blockState;
            cleanupInventory(brewingStand.getInventory(), sessionId);
        } else if (blockState instanceof Furnace) {
            Furnace furnace = (Furnace) blockState;
            cleanupInventory(furnace.getInventory(), sessionId);
        } else if (blockState instanceof Smoker) {
            Smoker smoker = (Smoker) blockState;
            cleanupInventory(smoker.getInventory(), sessionId);
        } else if (blockState instanceof BlastFurnace) {
            BlastFurnace blastFurnace = (BlastFurnace) blockState;
            cleanupInventory(blastFurnace.getInventory(), sessionId);
        } else if (blockState instanceof Barrel) {
            Barrel barrel = (Barrel) blockState;
            cleanupInventory(barrel.getInventory(), sessionId);
        }
    }

    /**
     * Cleans up equipment from an inventory
     * @param inventory The inventory to clean up
     * @param sessionId The session ID to clean up
     */
    private void cleanupInventory(Inventory inventory, String sessionId) {
        for (ItemStack item : inventory.getContents()) {
            if (item != null) {
                if (isEventEquipment(item, sessionId)) {
                    inventory.remove(item);
                } else if (item.getType().name().contains("SHULKER_BOX")) {
                    // Check inside shulker boxes
                    if (item.getItemMeta() instanceof BlockStateMeta) {
                        BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();
                        if (meta.getBlockState() instanceof ShulkerBox) {
                            ShulkerBox shulker = (ShulkerBox) meta.getBlockState();
                            cleanupInventory(shulker.getInventory(), sessionId);
                            meta.setBlockState(shulker);
                            item.setItemMeta(meta);
                        }
                    }
                }
            }
        }
    }

    /**
     * Cleans up all equipment in a player's inventory for a specific session
     * @param player The player to clean up
     * @param sessionId The session ID to clean up
     */
    private void cleanupPlayerInventory(Player player, String sessionId) {
        // Clean main inventory
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isEventEquipment(item, sessionId)) {
                player.getInventory().remove(item);
            }
        }

        // Clean armor
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item != null && isEventEquipment(item, sessionId)) {
                player.getInventory().setArmorContents(null);
                break;
            }
        }

        // Clean offhand
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && isEventEquipment(offhand, sessionId)) {
            player.getInventory().setItemInOffHand(null);
        }

        // Clean ender chest
        for (ItemStack item : player.getEnderChest().getContents()) {
            if (item != null && isEventEquipment(item, sessionId)) {
                player.getEnderChest().remove(item);
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
        if (player.getInventory().getItem(slot) == null) {
            player.getInventory().setItem(slot, item);
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
        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(item);
            return true;
        }
        return false;
    }

    // Event Listeners for Item Interactions

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        
        ItemStack item = event.getItem().getItemStack();
        String sessionId = getEventSessionId(item);
        
        if (sessionId != null && !activeSessions.contains(sessionId)) {
            event.setCancelled(true);
            event.getItem().remove();
        }
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        if (isEventEquipment(event.getEntity().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();
        ItemStack hotbarItem = event.getHotbarButton() != -1 ? 
            player.getInventory().getItem(event.getHotbarButton()) : null;
        
        String clickedSessionId = clickedItem != null ? getEventSessionId(clickedItem) : null;
        String cursorSessionId = cursorItem != null ? getEventSessionId(cursorItem) : null;
        String hotbarSessionId = hotbarItem != null ? getEventSessionId(hotbarItem) : null;
        
        if (clickedSessionId != null && !activeSessions.contains(clickedSessionId)) {
            event.setCancelled(true);
            if (clickedItem != null) {
                event.getInventory().remove(clickedItem);
            }
        }
        if (cursorSessionId != null && !activeSessions.contains(cursorSessionId)) {
            event.setCancelled(true);
            if (cursorItem != null) {
                event.getInventory().remove(cursorItem);
            }
        }
        if (hotbarSessionId != null && !activeSessions.contains(hotbarSessionId)) {
            event.setCancelled(true);
            if (hotbarItem != null) {
                event.getInventory().remove(hotbarItem);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        ItemStack cursorItem = event.getCursor();
        ItemStack oldCursorItem = event.getOldCursor();
        
        String cursorSessionId = cursorItem != null ? getEventSessionId(cursorItem) : null;
        String oldCursorSessionId = oldCursorItem != null ? getEventSessionId(oldCursorItem) : null;
        
        if (cursorSessionId != null && !activeSessions.contains(cursorSessionId)) {
            event.setCancelled(true);
            if (cursorItem != null) {
                event.getInventory().remove(cursorItem);
            }
        }
        if (oldCursorSessionId != null && !activeSessions.contains(oldCursorSessionId)) {
            event.setCancelled(true);
            if (oldCursorItem != null) {
                event.getInventory().remove(oldCursorItem);
            }
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        String sessionId = getEventSessionId(item);
        if (sessionId != null && !activeSessions.contains(sessionId)) {
            event.setCancelled(true);
            event.getItemDrop().remove();
        }
    }

    @EventHandler
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        ItemStack item = event.getItem();
        String sessionId = getEventSessionId(item);
        if (sessionId != null && !activeSessions.contains(sessionId)) {
            event.setCancelled(true);
            event.getSource().remove(item);
        }
    }

    @EventHandler
    public void onHopperPickup(InventoryPickupItemEvent event) {
        ItemStack item = event.getItem().getItemStack();
        String sessionId = getEventSessionId(item);
        if (sessionId != null && !activeSessions.contains(sessionId)) {
            event.setCancelled(true);
            event.getItem().remove();
        }
    }

    @EventHandler
    public void onHopperTransfer(InventoryMoveItemEvent event) {
        ItemStack item = event.getItem();
        String sessionId = getEventSessionId(item);
        if (sessionId != null && !activeSessions.contains(sessionId)) {
            event.setCancelled(true);
            event.getSource().remove(item);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getItem() != null) {
            String sessionId = getEventSessionId(event.getItem());
            if (sessionId != null && !activeSessions.contains(sessionId)) {
                event.setCancelled(true);
                event.getPlayer().getInventory().remove(event.getItem());
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        String sessionId = getEventSessionId(item);
        if (sessionId != null && !activeSessions.contains(sessionId)) {
            event.setCancelled(true);
            event.getPlayer().getInventory().remove(item);
        }
    }

    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        ItemStack mainHand = event.getMainHandItem();
        ItemStack offHand = event.getOffHandItem();
        
        String mainHandSessionId = mainHand != null ? getEventSessionId(mainHand) : null;
        String offHandSessionId = offHand != null ? getEventSessionId(offHand) : null;
        
        if (mainHandSessionId != null && !activeSessions.contains(mainHandSessionId)) {
            event.setCancelled(true);
            event.getPlayer().getInventory().setItemInOffHand(null);
        }
        if (offHandSessionId != null && !activeSessions.contains(offHandSessionId)) {
            event.setCancelled(true);
            event.getPlayer().getInventory().setItemInMainHand(null);
        }
    }
} 