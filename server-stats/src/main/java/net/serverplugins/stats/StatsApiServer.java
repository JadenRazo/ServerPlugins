package net.serverplugins.stats;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.serverplugins.api.ServerAPI;
import net.serverplugins.api.database.Database;

/**
 * HTTP server for ServerStats API.
 *
 * <p>Serves a single endpoint at /api/stats that returns JSON with server statistics:
 *
 * <ul>
 *   <li>totalPlayers - total unique players who have joined the server
 *   <li>totalPlaytime - total hours played across all players
 *   <li>blocksPlaced - blocks placed today
 *   <li>mobsKilled - mobs killed today
 *   <li>uniquePlayersToday - unique players who joined today
 *   <li>peakOnlineToday - peak concurrent players today
 * </ul>
 *
 * <p>CORS is configurable per origin. Rate limiting is enforced per IP.
 */
public class StatsApiServer {

    private final ServerStats plugin;
    private final StatsTracker tracker;
    private HttpServer server;

    // Cache
    private volatile String cachedResponse;
    private volatile long cacheTimestamp;

    // Rate limiting
    private final Map<String, long[]> rateLimits = new ConcurrentHashMap<>();
    private static final int MAX_REQUESTS = 30;
    private static final long WINDOW_MS = 60_000;

    public StatsApiServer(ServerStats plugin, StatsTracker tracker) {
        this.plugin = plugin;
        this.tracker = tracker;
    }

    /**
     * Start the HTTP server on configured port.
     *
     * <p>Serves /api/stats endpoint with JSON response.
     */
    public void start() {
        int port = plugin.getStatsConfig().getHttpPort();
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/api/stats", new StatsHandler());
            server.setExecutor(null);
            server.start();
            plugin.getLogger().info("Stats API started on port " + port);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to start Stats API: " + e.getMessage());
        }
    }

    /** Stop the HTTP server. */
    public void stop() {
        if (server != null) {
            server.stop(0);
            plugin.getLogger().info("Stats API stopped");
        }
    }

    /** Handler for /api/stats endpoint. */
    private class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Only allow GET
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            // Rate limit
            String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
            if (!checkRateLimit(clientIp)) {
                sendJson(exchange, 429, "{\"error\":\"Rate limit exceeded\"}");
                return;
            }

            // Check cache
            int cacheTtl = plugin.getStatsConfig().getCacheTtl();
            if (cachedResponse != null
                    && (System.currentTimeMillis() - cacheTimestamp) < cacheTtl * 1000L) {
                sendJson(exchange, 200, cachedResponse);
                return;
            }

            // Build fresh response
            try {
                long totalPlayers = 0;
                long totalPlaytime = 0;

                Database database = ServerAPI.getInstance().getDatabase();
                if (database != null) {
                    try (ResultSet rs =
                            database.executeQuery(
                                    "SELECT COUNT(DISTINCT player_uuid) AS cnt FROM"
                                            + " server_player_data")) {
                        if (rs.next()) {
                            totalPlayers = rs.getLong("cnt");
                        }
                    }
                    try (ResultSet rs =
                            database.executeQuery(
                                    "SELECT COALESCE(SUM(playtime_minutes), 0) / 60 AS hours FROM"
                                            + " server_playtime")) {
                        if (rs.next()) {
                            totalPlaytime = rs.getLong("hours");
                        }
                    }
                }

                String json =
                        "{"
                                + "\"totalPlayers\":"
                                + totalPlayers
                                + ","
                                + "\"totalPlaytime\":"
                                + totalPlaytime
                                + ","
                                + "\"blocksPlaced\":"
                                + tracker.getBlocksPlaced()
                                + ","
                                + "\"mobsKilled\":"
                                + tracker.getMobsKilled()
                                + ","
                                + "\"uniquePlayersToday\":"
                                + tracker.getUniquePlayersToday()
                                + ","
                                + "\"peakOnlineToday\":"
                                + tracker.getPeakOnlineToday()
                                + "}";

                cachedResponse = json;
                cacheTimestamp = System.currentTimeMillis();

                sendJson(exchange, 200, json);
            } catch (Exception e) {
                plugin.getLogger().warning("Stats API error: " + e.getMessage());
                sendJson(exchange, 500, "{\"error\":\"Internal server error\"}");
            }
        }
    }

    /**
     * Send JSON response with CORS headers.
     *
     * @param exchange The HTTP exchange
     * @param status The HTTP status code
     * @param json The JSON response body
     */
    private void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] response = json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json");

        // CORS
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        List<String> allowed = plugin.getStatsConfig().getAllowedOrigins();
        if (origin != null && (allowed.contains("*") || allowed.contains(origin))) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
        }
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

        exchange.sendResponseHeaders(status, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    /**
     * Check rate limit for client IP.
     *
     * <p>Uses a simple token bucket with 30 requests per 60 second window.
     *
     * @param ip The client IP address
     * @return true if request is allowed, false if rate limited
     */
    private boolean checkRateLimit(String ip) {
        long now = System.currentTimeMillis();
        long[] bucket = rateLimits.computeIfAbsent(ip, k -> new long[] {0, now});
        synchronized (bucket) {
            if (now - bucket[1] > WINDOW_MS) {
                bucket[0] = 0;
                bucket[1] = now;
            }
            bucket[0]++;
            return bucket[0] <= MAX_REQUESTS;
        }
    }
}
