package net.serverplugins.arcade.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import net.serverplugins.api.database.Database;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.statistics.StatisticsTracker;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Test suite for ArcadeStatsAPI class. Tests authentication, rate limiting, CORS, and API
 * functionality.
 *
 * <p>Note: This test does not actually start the HTTP server to avoid port conflicts. It tests the
 * configuration and validation logic.
 */
@ExtendWith(MockitoExtension.class)
class ArcadeStatsAPITest {

    @Mock(lenient = true)
    private ServerArcade plugin;

    @Mock(lenient = true)
    private Database database;

    @Mock(lenient = true)
    private StatisticsTracker tracker;

    @Mock(lenient = true)
    private FileConfiguration config;

    @Mock(lenient = true)
    private Logger logger;

    @BeforeEach
    void setUp() {
        when(plugin.getDatabase()).thenReturn(database);
        when(plugin.getStatisticsTracker()).thenReturn(tracker);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(logger);

        // Set up lenient default config values for all tests
        lenient().when(config.getBoolean("api.rate_limiting.enabled", true)).thenReturn(true);
        lenient().when(config.getInt("api.rate_limiting.requests_per_second", 5)).thenReturn(5);
        lenient().when(config.getInt("api.rate_limiting.burst_size", 10)).thenReturn(10);
    }

    @Test
    void testAPIInitializationWithValidConfig() {
        // Setup valid configuration
        when(config.getInt("api.port", 8080)).thenReturn(8080);
        when(config.getString("api.key", "")).thenReturn("test-api-key-12345");
        when(config.getBoolean("api.require_auth", true)).thenReturn(true);
        when(config.getStringList("api.allowed_origins"))
                .thenReturn(Arrays.asList("https://example.com"));
        when(config.getBoolean("api.enabled", false)).thenReturn(false);

        ArcadeStatsAPI api = new ArcadeStatsAPI(plugin);

        assertNotNull(api, "API should be initialized");
        verify(logger, never()).severe(anyString());
    }

    @Test
    void testAPIInitializationWithInvalidKey() {
        // Setup configuration with auth required but no key
        when(config.getInt("api.port", 8080)).thenReturn(8080);
        when(config.getString("api.key", "")).thenReturn("");
        when(config.getBoolean("api.require_auth", true)).thenReturn(true);
        when(config.getStringList("api.allowed_origins")).thenReturn(Arrays.asList("*"));
        when(config.getBoolean("api.enabled", false)).thenReturn(false);

        ArcadeStatsAPI api = new ArcadeStatsAPI(plugin);

        // Should log severe error about missing API key
        verify(logger, atLeastOnce())
                .severe(
                        argThat(
                                (String msg) ->
                                        msg != null
                                                && msg.contains(
                                                        "API authentication enabled but no API key configured")));
    }

    @Test
    void testAPIDisabledByDefault() {
        when(config.getInt("api.port", 8080)).thenReturn(8080);
        when(config.getString("api.key", "")).thenReturn("test-key");
        when(config.getBoolean("api.require_auth", true)).thenReturn(true);
        when(config.getStringList("api.allowed_origins")).thenReturn(Arrays.asList("*"));
        when(config.getBoolean("api.enabled", false)).thenReturn(false);

        ArcadeStatsAPI api = new ArcadeStatsAPI(plugin);
        api.start();

        // Should log that API is disabled
        verify(logger, atLeastOnce())
                .info(argThat((String msg) -> msg != null && msg.contains("API disabled")));
    }

    @Test
    void testAPIWithNoDatabaseConnection() {
        when(plugin.getDatabase()).thenReturn(null);
        when(config.getInt("api.port", 8080)).thenReturn(8080);
        when(config.getString("api.key", "")).thenReturn("test-key");
        when(config.getBoolean("api.require_auth", true)).thenReturn(true);
        when(config.getStringList("api.allowed_origins")).thenReturn(Arrays.asList("*"));
        when(config.getBoolean("api.enabled", false)).thenReturn(true);

        ArcadeStatsAPI api = new ArcadeStatsAPI(plugin);
        api.start();

        // Should log warning about no database
        verify(logger, atLeastOnce())
                .warning(
                        argThat(
                                (String msg) ->
                                        msg != null
                                                && msg.contains(
                                                        "API disabled - no database connection")));
    }

    @Test
    void testAPIWithEmptyAllowedOrigins() {
        when(config.getInt("api.port", 8080)).thenReturn(8080);
        when(config.getString("api.key", "")).thenReturn("test-key");
        when(config.getBoolean("api.require_auth", true)).thenReturn(true);
        when(config.getStringList("api.allowed_origins")).thenReturn(Arrays.asList());
        when(config.getBoolean("api.enabled", false)).thenReturn(false);

        ArcadeStatsAPI api = new ArcadeStatsAPI(plugin);

        // Should log warning about allowing all origins
        verify(logger, atLeastOnce())
                .warning(
                        argThat(
                                (String msg) ->
                                        msg != null && msg.contains("API CORS not configured")));
    }

    @Test
    void testAPIWithConfigKeyWarning() {
        // API key in config (not environment variable) should trigger warning
        when(config.getInt("api.port", 8080)).thenReturn(8080);
        when(config.getString("api.key", "")).thenReturn("test-key-in-config");
        when(config.getBoolean("api.require_auth", true)).thenReturn(true);
        when(config.getStringList("api.allowed_origins")).thenReturn(Arrays.asList("*"));
        when(config.getBoolean("api.enabled", false)).thenReturn(false);

        // Clear environment variable
        assertNull(System.getenv("API_KEY"), "API_KEY env var should not be set for this test");

        ArcadeStatsAPI api = new ArcadeStatsAPI(plugin);

        // Should log warning about using config instead of env var
        verify(logger, atLeastOnce())
                .warning(
                        argThat(
                                (String msg) ->
                                        msg != null
                                                && msg.contains(
                                                        "API key in config.yml - consider using API_KEY environment variable")));
    }

    @Test
    void testAPIWithMultipleAllowedOrigins() {
        List<String> origins =
                Arrays.asList(
                        "https://example.com",
                        "https://www.example.com",
                        "http://localhost:3000");

        when(config.getInt("api.port", 8080)).thenReturn(8080);
        when(config.getString("api.key", "")).thenReturn("test-key");
        when(config.getBoolean("api.require_auth", true)).thenReturn(true);
        when(config.getStringList("api.allowed_origins")).thenReturn(origins);
        when(config.getBoolean("api.enabled", false)).thenReturn(false);

        ArcadeStatsAPI api = new ArcadeStatsAPI(plugin);

        assertNotNull(api, "API should be initialized with multiple origins");
        verify(logger, never())
                .warning(
                        argThat(
                                (String msg) ->
                                        msg != null && msg.contains("API CORS not configured")));
    }

    @Test
    void testAPIWithCustomPort() {
        when(config.getInt("api.port", 8080)).thenReturn(9090);
        when(config.getString("api.key", "")).thenReturn("test-key");
        when(config.getBoolean("api.require_auth", true)).thenReturn(true);
        when(config.getStringList("api.allowed_origins")).thenReturn(Arrays.asList("*"));
        when(config.getBoolean("api.enabled", false)).thenReturn(false);

        ArcadeStatsAPI api = new ArcadeStatsAPI(plugin);

        assertNotNull(api, "API should be initialized with custom port");
    }

    @Test
    void testAPIWithAuthDisabled() {
        when(config.getInt("api.port", 8080)).thenReturn(8080);
        when(config.getString("api.key", "")).thenReturn("");
        when(config.getBoolean("api.require_auth", true)).thenReturn(false);
        when(config.getStringList("api.allowed_origins")).thenReturn(Arrays.asList("*"));
        when(config.getBoolean("api.enabled", false)).thenReturn(false);

        ArcadeStatsAPI api = new ArcadeStatsAPI(plugin);

        assertNotNull(api, "API should be initialized with auth disabled");
        verify(logger, never()).severe(anyString());
    }

    @Test
    void testAPIStopWithoutStart() {
        when(config.getInt("api.port", 8080)).thenReturn(8080);
        when(config.getString("api.key", "")).thenReturn("test-key");
        when(config.getBoolean("api.require_auth", true)).thenReturn(true);
        when(config.getStringList("api.allowed_origins")).thenReturn(Arrays.asList("*"));
        when(config.getBoolean("api.enabled", false)).thenReturn(false);

        ArcadeStatsAPI api = new ArcadeStatsAPI(plugin);

        // Should not throw exception when stopping without starting
        assertDoesNotThrow(() -> api.stop(), "Stopping unstarted API should not throw exception");
    }

    @Test
    void testAPIWithZeroPortShouldUseDefault() {
        when(config.getInt("api.port", 8080)).thenReturn(0);
        when(config.getString("api.key", "")).thenReturn("test-key");
        when(config.getBoolean("api.require_auth", true)).thenReturn(true);
        when(config.getStringList("api.allowed_origins")).thenReturn(Arrays.asList("*"));
        when(config.getBoolean("api.enabled", false)).thenReturn(false);

        // Should not throw exception with invalid port
        assertDoesNotThrow(() -> new ArcadeStatsAPI(plugin), "Should handle port 0 gracefully");
    }

    @Test
    void testAPIConfigurationConsistency() {
        // Test that multiple initializations with same config produce consistent state
        when(config.getInt("api.port", 8080)).thenReturn(8080);
        when(config.getString("api.key", "")).thenReturn("test-key");
        when(config.getBoolean("api.require_auth", true)).thenReturn(true);
        when(config.getStringList("api.allowed_origins"))
                .thenReturn(Arrays.asList("https://example.com"));
        when(config.getBoolean("api.enabled", false)).thenReturn(false);

        ArcadeStatsAPI api1 = new ArcadeStatsAPI(plugin);
        ArcadeStatsAPI api2 = new ArcadeStatsAPI(plugin);

        assertNotNull(api1, "First API instance should be initialized");
        assertNotNull(api2, "Second API instance should be initialized");
        assertNotSame(api1, api2, "Should create separate instances");
    }

    @Test
    void testAPIWithWildcardOrigin() {
        when(config.getInt("api.port", 8080)).thenReturn(8080);
        when(config.getString("api.key", "")).thenReturn("test-key");
        when(config.getBoolean("api.require_auth", true)).thenReturn(true);
        when(config.getStringList("api.allowed_origins")).thenReturn(Arrays.asList("*"));
        when(config.getBoolean("api.enabled", false)).thenReturn(false);

        ArcadeStatsAPI api = new ArcadeStatsAPI(plugin);

        assertNotNull(api, "API should accept wildcard origin");
        verify(logger, atLeastOnce())
                .warning(
                        argThat(
                                (String msg) ->
                                        msg != null && msg.contains("allowing all origins")));
    }

    @Test
    void testAPIInitializationLogging() {
        when(config.getInt("api.port", 8080)).thenReturn(8080);
        when(config.getString("api.key", "")).thenReturn("test-key");
        when(config.getBoolean("api.require_auth", true)).thenReturn(true);
        when(config.getStringList("api.allowed_origins"))
                .thenReturn(Arrays.asList("https://example.com"));
        when(config.getBoolean("api.enabled", false)).thenReturn(false);

        ArcadeStatsAPI api = new ArcadeStatsAPI(plugin);

        // Should log successful cache initialization
        verify(logger, atLeastOnce())
                .info(
                        argThat(
                                (String msg) ->
                                        msg != null
                                                && msg.contains(
                                                        "API leaderboard cache initialized")));
    }
}
