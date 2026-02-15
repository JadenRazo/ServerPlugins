package net.serverplugins.admin.commands;

import java.util.ArrayList;
import java.util.List;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.admin.xray.XrayTracker;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.messages.Placeholder;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class XrayCheckCommand implements CommandExecutor, TabCompleter {

    private final ServerAdmin plugin;

    public XrayCheckCommand(ServerAdmin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("serveradmin.xray")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        if (plugin.getXrayManager() == null) {
            plugin.getAdminConfig().getMessenger().sendError(sender, "Xray detection is disabled.");
            return true;
        }

        if (args.length == 0) {
            CommonMessages.INVALID_USAGE.send(
                    sender, Placeholder.of("usage", "/xraycheck <player>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            CommonMessages.PLAYER_NOT_FOUND.send(sender);
            return true;
        }

        XrayTracker tracker = plugin.getXrayManager().getTracker(target.getUniqueId());

        TextUtil.send(sender, "<gold>=== Mining Stats: " + target.getName() + " ===");

        if (tracker == null) {
            TextUtil.send(sender, "<gray>No mining data recorded.");
            return true;
        }

        int suspicion = tracker.calculateSuspicion();
        String suspicionColor;
        if (suspicion >= 80) {
            suspicionColor = "<dark_red>";
        } else if (suspicion >= 60) {
            suspicionColor = "<red>";
        } else if (suspicion >= 40) {
            suspicionColor = "<yellow>";
        } else {
            suspicionColor = "<green>";
        }

        TextUtil.send(sender, "<gray>Suspicion Level: " + suspicionColor + suspicion + "%");
        TextUtil.send(sender, "<gray>Total Ores: <white>" + tracker.getTotalOres());
        TextUtil.send(sender, "<gray>Unexposed Ores: <white>" + tracker.getUnexposedOres());
        TextUtil.send(sender, "<gray>Stone Mined: <white>" + tracker.getTotalStone());

        if (tracker.getTotalStone() + tracker.getTotalOres() > 0) {
            double oreRatio =
                    (double) tracker.getTotalOres()
                            / (tracker.getTotalStone() + tracker.getTotalOres())
                            * 100;
            TextUtil.send(sender, "<gray>Ore Ratio: <white>" + String.format("%.1f%%", oreRatio));
        }

        if (tracker.getTotalOres() > 0) {
            double unexposedRatio =
                    (double) tracker.getUnexposedOres() / tracker.getTotalOres() * 100;
            TextUtil.send(
                    sender,
                    "<gray>Unexposed Ratio: <white>" + String.format("%.1f%%", unexposedRatio));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase().startsWith(partial)) {
                    completions.add(online.getName());
                }
            }
        }

        return completions;
    }
}
