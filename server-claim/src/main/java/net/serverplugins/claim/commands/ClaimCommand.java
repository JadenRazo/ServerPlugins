package net.serverplugins.claim.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.messages.Placeholder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.gui.AddMemberGui;
import net.serverplugins.claim.gui.ChunkShopGui;
import net.serverplugins.claim.gui.ClaimMenuGui;
import net.serverplugins.claim.gui.ClaimSettingsGui;
import net.serverplugins.claim.gui.LandBankGui;
import net.serverplugins.claim.gui.MyProfilesGui;
import net.serverplugins.claim.gui.RewardsGui;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.util.CommandRateLimiter;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class ClaimCommand implements CommandExecutor, TabCompleter {

    private final ServerClaim plugin;
    private final CommandRateLimiter rateLimiter;

    public ClaimCommand(ServerClaim plugin) {
        this.plugin = plugin;
        // 1 second cooldown to prevent spam
        this.rateLimiter = new CommandRateLimiter("claim", 1);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!player.hasPermission("serverclaim.claim")) {
            CommonMessages.NO_PERMISSION.send(player);
            return true;
        }

        // Rate limiting to prevent spam (admins bypass)
        if (!rateLimiter.canExecute(player)) {
            rateLimiter.sendCooldownMessage(player);
            return true;
        }

        // No args = open menu
        if (args.length == 0) {
            new ClaimMenuGui(plugin, player).open();
            return true;
        }

        // Handle subcommands for quick actions
        String sub = args[0].toLowerCase();

        switch (sub) {
            case "here", "now" -> {
                // Quick claim current chunk
                plugin.getClaimManager()
                        .claimChunk(player, player.getLocation().getChunk())
                        .thenAccept(
                                result -> {
                                    if (result.success()) {
                                        TextUtil.send(
                                                player,
                                                plugin.getClaimConfig()
                                                        .getMessage("claim-success"));
                                    } else {
                                        TextUtil.send(
                                                player,
                                                plugin.getClaimConfig()
                                                        .getMessage(result.messageKey()));
                                    }
                                });
            }
            case "unclaim" -> {
                // Quick unclaim current chunk
                plugin.getClaimManager()
                        .unclaimChunk(player, player.getLocation().getChunk())
                        .thenAccept(
                                result -> {
                                    if (result.success()) {
                                        TextUtil.send(
                                                player,
                                                plugin.getClaimConfig()
                                                        .getMessage("unclaim-success"));
                                    } else {
                                        TextUtil.send(
                                                player,
                                                plugin.getClaimConfig()
                                                        .getMessage(result.messageKey()));
                                    }
                                });
            }
            case "info" -> {
                // Show claim info
                showClaimInfo(player);
            }
            case "help" -> {
                sendHelp(player);
            }
            case "group" -> {
                handleGroupCommand(player, args);
            }
            case "auto" -> {
                plugin.getMovementListener().toggleAutoClaim(player);
            }
            case "fly" -> {
                plugin.getMovementListener().toggleClaimFly(player);
            }
            case "ban" -> {
                handleBanCommand(player, args, true);
            }
            case "unban" -> {
                handleBanCommand(player, args, false);
            }
            case "welcome" -> {
                handleWelcomeCommand(player, args);
            }
            case "settings" -> {
                handleSettingsCommand(player);
            }
            case "bank" -> {
                handleBankCommand(player);
            }
            case "rewards" -> {
                new RewardsGui(plugin, player).open();
            }
            case "profiles" -> {
                new MyProfilesGui(plugin, player).open();
            }
            case "shop" -> {
                // Check if chunk pool is cached, if not load it asynchronously
                if (plugin.getClaimManager().getPlayerChunkPool(player.getUniqueId()) == null) {
                    TextUtil.send(player, "<gray>Loading chunk shop...");
                    plugin.getServer()
                            .getScheduler()
                            .runTaskAsynchronously(
                                    plugin,
                                    () -> {
                                        try {
                                            plugin.getClaimManager()
                                                    .getPlayerChunkPool(player.getUniqueId());
                                            // Now open GUI on main thread
                                            plugin.getServer()
                                                    .getScheduler()
                                                    .runTask(
                                                            plugin,
                                                            () -> {
                                                                new ChunkShopGui(plugin, player)
                                                                        .open();
                                                            });
                                        } catch (Exception e) {
                                            plugin.getLogger()
                                                    .severe(
                                                            "Failed to load chunk pool for "
                                                                    + player.getName()
                                                                    + ": "
                                                                    + e.getMessage());
                                            plugin.getServer()
                                                    .getScheduler()
                                                    .runTask(
                                                            plugin,
                                                            () -> {
                                                                TextUtil.send(
                                                                        player,
                                                                        "<red>Failed to load chunk shop data. Please try again.");
                                                            });
                                        }
                                    });
                } else {
                    new ChunkShopGui(plugin, player).open();
                }
            }
            case "reload" -> {
                if (!player.hasPermission("serverclaim.admin.reload")) {
                    CommonMessages.NO_PERMISSION.send(player);
                } else {
                    plugin.reloadConfiguration();
                    TextUtil.send(player, "<green>✓ ServerClaim configuration reloaded!");
                    TextUtil.send(
                            player,
                            "<gray>Allowed Worlds: <white>"
                                    + String.join(
                                            ", ", plugin.getClaimConfig().getAllowedWorlds()));
                    TextUtil.send(
                            player,
                            "<gray>Spawn Radius: <white>"
                                    + plugin.getClaimConfig().getSpawnProtectionRadius()
                                    + " blocks");
                }
            }
            default -> {
                TextUtil.send(player, "<red>Unknown subcommand. Use /claim help for help.");
            }
        }

        return true;
    }

    private void showClaimInfo(Player player) {
        var claim = plugin.getClaimManager().getClaimAt(player.getLocation().getChunk());
        var data = plugin.getClaimManager().getPlayerData(player.getUniqueId());
        int remaining = plugin.getClaimManager().getRemainingChunks(player.getUniqueId());
        int total =
                data != null ? data.getTotalChunks() : plugin.getClaimConfig().getStartingChunks();
        int currentChunks = total - remaining;
        int maxChunks = plugin.getClaimConfig().getMaxChunks();

        TextUtil.send(player, "");
        TextUtil.send(player, "<gold><bold>Claim Info</bold></gold>");
        TextUtil.send(player, "");

        if (claim != null) {
            String owner =
                    claim.isOwner(player.getUniqueId())
                            ? "You"
                            : plugin.getRepository()
                                    .getPlayerData(claim.getOwnerUuid())
                                    .getUsername();
            TextUtil.send(player, "<gray>This chunk: <white>Claimed by " + owner);
        } else {
            TextUtil.send(player, "<gray>This chunk: <green>Unclaimed");
        }

        TextUtil.send(player, "");
        TextUtil.send(player, "<gray>Your chunks: <yellow>" + currentChunks + "/" + total);
        TextUtil.send(player, "<gray>Available: <green>" + remaining);

        // Show next chunk cost if player can buy more
        if (currentChunks < maxChunks) {
            double nextChunkCost = calculateNextChunkCost(currentChunks);

            // Check if player can afford it
            if (plugin.getEconomy() != null) {
                double balance = plugin.getEconomy().getBalance(player);
                int affordableChunks = 0;
                double runningCost = 0;

                // Calculate how many chunks they can afford
                for (int i = currentChunks; i < maxChunks; i++) {
                    double chunkCost = calculateNextChunkCost(i);
                    if (runningCost + chunkCost <= balance) {
                        runningCost += chunkCost;
                        affordableChunks++;
                    } else {
                        break;
                    }
                }

                if (balance >= nextChunkCost) {
                    TextUtil.send(
                            player,
                            "<gray>Next chunk: <green>$"
                                    + String.format("%.2f", nextChunkCost)
                                    + " <gray>(you can afford <aqua>"
                                    + affordableChunks
                                    + "</aqua> more)");
                } else {
                    double needed = nextChunkCost - balance;
                    TextUtil.send(
                            player,
                            "<gray>Next chunk: <red>$"
                                    + String.format("%.2f", nextChunkCost)
                                    + " <gray>(need <red>$"
                                    + String.format("%.2f", needed)
                                    + "</red> more)");
                }
            } else {
                TextUtil.send(
                        player,
                        "<gray>Next chunk cost: <yellow>$" + String.format("%.2f", nextChunkCost));
            }
        } else {
            TextUtil.send(player, "<yellow>⚠ You've reached the maximum chunk limit!");
        }

        TextUtil.send(player, "");
    }

    /**
     * Calculate the cost of the next chunk based on current chunk count. Uses exponential pricing
     * formula from the plugin configuration.
     *
     * @param currentChunks The number of chunks currently owned
     * @return The cost of the next chunk
     */
    private double calculateNextChunkCost(int currentChunks) {
        double baseCost = plugin.getClaimConfig().getBasePrice();
        double multiplier = plugin.getClaimConfig().getGrowthRate();
        return baseCost * Math.pow(multiplier, currentChunks);
    }

    private void sendHelp(Player player) {
        TextUtil.send(player, "");
        TextUtil.send(player, "<gold><bold>Claim Commands</bold></gold>");
        TextUtil.send(player, "");
        TextUtil.send(player, "<yellow>/claim <gray>- Open the claim menu");
        TextUtil.send(player, "<yellow>/claim here <gray>- Quick claim current chunk");
        TextUtil.send(player, "<yellow>/claim unclaim <gray>- Quick unclaim current chunk");
        TextUtil.send(player, "<yellow>/claim info <gray>- Show claim information");
        TextUtil.send(player, "<yellow>/claim auto <gray>- Toggle auto-claim mode");
        TextUtil.send(player, "<yellow>/claim fly <gray>- Toggle claim fly (requires rank)");
        TextUtil.send(player, "<yellow>/claim group add <player> <gray>- Add a player to a group");
        TextUtil.send(player, "<yellow>/claim ban <player> <gray>- Ban a player from your claim");
        TextUtil.send(
                player, "<yellow>/claim unban <player> <gray>- Unban a player from your claim");
        TextUtil.send(player, "<yellow>/claim welcome <message> <gray>- Set welcome message");
        TextUtil.send(player, "<yellow>/claim settings <gray>- Open claim settings");
        TextUtil.send(player, "<yellow>/claim bank <gray>- Open claim land bank");
        TextUtil.send(player, "<yellow>/claim rewards <gray>- View dust color rewards");
        TextUtil.send(player, "");
    }

    private void handleGroupCommand(Player player, String[] args) {
        // /claim group add <player>
        if (args.length < 2) {
            TextUtil.send(player, "<red>✗ Usage: /claim group add <player>");
            return;
        }

        String subAction = args[1].toLowerCase();

        if (!subAction.equals("add")) {
            TextUtil.send(player, "<red>✗ Usage: /claim group add <player>");
            return;
        }

        if (args.length < 3) {
            TextUtil.send(player, "<red>✗ Usage: /claim group add <player>");
            return;
        }

        String targetName = args[2];

        // Get player's claim at current location
        Claim claim = plugin.getClaimManager().getClaimAt(player.getLocation().getChunk());

        if (claim == null) {
            TextUtil.send(player, "<red>✗ You are not standing in a claimed area");
            TextUtil.send(player, "<gray>→ Stand in your claim to add members");
            return;
        }

        if (!claim.isOwner(player.getUniqueId()) && !player.hasPermission("serverclaim.admin")) {
            TextUtil.send(player, "<red>✗ You do not own this claim");
            TextUtil.send(player, "<gray>→ Only the claim owner can add members");
            return;
        }

        // Find target player (online or offline)
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        UUID targetUuid = target.getUniqueId();

        // Check if it's a valid player (has played before or is online)
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            CommonMessages.PLAYER_NOT_FOUND.send(player, Placeholder.of("player", targetName));
            TextUtil.send(player, "<gray>→ They must have joined the server at least once");
            return;
        }

        String resolvedName = target.getName() != null ? target.getName() : targetName;

        // Check if trying to add themselves
        if (targetUuid.equals(player.getUniqueId())) {
            TextUtil.send(player, "<red>✗ You cannot add yourself to a group");
            return;
        }

        // Open the group selection GUI
        new AddMemberGui(plugin, player, claim, targetUuid, resolvedName).open();
    }

    private void handleBanCommand(Player player, String[] args, boolean isBan) {
        if (args.length < 2) {
            TextUtil.send(
                    player, "<red>✗ Usage: /claim " + (isBan ? "ban" : "unban") + " <player>");
            return;
        }

        String targetName = args[1];

        Claim claim = plugin.getClaimManager().getClaimAt(player.getLocation().getChunk());
        if (claim == null) {
            TextUtil.send(player, "<red>✗ You are not standing in a claimed area");
            TextUtil.send(
                    player,
                    "<gray>→ Stand in your claim to " + (isBan ? "ban" : "unban") + " players");
            return;
        }

        if (!claim.isOwner(player.getUniqueId()) && !player.hasPermission("serverclaim.admin")) {
            TextUtil.send(player, "<red>✗ You do not own this claim");
            TextUtil.send(
                    player,
                    "<gray>→ Only the claim owner can " + (isBan ? "ban" : "unban") + " players");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        UUID targetUuid = target.getUniqueId();

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            CommonMessages.PLAYER_NOT_FOUND.send(player, Placeholder.of("player", targetName));
            TextUtil.send(player, "<gray>→ They must have joined the server at least once");
            return;
        }

        String resolvedName = target.getName() != null ? target.getName() : targetName;

        if (targetUuid.equals(player.getUniqueId())) {
            TextUtil.send(player, "<red>✗ You cannot " + (isBan ? "ban" : "unban") + " yourself");
            return;
        }

        if (isBan) {
            claim.banPlayer(targetUuid);
            plugin.getRepository().banPlayer(claim.getId(), targetUuid);
            TextUtil.send(
                    player, "<green>✓ Banned <white>" + resolvedName + " <green>from your claim");
            TextUtil.send(player, "<gray>→ They can no longer enter or interact with this claim");

            // Kick them out if they're in the claim
            Player targetPlayer = Bukkit.getPlayer(targetUuid);
            if (targetPlayer != null && targetPlayer.isOnline()) {
                Claim targetClaim =
                        plugin.getClaimManager().getClaimAt(targetPlayer.getLocation().getChunk());
                if (targetClaim != null && targetClaim.getId() == claim.getId()) {
                    targetPlayer.teleport(player.getWorld().getSpawnLocation());
                    TextUtil.send(targetPlayer, "<red>✗ You have been banned from this claim!");
                }
            }
        } else {
            claim.unbanPlayer(targetUuid);
            plugin.getRepository().unbanPlayer(claim.getId(), targetUuid);
            TextUtil.send(player, "<green>✓ Unbanned <white>" + resolvedName);
            TextUtil.send(player, "<gray>→ They can now access this claim again");
        }
    }

    private void handleWelcomeCommand(Player player, String[] args) {
        Claim claim = plugin.getClaimManager().getClaimAt(player.getLocation().getChunk());
        if (claim == null) {
            TextUtil.send(player, "<red>✗ You are not standing in a claimed area");
            TextUtil.send(player, "<gray>→ Stand in your claim to set a welcome message");
            return;
        }

        if (!claim.isOwner(player.getUniqueId()) && !player.hasPermission("serverclaim.admin")) {
            TextUtil.send(player, "<red>✗ You do not own this claim");
            TextUtil.send(player, "<gray>→ Only the claim owner can set welcome messages");
            return;
        }

        if (args.length < 2) {
            // Clear welcome message
            claim.setWelcomeMessage(null);
            plugin.getRepository().saveClaim(claim);
            TextUtil.send(player, "<green>✓ Welcome message cleared");
            return;
        }

        // Join args to form message
        StringBuilder message = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) message.append(" ");
            message.append(args[i]);
        }

        String welcomeMessage = message.toString();
        if (welcomeMessage.length() > 256) {
            TextUtil.send(player, "<red>✗ Welcome message is too long");
            TextUtil.send(
                    player,
                    "<gray>→ Maximum 256 characters (current: " + welcomeMessage.length() + ")");
            return;
        }

        claim.setWelcomeMessage(welcomeMessage);
        plugin.getRepository().saveClaim(claim);
        TextUtil.send(player, "<green>✓ Welcome message set");
        TextUtil.send(player, "<gray>→ <white>" + welcomeMessage);
    }

    private void handleSettingsCommand(Player player) {
        Claim claim = plugin.getClaimManager().getClaimAt(player.getLocation().getChunk());

        // If standing in own claim, open settings directly
        if (claim != null && claim.isOwner(player.getUniqueId())) {
            new ClaimSettingsGui(plugin, player, claim).open();
            return;
        }

        // Not in own claim - show claims list to select from
        List<Claim> playerClaims = plugin.getClaimManager().getPlayerClaims(player.getUniqueId());
        if (playerClaims.isEmpty()) {
            TextUtil.send(
                    player, "<red>You don't have any claims yet! Use /claim to claim land first.");
            return;
        }

        // Open MyProfilesGui to select which claim's settings to view
        new MyProfilesGui(plugin, player).open();
    }

    private void handleBankCommand(Player player) {
        if (!plugin.getClaimConfig().isLandBankEnabled()) {
            TextUtil.send(player, "<red>The land bank system is currently disabled.");
            return;
        }

        Claim claim = plugin.getClaimManager().getClaimAt(player.getLocation().getChunk());

        if (claim != null && claim.isOwner(player.getUniqueId())) {
            new LandBankGui(plugin, player, claim).open();
            return;
        }

        // Not in own claim - check if player has any claims
        List<Claim> playerClaims = plugin.getClaimManager().getPlayerClaims(player.getUniqueId());
        if (playerClaims.isEmpty()) {
            TextUtil.send(
                    player, "<red>You don't have any claims yet! Use /claim to claim land first.");
            return;
        }

        // If they have exactly one claim, open bank for that claim
        if (playerClaims.size() == 1) {
            new LandBankGui(plugin, player, playerClaims.get(0)).open();
            return;
        }

        // Multiple claims - direct them to select one
        TextUtil.send(
                player,
                "<yellow>Stand in your claim to open its bank, or use /claim profiles to select one.");
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return Arrays.asList(
                            "here",
                            "unclaim",
                            "info",
                            "help",
                            "group",
                            "auto",
                            "fly",
                            "ban",
                            "unban",
                            "welcome",
                            "settings",
                            "bank",
                            "rewards",
                            "profiles",
                            "shop")
                    .stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("group")) {
            return Arrays.asList("add").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .toList();
        }

        if (args.length == 3
                && args[0].equalsIgnoreCase("group")
                && args[1].equalsIgnoreCase("add")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                    .toList();
        }

        if (args.length == 2
                && (args[0].equalsIgnoreCase("ban") || args[0].equalsIgnoreCase("unban"))) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }

        return new ArrayList<>();
    }
}
