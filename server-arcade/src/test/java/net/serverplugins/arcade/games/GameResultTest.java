package net.serverplugins.arcade.games;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;

/**
 * Test suite for GameResult class. Tests win/lose/push results, overflow protection, and safe
 * multiplication.
 */
class GameResultTest {

    @Test
    void testWinResult() {
        GameResult result = GameResult.win(100.0, 2.0);

        assertTrue(result.won(), "Result should be a win");
        assertEquals(100.0, result.bet(), "Bet should be 100");
        assertEquals(2.0, result.multiplier(), "Multiplier should be 2.0");
        assertEquals(200.0, result.payout(), "Payout should be 200 (100 * 2.0)");
        assertNull(result.message(), "Message should be null");
    }

    @Test
    void testWinResultWithMessage() {
        GameResult result = GameResult.win(100.0, 3.0, "Triple win!");

        assertTrue(result.won(), "Result should be a win");
        assertEquals(100.0, result.bet(), "Bet should be 100");
        assertEquals(3.0, result.multiplier(), "Multiplier should be 3.0");
        assertEquals(300.0, result.payout(), "Payout should be 300 (100 * 3.0)");
        assertEquals("Triple win!", result.message(), "Message should match");
    }

    @Test
    void testLoseResult() {
        GameResult result = GameResult.lose(100.0);

        assertFalse(result.won(), "Result should be a loss");
        assertEquals(100.0, result.bet(), "Bet should be 100");
        assertEquals(0, result.multiplier(), "Multiplier should be 0");
        assertEquals(0, result.payout(), "Payout should be 0");
        assertNull(result.message(), "Message should be null");
    }

    @Test
    void testLoseResultWithMessage() {
        GameResult result = GameResult.lose(100.0, "Better luck next time!");

        assertFalse(result.won(), "Result should be a loss");
        assertEquals(100.0, result.bet(), "Bet should be 100");
        assertEquals(0, result.multiplier(), "Multiplier should be 0");
        assertEquals(0, result.payout(), "Payout should be 0");
        assertEquals("Better luck next time!", result.message(), "Message should match");
    }

    @Test
    void testPushResult() {
        GameResult result = GameResult.push(100.0);

        assertFalse(result.won(), "Push should not count as won");
        assertEquals(100.0, result.bet(), "Bet should be 100");
        assertEquals(1.0, result.multiplier(), "Push multiplier should be 1.0");
        assertEquals(100.0, result.payout(), "Payout should equal bet (returned)");
        assertEquals("Push - bet returned", result.message(), "Push should have message");
    }

    @Test
    void testOverflowProtection() {
        // Test bet * multiplier that would overflow Integer.MAX_VALUE
        double hugeBet = Integer.MAX_VALUE;
        double largeMultiplier = 10.0;

        // Capture System.err to verify warning is logged
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(errContent));

        GameResult result = GameResult.win(hugeBet, largeMultiplier);

        // Restore System.err
        System.setErr(originalErr);

        assertTrue(result.won(), "Result should still be a win");
        assertEquals(
                Integer.MAX_VALUE, result.payout(), "Payout should be capped at Integer.MAX_VALUE");

        String errorOutput = errContent.toString();
        assertTrue(errorOutput.contains("SECURITY WARNING"), "Should log security warning");
        assertTrue(
                errorOutput.contains("overflow prevented"), "Should mention overflow prevention");
    }

    @Test
    void testSafeMultiplyBelowThreshold() {
        // Test normal multiplication that doesn't overflow
        GameResult result = GameResult.win(1000.0, 2.0);

        assertEquals(2000.0, result.payout(), "Normal multiplication should work correctly");
    }

    @Test
    void testSafeMultiplyAtThreshold() {
        // Test multiplication right at the threshold
        double bet = Integer.MAX_VALUE;
        double multiplier = 1.0;

        GameResult result = GameResult.win(bet, multiplier);

        assertEquals(Integer.MAX_VALUE, result.payout(), "At-threshold multiplication should work");
    }

    @Test
    void testSafeMultiplyAboveThreshold() {
        // Test multiplication that exceeds threshold
        double bet = Integer.MAX_VALUE / 2;
        double multiplier = 3.0; // This would exceed Integer.MAX_VALUE

        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(errContent));

        GameResult result = GameResult.win(bet, multiplier);

        System.setErr(originalErr);

        assertEquals(Integer.MAX_VALUE, result.payout(), "Should cap at Integer.MAX_VALUE");
        assertTrue(
                errContent.toString().contains("overflow prevented"),
                "Should log overflow warning");
    }

    @Test
    void testZeroBet() {
        GameResult result = GameResult.win(0.0, 10.0);

        assertEquals(0.0, result.payout(), "Zero bet should result in zero payout");
    }

    @Test
    void testZeroMultiplier() {
        GameResult result = GameResult.win(1000.0, 0.0);

        assertEquals(0.0, result.payout(), "Zero multiplier should result in zero payout");
    }

    @Test
    void testNegativeBet() {
        // Negative bet shouldn't happen in practice, but test overflow protection
        GameResult result = GameResult.win(-100.0, 2.0);

        assertEquals(
                -200.0, result.payout(), "Negative bet should work (though invalid in practice)");
    }

    @Test
    void testNegativeMultiplier() {
        // Negative multiplier shouldn't happen in practice
        GameResult result = GameResult.win(100.0, -2.0);

        assertEquals(
                -200.0,
                result.payout(),
                "Negative multiplier should work (though invalid in practice)");
    }

    @Test
    void testVerySmallBet() {
        GameResult result = GameResult.win(0.01, 2.0);

        assertEquals(0.02, result.payout(), 0.001, "Very small bet should work correctly");
    }

    @Test
    void testVeryLargeMultiplier() {
        // Test with very large multiplier
        double bet = 100.0;
        double multiplier = 1000000.0;

        GameResult result = GameResult.win(bet, multiplier);

        // 100 * 1,000,000 = 100,000,000 which is less than Integer.MAX_VALUE
        assertEquals(100000000.0, result.payout(), "Large multiplier should work if result fits");
    }

    @Test
    void testDecimalMultiplier() {
        GameResult result = GameResult.win(100.0, 2.5);

        assertEquals(250.0, result.payout(), "Decimal multiplier should work correctly");
    }

    @Test
    void testBlackjackMultiplier() {
        // Test typical blackjack 3:2 payout (2.5x)
        GameResult result = GameResult.win(100.0, 2.5);

        assertTrue(result.won(), "Blackjack should be a win");
        assertEquals(250.0, result.payout(), "Blackjack should pay 2.5x");
    }

    @Test
    void testSlotsTripleMatch() {
        // Test typical slots triple match (10x)
        GameResult result = GameResult.win(100.0, 10.0, "Three 7s!");

        assertTrue(result.won(), "Triple match should be a win");
        assertEquals(1000.0, result.payout(), "Triple match should pay 10x");
        assertEquals("Three 7s!", result.message(), "Should have win message");
    }

    @Test
    void testSlotsDoubleMatch() {
        // Test typical slots double match (30% of symbol multiplier)
        GameResult result = GameResult.win(100.0, 3.0, "Two cherries!");

        assertTrue(result.won(), "Double match should be a win");
        assertEquals(300.0, result.payout(), "Should calculate payout correctly");
    }

    @Test
    void testRecordEquality() {
        GameResult result1 = GameResult.win(100.0, 2.0);
        GameResult result2 = GameResult.win(100.0, 2.0);

        assertEquals(result1, result2, "Identical results should be equal");
    }

    @Test
    void testRecordInequality() {
        GameResult result1 = GameResult.win(100.0, 2.0);
        GameResult result2 = GameResult.win(100.0, 3.0);

        assertNotEquals(result1, result2, "Different results should not be equal");
    }

    @Test
    void testRecordHashCode() {
        GameResult result1 = GameResult.win(100.0, 2.0);
        GameResult result2 = GameResult.win(100.0, 2.0);

        assertEquals(
                result1.hashCode(),
                result2.hashCode(),
                "Identical results should have same hash code");
    }

    @Test
    void testRecordToString() {
        GameResult result = GameResult.win(100.0, 2.0, "You won!");

        String toString = result.toString();

        assertNotNull(toString, "toString should not be null");
        assertTrue(toString.contains("100"), "toString should contain bet");
        assertTrue(toString.contains("2"), "toString should contain multiplier");
    }

    @Test
    void testMaxIntegerEdgeCase() {
        // Test exactly at Integer.MAX_VALUE
        double bet = Integer.MAX_VALUE;
        double multiplier = 1.0;

        GameResult result = GameResult.win(bet, multiplier);

        assertEquals(Integer.MAX_VALUE, result.payout(), "Should handle Integer.MAX_VALUE exactly");
    }

    @Test
    void testJustOverMaxInteger() {
        // Test just over Integer.MAX_VALUE
        double bet = Integer.MAX_VALUE;
        double multiplier = 1.00001;

        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(errContent));

        GameResult result = GameResult.win(bet, multiplier);

        System.setErr(originalErr);

        assertEquals(Integer.MAX_VALUE, result.payout(), "Should cap just over Integer.MAX_VALUE");
        assertTrue(
                errContent.toString().contains("overflow prevented"),
                "Should log overflow warning");
    }

    @Test
    void testExtremeOverflow() {
        // Test extreme overflow scenario
        double bet = Double.MAX_VALUE / 2;
        double multiplier = 10.0;

        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(errContent));

        GameResult result = GameResult.win(bet, multiplier);

        System.setErr(originalErr);

        assertEquals(Integer.MAX_VALUE, result.payout(), "Should cap extreme overflow");
        assertTrue(
                errContent.toString().contains("overflow prevented"),
                "Should log overflow warning");
    }

    @Test
    void testMultipleOverflowsLogged() {
        // Test that multiple overflow attempts are logged
        ByteArrayOutputStream errContent = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(errContent));

        GameResult.win(Integer.MAX_VALUE, 2.0);
        GameResult.win(Integer.MAX_VALUE, 3.0);
        GameResult.win(Integer.MAX_VALUE, 4.0);

        System.setErr(originalErr);

        String errorOutput = errContent.toString();
        int warningCount = errorOutput.split("SECURITY WARNING").length - 1;

        assertEquals(3, warningCount, "Should log warning for each overflow attempt");
    }

    @Test
    void testLoseResultNeverHasPayout() {
        GameResult result = GameResult.lose(1000000.0);

        assertEquals(
                0, result.payout(), "Lose result should always have 0 payout regardless of bet");
    }

    @Test
    void testPushAlwaysReturnsBet() {
        GameResult result1 = GameResult.push(100.0);
        GameResult result2 = GameResult.push(500.0);
        GameResult result3 = GameResult.push(10000.0);

        assertEquals(100.0, result1.payout(), "Push should return bet amount");
        assertEquals(500.0, result2.payout(), "Push should return bet amount");
        assertEquals(10000.0, result3.payout(), "Push should return bet amount");
    }
}
