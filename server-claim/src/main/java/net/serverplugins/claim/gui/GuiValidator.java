package net.serverplugins.claim.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.Nation;
import org.bukkit.entity.Player;

/**
 * Utility class for validating GUI preconditions and safely handling null values. Prevents crashes
 * when opening GUIs with missing or deleted data.
 */
public class GuiValidator {

    /**
     * Validate that a claim exists before opening a GUI. If claim is null, sends error message to
     * player and returns false.
     *
     * @param plugin ServerClaim plugin instance
     * @param player Player trying to open the GUI
     * @param claim Claim to validate (may be null)
     * @param guiName Name of the GUI being opened (for error message)
     * @return true if claim exists, false if null
     */
    public static boolean validateClaim(
            ServerClaim plugin, Player player, Claim claim, String guiName) {
        if (claim == null) {
            player.sendMessage(Component.text("This claim no longer exists!", NamedTextColor.RED));
            plugin.getLogger()
                    .warning(
                            "GUI validation failed: "
                                    + guiName
                                    + " - Claim is null for player "
                                    + player.getName());
            return false;
        }
        return true;
    }

    /**
     * Validate that a claim exists and player owns it.
     *
     * @param plugin ServerClaim plugin instance
     * @param player Player trying to open the GUI
     * @param claim Claim to validate (may be null)
     * @param guiName Name of the GUI being opened
     * @return true if claim exists and player owns it
     */
    public static boolean validateClaimOwnership(
            ServerClaim plugin, Player player, Claim claim, String guiName) {
        if (!validateClaim(plugin, player, claim, guiName)) {
            return false;
        }

        if (!claim.isOwner(player.getUniqueId()) && !player.hasPermission("serverclaim.admin")) {
            player.sendMessage(Component.text("You don't own this claim!", NamedTextColor.RED));
            plugin.getLogger()
                    .warning(
                            "GUI validation failed: "
                                    + guiName
                                    + " - Player "
                                    + player.getName()
                                    + " doesn't own claim "
                                    + claim.getId());
            return false;
        }

        return true;
    }

    /**
     * Validate that a nation exists before opening a GUI.
     *
     * @param plugin ServerClaim plugin instance
     * @param player Player trying to open the GUI
     * @param nation Nation to validate (may be null)
     * @param guiName Name of the GUI being opened
     * @return true if nation exists
     */
    public static boolean validateNation(
            ServerClaim plugin, Player player, Nation nation, String guiName) {
        if (nation == null) {
            player.sendMessage(Component.text("This nation no longer exists!", NamedTextColor.RED));
            plugin.getLogger()
                    .warning(
                            "GUI validation failed: "
                                    + guiName
                                    + " - Nation is null for player "
                                    + player.getName());
            return false;
        }
        return true;
    }

    /**
     * Validate that a nation exists and player is the owner.
     *
     * @param plugin ServerClaim plugin instance
     * @param player Player trying to open the GUI
     * @param nation Nation to validate (may be null)
     * @param guiName Name of the GUI being opened
     * @return true if nation exists and player owns it
     */
    public static boolean validateNationOwnership(
            ServerClaim plugin, Player player, Nation nation, String guiName) {
        if (!validateNation(plugin, player, nation, guiName)) {
            return false;
        }

        if (!nation.isLeader(player.getUniqueId()) && !player.hasPermission("serverclaim.admin")) {
            player.sendMessage(Component.text("You don't own this nation!", NamedTextColor.RED));
            plugin.getLogger()
                    .warning(
                            "GUI validation failed: "
                                    + guiName
                                    + " - Player "
                                    + player.getName()
                                    + " doesn't lead nation "
                                    + nation.getId());
            return false;
        }

        return true;
    }

    /**
     * Validate that a player has at least one claim before opening a claims GUI.
     *
     * @param plugin ServerClaim plugin instance
     * @param player Player to check
     * @param guiName Name of the GUI being opened
     * @return true if player has claims
     */
    public static boolean validateHasClaims(ServerClaim plugin, Player player, String guiName) {
        int claimCount = plugin.getClaimManager().getClaimCount(player.getUniqueId());
        if (claimCount == 0) {
            player.sendMessage(
                    Component.text("You don't have any claims yet!", NamedTextColor.RED));
            player.sendMessage(
                    Component.text("Use /claim to claim land first.", NamedTextColor.GRAY));
            plugin.getLogger()
                    .info(
                            "GUI validation failed: "
                                    + guiName
                                    + " - Player "
                                    + player.getName()
                                    + " has no claims");
            return false;
        }
        return true;
    }

    /**
     * Safely get claim data with null check and fallback. Returns a safe default if claim manager
     * returns null.
     *
     * @param plugin ServerClaim plugin instance
     * @param claim Claim to get data for
     * @return Claim object or null if not found
     */
    public static Claim safeGetClaim(ServerClaim plugin, int claimId) {
        try {
            Claim claim = plugin.getClaimManager().getClaimById(claimId);
            if (claim == null) {
                plugin.getLogger().warning("Claim " + claimId + " not found in safe get");
            }
            return claim;
        } catch (Exception e) {
            plugin.getLogger().severe("Error getting claim " + claimId + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Validate claim bank exists before opening bank GUI.
     *
     * @param plugin ServerClaim plugin instance
     * @param player Player trying to open GUI
     * @param claim Claim to check bank for
     * @param guiName GUI name for error reporting
     * @return true if bank is accessible
     */
    public static boolean validateClaimBank(
            ServerClaim plugin, Player player, Claim claim, String guiName) {
        if (!validateClaim(plugin, player, claim, guiName)) {
            return false;
        }

        // Try to get bank
        try {
            plugin.getBankManager().getBank(claim.getId());
            return true;
        } catch (Exception e) {
            player.sendMessage(Component.text("Failed to access claim bank!", NamedTextColor.RED));
            plugin.getLogger()
                    .severe(
                            "Failed to access bank for claim "
                                    + claim.getId()
                                    + " in GUI "
                                    + guiName
                                    + ": "
                                    + e.getMessage());
            return false;
        }
    }
}
