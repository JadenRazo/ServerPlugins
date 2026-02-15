package net.serverplugins.filter.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.serverplugins.filter.ServerFilter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ChatProtectionListener implements Listener {

    private final ServerFilter plugin;
    private final ViolationHandler violationHandler;

    // Anti-spam tracking
    private final Map<UUID, Long> lastMessageTime = new HashMap<>();
    private final Map<UUID, String> lastMessage = new HashMap<>();
    private final Map<UUID, Integer> duplicateCount = new HashMap<>();

    // Config values
    private final boolean antiSpamEnabled;
    private final long messageDelayMs;
    private final int duplicateThreshold;
    private final long duplicateWindowMs;

    private final boolean antiCapsEnabled;
    private final int minCapsLength;
    private final int maxCapsPercentage;

    private final boolean antiAdvertisingEnabled;
    private final Pattern urlPattern;
    private final Pattern ipPattern;
    private final java.util.Set<String> whitelistedDomains;

    public ChatProtectionListener(ServerFilter plugin, ViolationHandler violationHandler) {
        this.plugin = plugin;
        this.violationHandler = violationHandler;

        // Load anti-spam config
        this.antiSpamEnabled = plugin.getConfig().getBoolean("protection.anti-spam.enabled", true);
        this.messageDelayMs =
                plugin.getConfig().getLong("protection.anti-spam.message-delay-ms", 1000);
        this.duplicateThreshold =
                plugin.getConfig().getInt("protection.anti-spam.duplicate-message-count", 3);
        this.duplicateWindowMs =
                plugin.getConfig().getLong("protection.anti-spam.duplicate-window-seconds", 30)
                        * 1000;

        // Load anti-caps config
        this.antiCapsEnabled = plugin.getConfig().getBoolean("protection.anti-caps.enabled", true);
        this.minCapsLength =
                plugin.getConfig().getInt("protection.anti-caps.min-message-length", 5);
        this.maxCapsPercentage =
                plugin.getConfig().getInt("protection.anti-caps.max-caps-percentage", 70);

        // Load anti-advertising config
        this.antiAdvertisingEnabled =
                plugin.getConfig().getBoolean("protection.anti-advertising.enabled", true);
        this.urlPattern =
                Pattern.compile(
                        "(https?://|www\\.)[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+",
                        Pattern.CASE_INSENSITIVE);
        this.ipPattern = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{1,5})?\\b");
        this.whitelistedDomains =
                new java.util.HashSet<>(
                        plugin.getConfig()
                                .getStringList("protection.anti-advertising.whitelisted-domains"));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChatProtection(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        // Check bypass permission
        if (player.hasPermission("serverfilter.bypass.protection")) {
            return;
        }

        // Anti-spam check
        if (antiSpamEnabled && isSpamming(player, message)) {
            event.setCancelled(true);
            violationHandler.handleSpamViolation(
                    player, "Please wait before sending another message.");
            return;
        }

        // Anti-advertising check
        if (antiAdvertisingEnabled && containsAdvertising(message)) {
            event.setCancelled(true);
            violationHandler.handleAdvertisingViolation(player, message);
            return;
        }

        // Anti-caps check (modifies message instead of blocking)
        if (antiCapsEnabled && exceedsCapsLimit(message)) {
            String lowercased = message.toLowerCase();
            event.message(Component.text(lowercased));
            violationHandler.handleCapsViolation(player);
        }

        // Update tracking data
        updatePlayerMessageData(player, message);
    }

    private boolean isSpamming(Player player, String message) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Check message delay
        Long lastTime = lastMessageTime.get(uuid);
        if (lastTime != null && (now - lastTime) < messageDelayMs) {
            return true;
        }

        // Check for duplicate messages
        String lastMsg = lastMessage.get(uuid);
        if (lastMsg != null && lastMsg.equalsIgnoreCase(message)) {
            int count = duplicateCount.getOrDefault(uuid, 0) + 1;
            if (count >= duplicateThreshold) {
                return true;
            }
        }

        return false;
    }

    private boolean exceedsCapsLimit(String message) {
        if (message.length() < minCapsLength) {
            return false;
        }

        // Count letters only
        int letterCount = 0;
        int upperCount = 0;

        for (char c : message.toCharArray()) {
            if (Character.isLetter(c)) {
                letterCount++;
                if (Character.isUpperCase(c)) {
                    upperCount++;
                }
            }
        }

        if (letterCount == 0) {
            return false;
        }

        int percentage = (upperCount * 100) / letterCount;
        return percentage > maxCapsPercentage;
    }

    private boolean containsAdvertising(String message) {
        // Check for URLs
        var urlMatcher = urlPattern.matcher(message);
        while (urlMatcher.find()) {
            String url = urlMatcher.group().toLowerCase();
            if (!isWhitelistedDomain(url)) {
                return true;
            }
        }

        // Check for IP addresses
        if (plugin.getConfig().getBoolean("protection.anti-advertising.block-ips", true)) {
            if (ipPattern.matcher(message).find()) {
                return true;
            }
        }

        return false;
    }

    private boolean isWhitelistedDomain(String url) {
        for (String domain : whitelistedDomains) {
            if (url.contains(domain.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private void updatePlayerMessageData(Player player, String message) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        String lastMsg = lastMessage.get(uuid);
        if (lastMsg != null && lastMsg.equalsIgnoreCase(message)) {
            duplicateCount.merge(uuid, 1, Integer::sum);
        } else {
            duplicateCount.put(uuid, 0);
        }

        lastMessageTime.put(uuid, now);
        lastMessage.put(uuid, message);
    }

    public void clearPlayerData(UUID uuid) {
        lastMessageTime.remove(uuid);
        lastMessage.remove(uuid);
        duplicateCount.remove(uuid);
    }
}
