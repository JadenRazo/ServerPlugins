package net.serverplugins.afk.gui;

import net.serverplugins.afk.ServerAFK;
import net.serverplugins.afk.commands.AfkCommand;
import net.serverplugins.afk.models.AfkZone;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ZoneEditorGui extends Gui {

    private final ServerAFK plugin;
    private final AfkCommand command;
    private final AfkZone zone;

    public ZoneEditorGui(ServerAFK plugin, Player player, AfkCommand command, AfkZone zone) {
        super(plugin, player, "Edit Zone: " + zone.getName(), 45);
        this.plugin = plugin;
        this.command = command;
        this.zone = zone;
    }

    @Override
    protected void initializeItems() {
        // Fill with glass
        ItemStack glass = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        fillEmpty(new GuiItem(glass));

        // Title row
        ItemStack titleItem =
                new ItemBuilder(Material.ENDER_CHEST)
                        .name("<gold>" + zone.getName())
                        .lore(
                                "",
                                "<gray>World: <white>" + zone.getWorldName(),
                                "<gray>Size: <white>"
                                        + zone.getWidth()
                                        + "x"
                                        + zone.getHeight()
                                        + "x"
                                        + zone.getDepth())
                        .build();
        setItem(4, new GuiItem(titleItem));

        // Slot 10: Rename Zone
        ItemStack renameItem =
                new ItemBuilder(Material.NAME_TAG)
                        .name("<yellow>Rename Zone")
                        .lore(
                                "",
                                "<gray>Current: <white>" + zone.getName(),
                                "",
                                "<yellow>Click to rename")
                        .build();
        setItem(
                10,
                new GuiItem(
                        renameItem,
                        e -> {
                            viewer.closeInventory();
                            TextUtil.send(
                                    viewer,
                                    plugin.getAfkConfig().getPrefix()
                                            + "<yellow>Type the new zone name in chat:");

                            new ChatInputHandler(
                                    plugin,
                                    viewer,
                                    input -> {
                                        if (input.equalsIgnoreCase("cancel")) {
                                            TextUtil.send(
                                                    viewer,
                                                    plugin.getAfkConfig().getPrefix()
                                                            + "<gray>Rename cancelled.");
                                        } else if (plugin.getZoneManager().zoneExists(input)
                                                && !input.equalsIgnoreCase(zone.getName())) {
                                            TextUtil.send(
                                                    viewer,
                                                    plugin.getAfkConfig().getPrefix()
                                                            + "<red>A zone with that name already exists!");
                                        } else {
                                            zone.setName(input);
                                            plugin.getZoneManager().updateZone(zone);
                                            TextUtil.send(
                                                    viewer,
                                                    plugin.getAfkConfig().getPrefix()
                                                            + "<green>Zone renamed to: <white>"
                                                            + input);
                                        }
                                        new ZoneEditorGui(plugin, viewer, command, zone).open();
                                    });
                        }));

        // Slot 12: Set Interval
        ItemStack intervalItem =
                new ItemBuilder(Material.CLOCK)
                        .name("<aqua>Reward Interval")
                        .lore(
                                "",
                                "<gray>Current: <yellow>"
                                        + zone.getTimeIntervalSeconds()
                                        + " seconds",
                                "",
                                "<gray>How often players receive",
                                "<gray>rewards while in this zone.",
                                "",
                                "<green>Left-click <gray>+30 seconds",
                                "<red>Right-click <gray>-30 seconds",
                                "<yellow>Shift-click <gray>to type value")
                        .build();
        setItem(
                12,
                GuiItem.withContext(
                        intervalItem,
                        ctx -> {
                            if (ctx.isShiftClick()) {
                                viewer.closeInventory();
                                TextUtil.send(
                                        viewer,
                                        plugin.getAfkConfig().getPrefix()
                                                + "<yellow>Type the interval in seconds:");

                                new ChatInputHandler(
                                        plugin,
                                        viewer,
                                        input -> {
                                            try {
                                                int seconds = Integer.parseInt(input);
                                                if (seconds < 10) seconds = 10;
                                                if (seconds > 3600) seconds = 3600;
                                                zone.setTimeIntervalSeconds(seconds);
                                                plugin.getZoneManager().updateZone(zone);
                                                TextUtil.send(
                                                        viewer,
                                                        plugin.getAfkConfig().getPrefix()
                                                                + "<green>Interval set to: <white>"
                                                                + seconds
                                                                + "s");
                                            } catch (NumberFormatException ex) {
                                                TextUtil.send(
                                                        viewer,
                                                        plugin.getAfkConfig().getPrefix()
                                                                + "<red>Invalid number!");
                                            }
                                            new ZoneEditorGui(plugin, viewer, command, zone).open();
                                        });
                            } else if (ctx.isLeftClick()) {
                                int newInterval =
                                        Math.min(zone.getTimeIntervalSeconds() + 30, 3600);
                                zone.setTimeIntervalSeconds(newInterval);
                                plugin.getZoneManager().updateZone(zone);
                                reopenGui();
                            } else if (ctx.isRightClick()) {
                                int newInterval = Math.max(zone.getTimeIntervalSeconds() - 30, 10);
                                zone.setTimeIntervalSeconds(newInterval);
                                plugin.getZoneManager().updateZone(zone);
                                reopenGui();
                            }
                        }));

        // Slot 14: Toggle Enabled
        ItemStack toggleItem =
                new ItemBuilder(zone.isEnabled() ? Material.LEVER : Material.REDSTONE_TORCH)
                        .name("<green>Toggle Zone")
                        .lore(
                                "",
                                "<gray>Status: "
                                        + (zone.isEnabled() ? "<green>Enabled" : "<red>Disabled"),
                                "",
                                "<yellow>Click to toggle")
                        .glow(zone.isEnabled())
                        .build();
        setItem(
                14,
                new GuiItem(
                        toggleItem,
                        e -> {
                            zone.setEnabled(!zone.isEnabled());
                            plugin.getZoneManager().updateZone(zone);
                            reopenGui();
                        }));

        // Slot 16: Redefine Boundaries
        Location c1 = command.getCorner1(viewer.getUniqueId());
        Location c2 = command.getCorner2(viewer.getUniqueId());
        boolean hasSelection = c1 != null && c2 != null;

        String c1Status = c1 != null ? "<green>Set" : "<red>Not set";
        String c2Status = c2 != null ? "<green>Set" : "<red>Not set";

        ItemStack boundaryItem =
                new ItemBuilder(Material.WOODEN_AXE)
                        .name("<gold>Redefine Boundaries")
                        .lore(
                                "",
                                "<gray>Use your current selection",
                                "<gray>to update zone boundaries.",
                                "",
                                "<gray>Corner 1: " + c1Status,
                                "<gray>Corner 2: " + c2Status,
                                "",
                                hasSelection
                                        ? "<yellow>Click to update boundaries"
                                        : "<red>Set corners with /wa p1 and /wa p2")
                        .build();
        setItem(
                16,
                new GuiItem(
                        boundaryItem,
                        e -> {
                            if (!hasSelection) {
                                TextUtil.send(
                                        viewer,
                                        plugin.getAfkConfig().getMessage("selection-incomplete"));
                                return;
                            }

                            zone.setWorldName(c1.getWorld().getName());
                            zone.setMinX(Math.min(c1.getBlockX(), c2.getBlockX()));
                            zone.setMinY(Math.min(c1.getBlockY(), c2.getBlockY()));
                            zone.setMinZ(Math.min(c1.getBlockZ(), c2.getBlockZ()));
                            zone.setMaxX(Math.max(c1.getBlockX(), c2.getBlockX()));
                            zone.setMaxY(Math.max(c1.getBlockY(), c2.getBlockY()));
                            zone.setMaxZ(Math.max(c1.getBlockZ(), c2.getBlockZ()));

                            plugin.getZoneManager().updateZone(zone);
                            command.clearSelection(viewer.getUniqueId());
                            TextUtil.send(
                                    viewer,
                                    plugin.getAfkConfig().getPrefix()
                                            + "<green>Zone boundaries updated!");
                            reopenGui();
                        }));

        // Slot 28: Edit Rewards
        int rewardCount = zone.getRewards().size();
        ItemStack rewardsItem =
                new ItemBuilder(Material.GOLD_INGOT)
                        .name("<gold>Edit Rewards")
                        .lore(
                                "",
                                "<gray>Configure what players earn",
                                "<gray>for staying in this zone.",
                                "",
                                "<gray>Current rewards: <yellow>" + rewardCount,
                                "",
                                "<yellow>Click to open reward editor")
                        .build();
        setItem(
                28,
                new GuiItem(
                        rewardsItem,
                        e -> {
                            viewer.closeInventory();
                            new RewardEditorGui(plugin, viewer, command, zone).open();
                        }));

        // Slot 30: Rank Multipliers Toggle
        ItemStack multiplierItem =
                new ItemBuilder(
                                zone.usesRankMultipliers()
                                        ? Material.EXPERIENCE_BOTTLE
                                        : Material.GLASS_BOTTLE)
                        .name("<light_purple>Rank Multipliers")
                        .lore(
                                "",
                                "<gray>Status: "
                                        + (zone.usesRankMultipliers()
                                                ? "<green>Enabled"
                                                : "<red>Disabled"),
                                "",
                                "<gray>When enabled, paid ranks earn",
                                "<gray>bonus rewards based on their tier.",
                                "",
                                "<yellow>Click to toggle")
                        .glow(zone.usesRankMultipliers())
                        .build();
        setItem(
                30,
                new GuiItem(
                        multiplierItem,
                        e -> {
                            zone.setUseRankMultipliers(!zone.usesRankMultipliers());
                            plugin.getZoneManager().updateZone(zone);
                            reopenGui();
                        }));

        // Slot 34: Delete Zone
        ItemStack deleteItem =
                new ItemBuilder(Material.BARRIER)
                        .name("<red>Delete Zone")
                        .lore(
                                "",
                                "<gray>Permanently delete this zone.",
                                "<red>This cannot be undone!",
                                "",
                                "<red>Drop-click (Q) to confirm",
                                "<gray>Or use: <yellow>/wa delete " + zone.getName())
                        .build();
        setItem(
                34,
                GuiItem.withContext(
                        deleteItem,
                        ctx -> {
                            if (ctx.isDropClick()) {
                                String name = zone.getName();
                                plugin.getZoneManager().deleteZone(zone);
                                TextUtil.send(
                                        viewer,
                                        plugin.getAfkConfig()
                                                .getMessage("zone-deleted")
                                                .replace("{name}", name));
                                viewer.closeInventory();
                                new ZoneListGui(plugin, viewer, command).open();
                            } else {
                                TextUtil.send(
                                        viewer,
                                        plugin.getAfkConfig().getPrefix()
                                                + "<red>Drop-click (Q key) to confirm deletion!");
                            }
                        }));

        // Slot 40: Back
        ItemStack backItem =
                new ItemBuilder(Material.ARROW)
                        .name("<red>Back")
                        .lore("<gray>Return to zone list")
                        .build();
        setItem(
                40,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            new ZoneListGui(plugin, viewer, command).open();
                        }));
    }

    private void reopenGui() {
        viewer.closeInventory();
        new ZoneEditorGui(plugin, viewer, command, zone).open();
    }
}
