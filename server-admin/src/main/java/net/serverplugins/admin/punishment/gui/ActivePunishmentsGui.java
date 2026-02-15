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
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ActivePunishmentsGui extends Gui {

    private final ServerAdmin plugin;
    private final OfflinePlayer target;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yy HH:mm");

    // Pre-loaded data to avoid synchronous DB queries
    private List<Punishment> activePunishments = Collections.emptyList();

    public ActivePunishmentsGui(ServerAdmin plugin, Player viewer, OfflinePlayer target) {
        super(plugin, viewer, "<dark_red>Active: <red>" + target.getName(), 54);
        this.plugin = plugin;
        this.target = target;
    }

    /** Opens the GUI after loading data asynchronously to avoid blocking the main thread. */
    public static void openAsync(ServerAdmin plugin, Player viewer, OfflinePlayer target) {
        CompletableFuture.supplyAsync(
                        () -> {
                            ActivePunishmentsGui gui =
                                    new ActivePunishmentsGui(plugin, viewer, target);
                            try {
                                gui.activePunishments =
                                        plugin.getPunishmentManager()
                                                .getActivePunishments(target.getUniqueId());
                            } catch (Exception e) {
                                plugin.getLogger()
                                        .warning(
                                                "Failed to load active punishments: "
                                                        + e.getMessage());
                                gui.activePunishments = Collections.emptyList();
                            }
                            return gui;
                        })
                .thenAccept(
                        gui -> {
                            Bukkit.getScheduler().runTask(plugin, (Runnable) gui::open);
                        });
    }

    @Override
    protected void initializeItems() {
        ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) {
            setItem(i, new GuiItem(filler));
        }

        // Use pre-loaded data instead of blocking DB call
        List<Punishment> activePunishments = this.activePunishments;

        if (activePunishments.isEmpty()) {
            ItemStack noPunishments =
                    new ItemBuilder(Material.EMERALD_BLOCK)
                            .name("<green>No Active Punishments")
                            .lore("", "<gray>This player has no active punishments")
                            .build();
            setItem(22, new GuiItem(noPunishments));
        } else {
            int slot = 10;
            for (Punishment p : activePunishments) {
                if (slot > 43) break;
                if (slot % 9 == 8) slot += 2;

                Material material =
                        switch (p.getType()) {
                            case WARN -> Material.PAPER;
                            case MUTE -> Material.BARRIER;
                            case KICK -> Material.LEATHER_BOOTS;
                            case BAN -> Material.IRON_DOOR;
                            case FREEZE -> Material.ICE;
                        };

                List<String> lore = new ArrayList<>();
                lore.add("");
                lore.add("<gray>ID: <white>#" + p.getId());
                lore.add("<gray>Staff: <white>" + p.getStaffName());
                lore.add("<gray>Issued: <white>" + dateFormat.format(new Date(p.getIssuedAt())));

                if (p.getDurationMs() != null) {
                    lore.add("<gray>Duration: <white>" + p.getFormattedDuration());
                    lore.add("<gray>Remaining: <yellow>" + p.getFormattedRemainingTime());
                } else if (p.getType().hasDuration()) {
                    lore.add("<gray>Duration: <red>Permanent");
                }

                if (p.getReason() != null) {
                    lore.add("");
                    lore.add("<gray>Reason:");
                    lore.add("<white>" + p.getReason());
                }

                if (p.getCategory() != null) {
                    lore.add("");
                    lore.add("<gray>Category: <gold>" + p.getCategory());
                    if (p.getOffenseNumber() != null) {
                        lore.add("<gray>Offense #: <white>" + p.getOffenseNumber());
                    }
                }

                lore.add("");
                lore.add("<red>Click to pardon");

                ItemStack item =
                        new ItemBuilder(material)
                                .name("<red>" + p.getType().getDisplayName())
                                .lore(lore.toArray(new String[0]))
                                .glow(true)
                                .build();

                final int punishmentId = p.getId();
                setItem(
                        slot,
                        GuiItem.withContext(
                                item,
                                ctx -> {
                                    plugin.getPunishmentManager()
                                            .pardon(punishmentId, viewer, "Pardoned via GUI")
                                            .thenAccept(
                                                    success -> {
                                                        Bukkit.getScheduler()
                                                                .runTask(
                                                                        plugin,
                                                                        () -> {
                                                                            if (success) {
                                                                                viewer.sendMessage(
                                                                                        TextUtil
                                                                                                .parse(
                                                                                                        "<green>Pardoned punishment #"
                                                                                                                + punishmentId));
                                                                                // Use async to
                                                                                // refresh GUI
                                                                                ActivePunishmentsGui
                                                                                        .openAsync(
                                                                                                plugin,
                                                                                                viewer,
                                                                                                target);
                                                                            } else {
                                                                                viewer.sendMessage(
                                                                                        TextUtil
                                                                                                .parse(
                                                                                                        "<red>Failed to pardon punishment"));
                                                                            }
                                                                        });
                                                    });
                                }));

                slot++;
            }
        }

        ItemStack backItem =
                new ItemBuilder(Material.ARROW).name("<yellow>Back to Punish Menu").build();
        setItem(
                45,
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
                        .name("<white>Active Punishments")
                        .lore(
                                "",
                                "<gray>Player: <yellow>" + target.getName(),
                                "<gray>Active: <red>" + activePunishments.size(),
                                "",
                                "<gray>Click a punishment to pardon it")
                        .build();
        setItem(4, new GuiItem(infoItem));
    }
}
