package net.serverplugins.enchants.gui;

import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.enchants.ServerEnchants;
import net.serverplugins.enchants.models.PlayerProgression;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Main enchanter GUI showing player stats and navigation options. */
public class EnchanterMainGui extends Gui {

    private final ServerEnchants plugin;

    public EnchanterMainGui(ServerEnchants plugin, Player viewer) {
        super(plugin, viewer, "<gradient:#9B59B6:#8E44AD>Enchanter</gradient>", 27);
        this.plugin = plugin;
    }

    @Override
    protected void initializeItems() {
        // Fill border with dark purple glass
        ItemStack borderPane =
                new ItemBuilder(Material.PURPLE_STAINED_GLASS_PANE).name(" ").build();
        fillBorder(new GuiItem(borderPane, false));

        // Fill empty slots with black glass
        ItemStack emptyPane = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        fillEmpty(new GuiItem(emptyPane, false));

        // Player stats (slot 4)
        setItem(4, createStatsItem());

        // Play Games (slot 10)
        ItemStack playGames =
                new ItemBuilder(Material.EMERALD)
                        .name("<green><bold>Play Games")
                        .lore(
                                "<gray>Test your skills in",
                                "<gray>enchanting mini-games",
                                "",
                                "<yellow>Click to select difficulty!")
                        .glow()
                        .build();
        setItem(
                10,
                GuiItem.of(
                        playGames,
                        player -> {
                            new TierSelectionGui(plugin, player).open();
                        }));

        // My Enchantments (slot 12)
        ItemStack myEnchants =
                new ItemBuilder(Material.ENCHANTED_BOOK)
                        .name("<light_purple><bold>My Enchantments")
                        .lore(
                                "<gray>View your unlocked",
                                "<gray>enchantments and upgrades",
                                "",
                                "<yellow>Click to view!")
                        .build();
        setItem(
                12,
                GuiItem.of(
                        myEnchants,
                        player -> {
                            new EnchantsListGui(plugin, player).open();
                        }));

        // Fragment Shop (slot 14)
        ItemStack shop =
                new ItemBuilder(Material.GOLD_INGOT)
                        .name("<gold><bold>Fragment Shop")
                        .lore(
                                "<gray>Spend your fragments",
                                "<gray>on powerful items",
                                "",
                                "<yellow>Click to browse!")
                        .build();
        setItem(
                14,
                GuiItem.of(
                        shop,
                        player -> {
                            new FragmentShopGui(plugin, player).open();
                        }));

        // Daily Bonus (slot 16)
        setItem(16, createDailyBonusItem());
    }

    private GuiItem createStatsItem() {
        PlayerProgression progression =
                plugin.getProgressionManager().getProgression(viewer.getUniqueId());

        ItemStack head =
                new ItemBuilder(Material.PLAYER_HEAD)
                        .name(
                                "<gradient:#FFD700:#FFA500>"
                                        + viewer.getName()
                                        + "'s Stats</gradient>")
                        .lore(
                                "<gray>Level: <white>" + progression.getLevel(),
                                "<gray>XP: <white>"
                                        + progression.getExperience()
                                        + "<gray>/<white>"
                                        + progression.getXpForNextLevel(),
                                "",
                                "<gold>Fragments: <yellow>" + progression.getTotalFragments(),
                                "",
                                "<aqua>Games Played: <white>"
                                        + progression.getLifetimeGamesPlayed(),
                                "<green>Games Won: <white>" + progression.getLifetimeGamesWon())
                        .build();

        return new GuiItem(head, false);
    }

    private GuiItem createDailyBonusItem() {
        boolean hasFreeAttempt = plugin.getDailyAttemptManager().hasFreeAttempt(viewer);

        Material material = hasFreeAttempt ? Material.LIME_DYE : Material.RED_DYE;
        String statusColor = hasFreeAttempt ? "<green>" : "<red>";
        String statusText = hasFreeAttempt ? "Available" : "Used Today";

        ItemStack clock =
                new ItemBuilder(material)
                        .name("<gold><bold>Daily Bonus")
                        .lore(
                                "<gray>Free game attempt:",
                                statusColor + statusText,
                                "",
                                hasFreeAttempt
                                        ? "<yellow>Play a game to claim!"
                                        : "<gray>Come back tomorrow!")
                        .glow(hasFreeAttempt)
                        .build();

        return new GuiItem(clock, false);
    }
}
