package nc.randomEvents.services.participants.container;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.core.SessionParticipant;
import nc.randomEvents.services.RewardGenerator;
import nc.randomEvents.services.RewardGenerator.Tier;
import nc.randomEvents.utils.PersistentDataHelper;
import net.kyori.adventure.text.Component;
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
        
        // The DataManager now handles verification during the loading process.
        plugin.getDataManager().loadAndVerifyContainers(registry.getAllContainers());
        
        // Initialize the behavior manager (it handles its own events)
        new ContainerBehaviorManager(plugin);
        
        // Register as session participant
        plugin.getSessionRegistry().registerParticipant(this);
    }

    public enum ContainerMaterial {
        CHEST(Material.CHEST),
        BARREL(Material.BARREL),
        SHULKER_BOX(Material.SHULKER_BOX),
        WHITE_SHULKER_BOX(Material.WHITE_SHULKER_BOX),
        ORANGE_SHULKER_BOX(Material.ORANGE_SHULKER_BOX),
        MAGENTA_SHULKER_BOX(Material.MAGENTA_SHULKER_BOX),
        LIGHT_BLUE_SHULKER_BOX(Material.LIGHT_BLUE_SHULKER_BOX),
        YELLOW_SHULKER_BOX(Material.YELLOW_SHULKER_BOX),
        LIME_SHULKER_BOX(Material.LIME_SHULKER_BOX),
        PINK_SHULKER_BOX(Material.PINK_SHULKER_BOX),
        GRAY_SHULKER_BOX(Material.GRAY_SHULKER_BOX),
        LIGHT_GRAY_SHULKER_BOX(Material.LIGHT_GRAY_SHULKER_BOX),
        CYAN_SHULKER_BOX(Material.CYAN_SHULKER_BOX),
        PURPLE_SHULKER_BOX(Material.PURPLE_SHULKER_BOX),
        BLUE_SHULKER_BOX(Material.BLUE_SHULKER_BOX),
        BROWN_SHULKER_BOX(Material.BROWN_SHULKER_BOX),
        GREEN_SHULKER_BOX(Material.GREEN_SHULKER_BOX),
        RED_SHULKER_BOX(Material.RED_SHULKER_BOX),
        BLACK_SHULKER_BOX(Material.BLACK_SHULKER_BOX);

        private final Material bukkitMaterial;
        ContainerMaterial(Material bukkitMaterial) { this.bukkitMaterial = bukkitMaterial; }
        public Material getBukkitMaterial() { return bukkitMaterial; }
    }

    public static class createContainer {
        private final Location location;
        private final ContainerData.ContainerType type;
        private final String containerId;
        private final UUID sessionId;
        private ContainerMaterial containerMaterial = ContainerMaterial.CHEST;
        private List<ItemStack> questItems = Collections.emptyList();
        private List<ItemStack> nonQuestItems = Collections.emptyList();
        private Map<Tier, Integer> rewardTiers = Collections.emptyMap();
        private boolean clearAtEnd = true;
        private String inventoryName = null;

        public createContainer(Location location, ContainerData.ContainerType type, String containerId, UUID sessionId) {
            this.location = location;
            this.type = type;
            this.containerId = containerId;
            this.sessionId = sessionId;
        }
        public createContainer material(ContainerMaterial material) {
            if (material != null) this.containerMaterial = material;
            return this;
        }
        public createContainer questItems(List<ItemStack> items) {
            if (items != null) this.questItems = items;
            return this;
        }
        public createContainer nonQuestItems(List<ItemStack> items) {
            if (items != null) this.nonQuestItems = items;
            return this;
        }
        public createContainer rewardTiers(Map<Tier, Integer> tiers) {
            if (tiers != null) this.rewardTiers = tiers;
            return this;
        }
        public createContainer clearAtEnd(boolean clear) {
            this.clearAtEnd = clear;
            return this;
        }
        public createContainer inventoryName(String name) {
            this.inventoryName = name;
            return this;
        }
        public Container build(ContainerManager mgr) {
            return mgr.createContainerInternal(this);
        }
    }

    private Container createContainerInternal(createContainer builder) {
        Block block = builder.location.getBlock();
        block.setType(builder.containerMaterial.getBukkitMaterial());
        if (!(block.getState() instanceof Container)) {
            block.setType(Material.AIR);
            return null;
        }
        Container container = (Container) block.getState();

        // Set custom name if applicable
        if (builder.type == ContainerData.ContainerType.REGULAR && builder.inventoryName != null && !builder.inventoryName.isEmpty()) {
            if (container instanceof Nameable) {
                ((Nameable) container).customName(Component.text(builder.inventoryName));
            }
        }

        PersistentDataHelper.set(container.getPersistentDataContainer(), plugin, CONTAINER_KEY, PersistentDataType.BYTE, (byte) 1);
        PersistentDataHelper.set(container.getPersistentDataContainer(), plugin, CONTAINER_ID_KEY, PersistentDataType.STRING, builder.containerId);
        PersistentDataHelper.set(container.getPersistentDataContainer(), plugin, CONTAINER_SESSION_KEY, PersistentDataType.STRING, builder.sessionId.toString());
        PersistentDataHelper.set(container.getPersistentDataContainer(), plugin, CONTAINER_TYPE_KEY, PersistentDataType.STRING, builder.type.name());
        PersistentDataHelper.set(container.getPersistentDataContainer(), plugin, CLEAR_AT_END_KEY, PersistentDataType.BYTE, (byte) (builder.clearAtEnd ? 1 : 0));
        
        container.update();
        if (!builder.questItems.isEmpty()) {
            for (ItemStack item : builder.questItems) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    PersistentDataHelper.set(meta.getPersistentDataContainer(), plugin, QUEST_ITEM_KEY, PersistentDataType.BYTE, (byte) 1);
                    PersistentDataHelper.set(meta.getPersistentDataContainer(), plugin, QUEST_ITEM_SESSION_KEY, PersistentDataType.STRING, builder.sessionId.toString());
                    item.setItemMeta(meta);
                }
                container.getInventory().addItem(item);
            }
        }
        if (!builder.nonQuestItems.isEmpty()) {
            container.getInventory().addItem(builder.nonQuestItems.toArray(new ItemStack[0]));
        }
        if (!builder.rewardTiers.isEmpty()) {
            RewardGenerator rewardGenerator = plugin.getRewardGenerator();
            if (rewardGenerator != null) {
                List<ItemStack> rewards = rewardGenerator.generateRewards(builder.rewardTiers);
                if (!rewards.isEmpty()) {
                    container.getInventory().addItem(rewards.toArray(new ItemStack[0]));
                }
            }
        }
        ContainerData data = new ContainerData(builder.location, builder.type, builder.containerId, builder.sessionId, builder.clearAtEnd);
        registry.registerContainer(builder.location, data);
        saveAllContainers();
        return container;
    }

    public static createContainer createContainer(Location location, ContainerData.ContainerType type, String containerId, UUID sessionId) {
        return new createContainer(location, type, containerId, sessionId);
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