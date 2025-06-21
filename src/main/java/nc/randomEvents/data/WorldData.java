package nc.randomEvents.data;

import nc.randomEvents.core.PluginData;
import java.util.UUID;

public class WorldData implements PluginData {
    public String worldName;
    public UUID worldId;
    public long lastModified;

    public WorldData(String worldName, UUID worldId) {
        this.worldName = worldName;
        this.worldId = worldId;
        this.lastModified = System.currentTimeMillis();
    }

    public WorldData(String worldName) {
        this(worldName, UUID.randomUUID());
    }

    @Override
    public String getId() {
        return worldName;
    }

}
