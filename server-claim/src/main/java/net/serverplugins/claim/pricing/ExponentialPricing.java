package net.serverplugins.claim.pricing;

import net.serverplugins.claim.ClaimConfig;

public class ExponentialPricing {

    private final double basePrice;
    private final double growthRate;
    private final int maxChunksPerClaim;
    private final double claimMultiplier;

    public ExponentialPricing(ClaimConfig config) {
        this.basePrice = config.getBasePrice();
        this.growthRate = config.getGrowthRate();
        this.maxChunksPerClaim = config.getMaxChunksPerClaim();
        this.claimMultiplier = config.getClaimMultiplier();
    }

    /**
     * Get the price for a specific chunk number within a claim.
     *
     * @param chunkNumber The chunk number (1-based) within the claim
     * @return The base price for that chunk (without claim multiplier)
     */
    public double getPrice(int chunkNumber) {
        if (chunkNumber <= 0) return basePrice;
        if (chunkNumber > maxChunksPerClaim) return -1;
        return basePrice * Math.pow(growthRate, chunkNumber - 1);
    }

    /**
     * Get the price for a specific chunk number within a claim, with claim order multiplier.
     *
     * @param chunkNumber The chunk number (1-based) within the claim
     * @param claimOrder The claim's order (1st, 2nd, 3rd claim, etc.)
     * @return The price including claim order multiplier
     */
    public double getPrice(int chunkNumber, int claimOrder) {
        double baseChunkPrice = getPrice(chunkNumber);
        if (baseChunkPrice < 0) return -1;
        double orderMultiplier = getClaimOrderMultiplier(claimOrder);
        return baseChunkPrice * orderMultiplier;
    }

    /**
     * Get the price for a chunk using GLOBAL chunk numbering across all profiles. This prevents
     * gaming by creating new profiles to get cheaper chunks.
     *
     * @param globalChunkNumber The total chunks owned across ALL profiles + 1 (next chunk)
     * @param profileNumber The profile's order (1st, 2nd, 3rd profile, etc.)
     * @return The price including profile multiplier, or -1 if invalid
     */
    public double getGlobalPrice(int globalChunkNumber, int profileNumber) {
        if (globalChunkNumber <= 0) return basePrice;
        double baseChunkPrice = basePrice * Math.pow(growthRate, globalChunkNumber - 1);
        double profileMult = Math.pow(claimMultiplier, Math.max(0, profileNumber - 1));
        return baseChunkPrice * profileMult;
    }

    /**
     * Get the multiplier for a claim based on its order. 1st claim = 1.0x, 2nd = 1.5x, 3rd = 2.25x,
     * etc.
     */
    public double getClaimOrderMultiplier(int claimOrder) {
        if (claimOrder <= 1) return 1.0;
        return Math.pow(claimMultiplier, claimOrder - 1);
    }

    public double getTotalCost(int fromChunk, int toChunk) {
        return getTotalCost(fromChunk, toChunk, 1);
    }

    public double getTotalCost(int fromChunk, int toChunk, int claimOrder) {
        double total = 0;
        for (int i = fromChunk; i <= toChunk; i++) {
            double price = getPrice(i, claimOrder);
            if (price < 0) break;
            total += price;
        }
        return total;
    }

    public int getMaxAffordableChunks(double balance, int currentPurchased) {
        return getMaxAffordableChunks(balance, currentPurchased, 1);
    }

    public int getMaxAffordableChunks(double balance, int currentPurchased, int claimOrder) {
        int chunks = 0;
        double remaining = balance;

        for (int i = currentPurchased + 1; i <= maxChunksPerClaim; i++) {
            double price = getPrice(i, claimOrder);
            if (price < 0 || price > remaining) break;
            remaining -= price;
            chunks++;
        }

        return chunks;
    }

    public String formatPrice(double price) {
        if (price >= 1_000_000_000) {
            // Billions: 1.23B
            return String.format("%.2fB", price / 1_000_000_000);
        } else if (price >= 100_000_000) {
            // 100M+: Show as whole millions (123M)
            return String.format("%.0fM", price / 1_000_000);
        } else if (price >= 1_000_000) {
            // 1M-99.9M: Show 2 decimal places (1.23M)
            return String.format("%.2fM", price / 1_000_000);
        } else if (price >= 100_000) {
            // 100K-999K: Show as whole thousands (123K)
            return String.format("%.0fK", price / 1_000);
        } else if (price >= 10_000) {
            // 10K-99.9K: Show 1 decimal place (12.3K)
            return String.format("%.1fK", price / 1_000);
        } else if (price >= 1_000) {
            // 1K-9.99K: Show 2 decimal places (1.23K)
            return String.format("%.2fK", price / 1_000);
        }
        // Under 1K: Show with commas
        return String.format("%,.0f", price);
    }

    /** Format the claim order multiplier as a display string. e.g., "1.5x" for second claim */
    public String formatMultiplier(int claimOrder) {
        double multiplier = getClaimOrderMultiplier(claimOrder);
        if (multiplier == 1.0) return "1x";
        return String.format("%.2gx", multiplier);
    }

    public double getBasePrice() {
        return basePrice;
    }

    public double getGrowthRate() {
        return growthRate;
    }

    public int getMaxChunksPerClaim() {
        return maxChunksPerClaim;
    }

    public double getClaimMultiplier() {
        return claimMultiplier;
    }

    @Deprecated
    public int getMaxChunks() {
        return maxChunksPerClaim;
    }
}
