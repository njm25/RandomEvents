package nc.randomEvents.events.Test.EquipmentManagerTest;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.core.BaseEvent;
import nc.randomEvents.services.EquipmentManager;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class EquipmentManagerTest extends BaseEvent {
    private final EquipmentManager equipmentManager;

    public EquipmentManagerTest(RandomEvents plugin) {
        this.equipmentManager = plugin.getEquipmentManager();
        
        // Configure event timing
        setTickInterval(20L); // Tick every second
        setDuration(800L); // Run for 20 seconds
    }
    
    @Override
    public void onStart(UUID sessionId, Set<Player> players) {
        players.forEach(player -> {
            player.sendMessage("EquipmentManagerTest has started!");
            
            // Test different equipment scenarios
            testEquipmentScenarios(player, sessionId);
        });

    }
    
    @Override
    public void onTick(UUID sessionId, Set<Player> players) {
        players.forEach(player -> {
            player.sendMessage("EquipmentManagerTest is ticking!");
        });
    }
    
    @Override
    public void onEnd(UUID sessionId, Set<Player> players) {
        players.forEach(player -> {
            player.sendMessage("EquipmentManagerTest has ended!");
        });
    }
    
    @Override
    public String getName() {
        return "EquipmentManagerTest";
    }
    
    @Override
    public String getDescription() {
        return "A test event for equipment management functionality";
    }

    private void testEquipmentScenarios(Player player, UUID sessionId) {
        // Scenario 1: Give a diamond sword
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        equipmentManager.giveEquipment(player, sword, "test_sword", sessionId);

        // Scenario 2: Give a golden apple
        ItemStack apple = new ItemStack(Material.GOLDEN_APPLE, 15);
        equipmentManager.giveEquipment(player, apple, "test_apple", sessionId);

        // Scenario 3: Give a full set of armor
        Map<Integer, ItemStack> armorKit = new HashMap<>();
        armorKit.put(39, new ItemStack(Material.DIAMOND_HELMET)); // Helmet
        armorKit.put(38, new ItemStack(Material.DIAMOND_CHESTPLATE)); // Chestplate
        armorKit.put(37, new ItemStack(Material.DIAMOND_LEGGINGS)); // Leggings
        armorKit.put(36, new ItemStack(Material.DIAMOND_BOOTS)); // Boots
        equipmentManager.giveFullKit(player, armorKit, "test_armor", sessionId);

        // Scenario 4: Give items for special blocks
        // Lectern items
        ItemStack book1 = new ItemStack(Material.WRITTEN_BOOK);
        ItemStack book2 = new ItemStack(Material.WRITABLE_BOOK);
        equipmentManager.giveEquipment(player, book1, "test_book1", sessionId);
        equipmentManager.giveEquipment(player, book2, "test_book2", sessionId);

        // Jukebox items
        ItemStack disc1 = new ItemStack(Material.MUSIC_DISC_13);
        ItemStack disc2 = new ItemStack(Material.MUSIC_DISC_CAT);
        equipmentManager.giveEquipment(player, disc1, "test_disc1", sessionId);
        equipmentManager.giveEquipment(player, disc2, "test_disc2", sessionId);

        // Campfire items (raw meat)
        ItemStack food1 = new ItemStack(Material.BEEF);
        ItemStack food2 = new ItemStack(Material.CHICKEN);
        equipmentManager.giveEquipment(player, food1, "test_food1", sessionId);
        equipmentManager.giveEquipment(player, food2, "test_food2", sessionId);

        // Composter items
        ItemStack compost1 = new ItemStack(Material.WHEAT);
        ItemStack compost2 = new ItemStack(Material.CARROT);
        equipmentManager.giveEquipment(player, compost1, "test_compost1", sessionId);
        equipmentManager.giveEquipment(player, compost2, "test_compost2", sessionId);

        // Beehive items
        ItemStack honey1 = new ItemStack(Material.HONEY_BOTTLE);
        ItemStack honey2 = new ItemStack(Material.HONEYCOMB);
        equipmentManager.giveEquipment(player, honey1, "test_honey1", sessionId);
        equipmentManager.giveEquipment(player, honey2, "test_honey2", sessionId);

        // Brewing Stand items
        ItemStack potion1 = new ItemStack(Material.POTION);
        ItemStack potion2 = new ItemStack(Material.SPLASH_POTION);
        equipmentManager.giveEquipment(player, potion1, "test_potion1", sessionId);
        equipmentManager.giveEquipment(player, potion2, "test_potion2", sessionId);

        // Villager Job Block items
        // Cartography Table
        ItemStack paper = new ItemStack(Material.PAPER, 16);
        ItemStack map = new ItemStack(Material.MAP);
        equipmentManager.giveEquipment(player, paper, "test_paper", sessionId);
        equipmentManager.giveEquipment(player, map, "test_map", sessionId);

        // Loom
        ItemStack banner = new ItemStack(Material.WHITE_BANNER);
        ItemStack dye = new ItemStack(Material.RED_DYE);
        equipmentManager.giveEquipment(player, banner, "test_banner", sessionId);
        equipmentManager.giveEquipment(player, dye, "test_dye", sessionId);

        // Smithing Table
        ItemStack netherite = new ItemStack(Material.NETHERITE_INGOT);
        ItemStack diamond = new ItemStack(Material.DIAMOND);
        equipmentManager.giveEquipment(player, netherite, "test_netherite", sessionId);
        equipmentManager.giveEquipment(player, diamond, "test_diamond", sessionId);

        // Stonecutter
        ItemStack stone = new ItemStack(Material.STONE, 16);
        ItemStack andesite = new ItemStack(Material.ANDESITE, 16);
        equipmentManager.giveEquipment(player, stone, "test_stone", sessionId);
        equipmentManager.giveEquipment(player, andesite, "test_andesite", sessionId);

        // Grindstone
        ItemStack damagedSword = new ItemStack(Material.IRON_SWORD);
        ItemStack damagedPickaxe = new ItemStack(Material.IRON_PICKAXE);
        equipmentManager.giveEquipment(player, damagedSword, "test_damaged_sword", sessionId);
        equipmentManager.giveEquipment(player, damagedPickaxe, "test_damaged_pickaxe", sessionId);

        // Anvil
        ItemStack enchantedBook = new ItemStack(Material.ENCHANTED_BOOK);
        ItemStack ironSword = new ItemStack(Material.IRON_SWORD);
        equipmentManager.giveEquipment(player, enchantedBook, "test_enchanted_book", sessionId);
        equipmentManager.giveEquipment(player, ironSword, "test_iron_sword", sessionId);

        // Shulker Box test
        ItemStack shulker = new ItemStack(Material.SHULKER_BOX);
        equipmentManager.giveEquipment(player, shulker, "test_shulker", sessionId);
    }
} 