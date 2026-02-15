package net.serverplugins.arcade.games.duo;

import java.util.HashMap;
import java.util.Map;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.games.GameType;
import net.serverplugins.arcade.machines.Machine;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/** Base class for 2-player competitive game types. */
public abstract class DuoGameType extends GameType {

    // Active games by machine ID
    protected final Map<String, DuoGame> activeGames = new HashMap<>();

    // Time settings
    protected int challengeTime = 120; // Time to find opponent
    protected int gameTime = 60; // Time limit for game

    public DuoGameType(ServerArcade plugin, String name, String configKey) {
        super(plugin, name, configKey);
    }

    @Override
    protected void onConfigLoad(ConfigurationSection config) {
        challengeTime = config.getInt("challenge_time", challengeTime);
        gameTime = config.getInt("game_time", gameTime);
    }

    @Override
    public void open(Player player, Machine machine) {
        String machineId = machine != null ? machine.getId() : "cmd_" + player.getUniqueId();

        // Check if there's an active game waiting for opponent
        DuoGame existingGame = activeGames.get(machineId);

        if (existingGame != null && existingGame.getState() == DuoGame.State.WAITING_FOR_OPPONENT) {
            // Join existing game - seat as player 2
            if (machine != null) {
                machine.seatPlayer(player, 2);
            }
            existingGame.join(player);
        } else {
            // Start new game - seat as player 1
            if (machine != null) {
                machine.seatPlayer(player, 1);
            }
            createNewGame(player, machine, defaultBet);
        }
    }

    /** Create a new duo game. */
    public void createNewGame(Player player, Machine machine, int bet) {
        String machineId = machine != null ? machine.getId() : "cmd_" + player.getUniqueId();

        DuoGame game = createGame(machine, player, bet);
        activeGames.put(machineId, game);
        game.startWaiting();
    }

    /** Create the specific game instance. */
    protected abstract DuoGame createGame(Machine machine, Player player1, int bet);

    /** Remove a finished game. */
    public void removeGame(DuoGame game) {
        String key =
                game.getMachine() != null
                        ? game.getMachine().getId()
                        : "cmd_" + game.getPlayer1().getUniqueId();
        activeGames.remove(key);
    }

    /** Get active game for a player. */
    public DuoGame getGameForPlayer(Player player) {
        for (DuoGame game : activeGames.values()) {
            if (player.equals(game.getPlayer1()) || player.equals(game.getPlayer2())) {
                return game;
            }
        }
        return null;
    }

    // Getters
    public int getChallengeTime() {
        return challengeTime;
    }

    public int getGameTime() {
        return gameTime;
    }

    public Map<String, DuoGame> getActiveGames() {
        return activeGames;
    }
}
