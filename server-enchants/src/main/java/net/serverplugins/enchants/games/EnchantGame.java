package net.serverplugins.enchants.games;

import java.util.function.Consumer;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.enchants.EnchantsConfig;
import net.serverplugins.enchants.ServerEnchants;
import net.serverplugins.enchants.enchantments.EnchantTier;
import net.serverplugins.enchants.models.GameResult;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Abstract base class for all enchantment mini-games. Extends Gui to provide GUI functionality and
 * manages game state, timers, and scoring.
 */
public abstract class EnchantGame extends Gui {

    protected final ServerEnchants plugin;
    protected final GameType gameType;
    protected final EnchantTier tier;
    protected final EnchantsConfig config;

    protected boolean gameOver;
    protected int score;
    protected long startTime;
    protected BukkitTask timerTask;
    protected int timeRemaining;
    protected Consumer<GameResult> onGameEnd;

    /**
     * Constructor for EnchantGame
     *
     * @param plugin the ServerEnchants plugin instance
     * @param player the player participating in the game
     * @param gameType the type of game being played
     * @param tier the enchantment tier/difficulty
     * @param size the GUI size (must be multiple of 9, between 9-54)
     */
    public EnchantGame(
            ServerEnchants plugin, Player player, GameType gameType, EnchantTier tier, int size) {
        super((Plugin) plugin, player, gameType.getDisplayName(), size);
        this.plugin = plugin;
        this.gameType = gameType;
        this.tier = tier;
        this.config = plugin.getEnchantsConfig();
        this.gameOver = false;
        this.score = 0;
        this.startTime = System.currentTimeMillis();
        this.timeRemaining = 0;
    }

    /**
     * Set up the game-specific GUI items and initialize game state. Called by initializeItems().
     */
    protected abstract void setupGame();

    /**
     * Handle game-specific click logic. Called by the GameGuiListener.
     *
     * @param player the player who clicked
     * @param slot the slot that was clicked
     * @param clickType the type of click
     */
    public abstract void handleGameClick(Player player, int slot, ClickType clickType);

    @Override
    protected void initializeItems() {
        setupGame();
    }

    /**
     * End the game with a win or loss result
     *
     * @param won whether the player won the game
     */
    public void endGame(boolean won) {
        if (gameOver) return;

        gameOver = true;
        cancelTimer();

        // Calculate rewards
        int fragmentsEarned = config.getFragmentReward(tier, won);
        int xpEarned = config.getXpReward(tier, won);

        // Create game result
        GameResult result =
                new GameResult(
                        viewer.getUniqueId(),
                        gameType,
                        tier,
                        won,
                        score,
                        fragmentsEarned,
                        xpEarned);

        // Display result message
        if (won) {
            TextUtil.sendSuccess(
                    viewer,
                    "<green>Victory! <white>Score: <gold>"
                            + score
                            + " <dark_gray>| <white>Earned: <gold>"
                            + fragmentsEarned
                            + " fragments <dark_gray>+ <aqua>"
                            + xpEarned
                            + " XP");
        } else {
            TextUtil.sendError(
                    viewer,
                    "<red>Defeat! <white>Score: <gold>"
                            + score
                            + " <dark_gray>| <white>Earned: <gold>"
                            + fragmentsEarned
                            + " fragments <dark_gray>+ <aqua>"
                            + xpEarned
                            + " XP");
        }

        // Trigger callback if set
        if (onGameEnd != null) {
            onGameEnd.accept(result);
        }

        // Close GUI after short delay
        plugin.getServer()
                .getScheduler()
                .runTaskLater(plugin, () -> viewer.closeInventory(), 40L); // 2 seconds
    }

    /**
     * Start a countdown timer for the game
     *
     * @param seconds number of seconds for the timer
     */
    public void startTimer(int seconds) {
        this.timeRemaining = seconds;

        timerTask =
                plugin.getServer()
                        .getScheduler()
                        .runTaskTimer(
                                plugin,
                                () -> {
                                    if (gameOver) {
                                        cancelTimer();
                                        return;
                                    }

                                    timeRemaining--;

                                    // Update action bar with timer
                                    if (timeRemaining % 5 == 0 || timeRemaining <= 10) {
                                        String color = timeRemaining <= 10 ? "<red>" : "<yellow>";
                                        TextUtil.sendActionBar(
                                                viewer, color + "Time: " + timeRemaining + "s");
                                    }

                                    // End game on timeout
                                    if (timeRemaining <= 0) {
                                        endGame(false);
                                    }
                                },
                                0L,
                                20L);
    }

    /** Cancel the active timer task */
    public void cancelTimer() {
        if (timerTask != null && !timerTask.isCancelled()) {
            timerTask.cancel();
            timerTask = null;
        }
    }

    @Override
    public void onClose(Player player) {
        // If game isn't over yet and GUI is closed, count as a loss
        if (!gameOver) {
            endGame(false);
        }
        cancelTimer();
    }

    /**
     * Set the callback to execute when the game ends
     *
     * @param callback consumer that accepts the GameResult
     */
    public void setOnGameEnd(Consumer<GameResult> callback) {
        this.onGameEnd = callback;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public int getScore() {
        return score;
    }

    public GameType getGameType() {
        return gameType;
    }

    public EnchantTier getTier() {
        return tier;
    }

    public int getTimeRemaining() {
        return timeRemaining;
    }
}
