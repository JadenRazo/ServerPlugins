package net.serverplugins.filter.gui;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.messages.Placeholder;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.filter.ServerFilter;
import net.serverplugins.filter.data.FilterLevel;
import net.serverplugins.filter.data.FilterPreferenceManager;
import net.serverplugins.filter.data.WordCategory;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class FilterSettingsGui extends Gui {

    private final ServerFilter plugin;
    private final FilterPreferenceManager preferences;

    public FilterSettingsGui(
            ServerFilter plugin, Player viewer, FilterPreferenceManager preferences) {
        super(plugin, 27, Component.text("Chat Filter Settings", NamedTextColor.DARK_GRAY));
        this.plugin = plugin;
        this.preferences = preferences;
        this.viewer = viewer;
    }

    @Override
    protected void initializeItems() {
        FilterLevel current = preferences.getFilterLevel(viewer.getUniqueId());

        // Title item
        setItem(
                4,
                GuiItem.of(
                        new ItemBuilder(Material.PAPER)
                                .name(
                                        Component.text(
                                                "Chat Filter Settings",
                                                NamedTextColor.GOLD,
                                                TextDecoration.BOLD))
                                .lore(
                                        Component.empty(),
                                        Component.text(
                                                "Choose how you want to see chat.",
                                                NamedTextColor.GRAY),
                                        Component.text(
                                                "Slurs are always blocked.", NamedTextColor.RED))
                                .build()));

        // STRICT - slot 10
        setItem(10, createLevelItem(FilterLevel.STRICT, current, Material.SUNFLOWER));

        // MODERATE - slot 12
        setItem(12, createLevelItem(FilterLevel.MODERATE, current, Material.ORANGE_DYE));

        // RELAXED - slot 14
        setItem(14, createLevelItem(FilterLevel.RELAXED, current, Material.YELLOW_DYE));

        // MINIMAL - slot 16
        setItem(16, createLevelItem(FilterLevel.MINIMAL, current, Material.RED_DYE));

        // Info item
        setItem(
                22,
                GuiItem.of(
                        new ItemBuilder(Material.BARRIER)
                                .name(
                                        Component.text(
                                                "Important",
                                                NamedTextColor.RED,
                                                TextDecoration.BOLD))
                                .lore(
                                        Component.empty(),
                                        Component.text(
                                                "Slurs and hate speech are", NamedTextColor.GRAY),
                                        Component.text(
                                                        "ALWAYS",
                                                        NamedTextColor.RED,
                                                        TextDecoration.BOLD)
                                                .append(
                                                        Component.text(
                                                                " blocked regardless",
                                                                NamedTextColor.GRAY)),
                                        Component.text(
                                                "of your filter level.", NamedTextColor.GRAY),
                                        Component.empty(),
                                        Component.text(
                                                "This is non-negotiable.",
                                                NamedTextColor.DARK_GRAY))
                                .build()));

        // Fill empty slots
        ItemStack filler =
                new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(Component.empty()).build();
        fillEmpty(filler);
    }

    private GuiItem createLevelItem(FilterLevel level, FilterLevel current, Material icon) {
        boolean isSelected = level == current;
        boolean hasPermission =
                viewer.hasPermission("serverfilter.level." + level.name().toLowerCase());

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text(level.getDescription(), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("Blocks:", NamedTextColor.YELLOW));

        for (WordCategory category : WordCategory.values()) {
            boolean blocked = level.isBlocked(category);
            String status = blocked ? "\u2714 " : "\u2718 ";
            NamedTextColor color = blocked ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY;

            if (category == WordCategory.SLURS) {
                lore.add(
                        Component.text(
                                "  " + status + category.getDisplayName() + " (always)", color));
            } else {
                lore.add(Component.text("  " + status + category.getDisplayName(), color));
            }
        }

        lore.add(Component.empty());

        if (!hasPermission) {
            lore.add(Component.text("No permission for this level", NamedTextColor.RED));
        } else if (isSelected) {
            lore.add(Component.text("Currently selected", NamedTextColor.GREEN));
        } else {
            lore.add(Component.text("Click to select", NamedTextColor.YELLOW));
        }

        ItemBuilder builder =
                new ItemBuilder(icon)
                        .name(
                                Component.text(
                                        level.getDisplayName(),
                                        isSelected ? NamedTextColor.GREEN : NamedTextColor.WHITE,
                                        TextDecoration.BOLD))
                        .lore(lore);

        if (isSelected) {
            builder.glow();
        }

        return new GuiItem(
                builder.build(),
                player -> {
                    if (!hasPermission) {
                        plugin.getFilterConfig()
                                .getMessenger()
                                .send(player, "filter-level-no-permission");
                        return;
                    }

                    if (!isSelected) {
                        preferences.setFilterLevel(player.getUniqueId(), player.getName(), level);
                        plugin.getFilterConfig()
                                .getMessenger()
                                .send(
                                        player,
                                        "filter-level-set",
                                        Placeholder.of("level", level.getDisplayName()));

                        // Refresh the GUI and update player's view
                        refresh();
                        player.updateInventory();
                    }
                });
    }
}
