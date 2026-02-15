package net.serverplugins.enchants.games.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.enchants.ServerEnchants;
import net.serverplugins.enchants.enchantments.EnchantTier;
import net.serverplugins.enchants.games.EnchantGame;
import net.serverplugins.enchants.games.GameType;
import net.serverplugins.enchants.models.RuneCard;
import net.serverplugins.enchants.utils.RuneGenerator;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/** Memory card matching game where players flip cards to find matching pairs of runes. */
public class MemoryGame extends EnchantGame {

    private final Map<Integer, RuneCard> cardSlots;
    private RuneCard firstCard;
    private int firstCardSlot;
    private boolean waitingForFlipBack;
    private int moves;
    private int maxMoves;
    private int pairsMatched;
    private final int totalPairs;

    public MemoryGame(ServerEnchants plugin, Player player, EnchantTier tier) {
        super(plugin, player, GameType.MEMORY, tier, 54);

        this.totalPairs = config.getMemoryPairs(tier);
        this.maxMoves = totalPairs * config.getMemoryMoveLimitMultiplier();
        this.cardSlots = new HashMap<>();
        this.firstCard = null;
        this.firstCardSlot = -1;
        this.waitingForFlipBack = false;
        this.moves = 0;
        this.pairsMatched = 0;
    }

    @Override
    protected void setupGame() {
        // Fill border with purple glass
        ItemStack borderItem =
                ItemBuilder.of(Material.PURPLE_STAINED_GLASS_PANE).name("<dark_purple>").build();
        fillBorder(GuiItem.of(borderItem));

        // Generate memory cards
        List<RuneCard> cards = RuneGenerator.generateMemoryCards(totalPairs);

        // Place cards in grid pattern (avoiding border)
        int[] cardPositions = getCardPositions(totalPairs * 2);
        for (int i = 0; i < cards.size(); i++) {
            int slot = cardPositions[i];
            RuneCard card = cards.get(i);
            cardSlots.put(slot, card);
            setItem(slot, GuiItem.of(createCardItem(card)));
        }

        // Info display at bottom center
        updateInfoDisplay();

        // Start timer
        startTimer(config.getMemoryTimeLimit());
    }

    /** Get slot positions for cards in a centered grid pattern */
    private int[] getCardPositions(int cardCount) {
        // Use rows 1-4 (slots 9-44), centering the cards
        int[] positions = new int[cardCount];
        int index = 0;

        if (cardCount <= 12) {
            // 2 rows of up to 6 cards each
            int cardsPerRow = (cardCount + 1) / 2;
            int offset = (9 - cardsPerRow) / 2;
            for (int row = 0; row < 2 && index < cardCount; row++) {
                for (int col = 0; col < cardsPerRow && index < cardCount; col++) {
                    positions[index++] = 9 + row * 9 + offset + col;
                }
            }
        } else if (cardCount <= 21) {
            // 3 rows of up to 7 cards each
            int cardsPerRow = (cardCount + 2) / 3;
            int offset = (9 - cardsPerRow) / 2;
            for (int row = 0; row < 3 && index < cardCount; row++) {
                for (int col = 0; col < cardsPerRow && index < cardCount; col++) {
                    positions[index++] = 9 + row * 9 + offset + col;
                }
            }
        } else {
            // 4 rows of cards
            int cardsPerRow = (cardCount + 3) / 4;
            int offset = (9 - cardsPerRow) / 2;
            for (int row = 0; row < 4 && index < cardCount; row++) {
                for (int col = 0; col < cardsPerRow && index < cardCount; col++) {
                    positions[index++] = 9 + row * 9 + offset + col;
                }
            }
        }

        return positions;
    }

    @Override
    public void handleGameClick(Player player, int slot, ClickType clickType) {
        if (gameOver || waitingForFlipBack) return;

        RuneCard card = cardSlots.get(slot);
        if (card == null || card.isMatched() || card.isFaceUp()) return;

        // Flip card face up
        card.setFaceUp(true);
        setItem(slot, GuiItem.of(createCardItem(card)));

        if (firstCard == null) {
            // First card selection
            firstCard = card;
            firstCardSlot = slot;
        } else {
            // Second card selection
            moves++;
            updateInfoDisplay();

            RuneCard secondCard = card;
            int secondCardSlot = slot;

            // Check for match
            if (firstCard.getPairId() == secondCard.getPairId()) {
                // Match found!
                firstCard.setMatched(true);
                secondCard.setMatched(true);
                pairsMatched++;
                score = pairsMatched;

                // Update to matched appearance
                setItem(firstCardSlot, GuiItem.of(createCardItem(firstCard)));
                setItem(secondCardSlot, GuiItem.of(createCardItem(secondCard)));

                player.playSound(player.getLocation(), "entity.experience_orb.pickup", 1.0f, 1.5f);

                // Check win condition
                if (pairsMatched >= totalPairs) {
                    endGame(true);
                }

                firstCard = null;
                firstCardSlot = -1;
            } else {
                // No match - schedule flip back
                waitingForFlipBack = true;
                player.playSound(player.getLocation(), "block.note_block.bass", 1.0f, 0.5f);

                plugin.getServer()
                        .getScheduler()
                        .runTaskLater(
                                plugin,
                                () -> {
                                    if (!gameOver) {
                                        firstCard.setFaceUp(false);
                                        secondCard.setFaceUp(false);
                                        setItem(
                                                firstCardSlot,
                                                GuiItem.of(createCardItem(firstCard)));
                                        setItem(
                                                secondCardSlot,
                                                GuiItem.of(createCardItem(secondCard)));
                                    }
                                    firstCard = null;
                                    firstCardSlot = -1;
                                    waitingForFlipBack = false;
                                },
                                20L); // 1 second
            }

            // Check lose condition
            if (moves >= maxMoves) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> endGame(false), 25L);
            }
        }
    }

    /** Create an ItemStack representation of a card */
    private ItemStack createCardItem(RuneCard card) {
        if (card.isMatched()) {
            // Matched card - green glass
            return ItemBuilder.of(Material.LIME_STAINED_GLASS_PANE)
                    .name("<green><bold>MATCHED!")
                    .lore(card.getRuneType().getColoredName())
                    .glow()
                    .build();
        } else if (card.isFaceUp()) {
            // Face up card - show rune
            return ItemBuilder.of(card.getRuneType().getMaterial())
                    .name(card.getRuneType().getColoredName())
                    .lore("<gray>Pair ID: " + card.getPairId())
                    .build();
        } else {
            // Face down card - gray glass
            return ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                    .name("<dark_gray>???")
                    .lore("<gray>Click to reveal")
                    .build();
        }
    }

    /** Update the info display showing moves and progress */
    private void updateInfoDisplay() {
        ItemStack infoItem =
                ItemBuilder.of(Material.PAPER)
                        .name("<gold><bold>Memory Game")
                        .lore(
                                "<white>Pairs Matched: <green>"
                                        + pairsMatched
                                        + "<gray>/"
                                        + totalPairs,
                                "<white>Moves: <yellow>" + moves + "<gray>/" + maxMoves,
                                "<white>Time: <aqua>" + timeRemaining + "s",
                                "",
                                "<gray>Match all pairs to win!")
                        .build();

        setItem(49, GuiItem.of(infoItem));
    }
}
