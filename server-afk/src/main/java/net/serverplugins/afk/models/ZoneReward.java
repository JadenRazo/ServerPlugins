package net.serverplugins.afk.models;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class ZoneReward {

    public enum RewardType {
        CURRENCY,
        ITEM,
        COMMAND,
        XP,
        GEMS
    }

    private static final Gson GSON = new Gson();

    private int id;
    private int zoneId;
    private RewardType type;
    private double currencyAmount;
    private ItemStack itemReward;
    private String commandData;
    private int xpAmount;
    private int gemsAmount;
    private double chancePercent;

    public ZoneReward() {
        this.type = RewardType.CURRENCY;
        this.currencyAmount = 100;
        this.chancePercent = 100.0;
        this.xpAmount = 0;
        this.gemsAmount = 0;
        this.commandData = null;
    }

    public ZoneReward(RewardType type) {
        this();
        this.type = type;
    }

    public static ZoneReward currency(double amount) {
        ZoneReward reward = new ZoneReward(RewardType.CURRENCY);
        reward.setCurrencyAmount(amount);
        return reward;
    }

    public static ZoneReward item(ItemStack item) {
        ZoneReward reward = new ZoneReward(RewardType.ITEM);
        reward.setItemReward(item);
        return reward;
    }

    public static ZoneReward command(String command) {
        ZoneReward reward = new ZoneReward(RewardType.COMMAND);
        reward.setCommandData(command);
        return reward;
    }

    public static ZoneReward xp(int amount) {
        ZoneReward reward = new ZoneReward(RewardType.XP);
        reward.setXpAmount(amount);
        return reward;
    }

    public static ZoneReward gems(int amount) {
        ZoneReward reward = new ZoneReward(RewardType.GEMS);
        reward.setGemsAmount(amount);
        return reward;
    }

    public String serializeItem() {
        if (itemReward == null) return null;

        JsonObject json = new JsonObject();
        json.addProperty("material", itemReward.getType().name());
        json.addProperty("amount", itemReward.getAmount());

        if (itemReward.hasItemMeta() && itemReward.getItemMeta().hasDisplayName()) {
            json.addProperty("displayName", itemReward.getItemMeta().getDisplayName());
        }

        return GSON.toJson(json);
    }

    public void deserializeItem(String data) {
        if (data == null || data.isEmpty()) {
            this.itemReward = null;
            return;
        }

        try {
            JsonObject json = GSON.fromJson(data, JsonObject.class);
            Material material = Material.valueOf(json.get("material").getAsString());
            int amount = json.has("amount") ? json.get("amount").getAsInt() : 1;

            this.itemReward = new ItemStack(material, amount);
        } catch (Exception e) {
            this.itemReward = null;
        }
    }

    public String getDisplayName() {
        String base;
        switch (type) {
            case CURRENCY:
                base = (int) currencyAmount + " coins";
                break;
            case ITEM:
                if (itemReward != null) {
                    String name = itemReward.getType().name().toLowerCase().replace("_", " ");
                    base = itemReward.getAmount() + "x " + name;
                } else {
                    base = "Unknown Item";
                }
                break;
            case COMMAND:
                base = commandData != null ? "Command: " + commandData : "Unknown Command";
                break;
            case XP:
                base = xpAmount + " XP";
                break;
            case GEMS:
                base = gemsAmount + (gemsAmount == 1 ? " Gem" : " Gems");
                break;
            default:
                base = "Unknown Reward";
        }

        if (chancePercent < 100.0) {
            return base + " (" + String.format("%.1f", chancePercent) + "% chance)";
        }
        return base;
    }

    public boolean shouldGive() {
        if (chancePercent >= 100.0) {
            return true;
        }
        return Math.random() * 100 < chancePercent;
    }

    public String processCommand(String playerName, String playerUuid) {
        if (commandData == null) return null;
        return commandData.replace("%player%", playerName).replace("%uuid%", playerUuid);
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getZoneId() {
        return zoneId;
    }

    public void setZoneId(int zoneId) {
        this.zoneId = zoneId;
    }

    public RewardType getType() {
        return type;
    }

    public void setType(RewardType type) {
        this.type = type;
    }

    public double getCurrencyAmount() {
        return currencyAmount;
    }

    public void setCurrencyAmount(double currencyAmount) {
        this.currencyAmount = currencyAmount;
    }

    public ItemStack getItemReward() {
        return itemReward;
    }

    public void setItemReward(ItemStack itemReward) {
        this.itemReward = itemReward;
    }

    public String getCommandData() {
        return commandData;
    }

    public void setCommandData(String commandData) {
        this.commandData = commandData;
    }

    public int getXpAmount() {
        return xpAmount;
    }

    public void setXpAmount(int xpAmount) {
        this.xpAmount = xpAmount;
    }

    public int getGemsAmount() {
        return gemsAmount;
    }

    public void setGemsAmount(int gemsAmount) {
        this.gemsAmount = gemsAmount;
    }

    public double getChancePercent() {
        return chancePercent;
    }

    public void setChancePercent(double chancePercent) {
        this.chancePercent = chancePercent;
    }
}
