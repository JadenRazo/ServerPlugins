package net.serverplugins.commands.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.serverplugins.api.messages.Placeholder;
import net.serverplugins.commands.ServerCommands;
import net.serverplugins.commands.data.MuteManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ChatListener implements Listener {

    private final ServerCommands plugin;

    public ChatListener(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        if (plugin.getMuteManager().isMuted(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            MuteManager.MuteData data =
                    plugin.getMuteManager().getMuteData(event.getPlayer().getUniqueId());
            if (data != null) {
                String remaining = data.getRemainingTime();
                plugin.getCommandsConfig()
                        .getMessenger()
                        .send(
                                event.getPlayer(),
                                "muted-chat",
                                Placeholder.of("remaining", remaining));
                plugin.getCommandsConfig()
                        .getMessenger()
                        .send(
                                event.getPlayer(),
                                "muted-chat-reason",
                                Placeholder.of("reason", data.getReason()));
            }
        }
    }
}
