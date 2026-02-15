package net.serverplugins.enchants.gui;

import net.serverplugins.api.ServerAPI;
import net.serverplugins.api.economy.EconomyProvider;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.messages.Placeholder;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.enchants.ServerEnchants;
import net.serverplugins.enchants.enchantments.EnchantTier;
import net.serverplugins.enchants.games.GameType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Tier selection GUI - allows players to select difficulty and game type. Two-stage selection:
 * first tier, then game type.
 */
public class TierSelectionGui extends Gui {

    private final ServerEnchants plugin;
    private EnchantTier selectedTier = null;

    public TierSelectionGui(ServerEnchants plugin, Player viewer) {
        super(plugin, viewer, "<gradient:#9B59B6:#8E44AD>Select Difficulty</gradient>", 45);
        this.plugin = plugin;
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

        // Tier selection buttons (row 1: slots 10-16)
        setItem(10, createTierButton(EnchantTier.COMMON));
        setItem(12, createTierButton(EnchantTier.UNCOMMON));
        setItem(14, createTierButton(EnchantTier.RARE));
        setItem(16, createTierButton(EnchantTier.LEGENDARY));

        // Game type buttons (row 3: slots 28-34) - only shown if tier selected
        if (selectedTier != null) {
            setItem(28, createGameButton(GameType.MEMORY));
            setItem(30, createGameButton(GameType.FORGE));
            setItem(32, createGameButton(GameType.ALCHEMY));
            setItem(34, createGameButton(GameType.DECRYPTION));
        }

        // Back button (slot 36)
        ItemStack backArrow = new ItemBuilder(Material.ARROW).name("<red>Back").build();
        setItem(
                36,
                GuiItem.of(
                        backArrow,
                        player -> {
                            new EnchanterMainGui(plugin, player).open();
                        }));
    }

    private GuiItem createTierButton(EnchantTier tier) {
        boolean canAccess = plugin.getProgressionManager().canAccessTier(viewer, tier);
        double cost = plugin.getEnchantsConfig().getCost(tier);
        int levelReq = plugin.getEnchantsConfig().getTierLevelRequirement(tier);
        int unlockReq = plugin.getEnchantsConfig().getTierUnlockRequirement(tier);

        Material material = canAccess ? tier.getGuiMaterial() : Material.BARRIER;

        ItemBuilder builder = new ItemBuilder(material).name(tier.getColoredName());

        if (canAccess) {
            builder.lore(
                    "<gray>Difficulty: " + tier.getDisplayName(),
                    "<gray>Cost: <gold>$" + String.format("%.0f", cost),
                    "",
                    tier == selectedTier ? "<green><bold>SELECTED" : "<yellow>Click to select!");

            if (tier == selectedTier) {
                builder.glow();
            }
        } else {
            builder.lore(
                    "<red><bold>LOCKED", "", "<gray>Requirements:", "<gray>• Level " + levelReq);

            if (unlockReq > 0) {
                EnchantTier previousTier =
                        switch (tier) {
                            case UNCOMMON -> EnchantTier.COMMON;
                            case RARE -> EnchantTier.UNCOMMON;
                            case LEGENDARY -> EnchantTier.RARE;
                            default -> null;
                        };

                if (previousTier != null) {
                    int currentUnlocks =
                            plugin.getProgressionManager()
                                    .countUnlocksByTier(viewer.getUniqueId(), previousTier);
                    builder.addLoreLine(
                            "<gray>• "
                                    + currentUnlocks
                                    + "/"
                                    + unlockReq
                                    + " "
                                    + previousTier.getDisplayName()
                                    + " unlocks");
                }
            }
        }

        ItemStack item = builder.build();

        return GuiItem.of(
                item,
                player -> {
                    if (!canAccess) {
                        TextUtil.sendError(
                                player, "You don't meet the requirements for this tier!");
                        return;
                    }

                    selectedTier = tier;
                    refresh();
                });
    }

    private GuiItem createGameButton(GameType gameType) {
        ItemStack gameItem =
                new ItemBuilder(gameType.getIcon())
                        .name("<aqua><bold>" + gameType.getDisplayName())
                        .lore(
                                "<gray>Tier: " + selectedTier.getColoredName(),
                                "<gray>Cost: <gold>$"
                                        + String.format(
                                                "%.0f",
                                                plugin.getEnchantsConfig().getCost(selectedTier)),
                                "",
                                "<yellow>Click to start!")
                        .build();

        return GuiItem.of(
                gameItem,
                player -> {
                    startGame(player, gameType);
                });
    }

    private void startGame(Player player, GameType gameType) {
        if (selectedTier == null) {
            TextUtil.sendError(player, "Please select a tier first!");
            return;
        }

        // Check if already in a game
        if (plugin.getGameSessionManager().hasActiveGame(player)) {
            TextUtil.sendError(player, "You already have an active game!");
            return;
        }

        // Check cost
        double cost = plugin.getEnchantsConfig().getCost(selectedTier);
        EconomyProvider economy = ServerAPI.getInstance().getEconomyProvider();

        boolean hasFreeAttempt = plugin.getDailyAttemptManager().hasFreeAttempt(player);

        if (!hasFreeAttempt && !economy.has(player, cost)) {
            CommonMessages.INSUFFICIENT_FUNDS.send(
                    player, Placeholder.of("amount", economy.format(cost)));
            return;
        }

        // Withdraw cost (if not using free attempt)
        if (!hasFreeAttempt) {
            if (!economy.withdraw(player, cost)) {
                TextUtil.sendError(player, "Failed to withdraw funds!");
                return;
            }
        } else {
            plugin.getDailyAttemptManager().useFreeAttempt(player);
            TextUtil.sendSuccess(player, "Used your free daily attempt!");
        }

        // Close GUI and start game
        player.closeInventory();
        plugin.getGameSessionManager().startGame(player, gameType, selectedTier);
    }
}
