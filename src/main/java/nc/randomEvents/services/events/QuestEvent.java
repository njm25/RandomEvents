package nc.randomEvents.services.events;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.services.RewardGenerator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class QuestEvent implements Event, Listener {

    private final RandomEvents plugin;
    private Location chestLocation;
    private final List<UUID> participatingPlayerUUIDs = new ArrayList<>();
    private boolean eventActive = false;
    private final Random random = new Random();

    public QuestEvent(RandomEvents plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(List<Player> players) {
        if (players == null || players.isEmpty()) {
            plugin.getLogger().warning("QuestEvent cannot start: No players provided.");
            return;
        }
        eventActive = true;
        participatingPlayerUUIDs.clear();
        for (Player p : players) {
            participatingPlayerUUIDs.add(p.getUniqueId());
        }

        Player referencePlayer = players.get(random.nextInt(players.size()));
        World world = referencePlayer.getWorld();

        if (world.getEnvironment() != World.Environment.NORMAL) {
            plugin.getLogger().warning("QuestEvent: Selected reference player " + referencePlayer.getName() + " is not in the Overworld. Attempting to find another.");
            boolean foundOverworldPlayer = false;
            for (Player p : players) {
                if (p.getWorld().getEnvironment() == World.Environment.NORMAL) {
                    referencePlayer = p;
                    world = p.getWorld();
                    foundOverworldPlayer = true;
                    plugin.getLogger().info("QuestEvent: Switched reference player to " + referencePlayer.getName() + " in the Overworld.");
                    break;
                }
            }
            if (!foundOverworldPlayer) {
                plugin.getLogger().severe("QuestEvent: No participating players found in the Overworld. Aborting event.");
                eventActive = false;
                return;
            }
        }

        Location playerInitialLoc = referencePlayer.getLocation();
        Location foundLocation = null;
        final int MAX_ATTEMPTS = 30;
        final int MIN_DISTANCE = 500;
        final int MAX_DISTANCE = 600;

        plugin.getLogger().info("QuestEvent: Starting search for chest location (" + MIN_DISTANCE + "-" + MAX_DISTANCE + " blocks away).");

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = MIN_DISTANCE + (random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE));
            int potentialX = playerInitialLoc.getBlockX() + (int) (distance * Math.cos(angle));
            int potentialZ = playerInitialLoc.getBlockZ() + (int) (distance * Math.sin(angle));

            int highestSolidY = world.getHighestBlockYAt(potentialX, potentialZ, HeightMap.WORLD_SURFACE);
            int chestY = highestSolidY + 1;

            if (chestY >= world.getMaxHeight() - 2 || chestY <= world.getMinHeight()) {
                continue;
            }

            Block blockBelowChest = world.getBlockAt(potentialX, highestSolidY, potentialZ);
            Block blockAtChest = world.getBlockAt(potentialX, chestY, potentialZ);
            Block blockAir1 = world.getBlockAt(potentialX, chestY + 1, potentialZ);

            if (blockBelowChest.getType().isSolid() &&
                !blockBelowChest.isPassable() &&
                blockAtChest.getType() == Material.AIR &&
                blockAir1.getType() == Material.AIR) {
                foundLocation = blockAtChest.getLocation();
                plugin.getLogger().info("QuestEvent: Found suitable location on attempt " + (attempt + 1) + " at (" + potentialX + ", " + chestY + ", " + potentialZ + ").");
                break;
            }
        }

        if (foundLocation == null) {
            plugin.getLogger().severe("QuestEvent: Could not find a suitable location for the chest after " + MAX_ATTEMPTS + " attempts. Aborting event.");
            Bukkit.broadcast(Component.text("[RandomEvent] Quest Event failed: The server couldn't find a suitable spot for the treasure chest.", NamedTextColor.RED));
            eventActive = false;
            return;
        }
        
        this.chestLocation = foundLocation;
        Block chestBlock = this.chestLocation.getBlock();
        chestBlock.setType(Material.CHEST);
        String chestCoordsString = "X: " + chestLocation.getBlockX() + ", Y: " + chestLocation.getBlockY() + ", Z: " + chestLocation.getBlockZ();

        plugin.getLogger().info("QuestEvent: Chest placed at " + chestCoordsString);

        for (UUID playerUUID : participatingPlayerUUIDs) {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
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
                    
                    String bookText = "x:\n" +
                                    xBinary + "\n\n" +
                                    "y:\n" +
                                    yBinary + "\n\n" +
                                    "z:\n" +
                                    zBinary;
                    bookMeta.addPages(Component.text(bookText, NamedTextColor.BLACK));
                    book.setItemMeta(bookMeta);
                    player.getInventory().addItem(book);
                    player.sendMessage(Component.text("[Event] You have received an Ancient Scroll! Check your inventory.", NamedTextColor.GOLD));
                    // Play mysterious sounds when receiving the scroll
                    player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 0.5f);
                    player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, 0.8f);
                }
            }
        }

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!eventActive || chestLocation == null) return;
        
        // We care about left or right clicking a block
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Block clickedBlock = event.getClickedBlock();
        
        // Compare block locations (ignores pitch/yaw, focuses on the block itself)
        if (clickedBlock.getLocation().equals(chestLocation) && clickedBlock.getType() == Material.CHEST) {
            Player winner = event.getPlayer();

            if (!participatingPlayerUUIDs.contains(winner.getUniqueId())) {
                winner.sendMessage(Component.text("[Event] This chest is not meant for you.", NamedTextColor.GOLD));
                return;
            }

            event.setCancelled(true); 
            
            Bukkit.broadcast(Component.text("[Event] " + winner.getName() + " has found the hidden chest!", NamedTextColor.GOLD));

            // Play effects at chest location
            Location effectLoc = clickedBlock.getLocation().add(0.5, 0.5, 0.5);
            winner.getWorld().spawnParticle(Particle.PORTAL, effectLoc, 50, 0.5, 0.5, 0.5, 1);
            winner.getWorld().spawnParticle(Particle.END_ROD, effectLoc, 20, 0.3, 0.3, 0.3, 0.1);
            winner.getWorld().playSound(effectLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
            winner.getWorld().playSound(effectLoc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.5f);

            clickedBlock.setType(Material.AIR); // Despawn chest
            // If chest had a state (e.g. custom name, or if it was more than just Material.CHEST initially)
            if (clickedBlock.getState() instanceof Chest) {
                Chest chestState = (Chest) clickedBlock.getState(); // Get state before changing type
                if(chestState.isPlaced()) chestState.getBlockInventory().clear(); // Clear inventory if it was a valid chest
            }

            RewardGenerator rewardGenerator = plugin.getRewardGenerator();
            if (rewardGenerator != null) {
                List<ItemStack> rewards = rewardGenerator.generateRewards(RewardGenerator.Tier.RARE, 2); // "a couple" of RARE rewards
                if (rewards.isEmpty()) {
                    winner.sendMessage(Component.text("The chest was surprisingly empty... better luck next time!", NamedTextColor.YELLOW));
                    plugin.getLogger().warning("QuestEvent: No RARE rewards generated for winner " + winner.getName());
                } else {
                    winner.sendMessage(Component.text("You received your rewards from the chest!", NamedTextColor.GOLD));
                    for (ItemStack reward : rewards) {
                        // Attempt to add to inventory, drop if full
                        winner.getInventory().addItem(reward).forEach((index, item) -> {
                            winner.getWorld().dropItemNaturally(winner.getLocation(), item);
                            winner.sendMessage(Component.text("Your inventory was full! Some items were dropped at your feet.", NamedTextColor.RED));
                        });
                    }
                }
            } else {
                plugin.getLogger().severe("QuestEvent: RewardGenerator is null. Cannot give rewards to " + winner.getName());
                winner.sendMessage(Component.text("An error occurred while attempting to grant your rewards. Please contact an admin.", NamedTextColor.RED));
            }

            finishEvent();
        }
    }
    
    private void finishEvent() {
        if (eventActive) {
            HandlerList.unregisterAll(this);
            
            // Clean up quest books from all participants
            for (UUID playerUUID : participatingPlayerUUIDs) {
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
                                        Inventory inv = container.getInventory();
                                        for (ItemStack item : inv.getContents()) {
                                            if (item != null && item.getType() == Material.WRITTEN_BOOK) {
                                                BookMeta meta = (BookMeta) item.getItemMeta();
                                                if (meta != null && meta.getTitle() != null &&
                                                    Component.text("Ancient Scroll", NamedTextColor.GOLD).equals(meta.title())) {
                                                    inv.remove(item);
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
            
            eventActive = false;
            chestLocation = null;
            participatingPlayerUUIDs.clear();
            plugin.getLogger().info("QuestEvent finished and resources cleaned up.");
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
}
