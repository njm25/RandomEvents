package nc.randomEvents.core;

import java.util.UUID;

public interface SessionParticipant {
    /**
     * Called when a new event session begins
     * @param sessionId The unique identifier for this session
     */
    void onSessionStart(UUID sessionId);

    /**
     * Called when an event session ends, either naturally or forcefully
     * @param sessionId The unique identifier for the ending session
     */
    void onSessionEnd(UUID sessionId);
}
