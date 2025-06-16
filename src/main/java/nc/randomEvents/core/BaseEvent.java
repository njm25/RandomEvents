package nc.randomEvents.core;

import org.bukkit.entity.Player;
import java.util.Set;
import java.util.UUID;

public abstract class BaseEvent {
    private long tickInterval = 20L; // Default 1 second
    private long duration = 0; // Default no duration
    private long maxPlayers = 0; // Default no max players
    private boolean stripsInventory = false; // Default no inventory stripping
    private boolean canBreakBlocks = true; // Default can break blocks
    private boolean canPlaceBlocks = true; // Default can place blocks
    private boolean clearEntitiesAtEnd = true; // Default clear entities at end
    private boolean clearEquipmentAtEnd = true; // Default clear equipment at end
    private boolean clearProjectilesAtEnd = true; // Default clear projectiles at end
    private boolean clearContainerAtEndDefault = false; // Default don't clear containers at end
    
    /**
     * Called when the event session starts
     * @param players The set of players participating in this event
     */
    public abstract void onStart(UUID sessionId, Set<Player> players);
    
    /**
     * Called periodically based on tickInterval
     * @param players The current set of players in the event
     */
    public void onTick(UUID sessionId, Set<Player> players) {
        // Default implementation does nothing
    }
    
    /**
     * Called when the event session ends
     * @param players The final set of players in the event
     */
    public abstract void onEnd(UUID sessionId, Set<Player> players);
    
    /**
     * @return The interval in ticks between onTick calls
     */
    public long getTickInterval() {
        return tickInterval;
    }

    /**
     * @return Whether the event strips player inventories
     */
    public boolean stripsInventory() {
        return stripsInventory;
    }

    /**
     * @return Whether the event can break blocks
     */
    public boolean canBreakBlocks() {
        return canBreakBlocks;
    }

    /**
     * @return Whether the event can place blocks
     */
    public boolean canPlaceBlocks() {
        return canPlaceBlocks;
    }
    
    /**
     * @param tickInterval The interval in ticks between onTick calls
     */
    protected void setTickInterval(long tickInterval) {
        this.tickInterval = tickInterval;
    }
    
    /**
     * @return The total duration of the event in ticks
     */
    public long getDuration() {
        return duration;
    }
    
    /**
     * @param duration The total duration of the event in ticks
     */
    protected void setDuration(long duration) {
        this.duration = duration;
    }

    /**
     * @return The maximum number of players that can participate in the event
     */
    public long getMaxPlayers() {
        return maxPlayers;
    }
    
    /**
     * @param maxPlayers The maximum number of players that can participate in the event
     */
    protected void setMaxPlayers(long maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    /**
     * @return Whether the event strips player inventories
     */
    protected void setStripsInventory(boolean stripsInventory) {
        this.stripsInventory = stripsInventory;
    }

    /**
     * @param canBreakBlocks Whether the event can break blocks
     */
    protected void setCanBreakBlocks(boolean canBreakBlocks) {
        this.canBreakBlocks = canBreakBlocks;
    }   

    /**
     * @param canPlaceBlocks Whether the event can place blocks
     */
    protected void setCanPlaceBlocks(boolean canPlaceBlocks) {
        this.canPlaceBlocks = canPlaceBlocks;
    }
    
    /**
     * @return A descriptive name for this event type
     */
    public abstract String getName();
    
    /**
     * @return A description of what this event does
     */
    public abstract String getDescription();

    /**
     * @return Whether the event clears its entities when ending
     */
    public boolean getClearEntitiesAtEnd() {
        return clearEntitiesAtEnd;
    }

    /**
     * @param clearEntitiesAtEnd Whether the event should clear its entities when ending
     */
    protected void setClearEntitiesAtEnd(boolean clearEntitiesAtEnd) {
        this.clearEntitiesAtEnd = clearEntitiesAtEnd;
    }

    /**
     * @return Whether the event clears its equipment when ending
     */
    public boolean clearEquipmentAtEnd() {
        return clearEquipmentAtEnd;
    }

    /**
     * @param clearEquipmentAtEnd Whether the event should clear its equipment when ending
     */
    protected void setClearEquipmentAtEnd(boolean clearEquipmentAtEnd) {
        this.clearEquipmentAtEnd = clearEquipmentAtEnd;
    }

    /**
     * @return Whether the event clears its projectiles when ending
     */
    public boolean clearProjectilesAtEnd() {
        return clearProjectilesAtEnd;
    }

    /**
     * @param clearProjectilesAtEnd Whether the event should clear its projectiles when ending
     */
    public void setClearProjectilesAtEnd(boolean clearProjectilesAtEnd) {
        this.clearProjectilesAtEnd = clearProjectilesAtEnd;
    }

    /**
     * @return Whether containers should be cleared by default when the event ends
     */
    public boolean getClearContainerAtEndDefault() {
        return clearContainerAtEndDefault;
    }

    /**
     * @param clearContainerAtEndDefault Whether containers should be cleared by default when the event ends
     */
    protected void setClearContainerAtEndDefault(boolean clearContainerAtEndDefault) {
        this.clearContainerAtEndDefault = clearContainerAtEndDefault;
    }
}
