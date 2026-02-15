package net.serverplugins.claim.gui;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.Nation;
import net.serverplugins.claim.models.War;
import net.serverplugins.claim.models.WarShield;
import net.serverplugins.claim.models.WarTribute;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class WarMenuGui extends Gui {

    private final ServerClaim plugin;
    private final Nation nation;
    private final Claim playerClaim;

    public WarMenuGui(ServerClaim plugin, Player player, Nation nation, Claim playerClaim) {
        super(
                plugin,
                player,
                "<dark_red>War Room - " + (nation != null ? nation.getName() : "Unknown"),
                54);
        this.plugin = plugin;

        // Validate nation exists
        if (!GuiValidator.validateNation(plugin, player, nation, "War Menu")) {
            this.nation = null;
            this.playerClaim = playerClaim;
            return;
        }

        this.nation = nation;
        this.playerClaim = playerClaim;
    }

    @Override
    protected void initializeItems() {
        // Early return if nation is null
        if (nation == null) {
            plugin.getLogger().warning("WarMenuGui: Nation is null, cannot initialize");
            return;
        }

        // Fill background
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) {
            setItem(i, new GuiItem(filler));
        }

        List<War> activeWars = null;
        try {
            activeWars = plugin.getWarManager().getActiveWarsForNation(nation.getId());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get active wars: " + e.getMessage());
            activeWars = new ArrayList<>();
        }

        boolean isLeader = nation.isLeader(viewer.getUniqueId());

        // War status (slot 4)
        Material statusMat = activeWars.isEmpty() ? Material.WHITE_BANNER : Material.RED_BANNER;
        String statusName = activeWars.isEmpty() ? "<green>At Peace" : "<dark_red>At War!";

        ItemStack statusItem =
                new ItemBuilder(statusMat)
                        .name(statusName)
                        .lore(
                                "",
                                activeWars.isEmpty()
                                        ? "<gray>Your nation is at peace."
                                        : "<gray>Active conflicts: <red>" + activeWars.size())
                        .glow(!activeWars.isEmpty())
                        .build();
        setItem(4, new GuiItem(statusItem));

        // Display active wars
        if (activeWars != null && !activeWars.isEmpty()) {
            int slot = 19;
            for (War war : activeWars) {
                if (war == null) continue; // Skip null wars
                if (slot > 25) break;

                GuiItem warItem = createWarItem(war);
                if (warItem != null) {
                    setItem(slot++, warItem);
                }
            }
        } else {
            ItemStack noWarsItem =
                    new ItemBuilder(Material.SUNFLOWER)
                            .name("<yellow>No Active Wars")
                            .lore(
                                    "",
                                    "<gray>Your nation is currently",
                                    "<gray>not engaged in any wars.")
                            .build();
            setItem(22, new GuiItem(noWarsItem));
        }

        // Declare War (slot 29) - Leader only
        if (isLeader) {
            ItemStack declareItem =
                    new ItemBuilder(Material.IRON_SWORD)
                            .name("<red>Declare War")
                            .lore(
                                    "",
                                    "<gray>Declare war on another",
                                    "<gray>nation to capture their",
                                    "<gray>territory.",
                                    "",
                                    "<yellow>Click to select target")
                            .build();
            setItem(
                    29,
                    new GuiItem(
                            declareItem,
                            e -> {
                                viewer.closeInventory();
                                new WarDeclareGui(plugin, viewer, nation, playerClaim).open();
                            }));
        } else {
            ItemStack leaderOnlyItem =
                    new ItemBuilder(Material.BARRIER)
                            .name("<gray>Declare War")
                            .lore("", "<red>Only the nation leader", "<red>can declare war.")
                            .build();
            setItem(29, new GuiItem(leaderOnlyItem));
        }

        // War Shield (slot 31)
        WarShield shield = null;
        try {
            shield = plugin.getWarManager().getActiveShield(nation.getId());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get war shield: " + e.getMessage());
        }

        Material shieldMat =
                shield != null && !shield.isExpired() ? Material.SHIELD : Material.GRAY_DYE;
        String shieldName =
                shield != null && !shield.isExpired()
                        ? "<green>War Shield Active"
                        : "<gray>No War Shield";

        List<String> shieldLore = new ArrayList<>();
        shieldLore.add("");
        if (shield != null && !shield.isExpired()) {
            String timeRemaining = shield.getFormattedTimeRemaining();
            if (timeRemaining != null) {
                shieldLore.add("<gray>Time Remaining: <green>" + timeRemaining);
            }
            shieldLore.add("");
            shieldLore.add("<gray>Your nation cannot be");
            shieldLore.add("<gray>declared war upon.");
            if (shield.getReason() != null && !shield.getReason().isEmpty()) {
                shieldLore.add("");
                shieldLore.add("<dark_gray>Reason: " + shield.getReason());
            }
        } else {
            shieldLore.add("<gray>No active protection.");
            shieldLore.add("<gray>Your nation can be");
            shieldLore.add("<gray>declared war upon.");
        }

        ItemStack shieldItem =
                new ItemBuilder(shieldMat)
                        .name(shieldName)
                        .lore(shieldLore.toArray(new String[0]))
                        .build();
        setItem(31, new GuiItem(shieldItem));

        // Tribute/Surrender (slot 33) - Only during war
        if (!activeWars.isEmpty() && isLeader) {
            final List<War> finalActiveWars = activeWars;
            final Claim finalPlayerClaim = playerClaim;
            ItemStack tributeItem =
                    new ItemBuilder(Material.GOLD_INGOT)
                            .name("<yellow>Negotiate Peace")
                            .lore(
                                    "",
                                    "<gray>Offer tribute or surrender",
                                    "<gray>to end the current war.",
                                    "",
                                    "<yellow>Click to negotiate")
                            .build();
            setItem(
                    33,
                    new GuiItem(
                            tributeItem,
                            e -> {
                                if (!finalActiveWars.isEmpty()) {
                                    viewer.closeInventory();
                                    new WarTributeGui(
                                                    plugin,
                                                    viewer,
                                                    nation,
                                                    finalActiveWars.get(0),
                                                    finalPlayerClaim)
                                            .open();
                                }
                            }));
        }

        // Back button (slot 49)
        ItemStack backItem =
                new ItemBuilder(Material.ARROW)
                        .name("<gray>Back")
                        .lore("<gray>Return to nation menu")
                        .build();
        setItem(
                49,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            new NationMenuGui(plugin, viewer, nation, playerClaim).open();
                        }));

        // Close (slot 53)
        ItemStack closeItem = new ItemBuilder(Material.BARRIER).name("<red>Close").build();
        setItem(53, new GuiItem(closeItem, e -> viewer.closeInventory()));
    }

    private GuiItem createWarItem(War war) {
        if (war == null) {
            plugin.getLogger().warning("Cannot create war item for null war");
            return null;
        }

        boolean isAttacker = war.isAttacker(nation.getId());
        Integer opponentId = isAttacker ? war.getDefenderNationId() : war.getAttackerNationId();

        Nation opponent = null;
        try {
            opponent = opponentId != null ? plugin.getNationManager().getNation(opponentId) : null;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get opponent nation: " + e.getMessage());
        }

        String opponentName =
                opponent != null && opponent.getName() != null ? opponent.getName() : "Unknown";

        Material mat = isAttacker ? Material.IRON_SWORD : Material.SHIELD;
        String role = isAttacker ? "<red>Attacking" : "<blue>Defending";

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<gray>Against: <white>" + opponentName);
        lore.add("<gray>Role: " + role);
        lore.add("<gray>State: " + getStateDisplay(war));

        if (war.isDeclared()) {
            Duration until =
                    Duration.between(Instant.now(), war.getDeclaredAt().plus(Duration.ofHours(24)));
            lore.add("");
            lore.add(
                    "<yellow>Combat begins in: "
                            + until.toHours()
                            + "h "
                            + (until.toMinutes() % 60)
                            + "m");
        }

        if (war.getDeclarationReason() != null && !war.getDeclarationReason().isEmpty()) {
            lore.add("");
            lore.add("<dark_gray>Reason: " + war.getDeclarationReason());
        }

        // Check for pending tributes
        List<WarTribute> tributes = null;
        try {
            tributes = plugin.getWarManager().getPendingTributes(war.getId());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get pending tributes: " + e.getMessage());
        }

        if (tributes != null && !tributes.isEmpty()) {
            lore.add("");
            lore.add("<yellow>Pending negotiations: " + tributes.size());
        }

        ItemStack item =
                new ItemBuilder(mat)
                        .name("<red>War with " + opponentName)
                        .lore(lore.toArray(new String[0]))
                        .glow(war.isActive())
                        .build();

        return new GuiItem(item);
    }

    private String getStateDisplay(War war) {
        return switch (war.getWarState()) {
            case DECLARED -> "<yellow>Declared";
            case ACTIVE -> "<red>ACTIVE";
            case CEASEFIRE -> "<yellow>Ceasefire";
            case ENDED -> "<gray>Ended";
        };
    }
}
