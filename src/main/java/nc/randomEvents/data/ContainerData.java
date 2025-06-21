package nc.randomEvents.data;

import nc.randomEvents.core.LootContainer.ContainerType;
import nc.randomEvents.core.PluginData;
import org.bukkit.Location;
import java.util.UUID;

public class ContainerData implements PluginData {
    public Location location;
    public ContainerType type;
    public String containerId;
    public UUID sessionId;
    public boolean clearAtEnd;
    public long lastModified;

    public ContainerData(Location location, ContainerType type, String containerId, UUID sessionId, boolean clearAtEnd) {
        this.location = location;
        this.type = type;
        this.containerId = containerId;
        this.sessionId = sessionId;
        this.clearAtEnd = clearAtEnd;
        this.lastModified = System.currentTimeMillis();
    }

    @Override
    public String getId() {
        return location.toString();
    }

} 