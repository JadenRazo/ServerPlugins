package net.serverplugins.afk.commands;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.serverplugins.afk.ServerAFK;
import net.serverplugins.afk.gui.AdminMainGui;
import net.serverplugins.afk.managers.HologramManager;
import net.serverplugins.afk.models.AfkZone;
import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.messages.Placeholder;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class AfkCommand implements CommandExecutor, TabCompleter {

    private final ServerAFK plugin;
    private final Map<UUID, Location> corner1Selections = new HashMap<>();
    private final Map<UUID, Location> corner2Selections = new HashMap<>();

    public AfkCommand(ServerAFK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!player.hasPermission("serverafk.admin")) {
            plugin.getAfkConfig().getMessenger().send(player, "no-permission");
            return true;
        }

        if (args.length == 0) {
            new AdminMainGui(plugin, player, this).open();
            return true;
        }

        String sub = args[0].toLowerCase();
        var messenger = plugin.getAfkConfig().getMessenger();
        switch (sub) {
            case "p1" -> {
                corner1Selections.put(player.getUniqueId(), player.getLocation().clone());
                messenger.send(player, "corner-set", Placeholder.of("corner", "1"));
            }
            case "p2" -> {
                corner2Selections.put(player.getUniqueId(), player.getLocation().clone());
                messenger.send(player, "corner-set", Placeholder.of("corner", "2"));
            }
            case "quickcreate", "qc" -> handleQuickCreate(player, args);
            case "delete", "del" -> handleDeleteCommand(player, args);
            case "reload" -> {
                plugin.reload();
                messenger.sendSuccess(player, "Configuration reloaded!");
            }
            case "sign", "holo" -> handleSignCommand(player);
            case "delsign", "delholo" -> handleDeleteSignCommand(player);
            default -> new AdminMainGui(plugin, player, this).open();
        }
        return true;
    }

    public Location getCorner1(UUID uuid) {
        return corner1Selections.get(uuid);
    }

    public Location getCorner2(UUID uuid) {
        return corner2Selections.get(uuid);
    }

    public boolean hasSelection(UUID uuid) {
        return corner1Selections.containsKey(uuid) && corner2Selections.containsKey(uuid);
    }

    public void clearSelection(UUID uuid) {
        corner1Selections.remove(uuid);
        corner2Selections.remove(uuid);
    }

    private void handleSignCommand(Player player) {
        var messenger = plugin.getAfkConfig().getMessenger();

        HologramManager holoManager = plugin.getHologramManager();
        if (holoManager == null) {
            messenger.sendError(player, "DecentHolograms is not installed!");
            return;
        }

        Block target = player.getTargetBlockExact(10);
        if (target == null) {
            messenger.sendError(player, "Look at a block within 10 blocks!");
            return;
        }

        Optional<AfkZone> zoneOpt = plugin.getZoneManager().getZoneAt(player.getLocation());
        if (zoneOpt.isEmpty()) {
            messenger.sendError(player, "You must be inside an AFK zone!");
            return;
        }

        AfkZone zone = zoneOpt.get();
        Location holoLoc = target.getLocation().add(0.5, 2, 0.5);
        holoManager.createHologram(zone, holoLoc);
        messenger.sendSuccess(
                player, "Hologram created for zone: " + ColorScheme.WARNING + zone.getName());
    }

    private void handleDeleteSignCommand(Player player) {
        var messenger = plugin.getAfkConfig().getMessenger();

        HologramManager holoManager = plugin.getHologramManager();
        if (holoManager == null) {
            messenger.sendError(player, "DecentHolograms is not installed!");
            return;
        }

        Optional<AfkZone> zoneOpt = plugin.getZoneManager().getZoneAt(player.getLocation());
        if (zoneOpt.isEmpty()) {
            messenger.sendError(player, "You must be inside an AFK zone!");
            return;
        }

        AfkZone zone = zoneOpt.get();
        if (!zone.hasHologram()) {
            messenger.sendError(player, "This zone doesn't have a hologram!");
            return;
        }

        holoManager.deleteHologram(zone);
        messenger.sendSuccess(
                player, "Hologram deleted for zone: " + ColorScheme.WARNING + zone.getName());
    }

    private void handleQuickCreate(Player player, String[] args) {
        var messenger = plugin.getAfkConfig().getMessenger();

        if (args.length < 2) {
            messenger.sendError(player, "Usage: /wa quickcreate <name> [radius] [height]");
            messenger.sendInfo(player, "Default radius: 10, Default height: 5");
            return;
        }

        String name = args[1];
        int radius = 10;
        int height = 5;

        if (args.length >= 3) {
            try {
                radius = Integer.parseInt(args[2]);
                if (radius < 1 || radius > 100) {
                    messenger.sendError(player, "Radius must be between 1 and 100!");
                    return;
                }
            } catch (NumberFormatException e) {
                messenger.sendError(player, "Invalid radius! Must be a number.");
                return;
            }
        }

        if (args.length >= 4) {
            try {
                height = Integer.parseInt(args[3]);
                if (height < 1 || height > 50) {
                    messenger.sendError(player, "Height must be between 1 and 50!");
                    return;
                }
            } catch (NumberFormatException e) {
                messenger.sendError(player, "Invalid height! Must be a number.");
                return;
            }
        }

        if (plugin.getZoneManager().zoneExists(name)) {
            messenger.sendError(player, "A zone with that name already exists!");
            return;
        }

        Location loc = player.getLocation();
        Location corner1 = loc.clone().add(-radius, -height, -radius);
        Location corner2 = loc.clone().add(radius, height, radius);

        AfkZone zone = plugin.getZoneManager().createZone(name, corner1, corner2);
        if (zone == null) {
            messenger.sendError(player, "Failed to create zone!");
            return;
        }

        messenger.send(player, "zone-created", Placeholder.of("name", name));
        messenger.sendInfo(
                player,
                "Size: " + (radius * 2 + 1) + "x" + (height * 2 + 1) + "x" + (radius * 2 + 1));
        messenger.sendInfo(
                player,
                "Use "
                        + ColorScheme.WARNING
                        + "/wa "
                        + ColorScheme.INFO
                        + "to edit rewards and settings.");
    }

    private void handleDeleteCommand(Player player, String[] args) {
        var messenger = plugin.getAfkConfig().getMessenger();

        if (args.length < 2) {
            messenger.sendError(player, "Usage: /wa delete <zone name>");
            return;
        }

        String zoneName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        Optional<AfkZone> zoneOpt = plugin.getZoneManager().getZoneByName(zoneName);

        if (zoneOpt.isEmpty()) {
            messenger.sendError(player, "Zone not found: " + zoneName);
            messenger.sendInfo(player, "Use /wa to view all zones");
            return;
        }

        AfkZone zone = zoneOpt.get();
        plugin.getZoneManager().deleteZone(zone);
        messenger.send(player, "zone-deleted", Placeholder.of("name", zone.getName()));
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList(
                    "p1",
                    "p2",
                    "quickcreate",
                    "qc",
                    "delete",
                    "del",
                    "reload",
                    "sign",
                    "holo",
                    "delsign",
                    "delholo");
        }
        if (args.length == 2
                && (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("del"))) {
            return plugin.getZoneManager().getAllZones().stream().map(AfkZone::getName).toList();
        }
        return Collections.emptyList();
    }
}
