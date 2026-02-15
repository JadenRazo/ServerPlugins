package net.serverplugins.claim.gui;

import java.util.List;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.PlayerClaimData;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ClaimMenuGui extends Gui {

    private final ServerClaim plugin;

    public ClaimMenuGui(ServerClaim plugin, Player player) {
        super(plugin, player, "Claim Menu", 54);
        this.plugin = plugin;
    }

    @Override
    protected void initializeItems() {
        // Validate player is in a valid world
        if (viewer == null || viewer.getWorld() == null) {
            plugin.getLogger().warning("ClaimMenuGui: Player or world is null");
            return;
        }

        // Navigation bar (top row, slots 0-8)
        setupNavBar();

        // Chunk map (rows 1-5, slots 9-53) - 5 rows with player centered
        setupChunkMap();
    }

    private void setupNavBar() {
        // Null-safe player data access
        PlayerClaimData data = null;
        try {
            data = plugin.getClaimManager().getPlayerData(viewer.getUniqueId());
        } catch (Exception e) {
            plugin.getLogger()
                    .warning(
                            "Failed to get player data for "
                                    + viewer.getName()
                                    + ": "
                                    + e.getMessage());
        }

        int total =
                data != null ? data.getTotalChunks() : plugin.getClaimConfig().getStartingChunks();
        int remaining = plugin.getClaimManager().getRemainingChunks(viewer.getUniqueId());
        int used = total - remaining;
        int purchased = data != null ? data.getPurchasedChunks() : 0;

        // Slot 0: Claim info/stats
        ItemStack statsItem =
                new ItemBuilder(Material.COMPASS)
                        .name("<gold>Claim Statistics")
                        .lore(
                                "",
                                "<gray>Total Chunks: <white>" + total,
                                "<gray>Used Chunks: <yellow>" + used,
                                "<gray>Available: <green>" + remaining,
                                "<gray>Purchased: <aqua>" + purchased)
                        .build();
        setItem(0, new GuiItem(statsItem));

        // Slot 2: Chunk Shop
        ItemStack shopItem =
                new ItemBuilder(Material.EMERALD)
                        .name("<green>Chunk Shop")
                        .lore(
                                "",
                                "<gray>Purchase additional chunks",
                                "",
                                "<yellow>Click to open shop")
                        .build();
        setItem(
                2,
                new GuiItem(
                        shopItem,
                        e -> {
                            viewer.closeInventory();
                            // Check if chunk pool is cached, if not load it asynchronously
                            if (plugin.getClaimManager().getPlayerChunkPool(viewer.getUniqueId())
                                    == null) {
                                TextUtil.send(viewer, "<gray>Loading chunk shop...");
                                plugin.getServer()
                                        .getScheduler()
                                        .runTaskAsynchronously(
                                                plugin,
                                                () -> {
                                                    try {
                                                        plugin.getClaimManager()
                                                                .getPlayerChunkPool(
                                                                        viewer.getUniqueId());
                                                        // Now open GUI on main thread
                                                        plugin.getServer()
                                                                .getScheduler()
                                                                .runTask(
                                                                        plugin,
                                                                        () -> {
                                                                            new ChunkShopGui(
                                                                                            plugin,
                                                                                            viewer)
                                                                                    .open();
                                                                        });
                                                    } catch (Exception ex) {
                                                        plugin.getLogger()
                                                                .severe(
                                                                        "Failed to load chunk pool for "
                                                                                + viewer.getName()
                                                                                + ": "
                                                                                + ex.getMessage());
                                                        plugin.getServer()
                                                                .getScheduler()
                                                                .runTask(
                                                                        plugin,
                                                                        () -> {
                                                                            TextUtil.send(
                                                                                    viewer,
                                                                                    "<red>Failed to load chunk shop data. Please try again.");
                                                                        });
                                                    }
                                                });
                            } else {
                                new ChunkShopGui(plugin, viewer).open();
                            }
                        }));

        // Slot 4: Profile/Settings
        ItemStack profileItem =
                new ItemBuilder(Material.CHEST)
                        .name("<light_purple>My Claims")
                        .lore(
                                "",
                                "<gray>View all your claim profiles",
                                "<gray>and manage their settings",
                                "",
                                "<yellow>Click to view your claims")
                        .build();
        setItem(
                4,
                new GuiItem(
                        profileItem,
                        e -> {
                            List<Claim> playerClaims =
                                    plugin.getClaimManager().getPlayerClaims(viewer.getUniqueId());
                            if (playerClaims == null || playerClaims.isEmpty()) {
                                TextUtil.send(
                                        viewer,
                                        "<red>You don't have any claims yet! Claim land first.");
                            } else {
                                viewer.closeInventory();
                                new MyProfilesGui(plugin, viewer).open();
                            }
                        }));

        // Slot 6: Help/Info
        ItemStack helpItem =
                new ItemBuilder(Material.BOOK)
                        .name("<yellow>How to Claim")
                        .lore(
                                "",
                                "<gray>Click <green>green chunks <gray>to claim",
                                "<gray>Click <aqua>your chunks <gray>to unclaim",
                                "",
                                "<dark_gray>Legend:",
                                "<green>● <gray>Unclaimed",
                                "<aqua>● <gray>Your claim",
                                "<light_purple>● <gray>Other player",
                                "<dark_red>● <gray>Spawn protected")
                        .build();
        setItem(6, new GuiItem(helpItem));

        // Slot 8: Close
        ItemStack closeItem =
                new ItemBuilder(Material.BARRIER)
                        .name("<red>Close")
                        .lore("<gray>Close this menu")
                        .build();
        setItem(8, new GuiItem(closeItem, e -> viewer.closeInventory()));

        // Fill nav bar dividers
        ItemStack navFiller = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {1, 3, 5, 7}) {
            setItem(i, new GuiItem(navFiller));
        }
    }

    private void setupChunkMap() {
        // Null safety for player location and world
        if (viewer == null || viewer.getLocation() == null || viewer.getWorld() == null) {
            plugin.getLogger()
                    .warning(
                            "ClaimMenuGui: Cannot setup chunk map - player location/world is null");
            return;
        }

        Chunk playerChunk = viewer.getLocation().getChunk();
        if (playerChunk == null) {
            plugin.getLogger().warning("ClaimMenuGui: Player chunk is null");
            return;
        }

        int centerX = playerChunk.getX();
        int centerZ = playerChunk.getZ();
        String world = viewer.getWorld().getName();

        // 5 rows x 9 columns = 45 chunks displayed
        // Player centered at row 2 (middle of 5 rows)
        // Rows 1-5 (slots 9-53)
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 9; col++) {
                int offsetX = col - 4; // -4 to +4 horizontally
                int offsetZ = row - 2; // -2 to +2 vertically (center at row 2)
                int chunkX = centerX + offsetX;
                int chunkZ = centerZ + offsetZ;

                int slot = (row + 1) * 9 + col;
                boolean isPlayerLocation = (chunkX == centerX && chunkZ == centerZ);

                ItemStack item = createChunkItem(world, chunkX, chunkZ, isPlayerLocation);
                final int finalChunkX = chunkX;
                final int finalChunkZ = chunkZ;

                setItem(
                        slot,
                        new GuiItem(item, e -> handleChunkClick(world, finalChunkX, finalChunkZ)));
            }
        }
    }

    private ItemStack createChunkItem(
            String world, int chunkX, int chunkZ, boolean isPlayerLocation) {
        Claim claim = plugin.getClaimManager().getClaimAt(world, chunkX, chunkZ);

        Material material;
        String name;
        java.util.List<String> loreLines = new java.util.ArrayList<>();
        boolean shouldGlow = false;

        if (isSpawnProtected(world, chunkX, chunkZ)) {
            material = Material.BEDROCK;
            name = "<dark_red>Protected World";
            loreLines.add("<red>✖ Cannot claim in this world");
        } else if (claim == null) {
            material = Material.GRASS_BLOCK;
            name = "<green>Unclaimed";
            loreLines.add("<yellow>Click to claim");
            if (isPlayerLocation) shouldGlow = true;
        } else if (claim.isOwner(viewer.getUniqueId())) {
            material = Material.NETHER_STAR;
            name = "<aqua>Your Claim";
            loreLines.add("<yellow>Click to unclaim");
            shouldGlow = true;
        } else {
            // Other player's claim - show their claim data
            String ownerName = getOwnerName(claim);

            material = claim.getIcon() != null ? claim.getIcon() : Material.PURPLE_STAINED_GLASS;
            String colorTag =
                    claim.getColor() != null ? claim.getColor().getColorTag() : "<light_purple>";
            name = colorTag + ownerName + "'s Claim";

            loreLines.add("");
            loreLines.add("<gray>Claim: <white>" + claim.getName());
            loreLines.add("<gray>Owner: <white>" + ownerName);
            loreLines.add("");
            loreLines.add("<red>✖ Already claimed");
            shouldGlow = true;
        }

        ItemBuilder builder = new ItemBuilder(material).name(name);

        // Add location prefix
        java.util.List<String> finalLore = new java.util.ArrayList<>();
        if (isPlayerLocation) {
            finalLore.add("<gold>★ <white>Your Location");
            finalLore.add("");
        }
        finalLore.add("<dark_gray>Chunk: " + chunkX + ", " + chunkZ);
        finalLore.add("");
        finalLore.addAll(loreLines);

        builder.lore(finalLore.toArray(new String[0]));

        if (shouldGlow) {
            builder.glow(true);
        }

        return builder.build();
    }

    private void handleChunkClick(String world, int chunkX, int chunkZ) {
        plugin.getLogger()
                .info(
                        "[DEBUG] handleChunkClick called: world="
                                + world
                                + " chunk="
                                + chunkX
                                + ","
                                + chunkZ
                                + " player="
                                + viewer.getName());

        if (isSpawnProtected(world, chunkX, chunkZ)) {
            TextUtil.send(viewer, "<red>You cannot claim chunks in this world!");
            return;
        }

        Claim existing = plugin.getClaimManager().getClaimAt(world, chunkX, chunkZ);
        Chunk chunk = viewer.getWorld().getChunkAt(chunkX, chunkZ);

        if (existing == null) {
            // Claim chunk
            plugin.getClaimManager()
                    .claimChunk(viewer, chunk)
                    .thenAccept(
                            result -> {
                                if (result.success()) {
                                    TextUtil.send(
                                            viewer,
                                            plugin.getClaimConfig().getMessage("claim-success"));
                                } else {
                                    TextUtil.send(
                                            viewer,
                                            plugin.getClaimConfig()
                                                    .getMessage(result.messageKey()));
                                }
                                // Refresh menu in-place to maintain mouse position
                                plugin.getServer().getScheduler().runTask(plugin, this::refresh);
                            });
        } else if (existing.isOwner(viewer.getUniqueId())) {
            // Unclaim chunk
            plugin.getClaimManager()
                    .unclaimChunk(viewer, chunk)
                    .thenAccept(
                            result -> {
                                if (result.success()) {
                                    TextUtil.send(
                                            viewer,
                                            plugin.getClaimConfig().getMessage("unclaim-success"));
                                } else {
                                    TextUtil.send(
                                            viewer,
                                            plugin.getClaimConfig()
                                                    .getMessage(result.messageKey()));
                                }
                                // Refresh menu in-place to maintain mouse position
                                plugin.getServer().getScheduler().runTask(plugin, this::refresh);
                            });
        }
    }

    private boolean isSpawnProtected(String world, int chunkX, int chunkZ) {
        // Check if world is in the allowed list
        return !plugin.getClaimConfig().isWorldAllowed(world);
    }

    private String getOwnerName(Claim claim) {
        if (claim == null || claim.getOwnerUuid() == null) {
            return "Unknown";
        }

        try {
            PlayerClaimData data = plugin.getRepository().getPlayerData(claim.getOwnerUuid());
            return data != null && data.getUsername() != null ? data.getUsername() : "Unknown";
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get owner name for claim: " + e.getMessage());
            return "Unknown";
        }
    }
}
