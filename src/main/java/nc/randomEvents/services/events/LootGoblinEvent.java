package nc.randomEvents.services.events;

import nc.randomEvents.RandomEvents;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LootGoblinEvent implements Event, Listener {
    private final RandomEvents plugin;
    private final Random random = new Random();
    private static final String LOOT_GOBLIN_METADATA_KEY = "loot_goblin";
    private static final String STOLEN_ITEM_METADATA_KEY = "loot_goblin_stolen_item";
    private static final String AGGRO_SWORD_METADATA_KEY = "loot_goblin_aggro_sword";
    private static final String CRYING_METADATA_KEY = "loot_goblin_crying";
    private final NamespacedKey goblinUniqueIdKey;

    private final Map<UUID, GoblinTask> activeGoblins = new ConcurrentHashMap<>(); // Zombie UUID to its task
    private static final int CHEST_SEARCH_RADIUS = 20;
    private static final int FLEE_TIMEOUT_SECONDS = 60;
    private static final double GOBLIN_SPEED_MULTIPLIER = 1.0;
    private static final int GOBLIN_SPAWN_MIN_RADIUS = 10;
    private static final int GOBLIN_SPAWN_MAX_RADIUS = 20;
    private static final int FLEE_RADIUS = 25;
    private static final int MIN_FLEE_DISTANCE = 10;

    public LootGoblinEvent(RandomEvents plugin) {
        this.plugin = plugin;
        this.goblinUniqueIdKey = new NamespacedKey(plugin, "loot_goblin_uuid");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public String getName() {
        return "LootGoblinEvent";
    }

    @Override
    public String getDescription() {
        return "A mischievous Loot Goblin appears! Catch it if you can!";
    }

    @Override
    public void execute(List<Player> players) {
        for (Player player : players) {
            if (!player.isOnline() || player.isDead()) continue;
            spawnGoblinForPlayer(player);
        }
    }

    private void spawnGoblinForPlayer(Player player) {
        Location playerLoc = player.getLocation();
        World world = player.getWorld();
        Location spawnLoc = findSafeSpawnLocation(playerLoc, GOBLIN_SPAWN_MIN_RADIUS, GOBLIN_SPAWN_MAX_RADIUS);

        if (spawnLoc == null) {
            plugin.getLogger().warning("Could not find a safe spawn location for Loot Goblin near " + player.getName());
            return;
        }

        Zombie goblin = (Zombie) world.spawnEntity(spawnLoc, EntityType.ZOMBIE);
        goblin.setBaby(true);
        goblin.setCustomName(ChatColor.GOLD + "Loot Goblin");
        goblin.setCustomNameVisible(true);
        goblin.getPersistentDataContainer().set(goblinUniqueIdKey, PersistentDataType.STRING, goblin.getUniqueId().toString());
        goblin.setMetadata(LOOT_GOBLIN_METADATA_KEY, new FixedMetadataValue(plugin, true));

        AttributeModifier speedBoost = new AttributeModifier(UUID.randomUUID(), "goblin_speed", GOBLIN_SPEED_MULTIPLIER - 1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlot.FEET);
        goblin.getAttribute(Attribute.MOVEMENT_SPEED).addModifier(speedBoost);
        goblin.setSilent(true);

        player.sendMessage(ChatColor.YELLOW + "You hear a faint giggle... a " + ChatColor.GOLD + "Loot Goblin" + ChatColor.YELLOW + " has appeared nearby!");
        world.playSound(goblin.getLocation(), Sound.ENTITY_PIGLIN_JEALOUS, SoundCategory.HOSTILE, 1.0f, 1.5f);

        GoblinTask task = new GoblinTask(goblin, player);
        activeGoblins.put(goblin.getUniqueId(), task);
        task.runTaskTimer(plugin, 0L, 5L); // Run AI tick more frequently (every 5 ticks = 0.25s)
    }

    private Location findSafeSpawnLocation(Location center, int minRadius, int maxRadius) {
        World world = center.getWorld();
        if (world == null) return null;
        for (int i = 0; i < 30; i++) { // Try 30 times for a better chance in a ring
            double angle = random.nextDouble() * 2 * Math.PI;
            // Ensure spawning in a ring
            double r_squared = (minRadius * minRadius) + ( (maxRadius * maxRadius) - (minRadius * minRadius) ) * random.nextDouble();
            double r = Math.sqrt(r_squared);

            double x = center.getX() + r * Math.cos(angle);
            double z = center.getZ() + r * Math.sin(angle);
            
            Location loc = new Location(world, x, center.getY(), z); // Start search at player's Y level for more relevant checks
            Location groundLoc = world.getHighestBlockAt(loc).getLocation(); // Find ground
            // If highest block is too far below, try to find a surface closer to player's Y
            if(Math.abs(groundLoc.getY() - center.getY()) > 10){
                loc = new Location(world, x, center.getY(), z);
                // Attempt to find a surface within a small vertical range around player's Y
                boolean foundSurface = false;
                for(int yOffset = 0; yOffset <=5; yOffset++){
                     Block bBelow = world.getBlockAt(loc.getBlockX(), loc.getBlockY() - yOffset -1, loc.getBlockZ());
                     Block bAt = world.getBlockAt(loc.getBlockX(), loc.getBlockY() - yOffset, loc.getBlockZ());
                     Block bAbove = world.getBlockAt(loc.getBlockX(), loc.getBlockY() - yOffset + 1, loc.getBlockZ());
                     if(!bBelow.isPassable() && bAt.isPassable() && bAbove.isPassable()){
                         loc.setY(loc.getY() - yOffset);
                         foundSurface = true;
                         break;
                     }
                }
                if(!foundSurface) continue; // No suitable surface nearby in vertical slice
            } else {
                loc.setY(groundLoc.getY());
            }
            
            if (loc.getY() < world.getMinHeight() + 2) loc.setY(world.getMinHeight() + 2);
            if (loc.getY() > world.getMaxHeight() -3) loc.setY(world.getMaxHeight() -3);


            if (loc.getBlock().getRelative(0, 0, 0).isPassable() &&
                loc.getBlock().getRelative(0, 1, 0).isPassable() &&
                !loc.getBlock().getRelative(0, -1, 0).isPassable()) {
                return loc.add(0.5, 0, 0.5); // Center on block
            }
        }
        // Fallback if no ideal spot is found after attempts, spawn near original center but higher
        Location fallbackLoc = center.clone().add( (random.nextDouble()-0.5) * minRadius * 2, 0 , (random.nextDouble()-0.5) * minRadius*2);
        fallbackLoc.setY(world.getHighestBlockYAt(fallbackLoc) +1.0);
         if (fallbackLoc.getBlock().getRelative(0, 0, 0).isPassable() &&
             fallbackLoc.getBlock().getRelative(0, 1, 0).isPassable() &&
             !fallbackLoc.getBlock().getRelative(0, -1, 0).isPassable()) {
                return fallbackLoc.add(0.5,0,0.5);
         }
        plugin.getLogger().warning("LootGoblinEvent: Could not find a suitable safe spawn location after many attempts near " + center.toString());
        return null; // Could not find a suitable spot
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity deadEntity = event.getEntity();
        if (deadEntity.hasMetadata(LOOT_GOBLIN_METADATA_KEY)) {
            event.getDrops().clear(); // Clear default zombie drops

            if (deadEntity.hasMetadata(STOLEN_ITEM_METADATA_KEY)) {
                ItemStack stolenItem = (ItemStack) deadEntity.getMetadata(STOLEN_ITEM_METADATA_KEY).get(0).value();
                if (stolenItem != null && stolenItem.getType() != Material.AIR) {
                    deadEntity.getWorld().dropItemNaturally(deadEntity.getLocation(), stolenItem);
                }
            } else if (deadEntity.getEquipment().getItemInMainHand().getType() == Material.DIAMOND_SWORD && deadEntity.hasMetadata(AGGRO_SWORD_METADATA_KEY)) {
                deadEntity.getWorld().dropItemNaturally(deadEntity.getLocation(), new ItemStack(Material.DIAMOND_SWORD));
            }
            // Victory sound/particle
            deadEntity.getWorld().playSound(deadEntity.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            deadEntity.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, deadEntity.getLocation().add(0, 0.5, 0), 30, 0.5, 0.5, 0.5, 0.1);
            cleanupGoblin(deadEntity.getUniqueId(), false);
        }
    }
    
    @EventHandler
    public void onEntityCombust(EntityCombustEvent event) {
        if (event.getEntity().hasMetadata(LOOT_GOBLIN_METADATA_KEY)) {
            event.setCancelled(true); // Prevent loot goblins from burning in daylight
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Zombie)) return;
        Zombie goblin = (Zombie) event.getEntity();
        
        if (!goblin.hasMetadata(LOOT_GOBLIN_METADATA_KEY)) return;
        
        // Cancel the damage event - we don't want the goblin to actually take damage
        event.setCancelled(true);
        
        // If already crying, ignore additional hits
        if (goblin.hasMetadata(CRYING_METADATA_KEY)) return;
        
        // Mark as crying
        goblin.setMetadata(CRYING_METADATA_KEY, new FixedMetadataValue(plugin, true));
        
        // Drop the item if carrying one
        if (goblin.hasMetadata(STOLEN_ITEM_METADATA_KEY)) {
            ItemStack stolenItem = (ItemStack) goblin.getMetadata(STOLEN_ITEM_METADATA_KEY).get(0).value();
            if (stolenItem != null && stolenItem.getType() != Material.AIR) {
                goblin.getWorld().dropItemNaturally(goblin.getLocation(), stolenItem);
                goblin.getEquipment().setItemInMainHand(new ItemStack(Material.AIR));
                goblin.removeMetadata(STOLEN_ITEM_METADATA_KEY, plugin);
            }
        }
        
        // Play crying effects
        Location loc = goblin.getLocation();
        World world = goblin.getWorld();
        world.playSound(loc, Sound.ENTITY_DOLPHIN_DEATH, 1.0f, 2.0f); // High-pitched sad sound
        world.spawnParticle(Particle.CLOUD, loc.add(0, 1.5, 0), 20, 0.2, 0.2, 0.2, 0); // Tear particles
        
        // Schedule disappearance after crying
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!goblin.isValid() || goblin.isDead()) {
                    this.cancel();
                    return;
                }
                
                ticks++;
                if (ticks % 10 == 0) { // Every half second
                    // Continue crying effects
                    world.spawnParticle(Particle.CLOUD, goblin.getLocation().add(0, 1.5, 0), 5, 0.2, 0.2, 0.2, 0);
                    world.playSound(goblin.getLocation(), Sound.ENTITY_DOLPHIN_HURT, 0.5f, 2.0f);
                }
                
                if (ticks >= 60) { // After 3 seconds
                    // Final disappearance
                    world.playSound(goblin.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    world.spawnParticle(Particle.CLOUD, goblin.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.05);
                    cleanupGoblin(goblin.getUniqueId(), true);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void cleanupGoblin(UUID goblinId, boolean withEscapeEffect) {
        GoblinTask task = activeGoblins.remove(goblinId);
        if (task != null) {
            task.cancel();
            Zombie goblinEntity = task.goblin;
            if (goblinEntity != null && goblinEntity.isValid()) {
                if (withEscapeEffect && !goblinEntity.hasMetadata(CRYING_METADATA_KEY)) {
                    goblinEntity.getWorld().playSound(goblinEntity.getLocation(), Sound.ENTITY_FOX_TELEPORT, 1.0f, 1.0f);
                    goblinEntity.getWorld().spawnParticle(Particle.CLOUD, goblinEntity.getLocation().add(0, 1, 0), 20, 0.3, 0.3, 0.3, 0.05);
                }
                goblinEntity.remove();
            }
        }
    }

    private class GoblinTask extends BukkitRunnable {
        final Zombie goblin;
        final Player initialPlayerTarget;
        Block targetChest = null;
        ItemStack carriedItem = null;
        boolean hasReachedChest = false;
        boolean isFleeing = false;
        long spawnTime = System.currentTimeMillis();
        int fleePathfindTicks = 0;
        int fleeAttempts = 0;
        static final int MAX_FLEE_ATTEMPTS = 10;

        GoblinTask(Zombie goblin, Player player) {
            this.goblin = goblin;
            this.initialPlayerTarget = player;
        }

        @Override
        public void run() {
            if (!goblin.isValid() || goblin.isDead()) {
                cleanupGoblin(goblin.getUniqueId(), false);
                return;
            }

            if (isFleeing) {
                if ((System.currentTimeMillis() - spawnTime) / 1000 > FLEE_TIMEOUT_SECONDS + (fleeAttempts * 5)) {
                    plugin.getLogger().info("Loot Goblin escaped from " + initialPlayerTarget.getName());
                    initialPlayerTarget.sendMessage(Component.text("The Loot Goblin got away!", NamedTextColor.RED));
                    cleanupGoblin(goblin.getUniqueId(), true);
                    return;
                }
                if (fleePathfindTicks++ % 2 == 0) {
                    fleeFromPlayer();
                }
                return;
            }

            if (targetChest == null && !hasReachedChest && goblin.getEquipment().getItemInMainHand().getType() != Material.DIAMOND_SWORD) {
                targetChest = findNearbyChest(goblin.getLocation(), CHEST_SEARCH_RADIUS);
                if (targetChest != null) {
                    goblin.getWorld().playSound(goblin.getLocation(), Sound.ENTITY_PIGLIN_ADMIRING_ITEM, 1.0f, 1.5f);
                } else {
                    goblin.getEquipment().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
                    goblin.setMetadata(AGGRO_SWORD_METADATA_KEY, new FixedMetadataValue(plugin, true));
                    goblin.getWorld().playSound(goblin.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 1.0f, 0.8f);
                }
            }

            if (targetChest != null && !hasReachedChest) {
                double distanceToChest = goblin.getLocation().distanceSquared(targetChest.getLocation().add(0.5,0.5,0.5));
                if (distanceToChest < 2.25) {
                    stealFromChest();
                    hasReachedChest = true;
                    isFleeing = true;
                    spawnTime = System.currentTimeMillis();
                    fleeFromPlayer(); 
                } else {
                    goblin.getPathfinder().moveTo(targetChest.getLocation());
                }
            } else if (goblin.getEquipment().getItemInMainHand().getType() == Material.DIAMOND_SWORD) {
                if (initialPlayerTarget.isValid() && initialPlayerTarget.isOnline() && !initialPlayerTarget.isDead()){
                    goblin.getPathfinder().moveTo(initialPlayerTarget.getLocation());
                } else {
                     cleanupGoblin(goblin.getUniqueId(), true);
                }
            }
        }
        
        private void stealFromChest() {
            if (targetChest.getState() instanceof Chest) {
                Chest chest = (Chest) targetChest.getState();
                Inventory chestInv = chest.getBlockInventory();
                List<ItemStack> possibleItems = new ArrayList<>();
                for (ItemStack item : chestInv.getContents()) {
                    if (item != null && item.getType() != Material.AIR) {
                        possibleItems.add(item.clone());
                    }
                }

                if (!possibleItems.isEmpty()) {
                    ItemStack stolen = possibleItems.get(random.nextInt(possibleItems.size()));
                    ItemStack stackStolen = stolen.clone(); // Goblin takes the whole stack
                    // No need to set amount to 1 anymore since we want the whole stack

                    // Try to remove the entire stack from the chest
                    ItemStack toRemoveFromChest = stackStolen.clone();
                    HashMap<Integer, ItemStack> notRemoved = chestInv.removeItem(toRemoveFromChest);
                    
                    if (notRemoved.isEmpty()){ // Successfully removed
                        carriedItem = stackStolen;
                        goblin.getEquipment().setItemInMainHand(carriedItem);
                        goblin.setMetadata(STOLEN_ITEM_METADATA_KEY, new FixedMetadataValue(plugin, carriedItem));
                        String itemDesc = stackStolen.getAmount() > 1 ? 
                            stackStolen.getAmount() + " " + stackStolen.getType().toString().toLowerCase().replace('_', ' ') :
                            "a " + stackStolen.getType().toString().toLowerCase().replace('_', ' ');
                        initialPlayerTarget.sendMessage(Component.text("The Loot Goblin stole " + itemDesc + " from a chest!", NamedTextColor.RED));
                        goblin.getWorld().playSound(goblin.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
                    } else {
                        // Failed to remove (e.g. item changed by another player), goblin gives up on this chest
                        initialPlayerTarget.sendMessage(Component.text("The Loot Goblin fumbled with the chest and gave up!", NamedTextColor.YELLOW));
                        targetChest = null; // Will try to find another or go aggro
                        hasReachedChest = false;
                    }
                } else {
                     initialPlayerTarget.sendMessage(Component.text("The Loot Goblin peeked into an empty chest!", NamedTextColor.YELLOW));
                     targetChest = null; // Will try to find another or go aggro
                     hasReachedChest = false;
                }
            } else {
                targetChest = null; // Not a chest anymore? Try again.
                hasReachedChest = false;
            }
        }

        private void fleeFromPlayer() {
            if (!initialPlayerTarget.isValid() || !initialPlayerTarget.isOnline()) {
                cleanupGoblin(goblin.getUniqueId(), true); // Player gone, goblin escapes
                return;
            }

            // Check if goblin is far enough to potentially despawn
            double distanceToPlayer = goblin.getLocation().distance(initialPlayerTarget.getLocation());
            if (distanceToPlayer > FLEE_RADIUS && random.nextDouble() < 0.1) { // 10% chance to despawn when far enough
                plugin.getLogger().info("Loot Goblin escaped from " + initialPlayerTarget.getName());
                initialPlayerTarget.sendMessage(Component.text("The Loot Goblin disappeared into the shadows!", NamedTextColor.RED));
                cleanupGoblin(goblin.getUniqueId(), true);
                return;
            }

            if(fleeAttempts >= MAX_FLEE_ATTEMPTS){
                plugin.getLogger().info("Loot Goblin couldn't find a flee path after " + MAX_FLEE_ATTEMPTS + " attempts for "+ initialPlayerTarget.getName());
                cleanupGoblin(goblin.getUniqueId(), true);
                return;
            }

            Location playerLoc = initialPlayerTarget.getLocation();
            Location goblinLoc = goblin.getLocation();
            
            // Calculate a random point to flee to
            double angle = random.nextDouble() * 2 * Math.PI; // Random angle
            double fleeDistance = MIN_FLEE_DISTANCE + (random.nextDouble() * 10); // Random distance between 10-20 blocks
            
            // Add some randomness to the flee direction
            Vector playerToGoblin = goblinLoc.toVector().subtract(playerLoc.toVector()).normalize();
            Vector perpendicular = new Vector(-playerToGoblin.getZ(), 0, playerToGoblin.getX());
            
            // Mix the direct flee vector with a perpendicular vector for more circular motion
            Vector fleeDir = playerToGoblin.clone().multiply(0.7) // 70% away from player
                .add(perpendicular.multiply(random.nextDouble() - 0.5)) // Add sideways movement
                .normalize()
                .multiply(fleeDistance);

            Location fleeTo = goblinLoc.clone().add(fleeDir);

            // Try to find a valid location
            for(int i = 0; i < 5; i++) {
                Location tempFleeTo = goblinLoc.clone().add(fleeDir.clone().multiply((random.nextDouble() * 0.5) + 0.5));
                Block blockAtFlee = tempFleeTo.getBlock();
                if(blockAtFlee.isPassable() && blockAtFlee.getRelative(BlockFace.UP).isPassable() 
                   && !blockAtFlee.getRelative(BlockFace.DOWN).isPassable()) {
                    fleeTo = tempFleeTo;
                    break;
                }
                // Rotate the flee direction if we can't find a spot
                fleeDir.rotateAroundY(Math.PI / 4);
            }
            
            goblin.getPathfinder().moveTo(fleeTo);
            
            // Add some playful particles and sounds
            if (random.nextDouble() < 0.2) { // 20% chance for extra effects
                goblin.getWorld().playSound(goblin.getLocation(), Sound.ENTITY_DOLPHIN_PLAY, 0.5f, 1.8f);
                goblin.getWorld().spawnParticle(Particle.HEART, goblin.getLocation().add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0);
            } else {
                goblin.getWorld().playSound(goblin.getLocation(), Sound.ENTITY_CHICKEN_STEP, 0.5f, 1.8f);
            }
            
            fleeAttempts++;
        }

        private Block findNearbyChest(Location center, double radius) {
            World world = center.getWorld();
            if (world == null) return null;
            List<Block> chests = new ArrayList<>();
            int R = (int) radius;
            for (int x = -R; x <= R; x++) {
                for (int y = -R; y <= R; y++) {
                    for (int z = -R; z <= R; z++) {
                        Block block = world.getBlockAt(center.getBlockX() + x, center.getBlockY() + y, center.getBlockZ() + z);
                        if (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST) {
                            if (block.getLocation().distanceSquared(center) <= radius * radius) {
                                chests.add(block);
                            }
                        }
                    }
                }
            }
            return chests.isEmpty() ? null : chests.get(random.nextInt(chests.size()));
        }
    }
}
