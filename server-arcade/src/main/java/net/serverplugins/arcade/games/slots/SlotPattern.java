package net.serverplugins.arcade.games.slots;

import java.util.ArrayList;
import java.util.List;

/** Utility class for detecting 2D patterns in slot grids. */
public class SlotPattern {

    /**
     * Check if the given 2D grid matches the specified pattern type.
     *
     * @param grid 2D array of SlotItems (rows x columns)
     * @param pattern The pattern type to check
     * @param requiredItem The item that must match (null means any matching items)
     * @return true if the pattern is detected
     */
    public static boolean matches(SlotItem[][] grid, PatternType pattern, SlotItem requiredItem) {
        if (grid == null || grid.length == 0) return false;

        int rows = grid.length;
        int cols = grid[0].length;

        List<Position> positions = getPatternPositions(pattern, rows, cols);
        if (positions.isEmpty()) return false;

        // Get the first item to compare against
        Position first = positions.get(0);
        SlotItem referenceItem = grid[first.row][first.col];

        if (referenceItem == null) return false;

        // If a specific item is required, check it matches
        if (requiredItem != null && !requiredItem.matches(referenceItem)) {
            return false;
        }

        // Check all positions in the pattern match the reference item
        for (Position pos : positions) {
            if (pos.row >= rows || pos.col >= cols) continue;
            SlotItem current = grid[pos.row][pos.col];
            if (current == null || !referenceItem.matches(current)) {
                return false;
            }
        }

        return true;
    }

    /** Get the grid positions that define the pattern. */
    public static List<Position> getPatternPositions(PatternType pattern, int rows, int cols) {
        List<Position> positions = new ArrayList<>();

        switch (pattern) {
                // ============ HORIZONTAL LINES ============
            case TOP_LINE -> {
                // Top row (row 0)
                for (int c = 0; c < cols; c++) {
                    positions.add(new Position(0, c));
                }
            }
            case MIDDLE_LINE -> {
                // Middle row (row 1)
                for (int c = 0; c < cols; c++) {
                    positions.add(new Position(1, c));
                }
            }
            case BOTTOM_LINE -> {
                // Bottom row (row 2)
                for (int c = 0; c < cols; c++) {
                    positions.add(new Position(2, c));
                }
            }

                // ============ VERTICAL COLUMNS ============
            case COLUMN_1 -> {
                // First column
                for (int r = 0; r < rows; r++) {
                    positions.add(new Position(r, 0));
                }
            }
            case COLUMN_2 -> {
                // Second column
                for (int r = 0; r < rows; r++) {
                    positions.add(new Position(r, 1));
                }
            }
            case COLUMN_3 -> {
                // Third column (center)
                for (int r = 0; r < rows; r++) {
                    positions.add(new Position(r, 2));
                }
            }
            case COLUMN_4 -> {
                // Fourth column
                for (int r = 0; r < rows; r++) {
                    positions.add(new Position(r, 3));
                }
            }
            case COLUMN_5 -> {
                // Fifth column
                for (int r = 0; r < rows; r++) {
                    positions.add(new Position(r, 4));
                }
            }

                // ============ DIAGONALS ============
            case DIAGONAL_DOWN -> {
                // Top-left to bottom-right (3 positions for 3x5 grid)
                positions.add(new Position(0, 0));
                positions.add(new Position(1, 1));
                positions.add(new Position(2, 2));
            }
            case DIAGONAL_UP -> {
                // Bottom-left to top-right
                positions.add(new Position(2, 0));
                positions.add(new Position(1, 1));
                positions.add(new Position(0, 2));
            }

                // ============ ZIGZAG PATTERNS ============
            case ZIGZAG_DOWN -> {
                // V shape across all 5 reels
                positions.add(new Position(0, 0)); // Top-left
                positions.add(new Position(1, 1)); // Down
                positions.add(new Position(2, 2)); // Bottom center
                positions.add(new Position(1, 3)); // Up
                positions.add(new Position(0, 4)); // Top-right
            }
            case ZIGZAG_UP -> {
                // Inverted V shape across all 5 reels
                positions.add(new Position(2, 0)); // Bottom-left
                positions.add(new Position(1, 1)); // Up
                positions.add(new Position(0, 2)); // Top center
                positions.add(new Position(1, 3)); // Down
                positions.add(new Position(2, 4)); // Bottom-right
            }
            case WAVE -> {
                // Wave pattern (up-down-up)
                positions.add(new Position(0, 0)); // Top
                positions.add(new Position(1, 1)); // Middle
                positions.add(new Position(0, 2)); // Top
                positions.add(new Position(1, 3)); // Middle
                positions.add(new Position(0, 4)); // Top
            }

                // ============ SPECIAL PATTERNS ============
            case V_SHAPE -> {
                // V-shape: top corners down to bottom center
                if (rows >= 3 && cols >= 5) {
                    positions.add(new Position(0, 0));
                    positions.add(new Position(0, cols - 1));
                    positions.add(new Position(1, 0));
                    positions.add(new Position(1, cols - 1));
                    positions.add(new Position(2, 1));
                    positions.add(new Position(2, 2));
                    positions.add(new Position(2, cols - 2));
                }
            }
            case DIAMOND -> {
                // Center diamond/cross pattern
                if (rows >= 3 && cols >= 3) {
                    int midRow = rows / 2;
                    int midCol = cols / 2;
                    positions.add(new Position(midRow - 1, midCol));
                    positions.add(new Position(midRow, midCol - 1));
                    positions.add(new Position(midRow, midCol));
                    positions.add(new Position(midRow, midCol + 1));
                    positions.add(new Position(midRow + 1, midCol));
                }
            }
            case FOUR_CORNERS -> {
                // Four corners of the grid
                positions.add(new Position(0, 0));
                positions.add(new Position(0, cols - 1));
                positions.add(new Position(rows - 1, 0));
                positions.add(new Position(rows - 1, cols - 1));
            }
            case BORDER -> {
                // Entire perimeter
                // Top row
                for (int c = 0; c < cols; c++) {
                    positions.add(new Position(0, c));
                }
                // Bottom row
                for (int c = 0; c < cols; c++) {
                    positions.add(new Position(rows - 1, c));
                }
                // Left column (excluding corners)
                for (int r = 1; r < rows - 1; r++) {
                    positions.add(new Position(r, 0));
                }
                // Right column (excluding corners)
                for (int r = 1; r < rows - 1; r++) {
                    positions.add(new Position(r, cols - 1));
                }
            }
            case CROSS -> {
                // Plus sign (horizontal and vertical through center)
                int midRow = rows / 2;
                int midCol = cols / 2;
                // Horizontal line
                for (int c = 0; c < cols; c++) {
                    positions.add(new Position(midRow, c));
                }
                // Vertical line (excluding center which is already added)
                for (int r = 0; r < rows; r++) {
                    if (r != midRow) {
                        positions.add(new Position(r, midCol));
                    }
                }
            }
            case X_PATTERN -> {
                // Both diagonals
                int steps = Math.min(rows, cols);
                // Descending diagonal
                for (int i = 0; i < steps; i++) {
                    positions.add(new Position(i, i));
                }
                // Ascending diagonal (excluding center if odd dimensions)
                for (int i = 0; i < steps; i++) {
                    int r = rows - 1 - i;
                    int c = i;
                    if (r != i) { // Avoid duplicate center
                        positions.add(new Position(r, c));
                    }
                }
            }
        }

        return positions;
    }

    /** Represents a position in the grid. */
    public static class Position {
        public final int row;
        public final int col;

        public Position(int row, int col) {
            this.row = row;
            this.col = col;
        }

        @Override
        public String toString() {
            return "(" + row + "," + col + ")";
        }
    }
}
