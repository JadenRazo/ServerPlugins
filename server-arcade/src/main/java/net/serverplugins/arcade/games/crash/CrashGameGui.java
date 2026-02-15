package net.serverplugins.arcade.games.crash;

import java.util.*;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.games.GameResult;
import net.serverplugins.arcade.gui.ArcadeGui;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

/** GUI for crash game - shows multiplier, betting interface, and crash history. */
public class CrashGameGui extends ArcadeGui {

    private final CrashManager crashManager;
    private final CrashType crashType;
    private BukkitTask updateTask;
    private int currentBet;
    private double autoCashoutMultiplier = 0; // 0 = disabled
    private int autoBetAmount = 0; // 0 = disabled
    private boolean autoBetPlacedThisRound = false;
    private final List<Double> recentCrashes = new ArrayList<>();
    private String currentTitle;

    // GUI Layout constants
    private static final int MULTIPLIER_SLOT = 22; // Center display (paper)
    private static final int BET_DISPLAY_SLOT = 31; // Show current bet (below paper)
    private static final int CUSTOM_BET_SLOT = 28; // Custom bet input button (row 3)
    private static final int AUTO_BET_SLOT = 4; // Two above paper, above middle history wool
    private static final int AUTO_CASHOUT_SLOT = 34; // Two right of increase button
    private static final int[] ACTION_SLOTS = {49, 50, 51, 52, 53}; // Row 5, place bet / cash out
    private static final int[] GRAPH_SLOTS = {10, 16, 19, 25};
    private static final int[] HISTORY_SLOTS = {11, 12, 13, 14, 15}; // 5 slots centered above paper
    private static final int MAX_CUSTOM_BET = 1_000_000;

    // Track active viewers for update broadcasts
    private static final Set<CrashGameGui> activeGuis =
            Collections.synchronizedSet(new HashSet<>());

    public CrashGameGui(ServerArcade plugin, CrashManager crashManager, CrashType crashType) {
        super(plugin, crashType.getWaitingTitle(), 54);
        this.crashManager = crashManager;
        this.crashType = crashType;
        this.currentBet = (int) plugin.getArcadeConfig().getMinBet();
        this.currentTitle = crashType.getWaitingTitle();

        // Seed history from CrashManager so players always see previous results
        List<Double> managerHistory = crashManager.getRecentCrashes();
        if (!managerHistory.isEmpty()) {
            int start = Math.max(0, managerHistory.size() - HISTORY_SLOTS.length);
            recentCrashes.addAll(managerHistory.subList(start, managerHistory.size()));
        }
    }

    @Override
    protected void build() {
        // No glass pane fillers - clean GUI with inventory hiding via packets

        // Multiplier display (center)
        updateMultiplierDisplay();

        // Action slots (place bet / cash out - invisible, custom UI provides visuals)
        updateActionSlots();

        // Bet amount display
        updateBetDisplay();

        // Custom bet button
        updateCustomBetButton();

        // Auto-bet button
        updateAutoBetButton();

        // Auto-cashout button
        updateAutoCashoutButton();

        // Check auto-bet and auto-cashout triggers
        checkAutoBet();
        checkAutoCashout();

        // Visual graph
        updateGraph();

        // History display (above multiplier paper)
        updateHistory();
    }

    @Override
    protected boolean shouldRebuildHandlers() {
        // Only rebuild handlers on explicit update(), not during animation
        return false;
    }

    /**
     * Update the multiplier display in the center. Also checks if title needs to change based on
     * game state.
     */
    private void updateMultiplierDisplay() {
        double mult = crashManager.getCurrentMultiplier();
        CrashManager.State state = crashManager.getCurrentState();

        // Update title based on state
        String newTitle;
        if (state == CrashManager.State.BETTING) {
            newTitle = crashType.getWaitingTitle();
        } else if (state == CrashManager.State.RUNNING) {
            newTitle = crashType.getStartedTitle();
        } else {
            newTitle = crashType.getWaitingTitle();
        }

        if (!newTitle.equals(currentTitle)) {
            currentTitle = newTitle;
            updateTitle(currentTitle);
        }

        // Display based on state
        String name;
        List<String> lore;

        switch (state) {
            case BETTING:
                int timeLeft = crashManager.getBettingTimeRemaining();
                name = "§e§lBETTING OPEN";
                lore =
                        List.of(
                                "§7Time remaining: §6" + timeLeft + "s",
                                "§f",
                                "§aPlace your bets now!");
                break;

            case RUNNING:
                name = String.format("§f§l%.2fx", mult);
                lore = List.of("§7Multiplier climbing...", "§f", "§cCould crash any moment!");
                break;

            case CRASHED:
                name = "§c§lCRASHED!";
                lore = List.of("§7Game ended", "§f", "§7Next round starting soon...");
                break;

            default:
                name = "§e§lWAITING...";
                lore = List.of("§7Preparing next round", "§f", "§7Stand by...");
                break;
        }

        ItemStack item = createItem(Material.PAPER, name, lore);
        inventory.setItem(MULTIPLIER_SLOT, item);
    }

    /**
     * Update the action slots (place bet / cash out). Items use custom model data for invisibility
     * - resource pack renders them transparent. Lore still displays on hover for player info.
     */
    private void updateActionSlots() {
        if (player == null) return;

        CrashManager.State state = crashManager.getCurrentState();
        boolean hasBet = crashManager.hasBet(player.getUniqueId());
        boolean canBet =
                !hasBet
                        && (state == CrashManager.State.BETTING
                                || (state == CrashManager.State.RUNNING
                                        && crashManager.getCurrentMultiplier() < 1.5));
        boolean canCashOut = hasBet && state == CrashManager.State.RUNNING;

        String name;
        List<String> lore;

        if (canBet) {
            name = "§a§lPLACE BET";
            if (state == CrashManager.State.BETTING) {
                int timeLeft = crashManager.getBettingTimeRemaining();
                lore =
                        List.of(
                                "§7Bet: §6$" + formatMoney(currentBet),
                                "§7Time left: §e" + timeLeft + "s",
                                "§f",
                                "§eClick to place bet!");
            } else {
                lore =
                        List.of(
                                "§7Bet: §6$" + formatMoney(currentBet),
                                "§f",
                                "§eClick to place bet!");
            }
        } else if (canCashOut) {
            double potential = currentBet * crashManager.getCurrentMultiplier();
            name = "§c§lCASH OUT";
            lore =
                    List.of(
                            "§7Value: §a$" + formatMoney((int) potential),
                            "§7Multiplier: §e"
                                    + String.format("%.2fx", crashManager.getCurrentMultiplier()),
                            "§f",
                            "§eClick to cash out!");
        } else if (hasBet) {
            name = "§e§lBET ACTIVE";
            lore =
                    List.of(
                            "§7Your bet: §6$" + formatMoney(currentBet),
                            "§f",
                            "§7Waiting for game to start...");
        } else {
            name = "§c§lBETTING CLOSED";
            lore = List.of("§7Wait for next round");
        }

        ItemStack item = createInvisibleItem(name, lore);
        for (int slot : ACTION_SLOTS) {
            inventory.setItem(slot, item);
            setItem(slot, item, e -> handleAction());
        }
    }

    /**
     * Create an item that renders as invisible via custom model data. Stick CMD 1 = ui_space
     * (transparent model in resource pack), lore still shows on hover.
     */
    private ItemStack createInvisibleItem(String name, List<String> lore) {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name != null ? name : "§f");
            if (lore != null) meta.setLore(lore);
            meta.setCustomModelData(1); // ui_space - invisible in resource pack
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Handle action slot click - place bet or cash out based on game state. */
    private void handleAction() {
        if (player == null) return;

        boolean hasBet = crashManager.hasBet(player.getUniqueId());
        CrashManager.State state = crashManager.getCurrentState();

        if (hasBet && state == CrashManager.State.RUNNING) {
            cashOut();
        } else if (!hasBet
                && (state == CrashManager.State.BETTING
                        || (state == CrashManager.State.RUNNING
                                && crashManager.getCurrentMultiplier() < 1.5))) {
            placeBet();
        }
    }

    /** Update the bet amount display with adjustment buttons. */
    private void updateBetDisplay() {
        ItemStack display =
                createItem(
                        Material.GOLD_BLOCK,
                        "§6$" + formatMoney(currentBet),
                        List.of("§7Current bet amount", "§f", "§a+ §7/ §c- §7to adjust"));
        inventory.setItem(BET_DISPLAY_SLOT, display);

        // Decrease button - use paper instead of glass pane
        ItemStack decrease = createItem(Material.PAPER, "§c§l-", List.of("§7Decrease bet"));
        setItem(BET_DISPLAY_SLOT - 1, decrease, e -> adjustBet(-getAdjustAmount()));

        // Increase button - use paper instead of glass pane
        ItemStack increase = createItem(Material.PAPER, "§a§l+", List.of("§7Increase bet"));
        setItem(BET_DISPLAY_SLOT + 1, increase, e -> adjustBet(getAdjustAmount()));
    }

    /**
     * Update the visual multiplier graph. Removed - resource pack background provides visual
     * design.
     */
    private void updateGraph() {
        // Graph removed - no glass panes needed with custom background
        // Clear graph slots to show clean background
        for (int slot : GRAPH_SLOTS) {
            inventory.setItem(slot, null);
        }
    }

    /** Update the crash history display (5 wool items centered above the paper). */
    private void updateHistory() {
        // Show most recent crashes, newest on the right
        int historyCount = Math.min(recentCrashes.size(), HISTORY_SLOTS.length);

        for (int i = 0; i < HISTORY_SLOTS.length; i++) {
            if (i < historyCount) {
                // Index from oldest (left) to newest (right)
                double crash = recentCrashes.get(recentCrashes.size() - historyCount + i);
                Material mat =
                        crash < 2.0
                                ? Material.RED_WOOL
                                : crash < 5.0 ? Material.YELLOW_WOOL : Material.GREEN_WOOL;
                String color = crash < 2.0 ? "§c" : crash < 5.0 ? "§e" : "§a";
                inventory.setItem(
                        HISTORY_SLOTS[i],
                        createItem(
                                mat,
                                color + String.format("%.2fx", crash),
                                List.of("§7Previous crash")));
            } else {
                inventory.setItem(HISTORY_SLOTS[i], null);
            }
        }
    }

    /** Place a bet on the current game. */
    private void placeBet() {
        CrashManager.State state = crashManager.getCurrentState();

        // Allow betting during BETTING countdown or early RUNNING (< 1.5x)
        if (state != CrashManager.State.BETTING
                && !(state == CrashManager.State.RUNNING
                        && crashManager.getCurrentMultiplier() < 1.5)) {
            TextUtil.sendError(player, "Betting window has closed!");
            return;
        }

        if (crashManager.hasBet(player.getUniqueId())) {
            TextUtil.sendError(player, "You already have an active bet!");
            return;
        }

        // Check balance
        if (ServerArcade.getEconomy() == null
                || !ServerArcade.getEconomy().has(player, currentBet)) {
            TextUtil.sendError(player, "You don't have enough money!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 1f);
            return;
        }

        // Withdraw and place bet
        ServerArcade.getEconomy().withdrawPlayer(player, currentBet);

        if (crashManager.placeBet(player.getUniqueId(), currentBet)) {
            TextUtil.send(player, "<green>Bet placed: <gold>$" + formatMoney(currentBet));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        } else {
            // Refund if bet failed
            ServerArcade.getEconomy().depositPlayer(player, currentBet);
            TextUtil.sendError(player, "Failed to place bet!");
        }
    }

    /** Cash out the current bet. */
    private void cashOut() {
        if (!crashManager.hasBet(player.getUniqueId())) {
            TextUtil.sendError(player, "You don't have an active bet!");
            return;
        }

        GameResult result = crashManager.cashOut(player.getUniqueId());
        if (result != null && result.payout() > 0) {
            ServerArcade.getEconomy().depositPlayer(player, result.payout());
            double multiplier = crashManager.getCurrentMultiplier();
            player.sendMessage(
                    "§a§lCASHED OUT! §7Won §6$"
                            + formatMoney((int) result.payout())
                            + " §7at §e"
                            + String.format("%.2fx", multiplier));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);

            // Send Discord webhook for big wins
            plugin.getDiscordWebhook()
                    .sendCrashWin(player.getName(), currentBet, multiplier, (int) result.payout());

            // Track statistics
            if (plugin.getStatisticsTracker() != null) {
                plugin.getStatisticsTracker()
                        .recordCrashGame(
                                player.getUniqueId(),
                                player.getName(),
                                currentBet,
                                multiplier,
                                (int) result.payout(),
                                true);
            }
        }
    }

    /** Adjust the bet amount. */
    private void adjustBet(int delta) {
        int min = (int) plugin.getArcadeConfig().getMinBet();
        int max = (int) plugin.getArcadeConfig().getMaxBet();
        currentBet = Math.max(min, Math.min(max, currentBet + delta));
        updateBetDisplay();
    }

    /** Get smart adjustment amount based on current bet. */
    private int getAdjustAmount() {
        if (currentBet < 500) return 50;
        if (currentBet < 2000) return 100;
        if (currentBet < 10000) return 500;
        return 1000;
    }

    /** Update the custom bet input button. */
    private void updateCustomBetButton() {
        ItemStack item =
                createItem(
                        Material.NAME_TAG,
                        "§e§lCUSTOM BET",
                        List.of(
                                "§7Type your own bet amount",
                                "§f",
                                "§7Max: §6$" + formatMoney(MAX_CUSTOM_BET),
                                "§f",
                                "§eClick to enter amount"));
        setItem(CUSTOM_BET_SLOT, item, e -> promptCustomBet());
    }

    /** Update the auto-bet button display. */
    private void updateAutoBetButton() {
        List<String> lore;
        if (autoBetAmount > 0) {
            lore =
                    List.of(
                            "§7Amount: §6$" + formatMoney(autoBetAmount),
                            "§f",
                            "§aAuto-bet is §lENABLED",
                            "§7Bets automatically each round",
                            "§f",
                            "§eClick to change",
                            "§cShift-click to disable");
        } else {
            lore =
                    List.of(
                            "§7Automatically place a bet",
                            "§7at the start of each round",
                            "§f",
                            "§cCurrently §lDISABLED",
                            "§f",
                            "§eClick to set amount");
        }

        String name =
                autoBetAmount > 0 ? "§a§lAUTO BET: $" + formatMoney(autoBetAmount) : "§7§lAUTO BET";

        ItemStack item = createItem(Material.HOPPER, name, lore);
        setItem(
                AUTO_BET_SLOT,
                item,
                e -> {
                    if (e.isShiftClick() && autoBetAmount > 0) {
                        autoBetAmount = 0;
                        TextUtil.sendError(player, "Auto-bet disabled.");
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
                    } else {
                        promptAutoBet();
                    }
                });
    }

    /** Check if auto-bet should trigger at the start of a betting phase. */
    private void checkAutoBet() {
        if (player == null || autoBetAmount <= 0) return;

        CrashManager.State state = crashManager.getCurrentState();

        // Reset the flag when game is not in BETTING (new round cycle)
        if (state != CrashManager.State.BETTING) {
            autoBetPlacedThisRound = false;
            return;
        }

        // Already placed auto-bet this round
        if (autoBetPlacedThisRound) return;

        // Don't double-bet
        if (crashManager.hasBet(player.getUniqueId())) return;

        // Check balance
        if (ServerArcade.getEconomy() == null
                || !ServerArcade.getEconomy().has(player, autoBetAmount)) {
            TextUtil.send(
                    player,
                    "<red><bold>AUTO BET</bold> <red>failed - insufficient funds. Auto-bet disabled.");
            autoBetAmount = 0;
            autoBetPlacedThisRound = true;
            return;
        }

        // Place the bet
        ServerArcade.getEconomy().withdrawPlayer(player, autoBetAmount);

        if (crashManager.placeBet(player.getUniqueId(), autoBetAmount)) {
            currentBet = autoBetAmount;
            TextUtil.send(
                    player,
                    "<yellow><bold>AUTO BET</bold> <gray>placed: <gold>$"
                            + formatMoney(autoBetAmount));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.8f);
        } else {
            ServerArcade.getEconomy().depositPlayer(player, autoBetAmount);
            TextUtil.send(player, "<red><bold>AUTO BET</bold> <red>failed to place bet.");
        }

        autoBetPlacedThisRound = true;
    }

    /** Prompt the player to type an auto-bet amount in chat. */
    private void promptAutoBet() {
        if (player == null) return;

        player.closeInventory();
        TextUtil.send(player, "<green><bold>AUTO BET</bold>");
        TextUtil.send(
                player,
                "<gray>Type the amount to bet each round (<gold>$"
                        + formatMoney((int) plugin.getArcadeConfig().getMinBet())
                        + " <gray>- <gold>$"
                        + formatMoney(MAX_CUSTOM_BET)
                        + "<gray>)");
        TextUtil.send(player, "<gray>Type <red>cancel <gray>to go back.");

        plugin.getChatInputManager()
                .waitForInput(
                        player,
                        input -> {
                            if (input.equalsIgnoreCase("cancel")) {
                                TextUtil.send(player, "<gray>Auto-bet setup cancelled.");
                                reopenGui();
                                return;
                            }

                            String cleaned =
                                    input.replace("$", "").replace(",", "").trim().toLowerCase();

                            int amount;
                            try {
                                if (cleaned.endsWith("m")) {
                                    amount =
                                            (int)
                                                    (Double.parseDouble(
                                                                    cleaned.substring(
                                                                            0,
                                                                            cleaned.length() - 1))
                                                            * 1_000_000);
                                } else if (cleaned.endsWith("k")) {
                                    amount =
                                            (int)
                                                    (Double.parseDouble(
                                                                    cleaned.substring(
                                                                            0,
                                                                            cleaned.length() - 1))
                                                            * 1_000);
                                } else {
                                    amount = Integer.parseInt(cleaned);
                                }
                            } catch (NumberFormatException ex) {
                                TextUtil.sendError(
                                        player, "Invalid amount. Please enter a number.");
                                reopenGui();
                                return;
                            }

                            int min = (int) plugin.getArcadeConfig().getMinBet();
                            if (amount < min) {
                                TextUtil.send(
                                        player, "<red>Minimum bet is <gold>$" + formatMoney(min));
                                reopenGui();
                                return;
                            }

                            if (amount > MAX_CUSTOM_BET) {
                                TextUtil.send(
                                        player,
                                        "<red>Maximum bet is <gold>$"
                                                + formatMoney(MAX_CUSTOM_BET));
                                reopenGui();
                                return;
                            }

                            autoBetAmount = amount;
                            TextUtil.send(
                                    player,
                                    "<green>Auto-bet set to <gold>$"
                                            + formatMoney(amount)
                                            + " <gray>per round");
                            player.playSound(
                                    player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.5f);
                            reopenGui();
                        });
    }

    /** Update the auto-cashout button display. */
    private void updateAutoCashoutButton() {
        List<String> lore;
        if (autoCashoutMultiplier > 0) {
            lore =
                    List.of(
                            "§7Target: §e" + String.format("%.2fx", autoCashoutMultiplier),
                            "§f",
                            "§aAuto-cashout is §lENABLED",
                            "§f",
                            "§eClick to change",
                            "§cShift-click to disable");
        } else {
            lore =
                    List.of(
                            "§7Automatically cash out when",
                            "§7the multiplier reaches your",
                            "§7target (e.g. 1.5x, 2.0x)",
                            "§f",
                            "§cCurrently §lDISABLED",
                            "§f",
                            "§eClick to set target");
        }

        String name =
                autoCashoutMultiplier > 0
                        ? "§e§lAUTO: " + String.format("%.2fx", autoCashoutMultiplier)
                        : "§7§lAUTO CASHOUT";

        ItemStack item = createItem(Material.CLOCK, name, lore);
        setItem(
                AUTO_CASHOUT_SLOT,
                item,
                e -> {
                    if (e.isShiftClick() && autoCashoutMultiplier > 0) {
                        autoCashoutMultiplier = 0;
                        TextUtil.sendError(player, "Auto-cashout disabled.");
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
                    } else {
                        promptAutoCashout();
                    }
                });
    }

    /** Check if auto-cashout should trigger. */
    private void checkAutoCashout() {
        if (player == null || autoCashoutMultiplier <= 0) return;
        if (crashManager.getCurrentState() != CrashManager.State.RUNNING) return;
        if (!crashManager.hasBet(player.getUniqueId())) return;

        if (crashManager.getCurrentMultiplier() >= autoCashoutMultiplier) {
            cashOut();
            player.sendMessage(
                    "§e§lAUTO-CASHOUT §7triggered at §e"
                            + String.format("%.2fx", autoCashoutMultiplier));
        }
    }

    /** Prompt the player to type an auto-cashout multiplier in chat. */
    private void promptAutoCashout() {
        if (player == null) return;

        player.closeInventory();
        TextUtil.send(player, "<yellow><bold>AUTO CASHOUT</bold>");
        TextUtil.send(
                player,
                "<gray>Type a target multiplier (e.g. <yellow>1.5<gray>, <yellow>2.0<gray>, <yellow>3.5<gray>)");
        TextUtil.send(player, "<gray>Type <red>cancel <gray>to go back.");

        plugin.getChatInputManager()
                .waitForInput(
                        player,
                        input -> {
                            if (input.equalsIgnoreCase("cancel")) {
                                TextUtil.send(player, "<gray>Auto-cashout setup cancelled.");
                                reopenGui();
                                return;
                            }

                            String cleaned = input.replace("x", "").replace("X", "").trim();

                            double target;
                            try {
                                target = Double.parseDouble(cleaned);
                            } catch (NumberFormatException ex) {
                                TextUtil.sendError(
                                        player,
                                        "Invalid multiplier. Enter a number like 1.5 or 2.0");
                                reopenGui();
                                return;
                            }

                            if (target < 1.01) {
                                TextUtil.send(player, "<red>Minimum auto-cashout is <yellow>1.01x");
                                reopenGui();
                                return;
                            }

                            if (target > 1000) {
                                TextUtil.send(player, "<red>Maximum auto-cashout is <yellow>1000x");
                                reopenGui();
                                return;
                            }

                            autoCashoutMultiplier = target;
                            TextUtil.send(
                                    player,
                                    "<green>Auto-cashout set to <yellow>"
                                            + String.format("%.2fx", target));
                            player.playSound(
                                    player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.5f);
                            reopenGui();
                        });
    }

    /** Prompt the player to type a custom bet amount in chat. */
    private void promptCustomBet() {
        if (player == null) return;

        player.closeInventory();
        TextUtil.send(player, "<gold><bold>CUSTOM BET</bold>");
        TextUtil.send(
                player,
                "<gray>Type your bet amount in chat (<gold>$"
                        + formatMoney((int) plugin.getArcadeConfig().getMinBet())
                        + " <gray>- <gold>$"
                        + formatMoney(MAX_CUSTOM_BET)
                        + "<gray>)");
        TextUtil.send(player, "<gray>Type <red>cancel <gray>to go back.");

        plugin.getChatInputManager()
                .waitForInput(
                        player,
                        input -> {
                            if (input.equalsIgnoreCase("cancel")) {
                                TextUtil.send(player, "<gray>Custom bet cancelled.");
                                reopenGui();
                                return;
                            }

                            // Strip common formatting: $, commas, k/m suffixes
                            String cleaned =
                                    input.replace("$", "").replace(",", "").trim().toLowerCase();

                            int amount;
                            try {
                                if (cleaned.endsWith("m")) {
                                    amount =
                                            (int)
                                                    (Double.parseDouble(
                                                                    cleaned.substring(
                                                                            0,
                                                                            cleaned.length() - 1))
                                                            * 1_000_000);
                                } else if (cleaned.endsWith("k")) {
                                    amount =
                                            (int)
                                                    (Double.parseDouble(
                                                                    cleaned.substring(
                                                                            0,
                                                                            cleaned.length() - 1))
                                                            * 1_000);
                                } else {
                                    amount = Integer.parseInt(cleaned);
                                }
                            } catch (NumberFormatException ex) {
                                TextUtil.sendError(
                                        player, "Invalid amount. Please enter a number.");
                                reopenGui();
                                return;
                            }

                            int min = (int) plugin.getArcadeConfig().getMinBet();
                            if (amount < min) {
                                TextUtil.send(
                                        player, "<red>Minimum bet is <gold>$" + formatMoney(min));
                                reopenGui();
                                return;
                            }

                            if (amount > MAX_CUSTOM_BET) {
                                TextUtil.send(
                                        player,
                                        "<red>Maximum bet is <gold>$"
                                                + formatMoney(MAX_CUSTOM_BET));
                                reopenGui();
                                return;
                            }

                            currentBet = amount;
                            TextUtil.send(
                                    player, "<green>Bet set to <gold>$" + formatMoney(currentBet));
                            reopenGui();
                        });
    }

    /** Reopen the crash GUI for this player after chat input. */
    private void reopenGui() {
        CrashGameGui gui = new CrashGameGui(plugin, crashManager, crashType);
        gui.currentBet = this.currentBet;
        gui.autoBetAmount = this.autoBetAmount;
        gui.autoCashoutMultiplier = this.autoCashoutMultiplier;
        // Constructor already seeds from CrashManager, but overlay our local copy
        // in case we have more recent entries not yet in the manager
        if (this.recentCrashes.size() > gui.recentCrashes.size()) {
            gui.recentCrashes.clear();
            int start = Math.max(0, this.recentCrashes.size() - HISTORY_SLOTS.length);
            gui.recentCrashes.addAll(this.recentCrashes.subList(start, this.recentCrashes.size()));
        }
        gui.open(player);
        gui.startUpdating();
    }

    /** Start the GUI update loop. */
    public void startUpdating() {
        activeGuis.add(this);

        updateTask =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                plugin,
                                () -> {
                                    if (player == null || !player.isOnline()) {
                                        stopUpdating();
                                        return;
                                    }

                                    // Update display elements without rebuilding handlers
                                    refreshDisplay();
                                },
                                0L,
                                2L); // Update every 2 ticks (10 times/sec)
    }

    /** Stop the GUI update loop. */
    public void stopUpdating() {
        activeGuis.remove(this);
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    /** Called when the game crashes - update all active GUIs. */
    public static void onGameCrash(double crashPoint) {
        synchronized (activeGuis) {
            for (CrashGameGui gui : activeGuis) {
                gui.addCrashToHistory(crashPoint);
                gui.updateHistory();
                gui.player.playSound(
                        gui.player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1f);
            }
        }
    }

    /** Update all active GUIs (called during betting countdown) */
    public static void updateAllGuis() {
        synchronized (activeGuis) {
            for (CrashGameGui gui : activeGuis) {
                gui.refreshDisplay();
            }
        }
    }

    private void addCrashToHistory(double crashPoint) {
        recentCrashes.add(crashPoint);
        while (recentCrashes.size() > HISTORY_SLOTS.length) {
            recentCrashes.remove(0);
        }
    }

    @Override
    protected void onClose(InventoryCloseEvent event) {
        stopUpdating();
    }

    private ItemStack createItem(Material material, String name) {
        return createItem(material, name, null);
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
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
