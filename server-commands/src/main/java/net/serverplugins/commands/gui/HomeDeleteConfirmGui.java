package net.serverplugins.commands.gui;

import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.commands.ServerCommands;
import net.serverplugins.commands.data.PlayerDataManager;
import net.serverplugins.commands.models.Home;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class HomeDeleteConfirmGui {

    private final ServerCommands plugin;
    private final Player viewer;
    private final Home home;
    private final HomesGui parentGui;

    public HomeDeleteConfirmGui(
            ServerCommands plugin, Player viewer, Home home, HomesGui parentGui) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.home = home;
        this.parentGui = parentGui;
    }

    public void open() {
        Gui gui = new Gui(plugin, "<red>Delete Home: " + home.getName() + "?</red>", 27);

        // Warning info in center top
        gui.setItem(
                4,
                new GuiItem(
                        new ItemBuilder(Material.TNT)
                                .name("<red>Warning!")
                                .lore(
                                        "",
                                        "<gray>You are about to delete:",
                                        "<white>" + home.getName(),
                                        "",
                                        "<gray>Location: <white>" + home.getFormattedCoordinates(),
                                        "<gray>World: <white>" + home.getWorldName(),
                                        "",
                                        "<red>This action cannot be undone!")
                                .build(),
                        event -> {}));

        // Confirm button (slot 11)
        gui.setItem(
                11,
                new GuiItem(
                        new ItemBuilder(Material.LIME_CONCRETE)
                                .name("<green>Confirm Delete")
                                .lore(
                                        "",
                                        "<gray>Click to permanently delete",
                                        "<gray>the home '<white>" + home.getName() + "<gray>'")
                                .build(),
                        event -> deleteHome()));

        // Cancel button (slot 15)
        gui.setItem(
                15,
                new GuiItem(
                        new ItemBuilder(Material.RED_CONCRETE)
                                .name("<red>Cancel")
                                .lore("", "<gray>Go back without deleting")
                                .build(),
                        event -> parentGui.open()));

        // Fill empty
        gui.fillEmpty(new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
        gui.open(viewer);
    }

    private void deleteHome() {
        PlayerDataManager.PlayerData data =
                plugin.getPlayerDataManager().getPlayerData(viewer.getUniqueId());

        if (!data.hasHome(home.getName())) {
            TextUtil.send(viewer, "<red>Home no longer exists!");
            parentGui.open();
            return;
        }

        data.removeHome(home.getName());
        plugin.getPlayerDataManager().savePlayerData(viewer.getUniqueId());

        TextUtil.send(
                viewer, "<green>Home '<aqua>" + home.getName() + "<green>' has been deleted!");
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_ANVIL_DESTROY, 0.5f, 1f);

        // Return to homes list
        parentGui.open();
    }
}
