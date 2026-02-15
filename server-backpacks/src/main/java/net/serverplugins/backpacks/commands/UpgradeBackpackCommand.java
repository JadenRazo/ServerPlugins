package net.serverplugins.backpacks.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.messages.PluginMessenger;
import net.serverplugins.backpacks.BackpackTier;
import net.serverplugins.backpacks.BackpacksConfig;
import net.serverplugins.backpacks.ServerBackpacks;
import net.serverplugins.backpacks.managers.BackpackManager;
import net.serverplugins.backpacks.utils.ItemNameFormatter;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class UpgradeBackpackCommand implements CommandExecutor, TabCompleter {

    private final ServerBackpacks plugin;

    public UpgradeBackpackCommand(ServerBackpacks plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!player.hasPermission("serverbackpacks.upgrade")) {
            CommonMessages.NO_PERMISSION.send(player);
            return true;
        }

        PluginMessenger messenger = plugin.getBackpacksConfig().getMessenger();
        BackpackManager manager = plugin.getBackpackManager();
        ItemStack mainHand = player.getInventory().getItemInMainHand();

        // Check if holding a backpack
        if (!manager.isBackpack(mainHand)) {
            messenger.send(player, "upgrade-hold-backpack");
            return true;
        }

        // Get current tier
        BackpackTier currentTier = manager.getBackpackTier(mainHand);
        if (currentTier == null) {
            messenger.sendError(player, "This backpack cannot be upgraded!");
            return true;
        }

        // Check if can upgrade
        if (!currentTier.canUpgrade()) {
            messenger.send(player, "upgrade-max-tier");
            return true;
        }

        BackpackTier nextTier = currentTier.getNextTier();
        BackpacksConfig.BackpackType nextType =
                plugin.getBackpacksConfig().getBackpackType(nextTier.getId());

        if (nextType == null) {
            messenger.sendError(player, "Upgrade configuration not found!");
            return true;
        }

        // Check permission for next tier
        if (!player.hasPermission(nextType.permission())) {
            messenger.sendError(
                    player,
                    "You don't have permission to use Tier "
                            + nextTier.getRomanNumeral()
                            + " backpacks!");
            return true;
        }

        // Get upgrade cost from config
        Map<Material, Integer> upgradeCost = getUpgradeCost(currentTier, nextTier);

        // Check if player has required materials
        if (!hasRequiredMaterials(player, upgradeCost)) {
            messenger.send(player, "upgrade-no-materials");
            sendCostMessage(player, upgradeCost);
            return true;
        }

        // Remove materials from inventory
        removeRequiredMaterials(player, upgradeCost);

        // Perform the upgrade
        ItemStack upgraded = manager.upgradeBackpack(player, mainHand);

        if (upgraded == null) {
            // Refund materials on failure
            for (Map.Entry<Material, Integer> entry : upgradeCost.entrySet()) {
                player.getInventory().addItem(new ItemStack(entry.getKey(), entry.getValue()));
            }
            messenger.sendError(player, "Upgrade failed! Materials have been refunded.");
            return true;
        }

        // Set upgraded backpack in hand
        player.getInventory().setItemInMainHand(upgraded);

        // Success effects
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        messenger.sendRaw(
                player,
                ColorScheme.SUCCESS
                        + "Backpack upgraded to "
                        + ColorScheme.EMPHASIS
                        + "Tier "
                        + nextTier.getRomanNumeral()
                        + ColorScheme.SUCCESS
                        + "!");
        messenger.sendRaw(player, ColorScheme.INFO + "Your items have been preserved.");

        return true;
    }

    private Map<Material, Integer> getUpgradeCost(BackpackTier current, BackpackTier next) {
        return plugin.getBackpacksConfig().getUpgradeCost(current, next);
    }

    private boolean hasRequiredMaterials(Player player, Map<Material, Integer> cost) {
        for (Map.Entry<Material, Integer> entry : cost.entrySet()) {
            if (!player.getInventory().contains(entry.getKey(), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private void removeRequiredMaterials(Player player, Map<Material, Integer> cost) {
        for (Map.Entry<Material, Integer> entry : cost.entrySet()) {
            int remaining = entry.getValue();
            for (int i = 0; i < player.getInventory().getSize() && remaining > 0; i++) {
                ItemStack item = player.getInventory().getItem(i);
                if (item != null && item.getType() == entry.getKey()) {
                    int toRemove = Math.min(remaining, item.getAmount());
                    item.setAmount(item.getAmount() - toRemove);
                    remaining -= toRemove;
                }
            }
        }
    }

    private void sendCostMessage(Player player, Map<Material, Integer> cost) {
        PluginMessenger messenger = plugin.getBackpacksConfig().getMessenger();
        messenger.sendRaw(player, ColorScheme.WARNING + "Required materials:");
        for (Map.Entry<Material, Integer> entry : cost.entrySet()) {
            String itemName = ItemNameFormatter.format(entry.getKey());
            messenger.sendRaw(player, ColorScheme.INFO + "- " + entry.getValue() + "x " + itemName);
        }
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        return new ArrayList<>();
    }
}
