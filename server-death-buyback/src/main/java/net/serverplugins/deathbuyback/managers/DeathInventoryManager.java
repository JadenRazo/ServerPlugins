package net.serverplugins.deathbuyback.managers;

import java.util.List;
import java.util.UUID;
import net.serverplugins.deathbuyback.ServerDeathBuyback;
import net.serverplugins.deathbuyback.models.DeathInventory;
import net.serverplugins.deathbuyback.models.PricingResult;
import net.serverplugins.deathbuyback.serialization.InventorySerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class DeathInventoryManager {

    private final ServerDeathBuyback plugin;

    public DeathInventoryManager(ServerDeathBuyback plugin) {
        this.plugin = plugin;
    }

    /** Get the maximum number of buyback slots for a player. */
    public int getMaxSlots(Player player) {
        return plugin.getDeathBuybackConfig().getMaxSlotsForPlayer(player);
    }

    /** Store a player's death inventory. */
    public DeathInventory storeDeathInventory(
            Player player,
            ItemStack[] contents,
            ItemStack[] armor,
            ItemStack offhand,
            int xpLevels,
            String deathCause) {
        int maxSlots = getMaxSlots(player);
        if (maxSlots <= 0) {
            return null; // Feature disabled for this player
        }

        UUID uuid = player.getUniqueId();

        // Check current slot usage
        int currentCount = plugin.getRepository().getActiveInventoryCount(uuid);

        // Handle overflow - delete oldest if at capacity
        if (currentCount >= maxSlots) {
            DeathInventory oldest = plugin.getRepository().getOldestActiveInventory(uuid);
            if (oldest != null) {
                plugin.getRepository().deleteInventory(oldest.getId());
                // Queue notification for next login
            }
        }

        // Serialize inventory data
        String inventoryData = InventorySerializer.serialize(contents);
        String armorData = InventorySerializer.serialize(armor);
        String offhandData = InventorySerializer.serializeSingle(offhand);

        // Calculate pricing
        PricingResult pricing =
                plugin.getPricingManager().calculateInventoryWorth(contents, armor, offhand);

        // Skip if inventory is empty
        if (pricing.isEmpty()) {
            return null;
        }

        // Create death inventory record
        long now = System.currentTimeMillis();
        long expiresAt = now + plugin.getDeathBuybackConfig().getExpirationMillis();

        DeathInventory death =
                new DeathInventory(
                        uuid,
                        player.getName(),
                        player.getLocation(),
                        truncateDeathCause(deathCause),
                        inventoryData,
                        armorData,
                        offhandData,
                        xpLevels,
                        pricing.baseWorth(),
                        pricing.buybackPrice(),
                        pricing.itemCount(),
                        now,
                        expiresAt);

        // Save to database
        plugin.getRepository().saveDeathInventory(death);

        // Publish to Discord via Redis
        plugin.publishDeathStored(
                player.getName(),
                player.getUniqueId().toString(),
                pricing.itemCount(),
                pricing.buybackPrice());

        return death;
    }

    /** Get all active (non-purchased, non-expired) death inventories for a player. */
    public List<DeathInventory> getActiveInventories(UUID playerUuid) {
        return plugin.getRepository().getActiveInventories(playerUuid);
    }

    /** Get a specific death inventory by ID. */
    public DeathInventory getDeathInventory(int id) {
        return plugin.getRepository().getById(id);
    }

    /** Attempt to purchase and restore a death inventory. */
    public PurchaseResult purchaseInventory(Player player, int deathId) {
        DeathInventory death = plugin.getRepository().getById(deathId);

        if (death == null) {
            return PurchaseResult.NOT_FOUND;
        }

        if (!death.getPlayerUuid().equals(player.getUniqueId())) {
            return PurchaseResult.NOT_OWNER;
        }

        if (death.isPurchased()) {
            return PurchaseResult.ALREADY_PURCHASED;
        }

        if (death.isExpired()) {
            return PurchaseResult.EXPIRED;
        }

        double price = death.getBuybackPrice();

        // Check if player has enough money
        if (!plugin.getEconomyProvider().has(player, price)) {
            return PurchaseResult.INSUFFICIENT_FUNDS;
        }

        // Deserialize inventory
        ItemStack[] inventory = InventorySerializer.deserialize(death.getInventoryData(), 36);
        ItemStack[] armor = InventorySerializer.deserialize(death.getArmorData(), 4);
        ItemStack offhand = InventorySerializer.deserializeSingle(death.getOffhandData());

        // Check if player has inventory space
        if (!hasInventorySpace(player, inventory, armor, offhand)) {
            return PurchaseResult.INVENTORY_FULL;
        }

        // Withdraw money
        if (!plugin.getEconomyProvider().withdraw(player, price)) {
            return PurchaseResult.TRANSACTION_FAILED;
        }

        try {
            // Restore items
            restoreItems(player, inventory, armor, offhand);

            // Restore XP levels
            if (death.getXpLevels() > 0) {
                player.giveExpLevels(death.getXpLevels());
            }

            // Mark as purchased
            plugin.getRepository().markAsPurchased(deathId);

            // Publish to Discord via Redis
            plugin.publishDeathPurchased(player.getName(), player.getUniqueId().toString(), price);
        } catch (Exception e) {
            // Refund the player since restoration failed
            plugin.getEconomyProvider().deposit(player, price);
            plugin.getLogger()
                    .severe(
                            "Failed to restore death inventory #"
                                    + deathId
                                    + " for "
                                    + player.getName()
                                    + " - refunded $"
                                    + price
                                    + ": "
                                    + e.getMessage());
            return PurchaseResult.TRANSACTION_FAILED;
        }

        return PurchaseResult.SUCCESS;
    }

    /** Check if player has enough inventory space. */
    private boolean hasInventorySpace(
            Player player, ItemStack[] inventory, ItemStack[] armor, ItemStack offhand) {
        PlayerInventory inv = player.getInventory();
        int emptySlots = 0;

        for (int i = 0; i < 36; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType().isAir()) {
                emptySlots++;
            }
        }

        // Count items that need slots
        int itemsNeedingSlots = 0;
        if (inventory != null) {
            itemsNeedingSlots += InventorySerializer.countStacks(inventory);
        }

        // Armor goes to armor slots, no need to count
        // Offhand goes to offhand slot, no need to count

        return emptySlots >= itemsNeedingSlots;
    }

    /** Restore items to player's inventory. */
    private void restoreItems(
            Player player, ItemStack[] inventory, ItemStack[] armor, ItemStack offhand) {
        PlayerInventory inv = player.getInventory();

        // Restore main inventory
        if (inventory != null) {
            for (ItemStack item : inventory) {
                if (item != null && !item.getType().isAir()) {
                    // Try to add to inventory, drop overflow
                    var leftover = inv.addItem(item);
                    for (ItemStack drop : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), drop);
                    }
                }
            }
        }

        // Restore armor (to armor slots if empty, otherwise to inventory)
        if (armor != null) {
            ItemStack[] currentArmor = inv.getArmorContents();
            for (int i = 0; i < armor.length && i < 4; i++) {
                ItemStack armorPiece = armor[i];
                if (armorPiece == null || armorPiece.getType().isAir()) continue;

                if (currentArmor[i] == null || currentArmor[i].getType().isAir()) {
                    // Armor slot is empty, equip directly
                    switch (i) {
                        case 0 -> inv.setBoots(armorPiece);
                        case 1 -> inv.setLeggings(armorPiece);
                        case 2 -> inv.setChestplate(armorPiece);
                        case 3 -> inv.setHelmet(armorPiece);
                    }
                } else {
                    // Armor slot occupied, add to inventory
                    var leftover = inv.addItem(armorPiece);
                    for (ItemStack drop : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), drop);
                    }
                }
            }
        }

        // Restore offhand
        if (offhand != null && !offhand.getType().isAir()) {
            ItemStack currentOffhand = inv.getItemInOffHand();
            if (currentOffhand.getType().isAir()) {
                inv.setItemInOffHand(offhand);
            } else {
                var leftover = inv.addItem(offhand);
                for (ItemStack drop : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
            }
        }
    }

    /** Truncate death cause to fit in database column. */
    private String truncateDeathCause(String cause) {
        if (cause == null) return "Unknown";
        if (cause.length() <= 128) return cause;
        return cause.substring(0, 125) + "...";
    }

    public enum PurchaseResult {
        SUCCESS,
        NOT_FOUND,
        NOT_OWNER,
        ALREADY_PURCHASED,
        EXPIRED,
        INSUFFICIENT_FUNDS,
        INVENTORY_FULL,
        TRANSACTION_FAILED
    }
}
