package net.serverplugins.bounty.managers;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.bounty.ServerBounty;
import net.serverplugins.bounty.models.TrophyHead;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public class HeadManager {

    private final ServerBounty plugin;
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd, yyyy").withZone(ZoneId.systemDefault());

    public HeadManager(ServerBounty plugin) {
        this.plugin = plugin;
    }

    public ItemStack createTrophyHead(TrophyHead trophy) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        if (meta != null) {
            // Set the skull owner to the victim's profile
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(trophy.getVictimUuid()));

            // Set display name
            meta.displayName(TextUtil.parse("<gold><bold>" + trophy.getVictimName() + "'s Head"));

            // Build lore
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse(""));
            lore.add(
                    TextUtil.parse(
                            "<gray>Bounty: <gold>"
                                    + plugin.getEconomyProvider()
                                            .format(trophy.getBountyAmount())));
            lore.add(
                    TextUtil.parse(
                            "<gray>Claimed: <yellow>" + DATE_FORMAT.format(trophy.getKillTime())));
            lore.add(TextUtil.parse(""));
            lore.add(TextUtil.parse("<dark_purple><italic>Trophy Head"));

            meta.lore(lore);
            head.setItemMeta(meta);
        }

        return head;
    }

    public int claimAllHeads(Player player) {
        UUID playerUuid = player.getUniqueId();
        List<TrophyHead> unclaimedHeads = plugin.getRepository().getUnclaimedHeads(playerUuid);

        if (unclaimedHeads.isEmpty()) {
            return 0;
        }

        int claimedCount = 0;

        for (TrophyHead trophy : unclaimedHeads) {
            // Check if inventory has space
            if (player.getInventory().firstEmpty() == -1) {
                // Inventory full, stop claiming
                break;
            }

            // Create the trophy head item
            ItemStack headItem = createTrophyHead(trophy);

            // Try to add to inventory
            var leftover = player.getInventory().addItem(headItem);

            if (leftover.isEmpty()) {
                // Successfully added to inventory
                plugin.getRepository().markHeadClaimed(trophy.getId());
                claimedCount++;
            } else {
                // Couldn't fit in inventory (shouldn't happen due to firstEmpty check)
                break;
            }
        }

        return claimedCount;
    }

    public List<TrophyHead> getUnclaimedHeads(UUID playerUuid) {
        return plugin.getRepository().getUnclaimedHeads(playerUuid);
    }

    public int getUnclaimedCount(UUID playerUuid) {
        return getUnclaimedHeads(playerUuid).size();
    }

    public void cleanupExpiredHeads() {
        int deleted = plugin.getRepository().deleteExpiredHeads();
        if (deleted > 0) {
            plugin.getLogger().info("Cleaned up " + deleted + " expired trophy heads");
        }
    }
}
