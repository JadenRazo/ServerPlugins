package net.serverplugins.claim.gui;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.NotificationType;
import net.serverplugins.claim.models.PlayerNotification;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** GUI for viewing and managing notifications. */
public class NotificationsGui extends Gui {

    private final ServerClaim plugin;
    private final int page;
    private final NotificationType filter;
    private static final int PAGE_SIZE = 36;
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd, HH:mm");

    public NotificationsGui(ServerClaim plugin, Player player) {
        this(plugin, player, 1, null);
    }

    public NotificationsGui(ServerClaim plugin, Player player, int page, NotificationType filter) {
        super(
                plugin,
                player,
                "Notifications " + (filter != null ? "- " + filter.getDisplayName() : ""),
                54);
        this.plugin = plugin;
        this.page = page;
        this.filter = filter;
    }

    @Override
    protected void initializeItems() {
        if (viewer == null) {
            plugin.getLogger().warning("NotificationsGui: Viewer is null");
            return;
        }

        setupHeader();
        setupNotificationsList();
        setupFooter();
    }

    private void setupHeader() {
        int unreadCount = plugin.getNotificationManager().getUnreadCount(viewer.getUniqueId());

        // Title item
        ItemStack titleItem =
                new ItemBuilder(Material.BELL)
                        .name("<gold>Your Notifications")
                        .lore(
                                "",
                                "<gray>Unread: <yellow>" + unreadCount,
                                "",
                                filter != null
                                        ? "<gray>Filtering: <white>" + filter.getDisplayName()
                                        : "<gray>Showing all notifications",
                                "",
                                "<gray>Page: <white>" + page)
                        .build();
        setItem(4, new GuiItem(titleItem));

        // Mark all as read button
        if (unreadCount > 0) {
            ItemStack markAllRead =
                    new ItemBuilder(Material.GREEN_WOOL)
                            .name("<green>Mark All as Read")
                            .lore(
                                    "",
                                    "<gray>Click to mark all " + unreadCount,
                                    "<gray>notifications as read")
                            .build();
            setItem(
                    1,
                    GuiItem.withContext(
                            markAllRead,
                            ctx -> {
                                plugin.getNotificationManager().markAllAsRead(viewer.getUniqueId());
                                TextUtil.send(
                                        viewer,
                                        ColorScheme.SUCCESS + "Marked all notifications as read!");
                                viewer.closeInventory();
                            }));
        }

        // Delete read notifications button
        ItemStack deleteRead =
                new ItemBuilder(Material.LAVA_BUCKET)
                        .name("<red>Delete Read Notifications")
                        .lore(
                                "",
                                "<gray>Click to delete all",
                                "<gray>notifications you've already read")
                        .build();
        setItem(
                2,
                GuiItem.withContext(
                        deleteRead,
                        ctx -> {
                            plugin.getNotificationManager()
                                    .deleteReadNotifications(viewer.getUniqueId());
                            TextUtil.send(
                                    viewer,
                                    ColorScheme.SUCCESS + "Deleted all read notifications!");
                            viewer.closeInventory();
                            new NotificationsGui(plugin, viewer, page, filter).open();
                        }));

        // Filter buttons
        setupFilterButtons();

        // Fill header with glass
        ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 9; i++) {
            if (getItem(i) == null) {
                setItem(i, new GuiItem(filler));
            }
        }
    }

    private void setupFilterButtons() {
        // All notifications filter
        ItemStack allFilter =
                new ItemBuilder(Material.PAPER)
                        .name(
                                filter == null
                                        ? "<yellow>All Notifications"
                                        : "<gray>All Notifications")
                        .lore("", "<gray>Click to show all types")
                        .glow(filter == null)
                        .build();
        setItem(
                7,
                GuiItem.withContext(
                        allFilter,
                        ctx -> {
                            viewer.closeInventory();
                            new NotificationsGui(plugin, viewer, 1, null).open();
                        }));

        // Specific type filters in row 2
        int slot = 9;
        for (NotificationType type : NotificationType.values()) {
            boolean active = type == filter;
            ItemStack filterItem =
                    new ItemBuilder(type.getIcon())
                            .name((active ? "<yellow>" : "<gray>") + type.getDisplayName())
                            .lore(
                                    "",
                                    "<gray>" + type.getDefaultMessage(),
                                    "",
                                    active
                                            ? "<yellow>Currently filtering"
                                            : "<gray>Click to filter")
                            .glow(active)
                            .build();

            final NotificationType filterType = type;
            setItem(
                    slot++,
                    GuiItem.withContext(
                            filterItem,
                            ctx -> {
                                viewer.closeInventory();
                                new NotificationsGui(plugin, viewer, 1, filterType).open();
                            }));

            if (slot >= 18) break; // Limit to one row
        }
    }

    private void setupNotificationsList() {
        List<PlayerNotification> notifications;

        if (filter != null) {
            notifications =
                    plugin.getNotificationManager()
                            .getNotificationsByType(viewer.getUniqueId(), filter, PAGE_SIZE);
        } else {
            notifications =
                    plugin.getNotificationManager()
                            .getNotifications(viewer.getUniqueId(), page, PAGE_SIZE);
        }

        if (notifications.isEmpty()) {
            ItemStack noNotifications =
                    new ItemBuilder(Material.FEATHER)
                            .name("<gray>No Notifications")
                            .lore(
                                    "",
                                    filter != null
                                            ? "<gray>No "
                                                    + filter.getDisplayName().toLowerCase()
                                                    + " notifications"
                                            : "<gray>You don't have any notifications",
                                    "",
                                    "<gray>Notifications will appear here when:",
                                    "<gray>- You receive a nation invite",
                                    "<gray>- Upkeep payment is due",
                                    "<gray>- War is declared",
                                    "<gray>- Claim ownership is transferred",
                                    "<gray>- And more!")
                            .build();
            setItem(31, new GuiItem(noNotifications));
            return;
        }

        int slot = 18;
        for (PlayerNotification notification : notifications) {
            if (slot >= 45) break; // Max 27 notifications (3 rows)

            ItemStack notifItem = createNotificationItem(notification);
            final PlayerNotification notif = notification;

            setItem(
                    slot++,
                    GuiItem.withContext(
                            notifItem,
                            ctx -> {
                                if (ctx.isLeftClick()) {
                                    // Mark as read
                                    if (!notif.isRead()) {
                                        plugin.getNotificationManager()
                                                .markAsRead(notif.getId(), viewer.getUniqueId());
                                        TextUtil.send(
                                                viewer,
                                                ColorScheme.SUCCESS
                                                        + "Marked notification as read!");
                                    }

                                    // Execute action if exists
                                    if (notif.getActionButton() != null
                                            && !notif.getActionButton().isEmpty()) {
                                        viewer.closeInventory();
                                        viewer.performCommand(
                                                notif.getActionButton().replace("/", ""));
                                    }
                                } else if (ctx.isRightClick()) {
                                    // Delete notification
                                    plugin.getNotificationManager()
                                            .deleteNotification(
                                                    notif.getId(), viewer.getUniqueId());
                                    TextUtil.send(
                                            viewer, ColorScheme.ERROR + "Deleted notification!");
                                    viewer.closeInventory();
                                    new NotificationsGui(plugin, viewer, page, filter).open();
                                }
                            }));
        }
    }

    private ItemStack createNotificationItem(PlayerNotification notification) {
        List<String> lore = new ArrayList<>();
        lore.add("");

        // Message
        lore.add("<white>" + notification.getMessage());
        lore.add("");

        // Priority
        lore.add(
                "<gray>Priority: "
                        + notification.getPriority().getColor()
                        + notification.getPriority().getDisplayName());

        // Time
        String timeStr =
                TIME_FORMAT.format(
                        notification.getCreatedAt().atZone(java.time.ZoneId.systemDefault()));
        lore.add("<gray>Time: <white>" + timeStr);

        // Read status
        if (notification.isRead()) {
            String readTime =
                    TIME_FORMAT.format(
                            notification.getReadAt().atZone(java.time.ZoneId.systemDefault()));
            lore.add("<gray>Read: <green>" + readTime);
        } else {
            lore.add("<yellow>Unread");
        }

        lore.add("");

        // Actions
        if (notification.getActionButton() != null && !notification.getActionButton().isEmpty()) {
            lore.add("<green>Left-Click: <white>Execute action");
            lore.add("<gray>" + notification.getActionButton());
        } else if (!notification.isRead()) {
            lore.add("<green>Left-Click: <white>Mark as read");
        }

        lore.add("<red>Right-Click: <white>Delete");

        // Determine icon - use notification type icon or special icon for read status
        Material icon = notification.getType().getIcon();
        if (notification.isRead()) {
            icon = Material.PAPER; // Read notifications use paper
        }

        return new ItemBuilder(icon)
                .name(notification.getPriority().getColor() + notification.getTitle())
                .lore(lore.toArray(new String[0]))
                .glow(!notification.isRead())
                .build();
    }

    private void setupFooter() {
        // Navigation buttons
        if (page > 1) {
            ItemStack prevPage =
                    new ItemBuilder(Material.ARROW)
                            .name("<yellow>Previous Page")
                            .lore("", "<gray>Go to page " + (page - 1))
                            .build();
            setItem(
                    48,
                    GuiItem.withContext(
                            prevPage,
                            ctx -> {
                                viewer.closeInventory();
                                new NotificationsGui(plugin, viewer, page - 1, filter).open();
                            }));
        }

        ItemStack nextPage =
                new ItemBuilder(Material.ARROW)
                        .name("<yellow>Next Page")
                        .lore("", "<gray>Go to page " + (page + 1))
                        .build();
        setItem(
                50,
                GuiItem.withContext(
                        nextPage,
                        ctx -> {
                            viewer.closeInventory();
                            new NotificationsGui(plugin, viewer, page + 1, filter).open();
                        }));

        // Back/Close button
        ItemStack close = new ItemBuilder(Material.BARRIER).name("<red>Close").build();
        setItem(49, GuiItem.withContext(close, ctx -> viewer.closeInventory()));

        // Fill footer with glass
        ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 45; i < 54; i++) {
            if (getItem(i) == null) {
                setItem(i, new GuiItem(filler));
            }
        }
    }
}
