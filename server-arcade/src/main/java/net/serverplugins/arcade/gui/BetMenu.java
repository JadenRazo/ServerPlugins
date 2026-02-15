package net.serverplugins.arcade.gui;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.games.GameType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Bet selection menu for games. Includes preset bet amounts, custom input, and +/- adjustment
 * buttons.
 */
public class BetMenu extends ArcadeGui {

    private final GameType gameType;
    private final GameGui parentGui;
    private final BiConsumer<Player, Integer> betCallback;
    private final Consumer<Integer> simpleBetCallback;

    // Current selected bet amount for adjustment
    private int currentSelectedBet;

    public BetMenu(ServerArcade plugin, GameType gameType, GameGui parentGui) {
        super(plugin, gameType.getBetMenuTitle(), gameType.getBetMenuSize());
        this.gameType = gameType;
        this.parentGui = parentGui;
        this.betCallback = null;
        this.simpleBetCallback = null;
        this.currentSelectedBet = gameType.getDefaultBet();
    }

    public BetMenu(ServerArcade plugin, GameType gameType, BiConsumer<Player, Integer> callback) {
        super(plugin, gameType.getBetMenuTitle(), gameType.getBetMenuSize());
        this.gameType = gameType;
        this.parentGui = null;
        this.betCallback = callback;
        this.simpleBetCallback = null;
        this.currentSelectedBet = gameType.getDefaultBet();
    }

    /** Simple constructor for TwoPlayerMachine that uses just a callback. */
    public BetMenu(GameType gameType, int size, Consumer<Integer> callback) {
        super(ServerArcade.getInstance(), gameType.getBetMenuTitle(), size);
        this.gameType = gameType;
        this.parentGui = null;
        this.betCallback = null;
        this.simpleBetCallback = callback;
        this.currentSelectedBet = gameType.getDefaultBet();
    }

    @Override
    protected void build() {
        int[] betAmounts = gameType.getBetAmounts();

        // Create preset bet buttons (top row)
        int[] slots = {9, 11, 13, 15, 17}; // Spread across row
        for (int i = 0; i < Math.min(betAmounts.length, slots.length); i++) {
            final int amount = betAmounts[i];
            ItemStack item = createBetItem(amount);
            setItem(
                    slots[i],
                    item,
                    e -> {
                        currentSelectedBet = amount;
                        selectBet(amount);
                    });
        }

        // Custom bet button (bottom row center)
        int[] customSlots = {38, 39, 40, 41, 42};
        ItemStack customItem = createCustomBetItem();
        setItems(customSlots, customItem, e -> openCustomBetInput());

        // Decrease bet buttons (bottom row left) - slots 27, 28, 29
        ItemStack decreaseBtn = createAdjustButton(false);
        int[] decreaseSlots = {27, 28, 29};
        for (int slot : decreaseSlots) {
            setItem(slot, decreaseBtn, e -> adjustBet(-getAdjustAmount()));
        }

        // Current bet display (center) - slot 31
        updateCurrentBetDisplay();

        // Increase bet buttons (bottom row right) - slots 33, 34, 35
        ItemStack increaseBtn = createAdjustButton(true);
        int[] increaseSlots = {33, 34, 35};
        for (int slot : increaseSlots) {
            setItem(slot, increaseBtn, e -> adjustBet(getAdjustAmount()));
        }

        // Confirm button - slot 31 (click current bet display to confirm)
        ItemStack confirmBtn = createConfirmButton();
        setItem(31, confirmBtn, e -> selectBet(currentSelectedBet));
    }

    /** Adjust the current bet by a delta amount. */
    private void adjustBet(int delta) {
        int newBet = currentSelectedBet + delta;
        newBet = Math.max(gameType.getMinBet(), Math.min(gameType.getMaxBet(), newBet));
        currentSelectedBet = newBet;
        updateCurrentBetDisplay();
    }

    /** Get the adjustment amount based on current bet size. */
    private int getAdjustAmount() {
        if (currentSelectedBet < 500) return 50;
        if (currentSelectedBet < 2000) return 100;
        if (currentSelectedBet < 10000) return 500;
        return 1000;
    }

    /** Update the display showing the current selected bet. */
    private void updateCurrentBetDisplay() {
        ItemStack display = createConfirmButton();
        inventory.setItem(31, display);
    }

    /** Create the confirm/current bet button. */
    private ItemStack createConfirmButton() {
        ItemStack item = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§l" + formatMoney(currentSelectedBet));
            meta.setLore(
                    List.of(
                            "§7Current bet amount",
                            "§f",
                            "§aClick to confirm!",
                            "§7Or use §c- §7/ §a+ §7to adjust"));
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Create a +/- adjustment button. */
    private ItemStack createAdjustButton(boolean increase) {
        ItemStack item =
                new ItemStack(
                        increase
                                ? Material.LIME_STAINED_GLASS_PANE
                                : Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String symbol = increase ? "§a§l+" : "§c§l-";
            meta.setDisplayName(symbol);
            meta.setLore(
                    List.of(
                            increase ? "§7Increase bet" : "§7Decrease bet",
                            "§7Adjustment: §6" + formatMoney(getAdjustAmount())));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createBetItem(int amount) {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(3); // Coin model from resource pack
            meta.setDisplayName("§6" + formatMoney(amount));
            meta.setLore(List.of("§7Click to select this bet!"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createCustomBetItem() {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(1);
            meta.setDisplayName("§f");
            meta.setLore(
                    List.of(
                            "§7Use this if you want to",
                            "§7enter a custom amount.",
                            "§f",
                            "§f §eLeft click to select!"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void selectBet(int amount) {
        if (parentGui != null) {
            parentGui.setBet(amount);
            parentGui.open(player);
        } else if (betCallback != null) {
            betCallback.accept(player, amount);
        } else if (simpleBetCallback != null) {
            simpleBetCallback.accept(amount);
        }
    }

    private void openCustomBetInput() {
        final Player currentPlayer = this.player;
        currentPlayer.closeInventory();
        currentPlayer.sendMessage(
                "§eEnter your bet amount in chat (min: §6"
                        + gameType.getMinBet()
                        + "§e, max: §6"
                        + gameType.getMaxBet()
                        + "§e):");

        plugin.getChatInputManager()
                .waitForInput(
                        currentPlayer,
                        input -> {
                            int amount;
                            try {
                                amount = Integer.parseInt(input);
                            } catch (NumberFormatException e) {
                                currentPlayer.sendMessage(
                                        "§cInvalid amount! Please enter a valid number.");
                                return;
                            }

                            int minBet = gameType.getMinBet();
                            int maxBet = gameType.getMaxBet();

                            if (amount < minBet || amount > maxBet) {
                                currentPlayer.sendMessage(
                                        "§cBet must be between §6"
                                                + minBet
                                                + "§c and §6"
                                                + maxBet
                                                + "§c!");
                                return;
                            }

                            // Restore player reference for callback execution
                            this.player = currentPlayer;
                            selectBet(amount);
                        });
    }

    private String formatMoney(int amount) {
        if (amount >= 1000000) {
            return String.format("%.1fM", amount / 1000000.0);
        } else if (amount >= 1000) {
            return String.format("%.1fK", amount / 1000.0);
        }
        return String.valueOf(amount);
    }
}
