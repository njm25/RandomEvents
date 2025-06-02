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
import java.util.HashMap;

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

    public static ItemStack createFullItem(Material material, int count, String name, TextColor titleColor, TextColor loreColor, Map<Enchantment, Integer> enchantments, String... lore) {
        ItemStack item = createItemWithLore(material, count, name, titleColor, loreColor, lore);
        
        if (material == Material.BOOK && enchantments != null && !enchantments.isEmpty()) {
            return convertToEnchantedBook(item, enchantments);
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

    private static ItemStack convertToEnchantedBook(ItemStack item, Map<Enchantment, Integer> enchantments) {
        ItemMeta originalMeta = item.getItemMeta();
        ItemStack enchantedBook = new ItemStack(Material.ENCHANTED_BOOK, item.getAmount());
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) enchantedBook.getItemMeta();
        if (meta != null) {
            // Preserve original metadata
            if (originalMeta != null) {
                if (originalMeta.hasDisplayName()) {
                    meta.displayName(originalMeta.displayName());
                }
                if (originalMeta.hasLore()) {
                    meta.lore(originalMeta.lore());
                }
            }
            for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
                meta.addStoredEnchant(entry.getKey(), entry.getValue(), true);
            }
            enchantedBook.setItemMeta(meta);
        }
        return enchantedBook;
    }

    /**
     * Checks if a material can be enchanted
     * @param material The material to check
     * @return true if the material can be enchanted, false otherwise
     */
    public static boolean isEnchantable(Material material) {
        if (material == null) return false;
        // Books can hold stored enchantments
        if (material == Material.BOOK) return true;
        // Check for tools (pickaxes, axes, shovels, hoes, shears, flint and steel, fishing rod, carrot on a stick, warped fungus on a stick)
        if (material.name().endsWith("_PICKAXE") || material.name().endsWith("_AXE") || material.name().endsWith("_SHOVEL") || material.name().endsWith("_HOE") ||
            material == Material.SHEARS || material == Material.FLINT_AND_STEEL || material == Material.FISHING_ROD ||
            material == Material.CARROT_ON_A_STICK || material == Material.WARPED_FUNGUS_ON_A_STICK) {
            return true;
        }
        // Check for weapons (swords, bows, crossbow, trident)
        if (material.name().endsWith("_SWORD") || material == Material.BOW || material == Material.CROSSBOW || material == Material.TRIDENT) {
            return true;
        }
        // Check for armor (helmets, chestplates, leggings, boots, elytra, turtle shell)
        if (material.name().endsWith("_HELMET") || material.name().endsWith("_CHESTPLATE") || material.name().endsWith("_LEGGINGS") || material.name().endsWith("_BOOTS") ||
            material == Material.ELYTRA || material == Material.TURTLE_HELMET) {
            return true;
        }

        // Check for enchanted book
        if (material == Material.ENCHANTED_BOOK) {
            return true;
        }
        // Add any other specific items that are enchantable but don't fit general categories
        // e.g. Shield, Trident
        return material == Material.SHIELD;
    }

    /**
     * Applies a list of enchantments to an item
     * @param item The item to enchant
     * @param enchantments List of enchantment entries with name and level
     * @return The enchanted item
     */
    public static ItemStack applyEnchantments(ItemStack item, List<Map<String, Object>> enchantments) {
        if (item == null || enchantments == null || enchantments.isEmpty()) {
            return item;
        }

        Map<Enchantment, Integer> enchantmentMap = new HashMap<>();
        for (Map<String, Object> enchEntry : enchantments) {
            String enchantmentName = (String) enchEntry.get("name");
            int level = enchEntry.get("level") instanceof Number ? 
                       ((Number) enchEntry.get("level")).intValue() : 1;

            Enchantment enchantment = getEnchantment(enchantmentName);
            if (enchantment != null) {
                enchantmentMap.put(enchantment, level);
            }
        }

        return createEnchantedItem(item.getType(), item.getAmount(), enchantmentMap);
    }

    /**
     * Safely clones armor contents, handling null values
     * @param armor The armor contents to clone
     * @return Cloned armor contents array
     */
    public static ItemStack[] cloneArmorContents(ItemStack[] armor) {
        if (armor == null) return new ItemStack[4];
        ItemStack[] clone = new ItemStack[4];
        for (int i = 0; i < Math.min(armor.length, 4); i++) {
            if (armor[i] != null) {
                clone[i] = armor[i].clone();
            }
        }
        return clone;
    }

    /**
     * Checks if an item is in an armor slot based on vanilla inventory indexing
     * @param slot The inventory slot index
     * @return true if it's an armor slot
     */
    public static boolean isArmorSlot(int slot) {
        return slot >= 36 && slot <= 39;
    }

    /**
     * Checks if an item is in the offhand slot based on vanilla inventory indexing
     * @param slot The inventory slot index
     * @return true if it's the offhand slot
     */
    public static boolean isOffhandSlot(int slot) {
        return slot == 40;
    }
} 