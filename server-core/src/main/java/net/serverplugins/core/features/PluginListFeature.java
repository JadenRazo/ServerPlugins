package net.serverplugins.core.features;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.core.ServerCore;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.jetbrains.annotations.NotNull;

public class PluginListFeature extends Feature implements CommandExecutor {

    public PluginListFeature(ServerCore plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "Plugin List";
    }

    @Override
    public String getDescription() {
        return "Custom /plugins command with hover information";
    }

    @Override
    protected void onEnable() {
        // Register command
        if (plugin.getCommand("plugins") != null) {
            plugin.getCommand("plugins").setExecutor(this);
        }
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (!isEnabled()) {
            // Use default behavior
            return false;
        }

        if (!sender.hasPermission("servercore.pluginlist")) {
            TextUtil.send(sender, "<red>You do not have permission to use this command.");
            return true;
        }

        Plugin[] plugins = Bukkit.getPluginManager().getPlugins();
        int count = plugins.length;

        // Build the message
        Component header = TextUtil.parse("<blue>Plugins (" + count + "): ");
        Component list = Component.empty();

        boolean first = true;
        for (Plugin pl : plugins) {
            if (!first) {
                list = list.append(TextUtil.parse("<white>, "));
            }
            first = false;

            PluginDescriptionFile desc = pl.getDescription();
            String name = desc.getName();
            String version = desc.getVersion();

            // Color based on enabled state
            NamedTextColor color = pl.isEnabled() ? NamedTextColor.GREEN : NamedTextColor.RED;

            // Get hover text from config and parse it with MiniMessage
            String hoverTextConfig = plugin.getCoreConfig().getPluginListHoverText();
            Component hover = TextUtil.parse(hoverTextConfig);

            Component pluginComponent =
                    Component.text(name, color)
                            .append(Component.text(" (" + version + ")", NamedTextColor.GRAY))
                            .hoverEvent(HoverEvent.showText(hover));

            list = list.append(pluginComponent);
        }

        sender.sendMessage(header.append(list));
        return true;
    }
}
