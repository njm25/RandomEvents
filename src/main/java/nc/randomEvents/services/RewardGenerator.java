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
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;

public class RewardGenerator {
    private final RandomEvents plugin;
    private FileConfiguration rewardsConfig = null;
    private File rewardsFile = null;
    private final Random random = new Random();
    private final List<RewardItem> possibleRewards = new ArrayList<>();
    private int totalChanceWeight = 0;

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
        // Fallback to default rewards.yml from JAR if user file is empty or corrupted
        if (rewardsConfig.getKeys(false).isEmpty()) {
            java.io.InputStream defaultRewardsStream = plugin.getResource("rewards.yml");
            if (defaultRewardsStream != null) {
                Reader defaultConfigReader = new InputStreamReader(defaultRewardsStream);
                YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(defaultConfigReader);
                rewardsConfig = defaultConfig;
            } else {
                plugin.getLogger().severe("Default rewards.yml not found in JAR!");
                return;
            }
        }

        ConfigurationSection rewardsSection = rewardsConfig.getConfigurationSection("rewards");
        if (rewardsSection == null) {
            plugin.getLogger().warning("No 'rewards' section found in rewards.yml. No rewards will be generated.");
            return;
        }

        possibleRewards.clear();
        totalChanceWeight = 0;
        Set<String> itemKeys = rewardsSection.getKeys(false);

        for (String key : itemKeys) {
            try {
                Material material = Material.matchMaterial(key.toUpperCase());
                if (material == null) {
                    plugin.getLogger().warning("Invalid material in rewards.yml: " + key);
                    continue;
                }
                int chance = rewardsSection.getInt(key + ".chance", 1);
                int minAmount = rewardsSection.getInt(key + ".minAmount", 1);
                int maxAmount = rewardsSection.getInt(key + ".maxAmount", 1);

                if (minAmount <= 0 || maxAmount < minAmount || chance <=0) {
                    plugin.getLogger().warning("Invalid amount/chance for " + key + " in rewards.yml. Skipping.");
                    continue;
                }

                possibleRewards.add(new RewardItem(material, chance, minAmount, maxAmount));
                totalChanceWeight += chance;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error parsing reward item: " + key, e);
            }
        }
        if (possibleRewards.isEmpty()) {
            plugin.getLogger().warning("No valid rewards loaded from rewards.yml.");
        }
    }

    public List<ItemStack> generateRewards(int numberOfItemStacksToGenerate) {
        List<ItemStack> generatedItems = new ArrayList<>();
        if (possibleRewards.isEmpty() || totalChanceWeight <= 0) {
            plugin.getLogger().warning("Cannot generate rewards: No rewards loaded or total chance weight is zero.");
            return generatedItems;
        }

        for (int i = 0; i < numberOfItemStacksToGenerate; i++) {
            int pickedChance = random.nextInt(totalChanceWeight);
            int currentWeight = 0;
            RewardItem selectedReward = null;

            for (RewardItem reward : possibleRewards) {
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
