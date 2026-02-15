package net.serverplugins.deathbuyback.gui;

import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.deathbuyback.ServerDeathBuyback;
import net.serverplugins.deathbuyback.managers.DeathInventoryManager;
import net.serverplugins.deathbuyback.models.DeathInventory;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ConfirmPurchaseGui extends Gui {

    private final ServerDeathBuyback plugin;
    private final DeathInventory death;
    private final Gui parentGui;

    public ConfirmPurchaseGui(
            ServerDeathBuyback plugin, Player player, DeathInventory death, Gui parentGui) {
        super(plugin, 27, net.serverplugins.api.utils.TextUtil.parse("<dark_red>Confirm Purchase"));
        this.plugin = plugin;
        this.viewer = player;
        this.death = death;
        this.parentGui = parentGui;
    }

    @Override
    protected void initializeItems() {
        clearItems();

        // Fill with glass
        ItemStack glass = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        fillEmpty(new GuiItem(glass, false));

        // Info item in center
        setItem(13, createInfoItem());

        // Cancel button (left side)
        setItem(11, createCancelButton());

        // Confirm button (right side)
        setItem(15, createConfirmButton());
    }

    private GuiItem createInfoItem() {
        String price = plugin.getPricingManager().formatPrice(death.getBuybackPrice());

        ItemStack item =
                new ItemBuilder(Material.GOLD_INGOT)
                        .name("<gold>Confirm Purchase")
                        .lore(
                                "",
                                "<gray>You are about to buy back",
                                "<gray>your death inventory.",
                                "",
                                "<gray>Price: <yellow>" + price,
                                "<gray>Items: <white>" + death.getItemCount(),
                                "",
                                "<yellow>This action cannot be undone!")
                        .build();

        return new GuiItem(item, false);
    }

    private GuiItem createCancelButton() {
        ItemStack item =
                new ItemBuilder(Material.RED_CONCRETE)
                        .name("<red>Cancel")
                        .lore("", "<gray>Go back without purchasing")
                        .build();

        return new GuiItem(
                item,
                player -> {
                    parentGui.open(player);
                });
    }

    private GuiItem createConfirmButton() {
        String price = plugin.getPricingManager().formatPrice(death.getBuybackPrice());

        ItemStack item =
                new ItemBuilder(Material.LIME_CONCRETE)
                        .name("<green>Confirm Purchase")
                        .lore(
                                "",
                                "<gray>Pay <yellow>" + price,
                                "<gray>to restore your inventory",
                                "",
                                "<green>Click to confirm!")
                        .glow()
                        .build();

        return new GuiItem(
                item,
                player -> {
                    processPurchase(player);
                });
    }

    private void processPurchase(Player player) {
        // Close the GUI first
        player.closeInventory();

        // Process the purchase
        DeathInventoryManager.PurchaseResult result =
                plugin.getDeathInventoryManager().purchaseInventory(player, death.getId());

        switch (result) {
            case SUCCESS -> {
                String price = plugin.getPricingManager().formatPrice(death.getBuybackPrice());
                plugin.getDeathBuybackConfig()
                        .sendMessage(player, "purchase-success", "{price}", price);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }
            case NOT_FOUND -> {
                plugin.getDeathBuybackConfig().sendMessage(player, "death-not-found");
            }
            case ALREADY_PURCHASED -> {
                plugin.getDeathBuybackConfig().sendMessage(player, "already-purchased");
            }
            case EXPIRED -> {
                plugin.getDeathBuybackConfig().sendMessage(player, "inventory-expired");
            }
            case INSUFFICIENT_FUNDS -> {
                String price = plugin.getPricingManager().formatPrice(death.getBuybackPrice());
                plugin.getDeathBuybackConfig()
                        .sendMessage(player, "not-enough-money", "{price}", price);
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            case INVENTORY_FULL -> {
                plugin.getDeathBuybackConfig().sendMessage(player, "inventory-full");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            case TRANSACTION_FAILED -> {
                plugin.getDeathBuybackConfig().sendMessage(player, "transaction-failed");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            default -> {
                plugin.getDeathBuybackConfig().sendMessage(player, "purchase-failed");
            }
        }
    }
}
