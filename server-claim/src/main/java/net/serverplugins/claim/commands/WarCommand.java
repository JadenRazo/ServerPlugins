package net.serverplugins.claim.commands;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.Nation;
import net.serverplugins.claim.models.War;
import net.serverplugins.claim.models.WarShield;
import net.serverplugins.claim.models.WarTribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class WarCommand implements CommandExecutor, TabCompleter {

    private final ServerClaim plugin;

    public WarCommand(ServerClaim plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!plugin.getWarManager().isWarsEnabled()) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<red>The war system is currently disabled.");
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "declare" -> handleDeclare(player, args);
            case "status" -> handleStatus(player);
            case "tribute" -> handleTribute(player, args);
            case "surrender" -> handleSurrender(player);
            case "accept" -> handleAccept(player);
            case "reject" -> handleReject(player);
            case "history" -> handleHistory(player, args);
            case "shield" -> handleShield(player);
            default -> showHelp(player);
        }

        return true;
    }

    private void showHelp(Player player) {
        TextUtil.send(player, plugin.getClaimConfig().getMessage("prefix") + "<gold>War Commands:");
        TextUtil.send(player, "<gray>/war declare <nation> [reason] <gray>- Declare war");
        TextUtil.send(player, "<gray>/war status <gray>- View active war status");
        TextUtil.send(player, "<gray>/war tribute <amount> <gray>- Offer tribute for peace");
        TextUtil.send(player, "<gray>/war surrender <gray>- Surrender the war");
        TextUtil.send(player, "<gray>/war accept <gray>- Accept a tribute offer");
        TextUtil.send(player, "<gray>/war reject <gray>- Reject a tribute offer");
        TextUtil.send(player, "<gray>/war history [nation] <gray>- View war history");
        TextUtil.send(player, "<gray>/war shield <gray>- Check war shield status");
    }

    private void handleDeclare(Player player, String[] args) {
        if (args.length < 2) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<red>Usage: /war declare <nation> [reason]");
            return;
        }

        List<Claim> claims = plugin.getClaimManager().getPlayerClaims(player.getUniqueId());
        if (claims.isEmpty()) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<red>You need a claim to participate in wars.");
            return;
        }

        Nation myNation = plugin.getNationManager().getNationForClaim(claims.get(0).getId());
        if (myNation == null) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<red>You must be in a nation to declare war.");
            return;
        }

        if (!myNation.isLeader(player.getUniqueId())) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<red>Only the nation leader can declare war.");
            return;
        }

        Nation targetNation = plugin.getNationManager().getNationByName(args[1]);
        if (targetNation == null) {
            targetNation = plugin.getNationManager().getNationByTag(args[1]);
        }

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
                            + "<red>You can't declare war on yourself.");
            return;
        }

        String reason =
                args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : null;
        Nation finalTarget = targetNation;

        plugin.getWarManager()
                .declareWar(
                        myNation,
                        targetNation,
                        reason,
                        result -> {
                            String message =
                                    switch (result) {
                                        case SUCCESS ->
                                                "<green>War declared on "
                                                        + finalTarget.getName()
                                                        + "!";
                                        case ALREADY_AT_WAR ->
                                                "<red>You're already at war with "
                                                        + finalTarget.getName()
                                                        + ".";
                                        case TARGET_SHIELDED ->
                                                "<red>"
                                                        + finalTarget.getName()
                                                        + " is protected by a war shield.";
                                        default -> "<red>Could not declare war.";
                                    };
                            TextUtil.send(
                                    player, plugin.getClaimConfig().getMessage("prefix") + message);
                        });
    }

    private void handleStatus(Player player) {
        List<Claim> claims = plugin.getClaimManager().getPlayerClaims(player.getUniqueId());
        if (claims.isEmpty()) return;

        Nation nation = plugin.getNationManager().getNationForClaim(claims.get(0).getId());
        if (nation == null) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix") + "<red>You're not in a nation.");
            return;
        }

        List<War> wars = plugin.getWarManager().getActiveWarsForNation(nation.getId());
        if (wars.isEmpty()) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<gray>Your nation is not currently at war.");
            return;
        }

        TextUtil.send(player, plugin.getClaimConfig().getMessage("prefix") + "<gold>Active Wars:");
        for (War war : wars) {
            boolean isAttacker = war.isAttacker(nation.getId());
            Integer opponentId = isAttacker ? war.getDefenderNationId() : war.getAttackerNationId();
            Nation opponent =
                    opponentId != null ? plugin.getNationManager().getNation(opponentId) : null;
            String opponentName = opponent != null ? opponent.getName() : "Unknown";

            String role = isAttacker ? "<red>Attacking</red>" : "<blue>Defending</blue>";
            String state =
                    switch (war.getWarState()) {
                        case DECLARED -> {
                            Duration until =
                                    Duration.between(
                                            Instant.now(),
                                            war.getDeclaredAt().plus(Duration.ofHours(24)));
                            yield "<yellow>Declared</yellow> (active in " + until.toHours() + "h)";
                        }
                        case ACTIVE -> "<red>ACTIVE</red>";
                        case CEASEFIRE -> "<yellow>Ceasefire</yellow>";
                        default -> war.getWarState().getDisplayName();
                    };

            TextUtil.send(player, "  <white>" + opponentName + " - " + role + " - " + state);

            // Show pending tributes
            List<WarTribute> tributes = plugin.getWarManager().getPendingTributes(war.getId());
            for (WarTribute tribute : tributes) {
                TextUtil.send(
                        player,
                        "    <yellow>Pending tribute: "
                                + tribute.getTributeType().getDisplayName()
                                + (tribute.getMoneyAmount() > 0
                                        ? " ($"
                                                + String.format("%.2f", tribute.getMoneyAmount())
                                                + ")"
                                        : ""));
            }
        }
    }

    private void handleTribute(Player player, String[] args) {
        if (args.length < 2) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<red>Usage: /war tribute <amount>");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            TextUtil.send(
                    player, plugin.getClaimConfig().getMessage("prefix") + "<red>Invalid amount.");
            return;
        }

        List<Claim> claims = plugin.getClaimManager().getPlayerClaims(player.getUniqueId());
        if (claims.isEmpty()) return;

        Nation nation = plugin.getNationManager().getNationForClaim(claims.get(0).getId());
        if (nation == null || !nation.isLeader(player.getUniqueId())) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<red>Only nation leaders can offer tribute.");
            return;
        }

        List<War> wars = plugin.getWarManager().getActiveWarsForNation(nation.getId());
        if (wars.isEmpty()) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<red>You're not currently at war.");
            return;
        }

        War war = wars.get(0);
        WarTribute.OfferingSide side =
                war.isAttacker(nation.getId())
                        ? WarTribute.OfferingSide.ATTACKER
                        : WarTribute.OfferingSide.DEFENDER;

        plugin.getWarManager()
                .offerTribute(
                        war,
                        side,
                        WarTribute.TributeType.PEACE_OFFER,
                        amount,
                        null,
                        success -> {
                            if (success) {
                                TextUtil.send(
                                        player,
                                        plugin.getClaimConfig().getMessage("prefix")
                                                + "<green>Peace offer sent with $"
                                                + String.format("%.2f", amount)
                                                + " tribute.");
                            } else {
                                TextUtil.send(
                                        player,
                                        plugin.getClaimConfig().getMessage("prefix")
                                                + "<red>Could not send tribute offer.");
                            }
                        });
    }

    private void handleSurrender(Player player) {
        List<Claim> claims = plugin.getClaimManager().getPlayerClaims(player.getUniqueId());
        if (claims.isEmpty()) return;

        Nation nation = plugin.getNationManager().getNationForClaim(claims.get(0).getId());
        if (nation == null || !nation.isLeader(player.getUniqueId())) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<red>Only nation leaders can surrender.");
            return;
        }

        List<War> wars = plugin.getWarManager().getActiveWarsForNation(nation.getId());
        if (wars.isEmpty()) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<red>You're not currently at war.");
            return;
        }

        War war = wars.get(0);
        WarTribute.OfferingSide side =
                war.isAttacker(nation.getId())
                        ? WarTribute.OfferingSide.ATTACKER
                        : WarTribute.OfferingSide.DEFENDER;

        plugin.getWarManager()
                .offerTribute(
                        war,
                        side,
                        WarTribute.TributeType.SURRENDER,
                        0,
                        "Unconditional surrender",
                        success -> {
                            if (success) {
                                TextUtil.send(
                                        player,
                                        plugin.getClaimConfig().getMessage("prefix")
                                                + "<yellow>Surrender offer sent. Waiting for opponent to accept.");
                            } else {
                                TextUtil.send(
                                        player,
                                        plugin.getClaimConfig().getMessage("prefix")
                                                + "<red>Could not send surrender offer.");
                            }
                        });
    }

    private void handleAccept(Player player) {
        List<Claim> claims = plugin.getClaimManager().getPlayerClaims(player.getUniqueId());
        if (claims.isEmpty()) return;

        Nation nation = plugin.getNationManager().getNationForClaim(claims.get(0).getId());
        if (nation == null || !nation.isLeader(player.getUniqueId())) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<red>Only nation leaders can accept tributes.");
            return;
        }

        List<War> wars = plugin.getWarManager().getActiveWarsForNation(nation.getId());
        for (War war : wars) {
            List<WarTribute> tributes = plugin.getWarManager().getPendingTributes(war.getId());
            for (WarTribute tribute : tributes) {
                // Accept first pending tribute from opponent
                boolean fromOpponent =
                        (war.isAttacker(nation.getId())
                                        && tribute.getOfferingSide()
                                                == WarTribute.OfferingSide.DEFENDER)
                                || (war.isDefender(nation.getId())
                                        && tribute.getOfferingSide()
                                                == WarTribute.OfferingSide.ATTACKER);

                if (fromOpponent) {
                    plugin.getWarManager()
                            .acceptTribute(
                                    tribute,
                                    success -> {
                                        if (success) {
                                            TextUtil.send(
                                                    player,
                                                    plugin.getClaimConfig().getMessage("prefix")
                                                            + "<green>Tribute accepted! The war has ended.");
                                        }
                                    });
                    return;
                }
            }
        }

        TextUtil.send(
                player,
                plugin.getClaimConfig().getMessage("prefix")
                        + "<red>No pending tribute offers to accept.");
    }

    private void handleReject(Player player) {
        List<Claim> claims = plugin.getClaimManager().getPlayerClaims(player.getUniqueId());
        if (claims.isEmpty()) return;

        Nation nation = plugin.getNationManager().getNationForClaim(claims.get(0).getId());
        if (nation == null || !nation.isLeader(player.getUniqueId())) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<red>Only nation leaders can reject tributes.");
            return;
        }

        List<War> wars = plugin.getWarManager().getActiveWarsForNation(nation.getId());
        for (War war : wars) {
            List<WarTribute> tributes = plugin.getWarManager().getPendingTributes(war.getId());
            for (WarTribute tribute : tributes) {
                boolean fromOpponent =
                        (war.isAttacker(nation.getId())
                                        && tribute.getOfferingSide()
                                                == WarTribute.OfferingSide.DEFENDER)
                                || (war.isDefender(nation.getId())
                                        && tribute.getOfferingSide()
                                                == WarTribute.OfferingSide.ATTACKER);

                if (fromOpponent) {
                    plugin.getWarManager()
                            .rejectTribute(
                                    tribute,
                                    success -> {
                                        if (success) {
                                            TextUtil.send(
                                                    player,
                                                    plugin.getClaimConfig().getMessage("prefix")
                                                            + "<yellow>Tribute rejected. The war continues.");
                                        }
                                    });
                    return;
                }
            }
        }

        TextUtil.send(
                player,
                plugin.getClaimConfig().getMessage("prefix")
                        + "<red>No pending tribute offers to reject.");
    }

    private void handleHistory(Player player, String[] args) {
        // Show recent wars for the player's nation or specified nation
        Nation nation;
        if (args.length >= 2) {
            nation = plugin.getNationManager().getNationByName(args[1]);
        } else {
            List<Claim> claims = plugin.getClaimManager().getPlayerClaims(player.getUniqueId());
            if (claims.isEmpty()) {
                TextUtil.send(
                        player,
                        plugin.getClaimConfig().getMessage("prefix")
                                + "<red>Specify a nation name.");
                return;
            }
            nation = plugin.getNationManager().getNationForClaim(claims.get(0).getId());
        }

        if (nation == null) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix") + "<red>Nation not found.");
            return;
        }

        TextUtil.send(
                player,
                plugin.getClaimConfig().getMessage("prefix")
                        + "<gold>War history for "
                        + nation.getName()
                        + ":");
        TextUtil.send(player, "<gray>(War history feature - wars are tracked in database)");
    }

    private void handleShield(Player player) {
        List<Claim> claims = plugin.getClaimManager().getPlayerClaims(player.getUniqueId());
        if (claims.isEmpty()) return;

        Nation nation = plugin.getNationManager().getNationForClaim(claims.get(0).getId());
        if (nation == null) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix") + "<red>You're not in a nation.");
            return;
        }

        WarShield shield = plugin.getWarManager().getActiveShield(nation.getId());
        if (shield == null || shield.isExpired()) {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<gray>Your nation has no active war shield.");
        } else {
            TextUtil.send(
                    player,
                    plugin.getClaimConfig().getMessage("prefix")
                            + "<green>War shield active! Time remaining: "
                            + shield.getFormattedTimeRemaining());
            if (shield.getReason() != null) {
                TextUtil.send(player, "<gray>Reason: " + shield.getReason());
            }
        }
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return List.of();

        if (args.length == 1) {
            return filterCompletions(
                    List.of(
                            "declare",
                            "status",
                            "tribute",
                            "surrender",
                            "accept",
                            "reject",
                            "history",
                            "shield"),
                    args[0]);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("declare") || sub.equals("history")) {
                return filterCompletions(
                        plugin.getNationManager().getAllNations().stream()
                                .map(Nation::getName)
                                .collect(Collectors.toList()),
                        args[1]);
            }
        }

        return List.of();
    }

    private List<String> filterCompletions(List<String> options, String input) {
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(input.toLowerCase()))
                .collect(Collectors.toList());
    }
}
