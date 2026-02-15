package net.serverplugins.enchants.gui;

import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.enchants.ServerEnchants;
import net.serverplugins.enchants.models.PlayerProgression;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Fragment shop GUI where players can spend fragments on useful items. */
public class FragmentShopGui extends Gui {

    private final ServerEnchants plugin;

    public FragmentShopGui(ServerEnchants plugin, Player viewer) {
        super(plugin, viewer, "<gold>Fragment Shop</gold>", 27);
        this.plugin = plugin;
    }

    @Override
    protected void initializeItems() {
        // Fill border
        ItemStack borderPane =
                new ItemBuilder(Material.ORANGE_STAINED_GLASS_PANE).name(" ").build();
        fillBorder(new GuiItem(borderPane, false));

        // Fill empty
        ItemStack emptyPane = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        fillEmpty(new GuiItem(emptyPane, false));

        // Upgrade Scroll (slot 10)
        int upgradeCost = plugin.getEnchantsConfig().getShopPrice("upgrade_scroll");
        ItemStack upgradeScroll =
                new ItemBuilder(Material.PAPER)
                        .name("<light_purple>Upgrade Scroll")
                        .lore(
                                "<gray>Upgrades an existing enchantment",
                                "<gray>on an item by 1 level.",
                                "",
                                "<gold>Cost: <yellow>" + upgradeCost + " fragments",
                                "",
                                "<yellow>Click to purchase!")
                        .build();
        setItem(
                10,
                GuiItem.of(
                        upgradeScroll,
                        player -> {
                            purchaseItem(
                                    player, "upgrade_scroll", upgradeCost, createUpgradeScroll());
                        }));

        // Enchant Dust (slot 12)
        int dustCost = plugin.getEnchantsConfig().getShopPrice("enchant_dust");
        ItemStack enchantDust =
                new ItemBuilder(Material.GLOWSTONE_DUST)
                        .name("<yellow>Enchant Dust")
                        .lore(
                                "<gray>A mysterious crafting material.",
                                "<gray>Future uses coming soon!",
                                "",
                                "<gold>Cost: <yellow>" + dustCost + " fragments",
                                "",
                                "<yellow>Click to purchase!")
                        .glow()
                        .build();
        setItem(
                12,
                GuiItem.of(
                        enchantDust,
                        player -> {
                            purchaseItem(
                                    player,
                                    "enchant_dust",
                                    dustCost,
                                    new ItemStack(Material.GLOWSTONE_DUST, 1));
                        }));

        // Random Enchant Scroll (slot 14)
        int randomCost = plugin.getEnchantsConfig().getShopPrice("random_scroll");
        ItemStack randomScroll =
                new ItemBuilder(Material.BOOK)
                        .name("<aqua>Random Enchant Scroll")
                        .lore(
                                "<gray>Applies a random unlocked",
                                "<gray>enchantment to your held item.",
                                "",
                                "<gold>Cost: <yellow>" + randomCost + " fragments",
                                "",
                                "<yellow>Click to purchase!")
                        .glow()
                        .build();
        setItem(
                14,
                GuiItem.of(
                        randomScroll,
                        player -> {
                            purchaseItem(player, "random_scroll", randomCost, createRandomScroll());
                        }));

        // Back button (slot 22)
        ItemStack backArrow = new ItemBuilder(Material.ARROW).name("<red>Back").build();
        setItem(
                22,
                GuiItem.of(
                        backArrow,
                        player -> {
                            new EnchanterMainGui(plugin, player).open();
                        }));
    }

    private void purchaseItem(Player player, String itemName, int cost, ItemStack item) {
        PlayerProgression progression =
                plugin.getProgressionManager().getProgression(player.getUniqueId());

        if (!progression.removeFragments(cost)) {
            TextUtil.sendError(player, "You don't have enough fragments! Need: " + cost);
            return;
        }

        // Check inventory space
        if (player.getInventory().firstEmpty() == -1) {
            TextUtil.sendError(player, "Your inventory is full!");
            progression.addFragments(cost); // Refund
            return;
        }

        // Give item
        player.getInventory().addItem(item);
        plugin.getProgressionManager().saveProgression(player.getUniqueId());

        TextUtil.sendSuccess(player, "Purchased for " + cost + " fragments!");
        player.playSound(player.getLocation(), "block.note_block.bell", 1.0f, 1.0f);

        // Refresh GUI
        refresh();
    }

    private ItemStack createUpgradeScroll() {
        ItemStack scroll =
                new ItemBuilder(Material.PAPER)
                        .name("<light_purple>Upgrade Scroll")
                        .lore(
                                "<gray>Right-click an enchanted item",
                                "<gray>to upgrade one enchantment",
                                "<gray>by 1 level.",
                                "",
                                "<yellow>Single use")
                        .glow()
                        .build();

        var meta = scroll.getItemMeta();
        if (meta != null) {
            NamespacedKey scrollTypeKey = new NamespacedKey(plugin, "scroll_type");
            meta.getPersistentDataContainer()
                    .set(scrollTypeKey, PersistentDataType.STRING, "upgrade");
            scroll.setItemMeta(meta);
        }

        return scroll;
    }

    private ItemStack createRandomScroll() {
        ItemStack scroll =
                new ItemBuilder(Material.BOOK)
                        .name("<aqua>Random Enchant Scroll")
                        .lore(
                                "<gray>Right-click while holding an item",
                                "<gray>to apply a random unlocked",
                                "<gray>enchantment.",
                                "",
                                "<yellow>Single use")
                        .glow()
                        .build();

        var meta = scroll.getItemMeta();
        if (meta != null) {
            NamespacedKey scrollTypeKey = new NamespacedKey(plugin, "scroll_type");
            meta.getPersistentDataContainer()
                    .set(scrollTypeKey, PersistentDataType.STRING, "random");
            scroll.setItemMeta(meta);
        }

        return scroll;
    }
}
