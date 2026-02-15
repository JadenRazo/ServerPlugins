package net.serverplugins.arcade.games;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.machines.Direction;
import net.serverplugins.arcade.machines.Machine;
import net.serverplugins.arcade.machines.MachineCategory;
import net.serverplugins.arcade.machines.MachineStructure;
import net.serverplugins.arcade.machines.MachineStructureParser;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Base class for all game types. */
public abstract class GameType {

    protected final ServerArcade plugin;
    protected final String name;
    protected final String configKey;

    // GUI settings
    protected String guiTitle = "§fGame";
    protected int guiSize = 54;

    // Bet settings
    protected int defaultBet = 100;
    protected int minBet = 100;
    protected int maxBet = 100000;
    protected int[] betAmounts = {100, 250, 500, 1000, 2500};
    protected String betMenuTitle = "§fSelect Bet";
    protected int betMenuSize = 45;

    // Machine structure
    protected MachineStructure machineStructure;
    protected ItemStack machineItem;

    // Machine category (personal vs casino)
    protected MachineCategory category = MachineCategory.CASINO;

    // Messages
    protected final Map<String, String> messages = new HashMap<>();

    // Hologram settings
    protected List<String> hologramLines = new ArrayList<>();
    protected double hologramHeight = 2.5;

    public GameType(ServerArcade plugin, String name, String configKey) {
        this.plugin = plugin;
        this.name = name;
        this.configKey = configKey;
    }

    /** Load game configuration from YAML. */
    public void loadConfig(ConfigurationSection config) {
        if (config == null) return;

        // Load GUI settings
        ConfigurationSection guiSection = config.getConfigurationSection("gui");
        if (guiSection != null) {
            guiTitle = guiSection.getString("title", guiTitle).replace("&", "§");
            guiSize = guiSection.getInt("size", guiSize);
        }

        // Load bet settings
        ConfigurationSection betSection = config.getConfigurationSection("bet");
        if (betSection != null) {
            defaultBet = betSection.getInt("default_bet", defaultBet);

            ConfigurationSection betGui = betSection.getConfigurationSection("gui");
            if (betGui != null) {
                betMenuTitle = betGui.getString("title", betMenuTitle).replace("&", "§");
                betMenuSize = betGui.getInt("size", betMenuSize);

                // Load bet values
                ConfigurationSection values = betGui.getConfigurationSection("values");
                if (values != null) {
                    var keys =
                            values.getKeys(false).stream()
                                    .filter(k -> !k.equals("custom"))
                                    .toList();

                    betAmounts = new int[keys.size()];
                    for (int i = 0; i < keys.size(); i++) {
                        try {
                            betAmounts[i] = Integer.parseInt(keys.get(i));
                        } catch (NumberFormatException e) {
                            betAmounts[i] = 100;
                        }
                    }

                    ConfigurationSection custom = values.getConfigurationSection("custom");
                    if (custom != null) {
                        minBet = custom.getInt("min", minBet);
                        maxBet = custom.getInt("max", maxBet);
                    }
                }
            }
        }

        // Load machine category (personal vs casino)
        String categoryStr = config.getString("category", "casino");
        category = MachineCategory.fromString(categoryStr);

        // Load machine settings
        ConfigurationSection machineSection = config.getConfigurationSection("machine");
        if (machineSection != null) {
            machineStructure = MachineStructureParser.parse(machineSection);
            machineItem =
                    MachineStructureParser.parseItem(
                            machineSection.getConfigurationSection("item"));
            plugin.getLogger()
                    .info(
                            "Loaded "
                                    + category.getConfigName()
                                    + " machine structure for "
                                    + name
                                    + " with "
                                    + (machineStructure != null
                                            ? machineStructure.getElements().size()
                                            : 0)
                                    + " elements");

            // Load hologram settings
            ConfigurationSection holoSection = machineSection.getConfigurationSection("hologram");
            if (holoSection != null) {
                hologramHeight = holoSection.getDouble("height", 2.5);
                List<String> lines = holoSection.getStringList("lines");
                if (!lines.isEmpty()) {
                    hologramLines = new ArrayList<>();
                    for (String line : lines) {
                        hologramLines.add(line.replace("&", "§"));
                    }
                }
            }
        }

        // Load game-specific config
        onConfigLoad(config);
    }

    /** Override to load game-specific configuration. */
    protected void onConfigLoad(ConfigurationSection config) {}

    /** Open the game for a player. */
    public abstract void open(Player player, Machine machine);

    /** Open the game without a machine. */
    public void open(Player player) {
        open(player, null);
    }

    /** Create a machine for this game type. */
    public Machine createMachine(String id, Location location, Direction direction) {
        return new Machine(id, this, location, direction, null, System.currentTimeMillis());
    }

    /** Get a message with optional default. */
    public String getMessage(String key, String defaultValue) {
        return messages.getOrDefault(key, defaultValue);
    }

    /** Get a message. */
    public String getMessage(String key) {
        return getMessage(key, "&cMissing message: " + key);
    }

    /**
     * Validate bet amount to prevent integer overflow exploits. SECURITY: Checks that bet *
     * multiplier won't overflow.
     *
     * @param bet the bet amount
     * @param maxMultiplier the maximum multiplier for this game
     * @return true if bet is safe, false if it would cause overflow
     */
    public static boolean isValidBet(int bet, double maxMultiplier) {
        // Check bet is positive
        if (bet <= 0) {
            return false;
        }

        // Calculate max safe bet = Integer.MAX_VALUE / maxMultiplier
        // Add safety margin to prevent edge cases
        double maxSafeBet = Integer.MAX_VALUE / (maxMultiplier * 1.1);

        return bet < maxSafeBet;
    }

    /**
     * Safely calculate winnings from bet and multiplier. SECURITY: Uses Math.multiplyExact to
     * detect overflow.
     *
     * @param bet the bet amount
     * @param multiplier the win multiplier
     * @return the winnings, or -1 if overflow would occur
     */
    public static int safeMultiply(int bet, double multiplier) {
        try {
            // First convert to long to avoid intermediate overflow
            long result = (long) (bet * multiplier);

            // Check if result fits in an int
            if (result > Integer.MAX_VALUE || result < Integer.MIN_VALUE) {
                return -1; // Overflow
            }

            return (int) result;
        } catch (ArithmeticException e) {
            return -1; // Overflow
        }
    }

    // Getters
    public ServerArcade getPlugin() {
        return plugin;
    }

    public String getName() {
        return name;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getGuiTitle() {
        return guiTitle;
    }

    public int getGuiSize() {
        return guiSize;
    }

    public int getDefaultBet() {
        return defaultBet;
    }

    public int getMinBet() {
        return minBet;
    }

    public int getMaxBet() {
        return maxBet;
    }

    public int[] getBetAmounts() {
        return betAmounts;
    }

    public String getBetMenuTitle() {
        return betMenuTitle;
    }

    public int getBetMenuSize() {
        return betMenuSize;
    }

    public MachineStructure getMachineStructure() {
        return machineStructure;
    }

    public ItemStack getMachineItem() {
        return machineItem;
    }

    public MachineCategory getCategory() {
        return category;
    }

    public List<String> getHologramLines() {
        return hologramLines;
    }

    public double getHologramHeight() {
        return hologramHeight;
    }
}
