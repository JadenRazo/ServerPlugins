package net.serverplugins.commands.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.commands.ServerCommands;
import net.serverplugins.commands.data.PlayerDataManager;
import net.serverplugins.commands.models.Home;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class HomesGui {

    private final ServerCommands plugin;
    private final Player viewer;
    private int currentPage = 0;
    private static final int ITEMS_PER_PAGE = 28; // 4 rows of 7

    public HomesGui(ServerCommands plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    public void open() {
        open(0);
    }

    public void open(int page) {
        this.currentPage = page;
        createAndOpenGui();
    }

    private void createAndOpenGui() {
        PlayerDataManager.PlayerData data =
                plugin.getPlayerDataManager().getPlayerData(viewer.getUniqueId());
        Map<String, Home> homes = data.getHomesWithDetails();
        List<String> homeNames = new ArrayList<>(homes.keySet());
        homeNames.sort(String::compareToIgnoreCase);

        int maxHomes = getMaxHomes(viewer);
        String title =
                "<gradient:#3498db:#2980b9>Your Homes ("
                        + homes.size()
                        + "/"
                        + maxHomes
                        + ")</gradient>";

        Gui gui = new Gui(plugin, title, 54);

        // Calculate pagination
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, homeNames.size());

        // Add home items
        int slot = 10;
        for (int i = startIndex; i < endIndex; i++) {
            if (slot % 9 == 8) slot += 2; // Skip edge columns
            if (slot >= 44) break; // Don't go past row 4

            String homeName = homeNames.get(i);
            Home home = homes.get(homeName);
            gui.setItem(slot, createHomeItem(home));
            slot++;
        }

        // Navigation buttons on bottom row
        addNavigationButtons(gui, homeNames.size());

        // Fill empty slots
        gui.fillEmpty(new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
        gui.open(viewer);
    }

    private GuiItem createHomeItem(Home home) {
        Material icon = home.getIcon() != null ? home.getIcon() : Material.RED_BED;
        String worldDisplay = getWorldDisplayName(home.getWorldName());
        boolean worldLoaded = home.isWorldLoaded();

        List<String> lore = new ArrayList<>();
        lore.add("");

        if (!worldLoaded) {
            lore.add("<red>World not loaded!");
            lore.add("");
        }

        lore.add("<gray>World: <white>" + worldDisplay);
        lore.add("<gray>Location: <white>" + home.getFormattedCoordinates());

        if (home.getDescription() != null && !home.getDescription().isEmpty()) {
            lore.add("");
            lore.add("<gray>Description:");
            lore.add("<white>" + home.getDescription());
        }

        lore.add("");
        lore.add("<gray>Created: <white>" + home.getFormattedCreatedDate());
        lore.add("");

        if (worldLoaded) {
            lore.add("<green>Left-click to teleport");
        }
        lore.add("<yellow>Right-click for options");

        ItemBuilder builder =
                new ItemBuilder(icon)
                        .name("<aqua>" + home.getName())
                        .lore(lore.toArray(new String[0]));

        return GuiItem.withContext(
                builder.build(),
                ctx -> {
                    if (ctx.isLeftClick()) {
                        // Teleport
                        teleportToHome(home);
                    } else if (ctx.isRightClick()) {
                        // Show options menu
                        openOptionsMenu(home);
                    }
                });
    }

    private void teleportToHome(Home home) {
        Location loc = home.getLocation();
        if (loc == null) {
            TextUtil.send(viewer, "<red>Cannot teleport - world is not loaded!");
            viewer.playSound(viewer.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 0.5f);
            return;
        }

        viewer.closeInventory();
        viewer.teleport(loc);
        TextUtil.send(viewer, "<green>Teleported to home '<aqua>" + home.getName() + "<green>'!");
        viewer.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
    }

    private void openOptionsMenu(Home home) {
        Gui optionsGui =
                new Gui(
                        plugin,
                        "<gradient:#f39c12:#e67e22>Home: " + home.getName() + "</gradient>",
                        27);

        // Teleport button (slot 10)
        if (home.isWorldLoaded()) {
            optionsGui.setItem(
                    10,
                    new GuiItem(
                            new ItemBuilder(Material.ENDER_PEARL)
                                    .name("<green>Teleport")
                                    .lore("", "<gray>Teleport to this home")
                                    .build(),
                            event -> teleportToHome(home)));
        } else {
            optionsGui.setItem(
                    10,
                    new GuiItem(
                            new ItemBuilder(Material.BARRIER)
                                    .name("<red>Cannot Teleport")
                                    .lore("", "<red>World is not loaded")
                                    .build(),
                            event -> {}));
        }

        // Change icon button (slot 12)
        optionsGui.setItem(
                12,
                new GuiItem(
                        new ItemBuilder(Material.PAINTING)
                                .name("<yellow>Change Icon")
                                .lore(
                                        "",
                                        "<gray>Current: <white>" + home.getIcon().name(),
                                        "",
                                        "<yellow>Click to change",
                                        "<gray>Use /sethome " + home.getName() + " <icon>")
                                .build(),
                        event -> {
                            viewer.closeInventory();
                            TextUtil.send(viewer, "<yellow>To change the icon, use:");
                            TextUtil.send(
                                    viewer,
                                    "<white>/sethome " + home.getName() + " <icon_material>");
                            TextUtil.send(
                                    viewer,
                                    "<gray>Example: /sethome " + home.getName() + " DIAMOND_BLOCK");
                        }));

        // Set description button (slot 14)
        optionsGui.setItem(
                14,
                new GuiItem(
                        new ItemBuilder(Material.WRITABLE_BOOK)
                                .name("<aqua>Set Description")
                                .lore(
                                        "",
                                        "<gray>Current: <white>"
                                                + (home.getDescription() != null
                                                        ? home.getDescription()
                                                        : "None"),
                                        "",
                                        "<yellow>Click for instructions")
                                .build(),
                        event -> {
                            viewer.closeInventory();
                            TextUtil.send(viewer, "<yellow>To set a description, use:");
                            TextUtil.send(
                                    viewer,
                                    "<white>/sethomedesc " + home.getName() + " <description>");
                        }));

        // Rename button (slot 16)
        optionsGui.setItem(
                16,
                new GuiItem(
                        new ItemBuilder(Material.NAME_TAG)
                                .name("<gold>Rename Home")
                                .lore(
                                        "",
                                        "<gray>Current name: <white>" + home.getName(),
                                        "",
                                        "<yellow>Click for instructions")
                                .build(),
                        event -> {
                            viewer.closeInventory();
                            TextUtil.send(viewer, "<yellow>To rename this home, use:");
                            TextUtil.send(
                                    viewer, "<white>/renamehome " + home.getName() + " <newname>");
                        }));

        // Delete button (slot 22)
        optionsGui.setItem(
                22,
                new GuiItem(
                        new ItemBuilder(Material.RED_CONCRETE)
                                .name("<red>Delete Home")
                                .lore(
                                        "",
                                        "<gray>Permanently delete this home",
                                        "",
                                        "<red>Click to confirm deletion")
                                .build(),
                        event -> new HomeDeleteConfirmGui(plugin, viewer, home, this).open()));

        // Back button (slot 18)
        optionsGui.setItem(
                18,
                new GuiItem(
                        new ItemBuilder(Material.ARROW).name("<gray>Back to Homes").build(),
                        event -> open(currentPage)));

        // Fill empty
        optionsGui.fillEmpty(new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
        optionsGui.open(viewer);
    }

    private void addNavigationButtons(Gui gui, int totalHomes) {
        int totalPages = (int) Math.ceil((double) totalHomes / ITEMS_PER_PAGE);

        // Previous page (slot 45)
        if (currentPage > 0) {
            gui.setItem(
                    45,
                    new GuiItem(
                            new ItemBuilder(Material.ARROW)
                                    .name("<gray>Previous Page")
                                    .lore("", "<yellow>Click to go back")
                                    .build(),
                            event -> open(currentPage - 1)));
        }

        // Page info (slot 49)
        gui.setItem(
                49,
                new GuiItem(
                        new ItemBuilder(Material.PAPER)
                                .name(
                                        "<white>Page "
                                                + (currentPage + 1)
                                                + "/"
                                                + Math.max(1, totalPages))
                                .lore("", "<gray>Total homes: <white>" + totalHomes)
                                .build(),
                        event -> {}));

        // Next page (slot 53)
        if (currentPage < totalPages - 1) {
            gui.setItem(
                    53,
                    new GuiItem(
                            new ItemBuilder(Material.ARROW)
                                    .name("<gray>Next Page")
                                    .lore("", "<yellow>Click for more")
                                    .build(),
                            event -> open(currentPage + 1)));
        }

        // Close button (slot 48)
        gui.setItem(
                48,
                new GuiItem(
                        new ItemBuilder(Material.BARRIER).name("<red>Close").build(),
                        event -> viewer.closeInventory()));

        // Set home button (slot 50)
        gui.setItem(
                50,
                new GuiItem(
                        new ItemBuilder(Material.LIME_CONCRETE)
                                .name("<green>Set New Home")
                                .lore(
                                        "",
                                        "<gray>Set a home at your current location",
                                        "",
                                        "<yellow>Use /sethome <name>")
                                .build(),
                        event -> {
                            viewer.closeInventory();
                            TextUtil.send(viewer, "<yellow>To set a new home, use:");
                            TextUtil.send(viewer, "<white>/sethome <name> [icon]");
                        }));
    }

    private String getWorldDisplayName(String worldName) {
        if (worldName == null) return "Unknown";

        return switch (worldName) {
            case "world", "playworld" -> "Overworld";
            case "world_nether", "playworld_nether" -> "Nether";
            case "world_the_end", "playworld_the_end" -> "The End";
            default -> worldName;
        };
    }

    private int getMaxHomes(Player player) {
        if (player.hasPermission("servercommands.homes.unlimited")) return Integer.MAX_VALUE;

        for (int i = 100; i >= 1; i--) {
            if (player.hasPermission("servercommands.homes." + i)) {
                return i;
            }
        }

        return plugin.getConfig().getInt("homes.default-max", 3);
    }
}
