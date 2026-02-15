package net.serverplugins.claim.models;

public class ClaimBenefits {

    // Level benefits table
    private static final int[] MEMBER_SLOTS = {10, 12, 14, 16, 18, 20, 22, 24, 26, 30};
    private static final int[] WARP_SLOTS = {1, 1, 2, 2, 3, 3, 4, 4, 5, 6};
    private static final double[] UPKEEP_DISC = {0, 5, 10, 15, 20, 25, 30, 35, 40, 50};
    private static final int[] WELCOME_LENGTH = {64, 96, 128, 160, 192, 224, 256, 288, 320, 512};
    private static final int[] PARTICLE_TIER = {1, 1, 2, 2, 2, 3, 3, 3, 4, 4};
    private static final int[] BONUS_CHUNKS = {0, 0, 2, 2, 4, 4, 6, 6, 8, 10};

    private int claimId;
    private int maxMemberSlots;
    private int maxWarpSlots;
    private double upkeepDiscountPercent;
    private int welcomeMessageLength;
    private int particleTier;
    private int bonusChunkSlots;

    public ClaimBenefits(int claimId) {
        this.claimId = claimId;
        applyLevel(1);
    }

    public ClaimBenefits(
            int claimId,
            int maxMemberSlots,
            int maxWarpSlots,
            double upkeepDiscountPercent,
            int welcomeMessageLength,
            int particleTier,
            int bonusChunkSlots) {
        this.claimId = claimId;
        this.maxMemberSlots = maxMemberSlots;
        this.maxWarpSlots = maxWarpSlots;
        this.upkeepDiscountPercent = upkeepDiscountPercent;
        this.welcomeMessageLength = welcomeMessageLength;
        this.particleTier = particleTier;
        this.bonusChunkSlots = bonusChunkSlots;
    }

    public final void applyLevel(int level) {
        int index = Math.max(0, Math.min(level - 1, 9));
        this.maxMemberSlots = MEMBER_SLOTS[index];
        this.maxWarpSlots = WARP_SLOTS[index];
        this.upkeepDiscountPercent = UPKEEP_DISC[index];
        this.welcomeMessageLength = WELCOME_LENGTH[index];
        this.particleTier = PARTICLE_TIER[index];
        this.bonusChunkSlots = BONUS_CHUNKS[index];
    }

    public static ClaimBenefits forLevel(int claimId, int level) {
        ClaimBenefits benefits = new ClaimBenefits(claimId);
        benefits.applyLevel(level);
        return benefits;
    }

    public int getClaimId() {
        return claimId;
    }

    public void setClaimId(int claimId) {
        this.claimId = claimId;
    }

    public int getMaxMemberSlots() {
        return maxMemberSlots;
    }

    public void setMaxMemberSlots(int maxMemberSlots) {
        this.maxMemberSlots = maxMemberSlots;
    }

    public int getMaxWarpSlots() {
        return maxWarpSlots;
    }

    public void setMaxWarpSlots(int maxWarpSlots) {
        this.maxWarpSlots = maxWarpSlots;
    }

    public double getUpkeepDiscountPercent() {
        return upkeepDiscountPercent;
    }

    public void setUpkeepDiscountPercent(double upkeepDiscountPercent) {
        this.upkeepDiscountPercent = upkeepDiscountPercent;
    }

    public int getWelcomeMessageLength() {
        return welcomeMessageLength;
    }

    public void setWelcomeMessageLength(int welcomeMessageLength) {
        this.welcomeMessageLength = welcomeMessageLength;
    }

    public int getParticleTier() {
        return particleTier;
    }

    public void setParticleTier(int particleTier) {
        this.particleTier = particleTier;
    }

    public int getBonusChunkSlots() {
        return bonusChunkSlots;
    }

    public void setBonusChunkSlots(int bonusChunkSlots) {
        this.bonusChunkSlots = bonusChunkSlots;
    }

    public static int getMemberSlotsForLevel(int level) {
        int index = Math.max(0, Math.min(level - 1, 9));
        return MEMBER_SLOTS[index];
    }

    public static int getWarpSlotsForLevel(int level) {
        int index = Math.max(0, Math.min(level - 1, 9));
        return WARP_SLOTS[index];
    }

    public static double getUpkeepDiscountForLevel(int level) {
        int index = Math.max(0, Math.min(level - 1, 9));
        return UPKEEP_DISC[index];
    }
}
