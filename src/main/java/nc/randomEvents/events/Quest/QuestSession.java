package nc.randomEvents.events.Quest;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class QuestSession {
    private final Set<UUID> participatingPlayerUUIDs;
    private final Location chestLocation;
    private boolean isActive;

    public QuestSession(Set<Player> players, Location chestLocation) {
        this.participatingPlayerUUIDs = new HashSet<>();
        players.forEach(p -> participatingPlayerUUIDs.add(p.getUniqueId()));
        this.chestLocation = chestLocation;
        this.isActive = true;
    }

    public boolean hasPlayer(UUID playerUUID) {
        return participatingPlayerUUIDs.contains(playerUUID);
    }

    public Location getChestLocation() {
        return chestLocation;
    }

    public Set<UUID> getParticipatingPlayerUUIDs() {
        return new HashSet<>(participatingPlayerUUIDs);
    }

    public boolean isActive() {
        return isActive;
    }

    public void deactivate() {
        isActive = false;
    }
} 