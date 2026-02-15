package net.serverplugins.commands.commands;

import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.commands.ServerCommands;
import net.serverplugins.commands.admin.AdminMenuManager;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;

/**
 * Comprehensive admin menu command providing access to all administrative functions. /admin - Opens
 * the main admin menu with categorized sections.
 */
public class AdminMenuCommand implements CommandExecutor {

    private final ServerCommands plugin;
    private final AdminMenuManager menuManager;

    public AdminMenuCommand(ServerCommands plugin) {
        this.plugin = plugin;
        this.menuManager = new AdminMenuManager(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!player.hasPermission("servercommands.admin")) {
            CommonMessages.NO_PERMISSION.send(player);
            return true;
        }

        openMainMenu(player);
        return true;
    }

    private void openMainMenu(Player player) {
        Gui menu = new Gui(plugin, "<gradient:#e74c3c:#c0392b>Admin Control Panel</gradient>", 54);

        // ==================== ROW 1: SERVER CONTROL ====================

        // Shutdown Server (Slot 10)
        menu.setItem(
                10,
                new GuiItem(
                        new ItemBuilder(Material.REDSTONE_BLOCK)
                                .name("<gradient:#ff0000:#8b0000>⚠ Shutdown Server</gradient>")
                                .lore(
                                        "",
                                        "<gray>Safely shutdown the server with",
                                        "<gray>a custom colored message",
                                        "",
                                        "<red>⚡ Configurable countdown",
                                        "<red>⚡ RGB color picker",
                                        "<red>⚡ Custom shutdown message",
                                        "",
                                        "<yellow>Click to configure shutdown")
                                .flags(ItemFlag.HIDE_ATTRIBUTES)
                                .build(),
                        event -> menuManager.openShutdownMenu(player)));

        // Restart Server (Slot 11)
        menu.setItem(
                11,
                new GuiItem(
                        new ItemBuilder(Material.EMERALD_BLOCK)
                                .name("<gradient:#2ecc71:#27ae60>↻ Restart Server</gradient>")
                                .lore(
                                        "",
                                        "<gray>Restart the server with a countdown",
                                        "",
                                        "<green>⚡ Scheduled restarts",
                                        "<green>⚡ Immediate restart",
                                        "<green>⚡ Warning broadcast",
                                        "",
                                        "<yellow>Click to schedule restart")
                                .flags(ItemFlag.HIDE_ATTRIBUTES)
                                .build(),
                        event -> menuManager.openRestartMenu(player)));

        // Reload Plugins (Slot 12)
        menu.setItem(
                12,
                new GuiItem(
                        new ItemBuilder(Material.COMMAND_BLOCK)
                                .name("<gradient:#3498db:#2980b9>⟳ Reload Plugins</gradient>")
                                .lore(
                                        "",
                                        "<gray>Reload all plugin configurations",
                                        "",
                                        "<aqua>⚡ ServerCommands config",
                                        "<aqua>⚡ Dynamic commands",
                                        "<aqua>⚡ Warp & mute data",
                                        "",
                                        "<yellow>Click to reload")
                                .flags(ItemFlag.HIDE_ATTRIBUTES)
                                .build(),
                        event -> {
                            player.closeInventory();
                            plugin.reloadConfiguration();
                            TextUtil.sendSuccess(player, "All configurations reloaded!");
                        }));

        // Save All Data (Slot 13)
        menu.setItem(
                13,
                new GuiItem(
                        new ItemBuilder(Material.WRITABLE_BOOK)
                                .name("<gradient:#f39c12:#e67e22>💾 Save All Data</gradient>")
                                .lore(
                                        "",
                                        "<gray>Force save all player data",
                                        "",
                                        "<gold>⚡ Player homes & warps",
                                        "<gold>⚡ Playtime & stats",
                                        "<gold>⚡ God mode & settings",
                                        "",
                                        "<yellow>Click to save")
                                .flags(ItemFlag.HIDE_ATTRIBUTES)
                                .build(),
                        event -> {
                            player.closeInventory();
                            plugin.getPlayerDataManager().saveAll();
                            TextUtil.sendSuccess(player, "All player data saved!");
                        }));

        // ==================== ROW 2: MODERATION ACTIONS ====================

        // Ban Player (Slot 19)
        menu.setItem(
                19,
                new GuiItem(
                        new ItemBuilder(Material.IRON_DOOR)
                                .name("<gradient:#e74c3c:#c0392b>🔨 Ban Player</gradient>")
                                .lore(
                                        "",
                                        "<gray>Permanently ban a player",
                                        "",
                                        "<red>⚡ Permanent ban",
                                        "<red>⚡ Custom reason",
                                        "<red>⚡ Broadcast to server",
                                        "",
                                        "<yellow>Click to select player")
                                .flags(ItemFlag.HIDE_ATTRIBUTES)
                                .build(),
                        event -> menuManager.openBanMenu(player)));

        // Temp Ban Player (Slot 20)
        menu.setItem(
                20,
                new GuiItem(
                        new ItemBuilder(Material.CLOCK)
                                .name("<gradient:#e67e22:#d35400>⏱ Temporary Ban</gradient>")
                                .lore(
                                        "",
                                        "<gray>Temporarily ban a player",
                                        "",
                                        "<gold>⚡ Custom duration",
                                        "<gold>⚡ Auto-unban on expiry",
                                        "<gold>⚡ Duration display",
                                        "",
                                        "<yellow>Click to select player")
                                .flags(ItemFlag.HIDE_ATTRIBUTES)
                                .build(),
                        event -> menuManager.openTempBanMenu(player)));

        // Kick Player (Slot 21)
        menu.setItem(
                21,
                new GuiItem(
                        new ItemBuilder(Material.LEATHER_BOOTS)
                                .name("<gradient:#f39c12:#e67e22>👢 Kick Player</gradient>")
                                .lore(
                                        "",
                                        "<gray>Kick a player from the server",
                                        "",
                                        "<gold>⚡ Immediate removal",
                                        "<gold>⚡ Custom reason",
                                        "<gold>⚡ No ban record",
                                        "",
                                        "<yellow>Click to select player")
                                .flags(ItemFlag.HIDE_ATTRIBUTES)
                                .build(),
                        event -> menuManager.openKickMenu(player)));

        // Mute Player (Slot 22)
        menu.setItem(
                22,
                new GuiItem(
                        new ItemBuilder(Material.BARRIER)
                                .name("<gradient:#95a5a6:#7f8c8d>🔇 Mute Player</gradient>")
                                .lore(
                                        "",
                                        "<gray>Mute a player's chat",
                                        "",
                                        "<gray>⚡ Permanent or timed",
                                        "<gray>⚡ Custom reason",
                                        "<gray>⚡ Chat block",
                                        "",
                                        "<yellow>Click to select player")
                                .flags(ItemFlag.HIDE_ATTRIBUTES)
                                .build(),
                        event -> menuManager.openMuteMenu(player)));

        // Freeze Player (Slot 23)
        menu.setItem(
                23,
                new GuiItem(
                        new ItemBuilder(Material.ICE)
                                .name("<gradient:#3498db:#2980b9>❄ Freeze Player</gradient>")
                                .lore(
                                        "",
                                        "<gray>Freeze a player in place",
                                        "",
                                        "<aqua>⚡ Prevent movement",
                                        "<aqua>⚡ Block interactions",
                                        "<aqua>⚡ Investigation tool",
                                        "",
                                        "<yellow>Click to select player")
                                .flags(ItemFlag.HIDE_ATTRIBUTES)
                                .build(),
                        event -> menuManager.openFreezeMenu(player)));

        // Warn Player (Slot 24)
        menu.setItem(
                24,
                new GuiItem(
                        new ItemBuilder(Material.PAPER)
                                .name("<gradient:#f1c40f:#f39c12>⚠ Warn Player</gradient>")
                                .lore(
                                        "",
                                        "<gray>Issue a warning to a player",
                                        "",
                                        "<yellow>⚡ Track warnings",
                                        "<yellow>⚡ Custom message",
                                        "<yellow>⚡ Warning history",
                                        "",
                                        "<yellow>Click to select player")
                                .flags(ItemFlag.HIDE_ATTRIBUTES)
                                .build(),
                        event -> menuManager.openWarnMenu(player)));

        // ==================== ROW 3: INSPECTION TOOLS ====================

        // View Inventory (Slot 28)
        menu.setItem(
                28,
                new GuiItem(
                        new ItemBuilder(Material.CHEST)
                                .name("<gradient:#9b59b6:#8e44ad>👁 View Inventory</gradient>")
                                .lore(
                                        "",
                                        "<gray>View a player's inventory",
                                        "",
                                        "<light_purple>⚡ Real-time view",
                                        "<light_purple>⚡ Modify items",
                                        "<light_purple>⚡ Hotbar included",
                                        "",
                                        "<yellow>Click to select player")
                                .flags(ItemFlag.HIDE_ATTRIBUTES)
                                .build(),
                        event -> menuManager.openInvseeMenu(player)));

        // View Ender Chest (Slot 29)
        menu.setItem(
                29,
                new GuiItem(
                        new ItemBuilder(Material.ENDER_CHEST)
                                .name("<gradient:#2c3e50:#34495e>🎒 View Ender Chest</gradient>")
                                .lore(
                                        "",
                                        "<gray>View a player's ender chest",
                                        "",
                                        "<dark_gray>⚡ Private storage",
                                        "<dark_gray>⚡ Modify contents",
                                        "<dark_gray>⚡ Inspection tool",
                                        "",
                                        "<yellow>Click to select player")
                                .flags(ItemFlag.HIDE_ATTRIBUTES)
                                .build(),
                        event -> menuManager.openEcSeeMenu(player)));

        // Spectate Player (Slot 30)
        menu.setItem(
                30,
                new GuiItem(
                        new ItemBuilder(Material.ENDER_EYE)
                                .name("<gradient:#16a085:#1abc9c>👁 Spectate Player</gradient>")
                                .lore(
                                        "",
                                        "<gray>Spectate a specific player",
                                        "",
                                        "<dark_aqua>⚡ Follow their movements",
                                        "<dark_aqua>⚡ See their view",
                                        "<dark_aqua>⚡ Invisible monitoring",
                                        "",
                                        "<yellow>Click to select player")
                                .flags(ItemFlag.HIDE_ATTRIBUTES)
                                .build(),
                        event -> menuManager.openSpectateMenu(player)));

        // POV View (Slot 31)
        menu.setItem(
                31,
                new GuiItem(
                        new ItemBuilder(Material.SPYGLASS)
                                .name("<gradient:#e67e22:#d35400>📷 POV View</gradient>")
                                .lore(
                                        "",
                                        "<gray>View from a player's perspective",
                                        "",
                                        "<gold>⚡ See their view",
                                        "<gold>⚡ Real-time feed",
                                        "<gold>⚡ Monitoring tool",
                                        "",
                                        "<yellow>Click to select player")
                                .flags(ItemFlag.HIDE_ATTRIBUTES)
                                .build(),
                        event -> menuManager.openPovMenu(player)));

        // FreeCam Mode (Slot 32)
        menu.setItem(
                32,
                new GuiItem(
                        new ItemBuilder(Material.PHANTOM_MEMBRANE)
                                .name("<gradient:#95a5a6:#7f8c8d>🎥 FreeCam Mode</gradient>")
                                .lore(
                                        "",
                                        "<gray>Free camera movement mode",
                                        "",
                                        "<gray>⚡ Fly freely",
                                        "<gray>⚡ Investigate areas",
                                        "<gray>⚡ No clip mode",
                                        "",
                                        "<yellow>Click to toggle")
                                .flags(ItemFlag.HIDE_ATTRIBUTES)
                                .build(),
                        event -> {
                            player.closeInventory();
                            player.performCommand("freecam");
                        }));

        // Vanish Toggle (Slot 33)
        menu.setItem(
                33,
                new GuiItem(
                        new ItemBuilder(Material.GLASS)
                                .name("<gradient:#ecf0f1:#bdc3c7>👻 Vanish Mode</gradient>")
                                .lore(
                                        "",
                                        "<gray>Toggle invisibility to players",
                                        "",
                                        "<white>⚡ Hide from players",
                                        "<white>⚡ Silent join/quit",
                                        "<white>⚡ Admin mode",
                                        "",
                                        "<yellow>Click to toggle")
                                .flags(ItemFlag.HIDE_ATTRIBUTES)
                                .build(),
                        event -> {
                            player.closeInventory();
                            player.performCommand("vanish");
                        }));

        // ==================== ROW 4: PLAYER MANAGEMENT ====================

        // Change Gamemode (Slot 37)
        menu.setItem(
                37,
                new GuiItem(
                        new ItemBuilder(Material.GRASS_BLOCK)
                                .name("<gradient:#27ae60:#2ecc71>🎮 Change Gamemode</gradient>")
                                .lore(
                                        "",
                                        "<gray>Change player gamemode",
                                        "",
                                        "<green>⚡ Survival, Creative",
                                        "<green>⚡ Adventure, Spectator",
                                        "<green>⚡ For self or others",
                                        "",
                                        "<yellow>Click to select mode")
                                .flags(ItemFlag.HIDE_ATTRIBUTES)
                                .build(),
                        event -> menuManager.openGamemodeMenu(player)));

        // Heal Player (Slot 38)
        menu.setItem(
                38,
                new GuiItem(
                        new ItemBuilder(Material.GOLDEN_APPLE)
                                .name("<gradient:#e74c3c:#c0392b>❤ Heal Player</gradient>")
                                .lore(
                                        "",
                                        "<gray>Restore health & hunger",
                                        "",
                                        "<red>⚡ Full health",
                                        "<red>⚡ Full hunger",
                                        "<red>⚡ Clear effects",
                                        "",
                                        "<yellow>Click to select player")
                                .flags(ItemFlag.HIDE_ATTRIBUTES)
                                .build(),
                        event -> menuManager.openHealMenu(player)));

        // Feed Player (Slot 39)
        menu.setItem(
                39,
                new GuiItem(
                        new ItemBuilder(Material.COOKED_BEEF)
                                .name("<gradient:#d35400:#e67e22>🍖 Feed Player</gradient>")
                                .lore(
                                        "",
                                        "<gray>Restore player hunger",
                                        "",
                                        "<gold>⚡ Max saturation",
                                        "<gold>⚡ Full hunger bar",
                                        "<gold>⚡ Instant effect",
                                        "",
                                        "<yellow>Click to select player")
                                .flags(ItemFlag.HIDE_ATTRIBUTES)
                                .build(),
                        event -> menuManager.openFeedMenu(player)));

        // Give Item (Slot 40)
        menu.setItem(
                40,
                new GuiItem(
                        new ItemBuilder(Material.DIAMOND)
                                .name("<gradient:#3498db:#2980b9>💎 Give Item</gradient>")
                                .lore(
                                        "",
                                        "<gray>Give items to a player",
                                        "",
                                        "<aqua>⚡ Any item type",
                                        "<aqua>⚡ Custom amount",
                                        "<aqua>⚡ NBT support",
                                        "",
                                        "<yellow>Click to select player")
                                .flags(ItemFlag.HIDE_ATTRIBUTES)
                                .build(),
                        event -> menuManager.openGiveItemMenu(player)));

        // Clear Inventory (Slot 41)
        menu.setItem(
                41,
                new GuiItem(
                        new ItemBuilder(Material.LAVA_BUCKET)
                                .name("<gradient:#e67e22:#d35400>🗑 Clear Inventory</gradient>")
                                .lore(
                                        "",
                                        "<gray>Clear a player's inventory",
                                        "",
                                        "<gold>⚡ Remove all items",
                                        "<gold>⚡ Warning prompt",
                                        "<gold>⚡ Irreversible",
                                        "",
                                        "<yellow>Click to select player")
                                .flags(ItemFlag.HIDE_ATTRIBUTES)
                                .build(),
                        event -> menuManager.openClearMenu(player)));

        // Teleport to Player (Slot 42)
        menu.setItem(
                42,
                new GuiItem(
                        new ItemBuilder(Material.ENDER_PEARL)
                                .name("<gradient:#9b59b6:#8e44ad>🌀 Teleport</gradient>")
                                .lore(
                                        "",
                                        "<gray>Teleport to a player",
                                        "",
                                        "<light_purple>⚡ Instant teleport",
                                        "<light_purple>⚡ No cooldown",
                                        "<light_purple>⚡ Silent mode",
                                        "",
                                        "<yellow>Click to select player")
                                .flags(ItemFlag.HIDE_ATTRIBUTES)
                                .build(),
                        event -> menuManager.openTeleportMenu(player)));

        // ==================== ROW 5: SELF ADMIN ====================

        // God Mode Toggle (Slot 46)
        menu.setItem(
                46,
                new GuiItem(
                        new ItemBuilder(Material.TOTEM_OF_UNDYING)
                                .name("<gradient:#f1c40f:#f39c12>✨ God Mode</gradient>")
                                .lore(
                                        "",
                                        "<gray>Toggle invincibility",
                                        "",
                                        "<yellow>⚡ No damage taken",
                                        "<yellow>⚡ All sources blocked",
                                        "<yellow>⚡ Persistent",
                                        "",
                                        plugin.getPlayerDataManager()
                                                        .getPlayerData(player.getUniqueId())
                                                        .isGodMode()
                                                ? "<green>Status: <bold>ENABLED</bold>"
                                                : "<red>Status: <bold>DISABLED</bold>",
                                        "",
                                        "<yellow>Click to toggle")
                                .flags(ItemFlag.HIDE_ATTRIBUTES)
                                .build(),
                        event -> {
                            player.closeInventory();
                            player.performCommand("god");
                        }));

        // Fly Toggle (Slot 47)
        menu.setItem(
                47,
                new GuiItem(
                        new ItemBuilder(Material.ELYTRA)
                                .name("<gradient:#3498db:#2980b9>🕊 Flight Mode</gradient>")
                                .lore(
                                        "",
                                        "<gray>Toggle flight ability",
                                        "",
                                        "<aqua>⚡ Creative flight",
                                        "<aqua>⚡ Any gamemode",
                                        "<aqua>⚡ Persistent",
                                        "",
                                        player.getAllowFlight()
                                                ? "<green>Status: <bold>ENABLED</bold>"
                                                : "<red>Status: <bold>DISABLED</bold>",
                                        "",
                                        "<yellow>Click to toggle")
                                .flags(ItemFlag.HIDE_ATTRIBUTES)
                                .build(),
                        event -> {
                            player.closeInventory();
                            player.performCommand("fly");
                        }));

        // Speed Control (Slot 48)
        menu.setItem(
                48,
                new GuiItem(
                        new ItemBuilder(Material.SUGAR)
                                .name("<gradient:#e74c3c:#c0392b>⚡ Speed Control</gradient>")
                                .lore(
                                        "",
                                        "<gray>Adjust movement speed",
                                        "",
                                        "<red>⚡ Walk speed (1-10)",
                                        "<red>⚡ Fly speed (1-10)",
                                        "<red>⚡ Instant effect",
                                        "",
                                        "<yellow>Click to open speed menu")
                                .flags(ItemFlag.HIDE_ATTRIBUTES)
                                .build(),
                        event -> menuManager.openSpeedMenu(player)));

        // Creative Mode (Slot 49)
        menu.setItem(
                49,
                new GuiItem(
                        new ItemBuilder(Material.CRAFTING_TABLE)
                                .name("<gradient:#27ae60:#2ecc71>🔨 Creative Mode</gradient>")
                                .lore(
                                        "",
                                        "<gray>Quick toggle creative mode",
                                        "",
                                        "<green>⚡ Instant switch",
                                        "<green>⚡ Fly enabled",
                                        "<green>⚡ Full access",
                                        "",
                                        "<yellow>Click to toggle")
                                .flags(ItemFlag.HIDE_ATTRIBUTES)
                                .build(),
                        event -> {
                            player.closeInventory();
                            player.performCommand("gmc");
                        }));

        // Night Vision (Slot 50)
        menu.setItem(
                50,
                new GuiItem(
                        new ItemBuilder(Material.GOLDEN_CARROT)
                                .name("<gradient:#f39c12:#e67e22>🌙 Night Vision</gradient>")
                                .lore(
                                        "",
                                        "<gray>Toggle night vision effect",
                                        "",
                                        "<gold>⚡ See in darkness",
                                        "<gold>⚡ Permanent effect",
                                        "<gold>⚡ No particles",
                                        "",
                                        "<yellow>Click to toggle")
                                .flags(ItemFlag.HIDE_ATTRIBUTES)
                                .build(),
                        event -> menuManager.toggleNightVision(player)));

        // Fill empty slots with decorative glass panes
        menu.fillEmpty(new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
        menu.open(player);
    }

    public AdminMenuManager getMenuManager() {
        return menuManager;
    }
}
