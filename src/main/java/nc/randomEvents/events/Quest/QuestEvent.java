package nc.randomEvents.events.Quest;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.core.BaseEvent;
import nc.randomEvents.services.RewardGenerator;
import nc.randomEvents.services.RewardGenerator.Tier;
import nc.randomEvents.services.RewardGenerator.TierQuantity;
import nc.randomEvents.utils.LocationHelper;
import nc.randomEvents.utils.SoundHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.stream.Collectors;

public class QuestEvent extends BaseEvent implements Listener {

    private static final int GROUP_RADIUS = 1500; // Radius for grouping players
    private static final int SINGLE_PLAYER_DISTANCE = 800; // Distance for single player
    private static final int MIN_GROUP_OFFSET = 600; // Minimum offset for group midpoint
    private static final int MAX_GROUP_OFFSET = 1000; // Maximum offset for group midpoint

    private final RandomEvents plugin;
    private final Set<QuestSession> activeSessions;
    private final Random random;
    private boolean eventActive;

    public QuestEvent(RandomEvents plugin) {
        this.plugin = plugin;
        this.activeSessions = new HashSet<>();
        this.random = new Random();
    }

    public void execute(Set<Player> players) {
        if (players == null || players.isEmpty()) {
            plugin.getLogger().warning("QuestEvent cannot start: No players provided.");
            return;
        }

        // Filter for overworld players only
        Set<Player> overworldPlayers = players.stream()
            .filter(p -> p.getWorld().getEnvironment() == World.Environment.NORMAL)
            .collect(Collectors.toSet());

        if (overworldPlayers.isEmpty()) {
            plugin.getLogger().severe("QuestEvent: No participating players found in the Overworld. Aborting event.");
            return;
        }

        eventActive = true;
        activeSessions.clear();

        // Group overworld players
        Set<Set<Player>> playerGroups = LocationHelper.groupPlayers(overworldPlayers, GROUP_RADIUS);
        
        // Create a session for each valid group
        for (Set<Player> group : playerGroups) {
            Location targetLocation = null;
            
            // Single player case
            if (group.size() == 1) {
                Player player = group.iterator().next();
                targetLocation = LocationHelper.getPointAwayFrom(player.getLocation(), SINGLE_PLAYER_DISTANCE);
            } else {
                // Find the farthest pair of players
                Player player1 = null;
                Player player2 = null;
                double maxDistance = 0;

                for (Player p1 : group) {
                    for (Player p2 : group) {
                        if (p1 == p2) continue;
                        double distance = p1.getLocation().distance(p2.getLocation());
                        if (distance > maxDistance) {
                            maxDistance = distance;
                            player1 = p1;
                            player2 = p2;
                        }
                    }
                }

                if (player1 != null && player2 != null) {
                    Location loc1 = player1.getLocation();
                    Location loc2 = player2.getLocation();
                    Location midpoint = new Location(
                        loc1.getWorld(),
                        (loc1.getX() + loc2.getX()) / 2,
                        0,
                        (loc1.getZ() + loc2.getZ()) / 2
                    );

                    Vector direction = new Vector(
                        loc2.getX() - loc1.getX(),
                        0,
                        loc2.getZ() - loc1.getZ()
                    ).normalize();
                    Vector perpendicular = new Vector(-direction.getZ(), 0, direction.getX());

                    double offset = MIN_GROUP_OFFSET + (Math.random() * (MAX_GROUP_OFFSET - MIN_GROUP_OFFSET));
                    if (Math.random() < 0.5) perpendicular.multiply(-1);

                    targetLocation = midpoint.add(perpendicular.multiply(offset));
                }
            }

            if (targetLocation != null) {
                Location chestLocation = findSuitableLocation(targetLocation);
                if (chestLocation != null) {
                    // Create new session for this group
                    QuestSession session = new QuestSession(group, chestLocation);
                    activeSessions.add(session);

                    // Place chest
                    Block chestBlock = chestLocation.getBlock();
                    chestBlock.setType(Material.CHEST);
                    
                    // Distribute books to group members
                    distributeQuestBooks(group, chestLocation);

                    String chestCoordsString = String.format("X: %d, Y: %d, Z: %d", 
                        chestLocation.getBlockX(), 
                        chestLocation.getBlockY(), 
                        chestLocation.getBlockZ()
                    );
                    plugin.getLogger().info("QuestEvent: Chest placed at " + chestCoordsString + " for group of " + group.size() + " players");
                }
            }
        }

        if (activeSessions.isEmpty()) {
            plugin.getLogger().severe("QuestEvent: Could not create any valid sessions. Aborting event.");
            Bukkit.broadcast(Component.text("[RandomEvent] Quest Event failed: The server couldn't find suitable spots for treasure chests.", NamedTextColor.RED));
            eventActive = false;
            return;
        }

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private Location findSuitableLocation(Location targetLocation) {
        World world = targetLocation.getWorld();
        if (world == null) return null;

        final int MAX_ATTEMPTS = 50; // User might have changed this, keeping it flexible
        int centerX = targetLocation.getBlockX();
        int centerZ = targetLocation.getBlockZ();
        int searchRadius = 32; 

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            int offsetX = random.nextInt(searchRadius * 2) - searchRadius;
            int offsetZ = random.nextInt(searchRadius * 2) - searchRadius;
            int x = centerX + offsetX;
            int z = centerZ + offsetZ;

            Block prospectiveGroundBlock = world.getHighestBlockAt(x, z, HeightMap.WORLD_SURFACE);

            // If the surface is water, find the actual bottom
            if (prospectiveGroundBlock.getType() == Material.WATER) {
                boolean foundBottom = false;
                for (int yScan = prospectiveGroundBlock.getY() - 1; yScan >= world.getMinHeight(); yScan--) {
                    Block currentBlock = world.getBlockAt(x, yScan, z);
                    if (currentBlock.getType() != Material.WATER && currentBlock.getType() != Material.AIR && currentBlock.getType().isSolid()) {
                        prospectiveGroundBlock = currentBlock;
                        foundBottom = true;
                        break;
                    }
                    // If we hit air below water before a solid bottom, this spot is not good for underwater placement.
                    if (currentBlock.getType() == Material.AIR && yScan < prospectiveGroundBlock.getY() -1) { // -1 because one layer of air is fine for chest
                        break; 
                    }
                }
                if (!foundBottom) {
                    continue; // Couldn't find a solid bottom under the water
                }
            }

            int chestY = prospectiveGroundBlock.getY() + 1;

            if (chestY >= world.getMaxHeight() -1 || chestY <= world.getMinHeight()) { // Ensure chestY itself is valid
                continue;
            }

            Block blockAtChest = world.getBlockAt(x, chestY, z);
            Block blockAboveChest = world.getBlockAt(x, chestY + 1, z);
            
            // Check if the prospective ground is solid, and the chest and above-chest locations are air
            // No need for isWaterlogged check on prospectiveGroundBlock if we are placing chest in AIR above it.
            if (prospectiveGroundBlock.getType().isSolid() && 
                blockAtChest.getType() == Material.AIR && 
                blockAboveChest.getType() == Material.AIR) {
                return blockAtChest.getLocation();
            }
        }
        plugin.getLogger().warning("QuestEvent: Failed to find suitable chest location after " + MAX_ATTEMPTS + " attempts in a " + (searchRadius*2) + "x" + (searchRadius*2) + " area around X:" + centerX + " Z:" + centerZ);
        return null;
    }

    private void distributeQuestBooks(Set<Player> players, Location chestLocation) {
        for (Player player : players) {
            if (player.isOnline()) {
                giveQuestBook(player, chestLocation);
            }
        }
    }

    private void giveQuestBook(Player player, Location chestLocation) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta bookMeta = (BookMeta) book.getItemMeta();
        if (bookMeta != null) {
            bookMeta.title(Component.text("Ancient Scroll", NamedTextColor.GOLD));
            bookMeta.author(Component.text("Event Master", NamedTextColor.DARK_PURPLE));
            
            // Convert coordinates to binary strings with sign
            String xBinary = String.format("%s%s", 
                chestLocation.getBlockX() < 0 ? "- " : "  ",
                String.format("%16s", Integer.toBinaryString(Math.abs(chestLocation.getBlockX()))).replace(' ', '0')
            );
            String yBinary = String.format("%s%s",
                chestLocation.getBlockY() < 0 ? "- " : "  ",
                String.format("%16s", Integer.toBinaryString(Math.abs(chestLocation.getBlockY()))).replace(' ', '0')
            );
            String zBinary = String.format("%s%s",
                chestLocation.getBlockZ() < 0 ? "- " : "  ",
                String.format("%16s", Integer.toBinaryString(Math.abs(chestLocation.getBlockZ()))).replace(' ', '0')
            );
            
            String bookText = "x:\n" + xBinary + "\n\n" +
                            "y:\n" + yBinary + "\n\n" +
                            "z:\n" + zBinary;
            bookMeta.addPages(Component.text(bookText, NamedTextColor.BLACK));
            book.setItemMeta(bookMeta);
            
            player.getInventory().addItem(book);
            player.sendMessage(Component.text("[Event] You have received an Ancient Scroll! Check your inventory.", NamedTextColor.GOLD));
            
            // Play mysterious sounds
            SoundHelper.playPlayerSoundSafely(player, "item.book.page_turn", player.getLocation(), 0.7f, 0.5f);
            SoundHelper.playPlayerSoundSafely(player, "block.enchantment_table.use", player.getLocation(), 0.5f, 0.8f);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!eventActive) return;
        
        // We care about left or right clicking a block
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Block clickedBlock = event.getClickedBlock();
        Player player = event.getPlayer();
        
        // Find the session this player belongs to
        Optional<QuestSession> playerSession = activeSessions.stream()
            .filter(session -> session.isActive() && session.hasPlayer(player.getUniqueId()))
            .findFirst();

        if (!playerSession.isPresent()) return;

        QuestSession session = playerSession.get();
        Location sessionChestLoc = session.getChestLocation();
        
        // Compare block locations
        if (clickedBlock.getLocation().equals(sessionChestLoc) && clickedBlock.getType() == Material.CHEST) {
            event.setCancelled(true);
            
            // Broadcast only to players in this session
            for (UUID playerUUID : session.getParticipatingPlayerUUIDs()) {
                Player sessionPlayer = Bukkit.getPlayer(playerUUID);
                if (sessionPlayer != null && sessionPlayer.isOnline()) {
                    sessionPlayer.sendMessage(Component.text("[Event] " + player.getName() + " has found the hidden chest!", NamedTextColor.GOLD));
                }
            }

            // Play effects at chest location
            Location effectLoc = clickedBlock.getLocation().add(0.5, 0.5, 0.5);
            player.getWorld().spawnParticle(Particle.PORTAL, effectLoc, 50, 0.5, 0.5, 0.5, 1);
            player.getWorld().spawnParticle(Particle.END_ROD, effectLoc, 20, 0.3, 0.3, 0.3, 0.1);
            SoundHelper.playWorldSoundSafely(player.getWorld(), "block.enchantment_table.use", effectLoc, 1.0f, 1.0f);
            SoundHelper.playWorldSoundSafely(player.getWorld(), "entity.player.levelup", effectLoc, 1.0f, 0.5f);

            // Remove chest
            clickedBlock.setType(Material.AIR);
            if (clickedBlock.getState() instanceof Chest) {
                Chest chestState = (Chest) clickedBlock.getState();
                if (chestState.isPlaced()) chestState.getBlockInventory().clear();
            }

            // Generate and give rewards
            RewardGenerator rewardGenerator = plugin.getRewardGenerator();

            if (rewardGenerator != null) {
                List<ItemStack> rewards = rewardGenerator.generateRewards(
                    new TierQuantity()
                        .add(Tier.RARE, 2)
                        .add(Tier.COMMON, 4)
                        .build()
                );

                if (rewards.isEmpty()) {
                    player.sendMessage(Component.text("The chest was surprisingly empty... better luck next time!", NamedTextColor.YELLOW));
                    plugin.getLogger().warning("QuestEvent: No RARE rewards generated for winner " + player.getName());
                } else {
                    player.sendMessage(Component.text("You received your rewards from the chest!", NamedTextColor.GOLD));
                    for (ItemStack reward : rewards) {
                        player.getInventory().addItem(reward).forEach((index, item) -> {
                            player.getWorld().dropItemNaturally(player.getLocation(), item);
                            player.sendMessage(Component.text("Your inventory was full! Some items were dropped at your feet.", NamedTextColor.RED));
                        });
                    }
                }
            } else {
                plugin.getLogger().severe("QuestEvent: RewardGenerator is null. Cannot give rewards to " + player.getName());
                player.sendMessage(Component.text("An error occurred while attempting to grant your rewards. Please contact an admin.", NamedTextColor.RED));
            }

            // Deactivate this session and clean up its resources
            cleanupSession(session);

            // If no more active sessions, end the event
            if (activeSessions.stream().noneMatch(QuestSession::isActive)) {
                finishEvent();
            }
        }
    }

    private void cleanupSession(QuestSession session) {
        session.deactivate();
        
        // Clean up quest books from all session participants
        for (UUID playerUUID : session.getParticipatingPlayerUUIDs()) {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                // Clean up from player inventory
                for (ItemStack item : player.getInventory().getContents()) {
                    if (item != null && item.getType() == Material.WRITTEN_BOOK) {
                        BookMeta meta = (BookMeta) item.getItemMeta();
                        if (meta != null && meta.getTitle() != null && 
                            Component.text("Ancient Scroll", NamedTextColor.GOLD).equals(meta.title())) {
                            player.getInventory().remove(item);
                        }
                    }
                }

                // Clean up from ground and containers near each player
                World world = player.getWorld();
                Location playerLoc = player.getLocation();
                int radius = 50; // Search within 50 blocks

                // Clean up dropped items
                world.getEntitiesByClass(org.bukkit.entity.Item.class).stream()
                    .filter(item -> item.getLocation().distance(playerLoc) <= radius)
                    .forEach(item -> {
                        ItemStack droppedItem = item.getItemStack();
                        if (droppedItem.getType() == Material.WRITTEN_BOOK) {
                            BookMeta meta = (BookMeta) droppedItem.getItemMeta();
                            if (meta != null && meta.getTitle() != null &&
                                Component.text("Ancient Scroll", NamedTextColor.GOLD).equals(meta.title())) {
                                item.remove();
                            }
                        }
                    });

                // Clean up from nearby containers
                for (int x = -radius; x <= radius; x++) {
                    for (int y = -radius; y <= radius; y++) {
                        for (int z = -radius; z <= radius; z++) {
                            Location loc = playerLoc.clone().add(x, y, z);
                            if (loc.distance(playerLoc) <= radius) {
                                Block block = loc.getBlock();
                                if (block.getState() instanceof Container) {
                                    Container container = (Container) block.getState();
                                    for (ItemStack item : container.getInventory().getContents()) {
                                        if (item != null && item.getType() == Material.WRITTEN_BOOK) {
                                            BookMeta meta = (BookMeta) item.getItemMeta();
                                            if (meta != null && meta.getTitle() != null &&
                                                Component.text("Ancient Scroll", NamedTextColor.GOLD).equals(meta.title())) {
                                                container.getInventory().remove(item);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    private void finishEvent() {
        if (eventActive) {
            HandlerList.unregisterAll(this);
            eventActive = false;
            activeSessions.clear();
            plugin.getLogger().info("QuestEvent finished and all resources cleaned up.");
        }
    }
    
    @Override
    public String getName() {
        return "QuestEvent";
    }

    @Override
    public String getDescription() {
        return "Players must seek a chest placed randomly in the world via coordinates in a book. First to interact wins rare loot.";
    }

    @Override
    public void onStart(UUID sessionId, Set<Player> players) {
        throw new UnsupportedOperationException("Unimplemented method 'onStart'");
    }

    @Override
    public void onEnd(UUID sessionId, Set<Player> players) {
        throw new UnsupportedOperationException("Unimplemented method 'onEnd'");
    }
}
