package net.serverplugins.claim.models;

import java.time.Instant;
import java.util.UUID;

public class Nation {

    private int id;
    private String name;
    private String tag;
    private UUID leaderUuid;
    private String description;
    private ProfileColor color;
    private Instant foundedAt;
    private int totalChunks;
    private int memberCount;
    private int level;
    private double taxRate;

    public Nation() {
        this.color = ProfileColor.WHITE;
        this.foundedAt = Instant.now();
        this.totalChunks = 0;
        this.memberCount = 0;
        this.level = 1;
        this.taxRate = 0.0;
    }

    public Nation(String name, String tag, UUID leaderUuid) {
        this.name = name;
        this.tag = tag.toUpperCase();
        this.leaderUuid = leaderUuid;
        this.color = ProfileColor.WHITE;
        this.foundedAt = Instant.now();
        this.totalChunks = 0;
        this.memberCount = 1;
        this.level = 1;
        this.taxRate = 0.0;
    }

    public Nation(
            int id,
            String name,
            String tag,
            UUID leaderUuid,
            String description,
            ProfileColor color,
            Instant foundedAt,
            int totalChunks,
            int memberCount,
            int level) {
        this(
                id,
                name,
                tag,
                leaderUuid,
                description,
                color,
                foundedAt,
                totalChunks,
                memberCount,
                level,
                0.0);
    }

    public Nation(
            int id,
            String name,
            String tag,
            UUID leaderUuid,
            String description,
            ProfileColor color,
            Instant foundedAt,
            int totalChunks,
            int memberCount,
            int level,
            double taxRate) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.leaderUuid = leaderUuid;
        this.description = description;
        this.color = color;
        this.foundedAt = foundedAt;
        this.totalChunks = totalChunks;
        this.memberCount = memberCount;
        this.level = level;
        this.taxRate = taxRate;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag != null ? tag.toUpperCase() : null;
    }

    public UUID getLeaderUuid() {
        return leaderUuid;
    }

    public void setLeaderUuid(UUID leaderUuid) {
        this.leaderUuid = leaderUuid;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ProfileColor getColor() {
        return color;
    }

    public void setColor(ProfileColor color) {
        this.color = color;
    }

    public Instant getFoundedAt() {
        return foundedAt;
    }

    public void setFoundedAt(Instant foundedAt) {
        this.foundedAt = foundedAt;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }

    public int getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(int memberCount) {
        this.memberCount = memberCount;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public double getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(double taxRate) {
        this.taxRate = taxRate;
    }

    public boolean isLeader(UUID uuid) {
        return leaderUuid != null && leaderUuid.equals(uuid);
    }

    public String getFormattedTag() {
        return "[" + tag + "]";
    }

    public String getColoredTag() {
        return color.getColorTag() + getFormattedTag();
    }

    public void incrementMemberCount() {
        this.memberCount++;
    }

    public void decrementMemberCount() {
        this.memberCount = Math.max(0, this.memberCount - 1);
    }

    public void addChunks(int count) {
        this.totalChunks += count;
    }

    public void removeChunks(int count) {
        this.totalChunks = Math.max(0, this.totalChunks - count);
    }
}
