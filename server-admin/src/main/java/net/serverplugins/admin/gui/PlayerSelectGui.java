package net.serverplugins.admin.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public class PlayerSelectGui extends Gui {

    private final ServerAdmin plugin;
    private final String title;
    private final Consumer<Player> onSelect;
    private int page = 0;

    public PlayerSelectGui(
            ServerAdmin plugin, Player player, String title, Consumer<Player> onSelect) {
        super(plugin, player, "<dark_gray>" + title, 54);
        this.plugin = plugin;
        this.title = title;
        this.onSelect = onSelect;
    }

    @Override
    protected void initializeItems() {
        // Fill top and bottom rows
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 9; i++) {
            setItem(i, new GuiItem(filler));
            setItem(45 + i, new GuiItem(filler));
        }

        // Get online players (excluding self)
        List<Player> players = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.equals(viewer)) {
                // Check if player is vanished and viewer can't see them
                if (plugin.getVanishManager().isVanished(p)
                        && !viewer.hasPermission("serveradmin.vanish.see")) {
                    continue;
                }
                players.add(p);
            }
        }

        // Calculate pagination
        int playersPerPage = 36; // 4 rows of 9
        int totalPages = (int) Math.ceil(players.size() / (double) playersPerPage);
        int startIndex = page * playersPerPage;
        int endIndex = Math.min(startIndex + playersPerPage, players.size());

        // Add player heads
        int slot = 9;
        for (int i = startIndex; i < endIndex && slot < 45; i++) {
            Player target = players.get(i);

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(target);
                meta.displayName(TextUtil.parse("<yellow>" + target.getName()));

                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                lore.add(TextUtil.parse(""));

                // Add status info
                if (plugin.getVanishManager().isVanished(target)) {
                    lore.add(TextUtil.parse("<aqua>Vanished"));
                }
                if (plugin.getFreezeManager().isFrozen(target)) {
                    lore.add(TextUtil.parse("<blue>Frozen"));
                }
                if (plugin.getSpectateManager().isSpectating(target)) {
                    lore.add(TextUtil.parse("<light_purple>Spectating"));
                }

                lore.add(TextUtil.parse(""));
                lore.add(TextUtil.parse("<gray>World: <white>" + target.getWorld().getName()));
                lore.add(
                        TextUtil.parse(
                                "<gray>Health: <red>"
                                        + String.format("%.1f", target.getHealth())
                                        + "<gray>/<white>20"));
                lore.add(TextUtil.parse(""));
                lore.add(TextUtil.parse("<yellow>Click to select"));

                meta.lore(lore);
                head.setItemMeta(meta);
            }

            final Player finalTarget = target;
            setItem(
                    slot,
                    new GuiItem(
                            head,
                            e -> {
                                viewer.closeInventory();
                                onSelect.accept(finalTarget);
                            }));

            slot++;
        }

        // Fill empty slots
        ItemStack empty = new ItemBuilder(Material.LIGHT_GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = slot; i < 45; i++) {
            setItem(i, new GuiItem(empty));
        }

        // Navigation
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

        // Back button
        ItemStack back =
                new ItemBuilder(Material.BARRIER)
                        .name("<red>Back")
                        .lore("<gray>Return to admin menu")
                        .build();
        setItem(
                49,
                new GuiItem(
                        back,
                        e -> {
                            viewer.closeInventory();
                            new AdminMenuGui(plugin, viewer).open();
                        }));

        // Info
        ItemStack info =
                new ItemBuilder(Material.BOOK)
                        .name("<white>" + title)
                        .lore(
                                "",
                                "<gray>Online: <green>" + players.size() + " players",
                                "<gray>Page: <white>" + (page + 1) + "/" + Math.max(1, totalPages))
                        .build();
        setItem(4, new GuiItem(info));
    }
}
