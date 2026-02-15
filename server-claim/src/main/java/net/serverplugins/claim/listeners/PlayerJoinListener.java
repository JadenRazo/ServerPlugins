package net.serverplugins.claim.listeners;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.managers.UpkeepManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerJoinListener implements Listener {

    private final ServerClaim plugin;

    public PlayerJoinListener(ServerClaim plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Load player claim data into cache asynchronously
        plugin.getClaimManager().loadPlayerDataAsync(player.getUniqueId(), player.getName());

        // Load player rewards data into cache asynchronously
        if (plugin.getRewardsManager() != null) {
            plugin.getRewardsManager().preloadPlayer(player);
        }

        // Check claim upkeep status and show on login (delayed to let data load)
        if (plugin.getUpkeepManager() != null && plugin.getUpkeepManager().isUpkeepEnabled()) {
            plugin.getServer()
                    .getScheduler()
                    .runTaskLaterAsynchronously(
                            plugin,
                            () -> {
                                sendUpkeepStatus(player);
                            },
                            60L); // 3 seconds after join
        }
    }

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d 'at' h:mm a").withZone(ZoneId.systemDefault());

    private void sendUpkeepStatus(Player player) {
        if (!player.isOnline()) return;

        List<UpkeepManager.ClaimUpkeepStatus> statuses =
                plugin.getUpkeepManager().getClaimUpkeepStatuses(player.getUniqueId());

        // No claims = no message
        if (statuses.isEmpty()) return;

        List<UpkeepManager.ClaimUpkeepStatus> atRisk = new ArrayList<>();
        List<UpkeepManager.ClaimUpkeepStatus> healthy = new ArrayList<>();

        for (UpkeepManager.ClaimUpkeepStatus status : statuses) {
            if (status.inGracePeriod()) {
                atRisk.add(status);
            } else {
                healthy.add(status);
            }
        }

        // Send on main thread
        plugin.getServer()
                .getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            if (!player.isOnline()) return;

                            String prefix = plugin.getClaimConfig().getMessage("prefix");

                            if (atRisk.isEmpty()) {
                                // All claims in good standing
                                TextUtil.send(player, "");
                                TextUtil.send(
                                        player,
                                        prefix
                                                + "<green>Your claims are in good standing!</green>");

                                for (UpkeepManager.ClaimUpkeepStatus s : healthy) {
                                    String nextPayment =
                                            s.nextPaymentDue() != null
                                                    ? DATE_FORMAT.format(s.nextPaymentDue())
                                                    : "N/A";
                                    String funded =
                                            s.daysRemaining() >= 0
                                                    ? s.daysRemaining() + " days funded"
                                                    : "no upkeep cost";

                                    TextUtil.send(
                                            player,
                                            prefix
                                                    + "  <gray>-</gray> <white>"
                                                    + s.claimName()
                                                    + "</white> <gray>|</gray> <green>$"
                                                    + String.format("%.0f", s.balance())
                                                    + " banked</green> <gray>|</gray> <gray>Next: "
                                                    + nextPayment
                                                    + " ("
                                                    + funded
                                                    + ")</gray>");
                                }
                                TextUtil.send(player, "");
                            } else {
                                // Some claims at risk
                                TextUtil.send(player, "");
                                TextUtil.send(
                                        player, prefix + "<red><bold>CLAIM WARNING!</bold></red>");
                                TextUtil.send(
                                        player,
                                        prefix
                                                + "<yellow>You have "
                                                + atRisk.size()
                                                + " claim(s) at risk of losing chunks:</yellow>");

                                for (UpkeepManager.ClaimUpkeepStatus s : atRisk) {
                                    String urgency;
                                    if (s.graceHoursRemaining() <= 24) {
                                        urgency =
                                                "<red><bold>"
                                                        + s.graceHoursRemaining()
                                                        + "h remaining</bold></red>";
                                    } else {
                                        long days = s.graceHoursRemaining() / 24;
                                        urgency = "<gold>" + days + " day(s) remaining</gold>";
                                    }

                                    double needed = s.upkeepCost() - s.balance();
                                    String neededStr =
                                            needed > 0 ? String.format("$%.2f", needed) : "funded";

                                    TextUtil.send(
                                            player,
                                            prefix
                                                    + "  <gray>-</gray> <white>"
                                                    + s.claimName()
                                                    + "</white> <gray>|</gray> "
                                                    + urgency
                                                    + " <gray>|</gray> <yellow>Need: "
                                                    + neededStr
                                                    + "</yellow>");
                                }

                                if (!healthy.isEmpty()) {
                                    TextUtil.send(player, "");
                                    TextUtil.send(
                                            player,
                                            prefix
                                                    + "<green>Your other claims are in good standing:</green>");

                                    for (UpkeepManager.ClaimUpkeepStatus s : healthy) {
                                        String nextPayment =
                                                s.nextPaymentDue() != null
                                                        ? DATE_FORMAT.format(s.nextPaymentDue())
                                                        : "N/A";
                                        String funded =
                                                s.daysRemaining() >= 0
                                                        ? s.daysRemaining() + " days funded"
                                                        : "no upkeep cost";

                                        TextUtil.send(
                                                player,
                                                prefix
                                                        + "  <gray>-</gray> <white>"
                                                        + s.claimName()
                                                        + "</white> <gray>|</gray> <green>$"
                                                        + String.format("%.0f", s.balance())
                                                        + " banked</green> <gray>|</gray> <gray>Next: "
                                                        + nextPayment
                                                        + " ("
                                                        + funded
                                                        + ")</gray>");
                                    }
                                }

                                TextUtil.send(player, "");
                                TextUtil.send(
                                        player,
                                        prefix
                                                + "<gray>Use</gray> <aqua>/claim bank deposit <amount></aqua> "
                                                + "<gray>to fund your claims!</gray>");
                                TextUtil.send(player, "");
                            }
                        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        java.util.UUID uuid = event.getPlayer().getUniqueId();

        // Clear all ClaimManager player state (caches and pending inputs)
        plugin.getClaimManager().clearPlayerState(uuid);

        // Clear ProfileManager pending inputs
        plugin.getProfileManager().clearPendingInputs(uuid);

        // Clear VisitationManager pending inputs
        plugin.getVisitationManager().clearPendingInputs(uuid);

        // Unload RewardsManager player cache
        if (plugin.getRewardsManager() != null) {
            plugin.getRewardsManager().unloadPlayer(uuid);
        }
    }
}
