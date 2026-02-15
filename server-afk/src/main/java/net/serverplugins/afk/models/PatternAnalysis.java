package net.serverplugins.afk.models;

import java.time.LocalDateTime;
import java.util.UUID;

public class PatternAnalysis {

    public enum PatternType {
        REPETITIVE_MOVEMENT, // Player moving in exact same pattern
        MINIMAL_ACTIVITY, // Activity just enough to avoid detection
        MACRO_SUSPECTED, // Automated input detected
        POOL_ROTATION, // Swimming in circles
        AUTO_CLICKER, // Consistent clicking pattern
        SUSPICIOUS_TIMING, // Perfect timing intervals
        NO_VARIANCE, // No human-like variance in behavior
        FAILED_VERIFICATION // Failed anti-AFK verification
    }

    public enum AutoAction {
        NONE,
        WARNING_SENT,
        VERIFICATION_REQUIRED,
        REWARDS_PAUSED,
        SESSION_ENDED,
        TEMPORARILY_BANNED
    }

    private int id;
    private UUID playerUuid;
    private LocalDateTime analysisTime;
    private PatternType patternType;
    private double confidenceScore;
    private boolean flaggedForReview;
    private AutoAction autoActionTaken;
    private boolean adminReviewed;
    private String adminNotes;

    public PatternAnalysis(UUID playerUuid, PatternType patternType, double confidenceScore) {
        this.playerUuid = playerUuid;
        this.patternType = patternType;
        this.confidenceScore = confidenceScore;
        this.analysisTime = LocalDateTime.now();
        this.flaggedForReview = confidenceScore >= 0.7; // Flag if 70% or more confident
        this.autoActionTaken = AutoAction.NONE;
        this.adminReviewed = false;
        this.adminNotes = null;
    }

    public PatternAnalysis() {
        // Empty constructor for database mapping
    }

    // Utility methods

    public boolean isSuspicious() {
        return confidenceScore >= 0.5;
    }

    public boolean isHighConfidence() {
        return confidenceScore >= 0.8;
    }

    public String getConfidenceLevel() {
        if (confidenceScore >= 0.9) {
            return "Very High";
        } else if (confidenceScore >= 0.7) {
            return "High";
        } else if (confidenceScore >= 0.5) {
            return "Medium";
        } else if (confidenceScore >= 0.3) {
            return "Low";
        } else {
            return "Very Low";
        }
    }

    public String getFormattedConfidence() {
        return String.format("%.1f%%", confidenceScore * 100);
    }

    // Getters and Setters

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public LocalDateTime getAnalysisTime() {
        return analysisTime;
    }

    public void setAnalysisTime(LocalDateTime analysisTime) {
        this.analysisTime = analysisTime;
    }

    public PatternType getPatternType() {
        return patternType;
    }

    public void setPatternType(PatternType patternType) {
        this.patternType = patternType;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public boolean isFlaggedForReview() {
        return flaggedForReview;
    }

    public void setFlaggedForReview(boolean flaggedForReview) {
        this.flaggedForReview = flaggedForReview;
    }

    public AutoAction getAutoActionTaken() {
        return autoActionTaken;
    }

    public void setAutoActionTaken(AutoAction autoActionTaken) {
        this.autoActionTaken = autoActionTaken;
    }

    public boolean isAdminReviewed() {
        return adminReviewed;
    }

    public void setAdminReviewed(boolean adminReviewed) {
        this.adminReviewed = adminReviewed;
    }

    public String getAdminNotes() {
        return adminNotes;
    }

    public void setAdminNotes(String adminNotes) {
        this.adminNotes = adminNotes;
    }
}
