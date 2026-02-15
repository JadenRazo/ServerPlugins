package net.serverplugins.arcade.games.slots;

import java.util.*;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.arcade.gui.GameGui;
import net.serverplugins.arcade.machines.Machine;
import net.serverplugins.arcade.utils.GameCommand;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

/** Slots game GUI with 5-reel spinning animation. */
public class SlotsGameGui extends GameGui {

    private final SlotsType slotsType;
    private boolean spinning = false;
    private BukkitTask spinEndTask;

    // Session statistics
    private int sessionWagered = 0;
    private int sessionWon = 0;

    // Animation state
    private final SlotItem[] lastItems;
    private final SlotItem[] finalItems;
    private final int[] animationSpeed;
    private final boolean[] finalItemPlaced;
    private final int[] shiftsAfterFinal;

    public SlotsGameGui(SlotsType slotsType, Machine machine) {
        super(slotsType, machine);
        this.slotsType = slotsType;

        int reelCount = slotsType.getDisplaySlots().size();
        this.lastItems = new SlotItem[reelCount];
        this.finalItems = new SlotItem[reelCount];
        this.animationSpeed = new int[reelCount];
        this.finalItemPlaced = new boolean[reelCount];
        this.shiftsAfterFinal = new int[reelCount];
    }

    @Override
    protected void build() {
        // Set initial slot display items
        for (int reel = 0; reel < slotsType.getDisplaySlots().size(); reel++) {
            List<Integer> slots = slotsType.getDisplaySlots().get(reel);
            for (int slot : slots) {
                SlotItem item = slotsType.getRandomItem();
                if (item != null) {
                    inventory.setItem(slot, item.getItemStack());
                }
            }
        }

        // Spin button
        ItemStack spinButton =
                createButton(
                        1,
                        "§f",
                        List.of(
                                "§7Try your luck for",
                                "§7a new combination.",
                                "§f",
                                "§f ▪ Your bet: §6" + formatMoney(bet),
                                "§f",
                                "§f §eLeft click to spin!"));
        setItems(slotsType.getSpinButtonSlots(), spinButton, e -> spin());

        // Bet button
        ItemStack betButton =
                createButton(
                        1,
                        "§f",
                        List.of(
                                "§7From here you can",
                                "§7choose bet amount.",
                                "§f",
                                "§f §eLeft click to choose!"));
        setItems(
                slotsType.getBetButtonSlots(),
                betButton,
                e -> {
                    if (!spinning) {
                        openBetMenu();
                    }
                });

        // Session statistics display (centered above spin/bet buttons)
        updateStatisticsDisplay();
    }

    /**
     * Create and update the session statistics display. Placed at slots 38-42 (directly above spin
     * and bet buttons).
     */
    private void updateStatisticsDisplay() {
        int netProfit = sessionWon - sessionWagered;
        String profitColor = netProfit >= 0 ? "§a" : "§c";
        String profitSign = netProfit >= 0 ? "+" : "";

        ItemStack statsDisplay =
                createButton(
                        1,
                        "§f",
                        List.of(
                                "§7━━━━━━━━━━━━━━━━━━━━━",
                                "§6Session Statistics",
                                "§f",
                                "§7Wagered: §c" + formatMoney(sessionWagered),
                                "§7Won: §a" + formatMoney(sessionWon),
                                "§7Profit: " + profitColor + profitSign + formatMoney(netProfit),
                                "§7━━━━━━━━━━━━━━━━━━━━━"));

        // Place at slots 38-42 (above spin/bet buttons)
        int[] statsSlots = {38, 39, 40, 41, 42};
        for (int slot : statsSlots) {
            inventory.setItem(slot, statsDisplay);
        }
    }

    private void spin() {
        if (spinning) return;

        // Check if player can afford bet
        if (!canAffordBet()) {
            TextUtil.sendError(player, "You don't have enough money!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 1f);
            return;
        }

        // Withdraw bet
        if (!withdrawBet()) return;

        // Track session statistics
        sessionWagered += bet;

        spinning = true;
        allowClose = false;

        // Pre-determine the final results using RTP-based odds calculator
        // This ensures proper 95% RTP (Return To Player) rate over time
        // The outcome tier is determined first, then symbols are generated to match
        SlotItem[] calculatedResult =
                slotsType.getOddsCalculator().calculateSpin(finalItems.length);
        System.arraycopy(calculatedResult, 0, finalItems, 0, finalItems.length);

        // LOG: What did the odds calculator give us?
        StringBuilder finalCombo = new StringBuilder("[");
        for (int i = 0; i < finalItems.length; i++) {
            finalCombo.append(finalItems[i].getId());
            if (i < finalItems.length - 1) finalCombo.append(", ");
        }
        finalCombo.append("]");
        Bukkit.getLogger().info("[SLOTS GUI] spin() - Pre-calculated finalItems[]: " + finalCombo);

        // Log the pre-calculated final items array
        StringBuilder combo = new StringBuilder("[");
        for (int i = 0; i < finalItems.length; i++) {
            combo.append(finalItems[i].getId());
            if (i < finalItems.length - 1) combo.append(", ");
        }
        combo.append("]");
        Bukkit.getLogger().info("[SLOTS DEBUG] spin() - Pre-calculated finalItems[]: " + combo);

        // Reset final item placement tracking
        Arrays.fill(finalItemPlaced, false);
        Arrays.fill(shiftsAfterFinal, 0);

        // Notify player
        TextUtil.send(player, "<gray>Bet placed: <gold>" + formatMoney(bet));

        // Start animation
        startSpinAnimation();

        // Schedule end of spinning
        // Add extra time to account for final shifts to middle slots (3 ticks + buffer)
        long totalDuration =
                slotsType.getSpinDuration() + 21L + 6L * slotsType.getDisplaySlots().size();
        spinEndTask =
                Bukkit.getScheduler()
                        .runTaskLater(
                                plugin,
                                () -> {
                                    spinning = false;
                                    allowClose = true;
                                    // Wait longer to ensure final shifts complete (3 tick shift +
                                    // 10 tick buffer)
                                    Bukkit.getScheduler()
                                            .runTaskLater(plugin, this::onSpinComplete, 15L);
                                },
                                totalDuration);
    }

    private void startSpinAnimation() {
        // Initialize animation speeds
        Arrays.fill(animationSpeed, slotsType.getSpinSpeed());

        // Start music
        playSpinMusic(true);

        // Start each reel with slight delay
        for (int reel = 0; reel < slotsType.getDisplaySlots().size(); reel++) {
            final int reelIndex = reel;
            Bukkit.getScheduler()
                    .runTaskLater(
                            plugin,
                            () -> {
                                animationSpeed[reelIndex]--;
                                spinReel(reelIndex);
                            },
                            2L * reel);
        }

        // Slow down animation gradually
        for (int pass = 0; pass < 2; pass++) {
            long delayOffset = pass * 4L;
            Bukkit.getScheduler()
                    .runTaskLater(
                            plugin,
                            () -> {
                                for (int i = 0; i < slotsType.getDisplaySlots().size(); i++) {
                                    final int idx = i;
                                    Bukkit.getScheduler()
                                            .runTaskLater(
                                                    plugin,
                                                    () -> {
                                                        animationSpeed[idx]--;
                                                    },
                                                    2L * idx);
                                }
                            },
                            delayOffset);
        }

        // Speed up briefly then stop
        Bukkit.getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            for (int j = 0; j < 3; j++) {
                                long delayJ = j * 5L;
                                Bukkit.getScheduler()
                                        .runTaskLater(
                                                plugin,
                                                () -> {
                                                    for (int i = 0;
                                                            i < slotsType.getDisplaySlots().size();
                                                            i++) {
                                                        final int idx = i;
                                                        Bukkit.getScheduler()
                                                                .runTaskLater(
                                                                        plugin,
                                                                        () -> {
                                                                            animationSpeed[idx]++;
                                                                        },
                                                                        4L * idx);
                                                    }
                                                },
                                                delayJ);
                            }
                        },
                        slotsType.getSpinDuration());

        // Stop each reel one by one
        Bukkit.getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            for (int i = 0; i < slotsType.getDisplaySlots().size(); i++) {
                                final int idx = i;
                                Bukkit.getScheduler()
                                        .runTaskLater(
                                                plugin,
                                                () -> {
                                                    animationSpeed[idx] = 0;
                                                    player.playSound(
                                                            player.getLocation(),
                                                            Sound.BLOCK_NOTE_BLOCK_BIT,
                                                            2f,
                                                            1f);
                                                },
                                                6L * idx);
                            }
                        },
                        slotsType.getSpinDuration() + 20L);
    }

    private void spinReel(int reel) {
        List<Integer> slots = slotsType.getDisplaySlots().get(reel);

        // Determine what item to use
        if (animationSpeed[reel] == 0 && !finalItemPlaced[reel]) {
            // Speed is 0, place the predetermined final item at the top
            lastItems[reel] = finalItems[reel];
            finalItemPlaced[reel] = true;
        } else if (finalItemPlaced[reel] && shiftsAfterFinal[reel] < 1) {
            // Final item placed, but needs to shift to middle slot - use random for other positions
            lastItems[reel] = slotsType.getRandomItem();
        } else if (animationSpeed[reel] > 0) {
            // Still spinning - use weighted random items
            lastItems[reel] = slotsType.getRandomItem();
        } else {
            // Animation complete - final item is in middle slot
            return;
        }

        // Shift items down (top -> middle -> bottom)
        for (int i = slots.size() - 1; i > 0; i--) {
            ItemStack prev = inventory.getItem(slots.get(i - 1));
            inventory.setItem(slots.get(i), prev);
        }

        // Add new item at top
        if (lastItems[reel] != null) {
            inventory.setItem(slots.get(0), lastItems[reel].getItemStack());
        }

        // Track shifts after final item placement
        if (finalItemPlaced[reel]) {
            shiftsAfterFinal[reel]++;
        }

        // Play tick sound
        player.playSound(player.getLocation(), Sound.BLOCK_BAMBOO_HIT, 0.02f, 0.5f);

        // Continue animation
        if (animationSpeed[reel] > 0) {
            // Still spinning fast
            Bukkit.getScheduler().runTaskLater(plugin, () -> spinReel(reel), animationSpeed[reel]);
        } else if (!finalItemPlaced[reel]) {
            // Speed is 0, need to place final item
            Bukkit.getScheduler().runTaskLater(plugin, () -> spinReel(reel), 1L);
        } else if (shiftsAfterFinal[reel] < 1) {
            // Final item placed, need to shift it to middle slot
            Bukkit.getScheduler().runTaskLater(plugin, () -> spinReel(reel), 3L);
        }
        // else: animation complete, stop
    }

    private void playSpinMusic(boolean highPitch) {
        player.playSound(
                player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, highPitch ? 1f : 1.5f);

        if (animationSpeed[1] > 0) {
            long delay = animationSpeed[3] == 1 ? 4L : animationSpeed[1] * 3L;
            Bukkit.getScheduler().runTaskLater(plugin, () -> playSpinMusic(!highPitch), delay);
        }
    }

    private void onSpinComplete() {
        // Read what's ACTUALLY displayed in ALL slots after animation (3x5 grid)
        List<List<Integer>> displaySlots = slotsType.getDisplaySlots();
        int rows = displaySlots.isEmpty() ? 0 : displaySlots.get(0).size();
        int cols = displaySlots.size();

        // Create 2D grid (rows x columns)
        SlotItem[][] grid = new SlotItem[rows][cols];

        // Read 1D results (middle row for backward compatibility)
        SlotItem[] actualResults = new SlotItem[cols];
        boolean allValid = true;

        for (int col = 0; col < cols; col++) {
            List<Integer> reelSlots = displaySlots.get(col);

            // Read all rows for this column
            for (int row = 0; row < rows && row < reelSlots.size(); row++) {
                ItemStack currentItem = inventory.getItem(reelSlots.get(row));
                SlotItem slotItem = slotsType.getSlotItemByStack(currentItem);
                grid[row][col] = slotItem;

                // Middle row becomes the 1D result for legacy reward checking
                if (row == 1) {
                    actualResults[col] = slotItem;
                    if (slotItem == null) {
                        allValid = false;
                    }
                }
            }
        }

        // If animation didn't complete properly, force correct items into middle slots
        if (!allValid) {
            Bukkit.getLogger()
                    .warning(
                            "[SLOTS GUI] onSpinComplete() - Animation incomplete! Forcing finalItems[] to middle slots");
            for (int col = 0; col < finalItems.length && col < cols; col++) {
                if (finalItems[col] != null) {
                    List<Integer> reelSlots = displaySlots.get(col);
                    if (reelSlots.size() > 1) {
                        inventory.setItem(reelSlots.get(1), finalItems[col].getItemStack());
                        actualResults[col] = finalItems[col];
                        grid[1][col] = finalItems[col];
                    }
                }
            }
        }

        // Log what we're actually checking
        StringBuilder displayedCombo = new StringBuilder("[");
        for (int i = 0; i < actualResults.length; i++) {
            displayedCombo.append(actualResults[i] != null ? actualResults[i].getId() : "null");
            if (i < actualResults.length - 1) displayedCombo.append(", ");
        }
        displayedCombo.append("]");
        Bukkit.getLogger()
                .info(
                        "[SLOTS DEBUG] onSpinComplete() - Displayed items in middle slots: "
                                + displayedCombo);

        // Check for rewards using both 1D and 2D patterns
        SlotReward reward = slotsType.checkRewards(actualResults, grid, bet);

        Bukkit.getLogger()
                .info(
                        "[SLOTS DEBUG] onSpinComplete() - Reward found: "
                                + (reward != null
                                        ? "YES (value: " + reward.getValue(bet) + ")"
                                        : "NO"));

        if (reward != null) {
            int winAmount = reward.getValue(bet);

            // Track session statistics
            sessionWon += winAmount;

            // Highlight winning slots with win information
            // Pass the actual grid so we can show what's REALLY displayed
            highlightWinningSlots(reward, displaySlots, grid, winAmount);

            // Execute reward commands
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("%bet%", String.valueOf(bet));
            placeholders.put("%player%", player.getName());

            for (GameCommand cmd : reward.getCommands()) {
                cmd.execute(player, placeholders);
            }

            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);

            TextUtil.send(
                    player,
                    "<green><bold>WIN!</bold> <gray>You won <gold>"
                            + formatMoney(winAmount)
                            + "<gray>!");
        } else {
            TextUtil.sendError(player, "No match. Better luck next time!");
        }

        // Update the statistics display with new session data
        updateStatisticsDisplay();

        // Don't call update() - it would rebuild the GUI and erase the spin results
    }

    /** Highlight winning slot positions with win information in lore. */
    private void highlightWinningSlots(
            SlotReward reward, List<List<Integer>> displaySlots, SlotItem[][] grid, int winAmount) {
        if (reward == null) return;

        List<SlotReward.Position> winPositions = reward.getMatchedPositions();
        if (winPositions == null || winPositions.isEmpty()) return;

        // Generate win description based on reward type and ACTUAL displayed items
        List<String> winDescription = getWinDescription(reward, grid, winAmount);

        // Create lime stained glass pane for highlighting
        ItemStack highlight = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = highlight.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§a§lWINNER!");
            highlight.setItemMeta(meta);
        }

        // For each winning position, add win info and effects
        for (SlotReward.Position pos : winPositions) {
            if (pos.col >= displaySlots.size()) continue;

            List<Integer> reelSlots = displaySlots.get(pos.col);
            if (pos.row >= reelSlots.size()) continue;

            int slotIndex = reelSlots.get(pos.row);

            // Try to add highlight above (if empty or air)
            if (pos.row > 0) {
                int aboveSlot = reelSlots.get(pos.row - 1);
                addHighlightIfEmpty(aboveSlot, highlight);
            }

            // Try to add highlight below (if empty or air)
            if (pos.row < reelSlots.size() - 1) {
                int belowSlot = reelSlots.get(pos.row + 1);
                addHighlightIfEmpty(belowSlot, highlight);
            }

            // Add enchantment glint and win info to the winning item itself
            ItemStack winningItem = inventory.getItem(slotIndex);
            if (winningItem != null) {
                winningItem = winningItem.clone();
                ItemMeta winMeta = winningItem.getItemMeta();
                if (winMeta != null) {
                    // Add enchantment glint
                    winMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
                    winMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

                    // Add win description to lore
                    List<String> lore =
                            winMeta.hasLore()
                                    ? new ArrayList<>(winMeta.getLore())
                                    : new ArrayList<>();
                    lore.add("§f"); // Empty line
                    lore.addAll(winDescription);
                    winMeta.setLore(lore);

                    winningItem.setItemMeta(winMeta);
                    inventory.setItem(slotIndex, winningItem);
                }
            }
        }
    }

    /** Generate a win description based on the reward type and ACTUAL displayed symbols. */
    private List<String> getWinDescription(SlotReward reward, SlotItem[][] grid, int winAmount) {
        List<String> description = new ArrayList<>();
        description.add("§a§l✓ WINNING COMBINATION");

        if (reward instanceof SlotReward.RowReward rowReward) {
            int actualMatches = reward.getMatchedPositions().size();
            int required = rowReward.getRequiredCount();

            // Get the ACTUAL symbol from the first matched position
            String actualSymbolName = "Unknown";
            if (!reward.getMatchedPositions().isEmpty() && grid != null) {
                SlotReward.Position firstMatch = reward.getMatchedPositions().get(0);
                if (firstMatch.row < grid.length && firstMatch.col < grid[0].length) {
                    SlotItem actualItem = grid[firstMatch.row][firstMatch.col];
                    if (actualItem != null) {
                        actualSymbolName = actualItem.getId();
                    }
                }
            }

            // Verify the match count is correct
            if (actualMatches < required) {
                plugin.getLogger()
                        .warning(
                                "[SLOTS ERROR] RowReward has "
                                        + actualMatches
                                        + " matched positions but requires "
                                        + required
                                        + "!");
                description.add("§c§lERROR: Invalid win detected!");
                description.add("§7Please report this bug");
            } else {
                description.add("§7" + actualMatches + "x §e" + actualSymbolName);
            }

            // Debug log to compare expected vs actual
            String expectedSymbol = rowReward.getItem().getId();
            if (!expectedSymbol.equals(actualSymbolName)) {
                plugin.getLogger()
                        .warning(
                                "[SLOTS DEBUG] Symbol mismatch! Expected: "
                                        + expectedSymbol
                                        + ", Actual: "
                                        + actualSymbolName);
            }
        } else if (reward instanceof SlotReward.PatternReward patternReward) {
            // Show the pattern type
            String patternName = patternReward.getPatternType().toString().replace("_", " ");
            description.add("§7Pattern: §e" + patternName);

            // Get the ACTUAL symbol from the first matched position
            if (patternReward.getRequiredItem() != null) {
                String symbolName = patternReward.getRequiredItem().getId();

                // Try to get actual symbol from grid
                if (!reward.getMatchedPositions().isEmpty() && grid != null) {
                    SlotReward.Position firstMatch = reward.getMatchedPositions().get(0);
                    if (firstMatch.row < grid.length && firstMatch.col < grid[0].length) {
                        SlotItem actualItem = grid[firstMatch.row][firstMatch.col];
                        if (actualItem != null) {
                            symbolName = actualItem.getId();
                        }
                    }
                }

                description.add("§7Symbol: §e" + symbolName);
            }
        } else if (reward instanceof SlotReward.ExactMatchReward) {
            // Show exact match
            description.add("§7Exact Match Combo!");
        }

        // Show the payout
        double multiplier = (double) winAmount / bet;
        description.add("§f");
        description.add("§6⭐ Pays: §e" + formatMoney(winAmount));
        description.add("§7(§e" + String.format("%.1f", multiplier) + "x §7your bet)");

        return description;
    }

    /** Add highlight pane to a slot if it's empty or contains a button. */
    private void addHighlightIfEmpty(int slot, ItemStack highlight) {
        ItemStack current = inventory.getItem(slot);
        if (current == null || current.getType() == Material.AIR) {
            inventory.setItem(slot, highlight);
        }
    }

    private ItemStack createButton(int customModelData, String name, List<String> lore) {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(customModelData);
            meta.setDisplayName(name);
            meta.setLore(lore);
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

    @Override
    protected void onClose(InventoryCloseEvent event) {
        // Cancel any pending spin tasks
        if (spinEndTask != null && !spinEndTask.isCancelled()) {
            spinEndTask.cancel();
            spinEndTask = null;
        }

        // Reset state
        spinning = false;
        allowClose = true;

        // Call parent to release machine and unseat player
        super.onClose(event);
    }

    public boolean isSpinning() {
        return spinning;
    }
}
