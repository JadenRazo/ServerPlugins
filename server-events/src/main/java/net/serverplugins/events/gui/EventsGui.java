package net.serverplugins.events.gui;

import java.util.Arrays;
import java.util.List;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.events.EventsConfig;
import net.serverplugins.events.ServerEvents;
import net.serverplugins.events.events.EventManager;
import net.serverplugins.events.events.ServerEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Admin GUI for managing events. */
public class EventsGui implements InventoryHolder, Listener {

    private final ServerEvents plugin;
    private final Player player;
    private final Inventory inventory;
    private final EventsConfig config;

    // Slot positions (7 events + random + stop)
    private static final int SLOT_STATUS = 4;
    private static final int SLOT_PINATA = 10;
    private static final int SLOT_SPELLING = 11;
    private static final int SLOT_CRAFTING = 12;
    private static final int SLOT_DRAGON = 13;
    private static final int SLOT_MATH = 14;
    private static final int SLOT_DROP_PARTY = 15;
    private static final int SLOT_RANDOM = 16;
    private static final int SLOT_PREMIUM_PINATA = 19;
    private static final int SLOT_STOP = 22;

    public EventsGui(ServerEvents plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.config = plugin.getEventsConfig();
        this.inventory = Bukkit.createInventory(this, 27, config.getGuiTitle());

        buildGui();
    }

    private void buildGui() {
        // Fill with glass panes
        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }

        // Status indicator
        EventManager manager = plugin.getEventManager();
        boolean hasActive = manager.hasActiveEvent();

        ItemStack statusItem;
        if (hasActive) {
            ServerEvent event = manager.getActiveEvent();
            statusItem =
                    createItem(
                            Material.LIME_WOOL,
                            "<green><bold>Active Event",
                            Arrays.asList(
                                    "<gray>Event: <white>" + event.getDisplayName(),
                                    "",
                                    "<gray>Click <red>Stop <gray>to end it"));
        } else {
            int nextEvent = manager.getTimeUntilNextEvent();
            String nextStr =
                    nextEvent >= 0 ? (nextEvent / 60) + "m " + (nextEvent % 60) + "s" : "Disabled";

            statusItem =
                    createItem(
                            Material.RED_WOOL,
                            "<red><bold>No Active Event",
                            Arrays.asList(
                                    "<gray>Next random event in:",
                                    "<white>" + nextStr,
                                    "",
                                    "<gray>Click an event to trigger it"));
        }
        inventory.setItem(SLOT_STATUS, statusItem);

        // Pinata event
        inventory.setItem(
                SLOT_PINATA,
                createItem(
                        Material.LEAD,
                        "<gold><bold>Pinata Event",
                        Arrays.asList(
                                "<gray>Spawns a pinata at spawn",
                                "<gray>that players click to break!",
                                "",
                                "<gray>Clicks: <white>"
                                        + config.getPinataClicksMin()
                                        + "-"
                                        + config.getPinataClicksMax(),
                                "<gray>Reward: <white>$" + config.getPinataBreakerCoins(),
                                "",
                                "<yellow>Click to trigger!")));

        // Spelling event
        inventory.setItem(
                SLOT_SPELLING,
                createItem(
                        Material.WRITABLE_BOOK,
                        "<aqua><bold>Spelling Bee",
                        Arrays.asList(
                                "<gray>Players race to type a",
                                "<gray>hidden word in chat!",
                                "",
                                "<gray>Time: <white>" + config.getSpellingTimeLimit() + " seconds",
                                "<gray>Reward: <white>$" + config.getSpellingCoins(),
                                "",
                                "<yellow>Click to trigger!")));

        // Crafting event
        inventory.setItem(
                SLOT_CRAFTING,
                createItem(
                        Material.CRAFTING_TABLE,
                        "<light_purple><bold>Crafting Challenge",
                        Arrays.asList(
                                "<gray>Players race to craft or",
                                "<gray>smelt specific items!",
                                "",
                                "<gray>Challenges: <white>"
                                        + config.getCraftingChallengeKeys().size(),
                                "",
                                "<yellow>Click to trigger random!",
                                "<gray>Use command for specific")));

        // Dragon event
        inventory.setItem(
                SLOT_DRAGON,
                createItem(
                        Material.DRAGON_HEAD,
                        "<dark_purple><bold>Dragon Fight",
                        Arrays.asList(
                                "<gray>Epic boss fight against",
                                "<gray>the Ender Dragon!",
                                "",
                                "<gray>World: <white>" + config.getDragonWorld(),
                                "<gray>Base Coins: <white>$2,000+",
                                "<gray>Keys: <white>Dungeon & Crate",
                                "",
                                "<yellow>Click to trigger!")));

        // Math event
        inventory.setItem(
                SLOT_MATH,
                createItem(
                        Material.CLOCK,
                        "<red><bold>Math Race",
                        Arrays.asList(
                                "<gray>Players race to solve a",
                                "<gray>math problem in chat!",
                                "",
                                "<gray>Time: <white>" + config.getMathTimeLimit() + " seconds",
                                "<gray>Reward: <white>$" + config.getMathCoins(),
                                "",
                                "<yellow>Click to trigger!")));

        // Drop Party event
        inventory.setItem(
                SLOT_DROP_PARTY,
                createItem(
                        Material.CHEST,
                        "<yellow><bold>Drop Party",
                        Arrays.asList(
                                "<gray>Items rain from the sky",
                                "<gray>at spawn for players!",
                                "",
                                "<gray>Total Drops: <white>" + config.getDropPartyTotalDrops(),
                                "",
                                "<yellow>Click to trigger!")));

        // Premium Pinata event
        inventory.setItem(
                SLOT_PREMIUM_PINATA,
                createItem(
                        Material.NETHER_STAR,
                        "<light_purple><bold>★ Premium Pinata",
                        Arrays.asList(
                                "<gray>Special pinata with",
                                "<light_purple>2x rewards <gray>+ <aqua>bonus keys<gray>!",
                                "",
                                "<gray>Clicks: <white>"
                                        + config.getPremiumPinataClicksMin()
                                        + "-"
                                        + config.getPremiumPinataClicksMax(),
                                "<gray>Total Pool: <gold>$"
                                        + String.format("%,d", config.getPremiumPinataTotalCoins()),
                                "<gray>Keys: <green>Diversity<gray>, <dark_purple>Epic<gray>, <yellow>Dungeon",
                                "",
                                "<yellow>Click to trigger!")));

        // Random event
        inventory.setItem(
                SLOT_RANDOM,
                createItem(
                        Material.ENDER_EYE,
                        "<green><bold>Random Event",
                        Arrays.asList(
                                "<gray>Triggers a random event",
                                "<gray>and resets the timer!",
                                "",
                                "<yellow>Click to trigger!")));

        // Stop button
        inventory.setItem(
                SLOT_STOP,
                createItem(
                        hasActive ? Material.BARRIER : Material.STRUCTURE_VOID,
                        hasActive ? "<red><bold>Stop Event" : "<gray><bold>No Event Active",
                        hasActive
                                ? Arrays.asList("<gray>Click to stop the", "<gray>current event")
                                : Arrays.asList("<gray>No event to stop")));
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Parse name as MiniMessage
            meta.displayName(TextUtil.parse(name));
            if (lore != null) {
                // Parse each lore line as MiniMessage
                meta.lore(lore.stream().map(TextUtil::parse).toList());
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public void open() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player clicker)) return;
        if (!clicker.equals(player)) return;

        int slot = event.getRawSlot();
        EventManager manager = plugin.getEventManager();

        switch (slot) {
            case SLOT_PINATA -> {
                if (triggerEvent(ServerEvent.EventType.PINATA)) {
                    clicker.closeInventory();
                }
            }
            case SLOT_SPELLING -> {
                if (triggerEvent(ServerEvent.EventType.SPELLING)) {
                    clicker.closeInventory();
                }
            }
            case SLOT_CRAFTING -> {
                if (triggerEvent(ServerEvent.EventType.CRAFTING)) {
                    clicker.closeInventory();
                }
            }
            case SLOT_DRAGON -> {
                if (triggerEvent(ServerEvent.EventType.DRAGON)) {
                    clicker.closeInventory();
                }
            }
            case SLOT_MATH -> {
                if (triggerEvent(ServerEvent.EventType.MATH)) {
                    clicker.closeInventory();
                }
            }
            case SLOT_DROP_PARTY -> {
                if (triggerEvent(ServerEvent.EventType.DROP_PARTY)) {
                    clicker.closeInventory();
                }
            }
            case SLOT_PREMIUM_PINATA -> {
                if (triggerEvent(ServerEvent.EventType.PREMIUM_PINATA)) {
                    clicker.closeInventory();
                }
            }
            case SLOT_RANDOM -> {
                if (manager.hasActiveEvent()) {
                    TextUtil.sendError(clicker, "An event is already active!");
                    clicker.playSound(clicker.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                } else if (manager.triggerRandomEvent()) {
                    TextUtil.sendSuccess(clicker, "Triggered random event!");
                    clicker.playSound(
                            clicker.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    clicker.closeInventory();
                }
            }
            case SLOT_STOP -> {
                if (manager.stopCurrentEvent()) {
                    TextUtil.sendSuccess(clicker, "Stopped the current event.");
                    clicker.playSound(
                            clicker.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                    buildGui(); // Refresh
                } else {
                    TextUtil.sendError(clicker, "No active event to stop.");
                    clicker.playSound(clicker.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
            }
        }
    }

    private boolean triggerEvent(ServerEvent.EventType type) {
        EventManager manager = plugin.getEventManager();

        if (manager.hasActiveEvent()) {
            TextUtil.sendError(player, "An event is already active!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }

        if (manager.triggerEvent(type)) {
            TextUtil.sendSuccess(player, "Triggered: " + type.getDisplayName());
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            return true;
        } else {
            TextUtil.sendError(player, "Failed to trigger event.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            HandlerList.unregisterAll(this);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
