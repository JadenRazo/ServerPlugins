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
import net.serverplugins.claim.models.NationRelation;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class NationRelationsGui extends Gui {

    private final ServerClaim plugin;
    private final Nation nation;
    private final Claim playerClaim;
    private final int page;
    private static final int ITEMS_PER_PAGE = 28;

    public NationRelationsGui(ServerClaim plugin, Player player, Nation nation, Claim playerClaim) {
        this(plugin, player, nation, playerClaim, 0);
    }

    public NationRelationsGui(
            ServerClaim plugin, Player player, Nation nation, Claim playerClaim, int page) {
        super(
                plugin,
                player,
                (nation != null ? nation.getColoredTag() : "<white>") + " <aqua>Diplomacy",
                54);
        this.plugin = plugin;

        // Validate nation exists
        if (!GuiValidator.validateNation(plugin, player, nation, "Nation Relations")) {
            this.nation = null;
            this.playerClaim = playerClaim;
            this.page = page;
            return;
        }

        this.nation = nation;
        this.playerClaim = playerClaim;
        this.page = page;
    }

    @Override
    protected void initializeItems() {
        // Early return if nation is null
        if (nation == null) {
            plugin.getLogger().warning("NationRelationsGui: Nation is null, cannot initialize");
            return;
        }

        // Fill border
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 9; i++) setItem(i, new GuiItem(filler));
        for (int i = 45; i < 54; i++) setItem(i, new GuiItem(filler));
        for (int i = 9; i < 45; i += 9) {
            setItem(i, new GuiItem(filler));
            setItem(i + 8, new GuiItem(filler));
        }

        List<Nation> allNations = null;
        try {
            allNations = plugin.getNationManager().getAllNations();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get all nations: " + e.getMessage());
            allNations = new ArrayList<>();
        }

        // Remove own nation
        allNations.removeIf(n -> n == null || n.getId() == nation.getId());

        int totalPages = (int) Math.ceil((double) allNations.size() / ITEMS_PER_PAGE);
        if (totalPages == 0) totalPages = 1;

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, allNations.size());

        int[] slots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };

        for (int i = start; i < end; i++) {
            int slotIndex = i - start;
            if (slotIndex >= slots.length) break;

            Nation otherNation = allNations.get(i);
            if (otherNation == null) continue; // Skip null nations

            GuiItem item = createNationItem(otherNation);
            if (item != null) {
                setItem(slots[slotIndex], item);
            }
        }

        // Legend (slot 4)
        ItemStack legendItem =
                new ItemBuilder(Material.BOOK)
                        .name("<yellow>Relation Types")
                        .lore(
                                "",
                                "<green>● Ally <gray>- Friendly nation",
                                "<white>● Neutral <gray>- No relation",
                                "<red>● Enemy <gray>- Hostile nation",
                                "<dark_red>● At War <gray>- Active conflict",
                                "<yellow>● Truce <gray>- Post-war peace",
                                "",
                                "<gray>Click a nation to change relation")
                        .build();
        setItem(4, new GuiItem(legendItem));

        // Previous page (slot 48)
        if (page > 0) {
            ItemStack prevItem =
                    new ItemBuilder(Material.ARROW).name("<yellow>Previous Page").build();
            setItem(
                    48,
                    new GuiItem(
                            prevItem,
                            e -> {
                                viewer.closeInventory();
                                new NationRelationsGui(
                                                plugin, viewer, nation, playerClaim, page - 1)
                                        .open();
                            }));
        }

        // Page info (slot 49)
        ItemStack pageInfo =
                new ItemBuilder(Material.PAPER)
                        .name("<gold>Page " + (page + 1) + " of " + totalPages)
                        .lore("<gray>Nations: <white>" + allNations.size())
                        .build();
        setItem(49, new GuiItem(pageInfo));

        // Next page (slot 50)
        if (page < totalPages - 1) {
            ItemStack nextItem = new ItemBuilder(Material.ARROW).name("<yellow>Next Page").build();
            setItem(
                    50,
                    new GuiItem(
                            nextItem,
                            e -> {
                                viewer.closeInventory();
                                new NationRelationsGui(
                                                plugin, viewer, nation, playerClaim, page + 1)
                                        .open();
                            }));
        }

        // Back button (slot 45)
        ItemStack backItem =
                new ItemBuilder(Material.DARK_OAK_DOOR)
                        .name("<gray>Back")
                        .lore("<gray>Return to nation menu")
                        .build();
        setItem(
                45,
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

    private GuiItem createNationItem(Nation otherNation) {
        if (otherNation == null) {
            plugin.getLogger().warning("Cannot create nation item for null nation");
            return null;
        }

        NationRelation.RelationType relation =
                plugin.getNationManager().getRelation(nation.getId(), otherNation.getId());

        Material material =
                switch (relation) {
                    case ALLY -> Material.EMERALD;
                    case NEUTRAL -> Material.IRON_INGOT;
                    case ENEMY -> Material.REDSTONE;
                    case AT_WAR -> Material.TNT;
                    case TRUCE -> Material.GOLDEN_APPLE;
                };

        String relationColor =
                switch (relation) {
                    case ALLY -> "<green>";
                    case NEUTRAL -> "<white>";
                    case ENEMY -> "<red>";
                    case AT_WAR -> "<dark_red>";
                    case TRUCE -> "<yellow>";
                };

        String nationName = otherNation.getName() != null ? otherNation.getName() : "Unknown";
        String nationTag =
                otherNation.getColoredTag() != null ? otherNation.getColoredTag() : "<white>";

        ItemStack item =
                new ItemBuilder(material)
                        .name(nationTag + " " + nationName)
                        .lore(
                                "",
                                "<gray>Members: <white>" + otherNation.getMemberCount(),
                                "<gray>Chunks: <white>" + otherNation.getTotalChunks(),
                                "",
                                "<gray>Relation: " + relationColor + relation.getDisplayName(),
                                "",
                                relation == NationRelation.RelationType.AT_WAR
                                        ? "<dark_red>Currently at war!"
                                        : "<yellow>Click to change relation")
                        .build();

        return new GuiItem(
                item,
                e -> {
                    if (relation == NationRelation.RelationType.AT_WAR) {
                        TextUtil.send(
                                viewer,
                                plugin.getClaimConfig().getMessage("prefix")
                                        + "<red>Cannot change relation during war!");
                        return;
                    }

                    viewer.closeInventory();
                    new NationRelationSelectGui(plugin, viewer, nation, otherNation, playerClaim)
                            .open();
                });
    }

    // Inner class for relation selection
    public static class NationRelationSelectGui extends Gui {
        private final ServerClaim plugin;
        private final Nation nation;
        private final Nation targetNation;
        private final Claim playerClaim;

        public NationRelationSelectGui(
                ServerClaim plugin,
                Player player,
                Nation nation,
                Nation targetNation,
                Claim playerClaim) {
            super(plugin, player, "<aqua>Set Relation with " + targetNation.getName(), 27);
            this.plugin = plugin;
            this.nation = nation;
            this.targetNation = targetNation;
            this.playerClaim = playerClaim;
        }

        @Override
        protected void initializeItems() {
            ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
            for (int i = 0; i < 27; i++) setItem(i, new GuiItem(filler));

            // Ally (slot 11)
            ItemStack allyItem =
                    new ItemBuilder(Material.EMERALD)
                            .name("<green>Set as Ally")
                            .lore("", "<gray>Mark this nation as", "<gray>a friendly ally")
                            .build();
            setItem(11, new GuiItem(allyItem, e -> setRelation(NationRelation.RelationType.ALLY)));

            // Neutral (slot 13)
            ItemStack neutralItem =
                    new ItemBuilder(Material.IRON_INGOT)
                            .name("<white>Set as Neutral")
                            .lore("", "<gray>Remove any special", "<gray>relation status")
                            .build();
            setItem(
                    13,
                    new GuiItem(
                            neutralItem, e -> setRelation(NationRelation.RelationType.NEUTRAL)));

            // Enemy (slot 15)
            ItemStack enemyItem =
                    new ItemBuilder(Material.REDSTONE)
                            .name("<red>Set as Enemy")
                            .lore("", "<gray>Mark this nation as", "<gray>a hostile enemy")
                            .build();
            setItem(
                    15,
                    new GuiItem(enemyItem, e -> setRelation(NationRelation.RelationType.ENEMY)));

            // Back (slot 22)
            ItemStack backItem = new ItemBuilder(Material.ARROW).name("<gray>Back").build();
            setItem(
                    22,
                    new GuiItem(
                            backItem,
                            e -> {
                                viewer.closeInventory();
                                new NationRelationsGui(plugin, viewer, nation, playerClaim).open();
                            }));
        }

        private void setRelation(NationRelation.RelationType type) {
            plugin.getNationManager()
                    .setRelation(
                            nation,
                            targetNation,
                            type,
                            success -> {
                                if (success) {
                                    TextUtil.send(
                                            viewer,
                                            plugin.getClaimConfig().getMessage("prefix")
                                                    + "<green>Relation with "
                                                    + targetNation.getName()
                                                    + " set to "
                                                    + type.getDisplayName());
                                } else {
                                    TextUtil.send(
                                            viewer,
                                            plugin.getClaimConfig().getMessage("prefix")
                                                    + "<red>Could not update relation.");
                                }
                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    viewer.closeInventory();
                                                    new NationRelationsGui(
                                                                    plugin,
                                                                    viewer,
                                                                    nation,
                                                                    playerClaim)
                                                            .open();
                                                });
                            });
        }
    }
}
