package net.serverplugins.enchants.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.enchants.ServerEnchants;
import net.serverplugins.enchants.enchantments.CustomEnchantment;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** GUI showing all enchantments, locked and unlocked. */
public class EnchantsListGui extends Gui {

    private final ServerEnchants plugin;

    public EnchantsListGui(ServerEnchants plugin, Player viewer) {
        super(plugin, viewer, "<gradient:#9B59B6:#8E44AD>Enchantments</gradient>", 45);
        this.plugin = plugin;
    }

    @Override
    protected void initializeItems() {
        // Fill border
        ItemStack borderPane =
                new ItemBuilder(Material.PURPLE_STAINED_GLASS_PANE).name(" ").build();
        fillBorder(new GuiItem(borderPane, false));

        // Get player unlocks
        Map<String, Integer> unlocks = plugin.getRepository().getUnlocks(viewer.getUniqueId());

        // Get all enchantments
        List<CustomEnchantment> allEnchants =
                new ArrayList<>(plugin.getEnchantmentRegistry().getAll());

        // Display enchantments (starting at slot 10)
        int slot = 10;
        for (CustomEnchantment enchant : allEnchants) {
            if (slot >= 35) break; // Don't overflow GUI

            int unlockedLevel = unlocks.getOrDefault(enchant.getId(), 0);
            boolean isUnlocked = unlockedLevel > 0;

            setItem(slot, createEnchantItem(enchant, isUnlocked, unlockedLevel));
            slot++;

            // Skip border slots
            if (slot % 9 == 0) slot += 2;
            else if (slot % 9 == 8) slot++;
        }

        // Back button
        ItemStack backArrow = new ItemBuilder(Material.ARROW).name("<red>Back").build();
        setItem(
                36,
                GuiItem.of(
                        backArrow,
                        player -> {
                            new EnchanterMainGui(plugin, player).open();
                        }));
    }

    private GuiItem createEnchantItem(CustomEnchantment enchant, boolean unlocked, int level) {
        ItemBuilder builder;

        if (unlocked) {
            builder =
                    new ItemBuilder(enchant.getIcon())
                            .name(
                                    enchant.getTier().getColor()
                                            + enchant.getDisplayName()
                                            + " "
                                            + enchant.getLevelDisplay(level))
                            .glow();

            // Add description
            for (String line : enchant.getDescription()) {
                builder.addLoreLine("<gray>" + line);
            }

            builder.addLoreLine("")
                    .addLoreLine(
                            "<dark_gray>Level: <white>"
                                    + level
                                    + "<gray>/<white>"
                                    + enchant.getMaxLevel())
                    .addLoreLine("<dark_gray>Tier: " + enchant.getTier().getColoredName())
                    .addLoreLine("")
                    .addLoreLine("<yellow>Click for details!");
        } else {
            builder =
                    new ItemBuilder(Material.BARRIER)
                            .name("<dark_gray>???")
                            .lore(
                                    "<gray>This enchantment is locked.",
                                    "",
                                    "<gray>Win games to unlock!");
        }

        ItemStack item = builder.build();

        return GuiItem.of(
                item,
                player -> {
                    if (unlocked) {
                        new EnchantDetailGui(plugin, player, enchant, level).open();
                    }
                });
    }
}
