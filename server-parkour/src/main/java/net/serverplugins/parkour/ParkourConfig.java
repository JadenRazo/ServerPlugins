package net.serverplugins.parkour;

import java.util.ArrayList;
import java.util.List;
import net.serverplugins.api.messages.PluginMessenger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public class ParkourConfig {

    private final ServerParkour plugin;
    private final FileConfiguration config;
    private final PluginMessenger messenger;

    // NPC settings
    private boolean npcEnabled;
    private Location npcLocation;
    private Location npcTeleportLocation;
    private String npcSkin;
    private String npcDisplayName;

    // Hologram settings
    private boolean hologramEnabled;
    private double hologramOffsetY;
    private List<String> hologramLines;

    // Game settings
    private int startHeight;
    private int blocksAhead;
    private int blocksBehind;
    private int fallThreshold;

    // Block types
    private List<WeightedBlock> normalBlocks;
    private Material backwardBlockMaterial;
    private int backwardBlockWeight;
    private Material speedBlockMaterial;
    private int speedBlockWeight;
    private Material doubleJumpBlockMaterial;
    private int doubleJumpBlockWeight;
    private int slimeBlockWeight;
    private int tntBlockWeight;

    // XP settings
    private boolean xpEnabled;
    private int xpPerBlock;
    private int personalBestBonus;
    private int milestoneInterval;
    private int milestoneBonus;

    // Reward settings
    private boolean rewardsEnabled;
    private double rewardPerBlock;
    private double milestoneReward;

    public ParkourConfig(ServerParkour plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.messenger =
                new PluginMessenger(
                        config, "messages", "<light_purple><bold>[Parkour]</bold></light_purple> ");
        loadConfig();
    }

    private void loadConfig() {
        // NPC settings
        npcEnabled = config.getBoolean("npc.enabled", true);
        String worldName = config.getString("npc.location.world", "lobby");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.getWorlds().get(0);
        }
        npcLocation =
                new Location(
                        world,
                        config.getDouble("npc.location.x", 876.5),
                        config.getDouble("npc.location.y", 168.0),
                        config.getDouble("npc.location.z", 736.5),
                        (float) config.getDouble("npc.location.yaw", 0.0),
                        (float) config.getDouble("npc.location.pitch", 0.0));
        npcSkin = config.getString("npc.skin", "Notch");
        npcDisplayName = config.getString("npc.display-name", "&d&lParkour");

        // Teleport location (where player spawns after game ends)
        npcTeleportLocation =
                new Location(
                        world,
                        config.getDouble("npc.teleport-location.x", npcLocation.getX()),
                        config.getDouble("npc.teleport-location.y", npcLocation.getY() + 1),
                        config.getDouble("npc.teleport-location.z", npcLocation.getZ()),
                        (float) config.getDouble("npc.teleport-location.yaw", npcLocation.getYaw()),
                        (float) config.getDouble("npc.teleport-location.pitch", 0.0));

        // Hologram settings
        hologramEnabled = config.getBoolean("hologram.enabled", true);
        hologramOffsetY = config.getDouble("hologram.offset-y", 2.5);
        hologramLines = config.getStringList("hologram.lines");
        if (hologramLines.isEmpty()) {
            hologramLines =
                    List.of(
                            "&d&lPARKOUR",
                            "",
                            "&7Online: &f%server_online%",
                            "",
                            "&7Your Best: &a%serverparkour_highscore%");
        }

        // Game settings
        startHeight = config.getInt("game.start-height", 10);
        blocksAhead = config.getInt("game.blocks-ahead", 5);
        blocksBehind = config.getInt("game.blocks-behind", 5);
        fallThreshold = config.getInt("game.fall-threshold", 8);

        // Load block types
        normalBlocks = new ArrayList<>();
        ConfigurationSection blocksSection = config.getConfigurationSection("blocks.normal");
        if (blocksSection != null) {
            for (String key : blocksSection.getKeys(false)) {
                String materialName = config.getString("blocks.normal." + key + ".material");
                int weight = config.getInt("blocks.normal." + key + ".weight", 10);
                try {
                    Material material = Material.valueOf(materialName);
                    normalBlocks.add(new WeightedBlock(material, weight));
                } catch (Exception e) {
                    plugin.getLogger().warning("Invalid material: " + materialName);
                }
            }
        }
        if (normalBlocks.isEmpty()) {
            normalBlocks.add(new WeightedBlock(Material.STONE, 30));
            normalBlocks.add(new WeightedBlock(Material.OAK_PLANKS, 25));
            normalBlocks.add(new WeightedBlock(Material.QUARTZ_BLOCK, 20));
        }

        // Special blocks
        String backwardMat = config.getString("blocks.special.backward.material", "BLACK_CONCRETE");
        backwardBlockMaterial = Material.valueOf(backwardMat);
        backwardBlockWeight = config.getInt("blocks.special.backward.weight", 8);

        String speedMat = config.getString("blocks.special.speed.material", "GOLD_BLOCK");
        speedBlockMaterial = Material.valueOf(speedMat);
        speedBlockWeight = config.getInt("blocks.special.speed.weight", 5);

        String doubleJumpMat =
                config.getString("blocks.special.double-jump.material", "DIAMOND_BLOCK");
        doubleJumpBlockMaterial = Material.valueOf(doubleJumpMat);
        doubleJumpBlockWeight = config.getInt("blocks.special.double-jump.weight", 3);

        slimeBlockWeight = config.getInt("blocks.special.slime.weight", 5);

        tntBlockWeight = config.getInt("blocks.special.tnt.weight", 5);

        // XP settings
        xpEnabled = config.getBoolean("xp.enabled", true);
        xpPerBlock = config.getInt("xp.per-block", 5);
        personalBestBonus = config.getInt("xp.personal-best-bonus", 50);
        milestoneInterval = config.getInt("xp.milestone-interval", 25);
        milestoneBonus = config.getInt("xp.milestone-bonus", 100);

        // Reward settings
        rewardsEnabled = config.getBoolean("rewards.enabled", false);
        rewardPerBlock = config.getDouble("rewards.per-block", 0);
        milestoneReward = config.getDouble("rewards.milestone-reward", 0);
    }

    // Getters
    public boolean isNpcEnabled() {
        return npcEnabled;
    }

    public Location getNpcLocation() {
        return npcLocation.clone();
    }

    public Location getNpcTeleportLocation() {
        return npcTeleportLocation.clone();
    }

    public String getNpcSkin() {
        return npcSkin;
    }

    public String getNpcDisplayName() {
        return npcDisplayName;
    }

    public boolean isHologramEnabled() {
        return hologramEnabled;
    }

    public double getHologramOffsetY() {
        return hologramOffsetY;
    }

    public List<String> getHologramLines() {
        return hologramLines;
    }

    public int getStartHeight() {
        return startHeight;
    }

    public int getBlocksAhead() {
        return blocksAhead;
    }

    public int getBlocksBehind() {
        return blocksBehind;
    }

    public int getFallThreshold() {
        return fallThreshold;
    }

    public List<WeightedBlock> getNormalBlocks() {
        return normalBlocks;
    }

    public Material getBackwardBlockMaterial() {
        return backwardBlockMaterial;
    }

    public int getBackwardBlockWeight() {
        return backwardBlockWeight;
    }

    public Material getSpeedBlockMaterial() {
        return speedBlockMaterial;
    }

    public int getSpeedBlockWeight() {
        return speedBlockWeight;
    }

    public Material getDoubleJumpBlockMaterial() {
        return doubleJumpBlockMaterial;
    }

    public int getDoubleJumpBlockWeight() {
        return doubleJumpBlockWeight;
    }

    public int getSlimeBlockWeight() {
        return slimeBlockWeight;
    }

    public int getTntBlockWeight() {
        return tntBlockWeight;
    }

    public boolean isXpEnabled() {
        return xpEnabled;
    }

    public int getXpPerBlock() {
        return xpPerBlock;
    }

    public int getPersonalBestBonus() {
        return personalBestBonus;
    }

    public int getMilestoneInterval() {
        return milestoneInterval;
    }

    public int getMilestoneBonus() {
        return milestoneBonus;
    }

    public boolean isRewardsEnabled() {
        return rewardsEnabled;
    }

    public double getRewardPerBlock() {
        return rewardPerBlock;
    }

    public double getMilestoneReward() {
        return milestoneReward;
    }

    public PluginMessenger getMessenger() {
        return messenger;
    }

    public static class WeightedBlock {
        private final Material material;
        private final int weight;

        public WeightedBlock(Material material, int weight) {
            this.material = material;
            this.weight = weight;
        }

        public Material getMaterial() {
            return material;
        }

        public int getWeight() {
            return weight;
        }
    }
}
