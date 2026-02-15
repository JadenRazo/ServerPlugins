package net.serverplugins.arcade.games.baccarat;

import java.util.*;
import net.serverplugins.arcade.games.GameResult;

public class BaccaratGame {

    public enum BetSide {
        PLAYER,
        BANKER,
        TIE
    }

    public enum Outcome {
        PLAYER_WIN,
        BANKER_WIN,
        TIE
    }

    private final UUID playerId;
    private final double bet;
    private final BetSide betSide;
    private final List<Card> playerHand = new ArrayList<>();
    private final List<Card> bankerHand = new ArrayList<>();
    private final List<Card> deck = new ArrayList<>();
    private Outcome outcome;
    private GameResult result;

    public BaccaratGame(UUID playerId, double bet, BetSide betSide) {
        this.playerId = playerId;
        this.bet = bet;
        this.betSide = betSide;
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

    private Card drawCard() {
        if (deck.isEmpty()) {
            initializeDeck();
            shuffleDeck();
        }
        return deck.remove(0);
    }

    /** Deal initial two cards to each hand and play out the game automatically. */
    public void play() {
        // Deal initial cards: Player, Banker, Player, Banker
        playerHand.add(drawCard());
        bankerHand.add(drawCard());
        playerHand.add(drawCard());
        bankerHand.add(drawCard());

        int playerValue = getHandValue(playerHand);
        int bankerValue = getHandValue(bankerHand);

        // Check for naturals (8 or 9)
        if (playerValue >= 8 || bankerValue >= 8) {
            determineOutcome();
            return;
        }

        // Player third card rule
        Card playerThirdCard = null;
        if (playerValue <= 5) {
            playerThirdCard = drawCard();
            playerHand.add(playerThirdCard);
        }

        // Banker third card rule
        if (playerThirdCard == null) {
            // Player stood - banker draws on 0-5, stands on 6-7
            if (bankerValue <= 5) {
                bankerHand.add(drawCard());
            }
        } else {
            // Player drew - banker follows the tableau
            int playerThirdCardValue = getCardValue(playerThirdCard);
            boolean bankerDraws = shouldBankerDraw(bankerValue, playerThirdCardValue);
            if (bankerDraws) {
                bankerHand.add(drawCard());
            }
        }

        determineOutcome();
    }

    /**
     * Determines whether the banker should draw a third card based on the banker's current total
     * and the player's third card value.
     */
    private boolean shouldBankerDraw(int bankerTotal, int playerThirdCardValue) {
        return switch (bankerTotal) {
            case 0, 1, 2 -> true;
            case 3 -> playerThirdCardValue != 8;
            case 4 -> playerThirdCardValue >= 2 && playerThirdCardValue <= 7;
            case 5 -> playerThirdCardValue >= 4 && playerThirdCardValue <= 7;
            case 6 -> playerThirdCardValue == 6 || playerThirdCardValue == 7;
            default -> false; // 7 stands
        };
    }

    private void determineOutcome() {
        int playerValue = getHandValue(playerHand);
        int bankerValue = getHandValue(bankerHand);

        if (playerValue > bankerValue) {
            outcome = Outcome.PLAYER_WIN;
        } else if (bankerValue > playerValue) {
            outcome = Outcome.BANKER_WIN;
        } else {
            outcome = Outcome.TIE;
        }
    }

    /** Calculate the result based on bet side and outcome. */
    public GameResult calculateResult(
            double playerMultiplier, double bankerMultiplier, double tieMultiplier) {
        if (outcome == Outcome.TIE) {
            if (betSide == BetSide.TIE) {
                result = GameResult.win(bet, tieMultiplier, "Tie! You win!");
            } else {
                // Player and Banker bets push on tie
                result = GameResult.push(bet);
            }
        } else if (outcome == Outcome.PLAYER_WIN) {
            if (betSide == BetSide.PLAYER) {
                result = GameResult.win(bet, playerMultiplier, "Player wins!");
            } else {
                result =
                        GameResult.lose(
                                bet, betSide == BetSide.TIE ? "Not a tie!" : "Player wins!");
            }
        } else {
            // BANKER_WIN
            if (betSide == BetSide.BANKER) {
                result = GameResult.win(bet, bankerMultiplier, "Banker wins!");
            } else {
                result =
                        GameResult.lose(
                                bet, betSide == BetSide.TIE ? "Not a tie!" : "Banker wins!");
            }
        }
        return result;
    }

    public static int getHandValue(List<Card> hand) {
        int total = 0;
        for (Card card : hand) {
            total += getCardValue(card);
        }
        return total % 10;
    }

    public static int getCardValue(Card card) {
        return card.rank().getBaccaratValue();
    }

    public int getPlayerValue() {
        return getHandValue(playerHand);
    }

    public int getBankerValue() {
        return getHandValue(bankerHand);
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public double getBet() {
        return bet;
    }

    public BetSide getBetSide() {
        return betSide;
    }

    public List<Card> getPlayerHand() {
        return playerHand;
    }

    public List<Card> getBankerHand() {
        return bankerHand;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public GameResult getResult() {
        return result;
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
        ACE("A", 1),
        TWO("2", 2),
        THREE("3", 3),
        FOUR("4", 4),
        FIVE("5", 5),
        SIX("6", 6),
        SEVEN("7", 7),
        EIGHT("8", 8),
        NINE("9", 9),
        TEN("10", 0),
        JACK("J", 0),
        QUEEN("Q", 0),
        KING("K", 0);

        private final String symbol;
        private final int baccaratValue;

        Rank(String symbol, int baccaratValue) {
            this.symbol = symbol;
            this.baccaratValue = baccaratValue;
        }

        public String getSymbol() {
            return symbol;
        }

        public int getBaccaratValue() {
            return baccaratValue;
        }
    }
}
