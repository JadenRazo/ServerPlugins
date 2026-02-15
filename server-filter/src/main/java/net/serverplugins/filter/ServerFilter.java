package net.serverplugins.filter;

import net.serverplugins.filter.chat.ChatFilterListener;
import net.serverplugins.filter.chat.ChatProtectionListener;
import net.serverplugins.filter.chat.ViolationHandler;
import net.serverplugins.filter.commands.FilterAdminCommand;
import net.serverplugins.filter.commands.SwearFilterCommand;
import net.serverplugins.filter.data.FilterPreferenceManager;
import net.serverplugins.filter.filter.MessageFilterService;
import net.serverplugins.filter.placeholders.FilterPlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class ServerFilter extends JavaPlugin implements Listener {

    private FilterConfig filterConfig;
    private MessageFilterService filterService;
    private FilterPreferenceManager preferenceManager;
    private ViolationHandler violationHandler;
    private ChatProtectionListener protectionListener;

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();

        // Initialize config wrapper
        this.filterConfig = new FilterConfig(this);

        // Save default word lists
        saveResource("wordlists/slurs.yml", false);
        saveResource("wordlists/extreme.yml", false);
        saveResource("wordlists/moderate.yml", false);
        saveResource("wordlists/mild.yml", false);

        // Initialize components
        this.filterService = new MessageFilterService(this);
        filterService.initialize();

        this.preferenceManager = new FilterPreferenceManager(this);
        this.violationHandler = new ViolationHandler(this, filterConfig);

        // Register listeners
        ChatFilterListener chatFilterListener =
                new ChatFilterListener(this, filterService, preferenceManager, violationHandler);
        this.protectionListener = new ChatProtectionListener(this, violationHandler);

        getServer().getPluginManager().registerEvents(chatFilterListener, this);
        getServer().getPluginManager().registerEvents(protectionListener, this);
        // GUI listener is handled by ServerAPI's GuiListener (Gui base class)
        getServer().getPluginManager().registerEvents(this, this);

        // Register commands
        var swearFilterCommand = new SwearFilterCommand(this, preferenceManager, filterConfig);
        var adminCommand = new FilterAdminCommand(this, filterService, filterConfig);

        getCommand("swearfilter").setExecutor(swearFilterCommand);
        getCommand("swearfilter").setTabCompleter(swearFilterCommand);
        getCommand("filteradmin").setExecutor(adminCommand);
        getCommand("filteradmin").setTabCompleter(adminCommand);

        // Register PlaceholderAPI expansion if available
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new FilterPlaceholderExpansion(this, preferenceManager).register();
            getLogger().info("PlaceholderAPI expansion registered.");
        }

        // Load data for online players (in case of reload)
        for (Player player : Bukkit.getOnlinePlayers()) {
            preferenceManager.loadPlayerData(player.getUniqueId(), player.getName());
        }

        getLogger()
                .info(
                        "ServerFilter enabled! Loaded "
                                + filterService.getWordListManager().getTotalWordCount()
                                + " words and "
                                + filterService.getWordListManager().getTotalPatternCount()
                                + " patterns.");
    }

    @Override
    public void onDisable() {
        // Flush any pending violation logs
        if (violationHandler != null) {
            violationHandler.shutdown();
        }

        // Save all player preferences
        if (preferenceManager != null) {
            preferenceManager.saveAll();
        }

        getLogger().info("ServerFilter disabled.");
    }

    public void reload() {
        filterConfig.reload();
        filterService.reload();
        getLogger().info("ServerFilter reloaded.");
    }

    public FilterConfig getFilterConfig() {
        return filterConfig;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        preferenceManager.loadPlayerData(
                event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        preferenceManager.unloadPlayerData(event.getPlayer().getUniqueId());
        if (protectionListener != null) {
            protectionListener.clearPlayerData(event.getPlayer().getUniqueId());
        }
    }

    public MessageFilterService getFilterService() {
        return filterService;
    }

    public FilterPreferenceManager getPreferenceManager() {
        return preferenceManager;
    }

    public ViolationHandler getViolationHandler() {
        return violationHandler;
    }
}
