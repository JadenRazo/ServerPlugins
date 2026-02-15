package net.serverplugins.admin.gui;

import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class AdminMenuGui extends Gui {

    private final ServerAdmin plugin;

    public AdminMenuGui(ServerAdmin plugin, Player player) {
        super(plugin, player, "<dark_red><bold>Admin Panel", 54);
        this.plugin = plugin;
    }

    @Override
    protected void initializeItems() {
        // Fill background
        ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) {
            setItem(i, new GuiItem(filler));
        }

        // Row 1: Vanish & Spectate tools
        setupVanishItem();
        setupSpectateItem();
        setupFreecamItem();
        setupPovItem();

        // Row 2: Player management
        setupFreezeItem();
        setupInvseeItem();
        setupEcseeItem();
        setupAltsItem();

        // Row 3: Monitoring
        setupXrayAlertsItem();
        setupXrayCheckItem();
        setupStaffChatItem();

        // Row 4: Online players
        setupOnlinePlayersItem();

        // Bottom: Close button
        setupCloseButton();
    }

    private void setupVanishItem() {
        boolean isVanished = plugin.getVanishManager().isVanished(viewer);
        String status = isVanished ? "<green>ENABLED" : "<red>DISABLED";

        ItemStack item =
                new ItemBuilder(Material.ENDER_EYE)
                        .name("<aqua>Vanish")
                        .lore(
                                "",
                                "<gray>Toggle your visibility to players",
                                "",
                                "<gray>Status: " + status,
                                "",
                                "<yellow>Left-click: <white>Toggle vanish",
                                "<yellow>Right-click: <white>Staff-only vanish")
                        .glow(isVanished)
                        .build();

        setItem(
                10,
                GuiItem.withContext(
                        item,
                        ctx -> {
                            viewer.closeInventory();
                            if (ctx.isRightClick()) {
                                viewer.performCommand("vanish staff");
                            } else {
                                viewer.performCommand("vanish");
                            }
                        }));
    }

    private void setupSpectateItem() {
        boolean isSpectating = plugin.getSpectateManager().isSpectating(viewer);

        ItemStack item =
                new ItemBuilder(Material.SPYGLASS)
                        .name("<light_purple>Spectate Player")
                        .lore(
                                "",
                                "<gray>Follow and watch a player",
                                "",
                                isSpectating
                                        ? "<green>Currently spectating"
                                        : "<gray>Not spectating",
                                "",
                                "<yellow>Click to select a player")
                        .glow(isSpectating)
                        .build();

        setItem(
                12,
                new GuiItem(
                        item,
                        e -> {
                            viewer.closeInventory();
                            new PlayerSelectGui(
                                            plugin,
                                            viewer,
                                            "Spectate Player",
                                            target -> {
                                                viewer.performCommand(
                                                        "spectate " + target.getName());
                                            })
                                    .open();
                        }));
    }

    private void setupFreecamItem() {
        ItemStack item =
                new ItemBuilder(Material.PHANTOM_MEMBRANE)
                        .name("<white>Freecam")
                        .lore(
                                "",
                                "<gray>Enter free camera mode",
                                "<gray>Fly around invisibly",
                                "",
                                "<yellow>Click to toggle")
                        .build();

        setItem(
                14,
                new GuiItem(
                        item,
                        e -> {
                            viewer.closeInventory();
                            viewer.performCommand("freecam");
                        }));
    }

    private void setupPovItem() {
        ItemStack item =
                new ItemBuilder(Material.PLAYER_HEAD)
                        .name("<gold>POV Mode")
                        .lore(
                                "",
                                "<gray>View from a player's perspective",
                                "<gray>See what they see",
                                "",
                                "<yellow>Click to select a player")
                        .build();

        setItem(
                16,
                new GuiItem(
                        item,
                        e -> {
                            viewer.closeInventory();
                            new PlayerSelectGui(
                                            plugin,
                                            viewer,
                                            "POV Mode",
                                            target -> {
                                                viewer.performCommand("pov " + target.getName());
                                            })
                                    .open();
                        }));
    }

    private void setupFreezeItem() {
        ItemStack item =
                new ItemBuilder(Material.PACKED_ICE)
                        .name("<aqua>Freeze Player")
                        .lore(
                                "",
                                "<gray>Freeze a player for inspection",
                                "<gray>They cannot move or interact",
                                "",
                                "<yellow>Click to select a player")
                        .build();

        setItem(
                28,
                new GuiItem(
                        item,
                        e -> {
                            viewer.closeInventory();
                            new PlayerSelectGui(
                                            plugin,
                                            viewer,
                                            "Freeze Player",
                                            target -> {
                                                if (plugin.getFreezeManager().isFrozen(target)) {
                                                    viewer.performCommand(
                                                            "unfreeze " + target.getName());
                                                } else {
                                                    viewer.performCommand(
                                                            "freeze " + target.getName());
                                                }
                                            })
                                    .open();
                        }));
    }

    private void setupInvseeItem() {
        ItemStack item =
                new ItemBuilder(Material.CHEST)
                        .name("<yellow>View Inventory")
                        .lore(
                                "",
                                "<gray>View a player's inventory",
                                "<gray>Modify items if permitted",
                                "",
                                "<yellow>Click to select a player")
                        .build();

        setItem(
                30,
                new GuiItem(
                        item,
                        e -> {
                            viewer.closeInventory();
                            new PlayerSelectGui(
                                            plugin,
                                            viewer,
                                            "View Inventory",
                                            target -> {
                                                viewer.performCommand("invsee " + target.getName());
                                            })
                                    .open();
                        }));
    }

    private void setupEcseeItem() {
        ItemStack item =
                new ItemBuilder(Material.ENDER_CHEST)
                        .name("<dark_purple>View Ender Chest")
                        .lore(
                                "",
                                "<gray>View a player's ender chest",
                                "<gray>Modify items if permitted",
                                "",
                                "<yellow>Click to select a player")
                        .build();

        setItem(
                32,
                new GuiItem(
                        item,
                        e -> {
                            viewer.closeInventory();
                            new PlayerSelectGui(
                                            plugin,
                                            viewer,
                                            "View Ender Chest",
                                            target -> {
                                                viewer.performCommand("ecsee " + target.getName());
                                            })
                                    .open();
                        }));
    }

    private void setupAltsItem() {
        ItemStack item =
                new ItemBuilder(Material.SKELETON_SKULL)
                        .name("<red>Check Alts")
                        .lore(
                                "",
                                "<gray>Check a player's alt accounts",
                                "<gray>Based on IP matching",
                                "",
                                "<yellow>Click to select a player")
                        .build();

        setItem(
                34,
                new GuiItem(
                        item,
                        e -> {
                            viewer.closeInventory();
                            new PlayerSelectGui(
                                            plugin,
                                            viewer,
                                            "Check Alts",
                                            target -> {
                                                viewer.performCommand("alts " + target.getName());
                                            })
                                    .open();
                        }));
    }

    private void setupXrayAlertsItem() {
        boolean alertsEnabled =
                plugin.getXrayManager() != null && plugin.getXrayManager().hasAlertsEnabled(viewer);

        ItemStack item =
                new ItemBuilder(Material.DIAMOND_ORE)
                        .name("<aqua>X-Ray Alerts")
                        .lore(
                                "",
                                "<gray>Toggle suspicious mining alerts",
                                "",
                                "<gray>Status: "
                                        + (alertsEnabled ? "<green>ENABLED" : "<red>DISABLED"),
                                "",
                                "<yellow>Click to toggle")
                        .glow(alertsEnabled)
                        .build();

        setItem(
                38,
                new GuiItem(
                        item,
                        e -> {
                            viewer.closeInventory();
                            viewer.performCommand("xrayalerts");
                        }));
    }

    private void setupXrayCheckItem() {
        ItemStack item =
                new ItemBuilder(Material.IRON_PICKAXE)
                        .name("<gold>X-Ray Check")
                        .lore(
                                "",
                                "<gray>Check a player's mining stats",
                                "<gray>Analyze suspicious patterns",
                                "",
                                "<yellow>Click to select a player")
                        .build();

        setItem(
                40,
                new GuiItem(
                        item,
                        e -> {
                            viewer.closeInventory();
                            new PlayerSelectGui(
                                            plugin,
                                            viewer,
                                            "X-Ray Check",
                                            target -> {
                                                viewer.performCommand(
                                                        "xraycheck " + target.getName());
                                            })
                                    .open();
                        }));
    }

    private void setupStaffChatItem() {
        boolean scEnabled = plugin.getStaffChatManager().isToggled(viewer);

        ItemStack item =
                new ItemBuilder(Material.WRITABLE_BOOK)
                        .name("<green>Staff Chat")
                        .lore(
                                "",
                                "<gray>Toggle staff-only chat mode",
                                "",
                                "<gray>Status: "
                                        + (scEnabled ? "<green>TOGGLED ON" : "<gray>Normal chat"),
                                "",
                                "<yellow>Click to toggle")
                        .glow(scEnabled)
                        .build();

        setItem(
                42,
                new GuiItem(
                        item,
                        e -> {
                            viewer.closeInventory();
                            viewer.performCommand("sctoggle");
                        }));
    }

    private void setupOnlinePlayersItem() {
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();

        ItemStack item =
                new ItemBuilder(Material.PLAYER_HEAD)
                        .name("<white>Online Players")
                        .lore(
                                "",
                                "<gray>Players: <green>" + online + "<gray>/<white>" + max,
                                "",
                                "<yellow>Click to view player list")
                        .build();

        setItem(
                22,
                new GuiItem(
                        item,
                        e -> {
                            viewer.closeInventory();
                            new PlayerSelectGui(
                                            plugin,
                                            viewer,
                                            "Online Players",
                                            target -> {
                                                // Re-open admin menu after viewing
                                                new AdminMenuGui(plugin, viewer).open();
                                            })
                                    .open();
                        }));
    }

    private void setupCloseButton() {
        ItemStack item =
                new ItemBuilder(Material.BARRIER)
                        .name("<red>Close")
                        .lore("<gray>Close this menu")
                        .build();

        setItem(49, new GuiItem(item, e -> viewer.closeInventory()));
    }
}
