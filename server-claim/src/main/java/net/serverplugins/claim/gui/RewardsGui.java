package net.serverplugins.claim.gui;

import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.managers.RewardsManager;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ClaimProfile;
import net.serverplugins.claim.models.DustEffect;
import net.serverplugins.claim.models.PlayerRewardsData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class RewardsGui extends Gui {

    private final ServerClaim plugin;
    private final RewardsManager rewardsManager;
    private final long playerPlaytime;
    private final PlayerRewardsData rewardsData;
    private final Claim currentClaim;
    private final ClaimProfile currentProfile;

    public RewardsGui(ServerClaim plugin, Player player) {
        super(plugin, player, "Dust Color Rewards", 54);
        this.plugin = plugin;
        this.rewardsManager = plugin.getRewardsManager();
        this.playerPlaytime = rewardsManager.getPlayerPlaytimeMinutes(player.getUniqueId());
        this.rewardsData = rewardsManager.getPlayerRewardsSync(player.getUniqueId());

        // Detect claim context
        Location loc = player.getLocation();
        int chunkX = loc.getBlockX() >> 4;
        int chunkZ = loc.getBlockZ() >> 4;
        String world = loc.getWorld().getName();

        this.currentClaim = plugin.getClaimManager().getClaimAt(world, chunkX, chunkZ);
        this.currentProfile =
                (currentClaim != null && currentClaim.getOwnerUuid().equals(player.getUniqueId()))
                        ? currentClaim.getActiveProfile()
                        : null;
    }

    @Override
    protected void initializeItems() {
        // Info item (slot 4)
        setItem(4, createInfoItem());

        // Solid colors (slots 10-25)
        DustEffect[] solidColors = DustEffect.getSolidColors();
        int[] colorSlots = {10, 11, 12, 13, 14, 15, 16, 17, 19, 20, 21, 22, 23, 24, 25, 26};
        for (int i = 0; i < solidColors.length && i < colorSlots.length; i++) {
            DustEffect effect = solidColors[i];
            setItem(colorSlots[i], createEffectItem(effect));
        }

        // Animated effects (slots 30-36)
        DustEffect[] animatedEffects = DustEffect.getAnimatedEffects();
        int[] animSlots = {30, 31, 32, 33, 34, 35, 36};
        for (int i = 0; i < animatedEffects.length && i < animSlots.length; i++) {
            DustEffect effect = animatedEffects[i];
            setItem(animSlots[i], createEffectItem(effect));
        }

        // Particle toggle (slot 46)
        setItem(46, createToggleItem());

        // Static mode toggle (slot 47)
        setItem(47, createStaticModeItem());

        // Progress to next unlock (slot 49)
        setItem(49, createProgressItem());

        // Profile indicator (slot 52)
        setItem(52, createProfileIndicatorItem());

        // Clear profile settings button (slot 48) - only in profile mode
        if (currentProfile != null) {
            setItem(48, createClearProfileSettingsItem());
        }

        // Close button (slot 53)
        ItemStack closeItem =
                new ItemBuilder(Material.BARRIER)
                        .name("<red>Close")
                        .lore("<gray>Close this menu")
                        .build();
        setItem(53, new GuiItem(closeItem, event -> viewer.closeInventory()));

        fillEmpty(new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
    }

    private GuiItem createInfoItem() {
        DustEffect globalEffect = rewardsData.getSelectedDustEffect();
        int unlockedColors = rewardsData.getUnlockedDustEffectCount(playerPlaytime);
        int totalColors = DustEffect.values().length;

        DustEffect nextUnlock = rewardsData.getNextLockedDustEffect(playerPlaytime);
        String nextUnlockText =
                nextUnlock != null
                        ? "<gray>Next unlock: "
                                + nextUnlock.getColorTag()
                                + nextUnlock.getDisplayName()
                                + " <gray>in <yellow>"
                                + formatTimeRemaining(
                                        nextUnlock.getRequiredPlaytimeMinutes() - playerPlaytime)
                        : "<rainbow>All rewards unlocked!";

        // Build lore dynamically based on context
        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add("");
        lore.add("<gray>Total Playtime: <white>" + formatPlaytime(playerPlaytime));
        lore.add("");

        if (currentProfile != null) {
            // Profile mode - show both profile and global settings
            DustEffect profileEffect = currentProfile.getSelectedDustEffect();
            boolean profileParticles = currentProfile.isParticlesEnabled();
            boolean profileStaticMode = currentProfile.isStaticParticleMode();

            lore.add("<aqua>━━━ Profile: " + currentProfile.getName() + " ━━━");
            lore.add(
                    "<gray>Effect: "
                            + (profileEffect != null
                                    ? profileEffect.getColorTag() + profileEffect.getDisplayName()
                                    : "<dark_gray>Using global"));
            lore.add("<gray>Particles: " + (profileParticles ? "<green>ON" : "<red>OFF"));
            lore.add(
                    "<gray>Mode: "
                            + (profileStaticMode ? "<light_purple>Static" : "<gold>Animated"));
            lore.add("");
            lore.add("<gray>━━━ Global Settings ━━━");
            lore.add(
                    "<gray>Effect: "
                            + (globalEffect != null
                                    ? globalEffect.getColorTag() + globalEffect.getDisplayName()
                                    : "<dark_gray>None"));
            lore.add(
                    "<gray>Particles: "
                            + (rewardsData.isParticlesEnabled() ? "<green>ON" : "<red>OFF"));
            lore.add(
                    "<gray>Mode: "
                            + (rewardsData.isStaticParticleMode()
                                    ? "<light_purple>Static"
                                    : "<gold>Animated"));
        } else {
            // Global mode - show only global settings
            lore.add("<gray>━━━ Global Settings ━━━");
            lore.add(
                    "<gray>Effect: "
                            + (globalEffect != null
                                    ? globalEffect.getColorTag() + globalEffect.getDisplayName()
                                    : "<dark_gray>None"));
            lore.add(
                    "<gray>Particles: "
                            + (rewardsData.isParticlesEnabled() ? "<green>ON" : "<red>OFF"));
            lore.add(
                    "<gray>Mode: "
                            + (rewardsData.isStaticParticleMode()
                                    ? "<light_purple>Static"
                                    : "<gold>Animated"));
        }

        lore.add("");
        lore.add("<gray>Effects Unlocked: <yellow>" + unlockedColors + "/" + totalColors);
        lore.add("");
        lore.add(nextUnlockText);

        ItemStack item =
                new ItemBuilder(Material.CLOCK)
                        .name("<gold>Your Rewards Progress")
                        .lore(lore.toArray(new String[0]))
                        .build();

        return new GuiItem(item, event -> {});
    }

    private GuiItem createEffectItem(DustEffect effect) {
        boolean unlocked = effect.isUnlockedFor(playerPlaytime);

        // Check selection based on context
        DustEffect globalEffect = rewardsData.getSelectedDustEffect();
        DustEffect profileEffect =
                currentProfile != null ? currentProfile.getSelectedDustEffect() : null;

        boolean isGloballySelected = globalEffect == effect;
        boolean isProfileSelected = profileEffect == effect;
        boolean isCurrentlyActive = currentProfile != null ? isProfileSelected : isGloballySelected;

        if (unlocked) {
            // Build lore dynamically based on context
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add("");
            lore.add("<green>UNLOCKED");
            lore.add("");
            lore.add(effect.isAnimated() ? "<light_purple>Animated Effect" : "<gray>Solid Color");
            lore.add("");

            // Show status based on mode
            if (currentProfile != null) {
                // Profile mode - show both profile and global status
                if (isProfileSelected) {
                    lore.add("<gold>★ Active in this profile");
                } else {
                    lore.add("<yellow>Click to set for this profile");
                }

                if (isGloballySelected) {
                    lore.add("<gray>▸ Also set globally");
                }
            } else {
                // Global mode - show only global status
                if (isGloballySelected) {
                    lore.add("<gold>★ Currently Selected (Global)");
                } else {
                    lore.add("<yellow>Click to set globally");
                }
            }

            lore.add("");
            lore.add("<aqua>Right-click to preview");

            ItemStack item =
                    new ItemBuilder(effect.getDisplayMaterial())
                            .name(effect.getColorTag() + effect.getDisplayName())
                            .lore(lore.toArray(new String[0]))
                            .glow(isCurrentlyActive)
                            .build();

            return GuiItem.withContext(
                    item,
                    context -> {
                        if (context.isRightClick()) {
                            // Preview mode - spawn particles at player location
                            viewer.closeInventory();
                            previewEffect(effect);
                            viewer.sendMessage(
                                    net.serverplugins.api.utils.TextUtil.parse(
                                            "<aqua>Previewing "
                                                    + effect.getColorTag()
                                                    + effect.getDisplayName()
                                                    + " <aqua>for 30 seconds..."));
                        } else {
                            // Select effect
                            if (currentProfile != null) {
                                // Save to active profile
                                currentProfile.setSelectedDustEffect(effect);
                                plugin.getProfileManager().updateProfile(currentProfile);
                                viewer.sendMessage(
                                        net.serverplugins.api.utils.TextUtil.parse(
                                                "<green>Profile "
                                                        + currentProfile.getName()
                                                        + " <green>particle set to "
                                                        + effect.getColorTag()
                                                        + effect.getDisplayName()
                                                        + "<green>!"));
                            } else {
                                // Save to global rewards
                                rewardsManager.setDustEffect(viewer.getUniqueId(), effect);
                                viewer.sendMessage(
                                        net.serverplugins.api.utils.TextUtil.parse(
                                                "<green>Global particle set to "
                                                        + effect.getColorTag()
                                                        + effect.getDisplayName()
                                                        + "<green>!"));
                            }
                            viewer.closeInventory();
                            new RewardsGui(plugin, viewer).open();
                        }
                    });
        } else {
            long remaining = effect.getRequiredPlaytimeMinutes() - playerPlaytime;
            double progress = (double) playerPlaytime / effect.getRequiredPlaytimeMinutes() * 100;

            ItemStack item =
                    new ItemBuilder(Material.GRAY_DYE)
                            .name("<gray>" + effect.getDisplayName())
                            .lore(
                                    "",
                                    "<red>LOCKED",
                                    "",
                                    "<gray>Required: <white>" + effect.formatPlaytimeRequired(),
                                    "<gray>Your playtime: <white>" + formatPlaytime(playerPlaytime),
                                    "",
                                    createProgressBar(progress)
                                            + " <white>"
                                            + String.format("%.1f%%", progress))
                            .build();

            return new GuiItem(
                    item,
                    event -> {
                        viewer.sendMessage(
                                net.serverplugins.api.utils.TextUtil.parse(
                                        "<red>You need <yellow>"
                                                + formatTimeRemaining(remaining)
                                                + " <red>more playtime to unlock "
                                                + effect.getColorTag()
                                                + effect.getDisplayName()
                                                + "<red>!"));
                    });
        }
    }

    /**
     * Spawns a 30-second particle preview burst at the player's location. Creates a ring of
     * particles around the player to demonstrate the effect.
     */
    private void previewEffect(DustEffect effect) {
        final double radius = 2.0;
        final int particlesPerRing = 20;
        final int totalTicks = 30 * 20; // 30 seconds at 20 ticks/second
        final int updateInterval = 3; // Update every 3 ticks for smooth animation

        org.bukkit.scheduler.BukkitTask[] taskHolder = new org.bukkit.scheduler.BukkitTask[1];

        taskHolder[0] =
                plugin.getServer()
                        .getScheduler()
                        .runTaskTimer(
                                plugin,
                                new Runnable() {
                                    int ticksElapsed = 0;

                                    @Override
                                    public void run() {
                                        if (ticksElapsed >= totalTicks || !viewer.isOnline()) {
                                            if (taskHolder[0] != null) {
                                                taskHolder[0].cancel();
                                            }
                                            if (viewer.isOnline()) {
                                                viewer.sendMessage(
                                                        net.serverplugins.api.utils.TextUtil.parse(
                                                                "<gray>Preview ended."));
                                            }
                                            return;
                                        }

                                        org.bukkit.Location playerLoc = viewer.getLocation();
                                        double centerY = playerLoc.getY() + 1.0; // Chest height

                                        // Calculate color for current tick (for animated effects)
                                        org.bukkit.Color color =
                                                effect.getColorAtTick(ticksElapsed);

                                        // Spawn particles in a ring around the player
                                        for (int i = 0; i < particlesPerRing; i++) {
                                            double angle = 2 * Math.PI * i / particlesPerRing;
                                            double x = playerLoc.getX() + radius * Math.cos(angle);
                                            double z = playerLoc.getZ() + radius * Math.sin(angle);

                                            org.bukkit.Particle.DustOptions dust =
                                                    new org.bukkit.Particle.DustOptions(
                                                            color, 1.2f);
                                            viewer.spawnParticle(
                                                    org.bukkit.Particle.DUST,
                                                    x,
                                                    centerY,
                                                    z,
                                                    2, // count
                                                    0.05,
                                                    0.05,
                                                    0.05, // offset
                                                    0, // extra
                                                    dust);
                                        }

                                        ticksElapsed += updateInterval;
                                    }
                                },
                                0L,
                                updateInterval);
    }

    private GuiItem createToggleItem() {
        // Read from profile if in profile mode, otherwise from global
        boolean enabled =
                currentProfile != null
                        ? currentProfile.isParticlesEnabled()
                        : rewardsData.isParticlesEnabled();

        ItemStack item =
                new ItemBuilder(enabled ? Material.LIME_DYE : Material.GRAY_DYE)
                        .name(enabled ? "<green>Particles: ON" : "<red>Particles: OFF")
                        .lore(
                                "",
                                "<gray>Toggle whether you see",
                                "<gray>claim border particles.",
                                "",
                                enabled ? "<yellow>Click to disable" : "<yellow>Click to enable")
                        .glow(enabled)
                        .build();

        return new GuiItem(
                item,
                event -> {
                    if (currentProfile != null) {
                        // Save to active profile
                        currentProfile.setParticlesEnabled(!enabled);
                        plugin.getProfileManager().updateProfile(currentProfile);
                        viewer.sendMessage(
                                net.serverplugins.api.utils.TextUtil.parse(
                                        enabled
                                                ? "<yellow>Profile particles disabled."
                                                : "<green>Profile particles enabled."));
                    } else {
                        // Save to global rewards
                        rewardsManager.setParticlesEnabled(viewer.getUniqueId(), !enabled);
                        viewer.sendMessage(
                                net.serverplugins.api.utils.TextUtil.parse(
                                        enabled
                                                ? "<yellow>Claim particles disabled."
                                                : "<green>Claim particles enabled."));
                    }
                    viewer.closeInventory();
                    new RewardsGui(plugin, viewer).open();
                });
    }

    private GuiItem createStaticModeItem() {
        // Read from profile if in profile mode, otherwise from global
        boolean staticMode =
                currentProfile != null
                        ? currentProfile.isStaticParticleMode()
                        : rewardsData.isStaticParticleMode();
        boolean particlesEnabled =
                currentProfile != null
                        ? currentProfile.isParticlesEnabled()
                        : rewardsData.isParticlesEnabled();

        Material material = staticMode ? Material.AMETHYST_SHARD : Material.BLAZE_POWDER;
        String name = staticMode ? "<light_purple>Static Mode: ON" : "<gold>Static Mode: OFF";

        ItemStack item =
                new ItemBuilder(material)
                        .name(name)
                        .lore(
                                "",
                                "<gray>When enabled, particles display",
                                "<gray>with smooth, non-blinking colors.",
                                "<gray>Animated effects show their base color.",
                                "",
                                staticMode
                                        ? "<yellow>Click for animated particles"
                                        : "<yellow>Click for static particles",
                                "",
                                particlesEnabled ? "" : "<red>Enable particles first!")
                        .glow(staticMode)
                        .build();

        return new GuiItem(
                item,
                event -> {
                    if (!particlesEnabled) {
                        viewer.sendMessage(
                                net.serverplugins.api.utils.TextUtil.parse(
                                        "<red>You must enable particles first before changing this setting."));
                        return;
                    }
                    if (currentProfile != null) {
                        // Save to active profile
                        currentProfile.setStaticParticleMode(!staticMode);
                        plugin.getProfileManager().updateProfile(currentProfile);
                        viewer.sendMessage(
                                net.serverplugins.api.utils.TextUtil.parse(
                                        staticMode
                                                ? "<gold>Profile animations enabled."
                                                : "<light_purple>Profile static mode enabled."));
                    } else {
                        // Save to global rewards
                        rewardsManager.setStaticParticleMode(viewer.getUniqueId(), !staticMode);
                        viewer.sendMessage(
                                net.serverplugins.api.utils.TextUtil.parse(
                                        staticMode
                                                ? "<gold>Particle animations enabled."
                                                : "<light_purple>Static particle mode enabled."));
                    }
                    viewer.closeInventory();
                    new RewardsGui(plugin, viewer).open();
                });
    }

    private GuiItem createProgressItem() {
        DustEffect nextUnlock = rewardsData.getNextLockedDustEffect(playerPlaytime);

        if (nextUnlock == null) {
            ItemStack item =
                    new ItemBuilder(Material.NETHER_STAR)
                            .name("<rainbow>All Rewards Unlocked!")
                            .lore(
                                    "",
                                    "<gray>Congratulations! You've unlocked",
                                    "<gray>every dust color and effect.",
                                    "",
                                    "<gold>Thank you for your dedication!")
                            .glow(true)
                            .build();
            return new GuiItem(item, event -> {});
        }

        long remaining = nextUnlock.getRequiredPlaytimeMinutes() - playerPlaytime;
        double progress = (double) playerPlaytime / nextUnlock.getRequiredPlaytimeMinutes() * 100;

        ItemStack item =
                new ItemBuilder(Material.EXPERIENCE_BOTTLE)
                        .name("<yellow>Next Unlock Progress")
                        .lore(
                                "",
                                "<gray>Next: "
                                        + nextUnlock.getColorTag()
                                        + nextUnlock.getDisplayName(),
                                "",
                                createProgressBar(progress),
                                "",
                                "<gray>Time remaining: <white>" + formatTimeRemaining(remaining))
                        .build();

        return new GuiItem(item, event -> {});
    }

    private String createProgressBar(double percent) {
        int filled = (int) Math.min(20, (percent / 100.0) * 20);
        int empty = 20 - filled;
        return "<gray>[<green>"
                + "█".repeat(filled)
                + "<dark_gray>"
                + "█".repeat(empty)
                + "<gray>]";
    }

    private String formatPlaytime(long minutes) {
        if (minutes < 60) {
            return minutes + " min";
        }
        long hours = minutes / 60;
        long mins = minutes % 60;
        if (mins == 0) {
            return hours + " hr";
        }
        return hours + " hr " + mins + " min";
    }

    private String formatTimeRemaining(long minutes) {
        if (minutes <= 0) return "0 min";
        if (minutes < 60) {
            return minutes + " min";
        }
        long hours = minutes / 60;
        long mins = minutes % 60;
        if (hours >= 24) {
            long days = hours / 24;
            hours = hours % 24;
            if (hours == 0) {
                return days + " day" + (days > 1 ? "s" : "");
            }
            return days + "d " + hours + "h";
        }
        if (mins == 0) {
            return hours + " hr";
        }
        return hours + "h " + mins + "m";
    }

    private GuiItem createClearProfileSettingsItem() {
        DustEffect profileEffect = currentProfile.getSelectedDustEffect();
        boolean hasCustomSettings = profileEffect != null;

        ItemStack item =
                new ItemBuilder(Material.BUCKET)
                        .name("<yellow>Reset to Global")
                        .lore(
                                "",
                                "<gray>Clear this profile's custom",
                                "<gray>particle settings to use",
                                "<gray>your global defaults.",
                                "",
                                hasCustomSettings
                                        ? "<red>Click to reset"
                                        : "<dark_gray>Already using global")
                        .build();

        return new GuiItem(
                item,
                event -> {
                    if (hasCustomSettings) {
                        currentProfile.setSelectedDustEffect(null);
                        currentProfile.setSelectedProfileColor(null);
                        plugin.getProfileManager().updateProfile(currentProfile);
                        viewer.sendMessage(
                                net.serverplugins.api.utils.TextUtil.parse(
                                        "<yellow>Profile "
                                                + currentProfile.getName()
                                                + " <yellow>reset to use global settings."));
                        viewer.closeInventory();
                        new RewardsGui(plugin, viewer).open();
                    } else {
                        viewer.sendMessage(
                                net.serverplugins.api.utils.TextUtil.parse(
                                        "<gray>This profile is already using global settings."));
                    }
                });
    }

    private GuiItem createProfileIndicatorItem() {
        if (currentProfile != null) {
            // Profile context - show active profile info
            ItemStack item =
                    new ItemBuilder(Material.WRITABLE_BOOK)
                            .name("<aqua>Profile Mode")
                            .lore(
                                    "",
                                    "<gray>You are in your claim!",
                                    "<gray>Settings will be saved to:",
                                    "<yellow>" + currentProfile.getName(),
                                    "",
                                    "<gray>Each profile can have",
                                    "<gray>unique particle colors that",
                                    "<gray>automatically switch when",
                                    "<gray>you change profiles.",
                                    "",
                                    "<green>Stand outside claims to",
                                    "<green>edit global settings.")
                            .glow(true)
                            .build();
            return new GuiItem(item, event -> {});
        } else {
            // Global context - show info
            ItemStack item =
                    new ItemBuilder(Material.PAPER)
                            .name("<gray>Global Mode")
                            .lore(
                                    "",
                                    "<gray>Settings will be saved",
                                    "<gray>to your global preferences.",
                                    "",
                                    "<gray>To customize per-profile,",
                                    "<gray>stand in your claim and",
                                    "<gray>open this menu again.")
                            .build();
            return new GuiItem(item, event -> {});
        }
    }
}
