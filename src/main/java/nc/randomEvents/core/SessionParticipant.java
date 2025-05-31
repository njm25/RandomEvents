package nc.randomEvents.core;

import org.bukkit.entity.Player;
import java.util.Set;
import java.util.UUID;

public interface SessionParticipant {
    /**
     * Called when a new event session begins
     * @param sessionId The unique identifier for this session
     * @param players The set of players participating in this session
     */
    void onSessionStart(UUID sessionId, Set<Player> players);

    /**
     * Called when an event session ends, either naturally or forcefully
     * @param sessionId The unique identifier for the ending session
     * @param players The final set of players in the session
     */
    void onSessionEnd(UUID sessionId, Set<Player> players);
}
