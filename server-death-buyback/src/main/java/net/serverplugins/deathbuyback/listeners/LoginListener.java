package net.serverplugins.deathbuyback.listeners;

import java.util.List;
import net.serverplugins.deathbuyback.ServerDeathBuyback;
import net.serverplugins.deathbuyback.models.DeathInventory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class LoginListener implements Listener {

    private final ServerDeathBuyback plugin;

    public LoginListener(ServerDeathBuyback plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getDeathBuybackConfig().notifyOnLogin()) {
            return;
        }

        Player player = event.getPlayer();

        // Check if player has any stored death inventories
        plugin.getServer()
                .getScheduler()
                .runTaskLaterAsynchronously(
                        plugin,
                        () -> {
                            List<DeathInventory> deaths =
                                    plugin.getDeathInventoryManager()
                                            .getActiveInventories(player.getUniqueId());

                            if (!deaths.isEmpty()) {
                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    if (player.isOnline()) {
                                                        int count = deaths.size();
                                                        double totalValue =
                                                                deaths.stream()
                                                                        .mapToDouble(
                                                                                DeathInventory
                                                                                        ::getBuybackPrice)
                                                                        .sum();

                                                        String formattedValue =
                                                                plugin.getPricingManager()
                                                                        .formatPrice(totalValue);

                                                        plugin.getDeathBuybackConfig()
                                                                .sendMessage(
                                                                        player,
                                                                        "login-reminder",
                                                                        "{count}",
                                                                        String.valueOf(count),
                                                                        "{value}",
                                                                        formattedValue);
                                                    }
                                                });
                            }
                        },
                        40L); // 2 second delay
    }
}
