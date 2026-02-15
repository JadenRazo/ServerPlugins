package net.serverplugins.enchants.gui;

import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.enchants.ServerEnchants;
import net.serverplugins.enchants.enchantments.CustomEnchantment;
import net.serverplugins.enchants.models.PlayerProgression;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Detailed view of a specific enchantment with upgrade and apply options. */
public class EnchantDetailGui extends Gui {

    private final ServerEnchants plugin;
    private final CustomEnchantment enchantment;
    private final int currentLevel;

    public EnchantDetailGui(
            ServerEnchants plugin, Player viewer, CustomEnchantment enchantment, int currentLevel) {
        super(plugin, viewer, enchantment.getTier().getColor() + enchantment.getDisplayName(), 27);
        this.plugin = plugin;
        this.enchantment = enchantment;
        this.currentLevel = currentLevel;
    }

    @Override
    protected void initializeItems() {
        // Fill border
        ItemStack borderPane =
                new ItemBuilder(Material.PURPLE_STAINED_GLASS_PANE).name(" ").build();
        fillBorder(new GuiItem(borderPane, false));

        // Fill empty
        ItemStack emptyPane = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        fillEmpty(new GuiItem(emptyPane, false));

        // Enchantment icon (slot 4)
        ItemBuilder iconBuilder =
                new ItemBuilder(enchantment.getIcon())
                        .name(enchantment.getTier().getColor() + enchantment.getDisplayName())
                        .glow();

        for (String line : enchantment.getDescription()) {
            iconBuilder.addLoreLine("<gray>" + line);
        }

        iconBuilder
                .addLoreLine("")
                .addLoreLine("<dark_gray>Tier: " + enchantment.getTier().getColoredName())
                .addLoreLine("<dark_gray>Max Level: <white>" + enchantment.getMaxLevel());

        setItem(4, new GuiItem(iconBuilder.build(), false));

        // Current level display (slot 11)
        ItemStack levelDisplay =
                new ItemBuilder(Material.EXPERIENCE_BOTTLE)
                        .name("<green>Current Level")
                        .lore(
                                "<white>" + enchantment.getLevelDisplay(currentLevel),
                                "",
                                "<gray>Progress: <white>"
                                        + currentLevel
                                        + "<gray>/<white>"
                                        + enchantment.getMaxLevel())
                        .build();
        setItem(11, new GuiItem(levelDisplay, false));

        // Upgrade button (slot 13) - if not max level
        if (currentLevel < enchantment.getMaxLevel()) {
            setItem(13, createUpgradeButton());
        } else {
            ItemStack maxed =
                    new ItemBuilder(Material.GOLD_BLOCK)
                            .name("<gold><bold>MAX LEVEL")
                            .lore("<gray>This enchantment is fully upgraded!")
                            .glow()
                            .build();
            setItem(13, new GuiItem(maxed, false));
        }

        // Apply to Item button (slot 15)
        setItem(15, createApplyScrollButton());

        // Back button (slot 22)
        ItemStack backArrow = new ItemBuilder(Material.ARROW).name("<red>Back").build();
        setItem(
                22,
                GuiItem.of(
                        backArrow,
                        player -> {
                            new EnchantsListGui(plugin, player).open();
                        }));
    }

    private GuiItem createUpgradeButton() {
        int upgradeCost = (currentLevel + 1) * 25; // Cost scales with level
        PlayerProgression progression =
                plugin.getProgressionManager().getProgression(viewer.getUniqueId());

        boolean canAfford = progression.getTotalFragments() >= upgradeCost;

        ItemStack upgradeItem =
                new ItemBuilder(canAfford ? Material.LIME_DYE : Material.RED_DYE)
                        .name("<gold>Upgrade to Level " + (currentLevel + 1))
                        .lore(
                                "<gray>Cost: <yellow>" + upgradeCost + " fragments",
                                "",
                                "<gray>Your fragments: <yellow>" + progression.getTotalFragments(),
                                "",
                                canAfford
                                        ? "<green>Click to upgrade!"
                                        : "<red>Not enough fragments!")
                        .glow(canAfford)
                        .build();

        return GuiItem.of(
                upgradeItem,
                player -> {
                    if (!canAfford) {
                        TextUtil.sendError(player, "You don't have enough fragments!");
                        return;
                    }

                    // Deduct fragments
                    if (!progression.removeFragments(upgradeCost)) {
                        TextUtil.sendError(player, "Failed to deduct fragments!");
                        return;
                    }

                    // Upgrade enchantment
                    plugin.getProgressionManager()
                            .unlockEnchantment(
                                    player.getUniqueId(), enchantment.getId(), currentLevel + 1);

                    // Save progression
                    plugin.getProgressionManager().saveProgression(player.getUniqueId());

                    TextUtil.sendSuccess(
                            player,
                            "Upgraded "
                                    + enchantment.getDisplayName()
                                    + " to level "
                                    + (currentLevel + 1)
                                    + "!");
                    player.playSound(
                            player.getLocation(), "block.enchantment_table.use", 1.0f, 1.0f);

                    // Refresh GUI
                    player.closeInventory();
                    new EnchantDetailGui(plugin, player, enchantment, currentLevel + 1).open();
                });
    }

    private GuiItem createApplyScrollButton() {
        ItemStack scroll =
                new ItemBuilder(Material.PAPER)
                        .name("<aqua>Create Application Scroll")
                        .lore(
                                "<gray>Creates a scroll that applies",
                                "<gray>this enchantment to an item.",
                                "",
                                "<gray>Enchantment: "
                                        + enchantment.getTier().getColor()
                                        + enchantment.getDisplayName(),
                                "<gray>Level: <white>" + enchantment.getLevelDisplay(currentLevel),
                                "",
                                "<yellow>Click to receive scroll!")
                        .build();

        return GuiItem.of(
                scroll,
                player -> {
                    // Create scroll item
                    ItemStack scrollItem = createEnchantScrollItem();

                    // Give to player
                    if (player.getInventory().firstEmpty() == -1) {
                        TextUtil.sendError(player, "Your inventory is full!");
                        return;
                    }

                    player.getInventory().addItem(scrollItem);
                    TextUtil.sendSuccess(
                            player, "Created enchantment scroll! Right-click an item to apply.");
                    player.playSound(player.getLocation(), "item.book.page_turn", 1.0f, 1.0f);
                    player.closeInventory();
                });
    }

    private ItemStack createEnchantScrollItem() {
        ItemStack scroll =
                new ItemBuilder(Material.PAPER)
                        .name(
                                enchantment.getTier().getColor()
                                        + enchantment.getDisplayName()
                                        + " Scroll "
                                        + enchantment.getLevelDisplay(currentLevel))
                        .lore(
                                "<gray>Right-click an item to apply",
                                "<gray>this enchantment.",
                                "",
                                "<gray>Enchantment: "
                                        + enchantment.getTier().getColor()
                                        + enchantment.getDisplayName(),
                                "<gray>Level: <white>" + currentLevel,
                                "",
                                "<yellow>Single use")
                        .glow()
                        .build();

        // Add PDC data
        var meta = scroll.getItemMeta();
        if (meta != null) {
            NamespacedKey scrollTypeKey = new NamespacedKey(plugin, "scroll_type");
            NamespacedKey enchantIdKey = new NamespacedKey(plugin, "scroll_enchant_id");
            NamespacedKey levelKey = new NamespacedKey(plugin, "scroll_level");

            meta.getPersistentDataContainer()
                    .set(scrollTypeKey, PersistentDataType.STRING, "apply");
            meta.getPersistentDataContainer()
                    .set(enchantIdKey, PersistentDataType.STRING, enchantment.getId());
            meta.getPersistentDataContainer()
                    .set(levelKey, PersistentDataType.INTEGER, currentLevel);

            scroll.setItemMeta(meta);
        }

        return scroll;
    }
}
