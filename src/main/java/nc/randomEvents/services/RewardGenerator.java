package nc.randomEvents.services;

import nc.randomEvents.RandomEvents;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;

public class RewardGenerator {
    private final RandomEvents plugin;
    private FileConfiguration rewardsConfig = null;
    private File rewardsFile = null;
    private final Random random = new Random();
    // private final List<RewardItem> possibleRewards = new ArrayList<>();
    // private int totalChanceWeight = 0;

    private final Map<Tier, List<RewardItem>> possibleRewardsByTier = new EnumMap<>(Tier.class);
    private final Map<Tier, Integer> totalChanceWeightByTier = new EnumMap<>(Tier.class);

    public enum Tier {
        BASIC, COMMON, RARE;

        public static Tier fromString(String s) {
            try {
                return Tier.valueOf(s.toUpperCase());
            } catch (IllegalArgumentException e) {
                return null; // Or throw an exception, or return a default
            }
        }
    }

    public RewardGenerator(RandomEvents plugin) {
        this.plugin = plugin;
        rewardsFile = new File(plugin.getDataFolder(), "rewards.yml");
        if (!rewardsFile.exists()) {
            plugin.saveResource("rewards.yml", false);
        }
        loadRewards();
    }

    private void loadRewards() {
        rewardsConfig = YamlConfiguration.loadConfiguration(rewardsFile);
        if (rewardsConfig.getKeys(false).isEmpty()) {
            java.io.InputStream defaultRewardsStream = plugin.getResource("rewards.yml");
            if (defaultRewardsStream != null) {
                Reader defaultConfigReader = new InputStreamReader(defaultRewardsStream);
                YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(defaultConfigReader);
                rewardsConfig = defaultConfig;
            } else {
                plugin.getLogger().severe("Default rewards.yml not found in JAR! No rewards will be loaded.");
                return;
            }
        }

        possibleRewardsByTier.clear();
        totalChanceWeightByTier.clear();

        ConfigurationSection tiersSection = rewardsConfig.getConfigurationSection("tiers");
        if (tiersSection == null) {
            plugin.getLogger().warning("No 'tiers' section found in rewards.yml. No rewards will be loaded.");
            return;
        }

        for (String tierKey : tiersSection.getKeys(false)) {
            Tier currentTier = Tier.fromString(tierKey);
            if (currentTier == null) {
                plugin.getLogger().warning("Invalid tier defined in rewards.yml: " + tierKey + ". Skipping this tier.");
                continue;
            }

            // Get the configuration section for the specific tier (e.g., BASIC, COMMON, RARE)
            ConfigurationSection specificTierDataSection = tiersSection.getConfigurationSection(tierKey);
            if (specificTierDataSection == null) {
                plugin.getLogger().warning("No configuration data found for tier '" + tierKey + "' in rewards.yml. Skipping this tier.");
                continue;
            }

            // Now get the 'rewards' section from within that tier's data
            ConfigurationSection rewardsSection = specificTierDataSection.getConfigurationSection("rewards");
            if (rewardsSection == null) {
                plugin.getLogger().warning("No 'rewards' section found for tier '" + tierKey + "' in rewards.yml. No rewards will be loaded for this tier.");
                continue;
            }

            List<RewardItem> tierRewards = new ArrayList<>();
            int tierTotalChanceWeight = 0;
            Set<String> itemKeys = rewardsSection.getKeys(false);

            for (String itemKey : itemKeys) {
                try {
                    Material material = Material.matchMaterial(itemKey.toUpperCase());
                    if (material == null) {
                        plugin.getLogger().warning("Invalid material in rewards.yml for tier " + tierKey + ": " + itemKey);
                        continue;
                    }
                    int chance = rewardsSection.getInt(itemKey + ".chance", 1);
                    int minAmount = rewardsSection.getInt(itemKey + ".minAmount", 1);
                    int maxAmount = rewardsSection.getInt(itemKey + ".maxAmount", 1);

                    if (minAmount <= 0 || maxAmount < minAmount || chance <= 0) {
                        plugin.getLogger().warning("Invalid amount/chance for " + itemKey + " in tier " + tierKey + " in rewards.yml. Skipping.");
                        continue;
                    }

                    tierRewards.add(new RewardItem(material, chance, minAmount, maxAmount));
                    tierTotalChanceWeight += chance;
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error parsing reward item: " + itemKey + " for tier " + tierKey, e);
                }
            }

            if (!tierRewards.isEmpty()) {
                possibleRewardsByTier.put(currentTier, tierRewards);
                totalChanceWeightByTier.put(currentTier, tierTotalChanceWeight);
                plugin.getLogger().info("Loaded " + tierRewards.size() + " rewards for tier " + currentTier + " with total chance weight " + tierTotalChanceWeight);
            } else {
                plugin.getLogger().warning("No valid rewards loaded for tier " + currentTier + " from rewards.yml.");
            }
        }
        if (possibleRewardsByTier.isEmpty()) {
            plugin.getLogger().warning("No valid rewards loaded from any tier in rewards.yml.");
        }
    }

    public List<ItemStack> generateRewards(Tier tier, int numberOfItemStacksToGenerate) {
        List<ItemStack> generatedItems = new ArrayList<>();
        List<RewardItem> rewardsForTier = possibleRewardsByTier.get(tier);
        Integer tierTotalChanceWeight = totalChanceWeightByTier.get(tier);

        if (rewardsForTier == null || rewardsForTier.isEmpty() || tierTotalChanceWeight == null || tierTotalChanceWeight <= 0) {
            plugin.getLogger().warning("Cannot generate rewards for tier " + tier + ": No rewards loaded or total chance weight is zero for this tier.");
            return generatedItems;
        }

        for (int i = 0; i < numberOfItemStacksToGenerate; i++) {
            int pickedChance = random.nextInt(tierTotalChanceWeight);
            int currentWeight = 0;
            RewardItem selectedReward = null;

            for (RewardItem reward : rewardsForTier) {
                currentWeight += reward.chance;
                if (pickedChance < currentWeight) {
                    selectedReward = reward;
                    break;
                }
            }

            if (selectedReward != null) {
                int amount = random.nextInt((selectedReward.maxAmount - selectedReward.minAmount) + 1) + selectedReward.minAmount;
                generatedItems.add(new ItemStack(selectedReward.material, amount));
            }
        }
        return generatedItems;
    }

    // Inner class to hold reward item details
    private static class RewardItem {
        Material material;
        int chance; // Relative weight
        int minAmount;
        int maxAmount;

        RewardItem(Material material, int chance, int minAmount, int maxAmount) {
            this.material = material;
            this.chance = chance;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
        }
    }
}
