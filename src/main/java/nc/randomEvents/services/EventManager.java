package nc.randomEvents.services;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.core.BaseEvent;
import nc.randomEvents.core.EventSession;
import nc.randomEvents.events.Test2.Test2Event;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class EventManager {
    private final Map<String, BaseEvent> events = new HashMap<>();
    private final RandomEvents plugin;
    private final SessionRegistry sessionRegistry;

    public EventManager(RandomEvents plugin) {
        this.plugin = plugin;
        this.sessionRegistry = plugin.getSessionRegistry();
        registerEvents();
    }

    private void registerEvents() {
        // For now, only register our new test event
        addEvent(new Test2Event(plugin));
    }

    public void addEvent(BaseEvent event) {
        events.put(event.getName().toLowerCase(), event);
    }

    public boolean startEvent(String eventName) {
        BaseEvent event = events.get(eventName.toLowerCase());
        if (event != null) {
            List<String> acceptedWorlds = plugin.getDataManager().getAcceptedWorlds();
            if (acceptedWorlds.isEmpty()) {
                plugin.getLogger().info("No accepted worlds configured. Event '" + eventName + "' will not run. Use /randomevents addworld <worldname>");
                return false;
            }

            Set<Player> playersInAcceptedWorlds = Bukkit.getOnlinePlayers().stream()
                    .filter(player -> acceptedWorlds.contains(player.getWorld().getName().toLowerCase()))
                    .collect(Collectors.toSet());

            if (playersInAcceptedWorlds.isEmpty()) {
                plugin.getLogger().info("No players online in accepted worlds to start event: " + eventName);
                return false;
            }

            // Create and start a new session
            new EventSession(plugin, event, playersInAcceptedWorlds);

            plugin.getLogger().info("Started event: " + eventName + " for players in accepted worlds: " + String.join(", ", acceptedWorlds));
            return true;
        } else {
            plugin.getLogger().warning("Event not found: " + eventName);
            return false;
        }
    }

    public List<String> getEventNames() {
        return new ArrayList<>(events.keySet());
    }


    /**
     * Clean up all running sessions when the plugin is disabled
     */
    public void shutdown() {
        sessionRegistry.endAll();
    }
}
