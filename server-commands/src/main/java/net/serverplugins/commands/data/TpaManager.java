package net.serverplugins.commands.data;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.serverplugins.api.messages.Placeholder;
import net.serverplugins.commands.ServerCommands;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class TpaManager {

    private final ServerCommands plugin;
    private final Map<UUID, TpaRequest> pendingRequests = new ConcurrentHashMap<>();
    private final int expirationSeconds;

    public TpaManager(ServerCommands plugin, int expirationSeconds) {
        this.plugin = plugin;
        this.expirationSeconds = expirationSeconds;
    }

    public boolean createRequest(Player sender, Player target, boolean tpaHere) {
        UUID targetId = target.getUniqueId();

        if (pendingRequests.containsKey(targetId)) {
            TpaRequest existing = pendingRequests.get(targetId);
            if (existing.getSenderId().equals(sender.getUniqueId())) {
                return false;
            }
        }

        TpaRequest request = new TpaRequest(sender.getUniqueId(), targetId, tpaHere);
        pendingRequests.put(targetId, request);

        new BukkitRunnable() {
            @Override
            public void run() {
                TpaRequest current = pendingRequests.get(targetId);
                if (current != null && current.equals(request)) {
                    pendingRequests.remove(targetId);
                    Player senderPlayer = Bukkit.getPlayer(sender.getUniqueId());
                    if (senderPlayer != null) {
                        plugin.getCommandsConfig()
                                .getMessenger()
                                .send(
                                        senderPlayer,
                                        "tpa-expired",
                                        Placeholder.of("player", target.getName()));
                    }
                }
            }
        }.runTaskLater(plugin, expirationSeconds * 20L);

        return true;
    }

    public TpaRequest getRequest(UUID targetId) {
        return pendingRequests.get(targetId);
    }

    public TpaRequest removeRequest(UUID targetId) {
        return pendingRequests.remove(targetId);
    }

    public boolean hasRequest(UUID targetId) {
        return pendingRequests.containsKey(targetId);
    }

    public static class TpaRequest {
        private final UUID senderId;
        private final UUID targetId;
        private final boolean tpaHere;
        private final long timestamp;

        public TpaRequest(UUID senderId, UUID targetId, boolean tpaHere) {
            this.senderId = senderId;
            this.targetId = targetId;
            this.tpaHere = tpaHere;
            this.timestamp = System.currentTimeMillis();
        }

        public UUID getSenderId() {
            return senderId;
        }

        public UUID getTargetId() {
            return targetId;
        }

        public boolean isTpaHere() {
            return tpaHere;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}
