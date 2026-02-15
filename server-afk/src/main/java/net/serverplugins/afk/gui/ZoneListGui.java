package net.serverplugins.afk.gui;

import java.util.ArrayList;
import java.util.List;
import net.serverplugins.afk.ServerAFK;
import net.serverplugins.afk.commands.AfkCommand;
import net.serverplugins.afk.models.AfkZone;
import net.serverplugins.afk.models.ZoneReward;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ZoneListGui extends Gui {

    private final ServerAFK plugin;
    private final AfkCommand command;
    private final int page;
    private static final int ZONES_PER_PAGE = 28;

    public ZoneListGui(ServerAFK plugin, Player player, AfkCommand command) {
        this(plugin, player, command, 0);
    }

    public ZoneListGui(ServerAFK plugin, Player player, AfkCommand command, int page) {
        super(plugin, player, "AFK Zones - Page " + (page + 1), 54);
        this.plugin = plugin;
        this.command = command;
        this.page = page;
    }

    @Override
    protected void initializeItems() {
        // Fill border
        ItemStack glass = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        fillBorder(new GuiItem(glass));

        // Title item
        ItemStack titleItem =
                new ItemBuilder(Material.ENDER_CHEST)
                        .name("<gold>AFK Zones")
                        .lore(
                                "",
                                "<gray>Manage your AFK zones here.",
                                "",
                                "<green>Left-click <gray>to edit zone",
                                "<red>Drop-click (Q) <gray>to delete")
                        .build();
        setItem(4, new GuiItem(titleItem));

        // Zone items in slots 10-16, 19-25, 28-34, 37-43
        int[] slots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };

        List<AfkZone> zones = plugin.getZoneManager().getAllZones();
        int startIndex = page * ZONES_PER_PAGE;
        int endIndex = Math.min(startIndex + ZONES_PER_PAGE, zones.size());

        for (int i = 0; i < slots.length; i++) {
            int zoneIndex = startIndex + i;
            if (zoneIndex < endIndex) {
                AfkZone zone = zones.get(zoneIndex);
                setItem(slots[i], createZoneItem(zone));
            } else {
                // Empty slot
                ItemStack empty =
                        new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
                setItem(slots[i], new GuiItem(empty));
            }
        }

        // Previous page - slot 45
        if (page > 0) {
            ItemStack prevItem =
                    new ItemBuilder(Material.ARROW)
                            .name("<yellow>Previous Page")
                            .lore("<gray>Go to page " + page)
                            .build();
            setItem(
                    45,
                    new GuiItem(
                            prevItem,
                            e -> {
                                viewer.closeInventory();
                                new ZoneListGui(plugin, viewer, command, page - 1).open();
                            }));
        }

        // Back button - slot 49
        ItemStack backItem =
                new ItemBuilder(Material.BARRIER)
                        .name("<red>Back")
                        .lore("<gray>Return to main menu")
                        .build();
        setItem(
                49,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            new AdminMainGui(plugin, viewer, command).open();
                        }));

        // Next page - slot 53
        int totalPages = (int) Math.ceil(zones.size() / (double) ZONES_PER_PAGE);
        if (page < totalPages - 1) {
            ItemStack nextItem =
                    new ItemBuilder(Material.ARROW)
                            .name("<yellow>Next Page")
                            .lore("<gray>Go to page " + (page + 2))
                            .build();
            setItem(
                    53,
                    new GuiItem(
                            nextItem,
                            e -> {
                                viewer.closeInventory();
                                new ZoneListGui(plugin, viewer, command, page + 1).open();
                            }));
        }
    }

    private GuiItem createZoneItem(AfkZone zone) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<gray>World: <white>" + zone.getWorldName());
        lore.add(
                "<gray>Size: <white>"
                        + zone.getWidth()
                        + "x"
                        + zone.getHeight()
                        + "x"
                        + zone.getDepth());
        lore.add("<gray>Reward Interval: <yellow>" + zone.getTimeIntervalSeconds() + "s");
        lore.add("");

        if (!zone.getRewards().isEmpty()) {
            lore.add("<gray>Rewards:");
            for (ZoneReward reward : zone.getRewards()) {
                lore.add("  <white>- " + reward.getDisplayName());
            }
        } else {
            lore.add("<gray>Rewards: <red>None configured");
        }

        lore.add("");
        lore.add("<gray>Status: " + (zone.isEnabled() ? "<green>Enabled" : "<red>Disabled"));
        lore.add("");
        lore.add("<green>Left-click <gray>to edit");
        lore.add("<red>Drop-click (Q) <gray>to delete");
        lore.add("<gray>Or use: <yellow>/wa delete " + zone.getName());

        ItemStack item =
                new ItemBuilder(zone.isEnabled() ? Material.ENDER_CHEST : Material.CHEST)
                        .name("<gold>" + zone.getName())
                        .lore(lore.toArray(new String[0]))
                        .glow(zone.isEnabled())
                        .build();

        return GuiItem.withContext(
                item,
                ctx -> {
                    if (ctx.isDropClick()) {
                        // Delete zone
                        plugin.getZoneManager().deleteZone(zone);
                        TextUtil.send(
                                viewer,
                                plugin.getAfkConfig()
                                        .getMessage("zone-deleted")
                                        .replace("{name}", zone.getName()));
                        viewer.closeInventory();
                        new ZoneListGui(plugin, viewer, command, page).open();
                    } else if (ctx.isLeftClick()) {
                        // Edit zone
                        viewer.closeInventory();
                        new ZoneEditorGui(plugin, viewer, command, zone).open();
                    }
                });
    }
}
