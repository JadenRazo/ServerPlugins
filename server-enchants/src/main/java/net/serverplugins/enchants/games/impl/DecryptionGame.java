package net.serverplugins.enchants.games.impl;

import java.util.*;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.enchants.ServerEnchants;
import net.serverplugins.enchants.enchantments.EnchantTier;
import net.serverplugins.enchants.games.EnchantGame;
import net.serverplugins.enchants.games.GameType;
import net.serverplugins.enchants.models.RuneType;
import net.serverplugins.enchants.utils.RuneGenerator;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/** Decryption game similar to Wordle/Mastermind where players guess a hidden rune code. */
public class DecryptionGame extends EnchantGame {

    private final List<RuneType> secretCode;
    private final List<RuneType> runePool;
    private final List<RuneType> currentGuess;
    private final List<List<RuneType>> guessHistory;
    private final List<List<FeedbackType>> feedbackHistory;
    private final int codeLength;
    private final int maxGuesses;
    private int guessCount;

    // Slot ranges
    private static final int CODE_DISPLAY_START = 2;
    private static final int GUESS_START_ROW = 1;
    private static final int RUNE_SELECTOR_ROW = 5;

    private enum FeedbackType {
        CORRECT_POSITION, // Green
        CORRECT_RUNE, // Yellow
        INCORRECT // Red
    }

    public DecryptionGame(ServerEnchants plugin, Player player, EnchantTier tier) {
        super(plugin, player, GameType.DECRYPTION, tier, 54);

        this.codeLength = config.getDecryptionCodeLength(tier);
        this.maxGuesses = config.getDecryptionGuesses(tier);
        int poolSize = config.getDecryptionRunePoolSize(tier);

        this.runePool = RuneGenerator.generateRunePool(poolSize);
        this.secretCode = RuneGenerator.generateCode(codeLength, runePool);
        this.currentGuess = new ArrayList<>();
        this.guessHistory = new ArrayList<>();
        this.feedbackHistory = new ArrayList<>();
        this.guessCount = 0;
    }

    @Override
    protected void setupGame() {
        // Display hidden code at top
        displaySecretCode();

        // Display guess area
        updateGuessDisplay();

        // Display rune selector buttons at bottom
        displayRuneSelector();

        // Info display
        updateInfoDisplay();

        // Border
        ItemStack border = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 9; i++) {
            if (i < CODE_DISPLAY_START || i > CODE_DISPLAY_START + codeLength) {
                setItem(i, GuiItem.of(border));
            }
        }

        // No timer for this game - it's puzzle-based
        startTimer(120); // 2 minutes
    }

    @Override
    public void handleGameClick(Player player, int slot, ClickType clickType) {
        if (gameOver) return;

        // Handle rune selector clicks (bottom row)
        if (slot >= RUNE_SELECTOR_ROW * 9 && slot < RUNE_SELECTOR_ROW * 9 + 9) {
            int runeIndex = slot - (RUNE_SELECTOR_ROW * 9);
            if (runeIndex < runePool.size() && currentGuess.size() < codeLength) {
                RuneType selected = runePool.get(runeIndex);
                currentGuess.add(selected);
                updateCurrentGuessDisplay();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);

                // Auto-submit when guess is complete
                if (currentGuess.size() == codeLength) {
                    plugin.getServer()
                            .getScheduler()
                            .runTaskLater(plugin, () -> submitGuess(player), 10L);
                }
            }
            return;
        }

        // Handle current guess clicks (remove last rune)
        if (slot >= 36 && slot < 36 + codeLength) {
            if (!currentGuess.isEmpty()) {
                currentGuess.remove(currentGuess.size() - 1);
                updateCurrentGuessDisplay();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
            }
        }
    }

    /** Display the secret code (hidden as ???) */
    private void displaySecretCode() {
        for (int i = 0; i < codeLength; i++) {
            ItemStack hidden =
                    ItemBuilder.of(Material.BARRIER)
                            .name("<red>???")
                            .lore("<gray>Hidden code position " + (i + 1))
                            .build();
            setItem(CODE_DISPLAY_START + i, GuiItem.of(hidden));
        }
    }

    /** Update the display of all guesses and feedback */
    private void updateGuessDisplay() {
        // Display previous guesses with feedback
        for (int guessIndex = 0; guessIndex < guessHistory.size() && guessIndex < 4; guessIndex++) {
            int row = GUESS_START_ROW + guessIndex;
            List<RuneType> guess = guessHistory.get(guessIndex);
            List<FeedbackType> feedback = feedbackHistory.get(guessIndex);

            // Display guess runes
            for (int i = 0; i < codeLength && i < 4; i++) {
                RuneType rune = guess.get(i);
                FeedbackType fb = feedback.get(i);

                String loreText =
                        switch (fb) {
                            case CORRECT_POSITION -> "<green>✓ Correct position";
                            case CORRECT_RUNE -> "<yellow>⚠ Wrong position";
                            case INCORRECT -> "<red>✗ Not in code";
                        };

                ItemStack item =
                        ItemBuilder.of(rune.getMaterial())
                                .name(rune.getColoredName())
                                .lore(loreText)
                                .build();
                setItem(row * 9 + 2 + i, GuiItem.of(item));
            }

            // Display feedback summary
            for (int i = 0; i < codeLength && i < 4; i++) {
                FeedbackType fb = feedback.get(i);
                Material mat =
                        switch (fb) {
                            case CORRECT_POSITION -> Material.LIME_WOOL;
                            case CORRECT_RUNE -> Material.YELLOW_WOOL;
                            case INCORRECT -> Material.RED_WOOL;
                        };

                ItemStack feedbackItem = ItemBuilder.of(mat).name(" ").build();
                setItem(row * 9 + 6 + i, GuiItem.of(feedbackItem));
            }
        }

        // Display current guess workspace (row 4)
        updateCurrentGuessDisplay();
    }

    /** Update the current guess workspace */
    private void updateCurrentGuessDisplay() {
        int workspaceRow = 4;
        for (int i = 0; i < codeLength && i < 4; i++) {
            int slot = workspaceRow * 9 + 2 + i;
            if (i < currentGuess.size()) {
                RuneType rune = currentGuess.get(i);
                ItemStack item =
                        ItemBuilder.of(rune.getMaterial())
                                .name(rune.getColoredName())
                                .lore("<yellow>Click to remove")
                                .build();
                setItem(slot, GuiItem.of(item));
            } else {
                ItemStack empty =
                        ItemBuilder.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                                .name("<gray>Empty")
                                .lore("<gray>Select rune below")
                                .build();
                setItem(slot, GuiItem.of(empty));
            }
        }
    }

    /** Display the rune selector buttons */
    private void displayRuneSelector() {
        for (int i = 0; i < runePool.size() && i < 9; i++) {
            RuneType rune = runePool.get(i);
            ItemStack item =
                    ItemBuilder.of(rune.getMaterial())
                            .name(rune.getColoredName())
                            .lore("<yellow>Click to add to guess")
                            .build();
            setItem(RUNE_SELECTOR_ROW * 9 + i, GuiItem.of(item));
        }
    }

    /** Submit the current guess and evaluate it */
    private void submitGuess(Player player) {
        if (currentGuess.size() != codeLength) return;

        guessCount++;
        List<FeedbackType> feedback = evaluateGuess(currentGuess, secretCode);

        guessHistory.add(new ArrayList<>(currentGuess));
        feedbackHistory.add(feedback);

        // Check for win
        boolean allCorrect = feedback.stream().allMatch(f -> f == FeedbackType.CORRECT_POSITION);
        if (allCorrect) {
            score = maxGuesses - guessCount + 1; // Higher score for fewer guesses
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            revealCode();
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> endGame(true), 40L);
        } else if (guessCount >= maxGuesses) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            revealCode();
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> endGame(false), 40L);
        } else {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
        }

        currentGuess.clear();
        updateGuessDisplay();
        updateInfoDisplay();
    }

    /** Evaluate a guess against the secret code */
    private List<FeedbackType> evaluateGuess(List<RuneType> guess, List<RuneType> code) {
        List<FeedbackType> feedback = new ArrayList<>();
        List<RuneType> remainingCode = new ArrayList<>(code);
        List<Boolean> matched = new ArrayList<>();

        // First pass: mark correct positions
        for (int i = 0; i < codeLength; i++) {
            if (guess.get(i) == code.get(i)) {
                feedback.add(FeedbackType.CORRECT_POSITION);
                matched.add(true);
                remainingCode.set(i, null); // Mark as used
            } else {
                feedback.add(null);
                matched.add(false);
            }
        }

        // Second pass: mark correct runes in wrong positions
        for (int i = 0; i < codeLength; i++) {
            if (!matched.get(i)) {
                RuneType guessRune = guess.get(i);
                if (remainingCode.contains(guessRune)) {
                    feedback.set(i, FeedbackType.CORRECT_RUNE);
                    remainingCode.remove(guessRune); // Remove first occurrence
                } else {
                    feedback.set(i, FeedbackType.INCORRECT);
                }
            }
        }

        return feedback;
    }

    /** Reveal the secret code */
    private void revealCode() {
        for (int i = 0; i < codeLength; i++) {
            RuneType rune = secretCode.get(i);
            ItemStack item =
                    ItemBuilder.of(rune.getMaterial())
                            .name(rune.getColoredName())
                            .lore("<green>Secret Code Position " + (i + 1))
                            .glow()
                            .build();
            setItem(CODE_DISPLAY_START + i, GuiItem.of(item));
        }
    }

    /** Update the info display */
    private void updateInfoDisplay() {
        ItemStack infoItem =
                ItemBuilder.of(Material.MAP)
                        .name("<gold><bold>Rune Decryption")
                        .lore(
                                "<white>Code Length: <yellow>" + codeLength,
                                "<white>Guesses: <aqua>" + guessCount + "<gray>/" + maxGuesses,
                                "<white>Time: <aqua>" + timeRemaining + "s",
                                "",
                                "<green>Green <gray>= Correct position",
                                "<yellow>Yellow <gray>= Wrong position",
                                "<red>Red <gray>= Not in code")
                        .build();

        setItem(0, GuiItem.of(infoItem));
    }
}
