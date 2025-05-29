package nc.randomEvents.utils;

import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.metadata.Metadatable;
import org.bukkit.plugin.Plugin;

import java.util.List;

public class MetadataHelper {
    
    public static final String METADATA_PREFIX = "NCRE_";
    
    /**
     * Sets metadata on a Metadatable object
     * @param target The object to set metadata on
     * @param key The metadata key (will be automatically prefixed with NCRE_)
     * @param value The value to store
     * @param plugin The plugin instance
     */
    public static void setMetadata(Metadatable target, String key, Object value, Plugin plugin) {
        target.setMetadata(METADATA_PREFIX + key, new FixedMetadataValue(plugin, value));
    }
    
    /**
     * Checks if a Metadatable object has metadata for a given key
     * @param target The object to check metadata on
     * @param key The metadata key (will be automatically prefixed with NCRE_)
     * @return true if the metadata exists, false otherwise
     */
    public static boolean hasMetadata(Metadatable target, String key) {
        return target.hasMetadata(METADATA_PREFIX + key);
    }
    
    /**
     * Gets metadata from a Metadatable object
     * @param target The object to get metadata from
     * @param key The metadata key (will be automatically prefixed with NCRE_)
     * @return List of metadata values
     */
    public static List<MetadataValue> getMetadata(Metadatable target, String key) {
        return target.getMetadata(METADATA_PREFIX + key);
    }
    
    /**
     * Removes metadata from a Metadatable object
     * @param target The object to remove metadata from
     * @param key The metadata key (will be automatically prefixed with NCRE_)
     * @param plugin The plugin instance
     */
    public static void removeMetadata(Metadatable target, String key, Plugin plugin) {
        target.removeMetadata(METADATA_PREFIX + key, plugin);
    }
}
