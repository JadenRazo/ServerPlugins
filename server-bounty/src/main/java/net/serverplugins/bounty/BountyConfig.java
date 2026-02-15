package net.serverplugins.bounty;

import net.serverplugins.api.messages.PluginMessenger;

public class BountyConfig {

    private final ServerBounty plugin;
    private PluginMessenger messenger;

    private double minBountyAmount;
    private double maxBountyAmount;
    private double taxPercentage;
    private boolean broadcastOnKill;
    private boolean broadcastOnPlace;
    private int headExpiryDays;
    private boolean allowSelfBounty;
    private int cooldownSeconds;

    public BountyConfig(ServerBounty plugin) {
        this.plugin = plugin;
        reload();
    }

    public final void reload() {
        minBountyAmount = plugin.getConfig().getDouble("min-bounty-amount", 100.0);
        maxBountyAmount = plugin.getConfig().getDouble("max-bounty-amount", 0);
        taxPercentage = plugin.getConfig().getDouble("tax-percentage", 5.0);
        broadcastOnKill = plugin.getConfig().getBoolean("broadcast-on-kill", true);
        broadcastOnPlace = plugin.getConfig().getBoolean("broadcast-on-place", true);
        headExpiryDays = plugin.getConfig().getInt("head-expiry-days", 30);
        allowSelfBounty = plugin.getConfig().getBoolean("allow-self-bounty", false);
        cooldownSeconds = plugin.getConfig().getInt("cooldown-seconds", 60);

        messenger = new PluginMessenger(plugin.getConfig(), "messages", "<red>[Bounty]</red> ");
    }

    public double getMinBountyAmount() {
        return minBountyAmount;
    }

    public double getMaxBountyAmount() {
        return maxBountyAmount;
    }

    public double getTaxPercentage() {
        return taxPercentage;
    }

    public boolean isBroadcastOnKill() {
        return broadcastOnKill;
    }

    public boolean isBroadcastOnPlace() {
        return broadcastOnPlace;
    }

    public int getHeadExpiryDays() {
        return headExpiryDays;
    }

    public boolean isAllowSelfBounty() {
        return allowSelfBounty;
    }

    public boolean allowSelfBounty() {
        return allowSelfBounty;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public boolean broadcastOnPlace() {
        return broadcastOnPlace;
    }

    public boolean broadcastOnKill() {
        return broadcastOnKill;
    }

    public PluginMessenger getMessenger() {
        return messenger;
    }
}
