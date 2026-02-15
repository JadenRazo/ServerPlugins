package net.serverplugins.arcade.games.blackjack;

import java.util.*;
import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.arcade.games.duo.DuoGame;
import net.serverplugins.arcade.machines.Machine;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** Instance of a blackjack game between two players. */
public class BlackjackGameInstance extends DuoGame {

    private final BlackjackType blackjackType;
    private final Random random = new Random();

    // Player hands
    private final List<BlackjackType.Card> player1Hand = new ArrayList<>();
    private final List<BlackjackType.Card> player2Hand = new ArrayList<>();

    // Player states
    private boolean player1Standing = false;
    private boolean player2Standing = false;

    // GUIs
    private BlackjackGameGui player1Gui;
    private BlackjackGameGui player2Gui;

    public BlackjackGameInstance(BlackjackType type, Machine machine, Player player1, int bet) {
        super(type, machine, player1, bet);
        this.blackjackType = type;
    }

    @Override
    protected void onGameStart() {
        // Deal initial cards (2 to each player)
        player1Hand.add(blackjackType.drawCard(random));
        player1Hand.add(blackjackType.drawCard(random));
        player2Hand.add(blackjackType.drawCard(random));
        player2Hand.add(blackjackType.drawCard(random));

        // Open GUIs
        player1Gui = new BlackjackGameGui(this, player1);
        player2Gui = new BlackjackGameGui(this, player2);

        player1Gui.open(player1);
        player2Gui.open(player2);

        TextUtil.sendSuccess(
                player1, "Game started! You have " + getHandValue(player1Hand) + " points.");
        TextUtil.sendSuccess(
                player2, "Game started! You have " + getHandValue(player2Hand) + " points.");
    }

    @Override
    protected void onTimeUpdate() {
        // Update GUIs
        if (player1Gui != null) player1Gui.update();
        if (player2Gui != null) player2Gui.update();
    }

    @Override
    protected void onTimeUp() {
        // Force both players to stand
        if (!player1Standing) stand(player1);
        if (!player2Standing) stand(player2);
    }

    @Override
    protected void onGameEnd() {
        // Close GUIs
        if (player1 != null) player1.closeInventory();
        if (player2 != null) player2.closeInventory();

        // Remove from active games
        blackjackType.removeGame(this);
    }

    /** Player hits (draws a card). */
    public void hit(Player player) {
        if (state != State.PLAYING) return;

        List<BlackjackType.Card> hand = getPlayerHand(player);
        if (hand == null) return;

        if (isPlayerStanding(player)) {
            TextUtil.sendError(player, "You already stood!");
            return;
        }

        BlackjackType.Card card = blackjackType.drawCard(random);
        hand.add(card);

        int value = getHandValue(hand);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);

        if (value > 21) {
            TextUtil.sendError(player, "Bust! You have " + value + " points.");
            stand(player); // Auto-stand on bust
        } else {
            TextUtil.send(
                    player,
                    ColorScheme.INFO
                            + "Drew "
                            + card.name
                            + " "
                            + ColorScheme.INFO
                            + "- Total: "
                            + ColorScheme.HIGHLIGHT
                            + value);
        }

        updateGuis();
        checkGameEnd();
    }

    /** Player stands (keeps current hand). */
    public void stand(Player player) {
        if (state != State.PLAYING) return;

        if (player.equals(player1)) {
            player1Standing = true;
        } else if (player.equals(player2)) {
            player2Standing = true;
        }

        TextUtil.send(
                player,
                ColorScheme.WARNING
                        + "You stand with "
                        + getHandValue(getPlayerHand(player))
                        + " points.");

        updateGuis();
        checkGameEnd();
    }

    /** Check if the game should end. */
    private void checkGameEnd() {
        if (!player1Standing || !player2Standing) return;

        int p1Value = getHandValue(player1Hand);
        int p2Value = getHandValue(player2Hand);

        boolean p1Bust = p1Value > 21;
        boolean p2Bust = p2Value > 21;

        Result result;
        if (p1Bust && p2Bust) {
            result = Result.TIE;
        } else if (p1Bust) {
            result = Result.PLAYER2_WIN;
        } else if (p2Bust) {
            result = Result.PLAYER1_WIN;
        } else if (p1Value > p2Value) {
            result = Result.PLAYER1_WIN;
        } else if (p2Value > p1Value) {
            result = Result.PLAYER2_WIN;
        } else {
            result = Result.TIE;
        }

        // Announce results
        String p1Name = player1.getName();
        String p2Name = player2.getName();

        String resultMsg =
                ColorScheme.INFO
                        + p1Name
                        + ": "
                        + ColorScheme.HIGHLIGHT
                        + p1Value
                        + (p1Bust ? " <red>(BUST)" : "")
                        + " "
                        + ColorScheme.INFO
                        + "vs "
                        + ColorScheme.INFO
                        + p2Name
                        + ": "
                        + ColorScheme.HIGHLIGHT
                        + p2Value
                        + (p2Bust ? " <red>(BUST)" : "");
        TextUtil.send(player1, resultMsg);
        TextUtil.send(player2, resultMsg);

        finish(result);
    }

    /** Calculate hand value (handling Aces). */
    public int getHandValue(List<BlackjackType.Card> hand) {
        int value = 0;
        int aces = 0;

        for (BlackjackType.Card card : hand) {
            value += card.value;
            if (card.id.equals("A")) aces++;
        }

        // Reduce Aces from 11 to 1 if busting
        while (value > 21 && aces > 0) {
            value -= 10;
            aces--;
        }

        return value;
    }

    private void updateGuis() {
        if (player1Gui != null) player1Gui.update();
        if (player2Gui != null) player2Gui.update();
    }

    public List<BlackjackType.Card> getPlayerHand(Player player) {
        if (player.equals(player1)) return player1Hand;
        if (player.equals(player2)) return player2Hand;
        return List.of();
    }

    public List<BlackjackType.Card> getOpponentHand(Player player) {
        if (player.equals(player1)) return player2Hand;
        if (player.equals(player2)) return player1Hand;
        return List.of();
    }

    public boolean isPlayerStanding(Player player) {
        if (player.equals(player1)) return player1Standing;
        if (player.equals(player2)) return player2Standing;
        return false;
    }

    public boolean isOpponentStanding(Player player) {
        if (player.equals(player1)) return player2Standing;
        if (player.equals(player2)) return player1Standing;
        return false;
    }

    public BlackjackType getBlackjackType() {
        return blackjackType;
    }
}
