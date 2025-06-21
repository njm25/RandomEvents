package nc.randomEvents.data;

import java.util.UUID;

public class WorldData {
    private final String worldName;
    private final UUID worldId;
    private long lastModified;

    public WorldData(String worldName, UUID worldId) {
        this.worldName = worldName;
        this.worldId = worldId;
        this.lastModified = System.currentTimeMillis();
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

    public void updateLastModified() {
        this.lastModified = System.currentTimeMillis();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        WorldData worldData = (WorldData) obj;
        return worldName.equalsIgnoreCase(worldData.worldName);
    }

    @Override
    public int hashCode() {
        return worldName.toLowerCase().hashCode();
    }

    @Override
    public String toString() {
        return "WorldData{worldName='" + worldName + "', worldId=" + worldId + "}";
    }
}
