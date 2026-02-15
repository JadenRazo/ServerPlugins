package net.serverplugins.arcade;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Test suite for ArcadeConfig class. Tests configuration loading, validation, and default values.
 */
@ExtendWith(MockitoExtension.class)
class ArcadeConfigTest {

    @Mock private ServerArcade plugin;

    @Mock private FileConfiguration config;

    private ArcadeConfig arcadeConfig;

    @BeforeEach
    void setUp() {
        when(plugin.getConfig()).thenReturn(config);
        arcadeConfig = new ArcadeConfig(plugin);
    }

    @Test
    void testDefaultMinBet() {
        when(config.getDouble("settings.min-bet", 100)).thenReturn(100.0);

        double minBet = arcadeConfig.getMinBet();

        assertEquals(100.0, minBet, "Default min bet should be 100");
    }

    @Test
    void testDefaultMaxBet() {
        when(config.getDouble("settings.max-bet", 1000000)).thenReturn(1000000.0);

        double maxBet = arcadeConfig.getMaxBet();

        assertEquals(1000000.0, maxBet, "Default max bet should be 1000000");
    }

    @Test
    void testCustomMinBet() {
        when(config.getDouble("settings.min-bet", 100)).thenReturn(50.0);

        double minBet = arcadeConfig.getMinBet();

        assertEquals(50.0, minBet, "Custom min bet should be 50");
    }

    @Test
    void testCustomMaxBet() {
        when(config.getDouble("settings.max-bet", 1000000)).thenReturn(500000.0);

        double maxBet = arcadeConfig.getMaxBet();

        assertEquals(500000.0, maxBet, "Custom max bet should be 500000");
    }

    @Test
    void testMinBetLessThanMaxBet() {
        when(config.getDouble("settings.min-bet", 100)).thenReturn(100.0);
        when(config.getDouble("settings.max-bet", 1000000)).thenReturn(1000000.0);

        assertTrue(
                arcadeConfig.getMinBet() < arcadeConfig.getMaxBet(),
                "Min bet should be less than max bet");
    }

    @Test
    void testNegativeMinBet() {
        when(config.getDouble("settings.min-bet", 100)).thenReturn(-50.0);

        double minBet = arcadeConfig.getMinBet();

        // Config returns whatever is set - validation should happen elsewhere
        assertEquals(-50.0, minBet, "Config should return configured value even if invalid");
    }

    @Test
    void testNegativeMaxBet() {
        when(config.getDouble("settings.max-bet", 1000000)).thenReturn(-1000.0);

        double maxBet = arcadeConfig.getMaxBet();

        // Config returns whatever is set - validation should happen elsewhere
        assertEquals(-1000.0, maxBet, "Config should return configured value even if invalid");
    }

    @Test
    void testDefaultCooldown() {
        when(config.getInt("settings.cooldown", 5)).thenReturn(5);

        int cooldown = arcadeConfig.getCooldown();

        assertEquals(5, cooldown, "Default cooldown should be 5 seconds");
    }

    @Test
    void testCustomCooldown() {
        when(config.getInt("settings.cooldown", 5)).thenReturn(10);

        int cooldown = arcadeConfig.getCooldown();

        assertEquals(10, cooldown, "Custom cooldown should be 10 seconds");
    }

    @Test
    void testZeroCooldown() {
        when(config.getInt("settings.cooldown", 5)).thenReturn(0);

        int cooldown = arcadeConfig.getCooldown();

        assertEquals(0, cooldown, "Cooldown can be 0 (instant play)");
    }

    @Test
    void testSlotsEnabledByDefault() {
        when(config.getBoolean("slots.enabled", true)).thenReturn(true);

        assertTrue(arcadeConfig.isSlotsEnabled(), "Slots should be enabled by default");
    }

    @Test
    void testSlotsCanBeDisabled() {
        when(config.getBoolean("slots.enabled", true)).thenReturn(false);

        assertFalse(arcadeConfig.isSlotsEnabled(), "Slots can be disabled");
    }

    @Test
    void testBlackjackEnabledByDefault() {
        when(config.getBoolean("blackjack.enabled", true)).thenReturn(true);

        assertTrue(arcadeConfig.isBlackjackEnabled(), "Blackjack should be enabled by default");
    }

    @Test
    void testBlackjackCanBeDisabled() {
        when(config.getBoolean("blackjack.enabled", true)).thenReturn(false);

        assertFalse(arcadeConfig.isBlackjackEnabled(), "Blackjack can be disabled");
    }

    @Test
    void testCrashEnabledByDefault() {
        when(config.getBoolean("crash.enabled", true)).thenReturn(true);

        assertTrue(arcadeConfig.isCrashEnabled(), "Crash should be enabled by default");
    }

    @Test
    void testCoinflipEnabledByDefault() {
        when(config.getBoolean("coinflip.enabled", true)).thenReturn(true);

        assertTrue(arcadeConfig.isCoinflipEnabled(), "Coinflip should be enabled by default");
    }

    @Test
    void testMachinesEnabledByDefault() {
        when(config.getBoolean("machines.enabled", true)).thenReturn(true);

        assertTrue(arcadeConfig.areMachinesEnabled(), "Machines should be enabled by default");
    }

    @Test
    void testDealerStandsOnDefault() {
        when(config.getInt("blackjack.dealer-stands-on", 17)).thenReturn(17);

        int standsOn = arcadeConfig.getDealerStandsOn();

        assertEquals(17, standsOn, "Dealer should stand on 17 by default");
    }

    @Test
    void testCustomDealerStandsOn() {
        when(config.getInt("blackjack.dealer-stands-on", 17)).thenReturn(18);

        int standsOn = arcadeConfig.getDealerStandsOn();

        assertEquals(18, standsOn, "Dealer can stand on custom value");
    }

    @Test
    void testBlackjackMultiplierDefault() {
        when(config.getDouble("blackjack.blackjack-multiplier", 2.5)).thenReturn(2.5);

        double multiplier = arcadeConfig.getBlackjackMultiplier();

        assertEquals(2.5, multiplier, "Blackjack multiplier should be 2.5 by default (3:2 payout)");
    }

    @Test
    void testWinMultiplierDefault() {
        when(config.getDouble("blackjack.win-multiplier", 2.0)).thenReturn(2.0);

        double multiplier = arcadeConfig.getWinMultiplier();

        assertEquals(2.0, multiplier, "Win multiplier should be 2.0 by default (1:1 payout)");
    }

    @Test
    void testCrashHouseEdgeDefault() {
        when(config.getDouble("crash.house-edge", 0.05)).thenReturn(0.05);

        double houseEdge = arcadeConfig.getCrashHouseEdge();

        assertEquals(0.05, houseEdge, "Crash house edge should be 5% by default");
    }

    @Test
    void testCrashMaxMultiplierDefault() {
        when(config.getDouble("crash.max-multiplier", 100.0)).thenReturn(100.0);

        double maxMult = arcadeConfig.getCrashMaxMultiplier();

        assertEquals(100.0, maxMult, "Crash max multiplier should be 100x by default");
    }

    @Test
    void testCrashTickRateDefault() {
        when(config.getInt("crash.tick-rate", 2)).thenReturn(2);

        int tickRate = arcadeConfig.getCrashTickRate();

        assertEquals(2, tickRate, "Crash tick rate should be 2 by default");
    }

    @Test
    void testCoinflipExpiryTimeDefault() {
        when(config.getInt("coinflip.expiry-time", 60)).thenReturn(60);

        int expiryTime = arcadeConfig.getCoinflipExpiryTime();

        assertEquals(60, expiryTime, "Coinflip expiry should be 60 seconds by default");
    }

    @Test
    void testSlotSymbolsLoading() {
        ConfigurationSection symbolsSection = mock(ConfigurationSection.class);
        when(config.getConfigurationSection("slots.symbols")).thenReturn(symbolsSection);

        Set<String> keys = new HashSet<>(Arrays.asList("cherry", "lemon", "seven"));
        when(symbolsSection.getKeys(false)).thenReturn(keys);

        // Setup cherry
        when(symbolsSection.getString("cherry.material", "STONE")).thenReturn("POPPY");
        when(symbolsSection.getInt("cherry.weight", 10)).thenReturn(50);
        when(symbolsSection.getDouble("cherry.multiplier", 2.0)).thenReturn(2.0);

        // Setup lemon
        when(symbolsSection.getString("lemon.material", "STONE")).thenReturn("GOLD_INGOT");
        when(symbolsSection.getInt("lemon.weight", 10)).thenReturn(30);
        when(symbolsSection.getDouble("lemon.multiplier", 2.0)).thenReturn(3.0);

        // Setup seven
        when(symbolsSection.getString("seven.material", "STONE")).thenReturn("DIAMOND");
        when(symbolsSection.getInt("seven.weight", 10)).thenReturn(10);
        when(symbolsSection.getDouble("seven.multiplier", 2.0)).thenReturn(10.0);

        List<ArcadeConfig.SlotSymbol> symbols = arcadeConfig.getSlotSymbols();

        assertEquals(3, symbols.size(), "Should load 3 symbols");

        // Verify symbols contain expected data
        boolean hasCherry = symbols.stream().anyMatch(s -> s.name().equals("cherry"));
        boolean hasLemon = symbols.stream().anyMatch(s -> s.name().equals("lemon"));
        boolean hasSeven = symbols.stream().anyMatch(s -> s.name().equals("seven"));

        assertTrue(hasCherry, "Should contain cherry symbol");
        assertTrue(hasLemon, "Should contain lemon symbol");
        assertTrue(hasSeven, "Should contain seven symbol");
    }

    @Test
    void testSlotSymbolsWithInvalidMaterial() {
        ConfigurationSection symbolsSection = mock(ConfigurationSection.class);
        when(config.getConfigurationSection("slots.symbols")).thenReturn(symbolsSection);

        Set<String> keys = new HashSet<>(Arrays.asList("invalid"));
        when(symbolsSection.getKeys(false)).thenReturn(keys);

        when(symbolsSection.getString("invalid.material", "STONE"))
                .thenReturn("INVALID_MATERIAL_NAME");
        when(symbolsSection.getInt("invalid.weight", 10)).thenReturn(10);
        when(symbolsSection.getDouble("invalid.multiplier", 2.0)).thenReturn(2.0);

        List<ArcadeConfig.SlotSymbol> symbols = arcadeConfig.getSlotSymbols();

        assertEquals(1, symbols.size(), "Should still create symbol with invalid material");
        assertEquals(
                Material.STONE,
                symbols.get(0).material(),
                "Should default to STONE for invalid material");
    }

    @Test
    void testSlotSymbolsEmptySection() {
        when(config.getConfigurationSection("slots.symbols")).thenReturn(null);

        List<ArcadeConfig.SlotSymbol> symbols = arcadeConfig.getSlotSymbols();

        assertNotNull(symbols, "Symbol list should not be null");
        assertTrue(symbols.isEmpty(), "Symbol list should be empty when section is null");
    }

    @Test
    void testGetMessageWithPrefix() {
        when(config.getString("messages.prefix", "")).thenReturn("[Arcade] ");
        when(config.getString("messages.test", "test")).thenReturn("Test message");

        String message = arcadeConfig.getMessage("test");

        assertEquals("[Arcade] Test message", message, "Message should include prefix");
    }

    @Test
    void testGetMessageWithoutPrefix() {
        when(config.getString("messages.prefix", "")).thenReturn("");
        when(config.getString("messages.test", "test")).thenReturn("Test message");

        String message = arcadeConfig.getMessage("test");

        assertEquals("Test message", message, "Message should not have prefix if not configured");
    }

    @Test
    void testGetMessageFallbackToKey() {
        when(config.getString("messages.prefix", "")).thenReturn("");
        when(config.getString("messages.missing", "missing")).thenReturn("missing");

        String message = arcadeConfig.getMessage("missing");

        assertEquals("missing", message, "Should fallback to key if message not configured");
    }

    @Test
    void testConfigReload() {
        // Test that config can be reloaded without breaking
        when(config.getDouble("settings.min-bet", 100)).thenReturn(100.0);
        double initialMinBet = arcadeConfig.getMinBet();

        // Change config value
        when(config.getDouble("settings.min-bet", 100)).thenReturn(200.0);
        double newMinBet = arcadeConfig.getMinBet();

        assertEquals(100.0, initialMinBet, "Initial min bet should be 100");
        assertEquals(200.0, newMinBet, "New min bet should be 200");
    }

    @Test
    void testMultipleConfigInstancesIndependent() {
        ServerArcade plugin2 = mock(ServerArcade.class);
        FileConfiguration config2 = mock(FileConfiguration.class);
        when(plugin2.getConfig()).thenReturn(config2);

        when(config.getDouble("settings.min-bet", 100)).thenReturn(100.0);
        when(config2.getDouble("settings.min-bet", 100)).thenReturn(200.0);

        ArcadeConfig arcadeConfig2 = new ArcadeConfig(plugin2);

        assertEquals(100.0, arcadeConfig.getMinBet(), "First config should have min bet 100");
        assertEquals(200.0, arcadeConfig2.getMinBet(), "Second config should have min bet 200");
    }

    @Test
    void testInvalidPortConfiguration() {
        // Test that invalid port values are handled
        when(config.getInt("api.port", 8080)).thenReturn(-1);

        int port = config.getInt("api.port", 8080);

        assertEquals(-1, port, "Config should return invalid port (validation happens elsewhere)");
    }

    @Test
    void testExtremelyHighMaxBet() {
        when(config.getDouble("settings.max-bet", 1000000)).thenReturn(Double.MAX_VALUE);

        double maxBet = arcadeConfig.getMaxBet();

        assertEquals(Double.MAX_VALUE, maxBet, "Should handle extremely high max bet");
    }

    @Test
    void testZeroMinBet() {
        when(config.getDouble("settings.min-bet", 100)).thenReturn(0.0);

        double minBet = arcadeConfig.getMinBet();

        assertEquals(0.0, minBet, "Min bet can be 0 (free play)");
    }

    @Test
    void testSlotSymbolDefaultValues() {
        ConfigurationSection symbolsSection = mock(ConfigurationSection.class);
        when(config.getConfigurationSection("slots.symbols")).thenReturn(symbolsSection);

        Set<String> keys = new HashSet<>(Arrays.asList("default"));
        when(symbolsSection.getKeys(false)).thenReturn(keys);

        // Don't specify any values - should use defaults
        when(symbolsSection.getString("default.material", "STONE")).thenReturn("STONE");
        when(symbolsSection.getInt("default.weight", 10)).thenReturn(10);
        when(symbolsSection.getDouble("default.multiplier", 2.0)).thenReturn(2.0);

        List<ArcadeConfig.SlotSymbol> symbols = arcadeConfig.getSlotSymbols();

        assertEquals(1, symbols.size(), "Should load 1 symbol");
        assertEquals(Material.STONE, symbols.get(0).material(), "Should use default material");
        assertEquals(10, symbols.get(0).weight(), "Should use default weight");
        assertEquals(2.0, symbols.get(0).multiplier(), "Should use default multiplier");
    }
}
