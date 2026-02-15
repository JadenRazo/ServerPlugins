package net.serverplugins.afk.gui;

import net.serverplugins.afk.ServerAFK;
import net.serverplugins.afk.commands.AfkCommand;
import net.serverplugins.afk.models.AfkZone;
import net.serverplugins.afk.models.ZoneReward;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ItemPickerGui extends Gui {

    private final ServerAFK plugin;
    private final AfkCommand command;
    private final AfkZone zone;
    private final ZoneReward existingReward;

    // Common reward items
    private static final Material[] COMMON_ITEMS = {
        Material.DIAMOND,
        Material.EMERALD,
        Material.GOLD_INGOT,
        Material.IRON_INGOT,
        Material.NETHERITE_INGOT,
        Material.LAPIS_LAZULI,
        Material.REDSTONE,
        Material.COAL,
        Material.GOLDEN_APPLE,
        Material.ENCHANTED_GOLDEN_APPLE,
        Material.EXPERIENCE_BOTTLE,
        Material.DIAMOND_PICKAXE,
        Material.DIAMOND_SWORD,
        Material.DIAMOND_AXE,
        Material.ELYTRA,
        Material.TOTEM_OF_UNDYING,
        Material.NETHER_STAR,
        Material.BEACON,
        Material.SHULKER_BOX,
        Material.ENDER_PEARL,
        Material.BLAZE_ROD,
        Material.PHANTOM_MEMBRANE
    };

    public ItemPickerGui(ServerAFK plugin, Player player, AfkCommand command, AfkZone zone) {
        this(plugin, player, command, zone, null);
    }

    public ItemPickerGui(
            ServerAFK plugin,
            Player player,
            AfkCommand command,
            AfkZone zone,
            ZoneReward existingReward) {
        super(plugin, player, "Choose Reward Item", 54);
        this.plugin = plugin;
        this.command = command;
        this.zone = zone;
        this.existingReward = existingReward;
    }

    @Override
    protected void initializeItems() {
        // Fill border
        ItemStack glass = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        fillBorder(new GuiItem(glass));

        // Title/info
        ItemStack infoItem =
                new ItemBuilder(Material.CHEST)
                        .name("<gold>Select Reward Item")
                        .lore(
                                "",
                                "<gray>Choose an item from the",
                                "<gray>common items below, or click",
                                "<gray>the hand to use held item.",
                                "",
                                existingReward != null
                                        ? "<yellow>Editing existing reward"
                                        : "<green>Adding new item reward")
                        .build();
        setItem(4, new GuiItem(infoItem));

        // Use held item - slot 49
        ItemStack handItem = viewer.getInventory().getItemInMainHand();
        boolean hasHeldItem = handItem.getType() != Material.AIR;

        ItemStack useHeldItem =
                new ItemBuilder(hasHeldItem ? handItem.getType() : Material.BARRIER)
                        .name(hasHeldItem ? "<green>Use Held Item" : "<red>No Item in Hand")
                        .lore(
                                "",
                                hasHeldItem
                                        ? "<gray>Use: <white>"
                                                + handItem.getType()
                                                        .name()
                                                        .toLowerCase()
                                                        .replace("_", " ")
                                        : "<gray>Hold an item in your hand",
                                "",
                                hasHeldItem
                                        ? "<yellow>Click to use this item"
                                        : "<red>Cannot use - nothing held")
                        .build();
        setItem(
                49,
                new GuiItem(
                        useHeldItem,
                        e -> {
                            if (!hasHeldItem) {
                                TextUtil.send(
                                        viewer,
                                        plugin.getAfkConfig().getPrefix()
                                                + "<red>Hold an item in your hand first!");
                                return;
                            }

                            ItemStack rewardItem = handItem.clone();
                            rewardItem.setAmount(1);
                            selectItem(rewardItem);
                        }));

        // Common items in slots 10-16, 19-25, 28-34, 37-43
        int[] slots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };

        for (int i = 0; i < slots.length && i < COMMON_ITEMS.length; i++) {
            Material material = COMMON_ITEMS[i];
            String displayName = material.name().toLowerCase().replace("_", " ");

            ItemStack item =
                    new ItemBuilder(material)
                            .name("<yellow>" + displayName)
                            .lore("", "<gray>Click to select as reward")
                            .build();

            setItem(
                    slots[i],
                    new GuiItem(
                            item,
                            e -> {
                                selectItem(new ItemStack(material, 1));
                            }));
        }

        // Back button - slot 45
        ItemStack backItem =
                new ItemBuilder(Material.ARROW)
                        .name("<red>Back")
                        .lore("<gray>Return to reward editor")
                        .build();
        setItem(
                45,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            new RewardEditorGui(plugin, viewer, command, zone).open();
                        }));
    }

    private void selectItem(ItemStack item) {
        viewer.closeInventory();
        TextUtil.send(
                viewer,
                plugin.getAfkConfig().getPrefix() + "<yellow>Enter the amount to give per reward:");

        new ChatInputHandler(
                plugin,
                viewer,
                input -> {
                    try {
                        int amount = Integer.parseInt(input);
                        if (amount <= 0 || amount > 64) {
                            TextUtil.send(
                                    viewer,
                                    plugin.getAfkConfig().getPrefix()
                                            + "<red>Amount must be between 1 and 64!");
                            new ItemPickerGui(plugin, viewer, command, zone, existingReward).open();
                            return;
                        }

                        item.setAmount(amount);

                        if (existingReward != null) {
                            // Update existing reward
                            existingReward.setItemReward(item);
                            plugin.getZoneManager().updateReward(existingReward);
                            TextUtil.send(
                                    viewer,
                                    plugin.getAfkConfig().getPrefix() + "<green>Reward updated!");
                        } else {
                            // Create new reward
                            ZoneReward reward = ZoneReward.item(item);
                            plugin.getZoneManager().addReward(zone, reward);
                            TextUtil.send(
                                    viewer,
                                    plugin.getAfkConfig().getPrefix()
                                            + "<green>Item reward added!");
                        }
                    } catch (NumberFormatException ex) {
                        TextUtil.send(
                                viewer, plugin.getAfkConfig().getPrefix() + "<red>Invalid number!");
                    }
                    new RewardEditorGui(plugin, viewer, command, zone).open();
                });
    }
}
