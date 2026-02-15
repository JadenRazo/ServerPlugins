package net.serverplugins.arcade.games.crash;

import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.games.GameType;
import net.serverplugins.arcade.machines.Machine;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Crash game type - a multiplayer betting game where players bet on when the multiplier crashes.
 */
public class CrashType extends GameType {

    private int bettingDuration = 120;
    private int timeBetweenGames = 60;

    // GUI titles for different states
    private String waitingTitle = "§fWaiting";
    private String startedTitle = "§fStarted";

    public CrashType(ServerArcade plugin) {
        super(plugin, "Crash", "crash");
    }

    @Override
    protected void onConfigLoad(ConfigurationSection config) {
        bettingDuration = config.getInt("betting_duration", bettingDuration);
        timeBetweenGames = config.getInt("time_between_games", timeBetweenGames);

        // Load titles for waiting and started states
        ConfigurationSection bettingGui = config.getConfigurationSection("betting_gui");
        if (bettingGui != null) {
            waitingTitle = bettingGui.getString("title", waitingTitle).replace("&", "§");
        }

        ConfigurationSection runningGui = config.getConfigurationSection("gui");
        if (runningGui != null) {
            startedTitle = runningGui.getString("title", startedTitle).replace("&", "§");
            guiSize = runningGui.getInt("size", guiSize);
        }
    }

    @Override
    public void open(Player player, Machine machine) {
        CrashManager manager = plugin.getCrashManager();
        if (manager == null) {
            TextUtil.sendError(player, "Crash game is not available.");
            return;
        }

        // Seat the player at the machine
        if (machine != null) {
            machine.seatPlayer(player, 1);
        }

        // Open the crash GUI with this type so it can access titles
        CrashGameGui gui = new CrashGameGui(plugin, manager, this);
        gui.open(player);
        gui.startUpdating();
    }

    public String getWaitingTitle() {
        return waitingTitle;
    }

    public String getStartedTitle() {
        return startedTitle;
    }

    public int getBettingDuration() {
        return bettingDuration;
    }

    public int getTimeBetweenGames() {
        return timeBetweenGames;
    }
}
