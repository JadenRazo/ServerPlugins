package net.serverplugins.arcade;

import java.util.*;
import net.serverplugins.arcade.games.slots.SlotsOddsCalculator;

/** Test to verify the slots odds calculator produces correct win/loss distribution. */
public class SlotsOddsTest {

    public void testOddsDistribution() {
        // Create a map to track outcome distribution
        Map<SlotsOddsCalculator.OutcomeTier, Integer> distribution = new HashMap<>();
        for (SlotsOddsCalculator.OutcomeTier tier : SlotsOddsCalculator.OutcomeTier.values()) {
            distribution.put(tier, 0);
        }

        // Run 10,000 trials
        int trials = 10000;
        for (int i = 0; i < trials; i++) {
            SlotsOddsCalculator.OutcomeTier tier = new SlotsOddsCalculator(null).determineOutcome();
            distribution.merge(tier, 1, Integer::sum);
        }

        // Print results
        System.out.println("\n========== SLOTS ODDS DISTRIBUTION TEST ==========");
        System.out.println("Trials: " + trials);
        System.out.println();

        double totalProbability = 0;
        for (SlotsOddsCalculator.OutcomeTier tier : SlotsOddsCalculator.OutcomeTier.values()) {
            int count = distribution.get(tier);
            double actual = (double) count / trials;
            double expected = tier.getProbability();
            double diff = Math.abs(actual - expected);

            System.out.printf(
                    "%8s: Expected=%5.2f%%, Actual=%5.2f%%, Diff=%5.2f%%, Count=%d%n",
                    tier.name(), expected * 100, actual * 100, diff * 100, count);

            totalProbability += expected;
        }

        System.out.println();
        System.out.println("Total probability sum: " + totalProbability);
        System.out.println();

        // Calculate loss rate
        double lossRate = (double) distribution.get(SlotsOddsCalculator.OutcomeTier.LOSS) / trials;
        double winRate = 1.0 - lossRate;

        System.out.println("Loss Rate: " + String.format("%.2f%%", lossRate * 100));
        System.out.println("Win Rate: " + String.format("%.2f%%", winRate * 100));
        System.out.println("==================================================\n");

        // Assert loss rate is approximately 82%
        if (Math.abs(lossRate - 0.82) > 0.02) {
            System.err.println(
                    "ERROR: Loss rate "
                            + String.format("%.2f%%", lossRate * 100)
                            + " is outside expected range of 80-84%!");
            System.exit(1);
        }

        System.out.println("✓ Test PASSED - Loss rate is within expected range");
    }

    public static void main(String[] args) {
        new SlotsOddsTest().testOddsDistribution();
    }
}
