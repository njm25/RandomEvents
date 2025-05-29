package nc.randomEvents.utils;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class SoundHelper {
    public static Sound getSoundSafely(String name) {
        // Convert to lowercase and ensure proper namespaced format
        NamespacedKey key = NamespacedKey.minecraft(name.toLowerCase());

        // Try to fetch from the sound registry
        Sound sound = Registry.SOUNDS.get(key);

        // Return the found sound or null if not present
        return (sound != null) ? sound : null;
       
    }

    public static void playPlayerSoundSafely(Player player, String name, Location location, float volume, float pitch) {
        Sound sound = getSoundSafely(name);
        if (sound != null) {
            player.playSound(location, sound, volume, pitch);
        }
    }

    public static void playWorldSoundSafely(World world, String name, Location location, float volume, float pitch) {
        Sound sound = getSoundSafely(name);
        if (sound != null) {
            world.playSound(location, sound, volume, pitch);
        }
    }
}
