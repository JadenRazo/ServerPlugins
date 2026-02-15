package net.serverplugins.api.utils;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.InternalStructure;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.serverplugins.api.ServerAPI;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiManager;
import net.serverplugins.api.messages.Message;
import net.serverplugins.api.messages.types.LegacyMessage;
import net.serverplugins.api.messages.types.ModernMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class for packet manipulation using ProtocolLib. Handles hiding player inventory in
 * custom GUIs when the title contains the special invisible inventory character.
 */
public class PacketUtils {
    private static PacketUtils instance;
    private final JavaPlugin plugin;
    private final boolean enabled;
    private ProtocolManager protocolManager;
    private final HashMap<UUID, InventoryPlayer> playerInventories = new HashMap<>();
    private PacketContainer inventoryClearPacket;
    private List<ItemStack> emptyInventory;
    private final HashMap<UUID, InventoryData> hiddenInventoriesPlayers = new HashMap<>();
    private boolean hideInventories;

    public static boolean isEnabled() {
        return instance != null && instance.enabled;
    }

    public PacketUtils(@NotNull JavaPlugin plugin) {
        instance = this;
        this.plugin = plugin;

        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
            this.enabled = false;
            plugin.getLogger().warning("ProtocolLib not found - inventory hiding disabled");
            return;
        }

        this.enabled = true;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        this.hideInventories = ServerAPI.getInstance() != null;

        // Create empty inventory list (45 air items for player inventory)
        this.emptyInventory = new ArrayList<>(45);
        for (int i = 0; i < 45; i++) {
            this.emptyInventory.add(new ItemStack(Material.AIR));
        }

        // Create the inventory clear packet
        this.inventoryClearPacket = new PacketContainer(PacketType.Play.Server.WINDOW_ITEMS);
        this.inventoryClearPacket.getIntegers().write(0, 0); // window ID 0 (player inventory)
        this.inventoryClearPacket
                .getIntegers()
                .write(1, -34); // special marker to identify our packet
        this.inventoryClearPacket.getItemListModifier().write(0, this.emptyInventory);
        this.inventoryClearPacket.getItemModifier().write(0, new ItemStack(Material.AIR));

        this.registerPacketListeners();
        plugin.getServer().getPluginManager().registerEvents(new PacketGuiListener(), plugin);

        plugin.getLogger().info("PacketUtils initialized - inventory hiding enabled");
    }

    private void registerPacketListeners() {
        this.protocolManager.addPacketListener(this.getOpenWindowPacketListener());
        this.protocolManager.addPacketListener(this.getSetItemPacketListener());
        this.protocolManager.addPacketListener(this.getSetItemPacketListener2());
        this.protocolManager.addPacketListener(this.getWindowClickListener());
    }

    // ==================== Title Manipulation Methods ====================

    /**
     * Updates placeholders in the current inventory title. Replaces placeholder keys with their
     * values and sends the updated title to the player.
     *
     * @param player The player whose inventory title to update
     * @param placeholders Map of placeholder keys to replacement values
     */
    public static void updateTitlePlaceholders(
            Player player, HashMap<String, String> placeholders) {
        if (!isEnabled()) return;

        InventoryPlayer inventoryPlayer = getInventoryPlayer(player);
        if (inventoryPlayer == null) return;

        int windowId = inventoryPlayer.windowId();
        if (windowId == 0) return;

        Object windowType = inventoryPlayer.containerType();
        String titleJson = inventoryPlayer.originalTitle();

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            titleJson = titleJson.replace(entry.getKey(), entry.getValue());
        }

        getInstance().sendOpenScreenPacket(player, windowId, windowType, titleJson);
        player.updateInventory();
    }

    /**
     * Sets the inventory title using a raw JSON string.
     *
     * @param player The player whose inventory title to set
     * @param titleJson The new title as a JSON string
     */
    public static void setInventoryTitle(Player player, String titleJson) {
        if (!isEnabled()) return;

        InventoryPlayer inventoryPlayer = getInventoryPlayer(player);
        if (inventoryPlayer == null) return;

        int windowId = inventoryPlayer.windowId();
        if (windowId == 0) return;

        Object windowType = inventoryPlayer.containerType();
        getInstance().sendOpenScreenPacket(player, windowId, windowType, titleJson);
        player.updateInventory();
    }

    /**
     * Sets the inventory title using an Adventure Component.
     *
     * @param player The player whose inventory title to set
     * @param title The new title as an Adventure Component
     */
    public static void setInventoryTitle(Player player, Component title) {
        if (!isEnabled()) return;

        String json = GsonComponentSerializer.gson().serialize(title);
        setInventoryTitle(player, json);
    }

    /**
     * Sets the inventory title using a Message object. Supports both Modern (MiniMessage) and
     * Legacy message formats.
     *
     * @param player The player whose inventory title to set
     * @param message The new title as a Message object
     */
    public static void setInventoryTitle(Player player, Message message) {
        if (!isEnabled()) return;

        WrappedChatComponent component =
                switch (message.getType()) {
                    case MODERN -> {
                        ModernMessage modern = (ModernMessage) message;
                        yield WrappedChatComponent.fromJson(
                                GsonComponentSerializer.gson().serialize(modern.getMessage()));
                    }
                    case LEGACY -> {
                        LegacyMessage legacy = (LegacyMessage) message;
                        yield WrappedChatComponent.fromLegacyText(legacy.getMessage());
                    }
                    default -> WrappedChatComponent.fromJson("");
                };

        InventoryPlayer inventoryPlayer = getInventoryPlayer(player);
        if (inventoryPlayer == null) return;

        int windowId = inventoryPlayer.windowId();
        if (windowId == 0) return;

        Object windowType = inventoryPlayer.containerType();
        getInstance().sendOpenScreenPacket(player, windowId, windowType, component);
        player.updateInventory();
    }

    /**
     * Gets the current inventory title JSON for a player.
     *
     * @param player The player to get the title for
     * @return The title JSON string, or null if not available
     */
    @Nullable
    public static String getInventoryTitle(Player player) {
        if (!isEnabled()) return null;

        InventoryPlayer inventoryPlayer = getInventoryPlayer(player);
        if (inventoryPlayer == null) return null;

        int windowId = inventoryPlayer.windowId();
        if (windowId == 0) return null;

        return inventoryPlayer.originalTitle();
    }

    /** Gets the InventoryPlayer data for a player. */
    @Nullable
    private static InventoryPlayer getInventoryPlayer(Player player) {
        InventoryType type = player.getOpenInventory().getType();
        if (type == InventoryType.CRAFTING || type == InventoryType.CREATIVE) {
            return null;
        }
        return getInstance().playerInventories.getOrDefault(player.getUniqueId(), null);
    }

    /** Sends an OPEN_WINDOW packet to update the inventory title. */
    private void sendOpenScreenPacket(
            Player player, int windowId, Object windowType, String titleJson) {
        sendOpenScreenPacket(
                player, windowId, windowType, WrappedChatComponent.fromJson(titleJson));
    }

    /** Sends an OPEN_WINDOW packet to update the inventory title. */
    private void sendOpenScreenPacket(
            Player player, int windowId, Object windowType, WrappedChatComponent title) {
        PacketContainer openScreen = new PacketContainer(PacketType.Play.Server.OPEN_WINDOW);
        openScreen.getIntegers().write(0, windowId);
        openScreen.getStructures().write(0, (InternalStructure) windowType);
        openScreen.getChatComponents().write(0, title);
        this.protocolManager.sendServerPacket(player, openScreen);
    }

    // ==================== Packet Listeners ====================

    /**
     * Listens for OPEN_WINDOW packets and triggers inventory hiding when the title contains the
     * invisible inventory character.
     */
    private PacketListener getOpenWindowPacketListener() {
        return new PacketAdapter(
                this.plugin, ListenerPriority.HIGH, PacketType.Play.Server.OPEN_WINDOW) {
            @Override
            public void onPacketSending(PacketEvent event) {
                UUID uuid = event.getPlayer().getUniqueId();
                int windowId = event.getPacket().getIntegers().read(0);

                InventoryPlayer oldInventory = playerInventories.get(uuid);
                if (oldInventory != null && oldInventory.windowId() == windowId) {
                    return;
                }

                Object containerType = event.getPacket().getStructures().readSafely(0);
                String titleJson = event.getPacket().getChatComponents().read(0).getJson();

                // Check if title contains the invisible inventory character OR any fullscreen menu
                // icons
                // This allows GUIs to hide inventory by either using the trigger character or the
                // menu icons
                boolean shouldHideInventory = false;

                if (hideInventories) {
                    String invisibleTitle = ServerAPI.getInstance().getInvisibleInventoryTitle();
                    if (titleJson.contains(invisibleTitle)) {
                        shouldHideInventory = true;
                    } else {
                        // Check for fullscreen menu icons (arcade, casino, etc.)
                        // These are the Unicode characters for custom backgrounds that should hide
                        // inventory
                        String[] fullscreenIcons = {
                            "\uE66C", // SLOTS
                            "\uE408", // BETS
                            "\uE409", // BLACKJACK_MENU
                            "\uE6CA", // BLACKJACK_BET
                            "\uE40A", // CRASH_WAITING
                            "\uE40B", // CRASH_CRASHED
                            "\uE40C", // CRASH_STARTED
                            "\uE5B1", // CRASH_REMOVE
                            "\uE6C3", // ROULETTE
                            "\uE6C4", // ROULETTE_2
                            "\uE737", // LOTTERY_BETS
                            "\uE738", // LOTTERY_START
                            "\uE5B4" // LOTTERY_REMOVE
                        };

                        for (String icon : fullscreenIcons) {
                            if (titleJson.contains(icon)) {
                                shouldHideInventory = true;
                                break;
                            }
                        }
                    }
                }

                if (shouldHideInventory) {
                    Bukkit.getScheduler()
                            .runTaskLater(
                                    plugin,
                                    () -> {
                                        if (event.getPlayer().getOpenInventory().getType()
                                                != InventoryType.CRAFTING) {
                                            hidePlayerInventory(event.getPlayer());
                                        }
                                    },
                                    1L);
                }

                InventoryPlayer player = new InventoryPlayer(windowId, containerType, titleJson);
                playerInventories.put(uuid, player);
            }
        };
    }

    /** Intercepts SET_SLOT packets and cancels them for hidden inventory slots. */
    private PacketListener getSetItemPacketListener() {
        return new PacketAdapter(
                this.plugin, ListenerPriority.NORMAL, PacketType.Play.Server.SET_SLOT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                UUID uuid = event.getPlayer().getUniqueId();
                if (!hiddenInventoriesPlayers.containsKey(uuid)) {
                    return;
                }

                sendInventoryClearPacket(event.getPlayer());

                int slot = event.getPacket().getIntegers().getValues().get(2);
                if (slot >= hiddenInventoriesPlayers.get(uuid).size()) {
                    event.setCancelled(true);
                }
            }
        };
    }

    /** Intercepts WINDOW_ITEMS packets and truncates them to only show GUI slots. */
    private PacketListener getSetItemPacketListener2() {
        return new PacketAdapter(
                this.plugin, ListenerPriority.NORMAL, PacketType.Play.Server.WINDOW_ITEMS) {
            @Override
            public void onPacketSending(PacketEvent event) {
                UUID uuid = event.getPlayer().getUniqueId();
                if (!hiddenInventoriesPlayers.containsKey(uuid)) {
                    return;
                }

                // Skip our own clear packet (identified by -34 marker)
                if (event.getPacket().getIntegers().getValues().get(1) == -34) {
                    return;
                }

                int size = hiddenInventoriesPlayers.get(uuid).size();

                // If window ID is 0 (player inventory), clear it
                if (event.getPacket().getIntegers().getValues().get(0) == 0) {
                    event.getPacket().getItemListModifier().write(0, emptyInventory);
                    return;
                }

                // Truncate item list to GUI size only
                List<ItemStack> list = event.getPacket().getItemListModifier().read(0);
                if (list.size() > size) {
                    list = new ArrayList<>(list.subList(0, size));
                    event.getPacket().getItemListModifier().write(0, list);
                }
            }
        };
    }

    /** Intercepts WINDOW_CLICK packets and cancels clicks on hidden inventory slots. */
    private PacketListener getWindowClickListener() {
        return new PacketAdapter(
                this.plugin, ListenerPriority.HIGHEST, PacketType.Play.Client.WINDOW_CLICK) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                org.bukkit.entity.Player player = event.getPlayer();
                UUID uuid = player.getUniqueId();

                if (!hiddenInventoriesPlayers.containsKey(uuid)) {
                    return;
                }

                com.comphenix.protocol.events.PacketContainer packet = event.getPacket();
                com.comphenix.protocol.reflect.StructureModifier<Integer> ints =
                        packet.getIntegers();

                int slot;
                if (ints.size() >= 3) {
                    slot = ints.read(2);
                } else {
                    slot = packet.getShorts().read(0).shortValue();
                }

                int guiSize = hiddenInventoriesPlayers.get(uuid).size();

                // Block clicks on hidden inventory slots
                if (slot >= guiSize) {
                    event.setCancelled(true);

                    // Immediately refresh player inventory to prevent client prediction
                    org.bukkit.Bukkit.getScheduler()
                            .runTask(
                                    plugin,
                                    () -> {
                                        player.updateInventory();
                                        sendInventoryClearPacket(player);
                                    });
                    return;
                }

                // Block creative mode clicks entirely (prevent item duplication)
                if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
                    event.setCancelled(true);
                    TextUtil.sendError(player, "Shop cannot be used in creative mode!");
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
                    return;
                }

                // Block shift-clicks and double-clicks (they bypass slot restrictions)
                int button =
                        ints.size() >= 2 ? ints.read(1) : 0; // Button: 0=left, 1=right, 2=middle
                int mode =
                        ints.size() >= 4
                                ? ints.read(3)
                                : 0; // Mode: 0=normal, 1=shift, 2=number key

                if (mode == 1 || button == 6) { // mode 1 = shift click, button 6 = double click
                    event.setCancelled(true);
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, player::updateInventory);
                    return;
                }
            }
        };
    }

    /** Hides the player's inventory by sending a clear packet. */
    public static void hidePlayerInventory(Player player) {
        if (!isEnabled()) return;

        Bukkit.getScheduler()
                .runTask(
                        ServerAPI.getInstance(),
                        () -> {
                            getInstance()
                                    .hiddenInventoriesPlayers
                                    .put(
                                            player.getUniqueId(),
                                            new InventoryData(
                                                    player.getOpenInventory()
                                                            .getTopInventory()
                                                            .getSize(),
                                                    System.currentTimeMillis()));
                            getInstance().sendInventoryClearPacket(player);
                        });
    }

    /** Restores the player's inventory visibility. */
    public static void restorePlayerInventory(Player player) {
        if (!isEnabled()) return;

        PacketUtils pu = getInstance();
        JavaPlugin plugin = ServerAPI.getInstance();
        InventoryData data = pu.hiddenInventoriesPlayers.get(player.getUniqueId());

        if (data == null) {
            player.updateInventory();
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> pu.sendInventoryClearPacket(player));
        Bukkit.getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            InventoryData dataNow =
                                    pu.hiddenInventoriesPlayers.get(player.getUniqueId());
                            if (dataNow != null && dataNow.time() == data.time()) {
                                pu.hiddenInventoriesPlayers.remove(player.getUniqueId());
                                player.updateInventory();
                            }
                        },
                        1L);
    }

    private void sendInventoryClearPacket(Player player) {
        this.protocolManager.sendServerPacket(player, this.inventoryClearPacket);
    }

    public static PacketUtils getInstance() {
        return instance;
    }

    public HashMap<UUID, InventoryPlayer> getPlayerInventories() {
        return this.playerInventories;
    }

    public HashMap<UUID, InventoryData> getHiddenInventoriesPlayers() {
        return this.hiddenInventoriesPlayers;
    }

    /** Listener for inventory close events to restore player inventory. */
    public final class PacketGuiListener implements Listener {
        @EventHandler
        public void onInventoryClose(InventoryCloseEvent event) {
            if (!(event.getPlayer() instanceof Player player)) return;

            if (hiddenInventoriesPlayers.containsKey(player.getUniqueId())) {
                restorePlayerInventory(player);
            }

            playerInventories.remove(player.getUniqueId());

            // Also handle GUI manager cleanup
            GuiManager guiManager = ServerAPI.getInstance().getGuiManager();
            if (guiManager != null) {
                Gui gui = guiManager.getOpenGui(player);
                if (gui != null) {
                    gui.onClose(player);
                    guiManager.removePlayer(player);
                }
            }
        }
    }

    /** Record to track open inventory windows. */
    public record InventoryPlayer(int windowId, Object containerType, String originalTitle) {}

    /** Record to track hidden inventory data. */
    public record InventoryData(int size, long time) {}
}
