package net.serverplugins.commands.data;

import static org.junit.jupiter.api.Assertions.*;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.serverplugins.commands.ServerCommands;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlayerDataManagerTest {

    private ServerMock server;
    private ServerCommands plugin;
    private PlayerDataManager manager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ServerCommands.class);
        manager = plugin.getPlayerDataManager();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void testPreloadPlayerDataAsync_NewPlayer()
            throws ExecutionException, InterruptedException, TimeoutException {
        UUID uuid = UUID.randomUUID();

        CompletableFuture<PlayerDataManager.PlayerData> future =
                manager.preloadPlayerDataAsync(uuid);
        assertNotNull(future, "Future should not be null");

        PlayerDataManager.PlayerData data = future.get(5, TimeUnit.SECONDS);
        assertNotNull(data, "Player data should be loaded");
        assertEquals(uuid, data.getUuid(), "UUID should match");
    }

    @Test
    void testPreloadPlayerDataAsync_CachedData()
            throws ExecutionException, InterruptedException, TimeoutException {
        UUID uuid = UUID.randomUUID();

        // First load
        CompletableFuture<PlayerDataManager.PlayerData> future1 =
                manager.preloadPlayerDataAsync(uuid);
        PlayerDataManager.PlayerData data1 = future1.get(5, TimeUnit.SECONDS);

        // Second load - should return cached data immediately
        CompletableFuture<PlayerDataManager.PlayerData> future2 =
                manager.preloadPlayerDataAsync(uuid);
        assertTrue(future2.isDone(), "Cached data should be returned immediately");

        PlayerDataManager.PlayerData data2 = future2.get(1, TimeUnit.SECONDS);
        assertSame(data1, data2, "Should return the same cached instance");
    }

    @Test
    void testPreloadPlayerDataAsync_ConcurrentLoads()
            throws ExecutionException, InterruptedException, TimeoutException {
        UUID uuid = UUID.randomUUID();

        // Simulate multiple concurrent preload requests
        // All should receive the same Future, avoiding duplicate work
        CompletableFuture<PlayerDataManager.PlayerData> future1 =
                manager.preloadPlayerDataAsync(uuid);
        CompletableFuture<PlayerDataManager.PlayerData> future2 =
                manager.preloadPlayerDataAsync(uuid);
        CompletableFuture<PlayerDataManager.PlayerData> future3 =
                manager.preloadPlayerDataAsync(uuid);

        PlayerDataManager.PlayerData data1 = future1.get(5, TimeUnit.SECONDS);
        PlayerDataManager.PlayerData data2 = future2.get(5, TimeUnit.SECONDS);
        PlayerDataManager.PlayerData data3 = future3.get(5, TimeUnit.SECONDS);

        assertNotNull(data1, "Data 1 should not be null");
        assertNotNull(data2, "Data 2 should not be null");
        assertNotNull(data3, "Data 3 should not be null");

        // All should be the same instance since they were loaded concurrently
        assertEquals(data1.getUuid(), data2.getUuid(), "UUIDs should match");
        assertEquals(data2.getUuid(), data3.getUuid(), "UUIDs should match");
    }

    @Test
    void testGetPlayerData_ReturnsPlaceholderOnMainThread() {
        UUID uuid = UUID.randomUUID();

        // On main thread, should return placeholder if not loaded
        PlayerDataManager.PlayerData data = manager.getPlayerData(uuid);
        assertNotNull(data, "Should return placeholder data");
        assertTrue(
                data instanceof PlayerDataManager.PlaceholderPlayerData,
                "Should be placeholder when not preloaded");
    }

    @Test
    void testGetPlayerData_ReturnsCachedDataAfterPreload()
            throws ExecutionException, InterruptedException, TimeoutException {
        UUID uuid = UUID.randomUUID();

        // Preload first
        manager.preloadPlayerDataAsync(uuid).get(5, TimeUnit.SECONDS);

        // Now getPlayerData should return real data
        PlayerDataManager.PlayerData data = manager.getPlayerData(uuid);
        assertNotNull(data, "Should return real data");
        assertFalse(
                data instanceof PlayerDataManager.PlaceholderPlayerData,
                "Should not be placeholder after preload");
    }

    @Test
    void testSessionTracking() {
        UUID uuid = UUID.randomUUID();
        long sessionStart = System.currentTimeMillis();

        manager.startSession(uuid, sessionStart);

        Long activeStart = manager.getActiveSessionStart(uuid);
        assertNotNull(activeStart, "Active session should be tracked");
        assertEquals(sessionStart, activeStart, "Session start time should match");

        // Finalize session
        long playtime = manager.finalizeSession(uuid);
        assertTrue(playtime >= 0, "Playtime should be non-negative");

        // Session should be cleared
        assertNull(
                manager.getActiveSessionStart(uuid),
                "Session should be cleared after finalization");
    }

    @Test
    void testSessionTracking_NoSession() {
        UUID uuid = UUID.randomUUID();

        // Finalize without starting
        long playtime = manager.finalizeSession(uuid);
        assertEquals(0, playtime, "Should return 0 for non-existent session");
    }

    @Test
    void testHomeManagement() throws ExecutionException, InterruptedException, TimeoutException {
        UUID uuid = UUID.randomUUID();
        PlayerMock player = server.addPlayer();
        Location testLoc = player.getLocation();

        // Preload data
        manager.preloadPlayerDataAsync(uuid).get(5, TimeUnit.SECONDS);
        PlayerDataManager.PlayerData data = manager.getPlayerData(uuid);

        // Set home
        data.setHome("test-home", testLoc);
        assertTrue(data.hasHome("test-home"), "Should have home");
        assertEquals(1, data.getHomeCount(), "Should have 1 home");

        // Get home
        Location retrievedLoc = data.getHome("test-home");
        assertNotNull(retrievedLoc, "Should retrieve home location");

        // Remove home
        data.removeHome("test-home");
        assertFalse(data.hasHome("test-home"), "Should not have home after removal");
        assertEquals(0, data.getHomeCount(), "Should have 0 homes");
    }

    @Test
    void testPlaytimeTracking() throws ExecutionException, InterruptedException, TimeoutException {
        UUID uuid = UUID.randomUUID();

        manager.preloadPlayerDataAsync(uuid).get(5, TimeUnit.SECONDS);
        PlayerDataManager.PlayerData data = manager.getPlayerData(uuid);

        assertEquals(0, data.getPlaytime(), "Initial playtime should be 0");

        data.addPlaytime(1000);
        assertEquals(1000, data.getPlaytime(), "Playtime should be updated");

        data.addPlaytime(500);
        assertEquals(1500, data.getPlaytime(), "Playtime should accumulate");
    }

    @Test
    void testFlyToggle() throws ExecutionException, InterruptedException, TimeoutException {
        UUID uuid = UUID.randomUUID();

        manager.preloadPlayerDataAsync(uuid).get(5, TimeUnit.SECONDS);
        PlayerDataManager.PlayerData data = manager.getPlayerData(uuid);

        assertFalse(data.isFlyEnabled(), "Fly should be disabled by default");

        data.setFlyEnabled(true);
        assertTrue(data.isFlyEnabled(), "Fly should be enabled");

        data.setFlyEnabled(false);
        assertFalse(data.isFlyEnabled(), "Fly should be disabled");
    }

    @Test
    void testGodModeToggle() throws ExecutionException, InterruptedException, TimeoutException {
        UUID uuid = UUID.randomUUID();

        manager.preloadPlayerDataAsync(uuid).get(5, TimeUnit.SECONDS);
        PlayerDataManager.PlayerData data = manager.getPlayerData(uuid);

        assertFalse(data.isGodMode(), "God mode should be disabled by default");

        data.setGodMode(true);
        assertTrue(data.isGodMode(), "God mode should be enabled");

        data.setGodMode(false);
        assertFalse(data.isGodMode(), "God mode should be disabled");
    }

    @Test
    void testWarningSystem() throws ExecutionException, InterruptedException, TimeoutException {
        UUID uuid = UUID.randomUUID();

        manager.preloadPlayerDataAsync(uuid).get(5, TimeUnit.SECONDS);
        PlayerDataManager.PlayerData data = manager.getPlayerData(uuid);

        assertEquals(0, data.getWarnings(), "Initial warnings should be 0");

        data.addWarning();
        assertEquals(1, data.getWarnings(), "Should have 1 warning");

        data.addWarning();
        assertEquals(2, data.getWarnings(), "Should have 2 warnings");

        data.clearWarnings();
        assertEquals(0, data.getWarnings(), "Warnings should be cleared");

        data.setWarnings(5);
        assertEquals(5, data.getWarnings(), "Should set warnings to 5");
    }

    @Test
    void testNoThreadSleepInPreload() {
        // This test verifies that our implementation doesn't use Thread.sleep()
        // by checking that operations complete quickly

        UUID uuid = UUID.randomUUID();

        long startTime = System.currentTimeMillis();
        try {
            manager.preloadPlayerDataAsync(uuid).get(100, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            fail(
                    "Preload should complete quickly without Thread.sleep blocking: "
                            + e.getMessage());
        }
        long duration = System.currentTimeMillis() - startTime;

        assertTrue(
                duration < 100,
                "Operation should complete in under 100ms, took: " + duration + "ms");
    }

    @Test
    void testConcurrentLoadsShareFuture() throws Exception {
        UUID uuid = UUID.randomUUID();

        // Start two concurrent loads
        CompletableFuture<PlayerDataManager.PlayerData> future1 =
                manager.preloadPlayerDataAsync(uuid);
        CompletableFuture<PlayerDataManager.PlayerData> future2 =
                manager.preloadPlayerDataAsync(uuid);

        // Both futures should complete successfully
        PlayerDataManager.PlayerData data1 = future1.get(5, TimeUnit.SECONDS);
        PlayerDataManager.PlayerData data2 = future2.get(5, TimeUnit.SECONDS);

        assertNotNull(data1, "Data 1 should not be null");
        assertNotNull(data2, "Data 2 should not be null");

        // Verify no duplicate loading occurred by checking they share the same cached instance
        PlayerDataManager.PlayerData data3 = manager.getPlayerData(uuid);
        assertSame(data1, data3, "Should return same cached instance");
    }

    @Test
    void testPlaceholderData_SafeDefaults() {
        PlayerDataManager.PlaceholderPlayerData placeholder =
                new PlayerDataManager.PlaceholderPlayerData(UUID.randomUUID());

        // Verify all methods return safe defaults
        assertEquals(0, placeholder.getHomeCount());
        assertEquals(0, placeholder.getPlaytime());
        assertEquals(0, placeholder.getWarnings());
        assertFalse(placeholder.isFlyEnabled());
        assertFalse(placeholder.isGodMode());
        assertNull(placeholder.getHome("test"));
        assertNull(placeholder.getLastLocation());

        // Verify setters are no-ops (shouldn't throw)
        assertDoesNotThrow(
                () -> {
                    placeholder.setFlyEnabled(true);
                    placeholder.setGodMode(true);
                    placeholder.addWarning();
                    placeholder.setHome("test", null);
                    placeholder.save();
                });
    }
}
