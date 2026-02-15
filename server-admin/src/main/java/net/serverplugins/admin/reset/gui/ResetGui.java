package net.serverplugins.admin.reset.gui;

import java.util.*;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.admin.reset.ResetType;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public class ResetGui extends Gui {

    private final ServerAdmin plugin;
    private final OfflinePlayer target;
    private final Set<ResetType> confirmationPending = new HashSet<>();

    public ResetGui(ServerAdmin plugin, Player viewer, OfflinePlayer target) {
        super(plugin, viewer, "<dark_red>Reset: <red>" + target.getName(), 27);
        this.plugin = plugin;
        this.target = target;
    }

    @Override
    protected void initializeItems() {
        ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 27; i++) {
            setItem(i, new GuiItem(filler));
        }

        setupPlayerInfo();
        setupResetOptions();
        setupNavigation();
    }

    private void setupPlayerInfo() {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(target);
            meta.displayName(TextUtil.parse("<yellow>" + target.getName()));

            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse(""));
            lore.add(
                    TextUtil.parse(
                            "<gray>Status: "
                                    + (target.isOnline() ? "<green>Online" : "<red>Offline")));
            lore.add(TextUtil.parse(""));
            lore.add(TextUtil.parse("<red><bold>WARNING: Resets are permanent!"));

            meta.lore(lore);
            head.setItemMeta(meta);
        }
        setItem(4, new GuiItem(head));
    }

    private void setupResetOptions() {
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        ResetType[] types = {
            ResetType.CLAIMS,
            ResetType.ECONOMY,
            ResetType.PLAYTIME,
            ResetType.RANK,
            ResetType.PUNISHMENTS,
            ResetType.ALL
        };

        for (int i = 0; i < Math.min(types.length, slots.length); i++) {
            ResetType type = types[i];
            int slot = slots[i];

            if (!viewer.hasPermission(type.getPermission())) {
                ItemStack locked =
                        new ItemBuilder(Material.GRAY_DYE)
                                .name("<gray>" + type.getDisplayName())
                                .lore("", "<red>No permission")
                                .build();
                setItem(slot, new GuiItem(locked));
                continue;
            }

            boolean pendingConfirm = confirmationPending.contains(type);

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("<gray>" + type.getDescription());
            lore.add("");

            if (pendingConfirm) {
                lore.add("<red><bold>CLICK AGAIN TO CONFIRM!");
                lore.add("<gray>This action cannot be undone.");
            } else {
                lore.add("<yellow>Click to reset");
            }

            Material icon = pendingConfirm ? Material.RED_WOOL : type.getIcon();

            ItemStack item =
                    new ItemBuilder(icon)
                            .name(
                                    (pendingConfirm ? "<red><bold>" : "<gold>")
                                            + type.getDisplayName())
                            .lore(lore.toArray(new String[0]))
                            .glow(pendingConfirm)
                            .build();

            final ResetType finalType = type;
            setItem(
                    slot,
                    GuiItem.withContext(
                            item,
                            ctx -> {
                                if (confirmationPending.contains(finalType)) {
                                    confirmationPending.remove(finalType);
                                    executeReset(finalType);
                                } else {
                                    confirmationPending.add(finalType);
                                    initializeItems();

                                    Bukkit.getScheduler()
                                            .runTaskLater(
                                                    plugin,
                                                    () -> {
                                                        if (confirmationPending.contains(
                                                                finalType)) {
                                                            confirmationPending.remove(finalType);
                                                            if (viewer.getOpenInventory()
                                                                    .getTopInventory()
                                                                    .equals(getInventory())) {
                                                                initializeItems();
                                                            }
                                                        }
                                                    },
                                                    100L);
                                }
                            }));
        }
    }

    private void executeReset(ResetType type) {
        viewer.closeInventory();
        viewer.sendMessage(
                TextUtil.parse(
                        "<yellow>Resetting "
                                + type.getDisplayName()
                                + " for "
                                + target.getName()
                                + "..."));

        plugin.getResetManager()
                .reset(target.getUniqueId(), target.getName(), type, viewer)
                .thenAccept(
                        result -> {
                            Bukkit.getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                if (result.isSuccess()) {
                                                    viewer.sendMessage(
                                                            TextUtil.parse(
                                                                    "<green>Successfully reset "
                                                                            + type.getDisplayName()
                                                                            + " for "
                                                                            + target.getName()
                                                                            + ": "
                                                                            + result.getDetails()));
                                                } else {
                                                    viewer.sendMessage(
                                                            TextUtil.parse(
                                                                    "<red>Failed to reset: "
                                                                            + result.getError()));
                                                }
                                            });
                        });
    }

    private void setupNavigation() {
        ItemStack closeItem = new ItemBuilder(Material.BARRIER).name("<red>Close").build();
        setItem(22, new GuiItem(closeItem, e -> viewer.closeInventory()));
    }
}
