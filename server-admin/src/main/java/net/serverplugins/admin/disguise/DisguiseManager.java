package net.serverplugins.admin.disguise;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

public class DisguiseManager implements Listener {

    private final ServerAdmin plugin;
    private final Map<UUID, DisguiseSession> activeSessions;

    private static final String DEFAULT_CHAT_FORMAT =
            "%vault_prefix%{nickname}%vault_suffix%<gray>: <white>{message}";

    public DisguiseManager(ServerAdmin plugin) {
        this.plugin = plugin;
        this.activeSessions = new ConcurrentHashMap<>();

        // Register as listener for chat events
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public boolean disguise(Player player, Player targetPlayer) {
        // If already disguised, remove old disguise first
        if (isDisguised(player)) {
            undisguise(player);
        }

        // Create session with target player reference
        DisguiseSession session =
                new DisguiseSession(
                        player.getUniqueId(),
                        PlainTextComponentSerializer.plainText().serialize(player.displayName()),
                        player.getPlayerListName(),
                        targetPlayer.getName(),
                        targetPlayer.getUniqueId());

        activeSessions.put(player.getUniqueId(), session);

        // Start action bar task
        startActionBarTask(player, session);

        // Log for audit
        plugin.getLogger()
                .info("[DISGUISE] " + player.getName() + " disguised as " + targetPlayer.getName());

        return true;
    }

    public boolean undisguise(Player player) {
        DisguiseSession session = activeSessions.remove(player.getUniqueId());
        if (session == null) {
            return false;
        }

        // Cancel action bar task
        cancelActionBarTask(session);

        // Clear action bar
        player.sendActionBar(Component.empty());

        // Log for audit
        plugin.getLogger()
                .info(
                        "[DISGUISE] "
                                + player.getName()
                                + " removed disguise (was "
                                + session.getDisguisedAsName()
                                + ")");

        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        DisguiseSession session = activeSessions.get(player.getUniqueId());

        if (session == null) {
            return;
        }

        // Cancel the original message
        event.setCancelled(true);

        // Get the target player to impersonate
        Player targetPlayer = Bukkit.getPlayer(session.getDisguisedAsUuid());
        if (targetPlayer == null) {
            // Target went offline, use stored name
            TextUtil.sendError(player, "Disguise target is offline. Message not sent.");
            return;
        }

        // Get the message content
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        // Get chat format from config
        String format = plugin.getConfig().getString("disguise.chat-format", DEFAULT_CHAT_FORMAT);

        // Get nickname (fallback to player name)
        String nickname = targetPlayer.getName();
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            String nickMeta = getPlaceholder(targetPlayer, "%luckperms_meta_nickname%");
            if (nickMeta != null
                    && !nickMeta.isEmpty()
                    && !nickMeta.equals("%luckperms_meta_nickname%")
                    && !nickMeta.equalsIgnoreCase("null")) {
                nickname = nickMeta;
            }
        }

        // Replace placeholders
        format = format.replace("{nickname}", nickname);
        format = format.replace("{displayname}", targetPlayer.getName());
        format = format.replace("{name}", targetPlayer.getName());
        format = format.replace("{message}", escapeForMiniMessage(message));

        // Apply PlaceholderAPI if available
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            format = getPlaceholder(targetPlayer, format);
        }

        // Parse with TextUtil (handles legacy-to-MiniMessage conversion internally)
        final String finalFormat = format;
        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            Component chatMessage = TextUtil.parse(finalFormat);

                            // Send to all players
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                p.sendMessage(chatMessage);
                            }

                            // Send to console
                            Bukkit.getConsoleSender().sendMessage(chatMessage);
                        });
    }

    private String getPlaceholder(Player player, String text) {
        try {
            Class<?> papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Method setPlaceholders =
                    papiClass.getMethod(
                            "setPlaceholders", org.bukkit.OfflinePlayer.class, String.class);
            return (String) setPlaceholders.invoke(null, player, text);
        } catch (Exception e) {
            return text;
        }
    }

    private String escapeForMiniMessage(String text) {
        return text.replace("<", "\\<").replace(">", "\\>");
    }

    public boolean isDisguised(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }

    public DisguiseSession getSession(Player player) {
        return activeSessions.get(player.getUniqueId());
    }

    public String getDisguisedName(Player player) {
        DisguiseSession session = activeSessions.get(player.getUniqueId());
        return session != null ? session.getDisguisedAsName() : null;
    }

    private void startActionBarTask(Player player, DisguiseSession session) {
        BukkitTask task =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                plugin,
                                () -> {
                                    if (!player.isOnline() || !isDisguised(player)) {
                                        cancelActionBarTask(session);
                                        return;
                                    }
                                    sendActionBar(player, session);
                                },
                                0L,
                                40L);

        session.setActionBarTaskId(task.getTaskId());
    }

    private void sendActionBar(Player player, DisguiseSession session) {
        String message =
                "<light_purple>Disguised as: <white>"
                        + session.getDisguisedAsName()
                        + "<gray> | <yellow>/disguise off<gray> to remove";

        TextUtil.sendActionBar(player, message);
    }

    private void cancelActionBarTask(DisguiseSession session) {
        if (session != null && session.hasActionBarTask()) {
            Bukkit.getScheduler().cancelTask(session.getActionBarTaskId());
            session.setActionBarTaskId(-1);
        }
    }

    public void handleQuit(Player player) {
        DisguiseSession session = activeSessions.remove(player.getUniqueId());
        if (session != null) {
            cancelActionBarTask(session);
        }
    }

    public void shutdown() {
        for (UUID uuid : new ArrayList<>(activeSessions.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                undisguise(player);
            }
        }
        activeSessions.clear();
    }
}
