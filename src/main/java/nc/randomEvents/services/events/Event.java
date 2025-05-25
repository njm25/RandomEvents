package nc.randomEvents.services.events;

import org.bukkit.entity.Player;

import java.util.List;

public interface Event {
    void execute(List<Player> players);
    String getName();
    String getDescription();
}
