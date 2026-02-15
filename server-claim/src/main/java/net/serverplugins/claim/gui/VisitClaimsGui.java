package net.serverplugins.claim.gui;

import java.util.ArrayList;
import java.util.List;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ClaimProfile;
import net.serverplugins.claim.models.ClaimWarp;
import net.serverplugins.claim.models.PlayerClaimData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class VisitClaimsGui extends Gui {

    private final ServerClaim plugin;
    private final int page;
    private static final int ITEMS_PER_PAGE = 36;

    public VisitClaimsGui(ServerClaim plugin, Player player) {
        this(plugin, player, 0);
    }

    public VisitClaimsGui(ServerClaim plugin, Player player, int page) {
        super(plugin, player, "Visit Claims", 54);
        this.plugin = plugin;
        this.page = page;
    }

    @Override
    protected void initializeItems() {
        // Null safety check
        if (viewer == null) {
            plugin.getLogger().warning("VisitClaimsGui: Viewer is null");
            return;
        }

        // Header
        setupHeader();

        // Load visitable claims asynchronously
        plugin.getVisitationManager()
                .getVisitableClaims(viewer.getUniqueId())
                .thenAccept(
                        claims -> {
                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                if (claims == null) {
                                                    setupClaimsList(new ArrayList<>());
                                                    setupFooter(0);
                                                } else {
                                                    setupClaimsList(claims);
                                                    setupFooter(claims.size());
                                                }
                                            });
                        })
                .exceptionally(
                        ex -> {
                            plugin.getLogger()
                                    .warning("Failed to load visitable claims: " + ex.getMessage());
                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                setupClaimsList(new ArrayList<>());
                                                setupFooter(0);
                                            });
                            return null;
                        });
    }

    private void setupHeader() {
        ItemStack headerItem =
                new ItemBuilder(Material.ENDER_PEARL)
                        .name("<gold>Visit Claims")
                        .lore(
                                "",
                                "<gray>Browse claims you can visit",
                                "",
                                "<yellow>Click to teleport!")
                        .glow(true)
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

    private void setupClaimsList(List<Claim> allClaims) {
        // Null safety check
        if (allClaims == null) {
            allClaims = new ArrayList<>();
        }

        // Calculate pagination
        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, allClaims.size());
        List<Claim> pageClaims = allClaims.subList(startIndex, endIndex);

        if (pageClaims.isEmpty()) {
            // No claims message
            ItemStack noClaimsItem =
                    new ItemBuilder(Material.BARRIER)
                            .name("<red>No Visitable Claims")
                            .lore(
                                    "",
                                    "<gray>There are no public claims",
                                    "<gray>available to visit right now.",
                                    "",
                                    "<dark_gray>Check back later!")
                            .build();
            setItem(22, new GuiItem(noClaimsItem));
            return;
        }

        // Display claims
        int slot = 9;
        for (Claim claim : pageClaims) {
            if (claim == null) continue; // Skip null claims
            if (slot >= 45) break;

            ClaimWarp warp = plugin.getVisitationManager().getClaimWarp(claim.getId());
            if (warp == null) continue;

            ItemStack claimItem = createClaimItem(claim, warp);
            if (claimItem == null) continue; // Skip if item creation failed
            final Claim c = claim;

            setItem(
                    slot,
                    new GuiItem(
                            claimItem,
                            e -> {
                                viewer.closeInventory();
                                plugin.getVisitationManager().visitClaim(viewer, c);
                            }));

            slot++;
        }

        // Fill remaining slots
        ItemStack empty = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = slot; i < 45; i++) {
            setItem(i, new GuiItem(empty));
        }
    }

    private ItemStack createClaimItem(Claim claim, ClaimWarp warp) {
        if (claim == null || warp == null) {
            plugin.getLogger().warning("Cannot create claim item for null claim or warp");
            return null;
        }

        // Get owner name
        PlayerClaimData ownerData = null;
        try {
            ownerData = plugin.getRepository().getPlayerData(claim.getOwnerUuid());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get owner data for claim: " + e.getMessage());
        }
        String ownerName =
                ownerData != null && ownerData.getUsername() != null
                        ? ownerData.getUsername()
                        : "Unknown";

        // Get profile for icon and color
        ClaimProfile profile = claim.getActiveProfile();
        Material icon =
                profile != null && profile.getIcon() != null
                        ? profile.getIcon()
                        : Material.GRASS_BLOCK;
        String colorTag = profile != null ? profile.getColor().getColorTag() : "<white>";

        // Build lore
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<gray>Owner: <white>" + ownerName);
        lore.add(
                "<gray>World: <white>" + (claim.getWorld() != null ? claim.getWorld() : "Unknown"));
        lore.add(
                "<gray>Chunks: <white>"
                        + (claim.getChunks() != null ? claim.getChunks().size() : 0));
        lore.add("");

        // Description
        if (warp.getDescription() != null && !warp.getDescription().isEmpty()) {
            lore.add("<gray>\"<white>" + warp.getDescription() + "<gray>\"");
            lore.add("");
        }

        // Visibility
        lore.add(
                "<gray>Visibility: "
                        + warp.getVisibility().getColorTag()
                        + warp.getVisibility().getDisplayName());

        // Visit cost
        double cost = warp.getVisitCost();
        if (cost > 0) {
            lore.add("<gray>Visit Cost: <yellow>" + (int) cost + " coins");
        } else {
            lore.add("<gray>Visit Cost: <green>Free");
        }

        lore.add("");
        lore.add("<yellow>Click to visit!");

        return new ItemBuilder(icon)
                .name(colorTag + claim.getName())
                .lore(lore.toArray(new String[0]))
                .glow(cost == 0)
                .build();
    }

    private void setupFooter(int totalClaims) {
        int totalPages = (int) Math.ceil((double) totalClaims / ITEMS_PER_PAGE);

        // Previous page button (slot 45)
        if (page > 0) {
            ItemStack prevItem =
                    new ItemBuilder(Material.ARROW)
                            .name("<yellow>Previous Page")
                            .lore("<gray>Page " + page + " of " + Math.max(1, totalPages))
                            .build();

            setItem(
                    45,
                    new GuiItem(
                            prevItem,
                            e -> {
                                viewer.closeInventory();
                                new VisitClaimsGui(plugin, viewer, page - 1).open();
                            }));
        } else {
            ItemStack emptyItem =
                    new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
            setItem(45, new GuiItem(emptyItem));
        }

        // Page info (slot 49)
        ItemStack pageItem =
                new ItemBuilder(Material.PAPER)
                        .name("<white>Page " + (page + 1) + " of " + Math.max(1, totalPages))
                        .lore("", "<gray>Total Claims: <white>" + totalClaims)
                        .build();
        setItem(49, new GuiItem(pageItem));

        // Next page button (slot 53)
        if ((page + 1) * ITEMS_PER_PAGE < totalClaims) {
            ItemStack nextItem =
                    new ItemBuilder(Material.ARROW)
                            .name("<yellow>Next Page")
                            .lore("<gray>Page " + (page + 2) + " of " + totalPages)
                            .build();

            setItem(
                    53,
                    new GuiItem(
                            nextItem,
                            e -> {
                                viewer.closeInventory();
                                new VisitClaimsGui(plugin, viewer, page + 1).open();
                            }));
        } else {
            ItemStack emptyItem =
                    new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
            setItem(53, new GuiItem(emptyItem));
        }

        // Fill footer with glass
        ItemStack blackGlass = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 45; i < 54; i++) {
            if (i != 45 && i != 49 && i != 53) {
                setItem(i, new GuiItem(blackGlass));
            }
        }
    }
}
