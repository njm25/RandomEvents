package nc.randomEvents.data;

import nc.randomEvents.core.PluginData;
import java.util.UUID;

public class WorldData implements PluginData {
    private transient String worldName;
    public UUID worldId;
    public long lastModified;

    public WorldData(String worldName, UUID worldId) {
        this.worldName = worldName;
        this.worldId = worldId;
        this.lastModified = System.currentTimeMillis();
    }

    // Constructor for deserialization
    public WorldData(UUID worldId, long lastModified) {
        this.worldId = worldId;
        this.lastModified = lastModified;
    }

    public WorldData(String worldName) {
        this(worldName, UUID.randomUUID());
    }

    public String getWorldName() {
        return worldName;
    }

    public UUID getWorldId() {
        return worldId;
    }

    public long getLastModified() {
        return lastModified;
    }

    @Override
    public String getId() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }
}
