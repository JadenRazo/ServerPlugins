package net.serverplugins.arcade.games.baccarat;

import net.serverplugins.arcade.ServerArcade;

public class BaccaratManager {

    private final ServerArcade plugin;

    public BaccaratManager(ServerArcade plugin) {
        this.plugin = plugin;
    }

    /**
     * Play a complete baccarat game. Since baccarat has no player decisions, the game resolves
     * instantly.
     */
    public BaccaratGame playGame(
            java.util.UUID playerId, double bet, BaccaratGame.BetSide betSide) {
        BaccaratGame game = new BaccaratGame(playerId, bet, betSide);
        game.play();
        game.calculateResult(
                plugin.getArcadeConfig().getBaccaratPlayerMultiplier(),
                plugin.getArcadeConfig().getBaccaratBankerMultiplier(),
                plugin.getArcadeConfig().getBaccaratTieMultiplier());
        return game;
    }
}
