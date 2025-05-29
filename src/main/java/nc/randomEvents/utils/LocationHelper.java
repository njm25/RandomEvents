package nc.randomEvents.utils;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;

public class LocationHelper {

    /**
     * Finds the exact 3D midpoint of a set of players' locations.
     * @param players Set of players to calculate midpoint from
     * @return Location representing the midpoint, or null if no players provided
     */
    public static Location findMidpoint(Set<Player> players) {
        if (players == null || players.isEmpty()) {
            return null;
        }

        double totalX = 0;
        double totalY = 0;
        double totalZ = 0;
        int count = 0;

        // Get first player's world to use for the midpoint location
        Location referenceLocation = null;
        for (Player player : players) {
            if (player != null) {
                referenceLocation = player.getLocation();
                break;
            }
        }

        if (referenceLocation == null) {
            return null;
        }

        // Sum up all coordinates
        for (Player player : players) {
            if (player != null) {
                Location loc = player.getLocation();
                totalX += loc.getX();
                totalY += loc.getY();
                totalZ += loc.getZ();
                count++;
            }
        }

        // Calculate average coordinates
        return new Location(
            referenceLocation.getWorld(),
            totalX / count,
            totalY / count,
            totalZ / count
        );
    }

    /**
     * Groups players based on their proximity to each other within a specified radius.
     * @param players Set of players to group
     * @param radius Maximum radius for players to be considered in the same group
     * @return Set of player groups where each group's players are within the radius of each other
     */
    public static Set<Set<Player>> groupPlayers(Set<Player> players, int radius) {
        if (players == null || players.isEmpty() || radius <= 0) {
            return new HashSet<>();
        }

        Set<Set<Player>> groups = new HashSet<>();
        Set<Player> unassignedPlayers = new HashSet<>(players);

        while (!unassignedPlayers.isEmpty()) {
            // Start a new group with the first unassigned player
            Player firstPlayer = unassignedPlayers.iterator().next();
            Set<Player> currentGroup = new HashSet<>();
            currentGroup.add(firstPlayer);
            unassignedPlayers.remove(firstPlayer);

            Location groupMidpoint = findMidpoint(currentGroup);
            if (groupMidpoint == null) continue;

            // Try to add more players to the current group
            Iterator<Player> iterator = unassignedPlayers.iterator();
            while (iterator.hasNext()) {
                Player player = iterator.next();
                Location playerLoc = player.getLocation();

                // Check if player is within radius of the group's midpoint
                if (playerLoc.distance(groupMidpoint) <= radius) {
                    currentGroup.add(player);
                    iterator.remove();
                    // Update midpoint with new player
                    groupMidpoint = findMidpoint(currentGroup);
                    
                    // Verify all players in group are still within radius of new midpoint
                    boolean validGroup = true;
                    for (Player groupPlayer : currentGroup) {
                        if (groupPlayer.getLocation().distance(groupMidpoint) > radius) {
                            validGroup = false;
                            break;
                        }
                    }

                    // If adding this player made some players too far, remove them and restore midpoint
                    if (!validGroup) {
                        currentGroup.remove(player);
                        unassignedPlayers.add(player);
                        groupMidpoint = findMidpoint(currentGroup);
                    }
                }
            }

            // Add the completed group to our set of groups
            groups.add(currentGroup);
        }

        return groups;
    }

    /**
     * Gets a point a specific distance away from a location in a random direction
     * @param source The source location
     * @param distance The distance to offset
     * @return A new location offset from the source
     */
    public static Location getPointAwayFrom(Location source, double distance) {
        if (source == null) return null;
        
        double angle = Math.random() * 2 * Math.PI;
        double x = source.getX() + (Math.cos(angle) * distance);
        double z = source.getZ() + (Math.sin(angle) * distance);
        
        return new Location(source.getWorld(), x, 0, z);
    }
} 