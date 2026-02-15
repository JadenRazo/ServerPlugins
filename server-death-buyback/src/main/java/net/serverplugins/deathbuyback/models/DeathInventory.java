package net.serverplugins.deathbuyback.models;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

public class DeathInventory {

    private int id;
    private final UUID playerUuid;
    private final String playerName;
    private final String world;
    private final double deathX;
    private final double deathY;
    private final double deathZ;
    private final String deathCause;
    private final String inventoryData;
    private final String armorData;
    private final String offhandData;
    private final int xpLevels;
    private final double baseWorth;
    private final double buybackPrice;
    private final int itemCount;
    private final long diedAt;
    private final long expiresAt;
    private boolean purchased;
    private Long purchasedAt;

    // Transient fields for deserialized items
    private transient ItemStack[] inventory;
    private transient ItemStack[] armor;
    private transient ItemStack offhand;

    public DeathInventory(
            UUID playerUuid,
            String playerName,
            Location location,
            String deathCause,
            String inventoryData,
            String armorData,
            String offhandData,
            int xpLevels,
            double baseWorth,
            double buybackPrice,
            int itemCount,
            long diedAt,
            long expiresAt) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.world = location.getWorld().getName();
        this.deathX = location.getX();
        this.deathY = location.getY();
        this.deathZ = location.getZ();
        this.deathCause = deathCause;
        this.inventoryData = inventoryData;
        this.armorData = armorData;
        this.offhandData = offhandData;
        this.xpLevels = xpLevels;
        this.baseWorth = baseWorth;
        this.buybackPrice = buybackPrice;
        this.itemCount = itemCount;
        this.diedAt = diedAt;
        this.expiresAt = expiresAt;
        this.purchased = false;
    }

    // Constructor for loading from database
    public DeathInventory(
            int id,
            UUID playerUuid,
            String playerName,
            String world,
            double deathX,
            double deathY,
            double deathZ,
            String deathCause,
            String inventoryData,
            String armorData,
            String offhandData,
            int xpLevels,
            double baseWorth,
            double buybackPrice,
            int itemCount,
            long diedAt,
            long expiresAt,
            boolean purchased,
            Long purchasedAt) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.world = world;
        this.deathX = deathX;
        this.deathY = deathY;
        this.deathZ = deathZ;
        this.deathCause = deathCause;
        this.inventoryData = inventoryData;
        this.armorData = armorData;
        this.offhandData = offhandData;
        this.xpLevels = xpLevels;
        this.baseWorth = baseWorth;
        this.buybackPrice = buybackPrice;
        this.itemCount = itemCount;
        this.diedAt = diedAt;
        this.expiresAt = expiresAt;
        this.purchased = purchased;
        this.purchasedAt = purchasedAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getWorld() {
        return world;
    }

    public double getDeathX() {
        return deathX;
    }

    public double getDeathY() {
        return deathY;
    }

    public double getDeathZ() {
        return deathZ;
    }

    public String getDeathCause() {
        return deathCause;
    }

    public String getInventoryData() {
        return inventoryData;
    }

    public String getArmorData() {
        return armorData;
    }

    public String getOffhandData() {
        return offhandData;
    }

    public int getXpLevels() {
        return xpLevels;
    }

    public double getBaseWorth() {
        return baseWorth;
    }

    public double getBuybackPrice() {
        return buybackPrice;
    }

    public int getItemCount() {
        return itemCount;
    }

    public long getDiedAt() {
        return diedAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public boolean isPurchased() {
        return purchased;
    }

    public void setPurchased(boolean purchased) {
        this.purchased = purchased;
    }

    public Long getPurchasedAt() {
        return purchasedAt;
    }

    public void setPurchasedAt(Long purchasedAt) {
        this.purchasedAt = purchasedAt;
    }

    public ItemStack[] getInventory() {
        return inventory;
    }

    public void setInventory(ItemStack[] inventory) {
        this.inventory = inventory;
    }

    public ItemStack[] getArmor() {
        return armor;
    }

    public void setArmor(ItemStack[] armor) {
        this.armor = armor;
    }

    public ItemStack getOffhand() {
        return offhand;
    }

    public void setOffhand(ItemStack offhand) {
        this.offhand = offhand;
    }

    public String getFormattedLocation() {
        return String.format("%s (%.0f, %.0f, %.0f)", world, deathX, deathY, deathZ);
    }

    public String getTimeAgo() {
        long elapsed = System.currentTimeMillis() - diedAt;
        long seconds = elapsed / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) return days + "d ago";
        if (hours > 0) return hours + "h ago";
        if (minutes > 0) return minutes + "m ago";
        return seconds + "s ago";
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }

    public String getTimeUntilExpiry() {
        long remaining = expiresAt - System.currentTimeMillis();
        if (remaining <= 0) return "Expired";

        long seconds = remaining / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) return days + "d " + (hours % 24) + "h";
        if (hours > 0) return hours + "h " + (minutes % 60) + "m";
        if (minutes > 0) return minutes + "m";
        return seconds + "s";
    }
}
