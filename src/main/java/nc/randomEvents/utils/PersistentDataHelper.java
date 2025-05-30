package nc.randomEvents.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public class PersistentDataHelper {
    public static final String PREFIX = "NCRE_";
    
    /**
     * Creates a NamespacedKey with the plugin and prefixed key
     * @param plugin The plugin instance
     * @param key The key to use (will be prefixed with NCRE_)
     * @return The created NamespacedKey
     */
    public static NamespacedKey createKey(Plugin plugin, String key) {
        return new NamespacedKey(plugin, PREFIX + key);
    }
    
    /**
     * Sets a value in a PersistentDataContainer
     * @param container The container to set the value in
     * @param plugin The plugin instance
     * @param key The key to use (will be prefixed with NCRE_)
     * @param type The PersistentDataType to use
     * @param value The value to set
     * @param <T> The primary object type
     * @param <Z> The retrieved object type
     */
    public static <T, Z> void set(PersistentDataContainer container, Plugin plugin, String key, 
                                PersistentDataType<T, Z> type, Z value) {
        container.set(createKey(plugin, key), type, value);
    }
    
    /**
     * Gets a value from a PersistentDataContainer
     * @param container The container to get the value from
     * @param plugin The plugin instance
     * @param key The key to use (will be prefixed with NCRE_)
     * @param type The PersistentDataType to use
     * @param <T> The primary object type
     * @param <Z> The retrieved object type
     * @return The value, or null if not found
     */
    public static <T, Z> Z get(PersistentDataContainer container, Plugin plugin, String key, 
                              PersistentDataType<T, Z> type) {
        return container.get(createKey(plugin, key), type);
    }
    
    /**
     * Checks if a PersistentDataContainer has a value
     * @param container The container to check
     * @param plugin The plugin instance
     * @param key The key to use (will be prefixed with NCRE_)
     * @param type The PersistentDataType to use
     * @param <T> The primary object type
     * @param <Z> The retrieved object type
     * @return true if the container has the value, false otherwise
     */
    public static <T, Z> boolean has(PersistentDataContainer container, Plugin plugin, String key, 
                                   PersistentDataType<T, Z> type) {
        return container.has(createKey(plugin, key), type);
    }
    
    /**
     * Removes a value from a PersistentDataContainer
     * @param container The container to remove the value from
     * @param plugin The plugin instance
     * @param key The key to use (will be prefixed with NCRE_)
     */
    public static void remove(PersistentDataContainer container, Plugin plugin, String key) {
        container.remove(createKey(plugin, key));
    }
} 