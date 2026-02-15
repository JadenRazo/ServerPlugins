package net.serverplugins.events.events;

import java.util.Random;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.events.ServerEvents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Utility class for handling event rewards. */
public class EventReward {

    private static final Random random = new Random();

    private final int coins;
    private final double keyChance;
    private final String keyType;

    public EventReward(int coins, double keyChance, String keyType) {
        this.coins = coins;
        this.keyChance = keyChance;
        this.keyType = keyType;
    }

    /**
     * Give the reward to a player.
     *
     * @param plugin The plugin instance
     * @param player The player to reward
     * @return true if a key was awarded
     */
    public boolean give(ServerEvents plugin, Player player) {
        boolean gotKey = false;

        // Give coins via Vault
        if (coins > 0 && plugin.hasEconomy()) {
            plugin.getEconomy().depositPlayer(player, coins);
            TextUtil.send(
                    player,
                    plugin.getEventsConfig().getPrefix() + "&aYou received &6$" + coins + "&a!");
        }

        // Roll for key
        if (keyChance > 0 && random.nextDouble() < keyChance) {
            gotKey = giveKey(player, keyType);
            if (gotKey) {
                TextUtil.send(
                        player,
                        plugin.getEventsConfig().getPrefix()
                                + "&6LUCKY! &eYou received a &f"
                                + keyType
                                + " &ekey!");
            }
        }

        return gotKey;
    }

    /** Give a crate key to a player. Uses ExcellentCrates command if available. */
    private boolean giveKey(Player player, String keyType) {
        // Use ExcellentCrates command to give key
        // Format: /crates key give <player> <crate> <amount>
        String command = "crates key give " + player.getName() + " " + keyType + " 1";

        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            return true;
        } catch (Exception e) {
            // ExcellentCrates might not be installed
            return false;
        }
    }

    /** Create a reward from config values. */
    public static EventReward of(int coins, double keyChance, String keyType) {
        return new EventReward(coins, keyChance, keyType);
    }

    // Getters
    public int getCoins() {
        return coins;
    }

    public double getKeyChance() {
        return keyChance;
    }

    public String getKeyType() {
        return keyType;
    }
}
