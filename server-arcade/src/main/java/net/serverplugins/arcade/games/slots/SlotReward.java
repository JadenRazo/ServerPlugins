package net.serverplugins.arcade.games.slots;

import java.util.ArrayList;
import java.util.List;
import net.serverplugins.arcade.utils.GameCommand;

/** Represents a reward for matching slot items. */
public abstract class SlotReward {

    protected final List<GameCommand> commands;
    protected List<Position> matchedPositions = new ArrayList<>();

    public SlotReward(List<GameCommand> commands) {
        this.commands = commands;
    }

    /** Check if this reward applies to the given results. */
    public abstract boolean check(SlotItem[] results);

    /** Get the priority/value of this reward (higher = better). */
    public abstract int getValue(int bet);

    /** Get the positions that matched for this reward. Used for highlighting winning slots. */
    public List<Position> getMatchedPositions() {
        return matchedPositions;
    }

    public List<GameCommand> getCommands() {
        return commands;
    }

    /** Represents a position in the 2D slot grid. */
    public static class Position {
        public final int row;
        public final int col;

        public Position(int row, int col) {
            this.row = row;
            this.col = col;
        }
    }

    /** Row reward - requires N matching items of a specific type. */
    public static class RowReward extends SlotReward {
        private final SlotItem item;
        private final int requiredCount;

        public RowReward(SlotItem item, int requiredCount, List<GameCommand> commands) {
            super(commands);
            this.item = item;
            this.requiredCount = requiredCount;
        }

        @Override
        public boolean check(SlotItem[] results) {
            matchedPositions.clear();
            int matches = 0;
            for (int i = 0; i < results.length; i++) {
                if (item.matches(results[i])) {
                    matches++;
                    // Assume middle row (row 1) for 1D results
                    matchedPositions.add(new Position(1, i));
                }
            }
            return matches >= requiredCount;
        }

        @Override
        public int getValue(int bet) {
            // Calculate total money reward value
            int total = 0;
            for (GameCommand cmd : commands) {
                if (cmd instanceof GameCommand.MoneyCommand moneyCmd) {
                    total += moneyCmd.calculate(bet);
                }
            }
            return total;
        }

        public SlotItem getItem() {
            return item;
        }

        public int getRequiredCount() {
            return requiredCount;
        }
    }

    /** Exact match reward - requires specific items in specific positions. */
    public static class ExactMatchReward extends SlotReward {
        private final SlotItem[] requiredItems;

        public ExactMatchReward(SlotItem[] requiredItems, List<GameCommand> commands) {
            super(commands);
            this.requiredItems = requiredItems;
        }

        @Override
        public boolean check(SlotItem[] results) {
            matchedPositions.clear();
            if (results.length != requiredItems.length) return false;

            for (int i = 0; i < results.length; i++) {
                if (!requiredItems[i].matches(results[i])) {
                    return false;
                }
            }

            // All matched, add all positions
            for (int i = 0; i < results.length; i++) {
                matchedPositions.add(new Position(1, i)); // Middle row
            }
            return true;
        }

        @Override
        public int getValue(int bet) {
            int total = 0;
            for (GameCommand cmd : commands) {
                if (cmd instanceof GameCommand.MoneyCommand moneyCmd) {
                    total += moneyCmd.calculate(bet);
                }
            }
            return total;
        }
    }

    /**
     * Pattern reward - requires a 2D pattern match on the grid. This enables detection of shapes
     * like lines, diagonals, crosses, etc.
     */
    public static class PatternReward extends SlotReward {
        private final PatternType patternType;
        private final SlotItem requiredItem; // null means any matching items

        public PatternReward(
                PatternType patternType, SlotItem requiredItem, List<GameCommand> commands) {
            super(commands);
            this.patternType = patternType;
            this.requiredItem = requiredItem;
        }

        @Override
        public boolean check(SlotItem[] results) {
            // Convert 1D results to 2D grid (assuming 3x5 grid)
            // This method is for compatibility with existing code
            // The proper check happens in check(SlotItem[][])
            return false; // Not used for pattern matching
        }

        /** Check if the 2D grid matches the pattern. */
        public boolean check(SlotItem[][] grid) {
            matchedPositions.clear();
            boolean matches = SlotPattern.matches(grid, patternType, requiredItem);

            if (matches) {
                // Get the pattern positions and add them
                int rows = grid.length;
                int cols = grid[0].length;
                List<SlotPattern.Position> patternPositions =
                        SlotPattern.getPatternPositions(patternType, rows, cols);

                for (SlotPattern.Position pos : patternPositions) {
                    matchedPositions.add(new Position(pos.row, pos.col));
                }
            }

            return matches;
        }

        @Override
        public int getValue(int bet) {
            int total = 0;
            for (GameCommand cmd : commands) {
                if (cmd instanceof GameCommand.MoneyCommand moneyCmd) {
                    total += moneyCmd.calculate(bet);
                }
            }
            return total;
        }

        public PatternType getPatternType() {
            return patternType;
        }

        public SlotItem getRequiredItem() {
            return requiredItem;
        }
    }
}
