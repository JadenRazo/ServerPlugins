package net.serverplugins.bluemap.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import net.serverplugins.api.messages.Placeholder;
import net.serverplugins.api.messages.PluginMessenger;
import net.serverplugins.bluemap.ServerBlueMap;
import net.serverplugins.bluemap.models.POI;
import net.serverplugins.bluemap.repository.POIRepository;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class POICommand implements CommandExecutor, TabCompleter {

    private final ServerBlueMap plugin;
    private final POIRepository poiRepository;
    private final PluginMessenger messenger;

    private static final List<String> CATEGORIES =
            Arrays.asList("shop", "warp", "landmark", "resource", "event");

    public POICommand(ServerBlueMap plugin, POIRepository poiRepository) {
        this.plugin = plugin;
        this.poiRepository = poiRepository;
        this.messenger = plugin.getBlueMapConfig().getMessenger();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "add":
                return handleAdd(sender, args);
            case "remove":
            case "delete":
                return handleRemove(sender, args);
            case "list":
                return handleList(sender, args);
            case "info":
                return handleInfo(sender, args);
            case "hide":
                return handleHide(sender, args);
            case "show":
                return handleShow(sender, args);
            case "reload":
                return handleReload(sender);
            default:
                sendHelp(sender);
                return true;
        }
    }

    private boolean handleAdd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("serverbluemap.admin")) {
            messenger.send(sender, "no-permission");
            return true;
        }

        if (!(sender instanceof Player)) {
            messenger.send(sender, "players-only");
            return true;
        }

        if (args.length < 3) {
            messenger.send(sender, "usage-add");
            messenger.send(
                    sender,
                    "valid-categories",
                    Placeholder.of("categories", String.join(", ", CATEGORIES)));
            return true;
        }

        Player player = (Player) sender;
        String name = args[1];
        String category = args[2].toLowerCase();

        // Validate category
        if (!CATEGORIES.contains(category)) {
            messenger.send(
                    sender,
                    "invalid-category",
                    Placeholder.of("categories", String.join(", ", CATEGORIES)));
            return true;
        }

        // Check if POI with this name already exists
        if (poiRepository.getPOIByName(name) != null) {
            messenger.send(sender, "poi-exists", Placeholder.of("name", name));
            return true;
        }

        // Get description if provided
        String description = null;
        if (args.length > 3) {
            description = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        }

        // Get player location
        Location loc = player.getLocation();

        // Create POI
        POI poi = new POI();
        poi.setName(name);
        poi.setWorld(loc.getWorld().getName());
        poi.setX(loc.getBlockX());
        poi.setY(loc.getBlockY());
        poi.setZ(loc.getBlockZ());
        poi.setCategory(category);
        poi.setDescription(description);
        poi.setCreatorUuid(player.getUniqueId());

        POI created = poiRepository.createPOI(poi);
        if (created != null) {
            messenger.send(sender, "poi-created", Placeholder.of("name", name));
            messenger.send(
                    sender,
                    "poi-location",
                    Placeholder.of("category", category),
                    Placeholder.of("x", String.valueOf(loc.getBlockX())),
                    Placeholder.of("y", String.valueOf(loc.getBlockY())),
                    Placeholder.of("z", String.valueOf(loc.getBlockZ())));
            plugin.triggerUpdate();
        } else {
            messenger.send(sender, "poi-operation-failed", Placeholder.of("operation", "create"));
        }

        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("serverbluemap.admin")) {
            messenger.send(sender, "no-permission");
            return true;
        }

        if (args.length < 2) {
            messenger.send(sender, "usage-remove");
            return true;
        }

        String name = args[1];
        POI poi = poiRepository.getPOIByName(name);

        if (poi == null) {
            messenger.send(sender, "poi-not-found", Placeholder.of("name", name));
            return true;
        }

        if (poiRepository.deletePOI(poi.getId())) {
            messenger.send(sender, "poi-removed", Placeholder.of("name", name));
            plugin.triggerUpdate();
        } else {
            messenger.send(sender, "poi-operation-failed", Placeholder.of("operation", "remove"));
        }

        return true;
    }

    private boolean handleList(CommandSender sender, String[] args) {
        if (!sender.hasPermission("serverbluemap.admin")) {
            messenger.send(sender, "no-permission");
            return true;
        }

        List<POI> pois;
        String filter = "";

        if (args.length > 1) {
            String category = args[1].toLowerCase();
            if (CATEGORIES.contains(category)) {
                pois = poiRepository.getPOIsByCategory(category);
                filter = " (Category: " + category + ")";
            } else {
                pois = poiRepository.getAllPOIs();
            }
        } else {
            pois = poiRepository.getAllPOIs();
        }

        if (pois.isEmpty()) {
            messenger.send(sender, "no-pois-found", Placeholder.of("filter", filter));
            return true;
        }

        messenger.send(sender, "list-header", Placeholder.of("filter", filter));
        for (POI poi : pois) {
            String visibility = poi.isVisible() ? "<green>✓" : "<red>✗";
            String line =
                    String.format(
                            "<gray>%d. %s<white>%s <gray>[%s] - %s (%d, %d, %d)",
                            poi.getId(),
                            visibility,
                            poi.getName(),
                            poi.getCategory(),
                            poi.getWorld(),
                            poi.getX(),
                            poi.getY(),
                            poi.getZ());
            messenger.sendRaw(sender, line);
        }
        messenger.send(sender, "list-total", Placeholder.of("count", String.valueOf(pois.size())));

        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("serverbluemap.admin")) {
            messenger.send(sender, "no-permission");
            return true;
        }

        if (args.length < 2) {
            messenger.send(sender, "usage-info");
            return true;
        }

        String name = args[1];
        POI poi = poiRepository.getPOIByName(name);

        if (poi == null) {
            messenger.send(sender, "poi-not-found", Placeholder.of("name", name));
            return true;
        }

        messenger.send(sender, "info-header", Placeholder.of("name", poi.getName()));
        messenger.send(sender, "info-id", Placeholder.of("id", String.valueOf(poi.getId())));
        messenger.send(sender, "info-category", Placeholder.of("category", poi.getCategory()));
        messenger.send(
                sender,
                "info-location",
                Placeholder.of("world", poi.getWorld()),
                Placeholder.of("x", String.valueOf(poi.getX())),
                Placeholder.of("y", String.valueOf(poi.getY())),
                Placeholder.of("z", String.valueOf(poi.getZ())));
        messenger.send(
                sender, "info-visible", Placeholder.of("visible", poi.isVisible() ? "Yes" : "No"));
        messenger.send(
                sender, "info-created", Placeholder.of("created", poi.getCreatedAt().toString()));
        if (poi.getDescription() != null && !poi.getDescription().isEmpty()) {
            messenger.send(
                    sender,
                    "info-description",
                    Placeholder.of("description", poi.getDescription()));
        }

        return true;
    }

    private boolean handleHide(CommandSender sender, String[] args) {
        if (!sender.hasPermission("serverbluemap.admin")) {
            messenger.send(sender, "no-permission");
            return true;
        }

        if (args.length < 2) {
            messenger.send(sender, "usage-hide");
            return true;
        }

        String name = args[1];
        POI poi = poiRepository.getPOIByName(name);

        if (poi == null) {
            messenger.send(sender, "poi-not-found", Placeholder.of("name", name));
            return true;
        }

        poi.setVisible(false);
        if (poiRepository.updatePOI(poi)) {
            messenger.send(sender, "poi-hidden", Placeholder.of("name", name));
            plugin.triggerUpdate();
        } else {
            messenger.send(sender, "poi-operation-failed", Placeholder.of("operation", "hide"));
        }

        return true;
    }

    private boolean handleShow(CommandSender sender, String[] args) {
        if (!sender.hasPermission("serverbluemap.admin")) {
            messenger.send(sender, "no-permission");
            return true;
        }

        if (args.length < 2) {
            messenger.send(sender, "usage-show");
            return true;
        }

        String name = args[1];
        POI poi = poiRepository.getPOIByName(name);

        if (poi == null) {
            messenger.send(sender, "poi-not-found", Placeholder.of("name", name));
            return true;
        }

        poi.setVisible(true);
        if (poiRepository.updatePOI(poi)) {
            messenger.send(sender, "poi-shown", Placeholder.of("name", name));
            plugin.triggerUpdate();
        } else {
            messenger.send(sender, "poi-operation-failed", Placeholder.of("operation", "show"));
        }

        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("serverbluemap.admin")) {
            messenger.send(sender, "no-permission");
            return true;
        }

        plugin.getBlueMapConfig().reload();
        plugin.triggerUpdate();
        messenger.send(sender, "config-reloaded");

        return true;
    }

    private void sendHelp(CommandSender sender) {
        messenger.send(sender, "help-header");
        messenger.send(sender, "help-add");
        messenger.send(sender, "help-remove");
        messenger.send(sender, "help-list");
        messenger.send(sender, "help-info");
        messenger.send(sender, "help-hide");
        messenger.send(sender, "help-show");
        messenger.send(sender, "help-reload");
        messenger.send(
                sender,
                "help-categories",
                Placeholder.of("categories", String.join(", ", CATEGORIES)));
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("serverbluemap.admin")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return Arrays.asList("add", "remove", "list", "info", "hide", "show", "reload").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("remove")
                    || subCommand.equals("delete")
                    || subCommand.equals("info")
                    || subCommand.equals("hide")
                    || subCommand.equals("show")) {
                // Return list of POI names
                return poiRepository.getAllPOIs().stream()
                        .map(POI::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (subCommand.equals("list")) {
                // Return list of categories
                return CATEGORIES.stream()
                        .filter(cat -> cat.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("add")) {
            // Return list of categories
            return CATEGORIES.stream()
                    .filter(cat -> cat.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
