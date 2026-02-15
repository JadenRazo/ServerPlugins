package net.serverplugins.arcade.machines;

import java.util.UUID;
import net.milkbowl.vault.economy.Economy;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.games.GameType;
import net.serverplugins.arcade.gui.BetMenu;
import net.serverplugins.arcade.gui.ConfirmMenu;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/** Machine type for two-player games (Blackjack). */
public class TwoPlayerMachine extends Machine {

    private final Object gameLock = new Object();
    private Player player1;
    private Player player2;
    private Entity player1Seat;
    private Entity player2Seat;
    private int bet = -1;

    public TwoPlayerMachine(
            String id,
            GameType gameType,
            Location location,
            Direction direction,
            UUID placedBy,
            long placedAt) {
        super(id, gameType, location, direction, placedBy, placedAt);
    }

    public TwoPlayerMachine(
            GameType gameType, Location location, Direction direction, UUID placedBy) {
        super(gameType, location, direction, placedBy);
    }

    @Override
    public void place() {
        super.place();
        findSeats();
    }

    /** Find seat entities for this machine. */
    private void findSeats() {
        player1Seat = null;
        player2Seat = null;

        NamespacedKey seatKey = Machine.MACHINE_SEAT_KEY;
        NamespacedKey entityKey = Machine.MACHINE_ENTITY_KEY;

        for (Entity entity : getLocation().getNearbyEntities(36, 36, 36)) {
            if (entity.getType() == EntityType.ARMOR_STAND
                    && entity.isInvulnerable()
                    && entity.getPersistentDataContainer()
                            .has(seatKey, PersistentDataType.INTEGER)) {

                String storedId =
                        entity.getPersistentDataContainer()
                                .get(entityKey, PersistentDataType.STRING);
                if (getId().equals(storedId)) {
                    int seatNum =
                            entity.getPersistentDataContainer()
                                    .get(seatKey, PersistentDataType.INTEGER);
                    if (seatNum == 1 || player1Seat == null) {
                        player1Seat = entity;
                    } else {
                        player2Seat = entity;
                    }
                }
            }
        }
    }

    @Override
    public void interact(Player player) {
        if (!isActive()) {
            TextUtil.sendError(player, "This machine is currently inactive.");
            return;
        }

        if (!player.hasPermission("serverarcade.play")) {
            TextUtil.sendError(player, "You don't have permission to use arcade machines.");
            return;
        }

        ServerArcade plugin = (ServerArcade) getGameType().getPlugin();

        synchronized (gameLock) {
            if (player1 != null) {
                // Player 1 already seated
                if (bet != -1) {
                    // Bet has been chosen
                    if (player.equals(player1)) {
                        // Player 1 trying to interact again
                        return;
                    }

                    if (player2 != null) {
                        // Both seats taken
                        TextUtil.sendError(player, "This machine is currently in use!");
                    } else {
                        // Player 2 joining - show confirmation
                        showConfirmMenu(player);
                    }
                }
            } else {
                // No player 1 yet - first player to interact
                player1 = player;

                // Show bet selection menu
                BetMenu betMenu =
                        new BetMenu(
                                getGameType(),
                                45,
                                selectedBet -> {
                                    Economy economy = ServerArcade.getEconomy();
                                    if (economy != null
                                            && economy.getBalance(player) < selectedBet) {
                                        TextUtil.sendError(player, "You don't have enough money!");
                                        synchronized (gameLock) {
                                            player1 = null;
                                        }
                                        return;
                                    }

                                    synchronized (gameLock) {
                                        bet = selectedBet;
                                    }
                                    player.closeInventory();

                                    // Seat player 1
                                    if (player1Seat != null) {
                                        seatPlayer(player, player1Seat);
                                    }

                                    TextUtil.send(
                                            player,
                                            "<green>Waiting for opponent... Bet: <gold>$" + bet);

                                    // Auto-close if no one joins after a delay
                                    Bukkit.getScheduler()
                                            .runTaskLater(
                                                    plugin,
                                                    () -> {
                                                        synchronized (gameLock) {
                                                            if (player2 == null
                                                                    && player1 != null) {
                                                                if (player1Seat == null
                                                                        || player1Seat
                                                                                .getPassengers()
                                                                                .isEmpty()) {
                                                                    close();
                                                                }
                                                            }
                                                        }
                                                    },
                                                    60L * 20L); // 60 seconds
                                });
                betMenu.open(player);
            }
        }
    }

    /** Show confirmation menu to player 2. */
    private void showConfirmMenu(Player player) {
        ServerArcade plugin = (ServerArcade) getGameType().getPlugin();
        Economy economy = ServerArcade.getEconomy();

        ConfirmMenu confirmMenu =
                new ConfirmMenu(
                        45,
                        "§8Confirm - §6$" + bet + " §8vs §e" + player1.getName(),
                        () -> {
                            synchronized (gameLock) {
                                // Double-check conditions inside lock
                                if (player1 == null) {
                                    player.closeInventory();
                                    TextUtil.sendError(player, "The opponent left!");
                                    return;
                                }

                                if (player2 != null) {
                                    TextUtil.sendError(player, "Someone else already joined!");
                                    player.closeInventory();
                                    return;
                                }

                                // Check balance
                                if (economy != null && economy.getBalance(player) < bet) {
                                    TextUtil.send(
                                            player,
                                            "<red>You need <gold>$"
                                                    + bet
                                                    + " <red>to join this game!");
                                    player.closeInventory();
                                    return;
                                }

                                // Atomic assignment
                                player2 = player;

                                // Seat player 2
                                if (player2Seat != null) {
                                    seatPlayer(player, player2Seat);
                                }

                                player.closeInventory();

                                // Open the game for both players
                                getGameType().open(player1, this);
                                getGameType().open(player, this);
                            }
                        },
                        () -> {
                            // Cancel
                            player.closeInventory();
                        });
        confirmMenu.open(player);
    }

    /** Seat a player on an entity. */
    private void seatPlayer(Player player, Entity seat) {
        seat.getPassengers().forEach(seat::removePassenger);
        player.setRotation(seat.getLocation().getYaw(), 0);
        seat.addPassenger(player);
    }

    /** Called when the game ends. */
    public void close() {
        synchronized (gameLock) {
            if (player2 != null && player2Seat != null) {
                player2Seat.removePassenger(player2);
            }
            if (player1 != null && player1Seat != null) {
                player1Seat.removePassenger(player1);
            }
            player1 = null;
            player2 = null;
            bet = -1;
        }
    }

    @Override
    public String getMachineTypeName() {
        return "TwoPlayer";
    }

    public Player getPlayer1() {
        return player1;
    }

    public Player getPlayer2() {
        return player2;
    }

    public Entity getPlayer1Seat() {
        return player1Seat;
    }

    public Entity getPlayer2Seat() {
        return player2Seat;
    }

    public int getBet() {
        return bet;
    }

    public void setBet(int bet) {
        this.bet = bet;
    }

    public boolean isInUse() {
        return player1 != null || player2 != null;
    }

    /** Get status text for holograms/placeholders. */
    public String getStatusText() {
        if (player1 == null) {
            return "§aClick to play!";
        }
        if (bet == -1) {
            return "§eChoosing bet...";
        }
        if (player2 == null) {
            return "§6Waiting for opponent - $" + bet;
        }
        return "§cIn game - $" + bet;
    }

    @Override
    public String getPlaceholder(String property) {
        switch (property.toLowerCase()) {
            case "status":
                if (!isActive()) {
                    return "Inactive";
                }
                synchronized (gameLock) {
                    if (player1 == null) {
                        return "Available";
                    }
                    if (bet == -1) {
                        return "Choosing bet";
                    }
                    if (player2 == null) {
                        return "Waiting for opponent";
                    }
                    return "In game";
                }
            case "player":
                synchronized (gameLock) {
                    if (player1 != null && player2 != null) {
                        return player1.getName() + " vs " + player2.getName();
                    } else if (player1 != null) {
                        return player1.getName() + " (waiting)";
                    }
                    return "None";
                }
            case "player1":
                synchronized (gameLock) {
                    return player1 != null ? player1.getName() : "None";
                }
            case "player2":
                synchronized (gameLock) {
                    return player2 != null ? player2.getName() : "None";
                }
            case "bet":
                synchronized (gameLock) {
                    return bet != -1 ? String.valueOf(bet) : "0";
                }
            case "game":
                return getGameType() != null ? getGameType().getName() : "Unknown";
            case "active":
                return String.valueOf(isActive());
            case "inuse":
                return String.valueOf(isInUse());
            default:
                return super.getPlaceholder(property);
        }
    }
}
