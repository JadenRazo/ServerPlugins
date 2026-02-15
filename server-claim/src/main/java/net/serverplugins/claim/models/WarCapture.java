package net.serverplugins.claim.models;

import java.time.Instant;

public class WarCapture {

    public static final int MAX_PROGRESS = 100;
    public static final int PROGRESS_PER_TICK = 1;
    public static final long TICK_INTERVAL_SECONDS = 6; // Every 6 seconds = 10 min to capture

    private int id;
    private int warId;
    private String chunkWorld;
    private int chunkX;
    private int chunkZ;
    private Integer capturingNationId;
    private int captureProgress;
    private Instant lastProgressUpdate;
    private Instant capturedAt;

    public WarCapture() {
        this.captureProgress = 0;
        this.lastProgressUpdate = Instant.now();
    }

    public WarCapture(int warId, String chunkWorld, int chunkX, int chunkZ) {
        this.warId = warId;
        this.chunkWorld = chunkWorld;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.captureProgress = 0;
        this.lastProgressUpdate = Instant.now();
    }

    public WarCapture(
            int id,
            int warId,
            String chunkWorld,
            int chunkX,
            int chunkZ,
            Integer capturingNationId,
            int captureProgress,
            Instant lastProgressUpdate,
            Instant capturedAt) {
        this.id = id;
        this.warId = warId;
        this.chunkWorld = chunkWorld;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.capturingNationId = capturingNationId;
        this.captureProgress = captureProgress;
        this.lastProgressUpdate = lastProgressUpdate;
        this.capturedAt = capturedAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getWarId() {
        return warId;
    }

    public void setWarId(int warId) {
        this.warId = warId;
    }

    public String getChunkWorld() {
        return chunkWorld;
    }

    public void setChunkWorld(String chunkWorld) {
        this.chunkWorld = chunkWorld;
    }

    public int getChunkX() {
        return chunkX;
    }

    public void setChunkX(int chunkX) {
        this.chunkX = chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public void setChunkZ(int chunkZ) {
        this.chunkZ = chunkZ;
    }

    public Integer getCapturingNationId() {
        return capturingNationId;
    }

    public void setCapturingNationId(Integer capturingNationId) {
        this.capturingNationId = capturingNationId;
    }

    public int getCaptureProgress() {
        return captureProgress;
    }

    public void setCaptureProgress(int captureProgress) {
        this.captureProgress = Math.max(0, Math.min(MAX_PROGRESS, captureProgress));
    }

    public Instant getLastProgressUpdate() {
        return lastProgressUpdate;
    }

    public void setLastProgressUpdate(Instant lastProgressUpdate) {
        this.lastProgressUpdate = lastProgressUpdate;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(Instant capturedAt) {
        this.capturedAt = capturedAt;
    }

    public boolean isCaptured() {
        return capturedAt != null;
    }

    public boolean isBeingCaptured() {
        return capturingNationId != null && captureProgress > 0 && !isCaptured();
    }

    public double getProgressPercentage() {
        return (captureProgress / (double) MAX_PROGRESS) * 100.0;
    }

    public void addProgress(int amount, int nationId) {
        if (capturingNationId == null || capturingNationId != nationId) {
            // New nation starting capture or taking over
            capturingNationId = nationId;
            captureProgress = amount;
        } else {
            captureProgress = Math.min(MAX_PROGRESS, captureProgress + amount);
        }
        lastProgressUpdate = Instant.now();

        if (captureProgress >= MAX_PROGRESS) {
            capturedAt = Instant.now();
        }
    }

    public void contest() {
        // Reset progress when contested by defender
        captureProgress = 0;
        capturingNationId = null;
        lastProgressUpdate = Instant.now();
    }

    public void decay(int amount) {
        captureProgress = Math.max(0, captureProgress - amount);
        if (captureProgress == 0) {
            capturingNationId = null;
        }
        lastProgressUpdate = Instant.now();
    }

    public String getChunkKey() {
        return chunkWorld + ":" + chunkX + ":" + chunkZ;
    }
}
