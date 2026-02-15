package net.serverplugins.arcade.games.blackjack;

import java.util.List;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.games.duo.DuoGame;
import net.serverplugins.arcade.gui.ArcadeFont;
import net.serverplugins.arcade.gui.ArcadeGui;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/** GUI for blackjack game - shows both players' hands and actions. */
public class BlackjackGameGui extends ArcadeGui {

    private final BlackjackGameInstance game;
    private final Player viewer;

    // Layout slots
    private static final int[] YOUR_HAND_SLOTS = {19, 20, 21, 22, 23};
    private static final int[] OPPONENT_HAND_SLOTS = {1, 2, 3, 4, 5};
    private static final int HIT_SLOT = 37;
    private static final int STAND_SLOT = 43;
    private static final int YOUR_SCORE_SLOT = 28;
    private static final int OPPONENT_SCORE_SLOT = 10;
    private static final int TIMER_SLOT = 4;
    private static final int YOUR_HEAD_SLOT = 36;
    private static final int OPPONENT_HEAD_SLOT = 8;

    public BlackjackGameGui(BlackjackGameInstance game, Player viewer) {
        super(
                ServerArcade.getInstance(),
                ArcadeFont.createTitle(ArcadeFont.BLACKJACK_MENU, "Blackjack - $" + game.getBet()),
                45);
        this.game = game;
        this.viewer = viewer;
    }

    @Override
    protected void build() {
        // No glass pane fillers - clean GUI with inventory hiding via packets

        // Action buttons
        updateButtons();

        // Player heads
        updateHeads();

        // Update display
        updateCards();
    }

    private void updateButtons() {
        boolean canAct = game.getState() == DuoGame.State.PLAYING && !game.isPlayerStanding(viewer);

        if (canAct) {
            // Hit button
            ItemStack hitButton =
                    createItem(
                            Material.LIME_CONCRETE,
                            "§a§lHIT",
                            List.of("§7Draw another card", "", "§eClick to draw!"));
            setItem(HIT_SLOT, hitButton, e -> game.hit(viewer));

            // Stand button
            ItemStack standButton =
                    createItem(
                            Material.RED_CONCRETE,
                            "§c§lSTAND",
                            List.of("§7Keep your current hand", "", "§eClick to stand!"));
            setItem(STAND_SLOT, standButton, e -> game.stand(viewer));
        } else {
            // Disabled buttons
            ItemStack waitButton =
                    createItem(
                            Material.GRAY_CONCRETE,
                            "§7Waiting...",
                            List.of(
                                    game.isPlayerStanding(viewer)
                                            ? "§eYou are standing"
                                            : "§eGame not active"));
            inventory.setItem(HIT_SLOT, waitButton);
            inventory.setItem(STAND_SLOT, waitButton);
        }
    }

    private void updateHeads() {
        // Your head
        ItemStack yourHead = createPlayerHead(viewer);
        ItemMeta yourMeta = yourHead.getItemMeta();
        if (yourMeta != null) {
            yourMeta.setDisplayName("§a" + viewer.getName() + " §7(You)");
            yourHead.setItemMeta(yourMeta);
        }
        inventory.setItem(YOUR_HEAD_SLOT, yourHead);

        // Opponent head
        Player opponent = game.getOpponent(viewer);
        if (opponent != null) {
            ItemStack opponentHead = createPlayerHead(opponent);
            ItemMeta oppMeta = opponentHead.getItemMeta();
            if (oppMeta != null) {
                oppMeta.setDisplayName("§c" + opponent.getName());
                opponentHead.setItemMeta(oppMeta);
            }
            inventory.setItem(OPPONENT_HEAD_SLOT, opponentHead);
        }
    }

    /** Update the GUI display. */
    public void updateCards() {
        // Update your hand
        List<BlackjackType.Card> yourHand = game.getPlayerHand(viewer);
        if (yourHand != null) {
            for (int i = 0; i < YOUR_HAND_SLOTS.length; i++) {
                if (i < yourHand.size()) {
                    BlackjackType.Card card = yourHand.get(i);
                    inventory.setItem(YOUR_HAND_SLOTS[i], card.createItem(0));
                } else {
                    inventory.setItem(YOUR_HAND_SLOTS[i], null);
                }
            }
        }

        // Update opponent hand
        List<BlackjackType.Card> opponentHand = game.getOpponentHand(viewer);
        boolean opponentStanding = game.isOpponentStanding(viewer);
        if (opponentHand != null) {
            for (int i = 0; i < OPPONENT_HAND_SLOTS.length; i++) {
                if (i < opponentHand.size()) {
                    if (opponentStanding || game.getState() == DuoGame.State.FINISHED) {
                        // Show all cards when opponent stands or game ends
                        BlackjackType.Card card = opponentHand.get(i);
                        inventory.setItem(OPPONENT_HAND_SLOTS[i], card.createItem(1));
                    } else if (i == 0) {
                        // Show first card face up
                        BlackjackType.Card card = opponentHand.get(i);
                        inventory.setItem(OPPONENT_HAND_SLOTS[i], card.createItem(1));
                    } else {
                        // Show other cards face down
                        inventory.setItem(
                                OPPONENT_HAND_SLOTS[i], game.getBlackjackType().getCardBackItem());
                    }
                } else {
                    inventory.setItem(OPPONENT_HAND_SLOTS[i], null);
                }
            }
        }

        // Update score displays
        updateScoreDisplay();

        // Update timer
        updateTimer();

        // Update buttons
        updateButtons();
    }

    private void updateScoreDisplay() {
        // Your score
        List<BlackjackType.Card> yourHand = game.getPlayerHand(viewer);
        if (yourHand != null) {
            int yourValue = game.getHandValue(yourHand);
            boolean yourBust = yourValue > 21;
            String yourStatus = game.isPlayerStanding(viewer) ? " §7(Standing)" : "";
            ItemStack yourScore =
                    createItem(
                            yourBust ? Material.RED_CONCRETE : Material.LIME_CONCRETE,
                            (yourBust ? "§c" : "§a") + "Your Score: " + yourValue + yourStatus,
                            yourBust ? List.of("§cBUST!") : List.of("§7Your current total"));
            inventory.setItem(YOUR_SCORE_SLOT, yourScore);
        }

        // Opponent score
        List<BlackjackType.Card> opponentHand = game.getOpponentHand(viewer);
        boolean showOpponentScore =
                game.isOpponentStanding(viewer) || game.getState() == DuoGame.State.FINISHED;

        if (showOpponentScore && opponentHand != null) {
            int oppValue = game.getHandValue(opponentHand);
            boolean oppBust = oppValue > 21;
            String oppStatus = game.isOpponentStanding(viewer) ? " §7(Standing)" : "";
            ItemStack oppScore =
                    createItem(
                            oppBust ? Material.RED_CONCRETE : Material.ORANGE_CONCRETE,
                            (oppBust ? "§c" : "§6") + "Opponent: " + oppValue + oppStatus,
                            oppBust ? List.of("§cBUST!") : List.of("§7Opponent's total"));
            inventory.setItem(OPPONENT_SCORE_SLOT, oppScore);
        } else {
            // Show only visible card value
            if (opponentHand != null && !opponentHand.isEmpty()) {
                int visibleValue = opponentHand.get(0).value;
                ItemStack oppScore =
                        createItem(
                                Material.ORANGE_CONCRETE,
                                "§6Opponent: " + visibleValue + "+?",
                                List.of("§7One card hidden"));
                inventory.setItem(OPPONENT_SCORE_SLOT, oppScore);
            }
        }
    }

    private void updateTimer() {
        int timeLeft = game.getTimeLeft();
        Material timerMat = timeLeft > 10 ? Material.CLOCK : Material.REDSTONE;
        String timeColor = timeLeft > 10 ? "§e" : "§c";

        ItemStack timer =
                createItem(
                        timerMat,
                        timeColor + "Time: " + timeLeft + "s",
                        List.of("§7Time remaining", "", "§8Bet: §6$" + game.getBet()));
        inventory.setItem(TIMER_SLOT, timer);
    }

    private ItemStack createItem(Material material, String name) {
        return createItem(material, name, null);
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createPlayerHead(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            head.setItemMeta(meta);
        }
        return head;
    }
}
