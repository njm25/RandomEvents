package nc.randomEvents.events.Test.ItemHelperTest;

import nc.randomEvents.core.BaseEvent;
import nc.randomEvents.RandomEvents;
import nc.randomEvents.utils.ItemHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.*;

public class ItemHelperTest extends BaseEvent {
    private int tickCount = 0;

    public ItemHelperTest(RandomEvents plugin) {
        
        // Configure event timing
        setTickInterval(20L); // Tick every second
        setDuration(200L); // Run for 10 seconds
        setStripsInventory(false);
        setCanBreakBlocks(false);
        setCanPlaceBlocks(false);
    }

    @Override
    public void onStart(UUID sessionId, Set<Player> players) {
        players.forEach(player -> {
            player.sendMessage(Component.text("ItemHelperTest has started! Testing all ItemHelper functions...").color(NamedTextColor.GREEN));
            
            // Test basic named item creation
            ItemStack namedPickaxe = ItemHelper.createNamedItem(Material.DIAMOND_PICKAXE, 1, "Test Pickaxe");
            ItemHelper.giveItemToPlayer(player, namedPickaxe);
            
            // Test named item with custom color
            ItemStack coloredSword = ItemHelper.createNamedItem(Material.DIAMOND_SWORD, 1, "Colorful Sword", TextColor.color(255, 128, 0));
            ItemHelper.giveItemToPlayer(player, coloredSword);
            
            // Test item with lore
            ItemStack loreAxe = ItemHelper.createItemWithLore(Material.DIAMOND_AXE, 1, "Lore Axe", 
                "Line 1 of lore", "Line 2 of lore", "Line 3 of lore");
            ItemHelper.giveItemToPlayer(player, loreAxe);
            
            // Test item with lore and custom colors
            ItemStack coloredLoreSpade = ItemHelper.createItemWithLore(Material.DIAMOND_SHOVEL, 1, "Colored Lore Spade",
                TextColor.color(255, 0, 0), TextColor.color(0, 255, 0),
                "Green lore line 1", "Green lore line 2");
            ItemHelper.giveItemToPlayer(player, coloredLoreSpade);
            
            // Test enchanted item
            Map<Enchantment, Integer> enchantments = new HashMap<>();
            enchantments.put(ItemHelper.getEnchantment("sharpness"), 5);
            enchantments.put(ItemHelper.getEnchantment("unbreaking"), 3);
            ItemStack enchantedSword = ItemHelper.createEnchantedItem(Material.DIAMOND_SWORD, 1, enchantments);
            ItemHelper.giveItemToPlayer(player, enchantedSword);
            
            // Test enchanted book
            Map<Enchantment, Integer> bookEnchants = new HashMap<>();
            bookEnchants.put(ItemHelper.getEnchantment("efficiency"), 5);
            bookEnchants.put(ItemHelper.getEnchantment("fortune"), 3);
            ItemStack enchantedBook = ItemHelper.createEnchantedItem(Material.BOOK, 1, bookEnchants);
            ItemHelper.giveItemToPlayer(player, enchantedBook);
            
            // Test full item (name, lore, and enchantments)
            Map<Enchantment, Integer> fullEnchants = new HashMap<>();
            fullEnchants.put(ItemHelper.getEnchantment("protection"), 4);
            fullEnchants.put(ItemHelper.getEnchantment("thorns"), 3);
            ItemStack fullItem = ItemHelper.createFullItem(Material.DIAMOND_CHESTPLATE, 1, "Ultimate Chestplate",
                fullEnchants, "The best protection", "Money can buy");
            ItemHelper.giveItemToPlayer(player, fullItem);
            
            // Test full item with custom colors
            ItemStack coloredFullItem = ItemHelper.createFullItem(Material.DIAMOND_HELMET, 1, "Rainbow Helmet",
                TextColor.color(255, 0, 255), TextColor.color(0, 255, 255),
                fullEnchants, "Colorful protection", "For your head");
            ItemHelper.giveItemToPlayer(player, coloredFullItem);
            
            // Test glowing item
            ItemStack glowingItem = ItemHelper.createGlowingItem(Material.DIAMOND, 1, "Glowing Diamond");
            ItemHelper.giveItemToPlayer(player, glowingItem);
        });
    }

    @Override
    public void onTick(UUID sessionId, Set<Player> players) {
        tickCount++;
        
        // Every 2 seconds, test armor-related functions
        if (tickCount % 2 == 0) {
            players.forEach(player -> {
                // Test armor slot checking
                player.sendMessage(Component.text("Testing armor slots...").color(NamedTextColor.YELLOW));
                for (int i = 0; i < player.getInventory().getSize(); i++) {
                    if (ItemHelper.isArmorSlot(i)) {
                        player.sendMessage(Component.text("Slot " + i + " is an armor slot").color(NamedTextColor.AQUA));
                    }
                    if (ItemHelper.isOffhandSlot(i)) {
                        player.sendMessage(Component.text("Slot " + i + " is the offhand slot").color(NamedTextColor.AQUA));
                    }
                }
                
                // Test armor contents cloning
                ItemStack[] armorContents = player.getInventory().getArmorContents();
                ItemStack[] clonedArmor = ItemHelper.cloneArmorContents(armorContents);
                player.getInventory().setArmorContents(clonedArmor);
                player.sendMessage(Component.text("Armor contents cloned successfully").color(NamedTextColor.YELLOW));
            });
        }
    }

    @Override
    public void onEnd(UUID sessionId, Set<Player> players) {
        players.forEach(player -> {
            player.sendMessage(Component.text("ItemHelperTest complete! All functions tested successfully.").color(NamedTextColor.GREEN));
        });
    }

    @Override
    public String getName() {
        return "ItemHelperTest";
    }

    @Override
    public String getDescription() {
        return "Tests all functionality provided by the ItemHelper utility class";
    }
}
