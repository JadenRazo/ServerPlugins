package net.serverplugins.backpacks;

import net.serverplugins.backpacks.commands.BackpackCommand;
import net.serverplugins.backpacks.commands.GiveBackpackCommand;
import net.serverplugins.backpacks.commands.UpgradeBackpackCommand;
import net.serverplugins.backpacks.handlers.WorldGuardHandler;
import net.serverplugins.backpacks.listeners.AutoRefillListener;
import net.serverplugins.backpacks.listeners.BackpackListener;
import net.serverplugins.backpacks.managers.BackpackManager;
import net.serverplugins.backpacks.managers.RecipeManager;
import net.serverplugins.backpacks.tasks.AutoSaveTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class ServerBackpacks extends JavaPlugin {

    private static ServerBackpacks instance;
    private BackpacksConfig backpacksConfig;
    private BackpackManager backpackManager;
    private RecipeManager recipeManager;
    private WorldGuardHandler worldGuardHandler;
    private AutoSaveTask autoSaveTask;

    @Override
    public void onLoad() {
        // Register WorldGuard flags BEFORE WorldGuard enables
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            try {
                worldGuardHandler = new WorldGuardHandler();
                worldGuardHandler.registerFlag();
            } catch (Exception e) {
                getLogger().warning("Failed to register WorldGuard flag: " + e.getMessage());
            }
        }
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        backpacksConfig = new BackpacksConfig(this);
        backpackManager = new BackpackManager(this);

        registerCommands();
        registerListeners();
        startTasks();

        // Register crafting recipes
        recipeManager = new RecipeManager(this);
        recipeManager.registerRecipes();

        getLogger().info("ServerBackpacks enabled!");
        if (worldGuardHandler != null && worldGuardHandler.isInitialized()) {
            getLogger()
                    .info(
                            "WorldGuard integration enabled - use '"
                                    + worldGuardHandler.getFlagName()
                                    + "' flag to control backpack access in regions.");
        }
    }

    @Override
    public void onDisable() {
        // Unregister recipes
        if (recipeManager != null) {
            recipeManager.unregisterRecipes();
        }

        if (autoSaveTask != null) {
            autoSaveTask.stop();
        }
        if (backpackManager != null) {
            backpackManager.saveAllOpenBackpacks();
            // Clear cache on plugin disable
            backpackManager.clearCache();
        }
        instance = null;
        getLogger().info("ServerBackpacks disabled!");
    }

    private void startTasks() {
        // Start auto-save task (every 60 ticks = 3 seconds)
        autoSaveTask = new AutoSaveTask(this);
        autoSaveTask.start(getConfig().getLong("auto-save-interval", 60L));
    }

    private void registerCommands() {
        BackpackCommand backpackCommand = new BackpackCommand(this);
        getCommand("backpack").setExecutor(backpackCommand);
        getCommand("backpack").setTabCompleter(backpackCommand);

        GiveBackpackCommand giveCommand = new GiveBackpackCommand(this);
        getCommand("givebackpack").setExecutor(giveCommand);
        getCommand("givebackpack").setTabCompleter(giveCommand);

        UpgradeBackpackCommand upgradeCommand = new UpgradeBackpackCommand(this);
        getCommand("upgradebackpack").setExecutor(upgradeCommand);
        getCommand("upgradebackpack").setTabCompleter(upgradeCommand);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new BackpackListener(this), this);

        // Register auto-refill feature if enabled
        if (getConfig().getBoolean("auto-refill.enabled", true)) {
            getServer().getPluginManager().registerEvents(new AutoRefillListener(this), this);
            getLogger().info("Auto-refill feature enabled");
        }
    }

    public void reloadConfiguration() {
        reloadConfig();
        backpacksConfig = new BackpacksConfig(this);

        // Clear cache on reload to ensure fresh configuration is used
        if (backpackManager != null) {
            backpackManager.clearCache();
        }

        // Re-register recipes after config reload
        if (recipeManager != null) {
            recipeManager.unregisterRecipes();
            recipeManager.registerRecipes();
        }
    }

    public static ServerBackpacks getInstance() {
        return instance;
    }

    public BackpacksConfig getBackpacksConfig() {
        return backpacksConfig;
    }

    public BackpackManager getBackpackManager() {
        return backpackManager;
    }

    public WorldGuardHandler getWorldGuardHandler() {
        return worldGuardHandler;
    }
}
