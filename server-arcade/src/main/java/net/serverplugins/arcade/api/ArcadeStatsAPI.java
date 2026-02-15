package net.serverplugins.arcade.api;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.serverplugins.api.database.Database;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.statistics.StatisticsTracker;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * REST API for example.com website to fetch gambling statistics.
 *
 * <p>Endpoints: GET /api/arcade/leaderboard/{type} - Get top 10 leaderboard GET
 * /api/arcade/stats/{uuid} - Get player statistics GET /api/arcade/recent - Get recent big wins
 *
 * <p>Example: http://localhost:8080/api/arcade/leaderboard/net_profit
 */
public class ArcadeStatsAPI {

    private final ServerArcade plugin;
    private final Database database;
    private final StatisticsTracker tracker;
    private HttpServer server;
    private final int port;

    // SECURITY: API key authentication
    private final String apiKey;
    private final boolean requireAuth;

    // SECURITY: Rate limiting (configurable per IP)
    private final Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();
    private final boolean rateLimitingEnabled;
    private final int requestsPerSecond;
    private final int burstSize;

    // SECURITY: Configurable CORS
    private final List<String> allowedOrigins;

    // PERFORMANCE: Cache leaderboards to reduce database load
    // Format: "type:limit" -> JSONArray
    private final Cache<String, String> leaderboardCache;

    public ArcadeStatsAPI(ServerArcade plugin) {
        this.plugin = plugin;
        this.database = plugin.getDatabase();
        this.tracker = plugin.getStatisticsTracker();
        this.port = plugin.getConfig().getInt("api.port", 8080);

        // SECURITY: Load API key from environment variable or config
        String envKey = System.getenv("API_KEY");
        this.apiKey =
                (envKey != null && !envKey.isEmpty())
                        ? envKey
                        : plugin.getConfig().getString("api.key", "");
        this.requireAuth = plugin.getConfig().getBoolean("api.require_auth", true);

        if (envKey == null && !apiKey.isEmpty()) {
            plugin.getLogger()
                    .warning("API key in config.yml - consider using API_KEY environment variable");
        }

        // SECURITY: Load allowed CORS origins
        this.allowedOrigins = plugin.getConfig().getStringList("api.allowed_origins");

        // Warn if wildcard is used
        if (allowedOrigins.contains("*")) {
            plugin.getLogger()
                    .warning(
                            "SECURITY WARNING: API CORS allows all origins (*) - this allows any website to call your API!");
            plugin.getLogger()
                    .warning(
                            "Consider restricting to specific origins in config.yml: api.allowed_origins");
        }

        // SECURITY: Load rate limiting configuration
        this.rateLimitingEnabled = plugin.getConfig().getBoolean("api.rate_limiting.enabled", true);
        this.requestsPerSecond =
                plugin.getConfig().getInt("api.rate_limiting.requests_per_second", 5);
        this.burstSize = plugin.getConfig().getInt("api.rate_limiting.burst_size", 10);

        if (rateLimitingEnabled) {
            plugin.getLogger()
                    .info(
                            String.format(
                                    "API rate limiting enabled: %d requests/second with burst size %d",
                                    requestsPerSecond, burstSize));
        } else {
            plugin.getLogger()
                    .warning("API rate limiting disabled - server may be vulnerable to abuse");
        }

        // PERFORMANCE: Initialize leaderboard cache with configured expiration
        int leaderboardTtlMinutes =
                plugin.getConfig().getInt("performance.cache.leaderboard_ttl_minutes", 5);
        int maxEntries = plugin.getConfig().getInt("performance.cache.max_entries", 10000);
        int cacheSize =
                Math.min(
                        100,
                        maxEntries / 100); // Use 1% of max entries or 100, whichever is smaller

        this.leaderboardCache =
                Caffeine.newBuilder()
                        .expireAfterWrite(
                                leaderboardTtlMinutes, java.util.concurrent.TimeUnit.MINUTES)
                        .maximumSize(cacheSize)
                        .build();

        plugin.getLogger()
                .info(
                        "API leaderboard cache initialized ("
                                + leaderboardTtlMinutes
                                + "min TTL, "
                                + cacheSize
                                + " max entries)");
    }

    /** Start the HTTP API server. */
    public void start() {
        if (database == null) {
            plugin.getLogger().warning("API disabled - no database connection");
            return;
        }

        if (!plugin.getConfig().getBoolean("api.enabled", false)) {
            plugin.getLogger().info("API disabled in config");
            return;
        }

        // SECURITY: Validate CORS configuration before starting
        if (allowedOrigins.isEmpty()) {
            plugin.getLogger().severe("ERROR: API enabled but no CORS origins configured!");
            plugin.getLogger().severe("Add to config.yml:");
            plugin.getLogger().severe("  api:");
            plugin.getLogger().severe("    allowed_origins:");
            plugin.getLogger().severe("      - \"https://example.com\"");
            plugin.getLogger().severe("API server NOT started due to missing CORS configuration");
            return;
        }

        // SECURITY: Validate API key if authentication is required
        if (requireAuth && (apiKey == null || apiKey.isEmpty())) {
            plugin.getLogger()
                    .severe("ERROR: API authentication enabled but no API key configured!");
            plugin.getLogger().severe("Generate a secure key: openssl rand -hex 32");
            plugin.getLogger()
                    .severe("Then set environment variable: export API_KEY=<generated_key>");
            plugin.getLogger().severe("Or add to config.yml: api.key: \"<generated_key>\"");
            plugin.getLogger().severe("API server NOT started due to missing API key");
            return;
        }

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);

            // Register endpoints
            server.createContext("/api/arcade/leaderboard", new LeaderboardHandler());
            server.createContext("/api/arcade/stats", new PlayerStatsHandler());
            server.createContext("/api/arcade/recent", new RecentWinsHandler());

            server.setExecutor(null); // Use default executor
            server.start();

            plugin.getLogger().info("Arcade Stats API started on port " + port);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to start API server: " + e.getMessage());
        }
    }

    /** Stop the HTTP API server. */
    public void stop() {
        if (server != null) {
            server.stop(0);
            plugin.getLogger().info("Arcade Stats API stopped");
        }
    }

    /**
     * Leaderboard endpoint handler. GET /api/arcade/leaderboard/{type}?limit=10
     *
     * <p>Types: net_profit, crash_mult, biggest_win, total_wagered
     */
    private class LeaderboardHandler implements HttpHandler {
        @Override
        @SuppressWarnings("unchecked")
        public void handle(HttpExchange exchange) throws IOException {
            // SECURITY: Check authentication and rate limiting
            if (!checkAuth(exchange)) return;
            if (!checkRateLimit(exchange)) return;

            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");
            String type = parts.length > 4 ? parts[4] : "net_profit";

            // Parse query params
            String query = exchange.getRequestURI().getQuery();
            int limit = 10;
            if (query != null && query.contains("limit=")) {
                try {
                    limit = Integer.parseInt(query.split("limit=")[1].split("&")[0]);
                    limit = Math.min(limit, 100); // Max 100 entries
                } catch (Exception ignored) {
                }
            }

            // PERFORMANCE: Check cache first
            String cacheKey = type + ":" + limit;
            String cachedResult = leaderboardCache.getIfPresent(cacheKey);
            if (cachedResult != null) {
                logRequest(exchange, "SUCCESS (CACHED)");
                sendJSON(exchange, 200, cachedResult);
                return;
            }

            JSONArray leaderboard = new JSONArray();

            try {
                String sql = getLeaderboardSQL(type, limit);
                try (ResultSet rs = database.executeQuery(sql)) {

                    int rank = 1;
                    while (rs.next()) {
                        JSONObject entry = new JSONObject();
                        entry.put("rank", rank++);
                        entry.put("player_name", rs.getString("player_name"));
                        entry.put("player_uuid", rs.getString("player_uuid"));

                        switch (type) {
                            case "net_profit":
                                entry.put("value", rs.getLong("net_profit"));
                                entry.put("label", "$" + formatNumber(rs.getLong("net_profit")));
                                break;
                            case "crash_mult":
                                entry.put("value", rs.getDouble("crash_highest_mult"));
                                entry.put(
                                        "label",
                                        String.format("%.2fx", rs.getDouble("crash_highest_mult")));
                                break;
                            case "biggest_win":
                                int biggestWin =
                                        Math.max(
                                                rs.getInt("crash_biggest_win"),
                                                Math.max(
                                                        rs.getInt("lottery_biggest_win"),
                                                        rs.getInt("dice_biggest_win")));
                                entry.put("value", biggestWin);
                                entry.put("label", "$" + formatNumber(biggestWin));
                                break;
                            case "total_wagered":
                                entry.put("value", rs.getLong("total_wagered"));
                                entry.put("label", "$" + formatNumber(rs.getLong("total_wagered")));
                                break;
                        }

                        leaderboard.add(entry);
                    }
                } // close ResultSet

                // PERFORMANCE: Cache the result
                String result = leaderboard.toJSONString();
                leaderboardCache.put(cacheKey, result);

                logRequest(exchange, "SUCCESS");
                sendJSON(exchange, 200, result);

            } catch (Exception e) {
                plugin.getLogger().warning("API error: " + e.getMessage());
                sendJSON(exchange, 500, "{\"error\":\"Internal server error\"}");
            }
        }
    }

    /** Player stats endpoint handler. GET /api/arcade/stats/{uuid} */
    private class PlayerStatsHandler implements HttpHandler {
        @Override
        @SuppressWarnings("unchecked")
        public void handle(HttpExchange exchange) throws IOException {
            // SECURITY: Check authentication and rate limiting
            if (!checkAuth(exchange)) return;
            if (!checkRateLimit(exchange)) return;

            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");
            if (parts.length < 5) {
                sendJSON(exchange, 400, "{\"error\":\"Missing UUID\"}");
                return;
            }

            String uuidStr = parts[4];
            UUID playerId;
            try {
                playerId = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                sendJSON(exchange, 400, "{\"error\":\"Invalid UUID\"}");
                return;
            }

            StatisticsTracker.PlayerStats stats = tracker.getPlayerStats(playerId);
            if (stats == null) {
                sendJSON(exchange, 404, "{\"error\":\"Player not found\"}");
                return;
            }

            JSONObject json = new JSONObject();
            json.put("player_name", stats.playerName);
            json.put("net_profit", stats.netProfit);
            json.put("total_wagered", stats.totalWagered);
            json.put("total_won", stats.totalWon);
            json.put("total_lost", stats.totalLost);
            json.put("win_rate", String.format("%.1f%%", stats.getWinRate()));

            JSONObject crash = new JSONObject();
            crash.put("total_bets", stats.crashTotalBets);
            crash.put("total_wins", stats.crashTotalWins);
            crash.put("biggest_win", stats.crashBiggestWin);
            crash.put("highest_mult", stats.crashHighestMult);
            crash.put("win_rate", String.format("%.1f%%", stats.getCrashWinRate()));
            json.put("crash", crash);

            JSONObject lottery = new JSONObject();
            lottery.put("total_bets", stats.lotteryTotalBets);
            lottery.put("total_wins", stats.lotteryTotalWins);
            lottery.put("biggest_win", stats.lotteryBiggestWin);
            json.put("lottery", lottery);

            JSONObject dice = new JSONObject();
            dice.put("total_bets", stats.diceTotalBets);
            dice.put("total_wins", stats.diceTotalWins);
            dice.put("biggest_win", stats.diceBiggestWin);
            dice.put("win_rate", String.format("%.1f%%", stats.getDiceWinRate()));
            json.put("dice", dice);

            JSONObject streaks = new JSONObject();
            streaks.put("current", stats.currentStreak);
            streaks.put("best_win", stats.bestWinStreak);
            streaks.put("worst_loss", stats.worstLossStreak);
            json.put("streaks", streaks);

            logRequest(exchange, "SUCCESS");
            sendJSON(exchange, 200, json.toJSONString());
        }
    }

    /** Recent big wins endpoint handler. GET /api/arcade/recent?limit=20 */
    private class RecentWinsHandler implements HttpHandler {
        @Override
        @SuppressWarnings("unchecked")
        public void handle(HttpExchange exchange) throws IOException {
            // SECURITY: Check authentication and rate limiting
            if (!checkAuth(exchange)) return;
            if (!checkRateLimit(exchange)) return;

            String query = exchange.getRequestURI().getQuery();
            int limit = 20;
            if (query != null && query.contains("limit=")) {
                try {
                    limit = Integer.parseInt(query.split("limit=")[1].split("&")[0]);
                    limit = Math.min(limit, 100);
                } catch (Exception ignored) {
                }
            }

            JSONArray recent = new JSONArray();

            try (ResultSet rs =
                    database.executeQuery(
                            "SELECT h.*, s.player_name FROM server_arcade_history h "
                                    + "LEFT JOIN server_arcade_stats s ON h.player_uuid = s.player_uuid "
                                    + "WHERE h.won = TRUE AND h.payout > 10000 "
                                    + "ORDER BY h.timestamp DESC LIMIT "
                                    + limit)) {

                while (rs.next()) {
                    JSONObject entry = new JSONObject();
                    entry.put("player_name", rs.getString("player_name"));
                    entry.put("game_type", rs.getString("game_type"));
                    entry.put("bet", rs.getInt("bet_amount"));
                    entry.put("payout", rs.getInt("payout"));
                    entry.put("multiplier", rs.getDouble("multiplier"));
                    entry.put("timestamp", rs.getLong("timestamp"));

                    recent.add(entry);
                }

                logRequest(exchange, "SUCCESS");
                sendJSON(exchange, 200, recent.toJSONString());

            } catch (Exception e) {
                plugin.getLogger().warning("API error: " + e.getMessage());
                sendJSON(exchange, 500, "{\"error\":\"Internal server error\"}");
            }
        }
    }

    /** Get SQL for leaderboard type. */
    private String getLeaderboardSQL(String type, int limit) {
        String orderBy =
                switch (type) {
                    case "crash_mult" -> "crash_highest_mult DESC";
                    case "biggest_win" ->
                            "GREATEST(crash_biggest_win, lottery_biggest_win, dice_biggest_win) DESC";
                    case "total_wagered" -> "total_wagered DESC";
                    default -> "net_profit DESC";
                };

        return "SELECT * FROM server_arcade_stats ORDER BY " + orderBy + " LIMIT " + limit;
    }

    /** SECURITY: Check API key authentication. */
    private boolean checkAuth(HttpExchange exchange) throws IOException {
        if (!requireAuth) return true;

        String providedKey = exchange.getRequestHeaders().getFirst("X-API-Key");
        if (providedKey == null || !providedKey.equals(apiKey)) {
            sendJSON(exchange, 401, "{\"error\":\"Unauthorized - Invalid or missing API key\"}");
            logRequest(exchange, "UNAUTHORIZED");
            return false;
        }
        return true;
    }

    /** SECURITY: Check rate limit (configurable requests per second per IP). */
    private boolean checkRateLimit(HttpExchange exchange) throws IOException {
        // Skip rate limiting if disabled
        if (!rateLimitingEnabled) {
            return true;
        }

        String clientIP = exchange.getRemoteAddress().getAddress().getHostAddress();
        RateLimiter limiter =
                rateLimiters.computeIfAbsent(
                        clientIP, k -> new RateLimiter(burstSize, 1000 / requestsPerSecond));

        if (!limiter.tryAcquire()) {
            String errorMsg =
                    String.format(
                            "{\"error\":\"Rate limit exceeded - Maximum %d requests per second\"}",
                            requestsPerSecond);
            sendJSON(exchange, 429, errorMsg);
            logRequest(exchange, "RATE_LIMITED");
            return false;
        }
        return true;
    }

    /** SECURITY: Log API requests for audit trail. */
    private void logRequest(HttpExchange exchange, String status) {
        String clientIP = exchange.getRemoteAddress().getAddress().getHostAddress();
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        plugin.getLogger()
                .info(String.format("[API] %s %s %s - %s", clientIP, method, path, status));
    }

    /** Send JSON response. */
    private void sendJSON(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] response = json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json");

        // SECURITY: Configurable CORS instead of wildcard
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null && (allowedOrigins.contains("*") || allowedOrigins.contains(origin))) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
        } else if (allowedOrigins.contains("*")) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        }

        exchange.sendResponseHeaders(statusCode, response.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    /**
     * Simple token bucket rate limiter.
     *
     * @param maxTokens The burst size (bucket capacity)
     * @param refillIntervalMs How often tokens refill (milliseconds)
     */
    private static class RateLimiter {
        private final int maxTokens;
        private final long refillIntervalMs;
        private int tokens;
        private long lastRefill;

        public RateLimiter(int maxTokens, long refillIntervalMs) {
            this.maxTokens = maxTokens;
            this.refillIntervalMs = refillIntervalMs;
            this.tokens = maxTokens;
            this.lastRefill = System.currentTimeMillis();
        }

        public synchronized boolean tryAcquire() {
            refill();
            if (tokens > 0) {
                tokens--;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefill;
            if (elapsed >= refillIntervalMs) {
                tokens = maxTokens;
                lastRefill = now;
            }
        }
    }

    /** Format large numbers with K/M/B suffixes. */
    private String formatNumber(long num) {
        if (num >= 1_000_000_000) return String.format("%.1fB", num / 1_000_000_000.0);
        if (num >= 1_000_000) return String.format("%.1fM", num / 1_000_000.0);
        if (num >= 1_000) return String.format("%.1fK", num / 1_000.0);
        return String.valueOf(num);
    }
}
