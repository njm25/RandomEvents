package nc.randomEvents.services;

import nc.randomEvents.RandomEvents;
import nc.randomEvents.utils.ItemHelper;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
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
import java.util.HashMap;

public class RewardGenerator {
    private final RandomEvents plugin;
    private FileConfiguration rewardsConfig = null;
    private File rewardsFile = null;
    private final Random random = new Random();
    // private final List<RewardItem> possibleRewards = new ArrayList<>();
    // private int totalChanceWeight = 0;

    private final Map<Tier, List<RewardItem>> possibleRewardsByTier = new EnumMap<>(Tier.class);
    private final Map<Tier, Double> totalChanceWeightByTier = new EnumMap<>(Tier.class);

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

    /**
     * Helper class to build tier-quantity mappings easily
     */
    public static class TierQuantity {
        private final Map<Tier, Integer> quantities;

        public TierQuantity() {
            this.quantities = new HashMap<>();
        }

        public TierQuantity add(Tier tier, int quantity) {
            if (quantity > 0) {
                quantities.put(tier, quantity);
            }
            return this;
        }

        public Map<Tier, Integer> build() {
            return new HashMap<>(quantities);
        }
    }

    // Inner class to hold enchantment details
    private static class EnchantmentData {
        Enchantment enchantment;
        int level;

        EnchantmentData(Enchantment enchantment, int level) {
            this.enchantment = enchantment;
            this.level = level;
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

    public void reloadRewards() {
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
            double tierTotalChanceWeight = 0.0;
            Set<String> itemKeys = rewardsSection.getKeys(false);

            for (String itemKey : itemKeys) {
                try {
                    Material material = Material.matchMaterial(itemKey.toUpperCase());
                    if (material == null) {
                        plugin.getLogger().warning("Invalid material in rewards.yml for tier " + tierKey + ": " + itemKey);
                        continue;
                    }
                    double chance = rewardsSection.getDouble(itemKey + ".chance", 1.0);
                    int minAmount = rewardsSection.getInt(itemKey + ".minAmount", 1);
                    int maxAmount = rewardsSection.getInt(itemKey + ".maxAmount", 1);

                    if (minAmount <= 0 || maxAmount < minAmount || chance <= 0 || chance > 100) {
                        plugin.getLogger().warning("Invalid amount/chance for " + itemKey + " in tier " + tierKey + " in rewards.yml. Chance must be between 0 and 100, amounts must be positive. Skipping.");
                        continue;
                    }

                    List<EnchantmentData> enchantments = new ArrayList<>();
                    // Corrected enchantment parsing using getMapList()
                    if (rewardsSection.isList(itemKey + ".enchantments")) {
                        if (ItemHelper.isEnchantable(material)) {
                            List<Map<?, ?>> enchList = rewardsSection.getMapList(itemKey + ".enchantments");
                            if (!enchList.isEmpty()) {
                                for (Map<?, ?> enchEntry : enchList) {
                                    String enchantmentName = null;
                                    if (enchEntry.get("name") instanceof String) {
                                        enchantmentName = (String) enchEntry.get("name");
                                    }
                                    int enchantmentLevel = 1; // Default level
                                    if (enchEntry.get("level") instanceof Number) {
                                        enchantmentLevel = ((Number) enchEntry.get("level")).intValue();
                                    }

                                    if (enchantmentName != null) {
                                        Enchantment bukkitEnchantment = ItemHelper.getEnchantment(enchantmentName);
                                        if (bukkitEnchantment != null) {
                                            enchantments.add(new EnchantmentData(bukkitEnchantment, enchantmentLevel));
                                        } else {
                                            plugin.getLogger().warning("Invalid enchantment name: " + enchantmentName + " for item " + itemKey + " in tier " + tierKey);
                                        }
                                    } else {
                                        plugin.getLogger().warning("Missing enchantment name for an entry in item " + itemKey + " in tier " + tierKey);
                                    }
                                }
                            }
                        } else {
                            plugin.getLogger().warning("Item " + itemKey + " (material: " + material.name() + ") in tier " + tierKey + " is not enchantable but has an enchantments list. Enchantments will be ignored.");
                        }
                    } else if (rewardsSection.contains(itemKey + ".enchantments")) {
                        // Log a warning if enchantments section exists but is not a list (e.g. if it was a map by mistake)
                        plugin.getLogger().warning("The 'enchantments' section for " + itemKey + " in tier " + tierKey + " is not formatted as a list. Please check your rewards.yml. Enchantments will be ignored for this item.");
                    }

                    tierRewards.add(new RewardItem(material, chance, minAmount, maxAmount, enchantments));
                    tierTotalChanceWeight += chance;
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error parsing reward item: " + itemKey + " for tier " + tierKey, e);
                }
            }

            if (!tierRewards.isEmpty()) {
                // Validate total percentage is approximately 100%
                if (Math.abs(tierTotalChanceWeight - 100.0) > 0.01) {
                    plugin.getLogger().warning("Total chance weight for tier " + currentTier + " is " + tierTotalChanceWeight + "%. It should sum to 100%.");
                }
                possibleRewardsByTier.put(currentTier, tierRewards);
                totalChanceWeightByTier.put(currentTier, tierTotalChanceWeight);
                plugin.getLogger().info("Loaded " + tierRewards.size() + " rewards for tier " + currentTier + " with total chance weight " + String.format("%.2f%%", tierTotalChanceWeight));
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
        Double tierTotalChanceWeight = totalChanceWeightByTier.get(tier);

        if (rewardsForTier == null || rewardsForTier.isEmpty() || tierTotalChanceWeight == null || tierTotalChanceWeight <= 0) {
            plugin.getLogger().warning("Cannot generate rewards for tier " + tier + ": No rewards loaded or total chance weight is zero for this tier.");
            return generatedItems;
        }

        for (int i = 0; i < numberOfItemStacksToGenerate; i++) {
            double roll = random.nextDouble() * 100.0; // Roll between 0 and 100
            double currentSum = 0.0;
            RewardItem selectedReward = null;

            for (RewardItem reward : rewardsForTier) {
                currentSum += reward.chance;
                if (roll < currentSum) {
                    selectedReward = reward;
                    break;
                }
            }

            if (selectedReward != null) {
                int amount = random.nextInt((selectedReward.maxAmount - selectedReward.minAmount) + 1) + selectedReward.minAmount;
                ItemStack itemStack = new ItemStack(selectedReward.material, amount);

                if (!selectedReward.enchantments.isEmpty()) {
                    // Convert EnchantmentData to the format expected by ItemHelper
                    List<Map<String, Object>> enchantmentList = new ArrayList<>();
                    for (EnchantmentData enchData : selectedReward.enchantments) {
                        Map<String, Object> enchMap = new HashMap<>();
                        enchMap.put("name", enchData.enchantment.getKey().getKey());
                        enchMap.put("level", enchData.level);
                        enchantmentList.add(enchMap);
                    }
                    itemStack = ItemHelper.applyEnchantments(itemStack, enchantmentList);
                }
                generatedItems.add(itemStack);
            }
        }
        return generatedItems;
    }

    /**
     * Generates rewards based on a map of tiers and their quantities
     * @param tierQuantities Map of Tier to number of items to generate for that tier
     * @return List of all generated ItemStacks
     */
    public List<ItemStack> generateRewards(Map<Tier, Integer> tierQuantities) {
        List<ItemStack> allGeneratedItems = new ArrayList<>();
        
        for (Map.Entry<Tier, Integer> entry : tierQuantities.entrySet()) {
            Tier tier = entry.getKey();
            int quantity = entry.getValue();
            
            if (quantity > 0) {
                allGeneratedItems.addAll(generateRewards(tier, quantity));
            }
        }
        
        return allGeneratedItems;
    }

    // Inner class to hold reward item details
    private static class RewardItem {
        Material material;
        double chance; // Percentage chance (0-100)
        int minAmount;
        int maxAmount;
        List<EnchantmentData> enchantments;

        RewardItem(Material material, double chance, int minAmount, int maxAmount, List<EnchantmentData> enchantments) {
            this.material = material;
            this.chance = chance;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
            this.enchantments = enchantments != null ? enchantments : new ArrayList<>();
        }
    }
}
