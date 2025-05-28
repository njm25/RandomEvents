package nc.randomEvents.services.events.ZombieHoard;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.services.RewardGenerator;
import nc.randomEvents.services.events.Event;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ZombieHoardEvent implements Event {

    private final RandomEvents plugin;
    private final RewardGenerator rewardGenerator;
    private final Random random = new Random();
    private final ConcurrentHashMap<UUID, List<Zombie>> activeZombies = new ConcurrentHashMap<>(); // Player UUID to their wave zombies
    private final int SPAWN_MIN_DISTANCE = 15;
    private final int SPAWN_MAX_DISTANCE = 25;

    public ZombieHoardEvent(RandomEvents plugin) {
        this.plugin = plugin;
        this.rewardGenerator = plugin.getRewardGenerator();
    }

    @Override
    public String getName() {
        return "ZombieHoardEvent";
    }

    @Override
    public String getDescription() {
        return "Spawns waves of zombies that drop loot when a wave is cleared.";
    }

    @Override
    public void execute(Set<Player> players) {
        for (Player player : players) {
            if (player.isOnline() && !player.isDead()) {
                startWave(player, 1);
            }
        }
    }

    private void startWave(Player player, int waveNumber) {
        List<Zombie> waveZombies = new ArrayList<>();
        activeZombies.put(player.getUniqueId(), waveZombies);

        int zombiesPerWave = plugin.getConfigManager().getConfigValue(getName(), "zombiesPerWave");
        for (int i = 0; i < zombiesPerWave; i++) {
            spawnZombieNearPlayer(player, waveZombies);
        }

        // Monitor this wave
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    cleanupPlayerZombies(player.getUniqueId());
                    this.cancel();
                    return;
                }

                waveZombies.removeIf(zombie -> zombie == null || zombie.isDead());

                if (waveZombies.isEmpty()) {
                    this.cancel();
                    giveWaveRewards(player, waveNumber);
                    int totalWaves = plugin.getConfigManager().getConfigValue(getName(), "waves");
                    if (waveNumber < totalWaves) {
                        startWave(player, waveNumber + 1);
                    } else {
                        // Last wave cleared
                        activeZombies.remove(player.getUniqueId());
                        player.sendMessage("You survived the zombie hoard!");
                    }
                }
            }
        }.runTaskTimer(plugin, 20L * 5, 20L); // Check every second, after an initial 5-second delay
    }

    private void spawnZombieNearPlayer(Player player, List<Zombie> waveZombies) {
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

            Location potentialSpawn = new Location(world, x, y, z);
            if (isSafeSpawnLocation(potentialSpawn)) {
                spawnLocation = potentialSpawn;
            }
        }

        if (spawnLocation == null) {
            // Fallback if no good location found, spawn at player's feet (not ideal, but better than nothing)
            // Or log an error and don't spawn. For now, let's use player's loc with a small offset.
            plugin.getLogger().warning("Could not find a safe spawn location for zombie near " + player.getName() + ". Spawning at player's approximate location.");
            spawnLocation = player.getLocation().add(random.nextInt(5) - 2, 0, random.nextInt(5) - 2);
            // Ensure it's on the ground
             spawnLocation.setY(world.getHighestBlockYAt(spawnLocation.getBlockX(), spawnLocation.getBlockZ()) +1);
        }


        Zombie zombie = (Zombie) world.spawnEntity(spawnLocation, EntityType.ZOMBIE);
        equipZombie(zombie);
        waveZombies.add(zombie);
        // Optional: Make zombies target the player they spawned for
        zombie.setTarget(player);
    }

    private boolean isSafeSpawnLocation(Location loc) {
        if (loc.getY() < loc.getWorld().getMinHeight() || loc.getY() > loc.getWorld().getMaxHeight() -2) return false; // check basic Y bounds
        // Check if block and block above are air/passable, and block below is solid
        return loc.getBlock().isPassable() &&
               loc.clone().add(0, 1, 0).getBlock().isPassable() &&
               !loc.clone().add(0, -1, 0).getBlock().isPassable() &&
                !loc.getBlock().isLiquid(); // Not in lava/water
    }


    private void equipZombie(Zombie zombie) {
        // 50% chance to get any armor piece or sword
        if (random.nextDouble() < 0.5) { // Helmet
            if (random.nextBoolean()) zombie.getEquipment().setHelmet(new ItemStack(Material.LEATHER_HELMET));
            else if (random.nextDouble() < 0.7) zombie.getEquipment().setHelmet(new ItemStack(Material.CHAINMAIL_HELMET)); // More common than iron
            else zombie.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
            zombie.getEquipment().setHelmetDropChance(0.1f); // Low drop chance
        }
        if (random.nextDouble() < 0.5) { // Chestplate
             if (random.nextBoolean()) zombie.getEquipment().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
            else if (random.nextDouble() < 0.7) zombie.getEquipment().setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
            else zombie.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            zombie.getEquipment().setChestplateDropChance(0.1f);
        }
        if (random.nextDouble() < 0.5) { // Leggings
            if (random.nextBoolean()) zombie.getEquipment().setLeggings(new ItemStack(Material.LEATHER_LEGGINGS));
            else if (random.nextDouble() < 0.7) zombie.getEquipment().setLeggings(new ItemStack(Material.CHAINMAIL_LEGGINGS));
            else zombie.getEquipment().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
            zombie.getEquipment().setLeggingsDropChance(0.1f);
        }
        if (random.nextDouble() < 0.5) { // Boots
            if (random.nextBoolean()) zombie.getEquipment().setBoots(new ItemStack(Material.LEATHER_BOOTS));
            else if (random.nextDouble() < 0.7) zombie.getEquipment().setBoots(new ItemStack(Material.CHAINMAIL_BOOTS));
            else zombie.getEquipment().setBoots(new ItemStack(Material.IRON_BOOTS));
            zombie.getEquipment().setBootsDropChance(0.1f);
        }
        if (random.nextDouble() < 0.5) { // Sword
            if (random.nextBoolean()) zombie.getEquipment().setItemInMainHand(new ItemStack(Material.WOODEN_SWORD));
            else if (random.nextDouble() < 0.7) zombie.getEquipment().setItemInMainHand(new ItemStack(Material.STONE_SWORD));
            else zombie.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
            zombie.getEquipment().setItemInMainHandDropChance(0.05f); // Very low drop chance
        }
    }

    private void giveWaveRewards(Player player, int waveNumber) {
        int itemsToGenerate = 1 + waveNumber; // Wave 1: 2 items, Wave 2: 3 items
        RewardGenerator.Tier tier = waveNumber == 1 ? RewardGenerator.Tier.BASIC : RewardGenerator.Tier.COMMON;
        List<ItemStack> rewards = rewardGenerator.generateRewards(tier, itemsToGenerate);
        if (!rewards.isEmpty()) {
            player.sendMessage("Wave " + waveNumber + " cleared! You received some loot!");
            for (ItemStack item : rewards) {
                player.getInventory().addItem(item).forEach((index, leftoverItem) -> {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftoverItem); // Drop if inventory is full
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
