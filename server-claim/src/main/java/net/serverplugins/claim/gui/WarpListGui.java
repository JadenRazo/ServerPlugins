package net.serverplugins.claim.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ClaimWarp;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public class WarpListGui extends Gui {

    private final ServerClaim plugin;
    private final Claim claim;
    private final ClaimWarp warp;
    private final ListType listType;

    public enum ListType {
        ALLOWLIST,
        BLOCKLIST
    }

    public WarpListGui(
            ServerClaim plugin, Player player, Claim claim, ClaimWarp warp, ListType listType) {
        super(plugin, player, "Warps", 54);
        this.plugin = plugin;

        // Validate claim and warp exist
        if (!GuiValidator.validateClaim(plugin, player, claim, "Warp List")) {
            this.claim = null;
            this.warp = null;
            this.listType = listType;
            return;
        }

        if (warp == null) {
            player.sendMessage(
                    net.kyori.adventure.text.Component.text(
                            "Warp settings not found!",
                            net.kyori.adventure.text.format.NamedTextColor.RED));
            plugin.getLogger().warning("WarpListGui: Warp is null for claim " + claim.getId());
            this.claim = claim;
            this.warp = null;
            this.listType = listType;
            return;
        }

        this.claim = claim;
        this.warp = warp;
        this.listType = listType;
    }

    @Override
    protected void initializeItems() {
        // Early return if claim or warp is null
        if (claim == null || warp == null) {
            plugin.getLogger().warning("WarpListGui: Claim or warp is null, cannot initialize");
            return;
        }

        // Header
        setupHeader();

        // Player list (slots 9-44)
        setupPlayerList();

        // Footer
        setupFooter();
    }

    private void setupHeader() {
        String listName = listType == ListType.ALLOWLIST ? "Allowlist" : "Blocklist";
        String color = listType == ListType.ALLOWLIST ? "<green>" : "<red>";
        String claimName =
                claim != null && claim.getName() != null ? claim.getName() : "Unknown Claim";

        ItemStack headerItem =
                new ItemBuilder(
                                listType == ListType.ALLOWLIST
                                        ? Material.LIME_STAINED_GLASS_PANE
                                        : Material.RED_STAINED_GLASS_PANE)
                        .name(color + listName)
                        .lore(
                                "",
                                "<gray>Claim: <white>" + claimName,
                                "",
                                listType == ListType.ALLOWLIST
                                        ? "<dark_gray>Players who can visit"
                                        : "<dark_gray>Players who cannot visit",
                                "",
                                "<yellow>Click player heads to remove")
                        .build();

        setItem(4, new GuiItem(headerItem));

        // Fill header with glass
        ItemStack blackGlass = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 9; i++) {
            if (i != 4) {
                setItem(i, new GuiItem(blackGlass));
            }
        }
    }

    private void setupPlayerList() {
        Set<UUID> playerList =
                listType == ListType.ALLOWLIST ? warp.getAllowlist() : warp.getBlocklist();

        if (playerList == null || playerList.isEmpty()) {
            // Empty list message
            ItemStack emptyItem =
                    new ItemBuilder(Material.BARRIER)
                            .name(
                                    "<gray>No players in "
                                            + (listType == ListType.ALLOWLIST
                                                    ? "allowlist"
                                                    : "blocklist"))
                            .lore("", "<yellow>Click 'Add Player' below", "<yellow>to add someone")
                            .build();
            setItem(22, new GuiItem(emptyItem));
            return;
        }

        // Display player heads
        int slot = 9;
        List<UUID> players = new ArrayList<>(playerList);

        for (UUID playerUuid : players) {
            if (playerUuid == null) continue; // Skip null UUIDs
            if (slot >= 45) break;

            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUuid);
            String playerName =
                    offlinePlayer != null && offlinePlayer.getName() != null
                            ? offlinePlayer.getName()
                            : "Unknown";

            ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) playerHead.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(offlinePlayer);
                playerHead.setItemMeta(meta);
            }

            ItemStack headItem =
                    new ItemBuilder(playerHead)
                            .name("<white>" + playerName)
                            .lore(
                                    "",
                                    listType == ListType.ALLOWLIST
                                            ? "<green>Can visit this claim"
                                            : "<red>Cannot visit this claim",
                                    "",
                                    "<yellow>Click to remove")
                            .build();

            final UUID uuid = playerUuid;
            setItem(
                    slot,
                    new GuiItem(
                            headItem,
                            e -> {
                                if (listType == ListType.ALLOWLIST) {
                                    plugin.getVisitationManager()
                                            .removeFromAllowlist(warp, uuid, this::reopenMenu);
                                    TextUtil.send(
                                            viewer,
                                            "<yellow>Removed <white>"
                                                    + playerName
                                                    + " <yellow>from allowlist.");
                                } else {
                                    plugin.getVisitationManager()
                                            .removeFromBlocklist(warp, uuid, this::reopenMenu);
                                    TextUtil.send(
                                            viewer,
                                            "<yellow>Removed <white>"
                                                    + playerName
                                                    + " <yellow>from blocklist.");
                                }
                            }));

            slot++;
        }

        // Fill remaining slots
        ItemStack empty = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = slot; i < 45; i++) {
            setItem(i, new GuiItem(empty));
        }
    }

    private void setupFooter() {
        // Add player button (slot 48)
        String listName = listType == ListType.ALLOWLIST ? "allowlist" : "blocklist";
        String color = listType == ListType.ALLOWLIST ? "<green>" : "<red>";

        ItemStack addItem =
                new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                        .name(color + "Add Player")
                        .lore("", "<yellow>Click to add a player", "<yellow>to the " + listName)
                        .build();

        setItem(
                48,
                new GuiItem(
                        addItem,
                        e -> {
                            viewer.closeInventory();
                            if (listType == ListType.ALLOWLIST) {
                                plugin.getVisitationManager().awaitAllowlistInput(viewer, warp);
                            } else {
                                plugin.getVisitationManager().awaitBlocklistInput(viewer, warp);
                            }
                        }));

        // Back button (slot 50)
        ItemStack backItem =
                new ItemBuilder(Material.ARROW)
                        .name("<red>Back")
                        .lore("<gray>Return to warp settings")
                        .build();

        setItem(
                50,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            new WarpSettingsGui(plugin, viewer, claim).open();
                        }));

        // Fill footer with glass
        ItemStack blackGlass = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 45; i < 54; i++) {
            if (i != 48 && i != 50) {
                setItem(i, new GuiItem(blackGlass));
            }
        }
    }

    private void reopenMenu() {
        plugin.getServer()
                .getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            viewer.closeInventory();
                            new WarpListGui(plugin, viewer, claim, warp, listType).open();
                        });
    }
}
