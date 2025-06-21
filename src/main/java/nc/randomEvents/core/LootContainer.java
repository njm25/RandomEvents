package nc.randomEvents.core;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.services.participants.ContainerManager;
import nc.randomEvents.services.RewardGenerator;
import nc.randomEvents.services.RewardGenerator.Tier;
import nc.randomEvents.utils.PersistentDataHelper;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

interface ILootContainer {
    void addQuestItem(ItemStack item);
    void addQuestItems(List<ItemStack> items);
    void addLootItem(ItemStack item);
    void addLootItems(List<ItemStack> items);
    void addRewardTier(Tier tier, int count);
    boolean spawn();
}

public class LootContainer implements ILootContainer {
    private final RandomEvents plugin;
    private final ContainerManager containerManager;
    private final Location location;
    private final String containerId;
    private final UUID sessionId;
    
    // Configuration
    private ContainerType type;
    private ContainerMaterial material = ContainerMaterial.CHEST;
    private List<ItemStack> questItems = new ArrayList<>();
    private List<ItemStack> normalItems = new ArrayList<>();
    private Map<Tier, Integer> rewardTiers = new HashMap<>();
    private boolean clearAtEnd;
    private String inventoryName = null;

    public LootContainer(RandomEvents plugin, ContainerManager containerManager, Location location, String containerId, UUID sessionId, ContainerType type, boolean clearAtEnd) {
        this.plugin = plugin;
        this.containerManager = containerManager;
        this.location = location;
        this.containerId = containerId;
        this.sessionId = sessionId;
        this.type = type;
        this.clearAtEnd = clearAtEnd;
    }

    // Getters and Setters
    public ContainerType getType() { return type; }
    public void setType(ContainerType type) { this.type = type; }
    
    public ContainerMaterial getMaterial() { return material; }
    public void setMaterial(ContainerMaterial material) { this.material = material; }
    
    public boolean isClearAtEnd() { return clearAtEnd; }
    public void setClearAtEnd(boolean clearAtEnd) { this.clearAtEnd = clearAtEnd; }
    
    public String getInventoryName() { return inventoryName; }
    public void setInventoryName(String inventoryName) { this.inventoryName = inventoryName; }
    
    public String getContainerId() { return containerId; }

    /**
     * Adds a quest item to the container
     */
    public void addQuestItem(ItemStack item) {
        if (item != null) {
            this.questItems.add(item);
        }
    }

    /**
     * Adds multiple quest items to the container
     */
    public void addQuestItems(List<ItemStack> items) {
        if (items != null) {
            this.questItems.addAll(items);
        }
    }

    /**
     * Adds a normal item to the container
     */
    public void addLootItem(ItemStack item) {
        if (item != null) {
            this.normalItems.add(item);
        }
    }

    /**
     * Adds multiple normal items to the container
     */
    public void addLootItems(List<ItemStack> items) {
        if (items != null) {
            this.normalItems.addAll(items);
        }
    }

    /**
     * Adds a reward tier to the container
     */
    public void addRewardTier(Tier tier, int count) {
        this.rewardTiers.put(tier, count);
    }

    /**
     * Spawns the container in the world
     * @return true if the container was successfully spawned
     */
    public boolean spawn() {
        Block block = location.getBlock();
        block.setType(material.getBukkitMaterial());
        
        if (!(block.getState() instanceof Container)) {
            block.setType(Material.AIR);
            return false;
        }
        
        Container container = (Container) block.getState();

        // Add persistent data
        PersistentDataHelper.set(container.getPersistentDataContainer(), plugin, "container", PersistentDataType.BYTE, (byte) 1);
        PersistentDataHelper.set(container.getPersistentDataContainer(), plugin, "container_id", PersistentDataType.STRING, containerId);
        PersistentDataHelper.set(container.getPersistentDataContainer(), plugin, "container_session", PersistentDataType.STRING, sessionId.toString());
        PersistentDataHelper.set(container.getPersistentDataContainer(), plugin, "container_type", PersistentDataType.STRING, type.name());
        PersistentDataHelper.set(container.getPersistentDataContainer(), plugin, "clear_at_end", PersistentDataType.BYTE, (byte) (clearAtEnd ? 1 : 0));
        
        container.update();

        // Add quest items
        for (ItemStack item : questItems) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                PersistentDataHelper.set(meta.getPersistentDataContainer(), plugin, "quest_item", PersistentDataType.BYTE, (byte) 1);
                PersistentDataHelper.set(meta.getPersistentDataContainer(), plugin, "quest_item_session", PersistentDataType.STRING, sessionId.toString());
                item.setItemMeta(meta);
            }
            container.getInventory().addItem(item);
        }

        // Add normal items
        for (ItemStack item : normalItems) {
            container.getInventory().addItem(item);
        }

        // Add reward items
        if (!rewardTiers.isEmpty()) {
            RewardGenerator rewardGenerator = plugin.getRewardGenerator();
            if (rewardGenerator != null) {
                List<ItemStack> rewards = rewardGenerator.generateRewards(rewardTiers);
                for (ItemStack reward : rewards) {
                    container.getInventory().addItem(reward);
                }
            }
        }

        // Register with container manager
        containerManager.registerContainer(location, sessionId);
        
        return true;
    }

    // Enums moved from ContainerManager
    public enum ContainerType {
        REGULAR,
        QUEST,
        INSTANT_REWARD
    }

    public enum ContainerMaterial {
        CHEST(Material.CHEST),
        TRAPPED_CHEST(Material.TRAPPED_CHEST),
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
}
