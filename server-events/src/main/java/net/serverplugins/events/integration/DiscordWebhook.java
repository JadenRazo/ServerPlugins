package net.serverplugins.events.integration;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import net.serverplugins.events.EventsConfig;
import net.serverplugins.events.ServerEvents;
import net.serverplugins.events.events.ServerEvent;
import org.bukkit.entity.Player;

/** Discord webhook integration for event announcements. */
public class DiscordWebhook {

    private final ServerEvents plugin;
    private final EventsConfig config;

    public DiscordWebhook(ServerEvents plugin) {
        this.plugin = plugin;
        this.config = plugin.getEventsConfig();
    }

    /** Send event start announcement to Discord. */
    public void sendEventStart(ServerEvent event) {
        // Publish via Redis (for Discord bot)
        plugin.publishEventAnnouncement(
                event.getDisplayName(),
                "START",
                event.getDisplayName() + " has started! Join the server to participate!");

        // Also send via webhook if configured
        if (!config.isDiscordEnabled() || !config.isDiscordAnnounceStart()) return;

        String webhookUrl = config.getDiscordWebhookUrl();
        if (webhookUrl == null || webhookUrl.isEmpty()) return;

        String json =
                buildEmbed(
                        "\uD83C\uDF89 Event Started!",
                        event.getDisplayName() + " has started!",
                        "Join the server to participate!",
                        0xFFD700 // Gold color
                        );

        sendAsync(webhookUrl, json);
    }

    /** Send event winner announcement to Discord. */
    public void sendEventWinner(ServerEvent event, Player winner, int coinsWon) {
        // Publish via Redis (for Discord bot)
        plugin.publishEventAnnouncement(
                event.getDisplayName(),
                "WINNER",
                winner.getName() + " won " + event.getDisplayName() + "! Prize: $" + coinsWon);

        // Also send via webhook if configured
        if (!config.isDiscordEnabled() || !config.isDiscordAnnounceWinner()) return;

        String webhookUrl = config.getDiscordWebhookUrl();
        if (webhookUrl == null || webhookUrl.isEmpty()) return;

        String json =
                buildEmbed(
                        "\uD83C\uDFC6 Event Winner!",
                        "**" + winner.getName() + "** won " + event.getDisplayName() + "!",
                        "Prize: $" + coinsWon,
                        0x00FF00 // Green color
                        );

        sendAsync(webhookUrl, json);
    }

    /** Send event timeout announcement to Discord. */
    public void sendEventTimeout(ServerEvent event) {
        // Publish via Redis (for Discord bot)
        plugin.publishEventAnnouncement(
                event.getDisplayName(),
                "TIMEOUT",
                event.getDisplayName() + " ended with no winner. Better luck next time!");

        // Also send via webhook if configured
        if (!config.isDiscordEnabled() || !config.isDiscordAnnounceWinner()) return;

        String webhookUrl = config.getDiscordWebhookUrl();
        if (webhookUrl == null || webhookUrl.isEmpty()) return;

        String json =
                buildEmbed(
                        "\u23F0 Event Ended",
                        event.getDisplayName() + " ended with no winner.",
                        "Better luck next time!",
                        0xFF6600 // Orange color
                        );

        sendAsync(webhookUrl, json);
    }

    private String buildEmbed(String title, String description, String footer, int color) {
        return String.format(
                """
            {
              "embeds": [{
                "title": "%s",
                "description": "%s",
                "color": %d,
                "footer": {"text": "%s"},
                "timestamp": "%s"
              }]
            }
            """,
                escapeJson(title),
                escapeJson(description),
                color,
                escapeJson(footer),
                java.time.Instant.now().toString());
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private void sendAsync(String webhookUrl, String json) {
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            try {
                                URL url = new URL(webhookUrl);
                                HttpURLConnection connection =
                                        (HttpURLConnection) url.openConnection();
                                connection.setRequestMethod("POST");
                                connection.setRequestProperty("Content-Type", "application/json");
                                connection.setRequestProperty("User-Agent", "ServerEvents/1.0");
                                connection.setDoOutput(true);
                                connection.setConnectTimeout(5000);
                                connection.setReadTimeout(5000);

                                try (OutputStream os = connection.getOutputStream()) {
                                    byte[] input = json.getBytes(StandardCharsets.UTF_8);
                                    os.write(input, 0, input.length);
                                }

                                int responseCode = connection.getResponseCode();
                                if (responseCode < 200 || responseCode >= 300) {
                                    plugin.getLogger()
                                            .warning("Discord webhook returned: " + responseCode);
                                }

                                connection.disconnect();
                            } catch (Exception e) {
                                plugin.getLogger()
                                        .warning(
                                                "Failed to send Discord webhook: "
                                                        + e.getMessage());
                            }
                        });
    }
}
