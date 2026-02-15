package net.serverplugins.commands.admin;

import java.util.ArrayList;
import java.util.List;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.commands.ServerCommands;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Manages all admin menu sub-menus and their logic. Handles player selection, action confirmation,
 * and command execution.
 */
public class AdminMenuManager {

    private final ServerCommands plugin;

    public AdminMenuManager(ServerCommands plugin) {
        this.plugin = plugin;
    }

    // ==================== SHUTDOWN & RESTART ====================

    public void openShutdownMenu(Player player) {
        Gui menu = new Gui(plugin, "<gradient:#e74c3c:#c0392b>⚠ Server Shutdown</gradient>", 27);

        // Immediate shutdown
        menu.setItem(
                11,
                new GuiItem(
                        new ItemBuilder(Material.REDSTONE_BLOCK)
                                .name("<gradient:#ff0000:#8b0000>⚡ Shutdown Now</gradient>")
                                .lore(
                                        "",
                                        "<gray>Immediately shutdown the server",
                                        "<gray>with a default message",
                                        "",
                                        "<red>⚠ No countdown",
                                        "<red>⚠ Kicks all players",
                                        "",
                                        "<yellow>Click to confirm")
                                .build(),
                        event -> openShutdownConfirm(player, 0)));

        // 30 second countdown
        menu.setItem(
                13,
                new GuiItem(
                        new ItemBuilder(Material.CLOCK)
                                .name("<gradient:#e67e22:#d35400>⏱ 30 Second Countdown</gradient>")
                                .lore(
                                        "",
                                        "<gray>Shutdown with a 30 second warning",
                                        "",
                                        "<gold>⚡ Broadcast warnings",
                                        "<gold>⚡ Custom message",
                                        "<gold>⚡ RGB color picker",
                                        "",
                                        "<yellow>Click to configure")
                                .build(),
                        event -> openShutdownColorPicker(player, 30)));

        // 5 minute countdown
        menu.setItem(
                15,
                new GuiItem(
                        new ItemBuilder(Material.CLOCK)
                                .name("<gradient:#3498db:#2980b9>⏱ 5 Minute Countdown</gradient>")
                                .lore(
                                        "",
                                        "<gray>Shutdown with a 5 minute warning",
                                        "",
                                        "<aqua>⚡ Players can finish tasks",
                                        "<aqua>⚡ Custom message",
                                        "<aqua>⚡ RGB color picker",
                                        "",
                                        "<yellow>Click to configure")
                                .build(),
                        event -> openShutdownColorPicker(player, 300)));

        // Back button
        menu.setItem(
                22,
                new GuiItem(
                        new ItemBuilder(Material.ARROW)
                                .name("<yellow>← Back to Admin Menu")
                                .build(),
                        event -> {
                            player.closeInventory();
                            player.performCommand("admin");
                        }));

        menu.fillEmpty(new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
        menu.open(player);
    }

    private void openShutdownConfirm(Player player, int seconds) {
        Gui menu = new Gui(plugin, "<gradient:#e74c3c:#c0392b>⚠ Confirm Shutdown</gradient>", 27);

        menu.setItem(
                11,
                new GuiItem(
                        new ItemBuilder(Material.EMERALD_BLOCK)
                                .name("<green>✓ Confirm Shutdown")
                                .lore(
                                        "",
                                        "<gray>Confirm server shutdown",
                                        seconds > 0
                                                ? "<gray>Countdown: <yellow>" + seconds + " seconds"
                                                : "<gray>Immediate shutdown",
                                        "",
                                        "<yellow>Click to confirm")
                                .build(),
                        event -> {
                            player.closeInventory();
                            if (seconds > 0) {
                                TextUtil.broadcastRaw(
                                        "<red><bold>⚠ SERVER SHUTTING DOWN IN "
                                                + seconds
                                                + " SECONDS!</bold></red>");
                            }
                            Bukkit.getScheduler()
                                    .runTaskLater(
                                            plugin,
                                            () -> {
                                                Bukkit.shutdown();
                                            },
                                            seconds * 20L);
                        }));

        menu.setItem(
                15,
                new GuiItem(
                        new ItemBuilder(Material.REDSTONE_BLOCK).name("<red>✗ Cancel").build(),
                        event -> openShutdownMenu(player)));

        menu.fillEmpty(new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
        menu.open(player);
    }

    private void openShutdownColorPicker(Player player, int seconds) {
        // For now, proceed with default color
        // TODO: Implement full color picker in future update
        openShutdownConfirm(player, seconds);
    }

    public void openRestartMenu(Player player) {
        TextUtil.sendWarning(player, "Restart feature coming soon!");
        TextUtil.send(
                player,
                "<gray>Use <white>/stop <gray>and your server manager's restart script for now.");
    }

    // ==================== MODERATION MENUS ====================

    public void openBanMenu(Player player) {
        openPlayerSelector(
                player,
                "Ban Player",
                Material.IRON_DOOR,
                selectedPlayer -> {
                    player.closeInventory();
                    Bukkit.getScheduler()
                            .runTask(
                                    plugin,
                                    () ->
                                            player.performCommand(
                                                    "ban "
                                                            + selectedPlayer.getName()
                                                            + " Banned by admin"));
                });
    }

    public void openTempBanMenu(Player player) {
        openPlayerSelector(
                player,
                "Temp Ban Player",
                Material.CLOCK,
                selectedPlayer -> {
                    player.closeInventory();
                    Bukkit.getScheduler()
                            .runTask(
                                    plugin,
                                    () ->
                                            player.performCommand(
                                                    "tempban "
                                                            + selectedPlayer.getName()
                                                            + " 1d Temp banned by admin"));
                });
    }

    public void openKickMenu(Player player) {
        openPlayerSelector(
                player,
                "Kick Player",
                Material.LEATHER_BOOTS,
                selectedPlayer -> {
                    player.closeInventory();
                    Bukkit.getScheduler()
                            .runTask(
                                    plugin,
                                    () ->
                                            player.performCommand(
                                                    "kick "
                                                            + selectedPlayer.getName()
                                                            + " Kicked by admin"));
                });
    }

    public void openMuteMenu(Player player) {
        openPlayerSelector(
                player,
                "Mute Player",
                Material.BARRIER,
                selectedPlayer -> {
                    player.closeInventory();
                    Bukkit.getScheduler()
                            .runTask(
                                    plugin,
                                    () ->
                                            player.performCommand(
                                                    "mute " + selectedPlayer.getName()));
                });
    }

    public void openFreezeMenu(Player player) {
        openPlayerSelector(
                player,
                "Freeze Player",
                Material.ICE,
                selectedPlayer -> {
                    player.closeInventory();
                    Bukkit.getScheduler()
                            .runTask(
                                    plugin,
                                    () ->
                                            player.performCommand(
                                                    "freeze " + selectedPlayer.getName()));
                });
    }

    public void openWarnMenu(Player player) {
        openPlayerSelector(
                player,
                "Warn Player",
                Material.PAPER,
                selectedPlayer -> {
                    player.closeInventory();
                    Bukkit.getScheduler()
                            .runTask(
                                    plugin,
                                    () ->
                                            player.performCommand(
                                                    "warn "
                                                            + selectedPlayer.getName()
                                                            + " Warning issued by admin"));
                });
    }

    // ==================== INSPECTION MENUS ====================

    public void openInvseeMenu(Player player) {
        openPlayerSelector(
                player,
                "View Inventory",
                Material.CHEST,
                selectedPlayer -> {
                    player.closeInventory();
                    Bukkit.getScheduler()
                            .runTask(
                                    plugin,
                                    () ->
                                            player.performCommand(
                                                    "invsee " + selectedPlayer.getName()));
                });
    }

    public void openEcSeeMenu(Player player) {
        openPlayerSelector(
                player,
                "View Ender Chest",
                Material.ENDER_CHEST,
                selectedPlayer -> {
                    player.closeInventory();
                    Bukkit.getScheduler()
                            .runTask(
                                    plugin,
                                    () ->
                                            player.performCommand(
                                                    "ecsee " + selectedPlayer.getName()));
                });
    }

    public void openSpectateMenu(Player player) {
        openPlayerSelector(
                player,
                "Spectate Player",
                Material.ENDER_EYE,
                selectedPlayer -> {
                    player.closeInventory();
                    Bukkit.getScheduler()
                            .runTask(
                                    plugin,
                                    () ->
                                            player.performCommand(
                                                    "spectate " + selectedPlayer.getName()));
                });
    }

    public void openPovMenu(Player player) {
        openPlayerSelector(
                player,
                "POV View",
                Material.SPYGLASS,
                selectedPlayer -> {
                    player.closeInventory();
                    Bukkit.getScheduler()
                            .runTask(
                                    plugin,
                                    () -> player.performCommand("pov " + selectedPlayer.getName()));
                });
    }

    // ==================== PLAYER MANAGEMENT MENUS ====================

    public void openGamemodeMenu(Player player) {
        Gui menu = new Gui(plugin, "<gradient:#27ae60:#2ecc71>🎮 Change Gamemode</gradient>", 27);

        menu.setItem(
                10,
                new GuiItem(
                        new ItemBuilder(Material.GRASS_BLOCK)
                                .name("<gradient:#27ae60:#2ecc71>⛏ Survival Mode</gradient>")
                                .lore(
                                        "",
                                        "<gray>Switch to survival mode",
                                        "",
                                        "<green>⚡ Standard gameplay",
                                        "<green>⚡ Take damage",
                                        "<green>⚡ Limited resources",
                                        "",
                                        "<yellow>Click to change")
                                .build(),
                        event -> {
                            player.closeInventory();
                            player.performCommand("gms");
                        }));

        menu.setItem(
                12,
                new GuiItem(
                        new ItemBuilder(Material.CRAFTING_TABLE)
                                .name("<gradient:#f39c12:#e67e22>🔨 Creative Mode</gradient>")
                                .lore(
                                        "",
                                        "<gray>Switch to creative mode",
                                        "",
                                        "<gold>⚡ Unlimited resources",
                                        "<gold>⚡ Flight enabled",
                                        "<gold>⚡ Invulnerable",
                                        "",
                                        "<yellow>Click to change")
                                .build(),
                        event -> {
                            player.closeInventory();
                            player.performCommand("gmc");
                        }));

        menu.setItem(
                14,
                new GuiItem(
                        new ItemBuilder(Material.IRON_SWORD)
                                .name("<gradient:#e74c3c:#c0392b>⚔ Adventure Mode</gradient>")
                                .lore(
                                        "",
                                        "<gray>Switch to adventure mode",
                                        "",
                                        "<red>⚡ Can't break blocks",
                                        "<red>⚡ Map gameplay",
                                        "<red>⚡ Restricted",
                                        "",
                                        "<yellow>Click to change")
                                .build(),
                        event -> {
                            player.closeInventory();
                            player.performCommand("gma");
                        }));

        menu.setItem(
                16,
                new GuiItem(
                        new ItemBuilder(Material.ENDER_EYE)
                                .name("<gradient:#95a5a6:#7f8c8d>👁 Spectator Mode</gradient>")
                                .lore(
                                        "",
                                        "<gray>Switch to spectator mode",
                                        "",
                                        "<gray>⚡ Fly through blocks",
                                        "<gray>⚡ Invisible",
                                        "<gray>⚡ Observation only",
                                        "",
                                        "<yellow>Click to change")
                                .build(),
                        event -> {
                            player.closeInventory();
                            player.performCommand("gmsp");
                        }));

        menu.setItem(
                22,
                new GuiItem(
                        new ItemBuilder(Material.ARROW)
                                .name("<yellow>← Back to Admin Menu")
                                .build(),
                        event -> {
                            player.closeInventory();
                            player.performCommand("admin");
                        }));

        menu.fillEmpty(new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
        menu.open(player);
    }

    public void openHealMenu(Player player) {
        openPlayerSelector(
                player,
                "Heal Player",
                Material.GOLDEN_APPLE,
                selectedPlayer -> {
                    player.closeInventory();
                    Bukkit.getScheduler()
                            .runTask(
                                    plugin,
                                    () ->
                                            player.performCommand(
                                                    "heal " + selectedPlayer.getName()));
                });
    }

    public void openFeedMenu(Player player) {
        openPlayerSelector(
                player,
                "Feed Player",
                Material.COOKED_BEEF,
                selectedPlayer -> {
                    player.closeInventory();
                    Bukkit.getScheduler()
                            .runTask(
                                    plugin,
                                    () ->
                                            player.performCommand(
                                                    "feed " + selectedPlayer.getName()));
                });
    }

    public void openGiveItemMenu(Player player) {
        TextUtil.sendWarning(player, "Give Item feature coming soon!");
        TextUtil.send(player, "<gray>Use <white>/give <gray>command for now.");
    }

    public void openClearMenu(Player player) {
        openPlayerSelector(
                player,
                "Clear Inventory",
                Material.LAVA_BUCKET,
                selectedPlayer -> {
                    player.closeInventory();
                    Bukkit.getScheduler()
                            .runTask(
                                    plugin,
                                    () ->
                                            player.performCommand(
                                                    "clear " + selectedPlayer.getName()));
                });
    }

    public void openTeleportMenu(Player player) {
        openPlayerSelector(
                player,
                "Teleport to Player",
                Material.ENDER_PEARL,
                selectedPlayer -> {
                    player.closeInventory();
                    Bukkit.getScheduler()
                            .runTask(plugin, () -> player.teleport(selectedPlayer.getLocation()));
                    TextUtil.sendSuccess(player, "Teleported to " + selectedPlayer.getName());
                });
    }

    // ==================== SELF ADMIN MENUS ====================

    public void openSpeedMenu(Player player) {
        Gui menu = new Gui(plugin, "<gradient:#e74c3c:#c0392b>⚡ Speed Control</gradient>", 54);

        // Walk Speed Title
        menu.setItem(
                10,
                new GuiItem(
                        new ItemBuilder(Material.LEATHER_BOOTS)
                                .name("<gradient:#27ae60:#2ecc71>🚶 Walk Speed</gradient>")
                                .lore(
                                        "",
                                        "<gray>Adjust your walking speed",
                                        "",
                                        "<green>Default: 2 (0.2)",
                                        "<green>Current: " + (int) (player.getWalkSpeed() * 10))
                                .flags(ItemFlag.HIDE_ATTRIBUTES)
                                .build(),
                        event -> {} // Informational item, no action
                        ));

        // Walk speed options (1-10)
        for (int i = 1; i <= 10; i++) {
            final int speed = i;
            menu.setItem(
                    18 + i,
                    new GuiItem(
                            new ItemBuilder(Material.LIME_DYE)
                                    .name("<green>Speed " + i)
                                    .amount(i)
                                    .lore(
                                            "",
                                            "<gray>Set walk speed to " + i,
                                            "",
                                            player.getWalkSpeed() == (i / 10f)
                                                    ? "<green>✓ Currently selected"
                                                    : "<yellow>Click to select")
                                    .build(),
                            event -> {
                                player.closeInventory();
                                player.performCommand("speed walk " + speed);
                            }));
        }

        // Fly Speed Title
        menu.setItem(
                12,
                new GuiItem(
                        new ItemBuilder(Material.ELYTRA)
                                .name("<gradient:#3498db:#2980b9>🕊 Fly Speed</gradient>")
                                .lore(
                                        "",
                                        "<gray>Adjust your flight speed",
                                        "",
                                        "<aqua>Default: 1 (0.1)",
                                        "<aqua>Current: " + (int) (player.getFlySpeed() * 10))
                                .flags(ItemFlag.HIDE_ATTRIBUTES)
                                .build(),
                        event -> {} // Informational item, no action
                        ));

        // Fly speed options (1-10)
        for (int i = 1; i <= 10; i++) {
            final int speed = i;
            menu.setItem(
                    36 + i,
                    new GuiItem(
                            new ItemBuilder(Material.CYAN_DYE)
                                    .name("<aqua>Speed " + i)
                                    .amount(i)
                                    .lore(
                                            "",
                                            "<gray>Set fly speed to " + i,
                                            "",
                                            player.getFlySpeed() == (i / 10f)
                                                    ? "<green>✓ Currently selected"
                                                    : "<yellow>Click to select")
                                    .build(),
                            event -> {
                                player.closeInventory();
                                player.performCommand("speed fly " + speed);
                            }));
        }

        // Back button
        menu.setItem(
                49,
                new GuiItem(
                        new ItemBuilder(Material.ARROW)
                                .name("<yellow>← Back to Admin Menu")
                                .build(),
                        event -> {
                            player.closeInventory();
                            player.performCommand("admin");
                        }));

        menu.fillEmpty(new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
        menu.open(player);
    }

    public void toggleNightVision(Player player) {
        player.closeInventory();

        if (player.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            TextUtil.send(player, "<gray>Night vision <red>disabled<gray>!");
        } else {
            player.addPotionEffect(
                    new PotionEffect(
                            PotionEffectType.NIGHT_VISION,
                            Integer.MAX_VALUE,
                            0,
                            false,
                            false,
                            false));
            TextUtil.send(player, "<gray>Night vision <green>enabled<gray>!");
        }
    }

    // ==================== UTILITY METHODS ====================

    /** Opens a player selector menu with pagination */
    private void openPlayerSelector(
            Player viewer, String title, Material icon, PlayerSelectCallback callback) {
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        onlinePlayers.remove(viewer); // Remove viewer from list

        if (onlinePlayers.isEmpty()) {
            TextUtil.sendError(viewer, "No other players are online!");
            return;
        }

        Gui menu = new Gui(plugin, "<gradient:#9b59b6:#8e44ad>" + title + "</gradient>", 54);

        // Add player heads (max 45 players per page)
        for (int i = 0; i < Math.min(45, onlinePlayers.size()); i++) {
            Player target = onlinePlayers.get(i);
            menu.setItem(
                    i,
                    new GuiItem(
                            new ItemBuilder(Material.PLAYER_HEAD)
                                    .name(
                                            "<gradient:#3498db:#2980b9>"
                                                    + target.getName()
                                                    + "</gradient>")
                                    .lore(
                                            "",
                                            "<gray>Health: <red>" + (int) target.getHealth() + "❤",
                                            "<gray>Food: <gold>" + target.getFoodLevel() + "🍖",
                                            "<gray>Gamemode: <yellow>" + target.getGameMode(),
                                            "",
                                            "<yellow>Click to select")
                                    .build(),
                            event -> callback.onSelect(target)));
        }

        // Back button
        menu.setItem(
                49,
                new GuiItem(
                        new ItemBuilder(Material.ARROW)
                                .name("<yellow>← Back to Admin Menu")
                                .build(),
                        event -> {
                            viewer.closeInventory();
                            viewer.performCommand("admin");
                        }));

        menu.fillEmpty(new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
        menu.open(viewer);
    }

    @FunctionalInterface
    private interface PlayerSelectCallback {
        void onSelect(Player player);
    }
}
