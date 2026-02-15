package net.serverplugins.arcade.games.blackjack;

import java.util.*;
import net.serverplugins.arcade.games.GameResult;

public class BlackjackGame {

    private final UUID playerId;
    private double bet;
    private final List<Card> playerHand = new ArrayList<>();
    private final List<Card> dealerHand = new ArrayList<>();
    private final List<Card> deck = new ArrayList<>();
    private GameResult result;
    private boolean dealerCardHidden = true;

    // Advanced blackjack features
    private boolean hasSplit = false;
    private List<Card> splitHand = null;
    private double splitBet = 0;
    private boolean hasInsurance = false;
    private double insuranceBet = 0;
    private boolean hasSurrendered = false;
    private boolean hasDoubledDown = false;

    public BlackjackGame(UUID playerId, double bet) {
        this.playerId = playerId;
        this.bet = bet;
        initializeDeck();
        shuffleDeck();
    }

    private void initializeDeck() {
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                deck.add(new Card(suit, rank));
            }
        }
    }

    private void shuffleDeck() {
        Collections.shuffle(deck);
    }

    public void dealInitialCards() {
        playerHand.add(drawCard());
        dealerHand.add(drawCard());
        playerHand.add(drawCard());
        dealerHand.add(drawCard());
    }

    private Card drawCard() {
        if (deck.isEmpty()) {
            initializeDeck();
            shuffleDeck();
        }
        return deck.remove(0);
    }

    public void playerHit() {
        playerHand.add(drawCard());
    }

    public void dealerHit() {
        dealerHand.add(drawCard());
        dealerCardHidden = false;
    }

    public int getPlayerValue() {
        return calculateHandValue(playerHand);
    }

    public int getDealerValue() {
        return calculateHandValue(dealerHand);
    }

    public int getDealerVisibleValue() {
        if (dealerCardHidden && dealerHand.size() > 1) {
            return calculateHandValue(Collections.singletonList(dealerHand.get(0)));
        }
        return getDealerValue();
    }

    private int calculateHandValue(List<Card> hand) {
        int value = 0;
        int aces = 0;

        for (Card card : hand) {
            value += card.rank().getValue();
            if (card.rank() == Rank.ACE) aces++;
        }

        while (value > 21 && aces > 0) {
            value -= 10;
            aces--;
        }

        return value;
    }

    public boolean isPlayerBlackjack() {
        return playerHand.size() == 2 && getPlayerValue() == 21;
    }

    public boolean isDealerBlackjack() {
        return dealerHand.size() == 2 && getDealerValue() == 21;
    }

    public void doubleBet() {
        this.bet *= 2;
        this.hasDoubledDown = true;
    }

    // ===== Split functionality =====

    /**
     * Check if player can split their hand. Split is allowed when player has exactly 2 cards of the
     * same rank.
     */
    public boolean canSplit() {
        return playerHand.size() == 2
                && playerHand.get(0).rank() == playerHand.get(1).rank()
                && !hasSplit;
    }

    /** Split the player's hand into two separate hands. */
    public void split() {
        if (!canSplit()) return;
        this.hasSplit = true;
        this.splitHand = new ArrayList<>();
        this.splitBet = bet;

        // Move second card to split hand
        splitHand.add(playerHand.remove(1));

        // Draw one card for each hand
        playerHand.add(drawCard());
        splitHand.add(drawCard());
    }

    // ===== Insurance functionality =====

    /**
     * Check if player can take insurance. Insurance is offered when dealer's face-up card is an
     * Ace.
     */
    public boolean canInsurance() {
        return !dealerHand.isEmpty()
                && dealerHand.get(0).rank() == Rank.ACE
                && playerHand.size() == 2
                && !hasInsurance;
    }

    /** Take insurance bet (half of original bet). Pays 2:1 if dealer has blackjack. */
    public void takeInsurance() {
        if (!canInsurance()) return;
        this.hasInsurance = true;
        this.insuranceBet = bet / 2;
    }

    // ===== Surrender functionality =====

    /** Check if player can surrender. Surrender is only allowed on initial 2 cards. */
    public boolean canSurrender() {
        return playerHand.size() == 2 && !hasSurrendered && !hasSplit && !hasDoubledDown;
    }

    /** Surrender the hand and forfeit half the bet. */
    public void surrender() {
        if (!canSurrender()) return;
        this.hasSurrendered = true;
    }

    // ===== Double Down functionality =====

    /**
     * Check if player can double down. Double down is allowed on initial 2 cards with total 9, 10,
     * or 11.
     */
    public boolean canDoubleDown() {
        if (playerHand.size() != 2 || hasDoubledDown) return false;
        int value = getPlayerValue();
        return value >= 9 && value <= 11;
    }

    // ===== Payout calculation =====

    /**
     * Calculate the payout for a winning hand. Natural blackjack pays 3:2, regular wins pay 1:1.
     */
    public double calculatePayout() {
        if (hasSurrendered) {
            return bet / 2; // Return half on surrender
        }

        if (isPlayerBlackjack() && !isDealerBlackjack()) {
            return bet * 2.5; // 3:2 payout for natural BJ (original bet + 1.5x winnings)
        }

        // Regular win pays 1:1
        return bet * 2; // Original bet + winnings
    }

    /** Calculate insurance payout. Insurance pays 2:1 if dealer has blackjack. */
    public double calculateInsurancePayout() {
        if (hasInsurance && isDealerBlackjack()) {
            return insuranceBet * 3; // Original insurance + 2:1 winnings
        }
        return 0;
    }

    // ===== Getters for new features =====

    public boolean hasSplit() {
        return hasSplit;
    }

    public List<Card> getSplitHand() {
        return splitHand;
    }

    public double getSplitBet() {
        return splitBet;
    }

    public boolean hasInsurance() {
        return hasInsurance;
    }

    public double getInsuranceBet() {
        return insuranceBet;
    }

    public boolean hasSurrendered() {
        return hasSurrendered;
    }

    public boolean hasDoubledDown() {
        return hasDoubledDown;
    }

    public int getSplitHandValue() {
        return splitHand != null ? calculateHandValue(splitHand) : 0;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public double getBet() {
        return bet;
    }

    public List<Card> getPlayerHand() {
        return playerHand;
    }

    public List<Card> getDealerHand() {
        return dealerHand;
    }

    public GameResult getResult() {
        return result;
    }

    public void setResult(GameResult result) {
        this.result = result;
    }

    public boolean isDealerCardHidden() {
        return dealerCardHidden;
    }

    public void revealDealerCard() {
        dealerCardHidden = false;
    }

    public record Card(Suit suit, Rank rank) {
        @Override
        public String toString() {
            return rank.getSymbol() + suit.getSymbol();
        }
    }

    public enum Suit {
        HEARTS("\u2665"),
        DIAMONDS("\u2666"),
        CLUBS("\u2663"),
        SPADES("\u2660");
        private final String symbol;

        Suit(String symbol) {
            this.symbol = symbol;
        }

        public String getSymbol() {
            return symbol;
        }
    }

    public enum Rank {
        ACE("A", 11),
        TWO("2", 2),
        THREE("3", 3),
        FOUR("4", 4),
        FIVE("5", 5),
        SIX("6", 6),
        SEVEN("7", 7),
        EIGHT("8", 8),
        NINE("9", 9),
        TEN("10", 10),
        JACK("J", 10),
        QUEEN("Q", 10),
        KING("K", 10);

        private final String symbol;
        private final int value;

        Rank(String symbol, int value) {
            this.symbol = symbol;
            this.value = value;
        }

        public String getSymbol() {
            return symbol;
        }

        public int getValue() {
            return value;
        }
    }
}
