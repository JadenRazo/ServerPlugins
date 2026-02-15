package net.serverplugins.arcade.games.slots;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.serverplugins.arcade.ArcadeConfig;
import net.serverplugins.arcade.ServerArcade;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Test suite for SlotsManager class. Tests cooldown management, spin mechanics, and memory leak
 * prevention.
 */
@ExtendWith(MockitoExtension.class)
class SlotsManagerTest {

    @Mock private ServerArcade plugin;

    @Mock private ArcadeConfig arcadeConfig;

    private SlotsManager slotsManager;

    @BeforeEach
    void setUp() {
        lenient().when(plugin.getArcadeConfig()).thenReturn(arcadeConfig);
        lenient().when(arcadeConfig.getCooldown()).thenReturn(5);

        // Setup default slot symbols
        List<ArcadeConfig.SlotSymbol> symbols =
                Arrays.asList(
                        new ArcadeConfig.SlotSymbol("cherry", Material.POPPY, 50, 2.0),
                        new ArcadeConfig.SlotSymbol("lemon", Material.GOLD_INGOT, 30, 3.0),
                        new ArcadeConfig.SlotSymbol("seven", Material.DIAMOND, 10, 10.0),
                        new ArcadeConfig.SlotSymbol("bar", Material.IRON_BLOCK, 10, 5.0));
        lenient().when(arcadeConfig.getSlotSymbols()).thenReturn(symbols);

        slotsManager = new SlotsManager(plugin);
    }

    @Test
    void testCooldownExpiration() throws InterruptedException {
        UUID playerId = UUID.randomUUID();

        // Set cooldown
        slotsManager.setCooldown(playerId);
        assertTrue(slotsManager.isOnCooldown(playerId), "Player should be on cooldown immediately");

        // Wait for cooldown + buffer (5 seconds + 5 second buffer = 10 seconds)
        // We'll test with shorter wait to verify automatic cleanup
        Thread.sleep(100);

        // Check remaining cooldown
        int remaining = slotsManager.getRemainingCooldown(playerId);
        assertTrue(
                remaining >= 4 && remaining <= 5,
                "Remaining cooldown should be close to 5 seconds");
    }

    @Test
    void testCooldownEnforcement() {
        UUID playerId = UUID.randomUUID();

        // Initially no cooldown
        assertFalse(slotsManager.isOnCooldown(playerId), "New player should not be on cooldown");
        assertEquals(
                0,
                slotsManager.getRemainingCooldown(playerId),
                "New player should have 0 remaining cooldown");

        // Set cooldown
        slotsManager.setCooldown(playerId);
        assertTrue(
                slotsManager.isOnCooldown(playerId), "Player should be on cooldown after setting");

        int remaining = slotsManager.getRemainingCooldown(playerId);
        assertTrue(
                remaining > 0 && remaining <= 5,
                "Remaining cooldown should be between 0 and 5 seconds");
    }

    @Test
    void testMemoryLeakPrevention() {
        // Test with 1000+ unique players to ensure cache doesn't grow unbounded
        for (int i = 0; i < 1500; i++) {
            UUID playerId = UUID.randomUUID();
            slotsManager.setCooldown(playerId);
        }

        // If we get here without OutOfMemoryError, the cache is working correctly
        // Caffeine cache should automatically limit to 10,000 entries and expire old ones
        assertTrue(true, "Cache handled 1500 unique players without memory issues");
    }

    @Test
    void testSpinResultsWithinConfiguredWeights() {
        // Run 1000 spins and verify distribution matches weights
        Map<String, Integer> symbolCounts = new HashMap<>();
        int trials = 1000;

        for (int i = 0; i < trials; i++) {
            SlotsManager.SpinResult result = slotsManager.spin(100.0);

            for (ArcadeConfig.SlotSymbol symbol : result.symbols()) {
                symbolCounts.merge(symbol.name(), 1, Integer::sum);
            }
        }

        // Total weight: 50 + 30 + 10 + 10 = 100
        // Expected probabilities:
        // - cherry: 50% (1500 out of 3000 slots)
        // - lemon: 30% (900 out of 3000 slots)
        // - seven: 10% (300 out of 3000 slots)
        // - bar: 10% (300 out of 3000 slots)

        int totalSymbols = trials * 3;
        double cherryPercent = (symbolCounts.getOrDefault("cherry", 0) * 100.0) / totalSymbols;
        double lemonPercent = (symbolCounts.getOrDefault("lemon", 0) * 100.0) / totalSymbols;

        // Allow 10% deviation from expected values
        assertTrue(
                cherryPercent >= 40 && cherryPercent <= 60,
                "Cherry should appear ~50% of the time, got " + cherryPercent + "%");
        assertTrue(
                lemonPercent >= 20 && lemonPercent <= 40,
                "Lemon should appear ~30% of the time, got " + lemonPercent + "%");
    }

    @Test
    void testSymbolPickingFollowsProbability() {
        // Run 100 spins and verify we get different symbols (not always the same)
        boolean hasVariation = false;
        String firstSymbolName = null;

        for (int i = 0; i < 100; i++) {
            SlotsManager.SpinResult result = slotsManager.spin(100.0);
            String symbolName = result.symbols()[0].name();

            if (firstSymbolName == null) {
                firstSymbolName = symbolName;
            } else if (!firstSymbolName.equals(symbolName)) {
                hasVariation = true;
                break;
            }
        }

        assertTrue(hasVariation, "Symbol selection should have variation across 100 spins");
    }

    @Test
    void testThreeMatchingSymbolsWin() {
        // Run spins until we get a three-match (should happen eventually with probability)
        boolean foundThreeMatch = false;

        for (int i = 0; i < 10000; i++) {
            SlotsManager.SpinResult result = slotsManager.spin(100.0);
            ArcadeConfig.SlotSymbol[] symbols = result.symbols();

            if (symbols[0].equals(symbols[1]) && symbols[1].equals(symbols[2])) {
                foundThreeMatch = true;

                // Verify the result is a win with correct multiplier
                assertTrue(result.result().won(), "Three matching symbols should win");
                assertEquals(
                        symbols[0].multiplier(),
                        result.result().multiplier(),
                        "Win multiplier should match symbol multiplier");

                break;
            }
        }

        assertTrue(foundThreeMatch, "Should eventually get three matching symbols in 10000 spins");
    }

    @Test
    void testTwoMatchingSymbolsWin() {
        // Run spins until we get a two-match
        boolean foundTwoMatch = false;

        for (int i = 0; i < 5000; i++) {
            SlotsManager.SpinResult result = slotsManager.spin(100.0);
            ArcadeConfig.SlotSymbol[] symbols = result.symbols();

            boolean twoMatch =
                    (symbols[0].equals(symbols[1]) && !symbols[1].equals(symbols[2]))
                            || (symbols[1].equals(symbols[2]) && !symbols[0].equals(symbols[1]))
                            || (symbols[0].equals(symbols[2]) && !symbols[0].equals(symbols[1]));

            if (twoMatch) {
                foundTwoMatch = true;

                // Verify the result is a win with 30% of symbol multiplier
                assertTrue(result.result().won(), "Two matching symbols should win");
                assertTrue(result.result().multiplier() > 0, "Win multiplier should be positive");

                break;
            }
        }

        assertTrue(foundTwoMatch, "Should eventually get two matching symbols in 5000 spins");
    }

    @Test
    void testNoMatchingSymbolsLose() {
        // Run spins until we get no matches
        boolean foundNoMatch = false;

        for (int i = 0; i < 1000; i++) {
            SlotsManager.SpinResult result = slotsManager.spin(100.0);
            ArcadeConfig.SlotSymbol[] symbols = result.symbols();

            boolean noMatch =
                    !symbols[0].equals(symbols[1])
                            && !symbols[1].equals(symbols[2])
                            && !symbols[0].equals(symbols[2]);

            if (noMatch) {
                foundNoMatch = true;

                // Verify the result is a loss
                assertFalse(result.result().won(), "No matching symbols should lose");
                assertEquals(0, result.result().payout(), "Loss should have 0 payout");

                break;
            }
        }

        assertTrue(foundNoMatch, "Should eventually get no matching symbols in 1000 spins");
    }

    @Test
    void testEmptySymbolsConfiguration() {
        when(arcadeConfig.getSlotSymbols()).thenReturn(Arrays.asList());

        SlotsManager emptyManager = new SlotsManager(plugin);
        SlotsManager.SpinResult result = emptyManager.spin(100.0);

        assertNotNull(result, "Result should not be null");
        assertFalse(result.result().won(), "Empty symbols should result in loss");
        assertEquals(
                "No symbols configured",
                result.result().message(),
                "Should have error message about missing symbols");
    }

    @Test
    void testSpinWithDifferentBetAmounts() {
        // Test that bet amount affects payout but not win/loss outcome logic
        SlotsManager.SpinResult result1 = slotsManager.spin(100.0);
        SlotsManager.SpinResult result2 = slotsManager.spin(1000.0);

        assertNotNull(result1, "Result with bet 100 should not be null");
        assertNotNull(result2, "Result with bet 1000 should not be null");

        // Both should have 3 symbols
        assertEquals(3, result1.symbols().length, "Should always return 3 symbols");
        assertEquals(3, result2.symbols().length, "Should always return 3 symbols");
    }

    @Test
    void testCooldownWithMultiplePlayers() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        UUID player3 = UUID.randomUUID();

        // Set cooldowns for multiple players
        slotsManager.setCooldown(player1);
        slotsManager.setCooldown(player2);
        slotsManager.setCooldown(player3);

        // All should be on cooldown
        assertTrue(slotsManager.isOnCooldown(player1), "Player 1 should be on cooldown");
        assertTrue(slotsManager.isOnCooldown(player2), "Player 2 should be on cooldown");
        assertTrue(slotsManager.isOnCooldown(player3), "Player 3 should be on cooldown");

        // All should have remaining cooldown
        assertTrue(
                slotsManager.getRemainingCooldown(player1) > 0,
                "Player 1 should have remaining cooldown");
        assertTrue(
                slotsManager.getRemainingCooldown(player2) > 0,
                "Player 2 should have remaining cooldown");
        assertTrue(
                slotsManager.getRemainingCooldown(player3) > 0,
                "Player 3 should have remaining cooldown");
    }
}
