package net.serverplugins.mobhealth;

import java.util.HashSet;
import java.util.Set;
import net.serverplugins.mobhealth.commands.MobHealthCommand;
import net.serverplugins.mobhealth.listeners.CleanupListener;
import net.serverplugins.mobhealth.listeners.DamageListener;
import org.bukkit.plugin.java.JavaPlugin;

public class ServerMobHealth extends JavaPlugin {

    private MobHealthConfig config;
    private MobHealthManager manager;
    private final Set<String> disabledPlayers = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        config = new MobHealthConfig(this);
        manager = new MobHealthManager(this);

        registerCommands();
        registerListeners();

        getLogger().info("ServerMobHealth enabled!");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.removeAll();
        }
        getLogger().info("ServerMobHealth disabled!");
    }

    private void registerCommands() {
        MobHealthCommand cmd = new MobHealthCommand(this);
        getCommand("mobhealth").setExecutor(cmd);
        getCommand("mobhealth").setTabCompleter(cmd);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new DamageListener(this), this);
        getServer().getPluginManager().registerEvents(new CleanupListener(this), this);
    }

    public void reloadConfiguration() {
        reloadConfig();
        config.reload();
    }

    /**
     * Toggles health bar display for a player by name. Returns true if now enabled, false if
     * disabled.
     */
    public boolean togglePlayer(String playerName) {
        if (disabledPlayers.contains(playerName)) {
            disabledPlayers.remove(playerName);
            return true;
        } else {
            disabledPlayers.add(playerName);
            return false;
        }
    }

    public boolean isDisabledFor(String playerName) {
        return disabledPlayers.contains(playerName);
    }

    public MobHealthConfig getMobHealthConfig() {
        return config;
    }

    public MobHealthManager getManager() {
        return manager;
    }
}
