package net.serverplugins.claim.gui;

import java.util.ArrayList;
import java.util.List;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Confirmation GUI for deleting a claim. Displays warning and requires explicit confirmation. Uses
 * double-click confirmation for safety.
 */
public class ClaimDeleteConfirmGui extends Gui {

    private final ServerClaim plugin;
    private final Claim claim;
    private int confirmationClicks = 0;

    public ClaimDeleteConfirmGui(ServerClaim plugin, Player player, Claim claim) {
        super(plugin, player, "<red>Delete Claim?</red>", 27);
        this.plugin = plugin;

        // Validate claim exists and player owns it
        if (!GuiValidator.validateClaimOwnership(plugin, player, claim, "ClaimDeleteConfirmGui")) {
            this.claim = null;
            return;
        }

        this.claim = claim;
    }

    @Override
    protected void initializeItems() {
        // Safety check - early return if validation failed in constructor
        if (claim == null) {
            return;
        }

        // Check permission
        if (!claim.isOwner(viewer.getUniqueId()) && !viewer.hasPermission("serverclaim.admin")) {
            TextUtil.send(viewer, "<red>You don't have permission to delete this claim!");
            return;
        }

        int chunkCount = claim.getChunks() != null ? claim.getChunks().size() : 0;
        int memberCount = claim.getMembers() != null ? claim.getMembers().size() : 0;

        // Get bank balance if available
        double bankBalance = 0.0;
        try {
            if (plugin.getBankManager() != null) {
                bankBalance = plugin.getBankManager().getBank(claim.getId()).getBalance();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get bank balance for claim " + claim.getId());
        }

        // Get additional data counts
        int groupCount = 0;
        int warpCount = 0;
        try {
            if (claim.getCustomGroups() != null) {
                groupCount = claim.getCustomGroups().size();
            }
            // Warps would need to be retrieved from repository if tracked
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get claim data counts: " + e.getMessage());
        }

        // Warning info in center top (slot 4) - Enhanced warning display
        List<String> warningLore = new ArrayList<>();
        warningLore.add("");
        warningLore.add("<dark_red><bold>⚠ PERMANENT DELETION ⚠</bold>");
        warningLore.add("");
        warningLore.add(
                "<gray>Claim: <white>" + (claim.getName() != null ? claim.getName() : "Unnamed"));
        warningLore.add(
                "<gray>World: <white>" + (claim.getWorld() != null ? claim.getWorld() : "Unknown"));
        warningLore.add("");
        warningLore.add("<yellow>You will lose:");
        warningLore.add("<red>  • " + chunkCount + " claimed chunks");

        if (bankBalance > 0) {
            warningLore.add("<red>  • $" + String.format("%.2f", bankBalance) + " in bank");
        }

        if (memberCount > 0) {
            warningLore.add("<red>  • " + memberCount + " members");
        }

        if (groupCount > 0) {
            warningLore.add("<red>  • " + groupCount + " custom groups");
        }

        warningLore.add("<red>  • All permissions & settings");
        warningLore.add("");
        warningLore.add("<dark_red><bold>THIS CANNOT BE UNDONE!</bold>");
        warningLore.add("");
        warningLore.add("<yellow>Click CONFIRM twice to delete");

        ItemStack warningItem =
                new ItemBuilder(Material.BARRIER)
                        .name("<dark_red><bold>⚠ WARNING ⚠</bold>")
                        .lore(warningLore.toArray(new String[0]))
                        .glow(true)
                        .build();
        setItem(4, new GuiItem(warningItem));

        // Confirm button (slot 11) - Updates on first click
        String confirmButtonName =
                confirmationClicks == 0
                        ? "<yellow>Confirm Delete (1/2)"
                        : "<red><bold>CLICK AGAIN TO DELETE (2/2)</bold>";

        List<String> confirmLore = new ArrayList<>();
        confirmLore.add("");

        if (confirmationClicks == 0) {
            confirmLore.add("<gray>Click TWICE to confirm deletion");
            confirmLore.add(
                    "<gray>of '<white>"
                            + (claim.getName() != null ? claim.getName() : "Unnamed")
                            + "<gray>'");
            confirmLore.add("");
            confirmLore.add("<red>All " + chunkCount + " chunks will be freed.");
        } else {
            confirmLore.add("<red><bold>CLICK NOW TO DELETE PERMANENTLY</bold>");
            confirmLore.add("");
            confirmLore.add("<dark_red>This is your final warning!");
        }

        ItemStack confirmItem =
                new ItemBuilder(
                                confirmationClicks == 0
                                        ? Material.YELLOW_CONCRETE
                                        : Material.RED_CONCRETE)
                        .name(confirmButtonName)
                        .lore(confirmLore.toArray(new String[0]))
                        .glow(confirmationClicks > 0)
                        .build();
        setItem(11, new GuiItem(confirmItem, e -> handleConfirmClick()));

        // Cancel button (slot 15)
        ItemStack cancelItem =
                new ItemBuilder(Material.RED_CONCRETE)
                        .name("<red>Cancel")
                        .lore("", "<gray>Go back without deleting")
                        .build();
        setItem(
                15,
                new GuiItem(
                        cancelItem,
                        e -> {
                            viewer.closeInventory();
                            // Safely return to claim settings if possible
                            if (claim != null) {
                                new ClaimSettingsGui(plugin, viewer, claim).open();
                            } else {
                                new MyProfilesGui(plugin, viewer).open();
                            }
                        }));

        // Fill empty slots with glass
        ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 27; i++) {
            if (i != 4 && i != 11 && i != 15) {
                setItem(i, new GuiItem(filler));
            }
        }
    }

    /** Handle confirmation button click with double-click safety. */
    private void handleConfirmClick() {
        // Re-validate claim exists
        if (claim == null) {
            TextUtil.send(viewer, "<red>Claim data is missing!");
            viewer.closeInventory();
            new MyProfilesGui(plugin, viewer).open();
            return;
        }

        confirmationClicks++;

        if (confirmationClicks == 1) {
            // First click - update button and warn
            viewer.playSound(viewer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
            TextUtil.send(viewer, "<yellow>Click CONFIRM again to permanently delete this claim!");
            // Refresh GUI to show updated button
            initializeItems();
        } else if (confirmationClicks >= 2) {
            // Second click - actually delete
            deleteClaim();
        }
    }

    private void deleteClaim() {
        // Re-validate claim exists
        if (claim == null) {
            TextUtil.send(viewer, "<red>Claim data is missing!");
            viewer.closeInventory();
            new MyProfilesGui(plugin, viewer).open();
            return;
        }

        // Check if claim still exists
        Claim existingClaim = plugin.getClaimManager().getClaimById(claim.getId());
        if (existingClaim == null) {
            TextUtil.send(viewer, "<red>Claim no longer exists!");
            viewer.closeInventory();
            new MyProfilesGui(plugin, viewer).open();
            return;
        }

        // Check ownership
        if (!claim.isOwner(viewer.getUniqueId()) && !viewer.hasPermission("serverclaim.admin")) {
            TextUtil.send(viewer, "<red>You don't own this claim!");
            viewer.closeInventory();
            return;
        }

        int chunkCount = claim.getChunks() != null ? claim.getChunks().size() : 0;
        String claimName = claim.getName() != null ? claim.getName() : "Unnamed Claim";

        // Delete the claim
        plugin.getClaimManager().deleteClaim(claim);

        viewer.closeInventory();
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_ANVIL_DESTROY, 0.5f, 1f);
        TextUtil.send(viewer, "<green>Claim '<aqua>" + claimName + "<green>' has been deleted!");
        TextUtil.send(viewer, "<gray>" + chunkCount + " chunks have been freed.");

        // Return to claims list
        new MyProfilesGui(plugin, viewer).open();
    }
}
