package nc.randomEvents;

import org.bukkit.plugin.java.JavaPlugin;

import nc.randomEvents.commands.CommandManager;
import nc.randomEvents.services.EventManager;
import nc.randomEvents.services.DataManager;

public final class RandomEvents extends JavaPlugin {

    private EventManager eventManager;
    private DataManager dataManager;

    @Override
    public void onEnable() {
        // Plugin startup logic
        dataManager = new DataManager(this);
        eventManager = new EventManager(this, dataManager);
        new CommandManager(this, eventManager);
        
        getLogger().info("RandomEvents has been enabled!");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public DataManager getDataManager() {
        return dataManager;
    }
}
