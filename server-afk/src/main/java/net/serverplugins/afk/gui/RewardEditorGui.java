package net.serverplugins.afk.gui;

import java.util.ArrayList;
import java.util.List;
import net.serverplugins.afk.ServerAFK;
import net.serverplugins.afk.commands.AfkCommand;
import net.serverplugins.afk.models.AfkZone;
import net.serverplugins.afk.models.ZoneReward;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiClickContext;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class RewardEditorGui extends Gui {

    private final ServerAFK plugin;
    private final AfkCommand command;
    private final AfkZone zone;

    public RewardEditorGui(ServerAFK plugin, Player player, AfkCommand command, AfkZone zone) {
        super(plugin, player, "Rewards: " + zone.getName(), 54);
        this.plugin = plugin;
        this.command = command;
        this.zone = zone;
    }

    @Override
    protected void initializeItems() {
        // Fill with glass
        ItemStack glass = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        fillEmpty(new GuiItem(glass));

        // Title
        ItemStack titleItem =
                new ItemBuilder(Material.GOLD_INGOT)
                        .name("<gold>Zone Rewards")
                        .lore(
                                "",
                                "<gray>Zone: <white>" + zone.getName(),
                                "<gray>Interval: <yellow>" + zone.getTimeIntervalSeconds() + "s",
                                "",
                                "<gray>Players receive these rewards",
                                "<gray>every " + zone.getTimeIntervalSeconds() + " seconds.")
                        .build();
        setItem(4, new GuiItem(titleItem));

        // Add Reward Buttons
        setupAddRewardButtons();

        // Display existing rewards
        displayRewards();

        // Back button
        ItemStack backItem =
                new ItemBuilder(Material.ARROW)
                        .name("<red>Back")
                        .lore("<gray>Return to zone editor")
                        .build();
        setItem(
                49,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            new ZoneEditorGui(plugin, viewer, command, zone).open();
                        }));
    }

    private void setupAddRewardButtons() {
        // Currency reward (slot 10)
        ItemStack currencyItem =
                new ItemBuilder(Material.GOLD_NUGGET)
                        .name("<yellow>Add Currency Reward")
                        .lore(
                                "",
                                "<gray>Give players money every interval",
                                "",
                                "<green>Click to create")
                        .build();
        setItem(10, new GuiItem(currencyItem, e -> createCurrencyReward()));

        // Item reward (slot 11)
        ItemStack itemRewardItem =
                new ItemBuilder(Material.DIAMOND)
                        .name("<aqua>Add Item Reward")
                        .lore(
                                "",
                                "<gray>Give players items every interval",
                                "",
                                "<green>Click to create")
                        .build();
        setItem(11, new GuiItem(itemRewardItem, e -> createItemReward()));

        // Command reward (slot 12)
        ItemStack commandItem =
                new ItemBuilder(Material.COMMAND_BLOCK)
                        .name("<light_purple>Add Command Reward")
                        .lore(
                                "",
                                "<gray>Execute console commands",
                                "<gray>Use %player% for player name",
                                "<gray>Use %uuid% for player UUID",
                                "",
                                "<green>Click to create")
                        .build();
        setItem(12, new GuiItem(commandItem, e -> createCommandReward()));

        // XP reward (slot 13)
        ItemStack xpItem =
                new ItemBuilder(Material.EXPERIENCE_BOTTLE)
                        .name("<green>Add XP Reward")
                        .lore(
                                "",
                                "<gray>Give players experience points",
                                "",
                                "<green>Click to create")
                        .build();
        setItem(13, new GuiItem(xpItem, e -> createXpReward()));
    }

    private void displayRewards() {
        // Display rewards in slots 19-43 (bottom 3 rows, excluding edges)
        int[] slots = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };

        List<ZoneReward> rewards = zone.getRewards();

        for (int i = 0; i < slots.length && i < rewards.size(); i++) {
            ZoneReward reward = rewards.get(i);
            setItem(slots[i], createRewardItem(reward));
        }
    }

    private GuiItem createRewardItem(ZoneReward reward) {
        Material material;
        String name;
        List<String> lore = new ArrayList<>();

        switch (reward.getType()) {
            case CURRENCY:
                material = Material.GOLD_NUGGET;
                name = "<gold>" + (int) reward.getCurrencyAmount() + " Coins";
                lore.add("");
                lore.add("<gray>Type: <yellow>Currency");
                lore.add("<gray>Amount: <gold>" + (int) reward.getCurrencyAmount());
                break;

            case ITEM:
                material =
                        reward.getItemReward() != null
                                ? reward.getItemReward().getType()
                                : Material.STONE;
                name = "<aqua>" + reward.getDisplayName();
                lore.add("");
                lore.add("<gray>Type: <aqua>Item");
                if (reward.getItemReward() != null) {
                    lore.add("<gray>Amount: <white>" + reward.getItemReward().getAmount());
                }
                break;

            case COMMAND:
                material = Material.COMMAND_BLOCK;
                name = "<light_purple>Command Reward";
                lore.add("");
                lore.add("<gray>Type: <light_purple>Command");
                lore.add(
                        "<gray>Command: <white>"
                                + (reward.getCommandData() != null
                                        ? reward.getCommandData()
                                        : "None"));
                break;

            case XP:
                material = Material.EXPERIENCE_BOTTLE;
                name = "<green>" + reward.getXpAmount() + " XP";
                lore.add("");
                lore.add("<gray>Type: <green>Experience");
                lore.add("<gray>Amount: <yellow>" + reward.getXpAmount() + " XP");
                break;

            default:
                material = Material.BARRIER;
                name = "<red>Unknown Reward";
                lore.add("");
                lore.add("<red>Error: Unknown type");
        }

        // Add chance percentage
        if (reward.getChancePercent() < 100.0) {
            lore.add(
                    "<gray>Chance: <yellow>"
                            + String.format("%.1f", reward.getChancePercent())
                            + "%");
        } else {
            lore.add("<gray>Chance: <green>100%");
        }

        lore.add("");
        lore.add("<green>Left-click <gray>to edit");
        lore.add("<yellow>Shift-click <gray>to edit chance");
        lore.add("<red>Right-click <gray>to remove");

        ItemStack item =
                new ItemBuilder(material).name(name).lore(lore.toArray(new String[0])).build();

        return GuiItem.withContext(item, ctx -> handleRewardClick(ctx, reward));
    }

    private void handleRewardClick(GuiClickContext ctx, ZoneReward reward) {
        if (ctx.isRightClick()) {
            // Remove reward
            plugin.getZoneManager().removeReward(zone, reward);
            TextUtil.send(viewer, plugin.getAfkConfig().getPrefix() + "<red>Reward removed!");
            viewer.closeInventory();
            new RewardEditorGui(plugin, viewer, command, zone).open();

        } else if (ctx.isShiftClick() && ctx.isLeftClick()) {
            // Edit chance
            editRewardChance(reward);

        } else if (ctx.isLeftClick()) {
            // Edit reward based on type
            editReward(reward);
        }
    }

    // Create reward methods
    private void createCurrencyReward() {
        viewer.closeInventory();
        TextUtil.send(viewer, plugin.getAfkConfig().getPrefix() + "<yellow>Enter the coin amount:");

        new ChatInputHandler(
                plugin,
                viewer,
                input -> {
                    try {
                        double amount = Double.parseDouble(input);
                        if (amount <= 0) {
                            TextUtil.send(
                                    viewer,
                                    plugin.getAfkConfig().getPrefix()
                                            + "<red>Amount must be positive!");
                        } else {
                            ZoneReward reward = ZoneReward.currency(amount);
                            plugin.getZoneManager().addReward(zone, reward);
                            TextUtil.send(
                                    viewer,
                                    plugin.getAfkConfig().getPrefix()
                                            + "<green>Added "
                                            + (int) amount
                                            + " coins reward!");
                        }
                    } catch (NumberFormatException ex) {
                        TextUtil.send(
                                viewer, plugin.getAfkConfig().getPrefix() + "<red>Invalid number!");
                    }
                    new RewardEditorGui(plugin, viewer, command, zone).open();
                });
    }

    private void createItemReward() {
        viewer.closeInventory();
        new ItemPickerGui(plugin, viewer, command, zone).open();
    }

    private void createCommandReward() {
        viewer.closeInventory();
        TextUtil.send(
                viewer,
                plugin.getAfkConfig().getPrefix() + "<yellow>Enter the command (without /):");
        TextUtil.send(viewer, "<gray>Use %player% for player name, %uuid% for player UUID");

        new ChatInputHandler(
                plugin,
                viewer,
                input -> {
                    if (input.isEmpty()) {
                        TextUtil.send(
                                viewer,
                                plugin.getAfkConfig().getPrefix()
                                        + "<red>Command cannot be empty!");
                    } else {
                        ZoneReward reward = ZoneReward.command(input);
                        plugin.getZoneManager().addReward(zone, reward);
                        TextUtil.send(
                                viewer,
                                plugin.getAfkConfig().getPrefix() + "<green>Added command reward!");
                    }
                    new RewardEditorGui(plugin, viewer, command, zone).open();
                });
    }

    private void createXpReward() {
        viewer.closeInventory();
        TextUtil.send(viewer, plugin.getAfkConfig().getPrefix() + "<yellow>Enter the XP amount:");

        new ChatInputHandler(
                plugin,
                viewer,
                input -> {
                    try {
                        int amount = Integer.parseInt(input);
                        if (amount <= 0) {
                            TextUtil.send(
                                    viewer,
                                    plugin.getAfkConfig().getPrefix()
                                            + "<red>Amount must be positive!");
                        } else {
                            ZoneReward reward = ZoneReward.xp(amount);
                            plugin.getZoneManager().addReward(zone, reward);
                            TextUtil.send(
                                    viewer,
                                    plugin.getAfkConfig().getPrefix()
                                            + "<green>Added "
                                            + amount
                                            + " XP reward!");
                        }
                    } catch (NumberFormatException ex) {
                        TextUtil.send(
                                viewer, plugin.getAfkConfig().getPrefix() + "<red>Invalid number!");
                    }
                    new RewardEditorGui(plugin, viewer, command, zone).open();
                });
    }

    // Edit reward methods
    private void editReward(ZoneReward reward) {
        viewer.closeInventory();

        switch (reward.getType()) {
            case CURRENCY -> editCurrencyAmount(reward);
            case ITEM -> editItemReward(reward);
            case COMMAND -> editCommandReward(reward);
            case XP -> editXpAmount(reward);
        }
    }

    private void editCurrencyAmount(ZoneReward reward) {
        TextUtil.send(
                viewer, plugin.getAfkConfig().getPrefix() + "<yellow>Enter the new coin amount:");

        new ChatInputHandler(
                plugin,
                viewer,
                input -> {
                    try {
                        double amount = Double.parseDouble(input);
                        if (amount <= 0) {
                            TextUtil.send(
                                    viewer,
                                    plugin.getAfkConfig().getPrefix()
                                            + "<red>Amount must be positive!");
                        } else {
                            reward.setCurrencyAmount(amount);
                            plugin.getZoneManager().updateReward(reward);
                            TextUtil.send(
                                    viewer,
                                    plugin.getAfkConfig().getPrefix()
                                            + "<green>Updated to "
                                            + (int) amount
                                            + " coins!");
                        }
                    } catch (NumberFormatException ex) {
                        TextUtil.send(
                                viewer, plugin.getAfkConfig().getPrefix() + "<red>Invalid number!");
                    }
                    new RewardEditorGui(plugin, viewer, command, zone).open();
                });
    }

    private void editItemReward(ZoneReward reward) {
        new ItemPickerGui(plugin, viewer, command, zone, reward).open();
    }

    private void editCommandReward(ZoneReward reward) {
        TextUtil.send(
                viewer,
                plugin.getAfkConfig().getPrefix() + "<yellow>Enter the new command (without /):");
        TextUtil.send(viewer, "<gray>Use %player% for player name, %uuid% for player UUID");

        new ChatInputHandler(
                plugin,
                viewer,
                input -> {
                    if (input.isEmpty()) {
                        TextUtil.send(
                                viewer,
                                plugin.getAfkConfig().getPrefix()
                                        + "<red>Command cannot be empty!");
                    } else {
                        reward.setCommandData(input);
                        plugin.getZoneManager().updateReward(reward);
                        TextUtil.send(
                                viewer,
                                plugin.getAfkConfig().getPrefix() + "<green>Command updated!");
                    }
                    new RewardEditorGui(plugin, viewer, command, zone).open();
                });
    }

    private void editXpAmount(ZoneReward reward) {
        TextUtil.send(
                viewer, plugin.getAfkConfig().getPrefix() + "<yellow>Enter the new XP amount:");

        new ChatInputHandler(
                plugin,
                viewer,
                input -> {
                    try {
                        int amount = Integer.parseInt(input);
                        if (amount <= 0) {
                            TextUtil.send(
                                    viewer,
                                    plugin.getAfkConfig().getPrefix()
                                            + "<red>Amount must be positive!");
                        } else {
                            reward.setXpAmount(amount);
                            plugin.getZoneManager().updateReward(reward);
                            TextUtil.send(
                                    viewer,
                                    plugin.getAfkConfig().getPrefix()
                                            + "<green>Updated to "
                                            + amount
                                            + " XP!");
                        }
                    } catch (NumberFormatException ex) {
                        TextUtil.send(
                                viewer, plugin.getAfkConfig().getPrefix() + "<red>Invalid number!");
                    }
                    new RewardEditorGui(plugin, viewer, command, zone).open();
                });
    }

    private void editRewardChance(ZoneReward reward) {
        TextUtil.send(
                viewer,
                plugin.getAfkConfig().getPrefix() + "<yellow>Enter chance percentage (0-100):");
        TextUtil.send(
                viewer, "<gray>Current: " + String.format("%.1f", reward.getChancePercent()) + "%");

        new ChatInputHandler(
                plugin,
                viewer,
                input -> {
                    try {
                        double chance = Double.parseDouble(input);
                        if (chance < 0 || chance > 100) {
                            TextUtil.send(
                                    viewer,
                                    plugin.getAfkConfig().getPrefix()
                                            + "<red>Chance must be between 0 and 100!");
                        } else {
                            reward.setChancePercent(chance);
                            plugin.getZoneManager().updateReward(reward);
                            TextUtil.send(
                                    viewer,
                                    plugin.getAfkConfig().getPrefix()
                                            + "<green>Chance updated to "
                                            + String.format("%.1f", chance)
                                            + "%!");
                        }
                    } catch (NumberFormatException ex) {
                        TextUtil.send(
                                viewer, plugin.getAfkConfig().getPrefix() + "<red>Invalid number!");
                    }
                    new RewardEditorGui(plugin, viewer, command, zone).open();
                });
    }
}
