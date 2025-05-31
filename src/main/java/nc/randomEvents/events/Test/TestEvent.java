package nc.randomEvents.events.Test;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.events.Event;
import nc.randomEvents.services.EquipmentManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class TestEvent implements Event {
    private final RandomEvents plugin;
    private final EquipmentManager equipmentManager;
    private final Map<UUID, BukkitTask> activeTasks;
    private static final int EVENT_DURATION = 40; // 40 seconds
    private static final String SESSION_ID = "test_event_session";

    public TestEvent(RandomEvents plugin) {
        this.plugin = plugin;
        this.equipmentManager = plugin.getEquipmentManager();
        this.activeTasks = new HashMap<>();
    }

    @Override
    public String getName() {
        return "TestEvent";
    }

    @Override
    public String getDescription() {
        return "A test event for equipment management functionality";
    }

    @Override
    public void execute(Set<Player> players) {
        // Start the session before giving out items
        equipmentManager.startSession(SESSION_ID);
        
        for (Player player : players) {
            if (!player.isOnline() || player.isDead()) continue;
            
            // Test different equipment scenarios
            testEquipmentScenarios(player);
            
            // Schedule event end
            scheduleEventEnd(player);
        }
    }

    private void testEquipmentScenarios(Player player) {
        // Scenario 1: Give a diamond sword
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        equipmentManager.giveEquipment(player, sword, "test_sword", SESSION_ID);

        // Scenario 2: Give a golden apple
        ItemStack apple = new ItemStack(Material.GOLDEN_APPLE, 15);
        equipmentManager.giveEquipment(player, apple, "test_apple", SESSION_ID);

        // Scenario 3: Give a full set of armor
        Map<Integer, ItemStack> armorKit = new HashMap<>();
        armorKit.put(39, new ItemStack(Material.DIAMOND_HELMET)); // Helmet
        armorKit.put(38, new ItemStack(Material.DIAMOND_CHESTPLATE)); // Chestplate
        armorKit.put(37, new ItemStack(Material.DIAMOND_LEGGINGS)); // Leggings
        armorKit.put(36, new ItemStack(Material.DIAMOND_BOOTS)); // Boots
        equipmentManager.giveFullKit(player, armorKit, "test_armor", SESSION_ID);

        // Scenario 4: Give items for special blocks
        // Lectern items
        ItemStack book1 = new ItemStack(Material.WRITTEN_BOOK);
        ItemStack book2 = new ItemStack(Material.WRITABLE_BOOK);
        equipmentManager.giveEquipment(player, book1, "test_book1", SESSION_ID);
        equipmentManager.giveEquipment(player, book2, "test_book2", SESSION_ID);

        // Jukebox items
        ItemStack disc1 = new ItemStack(Material.MUSIC_DISC_13);
        ItemStack disc2 = new ItemStack(Material.MUSIC_DISC_CAT);
        equipmentManager.giveEquipment(player, disc1, "test_disc1", SESSION_ID);
        equipmentManager.giveEquipment(player, disc2, "test_disc2", SESSION_ID);

        // Campfire items (raw meat)
        ItemStack food1 = new ItemStack(Material.BEEF);
        ItemStack food2 = new ItemStack(Material.CHICKEN);
        equipmentManager.giveEquipment(player, food1, "test_food1", SESSION_ID);
        equipmentManager.giveEquipment(player, food2, "test_food2", SESSION_ID);

        // Composter items
        ItemStack compost1 = new ItemStack(Material.WHEAT);
        ItemStack compost2 = new ItemStack(Material.CARROT);
        equipmentManager.giveEquipment(player, compost1, "test_compost1", SESSION_ID);
        equipmentManager.giveEquipment(player, compost2, "test_compost2", SESSION_ID);

        // Beehive items
        ItemStack honey1 = new ItemStack(Material.HONEY_BOTTLE);
        ItemStack honey2 = new ItemStack(Material.HONEYCOMB);
        equipmentManager.giveEquipment(player, honey1, "test_honey1", SESSION_ID);
        equipmentManager.giveEquipment(player, honey2, "test_honey2", SESSION_ID);

        // Brewing Stand items
        ItemStack potion1 = new ItemStack(Material.POTION);
        ItemStack potion2 = new ItemStack(Material.SPLASH_POTION);
        equipmentManager.giveEquipment(player, potion1, "test_potion1", SESSION_ID);
        equipmentManager.giveEquipment(player, potion2, "test_potion2", SESSION_ID);

        // Villager Job Block items
        // Cartography Table
        ItemStack paper = new ItemStack(Material.PAPER, 16);
        ItemStack map = new ItemStack(Material.MAP);
        equipmentManager.giveEquipment(player, paper, "test_paper", SESSION_ID);
        equipmentManager.giveEquipment(player, map, "test_map", SESSION_ID);

        // Loom
        ItemStack banner = new ItemStack(Material.WHITE_BANNER);
        ItemStack dye = new ItemStack(Material.RED_DYE);
        equipmentManager.giveEquipment(player, banner, "test_banner", SESSION_ID);
        equipmentManager.giveEquipment(player, dye, "test_dye", SESSION_ID);

        // Smithing Table
        ItemStack netherite = new ItemStack(Material.NETHERITE_INGOT);
        ItemStack diamond = new ItemStack(Material.DIAMOND);
        equipmentManager.giveEquipment(player, netherite, "test_netherite", SESSION_ID);
        equipmentManager.giveEquipment(player, diamond, "test_diamond", SESSION_ID);

        // Stonecutter
        ItemStack stone = new ItemStack(Material.STONE, 16);
        ItemStack andesite = new ItemStack(Material.ANDESITE, 16);
        equipmentManager.giveEquipment(player, stone, "test_stone", SESSION_ID);
        equipmentManager.giveEquipment(player, andesite, "test_andesite", SESSION_ID);

        // Grindstone
        ItemStack damagedSword = new ItemStack(Material.IRON_SWORD);
        ItemStack damagedPickaxe = new ItemStack(Material.IRON_PICKAXE);
        equipmentManager.giveEquipment(player, damagedSword, "test_damaged_sword", SESSION_ID);
        equipmentManager.giveEquipment(player, damagedPickaxe, "test_damaged_pickaxe", SESSION_ID);

        // Anvil
        ItemStack enchantedBook = new ItemStack(Material.ENCHANTED_BOOK);
        ItemStack ironSword = new ItemStack(Material.IRON_SWORD);
        equipmentManager.giveEquipment(player, enchantedBook, "test_enchanted_book", SESSION_ID);
        equipmentManager.giveEquipment(player, ironSword, "test_iron_sword", SESSION_ID);

        // Shulker Box test
        ItemStack shulker = new ItemStack(Material.SHULKER_BOX);
        equipmentManager.giveEquipment(player, shulker, "test_shulker", SESSION_ID);
    }

    private void scheduleEventEnd(Player player) {
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // Clean up all equipment for this session
            equipmentManager.cleanupSession(SESSION_ID);
            
            // Remove task from active tasks
            activeTasks.remove(player.getUniqueId());
        }, EVENT_DURATION * 20L); // Convert seconds to ticks

        activeTasks.put(player.getUniqueId(), task);
    }

    public void cleanup() {
        // Cancel all active tasks
        for (BukkitTask task : activeTasks.values()) {
            task.cancel();
        }
        activeTasks.clear();
        
        // Clean up any remaining equipment
        equipmentManager.cleanupSession(SESSION_ID);
    }
} 