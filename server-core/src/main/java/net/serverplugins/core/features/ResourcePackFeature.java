package net.serverplugins.core.features;

import java.net.URI;
import java.util.*;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.core.ServerCore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class ResourcePackFeature extends Feature implements Listener {

    private String defaultUrl;
    private String defaultHash;
    private Component prompt;
    private boolean force;
    private int delay;
    private boolean onlyFirstJoin;
    private final Map<Integer, VersionPack> versionPacks = new HashMap<>();
    private final Set<UUID> sentPlayers = new HashSet<>();

    public ResourcePackFeature(ServerCore plugin) {
        super(plugin);
        loadConfig();
    }

    @Override
    public String getName() {
        return "Resource Pack Loader";
    }

    @Override
    public String getDescription() {
        return "Sends resource pack to players on join";
    }

    @Override
    protected void onEnable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    protected void onDisable() {
        sentPlayers.clear();
    }

    private void loadConfig() {
        versionPacks.clear();

        defaultUrl = plugin.getConfig().getString("settings.resource-pack.url", "");
        defaultHash = plugin.getConfig().getString("settings.resource-pack.hash", "");
        String promptText = plugin.getConfig().getString("settings.resource-pack.prompt", "");
        prompt = promptText.isEmpty() ? Component.empty() : TextUtil.parseLegacy(promptText);
        force = plugin.getConfig().getBoolean("settings.resource-pack.force", false);
        delay = plugin.getConfig().getInt("settings.resource-pack.delay", 60);
        onlyFirstJoin =
                plugin.getConfig().getBoolean("settings.resource-pack.only-on-first-join", false);

        // Load version-specific packs
        ConfigurationSection versions =
                plugin.getConfig().getConfigurationSection("settings.resource-pack.versions");
        if (versions != null) {
            for (String key : versions.getKeys(false)) {
                try {
                    int protocolVersion = Integer.parseInt(key);
                    String url = versions.getString(key + ".url", "");
                    String hash = versions.getString(key + ".hash", "");
                    if (!url.isEmpty()) {
                        versionPacks.put(protocolVersion, new VersionPack(url, hash));
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!isEnabled()) return;

        Player player = event.getPlayer();

        // Check only first join
        if (onlyFirstJoin && sentPlayers.contains(player.getUniqueId())) {
            return;
        }

        // Delay sending the pack
        plugin.getServer()
                .getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            if (player.isOnline()) {
                                sendResourcePack(player);
                                sentPlayers.add(player.getUniqueId());
                            }
                        },
                        delay);
    }

    private void sendResourcePack(Player player) {
        int protocolVersion = player.getProtocolVersion();

        // Find best matching version pack
        String url = defaultUrl;
        String hash = defaultHash;

        // Check for exact or closest lower version match
        int bestMatch = 0;
        for (Map.Entry<Integer, VersionPack> entry : versionPacks.entrySet()) {
            int version = entry.getKey();
            if (version <= protocolVersion && version > bestMatch) {
                bestMatch = version;
                url = entry.getValue().url;
                hash = entry.getValue().hash;
            }
        }

        if (url == null || url.isEmpty()) {
            return; // No resource pack configured
        }

        try {
            URI uri = URI.create(url);

            ResourcePackInfo.Builder builder = ResourcePackInfo.resourcePackInfo().uri(uri);

            // Only set hash if it's a valid SHA-1 hash (40 hex characters)
            // Paper/Adventure API requires the hash to be lowercase hex
            if (hash != null && !hash.isEmpty() && isValidSha1Hash(hash)) {
                builder.hash(hash.toLowerCase());
            }

            ResourcePackInfo packInfo = builder.build();

            ResourcePackRequest.Builder requestBuilder =
                    ResourcePackRequest.resourcePackRequest().packs(packInfo).required(force);

            if (prompt != null && !prompt.equals(Component.empty())) {
                requestBuilder.prompt(prompt);
            }

            player.sendResourcePacks(requestBuilder.build());

        } catch (IllegalArgumentException e) {
            // Don't log noisy errors for invalid URLs or other config issues
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger()
                        .warning(
                                "Failed to send resource pack to "
                                        + player.getName()
                                        + ": "
                                        + e.getMessage());
            }
        } catch (Exception e) {
            plugin.getLogger()
                    .warning(
                            "Failed to send resource pack to "
                                    + player.getName()
                                    + ": "
                                    + e.getMessage());
        }
    }

    private boolean isValidSha1Hash(String hash) {
        return hash != null && hash.length() == 40 && hash.matches("[0-9a-fA-F]+");
    }

    public void reload() {
        loadConfig();
    }

    private record VersionPack(String url, String hash) {}
}
