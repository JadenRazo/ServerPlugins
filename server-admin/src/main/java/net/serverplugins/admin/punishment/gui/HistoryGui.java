package net.serverplugins.admin.punishment.gui;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.admin.punishment.Punishment;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class HistoryGui extends Gui {

    private final ServerAdmin plugin;
    private final OfflinePlayer target;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yy HH:mm");
    private int page = 0;

    // Pre-loaded data to avoid synchronous DB queries
    private List<Punishment> history = Collections.emptyList();

    public HistoryGui(ServerAdmin plugin, Player viewer, OfflinePlayer target) {
        super(plugin, viewer, "<dark_gray>History: <gray>" + target.getName(), 54);
        this.plugin = plugin;
        this.target = target;
    }

    /** Opens the GUI after loading data asynchronously to avoid blocking the main thread. */
    public static void openAsync(ServerAdmin plugin, Player viewer, OfflinePlayer target) {
        CompletableFuture.supplyAsync(
                        () -> {
                            HistoryGui gui = new HistoryGui(plugin, viewer, target);
                            try {
                                gui.history =
                                        plugin.getPunishmentManager()
                                                .getPunishmentHistory(target.getUniqueId(), 100);
                            } catch (Exception e) {
                                plugin.getLogger()
                                        .warning(
                                                "Failed to load punishment history: "
                                                        + e.getMessage());
                                gui.history = Collections.emptyList();
                            }
                            return gui;
                        })
                .thenAccept(
                        gui -> {
                            org.bukkit.Bukkit.getScheduler().runTask(plugin, (Runnable) gui::open);
                        });
    }

    @Override
    protected void initializeItems() {
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 9; i++) {
            setItem(i, new GuiItem(filler));
            setItem(45 + i, new GuiItem(filler));
        }

        // Use pre-loaded data instead of blocking DB call
        List<Punishment> history = this.history;

        int perPage = 36;
        int totalPages = Math.max(1, (int) Math.ceil(history.size() / (double) perPage));
        int startIndex = page * perPage;
        int endIndex = Math.min(startIndex + perPage, history.size());

        ItemStack empty = new ItemBuilder(Material.LIGHT_GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 9; i < 45; i++) {
            setItem(i, new GuiItem(empty));
        }

        if (history.isEmpty()) {
            ItemStack noHistory =
                    new ItemBuilder(Material.EMERALD_BLOCK)
                            .name("<green>No Punishment History")
                            .lore("", "<gray>This player has never been punished")
                            .build();
            setItem(22, new GuiItem(noHistory));
        } else {
            int slot = 9;
            for (int i = startIndex; i < endIndex && slot < 45; i++) {
                Punishment p = history.get(i);

                String status;
                Material material;
                if (p.isActive()) {
                    status = "<red>ACTIVE";
                    material = Material.RED_WOOL;
                } else if (p.getPardonedAt() != null) {
                    status = "<yellow>PARDONED";
                    material = Material.YELLOW_WOOL;
                } else {
                    status = "<gray>EXPIRED";
                    material = Material.GRAY_WOOL;
                }

                List<String> lore = new ArrayList<>();
                lore.add("");
                lore.add("<gray>ID: <white>#" + p.getId());
                lore.add("<gray>Status: " + status);
                lore.add("<gray>Staff: <white>" + p.getStaffName());
                lore.add("<gray>Date: <white>" + dateFormat.format(new Date(p.getIssuedAt())));

                if (p.getDurationMs() != null) {
                    lore.add("<gray>Duration: <white>" + p.getFormattedDuration());
                } else if (p.getType().hasDuration()) {
                    lore.add("<gray>Duration: <red>Permanent");
                }

                if (p.getReason() != null) {
                    lore.add("");
                    lore.add("<gray>Reason:");
                    String reason = p.getReason();
                    if (reason.length() > 30) {
                        reason = reason.substring(0, 30) + "...";
                    }
                    lore.add("<white>" + reason);
                }

                if (p.getCategory() != null) {
                    lore.add("");
                    lore.add(
                            "<gray>Category: <gold>"
                                    + p.getCategory()
                                    + " #"
                                    + p.getOffenseNumber());
                }

                if (p.getPardonedAt() != null) {
                    lore.add("");
                    lore.add("<yellow>Pardoned by: <white>" + p.getPardonedByName());
                    lore.add(
                            "<yellow>On: <white>" + dateFormat.format(new Date(p.getPardonedAt())));
                }

                ItemStack item =
                        new ItemBuilder(material)
                                .name(
                                        "<white>"
                                                + p.getType().getDisplayName()
                                                + " <gray>#"
                                                + p.getId())
                                .lore(lore.toArray(new String[0]))
                                .build();

                setItem(slot, new GuiItem(item));
                slot++;
            }
        }

        if (page > 0) {
            ItemStack prevPage =
                    new ItemBuilder(Material.ARROW)
                            .name("<yellow>Previous Page")
                            .lore("<gray>Page " + page + "/" + totalPages)
                            .build();
            setItem(
                    45,
                    new GuiItem(
                            prevPage,
                            e -> {
                                page--;
                                initializeItems();
                            }));
        }

        if (page < totalPages - 1) {
            ItemStack nextPage =
                    new ItemBuilder(Material.ARROW)
                            .name("<yellow>Next Page")
                            .lore("<gray>Page " + (page + 2) + "/" + totalPages)
                            .build();
            setItem(
                    53,
                    new GuiItem(
                            nextPage,
                            e -> {
                                page++;
                                initializeItems();
                            }));
        }

        ItemStack backItem =
                new ItemBuilder(Material.OAK_DOOR).name("<yellow>Back to Punish Menu").build();
        setItem(
                48,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            PunishGui.openAsync(plugin, viewer, target);
                        }));

        ItemStack closeItem = new ItemBuilder(Material.BARRIER).name("<red>Close").build();
        setItem(49, new GuiItem(closeItem, e -> viewer.closeInventory()));

        ItemStack infoItem =
                new ItemBuilder(Material.BOOK)
                        .name("<white>Punishment History")
                        .lore(
                                "",
                                "<gray>Player: <yellow>" + target.getName(),
                                "<gray>Total: <white>" + history.size() + " punishments",
                                "<gray>Page: <white>" + (page + 1) + "/" + totalPages)
                        .build();
        setItem(4, new GuiItem(infoItem));
    }
}
