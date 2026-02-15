package net.serverplugins.parkour.gui;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.parkour.ServerParkour;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class ParkourGui implements InventoryHolder {

    public static final Component INVENTORY_TITLE =
            TextUtil.parse("<light_purple><bold>Parkour</bold></light_purple>");
    public static final int START_SLOT = 13;

    private final ServerParkour plugin;
    private final Inventory inventory;

    public ParkourGui(ServerParkour plugin) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, 27, INVENTORY_TITLE);
        setupItems();
    }

    private void setupItems() {
        // Fill with gray glass panes as background
        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, filler);
        }

        // Center slot (13) - Pink glass pane to start parkour
        ItemStack startItem =
                createItem(
                        Material.PINK_STAINED_GLASS_PANE,
                        "<light_purple><bold>Start Parkour</bold></light_purple>",
                        "<gray>Click to begin your</gray>",
                        "<gray>parkour adventure!</gray>",
                        "",
                        "<yellow>Click to play!</yellow>");
        inventory.setItem(START_SLOT, startItem);

        // Stats item (slot 11)
        ItemStack statsItem =
                createItem(
                        Material.BOOK,
                        "<aqua><bold>Your Stats</bold></aqua>",
                        "<gray>View your parkour</gray>",
                        "<gray>statistics and highscore</gray>",
                        "",
                        "<yellow>Click to view!</yellow>");
        inventory.setItem(11, statsItem);

        // Leaderboard item (slot 15)
        ItemStack leaderboardItem =
                createItem(
                        Material.DIAMOND,
                        "<gold><bold>Leaderboard</bold></gold>",
                        "<gray>See the top parkour</gray>",
                        "<gray>players!</gray>",
                        "",
                        "<yellow>Click to view!</yellow>");
        inventory.setItem(15, leaderboardItem);
    }

    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.parse(name));
            if (lore.length > 0) {
                List<Component> loreComponents = new java.util.ArrayList<>();
                for (String line : lore) {
                    loreComponents.add(TextUtil.parse(line));
                }
                meta.lore(loreComponents);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public ServerParkour getPlugin() {
        return plugin;
    }
}
