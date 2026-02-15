package net.serverplugins.afk.gui;

import java.util.UUID;
import java.util.function.Consumer;
import net.serverplugins.afk.ServerAFK;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ChatInputHandler implements Listener {

    private final ServerAFK plugin;
    private final UUID playerId;
    private final Consumer<String> callback;

    public ChatInputHandler(ServerAFK plugin, Player player, Consumer<String> callback) {
        this.plugin = plugin;
        this.playerId = player.getUniqueId();
        this.callback = callback;

        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Auto-unregister after 60 seconds
        plugin.getServer()
                .getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            HandlerList.unregisterAll(this);
                        },
                        20L * 60);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!event.getPlayer().getUniqueId().equals(playerId)) {
            return;
        }

        event.setCancelled(true);
        String message = event.getMessage().trim();

        // Run callback on main thread
        plugin.getServer()
                .getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            callback.accept(message);
                        });

        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer().getUniqueId().equals(playerId)) {
            HandlerList.unregisterAll(this);
        }
    }
}
