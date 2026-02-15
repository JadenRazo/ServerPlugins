package net.serverplugins.bounty.managers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.serverplugins.api.messages.Placeholder;
import net.serverplugins.bounty.ServerBounty;
import net.serverplugins.bounty.models.Bounty;
import net.serverplugins.bounty.models.Contribution;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class BountyManager {

    private final ServerBounty plugin;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public BountyManager(ServerBounty plugin) {
        this.plugin = plugin;
    }

    public PlacementResult placeBounty(Player placer, OfflinePlayer target, double amount) {
        UUID placerUuid = placer.getUniqueId();
        UUID targetUuid = target.getUniqueId();

        // Check self-bounty
        if (!plugin.getBountyConfig().isAllowSelfBounty() && placerUuid.equals(targetUuid)) {
            return PlacementResult.failure("self-bounty-denied");
        }

        // Check minimum amount
        double minAmount = plugin.getBountyConfig().getMinBountyAmount();
        if (amount < minAmount) {
            return PlacementResult.failure(
                    "amount-too-low", Map.of("min", plugin.getEconomyProvider().format(minAmount)));
        }

        // Check maximum amount (0 means no limit)
        double maxAmount = plugin.getBountyConfig().getMaxBountyAmount();
        if (maxAmount > 0 && amount > maxAmount) {
            return PlacementResult.failure(
                    "amount-too-high",
                    Map.of("max", plugin.getEconomyProvider().format(maxAmount)));
        }

        // Check cooldown (unless bypassed)
        if (!placer.hasPermission("bounty.bypass.cooldown") && isOnCooldown(placerUuid)) {
            return PlacementResult.failure("on-cooldown");
        }

        // Calculate tax
        double taxRate = plugin.getBountyConfig().getTaxPercentage() / 100.0;
        double taxPaid = 0;
        if (!placer.hasPermission("bounty.bypass.tax") && taxRate > 0) {
            taxPaid = amount * taxRate;
        }
        double totalCost = amount + taxPaid;

        // Check funds
        if (!plugin.getEconomyProvider().has(placer, totalCost)) {
            return PlacementResult.failure("insufficient-funds");
        }

        // Withdraw funds
        if (!plugin.getEconomyProvider().withdraw(placer, totalCost)) {
            return PlacementResult.failure("transaction-failed");
        }

        // Get or create bounty
        Bounty bounty = plugin.getRepository().getActiveBounty(targetUuid);
        if (bounty == null) {
            String targetName =
                    target.getName() != null
                            ? target.getName()
                            : targetUuid.toString().substring(0, 8);
            bounty = plugin.getRepository().createBounty(targetUuid, targetName);
            if (bounty == null) {
                // Refund on failure
                plugin.getEconomyProvider().deposit(placer, totalCost);
                return PlacementResult.failure("database-error");
            }
        }

        // Add contribution
        plugin.getRepository()
                .addContribution(bounty.getId(), placerUuid, placer.getName(), amount, taxPaid);

        // Update bounty total
        double newTotal = bounty.getTotalAmount() + amount;
        plugin.getRepository().updateBountyTotal(bounty.getId(), newTotal);
        bounty.setTotalAmount(newTotal);

        // Set cooldown
        setCooldown(placerUuid);

        // Broadcast if enabled
        if (plugin.getBountyConfig().isBroadcastOnPlace()) {
            String targetName = target.getName() != null ? target.getName() : "Unknown";
            for (Player online : Bukkit.getOnlinePlayers()) {
                plugin.getBountyConfig()
                        .getMessenger()
                        .send(
                                online,
                                "broadcast-placed",
                                Placeholder.of("placer", placer.getName()),
                                Placeholder.of("target", targetName),
                                Placeholder.of("amount", plugin.formatCurrency(amount)),
                                Placeholder.of("total", plugin.formatCurrency(newTotal)));
            }
        }

        // Publish to Discord via Redis
        String targetName = target.getName() != null ? target.getName() : "Unknown";
        plugin.publishBountyPlaced(
                placer.getName(),
                placer.getUniqueId().toString(),
                targetName,
                target.getUniqueId().toString(),
                amount,
                newTotal);

        return PlacementResult.success(
                bounty,
                Map.of(
                        "amount", plugin.getEconomyProvider().format(amount),
                        "target", target.getName() != null ? target.getName() : "Unknown",
                        "total", plugin.getEconomyProvider().format(newTotal)));
    }

    public boolean processBountyKill(Player killer, Player victim) {
        UUID victimUuid = victim.getUniqueId();

        // Get active bounty on victim
        Bounty bounty = plugin.getRepository().getActiveBounty(victimUuid);
        if (bounty == null || bounty.getTotalAmount() <= 0) {
            return false;
        }

        // Get contributions and find top contributor
        var contributions = plugin.getRepository().getContributions(bounty.getId());
        Contribution topContribution = plugin.getRepository().getTopContribution(bounty.getId());

        // Calculate payout with tax
        double total = bounty.getTotalAmount();
        double taxRate = plugin.getBountyConfig().getTaxPercentage() / 100.0;
        double payout = total - (total * taxRate);

        // Deposit to killer
        if (!plugin.getEconomyProvider().deposit(killer, payout)) {
            plugin.getLogger().warning("Failed to deposit bounty payout to " + killer.getName());
            return false;
        }

        // Record in history
        plugin.getRepository()
                .recordHistory(
                        victimUuid,
                        victim.getName(),
                        killer.getUniqueId(),
                        killer.getName(),
                        payout,
                        victim.getWorld().getName(),
                        victim.getLocation().getX(),
                        victim.getLocation().getY(),
                        victim.getLocation().getZ());

        // Create trophy head for top contributor
        if (topContribution != null) {
            UUID topContributorUuid = topContribution.getContributorUuid();
            Instant expiresAt =
                    Instant.now()
                            .plus(plugin.getBountyConfig().getHeadExpiryDays(), ChronoUnit.DAYS);

            plugin.getRepository()
                    .createTrophyHead(
                            topContributorUuid, victimUuid, victim.getName(), total, "", expiresAt);

            // Notify top contributor if online
            Player topContributor = Bukkit.getPlayer(topContributorUuid);
            if (topContributor != null && topContributor.isOnline()) {
                plugin.getBountyConfig()
                        .getMessenger()
                        .send(
                                topContributor,
                                "head-ready",
                                Placeholder.of("victim", victim.getName()));
            }
        }

        // Delete bounty and contributions (contributions cascade via foreign key or manual delete)
        plugin.getRepository().deleteBounty(bounty.getId());

        // Broadcast if enabled
        if (plugin.getBountyConfig().isBroadcastOnKill()) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                plugin.getBountyConfig()
                        .getMessenger()
                        .send(
                                online,
                                "broadcast-kill",
                                Placeholder.of("killer", killer.getName()),
                                Placeholder.of("victim", victim.getName()),
                                Placeholder.of("amount", plugin.formatCurrency(payout)));
            }
        }

        // Publish to Discord via Redis
        plugin.publishBountyClaimed(
                killer.getName(),
                killer.getUniqueId().toString(),
                victim.getName(),
                victim.getUniqueId().toString(),
                payout);

        return true;
    }

    public Bounty getActiveBounty(UUID targetUuid) {
        return plugin.getRepository().getActiveBounty(targetUuid);
    }

    public boolean isOnCooldown(UUID playerUuid) {
        Long cooldownEnd = cooldowns.get(playerUuid);
        if (cooldownEnd == null) {
            return false;
        }
        if (System.currentTimeMillis() >= cooldownEnd) {
            cooldowns.remove(playerUuid);
            return false;
        }
        return true;
    }

    public long getCooldownRemaining(UUID playerUuid) {
        Long cooldownEnd = cooldowns.get(playerUuid);
        if (cooldownEnd == null) {
            return 0;
        }
        long remaining = cooldownEnd - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    private void setCooldown(UUID playerUuid) {
        long cooldownMs = plugin.getBountyConfig().getCooldownSeconds() * 1000L;
        cooldowns.put(playerUuid, System.currentTimeMillis() + cooldownMs);
    }

    public void clearCooldown(UUID playerUuid) {
        cooldowns.remove(playerUuid);
    }

    public static class PlacementResult {

        private final boolean success;
        private final Bounty bounty;
        private final String errorKey;
        private final Map<String, String> replacements;

        private PlacementResult(
                boolean success, Bounty bounty, String errorKey, Map<String, String> replacements) {
            this.success = success;
            this.bounty = bounty;
            this.errorKey = errorKey;
            this.replacements = replacements != null ? replacements : Map.of();
        }

        public static PlacementResult success(Bounty bounty, Map<String, String> replacements) {
            return new PlacementResult(true, bounty, null, replacements);
        }

        public static PlacementResult failure(String errorKey) {
            return new PlacementResult(false, null, errorKey, null);
        }

        public static PlacementResult failure(String errorKey, Map<String, String> replacements) {
            return new PlacementResult(false, null, errorKey, replacements);
        }

        public boolean isSuccess() {
            return success;
        }

        public Bounty getBounty() {
            return bounty;
        }

        public String getErrorKey() {
            return errorKey;
        }

        public Map<String, String> getReplacements() {
            return replacements;
        }
    }
}
