package nc.randomEvents.data;

import java.util.UUID;

import nc.randomEvents.core.PluginData;

public class PlayerData implements PluginData {
    private final UUID playerId;
    public int eventsParticipated = 0;

    public PlayerData(UUID playerId, int eventsParticipated) {
        this.playerId = playerId;
        this.eventsParticipated = eventsParticipated;
    }

    @Override
    public String getId() {
        return playerId.toString();
    }

}
