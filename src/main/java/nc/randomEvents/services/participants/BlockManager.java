package nc.randomEvents.services.participants;

import nc.randomEvents.core.SessionParticipant;
import nc.randomEvents.listeners.BlockListener;
import nc.randomEvents.services.SessionRegistry;

import java.util.UUID;

import nc.randomEvents.RandomEvents;

public class BlockManager implements SessionParticipant {
    private final SessionRegistry sessionRegistry;
    private final RandomEvents plugin;
    public BlockManager(RandomEvents plugin) {
        this.plugin = plugin;
        this.sessionRegistry = plugin.getSessionRegistry(); 
        this.sessionRegistry.registerParticipant(this);
        new BlockListener(plugin);
    }

    @Override
    public void onSessionStart(UUID sessionId) {
        plugin.getLogger().info("BlockManager tracking new session: " + sessionId);
    }

    @Override
    public void onSessionEnd(UUID sessionId) {
        plugin.getLogger().info("BlockManager tracking session end: " + sessionId);
    }

    @Override
    public void cleanupSession(UUID sessionId, boolean force) {
        plugin.getLogger().info("BlockManager cleaning up session: " + sessionId);
    }

}
