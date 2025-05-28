package nc.randomEvents.services.events;

import org.bukkit.entity.Player;

import java.util.Set;

public interface Event {
    void execute(Set<Player> playerSets);
    String getName();
    String getDescription();
}
