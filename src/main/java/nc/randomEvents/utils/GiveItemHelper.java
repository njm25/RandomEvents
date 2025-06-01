package nc.randomEvents.utils;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class GiveItemHelper {
    
    public static void giveItemToPlayer(Player player, ItemStack item) {
        // Attempt to add the item to the player's inventory
        if (!player.getInventory().addItem(item).isEmpty()) {   
            // Drop at player's feet if inventory is full
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
    }
}
