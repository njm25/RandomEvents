package nc.randomEvents.services;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.core.PluginData;
import nc.randomEvents.data.PlayerData;
import nc.randomEvents.data.WorldData;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

interface IDataManager {
    void register(Class<? extends PluginData> dataClass, String sectionPath);
    <T extends PluginData> T get(Class<T> dataClass, String id);
    <T extends PluginData> void set(String id, T data);
    <T extends PluginData> boolean remove(Class<T> dataClass, String id);
    <T extends PluginData> Collection<T> getAll(Class<T> dataClass);
}

public class DataManager implements IDataManager {
    private final RandomEvents plugin;
    private FileConfiguration dataConfig = null;
    private File configFile = null;
    private final Gson gson;
    
    // Registry for data types and their config paths
    private final Map<Class<? extends PluginData>, String> registeredTypes = new ConcurrentHashMap<>();
    private final Map<Class<? extends PluginData>, Map<String, PluginData>> cache = new ConcurrentHashMap<>();
    // Registry for field metadata to optimize serialization/deserialization
    private final Map<Class<? extends PluginData>, List<FieldMetadata>> fieldMetadata = new ConcurrentHashMap<>();

    public DataManager(RandomEvents plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder()
            .serializeNulls()
            .create();
        saveDefaultConfig();
        reloadData();
        
        // Register data types
        register(WorldData.class, "accepted-worlds");
        register(PlayerData.class, "players");
    }

    /**
     * Register a data type with its configuration section path
     * Dynamically analyzes the class structure for optimal serialization
     */
    @Override
    public void register(Class<? extends PluginData> dataClass, String sectionPath) {
        registeredTypes.put(dataClass, sectionPath);
        cache.put(dataClass, new ConcurrentHashMap<>());
        
        // Analyze class structure and cache field metadata
        analyzeClassStructure(dataClass);
        
        loadDataType(dataClass);
    }
    
    /**
     * Dynamically analyze the class structure to optimize serialization/deserialization
     */
    private void analyzeClassStructure(Class<? extends PluginData> dataClass) {
        List<FieldMetadata> fields = new ArrayList<>();
        
        // Get all declared fields including inherited ones
        Class<?> currentClass = dataClass;
        while (currentClass != null && currentClass != Object.class) {
            for (Field field : currentClass.getDeclaredFields()) {
                // Skip static, transient, and synthetic fields
                if (Modifier.isStatic(field.getModifiers()) || 
                    Modifier.isTransient(field.getModifiers()) ||
                    field.isSynthetic()) {
                    continue;
                }
                
                fields.add(new FieldMetadata(field));
            }
            currentClass = currentClass.getSuperclass();
        }
        
        fieldMetadata.put(dataClass, fields);
        
        // Log discovered fields for debugging
        plugin.getLogger().info("Registered " + dataClass.getSimpleName() + 
                               " with " + fields.size() + " serializable fields: " +
                               fields.stream().map(f -> f.name).reduce((a, b) -> a + ", " + b).orElse("none"));
    }

    /**
     * Get a data instance by type and ID
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T extends PluginData> T get(Class<T> dataClass, String id) {
        if (!registeredTypes.containsKey(dataClass)) {
            throw new IllegalArgumentException("Data type " + dataClass.getSimpleName() + " is not registered");
        }
        
        Map<String, PluginData> typeCache = cache.get(dataClass);
        return (T) typeCache.get(id);
    }

    /**
     * Set/save a data instance
     */
    public <T extends PluginData> void set(String id, T data) {
        Class<? extends PluginData> dataClass = data.getClass();
        if (!registeredTypes.containsKey(dataClass)) {
            throw new IllegalArgumentException("Data type " + dataClass.getSimpleName() + " is not registered");
        }

        // Update cache
        Map<String, PluginData> typeCache = cache.get(dataClass);
        typeCache.put(id, data);

        // Save to config
        saveDataInstance(data);
    }

    /**
     * Remove a data instance
     */
    public <T extends PluginData> boolean remove(Class<T> dataClass, String id) {
        if (!registeredTypes.containsKey(dataClass)) {
            throw new IllegalArgumentException("Data type " + dataClass.getSimpleName() + " is not registered");
        }

        Map<String, PluginData> typeCache = cache.get(dataClass);
        PluginData removed = typeCache.remove(id);
        
        if (removed != null) {
            String sectionPath = registeredTypes.get(dataClass);
            getData().set(sectionPath + "." + id, null);
            saveData();
            return true;
        }
        return false;
    }

    /**
     * Get all instances of a data type
     */
    @SuppressWarnings("unchecked")
    public <T extends PluginData> Collection<T> getAll(Class<T> dataClass) {
        if (!registeredTypes.containsKey(dataClass)) {
            throw new IllegalArgumentException("Data type " + dataClass.getSimpleName() + " is not registered");
        }

        Map<String, PluginData> typeCache = cache.get(dataClass);
        return (Collection<T>) typeCache.values();
    }

    /**
     * Load all data for a specific type from config
     */
    private <T extends PluginData> void loadDataType(Class<T> dataClass) {
        String sectionPath = registeredTypes.get(dataClass);
        ConfigurationSection section = getData().getConfigurationSection(sectionPath);
        Map<String, PluginData> typeCache = cache.get(dataClass);
        
        if (section == null) {
            // Create the section if it doesn't exist
            getData().createSection(sectionPath);
            saveData();
            return;
        }

        for (String id : section.getKeys(false)) {
            try {
                ConfigurationSection instanceSection = section.getConfigurationSection(id);
                if (instanceSection == null) continue;

                T instance = deserializeFromConfig(dataClass, instanceSection);
                if (instance != null) {
                    typeCache.put(id, instance);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, 
                    "Failed to load " + dataClass.getSimpleName() + " with id '" + id + "', skipping.", e);
            }
        }
    }

    /**
     * Save a single data instance to config
     */
    private void saveDataInstance(PluginData data) {
        String sectionPath = registeredTypes.get(data.getClass());
        String id = data.getId();
        
        try {
            serializeToConfig(data, sectionPath + "." + id);
            saveData();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, 
                "Failed to save " + data.getClass().getSimpleName() + " with id '" + id + "'", e);
        }
    }

    /**
     * Serialize a data object to config using cached field metadata
     */
    private void serializeToConfig(PluginData data, String basePath) throws IllegalAccessException {
        Class<?> clazz = data.getClass();
        
        // Clear existing data at this path
        getData().set(basePath, null);
        
        // Use cached field metadata for efficient serialization
        List<FieldMetadata> fields = fieldMetadata.get(clazz);
        if (fields == null) {
            plugin.getLogger().warning("No field metadata found for " + clazz.getSimpleName() + 
                                     ". Make sure it's properly registered.");
            return;
        }
        
        for (FieldMetadata fieldMeta : fields) {
            Object value = fieldMeta.field.get(data);
            if (value == null) continue;
            
            String fieldPath = basePath + "." + fieldMeta.name;
            
            // Handle different field types based on cached metadata
            if (fieldMeta.isDirectlySerializable) {
                getData().set(fieldPath, value);
            } else if (fieldMeta.isUUID) {
                getData().set(fieldPath, value.toString());
            } else if (fieldMeta.isLocation) {
                Location loc = (Location) value;
                getData().set(fieldPath + ".world", loc.getWorld().getName());
                getData().set(fieldPath + ".x", loc.getBlockX());
                getData().set(fieldPath + ".y", loc.getBlockY());
                getData().set(fieldPath + ".z", loc.getBlockZ());
            } else if (fieldMeta.isEnum) {
                getData().set(fieldPath, ((Enum<?>) value).name());
            } else if (fieldMeta.isConfigSerializable) {
                getData().set(fieldPath, ((ConfigurationSerializable) value).serialize());
            } else if (fieldMeta.needsJsonSerialization) {
                // For complex objects, store as JSON
                getData().set(fieldPath, gson.toJson(value));
            } else {
                // Fallback to string representation
                getData().set(fieldPath, value.toString());
            }
        }
    }

    /**
     * Deserialize a data object from config using cached field metadata
     */
    private <T extends PluginData> T deserializeFromConfig(Class<T> dataClass, ConfigurationSection section) throws Exception {
        // Try to find a suitable constructor
        Constructor<T> constructor = findBestConstructor(dataClass);
        
        if (constructor == null) {
            throw new IllegalArgumentException("No suitable constructor found for " + dataClass.getSimpleName());
        }

        // Get constructor parameters
        Class<?>[] paramTypes = constructor.getParameterTypes();
        Object[] params = new Object[paramTypes.length];
        
        // Use cached field metadata for efficient deserialization
        List<FieldMetadata> fields = fieldMetadata.get(dataClass);
        if (fields == null) {
            plugin.getLogger().warning("No field metadata found for " + dataClass.getSimpleName() + 
                                     ". Make sure it's properly registered.");
            throw new IllegalStateException("Class not properly registered: " + dataClass.getSimpleName());
        }
        
        Map<String, Object> fieldValues = new HashMap<>();
        
        // Extract values from config using cached metadata
        for (FieldMetadata fieldMeta : fields) {
            if (!Modifier.isTransient(fieldMeta.field.getModifiers())) {
                Object value = deserializeFieldValue(fieldMeta, section);
                if (value != null) {
                    fieldValues.put(fieldMeta.name, value);
                }
            }
        }

        // Match constructor parameters with field values
        for (int i = 0; i < paramTypes.length; i++) {
            Class<?> paramType = paramTypes[i];
            
            // Find a field value that matches this parameter type
            for (Map.Entry<String, Object> entry : fieldValues.entrySet()) {
                if (paramType.isAssignableFrom(entry.getValue().getClass()) || 
                    (paramType.isPrimitive() && isCompatiblePrimitive(paramType, entry.getValue().getClass()))) {
                    params[i] = entry.getValue();
                    break;
                }
            }
        }

        T instance = constructor.newInstance(params);

        // Find and set the transient field that corresponds to the ID
        String id = section.getName();
        for (FieldMetadata fieldMeta : fields) {
            if (Modifier.isTransient(fieldMeta.field.getModifiers())) {
                // Get the getId() value after hypothetically setting this field
                try {
                    Object oldValue = fieldMeta.field.get(instance);
                    Object newValue = convertIdToFieldType(id, fieldMeta.type);
                    if (newValue != null) {
                        fieldMeta.field.set(instance, newValue);
                        String generatedId = instance.getId();
                        if (id.equals(generatedId)) {
                            // We found the right field, keep the value set
                            break;
                        }
                        // Not the right field, restore old value
                        fieldMeta.field.set(instance, oldValue);
                    }
                } catch (Exception e) {
                    // Skip this field if we can't set it
                    plugin.getLogger().fine("Skipping transient field " + fieldMeta.name + 
                                          " for " + dataClass.getSimpleName() + ": " + e.getMessage());
                }
            }
        }

        return instance;
    }

    /**
     * Convert an ID string to the appropriate type for a field
     */
    private Object convertIdToFieldType(String id, Class<?> targetType) {
        try {
            if (targetType == String.class) {
                return id;
            } else if (targetType == UUID.class) {
                return UUID.fromString(id);
            } else if (targetType == Integer.class || targetType == int.class) {
                return Integer.parseInt(id);
            } else if (targetType == Long.class || targetType == long.class) {
                return Long.parseLong(id);
            }
            // Add more type conversions as needed
        } catch (Exception e) {
            // Return null if conversion fails
        }
        return null;
    }

    /**
     * Deserialize a field value from config using field metadata
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Object deserializeFieldValue(FieldMetadata fieldMeta, ConfigurationSection section) {
        if (!section.contains(fieldMeta.name)) {
            return null;
        }

        if (fieldMeta.isUUID) {
            String uuidStr = section.getString(fieldMeta.name);
            return uuidStr != null ? UUID.fromString(uuidStr) : null;
        } else if (fieldMeta.isLocation) {
            ConfigurationSection locSection = section.getConfigurationSection(fieldMeta.name);
            if (locSection != null) {
                String worldName = locSection.getString("world");
                int x = locSection.getInt("x");
                int y = locSection.getInt("y");
                int z = locSection.getInt("z");
                return new Location(plugin.getServer().getWorld(worldName), x, y, z);
            }
        } else if (fieldMeta.isEnum) {
            String enumName = section.getString(fieldMeta.name);
            if (enumName != null) {
                Class<? extends Enum> enumClass = (Class<? extends Enum>) fieldMeta.type;
                return Enum.valueOf(enumClass, enumName);
            }
        } else if (fieldMeta.isConfigSerializable) {
            try {
                ConfigurationSection subSection = section.getConfigurationSection(fieldMeta.name);
                if (subSection != null) {
                    Map<String, Object> serialized = subSection.getValues(true);
                    return fieldMeta.type.getConstructor(Map.class).newInstance(serialized);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to deserialize ConfigurationSerializable field " + 
                                        fieldMeta.name + " of type " + fieldMeta.type.getName() + ": " + e.getMessage());
            }
        } else if (fieldMeta.needsJsonSerialization) {
            String json = section.getString(fieldMeta.name);
            return json != null ? gson.fromJson(json, fieldMeta.type) : null;
        } else if (fieldMeta.isDirectlySerializable) {
            return section.get(fieldMeta.name);
        }
        
        return null;
    }

    /**
     * Find the best constructor for deserialization
     */
    private <T> Constructor<T> findBestConstructor(Class<T> clazz) {
        Constructor<?>[] constructors = clazz.getConstructors();
        
        // Prefer constructors with more parameters (likely the main constructor)
        Arrays.sort(constructors, (a, b) -> Integer.compare(b.getParameterCount(), a.getParameterCount()));
        
        @SuppressWarnings("unchecked")
        Constructor<T> bestConstructor = (Constructor<T>) constructors[0];
        return bestConstructor;
    }

    /**
     * Check if primitive types are compatible
     */
    private boolean isCompatiblePrimitive(Class<?> primitiveType, Class<?> wrapperType) {
        Map<Class<?>, Class<?>> primitiveToWrapper = Map.of(
            int.class, Integer.class,
            long.class, Long.class,
            double.class, Double.class,
            float.class, Float.class,
            boolean.class, Boolean.class,
            byte.class, Byte.class,
            short.class, Short.class,
            char.class, Character.class
        );
        
        return primitiveToWrapper.get(primitiveType) == wrapperType;
    }

    // File management methods
    public void reloadData() {
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "data.yml");
        }
        dataConfig = YamlConfiguration.loadConfiguration(configFile);

        // Reload all registered types
        for (Class<? extends PluginData> dataClass : registeredTypes.keySet()) {
            cache.put(dataClass, new ConcurrentHashMap<>());
            loadDataType(dataClass);
        }
    }

    public FileConfiguration getData() {
        if (dataConfig == null) {
            reloadData();
        }
        return dataConfig;
    }

    public void saveData() {
        if (dataConfig == null || configFile == null) {
            return;
        }
        try {
            getData().save(configFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save config to " + configFile, ex);
        }
    }

    public void saveDefaultConfig() {
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "data.yml");
        }
        if (!configFile.exists()) {
            plugin.saveResource("data.yml", false);
        }
    }

}

/**
 * Metadata class to store field information for efficient serialization
 */
class FieldMetadata {
    final Field field;
    final String name;
    final Class<?> type;
    final boolean isDirectlySerializable;
    final boolean isEnum;
    final boolean isUUID;
    final boolean isLocation;
    final boolean isConfigSerializable;
    final boolean isCollection;
    final boolean needsJsonSerialization;
    
    public FieldMetadata(Field field) {
        this.field = field;
        this.name = field.getName();
        this.type = field.getType();
        field.setAccessible(true);
        
        // Analyze type characteristics
        this.isDirectlySerializable = isDirectlySerializable(type);
        this.isEnum = type.isEnum();
        this.isUUID = type == UUID.class;
        this.isLocation = type == Location.class;
        this.isConfigSerializable = ConfigurationSerializable.class.isAssignableFrom(type);
        this.isCollection = Collection.class.isAssignableFrom(type);
        
        // If none of the above, we'll use JSON serialization
        this.needsJsonSerialization = !(isDirectlySerializable || isEnum || isUUID || 
                                      isLocation || isConfigSerializable || 
                                      (isCollection && isCollectionOfSerializables()));
    }
    
    private boolean isDirectlySerializable(Class<?> type) {
        return type == String.class || Number.class.isAssignableFrom(type) || 
               type == boolean.class || type == Boolean.class ||
               type.isPrimitive();
    }
    
    private boolean isCollectionOfSerializables() {
        if (!isCollection) return false;
        
        // Try to determine collection's generic type
        try {
            java.lang.reflect.Type genericType = field.getGenericType();
            if (genericType instanceof java.lang.reflect.ParameterizedType) {
                java.lang.reflect.ParameterizedType pt = (java.lang.reflect.ParameterizedType) genericType;
                java.lang.reflect.Type[] typeArgs = pt.getActualTypeArguments();
                if (typeArgs.length > 0 && typeArgs[0] instanceof Class<?>) {
                    Class<?> elementType = (Class<?>) typeArgs[0];
                    return isDirectlySerializable(elementType) || 
                           elementType.isEnum() || 
                           elementType == UUID.class ||
                           elementType == Location.class ||
                           ConfigurationSerializable.class.isAssignableFrom(elementType);
                }
            }
        } catch (Exception e) {
            // If we can't determine the generic type, assume it needs JSON serialization
            return false;
        }
        return false;
    }
}
