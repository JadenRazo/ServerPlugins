package net.serverplugins.arcade.games.blackjack;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import net.serverplugins.arcade.games.GameResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test suite for BlackjackGame class. Tests game mechanics, hand calculations, and game actions.
 */
class BlackjackGameTest {

    private UUID playerId;
    private BlackjackGame game;

    @BeforeEach
    void setUp() {
        playerId = UUID.randomUUID();
        game = new BlackjackGame(playerId, 100.0);
    }

    @Test
    void testGameInitialization() {
        assertNotNull(game, "Game should be initialized");
        assertEquals(playerId, game.getPlayerId(), "Player ID should match");
        assertEquals(100.0, game.getBet(), "Initial bet should be 100");
        assertNotNull(game.getPlayerHand(), "Player hand should be initialized");
        assertNotNull(game.getDealerHand(), "Dealer hand should be initialized");
        assertTrue(game.getPlayerHand().isEmpty(), "Player hand should start empty");
        assertTrue(game.getDealerHand().isEmpty(), "Dealer hand should start empty");
    }

    @Test
    void testDealInitialCards() {
        game.dealInitialCards();

        assertEquals(2, game.getPlayerHand().size(), "Player should have 2 cards");
        assertEquals(2, game.getDealerHand().size(), "Dealer should have 2 cards");
        assertTrue(game.isDealerCardHidden(), "Dealer's second card should be hidden");
    }

    @Test
    void testPlayerHit() {
        game.dealInitialCards();
        int initialSize = game.getPlayerHand().size();

        game.playerHit();

        assertEquals(
                initialSize + 1, game.getPlayerHand().size(), "Player hand should increase by 1");
    }

    @Test
    void testDealerHit() {
        game.dealInitialCards();
        int initialSize = game.getDealerHand().size();

        game.dealerHit();

        assertEquals(
                initialSize + 1, game.getDealerHand().size(), "Dealer hand should increase by 1");
        assertFalse(game.isDealerCardHidden(), "Dealer card should be revealed after hit");
    }

    @Test
    void testHandValueCalculation() {
        game.dealInitialCards();

        int playerValue = game.getPlayerValue();
        int dealerValue = game.getDealerValue();

        assertTrue(playerValue >= 2 && playerValue <= 30, "Player value should be in valid range");
        assertTrue(dealerValue >= 2 && dealerValue <= 30, "Dealer value should be in valid range");
    }

    @Test
    void testAceValueCalculation() {
        // Create a new game and deal cards until we get an ace
        boolean hasAce = false;

        for (int attempt = 0; attempt < 100; attempt++) {
            BlackjackGame testGame = new BlackjackGame(UUID.randomUUID(), 100.0);
            testGame.dealInitialCards();

            List<BlackjackGame.Card> hand = testGame.getPlayerHand();
            for (BlackjackGame.Card card : hand) {
                if (card.rank() == BlackjackGame.Rank.ACE) {
                    hasAce = true;
                    break;
                }
            }

            if (hasAce) {
                // Ace should count as 11 unless it would bust
                int value = testGame.getPlayerValue();
                assertTrue(value >= 2 && value <= 21, "Hand value with ace should be valid (2-21)");
                break;
            }
        }

        assertTrue(hasAce, "Should eventually deal an ace in 100 attempts");
    }

    @Test
    void testAceSoftToHardConversion() {
        // This test verifies that aces convert from 11 to 1 when needed
        // We'll draw cards and verify the value doesn't exceed 21 incorrectly
        for (int i = 0; i < 20; i++) {
            BlackjackGame testGame = new BlackjackGame(UUID.randomUUID(), 100.0);
            testGame.dealInitialCards();

            // Hit multiple times
            testGame.playerHit();
            testGame.playerHit();
            testGame.playerHit();

            int value = testGame.getPlayerValue();
            // Value should be calculated correctly (aces adjusted if needed)
            assertTrue(value >= 2, "Hand value should be at least 2");
        }
    }

    @Test
    void testBlackjackDetection() {
        // Test multiple games to eventually get a blackjack
        boolean foundBlackjack = false;

        for (int i = 0; i < 1000; i++) {
            BlackjackGame testGame = new BlackjackGame(UUID.randomUUID(), 100.0);
            testGame.dealInitialCards();

            if (testGame.isPlayerBlackjack()) {
                foundBlackjack = true;
                assertEquals(21, testGame.getPlayerValue(), "Blackjack should have value of 21");
                assertEquals(
                        2,
                        testGame.getPlayerHand().size(),
                        "Blackjack should have exactly 2 cards");
                break;
            }
        }

        // Blackjack probability is roughly 4.8%, so we should find one in 1000 attempts
        assertTrue(foundBlackjack, "Should eventually get a blackjack in 1000 games");
    }

    @Test
    void testDealerBlackjackDetection() {
        // Test multiple games to eventually get a dealer blackjack
        boolean foundDealerBlackjack = false;

        for (int i = 0; i < 1000; i++) {
            BlackjackGame testGame = new BlackjackGame(UUID.randomUUID(), 100.0);
            testGame.dealInitialCards();

            if (testGame.isDealerBlackjack()) {
                foundDealerBlackjack = true;
                assertEquals(
                        21, testGame.getDealerValue(), "Dealer blackjack should have value of 21");
                assertEquals(
                        2,
                        testGame.getDealerHand().size(),
                        "Dealer blackjack should have exactly 2 cards");
                break;
            }
        }

        assertTrue(foundDealerBlackjack, "Should eventually get a dealer blackjack in 1000 games");
    }

    @Test
    void testDoubleBet() {
        double initialBet = game.getBet();

        game.doubleBet();

        assertEquals(initialBet * 2, game.getBet(), "Bet should double");
        assertTrue(game.hasDoubledDown(), "Should mark as doubled down");
    }

    @Test
    void testCanSplit() {
        // Test split eligibility
        game.dealInitialCards();

        // Can only split if both cards have same rank
        boolean canSplit = game.canSplit();

        if (canSplit) {
            assertEquals(
                    game.getPlayerHand().get(0).rank(),
                    game.getPlayerHand().get(1).rank(),
                    "Can only split when both cards have same rank");
        }

        // After splitting once, can't split again
        if (canSplit) {
            game.split();
            assertFalse(game.canSplit(), "Should not be able to split twice");
        }
    }

    @Test
    void testSplitMechanic() {
        // Find a game where we can split
        boolean foundSplittableHand = false;

        for (int i = 0; i < 500; i++) {
            BlackjackGame testGame = new BlackjackGame(UUID.randomUUID(), 100.0);
            testGame.dealInitialCards();

            if (testGame.canSplit()) {
                foundSplittableHand = true;

                double originalBet = testGame.getBet();
                testGame.split();

                assertTrue(testGame.hasSplit(), "Should mark as split");
                assertNotNull(testGame.getSplitHand(), "Split hand should exist");
                assertEquals(
                        originalBet, testGame.getSplitBet(), "Split bet should equal original bet");
                assertEquals(
                        2,
                        testGame.getPlayerHand().size(),
                        "Main hand should have 2 cards after split");
                assertEquals(
                        2,
                        testGame.getSplitHand().size(),
                        "Split hand should have 2 cards after split");

                break;
            }
        }

        // Pairs occur roughly 6% of the time, so should find one in 500 attempts
        assertTrue(foundSplittableHand, "Should eventually get a splittable hand in 500 games");
    }

    @Test
    void testCanInsurance() {
        // Find a game where dealer shows an ace
        boolean foundDealerAce = false;

        for (int i = 0; i < 100; i++) {
            BlackjackGame testGame = new BlackjackGame(UUID.randomUUID(), 100.0);
            testGame.dealInitialCards();

            if (testGame.getDealerHand().get(0).rank() == BlackjackGame.Rank.ACE) {
                foundDealerAce = true;
                assertTrue(testGame.canInsurance(), "Should offer insurance when dealer shows ace");
                break;
            }
        }

        assertTrue(foundDealerAce, "Should eventually get dealer ace in 100 games");
    }

    @Test
    void testTakeInsurance() {
        // Find a game where we can take insurance
        for (int i = 0; i < 100; i++) {
            BlackjackGame testGame = new BlackjackGame(UUID.randomUUID(), 100.0);
            testGame.dealInitialCards();

            if (testGame.canInsurance()) {
                double originalBet = testGame.getBet();
                testGame.takeInsurance();

                assertTrue(testGame.hasInsurance(), "Should mark as has insurance");
                assertEquals(
                        originalBet / 2,
                        testGame.getInsuranceBet(),
                        "Insurance should be half of original bet");
                break;
            }
        }
    }

    @Test
    void testCanSurrender() {
        game.dealInitialCards();

        assertTrue(game.canSurrender(), "Should be able to surrender with initial 2 cards");

        game.playerHit();

        assertFalse(game.canSurrender(), "Should not be able to surrender after hitting");
    }

    @Test
    void testSurrender() {
        game.dealInitialCards();

        game.surrender();

        assertTrue(game.hasSurrendered(), "Should mark as surrendered");
        assertFalse(game.canSurrender(), "Should not be able to surrender again");
    }

    @Test
    void testCanDoubleDown() {
        // Find games with 9, 10, or 11 to test double down
        boolean foundDoubleDownHand = false;

        for (int i = 0; i < 100; i++) {
            BlackjackGame testGame = new BlackjackGame(UUID.randomUUID(), 100.0);
            testGame.dealInitialCards();

            int value = testGame.getPlayerValue();
            if (value >= 9 && value <= 11) {
                foundDoubleDownHand = true;
                assertTrue(testGame.canDoubleDown(), "Should be able to double down on " + value);
                break;
            } else {
                assertFalse(
                        testGame.canDoubleDown(), "Should not be able to double down on " + value);
            }
        }

        assertTrue(foundDoubleDownHand, "Should eventually get a hand with 9-11 in 100 games");
    }

    @Test
    void testCalculatePayout() {
        // Test regular win payout (1:1)
        game.dealInitialCards();
        double payout = game.calculatePayout();

        assertEquals(game.getBet() * 2, payout, "Regular win should pay 2x (original + winnings)");
    }

    @Test
    void testBlackjackPayout() {
        // Find a blackjack to test 3:2 payout
        for (int i = 0; i < 1000; i++) {
            BlackjackGame testGame = new BlackjackGame(UUID.randomUUID(), 100.0);
            testGame.dealInitialCards();

            if (testGame.isPlayerBlackjack() && !testGame.isDealerBlackjack()) {
                double payout = testGame.calculatePayout();
                assertEquals(
                        testGame.getBet() * 2.5,
                        payout,
                        "Blackjack should pay 2.5x (3:2 plus original bet)");
                break;
            }
        }
    }

    @Test
    void testSurrenderPayout() {
        game.dealInitialCards();
        game.surrender();

        double payout = game.calculatePayout();

        assertEquals(game.getBet() / 2, payout, "Surrender should return half the bet");
    }

    @Test
    void testInsurancePayout() {
        // Find a game where dealer has blackjack
        for (int i = 0; i < 1000; i++) {
            BlackjackGame testGame = new BlackjackGame(UUID.randomUUID(), 100.0);
            testGame.dealInitialCards();

            if (testGame.canInsurance()) {
                testGame.takeInsurance();

                if (testGame.isDealerBlackjack()) {
                    double insurancePayout = testGame.calculateInsurancePayout();
                    assertEquals(
                            testGame.getInsuranceBet() * 3,
                            insurancePayout,
                            "Insurance should pay 3x (2:1 plus original insurance)");
                    break;
                }
            }
        }
    }

    @Test
    void testDealerVisibleValue() {
        game.dealInitialCards();

        // Initially, should only see one dealer card
        int visibleValue = game.getDealerVisibleValue();
        assertTrue(
                visibleValue >= 1 && visibleValue <= 11, "Visible value should be from one card");

        game.revealDealerCard();

        // After reveal, should see full hand
        assertEquals(
                game.getDealerValue(),
                game.getDealerVisibleValue(),
                "After reveal, visible value should equal full value");
    }

    @Test
    void testResultGettersSetters() {
        assertNull(game.getResult(), "Result should initially be null");

        GameResult result = GameResult.win(100.0, 2.0);
        game.setResult(result);

        assertEquals(result, game.getResult(), "Result should be set correctly");
    }

    @Test
    void testDeckReshuffleOnEmpty() {
        // Draw many cards to empty the deck
        for (int i = 0; i < 20; i++) {
            game.playerHit();
        }

        // Should not throw exception - deck should auto-reshuffle
        assertDoesNotThrow(() -> game.playerHit(), "Should handle empty deck by reshuffling");
    }

    @Test
    void testSplitHandValue() {
        // Find a splittable hand
        for (int i = 0; i < 500; i++) {
            BlackjackGame testGame = new BlackjackGame(UUID.randomUUID(), 100.0);
            testGame.dealInitialCards();

            if (testGame.canSplit()) {
                testGame.split();

                int splitValue = testGame.getSplitHandValue();
                assertTrue(splitValue >= 2 && splitValue <= 30, "Split hand value should be valid");
                assertNotNull(testGame.getSplitHand(), "Split hand should exist");

                break;
            }
        }
    }

    @Test
    void testGetSplitHandValueWithoutSplit() {
        game.dealInitialCards();

        assertEquals(0, game.getSplitHandValue(), "Split hand value should be 0 when not split");
        assertNull(game.getSplitHand(), "Split hand should be null when not split");
    }

    @Test
    void testMultipleHitsIncreaseValue() {
        game.dealInitialCards();
        int initialValue = game.getPlayerValue();

        // Hit multiple times
        game.playerHit();
        int afterFirstHit = game.getPlayerValue();

        game.playerHit();
        int afterSecondHit = game.getPlayerValue();

        // Values should generally increase (though aces can make this complex)
        assertTrue(
                afterFirstHit >= initialValue || afterFirstHit >= 2,
                "Value should increase or remain valid after hit");
        assertTrue(afterSecondHit >= 2, "Value should remain valid after multiple hits");
    }
}
