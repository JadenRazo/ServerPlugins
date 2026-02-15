package net.serverplugins.arcade.games.slots;

/**
 * Defines 2D pattern types for slots games. Patterns are detected on a 3x5 grid (3 rows, 5
 * columns).
 */
public enum PatternType {
    // ============ HORIZONTAL PAYLINES (3 rows) ============
    /**
     * Top horizontal line - all 5 symbols in top row match. Example: X X X X X ← Top - - - - - - -
     * - - -
     */
    TOP_LINE,

    /**
     * Middle horizontal line - all 5 symbols in middle row match. Example: - - - - - X X X X X ←
     * Middle (most common) - - - - -
     */
    MIDDLE_LINE,

    /**
     * Bottom horizontal line - all 5 symbols in bottom row match. Example: - - - - - - - - - - X X
     * X X X ← Bottom
     */
    BOTTOM_LINE,

    // ============ VERTICAL PAYLINES (5 columns) ============
    /** Left column - all 3 symbols in first column match. Example: X - - - - X - - - - X - - - - */
    COLUMN_1,

    /** Second column - all 3 symbols in second column match. */
    COLUMN_2,

    /** Center column - all 3 symbols in third column match. */
    COLUMN_3,

    /** Fourth column - all 3 symbols in fourth column match. */
    COLUMN_4,

    /** Right column - all 3 symbols in fifth column match. */
    COLUMN_5,

    // ============ DIAGONAL PAYLINES ============
    /** Diagonal (top-left to bottom-right). Example: X - - - - - X - - - - - X - - */
    DIAGONAL_DOWN,

    /** Diagonal (bottom-left to top-right). Example: - - X - - - X - - - X - - - - */
    DIAGONAL_UP,

    // ============ ZIGZAG PATTERNS ============
    /** Zigzag down pattern (V shape across 5 reels). Example: X - - - X - X - X - - - X - - */
    ZIGZAG_DOWN,

    /** Zigzag up pattern (inverted V across 5 reels). Example: - - X - - - X - X - X - - - X */
    ZIGZAG_UP,

    /** Wave pattern (up-down-up). Example: X - X - X - X - X - - - - - - */
    WAVE,

    // ============ SPECIAL PATTERNS (harder to hit, higher multiplier) ============
    /** V-shape pattern (7 positions). Example: X - - - X X - - - X - X X X - */
    V_SHAPE,

    /** Diamond pattern (center cross). Example: - - X - - - X X X - - - X - - */
    DIAMOND,

    /** Four corners pattern. Example: X - - - X - - - - - X - - - X */
    FOUR_CORNERS,

    /** Border pattern (entire perimeter). Example: X X X X X X - - - X X X X X X */
    BORDER,

    /** Cross pattern (plus sign). Example: - - X - - X X X X X - - X - - */
    CROSS,

    /** X pattern (both diagonals). Example: X - - - X - X - X - - - X - - */
    X_PATTERN
}
