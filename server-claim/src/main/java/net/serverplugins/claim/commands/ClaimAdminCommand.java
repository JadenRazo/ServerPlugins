package net.serverplugins.claim.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ClaimGroup;
import net.serverplugins.claim.models.ClaimPermission;
import net.serverplugins.claim.models.ClaimedChunk;
import net.serverplugins.claim.models.GroupPermissions;
import net.serverplugins.claim.models.PlayerClaimData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ClaimAdminCommand implements CommandExecutor, TabCompleter {

    private final ServerClaim plugin;

    public ClaimAdminCommand(ServerClaim plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (!sender.hasPermission("serverclaim.admin")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list" -> handleList(sender, args);
            case "info" -> handleInfo(sender, args);
            case "delete" -> handleDelete(sender, args);
            case "deleteat" -> handleDeleteAt(sender, args);
            case "tp" -> handleTeleport(sender, args);
            case "setgroup" -> handleSetGroup(sender, args);
            case "toggleperm" -> handleTogglePerm(sender, args);
            case "repair" -> handleRepair(sender, args);
            case "repairall" -> handleRepairAll(sender, args);
            case "setchunks" -> handleSetChunks(sender, args);
            case "givechunks" -> handleGiveChunks(sender, args);
            case "migrate" -> handleMigrate(sender, args);
            case "migrateall" -> handleMigrateAll(sender, args);
            case "migrate-chunk-pool" -> handleMigrateChunkPool(sender, args);
            case "stats" -> handleStats(sender, args);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleList(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(
                    Component.text("Usage: /claimadmin list <player>", NamedTextColor.RED));
            return;
        }

        String playerName = args[1];
        PlayerClaimData playerData =
                plugin.getRepository().getPlayerDataByExactUsername(playerName);

        if (playerData == null) {
            playerData = plugin.getRepository().getPlayerDataByUsername(playerName);
        }

        if (playerData == null) {
            CommonMessages.PLAYER_NOT_FOUND.send(sender);
            return;
        }

        List<Claim> claims = plugin.getRepository().getClaimsByOwner(playerData.getUuid());

        sender.sendMessage(
                Component.text(
                        "=== Claims for " + playerData.getUsername() + " ===",
                        NamedTextColor.GOLD));
        sender.sendMessage(Component.text("UUID: " + playerData.getUuid(), NamedTextColor.GRAY));
        sender.sendMessage(
                Component.text(
                        "Total chunks: "
                                + playerData.getTotalChunks()
                                + ", Purchased: "
                                + playerData.getPurchasedChunks(),
                        NamedTextColor.GRAY));

        if (claims.isEmpty()) {
            sender.sendMessage(Component.text("No claims found.", NamedTextColor.YELLOW));
            return;
        }

        for (Claim claim : claims) {
            sender.sendMessage(Component.text(""));
            sender.sendMessage(
                    Component.text(
                            "Claim ID: " + claim.getId() + " - " + claim.getName(),
                            NamedTextColor.GREEN));
            sender.sendMessage(Component.text("  World: " + claim.getWorld(), NamedTextColor.GRAY));
            sender.sendMessage(
                    Component.text("  Chunks: " + claim.getChunks().size(), NamedTextColor.GRAY));

            if (!claim.getChunks().isEmpty()) {
                ClaimedChunk first = claim.getChunks().iterator().next();
                int centerX = (first.getChunkX() * 16) + 8;
                int centerZ = (first.getChunkZ() * 16) + 8;

                Component coordText =
                        Component.text(
                                        "  Center at: " + centerX + ", " + centerZ,
                                        NamedTextColor.GRAY)
                                .clickEvent(
                                        ClickEvent.runCommand("/claimadmin tp " + claim.getId()))
                                .append(Component.text(" [CLICK TO TP]", NamedTextColor.AQUA));

                sender.sendMessage(coordText);
            }
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(
                    Component.text("Usage: /claimadmin info <claimId>", NamedTextColor.RED));
            return;
        }

        int claimId;
        try {
            claimId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid claim ID: " + args[1], NamedTextColor.RED));
            return;
        }

        Claim claim = plugin.getRepository().getClaimById(claimId);
        if (claim == null) {
            sender.sendMessage(
                    Component.text("Claim not found with ID: " + claimId, NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.text("=== Claim Info ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("ID: " + claim.getId(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Name: " + claim.getName(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Owner: " + claim.getOwnerUuid(), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("World: " + claim.getWorld(), NamedTextColor.WHITE));
        sender.sendMessage(
                Component.text("Chunks: " + claim.getChunks().size(), NamedTextColor.WHITE));

        for (ClaimedChunk chunk : claim.getChunks()) {
            int blockX = chunk.getChunkX() * 16;
            int blockZ = chunk.getChunkZ() * 16;
            sender.sendMessage(
                    Component.text(
                            "  - Chunk ("
                                    + chunk.getChunkX()
                                    + ", "
                                    + chunk.getChunkZ()
                                    + ") = blocks ("
                                    + blockX
                                    + ", "
                                    + blockZ
                                    + ")",
                            NamedTextColor.GRAY));
        }
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(
                    Component.text("Usage: /claimadmin delete <claimId>", NamedTextColor.RED));
            return;
        }

        int claimId;
        try {
            claimId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid claim ID: " + args[1], NamedTextColor.RED));
            return;
        }

        Claim claim = plugin.getRepository().getClaimById(claimId);
        if (claim == null) {
            sender.sendMessage(
                    Component.text("Claim not found with ID: " + claimId, NamedTextColor.RED));
            return;
        }

        int chunkCount = claim.getChunks().size();
        String claimName = claim.getName();
        UUID ownerUuid = claim.getOwnerUuid();

        // Delete from cache and database
        plugin.getClaimManager().deleteClaim(claim);

        sender.sendMessage(
                Component.text(
                        "Deleted claim '"
                                + claimName
                                + "' (ID: "
                                + claimId
                                + ") with "
                                + chunkCount
                                + " chunks.",
                        NamedTextColor.GREEN));
        sender.sendMessage(Component.text("Owner UUID: " + ownerUuid, NamedTextColor.GRAY));
    }

    private void handleDeleteAt(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(
                    Component.text(
                            "Usage: /claimadmin deleteat <world> <x> <z>", NamedTextColor.RED));
            return;
        }

        String world = args[1];
        int x, z;
        try {
            x = Integer.parseInt(args[2]);
            z = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid coordinates.", NamedTextColor.RED));
            return;
        }

        int chunkX = x >> 4;
        int chunkZ = z >> 4;

        Claim claim = plugin.getRepository().getClaimAt(world, chunkX, chunkZ);
        if (claim == null) {
            sender.sendMessage(
                    Component.text(
                            "No claim found at "
                                    + world
                                    + " ("
                                    + x
                                    + ", "
                                    + z
                                    + ") / chunk ("
                                    + chunkX
                                    + ", "
                                    + chunkZ
                                    + ")",
                            NamedTextColor.RED));
            return;
        }

        int chunkCount = claim.getChunks().size();
        String claimName = claim.getName();
        int claimId = claim.getId();
        UUID ownerUuid = claim.getOwnerUuid();

        plugin.getClaimManager().deleteClaim(claim);

        sender.sendMessage(
                Component.text(
                        "Deleted claim '"
                                + claimName
                                + "' (ID: "
                                + claimId
                                + ") with "
                                + chunkCount
                                + " chunks.",
                        NamedTextColor.GREEN));
        sender.sendMessage(Component.text("Owner UUID: " + ownerUuid, NamedTextColor.GRAY));
    }

    private void handleTeleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can teleport!", NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(
                    Component.text("Usage: /claimadmin tp <claimId>", NamedTextColor.RED));
            return;
        }

        int claimId;
        try {
            claimId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid claim ID: " + args[1], NamedTextColor.RED));
            return;
        }

        Claim claim = plugin.getRepository().getClaimById(claimId);
        if (claim == null) {
            sender.sendMessage(
                    Component.text("Claim not found with ID: " + claimId, NamedTextColor.RED));
            return;
        }

        if (claim.getChunks().isEmpty()) {
            sender.sendMessage(
                    Component.text("This claim has no chunks to teleport to!", NamedTextColor.RED));
            return;
        }

        // Get world
        World world = plugin.getServer().getWorld(claim.getWorld());
        if (world == null) {
            sender.sendMessage(
                    Component.text("The world for this claim is not loaded!", NamedTextColor.RED));
            return;
        }

        // Calculate center of first chunk
        ClaimedChunk chunk = claim.getChunks().get(0);
        int x = (chunk.getChunkX() * 16) + 8;
        int z = (chunk.getChunkZ() * 16) + 8;

        // Find safe location
        Location warpLoc = findSafeLocation(world, x, z);

        if (warpLoc == null) {
            sender.sendMessage(
                    Component.text(
                            "Could not find a safe location to teleport to!", NamedTextColor.RED));
            sender.sendMessage(
                    Component.text(
                            "The area may be covered in lava or other hazards.",
                            NamedTextColor.GRAY));
            return;
        }

        // Teleport (free for admins)
        player.teleport(warpLoc);
        sender.sendMessage(
                Component.text(
                        "Teleported to claim '" + claim.getName() + "' (ID: " + claimId + ")",
                        NamedTextColor.GREEN));
    }

    private Location findSafeLocation(World world, int centerX, int centerZ) {
        int searchRadius = 5;

        Location bestLocation = null;
        int bestScore = -1;

        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;
                int y = world.getHighestBlockYAt(x, z);

                Block ground = world.getBlockAt(x, y, z);
                Block above = world.getBlockAt(x, y + 1, z);
                Block aboveTwo = world.getBlockAt(x, y + 2, z);

                // Skip if standing blocks are not passable
                if (!above.isPassable() || !aboveTwo.isPassable()) {
                    continue;
                }

                Material groundMaterial = ground.getType();

                // NEVER teleport to lava or near lava
                if (groundMaterial == Material.LAVA) {
                    continue;
                }

                int score = 0;

                // Check for safe solid ground
                if (ground.isSolid()
                        && groundMaterial != Material.CACTUS
                        && groundMaterial != Material.MAGMA_BLOCK
                        && groundMaterial != Material.CAMPFIRE
                        && groundMaterial != Material.SOUL_CAMPFIRE
                        && groundMaterial != Material.FIRE
                        && groundMaterial != Material.SWEET_BERRY_BUSH
                        && groundMaterial != Material.POWDER_SNOW
                        && groundMaterial != Material.POINTED_DRIPSTONE
                        && groundMaterial != Material.WITHER_ROSE) {
                    score = 10; // Best: safe solid ground
                } else if (groundMaterial == Material.WATER) {
                    score = 5; // Acceptable: water
                } else if (ground.isPassable()) {
                    score = 1; // Fallback: passable block (air above something)
                }

                // Prefer center of chunk
                if (dx == 0 && dz == 0) {
                    score += 2;
                }

                if (score > bestScore) {
                    bestScore = score;
                    bestLocation = new Location(world, x + 0.5, y + 1, z + 0.5);
                }
            }
        }

        return bestLocation;
    }

    private void handleSetGroup(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(
                    Component.text(
                            "Usage: /claimadmin setgroup <claimId> <player> <group>",
                            NamedTextColor.RED));
            sender.sendMessage(
                    Component.text(
                            "Groups: ENEMY, VISITOR, ACQUAINTANCE, FRIEND, ADMIN",
                            NamedTextColor.GRAY));
            return;
        }

        int claimId;
        try {
            claimId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid claim ID: " + args[1], NamedTextColor.RED));
            return;
        }

        Claim claim = plugin.getRepository().getClaimById(claimId);
        if (claim == null) {
            sender.sendMessage(
                    Component.text("Claim not found with ID: " + claimId, NamedTextColor.RED));
            return;
        }

        String playerName = args[2];
        UUID targetUuid = null;

        // Try to find player by name (online first, then database)
        Player onlinePlayer = Bukkit.getPlayer(playerName);
        if (onlinePlayer != null) {
            targetUuid = onlinePlayer.getUniqueId();
        } else {
            // Look up in claim database
            PlayerClaimData playerData =
                    plugin.getRepository().getPlayerDataByExactUsername(playerName);
            if (playerData == null) {
                playerData = plugin.getRepository().getPlayerDataByUsername(playerName);
            }
            if (playerData != null) {
                targetUuid = playerData.getUuid();
            }
        }

        if (targetUuid == null) {
            CommonMessages.PLAYER_NOT_FOUND.send(sender);
            return;
        }

        // Check if target is the claim owner
        if (claim.getOwnerUuid().equals(targetUuid)) {
            sender.sendMessage(
                    Component.text(
                            "Cannot change the owner's group - they always have full access.",
                            NamedTextColor.RED));
            return;
        }

        ClaimGroup group;
        try {
            group = ClaimGroup.valueOf(args[3].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Invalid group: " + args[3], NamedTextColor.RED));
            sender.sendMessage(
                    Component.text(
                            "Valid groups: ENEMY, VISITOR, ACQUAINTANCE, FRIEND, ADMIN",
                            NamedTextColor.GRAY));
            return;
        }

        // Set the member's group
        claim.setMemberGroup(targetUuid, group);
        plugin.getRepository().saveMember(claimId, targetUuid, group);

        // Invalidate cache
        plugin.getClaimManager().invalidateClaim(claimId);

        NamedTextColor groupColor = getGroupColor(group);
        sender.sendMessage(
                Component.text("Set ", NamedTextColor.GREEN)
                        .append(Component.text(playerName, NamedTextColor.WHITE))
                        .append(Component.text(" to group ", NamedTextColor.GREEN))
                        .append(Component.text(group.getDisplayName(), groupColor))
                        .append(Component.text(" in claim '", NamedTextColor.GREEN))
                        .append(Component.text(claim.getName(), NamedTextColor.WHITE))
                        .append(Component.text("' (ID: " + claimId + ")", NamedTextColor.GREEN)));
    }

    private void handleTogglePerm(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(
                    Component.text(
                            "Usage: /claimadmin toggleperm <claimId> <group> <permission>",
                            NamedTextColor.RED));
            sender.sendMessage(
                    Component.text(
                            "Groups: ENEMY, VISITOR, ACQUAINTANCE, FRIEND, ADMIN",
                            NamedTextColor.GRAY));
            sender.sendMessage(
                    Component.text("Use tab-complete for permission names", NamedTextColor.GRAY));
            return;
        }

        int claimId;
        try {
            claimId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid claim ID: " + args[1], NamedTextColor.RED));
            return;
        }

        Claim claim = plugin.getRepository().getClaimById(claimId);
        if (claim == null) {
            sender.sendMessage(
                    Component.text("Claim not found with ID: " + claimId, NamedTextColor.RED));
            return;
        }

        ClaimGroup group;
        try {
            group = ClaimGroup.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Invalid group: " + args[2], NamedTextColor.RED));
            sender.sendMessage(
                    Component.text(
                            "Valid groups: ENEMY, VISITOR, ACQUAINTANCE, FRIEND, ADMIN",
                            NamedTextColor.GRAY));
            return;
        }

        ClaimPermission permission;
        try {
            permission = ClaimPermission.valueOf(args[3].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(
                    Component.text("Invalid permission: " + args[3], NamedTextColor.RED));
            sender.sendMessage(
                    Component.text(
                            "Use tab-complete for valid permission names.", NamedTextColor.GRAY));
            return;
        }

        // Get or create group permissions
        GroupPermissions groupPerms = claim.getGroupPermissions();
        if (groupPerms == null) {
            groupPerms = new GroupPermissions(claim.getId());
            claim.setGroupPermissions(groupPerms);
        }

        // Toggle the permission
        boolean wasEnabled = groupPerms.hasPermission(group, permission);
        groupPerms.togglePermission(group, permission);
        boolean nowEnabled = groupPerms.hasPermission(group, permission);

        // Save to database
        plugin.getRepository().saveClaimSettings(claim);

        // Invalidate cache
        plugin.getClaimManager().invalidateClaim(claimId);

        String statusColor = nowEnabled ? "<green>" : "<red>";
        String status = nowEnabled ? "ENABLED" : "DISABLED";

        sender.sendMessage(
                Component.text("Toggled permission ", NamedTextColor.GREEN)
                        .append(Component.text(permission.getDisplayName(), NamedTextColor.AQUA))
                        .append(Component.text(" for group ", NamedTextColor.GREEN))
                        .append(Component.text(group.getDisplayName(), NamedTextColor.YELLOW))
                        .append(Component.text(" in claim '", NamedTextColor.GREEN))
                        .append(Component.text(claim.getName(), NamedTextColor.WHITE))
                        .append(Component.text("': ", NamedTextColor.GREEN))
                        .append(
                                Component.text(
                                        status,
                                        nowEnabled ? NamedTextColor.GREEN : NamedTextColor.RED)));
    }

    private NamedTextColor getGroupColor(ClaimGroup group) {
        return switch (group) {
            case ENEMY -> NamedTextColor.RED;
            case VISITOR -> NamedTextColor.YELLOW;
            case ACQUAINTANCE -> NamedTextColor.GREEN;
            case FRIEND -> NamedTextColor.AQUA;
            case ADMIN -> NamedTextColor.LIGHT_PURPLE;
        };
    }

    private void handleRepair(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(
                    Component.text("Usage: /claimadmin repair <claimId>", NamedTextColor.RED));
            return;
        }

        int claimId;
        try {
            claimId = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid claim ID: " + args[1], NamedTextColor.RED));
            return;
        }

        Claim claim = plugin.getRepository().getClaimById(claimId);
        if (claim == null) {
            sender.sendMessage(
                    Component.text("Claim not found with ID: " + claimId, NamedTextColor.RED));
            return;
        }

        if (!claim.hasNegativeChunks()) {
            sender.sendMessage(
                    Component.text(
                            "Claim ID " + claimId + " does not have negative chunks.",
                            NamedTextColor.YELLOW));
            sender.sendMessage(
                    Component.text(
                            "  Used: " + claim.getChunks().size() + "/" + claim.getTotalChunks(),
                            NamedTextColor.GRAY));
            return;
        }

        int deficit = claim.getChunkDeficit();
        int oldTotal = claim.getTotalChunks();
        int newTotal = claim.getChunks().size(); // Set total to match actual chunks

        // Update the claim
        claim.setTotalChunks(newTotal);
        claim.setPurchasedChunks(claim.getPurchasedChunks() + deficit);
        plugin.getRepository().saveClaimChunkData(claim);

        sender.sendMessage(
                Component.text(
                        "Repaired claim ID " + claimId + " (" + claim.getName() + ")",
                        NamedTextColor.GREEN));
        sender.sendMessage(Component.text("  Old total chunks: " + oldTotal, NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  New total chunks: " + newTotal, NamedTextColor.GRAY));
        sender.sendMessage(
                Component.text("  Fixed deficit: +" + deficit + " chunks", NamedTextColor.YELLOW));
    }

    private void handleRepairAll(CommandSender sender, String[] args) {
        sender.sendMessage(
                Component.text(
                        "Scanning all claims for chunk data issues...", NamedTextColor.YELLOW));

        List<Claim> allClaims = plugin.getRepository().getAllClaims();
        int repairedCount = 0;
        int totalDeficit = 0;

        for (Claim claim : allClaims) {
            if (claim.hasNegativeChunks()) {
                int deficit = claim.getChunkDeficit();
                int oldTotal = claim.getTotalChunks();
                int newTotal = claim.getChunks().size();

                // Update the claim
                claim.setTotalChunks(newTotal);
                claim.setPurchasedChunks(claim.getPurchasedChunks() + deficit);
                plugin.getRepository().saveClaimChunkData(claim);

                repairedCount++;
                totalDeficit += deficit;

                sender.sendMessage(
                        Component.text(
                                "  Repaired claim ID "
                                        + claim.getId()
                                        + " ("
                                        + claim.getName()
                                        + "): "
                                        + oldTotal
                                        + " -> "
                                        + newTotal
                                        + " chunks (+"
                                        + deficit
                                        + ")",
                                NamedTextColor.GRAY));
            }
        }

        if (repairedCount == 0) {
            sender.sendMessage(Component.text("No claims needed repair.", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Repair complete!", NamedTextColor.GREEN));
            sender.sendMessage(
                    Component.text(
                            "  Repaired: " + repairedCount + " claims", NamedTextColor.YELLOW));
            sender.sendMessage(
                    Component.text("  Total chunks added: " + totalDeficit, NamedTextColor.YELLOW));
        }
    }

    private void handleSetChunks(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(
                    Component.text(
                            "Usage: /claimadmin setchunks <claimId> <amount>", NamedTextColor.RED));
            return;
        }

        int claimId;
        int newAmount;
        try {
            claimId = Integer.parseInt(args[1]);
            newAmount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid numbers provided.", NamedTextColor.RED));
            return;
        }

        if (newAmount < 0) {
            sender.sendMessage(Component.text("Amount must be 0 or greater.", NamedTextColor.RED));
            return;
        }

        Claim claim = plugin.getRepository().getClaimById(claimId);
        if (claim == null) {
            sender.sendMessage(
                    Component.text("Claim not found with ID: " + claimId, NamedTextColor.RED));
            return;
        }

        int currentChunks = claim.getChunks().size();
        int oldTotal = claim.getTotalChunks();
        int oldPurchased = claim.getPurchasedChunks();

        if (newAmount < currentChunks) {
            sender.sendMessage(
                    Component.text(
                            "WARNING: Claim currently has " + currentChunks + " chunks claimed.",
                            NamedTextColor.YELLOW));
            sender.sendMessage(
                    Component.text(
                            "Setting total to "
                                    + newAmount
                                    + " will result in negative available chunks!",
                            NamedTextColor.YELLOW));
            sender.sendMessage(
                    Component.text(
                            "The owner will need to unclaim "
                                    + (currentChunks - newAmount)
                                    + " chunks.",
                            NamedTextColor.YELLOW));
        }

        // Calculate new purchased chunks based on the change
        int difference = newAmount - oldTotal;
        int newPurchased = Math.max(0, oldPurchased + difference);

        claim.setTotalChunks(newAmount);
        claim.setPurchasedChunks(newPurchased);
        plugin.getRepository().saveClaimChunkData(claim);

        sender.sendMessage(
                Component.text(
                        "Updated claim ID " + claimId + " (" + claim.getName() + ")",
                        NamedTextColor.GREEN));
        sender.sendMessage(
                Component.text(
                        "  Total chunks: " + oldTotal + " -> " + newAmount, NamedTextColor.GRAY));
        sender.sendMessage(
                Component.text(
                        "  Purchased chunks: " + oldPurchased + " -> " + newPurchased,
                        NamedTextColor.GRAY));
        sender.sendMessage(
                Component.text(
                        "  Currently claimed: " + currentChunks + "/" + newAmount,
                        NamedTextColor.GRAY));
        sender.sendMessage(
                Component.text("  Available: " + claim.getRemainingChunks(), NamedTextColor.GRAY));
    }

    /**
     * Give chunks to a player's global pool (perfect for Tebex store integration). Works offline -
     * creates player data if it doesn't exist.
     *
     * <p>Usage: /claimadmin givechunks <player> <amount> Example: /claimadmin givechunks Notch 10
     *
     * <p>For Tebex: Use command "claimadmin givechunks {username} 10" in your package
     */
    private void handleGiveChunks(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(
                    Component.text(
                            "Usage: /claimadmin givechunks <player> <amount>", NamedTextColor.RED));
            sender.sendMessage(
                    Component.text(
                            "Example: /claimadmin givechunks Notch 10", NamedTextColor.GRAY));
            sender.sendMessage(
                    Component.text(
                            "This gives chunks to the player's global pool that can be used in any claim.",
                            NamedTextColor.GRAY));
            return;
        }

        String playerName = args[1];
        int amount;

        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid amount: " + args[2], NamedTextColor.RED));
            return;
        }

        if (amount <= 0) {
            sender.sendMessage(
                    Component.text("Amount must be greater than 0.", NamedTextColor.RED));
            return;
        }

        // Run async to avoid blocking
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            try {
                                // Try to find player by exact username first
                                PlayerClaimData playerData =
                                        plugin.getRepository()
                                                .getPlayerDataByExactUsername(playerName);

                                // If not found, try partial match
                                if (playerData == null) {
                                    playerData =
                                            plugin.getRepository()
                                                    .getPlayerDataByUsername(playerName);
                                }

                                // If still not found, try to get UUID from Bukkit (works for
                                // offline players too)
                                if (playerData == null) {
                                    UUID playerUuid = null;
                                    Player onlinePlayer = Bukkit.getPlayerExact(playerName);

                                    if (onlinePlayer != null) {
                                        playerUuid = onlinePlayer.getUniqueId();
                                    } else {
                                        // Try to get offline player UUID
                                        @SuppressWarnings("deprecation")
                                        org.bukkit.OfflinePlayer offlinePlayer =
                                                Bukkit.getOfflinePlayer(playerName);
                                        if (offlinePlayer.hasPlayedBefore()
                                                || offlinePlayer.isOnline()) {
                                            playerUuid = offlinePlayer.getUniqueId();
                                        }
                                    }

                                    if (playerUuid == null) {
                                        plugin.getServer()
                                                .getScheduler()
                                                .runTask(
                                                        plugin,
                                                        () -> {
                                                            CommonMessages.PLAYER_NOT_FOUND.send(
                                                                    sender);
                                                            sender.sendMessage(
                                                                    Component.text(
                                                                            "The player must have joined the server at least once.",
                                                                            NamedTextColor.GRAY));
                                                        });
                                        return;
                                    }

                                    // Create new player data
                                    playerData =
                                            new PlayerClaimData(
                                                    playerUuid,
                                                    playerName,
                                                    plugin.getClaimConfig().getStartingChunks());
                                    plugin.getRepository().savePlayerData(playerData);
                                    plugin.getLogger()
                                            .info(
                                                    "Created new player data for "
                                                            + playerName
                                                            + " (UUID: "
                                                            + playerUuid
                                                            + ")");
                                }

                                // Save the original values for logging
                                final UUID finalUuid = playerData.getUuid();
                                final String finalUsername = playerData.getUsername();
                                final int oldTotal = playerData.getTotalChunks();
                                final int oldPurchased = playerData.getPurchasedChunks();

                                // Add chunks
                                playerData.addChunks(amount);
                                boolean saved = plugin.getRepository().savePlayerData(playerData);

                                if (!saved) {
                                    plugin.getLogger()
                                            .severe(
                                                    "Failed to save chunk grant for "
                                                            + finalUsername
                                                            + " - database error");
                                    plugin.getServer()
                                            .getScheduler()
                                            .runTask(
                                                    plugin,
                                                    () -> {
                                                        sender.sendMessage(
                                                                Component.text(
                                                                        "Failed to save chunk grant - check server logs",
                                                                        NamedTextColor.RED));
                                                    });
                                    return;
                                }

                                // Update cache if player data was cached
                                plugin.getClaimManager()
                                        .updateCachedPlayerData(finalUuid, playerData);

                                // Calculate new totals
                                final int newTotal = playerData.getTotalChunks();
                                final int newPurchased = playerData.getPurchasedChunks();

                                // Log to console for audit trail
                                plugin.getLogger()
                                        .info(
                                                "[TEBEX/ADMIN] "
                                                        + sender.getName()
                                                        + " gave "
                                                        + amount
                                                        + " chunk(s) to "
                                                        + finalUsername
                                                        + " (UUID: "
                                                        + finalUuid
                                                        + ") - Total: "
                                                        + oldTotal
                                                        + " -> "
                                                        + newTotal);

                                // Notify sender
                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    sender.sendMessage(
                                                            Component.text(
                                                                    "Successfully gave "
                                                                            + amount
                                                                            + " chunk(s) to "
                                                                            + finalUsername,
                                                                    NamedTextColor.GREEN));
                                                    sender.sendMessage(
                                                            Component.text(
                                                                    "  UUID: " + finalUuid,
                                                                    NamedTextColor.GRAY));
                                                    sender.sendMessage(
                                                            Component.text(
                                                                    "  Total chunks: "
                                                                            + oldTotal
                                                                            + " -> "
                                                                            + newTotal
                                                                            + " (+"
                                                                            + amount
                                                                            + ")",
                                                                    NamedTextColor.GRAY));
                                                    sender.sendMessage(
                                                            Component.text(
                                                                    "  Purchased chunks: "
                                                                            + oldPurchased
                                                                            + " -> "
                                                                            + newPurchased,
                                                                    NamedTextColor.GRAY));
                                                });

                                // Notify the player if they're online
                                Player targetPlayer = Bukkit.getPlayer(finalUuid);
                                if (targetPlayer != null && targetPlayer.isOnline()) {
                                    plugin.getServer()
                                            .getScheduler()
                                            .runTask(
                                                    plugin,
                                                    () -> {
                                                        targetPlayer.sendMessage(
                                                                Component.text(
                                                                                "You received ",
                                                                                NamedTextColor
                                                                                        .GREEN)
                                                                        .append(
                                                                                Component.text(
                                                                                        amount
                                                                                                + " claim chunk(s)",
                                                                                        NamedTextColor
                                                                                                .GOLD))
                                                                        .append(
                                                                                Component.text(
                                                                                        "! Use /claim to claim land.",
                                                                                        NamedTextColor
                                                                                                .GREEN)));
                                                        targetPlayer.sendMessage(
                                                                Component.text(
                                                                        "Total chunks: " + newTotal,
                                                                        NamedTextColor.GRAY));
                                                    });
                                }

                            } catch (Exception e) {
                                plugin.getLogger()
                                        .severe(
                                                "Error giving chunks to "
                                                        + playerName
                                                        + ": "
                                                        + e.getMessage());
                                e.printStackTrace();
                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    sender.sendMessage(
                                                            Component.text(
                                                                    "An error occurred - check server logs",
                                                                    NamedTextColor.RED));
                                                });
                            }
                        });
    }

    private void handleMigrate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(
                    Component.text("Usage: /claimadmin migrate <player>", NamedTextColor.RED));
            return;
        }

        String playerName = args[1];
        PlayerClaimData playerData =
                plugin.getRepository().getPlayerDataByExactUsername(playerName);
        if (playerData == null) {
            playerData = plugin.getRepository().getPlayerDataByUsername(playerName);
        }
        if (playerData == null) {
            CommonMessages.PLAYER_NOT_FOUND.send(sender);
            return;
        }

        List<Claim> claims = plugin.getRepository().getClaimsByOwner(playerData.getUuid());
        if (claims.isEmpty()) {
            sender.sendMessage(Component.text("Player has no claims.", NamedTextColor.YELLOW));
            return;
        }

        sender.sendMessage(
                Component.text(
                        "Migrating claims for " + playerData.getUsername() + "...",
                        NamedTextColor.YELLOW));

        int startingChunksPerClaim = plugin.getClaimConfig().getStartingChunksPerClaim();
        int playerGlobalPurchased = playerData.getPurchasedChunks();
        int totalClaimedChunks = 0;
        int totalClaimedAboveStarting = 0;

        // Calculate total claimed chunks and chunks above starting amount
        for (Claim claim : claims) {
            int claimed = claim.getChunks().size();
            totalClaimedChunks += claimed;
            totalClaimedAboveStarting += Math.max(0, claimed - startingChunksPerClaim);
        }

        sender.sendMessage(
                Component.text(
                        "  Player global purchased: " + playerGlobalPurchased,
                        NamedTextColor.GRAY));
        sender.sendMessage(
                Component.text(
                        "  Total chunks claimed: " + totalClaimedChunks, NamedTextColor.GRAY));
        sender.sendMessage(
                Component.text(
                        "  Total claimed above starting: " + totalClaimedAboveStarting,
                        NamedTextColor.GRAY));
        sender.sendMessage(
                Component.text(
                        "  Starting chunks per claim: " + startingChunksPerClaim,
                        NamedTextColor.GRAY));

        // DATA INTEGRITY CHECK: Detect if global purchased doesn't match reality
        if (totalClaimedAboveStarting > playerGlobalPurchased) {
            sender.sendMessage(
                    Component.text("WARNING: Data mismatch detected!", NamedTextColor.RED));
            sender.sendMessage(
                    Component.text(
                            "  Player has " + totalClaimedAboveStarting + " chunks above starting",
                            NamedTextColor.YELLOW));
            sender.sendMessage(
                    Component.text(
                            "  But only " + playerGlobalPurchased + " recorded as purchased",
                            NamedTextColor.YELLOW));
            sender.sendMessage(
                    Component.text(
                            "  Adjusting global purchased to match reality...",
                            NamedTextColor.YELLOW));

            // Update player data to reflect actual purchased chunks
            playerGlobalPurchased = totalClaimedAboveStarting;
            playerData =
                    new PlayerClaimData(
                            playerData.getUuid(),
                            playerData.getUsername(),
                            startingChunksPerClaim + playerGlobalPurchased,
                            playerGlobalPurchased,
                            playerData.getCreatedAt(),
                            playerData.getUpdatedAt());
            plugin.getRepository().savePlayerData(playerData);
            sender.sendMessage(
                    Component.text(
                            "  Updated global purchased to " + playerGlobalPurchased,
                            NamedTextColor.GREEN));
        }

        // Distribute purchased chunks PROPORTIONALLY based on usage
        int distributedPurchased = 0;
        for (int i = 0; i < claims.size(); i++) {
            Claim claim = claims.get(i);
            int claimedInThisClaim = claim.getChunks().size();
            int claimedAboveStarting = Math.max(0, claimedInThisClaim - startingChunksPerClaim);
            int oldTotal = claim.getTotalChunks();
            int oldPurchased = claim.getPurchasedChunks();

            int claimPurchased;
            if (totalClaimedAboveStarting == 0) {
                // No chunks purchased, just set starting
                claimPurchased = 0;
            } else if (i == claims.size() - 1) {
                // For the last claim, give any remaining to avoid rounding errors
                claimPurchased = playerGlobalPurchased - distributedPurchased;
            } else {
                // Proportional distribution: (chunks above starting) / (total above starting) *
                // global purchased
                claimPurchased =
                        (int)
                                Math.round(
                                        (double) claimedAboveStarting
                                                * playerGlobalPurchased
                                                / totalClaimedAboveStarting);
            }

            // Ensure non-negative
            claimPurchased = Math.max(0, claimPurchased);

            // Set totals
            int newTotal = startingChunksPerClaim + claimPurchased;
            claim.setTotalChunks(newTotal);
            claim.setPurchasedChunks(claimPurchased);
            plugin.getRepository().saveClaimChunkData(claim);

            distributedPurchased += claimPurchased;

            // Check for negative balance
            int available = newTotal - claimedInThisClaim;
            String availableColor = available >= 0 ? "<green>" : "<red>";

            sender.sendMessage(
                    Component.text(
                            "  Claim " + claim.getId() + " (" + claim.getName() + "):",
                            NamedTextColor.GRAY));
            sender.sendMessage(
                    Component.text(
                            "    Claimed: "
                                    + claimedInThisClaim
                                    + " | Total: "
                                    + oldTotal
                                    + " -> "
                                    + newTotal,
                            NamedTextColor.DARK_GRAY));
            sender.sendMessage(
                    Component.text(
                            "    Purchased: " + oldPurchased + " -> " + claimPurchased,
                            NamedTextColor.DARK_GRAY));
            sender.sendMessage(
                    Component.text(
                            "    Available: " + availableColor + available,
                            NamedTextColor.DARK_GRAY));

            if (available < 0) {
                sender.sendMessage(
                        Component.text(
                                "    WARNING: Negative balance! Player needs to unclaim "
                                        + Math.abs(available)
                                        + " chunks",
                                NamedTextColor.RED));
            }
        }

        // Verify the distribution
        if (distributedPurchased != playerGlobalPurchased) {
            int diff = Math.abs(distributedPurchased - playerGlobalPurchased);
            if (diff <= 1) {
                sender.sendMessage(
                        Component.text(
                                "Migration complete with "
                                        + diff
                                        + " chunk rounding difference (acceptable).",
                                NamedTextColor.GREEN));
            } else {
                sender.sendMessage(
                        Component.text(
                                "WARNING: Distributed "
                                        + distributedPurchased
                                        + " but player had "
                                        + playerGlobalPurchased
                                        + " purchased!",
                                NamedTextColor.RED));
            }
        } else {
            sender.sendMessage(
                    Component.text(
                            "Migration complete! Pricing will continue correctly.",
                            NamedTextColor.GREEN));
        }
    }

    private void handleMigrateAll(CommandSender sender, String[] args) {
        sender.sendMessage(
                Component.text("Scanning all players for migration...", NamedTextColor.YELLOW));

        // Get all unique player UUIDs from claims
        List<Claim> allClaims = plugin.getRepository().getAllClaims();
        java.util.Set<UUID> playerUUIDs = new java.util.HashSet<>();
        for (Claim claim : allClaims) {
            playerUUIDs.add(claim.getOwnerUuid());
        }

        int migratedCount = 0;
        int dataFixCount = 0;
        int warningCount = 0;

        for (UUID uuid : playerUUIDs) {
            PlayerClaimData playerData = plugin.getRepository().getPlayerData(uuid);
            if (playerData == null) continue;

            List<Claim> claims = plugin.getRepository().getClaimsByOwner(uuid);
            if (claims.isEmpty()) continue;

            int startingChunksPerClaim = plugin.getClaimConfig().getStartingChunksPerClaim();
            int playerGlobalPurchased = playerData.getPurchasedChunks();
            int totalClaimedAboveStarting = 0;

            // Calculate total claimed above starting
            for (Claim claim : claims) {
                int claimed = claim.getChunks().size();
                totalClaimedAboveStarting += Math.max(0, claimed - startingChunksPerClaim);
            }

            // Data integrity check
            if (totalClaimedAboveStarting > playerGlobalPurchased) {
                playerGlobalPurchased = totalClaimedAboveStarting;
                playerData =
                        new PlayerClaimData(
                                playerData.getUuid(),
                                playerData.getUsername(),
                                startingChunksPerClaim + playerGlobalPurchased,
                                playerGlobalPurchased,
                                playerData.getCreatedAt(),
                                playerData.getUpdatedAt());
                plugin.getRepository().savePlayerData(playerData);
                dataFixCount++;
            }

            // Distribute purchased chunks proportionally
            int distributedPurchased = 0;
            boolean hasNegativeBalance = false;

            for (int i = 0; i < claims.size(); i++) {
                Claim claim = claims.get(i);
                int claimedInThisClaim = claim.getChunks().size();
                int claimedAboveStarting = Math.max(0, claimedInThisClaim - startingChunksPerClaim);

                int claimPurchased;
                if (totalClaimedAboveStarting == 0) {
                    claimPurchased = 0;
                } else if (i == claims.size() - 1) {
                    claimPurchased = playerGlobalPurchased - distributedPurchased;
                } else {
                    claimPurchased =
                            (int)
                                    Math.round(
                                            (double) claimedAboveStarting
                                                    * playerGlobalPurchased
                                                    / totalClaimedAboveStarting);
                }

                claimPurchased = Math.max(0, claimPurchased);

                // Set totals
                int newTotal = startingChunksPerClaim + claimPurchased;
                claim.setTotalChunks(newTotal);
                claim.setPurchasedChunks(claimPurchased);
                plugin.getRepository().saveClaimChunkData(claim);

                distributedPurchased += claimPurchased;

                // Check for negative balance
                if (newTotal < claimedInThisClaim) {
                    hasNegativeBalance = true;
                }
            }

            if (hasNegativeBalance) {
                warningCount++;
            }

            migratedCount++;
            String status = hasNegativeBalance ? " <red>(has negative balance!)" : "";
            sender.sendMessage(
                    Component.text(
                            "  "
                                    + playerData.getUsername()
                                    + " ("
                                    + claims.size()
                                    + " claims, "
                                    + playerGlobalPurchased
                                    + " purchased)"
                                    + status,
                            NamedTextColor.GRAY));
        }

        sender.sendMessage(Component.text("Migration complete!", NamedTextColor.GREEN));
        sender.sendMessage(
                Component.text("  Migrated: " + migratedCount + " players", NamedTextColor.GRAY));
        sender.sendMessage(
                Component.text("  Data fixes: " + dataFixCount + " players", NamedTextColor.GRAY));
        if (warningCount > 0) {
            sender.sendMessage(
                    Component.text(
                            "  Warnings: " + warningCount + " players with negative balances",
                            NamedTextColor.RED));
        }
    }

    private void handleStats(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return;
        }

        ClaimAdminStatsCommand.handleStatsSubcommand(plugin, player);
    }

    /**
     * Migrate existing per-claim purchased chunks to the new global chunk pool system. Usage:
     * /claimadmin migrate-chunk-pool [--dry-run]
     */
    private void handleMigrateChunkPool(CommandSender sender, String[] args) {
        boolean dryRun = args.length > 1 && args[1].equalsIgnoreCase("--dry-run");

        if (dryRun) {
            sender.sendMessage(
                    Component.text(
                            "=== DRY RUN MODE - No changes will be made ===",
                            NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(
                    Component.text(
                            "=== Starting Global Chunk Pool Migration ===", NamedTextColor.GOLD));
            sender.sendMessage(
                    Component.text(
                            "This will migrate all purchased chunks to the new global pool system.",
                            NamedTextColor.GRAY));
        }

        sender.sendMessage(Component.text("Scanning all claims...", NamedTextColor.YELLOW));

        // Run migration asynchronously
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            try {
                                // Get all claims from database
                                List<Claim> allClaims = plugin.getRepository().getAllClaims();

                                // Calculate total purchased chunks per player
                                java.util.Map<UUID, Integer> chunksByPlayer =
                                        new java.util.HashMap<>();
                                java.util.Map<UUID, String> playerNames = new java.util.HashMap<>();

                                for (Claim claim : allClaims) {
                                    UUID ownerUuid = claim.getOwnerUuid();
                                    int purchased = claim.getPurchasedChunks();

                                    chunksByPlayer.merge(ownerUuid, purchased, Integer::sum);

                                    // Cache player name
                                    if (!playerNames.containsKey(ownerUuid)) {
                                        PlayerClaimData playerData =
                                                plugin.getRepository().getPlayerData(ownerUuid);
                                        if (playerData != null) {
                                            playerNames.put(ownerUuid, playerData.getUsername());
                                        }
                                    }
                                }

                                // Report findings
                                final int totalPlayers = chunksByPlayer.size();
                                final int totalChunks =
                                        chunksByPlayer.values().stream()
                                                .mapToInt(Integer::intValue)
                                                .sum();

                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    sender.sendMessage(
                                                            Component.text(
                                                                    "Scan complete:",
                                                                    NamedTextColor.GREEN));
                                                    sender.sendMessage(
                                                            Component.text(
                                                                    "  Players: " + totalPlayers,
                                                                    NamedTextColor.GRAY));
                                                    sender.sendMessage(
                                                            Component.text(
                                                                    "  Total purchased chunks: "
                                                                            + totalChunks,
                                                                    NamedTextColor.GRAY));
                                                    sender.sendMessage(
                                                            Component.text(
                                                                    "  Claims scanned: "
                                                                            + allClaims.size(),
                                                                    NamedTextColor.GRAY));
                                                });

                                if (dryRun) {
                                    // Show preview of what would be migrated
                                    plugin.getServer()
                                            .getScheduler()
                                            .runTask(
                                                    plugin,
                                                    () -> {
                                                        sender.sendMessage(
                                                                Component.text(
                                                                        "Preview of migration (top 10 players):",
                                                                        NamedTextColor.YELLOW));
                                                        chunksByPlayer.entrySet().stream()
                                                                .sorted(
                                                                        (e1, e2) ->
                                                                                e2.getValue()
                                                                                        .compareTo(
                                                                                                e1
                                                                                                        .getValue()))
                                                                .limit(10)
                                                                .forEach(
                                                                        entry -> {
                                                                            String name =
                                                                                    playerNames
                                                                                            .getOrDefault(
                                                                                                    entry
                                                                                                            .getKey(),
                                                                                                    "Unknown");
                                                                            sender.sendMessage(
                                                                                    Component.text(
                                                                                            "  "
                                                                                                    + name
                                                                                                    + ": "
                                                                                                    + entry
                                                                                                            .getValue()
                                                                                                    + " chunks",
                                                                                            NamedTextColor
                                                                                                    .GRAY));
                                                                        });
                                                        sender.sendMessage(
                                                                Component.text(
                                                                        "Run without --dry-run to perform migration",
                                                                        NamedTextColor.YELLOW));
                                                    });
                                    return;
                                }

                                // Perform actual migration
                                sender.sendMessage(
                                        Component.text(
                                                "Migrating chunk pools...", NamedTextColor.YELLOW));

                                int migratedPlayers = 0;
                                int existingPoolsUpdated = 0;

                                for (java.util.Map.Entry<UUID, Integer> entry :
                                        chunksByPlayer.entrySet()) {
                                    UUID playerUuid = entry.getKey();
                                    int totalChunksForPlayer = entry.getValue();

                                    if (totalChunksForPlayer > 0) {
                                        // Check if pool already exists
                                        net.serverplugins.claim.models.PlayerChunkPool existingPool =
                                                plugin.getRepository()
                                                        .getOrCreatePlayerChunkPool(playerUuid);

                                        if (existingPool.getPurchasedChunks() > 0) {
                                            // Pool already has chunks - add to existing
                                            existingPool.setPurchasedChunks(
                                                    existingPool.getPurchasedChunks()
                                                            + totalChunksForPlayer);
                                            plugin.getRepository()
                                                    .savePlayerChunkPool(existingPool);
                                            existingPoolsUpdated++;
                                        } else {
                                            // New pool - set chunks
                                            existingPool.setPurchasedChunks(totalChunksForPlayer);
                                            plugin.getRepository()
                                                    .savePlayerChunkPool(existingPool);
                                            migratedPlayers++;
                                        }
                                    }
                                }

                                // Migration complete
                                final int finalMigrated = migratedPlayers;
                                final int finalUpdated = existingPoolsUpdated;

                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    sender.sendMessage(
                                                            Component.text(
                                                                    "=== Migration Complete ===",
                                                                    NamedTextColor.GREEN));
                                                    sender.sendMessage(
                                                            Component.text(
                                                                    "New pools created: "
                                                                            + finalMigrated,
                                                                    NamedTextColor.GRAY));
                                                    sender.sendMessage(
                                                            Component.text(
                                                                    "Existing pools updated: "
                                                                            + finalUpdated,
                                                                    NamedTextColor.GRAY));
                                                    sender.sendMessage(
                                                            Component.text(
                                                                    "Total chunks migrated: "
                                                                            + totalChunks,
                                                                    NamedTextColor.GRAY));
                                                    sender.sendMessage(
                                                            Component.text(
                                                                    "", NamedTextColor.GRAY));
                                                    sender.sendMessage(
                                                            Component.text(
                                                                    "IMPORTANT: Chunks are now in global pools!",
                                                                    NamedTextColor.GOLD));
                                                    sender.sendMessage(
                                                            Component.text(
                                                                    "Players can allocate them to profiles via /claimshop",
                                                                    NamedTextColor.GRAY));
                                                });

                                plugin.getLogger()
                                        .info(
                                                "Chunk pool migration completed: "
                                                        + finalMigrated
                                                        + " new pools, "
                                                        + finalUpdated
                                                        + " updated, "
                                                        + totalChunks
                                                        + " total chunks");

                            } catch (Exception e) {
                                plugin.getLogger()
                                        .severe(
                                                "Error during chunk pool migration: "
                                                        + e.getMessage());
                                e.printStackTrace();
                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    sender.sendMessage(
                                                            Component.text(
                                                                    "Migration failed - check server logs",
                                                                    NamedTextColor.RED));
                                                    sender.sendMessage(
                                                            Component.text(
                                                                    "Error: " + e.getMessage(),
                                                                    NamedTextColor.RED));
                                                });
                            }
                        });
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(
                Component.text("=== ServerClaim Admin Commands ===", NamedTextColor.GOLD));
        sender.sendMessage(
                Component.text("/claimadmin list <player>", NamedTextColor.YELLOW)
                        .append(Component.text(" - List player's claims", NamedTextColor.GRAY)));
        sender.sendMessage(
                Component.text("/claimadmin info <claimId>", NamedTextColor.YELLOW)
                        .append(Component.text(" - View claim details", NamedTextColor.GRAY)));
        sender.sendMessage(
                Component.text("/claimadmin delete <claimId>", NamedTextColor.YELLOW)
                        .append(Component.text(" - Delete a claim by ID", NamedTextColor.GRAY)));
        sender.sendMessage(
                Component.text("/claimadmin deleteat <world> <x> <z>", NamedTextColor.YELLOW)
                        .append(
                                Component.text(
                                        " - Delete claim at coordinates", NamedTextColor.GRAY)));
        sender.sendMessage(
                Component.text("/claimadmin tp <claimId>", NamedTextColor.YELLOW)
                        .append(Component.text(" - Teleport to a claim", NamedTextColor.GRAY)));
        sender.sendMessage(
                Component.text(
                                "/claimadmin setgroup <claimId> <player> <group>",
                                NamedTextColor.YELLOW)
                        .append(
                                Component.text(
                                        " - Set player's group in claim", NamedTextColor.GRAY)));
        sender.sendMessage(
                Component.text(
                                "/claimadmin toggleperm <claimId> <group> <perm>",
                                NamedTextColor.YELLOW)
                        .append(Component.text(" - Toggle group permission", NamedTextColor.GRAY)));
        sender.sendMessage(
                Component.text("/claimadmin setchunks <claimId> <amount>", NamedTextColor.YELLOW)
                        .append(
                                Component.text(
                                        " - Set total chunks for a claim", NamedTextColor.GRAY)));
        sender.sendMessage(
                Component.text("/claimadmin givechunks <player> <amount>", NamedTextColor.YELLOW)
                        .append(
                                Component.text(
                                        " - Give chunks to player (Tebex compatible)",
                                        NamedTextColor.GRAY)));
        sender.sendMessage(
                Component.text("/claimadmin migrate <player>", NamedTextColor.YELLOW)
                        .append(
                                Component.text(
                                        " - Migrate player to per-claim chunks",
                                        NamedTextColor.GRAY)));
        sender.sendMessage(
                Component.text("/claimadmin migrateall", NamedTextColor.YELLOW)
                        .append(
                                Component.text(
                                        " - Migrate all players to per-claim chunks",
                                        NamedTextColor.GRAY)));
        sender.sendMessage(
                Component.text("/claimadmin repair <claimId>", NamedTextColor.YELLOW)
                        .append(
                                Component.text(
                                        " - Fix negative chunk data for a claim",
                                        NamedTextColor.GRAY)));
        sender.sendMessage(
                Component.text("/claimadmin repairall", NamedTextColor.YELLOW)
                        .append(
                                Component.text(
                                        " - Fix negative chunk data for all claims",
                                        NamedTextColor.GRAY)));
        sender.sendMessage(
                Component.text("/claimadmin migrate-chunk-pool [--dry-run]", NamedTextColor.YELLOW)
                        .append(
                                Component.text(
                                        " - Migrate existing chunks to global pool system",
                                        NamedTextColor.GRAY)));
        sender.sendMessage(
                Component.text("/claimadmin stats", NamedTextColor.YELLOW)
                        .append(Component.text(" - View server statistics", NamedTextColor.GRAY)));
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (!sender.hasPermission("serverclaim.admin")) {
            return List.of();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(
                    List.of(
                            "list",
                            "info",
                            "delete",
                            "deleteat",
                            "tp",
                            "setgroup",
                            "toggleperm",
                            "setchunks",
                            "givechunks",
                            "migrate",
                            "migrateall",
                            "migrate-chunk-pool",
                            "repair",
                            "repairall",
                            "stats"));
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("list")) {
                // Suggest online players
                Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
            } else if (sub.equals("deleteat")) {
                // Suggest worlds
                Bukkit.getWorlds().forEach(w -> completions.add(w.getName()));
            }
            // For info, delete, tp, setgroup, toggleperm - arg 2 is claimId (no completion)
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("setgroup")) {
                // Suggest online players for arg 3 (player name)
                Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
            } else if (sub.equals("toggleperm")) {
                // Suggest groups for arg 3
                for (ClaimGroup group : ClaimGroup.values()) {
                    completions.add(group.name());
                }
            }
        } else if (args.length == 4) {
            String sub = args[0].toLowerCase();
            if (sub.equals("setgroup")) {
                // Suggest groups for arg 4
                for (ClaimGroup group : ClaimGroup.values()) {
                    completions.add(group.name());
                }
            } else if (sub.equals("toggleperm")) {
                // Suggest permissions for arg 4
                for (ClaimPermission perm : ClaimPermission.values()) {
                    completions.add(perm.name());
                }
            }
        }

        String lastArg = args[args.length - 1].toLowerCase();
        return completions.stream().filter(s -> s.toLowerCase().startsWith(lastArg)).toList();
    }
}
