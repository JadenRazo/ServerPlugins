package net.serverplugins.bridge.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.chat.Chat;
import net.serverplugins.bridge.ServerBridge;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;

public class ChatListener implements Listener {

    private final ServerBridge plugin;
    private Chat vaultChat;
    private boolean hasPapi;

    public ChatListener(ServerBridge plugin) {
        this.plugin = plugin;
        setupVaultChat();
        this.hasPapi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    private void setupVaultChat() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault not found - prefix/suffix retrieval may not work");
            return;
        }
        RegisteredServiceProvider<Chat> rsp =
                Bukkit.getServicesManager().getRegistration(Chat.class);
        if (rsp != null) {
            vaultChat = rsp.getProvider();
            plugin.getLogger().info("Vault Chat provider registered for cross-server chat");
        } else {
            plugin.getLogger().warning("No Vault Chat provider found - prefix/suffix may be empty");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        var redis = plugin.getRedisClient();
        if (redis == null || !redis.isConnected()) {
            return;
        }

        Player player = event.getPlayer();
        String playerName = player.getName();
        String uuid = player.getUniqueId().toString();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        // Publish to Discord channel (existing behavior)
        redis.publishChat(playerName, uuid, message);

        // Publish to cross-server channel with prefix/suffix
        if (plugin.getBridgeConfig().isCrossServerChatEnabled()) {
            String prefix = getPlayerPrefix(player);
            String suffix = getPlayerSuffix(player);

            // Use display name if available, fallback to player name
            String displayName = getDisplayName(player);

            redis.publishCrossServerChat(displayName, uuid, prefix, suffix, message);
        }
    }

    /** Gets the player's display name, trying LuckPerms nickname first */
    private String getDisplayName(Player player) {
        // Try LuckPerms nickname via PlaceholderAPI first
        if (hasPapi) {
            try {
                String nickname =
                        me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(
                                player, "%luckperms_meta_nickname%");
                if (nickname != null
                        && !nickname.isEmpty()
                        && !nickname.equals("%luckperms_meta_nickname%")) {
                    return nickname;
                }
            } catch (Exception ignored) {
            }
        }

        // Fallback to Bukkit display name (usually same as player name unless modified)
        String displayName =
                PlainTextComponentSerializer.plainText().serialize(player.displayName());
        if (displayName != null && !displayName.isEmpty()) {
            return displayName;
        }

        // Ultimate fallback
        return player.getName();
    }

    private String getPlayerPrefix(Player player) {
        // Try PlaceholderAPI first (more reliable with LuckPerms)
        if (hasPapi) {
            try {
                String prefix =
                        me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(
                                player, "%vault_prefix%");
                if (prefix != null && !prefix.equals("%vault_prefix%") && !prefix.isEmpty()) {
                    return prefix;
                }
            } catch (Exception ignored) {
            }
        }

        // Fallback to direct Vault Chat API
        if (vaultChat != null) {
            try {
                String prefix = vaultChat.getPlayerPrefix(player);
                return prefix != null ? prefix : "";
            } catch (Exception e) {
                plugin.getLogger().warning("Error getting player prefix: " + e.getMessage());
            }
        }
        return "";
    }

    private String getPlayerSuffix(Player player) {
        // Try PlaceholderAPI first
        if (hasPapi) {
            try {
                String suffix =
                        me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(
                                player, "%vault_suffix%");
                if (suffix != null && !suffix.equals("%vault_suffix%") && !suffix.isEmpty()) {
                    return suffix;
                }
            } catch (Exception ignored) {
            }
        }

        // Fallback to direct Vault Chat API
        if (vaultChat != null) {
            try {
                String suffix = vaultChat.getPlayerSuffix(player);
                return suffix != null ? suffix : "";
            } catch (Exception e) {
                plugin.getLogger().warning("Error getting player suffix: " + e.getMessage());
            }
        }
        return "";
    }
}
