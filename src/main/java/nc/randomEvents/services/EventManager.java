package nc.randomEvents.services;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.core.BaseEvent;
import nc.randomEvents.core.EventSession;
import nc.randomEvents.events.ZombieHorde.ZombieHordeEvent;
import nc.randomEvents.events.tests.BaseEventTest;
import nc.randomEvents.events.tests.EntityManagerTest;
import nc.randomEvents.events.tests.EquipmentManagerTest;
import nc.randomEvents.events.tests.ItemHelperTest;
import nc.randomEvents.events.LootGoblin.LootGoblinEvent;
import nc.randomEvents.events.Sheepocalypse.SheepocalypseEvent;
import nc.randomEvents.events.Meteor.MeteorEvent;
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
        // Register test events
        addEvent(new EquipmentManagerTest(plugin));
        addEvent(new ItemHelperTest(plugin));
        addEvent(new BaseEventTest(plugin));
        addEvent(new EntityManagerTest(plugin));
        addEvent(new LootGoblinEvent(plugin));
        addEvent(new ZombieHordeEvent(plugin));
        addEvent(new SheepocalypseEvent(plugin));
        addEvent(new MeteorEvent(plugin));
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

    public BaseEvent getEvent(String eventName) {
        return events.get(eventName.toLowerCase());
    }

    /**
     * Clean up all running sessions when the plugin is disabled
     */
    public void shutdown() {
        sessionRegistry.endAll();
    }
}
