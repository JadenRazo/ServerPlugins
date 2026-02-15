package net.serverplugins.afk.gui;

import net.serverplugins.afk.ServerAFK;
import net.serverplugins.afk.commands.AfkCommand;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class AdminMainGui extends Gui {

    private final ServerAFK plugin;
    private final AfkCommand command;

    public AdminMainGui(ServerAFK plugin, Player player, AfkCommand command) {
        super(plugin, player, "AFK Zone Manager", 27);
        this.plugin = plugin;
        this.command = command;
    }

    @Override
    protected void initializeItems() {
        // Fill border with glass
        ItemStack glass = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        fillBorder(new GuiItem(glass));

        // Slot 10: Manage Zones
        ItemStack zonesItem =
                new ItemBuilder(Material.CHEST)
                        .name("<gold>Manage AFK Zones")
                        .lore(
                                "",
                                "<gray>View, create, edit and delete",
                                "<gray>AFK reward zones.",
                                "",
                                "<gray>Active zones: <yellow>"
                                        + plugin.getZoneManager().getEnabledZones().size(),
                                "",
                                "<yellow>Click to open zone list")
                        .build();
        setItem(
                10,
                new GuiItem(
                        zonesItem,
                        e -> {
                            viewer.closeInventory();
                            new ZoneListGui(plugin, viewer, command).open();
                        }));

        // Slot 12: Create Zone
        Location c1 = command.getCorner1(viewer.getUniqueId());
        Location c2 = command.getCorner2(viewer.getUniqueId());
        boolean hasSelection = c1 != null && c2 != null;

        String c1Status =
                c1 != null
                        ? "<green>Set <gray>("
                                + c1.getBlockX()
                                + ", "
                                + c1.getBlockY()
                                + ", "
                                + c1.getBlockZ()
                                + ")"
                        : "<red>Not set";
        String c2Status =
                c2 != null
                        ? "<green>Set <gray>("
                                + c2.getBlockX()
                                + ", "
                                + c2.getBlockY()
                                + ", "
                                + c2.getBlockZ()
                                + ")"
                        : "<red>Not set";

        ItemBuilder createBuilder =
                new ItemBuilder(hasSelection ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE)
                        .name("<green>Create New Zone")
                        .lore(
                                "",
                                "<gray>Create a new AFK zone using",
                                "<gray>your current selection.",
                                "",
                                "<gray>Corner 1: " + c1Status,
                                "<gray>Corner 2: " + c2Status,
                                "");

        if (hasSelection) {
            createBuilder.lore("<yellow>Click to create zone");
        } else {
            createBuilder.lore("<red>Set both corners first!");
            createBuilder.lore("<gray>Use /wa p1 and /wa p2");
        }

        setItem(
                12,
                new GuiItem(
                        createBuilder.build(),
                        e -> {
                            if (!hasSelection) {
                                TextUtil.send(
                                        viewer,
                                        plugin.getAfkConfig().getMessage("selection-incomplete"));
                                return;
                            }

                            // Prompt for zone name
                            viewer.closeInventory();
                            TextUtil.send(
                                    viewer,
                                    plugin.getAfkConfig().getPrefix()
                                            + "<yellow>Enter a name for the new zone in chat:");

                            // Use chat input listener pattern
                            plugin.getServer()
                                    .getScheduler()
                                    .runTaskLater(
                                            plugin,
                                            () -> {
                                                new ZoneNameInputGui(
                                                                plugin, viewer, command, c1, c2)
                                                        .open();
                                            },
                                            1L);
                        }));

        // Slot 14: Reload
        ItemStack reloadItem =
                new ItemBuilder(Material.REDSTONE)
                        .name("<red>Reload Configuration")
                        .lore(
                                "",
                                "<gray>Reload the plugin configuration",
                                "<gray>and zone data from disk.",
                                "",
                                "<yellow>Click to reload")
                        .build();
        setItem(
                14,
                new GuiItem(
                        reloadItem,
                        e -> {
                            plugin.reload();
                            TextUtil.send(
                                    viewer,
                                    plugin.getAfkConfig().getPrefix()
                                            + "<green>Configuration reloaded!");
                            viewer.closeInventory();
                            new AdminMainGui(plugin, viewer, command).open();
                        }));

        // Slot 16: Help
        int zoneCount = plugin.getZoneManager().getAllZones().size();
        int activePlayers = plugin.getPlayerTracker().getActiveSessionCount();

        ItemStack helpItem =
                new ItemBuilder(Material.BOOK)
                        .name("<aqua>Help & Information")
                        .lore(
                                "",
                                "<gray>Commands:",
                                "<white>/wa <gray>- Open this menu",
                                "<white>/wa p1 <gray>- Set corner 1",
                                "<white>/wa p2 <gray>- Set corner 2",
                                "<white>/wa qc <name> <gray>- Quick create zone",
                                "<white>/wa delete <name> <gray>- Delete zone",
                                "<white>/wa reload <gray>- Reload config",
                                "",
                                "<gray>Total Zones: <yellow>" + zoneCount,
                                "<gray>Players in AFK: <yellow>" + activePlayers)
                        .build();
        setItem(16, new GuiItem(helpItem));
    }
}
