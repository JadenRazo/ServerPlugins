package net.serverplugins.events.events.pinata;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.serverplugins.api.messages.MessageBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.events.EventsConfig;
import net.serverplugins.events.ServerEvents;
import net.serverplugins.events.events.ServerEvent;
import net.serverplugins.events.util.FakePlayer;
import net.serverplugins.events.util.PacketEventsNPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

/**
 * Abstract base class for pinata events. Provides common logic for entity spawning, click handling,
 * glowing effects, and rewards. Subclasses define event-specific behavior through template methods.
 */
public abstract class BasePinataEvent implements ServerEvent, Listener {

    protected final ServerEvents plugin;
    protected final EventsConfig config;
    protected final Random random = new Random();

    // Event state
    protected LivingEntity pinataEntity;
    protected ArmorStand pinataArmorStand;
    protected FakePlayer fakePlayer; // ProtocolLib fake player (deprecated)
    protected PacketEventsNPC packetEventsNPC; // PacketEvents fake player for full skin display
    protected Team glowTeam;
    protected volatile boolean active;
    protected int clicksRequired;
    protected final AtomicInteger currentClicks = new AtomicInteger(0);
    protected final Map<UUID, AtomicInteger> playerClicks = new HashMap<>();
    protected volatile Player breaker;
    protected String targetPlayerName;
    protected BukkitRunnable movementTask;
    protected BukkitTask timeoutTask;

    // UI Feedback state
    protected BossBar progressBossBar;
    protected final Set<Integer> triggeredMilestones = ConcurrentHashMap.newKeySet();
    protected BukkitTask warning30sTask;
    protected BukkitTask warning10sTask;
    protected int lastBossBarUpdateClicks = -1;

    // Anti-spam cooldown: minimum 200ms between clicks per player
    protected static final long CLICK_COOLDOWN_MS = 200;
    protected final Map<UUID, Long> lastClickTime = new ConcurrentHashMap<>();

    // Hit combo system: tracks consecutive hits by the same player
    protected volatile UUID currentComboPlayer = null;
    protected final AtomicInteger currentComboCount = new AtomicInteger(0);
    protected final Map<UUID, Integer> comboBonuses = new ConcurrentHashMap<>();

    // Hit streak tracking: which streak levels have been announced per player
    protected final Map<UUID, Set<Integer>> announcedStreaks = new ConcurrentHashMap<>();

    // Combo bonus thresholds and rewards
    protected static final int COMBO_THRESHOLD_1 = 5;
    protected static final int COMBO_BONUS_1 = 50;
    protected static final int COMBO_THRESHOLD_2 = 10;
    protected static final int COMBO_BONUS_2 = 100;
    protected static final int COMBO_THRESHOLD_3 = 15;
    protected static final int COMBO_BONUS_3 = 200;

    // Synchronization lock for click handling
    protected final Object clickLock = new Object();

    // Cached entity UUID string for team operations
    protected String cachedEntityUuid;

    // Random player appearance system
    protected Player mimickedPlayer = null; // The player being mimicked
    protected List<ItemStack> playerGearSnapshot = new ArrayList<>(); // Snapshot of player's gear
    protected List<ItemStack> configuredEquipmentItems =
            new ArrayList<>(); // Configured equipment items
    protected int currentDeteriorationStage = 0; // Visual deterioration stage
    protected BukkitTask deteriorationTask = null; // Deterioration particle task

    // Last name update tick to throttle updates
    protected int lastNameUpdateClicks = -1;

    // Sound/particle distance threshold (squared for performance)
    protected static final double EFFECT_DISTANCE_SQUARED = 64 * 64; // 64 blocks

    // Boss bar update throttling
    protected final AtomicLong lastBossBarUpdate = new AtomicLong(0);
    protected static final long BOSS_BAR_UPDATE_INTERVAL_MS = 500;

    // Particle scaling thresholds
    protected static final int HIGH_PLAYER_COUNT_THRESHOLD = 20;
    protected static final int VERY_HIGH_PLAYER_COUNT_THRESHOLD = 40;
    protected static final double PARTICLE_CHECK_RANGE = 32.0;
    protected static final double PARTICLE_CHECK_RANGE_SQUARED =
            PARTICLE_CHECK_RANGE * PARTICLE_CHECK_RANGE;

    // TPS thresholds for particle reduction
    protected static final double TPS_REDUCED_THRESHOLD = 18.0;
    protected static final double TPS_SKIP_THRESHOLD = 15.0;

    public BasePinataEvent(ServerEvents plugin) {
        this.plugin = plugin;
        this.config = plugin.getEventsConfig();
    }

    /** Template method: Get the event type. */
    @Override
    public abstract EventType getType();

    /** Template method: Get the display name for this event. */
    @Override
    public abstract String getDisplayName();

    /** Template method: Get the spawn location for the pinata. */
    protected abstract Location getSpawnLocation();

    /** Template method: Calculate the number of clicks required. */
    protected abstract int calculateClicksRequired();

    /** Template method: Get the entity type for the pinata. */
    protected abstract EntityType getPinataEntityType();

    /** Template method: Get the glow color for the pinata. */
    protected abstract org.bukkit.ChatColor getGlowColor();

    /** Template method: Get the team name for glowing effects. */
    protected abstract String getGlowTeamName();

    /**
     * Template method: Get the custom name format for the pinata (legacy format).
     *
     * @param clicks Current click count
     * @param required Required click count
     */
    protected abstract String formatPinataName(int clicks, int required);

    /** Get formatted nametag with progress bar (if enabled). */
    protected String getFormattedNametag(int clicks, int required) {
        // Check if progress bar is enabled
        if (!config.isProgressBarEnabled()) {
            return formatPinataName(clicks, required);
        }

        int percent = (clicks * 100) / required;

        // Build progress bar (filled = remaining health, empty = damage taken)
        int barLength = config.getProgressBarLength(); // 10 default
        int filledBars = barLength - ((clicks * barLength) / required);
        String filledChar = config.getProgressBarFilledChar();
        String emptyChar = config.getProgressBarEmptyChar();

        StringBuilder progressBar = new StringBuilder();
        progressBar.append(config.getProgressBarFilledColor());
        for (int i = 0; i < filledBars; i++) {
            progressBar.append(filledChar);
        }
        progressBar.append(config.getProgressBarEmptyColor());
        for (int i = filledBars; i < barLength; i++) {
            progressBar.append(emptyChar);
        }

        // Format: "&6&lPINATA &f[{clicks}/{total}] {bar}"
        return config.getNametagFormat()
                .replace("{clicks}", String.valueOf(clicks))
                .replace("{total}", String.valueOf(required))
                .replace("{percent}", percent + "%")
                .replace("{bar}", progressBar.toString());
    }

    /** Template method: Get the armor material for player skin mode. */
    protected abstract Material getArmorMaterial();

    /** Template method: Get the timeout in seconds. */
    protected abstract int getTimeoutSeconds();

    /** Template method: Broadcast the event start messages. */
    protected abstract void broadcastStartMessages();

    /** Template method: Broadcast the event break messages. */
    protected abstract void broadcastBreakMessages();

    /** Template method: Distribute rewards to participants. */
    protected abstract void distributeRewards();

    /** Template method: Get the teleport command for this event. */
    protected abstract String getTeleportCommand();

    /** Template method: Get the teleport hover text color. */
    protected abstract NamedTextColor getTeleportColor();

    /**
     * Template method: Get the boss bar color for this event. Override in subclasses for different
     * colors (e.g., YELLOW for regular, PINK for premium).
     */
    protected abstract BossBar.Color getBarColor();

    /** Template method: Get the particle type for movement effects. */
    protected Particle getMovementParticle() {
        return Particle.FIREWORK;
    }

    /** Template method: Get the secondary particle type. */
    protected Particle getSecondaryParticle() {
        return Particle.TOTEM_OF_UNDYING;
    }

    /** Template method: Get the number of movement particles. */
    protected int getMovementParticleCount() {
        return 3;
    }

    /** Template method: Get the number of burst particles. */
    protected int getBurstParticleCount() {
        return 8;
    }

    /**
     * Template method: Get the minimum number of online players required to start the event.
     * Override in subclasses for different requirements (e.g., 2 for regular, 3 for premium).
     *
     * @return Minimum player count (default 1)
     */
    @Override
    public int getMinimumPlayers() {
        return 1;
    }

    @Override
    public void start() {
        if (active) {
            plugin.getLogger()
                    .warning(
                            "["
                                    + getDisplayName().toUpperCase()
                                    + "] Event already active, cannot start again");
            return;
        }

        plugin.getLogger().info("[" + getDisplayName().toUpperCase() + "] Starting event...");

        // Check minimum players requirement
        int minPlayers = getMinimumPlayers();
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        plugin.getLogger()
                .info(
                        "["
                                + getDisplayName().toUpperCase()
                                + "] Player check: "
                                + onlinePlayers
                                + " online, "
                                + minPlayers
                                + " required");

        if (onlinePlayers < minPlayers) {
            plugin.getLogger()
                    .warning(
                            "["
                                    + getDisplayName().toUpperCase()
                                    + "] Not enough players online. Required: "
                                    + minPlayers
                                    + ", Online: "
                                    + onlinePlayers);
            return;
        }

        active = true;
        plugin.getLogger().info("[" + getDisplayName().toUpperCase() + "] Event marked as active");
        currentClicks.set(0);
        playerClicks.clear();
        breaker = null;
        targetPlayerName = null;
        lastNameUpdateClicks = -1;
        cachedEntityUuid = null;
        lastBossBarUpdate.set(0);
        triggeredMilestones.clear();
        lastBossBarUpdateClicks = -1;

        // Clear anti-spam and combo tracking
        lastClickTime.clear();
        currentComboPlayer = null;
        currentComboCount.set(0);
        comboBonuses.clear();
        announcedStreaks.clear();

        clicksRequired = calculateClicksRequired();

        Location spawnLoc = getSpawnLocation();
        if (spawnLoc == null) {
            plugin.getLogger()
                    .severe(
                            "["
                                    + getDisplayName().toUpperCase()
                                    + "] ERROR: Spawn location is null!");
            stop();
            return;
        }
        if (spawnLoc.getWorld() == null) {
            plugin.getLogger()
                    .severe(
                            "["
                                    + getDisplayName().toUpperCase()
                                    + "] ERROR: World not loaded! Location: "
                                    + spawnLoc);
            stop();
            return;
        }

        plugin.getLogger()
                .info(
                        "["
                                + getDisplayName().toUpperCase()
                                + "] Spawn location: "
                                + spawnLoc.getWorld().getName()
                                + " @ "
                                + spawnLoc.getBlockX()
                                + ", "
                                + spawnLoc.getBlockY()
                                + ", "
                                + spawnLoc.getBlockZ());

        // Register event listeners
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger()
                .info("[" + getDisplayName().toUpperCase() + "] Event listeners registered");

        boolean usePlayerSkin = config.isPinataUsePlayerSkin();
        plugin.getLogger()
                .info(
                        "["
                                + getDisplayName().toUpperCase()
                                + "] Using player skin mode: "
                                + usePlayerSkin);

        try {
            if (usePlayerSkin) {
                spawnPlayerSkinPinata(spawnLoc);
            } else {
                spawnEntityPinata(spawnLoc);
            }
        } catch (Exception e) {
            plugin.getLogger()
                    .severe(
                            "["
                                    + getDisplayName().toUpperCase()
                                    + "] EXCEPTION during entity spawn:");
            plugin.getLogger()
                    .severe(
                            "["
                                    + getDisplayName().toUpperCase()
                                    + "] "
                                    + e.getClass().getName()
                                    + ": "
                                    + e.getMessage());
            e.printStackTrace();
            stop();
            return;
        }

        if (pinataEntity == null) {
            plugin.getLogger()
                    .severe(
                            "["
                                    + getDisplayName().toUpperCase()
                                    + "] Entity spawn failed! pinataEntity is null");
            stop();
            return;
        }

        plugin.getLogger()
                .info(
                        "["
                                + getDisplayName().toUpperCase()
                                + "] Entity spawned successfully: "
                                + pinataEntity.getType()
                                + " at "
                                + pinataEntity.getLocation());

        // Cache UUID string for team operations
        cachedEntityUuid = pinataEntity.getUniqueId().toString();

        // Setup glowing effect
        setupGlowingEffect(pinataEntity);

        // Start dynamic movement
        startDynamicMovement();

        // Broadcast start messages
        broadcastStartMessages();

        // Play start sound for players in the same world (server-wide celebration for start)
        playToNearbyPlayers(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f, 0);

        // Create boss bar for progress tracking (with initial progress)
        createBossBar();

        // Immediately show initial boss bar state to all players
        if (progressBossBar != null && pinataEntity != null && pinataEntity.getWorld() != null) {
            for (Player player : pinataEntity.getWorld().getPlayers()) {
                player.showBossBar(progressBossBar);
            }
            plugin.getLogger()
                    .info(
                            "["
                                    + getDisplayName().toUpperCase()
                                    + "] Boss bar shown to "
                                    + pinataEntity.getWorld().getPlayers().size()
                                    + " players");
        }

        // Schedule timeout warnings
        int timeout = getTimeoutSeconds();
        scheduleTimeoutWarnings(timeout);

        // Schedule timeout
        timeoutTask =
                Bukkit.getScheduler()
                        .runTaskLater(
                                plugin,
                                () -> {
                                    if (active && pinataEntity != null) {
                                        for (Player p : Bukkit.getOnlinePlayers()) {
                                            MessageBuilder.create()
                                                    .error(
                                                            "The pinata has disappeared! Nobody broke it in time!")
                                                    .send(p);
                                        }
                                        plugin.getLogger()
                                                .info(
                                                        "["
                                                                + getDisplayName().toUpperCase()
                                                                + "] Pinata timed out after "
                                                                + timeout
                                                                + " seconds");
                                        stop();
                                    }
                                },
                                20L * timeout);
    }

    /** Spawn a regular entity pinata. */
    protected void spawnEntityPinata(Location spawnLoc) {
        pinataEntity =
                (LivingEntity)
                        spawnLoc.getWorld()
                                .spawnEntity(
                                        spawnLoc,
                                        getPinataEntityType(),
                                        CreatureSpawnEvent.SpawnReason.CUSTOM,
                                        entity -> {
                                            LivingEntity living = (LivingEntity) entity;
                                            living.setHealth(living.getMaxHealth());
                                            living.setAI(true);
                                            living.setSilent(true);
                                            living.setCustomName(
                                                    getFormattedNametag(0, clicksRequired));
                                            living.setCustomNameVisible(true);
                                            living.setPersistent(true);
                                            living.setRemoveWhenFarAway(false);

                                            if (living instanceof Ageable) {
                                                ((Ageable) living).setAdult();
                                            }
                                        });
    }

    /**
     * Spawn a player skin pinata. Priority: PacketEventsNPC (full player model) > ArmorStand with
     * head (fallback)
     */
    protected void spawnPlayerSkinPinata(Location spawnLoc) {
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        plugin.getLogger()
                .info(
                        "[PINATA] Spawning player skin pinata. Online players: "
                                + onlinePlayers.size());

        if (onlinePlayers.isEmpty()) {
            plugin.getLogger()
                    .warning("[PINATA] No players online, falling back to regular entity");
            spawnEntityPinata(spawnLoc);
            return;
        }

        Player targetPlayer = onlinePlayers.get(random.nextInt(onlinePlayers.size()));
        targetPlayerName = targetPlayer.getName();
        mimickedPlayer = targetPlayer;
        plugin.getLogger().info("[PINATA] Selected target player: " + targetPlayerName);

        // Try PacketEvents first (full player model with proper skin)
        boolean packetEventsAvailable = isPacketEventsAvailable();
        plugin.getLogger().info("[PINATA] PacketEvents available: " + packetEventsAvailable);

        if (packetEventsAvailable) {
            plugin.getLogger().info("[PINATA] Using PacketEvents NPC for player skin");
            spawnPacketEventsNPC(spawnLoc, targetPlayer);
        } else {
            // Fallback to armor stand with player head
            plugin.getLogger()
                    .info("[PINATA] PacketEvents not available, using armor stand fallback");
            spawnArmorStandPinata(spawnLoc, targetPlayer);
        }
    }

    /** Check if ProtocolLib is available. */
    protected boolean isProtocolLibAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("ProtocolLib");
    }

    /** Check if PacketEvents is available and initialized. */
    protected boolean isPacketEventsAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("packetevents")
                && PacketEventsNPC.isAvailable();
    }

    /**
     * Spawn a pinata using PacketEvents NPC for full player skin display. This creates a true
     * player entity with complete skin rendering.
     */
    protected void spawnPacketEventsNPC(Location spawnLoc, Player targetPlayer) {
        plugin.getLogger()
                .info("[PINATA] Spawning PacketEvents NPC with skin of " + targetPlayer.getName());

        // Check if this is a random player appearance with gear drops
        boolean shouldCaptureGear =
                config.isRandomPlayerAppearanceEnabled()
                        && config.isDropPlayerGear()
                        && random.nextFloat() < config.getRandomPlayerAppearanceChance();

        if (shouldCaptureGear) {
            // Capture player's gear for later distribution
            capturePlayerGearSnapshot(targetPlayer);
        }

        // Create the PacketEvents NPC with the target's skin
        packetEventsNPC =
                new PacketEventsNPC(
                        plugin, targetPlayer, spawnLoc, getFormattedNametag(0, clicksRequired));
        packetEventsNPC.setGlowing(true);

        // Set up interaction handler for hit detection
        packetEventsNPC.setInteractionHandler(
                player -> {
                    if (active) {
                        handlePinataClick(player);
                    }
                });

        // Spawn for all players
        packetEventsNPC.spawnForAll();

        // Apply equipment based on whether we're mimicking a player or using configured equipment
        if (mimickedPlayer != null) {
            // Apply player's actual equipment to NPC
            applyPlayerEquipmentToNPC(targetPlayer);
        } else {
            // Apply configured equipment
            applyConfiguredEquipment();
        }

        // Create an invisible armor stand for physics and hit detection
        // NOT invulnerable - we cancel damage via events so EntityDamageByEntityEvent fires for all
        pinataArmorStand =
                spawnLoc.getWorld()
                        .spawn(
                                spawnLoc,
                                ArmorStand.class,
                                stand -> {
                                    stand.setInvulnerable(false);
                                    stand.setGravity(true); // Enable gravity for jumping physics
                                    stand.setVisible(false); // Invisible - NPC handles visuals
                                    stand.setBasePlate(false);
                                    stand.setMarker(false); // Not a marker - needs hitbox
                                    stand.setSmall(false);
                                    stand.setCustomNameVisible(false);
                                    stand.setPersistent(true);
                                    stand.setSilent(true);
                                });

        pinataEntity = pinataArmorStand;

        plugin.getLogger()
                .info(
                        "[PINATA] Successfully spawned PacketEvents NPC at "
                                + spawnLoc.getX()
                                + ", "
                                + spawnLoc.getY()
                                + ", "
                                + spawnLoc.getZ());

        // Add NPC to glow team for colored glow effect
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = getGlowTeamName();
        Team glowTeamTemp = scoreboard.getTeam(teamName);
        if (glowTeamTemp == null) {
            glowTeamTemp = scoreboard.registerNewTeam(teamName);
        }
        glowTeamTemp.setColor(getGlowColor());
        glowTeamTemp.addEntry(packetEventsNPC.getUniqueId().toString());
        plugin.getLogger()
                .info("[PINATA] Added PacketEvents NPC to glow team with color " + getGlowColor());
    }

    /**
     * Spawn a pinata using ProtocolLib fake player for full skin display.
     *
     * @deprecated Use spawnPacketEventsNPC instead for MC 1.21+
     */
    @Deprecated
    protected void spawnFakePlayerPinata(Location spawnLoc, Player targetPlayer) {
        plugin.getLogger()
                .info(
                        "[PINATA] Attempting to spawn fake player pinata with skin of "
                                + targetPlayer.getName());

        // Create the fake player with the target's skin
        fakePlayer =
                new FakePlayer(plugin, targetPlayer, spawnLoc, formatPinataName(0, clicksRequired));
        fakePlayer.setGlowing(true);
        fakePlayer.spawnForAll();

        // Create an invisible armor stand for hit detection (fake players are packet-based)
        // The armor stand is INVISIBLE because the FakePlayer handles rendering
        // IMPORTANT: Gravity must be TRUE for velocity-based movement to work!
        pinataArmorStand =
                spawnLoc.getWorld()
                        .spawn(
                                spawnLoc,
                                ArmorStand.class,
                                stand -> {
                                    stand.setInvulnerable(true);
                                    stand.setGravity(
                                            true); // MUST be true for velocity/jumping to work!
                                    stand.setVisible(
                                            false); // Invisible - FakePlayer handles visuals
                                    stand.setBasePlate(false);
                                    stand.setMarker(false); // Not a marker so it has a hitbox
                                    stand.setSmall(false);
                                    stand.setCustomNameVisible(false);
                                    stand.setPersistent(true);
                                });

        pinataEntity = pinataArmorStand;

        plugin.getLogger()
                .info(
                        "[PINATA] Successfully spawned fake player pinata with hitbox armor stand at "
                                + spawnLoc.getX()
                                + ", "
                                + spawnLoc.getY()
                                + ", "
                                + spawnLoc.getZ());
    }

    /**
     * Spawn a pinata using armor stand with player head. This is the primary method for player skin
     * mode in MC 1.21.
     */
    protected void spawnArmorStandPinata(Location spawnLoc, Player targetPlayer) {
        plugin.getLogger()
                .info(
                        "[PINATA] Spawning armor stand pinata with head of "
                                + targetPlayer.getName());

        pinataArmorStand =
                spawnLoc.getWorld()
                        .spawn(
                                spawnLoc,
                                ArmorStand.class,
                                stand -> {
                                    // NOT invulnerable - we cancel damage via events so
                                    // EntityDamageByEntityEvent fires
                                    stand.setInvulnerable(false);
                                    stand.setGravity(
                                            true); // MUST be true for velocity/jumping to work!
                                    stand.setVisible(true); // Visible armor stand with player head
                                    stand.setBasePlate(false);
                                    stand.setArms(true);
                                    stand.setSmall(false); // Full size armor stand
                                    stand.setCustomName(getFormattedNametag(0, clicksRequired));
                                    stand.setCustomNameVisible(true);
                                    stand.setPersistent(true);
                                    stand.setMarker(
                                            false); // Not a marker - needs hitbox for hit detection
                                    stand.setCanTick(true); // Allow ticking for physics
                                    stand.setCanMove(true); // Allow movement for jumping

                                    // Create player skull using PlayerProfile API
                                    ItemStack skull = createPlayerSkull(targetPlayer);
                                    stand.getEquipment().setHelmet(skull);

                                    // Set armor based on event type
                                    Material armorMaterial = getArmorMaterial();
                                    stand.getEquipment()
                                            .setChestplate(new ItemStack(armorMaterial));
                                    stand.getEquipment()
                                            .setLeggings(
                                                    new ItemStack(
                                                            getLeggingsMaterial(armorMaterial)));
                                    stand.getEquipment()
                                            .setBoots(
                                                    new ItemStack(getBootsMaterial(armorMaterial)));

                                    // Enable glowing
                                    stand.setGlowing(true);
                                });

        pinataEntity = pinataArmorStand;

        plugin.getLogger()
                .info(
                        "[PINATA] Armor stand pinata spawned at "
                                + spawnLoc.getX()
                                + ", "
                                + spawnLoc.getY()
                                + ", "
                                + spawnLoc.getZ());
    }

    /**
     * Create a player skull item using the proper PlayerProfile API. This avoids the
     * ConcurrentModificationException issues with NMS.
     */
    protected ItemStack createPlayerSkull(Player player) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);

        // Use editMeta with SkullMeta.class for thread-safe modification
        skull.editMeta(
                SkullMeta.class,
                skullMeta -> {
                    // Get the player's profile which already has textures loaded
                    PlayerProfile profile = player.getPlayerProfile();

                    // If profile has textures, use it directly
                    if (profile.getTextures().getSkin() != null) {
                        skullMeta.setOwnerProfile(profile);
                    } else {
                        // Fallback: create profile with player's UUID and name
                        // The server will resolve textures when needed
                        PlayerProfile newProfile =
                                Bukkit.createProfile(player.getUniqueId(), player.getName());
                        skullMeta.setOwnerProfile(newProfile);
                    }
                });

        return skull;
    }

    /**
     * Create a skull with a custom texture URL. Texture must be from textures.minecraft.net domain.
     */
    protected ItemStack createCustomSkull(String textureUrl) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);

        skull.editMeta(
                SkullMeta.class,
                skullMeta -> {
                    PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
                    PlayerTextures textures = profile.getTextures();

                    try {
                        textures.setSkin(new URL(textureUrl));
                    } catch (MalformedURLException e) {
                        plugin.getLogger().warning("Invalid texture URL: " + textureUrl);
                        return;
                    }

                    profile.setTextures(textures);
                    skullMeta.setOwnerProfile(profile);
                });

        return skull;
    }

    /** Get leggings material matching the chestplate. */
    private Material getLeggingsMaterial(Material chestplate) {
        return switch (chestplate) {
            case DIAMOND_CHESTPLATE -> Material.DIAMOND_LEGGINGS;
            case IRON_CHESTPLATE -> Material.IRON_LEGGINGS;
            case GOLDEN_CHESTPLATE -> Material.GOLDEN_LEGGINGS;
            case NETHERITE_CHESTPLATE -> Material.NETHERITE_LEGGINGS;
            case CHAINMAIL_CHESTPLATE -> Material.CHAINMAIL_LEGGINGS;
            default -> Material.LEATHER_LEGGINGS;
        };
    }

    /** Get boots material matching the chestplate. */
    private Material getBootsMaterial(Material chestplate) {
        return switch (chestplate) {
            case DIAMOND_CHESTPLATE -> Material.DIAMOND_BOOTS;
            case IRON_CHESTPLATE -> Material.IRON_BOOTS;
            case GOLDEN_CHESTPLATE -> Material.GOLDEN_BOOTS;
            case NETHERITE_CHESTPLATE -> Material.NETHERITE_BOOTS;
            case CHAINMAIL_CHESTPLATE -> Material.CHAINMAIL_BOOTS;
            default -> Material.LEATHER_BOOTS;
        };
    }

    /**
     * Setup glowing effect for the pinata entity. Reuses existing team if available to avoid memory
     * leaks.
     */
    protected void setupGlowingEffect(LivingEntity entity) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = getGlowTeamName();

        // Reuse existing team or create new one
        glowTeam = scoreboard.getTeam(teamName);
        if (glowTeam == null) {
            glowTeam = scoreboard.registerNewTeam(teamName);
        } else {
            // Clear old entries
            for (String entry : new HashSet<>(glowTeam.getEntries())) {
                glowTeam.removeEntry(entry);
            }
        }

        glowTeam.setColor(getGlowColor());
        glowTeam.addEntry(entity.getUniqueId().toString());
        entity.setGlowing(true);
    }

    @Override
    public void stop() {
        if (!active) return;

        active = false;

        // Cancel tasks
        if (movementTask != null) {
            movementTask.cancel();
            movementTask = null;
        }

        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }

        // Cancel warning tasks
        if (warning30sTask != null) {
            warning30sTask.cancel();
            warning30sTask = null;
        }
        if (warning10sTask != null) {
            warning10sTask.cancel();
            warning10sTask = null;
        }

        // Cancel deterioration task
        if (deteriorationTask != null) {
            deteriorationTask.cancel();
            deteriorationTask = null;
        }

        // Remove boss bar from all players
        if (progressBossBar != null) {
            if (pinataEntity != null && pinataEntity.getWorld() != null) {
                for (Player player : pinataEntity.getWorld().getPlayers()) {
                    player.hideBossBar(progressBossBar);
                }
            }
            progressBossBar = null;
        }

        // Clear milestone tracking
        triggeredMilestones.clear();

        // Clear anti-spam and combo tracking
        lastClickTime.clear();
        currentComboPlayer = null;
        currentComboCount.set(0);
        comboBonuses.clear();
        announcedStreaks.clear();

        // Unregister listeners
        HandlerList.unregisterAll(this);

        // Cleanup glow team
        if (glowTeam != null) {
            try {
                glowTeam.unregister();
            } catch (IllegalStateException ignored) {
                // Team already unregistered
            }
            glowTeam = null;
        }

        // Remove PacketEvents NPC if used
        if (packetEventsNPC != null) {
            try {
                packetEventsNPC.remove();
            } catch (Exception e) {
                plugin.getLogger()
                        .warning("[PINATA] Failed to remove PacketEvents NPC: " + e.getMessage());
            }
            packetEventsNPC = null;
        }

        // Remove fake player if used (deprecated ProtocolLib approach)
        if (fakePlayer != null) {
            try {
                fakePlayer.remove();
            } catch (Exception e) {
                plugin.getLogger()
                        .warning("[PINATA] Failed to remove fake player: " + e.getMessage());
            }
            fakePlayer = null;
        }

        // Remove entity
        if (pinataEntity != null && !pinataEntity.isDead()) {
            pinataEntity.remove();
        }
        pinataEntity = null;
        pinataArmorStand = null;
        cachedEntityUuid = null;

        // Clear event reference
        plugin.getEventManager().clearActiveEvent();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!active || pinataEntity == null) return;
        if (event.getEntity().getUniqueId().equals(pinataEntity.getUniqueId())) {
            event.setCancelled(false);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (!active || pinataEntity == null) return;
        if (event.getEntity().getUniqueId().equals(pinataEntity.getUniqueId())) {
            event.setCancelled(false);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!active || pinataEntity == null) return;
        if (!event.getEntity().equals(pinataEntity)) return;

        event.setCancelled(true);

        if (!(event.getDamager() instanceof Player player)) return;

        handlePinataClick(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onAnyEntityDamage(EntityDamageEvent event) {
        if (!active || pinataEntity == null) return;
        if (!event.getEntity().equals(pinataEntity)) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onArmorStandInteract(PlayerInteractAtEntityEvent event) {
        if (!active || pinataArmorStand == null) return;
        if (!event.getRightClicked().equals(pinataArmorStand)) return;

        event.setCancelled(true);
        handlePinataClick(event.getPlayer());
    }

    @EventHandler
    public void onArmorStandPunch(PlayerInteractEntityEvent event) {
        if (!active || pinataArmorStand == null) return;
        if (!event.getRightClicked().equals(pinataArmorStand)) return;

        // This catches general entity interactions including punches
        event.setCancelled(true);
        handlePinataClick(event.getPlayer());
    }

    /**
     * Handle a pinata click with proper synchronization. Uses atomic operations and synchronized
     * blocks to prevent race conditions. Includes anti-spam cooldown check (200ms) BEFORE the
     * synchronized block for efficiency.
     */
    protected void handlePinataClick(Player player) {
        UUID playerId = player.getUniqueId();

        // Anti-spam cooldown check - BEFORE synchronized block for efficiency
        long now = System.currentTimeMillis();
        Long lastClick = lastClickTime.get(playerId);
        if (lastClick != null && (now - lastClick) < CLICK_COOLDOWN_MS) {
            // Player clicking too fast, ignore silently
            return;
        }
        lastClickTime.put(playerId, now);

        int clicks;
        int playerHits;
        boolean shouldBreak;
        int comboCount = 0;

        synchronized (clickLock) {
            // Increment clicks atomically
            clicks = currentClicks.incrementAndGet();

            // Track player clicks
            playerHits =
                    playerClicks
                            .computeIfAbsent(playerId, k -> new AtomicInteger(0))
                            .incrementAndGet();

            // Handle combo system: track consecutive hits by same player
            if (playerId.equals(currentComboPlayer)) {
                comboCount = currentComboCount.incrementAndGet();
            } else {
                // Different player hit - reset combo
                currentComboPlayer = playerId;
                currentComboCount.set(1);
                comboCount = 1;
            }

            // Check if should break
            shouldBreak = clicks >= clicksRequired && breaker == null;
            if (shouldBreak) {
                breaker = player;
            }
        }

        // Process combo bonuses and streak announcements outside the sync block
        processComboRewards(player, comboCount);

        // Play hurt animation on hit
        if (packetEventsNPC != null && config.isHurtAnimationEnabled()) {
            packetEventsNPC.playHurtAnimation();
        }

        // Play arm swing response
        if (packetEventsNPC != null && config.isArmSwingOnHitEnabled()) {
            packetEventsNPC.playArmSwing(random.nextBoolean());
        }

        // Critical hit effect (15% chance by default)
        if (packetEventsNPC != null && random.nextFloat() < config.getCriticalHitChance()) {
            playCriticalHitEffect(player);
        }

        // Shake effect for visual feedback
        if (config.isShakeOnHitEnabled()) {
            applyShakeEffect();
        }

        // Update name tag only every 5 clicks to reduce packet spam
        if (pinataEntity != null && (clicks - lastNameUpdateClicks >= 5 || shouldBreak)) {
            lastNameUpdateClicks = clicks;
            String newName = getFormattedNametag(clicks, clicksRequired);
            pinataEntity.setCustomName(newName);

            // Also update PacketEvents NPC display name if using it
            if (packetEventsNPC != null) {
                packetEventsNPC.setDisplayName(newName);
            }

            // Also update fake player display name if using it (deprecated)
            if (fakePlayer != null) {
                fakePlayer.setDisplayName(newName);
            }
        }

        // Update boss bar (update every click for accurate progress tracking)
        if (progressBossBar != null) {
            updateBossBar(clicks);
        }

        // Send action bar feedback every 3rd hit from this player
        if (playerHits % 3 == 0) {
            player.sendActionBar(
                    Component.text()
                            .append(Component.text("You hit the pinata! ", NamedTextColor.GOLD))
                            .append(
                                    Component.text(
                                            "(" + playerHits + " total hits)", NamedTextColor.GRAY))
                            .build());
        }

        // Check for milestone sounds (25%, 50%, 75%)
        checkAndPlayMilestoneSound(clicks);

        // Play hit sound and particles for nearby players (within 64 blocks)
        if (pinataEntity != null) {
            Location loc = pinataEntity.getLocation();

            // Play sound only to players within 64 blocks in the same world
            playToNearbyPlayers(Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.5f, 1.2f, 64.0);

            // Spawn hit particles (scaled based on player count and TPS)
            int scaledHitParticles = scaleParticleCount(10);
            if (scaledHitParticles > 0) {
                loc.getWorld()
                        .spawnParticle(
                                Particle.CRIT,
                                loc.clone().add(0, 1, 0),
                                scaledHitParticles,
                                0.3,
                                0.3,
                                0.3,
                                0.1);
            }
        }

        // Break the pinata if conditions met
        if (shouldBreak) {
            breakPinata();
        }
    }

    /**
     * Determine pinata skin source based on configuration. Returns the player to use for skin, or
     * null for entity mode.
     */
    protected Player determineSkinSource() {
        String skinMode = config.getPinataSkinMode();

        // Check random player appearance chance
        if (skinMode.equals("RANDOM_ONLINE") && config.isRandomPlayerAppearanceEnabled()) {
            float chance = (float) config.getRandomPlayerAppearanceChance();
            if (random.nextFloat() < chance) {
                // 35% chance (default) to use random online player with their gear
                return selectRandomOnlinePlayer();
            }
        }

        // Normal skin mode logic
        if (skinMode.equals("RANDOM_ONLINE")) {
            return selectRandomOnlinePlayer();
        } else if (skinMode.equals("SPECIFIC_PLAYER")) {
            String playerName = config.getPinataSkinPlayer();
            return Bukkit.getPlayerExact(playerName);
        }
        // THEMED mode returns null (handle separately)
        return null;
    }

    /** Select random online player, excluding configured players. */
    protected Player selectRandomOnlinePlayer() {
        List<String> excludeList = config.getExcludedPlayers();
        List<Player> eligible = new ArrayList<>();

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!excludeList.contains(p.getName())) {
                eligible.add(p);
            }
        }

        if (eligible.isEmpty()) return null;
        return eligible.get(random.nextInt(eligible.size()));
    }

    /**
     * Capture snapshot of player's current equipment. Used for dropping items when pinata breaks.
     */
    protected void capturePlayerGearSnapshot(Player player) {
        playerGearSnapshot.clear();
        mimickedPlayer = player;

        // Capture armor
        ItemStack helmet = player.getInventory().getHelmet();
        ItemStack chest = player.getInventory().getChestplate();
        ItemStack legs = player.getInventory().getLeggings();
        ItemStack boots = player.getInventory().getBoots();

        if (helmet != null && helmet.getType() != Material.AIR) {
            playerGearSnapshot.add(helmet.clone());
        }
        if (chest != null && chest.getType() != Material.AIR) {
            playerGearSnapshot.add(chest.clone());
        }
        if (legs != null && legs.getType() != Material.AIR) {
            playerGearSnapshot.add(legs.clone());
        }
        if (boots != null && boots.getType() != Material.AIR) {
            playerGearSnapshot.add(boots.clone());
        }

        // Capture held items
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        if (mainHand != null && mainHand.getType() != Material.AIR) {
            playerGearSnapshot.add(mainHand.clone());
        }
        if (offHand != null && offHand.getType() != Material.AIR) {
            playerGearSnapshot.add(offHand.clone());
        }

        // Announce if configured
        if (config.isAnnounceRandomPlayer()) {
            String message =
                    config.getRandomPlayerAnnounceMessage().replace("{player}", player.getName());
            Bukkit.broadcast(TextUtil.parse(message));
        }

        plugin.getLogger()
                .info(
                        "[Pinata] Mimicking player: "
                                + player.getName()
                                + " with "
                                + playerGearSnapshot.size()
                                + " items");
    }

    /** Apply player's actual equipment to the PacketEventsNPC. */
    protected void applyPlayerEquipmentToNPC(Player player) {
        if (packetEventsNPC == null) return;

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        ItemStack helmet = player.getInventory().getHelmet();
        ItemStack chestplate = player.getInventory().getChestplate();
        ItemStack leggings = player.getInventory().getLeggings();
        ItemStack boots = player.getInventory().getBoots();

        // Apply all equipment at once for efficiency
        packetEventsNPC.setFullEquipment(mainHand, offHand, helmet, chestplate, leggings, boots);
    }

    /** Apply configured equipment to the PacketEventsNPC. */
    protected void applyConfiguredEquipment() {
        if (packetEventsNPC == null || !config.isPinataEquipmentEnabled()) return;

        configuredEquipmentItems.clear();

        // Setup equipment if enabled
        Material mainHandMat = config.getPinataMainHandMaterial();
        ItemStack sword = new ItemStack(mainHandMat);

        if (config.isPinataEnchantGlow()) {
            sword.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.SHARPNESS, 1);
        }

        packetEventsNPC.setMainHand(sword);
        configuredEquipmentItems.add(sword.clone());

        if (config.isPinataOffHandEnabled()) {
            Material offHandMat = config.getPinataOffHandMaterial();
            ItemStack offHandItem = new ItemStack(offHandMat);
            packetEventsNPC.setOffHand(offHandItem);
            configuredEquipmentItems.add(offHandItem.clone());
        }
    }

    /**
     * Get all items that should be distributed when pinata breaks. Combines player gear snapshot +
     * configured equipment items.
     */
    protected List<ItemStack> getDroppableItems() {
        List<ItemStack> items = new ArrayList<>();

        // Add player gear if available and enabled
        if (config.isDropPlayerGear() && !playerGearSnapshot.isEmpty()) {
            items.addAll(playerGearSnapshot);
        }

        // Add configured equipment if enabled
        if (config.isItemDropsEnabled() && config.isDropHeldItems()) {
            items.addAll(configuredEquipmentItems);
        }

        return items;
    }

    /** Distribute items to participants using same logic as coin distribution. */
    protected void distributeItems() {
        List<ItemStack> items = getDroppableItems();
        if (items.isEmpty()) return;

        boolean breakerOnly = config.isItemDropsBreakerOnly();
        boolean proportional = config.isItemDropsProportional();
        float dropChance = (float) config.getItemDropChance();

        if (breakerOnly && breaker != null) {
            // All items go to breaker
            for (ItemStack item : items) {
                if (random.nextFloat() < dropChance) {
                    giveItemToPlayer(breaker, item);
                }
            }
        } else if (proportional) {
            // Distribute proportionally like coins
            int totalClicks = currentClicks.get();
            Map<UUID, Integer> eligiblePlayers = new HashMap<>();
            for (Map.Entry<UUID, AtomicInteger> entry : playerClicks.entrySet()) {
                eligiblePlayers.put(entry.getKey(), entry.getValue().get());
            }

            for (ItemStack item : items) {
                if (random.nextFloat() < dropChance) {
                    // Select random player weighted by their hit count
                    Player recipient = selectWeightedRandomPlayer(eligiblePlayers, totalClicks);
                    if (recipient != null) {
                        giveItemToPlayer(recipient, item);
                    }
                }
            }
        } else {
            // Random distribution (equal chance)
            List<UUID> participants = new ArrayList<>(playerClicks.keySet());
            for (ItemStack item : items) {
                if (random.nextFloat() < dropChance) {
                    UUID randomUuid = participants.get(random.nextInt(participants.size()));
                    Player recipient = Bukkit.getPlayer(randomUuid);
                    if (recipient != null) {
                        giveItemToPlayer(recipient, item);
                    }
                }
            }
        }
    }

    /** Select random player weighted by their contribution (hit count). */
    protected Player selectWeightedRandomPlayer(Map<UUID, Integer> playerHits, int totalHits) {
        if (totalHits == 0 || playerHits.isEmpty()) return null;

        int randomValue = random.nextInt(totalHits);
        int cumulative = 0;

        for (Map.Entry<UUID, Integer> entry : playerHits.entrySet()) {
            cumulative += entry.getValue();
            if (randomValue < cumulative) {
                return Bukkit.getPlayer(entry.getKey());
            }
        }

        return null; // Fallback
    }

    /** Give item to player with proper messaging. */
    protected void giveItemToPlayer(Player player, ItemStack item) {
        // Try to add to inventory
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);

        if (leftover.isEmpty()) {
            // Success
            player.sendMessage(
                    Component.text()
                            .append(Component.text("✨ ", NamedTextColor.YELLOW))
                            .append(Component.text("You received ", NamedTextColor.GREEN))
                            .append(
                                    Component.translatable(item.getType().translationKey())
                                            .color(NamedTextColor.GOLD))
                            .append(Component.text(" from the pinata!", NamedTextColor.GREEN))
                            .build());

            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
        } else {
            // Inventory full - drop at player location
            player.getWorld().dropItemNaturally(player.getLocation(), item);
            player.sendMessage(
                    Component.text(
                            "Your inventory is full! Item dropped nearby.", NamedTextColor.YELLOW));
        }

        plugin.getLogger()
                .info(
                        "[Pinata] "
                                + player.getName()
                                + " received "
                                + item.getType()
                                + " x"
                                + item.getAmount());
    }

    /** Update visual deterioration based on health stage. */
    protected void updateDeteriorationEffects(int stage) {
        // Cancel old task
        if (deteriorationTask != null) {
            deteriorationTask.cancel();
        }

        if (stage == 0) return;

        // Stage 1: Light smoke
        if (stage == 1) {
            deteriorationTask =
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (!active || pinataEntity == null) {
                                cancel();
                                return;
                            }
                            Location loc = pinataEntity.getLocation().add(0, 1, 0);
                            loc.getWorld()
                                    .spawnParticle(Particle.SMOKE, loc, 2, 0.2, 0.3, 0.2, 0.01);
                        }
                    }.runTaskTimer(plugin, 0L, 20L);
        }
        // Stage 2: Smoke + lava drips
        else if (stage == 2) {
            deteriorationTask =
                    new BukkitRunnable() {
                        private int tick = 0;

                        @Override
                        public void run() {
                            if (!active || pinataEntity == null) {
                                cancel();
                                return;
                            }
                            Location loc = pinataEntity.getLocation().add(0, 1, 0);
                            if (tick % 10 == 0) {
                                loc.getWorld()
                                        .spawnParticle(Particle.SMOKE, loc, 5, 0.3, 0.4, 0.3, 0.02);
                            }
                            if (tick % 15 == 0) {
                                loc.getWorld()
                                        .spawnParticle(Particle.LAVA, loc, 1, 0.2, 0.2, 0.2, 0);
                            }
                            tick++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
        }
        // Stage 3: Heavy smoke, lava, soul fire (CRITICAL)
        else if (stage == 3) {
            Bukkit.broadcast(
                    Component.text(
                            "⚠ CRITICAL! The pinata is nearly broken!",
                            NamedTextColor.RED,
                            TextDecoration.BOLD));

            deteriorationTask =
                    new BukkitRunnable() {
                        private int tick = 0;

                        @Override
                        public void run() {
                            if (!active || pinataEntity == null) {
                                cancel();
                                return;
                            }
                            Location loc = pinataEntity.getLocation().add(0, 1, 0);

                            if (tick % 5 == 0) {
                                loc.getWorld()
                                        .spawnParticle(
                                                Particle.LARGE_SMOKE, loc, 10, 0.4, 0.5, 0.4, 0.05);
                            }
                            if (tick % 10 == 0) {
                                loc.getWorld()
                                        .spawnParticle(Particle.LAVA, loc, 3, 0.3, 0.3, 0.3, 0);
                            }
                            if (tick % 15 == 0) {
                                loc.getWorld()
                                        .spawnParticle(
                                                Particle.SOUL_FIRE_FLAME,
                                                loc,
                                                5,
                                                0.3,
                                                0.4,
                                                0.3,
                                                0.03);
                            }

                            tick++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
        }
    }

    /** Apply shake effect to pinata (quick position jitter). */
    protected void applyShakeEffect() {
        if (pinataEntity == null || packetEventsNPC == null) return;

        Location originalLoc = pinataEntity.getLocation().clone();
        double intensity = config.getShakeIntensity(); // 0.15 default

        // 3-tick shake sequence
        new BukkitRunnable() {
            private int tick = 0;

            @Override
            public void run() {
                if (!active || tick >= 3) {
                    // Return to original position
                    if (packetEventsNPC != null) {
                        packetEventsNPC.teleport(originalLoc);
                    }
                    cancel();
                    return;
                }

                // Random offset
                double offsetX = (random.nextDouble() - 0.5) * intensity;
                double offsetZ = (random.nextDouble() - 0.5) * intensity;
                packetEventsNPC.teleport(originalLoc.clone().add(offsetX, 0, offsetZ));

                tick++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Play dramatic critical hit effect. */
    protected void playCriticalHitEffect(Player player) {
        if (pinataEntity == null) return;

        Location loc = pinataEntity.getLocation().clone().add(0, 1, 0);

        // Critical particles (white and gold)
        loc.getWorld().spawnParticle(Particle.CRIT, loc, 20, 0.3, 0.5, 0.3, 0.2);
        loc.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, 15, 0.3, 0.5, 0.3, 0.1);

        // Critical animation
        if (packetEventsNPC != null) {
            packetEventsNPC.playAnimation(4); // Critical effect
        }

        // Sound and action bar
        playToNearbyPlayers(Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.0f, 32.0);
        player.sendActionBar(
                Component.text()
                        .append(
                                Component.text(
                                        "CRITICAL HIT! ", NamedTextColor.GOLD, TextDecoration.BOLD))
                        .append(Component.text("+2 damage", NamedTextColor.YELLOW))
                        .build());
    }

    /** Break the pinata and distribute rewards. */
    protected void breakPinata() {
        if (pinataEntity == null) return;

        Location loc = pinataEntity.getLocation().clone();

        // Big particle explosion
        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc.clone().add(0, 1, 0), 3);
        loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 100, 1, 1, 1, 0.5);
        loc.getWorld().spawnParticle(Particle.FIREWORK, loc, 50, 1, 1, 1, 0.3);

        // Play break sound for all players (celebration sound)
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(
                    player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST_FAR, 1.0f, 1.0f);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.5f);
        }

        // Broadcast break messages
        broadcastBreakMessages();

        // Distribute rewards
        distributeRewards();

        // Distribute items (NEW)
        distributeItems();

        // Stop the event
        stop();
    }

    /**
     * Process combo rewards and streak broadcasts for a player. Awards bonus coins at 5, 10, and 15
     * consecutive hit thresholds. Broadcasts streak announcements at 10 and 15 hits.
     *
     * @param player The player who hit
     * @param comboCount Current combo count for this player
     */
    protected void processComboRewards(Player player, int comboCount) {
        UUID playerId = player.getUniqueId();

        // Get or create the announced streaks set for this player
        Set<Integer> announced =
                announcedStreaks.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet());

        // Check combo thresholds and award bonuses
        if (comboCount == COMBO_THRESHOLD_1) {
            // 5 hits: +50 bonus coins
            comboBonuses.merge(playerId, COMBO_BONUS_1, Integer::sum);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        } else if (comboCount == COMBO_THRESHOLD_2) {
            // 10 hits: +100 bonus coins
            comboBonuses.merge(playerId, COMBO_BONUS_2, Integer::sum);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);

            // Broadcast streak announcement (only once per streak level)
            if (announced.add(COMBO_THRESHOLD_2)) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    MessageBuilder.create()
                            .emphasis("[Pinata] ")
                            .warning(player.getName() + " ")
                            .highlight("is on fire! ")
                            .emphasis("10 hit streak!")
                            .send(p);
                }
            }
        } else if (comboCount == COMBO_THRESHOLD_3) {
            // 15 hits: +200 bonus coins
            comboBonuses.merge(playerId, COMBO_BONUS_3, Integer::sum);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);

            // Broadcast streak announcement (only once per streak level)
            if (announced.add(COMBO_THRESHOLD_3)) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    MessageBuilder.create()
                            .emphasis("[Pinata] ")
                            .colored(player.getName() + " ", "<red><bold>")
                            .highlight("is UNSTOPPABLE! ")
                            .colored("15 hit streak!", "<red><bold>")
                            .send(p);
                }
            }
        }
    }

    /**
     * Get the combo bonus for a specific player. To be used during reward distribution in
     * subclasses.
     *
     * @param playerId The player's UUID
     * @return The total combo bonus coins earned by this player
     */
    protected int getComboBonus(UUID playerId) {
        return comboBonuses.getOrDefault(playerId, 0);
    }

    /**
     * Get all combo bonuses for reward distribution.
     *
     * @return Map of player UUID to their total combo bonus
     */
    protected Map<UUID, Integer> getComboBonuses() {
        return new HashMap<>(comboBonuses);
    }

    /**
     * Start dynamic movement with launching, walking, and particle effects. Pinata-like behavior:
     * random walking, jumping, and dodging. Includes TPS-based and player-count-based particle
     * scaling for performance.
     */
    protected void startDynamicMovement() {
        if (pinataEntity == null) return;

        movementTask =
                new BukkitRunnable() {
                    private int tickCount = 0;

                    @Override
                    public void run() {
                        // Check if entity is still valid
                        LivingEntity entity = pinataEntity;
                        if (entity == null || entity.isDead() || !active) {
                            cancel();
                            return;
                        }

                        tickCount++;

                        // Check deterioration stage based on health
                        if (config.isDeteriorationEnabled() && clicksRequired > 0) {
                            int hitPercent = (currentClicks.get() * 100) / clicksRequired;
                            int newStage = 0;
                            if (hitPercent >= 75) newStage = 3; // 25% HP - CRITICAL
                            else if (hitPercent >= 50) newStage = 2; // 50% HP - DAMAGED
                            else if (hitPercent >= 25) newStage = 1; // 75% HP - SCRATCHED

                            if (newStage != currentDeteriorationStage) {
                                currentDeteriorationStage = newStage;
                                updateDeteriorationEffects(newStage);
                            }
                        }

                        // Sync PacketEvents NPC position with armor stand (every tick for smooth
                        // movement)
                        if (packetEventsNPC != null && packetEventsNPC.isSpawned()) {
                            Location entityLoc = entity.getLocation();
                            Location npcLoc = packetEventsNPC.getLocation();

                            // Only teleport if position changed significantly
                            if (entityLoc.distanceSquared(npcLoc) > 0.01) {
                                packetEventsNPC.teleport(entityLoc);
                            }
                        }

                        // Random jumping every 1.5-3 seconds (30-60 ticks)
                        if (tickCount % 40 == 0 && random.nextFloat() < 0.7f) {
                            // Jump with random direction
                            double jumpHeight = 0.6 + random.nextDouble() * 0.6; // 0.6-1.2 blocks
                            double moveX = (random.nextDouble() - 0.5) * 0.6; // Random X direction
                            double moveZ = (random.nextDouble() - 0.5) * 0.6; // Random Z direction
                            Vector velocity = new Vector(moveX, jumpHeight, moveZ);
                            entity.setVelocity(velocity);

                            // Also send velocity to NPC for smooth animation
                            if (packetEventsNPC != null && packetEventsNPC.isSpawned()) {
                                packetEventsNPC.setVelocity(velocity);
                            }

                            // Play jump sound
                            playToNearbyPlayers(Sound.ENTITY_SLIME_JUMP, 0.6f, 1.2f, 32.0);
                        }

                        // Big launch upward every 4 seconds (80 ticks) for pinata effect
                        if (tickCount % 80 == 0) {
                            double launchHeight = 0.8 + random.nextDouble() * 0.5;
                            double randomX = (random.nextDouble() - 0.5) * 0.4;
                            double randomZ = (random.nextDouble() - 0.5) * 0.4;
                            Vector velocity = new Vector(randomX, launchHeight, randomZ);
                            entity.setVelocity(velocity);

                            // Also send velocity to NPC
                            if (packetEventsNPC != null && packetEventsNPC.isSpawned()) {
                                packetEventsNPC.setVelocity(velocity);
                            }

                            // Play launch sound to nearby players
                            playToNearbyPlayers(
                                    Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.5f, 1.2f, 64.0);
                        }

                        // Check TPS before spawning any particles
                        double tps = Bukkit.getTPS()[0];
                        boolean shouldSpawnParticles = tps >= TPS_SKIP_THRESHOLD;

                        // Spawn movement particles when entity is moving
                        Vector vel = entity.getVelocity();
                        if (shouldSpawnParticles && vel.lengthSquared() > 0.01) {
                            Location loc = entity.getLocation().clone().add(0, 0.5, 0);

                            int scaledMovementParticles =
                                    scaleParticleCount(getMovementParticleCount());
                            if (scaledMovementParticles > 0) {
                                entity.getWorld()
                                        .spawnParticle(
                                                getMovementParticle(),
                                                loc,
                                                scaledMovementParticles,
                                                0.3,
                                                0.3,
                                                0.3,
                                                0.05);
                            }

                            int scaledSecondaryParticles = scaleParticleCount(1);
                            if (scaledSecondaryParticles > 0) {
                                entity.getWorld()
                                        .spawnParticle(
                                                getSecondaryParticle(),
                                                loc,
                                                scaledSecondaryParticles,
                                                0.2,
                                                0.2,
                                                0.2,
                                                0.02);
                            }
                        }

                        // Burst of particles every 2 seconds (40 ticks)
                        if (shouldSpawnParticles && tickCount % 40 == 0) {
                            int scaledBurstParticles = scaleParticleCount(getBurstParticleCount());
                            if (scaledBurstParticles > 0) {
                                Location loc = entity.getLocation().clone().add(0, 1, 0);
                                entity.getWorld()
                                        .spawnParticle(
                                                getMovementParticle(),
                                                loc,
                                                scaledBurstParticles,
                                                0.5,
                                                0.5,
                                                0.5,
                                                0.1);
                            }
                        }
                    }
                };

        movementTask.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Play a sound at a location for all players within range. Uses world.playSound which handles
     * distance culling automatically.
     */
    protected void playWorldSound(Location loc, Sound sound, float volume, float pitch) {
        if (loc.getWorld() != null) {
            loc.getWorld().playSound(loc, sound, volume, pitch);
        }
    }

    /**
     * Play a sound to players within a specific range in the same world. Only players in the same
     * world and within range will hear the sound.
     *
     * @param sound The sound to play
     * @param volume Sound volume
     * @param pitch Sound pitch
     * @param range Maximum distance in blocks (use 0 for world-wide in same world)
     */
    protected void playToNearbyPlayers(Sound sound, float volume, float pitch, double range) {
        if (pinataEntity == null || pinataEntity.getWorld() == null) return;

        Location entityLoc = pinataEntity.getLocation();
        org.bukkit.World entityWorld = entityLoc.getWorld();
        double rangeSquared = range * range;

        for (Player player : entityWorld.getPlayers()) {
            if (range <= 0 || player.getLocation().distanceSquared(entityLoc) <= rangeSquared) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        }
    }

    /**
     * Calculate particle multiplier based on nearby player count. Reduces particles when many
     * players are nearby to prevent lag.
     *
     * @return Multiplier between 0.25 and 1.0
     */
    protected double getParticleMultiplier() {
        if (pinataEntity == null || pinataEntity.getWorld() == null) return 1.0;

        Location entityLoc = pinataEntity.getLocation();
        int nearbyCount = 0;

        for (Player player : pinataEntity.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(entityLoc) <= PARTICLE_CHECK_RANGE_SQUARED) {
                nearbyCount++;
            }
        }

        if (nearbyCount > VERY_HIGH_PLAYER_COUNT_THRESHOLD) {
            return 0.25;
        } else if (nearbyCount > HIGH_PLAYER_COUNT_THRESHOLD) {
            return 0.5;
        }
        return 1.0;
    }

    /**
     * Scale particle count based on nearby player count and server TPS. Returns 0 if particles
     * should be skipped entirely.
     *
     * @param baseCount The base particle count
     * @return Scaled particle count (may be 0 to skip particles)
     */
    protected int scaleParticleCount(int baseCount) {
        // Check TPS first (Paper API)
        double tps = Bukkit.getTPS()[0];

        if (tps < TPS_SKIP_THRESHOLD) {
            // Skip particles entirely when TPS is very low
            return 0;
        }

        double multiplier = getParticleMultiplier();

        // Further reduce if TPS is below threshold
        if (tps < TPS_REDUCED_THRESHOLD) {
            multiplier *= 0.5;
        }

        return Math.max(1, (int) (baseCount * multiplier));
    }

    /**
     * Check if boss bar can be updated based on throttle interval. Thread-safe using atomic
     * compare-and-set.
     *
     * @return true if enough time has passed since last update
     */
    protected boolean canUpdateBossBar() {
        long now = System.currentTimeMillis();
        long lastUpdate = lastBossBarUpdate.get();

        if (now - lastUpdate >= BOSS_BAR_UPDATE_INTERVAL_MS) {
            // Try to atomically set the new timestamp
            return lastBossBarUpdate.compareAndSet(lastUpdate, now);
        }
        return false;
    }

    /** Create the progress boss bar and show it to players in the same world. */
    protected void createBossBar() {
        if (pinataEntity == null || pinataEntity.getWorld() == null) return;

        progressBossBar =
                BossBar.bossBar(
                        Component.text(
                                getDisplayName().toUpperCase() + " [0/" + clicksRequired + "]",
                                NamedTextColor.WHITE),
                        1.0f,
                        BossBar.Color.GREEN,
                        BossBar.Overlay.PROGRESS);

        // Show to all players in the same world as the pinata
        for (Player player : pinataEntity.getWorld().getPlayers()) {
            player.showBossBar(progressBossBar);
        }
    }

    /**
     * Update the boss bar progress and name.
     *
     * @param clicks Current click count
     */
    protected void updateBossBar(int clicks) {
        if (progressBossBar == null) return;

        float remaining = Math.max(0.0f, 1.0f - (float) clicks / clicksRequired);
        progressBossBar.progress(remaining);
        progressBossBar.name(
                Component.text(
                        getDisplayName().toUpperCase() + " [" + clicks + "/" + clicksRequired + "]",
                        NamedTextColor.WHITE));

        // Dynamic color transitions based on remaining health
        if (remaining > 0.5f) {
            progressBossBar.color(BossBar.Color.GREEN);
        } else if (remaining > 0.25f) {
            progressBossBar.color(BossBar.Color.YELLOW);
        } else {
            progressBossBar.color(BossBar.Color.RED);
        }
    }

    /**
     * Check if a milestone has been reached and play the appropriate sound. Milestones are at 25%,
     * 50%, and 75% progress.
     *
     * @param clicks Current click count
     */
    protected void checkAndPlayMilestoneSound(int clicks) {
        if (pinataEntity == null || clicksRequired <= 0) return;

        int progress25 = clicksRequired / 4;
        int progress50 = clicksRequired / 2;
        int progress75 = (clicksRequired * 3) / 4;

        // Check each milestone (25%, 50%, 75%)
        if (clicks >= progress25 && clicks < progress50 && triggeredMilestones.add(25)) {
            playMilestoneSound(0.8f); // Lower pitch for 25%
        } else if (clicks >= progress50 && clicks < progress75 && triggeredMilestones.add(50)) {
            playMilestoneSound(1.0f); // Normal pitch for 50%
        } else if (clicks >= progress75 && clicks < clicksRequired && triggeredMilestones.add(75)) {
            playMilestoneSound(1.2f); // Higher pitch for 75%
        }
    }

    /**
     * Play a milestone sound to all players in the pinata's world.
     *
     * @param pitch The pitch of the note block pling sound
     */
    protected void playMilestoneSound(float pitch) {
        if (pinataEntity == null || pinataEntity.getWorld() == null) return;

        for (Player player : pinataEntity.getWorld().getPlayers()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, pitch);
        }
    }

    /**
     * Schedule timeout warning messages at 30 seconds and 10 seconds remaining.
     *
     * @param timeoutSeconds Total timeout in seconds
     */
    protected void scheduleTimeoutWarnings(int timeoutSeconds) {
        // Only schedule if there's enough time for warnings
        if (timeoutSeconds > 30) {
            long ticksUntil30sWarning = 20L * (timeoutSeconds - 30);
            warning30sTask =
                    Bukkit.getScheduler()
                            .runTaskLater(
                                    plugin,
                                    () -> {
                                        if (active) {
                                            for (Player p : Bukkit.getOnlinePlayers()) {
                                                MessageBuilder.create()
                                                        .warning(
                                                                "30 seconds remaining to break the pinata!")
                                                        .send(p);
                                            }
                                        }
                                    },
                                    ticksUntil30sWarning);
        }

        if (timeoutSeconds > 10) {
            long ticksUntil10sWarning = 20L * (timeoutSeconds - 10);
            warning10sTask =
                    Bukkit.getScheduler()
                            .runTaskLater(
                                    plugin,
                                    () -> {
                                        if (active) {
                                            for (Player p : Bukkit.getOnlinePlayers()) {
                                                MessageBuilder.create()
                                                        .colored(
                                                                "URGENT: 10 seconds remaining!",
                                                                "<red><bold>")
                                                        .send(p);
                                            }
                                            // Play urgent sound to all players in the world
                                            if (pinataEntity != null
                                                    && pinataEntity.getWorld() != null) {
                                                for (Player player :
                                                        pinataEntity.getWorld().getPlayers()) {
                                                    player.playSound(
                                                            player.getLocation(),
                                                            Sound.BLOCK_NOTE_BLOCK_PLING,
                                                            1.0f,
                                                            0.5f);
                                                }
                                            }
                                        }
                                    },
                                    ticksUntil10sWarning);
        }
    }

    /** Broadcast a message with a clickable teleport button. */
    protected void broadcastWithTeleport(String message) {
        Component textComponent =
                TextUtil.parse(message)
                        .append(Component.text(" "))
                        .append(
                                Component.text("[TELEPORT]")
                                        .color(getTeleportColor())
                                        .decorate(TextDecoration.BOLD)
                                        .clickEvent(ClickEvent.runCommand(getTeleportCommand()))
                                        .hoverEvent(
                                                HoverEvent.showText(
                                                        Component.text("Click to teleport!")
                                                                .color(getTeleportColor()))));

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(textComponent);
        }
    }

    /** Get the total clicks from all players. */
    protected int getTotalPlayerClicks() {
        int total = 0;
        for (AtomicInteger clicks : playerClicks.values()) {
            total += clicks.get();
        }
        return total;
    }

    /** Get the click count for a specific player. */
    protected int getPlayerClickCount(UUID playerId) {
        AtomicInteger clicks = playerClicks.get(playerId);
        return clicks != null ? clicks.get() : 0;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    public int getCurrentClicks() {
        return currentClicks.get();
    }

    public int getClicksRequired() {
        return clicksRequired;
    }

    public Map<UUID, Integer> getPlayerClicks() {
        Map<UUID, Integer> result = new HashMap<>();
        for (Map.Entry<UUID, AtomicInteger> entry : playerClicks.entrySet()) {
            result.put(entry.getKey(), entry.getValue().get());
        }
        return result;
    }
}
