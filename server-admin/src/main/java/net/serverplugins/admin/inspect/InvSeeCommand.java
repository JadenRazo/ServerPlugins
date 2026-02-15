package net.serverplugins.admin.inspect;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.messages.MessageBuilder;
import net.serverplugins.api.messages.Placeholder;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class InvSeeCommand implements CommandExecutor, TabCompleter {

    private final ServerAdmin plugin;

    public InvSeeCommand(ServerAdmin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!player.hasPermission("serveradmin.invsee")) {
            CommonMessages.NO_PERMISSION.send(player);
            return true;
        }

        if (args.length < 1) {
            CommonMessages.INVALID_USAGE.send(player, Placeholder.of("usage", "/invsee <player>"));
            return true;
        }

        // Try to find online player first
        Player target = Bukkit.getPlayer(args[0]);

        if (target != null) {
            openOnlineInventory(player, target);
        } else {
            // Check if offline inspection is supported
            if (!plugin.getInspectManager().isOfflineSupported()) {
                plugin.getAdminConfig()
                        .getMessenger()
                        .sendError(
                                player,
                                "Player is not online. Offline inspection requires NBTAPI plugin.");
                return true;
            }

            // Notify user that we're searching
            plugin.getAdminConfig()
                    .getMessenger()
                    .sendWarning(player, "Searching for offline player...");

            // Perform blocking offline player lookup asynchronously
            String targetName = args[0];
            Bukkit.getScheduler()
                    .runTaskAsynchronously(
                            plugin,
                            () -> {
                                @SuppressWarnings("deprecation")
                                OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
                                boolean hasPlayed = offlineTarget.hasPlayedBefore();

                                // Return to main thread to show results
                                Bukkit.getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    // Check if viewer is still online
                                                    if (!player.isOnline()) {
                                                        return;
                                                    }

                                                    if (hasPlayed) {
                                                        openOfflineInventory(player, offlineTarget);
                                                    } else {
                                                        CommonMessages.PLAYER_NOT_FOUND.send(
                                                                player);
                                                    }
                                                });
                            });
        }

        return true;
    }

    private void openOnlineInventory(Player viewer, Player target) {
        // Create a custom inventory with armor slots displayed
        Component title = TextUtil.parse("<dark_gray>Inv: <white>" + target.getName());
        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Copy main inventory (slots 0-35)
        ItemStack[] contents = target.getInventory().getContents();
        for (int i = 0; i < Math.min(36, contents.length); i++) {
            inv.setItem(i, contents[i]);
        }

        // Add armor in row 5 (slots 36-39)
        ItemStack[] armor = target.getInventory().getArmorContents();
        inv.setItem(45, armor[3]); // Helmet
        inv.setItem(46, armor[2]); // Chestplate
        inv.setItem(47, armor[1]); // Leggings
        inv.setItem(48, armor[0]); // Boots

        // Offhand in slot 53
        inv.setItem(53, target.getInventory().getItemInOffHand());

        // Register for live sync
        plugin.getInspectManager().openInvSee(viewer, target, inv);

        viewer.openInventory(inv);
        MessageBuilder.create()
                .prefix(plugin.getAdminConfig().getMessenger().getPrefix())
                .success("Viewing ")
                .highlight(target.getName())
                .success("'s inventory")
                .send(viewer);
    }

    private void openOfflineInventory(Player viewer, OfflinePlayer target) {
        plugin.getAdminConfig()
                .getMessenger()
                .sendWarning(viewer, "Loading offline player inventory...");

        UUID uuid = target.getUniqueId();
        String targetName = target.getName() != null ? target.getName() : uuid.toString();
        boolean canEdit = viewer.hasPermission("serveradmin.invsee.modify");

        plugin.getInspectManager()
                .getOfflineHandler()
                .loadInventoryAsync(uuid)
                .thenAccept(
                        data -> {
                            Bukkit.getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                if (data == null) {
                                                    plugin.getAdminConfig()
                                                            .getMessenger()
                                                            .sendError(
                                                                    viewer,
                                                                    "Failed to load offline player data for "
                                                                            + targetName);
                                                    return;
                                                }

                                                // Create display inventory with "(Offline)" in
                                                // title
                                                Component title =
                                                        TextUtil.parse(
                                                                "<dark_gray>Inv: <white>"
                                                                        + targetName
                                                                        + " <gray>(Offline)");
                                                Inventory inv =
                                                        Bukkit.createInventory(null, 54, title);

                                                // Populate main inventory (slots 0-35)
                                                if (data.mainInventory() != null) {
                                                    for (int i = 0;
                                                            i
                                                                    < Math.min(
                                                                            36,
                                                                            data.mainInventory()
                                                                                    .length);
                                                            i++) {
                                                        inv.setItem(i, data.mainInventory()[i]);
                                                    }
                                                }

                                                // Add separator glass panes
                                                addSeparators(inv);

                                                // Populate armor (slots 45-48)
                                                // armor[3]=helmet, armor[2]=chest, armor[1]=legs,
                                                // armor[0]=boots
                                                if (data.armorContents() != null) {
                                                    inv.setItem(
                                                            45, data.armorContents()[3]); // Helmet
                                                    inv.setItem(
                                                            46,
                                                            data.armorContents()[2]); // Chestplate
                                                    inv.setItem(
                                                            47,
                                                            data.armorContents()[1]); // Leggings
                                                    inv.setItem(
                                                            48, data.armorContents()[0]); // Boots
                                                }

                                                // Populate offhand (slot 53)
                                                inv.setItem(53, data.offhand());

                                                // Register session
                                                plugin.getInspectManager()
                                                        .openOfflineInvSee(
                                                                viewer,
                                                                uuid,
                                                                targetName,
                                                                inv,
                                                                canEdit);

                                                viewer.openInventory(inv);
                                                MessageBuilder builder =
                                                        MessageBuilder.create()
                                                                .prefix(
                                                                        plugin.getAdminConfig()
                                                                                .getMessenger()
                                                                                .getPrefix())
                                                                .success("Viewing ")
                                                                .highlight(targetName)
                                                                .success("'s offline inventory");
                                                if (!canEdit) {
                                                    builder.info(" (read-only)");
                                                }
                                                builder.send(viewer);
                                            });
                        })
                .exceptionally(
                        ex -> {
                            Bukkit.getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                plugin.getAdminConfig()
                                                        .getMessenger()
                                                        .sendError(
                                                                viewer,
                                                                "Error loading offline inventory: "
                                                                        + ex.getMessage());
                                            });
                            return null;
                        });
    }

    private void addSeparators(Inventory inv) {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.parse(" "));
            glass.setItemMeta(meta);
        }

        // Fill separator rows (36-44 and 49-52)
        for (int i = 36; i <= 44; i++) {
            inv.setItem(i, glass);
        }
        for (int i = 49; i <= 52; i++) {
            inv.setItem(i, glass);
        }
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partial)) {
                    completions.add(player.getName());
                }
            }
        }

        return completions;
    }
}
