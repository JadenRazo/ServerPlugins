package net.serverplugins.keys.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.keys.KeysConfig;
import net.serverplugins.keys.ServerKeys;
import net.serverplugins.keys.cache.StatsCache;
import net.serverplugins.keys.models.KeyType;
import net.serverplugins.keys.models.UnclaimedKey;
import net.serverplugins.keys.repository.KeysRepository;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Manages key distribution with non-blocking async database operations. Updates the in-memory cache
 * immediately for instant reflection in placeholders.
 */
public class KeyManager {

    private final ServerKeys plugin;
    private final KeysConfig config;
    private final KeysRepository repository;
    private final StatsCache statsCache;

    public KeyManager(
            ServerKeys plugin,
            KeysConfig config,
            KeysRepository repository,
            StatsCache statsCache) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.statsCache = statsCache;
    }

    /**
     * Give a key to a player. Database writes are async (fire-and-forget). Cache is updated
     * immediately for instant placeholder reflection.
     *
     * @param player The player to give the key to
     * @param type The type of key (CRATE or DUNGEON)
     * @param keyName The key name (e.g., "daily", "easy")
     * @param amount The amount of keys to give
     * @param source The source of the key (for logging)
     * @return true if successful
     */
    public boolean giveKey(Player player, KeyType type, String keyName, int amount, String source) {
        if (!config.isValidKey(type, keyName)) {
            return false;
        }

        boolean success;
        if (type == KeyType.CRATE) {
            success = giveCrateKey(player, keyName, amount);
        } else {
            success = giveDungeonKey(player, keyName, amount);
        }

        if (success) {
            // Update cache immediately (non-blocking in-memory update)
            statsCache.updateOnKeyGiven(
                    player.getUniqueId(), player.getName(), type, keyName, amount);

            // Fire-and-forget async database writes
            repository.addReceivedAsync(
                    player.getUniqueId(), player.getName(), type, keyName, amount);
            repository.recordTransactionAsync(
                    player.getUniqueId(), player.getName(), type, keyName, amount, "GIVE", source);

            // Send message to player (no prefix, clean notification)
            TextUtil.sendSuccess(
                    player,
                    "You received <white>"
                            + amount
                            + "x "
                            + config.getKeyDisplay(type, keyName)
                            + " Key</white>!");
        }

        return success;
    }

    /**
     * Give keys to all online players.
     *
     * @param type The type of key
     * @param keyName The key name
     * @param amount The amount per player
     * @param source The source for logging
     * @return Number of players who received keys
     */
    public int giveToAll(KeyType type, String keyName, int amount, String source) {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (giveKey(player, type, keyName, amount, source)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Give keys to all online players with broadcast.
     *
     * @param type The type of key
     * @param keyName The key name
     * @param amount The amount per player
     * @param source The source for logging
     * @return Number of players who received keys
     */
    public int giveToAllWithBroadcast(KeyType type, String keyName, int amount, String source) {
        int count = giveToAll(type, keyName, amount, source);

        if (count > 0) {
            Bukkit.broadcast(
                    TextUtil.parse(
                            "<yellow>"
                                    + count
                                    + " players received <white>"
                                    + amount
                                    + "x "
                                    + config.getKeyDisplay(type, keyName)
                                    + " Key</white>!"));
        }

        return count;
    }

    /** Give a crate key via ExcellentCrates. */
    private boolean giveCrateKey(Player player, String keyName, int amount) {
        if (Bukkit.getPluginManager().getPlugin("ExcellentCrates") == null) {
            plugin.getLogger().warning("ExcellentCrates not found, cannot give crate key");
            return false;
        }

        // Check if player has inventory space (crate keys are virtual, but check for consistency)
        if (player.getInventory().firstEmpty() == -1) {
            // Store as unclaimed instead
            repository.storeUnclaimedAsync(
                    player.getUniqueId(),
                    player.getName(),
                    KeyType.CRATE,
                    keyName,
                    amount,
                    "overflow");
            TextUtil.sendWarning(
                    player,
                    "Your inventory is full! Use <white>/claimkeys</white> to claim your keys later.");
            return true;
        }

        try {
            String command = "crate key give " + player.getName() + " " + keyName + " " + amount;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to give crate key: " + e.getMessage());
            return false;
        }
    }

    /** Give a dungeon key (physical item). */
    private boolean giveDungeonKey(Player player, String keyName, int amount) {
        ItemStack key = createDungeonKey(keyName, amount);
        if (key == null) {
            return false;
        }

        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(key);
        if (!overflow.isEmpty()) {
            // Store overflow as unclaimed instead of dropping on ground
            int overflowAmount = 0;
            for (ItemStack item : overflow.values()) {
                overflowAmount += item.getAmount();
            }
            repository.storeUnclaimedAsync(
                    player.getUniqueId(),
                    player.getName(),
                    KeyType.DUNGEON,
                    keyName,
                    overflowAmount,
                    "overflow");
            TextUtil.sendWarning(
                    player,
                    "Your inventory is full! Use <white>/claimkeys</white> to claim your keys later.");
        }
        return true;
    }

    /** Create a dungeon key item matching the Skript format exactly. */
    public ItemStack createDungeonKey(String difficulty, int amount) {
        int customModelData = config.getDungeonKeyModelData(difficulty);

        String arenaName =
                switch (difficulty.toLowerCase()) {
                    case "easy" -> "easy";
                    case "medium" -> "medium";
                    case "hard" -> "hard";
                    default -> "easy";
                };

        ItemStack item = new ItemStack(Material.STICK, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        // Match exact Skript format: name is "&f" (single section symbol followed by f)
        meta.displayName(LegacyComponentSerializer.legacySection().deserialize("\u00A7f"));

        // Build lore matching Skript format exactly
        List<Component> lore = new ArrayList<>();
        lore.add(
                LegacyComponentSerializer.legacySection()
                        .deserialize(
                                "\u00A77\u0269\u1d1b \u1d04\u1d00\u0274 \u0299\u1d07 \u1d1c\ua731\u1d07\u1d05 \u1d1b\u1d0f \u1d07\u0274\u1d1b\u1d07\u0280"));
        lore.add(
                LegacyComponentSerializer.legacySection()
                        .deserialize(
                                "\u00A77\u1d1b\u029c\u1d07 "
                                        + arenaName
                                        + " \u1d05\u1d1c\u0274\u0262\u1d07\u1d0f\u0274 \u1d00\u0280\u1d07\u0274\u1d00"));
        lore.add(Component.empty());
        lore.add(
                LegacyComponentSerializer.legacySection()
                        .deserialize(
                                "\u00A78\u0262\u026a\u1d20\u1d07 \u1d1b\u029c\u026a\ua731 \u1d0b\u1d07\u028f \u1d1b\u1d0f \u1d1b\u029c\u028f\u0280\u1d00"));
        meta.lore(lore);

        meta.setCustomModelData(customModelData);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Claim all unclaimed keys for a player.
     *
     * @param player The player claiming keys
     */
    public void claimKeys(Player player) {
        repository
                .getUnclaimedAsync(player.getUniqueId())
                .thenAccept(
                        unclaimedKeys -> {
                            if (unclaimedKeys.isEmpty()) {
                                Bukkit.getScheduler()
                                        .runTask(
                                                plugin,
                                                () ->
                                                        TextUtil.sendError(
                                                                player,
                                                                "You have no unclaimed keys."));
                                return;
                            }

                            // Process on main thread since we're modifying inventory
                            Bukkit.getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                int claimed = 0;
                                                int remaining = 0;

                                                for (UnclaimedKey unclaimed : unclaimedKeys) {
                                                    boolean success;
                                                    if (unclaimed.getKeyType() == KeyType.CRATE) {
                                                        success =
                                                                giveClaimedCrateKey(
                                                                        player,
                                                                        unclaimed.getKeyName(),
                                                                        unclaimed.getAmount());
                                                    } else {
                                                        success =
                                                                giveClaimedDungeonKey(
                                                                        player,
                                                                        unclaimed.getKeyName(),
                                                                        unclaimed.getAmount());
                                                    }

                                                    if (success) {
                                                        repository.markClaimedAsync(
                                                                unclaimed.getId());
                                                        claimed += unclaimed.getAmount();
                                                    } else {
                                                        remaining += unclaimed.getAmount();
                                                        // Stop trying if inventory is full
                                                        break;
                                                    }
                                                }

                                                if (claimed > 0) {
                                                    TextUtil.sendSuccess(
                                                            player,
                                                            "Claimed <white>"
                                                                    + claimed
                                                                    + "</white> key(s)!");
                                                }
                                                if (remaining > 0) {
                                                    TextUtil.sendWarning(
                                                            player,
                                                            "You still have <white>"
                                                                    + remaining
                                                                    + "</white> unclaimed key(s). Make space and try again!");
                                                }
                                            });
                        });
    }

    /** Give a crate key directly (for claiming, no overflow storage). */
    private boolean giveClaimedCrateKey(Player player, String keyName, int amount) {
        if (player.getInventory().firstEmpty() == -1) {
            return false;
        }
        if (Bukkit.getPluginManager().getPlugin("ExcellentCrates") == null) {
            return false;
        }
        try {
            String command = "crate key give " + player.getName() + " " + keyName + " " + amount;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to give claimed crate key: " + e.getMessage());
            return false;
        }
    }

    /** Give a dungeon key directly (for claiming, no overflow storage). */
    private boolean giveClaimedDungeonKey(Player player, String keyName, int amount) {
        ItemStack key = createDungeonKey(keyName, amount);
        if (key == null) {
            return false;
        }
        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(key);
        if (!overflow.isEmpty()) {
            // Put the overflow back (remove what was added)
            for (ItemStack item : overflow.values()) {
                player.getInventory().removeItem(item);
            }
            return false;
        }
        return true;
    }

    /** Get available key names formatted for display. */
    public String getAvailableKeysFormatted(KeyType type) {
        return String.join(", ", config.getKeys(type));
    }
}
