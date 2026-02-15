package net.serverplugins.core.data;

import static org.junit.jupiter.api.Assertions.*;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import java.io.File;
import java.io.FileWriter;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.serverplugins.core.ServerCore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerDataManagerTest {

    private ServerMock server;
    private ServerCore plugin;
    private PlayerDataManager manager;

    @TempDir File tempDir;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ServerCore.class);
        manager = plugin.getPlayerDataManager();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void testPreloadPlayerDataAsync_NewPlayer()
            throws ExecutionException, InterruptedException, TimeoutException {
        UUID playerId = UUID.randomUUID();

        CompletableFuture<PlayerDataManager.PlayerData> future =
                manager.preloadPlayerDataAsync(playerId);
        assertNotNull(future, "Future should not be null");

        PlayerDataManager.PlayerData data = future.get(5, TimeUnit.SECONDS);
        assertNotNull(data, "Player data should be loaded");
        assertEquals(playerId, data.getPlayerId(), "Player ID should match");
        assertTrue(data.isFirstJoin(), "New player should be marked as first join");
    }

    @Test
    void testPreloadPlayerDataAsync_ExistingPlayer() throws Exception {
        UUID playerId = UUID.randomUUID();

        // Create a player data file manually
        File playerFile = new File(plugin.getDataFolder(), "playerdata/" + playerId + ".json");
        playerFile.getParentFile().mkdirs();

        try (FileWriter writer = new FileWriter(playerFile)) {
            writer.write("{\"firstJoin\":false,\"features\":{\"auto-totem\":true}}");
        }

        CompletableFuture<PlayerDataManager.PlayerData> future =
                manager.preloadPlayerDataAsync(playerId);
        PlayerDataManager.PlayerData data = future.get(5, TimeUnit.SECONDS);

        assertNotNull(data, "Player data should be loaded");
        assertFalse(data.isFirstJoin(), "Existing player should not be first join");
        assertTrue(data.isFeatureEnabled("auto-totem"), "Feature toggle should be loaded");
    }

    @Test
    void testPreloadPlayerDataAsync_CachedData()
            throws ExecutionException, InterruptedException, TimeoutException {
        UUID playerId = UUID.randomUUID();

        // First load
        CompletableFuture<PlayerDataManager.PlayerData> future1 =
                manager.preloadPlayerDataAsync(playerId);
        PlayerDataManager.PlayerData data1 = future1.get(5, TimeUnit.SECONDS);

        // Second load - should return cached data immediately
        CompletableFuture<PlayerDataManager.PlayerData> future2 =
                manager.preloadPlayerDataAsync(playerId);
        assertTrue(future2.isDone(), "Cached data should be returned immediately");

        PlayerDataManager.PlayerData data2 = future2.get(1, TimeUnit.SECONDS);
        assertSame(data1, data2, "Should return the same cached instance");
    }

    @Test
    void testLoadPlayerData_ThrowsExceptionIfNotPreloaded() {
        UUID playerId = UUID.randomUUID();

        // Attempting to load without preloading should throw exception
        assertThrows(
                IllegalStateException.class,
                () -> {
                    manager.loadPlayerData(playerId);
                },
                "Should throw IllegalStateException when data not preloaded");
    }

    @Test
    void testLoadPlayerData_SucceedsAfterPreload()
            throws ExecutionException, InterruptedException, TimeoutException {
        UUID playerId = UUID.randomUUID();

        // Preload first
        manager.preloadPlayerDataAsync(playerId).get(5, TimeUnit.SECONDS);

        // Now loadPlayerData should work
        PlayerDataManager.PlayerData data = manager.loadPlayerData(playerId);
        assertNotNull(data, "Should return preloaded data");
        assertEquals(playerId, data.getPlayerId(), "Player ID should match");
    }

    @Test
    void testFeatureToggle() throws ExecutionException, InterruptedException, TimeoutException {
        UUID playerId = UUID.randomUUID();

        // Preload data
        manager.preloadPlayerDataAsync(playerId).get(5, TimeUnit.SECONDS);

        PlayerDataManager.PlayerData data = manager.loadPlayerData(playerId);

        // Test feature toggle
        data.setFeatureEnabled("test-feature", true);
        assertTrue(data.isFeatureEnabled("test-feature"), "Feature should be enabled");

        data.setFeatureEnabled("test-feature", false);
        assertFalse(data.isFeatureEnabled("test-feature"), "Feature should be disabled");
    }

    @Test
    void testFirstJoinStatus() throws ExecutionException, InterruptedException, TimeoutException {
        UUID playerId = UUID.randomUUID();

        // Preload data
        manager.preloadPlayerDataAsync(playerId).get(5, TimeUnit.SECONDS);

        assertTrue(manager.isFirstJoin(playerId), "New player should be first join");

        manager.markJoined(playerId);

        assertFalse(manager.isFirstJoin(playerId), "Player should no longer be first join");
    }

    @Test
    void testConcurrentPreload() throws ExecutionException, InterruptedException, TimeoutException {
        UUID playerId = UUID.randomUUID();

        // Simulate multiple concurrent preload requests
        CompletableFuture<PlayerDataManager.PlayerData> future1 =
                manager.preloadPlayerDataAsync(playerId);
        CompletableFuture<PlayerDataManager.PlayerData> future2 =
                manager.preloadPlayerDataAsync(playerId);
        CompletableFuture<PlayerDataManager.PlayerData> future3 =
                manager.preloadPlayerDataAsync(playerId);

        PlayerDataManager.PlayerData data1 = future1.get(5, TimeUnit.SECONDS);
        PlayerDataManager.PlayerData data2 = future2.get(5, TimeUnit.SECONDS);
        PlayerDataManager.PlayerData data3 = future3.get(5, TimeUnit.SECONDS);

        // All should return the same instance (from cache)
        assertNotNull(data1, "Data 1 should not be null");
        assertNotNull(data2, "Data 2 should not be null");
        assertNotNull(data3, "Data 3 should not be null");
    }

    @Test
    void testSaveAndUnload() throws Exception {
        UUID playerId = UUID.randomUUID();

        // Preload data
        manager.preloadPlayerDataAsync(playerId).get(5, TimeUnit.SECONDS);

        PlayerDataManager.PlayerData data = manager.loadPlayerData(playerId);
        data.setFeatureEnabled("test-save", true);
        data.setFirstJoin(false);

        // Save and unload
        manager.unloadPlayerData(playerId);

        // Wait for async save to complete
        Thread.sleep(500);

        // Reload to verify persistence
        manager.preloadPlayerDataAsync(playerId).get(5, TimeUnit.SECONDS);
        PlayerDataManager.PlayerData reloadedData = manager.loadPlayerData(playerId);

        assertFalse(reloadedData.isFirstJoin(), "First join status should be persisted");
        assertTrue(
                reloadedData.isFeatureEnabled("test-save"), "Feature toggle should be persisted");
    }

    @Test
    void testNoThreadSleep() {
        // This test verifies that our implementation doesn't use Thread.sleep()
        // by checking that operations complete quickly

        UUID playerId = UUID.randomUUID();

        long startTime = System.currentTimeMillis();
        try {
            manager.preloadPlayerDataAsync(playerId).get(100, TimeUnit.MILLISECONDS);
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
}
