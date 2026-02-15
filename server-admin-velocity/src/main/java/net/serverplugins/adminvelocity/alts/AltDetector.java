package net.serverplugins.adminvelocity.alts;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serverplugins.adminvelocity.database.AltTable;
import net.serverplugins.adminvelocity.database.PunishmentTable;
import net.serverplugins.adminvelocity.messaging.VelocityTextUtil;
import net.serverplugins.adminvelocity.punishment.VelocityPunishment;
import org.slf4j.Logger;

/** Alt detection and tracking for Velocity proxy. */
public class AltDetector {

    private final ProxyServer server;
    private final Logger logger;
    private final AltTable altTable;
    private final PunishmentTable punishmentTable;
    private final int maxAccountsPerIp;
    private final boolean notifyStaff;

    public AltDetector(
            ProxyServer server,
            Logger logger,
            AltTable altTable,
            PunishmentTable punishmentTable,
            int maxAccountsPerIp,
            boolean notifyStaff) {
        this.server = server;
        this.logger = logger;
        this.altTable = altTable;
        this.punishmentTable = punishmentTable;
        this.maxAccountsPerIp = maxAccountsPerIp;
        this.notifyStaff = notifyStaff;
    }

    /**
     * Hashes an IP address using SHA-256.
     *
     * @param ip IP address
     * @return Hex-encoded SHA-256 hash
     */
    public String hashIp(String ip) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(ip.getBytes(StandardCharsets.UTF_8));

            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // Fallback to simple hash if SHA-256 not available
            return String.valueOf(ip.hashCode());
        }
    }

    /**
     * Checks and records a player's IP on connection.
     *
     * @param player Player to check
     * @return CompletableFuture with detection result
     */
    public CompletableFuture<AltCheckResult> checkAndRecordPlayer(Player player) {
        InetSocketAddress address = player.getRemoteAddress();
        if (address == null) {
            logger.warn("Could not get IP address for player {}", player.getUsername());
            return CompletableFuture.completedFuture(
                    new AltCheckResult(player.getUniqueId(), null, 0, false));
        }

        String ip = address.getAddress().getHostAddress();
        String ipHash = hashIp(ip);
        UUID uuid = player.getUniqueId();
        String username = player.getUsername();

        // Record this connection
        return altTable.recordIp(uuid, username, ipHash)
                .thenCompose(
                        recorded -> {
                            // Get all alts for this IP
                            return altTable.getAltsByIpHash(ipHash);
                        })
                .thenCompose(
                        altMap -> {
                            int altCount = altMap.size() - 1; // Exclude current player
                            if (altCount <= 0) {
                                return CompletableFuture.completedFuture(
                                        new AltCheckResult(uuid, ipHash, 0, false));
                            }

                            // Check if any alts have active bans
                            CompletableFuture<Boolean> hasBannedAlt =
                                    checkForBannedAlts(altMap, uuid);

                            return hasBannedAlt.thenApply(
                                    banned -> {
                                        AltCheckResult result =
                                                new AltCheckResult(uuid, ipHash, altCount, banned);

                                        // Notify staff if enabled and threshold exceeded
                                        if (notifyStaff
                                                && altCount > 0
                                                && altCount <= maxAccountsPerIp) {
                                            notifyStaffOfAlts(player, altMap, banned);
                                        }

                                        return result;
                                    });
                        })
                .exceptionally(
                        ex -> {
                            logger.error(
                                    "Error checking alts for {}: {}", username, ex.getMessage());
                            return new AltCheckResult(uuid, ipHash, 0, false);
                        });
    }

    /**
     * Gets all alt accounts for a player.
     *
     * @param uuid Player UUID
     * @return CompletableFuture with alt map
     */
    public CompletableFuture<Map<UUID, String>> getAlts(UUID uuid) {
        return altTable.getIpHashes(uuid)
                .thenCompose(
                        ipHashes -> {
                            if (ipHashes.isEmpty()) {
                                return CompletableFuture.completedFuture(Map.of());
                            }

                            // Get alts from all known IP hashes
                            String primaryHash = ipHashes.get(0);
                            return altTable.getAltsByIpHash(primaryHash);
                        });
    }

    /**
     * Checks if any alt accounts have active bans.
     *
     * @param altMap Map of UUID to username
     * @param excludeUuid UUID to exclude (current player)
     * @return CompletableFuture with result
     */
    private CompletableFuture<Boolean> checkForBannedAlts(
            Map<UUID, String> altMap, UUID excludeUuid) {
        CompletableFuture<?>[] futures =
                altMap.entrySet().stream()
                        .filter(entry -> !entry.getKey().equals(excludeUuid))
                        .map(entry -> punishmentTable.getActiveBan(entry.getKey()))
                        .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures)
                .thenApply(
                        v -> {
                            for (CompletableFuture<?> future : futures) {
                                VelocityPunishment ban = (VelocityPunishment) future.join();
                                if (ban != null && !ban.isExpired()) {
                                    return true;
                                }
                            }
                            return false;
                        });
    }

    /**
     * Notifies staff of detected alts.
     *
     * @param player Player with alts
     * @param altMap Map of alt UUIDs to usernames
     * @param hasBannedAlt Whether any alt has an active ban
     */
    private void notifyStaffOfAlts(Player player, Map<UUID, String> altMap, boolean hasBannedAlt) {
        StringBuilder altList = new StringBuilder();
        for (Map.Entry<UUID, String> entry : altMap.entrySet()) {
            if (entry.getKey().equals(player.getUniqueId())) {
                continue; // Skip current player
            }
            if (altList.length() > 0) {
                altList.append(", ");
            }
            altList.append(entry.getValue());
        }

        String color = hasBannedAlt ? "<red>" : "<yellow>";
        String warning = hasBannedAlt ? " <red><bold>[BANNED ALT]" : "";
        String message =
                color
                        + "[Alt] <white>"
                        + player.getUsername()
                        + color
                        + " has "
                        + (altMap.size() - 1)
                        + " alt account(s): <white>"
                        + altList
                        + warning;

        // Send to all staff with permission
        server.getAllPlayers().stream()
                .filter(p -> p.hasPermission("serveradmin.galts"))
                .forEach(staff -> VelocityTextUtil.send(staff, message));

        logger.info(
                "Alt detection: {} has {} alt(s){}",
                player.getUsername(),
                altMap.size() - 1,
                hasBannedAlt ? " (with banned alt)" : "");
    }

    /** Result of an alt check. */
    public static class AltCheckResult {
        private final UUID uuid;
        private final String ipHash;
        private final int altCount;
        private final boolean hasBannedAlt;

        public AltCheckResult(UUID uuid, String ipHash, int altCount, boolean hasBannedAlt) {
            this.uuid = uuid;
            this.ipHash = ipHash;
            this.altCount = altCount;
            this.hasBannedAlt = hasBannedAlt;
        }

        public UUID getUuid() {
            return uuid;
        }

        public String getIpHash() {
            return ipHash;
        }

        public int getAltCount() {
            return altCount;
        }

        public boolean hasBannedAlt() {
            return hasBannedAlt;
        }
    }
}
