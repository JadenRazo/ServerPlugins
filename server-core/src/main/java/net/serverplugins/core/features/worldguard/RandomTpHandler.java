package net.serverplugins.core.features.worldguard;

import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.session.MoveType;
import com.sk89q.worldguard.session.Session;
import com.sk89q.worldguard.session.handler.Handler;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.core.ServerCore;
import net.serverplugins.core.features.WorldGuardFlagsFeature;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * WorldGuard session handler for the random-tp flag Integrates BetterRTP API with WorldGuard
 * regions
 */
public class RandomTpHandler extends Handler {

    private static final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private static final Set<UUID> pendingTeleports = new HashSet<>();

    private final ServerCore plugin;

    // Configuration cache
    private boolean enabled;
    private int cooldownSeconds;
    private String bypassPermission;
    private boolean grantBypassPermission;
    private int bypassDurationSeconds;
    private List<PotionEffectConfig> potionEffects;
    private String teleportMessage;
    private String cooldownMessage;

    public static final Factory FACTORY = new Factory();

    public static class Factory extends Handler.Factory<RandomTpHandler> {
        @Override
        public RandomTpHandler create(Session session) {
            ServerCore plugin = (ServerCore) Bukkit.getPluginManager().getPlugin("ServerCore");
            if (plugin == null) return null;

            // Check if BetterRTP is available (soft dependency - no compile-time reference needed)
            Plugin betterRTP = Bukkit.getPluginManager().getPlugin("BetterRTP");
            if (betterRTP == null || !betterRTP.isEnabled()) return null;

            return new RandomTpHandler(session, plugin);
        }
    }

    protected RandomTpHandler(Session session, ServerCore plugin) {
        super(session);
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        String basePath = "settings.worldguard-flags.random-tp.";
        enabled = plugin.getConfig().getBoolean(basePath + "enabled", true);
        cooldownSeconds = plugin.getConfig().getInt(basePath + "cooldown", 60);
        bypassPermission =
                plugin.getConfig().getString(basePath + "bypass-permission", "betterrtp.bypass");
        grantBypassPermission =
                plugin.getConfig().getBoolean(basePath + "grant-bypass-permission", true);
        bypassDurationSeconds = plugin.getConfig().getInt(basePath + "bypass-duration", 10);
        teleportMessage =
                plugin.getConfig()
                        .getString(
                                basePath + "teleport-message",
                                "<green>You have been randomly teleported!");
        cooldownMessage =
                plugin.getConfig()
                        .getString(
                                basePath + "cooldown-message",
                                "<red>Please wait <seconds> seconds before using random teleport again.");

        // Load potion effects
        potionEffects = new ArrayList<>();
        var effectsList = plugin.getConfig().getMapList(basePath + "potion-effects");
        for (var effectMap : effectsList) {
            String type = (String) effectMap.get("type");
            Object durationObj = effectMap.get("duration");
            Object amplifierObj = effectMap.get("amplifier");

            int duration = (durationObj instanceof Integer) ? (Integer) durationObj : 100;
            int amplifier = (amplifierObj instanceof Integer) ? (Integer) amplifierObj : 0;

            PotionEffectType effectType = PotionEffectType.getByName(type);
            if (effectType != null) {
                potionEffects.add(new PotionEffectConfig(effectType, duration, amplifier));
            }
        }
    }

    @Override
    public boolean onCrossBoundary(
            LocalPlayer player,
            Location from,
            Location to,
            ApplicableRegionSet toSet,
            Set<ProtectedRegion> entered,
            Set<ProtectedRegion> exited,
            MoveType moveType) {

        if (!enabled || WorldGuardFlagsFeature.FLAG_RANDOM_TP == null) {
            return true;
        }

        // Check if entering a region with random-tp flag set to ALLOW
        boolean shouldTeleport = toSet.testState(player, WorldGuardFlagsFeature.FLAG_RANDOM_TP);

        if (shouldTeleport && !pendingTeleports.contains(player.getUniqueId())) {
            Player bukkitPlayer = Bukkit.getPlayer(player.getUniqueId());
            if (bukkitPlayer != null) {
                handleRandomTeleport(bukkitPlayer);
            }
        }

        return true;
    }

    private void handleRandomTeleport(Player player) {
        UUID playerId = player.getUniqueId();

        // Check cooldown
        if (cooldowns.containsKey(playerId)) {
            long lastUse = cooldowns.get(playerId);
            long now = System.currentTimeMillis();
            long elapsed = (now - lastUse) / 1000;

            if (elapsed < cooldownSeconds) {
                long remaining = cooldownSeconds - elapsed;
                String message = cooldownMessage.replace("<seconds>", String.valueOf(remaining));
                TextUtil.send(player, message);
                return;
            }
        }

        // Mark as pending to prevent multiple simultaneous teleports
        if (pendingTeleports.contains(playerId)) {
            return; // Already teleporting
        }
        pendingTeleports.add(playerId);

        // Grant temporary bypass permission if configured
        if (grantBypassPermission && !bypassPermission.isEmpty()) {
            grantTemporaryPermission(player, bypassPermission, bypassDurationSeconds);
        }

        // Apply potion effects
        for (PotionEffectConfig effect : potionEffects) {
            player.addPotionEffect(
                    new PotionEffect(
                            effect.type,
                            effect.duration,
                            effect.amplifier,
                            false, // ambient
                            true, // particles
                            true // icon
                            ));
        }

        // Execute BetterRTP teleport using command
        // This is the most compatible approach that works with all BetterRTP features
        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            try {
                                // Execute RTP command as player to use BetterRTP's built-in logic
                                boolean success = player.performCommand("rtp");

                                pendingTeleports.remove(playerId);

                                if (success) {
                                    cooldowns.put(playerId, System.currentTimeMillis());
                                    // Note: BetterRTP will handle its own messaging
                                } else {
                                    TextUtil.send(
                                            player,
                                            "<red>Failed to find a safe teleport location. Please try again.");
                                }
                            } catch (Exception e) {
                                pendingTeleports.remove(playerId);
                                plugin.getLogger()
                                        .warning(
                                                "Failed to execute BetterRTP teleport for "
                                                        + player.getName()
                                                        + ": "
                                                        + e.getMessage());
                                TextUtil.send(
                                        player, "<red>An error occurred during teleportation.");
                            }
                        });
    }

    private void grantTemporaryPermission(Player player, String permission, int durationSeconds) {
        try {
            LuckPerms luckPerms = LuckPermsProvider.get();

            Bukkit.getScheduler()
                    .runTaskAsynchronously(
                            plugin,
                            () -> {
                                try {
                                    User user =
                                            luckPerms
                                                    .getUserManager()
                                                    .loadUser(player.getUniqueId())
                                                    .join();
                                    if (user == null) return;

                                    // Create temporary permission node
                                    Node node =
                                            Node.builder(permission)
                                                    .expiry(Duration.ofSeconds(durationSeconds))
                                                    .build();

                                    // Add the node
                                    user.data().add(node);

                                    // Save the user
                                    luckPerms.getUserManager().saveUser(user);

                                    plugin.getLogger()
                                            .info(
                                                    "Granted temporary permission '"
                                                            + permission
                                                            + "' to "
                                                            + player.getName()
                                                            + " for "
                                                            + durationSeconds
                                                            + " seconds");

                                } catch (Exception e) {
                                    plugin.getLogger()
                                            .warning(
                                                    "Failed to grant temporary permission to "
                                                            + player.getName()
                                                            + ": "
                                                            + e.getMessage());
                                }
                            });
        } catch (Exception e) {
            plugin.getLogger()
                    .warning(
                            "LuckPerms not available for temporary permissions: " + e.getMessage());
        }
    }

    /** Clear cooldown for a player (useful for admin commands or resets) */
    public static void clearCooldown(UUID playerId) {
        cooldowns.remove(playerId);
    }

    /** Clear all cooldowns (useful for server reload) */
    public static void clearAllCooldowns() {
        cooldowns.clear();
        pendingTeleports.clear();
    }

    private static class PotionEffectConfig {
        final PotionEffectType type;
        final int duration;
        final int amplifier;

        PotionEffectConfig(PotionEffectType type, int duration, int amplifier) {
            this.type = type;
            this.duration = duration;
            this.amplifier = amplifier;
        }
    }
}
