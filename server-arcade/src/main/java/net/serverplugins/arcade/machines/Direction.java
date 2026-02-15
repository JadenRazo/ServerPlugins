package net.serverplugins.arcade.machines;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;

/**
 * Represents a cardinal direction for machine placement. Matches DreamArcade's Direction
 * implementation.
 */
public enum Direction {
    NORTH(0),
    SOUTH(180),
    WEST(90),
    EAST(-90);

    private final int angle;

    Direction(int angle) {
        this.angle = angle;
    }

    public int getAngle() {
        return angle;
    }

    /** Get the direction from a player's yaw. */
    public static Direction fromYaw(float yaw) {
        yaw = (yaw % 360 + 360) % 360;
        if (yaw >= 315 || yaw < 45) return SOUTH;
        if (yaw >= 45 && yaw < 135) return WEST;
        if (yaw >= 135 && yaw < 225) return NORTH;
        return EAST;
    }

    /** Get the direction from an angle. */
    public static Direction fromAngle(int angle) {
        return switch (angle) {
            case -90 -> EAST;
            case 90 -> WEST;
            case 180 -> SOUTH;
            default -> NORTH;
        };
    }

    /** Get the direction a player should face when looking at a location. */
    public static Direction getDirectionFromPlayer(Location playerLoc, Location machineLoc) {
        if (Math.abs(playerLoc.getX() - machineLoc.getX())
                > Math.abs(playerLoc.getZ() - machineLoc.getZ())) {
            return playerLoc.getX() > machineLoc.getX() ? EAST : WEST;
        }
        return playerLoc.getZ() > machineLoc.getZ() ? NORTH : SOUTH;
    }

    /**
     * Apply offset to a location based on this direction. Matches DreamArcade's offset calculation.
     */
    public Location addOffset(Location location, float x, float y, float z) {
        return switch (this) {
            case NORTH -> location.add(x, y, z);
            case SOUTH -> location.add(x, y, -z);
            case WEST -> location.add(-z, y, x);
            case EAST -> location.add(z, y, x);
        };
    }

    /**
     * Transform a relative direction based on this machine's direction. Matches DreamArcade's
     * transformation logic.
     */
    public Direction transformDirection(Direction direction) {
        return switch (this) {
            case NORTH -> direction;
            case SOUTH ->
                    switch (direction) {
                        case NORTH -> SOUTH;
                        case SOUTH -> NORTH;
                        case WEST -> EAST;
                        case EAST -> WEST;
                    };
            case WEST ->
                    switch (direction) {
                        case NORTH -> WEST;
                        case SOUTH -> EAST;
                        case WEST -> NORTH;
                        case EAST -> SOUTH;
                    };
            case EAST ->
                    switch (direction) {
                        case NORTH -> EAST;
                        case SOUTH -> WEST;
                        case WEST -> SOUTH;
                        case EAST -> NORTH;
                    };
        };
    }

    /** Convert to BlockFace. */
    public BlockFace toBlockFace() {
        return switch (this) {
            case NORTH -> BlockFace.NORTH;
            case SOUTH -> BlockFace.SOUTH;
            case EAST -> BlockFace.EAST;
            case WEST -> BlockFace.WEST;
        };
    }
}
