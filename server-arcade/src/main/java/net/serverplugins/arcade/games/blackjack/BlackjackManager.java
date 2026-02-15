package net.serverplugins.arcade.games.blackjack;

import java.util.*;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.games.GameResult;

public class BlackjackManager {

    private final ServerArcade plugin;
    private final Map<UUID, BlackjackGame> activeGames = new HashMap<>();

    public BlackjackManager(ServerArcade plugin) {
        this.plugin = plugin;
    }

    public BlackjackGame startGame(UUID playerId, double bet) {
        BlackjackGame game = new BlackjackGame(playerId, bet);
        game.dealInitialCards();
        activeGames.put(playerId, game);

        if (game.isPlayerBlackjack()) {
            if (game.isDealerBlackjack()) {
                game.setResult(GameResult.push(bet));
            } else {
                double multiplier = plugin.getArcadeConfig().getBlackjackMultiplier();
                game.setResult(GameResult.win(bet, multiplier, "Blackjack!"));
            }
            activeGames.remove(playerId);
        }

        return game;
    }

    public BlackjackGame hit(UUID playerId) {
        BlackjackGame game = activeGames.get(playerId);
        if (game == null) return null;

        game.playerHit();

        if (game.getPlayerValue() > 21) {
            game.setResult(GameResult.lose(game.getBet(), "Bust!"));
            activeGames.remove(playerId);
        }

        return game;
    }

    public BlackjackGame stand(UUID playerId) {
        BlackjackGame game = activeGames.get(playerId);
        if (game == null) return null;

        int dealerStandsOn = plugin.getArcadeConfig().getDealerStandsOn();
        while (game.getDealerValue() < dealerStandsOn) {
            game.dealerHit();
        }

        int playerValue = game.getPlayerValue();
        int dealerValue = game.getDealerValue();

        if (dealerValue > 21) {
            game.setResult(
                    GameResult.win(
                            game.getBet(),
                            plugin.getArcadeConfig().getWinMultiplier(),
                            "Dealer busts!"));
        } else if (playerValue > dealerValue) {
            game.setResult(
                    GameResult.win(
                            game.getBet(),
                            plugin.getArcadeConfig().getWinMultiplier(),
                            "You win!"));
        } else if (playerValue < dealerValue) {
            game.setResult(GameResult.lose(game.getBet(), "Dealer wins!"));
        } else {
            game.setResult(GameResult.push(game.getBet()));
        }

        activeGames.remove(playerId);
        return game;
    }

    public BlackjackGame doubleDown(UUID playerId) {
        BlackjackGame game = activeGames.get(playerId);
        if (game == null || !game.canDoubleDown()) return null;

        game.doubleBet();
        game.playerHit();

        if (game.getPlayerValue() > 21) {
            game.setResult(GameResult.lose(game.getBet(), "Bust!"));
            activeGames.remove(playerId);
            return game;
        }

        return stand(playerId);
    }

    /** Player splits their hand into two hands. */
    public BlackjackGame split(UUID playerId) {
        BlackjackGame game = activeGames.get(playerId);
        if (game == null || !game.canSplit()) return null;

        game.split();
        return game;
    }

    /** Player takes insurance against dealer blackjack. */
    public BlackjackGame takeInsurance(UUID playerId) {
        BlackjackGame game = activeGames.get(playerId);
        if (game == null || !game.canInsurance()) return null;

        game.takeInsurance();
        return game;
    }

    /** Player surrenders and gets half their bet back. */
    public BlackjackGame surrender(UUID playerId) {
        BlackjackGame game = activeGames.get(playerId);
        if (game == null || !game.canSurrender()) return null;

        game.surrender();
        double refund = game.getBet() / 2;
        game.setResult(GameResult.lose(game.getBet() - refund, "Surrendered - half bet returned."));
        activeGames.remove(playerId);
        return game;
    }

    /** Check if player can split their current hand. */
    public boolean canSplit(UUID playerId) {
        BlackjackGame game = activeGames.get(playerId);
        return game != null && game.canSplit();
    }

    /** Check if player can take insurance. */
    public boolean canInsurance(UUID playerId) {
        BlackjackGame game = activeGames.get(playerId);
        return game != null && game.canInsurance();
    }

    /** Check if player can surrender. */
    public boolean canSurrender(UUID playerId) {
        BlackjackGame game = activeGames.get(playerId);
        return game != null && game.canSurrender();
    }

    /** Check if player can double down. */
    public boolean canDoubleDown(UUID playerId) {
        BlackjackGame game = activeGames.get(playerId);
        return game != null && game.canDoubleDown();
    }

    public boolean hasActiveGame(UUID playerId) {
        return activeGames.containsKey(playerId);
    }

    public BlackjackGame getGame(UUID playerId) {
        return activeGames.get(playerId);
    }

    public void cancelGame(UUID playerId) {
        activeGames.remove(playerId);
    }
}
