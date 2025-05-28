package nc.randomEvents.services.events.Sound;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import nc.randomEvents.services.events.Event;

import java.util.Set;

public class SoundEvent implements Event {
    @Override
    public void execute(Set<Player> players) {
        for (Player player : players) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
        }
    }

    @Override
    public String getName() {
        return "SoundEvent";
    }

    @Override
    public String getDescription() {
        return "Plays a sound to all online players.";
    }
}
