package net.serverplugins.keys.gui;

import static net.serverplugins.api.messages.ColorScheme.EMPHASIS;
import static net.serverplugins.api.messages.ColorScheme.ERROR;
import static net.serverplugins.api.messages.ColorScheme.HIGHLIGHT;
import static net.serverplugins.api.messages.ColorScheme.INFO;
import static net.serverplugins.api.messages.ColorScheme.SECONDARY;
import static net.serverplugins.api.messages.ColorScheme.WARNING;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.keys.KeysConfig;
import net.serverplugins.keys.ServerKeys;
import net.serverplugins.keys.cache.StatsCache;
import net.serverplugins.keys.models.KeyStats;
import net.serverplugins.keys.models.KeyType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** GUI for displaying player key statistics. Uses StatsCache for non-blocking data access. */
public class KeyBalanceGui implements InventoryHolder, Listener {

    private final ServerKeys plugin;
    private final KeysConfig config;
    private final StatsCache statsCache;
    private final Player viewer;
    private final UUID targetUuid;
    private final String targetName;
    private Inventory inventory;

    public KeyBalanceGui(
            ServerKeys plugin,
            KeysConfig config,
            StatsCache statsCache,
            Player viewer,
            UUID targetUuid,
            String targetName) {
        this.plugin = plugin;
        this.config = config;
        this.statsCache = statsCache;
        this.viewer = viewer;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
    }

    /**
     * Opens the GUI. Uses cached data for instant display. Cache is warmed on player join, so this
     * should be fast.
     */
    public void open() {
        // Get stats from cache - this is non-blocking
        List<KeyStats> stats = statsCache.getPlayerStats(targetUuid);

        String title =
                targetUuid.equals(viewer.getUniqueId()) ? "Your Keys" : targetName + "'s Keys";
        inventory =
                Bukkit.createInventory(
                        this,
                        54,
                        TextUtil.parse("<gradient:#FFD700:#FFA500>" + title + "</gradient>"));

        // Fill background
        ItemStack filler = createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, filler);
        }

        // Header info item (slot 4)
        int totalReceived = stats.stream().mapToInt(KeyStats::getTotalReceived).sum();
        int totalUsed = stats.stream().mapToInt(KeyStats::getTotalUsed).sum();

        List<String> headerLore = new ArrayList<>();
        headerLore.add("");
        headerLore.add(INFO + "Total Keys Received: " + HIGHLIGHT + totalReceived);
        headerLore.add(INFO + "Total Keys Used: " + HIGHLIGHT + totalUsed);
        headerLore.add("");

        inventory.setItem(
                4, createItem(Material.NETHER_STAR, EMPHASIS + "Key Statistics", headerLore));

        // Crate Keys section (slots 19-24)
        inventory.setItem(
                10,
                createItem(
                        Material.CHEST,
                        WARNING + "Crate Keys",
                        List.of(INFO + "Keys for reward crates")));

        int crateSlot = 19;
        for (String keyName : config.getCrateKeys()) {
            KeyStats stat = findStats(stats, KeyType.CRATE, keyName);
            int received = stat != null ? stat.getTotalReceived() : 0;
            int used = stat != null ? stat.getTotalUsed() : 0;

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(INFO + "Received: " + HIGHLIGHT + received);
            lore.add(INFO + "Used: " + HIGHLIGHT + used);
            lore.add("");
            lore.add(SECONDARY + config.getKeyDescription(KeyType.CRATE, keyName));

            ItemStack item =
                    createItem(
                            Material.TRIPWIRE_HOOK,
                            EMPHASIS + config.getKeyDisplay(KeyType.CRATE, keyName) + " Key",
                            lore);
            if (received > 0) {
                addGlow(item);
            }
            inventory.setItem(crateSlot++, item);

            if (crateSlot > 24) break;
        }

        // Dungeon Keys section (slots 28-33)
        inventory.setItem(
                28,
                createItem(
                        Material.MOSSY_COBBLESTONE,
                        WARNING + "Dungeon Keys",
                        List.of(INFO + "Keys for dungeon arenas")));

        int dungeonSlot = 29;
        for (String keyName : config.getDungeonKeys()) {
            KeyStats stat = findStats(stats, KeyType.DUNGEON, keyName);
            int received = stat != null ? stat.getTotalReceived() : 0;
            int used = stat != null ? stat.getTotalUsed() : 0;

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(INFO + "Received: " + HIGHLIGHT + received);
            lore.add(INFO + "Used: " + HIGHLIGHT + used);
            lore.add("");
            lore.add(SECONDARY + config.getKeyDescription(KeyType.DUNGEON, keyName));

            ItemStack item =
                    createItem(
                            Material.STICK,
                            "<#8B4513>" + config.getKeyDisplay(KeyType.DUNGEON, keyName) + " Key",
                            lore);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(config.getDungeonKeyModelData(keyName));
                item.setItemMeta(meta);
            }
            if (received > 0) {
                addGlow(item);
            }
            inventory.setItem(dungeonSlot++, item);

            if (dungeonSlot > 33) break;
        }

        // Close button (slot 49)
        inventory.setItem(
                49,
                createItem(Material.BARRIER, ERROR + "Close", List.of(INFO + "Click to close")));

        // Register listener and open
        Bukkit.getPluginManager().registerEvents(this, plugin);
        viewer.openInventory(inventory);
    }

    private KeyStats findStats(List<KeyStats> stats, KeyType type, String keyName) {
        return stats.stream()
                .filter(s -> s.getKeyType() == type && s.getKeyName().equalsIgnoreCase(keyName))
                .findFirst()
                .orElse(null);
    }

    private ItemStack createItem(Material material, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.parse(name));
            if (loreLines != null && !loreLines.isEmpty()) {
                List<Component> lore = loreLines.stream().map(TextUtil::parse).toList();
                meta.lore(lore);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void addGlow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);

        if (event.getCurrentItem() == null) return;
        if (event.getCurrentItem().getType() == Material.BARRIER) {
            viewer.closeInventory();
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        HandlerList.unregisterAll(this);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
