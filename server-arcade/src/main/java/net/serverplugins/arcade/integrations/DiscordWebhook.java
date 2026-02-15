package net.serverplugins.arcade.integrations;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import net.serverplugins.arcade.ServerArcade;
import org.bukkit.Bukkit;
import org.json.simple.JSONObject;

/** Discord webhook integration for broadcasting big wins. */
public class DiscordWebhook {

    private final ServerArcade plugin;
    private final String webhookUrl;
    private final boolean enabled;

    // Thresholds for big win announcements
    private final int crashThreshold;
    private final int lotteryThreshold;
    private final int diceThreshold;

    public DiscordWebhook(ServerArcade plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("discord.enabled", false);
        this.webhookUrl = plugin.getConfig().getString("discord.webhook_url", "");
        this.crashThreshold = plugin.getConfig().getInt("discord.big_win_threshold.crash", 10000);
        this.lotteryThreshold = plugin.getConfig().getInt("discord.big_win_threshold.lottery", 0);
        this.diceThreshold = plugin.getConfig().getInt("discord.big_win_threshold.dice", 5000);

        if (enabled && !webhookUrl.isEmpty()) {
            plugin.getLogger().info("Discord webhook integration enabled");
        }
    }

    /** Send a big win notification for crash game. */
    public void sendCrashWin(String playerName, int bet, double multiplier, int payout) {
        if (!shouldAnnounce(payout, crashThreshold)) return;

        JSONObject embed = new JSONObject();
        embed.put("title", "🎰 BIG CRASH WIN!");
        embed.put(
                "description",
                String.format(
                        "**Player:** %s\n**Bet:** $%,d\n**Multiplier:** %.2fx\n**Payout:** **$%,d**",
                        playerName, bet, multiplier, payout));
        embed.put("color", 0xFFD700); // Gold color

        sendWebhook(embed);
    }

    /** Send a big win notification for lottery/jackpot. */
    public void sendLotteryWin(
            String playerName, String gameName, int bet, int payout, int totalPlayers) {
        if (!shouldAnnounce(payout, lotteryThreshold)) return;

        double chance = totalPlayers > 0 ? (double) bet / (payout + bet) * 100 : 0;

        JSONObject embed = new JSONObject();
        embed.put("title", "🎰 JACKPOT WIN!");
        embed.put(
                "description",
                String.format(
                        "**Player:** %s\n**Game:** %s\n**Players:** %d\n**Bet:** $%,d\n**Won:** **$%,d**",
                        playerName, gameName, totalPlayers, bet, payout));
        embed.put("color", 0xFF6B00); // Orange color

        sendWebhook(embed);
    }

    /** Send a big win notification for mega jackpot (always announced). */
    public void sendMegaJackpotWin(
            String playerName, int totalPot, double chance, int totalPlayers) {
        JSONObject embed = new JSONObject();
        embed.put("title", "🏆 MEGA JACKPOT WINNER! 🏆");
        embed.put(
                "description",
                String.format(
                        "**Winner:** %s\n**Total Players:** %d\n**Win Chance:** %.2f%%\n**PRIZE:** **$%,d**",
                        playerName, totalPlayers, chance, totalPot));
        embed.put("color", 0xFF0000); // Red color

        sendWebhook(embed);
    }

    /** Send a big win notification for dice game. */
    public void sendDiceWin(
            String playerName, String betType, int bet, double multiplier, int payout) {
        if (!shouldAnnounce(payout, diceThreshold)) return;

        JSONObject embed = new JSONObject();
        embed.put("title", "🎲 BIG DICE WIN!");
        embed.put(
                "description",
                String.format(
                        "**Player:** %s\n**Bet Type:** %s\n**Bet:** $%,d\n**Multiplier:** %.2fx\n**Payout:** **$%,d**",
                        playerName, betType, bet, multiplier, payout));
        embed.put("color", 0x00FF00); // Green color

        sendWebhook(embed);
    }

    /** Check if win should be announced based on threshold. */
    private boolean shouldAnnounce(int payout, int threshold) {
        return enabled && !webhookUrl.isEmpty() && payout >= threshold;
    }

    /** Send webhook message to Discord asynchronously. */
    @SuppressWarnings("unchecked")
    private void sendWebhook(JSONObject embed) {
        // Run async to avoid blocking main thread
        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            try {
                                URL url = new URL(webhookUrl);
                                HttpURLConnection connection =
                                        (HttpURLConnection) url.openConnection();
                                connection.setRequestMethod("POST");
                                connection.setRequestProperty("Content-Type", "application/json");
                                connection.setRequestProperty("User-Agent", "ServerArcade-Webhook");
                                connection.setDoOutput(true);

                                // Build JSON payload
                                JSONObject payload = new JSONObject();
                                JSONObject embedArray = new JSONObject();
                                embedArray.put("embeds", new JSONObject[] {embed});

                                String jsonPayload = "{\"embeds\": [" + embed.toJSONString() + "]}";

                                // Send request
                                try (OutputStream os = connection.getOutputStream()) {
                                    byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                                    os.write(input, 0, input.length);
                                }

                                // Check response
                                int responseCode = connection.getResponseCode();
                                if (responseCode < 200 || responseCode >= 300) {
                                    plugin.getLogger()
                                            .warning(
                                                    "Discord webhook returned status: "
                                                            + responseCode);
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
