package nc.randomEvents.services.events.LootGoblin;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.services.events.Event;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.PigZombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
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
    private static final String CRYING_METADATA_KEY = "loot_goblin_crying";
    private final NamespacedKey goblinUniqueIdKey;

    private final Map<UUID, GoblinTask> activeGoblins = new ConcurrentHashMap<>(); // Zombie UUID to its task
    private static final int CHEST_SEARCH_RADIUS = 20;
    private static final int FLEE_TIMEOUT_SECONDS = 60;

    // Item Categorization Sets
    private static final Set<Material> HIGH_VALUE_ITEMS = EnumSet.of(
            Material.DIAMOND, Material.DIAMOND_BLOCK,
            Material.EMERALD, Material.EMERALD_BLOCK,
            Material.NETHERITE_INGOT, Material.NETHERITE_BLOCK, Material.NETHERITE_SCRAP, Material.ANCIENT_DEBRIS,
            Material.TOTEM_OF_UNDYING, Material.ELYTRA,
            Material.ENCHANTED_BOOK,
            Material.NETHER_STAR, Material.BEACON,
            Material.SHULKER_BOX
    );

    private static final Set<Material> VALUABLE_MINERALS = EnumSet.of(
            Material.GOLD_INGOT, Material.GOLD_BLOCK, Material.RAW_GOLD, Material.RAW_GOLD_BLOCK,
            Material.IRON_INGOT, Material.IRON_BLOCK, Material.RAW_IRON, Material.RAW_IRON_BLOCK,
            Material.LAPIS_LAZULI, Material.LAPIS_BLOCK,
            Material.REDSTONE, Material.REDSTONE_BLOCK,
            Material.COAL, Material.COAL_BLOCK,
            Material.QUARTZ, Material.QUARTZ_BLOCK,
            Material.AMETHYST_SHARD, Material.COPPER_INGOT, Material.RAW_COPPER // Added Copper
    );

    // Helper method to identify armor
    private static boolean isArmor(Material material) {
        String name = material.name();
        // Covers leather, chainmail, iron, gold, diamond, netherite, turtle helmets
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") ||
               name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
    }

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
    public void execute(Set<Player> players) {
        for (Player player : players) {
            if (!player.isOnline() || player.isDead()) continue;
            spawnGoblinForPlayer(player);
        }
    }

    private void spawnGoblinForPlayer(Player player) {
        Location playerLoc = player.getLocation();
        World world = player.getWorld();
        Location spawnLoc = null;

        Vector direction = playerLoc.getDirection().normalize();
        // Calculate target X, Z ~two blocks behind the player
        double targetX = playerLoc.getX() - direction.getX() * 2.0;
        double targetZ = playerLoc.getZ() - direction.getZ() * 2.0;

        // Try to find a safe spot around the player's Y level at the target X, Z
        // Checking player's Y, Y-1, Y-2 (for ground if player is slightly above it)
        for (int yDelta = 0; yDelta >= -2; yDelta--) {
            Location potentialFeetLoc = new Location(world, targetX, playerLoc.getY() + yDelta, targetZ);
            if (isSafeToSpawn(potentialFeetLoc)) {
                spawnLoc = new Location(world, potentialFeetLoc.getX() + 0.5, potentialFeetLoc.getY(), potentialFeetLoc.getZ() + 0.5);
                break;
            }
        }
        
        // If not found, try one block above player's Y (e.g. if player is slightly in ground or behind is a step up)
        if (spawnLoc == null) {
            Location potentialFeetLoc = new Location(world, targetX, playerLoc.getY() + 1, targetZ);
            if (isSafeToSpawn(potentialFeetLoc)) {
                spawnLoc = new Location(world, potentialFeetLoc.getX() + 0.5, potentialFeetLoc.getY(), potentialFeetLoc.getZ() + 0.5);
            }
        }

        if (spawnLoc == null) {
            plugin.getLogger().warning("LootGoblinEvent: Could not find a safe spawn location behind " + player.getName() + ". Goblin will not spawn.");
            return;
        }

        PigZombie goblin = (PigZombie) world.spawnEntity(spawnLoc, EntityType.ZOMBIFIED_PIGLIN);
        goblin.setAge(-1); // Ensure it's a baby
        goblin.customName(Component.text("Loot Goblin", NamedTextColor.GOLD));
        goblin.setCustomNameVisible(true);

        if (goblin.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
            double speedMultiplier = plugin.getConfigManager().getConfigValue(getName(), "speed");
            double baseSpeed = goblin.getAttribute(Attribute.MOVEMENT_SPEED).getBaseValue();
            goblin.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(baseSpeed * speedMultiplier);
        }

        // Ensure goblin starts with an empty hand
        if (goblin.getEquipment() != null) {
            goblin.getEquipment().setItemInMainHand(new ItemStack(Material.AIR));
            goblin.getEquipment().setItemInMainHandDropChance(0.0f); // Prevent dropping this 'nothing'
        }

        goblin.getPersistentDataContainer().set(goblinUniqueIdKey, PersistentDataType.STRING, goblin.getUniqueId().toString());
        goblin.setMetadata(LOOT_GOBLIN_METADATA_KEY, new FixedMetadataValue(plugin, true));

        // Make goblin completely passive
        goblin.setTarget(null);
        goblin.setCanPickupItems(false);
        goblin.setSilent(true);
        goblin.setCanBreakDoors(true);

        // Enable door opening for the pathfinder
        if (goblin.getPathfinder() != null) {
            goblin.getPathfinder().setCanOpenDoors(true);
        }

        player.sendMessage(Component.text("You hear a faint giggle... a ", NamedTextColor.YELLOW)
            .append(Component.text("Loot Goblin", NamedTextColor.GOLD))
            .append(Component.text(" has appeared nearby!", NamedTextColor.YELLOW)));
        world.playSound(goblin.getLocation(), Sound.ENTITY_PIGLIN_JEALOUS, SoundCategory.HOSTILE, 1.0f, 1.5f);

        GoblinTask task = new GoblinTask(goblin, player);
        activeGoblins.put(goblin.getUniqueId(), task);
        task.runTaskTimer(plugin, 0L, 5L); // Run AI tick every 5 ticks (0.25s)
    }

    // Helper method to check if a location is safe for a 2-block high mob
    private boolean isSafeToSpawn(Location feetLocation) {
        World world = feetLocation.getWorld();
        if (world == null) return false;

        // Ensure coordinates are integers for block fetching
        int x = feetLocation.getBlockX();
        int y = feetLocation.getBlockY();
        int z = feetLocation.getBlockZ();

        // Check world bounds (feet location is Y, head is Y+1)
        if (y < world.getMinHeight() || y + 1 > world.getMaxHeight()) {
            return false;
        }

        Block blockBelowFeet = world.getBlockAt(x, y - 1, z);
        Block blockAtFeet = world.getBlockAt(x, y, z);
        Block blockAtHead = world.getBlockAt(x, y + 1, z);

        return !blockBelowFeet.isPassable() && // Solid ground
               blockAtFeet.isPassable() &&    // Space for feet
               blockAtHead.isPassable() &&    // Space for head
               !blockAtFeet.isLiquid() &&     // Not in liquid at feet
               !blockBelowFeet.isLiquid();    // Not standing on liquid source that looks solid (e.g. top of waterfall)
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
        if (!(event.getEntity() instanceof LivingEntity && event.getEntity().hasMetadata(LOOT_GOBLIN_METADATA_KEY))) {
            return;
        }
        LivingEntity livingEntity = (LivingEntity) event.getEntity();
        // Now we know it's our goblin, cast if PigZombie specific methods are needed,
        // but for current logic, LivingEntity is sufficient for most parts.
        // For consistency and future use, we can cast to PigZombie if needed.
        // PigZombie goblin = (PigZombie) livingEntity;

        // Cancel the damage event - we don't want the goblin to actually take damage
        event.setCancelled(true);

        // If already crying, ignore additional hits
        if (livingEntity.hasMetadata(CRYING_METADATA_KEY)) return;

        // Mark as crying
        livingEntity.setMetadata(CRYING_METADATA_KEY, new FixedMetadataValue(plugin, true));

        // Drop the item if carrying one
        if (livingEntity.hasMetadata(STOLEN_ITEM_METADATA_KEY)) {
            ItemStack stolenItem = (ItemStack) livingEntity.getMetadata(STOLEN_ITEM_METADATA_KEY).get(0).value();
            if (stolenItem != null && stolenItem.getType() != Material.AIR) {
                livingEntity.getWorld().dropItemNaturally(livingEntity.getLocation(), stolenItem);
                if (livingEntity.getEquipment() != null) { // Null check for equipment
                    livingEntity.getEquipment().setItemInMainHand(new ItemStack(Material.AIR));
                }
                livingEntity.removeMetadata(STOLEN_ITEM_METADATA_KEY, plugin);
            }
        }

        // Play crying effects
        Location loc = livingEntity.getLocation();
        World world = livingEntity.getWorld();
        world.playSound(loc, Sound.ENTITY_DOLPHIN_DEATH, 1.0f, 2.0f); // High-pitched sad sound
        Location particleLoc = loc.clone().add(0, 1.5, 0);
        world.spawnParticle(Particle.CLOUD, particleLoc.getX(), particleLoc.getY(), particleLoc.getZ(), 20, 0.2, 0.2, 0.2, 0);

        // Schedule disappearance after crying
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!livingEntity.isValid() || livingEntity.isDead()) {
                    this.cancel();
                    return;
                }

                ticks++;
                if (ticks % 10 == 0) { // Every half second
                    // Continue crying effects
                    Location currentParticleLoc = livingEntity.getLocation().add(0, 1.5, 0);
                    world.spawnParticle(Particle.CLOUD, currentParticleLoc.getX(), currentParticleLoc.getY(), currentParticleLoc.getZ(), 5, 0.2, 0.2, 0.2, 0);
                    world.playSound(livingEntity.getLocation(), Sound.ENTITY_DOLPHIN_HURT, 0.5f, 2.0f);
                }

                if (ticks >= 60) { // After 3 seconds
                    // Final disappearance
                    Location finalParticleLoc = livingEntity.getLocation().add(0, 1, 0);
                    world.playSound(livingEntity.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    world.spawnParticle(Particle.CLOUD, finalParticleLoc.getX(), finalParticleLoc.getY(), finalParticleLoc.getZ(), 30, 0.3, 0.5, 0.3, 0.05);
                    cleanupGoblin(livingEntity.getUniqueId(), true);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @EventHandler
    public void onGoblinTargetPlayer(EntityTargetLivingEntityEvent event) {
        if (event.getEntity().hasMetadata(LOOT_GOBLIN_METADATA_KEY)) {
            if (event.getTarget() instanceof Player) {
                // Prevent the Loot Goblin from targeting players
                event.setCancelled(true);
            }
        }
    }

    private void cleanupGoblin(UUID goblinId, boolean withEscapeEffect) {
        GoblinTask task = activeGoblins.remove(goblinId);
        if (task != null) {
            task.cancel();
            PigZombie goblinEntity = task.goblin; // Changed type
            if (goblinEntity != null && goblinEntity.isValid()) {
                if (withEscapeEffect && !goblinEntity.hasMetadata(CRYING_METADATA_KEY)) {
                    goblinEntity.getWorld().playSound(goblinEntity.getLocation(), Sound.ENTITY_FOX_TELEPORT, 1.0f, 1.0f);
                    Location escapeParticleLoc = goblinEntity.getLocation().add(0, 1, 0);
                    goblinEntity.getWorld().spawnParticle(Particle.CLOUD, escapeParticleLoc.getX(), escapeParticleLoc.getY(), escapeParticleLoc.getZ(), 20, 0.3, 0.3, 0.3, 0.05);
                }
                goblinEntity.remove();
            }
        }
    }

    private class GoblinTask extends BukkitRunnable {
        final PigZombie goblin; // Changed type from ZombifiedPiglin to PigZombie
        final Player initialPlayerTarget;
        Block targetChest = null;
        ItemStack carriedItem = null;
        boolean hasReachedChest = false;
        boolean isFleeing = false;
        long spawnTime = System.currentTimeMillis(); // This marks the initial spawn or task start time
        int fleePathfindTicks = 0;
        int fleeAttempts = 0;
        private Location fleeDestination = null;

        GoblinTask(PigZombie goblin, Player player) { // Changed type
            this.goblin = goblin;
            this.initialPlayerTarget = player;
        }

        @Override
        public void run() {
            if (!goblin.isValid() || goblin.isDead()) {
                cleanupGoblin(goblin.getUniqueId(), false);
                return;
            }

            // Continuously ensure passivity and pathfinder settings
            goblin.setTarget(null);
            if (goblin.getPathfinder() != null) {
                goblin.getPathfinder().setCanOpenDoors(true);
            }

            // Timeout for finding/reaching a chest
            if (!isFleeing && !hasReachedChest && (System.currentTimeMillis() - spawnTime) / 1000 > 30) {
                plugin.getLogger().info("Loot Goblin for " + initialPlayerTarget.getName() + " timed out before reaching a chest.");
                initialPlayerTarget.sendMessage(Component.text("The Loot Goblin got bored and vanished before finding a suitable chest!", NamedTextColor.YELLOW));
                cleanupGoblin(goblin.getUniqueId(), true); // True for escape effect
                return;
            }

            if (isFleeing) {
                if ((System.currentTimeMillis() - spawnTime) / 1000 > FLEE_TIMEOUT_SECONDS + (fleeAttempts * 5)) { // Note: spawnTime is reset when fleeing starts
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

            if (targetChest == null && !hasReachedChest) {
                targetChest = findNearbyChest(goblin.getLocation(), CHEST_SEARCH_RADIUS);
                if (targetChest != null) {
                    goblin.getWorld().playSound(goblin.getLocation(), Sound.ENTITY_PIGLIN_ADMIRING_ITEM, 1.0f, 1.5f);
                } else {
                    // If no chest found, start fleeing
                    isFleeing = true;
                    spawnTime = System.currentTimeMillis();
                    fleeFromPlayer();
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
                    // Path to the top center of the chest block to encourage climbing
                    Location pathTarget = targetChest.getLocation().add(0.5, 1.0, 0.5);
                    goblin.getPathfinder().moveTo(pathTarget);
                }
            }
        }
        
        private void stealFromChest() {
            if (targetChest.getState() instanceof Chest) {
                Chest chest = (Chest) targetChest.getState();
                Inventory chestInv = chest.getBlockInventory();
                List<ItemStack> possibleItems = new ArrayList<>();

                // Prioritized item selection
                List<ItemStack> highValueSteal = new ArrayList<>();
                List<ItemStack> armorSteal = new ArrayList<>();
                List<ItemStack> mineralSteal = new ArrayList<>();
                List<ItemStack> foodSteal = new ArrayList<>();

                for (ItemStack item : chestInv.getContents()) {
                    if (item != null && item.getType() != Material.AIR) {
                        Material mat = item.getType();
                        if (HIGH_VALUE_ITEMS.contains(mat)) {
                            highValueSteal.add(item.clone());
                        } else if (isArmor(mat)) {
                            armorSteal.add(item.clone());
                        } else if (VALUABLE_MINERALS.contains(mat)) {
                            mineralSteal.add(item.clone());
                        } else if (mat.isEdible()) {
                            foodSteal.add(item.clone());
                        }
                    }
                }

                if (!highValueSteal.isEmpty()) {
                    possibleItems.addAll(highValueSteal);
                } else if (!armorSteal.isEmpty()) {
                    possibleItems.addAll(armorSteal);
                } else if (!mineralSteal.isEmpty()) {
                    possibleItems.addAll(mineralSteal);
                } else if (!foodSteal.isEmpty()) {
                    possibleItems.addAll(foodSteal);
                }

                if (!possibleItems.isEmpty()) {
                    ItemStack stolen = possibleItems.get(random.nextInt(possibleItems.size()));
                    // ItemStack stackStolen = stolen.clone(); // Already cloned when added to lists

                    // Try to remove the entire stack from the chest
                    ItemStack toRemoveFromChest = stolen.clone(); // Use the chosen 'stolen' item for removal
                    HashMap<Integer, ItemStack> notRemoved = chestInv.removeItem(toRemoveFromChest);
                    
                    if (notRemoved.isEmpty()){ // Successfully removed
                        carriedItem = stolen; // Use the 'stolen' item which is already a clone
                        goblin.getEquipment().setItemInMainHand(carriedItem);
                        goblin.setMetadata(STOLEN_ITEM_METADATA_KEY, new FixedMetadataValue(plugin, carriedItem));
                        String itemDesc = stolen.getAmount() > 1 ?
                            stolen.getAmount() + " " + stolen.getType().toString().toLowerCase().replace('_', ' ') :
                            "a " + stolen.getType().toString().toLowerCase().replace('_', ' ');
                        initialPlayerTarget.sendMessage(Component.text("The Loot Goblin stole " + itemDesc + " from a chest!", NamedTextColor.RED));
                        goblin.getWorld().playSound(goblin.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
                    } else {
                        // Failed to remove (e.g. item changed by another player), goblin gives up on this chest
                        initialPlayerTarget.sendMessage(Component.text("The Loot Goblin fumbled with the chest and gave up!", NamedTextColor.YELLOW));
                        targetChest = null; // Will try to find another or go aggro
                        hasReachedChest = false;
                    }
                } else {
                     initialPlayerTarget.sendMessage(Component.text("The Loot Goblin peeked into a chest, but found nothing of interest!", NamedTextColor.YELLOW)); // Changed message
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

            // If we don't have a flee destination, pick one 30 blocks away
            // in a random direction from the player's current location.
            // This destination remains fixed for this fleeing phase.
            if (fleeDestination == null) {
                Location playerLoc = initialPlayerTarget.getLocation();
                double angle = random.nextDouble() * 2 * Math.PI;
                double dx = Math.cos(angle) * 30; // 30 blocks away
                double dz = Math.sin(angle) * 30;
                Location dest = playerLoc.clone().add(dx, 0, dz);
                // Set Y to highest block at that X/Z for safety
                if (playerLoc.getWorld() != null) { // Null check for world
                    dest.setY(playerLoc.getWorld().getHighestBlockYAt(dest));
                    fleeDestination = dest.add(0.5, 1.0, 0.5); // Center on block and ensure it's a walkable height
                } else {
                    // Fallback if world is null, shouldn't happen in normal gameplay
                    cleanupGoblin(goblin.getUniqueId(), true);
                    return;
                }
            }

            // Simply tell the goblin to pathfind to the destination.
            if (fleeDestination != null) { // Check if fleeDestination was successfully set
                goblin.getPathfinder().moveTo(fleeDestination);

                // Check if the goblin has effectively reached its destination.
                // Using a threshold of 2.5*2.5 blocks (distance squared 6.25).
                if (goblin.getLocation().distanceSquared(fleeDestination) < 6.25) {
                    plugin.getLogger().info("Loot Goblin reached its flee point and escaped from " + initialPlayerTarget.getName());
                    initialPlayerTarget.sendMessage(Component.text("The Loot Goblin disappeared into the shadows!", NamedTextColor.RED));
                    cleanupGoblin(goblin.getUniqueId(), true);
                    return; // Goblin has escaped, task will be cancelled
                }
            } else {
                // If fleeDestination is still null (e.g. world was null), goblin escapes as a fallback
                 plugin.getLogger().warning("Loot Goblin could not determine a flee destination, escaping.");
                 cleanupGoblin(goblin.getUniqueId(), true);
                 return;
            }

            // Playful effects and step-by-step movement logic removed.
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
