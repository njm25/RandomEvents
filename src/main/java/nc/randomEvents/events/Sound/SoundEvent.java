package nc.randomEvents.events.Sound;

import org.bukkit.entity.Player;

import nc.randomEvents.events.Event;
import nc.randomEvents.utils.SoundHelper;

import java.util.Set;

public class SoundEvent implements Event {
    @Override
    public void execute(Set<Player> players) {
        for (Player player : players) {
            SoundHelper.playPlayerSoundSafely(player, "entity.ender_dragon.growl", player.getLocation(), 1.0f, 1.0f);
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
