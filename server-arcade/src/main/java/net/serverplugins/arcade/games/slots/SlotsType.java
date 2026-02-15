package net.serverplugins.arcade.games.slots;

import java.util.*;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.games.GameType;
import net.serverplugins.arcade.machines.Direction;
import net.serverplugins.arcade.machines.Machine;
import net.serverplugins.arcade.machines.OnePlayerMachine;
import net.serverplugins.arcade.utils.GameCommand;
import net.serverplugins.arcade.utils.RandomList;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Slots game type with 5-reel animation. */
public class SlotsType extends GameType {

    // Display configuration
    private List<List<Integer>> displaySlots;
    private int spinDuration = 80;
    private int spinSpeed = 5;

    // Slot items and rewards
    private final RandomList<SlotItem> slotItems = new RandomList<>();
    private final List<SlotReward> rewards = new ArrayList<>();
    private final Map<String, SlotItem> itemMap = new HashMap<>();

    // RTP-based odds calculator (initialized after config load)
    private SlotsOddsCalculator oddsCalculator;

    // Button slots
    private int[] spinButtonSlots = {47, 48, 49};
    private int[] betButtonSlots = {50, 51};

    public SlotsType(ServerArcade plugin) {
        super(plugin, "Slots", "SLOTS");
        this.guiSize = 54;

        // Default display slots (5 columns, 3 rows each)
        displaySlots =
                List.of(
                        List.of(11, 20, 29),
                        List.of(12, 21, 30),
                        List.of(13, 22, 31),
                        List.of(14, 23, 32),
                        List.of(15, 24, 33));
    }

    @Override
    protected void onConfigLoad(ConfigurationSection config) {
        // Load spin animation settings
        ConfigurationSection settings = config.getConfigurationSection("settings");
        if (settings != null) {
            ConfigurationSection spinAnim = settings.getConfigurationSection("spin_animation");
            if (spinAnim != null) {
                spinDuration = spinAnim.getInt("duration", spinDuration);
                spinSpeed = spinAnim.getInt("speed", spinSpeed);
            }

            // Load display slots
            List<?> rawSlots = settings.getList("display_slots");
            if (rawSlots != null && !rawSlots.isEmpty()) {
                displaySlots = new ArrayList<>();
                for (Object row : rawSlots) {
                    if (row instanceof List<?> list) {
                        List<Integer> slots = new ArrayList<>();
                        for (Object slot : list) {
                            if (slot instanceof Number num) {
                                slots.add(num.intValue());
                            }
                        }
                        displaySlots.add(slots);
                    }
                }
            }
        }

        // Load slot items
        slotItems.clear();
        itemMap.clear();
        rewards.clear();

        ConfigurationSection items = config.getConfigurationSection("items");
        if (items != null) {
            for (String itemId : items.getKeys(false)) {
                ConfigurationSection itemSection = items.getConfigurationSection(itemId);
                if (itemSection == null) continue;

                int chance = itemSection.getInt("chance", 10);

                // Load item stack
                ConfigurationSection itemConfig = itemSection.getConfigurationSection("item");
                ItemStack stack = loadItemStack(itemConfig);

                SlotItem slotItem = new SlotItem(itemId, stack, chance);
                slotItems.addElement(slotItem, chance);
                itemMap.put(itemId, slotItem);

                // Load rewards for this item
                ConfigurationSection rewardsSection =
                        itemSection.getConfigurationSection("rewards");
                if (rewardsSection != null) {
                    for (String countStr : rewardsSection.getKeys(false)) {
                        try {
                            int count = Integer.parseInt(countStr);
                            List<String> commandStrings = rewardsSection.getStringList(countStr);
                            List<GameCommand> commands =
                                    commandStrings.stream().map(GameCommand::fromString).toList();

                            rewards.add(new SlotReward.RowReward(slotItem, count, commands));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }

            // Load equivalents (e.g., star acts as wildcard for other symbols)
            // If item A has equivalents [B, C, D], then A should be added to B's, C's, and D's
            // equivalents
            // This allows A in results to match when checking for B, C, or D rewards
            for (String itemId : items.getKeys(false)) {
                ConfigurationSection itemSection = items.getConfigurationSection(itemId);
                if (itemSection == null) continue;

                List<String> equivalentNames = itemSection.getStringList("equivalents");
                if (!equivalentNames.isEmpty()) {
                    SlotItem wildcardItem = itemMap.get(itemId);
                    if (wildcardItem != null) {
                        // Add this wildcard item to each equivalent's list
                        for (String eqName : equivalentNames) {
                            SlotItem targetItem = itemMap.get(eqName);
                            if (targetItem != null) {
                                targetItem.addEquivalent(wildcardItem);
                            }
                        }
                    }
                }
            }
        }

        // Load pattern rewards
        ConfigurationSection patterns = config.getConfigurationSection("patterns");
        if (patterns != null) {
            for (String patternName : patterns.getKeys(false)) {
                ConfigurationSection patternSection = patterns.getConfigurationSection(patternName);
                if (patternSection == null) continue;

                try {
                    // Parse pattern type
                    String typeStr = patternSection.getString("type", "").toUpperCase();
                    PatternType patternType = PatternType.valueOf(typeStr);

                    // Get required item (optional)
                    String itemId = patternSection.getString("item");
                    SlotItem requiredItem = itemId != null ? itemMap.get(itemId) : null;

                    // Load commands
                    List<String> commandStrings = patternSection.getStringList("commands");
                    List<GameCommand> commands =
                            commandStrings.stream().map(GameCommand::fromString).toList();

                    rewards.add(new SlotReward.PatternReward(patternType, requiredItem, commands));

                    Bukkit.getLogger()
                            .info(
                                    "[SLOTS] Loaded pattern reward: "
                                            + patternName
                                            + " (type: "
                                            + patternType
                                            + ", item: "
                                            + (itemId != null ? itemId : "any")
                                            + ")");
                } catch (IllegalArgumentException e) {
                    Bukkit.getLogger()
                            .warning("[SLOTS] Invalid pattern type in config: " + patternName);
                }
            }
        }

        // Load GUI items (buttons)
        ConfigurationSection buttons = config.getConfigurationSection("gui.buttons");
        if (buttons != null) {
            spinButtonSlots = loadSlots(buttons.getConfigurationSection("spin"));
            betButtonSlots = loadSlots(buttons.getConfigurationSection("bet"));
        }

        // Set default items if none loaded
        if (slotItems.isEmpty()) {
            loadDefaultItems();
        }

        // Initialize the odds calculator after items are loaded
        this.oddsCalculator = new SlotsOddsCalculator(this);

        // Override GUI titles with proper formatting
        // Use the negative space counts from YAML (24 for slots, 14 for bets) for correct alignment
        // Format: §f + (N × ⻔) + customFontChar
        // No trigger character needed - PacketUtils detects these icons automatically
        String negativeSpace = net.serverplugins.arcade.gui.ArcadeFont.NEGATIVE_SPACE;

        // Slots GUI: 24 negative spaces + slots icon
        StringBuilder slotsTitle = new StringBuilder("§f");
        for (int i = 0; i < 24; i++) {
            slotsTitle.append(negativeSpace);
        }
        slotsTitle.append(net.serverplugins.api.ui.ResourcePackIcons.MenuTitles.SLOTS);
        this.guiTitle = slotsTitle.toString();

        // Bet menu: 14 negative spaces + bets icon
        StringBuilder betTitle = new StringBuilder("§f");
        for (int i = 0; i < 14; i++) {
            betTitle.append(negativeSpace);
        }
        betTitle.append(net.serverplugins.api.ui.ResourcePackIcons.MenuTitles.BETS);
        this.betMenuTitle = betTitle.toString();
    }

    private ItemStack loadItemStack(ConfigurationSection config) {
        if (config == null) {
            return new ItemStack(Material.STICK);
        }

        Material material =
                Material.getMaterial(config.getString("material", "STICK").toUpperCase());
        if (material == null) material = Material.STICK;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (config.contains("custom_model_data")) {
                meta.setCustomModelData(config.getInt("custom_model_data"));
            }
            String name = config.getString("name", "§f");
            meta.setDisplayName(name.replace("&", "§"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private int[] loadSlots(ConfigurationSection config) {
        if (config == null) return new int[0];

        Object slotObj = config.get("slot");
        if (slotObj instanceof List<?> list) {
            return list.stream()
                    .filter(o -> o instanceof Number)
                    .mapToInt(o -> ((Number) o).intValue())
                    .toArray();
        } else if (slotObj instanceof Number num) {
            return new int[] {num.intValue()};
        }
        return new int[0];
    }

    private void loadDefaultItems() {
        // Default slot items matching DreamArcade
        addDefaultItem("seven", 501, 6, 10.0);
        addDefaultItem("grapes", 508, 7, 5.0);
        addDefaultItem("melon", 505, 8, 5.0);
        addDefaultItem("plum", 504, 13, 3.0);
        addDefaultItem("lemon", 503, 13, 3.0);
        addDefaultItem("orange", 507, 13, 3.0);
        addDefaultItem("cherry", 502, 30, 2.0);
        addDefaultItem("star", 506, 10, 5.0);
    }

    private void addDefaultItem(String id, int customModelData, int weight, double multiplier) {
        SlotItem item = new SlotItem(id, Material.STICK, customModelData, weight);
        slotItems.addElement(item, weight);
        itemMap.put(id, item);

        // Add default rewards
        rewards.add(
                new SlotReward.RowReward(
                        item,
                        3,
                        List.of(new GameCommand.MoneyCommand("%bet% * " + (int) (multiplier)))));
        rewards.add(
                new SlotReward.RowReward(
                        item,
                        4,
                        List.of(
                                new GameCommand.MoneyCommand(
                                        "%bet% * " + (int) (multiplier * 2)))));
        rewards.add(
                new SlotReward.RowReward(
                        item,
                        5,
                        List.of(
                                new GameCommand.MoneyCommand(
                                        "%bet% * " + (int) (multiplier * 4)))));
    }

    @Override
    public void open(Player player, Machine machine) {
        // Seat the player at the machine
        if (machine != null) {
            machine.seatPlayer(player, 1);
        }

        new SlotsGameGui(this, machine).open(player);
    }

    @Override
    public Machine createMachine(String id, Location location, Direction direction) {
        return new OnePlayerMachine(
                id, this, location, direction, null, System.currentTimeMillis());
    }

    public SlotItem getRandomItem() {
        return slotItems.getRandomElement();
    }

    /** Check rewards using 1D results (backward compatible). */
    public SlotReward checkRewards(SlotItem[] results, int bet) {
        return checkRewards(results, null, bet);
    }

    /**
     * Check rewards using both 1D and 2D patterns.
     *
     * @param results 1D array of middle row results
     * @param grid 2D grid of all visible symbols (can be null)
     * @param bet The bet amount
     * @return The best matching reward, or null
     */
    public SlotReward checkRewards(SlotItem[] results, SlotItem[][] grid, int bet) {
        SlotReward bestReward = null;
        int bestValue = 0;
        boolean debugEnabled =
                ServerArcade.getInstance().getConfig().getBoolean("debug.games", false);

        if (debugEnabled) {
            // Log what we're actually checking
            StringBuilder actualResults = new StringBuilder("[");
            for (int i = 0; i < results.length; i++) {
                actualResults.append(results[i] != null ? results[i].getId() : "null");
                if (i < results.length - 1) actualResults.append(", ");
            }
            actualResults.append("]");
            org.bukkit.Bukkit.getLogger()
                    .info("[Games] Slots checkRewards() - Checking results: " + actualResults);
            org.bukkit.Bukkit.getLogger()
                    .info(
                            "[Games] Slots checkRewards() - Starting reward check with "
                                    + rewards.size()
                                    + " possible rewards");
        }

        for (SlotReward reward : rewards) {
            // IMPORTANT: Clear matched positions before checking
            reward.getMatchedPositions().clear();
            boolean matched = false;

            if (reward instanceof SlotReward.PatternReward patternReward) {
                // 2D pattern matching
                if (grid != null && patternReward.check(grid)) {
                    matched = true;
                    if (debugEnabled) {
                        org.bukkit.Bukkit.getLogger()
                                .info(
                                        "[Games] Slots checkRewards() - Pattern matched: "
                                                + patternReward.getPatternType());
                    }
                }
            } else if (reward instanceof SlotReward.RowReward rowReward) {
                // Count matches for this symbol
                int matches = 0;
                for (SlotItem result : results) {
                    if (rowReward.getItem().matches(result)) {
                        matches++;
                    }
                }

                if (debugEnabled) {
                    org.bukkit.Bukkit.getLogger()
                            .info(
                                    "[Games] Slots checkRewards() - Symbol: "
                                            + rowReward.getItem().getId()
                                            + ", Matches: "
                                            + matches
                                            + "/"
                                            + rowReward.getRequiredCount()
                                            + " (match: "
                                            + (matches >= rowReward.getRequiredCount())
                                            + ")");
                }

                if (reward.check(results)) {
                    matched = true;
                }
            } else if (reward.check(results)) {
                matched = true;
                if (debugEnabled) {
                    org.bukkit.Bukkit.getLogger()
                            .info("[Games] Slots checkRewards() - Exact match reward found!");
                }
            }

            if (matched) {
                int value = reward.getValue(bet);
                if (value > bestValue) {
                    bestValue = value;
                    bestReward = reward;
                    if (debugEnabled) {
                        org.bukkit.Bukkit.getLogger()
                                .info(
                                        "[Games] Slots checkRewards() - New best reward! Value: "
                                                + value);
                    }
                }
            }
        }

        // CRITICAL FIX: Re-check the best reward to populate matchedPositions correctly
        // This ensures the highlighted positions match the actual winning reward
        if (bestReward != null) {
            bestReward.getMatchedPositions().clear();
            if (bestReward instanceof SlotReward.PatternReward patternReward) {
                patternReward.check(grid);
            } else {
                bestReward.check(results);
            }

            if (debugEnabled) {
                org.bukkit.Bukkit.getLogger()
                        .info(
                                "[Games] Slots checkRewards() - Re-checked best reward, matched positions: "
                                        + bestReward.getMatchedPositions().size());
            }
        }

        if (debugEnabled) {
            org.bukkit.Bukkit.getLogger()
                    .info(
                            "[Games] Slots checkRewards() - Final result: "
                                    + (bestReward != null
                                            ? "REWARD FOUND (value: " + bestValue + ")"
                                            : "NO REWARD"));
        }

        return bestReward;
    }

    /** Find a SlotItem by matching its displayed ItemStack. */
    public SlotItem getSlotItemByStack(ItemStack stack) {
        if (stack == null) return null;

        for (SlotItem item : itemMap.values()) {
            ItemStack itemStack = item.getItemStack();
            if (itemStack != null && itemStack.isSimilar(stack)) {
                return item;
            }
        }
        return null;
    }

    // Getters
    public List<List<Integer>> getDisplaySlots() {
        return displaySlots;
    }

    public int getSpinDuration() {
        return spinDuration;
    }

    public int getSpinSpeed() {
        return spinSpeed;
    }

    public int[] getSpinButtonSlots() {
        return spinButtonSlots;
    }

    public int[] getBetButtonSlots() {
        return betButtonSlots;
    }

    public RandomList<SlotItem> getSlotItems() {
        return slotItems;
    }

    public List<SlotReward> getRewards() {
        return rewards;
    }

    public Map<String, SlotItem> getItemMap() {
        return itemMap;
    }

    public SlotsOddsCalculator getOddsCalculator() {
        return oddsCalculator;
    }
}
