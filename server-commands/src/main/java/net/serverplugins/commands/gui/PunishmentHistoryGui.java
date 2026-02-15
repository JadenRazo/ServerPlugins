package net.serverplugins.commands.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.commands.ServerCommands;
import net.serverplugins.commands.data.punishment.PunishmentRecord;
import net.serverplugins.commands.data.punishment.PunishmentType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;

public class PunishmentHistoryGui {

    private final ServerCommands plugin;
    private final Player viewer;
    private final UUID targetUuid;
    private final String targetName;
    private final PunishmentType filterType;
    private int currentPage = 0;
    private static final int ITEMS_PER_PAGE = 28; // 4 rows of 7

    public PunishmentHistoryGui(ServerCommands plugin, Player viewer, OfflinePlayer target) {
        this(plugin, viewer, target, null);
    }

    public PunishmentHistoryGui(
            ServerCommands plugin, Player viewer, OfflinePlayer target, PunishmentType filterType) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.targetUuid = target.getUniqueId();
        this.targetName = target.getName();
        this.filterType = filterType;
    }

    public void open() {
        loadAndDisplay(currentPage);
    }

    private void loadAndDisplay(int page) {
        plugin.getPunishmentHistoryManager()
                .getPlayerHistory(targetUuid, filterType, ITEMS_PER_PAGE, page * ITEMS_PER_PAGE)
                .thenAccept(
                        records -> {
                            Bukkit.getScheduler()
                                    .runTask(plugin, () -> createAndOpenGui(records, page));
                        });
    }

    private void createAndOpenGui(List<PunishmentRecord> records, int page) {
        String title =
                filterType != null
                        ? "<gradient:#e74c3c:#c0392b>History: "
                                + targetName
                                + " ("
                                + filterType.getDisplayName()
                                + ")</gradient>"
                        : "<gradient:#e74c3c:#c0392b>Punishment History: "
                                + targetName
                                + "</gradient>";

        Gui gui = new Gui(plugin, title, 54);

        // Add filter buttons on top row
        addFilterButtons(gui);

        // Add punishment records
        int slot = 10;
        for (PunishmentRecord record : records) {
            if (slot % 9 == 8) slot += 2; // Skip edge columns
            if (slot >= 44) break; // Don't go past row 4

            gui.setItem(slot, createRecordItem(record));
            slot++;
        }

        // Navigation buttons on bottom row
        // Previous page (slot 45)
        if (page > 0) {
            gui.setItem(
                    45,
                    new GuiItem(
                            new ItemBuilder(Material.ARROW)
                                    .name("<gray>Previous Page")
                                    .lore("", "<yellow>Click to go back")
                                    .build(),
                            event -> {
                                currentPage = page - 1;
                                loadAndDisplay(currentPage);
                            }));
        }

        // Info (slot 49)
        gui.setItem(
                49,
                new GuiItem(
                        new ItemBuilder(Material.PAPER)
                                .name("<white>Page " + (page + 1))
                                .lore(
                                        "",
                                        "<gray>Showing " + records.size() + " records",
                                        "<gray>Target: <white>" + targetName)
                                .build(),
                        event -> {}));

        // Next page (slot 53)
        if (records.size() == ITEMS_PER_PAGE) {
            gui.setItem(
                    53,
                    new GuiItem(
                            new ItemBuilder(Material.ARROW)
                                    .name("<gray>Next Page")
                                    .lore("", "<yellow>Click for more")
                                    .build(),
                            event -> {
                                currentPage = page + 1;
                                loadAndDisplay(currentPage);
                            }));
        }

        // Close button (slot 48)
        gui.setItem(
                48,
                new GuiItem(
                        new ItemBuilder(Material.BARRIER).name("<red>Close").build(),
                        event -> viewer.closeInventory()));

        // Fill empty slots
        gui.fillEmpty(new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
        gui.open(viewer);
    }

    private void addFilterButtons(Gui gui) {
        // All punishments (slot 1)
        gui.setItem(1, createFilterButton(null, Material.BOOK, "All Types", filterType == null));

        // Ban (slot 2)
        gui.setItem(
                2,
                createFilterButton(
                        PunishmentType.BAN,
                        Material.IRON_DOOR,
                        "Bans",
                        filterType == PunishmentType.BAN));

        // Tempban (slot 3)
        gui.setItem(
                3,
                createFilterButton(
                        PunishmentType.TEMPBAN,
                        Material.CLOCK,
                        "Temp Bans",
                        filterType == PunishmentType.TEMPBAN));

        // Kick (slot 4)
        gui.setItem(
                4,
                createFilterButton(
                        PunishmentType.KICK,
                        Material.LEATHER_BOOTS,
                        "Kicks",
                        filterType == PunishmentType.KICK));

        // Mute (slot 5)
        gui.setItem(
                5,
                createFilterButton(
                        PunishmentType.MUTE,
                        Material.BARRIER,
                        "Mutes",
                        filterType == PunishmentType.MUTE));

        // Warn (slot 6)
        gui.setItem(
                6,
                createFilterButton(
                        PunishmentType.WARN,
                        Material.PAPER,
                        "Warnings",
                        filterType == PunishmentType.WARN));

        // Freeze (slot 7)
        gui.setItem(
                7,
                createFilterButton(
                        PunishmentType.FREEZE,
                        Material.ICE,
                        "Freezes",
                        filterType == PunishmentType.FREEZE));
    }

    private GuiItem createFilterButton(
            PunishmentType type, Material material, String name, boolean selected) {
        ItemBuilder builder =
                new ItemBuilder(material)
                        .name((selected ? "<green>" : "<gray>") + name)
                        .lore(
                                "",
                                selected ? "<green>Currently selected" : "<yellow>Click to filter");

        if (selected) {
            builder.flags(ItemFlag.HIDE_ENCHANTS);
            // Would add enchant glow, but ItemBuilder may not support it
        }

        return new GuiItem(
                builder.build(),
                event -> {
                    if (!selected) {
                        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
                        new PunishmentHistoryGui(plugin, viewer, target, type).open();
                    }
                });
    }

    private GuiItem createRecordItem(PunishmentRecord record) {
        Material icon = record.getType().getIcon();
        String colorCode = record.getType().getColorCode();
        String status = record.isActive() ? "<green>[ACTIVE]" : "<gray>[INACTIVE]";

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(status);
        lore.add("");
        lore.add("<gray>Staff: <white>" + record.getStaffName());
        lore.add("<gray>Date: <white>" + record.getFormattedIssuedDate());
        lore.add("<gray>Reason: <white>" + record.getReason());

        if (record.getDurationMs() != null) {
            lore.add("<gray>Duration: <white>" + record.getFormattedDuration());
            if (record.isActive() && !record.isExpired()) {
                lore.add("<gray>Remaining: <yellow>" + record.getFormattedRemainingTime());
            }
        }

        if (record.getLiftedAt() != null) {
            lore.add("");
            lore.add("<green>Lifted by: " + record.getLiftedByName());
        }

        if (viewer.hasPermission("servercommands.history.lift")
                && record.isActive()
                && !record.getType().isRemoval()) {
            lore.add("");
            lore.add("<yellow>Click to lift this punishment");
        }

        ItemBuilder builder =
                new ItemBuilder(icon)
                        .name(
                                "<"
                                        + colorCode
                                        + ">"
                                        + record.getType().getDisplayName()
                                        + " #"
                                        + record.getId())
                        .lore(lore.toArray(new String[0]))
                        .flags(ItemFlag.HIDE_ATTRIBUTES);

        return new GuiItem(
                builder.build(),
                event -> {
                    if (viewer.hasPermission("servercommands.history.lift")
                            && record.isActive()
                            && !record.getType().isRemoval()) {
                        // Lift the punishment
                        liftPunishment(record);
                    }
                });
    }

    private void liftPunishment(PunishmentRecord record) {
        TextUtil.send(viewer, "<yellow>Lifting punishment #" + record.getId() + "...");

        plugin.getPunishmentHistoryManager()
                .liftPunishment(record.getId(), viewer.getUniqueId(), viewer.getName())
                .thenAccept(
                        success -> {
                            Bukkit.getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                if (success) {
                                                    TextUtil.sendSuccess(
                                                            viewer,
                                                            "Punishment #"
                                                                    + record.getId()
                                                                    + " has been lifted!");

                                                    // Actually remove the active punishment
                                                    switch (record.getType()) {
                                                        case BAN, TEMPBAN ->
                                                                plugin.getBanManager()
                                                                        .unban(
                                                                                record
                                                                                        .getTargetName());
                                                        case MUTE ->
                                                                plugin.getMuteManager()
                                                                        .unmute(
                                                                                record
                                                                                        .getTargetUuid());
                                                            // FREEZE would need a FreezeManager
                                                    }

                                                    // Refresh the GUI
                                                    loadAndDisplay(currentPage);
                                                } else {
                                                    TextUtil.sendError(
                                                            viewer,
                                                            "Failed to lift punishment #"
                                                                    + record.getId());
                                                }
                                            });
                        });
    }
}
