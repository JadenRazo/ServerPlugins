package net.serverplugins.api.permissions;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.MetaNode;
import org.bukkit.entity.Player;

public class PermissionProvider {

    private final LuckPerms luckPerms;

    public PermissionProvider() {
        this.luckPerms = LuckPermsProvider.get();
    }

    public LuckPerms getLuckPerms() {
        return luckPerms;
    }

    public String getPrimaryGroup(Player player) {
        return getPrimaryGroup(player.getUniqueId());
    }

    public String getPrimaryGroup(UUID uuid) {
        User user = luckPerms.getUserManager().getUser(uuid);
        return user != null ? user.getPrimaryGroup() : "default";
    }

    public boolean hasPermission(Player player, String permission) {
        return player.hasPermission(permission);
    }

    public CompletableFuture<Boolean> hasPermission(UUID uuid, String permission) {
        return luckPerms
                .getUserManager()
                .loadUser(uuid)
                .thenApply(
                        user ->
                                user.getCachedData()
                                        .getPermissionData()
                                        .checkPermission(permission)
                                        .asBoolean());
    }

    public String getMetadata(Player player, String key) {
        return getMetadata(player.getUniqueId(), key);
    }

    public String getMetadata(UUID uuid, String key) {
        User user = luckPerms.getUserManager().getUser(uuid);
        return user != null ? user.getCachedData().getMetaData().getMetaValue(key) : null;
    }

    public int getMetadataInt(Player player, String key, int defaultValue) {
        String value = getMetadata(player, key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public CompletableFuture<Void> setMetadata(UUID uuid, String key, String value) {
        return luckPerms
                .getUserManager()
                .loadUser(uuid)
                .thenAccept(
                        user -> {
                            user.data()
                                    .clear(
                                            NodeType.META.predicate(
                                                    mn -> mn.getMetaKey().equals(key)));
                            MetaNode node = MetaNode.builder(key, value).build();
                            user.data().add(node);
                            luckPerms.getUserManager().saveUser(user);
                        });
    }

    public String[] getGroups(Player player) {
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return new String[0];
        return user.getInheritedGroups(user.getQueryOptions()).stream()
                .map(group -> group.getName())
                .toArray(String[]::new);
    }

    public boolean inGroup(Player player, String groupName) {
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return false;
        return user.getInheritedGroups(user.getQueryOptions()).stream()
                .anyMatch(group -> group.getName().equalsIgnoreCase(groupName));
    }
}
