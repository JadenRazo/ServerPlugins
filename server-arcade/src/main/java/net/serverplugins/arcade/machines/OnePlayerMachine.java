package net.serverplugins.arcade.machines;

import java.util.UUID;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.arcade.games.GameType;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/** Machine type for single-player games (Slots, Crash). */
public class OnePlayerMachine extends Machine {

    private Player currentPlayer;
    private Entity seat;

    public OnePlayerMachine(
            String id,
            GameType gameType,
            Location location,
            Direction direction,
            UUID placedBy,
            long placedAt) {
        super(id, gameType, location, direction, placedBy, placedAt);
    }

    public OnePlayerMachine(
            GameType gameType, Location location, Direction direction, UUID placedBy) {
        super(gameType, location, direction, placedBy);
    }

    @Override
    public void place() {
        super.place();
        findSeat();
    }

    /** Find the seat entity for this machine. */
    private void findSeat() {
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
                    this.seat = entity;
                    break;
                }
            }
        }
    }

    @Override
    public synchronized void interact(Player player) {
        if (!isActive()) {
            TextUtil.sendError(player, "This machine is currently inactive.");
            return;
        }

        if (!player.hasPermission("serverarcade.play")) {
            TextUtil.sendError(player, "You don't have permission to use arcade machines.");
            return;
        }

        // Double-check inside synchronized block
        if (currentPlayer != null && currentPlayer.isOnline()) {
            TextUtil.sendError(player, "This machine is currently in use!");
            return;
        }

        // Atomic assignment
        currentPlayer = player;
        getGameType().open(player, this);

        // Seat the player if seat exists
        if (seat != null) {
            seatPlayer(player, seat);
        }
    }

    /** Called when the game ends. */
    public synchronized void close() {
        if (currentPlayer != null && seat != null) {
            seat.removePassenger(currentPlayer);
        }
        currentPlayer = null;
    }

    /** Seat a player on an entity. */
    private void seatPlayer(Player player, Entity seat) {
        seat.getPassengers().forEach(seat::removePassenger);
        player.setRotation(seat.getLocation().getYaw(), 0);
        seat.addPassenger(player);
    }

    @Override
    public String getMachineTypeName() {
        return "OnePlayer";
    }

    public Player getCurrentPlayer() {
        return currentPlayer;
    }

    public Entity getSeat() {
        return seat;
    }

    public boolean isInUse() {
        return currentPlayer != null && currentPlayer.isOnline();
    }

    @Override
    public String getPlaceholder(String property) {
        switch (property.toLowerCase()) {
            case "status":
                if (!isActive()) {
                    return "Inactive";
                }
                return isInUse() ? "In Use" : "Available";
            case "player":
                if (isInUse() && currentPlayer != null) {
                    return currentPlayer.getName();
                }
                return "None";
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
