package net.serverplugins.claim.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

import net.serverplugins.claim.ClaimConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExponentialPricing Tests")
class ExponentialPricingTest {

    @Mock private ClaimConfig config;

    private ExponentialPricing pricing;

    private static final double BASE_PRICE = 100.0;
    private static final double GROWTH_RATE = 1.1;
    private static final int MAX_CHUNKS = 50;
    private static final double CLAIM_MULTIPLIER = 1.5;
    private static final double DELTA = 0.001;

    @BeforeEach
    void setUp() {
        when(config.getBasePrice()).thenReturn(BASE_PRICE);
        when(config.getGrowthRate()).thenReturn(GROWTH_RATE);
        when(config.getMaxChunksPerClaim()).thenReturn(MAX_CHUNKS);
        when(config.getClaimMultiplier()).thenReturn(CLAIM_MULTIPLIER);

        pricing = new ExponentialPricing(config);
    }

    @Test
    @DisplayName("Constructor should initialize fields from config")
    void testConstructor() {
        assertThat(pricing.getBasePrice()).isEqualTo(BASE_PRICE);
        assertThat(pricing.getGrowthRate()).isEqualTo(GROWTH_RATE);
        assertThat(pricing.getMaxChunksPerClaim()).isEqualTo(MAX_CHUNKS);
        assertThat(pricing.getClaimMultiplier()).isEqualTo(CLAIM_MULTIPLIER);
    }

    @Test
    @DisplayName("getPrice() for chunk number <= 0 should return base price")
    void testGetPriceZeroOrNegative() {
        assertThat(pricing.getPrice(0)).isEqualTo(BASE_PRICE, within(DELTA));
        assertThat(pricing.getPrice(-1)).isEqualTo(BASE_PRICE, within(DELTA));
        assertThat(pricing.getPrice(-100)).isEqualTo(BASE_PRICE, within(DELTA));
    }

    @Test
    @DisplayName("getPrice() for chunk number > max should return -1")
    void testGetPriceAboveMax() {
        assertThat(pricing.getPrice(MAX_CHUNKS + 1)).isEqualTo(-1, within(DELTA));
        assertThat(pricing.getPrice(MAX_CHUNKS + 10)).isEqualTo(-1, within(DELTA));
        assertThat(pricing.getPrice(1000)).isEqualTo(-1, within(DELTA));
    }

    @Test
    @DisplayName("getPrice() should calculate exponential price correctly")
    void testGetPriceExponential() {
        // Chunk 1: 100 * 1.1^0 = 100
        assertThat(pricing.getPrice(1)).isEqualTo(100.0, within(DELTA));

        // Chunk 2: 100 * 1.1^1 = 110
        assertThat(pricing.getPrice(2)).isEqualTo(110.0, within(DELTA));

        // Chunk 3: 100 * 1.1^2 = 121
        assertThat(pricing.getPrice(3)).isEqualTo(121.0, within(DELTA));

        // Chunk 10: 100 * 1.1^9
        double expected10 = BASE_PRICE * Math.pow(GROWTH_RATE, 9);
        assertThat(pricing.getPrice(10)).isEqualTo(expected10, within(DELTA));

        // Chunk 50 (max): 100 * 1.1^49
        double expected50 = BASE_PRICE * Math.pow(GROWTH_RATE, 49);
        assertThat(pricing.getPrice(50)).isEqualTo(expected50, within(DELTA));
    }

    @Test
    @DisplayName("getPrice(chunk, claimOrder) should apply claim multiplier")
    void testGetPriceWithClaimOrder() {
        // First claim (order 1): no multiplier
        assertThat(pricing.getPrice(1, 1)).isEqualTo(100.0, within(DELTA));
        assertThat(pricing.getPrice(2, 1)).isEqualTo(110.0, within(DELTA));

        // Second claim (order 2): 1.5x multiplier
        assertThat(pricing.getPrice(1, 2)).isEqualTo(150.0, within(DELTA));
        assertThat(pricing.getPrice(2, 2)).isEqualTo(165.0, within(DELTA));

        // Third claim (order 3): 1.5^2 = 2.25x multiplier
        assertThat(pricing.getPrice(1, 3)).isEqualTo(225.0, within(DELTA));
        assertThat(pricing.getPrice(2, 3)).isEqualTo(247.5, within(DELTA));
    }

    @Test
    @DisplayName("getPrice(chunk, claimOrder) should return -1 when chunk exceeds max")
    void testGetPriceWithClaimOrderAboveMax() {
        assertThat(pricing.getPrice(MAX_CHUNKS + 1, 1)).isEqualTo(-1, within(DELTA));
        assertThat(pricing.getPrice(MAX_CHUNKS + 1, 2)).isEqualTo(-1, within(DELTA));
        assertThat(pricing.getPrice(100, 5)).isEqualTo(-1, within(DELTA));
    }

    @Test
    @DisplayName("getClaimOrderMultiplier() should return 1.0 for first claim")
    void testGetClaimOrderMultiplierFirst() {
        assertThat(pricing.getClaimOrderMultiplier(1)).isEqualTo(1.0, within(DELTA));
        assertThat(pricing.getClaimOrderMultiplier(0)).isEqualTo(1.0, within(DELTA));
        assertThat(pricing.getClaimOrderMultiplier(-1)).isEqualTo(1.0, within(DELTA));
    }

    @Test
    @DisplayName("getClaimOrderMultiplier() should calculate exponential multiplier")
    void testGetClaimOrderMultiplierExponential() {
        // Claim 2: 1.5^1 = 1.5
        assertThat(pricing.getClaimOrderMultiplier(2)).isEqualTo(1.5, within(DELTA));

        // Claim 3: 1.5^2 = 2.25
        assertThat(pricing.getClaimOrderMultiplier(3)).isEqualTo(2.25, within(DELTA));

        // Claim 4: 1.5^3 = 3.375
        assertThat(pricing.getClaimOrderMultiplier(4)).isEqualTo(3.375, within(DELTA));

        // Claim 5: 1.5^4 = 5.0625
        assertThat(pricing.getClaimOrderMultiplier(5)).isEqualTo(5.0625, within(DELTA));
    }

    @Test
    @DisplayName("getGlobalPrice() should calculate with global chunk number")
    void testGetGlobalPrice() {
        // First global chunk, first profile: 100 * 1.1^0 * 1.5^0 = 100
        assertThat(pricing.getGlobalPrice(1, 1)).isEqualTo(100.0, within(DELTA));

        // Second global chunk, first profile: 100 * 1.1^1 * 1.5^0 = 110
        assertThat(pricing.getGlobalPrice(2, 1)).isEqualTo(110.0, within(DELTA));

        // First global chunk, second profile: 100 * 1.1^0 * 1.5^1 = 150
        assertThat(pricing.getGlobalPrice(1, 2)).isEqualTo(150.0, within(DELTA));

        // Third global chunk, third profile: 100 * 1.1^2 * 1.5^2 = 121 * 2.25 = 272.25
        double expected = 100.0 * Math.pow(1.1, 2) * Math.pow(1.5, 2);
        assertThat(pricing.getGlobalPrice(3, 3)).isEqualTo(expected, within(DELTA));
    }

    @Test
    @DisplayName("getGlobalPrice() with zero or negative should return base price")
    void testGetGlobalPriceEdgeCases() {
        assertThat(pricing.getGlobalPrice(0, 1)).isEqualTo(BASE_PRICE, within(DELTA));
        assertThat(pricing.getGlobalPrice(-1, 1)).isEqualTo(BASE_PRICE, within(DELTA));
    }

    @Test
    @DisplayName("getTotalCost() should sum chunk prices")
    void testGetTotalCost() {
        // Chunks 1-3 for first claim: 100 + 110 + 121 = 331
        double expected = 100.0 + 110.0 + 121.0;
        assertThat(pricing.getTotalCost(1, 3, 1)).isEqualTo(expected, within(DELTA));

        // Chunks 1-2 for second claim: 150 + 165 = 315
        double expected2 = 150.0 + 165.0;
        assertThat(pricing.getTotalCost(1, 2, 2)).isEqualTo(expected2, within(DELTA));
    }

    @Test
    @DisplayName("getTotalCost() should stop at max chunks")
    void testGetTotalCostAtMax() {
        // If range exceeds max, should stop at the chunk before invalid price
        double total = pricing.getTotalCost(MAX_CHUNKS, MAX_CHUNKS + 5, 1);
        double expectedLast = BASE_PRICE * Math.pow(GROWTH_RATE, MAX_CHUNKS - 1);
        assertThat(total).isEqualTo(expectedLast, within(DELTA));
    }

    @Test
    @DisplayName("getTotalCost() with default claim order should use 1")
    void testGetTotalCostDefaultClaimOrder() {
        double withDefault = pricing.getTotalCost(1, 3);
        double withExplicit = pricing.getTotalCost(1, 3, 1);
        assertThat(withDefault).isEqualTo(withExplicit, within(DELTA));
    }

    @Test
    @DisplayName("getMaxAffordableChunks() should count affordable chunks")
    void testGetMaxAffordableChunks() {
        // With $350 balance, can afford chunks 1, 2, 3 (100 + 110 + 121 = 331)
        // Cannot afford chunk 4 (133.1, total would be 464.1)
        assertThat(pricing.getMaxAffordableChunks(350.0, 0, 1)).isEqualTo(3);

        // With $100 balance, can only afford chunk 1
        assertThat(pricing.getMaxAffordableChunks(100.0, 0, 1)).isEqualTo(1);

        // With $50 balance, cannot afford any chunk
        assertThat(pricing.getMaxAffordableChunks(50.0, 0, 1)).isEqualTo(0);
    }

    @Test
    @DisplayName("getMaxAffordableChunks() should respect currentPurchased offset")
    void testGetMaxAffordableChunksWithOffset() {
        // Already purchased 2 chunks, next is chunk 3 ($121)
        // With $130, can afford chunk 3 only
        assertThat(pricing.getMaxAffordableChunks(130.0, 2, 1)).isEqualTo(1);

        // With $300, can afford chunks 3, 4 (121 + 133.1 = 254.1)
        assertThat(pricing.getMaxAffordableChunks(300.0, 2, 1)).isEqualTo(2);
    }

    @Test
    @DisplayName("getMaxAffordableChunks() should apply claim order multiplier")
    void testGetMaxAffordableChunksWithClaimOrder() {
        // Second claim: chunk 1 costs $150
        // With $160, can afford 1 chunk
        assertThat(pricing.getMaxAffordableChunks(160.0, 0, 2)).isEqualTo(1);

        // With $320, can afford chunks 1, 2 (150 + 165 = 315)
        assertThat(pricing.getMaxAffordableChunks(320.0, 0, 2)).isEqualTo(2);
    }

    @Test
    @DisplayName("getMaxAffordableChunks() with default claim order should use 1")
    void testGetMaxAffordableChunksDefaultClaimOrder() {
        int withDefault = pricing.getMaxAffordableChunks(350.0, 0);
        int withExplicit = pricing.getMaxAffordableChunks(350.0, 0, 1);
        assertThat(withDefault).isEqualTo(withExplicit);
    }

    @Test
    @DisplayName("formatPrice() should format amounts under 1K with commas")
    void testFormatPriceUnder1K() {
        assertThat(pricing.formatPrice(0)).isEqualTo("0");
        assertThat(pricing.formatPrice(100)).isEqualTo("100");
        assertThat(pricing.formatPrice(999)).isEqualTo("999");
    }

    @Test
    @DisplayName("formatPrice() should format 1K-9.99K with 2 decimals")
    void testFormatPrice1KTo10K() {
        assertThat(pricing.formatPrice(1000)).isEqualTo("1.00K");
        assertThat(pricing.formatPrice(1234)).isEqualTo("1.23K");
        assertThat(pricing.formatPrice(9999)).isEqualTo("10.00K");
    }

    @Test
    @DisplayName("formatPrice() should format 10K-99.9K with 1 decimal")
    void testFormatPrice10KTo100K() {
        assertThat(pricing.formatPrice(10000)).isEqualTo("10.0K");
        assertThat(pricing.formatPrice(12345)).isEqualTo("12.3K");
        assertThat(pricing.formatPrice(99900)).isEqualTo("99.9K");
    }

    @Test
    @DisplayName("formatPrice() should format 100K-999K as whole thousands")
    void testFormatPrice100KTo1M() {
        assertThat(pricing.formatPrice(100000)).isEqualTo("100K");
        assertThat(pricing.formatPrice(123456)).isEqualTo("123K");
        assertThat(pricing.formatPrice(999000)).isEqualTo("999K");
    }

    @Test
    @DisplayName("formatPrice() should format 1M-99.9M with 2 decimals")
    void testFormatPrice1MTo100M() {
        assertThat(pricing.formatPrice(1000000)).isEqualTo("1.00M");
        assertThat(pricing.formatPrice(1234567)).isEqualTo("1.23M");
        assertThat(pricing.formatPrice(99900000)).isEqualTo("99.90M");
    }

    @Test
    @DisplayName("formatPrice() should format 100M+ as whole millions")
    void testFormatPrice100MTo1B() {
        assertThat(pricing.formatPrice(100000000)).isEqualTo("100M");
        assertThat(pricing.formatPrice(123456789)).isEqualTo("123M");
        assertThat(pricing.formatPrice(999000000)).isEqualTo("999M");
    }

    @Test
    @DisplayName("formatPrice() should format 1B+ with 2 decimals")
    void testFormatPrice1BAndAbove() {
        assertThat(pricing.formatPrice(1000000000)).isEqualTo("1.00B");
        assertThat(pricing.formatPrice(1234567890)).isEqualTo("1.23B");
        assertThat(pricing.formatPrice(99900000000L)).isEqualTo("99.90B");
    }

    @Test
    @DisplayName("formatMultiplier() should format as 1x for first claim")
    void testFormatMultiplierFirst() {
        assertThat(pricing.formatMultiplier(1)).isEqualTo("1x");
        assertThat(pricing.formatMultiplier(0)).isEqualTo("1x");
        assertThat(pricing.formatMultiplier(-1)).isEqualTo("1x");
    }

    @Test
    @DisplayName("formatMultiplier() should format with %.2g precision")
    void testFormatMultiplierExponential() {
        // Claim 2: 1.5x
        assertThat(pricing.formatMultiplier(2)).isEqualTo("1.5x");

        // Claim 3: 2.25x
        assertThat(pricing.formatMultiplier(3)).isEqualTo("2.3x");

        // Claim 4: 3.375x
        assertThat(pricing.formatMultiplier(4)).isEqualTo("3.4x");

        // Claim 5: 5.0625x
        assertThat(pricing.formatMultiplier(5)).isEqualTo("5.1x");
    }

    @Test
    @DisplayName("Deprecated getMaxChunks() should return maxChunksPerClaim")
    void testDeprecatedGetMaxChunks() {
        assertThat(pricing.getMaxChunks()).isEqualTo(MAX_CHUNKS);
    }
}
