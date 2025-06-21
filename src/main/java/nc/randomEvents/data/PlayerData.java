package nc.randomEvents.data;

import java.util.UUID;

import nc.randomEvents.core.PluginData;

public class PlayerData implements PluginData {
    private transient UUID playerId;
    public int eventsParticipated = 0;

    public PlayerData(UUID playerId, int eventsParticipated) {
        this.playerId = playerId;
        this.eventsParticipated = eventsParticipated;
    }

    // Constructor for deserialization
    public PlayerData(int eventsParticipated) {
        this.eventsParticipated = eventsParticipated;
    }

    @Override
    public String getId() {
        return playerId.toString();
    }

    public void setPlayerId(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }
}
