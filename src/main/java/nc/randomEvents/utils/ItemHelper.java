package nc.randomEvents.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ItemHelper {
    
    /**
     * Gives an item to a player, dropping it if their inventory is full
     * @param player The player to give the item to
     * @param item The item to give
     */
    public static void giveItemToPlayer(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
        Location loc = player.getLocation();
        for (ItemStack drop : leftover.values()) {
            loc.getWorld().dropItem(loc, drop);
        }
    }

    /**
     * Creates an item with custom name
     * @param material The material of the item
     * @param count The amount of the item
     * @param name The name of the item
     * @return The created ItemStack
     */
    public static ItemStack createNamedItem(Material material, int count, String name) {
        return createNamedItem(material, count, name, NamedTextColor.YELLOW);
    }

    /**
     * Creates an item with custom name and color
     * @param material The material of the item
     * @param count The amount of the item
     * @param name The name of the item
     * @param color The color of the name
     * @return The created ItemStack
     */
    public static ItemStack createNamedItem(Material material, int count, String name, TextColor color) {
        ItemStack item = new ItemStack(material, Math.max(1, count));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name).color(color));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Creates an item with custom name and lore
     * @param material The material of the item
     * @param count The amount of the item
     * @param name The name of the item
     * @param lore The lore lines
     * @return The created ItemStack
     */
    public static ItemStack createItemWithLore(Material material, int count, String name, String... lore) {
        return createItemWithLore(material, count, name, NamedTextColor.YELLOW, NamedTextColor.GRAY, lore);
    }

    /**
     * Creates an item with custom name, colors, and lore
     * @param material The material of the item
     * @param count The amount of the item
     * @param name The name of the item
     * @param titleColor The color of the name
     * @param loreColor The color of the lore
     * @param lore The lore lines
     * @return The created ItemStack
     */
    public static ItemStack createItemWithLore(Material material, int count, String name, TextColor titleColor, TextColor loreColor, String... lore) {
        ItemStack item = createNamedItem(material, count, name, titleColor);
        ItemMeta meta = item.getItemMeta();
        if (meta != null && lore.length > 0) {
            List<Component> loreList = new ArrayList<>();
            for (String line : lore) {
                loreList.add(Component.text(line).color(loreColor));
            }
            meta.lore(loreList);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Creates an item with enchantments
     * @param material The material of the item
     * @param count The amount of the item
     * @param enchantments Map of enchantments and their levels
     * @return The created ItemStack
     */
    public static ItemStack createEnchantedItem(Material material, int count, Map<Enchantment, Integer> enchantments) {
        ItemStack item = new ItemStack(material, Math.max(1, count));
        
        if (material == Material.BOOK && enchantments != null && !enchantments.isEmpty()) {
            // Convert to enchanted book by creating a new ItemStack
            item = new ItemStack(Material.ENCHANTED_BOOK, item.getAmount());
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
            if (meta != null) {
                for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                    meta.addStoredEnchant(entry.getKey(), entry.getValue(), true);
                }
                item.setItemMeta(meta);
            }
        } else {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && enchantments != null) {
                for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                    meta.addEnchant(entry.getKey(), entry.getValue(), true);
                }
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    /**
     * Creates an item with name, lore, and enchantments
     * @param material The material of the item
     * @param count The amount of the item
     * @param name The name of the item
     * @param enchantments Map of enchantments and their levels
     * @param lore The lore lines
     * @return The created ItemStack
     */
    public static ItemStack createFullItem(Material material, int count, String name, Map<Enchantment, Integer> enchantments, String... lore) {
        return createFullItem(material, count, name, NamedTextColor.YELLOW, NamedTextColor.GRAY, enchantments, lore);
    }

    /**
     * Creates an item with name, colors, lore, and enchantments
     * @param material The material of the item
     * @param count The amount of the item
     * @param name The name of the item
     * @param titleColor The color of the name
     * @param loreColor The color of the lore
     * @param enchantments Map of enchantments and their levels
     * @param lore The lore lines
     * @return The created ItemStack
     */
    public static ItemStack createFullItem(Material material, int count, String name, TextColor titleColor, TextColor loreColor, Map<Enchantment, Integer> enchantments, String... lore) {
        ItemStack item = createItemWithLore(material, count, name, titleColor, loreColor, lore);
        
        if (material == Material.BOOK && enchantments != null && !enchantments.isEmpty()) {
            // Convert to enchanted book by creating a new ItemStack
            item = new ItemStack(Material.ENCHANTED_BOOK, item.getAmount());
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
            if (meta != null) {
                for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                    meta.addStoredEnchant(entry.getKey(), entry.getValue(), true);
                }
                item.setItemMeta(meta);
            }
        } else {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && enchantments != null) {
                for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                    meta.addEnchant(entry.getKey(), entry.getValue(), true);
                }
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    /**
     * Creates a visual-only enchanted item (glowing effect)
     * @param material The material of the item
     * @param count The amount of the item
     * @param name The name of the item
     * @return The created ItemStack
     */
    public static ItemStack createGlowingItem(Material material, int count, String name) {
        ItemStack item = createNamedItem(material, count, name);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.LURE, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Helper method to get an enchantment from its name
     * @param name The name of the enchantment (e.g. "efficiency", "sharpness")
     * @return The Enchantment, or null if not found
     */
    public static Enchantment getEnchantment(String name) {
        NamespacedKey key = NamespacedKey.minecraft(name.toLowerCase());
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key);
    }
} 