package net.serverplugins.npcs;

import net.serverplugins.npcs.commands.DialogCommand;
import net.serverplugins.npcs.commands.NpcCommand;
import net.serverplugins.npcs.listeners.NpcInteractionListener;
import net.serverplugins.npcs.managers.DialogManager;
import net.serverplugins.npcs.managers.NpcManager;
import org.bukkit.plugin.java.JavaPlugin;

public class ServerNpcs extends JavaPlugin {

    private static ServerNpcs instance;
    private NpcsConfig config;
    private DialogManager dialogManager;
    private NpcManager npcManager;

    public static ServerNpcs getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        // Load configuration
        saveDefaultConfig();
        config = new NpcsConfig(this);

        // Initialize managers
        npcManager = new NpcManager(this);
        dialogManager = new DialogManager(this);

        // Register commands
        registerCommands();

        // Register listeners
        registerListeners();

        getLogger().info("ServerNpcs enabled!");
        getLogger().info("Loaded " + dialogManager.getDialogCount() + " dialogs");
        getLogger().info("Loaded " + npcManager.getNpcCount() + " NPCs");
    }

    @Override
    public void onDisable() {
        // Save any pending data
        if (npcManager != null) {
            npcManager.saveAll();
        }

        getLogger().info("ServerNpcs disabled!");
    }

    private void registerCommands() {
        NpcCommand npcCommand = new NpcCommand(this);
        getCommand("npc").setExecutor(npcCommand);
        getCommand("npc").setTabCompleter(npcCommand);

        DialogCommand dialogCommand = new DialogCommand(this);
        getCommand("dialog").setExecutor(dialogCommand);
        getCommand("dialog").setTabCompleter(dialogCommand);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new NpcInteractionListener(this), this);
    }

    public void reloadConfiguration() {
        // Reload config first
        reloadConfig();

        // Update config wrapper (this will reload the messenger)
        config.reload();

        // Reload managers
        dialogManager.reload();
        npcManager.reload();

        getLogger().info("Configuration reloaded!");
        getLogger().info("Loaded " + dialogManager.getDialogCount() + " dialogs");
        getLogger().info("Loaded " + npcManager.getNpcCount() + " NPCs");
    }

    public NpcsConfig getNpcsConfig() {
        return config;
    }

    public DialogManager getDialogManager() {
        return dialogManager;
    }

    public NpcManager getNpcManager() {
        return npcManager;
    }
}
