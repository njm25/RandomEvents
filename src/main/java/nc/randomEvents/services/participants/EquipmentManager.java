package nc.randomEvents.services.participants;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.core.SessionParticipant;
import nc.randomEvents.listeners.EquipmentListener;
import nc.randomEvents.services.SessionRegistry;
import nc.randomEvents.utils.ItemHelper;
import nc.randomEvents.utils.PersistentDataHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

interface IEquipmentManager {
    void giveEquipment(Player player, ItemStack item, String equipmentId, UUID sessionId);
    void giveFullKit(Player player, Map<Integer, ItemStack> kit, String kitId, UUID sessionId);
}

public class EquipmentManager implements SessionParticipant, IEquipmentManager {
    private final RandomEvents plugin;
    private static final String EQUIPMENT_KEY = "equipment";
    private static final String EQUIPMENT_ID_KEY = "equipment_id";
    private static final String EQUIPMENT_SESSION_KEY = "equipment_session";
    private final SessionRegistry sessionRegistry;
    private EquipmentListener equipmentListener;
    
    // Map to store stripped inventories: sessionId -> (playerUUID -> StoredInventory)
    private final Map<UUID, Map<UUID, StoredInventory>> strippedInventories = new HashMap<>();

    public static class StoredInventory {
        private final ItemStack[] inventory;
        private final ItemStack[] armor;
        private final ItemStack offhand;

        public StoredInventory(Player player) {
            this.inventory = player.getInventory().getContents().clone();
            this.armor = ItemHelper.cloneArmorContents(player.getInventory().getArmorContents());
            this.offhand = player.getInventory().getItemInOffHand().clone();
        }

        private boolean isArmorSlot(int index) {
            return ItemHelper.isArmorSlot(index);
        }
        
        private boolean isOffhandSlot(int index) {
            return ItemHelper.isOffhandSlot(index);
        }
        
        public void restore(Player player) {
            // Store current inventory items to preserve them
            ItemStack[] currentInventory = player.getInventory().getContents().clone();
            ItemStack[] currentArmor = ItemHelper.cloneArmorContents(player.getInventory().getArmorContents());
            ItemStack currentOffhand = player.getInventory().getItemInOffHand().clone();

            // Clear inventory to start fresh
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getInventory().setItemInOffHand(null);

            // Create new armor array for merging
            ItemStack[] newArmor = new ItemStack[4];
            
            // Merge armor - prefer current armor over stored armor
            for (int i = 0; i < armor.length; i++) {
                if (currentArmor[i] != null) {
                    newArmor[i] = currentArmor[i].clone();
                } else if (armor[i] != null) {
                    newArmor[i] = armor[i].clone();
                }
            }
            player.getInventory().setArmorContents(newArmor);

            // Restore offhand - prefer current over stored
            if (currentOffhand != null) {
                player.getInventory().setItemInOffHand(currentOffhand.clone());
            } else if (offhand != null) {
                player.getInventory().setItemInOffHand(offhand.clone());
            }

            // Restore original inventory items using ItemHelper, skipping armor slots
            for (int i = 0; i < inventory.length; i++) {
                if (inventory[i] != null && !isArmorSlot(i) && !isOffhandSlot(i)) {
                    ItemHelper.giveItemToPlayer(player, inventory[i].clone());
                }
            }

            // Restore current inventory items, skipping armor slots
            for (int i = 0; i < currentInventory.length; i++) {
                ItemStack item = currentInventory[i];
                if (item != null && !isArmorSlot(i) && !isOffhandSlot(i)) {
                    ItemHelper.giveItemToPlayer(player, item.clone());
                }
            }
        }
    }

    public EquipmentManager(RandomEvents plugin) {
        this.plugin = plugin;
        this.sessionRegistry = plugin.getSessionRegistry();
        plugin.getSessionRegistry().registerParticipant(this);
        equipmentListener = new EquipmentListener(plugin);
        equipmentListener.registerListener(plugin);
        plugin.getLogger().info("EquipmentManager initialized");
    }

    /**
     * Gives equipment to a player with persistent data
     * @param player The player to give the equipment to
     * @param item The item to give
     * @param equipmentId Unique identifier for this equipment
     * @param sessionId The event session this equipment belongs to
     */
    public void giveEquipment(Player player, ItemStack item, String equipmentId, UUID sessionId) {
        // Add persistent data to the item
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataHelper.set(meta.getPersistentDataContainer(), plugin, EQUIPMENT_KEY, 
                                   PersistentDataType.BYTE, (byte) 1);
            PersistentDataHelper.set(meta.getPersistentDataContainer(), plugin, EQUIPMENT_ID_KEY, 
                                   PersistentDataType.STRING, equipmentId);
            PersistentDataHelper.set(meta.getPersistentDataContainer(), plugin, EQUIPMENT_SESSION_KEY, 
                                   PersistentDataType.STRING, sessionId.toString());
            item.setItemMeta(meta);
        }

        // Try to give the item to the player
        ItemHelper.giveItemToPlayer(player, item);
    }

    /**
     * Gives a full kit to a player
     * @param player The player to give the kit to
     * @param kit Map of slot numbers to items
     * @param kitId Unique identifier for this kit
     * @param sessionId The event session this kit belongs to
     */
    public void giveFullKit(Player player, Map<Integer, ItemStack> kit, String kitId, UUID sessionId) {
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
                                       PersistentDataType.STRING, sessionId.toString());
                item.setItemMeta(meta);
            }

            ItemHelper.giveItemToPlayer(player, item);
        }
    }

    @Override
    public void onSessionStart(UUID sessionId) {
        plugin.getLogger().info("EquipmentManager tracking new session: " + sessionId);
        boolean hasStripsInventory = sessionRegistry.getSession(sessionId).getEvent().stripsInventory();
        
        if (hasStripsInventory) {
            // Get all players in the session
            Set<Player> players = sessionRegistry.getSession(sessionId).getPlayers();
            
            // Create a new map for this session
            Map<UUID, StoredInventory> sessionInventories = new HashMap<>();
            strippedInventories.put(sessionId, sessionInventories);
            
            // Store and clear each player's inventory
            for (Player player : players) {
                // Store the inventory
                sessionInventories.put(player.getUniqueId(), new StoredInventory(player));
                
                // Clear the inventory
                player.getInventory().clear();
                player.getInventory().setArmorContents(null);
                player.getInventory().setItemInOffHand(null);
                
                // Inform the player
                player.sendMessage(Component.text("[Event] Your inventory has been temporarily stored for this event.").color(NamedTextColor.GOLD));
            }
        }
    }

    @Override
    public void onSessionEnd(UUID sessionId) {
        plugin.getLogger().info("EquipmentManager cleaning up session: " + sessionId);
        boolean hasStrippedInventory = this.strippedInventories.containsKey(sessionId);
        cleanupSession(sessionId, false);

        // Always restore stripped inventories regardless of clearEquipmentAtEnd flag
        if (hasStrippedInventory) {
            Map<UUID, StoredInventory> sessionInventories = strippedInventories.get(sessionId);
            if (sessionInventories != null) {
                // Restore inventories for all players
                for (Map.Entry<UUID, StoredInventory> entry : sessionInventories.entrySet()) {
                    Player player = plugin.getServer().getPlayer(entry.getKey());
                    if (player != null && player.isOnline()) {
                        entry.getValue().restore(player);
                    }
                }
            }
            strippedInventories.remove(sessionId);
        }
    }

    /**
     * Checks if an item is event equipment for any active session
     * @param item The item to check
     * @return true if the item is event equipment
     */
    public static boolean isEventEquipment(ItemStack item, RandomEvents plugin) {
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
    public UUID getEventSessionId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        String sessionIdStr = PersistentDataHelper.get(
            meta.getPersistentDataContainer(),
            plugin,
            EQUIPMENT_SESSION_KEY,
            PersistentDataType.STRING
        );
        
        if (sessionIdStr != null) {
            try {
                return UUID.fromString(sessionIdStr);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid session ID format in item: " + sessionIdStr);
                return null;
            }
        }
        return null;
    }

    /**
     * Checks if an item is event equipment for a specific session
     * @param item The item to check
     * @param sessionId The session ID to check against
     * @return true if the item is event equipment for the session
     */
    private boolean isEventEquipment(ItemStack item, UUID sessionId) {
        if (item == null || !item.hasItemMeta()) return false;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        return PersistentDataHelper.has(meta.getPersistentDataContainer(), plugin, EQUIPMENT_KEY, PersistentDataType.BYTE) &&
               sessionId.toString().equals(PersistentDataHelper.get(meta.getPersistentDataContainer(), plugin, EQUIPMENT_SESSION_KEY, 
                                      PersistentDataType.STRING));
    }

    /**
     * Cleans up all equipment for a specific session
     * @param sessionId The session ID to clean up
     */
    @Override
    public void cleanupSession(UUID sessionId, boolean force) {
        if (!force) {
            boolean clearEquipmentAtEnd = sessionRegistry.getSession(sessionId).getEvent().clearEquipmentAtEnd();
            if (!clearEquipmentAtEnd) {
                return;
            }
        }
        // Clean up items in player inventories
        Set<Player> players = sessionRegistry.getSession(sessionId).getPlayers();
        for (Player player : players) {
            cleanupPlayerInventory(player, sessionId);
            // Check player's cursor
            player.updateInventory(); // triggers sync from client
            ItemStack cursorItem = player.getItemOnCursor();
            if (cursorItem != null) {
                if (isEventEquipment(cursorItem, sessionId)) {
                    player.setItemOnCursor(null);
                } else if (cursorItem.getType().name().contains("SHULKER_BOX")) {
                    cleanupShulkerBox(cursorItem, sessionId);
                }
            }

            // Check any open inventory
            if (player.getOpenInventory() != null) {
                Inventory topInventory = player.getOpenInventory().getTopInventory();
                if (topInventory != null) {
                    for (ItemStack item : topInventory.getContents()) {
                        if (item != null) {
                            UUID itemSessionId = getEventSessionId(item);
                            if (itemSessionId != null && itemSessionId.equals(sessionId)) {
                                topInventory.remove(item);
                            } else if (item.getType().name().contains("SHULKER_BOX")) {
                                cleanupShulkerBox(item, itemSessionId);
                            }
                        }
                    }
                }
            }
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
    }

    /**
     * Cleans up equipment from an entity
     * @param entity The entity to clean up
     * @param sessionId The session ID to clean up
     */
    private void cleanupEntity(Entity entity, UUID sessionId) {
        if (entity instanceof Item) {
            Item item = (Item) entity;
            if (isEventEquipment(item.getItemStack(), sessionId)) {
                item.remove();
            } else {
                // Check if it's a shulker box
                ItemStack itemStack = item.getItemStack();
                if (itemStack.getType().name().contains("SHULKER_BOX")) {
                    cleanupShulkerBox(itemStack, sessionId);
                    item.setItemStack(itemStack);
                }
            }
        } else if (entity instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) entity;
            if (living.getEquipment() != null) {
                // Clean main hand
                ItemStack mainHand = living.getEquipment().getItemInMainHand();
                if (isEventEquipment(mainHand, sessionId)) {
                    living.getEquipment().setItemInMainHand(null);
                } else if (mainHand != null && mainHand.getType().name().contains("SHULKER_BOX")) {
                    cleanupShulkerBox(mainHand, sessionId);
                }

                // Clean off hand
                ItemStack offHand = living.getEquipment().getItemInOffHand();
                if (isEventEquipment(offHand, sessionId)) {
                    living.getEquipment().setItemInOffHand(null);
                } else if (offHand != null && offHand.getType().name().contains("SHULKER_BOX")) {
                    cleanupShulkerBox(offHand, sessionId);
                }

                // Clean armor
                ItemStack[] armor = living.getEquipment().getArmorContents();
                boolean hasEventArmor = false;
                for (ItemStack item : armor) {
                    if (isEventEquipment(item, sessionId)) {
                        hasEventArmor = true;
                        break;
                    } else if (item != null && item.getType().name().contains("SHULKER_BOX")) {
                        cleanupShulkerBox(item, sessionId);
                    }
                }
                if (hasEventArmor) {
                    living.getEquipment().setArmorContents(null);
                }
            }
        } else if (entity instanceof ItemFrame) {
            ItemFrame frame = (ItemFrame) entity;
            ItemStack frameItem = frame.getItem();
            if (frameItem != null) {
                if (isEventEquipment(frameItem, sessionId)) {
                    frame.setItem(null);
                } else if (frameItem.getType().name().contains("SHULKER_BOX")) {
                    cleanupShulkerBox(frameItem, sessionId);
                }
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
                } else if (item != null && item.getType().name().contains("SHULKER_BOX")) {
                    cleanupShulkerBox(item, sessionId);
                }
            }
            if (hasEventArmor) {
                stand.getEquipment().setArmorContents(null);
            }
        } else if (entity instanceof AbstractHorse) {
            if (entity instanceof ChestedHorse) {
                ChestedHorse horse = (ChestedHorse) entity;
                cleanupInventory(horse.getInventory(), sessionId);
            }
        } else if (entity instanceof Llama) {
            Llama llama = (Llama) entity;
            if (llama.isCarryingChest()) {
                cleanupInventory(llama.getInventory(), sessionId);
            }
        } else if (entity instanceof Donkey) {
            Donkey donkey = (Donkey) entity;
            if (donkey.isCarryingChest()) {
                cleanupInventory(donkey.getInventory(), sessionId);
            }
        }
    }

    /**
     * Cleans up equipment from a block inventory
     * @param blockState The block state to clean up
     * @param sessionId The session ID to clean up
     */
    private void cleanupBlockInventory(BlockState blockState, UUID sessionId) {
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
            ItemStack record = jukebox.getRecord();
            if (record != null && isEventEquipment(record, sessionId)) {
                jukebox.setRecord(null);
                jukebox.update();
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
    private void cleanupInventory(Inventory inventory, UUID sessionId) {
        for (ItemStack item : inventory.getContents()) {
            if (item != null) {
                if (isEventEquipment(item, sessionId)) {
                    inventory.remove(item);
                } else if (item.getType().name().contains("SHULKER_BOX")) {
                    cleanupShulkerBox(item, sessionId);
                }
            }
        }
    }

    public void cleanupShulkerBox(ItemStack shulkerBox, UUID sessionId) {
        if (shulkerBox.getItemMeta() instanceof BlockStateMeta) {
            BlockStateMeta meta = (BlockStateMeta) shulkerBox.getItemMeta();
            if (meta.getBlockState() instanceof ShulkerBox) {
                ShulkerBox shulker = (ShulkerBox) meta.getBlockState();
                // Only remove event items, keep other items
                for (ItemStack item : shulker.getInventory().getContents()) {
                    if (item != null && isEventEquipment(item, sessionId)) {
                        shulker.getInventory().remove(item);
                    }
                }
                meta.setBlockState(shulker);
                shulkerBox.setItemMeta(meta);
            }
        }
    }

    /**
     * Cleans up all equipment in a player's inventory for a specific session
     * @param player The player to clean up
     * @param sessionId The session ID to clean up
     */
    public void cleanupPlayerInventory(Player player, UUID sessionId) {
        // Clean main inventory
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null) {
                if (isEventEquipment(item, sessionId)) {
                    player.getInventory().remove(item);
                } else if (item.getType().name().contains("SHULKER_BOX")) {
                    cleanupShulkerBox(item, sessionId);
                }
            }
        }

        // Clean armor
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item != null) {
                if (isEventEquipment(item, sessionId)) {
                    player.getInventory().setArmorContents(null);
                    break;
                } else if (item.getType().name().contains("SHULKER_BOX")) {
                    cleanupShulkerBox(item, sessionId);
                }
            }
        }

        // Clean offhand
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null) {
            if (isEventEquipment(offhand, sessionId)) {
                player.getInventory().setItemInOffHand(null);
            } else if (offhand.getType().name().contains("SHULKER_BOX")) {
                cleanupShulkerBox(offhand, sessionId);
            }
        }

        // Clean ender chest
        for (ItemStack item : player.getEnderChest().getContents()) {
            if (item != null) {
                if (isEventEquipment(item, sessionId)) {
                    player.getEnderChest().remove(item);
                } else if (item.getType().name().contains("SHULKER_BOX")) {
                    cleanupShulkerBox(item, sessionId);
                }
            }
        }
    }

    public Map<UUID, Map<UUID, StoredInventory>> getStrippedInventories() {
        return strippedInventories;
    }
} 