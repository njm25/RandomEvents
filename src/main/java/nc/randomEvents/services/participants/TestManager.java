package nc.randomEvents.services.participants;

import java.util.UUID;

import nc.randomEvents.core.SessionParticipant;
import nc.randomEvents.RandomEvents;
public class TestManager implements SessionParticipant {
    private final RandomEvents plugin;

    public TestManager(RandomEvents plugin) {
        this.plugin = plugin;
        plugin.getSessionRegistry().registerParticipant(this);
    }

    @Override
    public void onSessionStart(UUID sessionId) {
       plugin.getLogger().info("TestManager onSessionStart: " + sessionId);
    }

    @Override
    public void onSessionEnd(UUID sessionId) {
        plugin.getLogger().info("TestManager onSessionEnd: " + sessionId);
    }

    @Override
    public void cleanupSession(UUID sessionId, boolean force) {
        plugin.getLogger().info("TestManager cleaning up session: " + sessionId);
    }
    
}
