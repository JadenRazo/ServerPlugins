package net.serverplugins.arcade.utils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.serverplugins.arcade.ServerArcade;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Manages chat input for custom bet amounts and other inputs. */
public class ChatInputManager implements Listener {

    private final ServerArcade plugin;
    private final Map<UUID, Consumer<String>> waitingPlayers = new ConcurrentHashMap<>();

    public ChatInputManager(ServerArcade plugin) {
        this.plugin = plugin;
    }

    /** Wait for a player's next chat message. */
    public void waitForInput(Player player, Consumer<String> callback) {
        waitingPlayers.put(player.getUniqueId(), callback);
    }

    /** Cancel waiting for a player's input. */
    public void cancelInput(Player player) {
        waitingPlayers.remove(player.getUniqueId());
    }

    /** Check if a player is currently providing input. */
    public boolean isWaitingForInput(Player player) {
        return waitingPlayers.containsKey(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Consumer<String> callback = waitingPlayers.remove(uuid);

        if (callback != null) {
            event.setCancelled(true);
            String message = event.getMessage();

            // Run callback on main thread
            plugin.getServer()
                    .getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                callback.accept(message);
                            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        waitingPlayers.remove(event.getPlayer().getUniqueId());
    }
}
