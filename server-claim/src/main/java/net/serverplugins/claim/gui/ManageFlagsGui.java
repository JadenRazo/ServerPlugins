package net.serverplugins.claim.gui;

import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ClaimSettings;
import net.serverplugins.claim.models.ManagementPermission;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * GUI for managing claim protection flags. Requires MANAGE_FLAGS permission.
 *
 * <p>Flags include: - PvP enabled - Fire spread - Explosions - Hostile spawns - Mob griefing -
 * Passive spawns - Crop trampling - Leaf decay
 */
public class ManageFlagsGui extends Gui {

    private final ServerClaim plugin;
    private final Claim claim;

    public ManageFlagsGui(ServerClaim plugin, Player player, Claim claim) {
        super(plugin, player, "Manage Flags", 27);
        this.plugin = plugin;

        // Validate claim at constructor level
        if (!GuiValidator.validateClaim(plugin, player, claim, "ManageFlagsGui")) {
            this.claim = null;
            return;
        }

        this.claim = claim;
    }

    @Override
    protected void initializeItems() {
        // Safety check: claim was invalidated in constructor
        if (claim == null) {
            showErrorState();
            return;
        }

        // Check permission
        if (!claim.hasManagementPermission(viewer.getUniqueId(), ManagementPermission.MANAGE_FLAGS)
                && !viewer.hasPermission("serverclaim.admin")) {
            showNoPermission();
            return;
        }

        // Null-safe settings access
        ClaimSettings settings = claim.getSettings();
        if (settings == null) {
            settings = new ClaimSettings();
            claim.setSettings(settings);
        }

        final ClaimSettings s = settings;

        // Row 0: Title bar
        setupTitleBar();

        // Row 1: Protection flags
        setupProtectionFlags(s);

        // Row 2: Navigation
        setupNavigationBar();

        // Fill with claim-colored glass
        Material borderMaterial =
                claim.getColor() != null
                        ? claim.getColor().getGlassPaneMaterial()
                        : Material.WHITE_STAINED_GLASS_PANE;
        fillEmpty(new ItemBuilder(borderMaterial).name(" ").build());
    }

    private void showNoPermission() {
        ItemStack noPermItem =
                new ItemBuilder(Material.BARRIER)
                        .name("<red>No Permission")
                        .lore(
                                "",
                                "<gray>You do not have permission",
                                "<gray>to manage claim protection flags.",
                                "",
                                "<yellow>Required: <white>Manage Flags")
                        .build();
        setItem(13, new GuiItem(noPermItem));

        // Back button
        ItemStack backItem =
                new ItemBuilder(Material.ARROW)
                        .name("<red>Back")
                        .lore("<gray>Return to claim settings")
                        .build();
        setItem(
                22,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            new ClaimSettingsGui(plugin, viewer, claim).open();
                        }));

        fillEmpty(new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name(" ").build());
    }

    private void setupTitleBar() {
        ItemStack blackGlass = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {0, 1, 2, 3, 5, 6, 7, 8}) {
            setItem(i, new GuiItem(blackGlass));
        }

        ItemStack titleItem =
                new ItemBuilder(Material.COMPARATOR)
                        .name("<gold>Protection Flags")
                        .lore(
                                "",
                                "<gray>Claim: <white>" + claim.getName(),
                                "",
                                "<dark_gray>Configure protection settings",
                                "<dark_gray>for your claim")
                        .glow(true)
                        .build();
        setItem(4, new GuiItem(titleItem));
    }

    private void setupProtectionFlags(ClaimSettings s) {
        // PvP Toggle (slot 9)
        boolean pvp = s.isPvpEnabled();
        ItemStack pvpItem =
                new ItemBuilder(pvp ? Material.DIAMOND_SWORD : Material.WOODEN_SWORD)
                        .name((pvp ? "<green>" : "<red>") + "PvP Combat")
                        .lore(
                                "",
                                "<gray>Status: " + (pvp ? "<green>ENABLED" : "<red>DISABLED"),
                                "",
                                "<dark_gray>Allow players to fight",
                                "<dark_gray>in your claim",
                                "",
                                "<yellow>Click to toggle")
                        .glow(pvp)
                        .build();
        setItem(
                9,
                new GuiItem(
                        pvpItem,
                        e -> {
                            s.setPvpEnabled(!s.isPvpEnabled());
                            plugin.getRepository().saveClaimSettings(claim);
                            reopenMenu();
                        }));

        // Fire Spread Toggle (slot 10)
        boolean fire = s.isFireSpread();
        ItemStack fireItem =
                new ItemBuilder(fire ? Material.CAMPFIRE : Material.SOUL_CAMPFIRE)
                        .name((fire ? "<green>" : "<red>") + "Fire Spread")
                        .lore(
                                "",
                                "<gray>Status: " + (fire ? "<green>ENABLED" : "<red>DISABLED"),
                                "",
                                "<dark_gray>Allow fire to spread",
                                "<dark_gray>in your claim",
                                "",
                                "<yellow>Click to toggle")
                        .glow(fire)
                        .build();
        setItem(
                10,
                new GuiItem(
                        fireItem,
                        e -> {
                            s.setFireSpread(!s.isFireSpread());
                            plugin.getRepository().saveClaimSettings(claim);
                            reopenMenu();
                        }));

        // Explosions Toggle (slot 11)
        boolean explosions = s.isExplosions();
        ItemStack explosionsItem =
                new ItemBuilder(explosions ? Material.TNT : Material.GUNPOWDER)
                        .name((explosions ? "<green>" : "<red>") + "Explosions")
                        .lore(
                                "",
                                "<gray>Status: "
                                        + (explosions ? "<green>ENABLED" : "<red>DISABLED"),
                                "",
                                "<dark_gray>Allow TNT and creeper",
                                "<dark_gray>explosions in claim",
                                "",
                                "<yellow>Click to toggle")
                        .glow(explosions)
                        .build();
        setItem(
                11,
                new GuiItem(
                        explosionsItem,
                        e -> {
                            s.setExplosions(!s.isExplosions());
                            plugin.getRepository().saveClaimSettings(claim);
                            reopenMenu();
                        }));

        // Hostile Spawns Toggle (slot 12)
        boolean hostile = s.isHostileSpawns();
        ItemStack hostileItem =
                new ItemBuilder(hostile ? Material.ZOMBIE_HEAD : Material.ROTTEN_FLESH)
                        .name((hostile ? "<green>" : "<red>") + "Hostile Mobs")
                        .lore(
                                "",
                                "<gray>Status: " + (hostile ? "<green>ENABLED" : "<red>DISABLED"),
                                "",
                                "<dark_gray>Allow hostile mobs to",
                                "<dark_gray>spawn in your claim",
                                "",
                                "<yellow>Click to toggle")
                        .glow(hostile)
                        .build();
        setItem(
                12,
                new GuiItem(
                        hostileItem,
                        e -> {
                            s.setHostileSpawns(!s.isHostileSpawns());
                            plugin.getRepository().saveClaimSettings(claim);
                            reopenMenu();
                        }));

        // Mob Griefing Toggle (slot 13)
        boolean grief = s.isMobGriefing();
        ItemStack griefItem =
                new ItemBuilder(grief ? Material.GRASS_BLOCK : Material.DIRT)
                        .name((grief ? "<green>" : "<red>") + "Mob Griefing")
                        .lore(
                                "",
                                "<gray>Status: " + (grief ? "<green>ENABLED" : "<red>DISABLED"),
                                "",
                                "<dark_gray>Allow mobs to modify",
                                "<dark_gray>blocks (endermen, etc)",
                                "",
                                "<yellow>Click to toggle")
                        .glow(grief)
                        .build();
        setItem(
                13,
                new GuiItem(
                        griefItem,
                        e -> {
                            s.setMobGriefing(!s.isMobGriefing());
                            plugin.getRepository().saveClaimSettings(claim);
                            reopenMenu();
                        }));

        // Passive Spawns Toggle (slot 14)
        boolean passive = s.isPassiveSpawns();
        ItemStack passiveItem =
                new ItemBuilder(passive ? Material.EGG : Material.WHEAT)
                        .name((passive ? "<green>" : "<red>") + "Passive Mobs")
                        .lore(
                                "",
                                "<gray>Status: " + (passive ? "<green>ENABLED" : "<red>DISABLED"),
                                "",
                                "<dark_gray>Allow passive mobs to",
                                "<dark_gray>spawn in your claim",
                                "",
                                "<yellow>Click to toggle")
                        .glow(passive)
                        .build();
        setItem(
                14,
                new GuiItem(
                        passiveItem,
                        e -> {
                            s.setPassiveSpawns(!s.isPassiveSpawns());
                            plugin.getRepository().saveClaimSettings(claim);
                            reopenMenu();
                        }));

        // Crop Trampling Toggle (slot 15)
        boolean trample = s.isCropTrampling();
        ItemStack trampleItem =
                new ItemBuilder(trample ? Material.WHEAT : Material.FARMLAND)
                        .name((trample ? "<green>" : "<red>") + "Crop Trampling")
                        .lore(
                                "",
                                "<gray>Status: " + (trample ? "<green>ENABLED" : "<red>DISABLED"),
                                "",
                                "<dark_gray>Allow crops to be",
                                "<dark_gray>trampled in your claim",
                                "",
                                "<yellow>Click to toggle")
                        .glow(trample)
                        .build();
        setItem(
                15,
                new GuiItem(
                        trampleItem,
                        e -> {
                            s.setCropTrampling(!s.isCropTrampling());
                            plugin.getRepository().saveClaimSettings(claim);
                            reopenMenu();
                        }));

        // Leaf Decay Toggle (slot 16) - Only if player has permission
        if (viewer.hasPermission("serverclaim.settings.leafdecay")) {
            boolean leafDecay = s.isLeafDecay();
            ItemStack leafItem =
                    new ItemBuilder(leafDecay ? Material.OAK_LEAVES : Material.DEAD_BUSH)
                            .name((leafDecay ? "<green>" : "<red>") + "Leaf Decay")
                            .lore(
                                    "",
                                    "<gray>Status: "
                                            + (leafDecay ? "<green>ENABLED" : "<red>DISABLED"),
                                    "",
                                    "<dark_gray>Allow leaves to decay",
                                    "<dark_gray>naturally in your claim",
                                    "",
                                    "<yellow>Click to toggle")
                            .glow(leafDecay)
                            .build();
            setItem(
                    16,
                    new GuiItem(
                            leafItem,
                            e -> {
                                s.setLeafDecay(!s.isLeafDecay());
                                plugin.getRepository().saveClaimSettings(claim);
                                reopenMenu();
                            }));
        } else {
            // Show locked leaf decay
            ItemStack lockedItem =
                    new ItemBuilder(Material.OAK_LEAVES)
                            .name("<gray>Leaf Decay")
                            .lore(
                                    "",
                                    "<red>LOCKED",
                                    "",
                                    "<dark_gray>Requires special permission",
                                    "<dark_gray>to toggle")
                            .build();
            setItem(16, new GuiItem(lockedItem));
        }

        // Divider at slot 17
        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        setItem(17, new GuiItem(divider));
    }

    private void setupNavigationBar() {
        // Back button (slot 18)
        ItemStack backItem =
                new ItemBuilder(Material.ARROW)
                        .name("<red>Back")
                        .lore("<gray>Return to claim settings")
                        .build();
        setItem(
                18,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            new ClaimSettingsGui(plugin, viewer, claim).open();
                        }));

        // Info item (slot 22)
        ItemStack infoItem =
                new ItemBuilder(Material.OAK_SIGN)
                        .name("<yellow>Flag Info")
                        .lore(
                                "",
                                "<green>GREEN<gray> = Enabled/Allowed",
                                "<red>RED<gray> = Disabled/Protected",
                                "",
                                "<dark_gray>Click any flag to toggle it")
                        .build();
        setItem(22, new GuiItem(infoItem));

        // Dividers
        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {19, 20, 21, 23, 24, 25, 26}) {
            setItem(i, new GuiItem(divider));
        }
    }

    private void reopenMenu() {
        plugin.getServer()
                .getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            viewer.closeInventory();
                            new ManageFlagsGui(plugin, viewer, claim).open();
                        });
    }

    /** Show error state when claim is null. */
    private void showErrorState() {
        ItemStack errorItem =
                new ItemBuilder(Material.BARRIER)
                        .name("<red>Error")
                        .lore(
                                "",
                                "<gray>Cannot manage flags:",
                                "<gray>Claim no longer exists",
                                "",
                                "<yellow>Try closing and reopening")
                        .build();
        setItem(13, new GuiItem(errorItem));

        ItemStack backItem = new ItemBuilder(Material.ARROW).name("<red>Back").build();
        setItem(22, new GuiItem(backItem, e -> viewer.closeInventory()));

        fillEmpty(new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name(" ").build());
    }
}
