package nc.randomEvents.services;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.core.EventSession;
import nc.randomEvents.core.SessionParticipant;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SessionRegistry {
    private final Map<UUID, EventSession> activeSessions = new ConcurrentHashMap<>();
    private final Set<SessionParticipant> participants = new HashSet<>();
    
    public SessionRegistry(RandomEvents plugin) {
        
    }
    
    /**
     * Register a service to receive session lifecycle events
     */
    public void registerParticipant(SessionParticipant participant) {
        participants.add(participant);
    }
    
    /**
     * Register a new active session
     */
    public void registerSession(EventSession session) {
        activeSessions.put(session.getSessionId(), session);
        // Notify all participants of session start
        participants.forEach(p -> p.onSessionStart(session.getSessionId(), session.getPlayers()));
    }
    
    /**
     * Remove a session from the registry and notify participants
     */
    public void unregisterSession(UUID sessionId) {
        EventSession session = activeSessions.remove(sessionId);
        if (session != null) {
            // Notify all participants of session end
            participants.forEach(p -> p.onSessionEnd(sessionId, session.getPlayers()));
        }
    }
    
    /**
     * Check if a session is still active
     */
    public boolean isActive(UUID sessionId) {
        return activeSessions.containsKey(sessionId);
    }
    
    /**
     * Get all currently active sessions
     */
    public Collection<EventSession> getActiveSessions() {
        return Collections.unmodifiableCollection(activeSessions.values());
    }
    
    /**
     * End all active sessions
     */
    public void endAll() {
        // Create a copy to avoid concurrent modification
        new ArrayList<>(activeSessions.values()).forEach(EventSession::end);
    }
    
    /**
     * Force end a specific session
     */
    public void endSession(UUID sessionId) {
        EventSession session = activeSessions.get(sessionId);
        if (session != null) {
            session.end();
        }
    }
    
}
