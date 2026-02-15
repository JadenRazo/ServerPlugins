/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.HandlerList
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.AsyncPlayerPreLoginEvent
 *  org.bukkit.event.player.PlayerQuitEvent
 *  org.bukkit.event.server.PluginEnableEvent
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 */
package net.serverplugins.core;

import java.util.HashMap;
import java.util.Map;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.core.commands.BattlepassCommand;
import net.serverplugins.core.commands.GiveCommand;
import net.serverplugins.core.commands.GiveSpawnerCommand;
import net.serverplugins.core.commands.HammerPickaxeCommand;
import net.serverplugins.core.commands.HatCommand;
import net.serverplugins.core.commands.JavaBedrockCommand;
import net.serverplugins.core.commands.RestartCommand;
import net.serverplugins.core.commands.ToggleCommand;
import net.serverplugins.core.config.CoreConfig;
import net.serverplugins.core.data.PlayerDataManager;
import net.serverplugins.core.features.AnvilColorsFeature;
import net.serverplugins.core.features.AutoCompleteFeature;
import net.serverplugins.core.features.AutoTotemFeature;
import net.serverplugins.core.features.CustomInventoryFeature;
import net.serverplugins.core.features.DoubleDoorFeature;
import net.serverplugins.core.features.DropToInventoryFeature;
import net.serverplugins.core.features.EditableSignsFeature;
import net.serverplugins.core.features.EmojisFeature;
import net.serverplugins.core.features.Feature;
import net.serverplugins.core.features.HammerPickaxeFeature;
import net.serverplugins.core.features.HatFeature;
import net.serverplugins.core.features.PlatformCommandFeature;
import net.serverplugins.core.features.PluginListFeature;
import net.serverplugins.core.features.ResourcePackFeature;
import net.serverplugins.core.features.SilkSpawnerFeature;
import net.serverplugins.core.features.SpawnerEnhancementFeature;
import net.serverplugins.core.features.WorldBorderFeature;
import net.serverplugins.core.features.WorldGuardFlagsFeature;
import net.serverplugins.core.listeners.CoreListener;
import net.serverplugins.core.listeners.JoinListener;
import net.serverplugins.core.placeholders.CorePlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class ServerCore extends JavaPlugin implements Listener {
    private CoreConfig config;
    private PlayerDataManager playerDataManager;
    private final Map<String, Feature> features = new HashMap<String, Feature>();
    private CoreListener listener;

    public void onLoad() {
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            try {
                WorldGuardFlagsFeature.registerFlags();
            } catch (Exception e) {
                this.getLogger().warning("Failed to register WorldGuard flags: " + e.getMessage());
            }
        }
    }

    public void onEnable() {
        this.saveDefaultConfig();
        this.config = new CoreConfig(this);
        this.playerDataManager = new PlayerDataManager(this);
        this.registerFeatures();
        this.listener = new CoreListener(this, this.features);
        this.getServer().getPluginManager().registerEvents((Listener) this.listener, (Plugin) this);
        this.getServer()
                .getPluginManager()
                .registerEvents(
                        (Listener) new JoinListener(this, this.playerDataManager), (Plugin) this);
        this.getServer().getPluginManager().registerEvents((Listener) this, (Plugin) this);
        this.registerCommands();
        this.registerPlaceholders();
        this.startEarlyAccessReminder();
        this.getLogger().info("ServerCore enabled!");
        this.logFeatureStatus();
    }

    private void registerPlaceholders() {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new CorePlaceholderExpansion(this).register();
            this.getLogger().info("PlaceholderAPI expansion registered");
            return;
        }
        Bukkit.getPluginManager()
                .registerEvents(
                        new Listener() {

                            @EventHandler
                            public void onPluginEnable(PluginEnableEvent event) {
                                if (event.getPlugin().getName().equals("PlaceholderAPI")) {
                                    new CorePlaceholderExpansion(ServerCore.this).register();
                                    ServerCore.this
                                            .getLogger()
                                            .info("PlaceholderAPI expansion registered (delayed)");
                                    HandlerList.unregisterAll((Listener) this);
                                }
                            }
                        },
                        (Plugin) this);
    }

    public void onDisable() {
        if (this.playerDataManager != null) {
            this.playerDataManager.saveAll();
        }
        this.features.values().forEach(Feature::disable);
        this.features.clear();
        this.getLogger().info("ServerCore disabled!");
    }

    @EventHandler
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        if (this.playerDataManager != null) {
            this.playerDataManager.preloadPlayerDataAsync(event.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (this.playerDataManager != null) {
            this.playerDataManager.unloadPlayerData(player.getUniqueId());
        }
    }

    private void registerFeatures() {
        this.registerFeature("auto-totem", new AutoTotemFeature(this));
        this.registerFeature("double-door", new DoubleDoorFeature(this));
        this.registerFeature("drop-to-inventory", new DropToInventoryFeature(this));
        this.registerFeature("editable-signs", new EditableSignsFeature(this));
        this.registerFeature("hat", new HatFeature(this));
        this.registerFeature("anvil-colors", new AnvilColorsFeature(this));
        this.registerFeature("emojis", new EmojisFeature(this));
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            this.registerFeature("worldguard-flags", new WorldGuardFlagsFeature(this));
        }
        this.registerFeature("plugin-list", new PluginListFeature(this));
        this.registerFeature("resource-pack", new ResourcePackFeature(this));
        this.registerFeature("platform-command", new PlatformCommandFeature(this));
        this.registerFeature("custom-inventory", new CustomInventoryFeature(this));
        this.registerFeature("auto-complete", new AutoCompleteFeature(this));
        this.registerFeature("spawner-enhancement", new SpawnerEnhancementFeature(this));
        this.registerFeature("silk-spawner", new SilkSpawnerFeature(this));
        this.registerFeature("world-border", new WorldBorderFeature(this));
        this.registerFeature("hammer-pickaxe", new HammerPickaxeFeature(this));
        this.features.forEach(
                (name, feature) -> {
                    if (this.config.isFeatureEnabled((String) name)) {
                        feature.enable();
                    }
                });
    }

    private void registerFeature(String name, Feature feature) {
        this.features.put(name, feature);
    }

    private void registerCommands() {
        HatCommand hatCommand = new HatCommand(this);
        this.getCommand("hat").setExecutor((CommandExecutor) hatCommand);
        ToggleCommand toggleCommand = new ToggleCommand(this, this.features);
        this.getCommand("servercore").setExecutor((CommandExecutor) toggleCommand);
        this.getCommand("servercore").setTabCompleter((TabCompleter) toggleCommand);
        GiveCommand giveCommand = new GiveCommand(this);
        this.getCommand("dgive").setExecutor((CommandExecutor) giveCommand);
        this.getCommand("dgive").setTabCompleter((TabCompleter) giveCommand);
        JavaBedrockCommand javaBedrockCommand = new JavaBedrockCommand(this);
        this.getCommand("javabedrock").setExecutor((CommandExecutor) javaBedrockCommand);
        this.getCommand("javabedrock").setTabCompleter((TabCompleter) javaBedrockCommand);
        BattlepassCommand battlepassCommand = new BattlepassCommand(this);
        this.getCommand("battlepass").setExecutor((CommandExecutor) battlepassCommand);
        this.getCommand("battlepass").setTabCompleter((TabCompleter) battlepassCommand);
        RestartCommand restartCommand = new RestartCommand(this);
        this.getCommand("restart").setExecutor((CommandExecutor) restartCommand);
        GiveSpawnerCommand giveSpawnerCommand = new GiveSpawnerCommand(this);
        this.getCommand("givespawner").setExecutor((CommandExecutor) giveSpawnerCommand);
        this.getCommand("givespawner").setTabCompleter((TabCompleter) giveSpawnerCommand);
        HammerPickaxeCommand hammerPickaxeCommand = new HammerPickaxeCommand(this);
        this.getCommand("hammerpick").setExecutor((CommandExecutor) hammerPickaxeCommand);
        this.getCommand("hammerpick").setTabCompleter((TabCompleter) hammerPickaxeCommand);
    }

    private void startEarlyAccessReminder() {
        String message =
                "<gradient:#FFD700:#FFA500>Early Access</gradient> <gray>- Bugs may occur! Create a ticket in Discord to report issues.";
        // 12000 ticks = 10 minutes
        this.getServer()
                .getScheduler()
                .runTaskTimer(
                        this,
                        () -> {
                            for (Player player : Bukkit.getOnlinePlayers()) {
                                TextUtil.sendActionBar(player, message);
                            }
                        },
                        12000L,
                        12000L);
    }

    private void logFeatureStatus() {
        this.getLogger().info("Feature Status:");
        this.features.forEach(
                (name, feature) -> {
                    String status = feature.isEnabled() ? "ENABLED" : "DISABLED";
                    this.getLogger().info("  - " + name + ": " + status);
                });
    }

    public CoreConfig getCoreConfig() {
        return this.config;
    }

    public Map<String, Feature> getFeatures() {
        return this.features;
    }

    public PlayerDataManager getPlayerDataManager() {
        return this.playerDataManager;
    }

    public void reloadConfiguration() {
        this.reloadConfig();
        this.config = new CoreConfig(this);
        this.features.forEach(
                (name, feature) -> {
                    boolean shouldBeEnabled = this.config.isFeatureEnabled((String) name);
                    if (shouldBeEnabled && !feature.isEnabled()) {
                        feature.enable();
                    } else if (!shouldBeEnabled && feature.isEnabled()) {
                        feature.disable();
                    }
                });
        this.getLogger().info("Configuration reloaded!");
    }
}
