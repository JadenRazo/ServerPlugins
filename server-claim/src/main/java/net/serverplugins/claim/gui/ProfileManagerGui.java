package net.serverplugins.claim.gui;

import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ClaimProfile;
import net.serverplugins.claim.models.ClaimSettings;
import net.serverplugins.claim.models.ProfileColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ProfileManagerGui extends Gui {

    private final ServerClaim plugin;
    private final Claim claim;

    public ProfileManagerGui(ServerClaim plugin, Player player, Claim claim) {
        super(plugin, player, "Profile Manager", 45);
        this.plugin = plugin;
        this.claim = claim;
    }

    @Override
    protected void initializeItems() {
        ClaimProfile activeProfile = claim.getActiveProfile();
        if (activeProfile == null) {
            activeProfile =
                    plugin.getProfileManager().createProfile(claim, "Default", ProfileColor.WHITE);
            plugin.getProfileManager().setActiveProfile(claim, activeProfile);
        }

        ClaimSettings settings = activeProfile.getSettings();
        if (settings == null) {
            settings = new ClaimSettings();
            activeProfile.setSettings(settings);
        }

        final ClaimProfile profile = activeProfile;
        final ClaimSettings s = settings;

        // Row 0: Title and info
        setupTitleRow(profile);

        // Row 1: Protection toggles with spacing
        setupProtectionSettings(profile, s);

        // Row 2: Additional settings
        setupAdditionalSettings(profile, s);

        // Row 3: Management options
        setupManagementRow(profile);

        // Row 4: Profile slots
        setupProfileSlots(profile);

        fillEmpty(new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
    }

    private void setupTitleRow(ClaimProfile profile) {
        // Decorative glass panes
        ItemStack blackGlass = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {0, 1, 2, 3, 5, 6, 7, 8}) {
            setItem(i, new GuiItem(blackGlass));
        }

        // Title item (slot 4)
        int chunkCount = claim.getChunks().size();
        int memberCount = claim.getMembers() != null ? claim.getMembers().size() : 0;

        ItemStack titleItem =
                new ItemBuilder(Material.NETHER_STAR)
                        .name("<gold>Claim Settings")
                        .lore(
                                "",
                                "<gray>Claim: <white>" + claim.getName(),
                                "<gray>Profile: <white>" + profile.getName(),
                                "",
                                "<dark_gray>Chunks: " + chunkCount + " | Members: " + memberCount)
                        .glow(true)
                        .build();
        setItem(4, new GuiItem(titleItem));
    }

    private void setupProtectionSettings(ClaimProfile profile, ClaimSettings s) {
        // PvP Toggle (slot 10)
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
                10,
                new GuiItem(
                        pvpItem,
                        e -> {
                            s.setPvpEnabled(!s.isPvpEnabled());
                            plugin.getRepository().saveProfileSettings(profile);
                            reopenMenu();
                        }));

        // Explosions Toggle (slot 12)
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
                12,
                new GuiItem(
                        explosionsItem,
                        e -> {
                            s.setExplosions(!s.isExplosions());
                            plugin.getRepository().saveProfileSettings(profile);
                            reopenMenu();
                        }));

        // Fire Spread Toggle (slot 14)
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
                14,
                new GuiItem(
                        fireItem,
                        e -> {
                            s.setFireSpread(!s.isFireSpread());
                            plugin.getRepository().saveProfileSettings(profile);
                            reopenMenu();
                        }));

        // Hostile Spawns Toggle (slot 16)
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
                16,
                new GuiItem(
                        hostileItem,
                        e -> {
                            s.setHostileSpawns(!s.isHostileSpawns());
                            plugin.getRepository().saveProfileSettings(profile);
                            reopenMenu();
                        }));

        // Glass dividers
        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {9, 11, 13, 15, 17}) {
            setItem(i, new GuiItem(divider));
        }
    }

    private void setupAdditionalSettings(ClaimProfile profile, ClaimSettings s) {
        // Passive Spawns Toggle (slot 19)
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
                19,
                new GuiItem(
                        passiveItem,
                        e -> {
                            s.setPassiveSpawns(!s.isPassiveSpawns());
                            plugin.getRepository().saveProfileSettings(profile);
                            reopenMenu();
                        }));

        // Mob Griefing Toggle (slot 21)
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
                21,
                new GuiItem(
                        griefItem,
                        e -> {
                            s.setMobGriefing(!s.isMobGriefing());
                            plugin.getRepository().saveProfileSettings(profile);
                            reopenMenu();
                        }));

        // Delete Claim button (slot 25) - Danger zone
        int chunkCount = claim.getChunks().size();
        ItemStack deleteItem =
                new ItemBuilder(Material.TNT)
                        .name("<red>Delete Claim")
                        .lore(
                                "",
                                "<gray>Permanently delete this claim",
                                "<gray>and unclaim all <white>" + chunkCount + "<gray> chunks.",
                                "",
                                "<red>This cannot be undone!",
                                "",
                                "<yellow>Click to confirm deletion")
                        .build();
        setItem(
                25,
                new GuiItem(
                        deleteItem,
                        e -> {
                            viewer.closeInventory();
                            new ClaimDeleteConfirmGui(plugin, viewer, claim).open();
                        }));

        // Dividers for row 2
        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {18, 20, 22, 23, 24, 26}) {
            setItem(i, new GuiItem(divider));
        }
    }

    private void setupManagementRow(ClaimProfile profile) {
        // Manage Groups & Members (slot 28)
        int memberCount = claim.getMembers() != null ? claim.getMembers().size() : 0;
        ItemStack groupItem =
                new ItemBuilder(Material.WRITABLE_BOOK)
                        .name("<aqua>Groups & Members")
                        .lore(
                                "",
                                "<gray>Members: <white>" + memberCount,
                                "",
                                "<dark_gray>Manage player groups",
                                "<dark_gray>and permissions",
                                "",
                                "<yellow>Click to manage")
                        .build();
        setItem(
                28,
                new GuiItem(
                        groupItem,
                        e -> {
                            viewer.closeInventory();
                            new ManageGroupsGui(plugin, viewer, claim).open();
                        }));

        // Profile Color (slot 30)
        ItemStack colorItem =
                new ItemBuilder(profile.getColor().getGlassPaneMaterial())
                        .name("<light_purple>Profile Color")
                        .lore(
                                "",
                                "<gray>Current: "
                                        + profile.getColor().getColorTag()
                                        + profile.getColor().getDisplayName(),
                                "",
                                "<dark_gray>Changes the border",
                                "<dark_gray>particle color",
                                "",
                                "<yellow>Click to change")
                        .build();
        setItem(
                30,
                new GuiItem(
                        colorItem,
                        e -> {
                            viewer.closeInventory();
                            new ColorPickerGui(plugin, viewer, claim).open();
                        }));

        // Profile Name (slot 32)
        ItemStack nameItem =
                new ItemBuilder(Material.NAME_TAG)
                        .name("<white>Profile Name")
                        .lore(
                                "",
                                "<gray>Current: <white>" + profile.getName(),
                                "",
                                "<yellow>Click to rename")
                        .build();
        setItem(
                32,
                new GuiItem(
                        nameItem,
                        e -> {
                            viewer.closeInventory();
                            TextUtil.send(viewer, "<yellow>Type the new profile name in chat:");
                            TextUtil.send(viewer, "<gray>(Type 'cancel' to cancel)");
                            plugin.getProfileManager().awaitRenameInput(viewer, profile);
                        }));

        // Warp Settings (slot 34)
        ItemStack warpItem =
                new ItemBuilder(Material.ENDER_PEARL)
                        .name("<light_purple>Warp Settings")
                        .lore(
                                "",
                                "<dark_gray>Configure your claim's",
                                "<dark_gray>public warp point",
                                "",
                                "<yellow>Click to manage")
                        .build();
        setItem(
                34,
                new GuiItem(
                        warpItem,
                        e -> {
                            viewer.closeInventory();
                            new WarpSettingsGui(plugin, viewer, claim).open();
                        }));

        // Dividers
        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {27, 29, 31, 33, 35}) {
            setItem(i, new GuiItem(divider));
        }
    }

    private void setupProfileSlots(ClaimProfile activeProfile) {
        int maxProfiles = plugin.getProfileManager().getMaxProfiles(viewer);

        // Back button (slot 36 - far left of bottom row)
        ItemStack backItem =
                new ItemBuilder(Material.ARROW)
                        .name("<red>Back")
                        .lore("<gray>Return to claim menu")
                        .build();
        setItem(
                36,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            new ClaimMenuGui(plugin, viewer).open();
                        }));

        // Profile slots: 37, 38, 39, 40, 41, 42, 43
        // With active profile indicator in middle (slot 40)
        int[] profileSlots = {37, 38, 39, 41, 42, 43};

        // Active profile display (slot 40)
        ItemStack activeItem =
                new ItemBuilder(activeProfile.getColor().getGlassPaneMaterial())
                        .name("<green>" + activeProfile.getName())
                        .lore(
                                "",
                                "<green>ACTIVE PROFILE",
                                "",
                                "<gray>Color: "
                                        + activeProfile.getColor().getColorTag()
                                        + activeProfile.getColor().getDisplayName(),
                                "<gray>Particle: <white>" + activeProfile.getParticleEffect())
                        .glow(true)
                        .build();
        setItem(40, new GuiItem(activeItem));

        // Other profile slots
        int profileIndex = 0;
        for (int slotIdx = 0; slotIdx < profileSlots.length; slotIdx++) {
            int slot = profileSlots[slotIdx];

            // Find next non-active profile
            ClaimProfile slotProfile = null;
            while (profileIndex < claim.getProfiles().size()) {
                ClaimProfile p = claim.getProfiles().get(profileIndex);
                profileIndex++;
                if (!p.isActive()) {
                    slotProfile = p;
                    break;
                }
            }

            if (slotProfile != null) {
                final ClaimProfile p = slotProfile;
                ItemStack profileItem =
                        new ItemBuilder(p.getColor().getGlassPaneMaterial())
                                .name("<gray>" + p.getName())
                                .lore(
                                        "",
                                        "<gray>Status: <dark_gray>Inactive",
                                        "",
                                        "<yellow>Click to activate")
                                .build();
                setItem(
                        slot,
                        new GuiItem(
                                profileItem,
                                e -> {
                                    plugin.getProfileManager().setActiveProfile(claim, p);
                                    reopenMenu();
                                }));
            } else if (slotIdx < maxProfiles - 1) {
                // Empty unlocked slot
                final int slotNumber = slotIdx + 2;
                ItemStack emptyItem =
                        new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                                .name("<green>Create Profile")
                                .lore(
                                        "",
                                        "<gray>Slot " + slotNumber + "/" + maxProfiles,
                                        "",
                                        "<yellow>Click to create")
                                .build();
                setItem(
                        slot,
                        new GuiItem(
                                emptyItem,
                                e -> {
                                    plugin.getProfileManager()
                                            .createProfile(
                                                    claim,
                                                    "Profile " + slotNumber,
                                                    ProfileColor.values()[
                                                            (slotNumber - 1)
                                                                    % ProfileColor.values()
                                                                            .length]);
                                    reopenMenu();
                                }));
            } else {
                // Locked slot
                ItemStack lockedItem =
                        new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                                .name("<red>Locked")
                                .lore(
                                        "",
                                        "<gray>Upgrade your rank",
                                        "<gray>to unlock more profiles!")
                                .build();
                setItem(slot, new GuiItem(lockedItem));
            }
        }

        // Divider at slot 44
        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        setItem(44, new GuiItem(divider));
    }

    private void reopenMenu() {
        plugin.getServer()
                .getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            viewer.closeInventory();
                            new ProfileManagerGui(plugin, viewer, claim).open();
                        });
    }
}
