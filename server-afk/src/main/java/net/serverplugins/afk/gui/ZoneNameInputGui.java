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

public class ZoneNameInputGui extends Gui {

    private final ServerAFK plugin;
    private final AfkCommand command;
    private final Location corner1;
    private final Location corner2;

    private static final String[] SUGGESTED_NAMES = {
        "AFK Area", "Spawn AFK", "VIP Lounge", "AFK Pit",
        "Rest Zone", "Idle Zone", "AFK Corner", "Chill Zone"
    };

    public ZoneNameInputGui(
            ServerAFK plugin,
            Player player,
            AfkCommand command,
            Location corner1,
            Location corner2) {
        super(plugin, player, "Choose Zone Name", 45);
        this.plugin = plugin;
        this.command = command;
        this.corner1 = corner1;
        this.corner2 = corner2;
    }

    @Override
    protected void initializeItems() {
        // Fill with glass
        ItemStack glass = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        fillEmpty(new GuiItem(glass));

        // Title/info
        ItemStack infoItem =
                new ItemBuilder(Material.NAME_TAG)
                        .name("<gold>Name Your Zone")
                        .lore(
                                "",
                                "<gray>Select a suggested name below,",
                                "<gray>or click the sign to enter",
                                "<gray>a custom name.",
                                "",
                                "<gray>Location: <white>" + corner1.getWorld().getName())
                        .build();
        setItem(4, new GuiItem(infoItem));

        // Suggested names in slots 19-26
        int[] slots = {19, 20, 21, 22, 23, 24, 25};
        for (int i = 0; i < slots.length && i < SUGGESTED_NAMES.length; i++) {
            String name = SUGGESTED_NAMES[i];
            boolean exists = plugin.getZoneManager().zoneExists(name);

            ItemStack nameItem =
                    new ItemBuilder(exists ? Material.RED_WOOL : Material.LIME_WOOL)
                            .name("<yellow>" + name)
                            .lore(
                                    "",
                                    exists
                                            ? "<red>Name already taken!"
                                            : "<green>Click to use this name")
                            .build();

            final String zoneName = name;
            setItem(
                    slots[i],
                    new GuiItem(
                            nameItem,
                            e -> {
                                if (exists) {
                                    TextUtil.send(
                                            viewer,
                                            plugin.getAfkConfig().getPrefix()
                                                    + "<red>That zone name is already taken!");
                                    return;
                                }
                                createZone(zoneName);
                            }));
        }

        // Custom name with sign - slot 31
        ItemStack customItem =
                new ItemBuilder(Material.OAK_SIGN)
                        .name("<aqua>Custom Name")
                        .lore(
                                "",
                                "<gray>Type a custom name in chat",
                                "<gray>after clicking this.",
                                "",
                                "<yellow>Click to enter custom name")
                        .build();
        setItem(
                31,
                new GuiItem(
                        customItem,
                        e -> {
                            viewer.closeInventory();
                            TextUtil.send(
                                    viewer,
                                    plugin.getAfkConfig().getPrefix()
                                            + "<yellow>Type the zone name in chat (or 'cancel' to abort):");

                            // Register chat listener for this player
                            new ChatInputHandler(
                                    plugin,
                                    viewer,
                                    input -> {
                                        if (input.equalsIgnoreCase("cancel")) {
                                            TextUtil.send(
                                                    viewer,
                                                    plugin.getAfkConfig().getPrefix()
                                                            + "<gray>Zone creation cancelled.");
                                            new AdminMainGui(plugin, viewer, command).open();
                                            return;
                                        }

                                        if (plugin.getZoneManager().zoneExists(input)) {
                                            TextUtil.send(
                                                    viewer,
                                                    plugin.getAfkConfig().getPrefix()
                                                            + "<red>A zone with that name already exists!");
                                            new ZoneNameInputGui(
                                                            plugin, viewer, command, corner1,
                                                            corner2)
                                                    .open();
                                            return;
                                        }

                                        createZone(input);
                                    });
                        }));

        // Back button - slot 40
        ItemStack backItem =
                new ItemBuilder(Material.ARROW)
                        .name("<red>Back")
                        .lore("<gray>Return to main menu")
                        .build();
        setItem(
                40,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            new AdminMainGui(plugin, viewer, command).open();
                        }));
    }

    private void createZone(String name) {
        AfkZone zone = plugin.getZoneManager().createZone(name, corner1, corner2);
        if (zone != null) {
            command.clearSelection(viewer.getUniqueId());
            TextUtil.send(
                    viewer,
                    plugin.getAfkConfig().getMessage("zone-created").replace("{name}", name));
            viewer.closeInventory();
            new ZoneEditorGui(plugin, viewer, command, zone).open();
        } else {
            TextUtil.send(
                    viewer, plugin.getAfkConfig().getPrefix() + "<red>Failed to create zone!");
            viewer.closeInventory();
            new AdminMainGui(plugin, viewer, command).open();
        }
    }
}
