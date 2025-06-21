package nc.randomEvents.core;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.data.PlayerData;
import nc.randomEvents.services.DataManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class EventSession {
    private final UUID sessionId;
    private final BaseEvent event;
    private final Set<Player> players;
    private final RandomEvents plugin;
    private BukkitTask tickTask;
    private BukkitTask endTask;
    private boolean isEnded = false;
    private final DataManager dataManager;
    
    public EventSession(RandomEvents plugin, BaseEvent event, Set<Player> players) {
        this.sessionId = UUID.randomUUID();
        this.plugin = plugin;
        this.event = event;
        this.players = new HashSet<>(players);
        this.dataManager = plugin.getDataManager();
        start();
    }
    
    /**
     * Start the event session and schedule its tasks
     */
    public void start() {
        if (isEnded) {
            throw new IllegalStateException("Cannot start an ended session");
        }
        if (event.getMaxPlayers() > 0 && getPlayers().size() > event.getMaxPlayers()) {
            throw new IllegalStateException("Cannot start an event with more players than the max players");
        }
        
        // Start the event
        plugin.getSessionRegistry().registerSession(this);
        event.onStart(sessionId, getPlayers());
        
        // Schedule periodic ticks if interval > 0
        if (event.getTickInterval() > 0) {
            tickTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                () -> event.onTick(sessionId, getPlayers()),
                event.getTickInterval(),
                event.getTickInterval()
            );
        }
        
        // Schedule the end task if duration > 0
        if (event.getDuration() > 0) {
            endTask = plugin.getServer().getScheduler().runTaskLater(
                plugin,
                this::end,
                event.getDuration()
            );
        }
    }
    
    /**
     * End the event session and cleanup tasks
     */
    public void end() {
        if (isEnded) {
            return;
        }
        
        isEnded = true;
        
        // Cancel scheduled tasks
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (endTask != null) {
            endTask.cancel();
            endTask = null;
        }
        
        // End the event
        event.onEnd(sessionId, getPlayers());
        plugin.getSessionRegistry().unregisterSession(sessionId);

        for (Player player : getPlayers()) {
            PlayerData playerData = dataManager.get(PlayerData.class, player.getUniqueId().toString());
            if (playerData != null) {
                playerData.eventsParticipated++;
                dataManager.set(playerData.getId(), playerData);
            }
        }
    }
    
    /**
     * @return The unique identifier for this session
     */
    public UUID getSessionId() {
        return sessionId;
    }
    
    /**
     * @return An unmodifiable view of the players in this session
     */
    public Set<Player> getPlayers() {
        return Collections.unmodifiableSet(players);
    }
    
    /**
     * @return The event instance for this session
     */
    public BaseEvent getEvent() {
        return event;
    }
    
    /**
     * @return Whether this session has ended
     */
    public boolean isEnded() {
        return isEnded;
    }
}
