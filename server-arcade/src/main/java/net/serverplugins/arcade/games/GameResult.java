package net.serverplugins.arcade.games;

public record GameResult(
        boolean won, double bet, double multiplier, double payout, String message) {

    /**
     * Create a win result with overflow protection. SECURITY: Prevents integer overflow exploits by
     * checking result fits in safe range.
     */
    public static GameResult win(double bet, double multiplier) {
        double payout = safeMultiply(bet, multiplier);
        return new GameResult(true, bet, multiplier, payout, null);
    }

    /**
     * Create a win result with overflow protection and custom message. SECURITY: Prevents integer
     * overflow exploits by checking result fits in safe range.
     */
    public static GameResult win(double bet, double multiplier, String message) {
        double payout = safeMultiply(bet, multiplier);
        return new GameResult(true, bet, multiplier, payout, message);
    }

    /**
     * Safely calculate payout from bet and multiplier. SECURITY: Caps payout at Integer.MAX_VALUE
     * to prevent overflow.
     */
    private static double safeMultiply(double bet, double multiplier) {
        double result = bet * multiplier;

        // Cap at Integer.MAX_VALUE to prevent overflow when converting to int later
        if (result > Integer.MAX_VALUE) {
            // Log potential exploit attempt
            System.err.println(
                    "[SECURITY WARNING] Bet calculation overflow prevented: bet="
                            + bet
                            + " multiplier="
                            + multiplier
                            + " result="
                            + result);
            return Integer.MAX_VALUE;
        }

        return result;
    }

    public static GameResult lose(double bet) {
        return new GameResult(false, bet, 0, 0, null);
    }

    public static GameResult lose(double bet, String message) {
        return new GameResult(false, bet, 0, 0, message);
    }

    public static GameResult push(double bet) {
        return new GameResult(false, bet, 1.0, bet, "Push - bet returned");
    }
}
