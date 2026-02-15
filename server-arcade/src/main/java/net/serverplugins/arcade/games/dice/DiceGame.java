package net.serverplugins.arcade.games.dice;

import java.util.concurrent.ThreadLocalRandom;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.games.GameResult;
import net.serverplugins.arcade.utils.AuditLogger;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** Main dice roll game logic with animated Unicode die faces. */
public class DiceGame {

    private final ServerArcade plugin;
    private final DiceConfig config;

    public enum BetType {
        OVER,
        UNDER,
        EXACT,
        ODD,
        EVEN
    }

    public DiceGame(ServerArcade plugin, DiceConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /** Roll dice with "over" bet type. */
    public GameResult rollOver(Player player, int bet, int threshold) {
        if (threshold < 1 || threshold > 5) {
            player.sendMessage(config.getMessage("invalid_number"));
            return null;
        }

        int roll = rollWithAnimation(player);
        double multiplier = config.getOverMultiplier(threshold);
        boolean won = roll > threshold;

        return processResult(player, bet, roll, multiplier, won, BetType.OVER, threshold);
    }

    /** Roll dice with "under" bet type. */
    public GameResult rollUnder(Player player, int bet, int threshold) {
        if (threshold < 2 || threshold > 6) {
            player.sendMessage(config.getMessage("invalid_number"));
            return null;
        }

        int roll = rollWithAnimation(player);
        double multiplier = config.getUnderMultiplier(threshold);
        boolean won = roll < threshold;

        return processResult(player, bet, roll, multiplier, won, BetType.UNDER, threshold);
    }

    /** Roll dice with "exact" bet type. */
    public GameResult rollExact(Player player, int bet, int target) {
        if (target < 1 || target > 6) {
            player.sendMessage(config.getMessage("invalid_number"));
            return null;
        }

        int roll = rollWithAnimation(player);
        double multiplier = config.getExactMultiplier();
        boolean won = roll == target;

        return processResult(player, bet, roll, multiplier, won, BetType.EXACT, target);
    }

    /** Roll dice with "odd" bet type. */
    public GameResult rollOdd(Player player, int bet) {
        int roll = rollWithAnimation(player);
        double multiplier = config.getOddMultiplier();
        boolean won = roll % 2 == 1;

        return processResult(player, bet, roll, multiplier, won, BetType.ODD, 0);
    }

    /** Roll dice with "even" bet type. */
    public GameResult rollEven(Player player, int bet) {
        int roll = rollWithAnimation(player);
        double multiplier = config.getEvenMultiplier();
        boolean won = roll % 2 == 0;

        return processResult(player, bet, roll, multiplier, won, BetType.EVEN, 0);
    }

    /**
     * Roll the dice and show animated sequence.
     *
     * @return The final dice roll result (1-6)
     */
    private int rollWithAnimation(Player player) {
        int finalRoll = ThreadLocalRandom.current().nextInt(1, 7);
        int frames = config.getRollFrames();

        // Show animated rolling sequence
        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            for (int i = 0; i < frames; i++) {
                                int randomFace = ThreadLocalRandom.current().nextInt(1, 7);
                                String face = config.getDieFace(randomFace);

                                final int currentFrame = i;
                                Bukkit.getScheduler()
                                        .runTaskLater(
                                                plugin,
                                                () -> {
                                                    String message =
                                                            config.getMessage("rolling")
                                                                    .replace("%face%", face);
                                                    TextUtil.sendActionBar(player, message);

                                                    // Click sound for each frame
                                                    player.playSound(
                                                            player.getLocation(),
                                                            Sound.UI_BUTTON_CLICK,
                                                            0.5f,
                                                            1.5f);
                                                },
                                                (currentFrame * config.getFrameDelay())
                                                        / 50); // Convert ms to ticks
                            }

                            // Show final result after animation
                            Bukkit.getScheduler()
                                    .runTaskLater(
                                            plugin,
                                            () -> {
                                                String face = config.getDieFace(finalRoll);
                                                String message =
                                                        config.getMessage("result")
                                                                .replace("%face%", face)
                                                                .replace(
                                                                        "%number%",
                                                                        String.valueOf(finalRoll));
                                                TextUtil.sendActionBar(player, message);

                                                // Ding sound for final result
                                                player.playSound(
                                                        player.getLocation(),
                                                        Sound.BLOCK_NOTE_BLOCK_PLING,
                                                        1f,
                                                        1.2f);
                                            },
                                            (frames * config.getFrameDelay()) / 50);
                        });

        return finalRoll;
    }

    /** Process the game result and award winnings/losses. */
    private GameResult processResult(
            Player player,
            int bet,
            int roll,
            double multiplier,
            boolean won,
            BetType betType,
            int target) {
        int payout = won ? (int) (bet * multiplier) : 0;

        // Wait for animation to finish before showing result
        int animationTicks =
                ((config.getRollFrames() * config.getFrameDelay()) / 50) + 20; // +1 second

        Bukkit.getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            if (won) {
                                ServerArcade.getEconomy().depositPlayer(player, payout);

                                // Use MessageBuilder for win message with star icon
                                net.serverplugins.api.messages.MessageBuilder.create()
                                        .prefix(plugin.getArcadeConfig().getMessenger().getPrefix())
                                        .star()
                                        .success("You won ")
                                        .emphasis("$" + payout)
                                        .success("!")
                                        .send(player);

                                player.playSound(
                                        player.getLocation(),
                                        Sound.ENTITY_PLAYER_LEVELUP,
                                        1f,
                                        1.2f);

                                // Send Discord webhook for big wins
                                String betDesc = getBetDescription(betType, target);
                                plugin.getDiscordWebhook()
                                        .sendDiceWin(
                                                player.getName(), betDesc, bet, multiplier, payout);
                            } else {
                                // Use MessageBuilder for lose message
                                net.serverplugins.api.messages.MessageBuilder.create()
                                        .prefix(plugin.getArcadeConfig().getMessenger().getPrefix())
                                        .warning("You lost ")
                                        .emphasis("$" + bet)
                                        .warning("!")
                                        .send(player);

                                player.playSound(
                                        player.getLocation(),
                                        Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO,
                                        1f,
                                        0.8f);
                            }

                            // Log the game
                            logDiceGame(
                                    player, betType, target, bet, roll, multiplier, payout, won);

                            // Track statistics
                            if (plugin.getStatisticsTracker() != null) {
                                plugin.getStatisticsTracker()
                                        .recordDiceGame(
                                                player.getUniqueId(),
                                                player.getName(),
                                                bet,
                                                multiplier,
                                                payout,
                                                won);
                            }
                        },
                        animationTicks);

        return won ? GameResult.win(bet, multiplier) : GameResult.lose(bet);
    }

    /** Get human-readable bet description. */
    private String getBetDescription(BetType betType, int target) {
        return switch (betType) {
            case OVER -> "OVER " + target;
            case UNDER -> "UNDER " + target;
            case EXACT -> "EXACT " + target;
            case ODD -> "ODD";
            case EVEN -> "EVEN";
        };
    }

    /** Log dice game to audit system. */
    private void logDiceGame(
            Player player,
            BetType betType,
            int target,
            int bet,
            int roll,
            double multiplier,
            int payout,
            boolean won) {
        String betDesc = getBetDescription(betType, target);

        AuditLogger.logPlayerAction(
                "dice",
                player.getUniqueId(),
                player.getName(),
                "DICE_ROLL",
                String.format(
                        "Bet: %s, Amount: $%d, Roll: %d, Mult: %.2fx, Payout: $%d, Won: %s",
                        betDesc, bet, roll, multiplier, payout, won));
    }
}
