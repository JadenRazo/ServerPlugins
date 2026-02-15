package net.serverplugins.admin.punishment.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.admin.punishment.Punishment;
import net.serverplugins.admin.punishment.PunishmentType;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public class PunishGui extends Gui {

    private final ServerAdmin plugin;
    private final OfflinePlayer target;

    // Pre-loaded data to avoid synchronous DB queries
    private int punishmentCount = 0;
    private List<Punishment> activePunishments = Collections.emptyList();
    private List<Punishment> recentHistory = Collections.emptyList();

    public PunishGui(ServerAdmin plugin, Player viewer, OfflinePlayer target) {
        super(plugin, viewer, "<dark_red>Punish: <red>" + target.getName(), 54);
        this.plugin = plugin;
        this.target = target;
    }

    /** Opens the GUI after loading data asynchronously to avoid blocking the main thread. */
    public static void openAsync(ServerAdmin plugin, Player viewer, OfflinePlayer target) {
        // Load all data asynchronously first
        CompletableFuture.supplyAsync(
                        () -> {
                            PunishGui gui = new PunishGui(plugin, viewer, target);
                            try {
                                gui.punishmentCount =
                                        plugin.getPunishmentManager()
                                                .getPunishmentCount(target.getUniqueId());
                                gui.activePunishments =
                                        plugin.getPunishmentManager()
                                                .getActivePunishments(target.getUniqueId());
                                gui.recentHistory =
                                        plugin.getPunishmentManager()
                                                .getPunishmentHistory(target.getUniqueId(), 5);
                            } catch (Exception e) {
                                plugin.getLogger()
                                        .warning(
                                                "Failed to load punishment data: "
                                                        + e.getMessage());
                                gui.punishmentCount = 0;
                                gui.activePunishments = Collections.emptyList();
                                gui.recentHistory = Collections.emptyList();
                            }
                            return gui;
                        })
                .thenAccept(
                        gui -> {
                            // Open GUI on main thread
                            Bukkit.getScheduler().runTask(plugin, (Runnable) gui::open);
                        });
    }

    @Override
    protected void initializeItems() {
        ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) {
            setItem(i, new GuiItem(filler));
        }

        setupPlayerInfo();
        setupTypeButtons();
        setupRecentHistory();
        setupNavigation();
    }

    private void setupPlayerInfo() {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(target);

            // Use pre-loaded data instead of blocking DB calls
            int punishmentCount = this.punishmentCount;
            List<Punishment> activePunishments = this.activePunishments;

            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse(""));
            lore.add(
                    TextUtil.parse(
                            "<gray>Status: "
                                    + (target.isOnline() ? "<green>Online" : "<red>Offline")));
            lore.add(TextUtil.parse("<gray>Total Punishments: <white>" + punishmentCount));
            lore.add(
                    TextUtil.parse(
                            "<gray>Active Punishments: <yellow>" + activePunishments.size()));

            if (!activePunishments.isEmpty()) {
                lore.add(TextUtil.parse(""));
                lore.add(TextUtil.parse("<gold>Active:"));
                for (Punishment p : activePunishments) {
                    String remaining =
                            p.isPermanent() ? "Permanent" : p.getFormattedRemainingTime();
                    lore.add(
                            TextUtil.parse(
                                    "<gray>- <white>"
                                            + p.getType().getDisplayName()
                                            + " <gray>("
                                            + remaining
                                            + ")"));
                }
            }

            meta.displayName(TextUtil.parse("<yellow>" + target.getName()));
            meta.lore(lore);
            head.setItemMeta(meta);
        }

        setItem(4, new GuiItem(head));
    }

    private void setupTypeButtons() {
        // Row 3: Punishment type buttons at slots 19, 21, 23, 25

        // WARN button
        ItemStack warnItem =
                new ItemBuilder(Material.PAPER)
                        .name("<yellow>Warn")
                        .lore(
                                "",
                                "<gray>Issue a warning to the player",
                                "<gray>No immediate punishment applied",
                                "",
                                "<yellow>Click to select reason")
                        .build();

        setItem(
                19,
                GuiItem.withContext(
                        warnItem,
                        ctx -> {
                            ReasonSelectGui.openAsync(plugin, viewer, target, PunishmentType.WARN);
                        }));

        // MUTE button
        ItemStack muteItem =
                new ItemBuilder(Material.BOOK)
                        .name("<gold>Mute")
                        .lore(
                                "",
                                "<gray>Prevent player from chatting",
                                "<gray>Duration depends on offense count",
                                "",
                                "<yellow>Click to select reason")
                        .build();

        setItem(
                21,
                GuiItem.withContext(
                        muteItem,
                        ctx -> {
                            ReasonSelectGui.openAsync(plugin, viewer, target, PunishmentType.MUTE);
                        }));

        // KICK button
        ItemStack kickItem =
                new ItemBuilder(Material.LEATHER_BOOTS)
                        .name("<red>Kick")
                        .lore(
                                "",
                                "<gray>Remove player from server",
                                "<gray>They can rejoin immediately",
                                "",
                                "<yellow>Click to select reason")
                        .build();

        setItem(
                23,
                GuiItem.withContext(
                        kickItem,
                        ctx -> {
                            ReasonSelectGui.openAsync(plugin, viewer, target, PunishmentType.KICK);
                        }));

        // BAN button
        ItemStack banItem =
                new ItemBuilder(Material.BARRIER)
                        .name("<dark_red>Ban")
                        .lore(
                                "",
                                "<gray>Ban player from the server",
                                "<gray>Duration depends on offense count",
                                "",
                                "<yellow>Click to select reason")
                        .build();

        setItem(
                25,
                GuiItem.withContext(
                        banItem,
                        ctx -> {
                            ReasonSelectGui.openAsync(plugin, viewer, target, PunishmentType.BAN);
                        }));
    }

    private void setupRecentHistory() {
        // Use pre-loaded data instead of blocking DB calls
        List<Punishment> history = this.recentHistory;

        if (history.isEmpty()) {
            ItemStack noHistory =
                    new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                            .name("<gray>No Recent Punishments")
                            .build();
            setItem(38, new GuiItem(noHistory));
            setItem(40, new GuiItem(noHistory));
            setItem(42, new GuiItem(noHistory));
            return;
        }

        int[] slots = {38, 39, 40, 41, 42};
        for (int i = 0; i < Math.min(history.size(), 5); i++) {
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
            lore.add("<gray>Status: " + status);
            lore.add("<gray>Staff: <white>" + p.getStaffName());
            if (p.getDurationMs() != null) {
                lore.add("<gray>Duration: <white>" + p.getFormattedDuration());
            }
            if (p.getReason() != null) {
                lore.add("<gray>Reason: <white>" + p.getReason());
            }

            ItemStack item =
                    new ItemBuilder(material)
                            .name("<white>" + p.getType().getDisplayName() + " <gray>#" + p.getId())
                            .lore(lore.toArray(new String[0]))
                            .build();

            setItem(slots[i], new GuiItem(item));
        }
    }

    private void setupNavigation() {
        ItemStack historyItem =
                new ItemBuilder(Material.BOOK)
                        .name("<aqua>View Full History")
                        .lore(
                                "",
                                "<gray>View complete punishment history",
                                "",
                                "<yellow>Click to open")
                        .build();

        setItem(
                46,
                new GuiItem(
                        historyItem,
                        e -> {
                            viewer.closeInventory();
                            HistoryGui.openAsync(plugin, viewer, target);
                        }));

        ItemStack activeItem =
                new ItemBuilder(Material.BLAZE_POWDER)
                        .name("<red>Active Punishments")
                        .lore(
                                "",
                                "<gray>View and pardon active punishments",
                                "",
                                "<yellow>Click to open")
                        .build();

        setItem(
                47,
                new GuiItem(
                        activeItem,
                        e -> {
                            viewer.closeInventory();
                            ActivePunishmentsGui.openAsync(plugin, viewer, target);
                        }));

        ItemStack closeItem = new ItemBuilder(Material.BARRIER).name("<red>Close").build();

        setItem(49, new GuiItem(closeItem, e -> viewer.closeInventory()));
    }
}
