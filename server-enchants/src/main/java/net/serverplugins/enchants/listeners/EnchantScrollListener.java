package net.serverplugins.enchants.listeners;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.enchants.ServerEnchants;
import net.serverplugins.enchants.enchantments.CustomEnchantment;
import net.serverplugins.enchants.managers.EnchantmentApplicationManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Handles right-click events for enchantment scrolls. Supports three scroll types: apply, upgrade,
 * and random.
 */
public class EnchantScrollListener implements Listener {

    private final ServerEnchants plugin;
    private final Random random = new Random();

    public EnchantScrollListener(ServerEnchants plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only handle right-click with item
        if (!event.getAction().isRightClick()) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack scrollItem = player.getInventory().getItemInMainHand();

        if (scrollItem.getType() == Material.AIR) return;

        var meta = scrollItem.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey scrollTypeKey = new NamespacedKey(plugin, "scroll_type");

        if (!pdc.has(scrollTypeKey, PersistentDataType.STRING)) return;

        String scrollType = pdc.get(scrollTypeKey, PersistentDataType.STRING);
        if (scrollType == null) return;

        event.setCancelled(true);

        switch (scrollType) {
            case "apply" -> handleApplyScroll(player, scrollItem, pdc);
            case "upgrade" -> handleUpgradeScroll(player, scrollItem);
            case "random" -> handleRandomScroll(player, scrollItem);
        }
    }

    /** Handles "apply" type scrolls - applies a specific enchantment to the off-hand item. */
    private void handleApplyScroll(
            Player player, ItemStack scrollItem, PersistentDataContainer pdc) {
        // Read scroll data
        NamespacedKey enchantIdKey = new NamespacedKey(plugin, "scroll_enchant_id");
        NamespacedKey levelKey = new NamespacedKey(plugin, "scroll_level");

        if (!pdc.has(enchantIdKey, PersistentDataType.STRING)
                || !pdc.has(levelKey, PersistentDataType.INTEGER)) {
            TextUtil.sendError(player, "This scroll is corrupted!");
            return;
        }

        String enchantId = pdc.get(enchantIdKey, PersistentDataType.STRING);
        int level = pdc.get(levelKey, PersistentDataType.INTEGER);

        CustomEnchantment enchant = plugin.getEnchantmentRegistry().getById(enchantId);
        if (enchant == null) {
            TextUtil.sendError(player, "Unknown enchantment: " + enchantId);
            return;
        }

        // Get target item (off-hand)
        ItemStack targetItem = player.getInventory().getItemInOffHand();
        if (targetItem.getType() == Material.AIR) {
            TextUtil.sendError(player, "Hold an item in your off-hand to enchant!");
            return;
        }

        // Check if enchantment can be applied
        if (!enchant.canApplyTo(targetItem)) {
            TextUtil.sendError(player, "This enchantment cannot be applied to that item!");
            return;
        }

        EnchantmentApplicationManager appManager = plugin.getEnchantmentApplicationManager();

        // Check if item already has this enchantment
        if (appManager.hasEnchantment(targetItem, enchant)) {
            TextUtil.sendError(
                    player,
                    "This item already has that enchantment! Use an upgrade scroll instead.");
            return;
        }

        // Apply enchantment
        boolean success = appManager.applyEnchantment(targetItem, enchant, level);
        if (!success) {
            TextUtil.sendError(player, "Failed to apply enchantment!");
            return;
        }

        // Remove scroll
        scrollItem.setAmount(scrollItem.getAmount() - 1);

        // Success feedback
        TextUtil.sendSuccess(
                player, "Applied " + enchant.getColoredDisplay(level) + " to your item!");
        player.playSound(player.getLocation(), "block.enchantment_table.use", 1.0f, 1.2f);
    }

    /** Handles "upgrade" type scrolls - upgrades an existing enchantment by 1 level. */
    private void handleUpgradeScroll(Player player, ItemStack scrollItem) {
        // Get target item (off-hand)
        ItemStack targetItem = player.getInventory().getItemInOffHand();
        if (targetItem.getType() == Material.AIR) {
            TextUtil.sendError(player, "Hold an enchanted item in your off-hand to upgrade!");
            return;
        }

        EnchantmentApplicationManager appManager = plugin.getEnchantmentApplicationManager();

        // Find first custom enchantment on the item
        CustomEnchantment enchantToUpgrade = null;
        int currentLevel = 0;

        for (CustomEnchantment enchant : plugin.getEnchantmentRegistry().getAll()) {
            int level = appManager.getEnchantmentLevel(targetItem, enchant);
            if (level > 0) {
                enchantToUpgrade = enchant;
                currentLevel = level;
                break;
            }
        }

        if (enchantToUpgrade == null) {
            TextUtil.sendError(player, "This item doesn't have any custom enchantments!");
            return;
        }

        if (currentLevel >= enchantToUpgrade.getMaxLevel()) {
            TextUtil.sendError(
                    player, enchantToUpgrade.getDisplayName() + " is already at max level!");
            return;
        }

        // Upgrade enchantment
        boolean success = appManager.upgradeEnchantment(targetItem, enchantToUpgrade);
        if (!success) {
            TextUtil.sendError(player, "Failed to upgrade enchantment!");
            return;
        }

        // Remove scroll
        scrollItem.setAmount(scrollItem.getAmount() - 1);

        // Success feedback
        TextUtil.sendSuccess(
                player,
                "Upgraded "
                        + enchantToUpgrade.getDisplayName()
                        + " to level "
                        + (currentLevel + 1)
                        + "!");
        player.playSound(player.getLocation(), "block.enchantment_table.use", 1.0f, 1.4f);
    }

    /** Handles "random" type scrolls - applies a random unlocked enchantment. */
    private void handleRandomScroll(Player player, ItemStack scrollItem) {
        // Get target item (off-hand)
        ItemStack targetItem = player.getInventory().getItemInOffHand();
        if (targetItem.getType() == Material.AIR) {
            TextUtil.sendError(player, "Hold an item in your off-hand to enchant!");
            return;
        }

        // Get unlocked enchantments
        Map<String, Integer> unlocks = plugin.getRepository().getUnlocks(player.getUniqueId());
        if (unlocks.isEmpty()) {
            TextUtil.sendError(player, "You haven't unlocked any enchantments yet!");
            return;
        }

        // Filter to enchantments that can apply to this item and aren't already on it
        List<CustomEnchantment> validEnchants = new ArrayList<>();
        EnchantmentApplicationManager appManager = plugin.getEnchantmentApplicationManager();

        for (Map.Entry<String, Integer> entry : unlocks.entrySet()) {
            CustomEnchantment enchant = plugin.getEnchantmentRegistry().getById(entry.getKey());
            if (enchant != null
                    && enchant.canApplyTo(targetItem)
                    && !appManager.hasEnchantment(targetItem, enchant)) {
                validEnchants.add(enchant);
            }
        }

        if (validEnchants.isEmpty()) {
            TextUtil.sendError(player, "No compatible enchantments available for this item!");
            return;
        }

        // Pick random enchantment
        CustomEnchantment chosenEnchant = validEnchants.get(random.nextInt(validEnchants.size()));
        int level = unlocks.get(chosenEnchant.getId());

        // Apply enchantment
        boolean success = appManager.applyEnchantment(targetItem, chosenEnchant, level);
        if (!success) {
            TextUtil.sendError(player, "Failed to apply enchantment!");
            return;
        }

        // Remove scroll
        scrollItem.setAmount(scrollItem.getAmount() - 1);

        // Success feedback
        TextUtil.sendSuccess(
                player,
                "Applied random enchantment: " + chosenEnchant.getColoredDisplay(level) + "!");
        player.playSound(player.getLocation(), "block.enchantment_table.use", 1.0f, 1.0f);
    }
}
