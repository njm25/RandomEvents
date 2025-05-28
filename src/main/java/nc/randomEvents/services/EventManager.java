package nc.randomEvents.services;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.services.events.Event;
import nc.randomEvents.services.events.LootGoblin.LootGoblinEvent;
import nc.randomEvents.services.events.Meteor.MeteorEvent;
import nc.randomEvents.services.events.Quest.QuestEvent;
import nc.randomEvents.services.events.Sheepocalypse.SheepocalypseEvent;
import nc.randomEvents.services.events.Sound.SoundEvent;
import nc.randomEvents.services.events.ZombieHoard.ZombieHoardEvent;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class EventManager {
    private final Map<String, Event> events = new HashMap<>();
    private final RandomEvents plugin;
    private final DataManager dataManager;

    public EventManager(RandomEvents plugin, DataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        registerEvents();
    }

    private void registerEvents() {
        addEvent(new SoundEvent());
        addEvent(new MeteorEvent(plugin));
        addEvent(new LootGoblinEvent(plugin));
        addEvent(new ZombieHoardEvent(plugin));
        addEvent(new QuestEvent(plugin));
        addEvent(new SheepocalypseEvent(plugin));
        // Register other events here
    }

    public void addEvent(Event event) {
        events.put(event.getName().toLowerCase(), event);
    }

    public boolean startEvent(String eventName) {
        Event event = events.get(eventName.toLowerCase());
        if (event != null) {
            List<String> acceptedWorlds = dataManager.getAcceptedWorlds();
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
            event.execute(playersInAcceptedWorlds);
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
}
