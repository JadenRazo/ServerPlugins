package net.serverplugins.enchants.managers;

import java.util.Map;
import net.serverplugins.enchants.ServerEnchants;
import net.serverplugins.enchants.enchantments.CustomEnchantment;
import net.serverplugins.enchants.utils.EnchantmentUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

/**
 * Manager for enchantments that need periodic tick updates. Runs a scheduled task to process
 * enchantments like Magnet.
 */
public class EnchantmentTickManager {

    private final ServerEnchants plugin;
    private final EnchantmentRegistry registry;
    private BukkitTask tickTask;

    // How often to tick enchantments (in server ticks, 20 ticks = 1 second)
    private static final long TICK_INTERVAL = 10L; // 0.5 seconds

    public EnchantmentTickManager(ServerEnchants plugin) {
        this.plugin = plugin;
        this.registry = plugin.getEnchantmentRegistry();
    }

    /** Start the tick task. */
    public void start() {
        if (tickTask != null) {
            return; // Already running
        }

        tickTask =
                Bukkit.getScheduler()
                        .runTaskTimer(plugin, this::tick, TICK_INTERVAL, TICK_INTERVAL);

        plugin.getLogger().info("Enchantment tick manager started");
    }

    /** Stop the tick task. */
    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
            plugin.getLogger().info("Enchantment tick manager stopped");
        }
    }

    /** Process one tick for all online players. */
    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            processPlayer(player);
        }
    }

    /**
     * Process enchantments for a single player.
     *
     * @param player The player to process
     */
    private void processPlayer(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        // Check all equipped items and held items
        ItemStack[] items = new ItemStack[6];
        items[0] = player.getInventory().getItemInMainHand();
        items[1] = player.getInventory().getItemInOffHand();
        items[2] = player.getInventory().getHelmet();
        items[3] = player.getInventory().getChestplate();
        items[4] = player.getInventory().getLeggings();
        items[5] = player.getInventory().getBoots();

        // Process each item
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }

            processItem(player, item);
        }
    }

    /**
     * Process enchantments on a single item.
     *
     * @param player The player holding the item
     * @param item The item to process
     */
    private void processItem(Player player, ItemStack item) {
        Map<String, Integer> enchants = EnchantmentUtils.getEnchantments(item, registry);

        for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
            CustomEnchantment enchant = registry.getById(entry.getKey());
            if (enchant == null) {
                continue;
            }

            try {
                enchant.onTick(player, entry.getValue());
            } catch (Exception e) {
                plugin.getLogger()
                        .warning(
                                "Error processing tick for "
                                        + enchant.getDisplayName()
                                        + ": "
                                        + e.getMessage());
                // Don't print full stack trace every tick - too spammy
            }
        }
    }

    /**
     * Check if the tick manager is running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return tickTask != null;
    }
}
