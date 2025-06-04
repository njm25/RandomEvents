package nc.randomEvents.events.ZombieHorde;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.core.BaseEvent;
import nc.randomEvents.services.RewardGenerator;
import nc.randomEvents.services.EntityManager;
import nc.randomEvents.utils.EntityHelper;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.HashMap;

public class ZombieHordeEvent extends BaseEvent {
    private final RandomEvents plugin;
    private final RewardGenerator rewardGenerator;
    private final EntityManager entityManager;
    private final Random random = new Random();
    private final ConcurrentHashMap<UUID, List<Zombie>> activeZombies = new ConcurrentHashMap<>(); // Player UUID to their wave zombies
    private final ConcurrentHashMap<UUID, Integer> playerWaves = new ConcurrentHashMap<>(); // Track current wave per player
    private final int SPAWN_MIN_DISTANCE = 15;
    private final int SPAWN_MAX_DISTANCE = 25;

    public ZombieHordeEvent(RandomEvents plugin) {
        this.plugin = plugin;
        this.rewardGenerator = plugin.getRewardGenerator();
        this.entityManager = plugin.getEntityManager();
        
        // Configure event settings
        setTickInterval(20L); // Check wave status every second
        setDuration(0); // No fixed duration, ends when waves are complete
        setStripsInventory(false);
        setCanBreakBlocks(true);
        setCanPlaceBlocks(true);
    }

    @Override
    public String getName() {
        return "ZombieHordeEvent";
    }

    @Override
    public String getDescription() {
        return "Spawns waves of zombies that drop loot when a wave is cleared.";
    }

    @Override
    public void onStart(UUID sessionId, Set<Player> players) {
        playerWaves.clear();
        activeZombies.clear();
        
        for (Player player : players) {
            if (player.isOnline() && !player.isDead()) {
                playerWaves.put(player.getUniqueId(), 1); // Start at wave 1
                startWave(player, 1);
            }
        }
    }

    @Override
    public void onTick(UUID sessionId, Set<Player> players) {
        // Check wave status for each player
        for (Player player : players) {
            if (!player.isOnline() || player.isDead()) {
                cleanupPlayerZombies(player.getUniqueId());
                playerWaves.remove(player.getUniqueId());
                continue;
            }

            List<Zombie> waveZombies = activeZombies.get(player.getUniqueId());
            if (waveZombies != null) {
                waveZombies.removeIf(zombie -> zombie == null || zombie.isDead());

                if (waveZombies.isEmpty()) {
                    int currentWave = playerWaves.getOrDefault(player.getUniqueId(), 1);
                    
                    // Current wave cleared
                    giveWaveRewards(player, currentWave);
                    int totalWaves = plugin.getConfigManager().getConfigValue(getName(), "waves");
                    
                    if (currentWave < totalWaves) {
                        int nextWave = currentWave + 1;
                        playerWaves.put(player.getUniqueId(), nextWave);
                        startWave(player, nextWave);
                    } else {
                        // All waves cleared
                        activeZombies.remove(player.getUniqueId());
                        playerWaves.remove(player.getUniqueId());
                        player.sendMessage("You survived the zombie horde!");
                    }
                }
            }
        }
    }

    @Override
    public void onEnd(UUID sessionId, Set<Player> players) {
        // Clean up any remaining zombies
        for (UUID playerId : new ArrayList<>(activeZombies.keySet())) {
            cleanupPlayerZombies(playerId);
        }
        activeZombies.clear();
        playerWaves.clear();
    }

    private void startWave(Player player, int waveNumber) {
        List<Zombie> waveZombies = new ArrayList<>();
        activeZombies.put(player.getUniqueId(), waveZombies);

        int zombiesPerWave = plugin.getConfigManager().getConfigValue(getName(), "zombiesPerWave");
        for (int i = 0; i < zombiesPerWave; i++) {
            spawnZombieNearPlayer(player, waveZombies, waveNumber);
        }

        // Monitor this wave only for player disconnect/death
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    cleanupPlayerZombies(player.getUniqueId());
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 20L * 5, 20L); // Check every second, after an initial 5-second delay
    }

    private void spawnZombieNearPlayer(Player player, List<Zombie> waveZombies, int waveNumber) {
        Location playerLocation = player.getLocation();
        World world = player.getWorld();
        Location spawnLocation = null;

        int attempts = 0;
        while (spawnLocation == null && attempts < 50) { // Try 50 times to find a valid spot
            attempts++;
            double angle = random.nextDouble() * 2 * Math.PI; // Random angle
            double distance = SPAWN_MIN_DISTANCE + random.nextDouble() * (SPAWN_MAX_DISTANCE - SPAWN_MIN_DISTANCE); // Random distance within range

            double x = playerLocation.getX() + distance * Math.cos(angle);
            double z = playerLocation.getZ() + distance * Math.sin(angle);
            double y = world.getHighestBlockYAt((int) x, (int) z) + 1; // Spawn on surface

            Location potentialLoc = new Location(world, x, y, z);
            if (isSafeSpawnLocation(potentialLoc)) {
                spawnLocation = potentialLoc;
            }
        }

        if (spawnLocation == null) {
            // Fallback if no good location found
            plugin.getLogger().warning("Could not find a safe spawn location for zombie near " + player.getName() + ". Spawning at player's approximate location.");
            spawnLocation = player.getLocation().add(random.nextInt(5) - 2, 0, random.nextInt(5) - 2);
            // Ensure it's on the ground
            spawnLocation.setY(world.getHighestBlockYAt(spawnLocation.getBlockX(), spawnLocation.getBlockZ()) + 1);
        }

        // Use EntityManager to spawn and track the zombie
        Zombie zombie = entityManager.spawnTracked(EntityType.ZOMBIE, spawnLocation, "wave_" + waveNumber + "_zombie", player.getUniqueId());
        
        // Configure zombie attributes
        EntityHelper.setMaxHealth(zombie, 20.0);
        EntityHelper.setMovementSpeed(zombie, 0.23); // Default zombie speed
        
        // Use EquipmentManager to equip the zombie based on wave number
        if (random.nextDouble() < 0.5) { // 50% chance for equipment
            Map<Integer, ItemStack> equipment = generateZombieEquipment(waveNumber == 1 ? RewardGenerator.Tier.BASIC : RewardGenerator.Tier.COMMON);
            EntityEquipment zombieEquipment = zombie.getEquipment();
            if (zombieEquipment != null) {
                // Set equipment drop chances
                zombieEquipment.setHelmetDropChance(0.1f);
                zombieEquipment.setChestplateDropChance(0.1f);
                zombieEquipment.setLeggingsDropChance(0.1f);
                zombieEquipment.setBootsDropChance(0.1f);
                zombieEquipment.setItemInMainHandDropChance(0.05f); // Very low drop chance for weapons

                // Apply equipment
                if (equipment.containsKey(39)) zombieEquipment.setHelmet(equipment.get(39));
                if (equipment.containsKey(38)) zombieEquipment.setChestplate(equipment.get(38));
                if (equipment.containsKey(37)) zombieEquipment.setLeggings(equipment.get(37));
                if (equipment.containsKey(36)) zombieEquipment.setBoots(equipment.get(36));
                if (equipment.containsKey(0)) zombieEquipment.setItemInMainHand(equipment.get(0));
            }
        }

        waveZombies.add(zombie);
        zombie.setTarget(player);
    }

    private Map<Integer, ItemStack> generateZombieEquipment(RewardGenerator.Tier tier) {
        Map<Integer, ItemStack> equipment = new HashMap<>();
        
        // Helmet slot (39)
        if (random.nextDouble() < 0.5) {
            Material helmet;
            if (random.nextBoolean()) helmet = Material.LEATHER_HELMET;
            else if (random.nextDouble() < 0.7) helmet = Material.CHAINMAIL_HELMET;
            else helmet = Material.IRON_HELMET;
            equipment.put(39, new ItemStack(helmet));
        }
        
        // Chestplate slot (38)
        if (random.nextDouble() < 0.5) {
            Material chestplate;
            if (random.nextBoolean()) chestplate = Material.LEATHER_CHESTPLATE;
            else if (random.nextDouble() < 0.7) chestplate = Material.CHAINMAIL_CHESTPLATE;
            else chestplate = Material.IRON_CHESTPLATE;
            equipment.put(38, new ItemStack(chestplate));
        }
        
        // Leggings slot (37)
        if (random.nextDouble() < 0.5) {
            Material leggings;
            if (random.nextBoolean()) leggings = Material.LEATHER_LEGGINGS;
            else if (random.nextDouble() < 0.7) leggings = Material.CHAINMAIL_LEGGINGS;
            else leggings = Material.IRON_LEGGINGS;
            equipment.put(37, new ItemStack(leggings));
        }
        
        // Boots slot (36)
        if (random.nextDouble() < 0.5) {
            Material boots;
            if (random.nextBoolean()) boots = Material.LEATHER_BOOTS;
            else if (random.nextDouble() < 0.7) boots = Material.CHAINMAIL_BOOTS;
            else boots = Material.IRON_BOOTS;
            equipment.put(36, new ItemStack(boots));
        }
        
        // Main hand slot (0)
        if (random.nextDouble() < 0.5) {
            Material sword;
            if (random.nextBoolean()) sword = Material.WOODEN_SWORD;
            else if (random.nextDouble() < 0.7) sword = Material.STONE_SWORD;
            else sword = Material.IRON_SWORD;
            equipment.put(0, new ItemStack(sword));
        }
        
        return equipment;
    }

    private boolean isSafeSpawnLocation(Location loc) {
        if (loc.getY() < loc.getWorld().getMinHeight() || loc.getY() > loc.getWorld().getMaxHeight() - 2) return false;
        return loc.getBlock().isPassable() &&
                loc.clone().add(0, 1, 0).getBlock().isPassable() &&
                !loc.clone().add(0, -1, 0).getBlock().isPassable() &&
                !loc.getBlock().isLiquid();
    }

    private void giveWaveRewards(Player player, int waveNumber) {
        int itemsToGenerate = 1 + waveNumber; // Wave 1: 2 items, Wave 2: 3 items
        RewardGenerator.Tier tier = waveNumber == 1 ? RewardGenerator.Tier.BASIC : RewardGenerator.Tier.COMMON;
        List<ItemStack> rewards = rewardGenerator.generateRewards(tier, itemsToGenerate);
        if (!rewards.isEmpty()) {
            player.sendMessage("Wave " + waveNumber + " cleared! You received some loot!");
            for (ItemStack item : rewards) {
                player.getInventory().addItem(item).forEach((index, leftoverItem) -> {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftoverItem);
                });
            }
        }
    }

    private void cleanupPlayerZombies(UUID playerUUID) {
        List<Zombie> zombies = activeZombies.remove(playerUUID);
        if (zombies != null) {
            for (Zombie zombie : zombies) {
                if (zombie != null && !zombie.isDead()) {
                    zombie.remove();
                }
            }
        }
    }
}
