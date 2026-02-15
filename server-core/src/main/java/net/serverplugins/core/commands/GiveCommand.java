package net.serverplugins.core.commands;

import java.util.*;
import java.util.stream.Stream;
import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.LegacyText;
import net.serverplugins.api.utils.StringUtils;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.core.ServerCore;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

/**
 * Advanced item giving command with support for custom names, lore, enchantments, and more. Usage:
 * /dgive <player> <item> [amount] [-name <name>] [-lore <lore>] [-cmd <model>] [-ench
 * <enchantment:level>] [-flag <flag>] [-color <#hex>] [-silent]
 */
public class GiveCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "servercore.give";
    private final ServerCore plugin;

    public GiveCommand(ServerCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        // Check if the feature is enabled
        if (!plugin.getCoreConfig().isGiveCommandEnabled()) {
            plugin.getCoreConfig()
                    .getMessenger()
                    .sendError(sender, "This command is currently disabled.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        if (args.length < 2) {
            TextUtil.send(
                    sender,
                    ColorScheme.ERROR + "Usage: /dgive <player> <item> [amount] [flags...]");
            return true;
        }

        // Parse player
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            plugin.getCoreConfig().getMessenger().sendError(sender, "Player not found: " + args[0]);
            return true;
        }

        // Parse material
        Material material;
        try {
            material = Material.valueOf(args[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getCoreConfig().getMessenger().sendError(sender, "Invalid material: " + args[1]);
            return true;
        }

        // Create item
        ItemStack item = new ItemStack(material);
        Set<ItemTag> appliedTags = new HashSet<>();

        // Parse amount (if present)
        int argIndex = 2;
        if (args.length > 2 && !args[2].startsWith("-")) {
            try {
                int amount = Integer.parseInt(args[2]);
                if (amount < 1) amount = 1;
                if (amount > material.getMaxStackSize() * 36)
                    amount = material.getMaxStackSize() * 36;
                item.setAmount(amount);
                argIndex = 3;
            } catch (NumberFormatException e) {
                plugin.getCoreConfig()
                        .getMessenger()
                        .sendError(sender, "Amount must be a number: " + args[2]);
                return true;
            }
        }

        // Parse flags
        String currentFlag = null;
        StringBuilder currentValue = new StringBuilder();

        for (int i = argIndex; i < args.length; i++) {
            String arg = args[i];

            if (arg.startsWith("-")) {
                // Process previous flag if exists
                if (currentFlag != null) {
                    if (!processFlag(
                            item,
                            currentFlag,
                            currentValue.toString().trim(),
                            sender,
                            appliedTags)) {
                        return true;
                    }
                }
                currentFlag = arg.substring(1).toLowerCase();
                currentValue = new StringBuilder();
            } else {
                if (currentValue.length() > 0) currentValue.append(" ");
                currentValue.append(arg);
            }
        }

        // Process last flag
        if (currentFlag != null) {
            if (!processFlag(
                    item, currentFlag, currentValue.toString().trim(), sender, appliedTags)) {
                return true;
            }
        }

        // Give item to player
        Map<Integer, ItemStack> overflow = target.getInventory().addItem(item);

        // Format item display name for messages
        String itemDisplayName;
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            itemDisplayName = meta.getDisplayName();
        } else {
            itemDisplayName = StringUtils.formatEnumName(material.name(), true);
        }
        int amount = item.getAmount();
        String itemWithAmount = amount + "x " + itemDisplayName;

        // Send messages (unless silent)
        if (!appliedTags.contains(ItemTag.SILENT)) {
            // Send item-received message to target
            String receivedMsg =
                    plugin.getCoreConfig()
                            .getGiveMessage("item-received")
                            .replace("<item>", itemWithAmount);
            TextUtil.send(target, receivedMsg);

            // Send item-given message to sender (if different from target)
            if (!target.equals(sender)) {
                String givenMsg =
                        plugin.getCoreConfig()
                                .getGiveMessage("item-given")
                                .replace("<item>", itemWithAmount)
                                .replace("<player>", target.getName());
                TextUtil.send(sender, givenMsg);
            }

            // Handle overflow
            if (!overflow.isEmpty()) {
                for (ItemStack dropped : overflow.values()) {
                    target.getWorld().dropItem(target.getLocation(), dropped);
                }
                String droppedMsg = plugin.getCoreConfig().getGiveMessage("item-dropped");
                TextUtil.send(target, droppedMsg);
            }
        }

        return true;
    }

    private boolean processFlag(
            ItemStack item,
            String flag,
            String value,
            CommandSender sender,
            Set<ItemTag> appliedTags) {
        ItemTag tag;
        try {
            tag = ItemTag.valueOf(flag.toUpperCase());
        } catch (IllegalArgumentException e) {
            if (plugin.getCoreConfig().getGiveSendWithErrors()) {
                plugin.getCoreConfig().getMessenger().sendError(sender, "Unknown flag: -" + flag);
                TextUtil.send(
                        sender,
                        ColorScheme.INFO
                                + "Valid flags: -name, -lore, -cmd, -ench, -flag, -color, -silent");
            }
            return !plugin.getCoreConfig().getGiveSendWithErrors();
        }

        appliedTags.add(tag);
        return tag.apply(item, value, sender, plugin.getCoreConfig().getGiveSendWithErrors());
    }

    private void sendHelp(CommandSender sender) {
        TextUtil.send(sender, ColorScheme.EMPHASIS + "<bold>=== DGive Command Help ===");
        TextUtil.send(
                sender,
                ColorScheme.WARNING
                        + "Usage: "
                        + ColorScheme.HIGHLIGHT
                        + "/dgive <player> <item> [amount] [flags...]");
        TextUtil.send(sender, "");
        TextUtil.send(sender, ColorScheme.EMPHASIS + "Flags:");
        TextUtil.send(
                sender,
                ColorScheme.WARNING
                        + "-name <name> "
                        + ColorScheme.INFO
                        + "- Set item display name (supports color codes)");
        TextUtil.send(
                sender,
                ColorScheme.WARNING
                        + "-lore <line|line2> "
                        + ColorScheme.INFO
                        + "- Set item lore (use | for new lines)");
        TextUtil.send(
                sender,
                ColorScheme.WARNING
                        + "-cmd <number> "
                        + ColorScheme.INFO
                        + "- Set custom model data");
        TextUtil.send(
                sender,
                ColorScheme.WARNING
                        + "-ench <enchant:level> "
                        + ColorScheme.INFO
                        + "- Add enchantment");
        TextUtil.send(
                sender,
                ColorScheme.WARNING + "-flag <itemflag> " + ColorScheme.INFO + "- Add item flag");
        TextUtil.send(
                sender,
                ColorScheme.WARNING
                        + "-color <#hex> "
                        + ColorScheme.INFO
                        + "- Set leather armor color");
        TextUtil.send(
                sender,
                ColorScheme.WARNING
                        + "-silent "
                        + ColorScheme.INFO
                        + "- Don't send receive message to player");
        TextUtil.send(sender, "");
        TextUtil.send(sender, ColorScheme.EMPHASIS + "Example:");
        TextUtil.send(
                sender,
                ColorScheme.HIGHLIGHT
                        + "/dgive Steve diamond_sword 1 -name &bFrostbrand -ench sharpness:5");
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Player names
            completions.add("help");
            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        } else if (args.length == 2) {
            // Materials
            for (Material m : Material.values()) {
                completions.add(m.name().toLowerCase());
            }
        } else if (args.length == 3) {
            // Amount or flag
            completions.add("1");
            try {
                Material m = Material.valueOf(args[1].toUpperCase());
                for (int i = 2; i <= Math.min(m.getMaxStackSize(), 64); i++) {
                    completions.add(String.valueOf(i));
                }
            } catch (IllegalArgumentException ignored) {
            }
            addFlagCompletions(completions);
        } else {
            // Check if we're completing a flag value or a new flag
            String prevArg = args[args.length - 2];

            if (prevArg.equalsIgnoreCase("-ench")) {
                // Enchantment completions
                for (Enchantment e : Enchantment.values()) {
                    completions.add(e.getKey().getKey() + ":1");
                }
            } else if (prevArg.equalsIgnoreCase("-flag")) {
                // Item flag completions
                for (ItemFlag f : ItemFlag.values()) {
                    completions.add(f.name().toLowerCase());
                }
            } else if (prevArg.equalsIgnoreCase("-color")) {
                completions.add("#FF0000");
                completions.add("#00FF00");
                completions.add("#0000FF");
            } else {
                addFlagCompletions(completions);
            }
        }

        // Filter based on current input
        String current = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(current))
                .limit(50)
                .toList();
    }

    private void addFlagCompletions(List<String> completions) {
        completions.add("-name");
        completions.add("-lore");
        completions.add("-cmd");
        completions.add("-ench");
        completions.add("-flag");
        completions.add("-color");
        completions.add("-silent");
    }

    /** Item modification tags */
    private enum ItemTag {
        NAME {
            @Override
            public boolean apply(
                    ItemStack item, String value, CommandSender sender, boolean sendWithErrors) {
                if (value.isEmpty()) {
                    if (sendWithErrors) {
                        TextUtil.send(sender, ColorScheme.ERROR + "Name cannot be empty.");
                    }
                    return !sendWithErrors;
                }
                ItemMeta meta = item.getItemMeta();
                if (meta == null) return true;
                meta.setDisplayName(LegacyText.colorize(value));
                item.setItemMeta(meta);
                return true;
            }
        },
        LORE {
            @Override
            public boolean apply(
                    ItemStack item, String value, CommandSender sender, boolean sendWithErrors) {
                if (value.isEmpty()) {
                    if (sendWithErrors) {
                        TextUtil.send(sender, ColorScheme.ERROR + "Lore cannot be empty.");
                    }
                    return !sendWithErrors;
                }
                ItemMeta meta = item.getItemMeta();
                if (meta == null) return true;
                List<String> lore =
                        Stream.of(value.split("\\|"))
                                .map(String::trim)
                                .map(LegacyText::colorize)
                                .toList();
                meta.setLore(lore);
                item.setItemMeta(meta);
                return true;
            }
        },
        CMD {
            @Override
            public boolean apply(
                    ItemStack item, String value, CommandSender sender, boolean sendWithErrors) {
                if (value.isEmpty()) {
                    if (sendWithErrors) {
                        TextUtil.send(
                                sender, ColorScheme.ERROR + "Custom model data cannot be empty.");
                    }
                    return !sendWithErrors;
                }
                try {
                    int cmd = Integer.parseInt(value.trim());
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        meta.setCustomModelData(cmd);
                        item.setItemMeta(meta);
                    }
                    return true;
                } catch (NumberFormatException e) {
                    if (sendWithErrors) {
                        TextUtil.send(
                                sender, ColorScheme.ERROR + "Custom model data must be a number.");
                    }
                    return !sendWithErrors;
                }
            }
        },
        ENCH {
            @Override
            public boolean apply(
                    ItemStack item, String value, CommandSender sender, boolean sendWithErrors) {
                if (value.isEmpty()) {
                    if (sendWithErrors) {
                        TextUtil.send(sender, ColorScheme.ERROR + "Enchantment cannot be empty.");
                    }
                    return !sendWithErrors;
                }
                String[] parts = value.replace(" ", "").split(":");
                String enchName = parts[0].toLowerCase();
                int level = 1;

                if (parts.length > 1) {
                    try {
                        level = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException e) {
                        if (sendWithErrors) {
                            TextUtil.send(
                                    sender,
                                    ColorScheme.ERROR + "Enchantment level must be a number.");
                        }
                        return !sendWithErrors;
                    }
                }

                Enchantment ench = Enchantment.getByKey(NamespacedKey.minecraft(enchName));
                if (ench == null) {
                    if (sendWithErrors) {
                        TextUtil.send(
                                sender, ColorScheme.ERROR + "Unknown enchantment: " + enchName);
                    }
                    return !sendWithErrors;
                }

                item.addUnsafeEnchantment(ench, level);
                return true;
            }
        },
        FLAG {
            @Override
            public boolean apply(
                    ItemStack item, String value, CommandSender sender, boolean sendWithErrors) {
                if (value.isEmpty()) {
                    if (sendWithErrors) {
                        TextUtil.send(sender, ColorScheme.ERROR + "Item flag cannot be empty.");
                    }
                    return !sendWithErrors;
                }
                try {
                    ItemFlag flag = ItemFlag.valueOf(value.toUpperCase().replace(" ", "_"));
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        meta.addItemFlags(flag);
                        item.setItemMeta(meta);
                    }
                    return true;
                } catch (IllegalArgumentException e) {
                    if (sendWithErrors) {
                        TextUtil.send(sender, ColorScheme.ERROR + "Unknown item flag: " + value);
                    }
                    return !sendWithErrors;
                }
            }
        },
        COLOR {
            @Override
            public boolean apply(
                    ItemStack item, String value, CommandSender sender, boolean sendWithErrors) {
                if (value.isEmpty()) {
                    if (sendWithErrors) {
                        TextUtil.send(sender, ColorScheme.ERROR + "Color cannot be empty.");
                    }
                    return !sendWithErrors;
                }
                String hex = value.replace(" ", "").replace("#", "");
                if (hex.length() != 6) {
                    if (sendWithErrors) {
                        TextUtil.send(
                                sender,
                                ColorScheme.ERROR
                                        + "Color must be a 6-character hex value (e.g., #FF0000).");
                    }
                    return !sendWithErrors;
                }

                ItemMeta meta = item.getItemMeta();
                if (!(meta instanceof LeatherArmorMeta leatherMeta)) {
                    if (sendWithErrors) {
                        TextUtil.send(
                                sender,
                                ColorScheme.WARNING + "Note: Color only applies to leather armor.");
                    }
                    return true;
                }

                try {
                    int rgb = Integer.parseInt(hex, 16);
                    leatherMeta.setColor(Color.fromRGB(rgb));
                    item.setItemMeta(leatherMeta);
                    return true;
                } catch (NumberFormatException e) {
                    if (sendWithErrors) {
                        TextUtil.send(sender, ColorScheme.ERROR + "Invalid hex color: " + value);
                    }
                    return !sendWithErrors;
                }
            }
        },
        SILENT {
            @Override
            public boolean apply(
                    ItemStack item, String value, CommandSender sender, boolean sendWithErrors) {
                // Silent flag just marks that we shouldn't send messages
                return true;
            }
        };

        public abstract boolean apply(
                ItemStack item, String value, CommandSender sender, boolean sendWithErrors);
    }
}
