package net.serverplugins.bounty.listeners;

import java.util.List;
import net.serverplugins.api.messages.Placeholder;
import net.serverplugins.bounty.ServerBounty;
import net.serverplugins.bounty.models.Bounty;
import net.serverplugins.bounty.models.TrophyHead;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class JoinListener implements Listener {

    private final ServerBounty plugin;

    public JoinListener(ServerBounty plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Schedule delayed task (40 ticks = 2 seconds) to let player fully load
        plugin.getServer()
                .getScheduler()
                .runTaskLaterAsynchronously(
                        plugin,
                        () -> {
                            // Check if player is still online
                            if (!player.isOnline()) {
                                return;
                            }

                            // Check for unclaimed trophy heads
                            List<TrophyHead> unclaimedHeads =
                                    plugin.getRepository().getUnclaimedHeads(player.getUniqueId());

                            if (!unclaimedHeads.isEmpty()) {
                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    if (player.isOnline()) {
                                                        plugin.getBountyConfig()
                                                                .getMessenger()
                                                                .send(
                                                                        player,
                                                                        "unclaimed-heads",
                                                                        Placeholder.of(
                                                                                "count",
                                                                                unclaimedHeads
                                                                                        .size()));
                                                    }
                                                });
                            }

                            // Check if player has a bounty on them
                            Bounty bounty =
                                    plugin.getRepository().getActiveBounty(player.getUniqueId());
                            if (bounty != null && bounty.getTotalAmount() > 0) {
                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    if (player.isOnline()) {
                                                        plugin.getBountyConfig()
                                                                .getMessenger()
                                                                .send(
                                                                        player,
                                                                        "has-bounty",
                                                                        Placeholder.of(
                                                                                "amount",
                                                                                plugin
                                                                                        .formatCurrency(
                                                                                                bounty
                                                                                                        .getTotalAmount())));
                                                    }
                                                });
                            }
                        },
                        40L);
    }
}
