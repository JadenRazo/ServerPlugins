package net.serverplugins.commands.commands;

import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.ui.ResourcePackIcons;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.commands.ServerCommands;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class RtpMenuCommand implements CommandExecutor {

    private final ServerCommands plugin;

    public RtpMenuCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!player.hasPermission("servercommands.rtp")) {
            CommonMessages.NO_PERMISSION.send(player);
            return true;
        }

        openRtpMenu(player);
        return true;
    }

    private void openRtpMenu(Player player) {
        String title =
                ResourcePackIcons.MenuTitles.createFullscreenTitle(
                        ResourcePackIcons.MenuTitles.RTP_MENU);
        Gui menu = new Gui(plugin, title, 27);

        // Overworld Button (Slot 11)
        ItemStack overworldItem =
                new ItemBuilder(Material.GRASS_BLOCK)
                        .name("<gradient:#32CD32:#228B22>Overworld RTP</gradient>")
                        .lore(
                                "",
                                "<gray>Teleport to a random location",
                                "<gray>in the overworld",
                                "",
                                "<yellow>Cost: <white>500 coins")
                        .build();
        menu.setItem(
                11,
                new GuiItem(
                        overworldItem,
                        event -> {
                            player.closeInventory();
                            player.performCommand("rtp playworld");
                        }));

        // Nether Button (Slot 13)
        ItemStack netherItem =
                new ItemBuilder(Material.NETHERRACK)
                        .name("<gradient:#FF4500:#8B0000>Nether RTP</gradient>")
                        .lore(
                                "",
                                "<gray>Teleport to a random location",
                                "<gray>in the nether dimension",
                                "",
                                "<yellow>Cost: <white>750 coins")
                        .build();
        menu.setItem(
                13,
                new GuiItem(
                        netherItem,
                        event -> {
                            player.closeInventory();
                            player.performCommand("rtp playworld_nether");
                        }));

        // End Button (Slot 15)
        ItemStack endItem =
                new ItemBuilder(Material.END_STONE)
                        .name("<gradient:#9B59B6:#8E44AD>End RTP</gradient>")
                        .lore(
                                "",
                                "<gray>Teleport to a random location",
                                "<gray>in the end dimension",
                                "",
                                "<yellow>Cost: <white>2500 coins")
                        .build();
        menu.setItem(
                15,
                new GuiItem(
                        endItem,
                        event -> {
                            player.closeInventory();
                            player.performCommand("rtp playworld_the_end");
                        }));

        // Fill empty slots with decorative glass panes
        menu.fillEmpty(new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
        menu.open(player);
    }
}
