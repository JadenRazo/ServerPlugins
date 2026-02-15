package net.serverplugins.items.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.items.ServerItems;
import net.serverplugins.items.managers.ItemManager;
import net.serverplugins.items.mechanics.Mechanic;
import net.serverplugins.items.mechanics.impl.DurabilityMechanic;
import net.serverplugins.items.models.CustomItem;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ItemsCommand implements CommandExecutor, TabCompleter {

    private final ServerItems plugin;
    private final ItemManager itemManager;

    public ItemsCommand(ServerItems plugin) {
        this.plugin = plugin;
        this.itemManager = plugin.getItemManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "give" -> handleGive(sender, args);
            case "list" -> handleList(sender, args);
            case "info" -> handleInfo(sender, args);
            case "reload" -> handleReload(sender);
            case "pack" -> handlePack(sender, args);
            case "browse" -> handleBrowse(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("serveritems.give")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return;
        }

        if (args.length < 3) {
            TextUtil.sendError(sender, "Usage: /witems give <player> <id> [amount]");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            CommonMessages.PLAYER_NOT_FOUND.send(sender);
            return;
        }

        String itemId = args[2].toLowerCase();
        CustomItem item = itemManager.getItem(itemId);
        if (item == null) {
            TextUtil.sendError(sender, "Item <white>" + itemId + "</white> not found.");
            return;
        }

        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
                amount = Math.max(1, Math.min(amount, 64));
            } catch (NumberFormatException e) {
                TextUtil.sendError(sender, "Invalid amount: " + args[3]);
                return;
            }
        }

        ItemStack stack = itemManager.buildItemStack(item, amount);
        target.getInventory().addItem(stack);

        TextUtil.sendSuccess(
                sender,
                "Gave <white>"
                        + amount
                        + "x "
                        + item.getDisplayName()
                        + "</white> to <white>"
                        + target.getName()
                        + "</white>.");

        if (!sender.equals(target)) {
            TextUtil.sendSuccess(
                    target,
                    "You received <white>" + amount + "x " + item.getDisplayName() + "</white>.");
        }
    }

    private void handleList(CommandSender sender, String[] args) {
        if (!sender.hasPermission("serveritems.admin")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return;
        }

        var allItems = new ArrayList<>(itemManager.getAllItems());
        if (allItems.isEmpty()) {
            TextUtil.sendWarning(sender, "No custom items are registered.");
            return;
        }

        int pageSize = 10;
        int page = 1;
        if (args.length >= 2) {
            try {
                page = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException ignored) {
            }
        }

        int totalPages = (int) Math.ceil((double) allItems.size() / pageSize);
        page = Math.min(page, totalPages);
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, allItems.size());

        TextUtil.send(
                sender, "<gold>Custom Items <gray>(" + page + "/" + totalPages + ")</gray></gold>");

        for (int i = start; i < end; i++) {
            CustomItem item = allItems.get(i);
            TextUtil.send(
                    sender,
                    " <gray>- <white>"
                            + item.getId()
                            + "</white> <dark_gray>["
                            + item.getMaterial().name()
                            + "]</dark_gray>");
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("serveritems.admin")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return;
        }

        if (args.length < 2) {
            TextUtil.sendError(sender, "Usage: /witems info <id>");
            return;
        }

        String itemId = args[1].toLowerCase();
        CustomItem item = itemManager.getItem(itemId);
        if (item == null) {
            TextUtil.sendError(sender, "Item <white>" + itemId + "</white> not found.");
            return;
        }

        TextUtil.send(sender, "<gold>Item Info: <white>" + item.getId() + "</white></gold>");
        TextUtil.send(sender, " <gray>Material: <white>" + item.getMaterial().name() + "</white>");
        TextUtil.send(sender, " <gray>Display: " + item.getDisplayName());

        if (item.getCustomModelData() > 0) {
            TextUtil.send(sender, " <gray>CMD: <white>" + item.getCustomModelData() + "</white>");
        }

        if (!item.getEnchantments().isEmpty()) {
            TextUtil.send(
                    sender,
                    " <gray>Enchantments: <white>" + item.getEnchantments().size() + "</white>");
        }

        if (!item.getMechanics().isEmpty()) {
            String mechanicNames =
                    item.getMechanics().stream()
                            .map(Mechanic::getId)
                            .collect(Collectors.joining(", "));
            TextUtil.send(sender, " <gray>Mechanics: <white>" + mechanicNames + "</white>");
        }

        DurabilityMechanic durability = item.getMechanic(DurabilityMechanic.class);
        if (durability != null) {
            TextUtil.send(
                    sender,
                    " <gray>Custom Durability: <white>"
                            + durability.getMaxDurability()
                            + "</white>");
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("serveritems.reload")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return;
        }

        plugin.reload();
        TextUtil.sendSuccess(
                sender, "Reloaded <white>" + itemManager.getItemCount() + "</white> custom items.");
    }

    private void handlePack(CommandSender sender, String[] args) {
        if (!sender.hasPermission("serveritems.admin")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return;
        }

        if (args.length < 2 || !args[1].equalsIgnoreCase("generate")) {
            TextUtil.sendError(sender, "Usage: /witems pack generate");
            return;
        }

        TextUtil.send(sender, "<gray>Generating resource pack...");
        net.serverplugins.items.pack.PackManifest manifest = plugin.generatePack();
        if (manifest != null) {
            TextUtil.sendSuccess(
                    sender,
                    "Generated <white>"
                            + manifest.getUniqueFileCount()
                            + "</white> version packs, <white>"
                            + manifest.getPackCount()
                            + "</white> protocol mappings.");
        } else {
            TextUtil.sendError(sender, "Pack generation failed. Check console.");
        }
    }

    private void handleBrowse(CommandSender sender) {
        if (!(sender instanceof org.bukkit.entity.Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return;
        }
        if (!player.hasPermission("serveritems.admin")) {
            CommonMessages.NO_PERMISSION.send(player);
            return;
        }
        plugin.getBrowserGui().open(player, 1);
    }

    private void sendHelp(CommandSender sender) {
        TextUtil.send(sender, "<gold>ServerItems Commands:</gold>");
        TextUtil.send(sender, " <gray>/witems give <player> <id> [amount] <dark_gray>- Give item");
        TextUtil.send(sender, " <gray>/witems list [page] <dark_gray>- List items");
        TextUtil.send(sender, " <gray>/witems info <id> <dark_gray>- Item details");
        TextUtil.send(sender, " <gray>/witems browse <dark_gray>- Browse items GUI");
        TextUtil.send(sender, " <gray>/witems reload <dark_gray>- Reload configs");
        TextUtil.send(sender, " <gray>/witems pack generate <dark_gray>- Generate resource pack");
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return filterStartsWith(
                    List.of("give", "list", "info", "reload", "pack", "browse"), args[0]);
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "give" -> {
                    return null; // default player completion
                }
                case "info" -> {
                    return filterStartsWith(
                            itemManager.getAllItems().stream()
                                    .map(CustomItem::getId)
                                    .collect(Collectors.toList()),
                            args[1]);
                }
                case "pack" -> {
                    return filterStartsWith(List.of("generate"), args[1]);
                }
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filterStartsWith(
                    itemManager.getAllItems().stream()
                            .map(CustomItem::getId)
                            .collect(Collectors.toList()),
                    args[2]);
        }

        return Collections.emptyList();
    }

    private List<String> filterStartsWith(List<String> options, String prefix) {
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }
}
