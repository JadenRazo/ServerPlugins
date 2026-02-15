package net.serverplugins.deathbuyback.gui;

import java.util.ArrayList;
import java.util.List;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.deathbuyback.ServerDeathBuyback;
import net.serverplugins.deathbuyback.models.DeathInventory;
import net.serverplugins.deathbuyback.serialization.InventorySerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class InventoryPreviewGui extends Gui {

    private final ServerDeathBuyback plugin;
    private final DeathInventory death;
    private final Gui parentGui;

    public InventoryPreviewGui(
            ServerDeathBuyback plugin, Player player, DeathInventory death, Gui parentGui) {
        super(
                plugin,
                54,
                net.serverplugins.api.utils.TextUtil.parse(
                        "<dark_red>Preview Death #" + death.getId()));
        this.plugin = plugin;
        this.viewer = player;
        this.death = death;
        this.parentGui = parentGui;
    }

    @Override
    protected void initializeItems() {
        clearItems();

        // Fill border with glass
        ItemStack glass = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        fillBorder(new GuiItem(glass, false));

        // Header items
        setItem(0, createBackButton());
        setItem(4, createInfoItem());
        setItem(8, createCloseButton());

        // Deserialize and display inventory preview
        ItemStack[] inventory = InventorySerializer.deserialize(death.getInventoryData(), 36);
        ItemStack[] armor = InventorySerializer.deserialize(death.getArmorData(), 4);
        ItemStack offhand = InventorySerializer.deserializeSingle(death.getOffhandData());

        // Display main inventory (slots 10-16, 19-25, 28-34)
        // This shows the first 21 items
        int[] inventorySlots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
        };

        int itemIndex = 0;
        for (int slot : inventorySlots) {
            if (itemIndex < inventory.length
                    && inventory[itemIndex] != null
                    && !inventory[itemIndex].getType().isAir()) {
                setItem(slot, new GuiItem(inventory[itemIndex], false));
            }
            itemIndex++;
        }

        // Display armor (slots 37-40)
        // Helmet, Chestplate, Leggings, Boots
        if (armor[3] != null && !armor[3].getType().isAir()) {
            setItem(37, createArmorSlotItem(armor[3], "Helmet"));
        }
        if (armor[2] != null && !armor[2].getType().isAir()) {
            setItem(38, createArmorSlotItem(armor[2], "Chestplate"));
        }
        if (armor[1] != null && !armor[1].getType().isAir()) {
            setItem(39, createArmorSlotItem(armor[1], "Leggings"));
        }
        if (armor[0] != null && !armor[0].getType().isAir()) {
            setItem(40, createArmorSlotItem(armor[0], "Boots"));
        }

        // Display offhand (slot 41)
        if (offhand != null && !offhand.getType().isAir()) {
            setItem(41, createOffhandSlotItem(offhand));
        }

        // Action buttons (centered)
        setItem(49, createPriceDisplay());
        setItem(50, createPurchaseButton());
    }

    private GuiItem createBackButton() {
        ItemStack item = new ItemBuilder(Material.ARROW).name("<yellow>Back to Menu").build();

        return new GuiItem(
                item,
                player -> {
                    parentGui.open(player);
                });
    }

    private GuiItem createInfoItem() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<gray>Location: <white>" + death.getFormattedLocation());
        lore.add("<gray>Died: <white>" + death.getTimeAgo());
        lore.add("<gray>Cause: <white>" + truncateCause(death.getDeathCause()));
        lore.add("");
        lore.add("<gray>Items: <white>" + death.getItemCount());
        lore.add("<gray>XP Levels: <white>" + death.getXpLevels());
        lore.add("");
        lore.add("<gray>Expires in: <yellow>" + death.getTimeUntilExpiry());

        ItemStack item =
                new ItemBuilder(Material.SKELETON_SKULL)
                        .name("<red>Death Details")
                        .lore(lore.toArray(new String[0]))
                        .build();

        return new GuiItem(item, false);
    }

    private GuiItem createCloseButton() {
        ItemStack item = new ItemBuilder(Material.BARRIER).name("<red>Close").build();

        return GuiItem.closeButton(item);
    }

    private GuiItem createArmorSlotItem(ItemStack armor, String slotName) {
        // Just display the armor piece
        return new GuiItem(armor, false);
    }

    private GuiItem createOffhandSlotItem(ItemStack offhand) {
        return new GuiItem(offhand, false);
    }

    private GuiItem createPriceDisplay() {
        String price = plugin.getPricingManager().formatPrice(death.getBuybackPrice());
        double balance = plugin.getEconomyProvider().getBalance(viewer);
        String balanceFormatted = plugin.getPricingManager().formatPrice(balance);

        boolean canAfford = balance >= death.getBuybackPrice();

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<gray>Your Balance: <white>" + balanceFormatted);
        lore.add("");
        if (canAfford) {
            lore.add("<green>You can afford this!");
        } else {
            lore.add("<red>Insufficient funds!");
        }

        ItemStack item =
                new ItemBuilder(Material.GOLD_INGOT)
                        .name("<gold>Price: <yellow>" + price)
                        .lore(lore.toArray(new String[0]))
                        .build();

        return new GuiItem(item, false);
    }

    private GuiItem createPurchaseButton() {
        double balance = plugin.getEconomyProvider().getBalance(viewer);
        boolean canAfford = balance >= death.getBuybackPrice();

        Material material = canAfford ? Material.LIME_WOOL : Material.GRAY_WOOL;
        String color = canAfford ? "<green>" : "<gray>";

        List<String> lore = new ArrayList<>();
        lore.add("");
        if (canAfford) {
            lore.add("<gray>Click to purchase and");
            lore.add("<gray>restore your inventory!");
        } else {
            lore.add("<red>You don't have enough money");
            lore.add("<red>to buy back this inventory.");
        }

        ItemStack item =
                new ItemBuilder(material)
                        .name(color + "Purchase")
                        .lore(lore.toArray(new String[0]))
                        .glow(canAfford)
                        .build();

        if (!canAfford) {
            return new GuiItem(item, false);
        }

        return new GuiItem(
                item,
                player -> {
                    new ConfirmPurchaseGui(plugin, player, death, parentGui).open(player);
                });
    }

    private String truncateCause(String cause) {
        if (cause == null) return "Unknown";
        // Strip color codes and truncate
        String stripped = cause.replaceAll("(?i)§[0-9A-FK-OR]", "");
        if (stripped.length() > 40) {
            return stripped.substring(0, 37) + "...";
        }
        return stripped;
    }
}
