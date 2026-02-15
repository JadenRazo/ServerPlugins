package net.serverplugins.claim.gui;

import java.util.ArrayList;
import java.util.List;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.Nation;
import net.serverplugins.claim.models.War;
import net.serverplugins.claim.models.WarTribute;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class WarTributeGui extends Gui {

    private final ServerClaim plugin;
    private final Nation nation;
    private final War war;
    private final Claim playerClaim;

    public WarTributeGui(
            ServerClaim plugin, Player player, Nation nation, War war, Claim playerClaim) {
        super(plugin, player, "<yellow>Peace Negotiations", 54);
        this.plugin = plugin;

        // Validate nation exists
        if (!GuiValidator.validateNationOwnership(plugin, player, nation, "War Tribute")) {
            this.nation = null;
            this.war = war;
            this.playerClaim = playerClaim;
            return;
        }

        if (war == null) {
            player.sendMessage(
                    net.kyori.adventure.text.Component.text(
                            "War no longer exists!",
                            net.kyori.adventure.text.format.NamedTextColor.RED));
            plugin.getLogger().warning("WarTributeGui: War is null");
            this.nation = nation;
            this.war = null;
            this.playerClaim = playerClaim;
            return;
        }

        this.nation = nation;
        this.war = war;
        this.playerClaim = playerClaim;
    }

    @Override
    protected void initializeItems() {
        // Early return if nation or war is null
        if (nation == null || war == null) {
            plugin.getLogger().warning("WarTributeGui: Nation or war is null, cannot initialize");
            return;
        }

        // Fill background
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) {
            setItem(i, new GuiItem(filler));
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

        // War info (slot 4)
        ItemStack warInfo =
                new ItemBuilder(Material.MAP)
                        .name("<yellow>War Status")
                        .lore(
                                "",
                                "<gray>Against: <white>" + opponentName,
                                "<gray>Role: " + (isAttacker ? "<red>Attacker" : "<blue>Defender"),
                                "<gray>State: " + war.getWarState().getDisplayName())
                        .build();
        setItem(4, new GuiItem(warInfo));

        // Pending tributes from enemy
        List<WarTribute> pendingTributes = null;
        try {
            pendingTributes = plugin.getWarManager().getPendingTributes(war.getId());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get pending tributes: " + e.getMessage());
            pendingTributes = new ArrayList<>();
        }

        WarTribute.OfferingSide mySide =
                isAttacker ? WarTribute.OfferingSide.ATTACKER : WarTribute.OfferingSide.DEFENDER;

        WarTribute incomingOffer = null;
        for (WarTribute t : pendingTributes) {
            if (t == null) continue; // Skip null tributes
            if (t.getOfferingSide() != mySide && t.isPending()) {
                incomingOffer = t;
                break;
            }
        }

        // Incoming offer (slot 20)
        if (incomingOffer != null) {
            WarTribute finalOffer = incomingOffer;
            String tributeType =
                    incomingOffer.getTributeType() != null
                            ? incomingOffer.getTributeType().getDisplayName()
                            : "Unknown";
            String moneyLine =
                    incomingOffer.getMoneyAmount() > 0
                            ? "<gray>Amount: <gold>$"
                                    + String.format("%.2f", incomingOffer.getMoneyAmount())
                            : "";
            String messageLine =
                    incomingOffer.getMessage() != null && !incomingOffer.getMessage().isEmpty()
                            ? "<gray>Message: <italic>" + incomingOffer.getMessage()
                            : "";

            ItemStack offerItem =
                    new ItemBuilder(Material.CHEST)
                            .name("<gold>Incoming Peace Offer!")
                            .lore(
                                    "",
                                    "<gray>Type: <white>" + tributeType,
                                    moneyLine,
                                    messageLine,
                                    "",
                                    "<green>Left-click to ACCEPT",
                                    "<red>Right-click to REJECT")
                            .glow(true)
                            .build();
            setItem(
                    20,
                    GuiItem.withContext(
                            offerItem,
                            ctx -> {
                                if (ctx.isLeftClick()) {
                                    plugin.getWarManager()
                                            .acceptTribute(
                                                    finalOffer,
                                                    success -> {
                                                        if (success) {
                                                            TextUtil.send(
                                                                    viewer,
                                                                    plugin.getClaimConfig()
                                                                                    .getMessage(
                                                                                            "prefix")
                                                                            + "<green>Peace offer accepted! The war has ended.");
                                                        }
                                                        viewer.closeInventory();
                                                    });
                                } else if (ctx.isRightClick()) {
                                    plugin.getWarManager()
                                            .rejectTribute(
                                                    finalOffer,
                                                    success -> {
                                                        if (success) {
                                                            TextUtil.send(
                                                                    viewer,
                                                                    plugin.getClaimConfig()
                                                                                    .getMessage(
                                                                                            "prefix")
                                                                            + "<yellow>Peace offer rejected. The war continues.");
                                                        }
                                                        plugin.getServer()
                                                                .getScheduler()
                                                                .runTask(
                                                                        plugin,
                                                                        () -> {
                                                                            viewer.closeInventory();
                                                                            new WarTributeGui(
                                                                                            plugin,
                                                                                            viewer,
                                                                                            nation,
                                                                                            war,
                                                                                            playerClaim)
                                                                                    .open();
                                                                        });
                                                    });
                                }
                            }));
        } else {
            ItemStack noOfferItem =
                    new ItemBuilder(Material.BARRIER)
                            .name("<gray>No Incoming Offers")
                            .lore("", "<gray>The enemy has not sent", "<gray>any peace offers yet.")
                            .build();
            setItem(20, new GuiItem(noOfferItem));
        }

        // Offer peace with tribute (slot 22)
        ItemStack peaceItem =
                new ItemBuilder(Material.GOLD_INGOT)
                        .name("<yellow>Offer Peace Tribute")
                        .lore(
                                "",
                                "<gray>Offer money to the enemy",
                                "<gray>in exchange for peace.",
                                "",
                                "<yellow>Click to make offer")
                        .build();
        setItem(
                22,
                new GuiItem(
                        peaceItem,
                        e -> {
                            viewer.closeInventory();
                            new TributeAmountGui(
                                            plugin,
                                            viewer,
                                            nation,
                                            war,
                                            playerClaim,
                                            WarTribute.TributeType.PEACE_OFFER)
                                    .open();
                        }));

        // Surrender (slot 24)
        ItemStack surrenderItem =
                new ItemBuilder(Material.WHITE_BANNER)
                        .name("<red>Surrender")
                        .lore(
                                "",
                                "<gray>Offer unconditional surrender.",
                                "<gray>The enemy must accept.",
                                "",
                                "<red>You will receive a war shield",
                                "<red>but lose the war.",
                                "",
                                "<dark_red>Click to surrender")
                        .build();
        setItem(
                24,
                new GuiItem(
                        surrenderItem,
                        e -> {
                            plugin.getWarManager()
                                    .offerTribute(
                                            war,
                                            mySide,
                                            WarTribute.TributeType.SURRENDER,
                                            0,
                                            "Unconditional surrender",
                                            success -> {
                                                if (success) {
                                                    TextUtil.send(
                                                            viewer,
                                                            plugin.getClaimConfig()
                                                                            .getMessage("prefix")
                                                                    + "<yellow>Surrender offer sent. Waiting for enemy response.");
                                                } else {
                                                    TextUtil.send(
                                                            viewer,
                                                            plugin.getClaimConfig()
                                                                            .getMessage("prefix")
                                                                    + "<red>Could not send surrender offer.");
                                                }
                                                viewer.closeInventory();
                                            });
                        }));

        // Back button (slot 49)
        ItemStack backItem =
                new ItemBuilder(Material.ARROW)
                        .name("<gray>Back")
                        .lore("<gray>Return to war room")
                        .build();
        setItem(
                49,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            new WarMenuGui(plugin, viewer, nation, playerClaim).open();
                        }));

        // Close (slot 53)
        ItemStack closeItem = new ItemBuilder(Material.BARRIER).name("<red>Close").build();
        setItem(53, new GuiItem(closeItem, e -> viewer.closeInventory()));
    }

    // Inner class for tribute amount selection
    public static class TributeAmountGui extends Gui {
        private final ServerClaim plugin;
        private final Nation nation;
        private final War war;
        private final Claim playerClaim;
        private final WarTribute.TributeType type;

        public TributeAmountGui(
                ServerClaim plugin,
                Player player,
                Nation nation,
                War war,
                Claim playerClaim,
                WarTribute.TributeType type) {
            super(plugin, player, "<gold>Select Tribute Amount", 27);
            this.plugin = plugin;
            this.nation = nation;
            this.war = war;
            this.playerClaim = playerClaim;
            this.type = type;
        }

        @Override
        protected void initializeItems() {
            ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
            for (int i = 0; i < 27; i++) setItem(i, new GuiItem(filler));

            boolean isAttacker = war.isAttacker(nation.getId());
            WarTribute.OfferingSide side =
                    isAttacker
                            ? WarTribute.OfferingSide.ATTACKER
                            : WarTribute.OfferingSide.DEFENDER;

            // Amount options
            createAmountButton(10, 1000, side);
            createAmountButton(11, 5000, side);
            createAmountButton(12, 10000, side);
            createAmountButton(13, 50000, side);
            createAmountButton(14, 100000, side);
            createAmountButton(15, 500000, side);
            createAmountButton(16, 1000000, side);

            // Back (slot 22)
            ItemStack backItem = new ItemBuilder(Material.ARROW).name("<gray>Back").build();
            setItem(
                    22,
                    new GuiItem(
                            backItem,
                            e -> {
                                viewer.closeInventory();
                                new WarTributeGui(plugin, viewer, nation, war, playerClaim).open();
                            }));
        }

        private void createAmountButton(int slot, int amount, WarTribute.OfferingSide side) {
            String formatted = formatAmount(amount);
            ItemStack item =
                    new ItemBuilder(Material.GOLD_INGOT)
                            .name("<gold>$" + formatted)
                            .lore(
                                    "",
                                    "<gray>Click to offer",
                                    "<gold>$" + formatted + "</gold> as tribute")
                            .build();
            setItem(
                    slot,
                    new GuiItem(
                            item,
                            e -> {
                                plugin.getWarManager()
                                        .offerTribute(
                                                war,
                                                side,
                                                type,
                                                amount,
                                                "Peace offer of $" + formatted,
                                                success -> {
                                                    if (success) {
                                                        TextUtil.send(
                                                                viewer,
                                                                plugin.getClaimConfig()
                                                                                .getMessage(
                                                                                        "prefix")
                                                                        + "<green>Peace offer of $"
                                                                        + formatted
                                                                        + " sent!");
                                                    } else {
                                                        TextUtil.send(
                                                                viewer,
                                                                plugin.getClaimConfig()
                                                                                .getMessage(
                                                                                        "prefix")
                                                                        + "<red>Could not send peace offer.");
                                                    }
                                                    viewer.closeInventory();
                                                });
                            }));
        }

        private String formatAmount(int amount) {
            if (amount >= 1000000) {
                return (amount / 1000000) + "M";
            } else if (amount >= 1000) {
                return (amount / 1000) + "K";
            }
            return String.valueOf(amount);
        }
    }
}
