package net.serverplugins.admin.reset.handlers;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.admin.reset.ResetResult;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class RankResetHandler implements ResetHandler {

    private final ServerAdmin plugin;

    public RankResetHandler(ServerAdmin plugin) {
        this.plugin = plugin;
    }

    @Override
    public CompletableFuture<ResetResult> execute(
            UUID targetUuid, String targetName, Player staff) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        RegisteredServiceProvider<?> provider =
                                Bukkit.getServicesManager()
                                        .getRegistration(
                                                Class.forName("net.luckperms.api.LuckPerms"));

                        if (provider == null) {
                            return ResetResult.failure("LuckPerms not available");
                        }

                        Object luckPerms = provider.getProvider();
                        String defaultRank =
                                plugin.getConfig().getString("reset.default-rank", "default");

                        Method getUserManager = luckPerms.getClass().getMethod("getUserManager");
                        Object userManager = getUserManager.invoke(luckPerms);

                        Method getGroupManager = luckPerms.getClass().getMethod("getGroupManager");
                        Object groupManager = getGroupManager.invoke(luckPerms);

                        Method getGroup =
                                groupManager.getClass().getMethod("getGroup", String.class);
                        Object defaultGroup = getGroup.invoke(groupManager, defaultRank);

                        if (defaultGroup == null) {
                            return ResetResult.failure(
                                    "Default group '" + defaultRank + "' not found");
                        }

                        Method loadUser = userManager.getClass().getMethod("loadUser", UUID.class);
                        Object userFuture = loadUser.invoke(userManager, targetUuid);

                        Method join = userFuture.getClass().getMethod("join");
                        Object user = join.invoke(userFuture);

                        if (user == null) {
                            return ResetResult.failure("User not found in LuckPerms");
                        }

                        Method getPrimaryGroup = user.getClass().getMethod("getPrimaryGroup");
                        String previousPrimaryGroup = (String) getPrimaryGroup.invoke(user);

                        Method data = user.getClass().getMethod("data");
                        Object userData = data.invoke(user);

                        Class<?> nodeClass =
                                Class.forName("net.luckperms.api.node.types.InheritanceNode");
                        Method clear =
                                userData.getClass()
                                        .getMethod("clear", java.util.function.Predicate.class);
                        clear.invoke(
                                userData,
                                (java.util.function.Predicate<Object>)
                                        node -> nodeClass.isInstance(node));

                        Class<?> inheritanceNodeClass =
                                Class.forName("net.luckperms.api.node.types.InheritanceNode");
                        Method builder =
                                inheritanceNodeClass.getMethod(
                                        "builder",
                                        Class.forName("net.luckperms.api.model.group.Group"));
                        Object nodeBuilder = builder.invoke(null, defaultGroup);
                        Method build = nodeBuilder.getClass().getMethod("build");
                        Object defaultNode = build.invoke(nodeBuilder);

                        Method add =
                                userData.getClass()
                                        .getMethod(
                                                "add",
                                                Class.forName("net.luckperms.api.node.Node"));
                        add.invoke(userData, defaultNode);

                        Method saveUser =
                                userManager
                                        .getClass()
                                        .getMethod(
                                                "saveUser",
                                                Class.forName("net.luckperms.api.model.user.User"));
                        saveUser.invoke(userManager, user);

                        return ResetResult.success(previousPrimaryGroup + " -> " + defaultRank);
                    } catch (ClassNotFoundException e) {
                        return ResetResult.failure("LuckPerms not available");
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to reset rank: " + e.getMessage());
                        return ResetResult.failure("Error resetting rank: " + e.getMessage());
                    }
                });
    }
}
