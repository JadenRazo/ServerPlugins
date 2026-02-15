package net.serverplugins.events;

import java.util.*;
import java.util.EnumMap;
import net.serverplugins.api.messages.PluginMessenger;
import net.serverplugins.events.events.ServerEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

/** Configuration handler for ServerEvents. */
public class EventsConfig {

    private final ServerEvents plugin;
    private final FileConfiguration config;
    private final PluginMessenger messenger;

    // Scheduler
    private final int schedulerInterval;
    private final boolean schedulerEnabled;

    // Spawn location
    private final Location spawnLocation;

    // Messages
    private final String prefix;

    // Pinata config
    private final EntityType pinataEntity;
    private final int pinataClicksMin;
    private final int pinataClicksMax;
    private final org.bukkit.ChatColor pinataGlowColor;
    private final Map<String, String> pinataMessages;
    private final int pinataParticipationCoins;
    private final int pinataBreakerCoins;
    private final double pinataKeyChance;
    private final String pinataKeyType;

    // Pinata reward distribution
    private final int pinataTotalCoins;
    private final int pinataBreakerBonus;
    private final int pinataProportionalPool;

    // Pinata anti-spam and combo
    private final long pinataClickCooldownMs;
    private final int pinataCombo5Bonus;
    private final int pinataCombo10Bonus;
    private final int pinataCombo15Bonus;

    // Pinata minimum players
    private final int pinataMinPlayers;
    private final int premiumPinataMinPlayers;

    // Spelling config
    private final int spellingTimeLimit;
    private final List<String> spellingWords;
    private final Map<String, String> spellingMessages;
    private final int spellingCoins;
    private final double spellingKeyChance;
    private final String spellingKeyType;

    // Crafting config
    private final int craftingDefaultTime;
    private final Map<String, String> craftingMessages;
    private final Map<String, CraftingChallenge> craftingChallenges;

    // GUI
    private final String guiTitle;
    private final int guiSize;

    // Math config
    private final int mathTimeLimit;
    private final int mathDifficulty;
    private final Map<String, String> mathMessages;
    private final int mathCoins;
    private final double mathKeyChance;
    private final String mathKeyType;

    // Drop party config
    private final int dropPartyTotalDrops;
    private final int dropPartyInterval;
    private final int dropPartyItemsPerDrop;
    private final double dropPartyRadius;
    private final double dropPartyHeight;
    private final List<ItemStack> dropPartyItems;
    private final Map<String, String> dropPartyMessages;

    // Event random weights (for weighted random selection)
    private final Map<ServerEvent.EventType, Double> randomEventWeights;

    // Pinata player mode
    private final boolean pinataUsePlayerSkin;

    // Premium pinata config
    private final Location premiumPinataSpawnCenter;
    private final double premiumPinataSpawnRadius;
    private final int premiumPinataClicksMin;
    private final int premiumPinataClicksMax;
    private final int premiumPinataTotalCoins;
    private final int premiumPinataBreakerBonus;
    private final int premiumPinataTimeout;
    private final double premiumPinataDiversityChance;
    private final double premiumPinataEpicChance;
    private final double premiumPinataDkeyMediumChance;
    private final double premiumPinataDkeyHardChance;

    // Premium pinata item drops
    private final boolean premiumItemDropsEnabled;
    private final double premiumDropChance;
    private final List<ItemStack> premiumBonusItems;

    // Countdown warnings
    private final boolean countdownEnabled;
    private final List<Integer> countdownWarnings;

    // Discord webhook
    private final boolean discordEnabled;
    private final String discordWebhookUrl;
    private final boolean discordAnnounceStart;
    private final boolean discordAnnounceWinner;

    // Keyall scheduler
    private final boolean keyallEnabled;
    private final int keyallInterval;
    private final String keyallType;
    private final String keyallKey;
    private final int keyallAmount;
    private final List<Integer> keyallWarnings;

    // Dragon fight config
    private final String dragonWorld;
    private final double dragonBaseHealth;
    private final double dragonPerPlayerHealth;
    private final double dragonMaxHealth;
    private final int dragonScaleRadius;
    private final List<Integer> dragonPhaseThresholds;
    private final DragonAttackConfig dragonLightningAttack;
    private final DragonAttackConfig dragonBreathAttack;
    private final DragonAttackConfig dragonKnockbackAttack;
    private final DragonAttackConfig dragonFireballAttack;
    private final boolean dragonBossbarEnabled;
    private final String dragonBossbarTitle;
    private final org.bukkit.boss.BarColor dragonBossbarColor;
    private final org.bukkit.boss.BarStyle dragonBossbarStyle;
    private final int dragonBaseCoins;
    private final int dragonPerDamagePercent;
    private final int dragonKillerBonus;
    private final double dragonKeyChance;
    private final String dragonKeyType;
    private final Map<String, String> dragonMessages;
    private final int dragonTimeLimit;

    public EventsConfig(ServerEvents plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();

        // Initialize messenger with events-themed prefix
        this.messenger = new PluginMessenger(config, "messages", "<yellow>[Events]</yellow> ");

        // Scheduler
        this.schedulerInterval = config.getInt("scheduler.interval", 600);
        this.schedulerEnabled = config.getBoolean("scheduler.enabled", true);

        // Spawn location
        String worldName = config.getString("spawn.world", "spawn");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger()
                    .severe(
                            "[ServerEvents] Spawn world '"
                                    + worldName
                                    + "' not found! Using first available world.");
            plugin.getLogger()
                    .severe("[ServerEvents] Please update config.yml with a valid world name.");
            plugin.getLogger()
                    .severe(
                            "[ServerEvents] Available worlds: "
                                    + Bukkit.getWorlds().stream()
                                            .map(World::getName)
                                            .reduce((a, b) -> a + ", " + b)
                                            .orElse("none"));
            world = Bukkit.getWorlds().get(0);
        }
        this.spawnLocation =
                new Location(
                        world,
                        config.getDouble("spawn.x", -21.5),
                        config.getDouble("spawn.y", 66),
                        config.getDouble("spawn.z", -68.5));

        // Messages - no colorization needed, TextUtil handles legacy codes
        this.prefix = config.getString("messages.prefix", "&6[Events] &r");

        // Pinata
        String entityName = config.getString("pinata.entity", "LLAMA");
        EntityType entityType;
        try {
            entityType = EntityType.valueOf(entityName.toUpperCase());
        } catch (IllegalArgumentException e) {
            entityType = EntityType.LLAMA;
        }
        this.pinataEntity = entityType;
        this.pinataClicksMin = config.getInt("pinata.clicks_min", 70);
        this.pinataClicksMax = config.getInt("pinata.clicks_max", 100);

        String glowColorName = config.getString("pinata.glow_color", "YELLOW");
        org.bukkit.ChatColor glowColor;
        try {
            glowColor = org.bukkit.ChatColor.valueOf(glowColorName.toUpperCase());
        } catch (IllegalArgumentException e) {
            glowColor = org.bukkit.ChatColor.YELLOW;
        }
        this.pinataGlowColor = glowColor;

        this.pinataMessages = new HashMap<>();
        ConfigurationSection pinataMessagesSection =
                config.getConfigurationSection("pinata.messages");
        if (pinataMessagesSection != null) {
            for (String key : pinataMessagesSection.getKeys(false)) {
                pinataMessages.put(key, pinataMessagesSection.getString(key));
            }
        }

        this.pinataParticipationCoins = config.getInt("pinata.rewards.participation_coins", 50);
        this.pinataBreakerCoins = config.getInt("pinata.rewards.breaker_coins", 500);
        this.pinataKeyChance = config.getDouble("pinata.rewards.key_chance", 0.1);
        this.pinataKeyType = config.getString("pinata.rewards.key_type", "common");

        // Pinata reward distribution
        this.pinataTotalCoins = config.getInt("pinata.rewards.total_coins", 3000);
        this.pinataBreakerBonus = config.getInt("pinata.rewards.breaker_bonus", 900);
        this.pinataProportionalPool = config.getInt("pinata.rewards.proportional_pool", 2100);

        // Pinata anti-spam and combo
        this.pinataClickCooldownMs = config.getLong("pinata.mechanics.click_cooldown_ms", 200L);
        this.pinataCombo5Bonus = config.getInt("pinata.mechanics.combo_5_bonus", 50);
        this.pinataCombo10Bonus = config.getInt("pinata.mechanics.combo_10_bonus", 100);
        this.pinataCombo15Bonus = config.getInt("pinata.mechanics.combo_15_bonus", 200);

        // Pinata minimum players
        this.pinataMinPlayers = config.getInt("pinata.min_players", 2);
        this.premiumPinataMinPlayers = config.getInt("premium_pinata.min_players", 3);

        // Spelling
        this.spellingTimeLimit = config.getInt("spelling.time_limit", 30);
        this.spellingWords = config.getStringList("spelling.words");
        if (spellingWords.isEmpty()) {
            spellingWords.add("obsidian");
            spellingWords.add("enchantment");
        }

        this.spellingMessages = new HashMap<>();
        ConfigurationSection spellingMessagesSection =
                config.getConfigurationSection("spelling.messages");
        if (spellingMessagesSection != null) {
            for (String key : spellingMessagesSection.getKeys(false)) {
                spellingMessages.put(key, spellingMessagesSection.getString(key));
            }
        }

        this.spellingCoins = config.getInt("spelling.rewards.coins", 200);
        this.spellingKeyChance = config.getDouble("spelling.rewards.key_chance", 0.05);
        this.spellingKeyType = config.getString("spelling.rewards.key_type", "common");

        // Crafting
        this.craftingDefaultTime = config.getInt("crafting.default_time", 60);

        this.craftingMessages = new HashMap<>();
        ConfigurationSection craftingMessagesSection =
                config.getConfigurationSection("crafting.messages");
        if (craftingMessagesSection != null) {
            for (String key : craftingMessagesSection.getKeys(false)) {
                craftingMessages.put(key, craftingMessagesSection.getString(key));
            }
        }

        this.craftingChallenges = new HashMap<>();
        ConfigurationSection challengesSection =
                config.getConfigurationSection("crafting.challenges");
        if (challengesSection != null) {
            for (String key : challengesSection.getKeys(false)) {
                ConfigurationSection challenge = challengesSection.getConfigurationSection(key);
                if (challenge != null) {
                    craftingChallenges.put(
                            key,
                            new CraftingChallenge(
                                    challenge.getString("type", "CRAFT"),
                                    Material.matchMaterial(
                                            challenge.getString("material", "STONE")),
                                    challenge.getInt("amount", 1),
                                    challenge.getInt("time", craftingDefaultTime),
                                    challenge.getString("display_name", key),
                                    challenge.getInt("rewards.coins", 100),
                                    challenge.getDouble("rewards.key_chance", 0.05),
                                    challenge.getString("rewards.key_type", "common")));
                }
            }
        }

        // GUI - keep legacy format for GUI titles (handled by GUI system)
        this.guiTitle = config.getString("gui.title", "&0&lEvents Menu").replace("&", "\u00A7");
        this.guiSize = config.getInt("gui.size", 27);

        // Math
        this.mathTimeLimit = config.getInt("math.time_limit", 30);
        this.mathDifficulty = config.getInt("math.difficulty", 2);

        this.mathMessages = new HashMap<>();
        ConfigurationSection mathMessagesSection = config.getConfigurationSection("math.messages");
        if (mathMessagesSection != null) {
            for (String key : mathMessagesSection.getKeys(false)) {
                mathMessages.put(key, mathMessagesSection.getString(key));
            }
        }

        this.mathCoins = config.getInt("math.rewards.coins", 150);
        this.mathKeyChance = config.getDouble("math.rewards.key_chance", 0.05);
        this.mathKeyType = config.getString("math.rewards.key_type", "common");

        // Drop party
        this.dropPartyTotalDrops = config.getInt("drop_party.total_drops", 50);
        this.dropPartyInterval = config.getInt("drop_party.interval_ticks", 10);
        this.dropPartyItemsPerDrop = config.getInt("drop_party.items_per_drop", 3);
        this.dropPartyRadius = config.getDouble("drop_party.radius", 5.0);
        this.dropPartyHeight = config.getDouble("drop_party.height", 10.0);

        this.dropPartyItems = new ArrayList<>();
        List<String> itemStrings = config.getStringList("drop_party.items");
        for (String itemStr : itemStrings) {
            Material mat = Material.matchMaterial(itemStr);
            if (mat != null) {
                dropPartyItems.add(new ItemStack(mat));
            }
        }
        // Default items if none configured
        if (dropPartyItems.isEmpty()) {
            dropPartyItems.add(new ItemStack(Material.DIAMOND));
            dropPartyItems.add(new ItemStack(Material.IRON_INGOT));
            dropPartyItems.add(new ItemStack(Material.GOLD_INGOT));
            dropPartyItems.add(new ItemStack(Material.EMERALD));
            dropPartyItems.add(new ItemStack(Material.COAL));
        }

        this.dropPartyMessages = new HashMap<>();
        ConfigurationSection dropPartyMessagesSection =
                config.getConfigurationSection("drop_party.messages");
        if (dropPartyMessagesSection != null) {
            for (String key : dropPartyMessagesSection.getKeys(false)) {
                dropPartyMessages.put(key, dropPartyMessagesSection.getString(key));
            }
        }

        // Event random weights
        this.randomEventWeights = new EnumMap<>(ServerEvent.EventType.class);
        ConfigurationSection randomSection = config.getConfigurationSection("random_events");
        if (randomSection != null) {
            for (ServerEvent.EventType type : ServerEvent.EventType.values()) {
                // Support both old boolean format and new weight format
                Object value = randomSection.get(type.getConfigKey());
                double weight;
                if (value instanceof Boolean) {
                    weight = ((Boolean) value) ? 1.0 : 0.0;
                } else if (value instanceof Number) {
                    weight = ((Number) value).doubleValue();
                } else {
                    weight = 1.0; // Default weight
                }
                randomEventWeights.put(type, weight);
            }
        } else {
            // Default: all enabled with weight 1.0
            for (ServerEvent.EventType type : ServerEvent.EventType.values()) {
                randomEventWeights.put(type, 1.0);
            }
        }

        // Pinata player mode
        this.pinataUsePlayerSkin = config.getBoolean("pinata.use_player_skin", false);

        // Premium pinata config
        String premiumPinataWorld = config.getString("premium_pinata.spawn.world", "spawn");
        World premiumWorld = Bukkit.getWorld(premiumPinataWorld);
        if (premiumWorld == null) {
            plugin.getLogger()
                    .warning(
                            "[ServerEvents] Premium pinata world '"
                                    + premiumPinataWorld
                                    + "' not found! Using spawn world.");
            premiumWorld = world; // Fall back to default spawn world
        }
        this.premiumPinataSpawnCenter =
                new Location(
                        premiumWorld,
                        config.getDouble("premium_pinata.spawn.x", 56.5),
                        config.getDouble("premium_pinata.spawn.y", 112.0),
                        config.getDouble("premium_pinata.spawn.z", -94.5));
        this.premiumPinataSpawnRadius = config.getDouble("premium_pinata.spawn.radius", 5.0);
        this.premiumPinataClicksMin = config.getInt("premium_pinata.clicks_min", 100);
        this.premiumPinataClicksMax = config.getInt("premium_pinata.clicks_max", 150);
        this.premiumPinataTotalCoins = config.getInt("premium_pinata.total_coins", 6000);
        this.premiumPinataBreakerBonus = config.getInt("premium_pinata.breaker_bonus", 1800);
        this.premiumPinataTimeout = config.getInt("premium_pinata.timeout_seconds", 120);
        this.premiumPinataDiversityChance =
                config.getDouble("premium_pinata.keys.diversity_chance", 0.10);
        this.premiumPinataEpicChance = config.getDouble("premium_pinata.keys.epic_chance", 0.03);
        this.premiumPinataDkeyMediumChance =
                config.getDouble("premium_pinata.keys.dkey_medium_chance", 0.10);
        this.premiumPinataDkeyHardChance =
                config.getDouble("premium_pinata.keys.dkey_hard_chance", 0.03);

        // Premium pinata item drops
        this.premiumItemDropsEnabled = config.getBoolean("premium_pinata.item_drops.enabled", true);
        this.premiumDropChance = config.getDouble("premium_pinata.item_drops.drop_chance", 0.5);
        this.premiumBonusItems = new ArrayList<>();
        List<String> premiumItemStrings =
                config.getStringList("premium_pinata.item_drops.bonus_items");
        for (String itemStr : premiumItemStrings) {
            Material mat = Material.matchMaterial(itemStr);
            if (mat != null) {
                premiumBonusItems.add(new ItemStack(mat));
            }
        }

        // Countdown warnings
        this.countdownEnabled = config.getBoolean("countdown.enabled", true);
        this.countdownWarnings = config.getIntegerList("countdown.warnings");
        if (countdownWarnings.isEmpty()) {
            countdownWarnings.add(30);
            countdownWarnings.add(10);
            countdownWarnings.add(5);
        }

        // Discord webhook
        this.discordEnabled = config.getBoolean("discord.enabled", false);
        this.discordWebhookUrl = config.getString("discord.webhook_url", "");
        this.discordAnnounceStart = config.getBoolean("discord.announce_start", true);
        this.discordAnnounceWinner = config.getBoolean("discord.announce_winner", true);

        // Dragon fight config
        this.dragonWorld = config.getString("dragon.world", "playworld_the_end");
        this.dragonBaseHealth = config.getDouble("dragon.base_health", 200.0);
        this.dragonPerPlayerHealth = config.getDouble("dragon.per_player_health", 50.0);
        this.dragonMaxHealth = config.getDouble("dragon.max_health", 2000.0);
        this.dragonScaleRadius = config.getInt("dragon.scale_radius", 200);
        this.dragonPhaseThresholds = config.getIntegerList("dragon.phase_thresholds");
        if (dragonPhaseThresholds.isEmpty()) {
            dragonPhaseThresholds.add(75);
            dragonPhaseThresholds.add(50);
            dragonPhaseThresholds.add(25);
        }

        // Dragon attacks
        this.dragonLightningAttack =
                new DragonAttackConfig(
                        config.getBoolean("dragon.attacks.lightning.enabled", true),
                        config.getDouble("dragon.attacks.lightning.damage", 8.0),
                        config.getDouble("dragon.attacks.lightning.radius", 5.0),
                        config.getInt("dragon.attacks.lightning.cooldown_seconds", 15),
                        config.getInt("dragon.attacks.lightning.min_phase", 2));
        this.dragonBreathAttack =
                new DragonAttackConfig(
                        config.getBoolean("dragon.attacks.breath.enabled", true),
                        config.getDouble("dragon.attacks.breath.damage", 6.0),
                        config.getInt("dragon.attacks.breath.duration_ticks", 100),
                        config.getInt("dragon.attacks.breath.cooldown_seconds", 20),
                        config.getInt("dragon.attacks.breath.min_phase", 1));
        this.dragonKnockbackAttack =
                new DragonAttackConfig(
                        config.getBoolean("dragon.attacks.knockback.enabled", true),
                        config.getDouble("dragon.attacks.knockback.damage", 4.0),
                        config.getDouble("dragon.attacks.knockback.strength", 2.5),
                        config.getInt("dragon.attacks.knockback.cooldown_seconds", 10),
                        config.getInt("dragon.attacks.knockback.min_phase", 3));
        this.dragonFireballAttack =
                new DragonAttackConfig(
                        config.getBoolean("dragon.attacks.fireball.enabled", true),
                        config.getDouble("dragon.attacks.fireball.damage", 10.0),
                        config.getInt("dragon.attacks.fireball.count", 5),
                        config.getInt("dragon.attacks.fireball.cooldown_seconds", 25),
                        config.getInt("dragon.attacks.fireball.min_phase", 4));

        // Dragon bossbar - keep legacy format for bossbar titles (handled by Bukkit API)
        this.dragonBossbarEnabled = config.getBoolean("dragon.bossbar.enabled", true);
        this.dragonBossbarTitle =
                config.getString("dragon.bossbar.title", "&5&lEnder Dragon &7- &c{health}%")
                        .replace("&", "\u00A7");
        String bossbarColorName = config.getString("dragon.bossbar.color", "PURPLE");
        org.bukkit.boss.BarColor bossbarColor;
        try {
            bossbarColor = org.bukkit.boss.BarColor.valueOf(bossbarColorName.toUpperCase());
        } catch (IllegalArgumentException e) {
            bossbarColor = org.bukkit.boss.BarColor.PURPLE;
        }
        this.dragonBossbarColor = bossbarColor;
        String bossbarStyleName = config.getString("dragon.bossbar.style", "SEGMENTED_12");
        org.bukkit.boss.BarStyle bossbarStyle;
        try {
            bossbarStyle = org.bukkit.boss.BarStyle.valueOf(bossbarStyleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            bossbarStyle = org.bukkit.boss.BarStyle.SEGMENTED_12;
        }
        this.dragonBossbarStyle = bossbarStyle;

        // Dragon rewards
        this.dragonBaseCoins = config.getInt("dragon.rewards.base_coins", 500);
        this.dragonPerDamagePercent = config.getInt("dragon.rewards.per_damage_percent", 50);
        this.dragonKillerBonus = config.getInt("dragon.rewards.killer_bonus", 1000);
        this.dragonKeyChance = config.getDouble("dragon.rewards.key_chance", 0.25);
        this.dragonKeyType = config.getString("dragon.rewards.key_type", "rare");

        // Dragon messages
        this.dragonMessages = new HashMap<>();
        ConfigurationSection dragonMessagesSection =
                config.getConfigurationSection("dragon.messages");
        if (dragonMessagesSection != null) {
            for (String key : dragonMessagesSection.getKeys(false)) {
                dragonMessages.put(key, dragonMessagesSection.getString(key));
            }
        }

        this.dragonTimeLimit = config.getInt("dragon.time_limit", 600);

        // Keyall scheduler
        this.keyallEnabled = config.getBoolean("keyall.enabled", false);
        this.keyallInterval = config.getInt("keyall.interval", 3600);
        this.keyallType = config.getString("keyall.type", "crate");
        this.keyallKey = config.getString("keyall.key", "balanced");
        this.keyallAmount = config.getInt("keyall.amount", 1);
        this.keyallWarnings = config.getIntegerList("keyall.warnings");
        if (keyallWarnings.isEmpty()) {
            keyallWarnings.add(1800);
            keyallWarnings.add(600);
            keyallWarnings.add(300);
            keyallWarnings.add(60);
        }
    }

    // Getters
    public int getSchedulerInterval() {
        return schedulerInterval;
    }

    public boolean isSchedulerEnabled() {
        return schedulerEnabled;
    }

    public Location getSpawnLocation() {
        return spawnLocation.clone();
    }

    public String getPrefix() {
        return prefix;
    }

    public PluginMessenger getMessenger() {
        return messenger;
    }

    // Pinata getters
    public EntityType getPinataEntity() {
        return pinataEntity;
    }

    public int getPinataClicksMin() {
        return pinataClicksMin;
    }

    public int getPinataClicksMax() {
        return pinataClicksMax;
    }

    public org.bukkit.ChatColor getPinataGlowColor() {
        return pinataGlowColor;
    }

    public String getPinataMessage(String key) {
        return pinataMessages.getOrDefault(key, "");
    }

    public int getPinataParticipationCoins() {
        return pinataParticipationCoins;
    }

    public int getPinataBreakerCoins() {
        return pinataBreakerCoins;
    }

    public double getPinataKeyChance() {
        return pinataKeyChance;
    }

    public String getPinataKeyType() {
        return pinataKeyType;
    }

    // Pinata reward distribution getters
    public int getPinataTotalCoins() {
        return pinataTotalCoins;
    }

    public int getPinataBreakerBonus() {
        return pinataBreakerBonus;
    }

    public int getPinataProportionalPool() {
        return pinataProportionalPool;
    }

    // Pinata anti-spam and combo getters
    public long getPinataClickCooldownMs() {
        return pinataClickCooldownMs;
    }

    public int getPinataCombo5Bonus() {
        return pinataCombo5Bonus;
    }

    public int getPinataCombo10Bonus() {
        return pinataCombo10Bonus;
    }

    public int getPinataCombo15Bonus() {
        return pinataCombo15Bonus;
    }

    // Pinata minimum players getters
    public int getPinataMinPlayers() {
        return pinataMinPlayers;
    }

    public int getPremiumPinataMinPlayers() {
        return premiumPinataMinPlayers;
    }

    // Spelling getters
    public int getSpellingTimeLimit() {
        return spellingTimeLimit;
    }

    public List<String> getSpellingWords() {
        return new ArrayList<>(spellingWords);
    }

    public String getSpellingMessage(String key) {
        return spellingMessages.getOrDefault(key, "");
    }

    public int getSpellingCoins() {
        return spellingCoins;
    }

    public double getSpellingKeyChance() {
        return spellingKeyChance;
    }

    public String getSpellingKeyType() {
        return spellingKeyType;
    }

    // Crafting getters
    public int getCraftingDefaultTime() {
        return craftingDefaultTime;
    }

    public String getCraftingMessage(String key) {
        return craftingMessages.getOrDefault(key, "");
    }

    public Map<String, CraftingChallenge> getCraftingChallenges() {
        return craftingChallenges;
    }

    public CraftingChallenge getCraftingChallenge(String key) {
        return craftingChallenges.get(key);
    }

    public List<String> getCraftingChallengeKeys() {
        return new ArrayList<>(craftingChallenges.keySet());
    }

    // GUI getters
    public String getGuiTitle() {
        return guiTitle;
    }

    public int getGuiSize() {
        return guiSize;
    }

    // Math getters
    public int getMathTimeLimit() {
        return mathTimeLimit;
    }

    public int getMathDifficulty() {
        return mathDifficulty;
    }

    public String getMathMessage(String key) {
        return mathMessages.getOrDefault(key, "");
    }

    public int getMathCoins() {
        return mathCoins;
    }

    public double getMathKeyChance() {
        return mathKeyChance;
    }

    public String getMathKeyType() {
        return mathKeyType;
    }

    // Drop party getters
    public int getDropPartyTotalDrops() {
        return dropPartyTotalDrops;
    }

    public int getDropPartyInterval() {
        return dropPartyInterval;
    }

    public int getDropPartyItemsPerDrop() {
        return dropPartyItemsPerDrop;
    }

    public double getDropPartyRadius() {
        return dropPartyRadius;
    }

    public double getDropPartyHeight() {
        return dropPartyHeight;
    }

    public List<ItemStack> getDropPartyItems() {
        return new ArrayList<>(dropPartyItems);
    }

    public String getDropPartyMessage(String key) {
        return dropPartyMessages.getOrDefault(key, "");
    }

    // Event random weight getters
    public double getEventWeight(ServerEvent.EventType type) {
        return randomEventWeights.getOrDefault(type, 0.0);
    }

    public boolean isEventEnabledForRandom(ServerEvent.EventType type) {
        return getEventWeight(type) > 0.0;
    }

    public Map<ServerEvent.EventType, Double> getRandomEventWeights() {
        return new EnumMap<>(randomEventWeights);
    }

    // Pinata player mode getter
    public boolean isPinataUsePlayerSkin() {
        return pinataUsePlayerSkin;
    }

    // Premium pinata getters
    public Location getPremiumPinataSpawnCenter() {
        return premiumPinataSpawnCenter.clone();
    }

    public double getPremiumPinataSpawnRadius() {
        return premiumPinataSpawnRadius;
    }

    public int getPremiumPinataClicksMin() {
        return premiumPinataClicksMin;
    }

    public int getPremiumPinataClicksMax() {
        return premiumPinataClicksMax;
    }

    public int getPremiumPinataTotalCoins() {
        return premiumPinataTotalCoins;
    }

    public int getPremiumPinataBreakerBonus() {
        return premiumPinataBreakerBonus;
    }

    public int getPremiumPinataTimeout() {
        return premiumPinataTimeout;
    }

    public double getPremiumPinataDiversityChance() {
        return premiumPinataDiversityChance;
    }

    public double getPremiumPinataEpicChance() {
        return premiumPinataEpicChance;
    }

    public double getPremiumPinataDkeyMediumChance() {
        return premiumPinataDkeyMediumChance;
    }

    public double getPremiumPinataDkeyHardChance() {
        return premiumPinataDkeyHardChance;
    }

    // Premium pinata item drops getters
    public boolean isPremiumItemDropsEnabled() {
        return premiumItemDropsEnabled;
    }

    public double getPremiumDropChance() {
        return premiumDropChance;
    }

    public List<ItemStack> getPremiumBonusItems() {
        return new ArrayList<>(premiumBonusItems);
    }

    // Countdown getters
    public boolean isCountdownEnabled() {
        return countdownEnabled;
    }

    public List<Integer> getCountdownWarnings() {
        return new ArrayList<>(countdownWarnings);
    }

    // Discord getters
    public boolean isDiscordEnabled() {
        return discordEnabled;
    }

    public String getDiscordWebhookUrl() {
        return discordWebhookUrl;
    }

    public boolean isDiscordAnnounceStart() {
        return discordAnnounceStart;
    }

    public boolean isDiscordAnnounceWinner() {
        return discordAnnounceWinner;
    }

    // Dragon getters
    public String getDragonWorld() {
        return dragonWorld;
    }

    public double getDragonBaseHealth() {
        return dragonBaseHealth;
    }

    public double getDragonPerPlayerHealth() {
        return dragonPerPlayerHealth;
    }

    public double getDragonMaxHealth() {
        return dragonMaxHealth;
    }

    public int getDragonScaleRadius() {
        return dragonScaleRadius;
    }

    public List<Integer> getDragonPhaseThresholds() {
        return new ArrayList<>(dragonPhaseThresholds);
    }

    public DragonAttackConfig getDragonLightningAttack() {
        return dragonLightningAttack;
    }

    public DragonAttackConfig getDragonBreathAttack() {
        return dragonBreathAttack;
    }

    public DragonAttackConfig getDragonKnockbackAttack() {
        return dragonKnockbackAttack;
    }

    public DragonAttackConfig getDragonFireballAttack() {
        return dragonFireballAttack;
    }

    public boolean isDragonBossbarEnabled() {
        return dragonBossbarEnabled;
    }

    public String getDragonBossbarTitle() {
        return dragonBossbarTitle;
    }

    public org.bukkit.boss.BarColor getDragonBossbarColor() {
        return dragonBossbarColor;
    }

    public org.bukkit.boss.BarStyle getDragonBossbarStyle() {
        return dragonBossbarStyle;
    }

    public int getDragonBaseCoins() {
        return dragonBaseCoins;
    }

    public int getDragonPerDamagePercent() {
        return dragonPerDamagePercent;
    }

    public int getDragonKillerBonus() {
        return dragonKillerBonus;
    }

    public double getDragonKeyChance() {
        return dragonKeyChance;
    }

    public String getDragonKeyType() {
        return dragonKeyType;
    }

    public String getDragonMessage(String key) {
        return dragonMessages.getOrDefault(key, "");
    }

    public int getDragonTimeLimit() {
        return dragonTimeLimit;
    }

    // Skin settings
    public String getPinataSkinMode() {
        return config.getString("pinata.skin_mode", "RANDOM_ONLINE");
    }

    public String getPinataSkinPlayer() {
        return config.getString("pinata.skin_player", "Notch");
    }

    public String getPinataSkinTheme() {
        return config.getString("pinata.skin_theme", "pirate");
    }

    // Equipment settings
    public boolean isPinataEquipmentEnabled() {
        return config.getBoolean("pinata.equipment.enabled", true);
    }

    public Material getPinataMainHandMaterial() {
        String matName = config.getString("pinata.equipment.main_hand", "DIAMOND_SWORD");
        Material mat = Material.matchMaterial(matName);
        return mat != null ? mat : Material.DIAMOND_SWORD;
    }

    public boolean isPinataEnchantGlow() {
        return config.getBoolean("pinata.equipment.enchant_glow", true);
    }

    public boolean isPinataOffHandEnabled() {
        return config.contains("pinata.equipment.off_hand")
                && !config.getString("pinata.equipment.off_hand", "").isEmpty();
    }

    public Material getPinataOffHandMaterial() {
        String matName = config.getString("pinata.equipment.off_hand", "SHIELD");
        Material mat = Material.matchMaterial(matName);
        return mat != null ? mat : Material.SHIELD;
    }

    // Animation settings
    public boolean isHurtAnimationEnabled() {
        return config.getBoolean("pinata.animations.hurt_on_hit", true);
    }

    public boolean isArmSwingOnHitEnabled() {
        return config.getBoolean("pinata.animations.arm_swing_on_hit", true);
    }

    public double getCriticalHitChance() {
        return config.getDouble("pinata.animations.critical_hit_chance", 0.15);
    }

    // Visual feedback
    public boolean isShakeOnHitEnabled() {
        return config.getBoolean("pinata.visual_feedback.shake_on_hit", true);
    }

    public double getShakeIntensity() {
        return config.getDouble("pinata.visual_feedback.shake_intensity", 0.15);
    }

    public boolean isDeteriorationEnabled() {
        return config.getBoolean("pinata.visual_feedback.deterioration_enabled", true);
    }

    // Nametag
    public String getNametagFormat() {
        return config.getString("pinata.nametag.format", "&6&lPINATA &f[{clicks}/{total}]");
    }

    public boolean isProgressBarEnabled() {
        return config.getBoolean("pinata.nametag.progress_bar", true);
    }

    public String getProgressBarFilledChar() {
        return config.getString("pinata.nametag.progress_bar_filled", "█");
    }

    public String getProgressBarEmptyChar() {
        return config.getString("pinata.nametag.progress_bar_empty", "░");
    }

    public int getProgressBarLength() {
        return config.getInt("pinata.nametag.progress_bar_length", 10);
    }

    public String getProgressBarFilledColor() {
        return config.getString("pinata.nametag.progress_bar_filled_color", "&a");
    }

    public String getProgressBarEmptyColor() {
        return config.getString("pinata.nametag.progress_bar_empty_color", "&7");
    }

    public double getNametagHeightOffset() {
        return config.getDouble("pinata.nametag.height_offset", 0.3);
    }

    // Item drops
    public boolean isItemDropsEnabled() {
        return config.getBoolean("pinata.item_drops.enabled", true);
    }

    public boolean isDropHeldItems() {
        return config.getBoolean("pinata.item_drops.drop_held_items", true);
    }

    public double getItemDropChance() {
        return config.getDouble("pinata.item_drops.drop_chance", 0.3);
    }

    public boolean isItemDropsProportional() {
        return config.getBoolean("pinata.item_drops.use_proportional_distribution", true);
    }

    public boolean isItemDropsBreakerOnly() {
        return config.getBoolean("pinata.item_drops.breaker_only", false);
    }

    // Random player appearance
    public boolean isRandomPlayerAppearanceEnabled() {
        return config.getBoolean("pinata.random_player_appearance.enabled", true);
    }

    public double getRandomPlayerAppearanceChance() {
        return config.getDouble("pinata.random_player_appearance.chance", 0.35);
    }

    public boolean isDropPlayerGear() {
        return config.getBoolean("pinata.random_player_appearance.drop_player_gear", true);
    }

    public List<String> getExcludedPlayers() {
        return config.getStringList("pinata.random_player_appearance.exclude_players");
    }

    public boolean isAnnounceRandomPlayer() {
        return config.getBoolean("pinata.random_player_appearance.announce_player", true);
    }

    public String getRandomPlayerAnnounceMessage() {
        return config.getString(
                "pinata.random_player_appearance.announce_message",
                "&6A pinata has appeared disguised as &e{player}&6!");
    }

    // Keyall getters
    public boolean isKeyallEnabled() {
        return keyallEnabled;
    }

    public int getKeyallInterval() {
        return keyallInterval;
    }

    public String getKeyallType() {
        return keyallType;
    }

    public String getKeyallKey() {
        return keyallKey;
    }

    public int getKeyallAmount() {
        return keyallAmount;
    }

    public List<Integer> getKeyallWarnings() {
        return new ArrayList<>(keyallWarnings);
    }

    /** Crafting challenge definition. */
    public record CraftingChallenge(
            String type,
            Material material,
            int amount,
            int timeLimit,
            String displayName,
            int rewardCoins,
            double keyChance,
            String keyType) {
        public boolean isCraft() {
            return "CRAFT".equalsIgnoreCase(type);
        }

        public boolean isSmelt() {
            return "SMELT".equalsIgnoreCase(type);
        }
    }

    /** Dragon attack configuration. */
    public record DragonAttackConfig(
            boolean enabled,
            double damage,
            double secondaryValue, // radius for lightning, duration for breath, strength for
            // knockback, count for fireball
            int cooldownSeconds,
            int minPhase) {}
}
