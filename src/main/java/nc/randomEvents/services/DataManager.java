package nc.randomEvents.services;

import nc.randomEvents.RandomEvents;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class DataManager {
    private final RandomEvents plugin;
    private FileConfiguration dataConfig = null;
    private File configFile = null;
    private static final String WORLDS_PATH = "accepted-worlds";

    public DataManager(RandomEvents plugin) {
        this.plugin = plugin;
        saveDefaultConfig();
        reloadData(); // Load data on initialization
    }

    public void reloadData() {
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "data.yml");
        }
        dataConfig = YamlConfiguration.loadConfiguration(configFile);

        // Ensure the list path exists
        if (!dataConfig.isList(WORLDS_PATH)) {
            dataConfig.set(WORLDS_PATH, new ArrayList<String>());
            saveData();
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

    public List<String> getAcceptedWorlds() {
        return getData().getStringList(WORLDS_PATH);
    }

    public boolean addAcceptedWorld(String worldName) {
        List<String> worlds = getAcceptedWorlds();
        if (!worlds.contains(worldName.toLowerCase())) {
            worlds.add(worldName.toLowerCase());
            getData().set(WORLDS_PATH, worlds);
            saveData();
            return true;
        }
        return false;
    }

    public boolean removeAcceptedWorld(String worldName) {
        List<String> worlds = getAcceptedWorlds();
        boolean removed = worlds.remove(worldName.toLowerCase());
        if (removed) {
            getData().set(WORLDS_PATH, worlds);
            saveData();
        }
        return removed;
    }
}
