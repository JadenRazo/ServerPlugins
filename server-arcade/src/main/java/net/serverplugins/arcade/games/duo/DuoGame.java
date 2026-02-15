package net.serverplugins.arcade.games.duo;

import net.milkbowl.vault.economy.Economy;
import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.machines.Machine;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/** Base class for 2-player competitive games (like Blackjack). */
public abstract class DuoGame {

    public enum State {
        WAITING_FOR_OPPONENT,
        PLAYING,
        FINISHED
    }

    public enum Result {
        PLAYER1_WIN,
        PLAYER2_WIN,
        TIE
    }

    protected final DuoGameType gameType;
    protected final Machine machine;
    protected final ServerArcade plugin;

    protected Player player1;
    protected Player player2;
    protected int bet;
    protected State state = State.WAITING_FOR_OPPONENT;
    protected Result result;

    protected BukkitTask timeoutTask;
    protected int timeLeft;

    public DuoGame(DuoGameType gameType, Machine machine, Player player1, int bet) {
        this.gameType = gameType;
        this.machine = machine;
        this.plugin = gameType.getPlugin();
        this.player1 = player1;
        this.bet = bet;
        this.timeLeft = gameType.getChallengeTime();
    }

    /** Start waiting for an opponent. */
    public void startWaiting() {
        state = State.WAITING_FOR_OPPONENT;

        // Withdraw bet from player 1
        Economy economy = ServerArcade.getEconomy();
        if (economy != null) {
            economy.withdrawPlayer(player1, bet);
        }

        // Start timeout
        timeoutTask =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                plugin,
                                () -> {
                                    timeLeft--;
                                    if (timeLeft <= 0) {
                                        // No opponent found, refund and cancel
                                        cancel("No opponent found. Game cancelled.");
                                    }
                                    onTimeUpdate();
                                },
                                20L,
                                20L);

        TextUtil.send(
                player1,
                ColorScheme.SUCCESS
                        + "Waiting for opponent... "
                        + ColorScheme.INFO
                        + "("
                        + timeLeft
                        + "s)");
    }

    /** Join as the second player. */
    public void join(Player player) {
        if (state != State.WAITING_FOR_OPPONENT) {
            TextUtil.sendError(player, "This game is no longer available!");
            return;
        }

        if (player.equals(player1)) {
            TextUtil.sendError(player, "You can't play against yourself!");
            return;
        }

        // Withdraw bet from player 2
        Economy economy = ServerArcade.getEconomy();
        if (economy != null) {
            if (economy.getBalance(player) < bet) {
                TextUtil.sendError(
                        player,
                        "You need " + ColorScheme.EMPHASIS + bet + " <red>to join this game!");
                return;
            }
            economy.withdrawPlayer(player, bet);
        }

        this.player2 = player;

        // Cancel timeout
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }

        // Start the game
        state = State.PLAYING;
        timeLeft = gameType.getGameTime();

        // Start game timer
        timeoutTask =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                plugin,
                                () -> {
                                    timeLeft--;
                                    if (timeLeft <= 0) {
                                        onTimeUp();
                                    }
                                    onTimeUpdate();
                                },
                                20L,
                                20L);

        onGameStart();
    }

    /** Cancel the game and refund. */
    public void cancel(String reason) {
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }

        Economy economy = ServerArcade.getEconomy();
        if (economy != null) {
            if (player1 != null) {
                economy.depositPlayer(player1, bet);
                TextUtil.send(
                        player1,
                        ColorScheme.WARNING + reason + " " + ColorScheme.INFO + "Bet refunded.");
            }
            if (player2 != null) {
                economy.depositPlayer(player2, bet);
                TextUtil.send(
                        player2,
                        ColorScheme.WARNING + reason + " " + ColorScheme.INFO + "Bet refunded.");
            }
        }

        state = State.FINISHED;
        onGameEnd();
    }

    /** End the game with a result. */
    public void finish(Result result) {
        this.result = result;
        state = State.FINISHED;

        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }

        Economy economy = ServerArcade.getEconomy();
        int totalPot = bet * 2;

        switch (result) {
            case PLAYER1_WIN -> {
                if (economy != null && player1 != null) {
                    economy.depositPlayer(player1, totalPot);
                }
                if (player1 != null)
                    TextUtil.send(
                            player1,
                            "<green><bold>You win!</bold> " + ColorScheme.INFO + "+" + totalPot);
                if (player2 != null)
                    TextUtil.send(
                            player2, "<red><bold>You lose!</bold> " + ColorScheme.INFO + "-" + bet);
            }
            case PLAYER2_WIN -> {
                if (economy != null && player2 != null) {
                    economy.depositPlayer(player2, totalPot);
                }
                if (player2 != null)
                    TextUtil.send(
                            player2,
                            "<green><bold>You win!</bold> " + ColorScheme.INFO + "+" + totalPot);
                if (player1 != null)
                    TextUtil.send(
                            player1, "<red><bold>You lose!</bold> " + ColorScheme.INFO + "-" + bet);
            }
            case TIE -> {
                // Refund both
                if (economy != null) {
                    if (player1 != null) economy.depositPlayer(player1, bet);
                    if (player2 != null) economy.depositPlayer(player2, bet);
                }
                if (player1 != null)
                    TextUtil.send(
                            player1,
                            "<yellow><bold>Tie!</bold> " + ColorScheme.INFO + "Bet refunded.");
                if (player2 != null)
                    TextUtil.send(
                            player2,
                            "<yellow><bold>Tie!</bold> " + ColorScheme.INFO + "Bet refunded.");
            }
        }

        onGameEnd();
    }

    /** Called when the game starts (both players joined). */
    protected abstract void onGameStart();

    /** Called every second. */
    protected abstract void onTimeUpdate();

    /** Called when time runs out. */
    protected abstract void onTimeUp();

    /** Called when the game ends. */
    protected abstract void onGameEnd();

    // Getters
    public DuoGameType getGameType() {
        return gameType;
    }

    public Machine getMachine() {
        return machine;
    }

    public Player getPlayer1() {
        return player1;
    }

    public Player getPlayer2() {
        return player2;
    }

    public int getBet() {
        return bet;
    }

    public State getState() {
        return state;
    }

    public Result getResult() {
        return result;
    }

    public int getTimeLeft() {
        return timeLeft;
    }

    public Player getOpponent(Player player) {
        if (player.equals(player1)) return player2;
        if (player.equals(player2)) return player1;
        return null;
    }
}
