package nc.randomEvents.data;

import org.bukkit.Location;
import java.util.UUID;

public class ContainerData {
    private final Location location;
    private final ContainerType type;
    private final String containerId;
    private final UUID sessionId;
    private final boolean clearAtEnd;
    private long lastModified;

    public ContainerData(Location location, ContainerType type, String containerId, UUID sessionId, boolean clearAtEnd) {
        this.location = location;
        this.type = type;
        this.containerId = containerId;
        this.sessionId = sessionId;
        this.clearAtEnd = clearAtEnd;
        this.lastModified = System.currentTimeMillis();
    }

    public Location getLocation() {
        return location;
    }

    public ContainerType getType() {
        return type;
    }

    public String getContainerId() {
        return containerId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public boolean isClearAtEnd() {
        return clearAtEnd;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void updateLastModified() {
        this.lastModified = System.currentTimeMillis();
    }

    public enum ContainerType {
        REGULAR,
        INSTANT_REWARD
    }
} 