package net.serverplugins.admin.punishment.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.admin.punishment.Punishment;
import net.serverplugins.admin.punishment.PunishmentContext;
import net.serverplugins.admin.punishment.PunishmentType;
import net.serverplugins.admin.punishment.ReasonManager;
import net.serverplugins.admin.punishment.ReasonPreset;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ReasonSelectGui extends Gui {

    private final ServerAdmin plugin;
    private final OfflinePlayer target;
    private final PunishmentType selectedType;

    // Pre-loaded offense counts to avoid synchronous DB queries
    private Map<String, Integer> offenseCounts = new HashMap<>();

    public ReasonSelectGui(
            ServerAdmin plugin, Player viewer, OfflinePlayer target, PunishmentType selectedType) {
        super(plugin, viewer, "<dark_red>Select Reason: <red>" + selectedType.getDisplayName(), 54);
        this.plugin = plugin;
        this.target = target;
        this.selectedType = selectedType;
    }

    /** Opens the GUI after loading data asynchronously to avoid blocking the main thread. */
    public static void openAsync(
            ServerAdmin plugin, Player viewer, OfflinePlayer target, PunishmentType selectedType) {
        CompletableFuture.supplyAsync(
                        () -> {
                            ReasonSelectGui gui =
                                    new ReasonSelectGui(plugin, viewer, target, selectedType);
                            try {
                                ReasonManager reasonManager = plugin.getReasonManager();
                                if (reasonManager != null) {
                                    List<ReasonPreset> reasons =
                                            reasonManager.getReasonsForType(selectedType);
                                    for (ReasonPreset preset : reasons) {
                                        if (!preset.getId().equals("other")) {
                                            int count =
                                                    reasonManager.getOffenseCount(
                                                            target.getUniqueId(), preset.getId());
                                            gui.offenseCounts.put(preset.getId(), count);
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                plugin.getLogger()
                                        .warning(
                                                "Failed to load offense counts: " + e.getMessage());
                            }
                            return gui;
                        })
                .thenAccept(
                        gui -> {
                            Bukkit.getScheduler().runTask(plugin, (Runnable) gui::open);
                        });
    }

    @Override
    protected void initializeItems() {
        ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) {
            setItem(i, new GuiItem(filler));
        }

        setupHeader();
        setupReasonItems();
        setupNavigation();
    }

    private void setupHeader() {
        Material headerMaterial =
                switch (selectedType) {
                    case WARN -> Material.PAPER;
                    case MUTE -> Material.BOOK;
                    case KICK -> Material.LEATHER_BOOTS;
                    case BAN -> Material.BARRIER;
                    case FREEZE -> Material.ICE;
                };

        ItemStack header =
                new ItemBuilder(headerMaterial)
                        .name("<gold>Punishing: <yellow>" + target.getName())
                        .lore(
                                "",
                                "<gray>Punishment Type: <white>" + selectedType.getDisplayName(),
                                "",
                                "<gray>Select a reason below")
                        .build();

        setItem(4, new GuiItem(header));
    }

    private void setupReasonItems() {
        ReasonManager reasonManager = plugin.getReasonManager();
        if (reasonManager == null) return;

        List<ReasonPreset> reasons = reasonManager.getReasonsForType(selectedType);

        int[] slots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };

        int slotIndex = 0;
        for (ReasonPreset preset : reasons) {
            if (slotIndex >= slots.length) break;
            if (preset.getId().equals("other")) continue;

            // Use pre-loaded offense count instead of blocking DB call
            int offenseCount = offenseCounts.getOrDefault(preset.getId(), 0);
            ReasonPreset.EscalationLevel nextLevel = preset.getEscalation(offenseCount + 1);

            if (nextLevel == null) continue;

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("<gray>" + preset.getDescription());
            lore.add("");
            lore.add("<white>Current offenses: <yellow>" + offenseCount);

            String nextPunishment;
            if (nextLevel.isPermanent()) {
                nextPunishment = nextLevel.getType().getDisplayName() + " <red>(Permanent)";
            } else if (nextLevel.getDurationMs() != null) {
                nextPunishment =
                        nextLevel.getType().getDisplayName()
                                + " <gray>("
                                + nextLevel.getFormattedDuration()
                                + ")";
            } else {
                nextPunishment = nextLevel.getType().getDisplayName();
            }
            lore.add("<white>Next: <gold>" + nextPunishment);
            lore.add("");
            lore.add("<yellow>Click to apply");

            ItemStack item =
                    new ItemBuilder(Material.PAPER)
                            .name("<white>" + preset.getDisplayName())
                            .lore(lore.toArray(new String[0]))
                            .build();

            final ReasonPreset finalPreset = preset;
            final ReasonPreset.EscalationLevel finalLevel = nextLevel;
            final int newOffenseNumber = offenseCount + 1;

            setItem(
                    slots[slotIndex],
                    GuiItem.withContext(
                            item,
                            ctx -> {
                                viewer.closeInventory();

                                PunishmentContext context =
                                        PunishmentContext.builder()
                                                .target(target)
                                                .staff(viewer)
                                                .reasonId(finalPreset.getId())
                                                .type(finalLevel.getType())
                                                .offenseNumber(newOffenseNumber)
                                                .durationMs(finalLevel.getDurationMs())
                                                .reason(finalPreset.getDisplayName())
                                                .build();

                                plugin.getPunishmentManager()
                                        .punish(context)
                                        .thenAccept(
                                                result -> {
                                                    Bukkit.getScheduler()
                                                            .runTask(
                                                                    plugin,
                                                                    () -> {
                                                                        if (result.isSuccess()) {
                                                                            Punishment p =
                                                                                    result
                                                                                            .getPunishment();
                                                                            String duration =
                                                                                    p
                                                                                                            .getDurationMs()
                                                                                                    != null
                                                                                            ? " for "
                                                                                                    + p
                                                                                                            .getFormattedDuration()
                                                                                            : "";
                                                                            String msg =
                                                                                    "<green>Applied <white>"
                                                                                            + p.getType()
                                                                                                    .getDisplayName()
                                                                                            + "<green> to <white>"
                                                                                            + target
                                                                                                    .getName()
                                                                                            + duration
                                                                                            + " <gray>("
                                                                                            + finalPreset
                                                                                                    .getDisplayName()
                                                                                            + " #"
                                                                                            + newOffenseNumber
                                                                                            + ")";
                                                                            viewer.sendMessage(
                                                                                    TextUtil.parse(
                                                                                            msg));
                                                                        } else {
                                                                            viewer.sendMessage(
                                                                                    TextUtil.parse(
                                                                                            "<red>Failed to apply punishment: "
                                                                                                    + result
                                                                                                            .getError()));
                                                                        }
                                                                    });
                                                });
                            }));

            slotIndex++;
        }

        setupCustomReasonItem(slots, slotIndex);
    }

    private void setupCustomReasonItem(int[] slots, int slotIndex) {
        if (slotIndex >= slots.length) return;

        ReasonManager reasonManager = plugin.getReasonManager();
        ReasonPreset otherPreset = reasonManager != null ? reasonManager.getReason("other") : null;

        ItemStack customItem =
                new ItemBuilder(Material.WRITABLE_BOOK)
                        .name("<light_purple>Custom Reason")
                        .lore(
                                "",
                                "<gray>Enter a custom reason",
                                "",
                                "<yellow>Click to enter reason in chat")
                        .build();

        setItem(
                slots[slotIndex],
                GuiItem.withContext(
                        customItem,
                        ctx -> {
                            viewer.closeInventory();
                            viewer.sendMessage(
                                    TextUtil.parse(
                                            "<yellow>Enter your custom reason in chat <gray>(or type 'cancel'):"));

                            plugin.getPunishmentManager()
                                    .awaitCustomReason(viewer, target, selectedType);
                        }));
    }

    private void setupNavigation() {
        ItemStack backItem =
                new ItemBuilder(Material.ARROW)
                        .name("<gray>Back")
                        .lore("", "<gray>Return to punishment menu")
                        .build();

        setItem(
                45,
                new GuiItem(
                        backItem,
                        e -> {
                            PunishGui.openAsync(plugin, viewer, target);
                        }));

        ItemStack closeItem = new ItemBuilder(Material.BARRIER).name("<red>Close").build();

        setItem(53, new GuiItem(closeItem, e -> viewer.closeInventory()));
    }
}
