package net.serverplugins.arcade.games.slots;

import java.util.*;

/**
 * Calculates slot machine outcomes based on target RTP (Return To Player). Uses a tiered outcome
 * system where the payout tier is determined first, then symbols are generated to match that
 * outcome.
 */
public class SlotsOddsCalculator {

    // Target RTP - 45% means players get back 45 cents per dollar on average
    private static final double TARGET_RTP = 0.45;

    // Outcome tiers with probabilities designed to achieve ~45% RTP
    // Probabilities and multipliers are balanced to achieve target RTP:
    // Expected value = sum(probability * avgMultiplier) = 0.45
    public enum OutcomeTier {
        LOSS(0.82, 0, 0), // 82% - No win
        SMALL(0.12, 1.5, 1.5), // 12% - Small win (1.5x) -> 0.12*1.5=0.18
        MEDIUM(0.04, 2.5, 2.5), // 4% - Medium win (2.5x) -> 0.04*2.5=0.10
        LARGE(0.015, 5.0, 5.0), // 1.5% - Large win (5.0x) -> 0.015*5.0=0.075
        HUGE(0.004, 10, 10), // 0.4% - Huge win (10x) -> 0.004*10=0.04
        JACKPOT(0.001, 50, 50); // 0.1% - Jackpot (50x) -> 0.001*50=0.05
        // Total RTP = 0 + 0.18 + 0.10 + 0.075 + 0.04 + 0.05 = 0.445 (~45% RTP)
        // High house advantage for casino profitability
        // Win rate: 18% | Loss rate: 82% | House edge: 55%

        private final double probability;
        private final double minMultiplier;
        private final double maxMultiplier;
        private final double cumulativeProbability;

        OutcomeTier(double probability, double minMult, double maxMult) {
            this.probability = probability;
            this.minMultiplier = minMult;
            this.maxMultiplier = maxMult;
            this.cumulativeProbability = 0; // Set in static block
        }

        public double getProbability() {
            return probability;
        }

        public double getMinMultiplier() {
            return minMultiplier;
        }

        public double getMaxMultiplier() {
            return maxMultiplier;
        }
    }

    // Cumulative probabilities for tier selection
    private static final double[] CUMULATIVE_PROBS;

    static {
        CUMULATIVE_PROBS = new double[OutcomeTier.values().length];
        double cumulative = 0;
        for (int i = 0; i < OutcomeTier.values().length; i++) {
            cumulative += OutcomeTier.values()[i].probability;
            CUMULATIVE_PROBS[i] = cumulative;
        }
    }

    private final Random random = new Random();
    private final SlotsType slotsType;

    // Symbol tiers (from high value to low value)
    private final List<SlotItem> highValueSymbols = new ArrayList<>(); // seven, star
    private final List<SlotItem> midValueSymbols = new ArrayList<>(); // grapes, melon
    private final List<SlotItem> lowValueSymbols = new ArrayList<>(); // plum, lemon, orange, cherry

    public SlotsOddsCalculator(SlotsType slotsType) {
        this.slotsType = slotsType;
        categorizeSymbols();
    }

    private void categorizeSymbols() {
        Map<String, SlotItem> itemMap = slotsType.getItemMap();

        // Categorize by ID (matches typical slot machine symbol values)
        for (Map.Entry<String, SlotItem> entry : itemMap.entrySet()) {
            String id = entry.getKey().toLowerCase();
            SlotItem item = entry.getValue();

            if (id.equals("seven") || id.equals("star")) {
                highValueSymbols.add(item);
            } else if (id.equals("grapes") || id.equals("melon")) {
                midValueSymbols.add(item);
            } else {
                lowValueSymbols.add(item);
            }
        }

        // Fallback: if no categorization worked, use weight-based sorting
        if (highValueSymbols.isEmpty() && midValueSymbols.isEmpty()) {
            List<SlotItem> sorted = new ArrayList<>(itemMap.values());
            sorted.sort(Comparator.comparingInt(SlotItem::getWeight));

            int third = sorted.size() / 3;
            for (int i = 0; i < sorted.size(); i++) {
                if (i < third) {
                    highValueSymbols.add(sorted.get(i));
                } else if (i < third * 2) {
                    midValueSymbols.add(sorted.get(i));
                } else {
                    lowValueSymbols.add(sorted.get(i));
                }
            }
        }
    }

    /** Determine the outcome tier for this spin. */
    public OutcomeTier determineOutcome() {
        double roll = random.nextDouble();

        for (int i = 0; i < CUMULATIVE_PROBS.length; i++) {
            if (roll < CUMULATIVE_PROBS[i]) {
                OutcomeTier tier = OutcomeTier.values()[i];
                org.bukkit.Bukkit.getLogger()
                        .info(
                                "[SLOTS DEBUG] determineOutcome() - Roll: "
                                        + String.format("%.6f", roll)
                                        + " -> Selected tier: "
                                        + tier.name()
                                        + " (probability: "
                                        + String.format("%.2f", tier.getProbability() * 100)
                                        + "%)");
                return tier;
            }
        }

        org.bukkit.Bukkit.getLogger()
                .info(
                        "[SLOTS DEBUG] determineOutcome() - Roll: "
                                + String.format("%.6f", roll)
                                + " -> Defaulting to LOSS");
        return OutcomeTier.LOSS;
    }

    /**
     * Generate symbols for the given outcome tier. The symbols are arranged to produce the desired
     * result.
     */
    public SlotItem[] generateSymbols(OutcomeTier tier, int reelCount) {
        SlotItem[] result = new SlotItem[reelCount];

        switch (tier) {
            case LOSS -> generateLosingCombination(result);
            case SMALL -> generateSmallWin(result);
            case MEDIUM -> generateMediumWin(result);
            case LARGE -> generateLargeWin(result);
            case HUGE -> generateHugeWin(result);
            case JACKPOT -> generateJackpot(result);
        }

        return result;
    }

    /**
     * Generate a losing combination - no 3+ matches of any symbol (including wildcards).
     * WILDCARD-AWARE: With wildcards enabled, we must ensure that even accounting for wildcard
     * matches, no symbol can reach 3+ total matches.
     */
    private void generateLosingCombination(SlotItem[] result) {
        List<SlotItem> allItems = new ArrayList<>(slotsType.getItemMap().values());

        // Identify the wildcard (star)
        SlotItem wildcard = slotsType.getItemMap().get("star");

        // Strategy with wildcards:
        // - Allow maximum 1 wildcard in losing combinations
        // - If wildcard is present, all other symbols can appear at most once
        // - This ensures: 1 wildcard + 1 other symbol = 2 matches (not 3+)
        Map<SlotItem, Integer> counts = new HashMap<>();
        int wildcardCount = 0;

        for (int i = 0; i < result.length; i++) {
            List<SlotItem> available = new ArrayList<>();

            for (SlotItem item : allItems) {
                boolean isWildcard = wildcard != null && item.equals(wildcard);
                int currentCount = counts.getOrDefault(item, 0);

                if (isWildcard) {
                    // Allow max 1 wildcard total
                    if (wildcardCount < 1) {
                        available.add(item);
                    }
                } else {
                    // For non-wildcards: if we have a wildcard, only allow 1 of each symbol
                    // If no wildcard, allow up to 2 of each symbol
                    int maxAllowed = wildcardCount > 0 ? 1 : 2;
                    if (currentCount < maxAllowed) {
                        available.add(item);
                    }
                }
            }

            // Fallback: use items with lowest count (excluding wildcards if we already have one)
            if (available.isEmpty()) {
                int minCount = Integer.MAX_VALUE;
                for (SlotItem item : allItems) {
                    boolean isWildcard = wildcard != null && item.equals(wildcard);
                    if (isWildcard && wildcardCount > 0) continue; // Skip wildcard if already used

                    int count = counts.getOrDefault(item, 0);
                    if (count < minCount) minCount = count;
                }

                for (SlotItem item : allItems) {
                    boolean isWildcard = wildcard != null && item.equals(wildcard);
                    if (isWildcard && wildcardCount > 0) continue;

                    if (counts.getOrDefault(item, 0) == minCount) {
                        available.add(item);
                    }
                }
            }

            // If still empty, use any non-wildcard with lowest count
            if (available.isEmpty()) {
                available =
                        allItems.stream()
                                .filter(item -> wildcard == null || !item.equals(wildcard))
                                .sorted(
                                        Comparator.comparingInt(
                                                item -> counts.getOrDefault(item, 0)))
                                .limit(3)
                                .toList();
            }

            SlotItem chosen = available.get(random.nextInt(available.size()));
            result[i] = chosen;

            if (wildcard != null && chosen.equals(wildcard)) {
                wildcardCount++;
            }
            counts.merge(chosen, 1, Integer::sum);
        }

        // Log the final losing combination to verify correctness
        StringBuilder combo = new StringBuilder("[");
        for (int i = 0; i < result.length; i++) {
            combo.append(result[i].getId());
            if (i < result.length - 1) combo.append(", ");
        }
        combo.append("]");
        org.bukkit.Bukkit.getLogger()
                .info(
                        "[SLOTS DEBUG] generateLosingCombination() - Final combination: "
                                + combo
                                + " - Counts: "
                                + counts
                                + " - Wildcards: "
                                + wildcardCount);
    }

    /** Generate a small win - 3 matches of a low-value symbol. */
    private void generateSmallWin(SlotItem[] result) {
        SlotItem winSymbol =
                getRandomFromList(
                        lowValueSymbols.isEmpty()
                                ? new ArrayList<>(slotsType.getItemMap().values())
                                : lowValueSymbols);

        // Place 3 matching symbols
        generateWinWithMatches(result, winSymbol, 3);
    }

    /** Generate a medium win - 3-4 matches of a mid-value symbol. */
    private void generateMediumWin(SlotItem[] result) {
        List<SlotItem> pool = midValueSymbols.isEmpty() ? lowValueSymbols : midValueSymbols;
        if (pool.isEmpty()) pool = new ArrayList<>(slotsType.getItemMap().values());

        SlotItem winSymbol = getRandomFromList(pool);
        int matches = random.nextBoolean() ? 3 : 4;

        generateWinWithMatches(result, winSymbol, matches);
    }

    /** Generate a large win - 4-5 matches of a mid-value symbol or 3 high-value. */
    private void generateLargeWin(SlotItem[] result) {
        if (!highValueSymbols.isEmpty() && random.nextDouble() < 0.3) {
            // 30% chance: 3 high-value symbols
            SlotItem winSymbol = getRandomFromList(highValueSymbols);
            generateWinWithMatches(result, winSymbol, 3);
        } else {
            // 70% chance: 4-5 mid-value symbols
            List<SlotItem> pool = midValueSymbols.isEmpty() ? lowValueSymbols : midValueSymbols;
            if (pool.isEmpty()) pool = new ArrayList<>(slotsType.getItemMap().values());

            SlotItem winSymbol = getRandomFromList(pool);
            int matches = random.nextBoolean() ? 4 : 5;
            generateWinWithMatches(result, winSymbol, matches);
        }
    }

    /** Generate a huge win - 4-5 high-value matches. */
    private void generateHugeWin(SlotItem[] result) {
        List<SlotItem> pool = highValueSymbols.isEmpty() ? midValueSymbols : highValueSymbols;
        if (pool.isEmpty()) pool = new ArrayList<>(slotsType.getItemMap().values());

        SlotItem winSymbol = getRandomFromList(pool);
        int matches = random.nextBoolean() ? 4 : 5;

        generateWinWithMatches(result, winSymbol, matches);
    }

    /** Generate a jackpot - 5 of the highest value symbol (typically "seven"). */
    private void generateJackpot(SlotItem[] result) {
        SlotItem jackpotSymbol = slotsType.getItemMap().get("seven");
        if (jackpotSymbol == null && !highValueSymbols.isEmpty()) {
            jackpotSymbol = highValueSymbols.get(0);
        }
        if (jackpotSymbol == null) {
            jackpotSymbol = getRandomFromList(new ArrayList<>(slotsType.getItemMap().values()));
        }

        // Fill all reels with the jackpot symbol
        Arrays.fill(result, jackpotSymbol);
    }

    /** Helper: Generate a winning combination with specified number of matches. */
    private void generateWinWithMatches(SlotItem[] result, SlotItem winSymbol, int matchCount) {
        List<SlotItem> allItems = new ArrayList<>(slotsType.getItemMap().values());
        allItems.remove(winSymbol); // Remove win symbol from filler pool

        // Create positions for win symbols
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < result.length; i++) {
            positions.add(i);
        }
        Collections.shuffle(positions);

        // Place win symbols
        for (int i = 0; i < Math.min(matchCount, result.length); i++) {
            result[positions.get(i)] = winSymbol;
        }

        // Fill remaining positions with different symbols
        for (int i = matchCount; i < result.length; i++) {
            if (!allItems.isEmpty()) {
                result[positions.get(i)] = allItems.get(random.nextInt(allItems.size()));
            } else {
                result[positions.get(i)] = winSymbol;
            }
        }
    }

    private SlotItem getRandomFromList(List<SlotItem> list) {
        if (list == null || list.isEmpty()) {
            return slotsType.getRandomItem();
        }
        return list.get(random.nextInt(list.size()));
    }

    /**
     * Generate a 2D grid of symbols based on outcome tier. This supports pattern-based rewards on
     * 3x5 grids.
     *
     * @param rows Number of rows (typically 3)
     * @param cols Number of columns (typically 5)
     * @return 2D array of SlotItems
     */
    public SlotItem[][] calculateGrid(int rows, int cols) {
        OutcomeTier tier = determineOutcome();
        SlotItem[][] grid = new SlotItem[rows][cols];

        // Generate the middle row using existing logic
        SlotItem[] middleRow = generateSymbols(tier, cols);

        // Place middle row in the grid
        int midRow = rows / 2;
        grid[midRow] = middleRow;

        // Fill other rows with random items
        for (int r = 0; r < rows; r++) {
            if (r == midRow) continue; // Skip middle row (already set)

            for (int c = 0; c < cols; c++) {
                grid[r][c] = slotsType.getRandomItem();
            }
        }

        return grid;
    }

    /** Convenience method: determine outcome and generate symbols in one call. */
    public SlotItem[] calculateSpin(int reelCount) {
        OutcomeTier tier = determineOutcome();

        // Log the determined outcome
        org.bukkit.Bukkit.getLogger()
                .info(
                        "[SLOTS DEBUG] determineOutcome() - Selected tier: "
                                + tier.name()
                                + " (probability: "
                                + String.format("%.2f%%", tier.getProbability() * 100)
                                + ")");

        // Generate symbols for this tier, validating if it's a LOSS tier
        SlotItem[] symbols = null;
        int attempts = 0;
        int maxAttempts = 100;

        while (symbols == null && attempts < maxAttempts) {
            attempts++;
            SlotItem[] candidate = generateSymbols(tier, reelCount);

            // Log the candidate
            StringBuilder combo = new StringBuilder("[");
            for (int i = 0; i < candidate.length; i++) {
                combo.append(candidate[i].getId());
                if (i < candidate.length - 1) combo.append(", ");
            }
            combo.append("]");

            // CRITICAL VALIDATION: If tier is LOSS, verify no symbol has 3+ matches (including
            // wildcards)
            if (tier == OutcomeTier.LOSS && slotsType != null) {
                SlotItem wildcard = slotsType.getItemMap().get("star");

                // Count wildcards in the candidate result
                int wildcardCount = 0;
                if (wildcard != null) {
                    for (SlotItem item : candidate) {
                        if (item.equals(wildcard)) {
                            wildcardCount++;
                        }
                    }
                }

                StringBuilder matchLog = new StringBuilder();
                boolean hasInvalidCombo = false;

                for (SlotItem symbol : slotsType.getItemMap().values()) {
                    // Skip wildcard itself - we don't care if wildcard "matches" everything
                    if (wildcard != null && symbol.equals(wildcard)) {
                        matchLog.append(symbol.getId()).append(":WILDCARD(skipped) ");
                        continue;
                    }

                    // For non-wildcard symbols, count literal occurrences
                    int literalCount = 0;
                    for (SlotItem item : candidate) {
                        if (item.equals(symbol)) {
                            literalCount++;
                        }
                    }

                    // Total matches = literal count + wildcard count
                    int totalMatches = literalCount + wildcardCount;
                    matchLog.append(symbol.getId())
                            .append(":")
                            .append(literalCount)
                            .append("+")
                            .append(wildcardCount)
                            .append("=")
                            .append(totalMatches)
                            .append(" ");

                    if (totalMatches >= 3) {
                        hasInvalidCombo = true;
                    }
                }

                if (hasInvalidCombo) {
                    org.bukkit.Bukkit.getLogger()
                            .warning(
                                    "[SLOTS] LOSS tier attempt "
                                            + attempts
                                            + " invalid: "
                                            + combo
                                            + " - Matches: "
                                            + matchLog
                                            + " - Regenerating SAME tier...");
                    continue; // Regenerate symbols for SAME tier (don't change outcome)
                } else {
                    org.bukkit.Bukkit.getLogger()
                            .info(
                                    "[SLOTS DEBUG] LOSS validation passed - Wildcards:"
                                            + wildcardCount
                                            + " - "
                                            + matchLog);
                    symbols = candidate; // Valid losing combination
                }
            } else {
                // Non-LOSS tier, accept as-is
                symbols = candidate;
            }

            if (symbols != null) {
                org.bukkit.Bukkit.getLogger()
                        .info(
                                "[SLOTS DEBUG] calculateSpin() - Final: Tier="
                                        + tier.name()
                                        + ", Symbols="
                                        + combo
                                        + (attempts > 1
                                                ? " (took " + attempts + " attempts)"
                                                : ""));
            }
        }

        if (symbols == null) {
            // Fallback: after max attempts, force a simple losing combo
            org.bukkit.Bukkit.getLogger()
                    .severe(
                            "[SLOTS CRITICAL ERROR] Could not generate valid LOSS combo after "
                                    + maxAttempts
                                    + " attempts! Using fallback.");
            symbols = generateSimpleLoss(reelCount);
        }

        return symbols;
    }

    /** Fallback: Generate a simple guaranteed loss with no matching symbols. */
    private SlotItem[] generateSimpleLoss(int reelCount) {
        List<SlotItem> allItems = new ArrayList<>(slotsType.getItemMap().values());
        SlotItem[] result = new SlotItem[reelCount];

        // Use different symbols for each position
        for (int i = 0; i < reelCount && i < allItems.size(); i++) {
            result[i] = allItems.get(i);
        }

        // If we have more reels than unique symbols, fill with randoms ensuring no 3+ matches
        for (int i = allItems.size(); i < reelCount; i++) {
            result[i] = allItems.get(i % allItems.size());
        }

        return result;
    }
}
