package net.serverplugins.claim.commands;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.Nation;
import net.serverplugins.claim.models.NationInvite;
import net.serverplugins.claim.models.NationMember;
import net.serverplugins.claim.models.NationRelation;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class NationCommand implements CommandExecutor, TabCompleter {

    private final ServerClaim plugin;

    public NationCommand(ServerClaim plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!plugin.getNationManager().isNationsEnabled()) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<red>The nations system is currently disabled.");
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create" -> handleCreate(player, args);
            case "info" -> handleInfo(player, args);
            case "invite" -> handleInvite(player, args);
            case "accept" -> handleAccept(player, args);
            case "kick" -> handleKick(player, args);
            case "leave" -> handleLeave(player);
            case "disband" -> handleDisband(player);
            case "promote" -> handlePromote(player, args);
            case "demote" -> handleDemote(player, args);
            case "relation" -> handleRelation(player, args);
            case "chat", "c" -> handleChat(player, args);
            case "bank" -> handleBank(player, args);
            case "list" -> handleList(player);
            default -> showHelp(player);
        }

        return true;
    }

    private void showHelp(Player player) {
        TextUtil.send(
                player, plugin.getClaimConfig().getMessage("prefix") + "<gold>Nation Commands:");
        TextUtil.send(player, "<gray>/nation create <name> <tag> <gray>- Create a nation");
        TextUtil.send(player, "<gray>/nation info [nation] <gray>- View nation info");
        TextUtil.send(
                player, "<gray>/nation invite <player> <gray>- Invite a claim to your nation");
        TextUtil.send(player, "<gray>/nation accept <gray>- Accept a nation invite");
        TextUtil.send(player, "<gray>/nation kick <player> <gray>- Kick a claim from nation");
        TextUtil.send(player, "<gray>/nation leave <gray>- Leave your nation");
        TextUtil.send(player, "<gray>/nation disband <gray>- Disband your nation (leader only)");
        TextUtil.send(player, "<gray>/nation promote/demote <player> <gray>- Change member rank");
        TextUtil.send(
                player,
                "<gray>/nation relation <nation> <ally|neutral|enemy> <gray>- Set relation");
        TextUtil.send(player, "<gray>/nation chat <message> <gray>- Send message to nation");
        TextUtil.send(
                player, "<gray>/nation bank deposit/withdraw <amount> <gray>- Manage nation bank");
        TextUtil.send(player, "<gray>/nation list <gray>- List all nations");
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 3) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<red>✗ Usage: /nation create <name> <tag>");
            return;
        }

        String name = args[1];
        String tag = args[2];

        // Get player's primary claim
        List<Claim> claims = plugin.getClaimManager().getPlayerClaims(player.getUniqueId());
        if (claims.isEmpty()) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<red>✗ You need to own a claim to create a nation");
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<gray>→ Create a claim first with /claim");
            return;
        }

        Claim primaryClaim = claims.get(0);

        plugin.getNationManager()
                .createNation(
                        player,
                        primaryClaim,
                        name,
                        tag,
                        result -> {
                            String prefix = plugin.getClaimConfig().getMessage("prefix");
                            switch (result) {
                                case SUCCESS -> {
                                    TextUtil.send(
                                            player,
                                            prefix
                                                    + "<green>✓ Nation '<yellow>"
                                                    + name
                                                    + "</yellow>' created successfully!");
                                    TextUtil.send(
                                            player,
                                            prefix
                                                    + "<gray>→ Invite members with /nation invite <player>");
                                }
                                case INVALID_NAME -> {
                                    TextUtil.send(
                                            player,
                                            prefix + "<red>✗ Nation name must be 3-32 characters");
                                    TextUtil.send(
                                            player,
                                            prefix + "<gray>→ Current length: " + name.length());
                                }
                                case INVALID_TAG -> {
                                    TextUtil.send(
                                            player,
                                            prefix + "<red>✗ Nation tag must be 2-5 characters");
                                    TextUtil.send(
                                            player,
                                            prefix + "<gray>→ Current length: " + tag.length());
                                }
                                case NAME_TAKEN -> {
                                    TextUtil.send(
                                            player,
                                            prefix
                                                    + "<red>✗ A nation with that name already exists");
                                    TextUtil.send(player, prefix + "<gray>→ Try a different name");
                                }
                                case TAG_TAKEN -> {
                                    TextUtil.send(
                                            player,
                                            prefix
                                                    + "<red>✗ A nation with that tag already exists");
                                    TextUtil.send(player, prefix + "<gray>→ Try a different tag");
                                }
                                case ALREADY_IN_NATION -> {
                                    TextUtil.send(
                                            player,
                                            prefix + "<red>✗ Your claim is already in a nation");
                                    TextUtil.send(
                                            player,
                                            prefix + "<gray>→ Leave your current nation first");
                                }
                                case INSUFFICIENT_FUNDS -> {
                                    double cost = plugin.getClaimConfig().getNationCreationCost();
                                    TextUtil.send(player, prefix + "<red>✗ Insufficient funds");
                                    TextUtil.send(
                                            player,
                                            prefix
                                                    + "<gray>→ Nation creation costs $"
                                                    + String.format("%.2f", cost));
                                }
                                case ECONOMY_ERROR -> {
                                    TextUtil.send(player, prefix + "<red>✗ Economy error occurred");
                                    TextUtil.send(
                                            player, prefix + "<gray>→ Contact an administrator");
                                }
                                default ->
                                        TextUtil.send(
                                                player, prefix + "<red>✗ Could not create nation");
                            }
                        });
    }

    private void handleInfo(Player player, String[] args) {
        Nation nation;
        if (args.length >= 2) {
            nation = plugin.getNationManager().getNationByName(args[1]);
            if (nation == null) {
                nation = plugin.getNationManager().getNationByTag(args[1]);
            }
        } else {
            List<Claim> claims = plugin.getClaimManager().getPlayerClaims(player.getUniqueId());
            if (claims.isEmpty()) {
                TextUtil.send(
                        player,
                        plugin.getClaimConfig().getMessage("prefix")
                                + "<red>✗ Specify a nation name or create a claim first");
                TextUtil.send(
                        player,
                        plugin.getClaimConfig().getMessage("prefix")
                                + "<gray>→ Usage: /nation info <name>");
                return;
            }
            nation = plugin.getNationManager().getNationForClaim(claims.get(0).getId());
        }

        if (nation == null) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix") + "<red>✗ Nation not found");
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<gray>→ Use /nation list to see all nations");
            return;
        }

        TextUtil.send(player, "");
        TextUtil.send(
                player,
                "<gold>=== " + nation.getColoredTag() + " <gold>" + nation.getName() + " ===");
        TextUtil.send(player, "<gray>Tag: <white>" + nation.getTag());
        TextUtil.send(player, "<gray>Members: <white>" + nation.getMemberCount());
        TextUtil.send(player, "<gray>Total Chunks: <white>" + nation.getTotalChunks());
        TextUtil.send(player, "<gray>Level: <white>" + nation.getLevel());
        if (nation.getDescription() != null) {
            TextUtil.send(player, "<gray>Description: <white>" + nation.getDescription());
        }
        TextUtil.send(player, "");
    }

    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<red>✗ Usage: /nation invite <player>");
            return;
        }

        List<Claim> claims = plugin.getClaimManager().getPlayerClaims(player.getUniqueId());
        if (claims.isEmpty()) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix") + "<red>✗ You don't have a claim");
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<gray>→ Create a claim first with /claim");
            return;
        }

        Nation nation = plugin.getNationManager().getNationForClaim(claims.get(0).getId());
        if (nation == null) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix") + "<red>✗ You're not in a nation");
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<gray>→ Create one with /nation create or join an existing one");
            return;
        }

        NationMember member = plugin.getNationManager().getMember(claims.get(0).getId());
        if (member == null || !member.canInvite()) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<red>✗ You don't have permission to invite");
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<gray>→ Only officers and leaders can invite members");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<red>✗ Player not found or offline");
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<gray>→ The player must be online to receive invites");
            return;
        }

        // Check if already in a nation
        List<Claim> targetClaims = plugin.getClaimManager().getPlayerClaims(target.getUniqueId());
        if (!targetClaims.isEmpty()) {
            Nation targetNation =
                    plugin.getNationManager().getNationForClaim(targetClaims.get(0).getId());
            if (targetNation != null) {
                TextUtil.send(
                        player,
                        plugin.getClaimConfig().getMessage("prefix")
                                + "<red>✗ "
                                + target.getName()
                                + " is already in a nation");
                TextUtil.send(
                        player,
                        plugin.getClaimConfig().getMessage("prefix")
                                + "<gray>→ They must leave their current nation first");
                return;
            }
        }

        // Create persistent invite
        int expiryHours = plugin.getClaimConfig().getNationInviteExpiryHours();
        java.time.Instant expiresAt =
                expiryHours > 0 ? java.time.Instant.now().plusSeconds(expiryHours * 3600) : null;

        plugin.getNationRepository()
                .createInvite(
                        nation.getId(), target.getUniqueId(), player.getUniqueId(), expiresAt);

        // Send success messages
        TextUtil.send(
                player,
                plugin.getClaimConfig().getMessage("prefix")
                        + "<green>✓ Invited "
                        + target.getName()
                        + " to "
                        + nation.getName());

        if (expiryHours > 0) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<gray>→ Invite expires in "
                            + expiryHours
                            + " hours");
        }

        // Notify target if online
        target.sendMessage(
                plugin.getClaimConfig().getMessage("prefix")
                        + "<yellow>⚠ You've been invited to nation: <aqua>"
                        + nation.getName());
        target.sendMessage(
                plugin.getClaimConfig().getMessage("prefix")
                        + "<gray>→ Use /nation accept "
                        + nation.getName()
                        + " to join");

        if (expiryHours > 0) {
            target.sendMessage(
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<gray>→ Invite expires in "
                            + expiryHours
                            + " hours");
        }
    }

    private void handleAccept(Player player, String[] args) {
        List<Claim> claims = plugin.getClaimManager().getPlayerClaims(player.getUniqueId());
        if (claims.isEmpty()) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix") + "<red>✗ You don't have a claim");
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<gray>→ Create a claim first with /claim");
            return;
        }

        // If no nation name provided, show pending invites
        if (args.length == 1) {
            plugin.getServer()
                    .getScheduler()
                    .runTaskAsynchronously(
                            plugin,
                            () -> {
                                List<NationInvite> invites =
                                        plugin.getNationRepository()
                                                .getPendingInvitesForPlayer(player.getUniqueId());

                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    if (invites.isEmpty()) {
                                                        TextUtil.send(
                                                                player,
                                                                plugin.getClaimConfig()
                                                                                .getMessage(
                                                                                        "prefix")
                                                                        + "<yellow>⚠ No pending nation invites");
                                                        TextUtil.send(
                                                                player,
                                                                plugin.getClaimConfig()
                                                                                .getMessage(
                                                                                        "prefix")
                                                                        + "<gray>→ Invites expire after "
                                                                        + plugin.getClaimConfig()
                                                                                .getNationInviteExpiryHours()
                                                                        + " hours");
                                                        return;
                                                    }

                                                    TextUtil.send(
                                                            player,
                                                            plugin.getClaimConfig()
                                                                            .getMessage("prefix")
                                                                    + "<aqua>Pending Nation Invites:");

                                                    for (NationInvite invite : invites) {
                                                        Nation nation =
                                                                plugin.getNationManager()
                                                                        .getNation(
                                                                                invite
                                                                                        .getNationId());
                                                        if (nation == null) continue;

                                                        long minutesAgo =
                                                                invite.getMinutesSinceInvited();
                                                        long hoursUntilExpiry =
                                                                invite.getHoursUntilExpiry();

                                                        String timeInfo =
                                                                minutesAgo < 60
                                                                        ? minutesAgo
                                                                                + " minutes ago"
                                                                        : (minutesAgo / 60)
                                                                                + " hours ago";

                                                        String expiryInfo =
                                                                hoursUntilExpiry >= 0
                                                                        ? ", expires in "
                                                                                + hoursUntilExpiry
                                                                                + "h"
                                                                        : "";

                                                        TextUtil.send(
                                                                player,
                                                                plugin.getClaimConfig()
                                                                                .getMessage(
                                                                                        "prefix")
                                                                        + "<gray>• <white>"
                                                                        + nation.getName()
                                                                        + " <gray>(invited "
                                                                        + timeInfo
                                                                        + expiryInfo
                                                                        + ")");
                                                    }

                                                    TextUtil.send(player, "");
                                                    TextUtil.send(
                                                            player,
                                                            plugin.getClaimConfig()
                                                                            .getMessage("prefix")
                                                                    + "<aqua>💡 Use /nation accept <name> to join");
                                                });
                            });
            return;
        }

        // If nation name provided, accept specific invite
        String nationName = args[1];
        Nation nation = plugin.getNationManager().getNationByName(nationName);

        if (nation == null) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<red>✗ Nation not found: "
                            + nationName);
            return;
        }

        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            List<NationInvite> invites =
                                    plugin.getNationRepository()
                                            .getPendingInvitesForPlayer(player.getUniqueId());
                            NationInvite validInvite = null;

                            for (NationInvite invite : invites) {
                                if (invite.getNationId() == nation.getId() && !invite.isExpired()) {
                                    validInvite = invite;
                                    break;
                                }
                            }

                            final NationInvite finalInvite = validInvite;

                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                if (finalInvite == null) {
                                                    TextUtil.send(
                                                            player,
                                                            plugin.getClaimConfig()
                                                                            .getMessage("prefix")
                                                                    + "<red>✗ No valid invite from "
                                                                    + nationName);
                                                    TextUtil.send(
                                                            player,
                                                            plugin.getClaimConfig()
                                                                            .getMessage("prefix")
                                                                    + "<gray>→ Invites expire after "
                                                                    + plugin.getClaimConfig()
                                                                            .getNationInviteExpiryHours()
                                                                    + " hours");
                                                    return;
                                                }

                                                // Accept invite using existing manager method
                                                plugin.getNationManager()
                                                        .acceptInvite(
                                                                nation.getId(),
                                                                claims.get(0),
                                                                success -> {
                                                                    if (success) {
                                                                        // Delete the invite after
                                                                        // successful acceptance
                                                                        plugin.getNationRepository()
                                                                                .deleteInvite(
                                                                                        finalInvite
                                                                                                .getId());

                                                                        TextUtil.send(
                                                                                player,
                                                                                plugin.getClaimConfig()
                                                                                                .getMessage(
                                                                                                        "prefix")
                                                                                        + "<green>✓ Joined nation: "
                                                                                        + nation
                                                                                                .getName());
                                                                        TextUtil.send(
                                                                                player,
                                                                                plugin.getClaimConfig()
                                                                                                .getMessage(
                                                                                                        "prefix")
                                                                                        + "<gray>→ Use /nation chat to communicate with your nation");
                                                                    } else {
                                                                        TextUtil.send(
                                                                                player,
                                                                                plugin.getClaimConfig()
                                                                                                .getMessage(
                                                                                                        "prefix")
                                                                                        + "<red>✗ Could not join nation");
                                                                        TextUtil.send(
                                                                                player,
                                                                                plugin.getClaimConfig()
                                                                                                .getMessage(
                                                                                                        "prefix")
                                                                                        + "<gray>→ You may already be in another nation");
                                                                    }
                                                                });
                                            });
                        });
    }

    private void handleKick(Player player, String[] args) {
        if (args.length < 2) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<red>✗ Usage: /nation kick <player>");
            return;
        }

        List<Claim> claims = plugin.getClaimManager().getPlayerClaims(player.getUniqueId());
        if (claims.isEmpty()) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix") + "<red>✗ You don't have a claim");
            return;
        }

        Nation nation = plugin.getNationManager().getNationForClaim(claims.get(0).getId());
        if (nation == null) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix") + "<red>✗ You're not in a nation");
            return;
        }

        NationMember member = plugin.getNationManager().getMember(claims.get(0).getId());
        if (member == null || !member.canKick()) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<red>✗ You don't have permission to kick members");
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<gray>→ Only officers and leaders can kick members");
            return;
        }

        // Find target's claim
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<red>✗ Player not found or offline");
            return;
        }

        List<Claim> targetClaims = plugin.getClaimManager().getPlayerClaims(target.getUniqueId());
        if (targetClaims.isEmpty()) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<red>✗ That player has no claims");
            return;
        }

        plugin.getNationManager()
                .kickClaim(
                        nation,
                        targetClaims.get(0).getId(),
                        success -> {
                            if (success) {
                                TextUtil.send(
                                        player,
                                        plugin.getClaimConfig().getMessage("prefix")
                                                + "<green>✓ Kicked "
                                                + target.getName()
                                                + " from the nation");
                                target.sendMessage(
                                        plugin.getClaimConfig().getMessage("prefix")
                                                + "<red>✗ You have been kicked from "
                                                + nation.getName());
                            } else {
                                TextUtil.send(
                                        player,
                                        plugin.getClaimConfig().getMessage("prefix")
                                                + "<red>✗ Could not kick that member");
                                TextUtil.send(
                                        player,
                                        plugin.getClaimConfig().getMessage("prefix")
                                                + "<gray>→ They may not be in your nation");
                            }
                        });
    }

    private void handleLeave(Player player) {
        List<Claim> claims = plugin.getClaimManager().getPlayerClaims(player.getUniqueId());
        if (claims.isEmpty()) return;

        plugin.getNationManager()
                .leaveNation(
                        claims.get(0),
                        success -> {
                            if (success) {
                                TextUtil.send(
                                        player,
                                        plugin.getClaimConfig().getMessage("prefix")
                                                + "<green>You have left the nation.");
                            } else {
                                TextUtil.send(
                                        player,
                                        plugin.getClaimConfig().getMessage("prefix")
                                                + "<red>Could not leave nation. Leaders must disband or transfer leadership.");
                            }
                        });
    }

    private void handleDisband(Player player) {
        List<Claim> claims = plugin.getClaimManager().getPlayerClaims(player.getUniqueId());
        if (claims.isEmpty()) return;

        Nation nation = plugin.getNationManager().getNationForClaim(claims.get(0).getId());
        if (nation == null) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix") + "<red>You're not in a nation.");
            return;
        }

        if (!nation.isLeader(player.getUniqueId())) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<red>Only the nation leader can disband.");
            return;
        }

        String nationName = nation.getName();
        plugin.getNationManager()
                .disbandNation(
                        nation,
                        success -> {
                            if (success) {
                                TextUtil.send(
                                        player,
                                        plugin.getClaimConfig().getMessage("prefix")
                                                + "<green>Nation '"
                                                + nationName
                                                + "' has been disbanded.");
                            } else {
                                TextUtil.send(
                                        player,
                                        plugin.getClaimConfig().getMessage("prefix")
                                                + "<red>Could not disband nation.");
                            }
                        });
    }

    private void handlePromote(Player player, String[] args) {
        handleRankChange(player, args, true);
    }

    private void handleDemote(Player player, String[] args) {
        handleRankChange(player, args, false);
    }

    private void handleRankChange(Player player, String[] args, boolean promote) {
        if (args.length < 2) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<red>Usage: /nation "
                            + (promote ? "promote" : "demote")
                            + " <player>");
            return;
        }

        List<Claim> claims = plugin.getClaimManager().getPlayerClaims(player.getUniqueId());
        if (claims.isEmpty()) return;

        Nation nation = plugin.getNationManager().getNationForClaim(claims.get(0).getId());
        if (nation == null || !nation.isLeader(player.getUniqueId())) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<red>Only the nation leader can change ranks.");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix") + "<red>Player not found.");
            return;
        }

        List<Claim> targetClaims = plugin.getClaimManager().getPlayerClaims(target.getUniqueId());
        if (targetClaims.isEmpty()) return;

        var callback =
                (java.util.function.Consumer<Boolean>)
                        success -> {
                            if (success) {
                                TextUtil.send(
                                        player,
                                        plugin.getClaimConfig().getMessage("prefix")
                                                + "<green>"
                                                + (promote ? "Promoted" : "Demoted")
                                                + " "
                                                + target.getName()
                                                + "!");
                            } else {
                                TextUtil.send(
                                        player,
                                        plugin.getClaimConfig().getMessage("prefix")
                                                + "<red>Could not change rank.");
                            }
                        };

        if (promote) {
            plugin.getNationManager().promoteMember(nation, targetClaims.get(0).getId(), callback);
        } else {
            plugin.getNationManager().demoteMember(nation, targetClaims.get(0).getId(), callback);
        }
    }

    private void handleRelation(Player player, String[] args) {
        if (args.length < 3) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<red>Usage: /nation relation <nation> <ally|neutral|enemy>");
            return;
        }

        List<Claim> claims = plugin.getClaimManager().getPlayerClaims(player.getUniqueId());
        if (claims.isEmpty()) return;

        Nation myNation = plugin.getNationManager().getNationForClaim(claims.get(0).getId());
        if (myNation == null || !myNation.isLeader(player.getUniqueId())) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<red>Only nation leaders can set relations.");
            return;
        }

        Nation targetNation = plugin.getNationManager().getNationByName(args[1]);
        if (targetNation == null) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix") + "<red>Nation not found.");
            return;
        }

        if (targetNation.getId() == myNation.getId()) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<red>You can't set a relation with yourself.");
            return;
        }

        NationRelation.RelationType type =
                switch (args[2].toLowerCase()) {
                    case "ally" -> NationRelation.RelationType.ALLY;
                    case "enemy" -> NationRelation.RelationType.ENEMY;
                    default -> NationRelation.RelationType.NEUTRAL;
                };

        plugin.getNationManager()
                .setRelation(
                        myNation,
                        targetNation,
                        type,
                        success -> {
                            TextUtil.send(
                                    player,
                                    plugin.getClaimConfig().getMessage("prefix")
                                            + "<green>Relation with "
                                            + targetNation.getName()
                                            + " set to "
                                            + type.getColored());
                        });
    }

    private void handleChat(Player player, String[] args) {
        if (args.length < 2) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<red>Usage: /nation chat <message>");
            return;
        }

        List<Claim> claims = plugin.getClaimManager().getPlayerClaims(player.getUniqueId());
        if (claims.isEmpty()) return;

        Nation nation = plugin.getNationManager().getNationForClaim(claims.get(0).getId());
        if (nation == null) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix") + "<red>You're not in a nation.");
            return;
        }

        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        plugin.getNationManager()
                .broadcastToNation(
                        nation, "<gray>" + player.getName() + ": </gray><white>" + message);
    }

    private void handleBank(Player player, String[] args) {
        if (args.length < 2) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<red>✗ Usage: /nation bank <deposit|withdraw|balance> [amount]");
            return;
        }

        List<Claim> claims = plugin.getClaimManager().getPlayerClaims(player.getUniqueId());
        if (claims.isEmpty()) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix") + "<red>✗ You don't have a claim");
            return;
        }

        Nation nation = plugin.getNationManager().getNationForClaim(claims.get(0).getId());
        if (nation == null) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix") + "<red>✗ You're not in a nation");
            return;
        }

        String action = args[1].toLowerCase();
        if (action.equals("balance")) {
            double balance = plugin.getNationManager().getNationBalance(nation.getId());
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<gray>Nation balance: <gold>$"
                            + String.format("%.2f", balance));
            return;
        }

        if (args.length < 3) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<red>✗ Usage: /nation bank "
                            + action
                            + " <amount>");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            TextUtil.send(
                    player, plugin.getClaimConfig().getMessage("prefix") + "<red>✗ Invalid amount");
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<gray>→ Amount must be a positive number");
            return;
        }

        if (action.equals("deposit")) {
            plugin.getNationManager()
                    .depositToNationBank(
                            player,
                            nation,
                            amount,
                            success -> {
                                if (success) {
                                    TextUtil.send(
                                            player,
                                            plugin.getClaimConfig().getMessage("prefix")
                                                    + "<green>✓ Deposited $"
                                                    + String.format("%.2f", amount)
                                                    + " to nation bank");
                                    double newBalance =
                                            plugin.getNationManager()
                                                    .getNationBalance(nation.getId());
                                    TextUtil.send(
                                            player,
                                            plugin.getClaimConfig().getMessage("prefix")
                                                    + "<gray>→ New balance: <gold>$"
                                                    + String.format("%.2f", newBalance));
                                } else {
                                    TextUtil.send(
                                            player,
                                            plugin.getClaimConfig().getMessage("prefix")
                                                    + "<red>✗ Deposit failed");
                                    TextUtil.send(
                                            player,
                                            plugin.getClaimConfig().getMessage("prefix")
                                                    + "<gray>→ Insufficient funds (you have $"
                                                    + String.format(
                                                            "%.2f",
                                                            plugin.getEconomy().getBalance(player))
                                                    + ")");
                                }
                            });
        } else if (action.equals("withdraw")) {
            plugin.getNationManager()
                    .withdrawFromNationBank(
                            player,
                            nation,
                            amount,
                            success -> {
                                if (success) {
                                    TextUtil.send(
                                            player,
                                            plugin.getClaimConfig().getMessage("prefix")
                                                    + "<green>✓ Withdrew $"
                                                    + String.format("%.2f", amount)
                                                    + " from nation bank");
                                    double newBalance =
                                            plugin.getNationManager()
                                                    .getNationBalance(nation.getId());
                                    TextUtil.send(
                                            player,
                                            plugin.getClaimConfig().getMessage("prefix")
                                                    + "<gray>→ New balance: <gold>$"
                                                    + String.format("%.2f", newBalance));
                                } else {
                                    TextUtil.send(
                                            player,
                                            plugin.getClaimConfig().getMessage("prefix")
                                                    + "<red>✗ Withdrawal failed");
                                    TextUtil.send(
                                            player,
                                            plugin.getClaimConfig().getMessage("prefix")
                                                    + "<gray>→ You must be the nation leader to withdraw funds");
                                }
                            });
        } else {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<red>✗ Unknown action: "
                            + action);
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<gray>→ Use deposit, withdraw, or balance");
        }
    }

    private void handleList(Player player) {
        List<Nation> nations = plugin.getNationManager().getAllNations();
        if (nations.isEmpty()) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix") + "<gray>No nations exist yet");
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<aqua>💡 Create one with /nation create <name> <tag>");
            return;
        }

        int maxMembers = plugin.getClaimConfig().getNationMaxMembers();

        TextUtil.send(player, plugin.getClaimConfig().getMessage("prefix") + "<gold>Nations:");

        int index = 1;
        for (Nation nation : nations) {
            if (nation == null) continue;

            int memberCount = nation.getMemberCount();

            // Capacity display with color coding
            String capacityDisplay;
            if (memberCount >= maxMembers) {
                capacityDisplay = "<red>" + memberCount + "/" + maxMembers + " ⛔ FULL";
            } else if (memberCount >= maxMembers * 0.9) { // 90% full
                capacityDisplay = "<yellow>" + memberCount + "/" + maxMembers + " ⚠ Almost full!";
            } else {
                capacityDisplay = "<green>" + memberCount + "/" + maxMembers;
            }

            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<gray>"
                            + index++
                            + ". "
                            + nation.getColoredTag()
                            + " <white>"
                            + nation.getName()
                            + " <gray>- "
                            + capacityDisplay
                            + " <gray>members, <aqua>"
                            + nation.getTotalChunks()
                            + " <gray>chunks");
        }
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return List.of();

        if (args.length == 1) {
            return filterCompletions(
                    List.of(
                            "create",
                            "info",
                            "invite",
                            "accept",
                            "kick",
                            "leave",
                            "disband",
                            "promote",
                            "demote",
                            "relation",
                            "chat",
                            "bank",
                            "list"),
                    args[0]);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("info") || sub.equals("relation")) {
                return filterCompletions(
                        plugin.getNationManager().getAllNations().stream()
                                .map(Nation::getName)
                                .collect(Collectors.toList()),
                        args[1]);
            }
            if (sub.equals("invite")
                    || sub.equals("kick")
                    || sub.equals("promote")
                    || sub.equals("demote")) {
                return List.of(); // Default player completion
            }
            if (sub.equals("bank")) {
                return filterCompletions(List.of("deposit", "withdraw", "balance"), args[1]);
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("relation")) {
            return filterCompletions(List.of("ally", "neutral", "enemy"), args[2]);
        }

        return List.of();
    }

    private List<String> filterCompletions(List<String> options, String input) {
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(input.toLowerCase()))
                .collect(Collectors.toList());
    }
}
