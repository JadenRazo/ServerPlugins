package net.serverplugins.claim.listeners;

import java.util.Set;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ClaimPermission;
import net.serverplugins.claim.models.ClaimSettings;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleEnterEvent;

public class ClaimProtectionListener implements Listener {

    private final ServerClaim plugin;

    // Container blocks
    private static final Set<Material> CONTAINERS =
            Set.of(
                    Material.CHEST,
                    Material.TRAPPED_CHEST,
                    Material.BARREL,
                    Material.ENDER_CHEST,
                    Material.HOPPER,
                    Material.DISPENSER,
                    Material.DROPPER,
                    Material.FURNACE,
                    Material.BLAST_FURNACE,
                    Material.SMOKER,
                    Material.LECTERN,
                    Material.JUKEBOX,
                    Material.CHISELED_BOOKSHELF,
                    Material.DECORATED_POT,
                    Material.CRAFTER);

    // Shulker boxes (all colors)
    private static final Set<Material> SHULKER_BOXES =
            Set.of(
                    Material.SHULKER_BOX,
                    Material.WHITE_SHULKER_BOX,
                    Material.ORANGE_SHULKER_BOX,
                    Material.MAGENTA_SHULKER_BOX,
                    Material.LIGHT_BLUE_SHULKER_BOX,
                    Material.YELLOW_SHULKER_BOX,
                    Material.LIME_SHULKER_BOX,
                    Material.PINK_SHULKER_BOX,
                    Material.GRAY_SHULKER_BOX,
                    Material.LIGHT_GRAY_SHULKER_BOX,
                    Material.CYAN_SHULKER_BOX,
                    Material.PURPLE_SHULKER_BOX,
                    Material.BLUE_SHULKER_BOX,
                    Material.BROWN_SHULKER_BOX,
                    Material.GREEN_SHULKER_BOX,
                    Material.RED_SHULKER_BOX,
                    Material.BLACK_SHULKER_BOX);

    // Brewing blocks
    private static final Set<Material> BREWING_BLOCKS = Set.of(Material.BREWING_STAND);

    // Anvil blocks
    private static final Set<Material> ANVIL_BLOCKS =
            Set.of(
                    Material.ANVIL,
                    Material.CHIPPED_ANVIL,
                    Material.DAMAGED_ANVIL,
                    Material.GRINDSTONE);

    // Redstone components (clicked)
    private static final Set<Material> REDSTONE_BLOCKS =
            Set.of(
                    Material.LEVER,
                    Material.COMPARATOR,
                    Material.REPEATER,
                    Material.DAYLIGHT_DETECTOR,
                    Material.NOTE_BLOCK);

    // Pressure plates (all types) - stepped on
    private static final Set<Material> PRESSURE_PLATES =
            Set.of(
                    Material.OAK_PRESSURE_PLATE,
                    Material.SPRUCE_PRESSURE_PLATE,
                    Material.BIRCH_PRESSURE_PLATE,
                    Material.JUNGLE_PRESSURE_PLATE,
                    Material.ACACIA_PRESSURE_PLATE,
                    Material.DARK_OAK_PRESSURE_PLATE,
                    Material.CHERRY_PRESSURE_PLATE,
                    Material.BAMBOO_PRESSURE_PLATE,
                    Material.MANGROVE_PRESSURE_PLATE,
                    Material.CRIMSON_PRESSURE_PLATE,
                    Material.WARPED_PRESSURE_PLATE,
                    Material.STONE_PRESSURE_PLATE,
                    Material.POLISHED_BLACKSTONE_PRESSURE_PLATE,
                    Material.LIGHT_WEIGHTED_PRESSURE_PLATE,
                    Material.HEAVY_WEIGHTED_PRESSURE_PLATE);

    // Tripwire components
    private static final Set<Material> TRIPWIRE_BLOCKS =
            Set.of(Material.TRIPWIRE, Material.TRIPWIRE_HOOK);

    // Interactive blocks that need protection
    private static final Set<Material> INTERACTIVE_BLOCKS =
            Set.of(
                    Material.BELL,
                    Material.CAMPFIRE,
                    Material.SOUL_CAMPFIRE,
                    Material.CAULDRON,
                    Material.WATER_CAULDRON,
                    Material.LAVA_CAULDRON,
                    Material.POWDER_SNOW_CAULDRON,
                    Material.COMPOSTER,
                    Material.BEEHIVE,
                    Material.BEE_NEST,
                    Material.RESPAWN_ANCHOR,
                    Material.LODESTONE,
                    Material.ENCHANTING_TABLE,
                    Material.SMITHING_TABLE,
                    Material.CARTOGRAPHY_TABLE,
                    Material.LOOM,
                    Material.STONECUTTER,
                    Material.FLOWER_POT,
                    Material.DRAGON_EGG);

    // Candles (all colors)
    private static final Set<Material> CANDLES =
            Set.of(
                    Material.CANDLE,
                    Material.WHITE_CANDLE,
                    Material.ORANGE_CANDLE,
                    Material.MAGENTA_CANDLE,
                    Material.LIGHT_BLUE_CANDLE,
                    Material.YELLOW_CANDLE,
                    Material.LIME_CANDLE,
                    Material.PINK_CANDLE,
                    Material.GRAY_CANDLE,
                    Material.LIGHT_GRAY_CANDLE,
                    Material.CYAN_CANDLE,
                    Material.PURPLE_CANDLE,
                    Material.BLUE_CANDLE,
                    Material.BROWN_CANDLE,
                    Material.GREEN_CANDLE,
                    Material.RED_CANDLE,
                    Material.BLACK_CANDLE);

    // Cake types
    private static final Set<Material> CAKES =
            Set.of(
                    Material.CAKE,
                    Material.CANDLE_CAKE,
                    Material.WHITE_CANDLE_CAKE,
                    Material.ORANGE_CANDLE_CAKE,
                    Material.MAGENTA_CANDLE_CAKE,
                    Material.LIGHT_BLUE_CANDLE_CAKE,
                    Material.YELLOW_CANDLE_CAKE,
                    Material.LIME_CANDLE_CAKE,
                    Material.PINK_CANDLE_CAKE,
                    Material.GRAY_CANDLE_CAKE,
                    Material.LIGHT_GRAY_CANDLE_CAKE,
                    Material.CYAN_CANDLE_CAKE,
                    Material.PURPLE_CANDLE_CAKE,
                    Material.BLUE_CANDLE_CAKE,
                    Material.BROWN_CANDLE_CAKE,
                    Material.GREEN_CANDLE_CAKE,
                    Material.RED_CANDLE_CAKE,
                    Material.BLACK_CANDLE_CAKE);

    // Beds (all colors) - for explosions/sleeping
    private static final Set<Material> BEDS =
            Set.of(
                    Material.WHITE_BED,
                    Material.ORANGE_BED,
                    Material.MAGENTA_BED,
                    Material.LIGHT_BLUE_BED,
                    Material.YELLOW_BED,
                    Material.LIME_BED,
                    Material.PINK_BED,
                    Material.GRAY_BED,
                    Material.LIGHT_GRAY_BED,
                    Material.CYAN_BED,
                    Material.PURPLE_BED,
                    Material.BLUE_BED,
                    Material.BROWN_BED,
                    Material.GREEN_BED,
                    Material.RED_BED,
                    Material.BLACK_BED);

    public ClaimProtectionListener(ServerClaim plugin) {
        this.plugin = plugin;
    }

    // ==================== BLOCK EVENTS ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Chunk chunk = event.getBlock().getChunk();

        // Cache claim lookup - avoid redundant getClaimAt calls
        Claim claim = plugin.getClaimManager().getClaimAt(chunk);

        if (!hasClaimPermissionWithClaim(player, claim, ClaimPermission.BREAK_BLOCKS)) {
            event.setCancelled(true);
            sendDenyMessage(player);
            return;
        }

        // Track block break for XP (reuse cached claim)
        if (claim != null && plugin.getLevelManager() != null) {
            plugin.getLevelManager().trackBlockBroken(claim.getId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Chunk chunk = event.getBlock().getChunk();

        // Cache claim lookup - avoid redundant getClaimAt calls
        Claim claim = plugin.getClaimManager().getClaimAt(chunk);

        if (!hasClaimPermissionWithClaim(player, claim, ClaimPermission.PLACE_BLOCKS)) {
            event.setCancelled(true);
            sendDenyMessage(player);
            return;
        }

        // Track block place for XP (reuse cached claim)
        if (claim != null && plugin.getLevelManager() != null) {
            plugin.getLevelManager().trackBlockPlaced(claim.getId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (!hasClaimPermission(
                event.getPlayer(), event.getBlock().getChunk(), ClaimPermission.EDIT_SIGNS)) {
            event.setCancelled(true);
            sendDenyMessage(event.getPlayer());
        }
    }

    // ==================== PLAYER INTERACT EVENTS ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;

        Player player = event.getPlayer();
        Material type = block.getType();

        // Single claim lookup for the entire event
        if (player.hasPermission("serverclaim.bypass")) return;
        Claim claim = plugin.getClaimManager().getClaimAt(block.getChunk());
        if (claim == null) return; // Wilderness — all interactions allowed

        // Handle PHYSICAL actions (stepping on pressure plates, tripwire)
        if (event.getAction() == Action.PHYSICAL) {
            // Pressure plates
            if (PRESSURE_PLATES.contains(type)) {
                if (!hasClaimPermissionWithClaim(player, claim, ClaimPermission.USE_REDSTONE)) {
                    event.setCancelled(true);
                    return;
                }
            }

            // Tripwire
            if (TRIPWIRE_BLOCKS.contains(type)) {
                if (!hasClaimPermissionWithClaim(player, claim, ClaimPermission.USE_REDSTONE)) {
                    event.setCancelled(true);
                    return;
                }
            }

            // Farmland trampling (handled by EntityChangeBlock, but double-check here)
            if (type == Material.FARMLAND) {
                ClaimSettings settings = claim.getSettings();
                if (settings != null && !settings.isCropTrampling()) {
                    event.setCancelled(true);
                    return;
                }
            }

            // Turtle eggs
            if (type == Material.TURTLE_EGG) {
                if (!hasClaimPermissionWithClaim(player, claim, ClaimPermission.BREAK_BLOCKS)) {
                    event.setCancelled(true);
                    return;
                }
            }

            return; // Physical actions handled
        }

        // Below this point is for RIGHT_CLICK and LEFT_CLICK actions

        // Check containers (chests, barrels, furnaces, etc.)
        if (CONTAINERS.contains(type) || SHULKER_BOXES.contains(type)) {
            if (!hasClaimPermissionWithClaim(player, claim, ClaimPermission.OPEN_CONTAINERS)) {
                event.setCancelled(true);
                sendDenyMessage(player);
                return;
            }
        }

        // Check brewing stands
        if (BREWING_BLOCKS.contains(type)) {
            if (!hasClaimPermissionWithClaim(player, claim, ClaimPermission.USE_BREWING_STANDS)) {
                event.setCancelled(true);
                sendDenyMessage(player);
                return;
            }
        }

        // Check anvils/grindstones
        if (ANVIL_BLOCKS.contains(type)) {
            if (!hasClaimPermissionWithClaim(player, claim, ClaimPermission.USE_ANVILS)) {
                event.setCancelled(true);
                sendDenyMessage(player);
                return;
            }
        }

        // Check doors and trapdoors
        if (Tag.DOORS.isTagged(type) || Tag.TRAPDOORS.isTagged(type)) {
            if (!hasClaimPermissionWithClaim(player, claim, ClaimPermission.USE_DOORS)) {
                event.setCancelled(true);
                sendDenyMessage(player);
                return;
            }
        }

        // Check fence gates
        if (Tag.FENCE_GATES.isTagged(type)) {
            if (!hasClaimPermissionWithClaim(player, claim, ClaimPermission.USE_FENCE_GATES)) {
                event.setCancelled(true);
                sendDenyMessage(player);
                return;
            }
        }

        // Check redstone components (buttons, levers, etc.)
        if (REDSTONE_BLOCKS.contains(type) || Tag.BUTTONS.isTagged(type)) {
            if (!hasClaimPermissionWithClaim(player, claim, ClaimPermission.USE_REDSTONE)) {
                event.setCancelled(true);
                sendDenyMessage(player);
                return;
            }
        }

        // Check spawners
        if (type == Material.SPAWNER) {
            if (!hasClaimPermissionWithClaim(player, claim, ClaimPermission.SPAWNERS)) {
                event.setCancelled(true);
                sendDenyMessage(player);
                return;
            }
        }

        // Check interactive blocks (bells, cauldrons, campfires, etc.)
        if (INTERACTIVE_BLOCKS.contains(type)) {
            if (!hasClaimPermissionWithClaim(player, claim, ClaimPermission.INTERACT_ENTITIES)) {
                event.setCancelled(true);
                sendDenyMessage(player);
                return;
            }
        }

        // Check candles
        if (CANDLES.contains(type)) {
            if (!hasClaimPermissionWithClaim(player, claim, ClaimPermission.INTERACT_ENTITIES)) {
                event.setCancelled(true);
                sendDenyMessage(player);
                return;
            }
        }

        // Check cakes (eating)
        if (CAKES.contains(type)) {
            if (!hasClaimPermissionWithClaim(player, claim, ClaimPermission.INTERACT_ENTITIES)) {
                event.setCancelled(true);
                sendDenyMessage(player);
                return;
            }
        }

        // Check beds
        if (BEDS.contains(type)) {
            if (!hasClaimPermissionWithClaim(player, claim, ClaimPermission.SET_HOME)) {
                event.setCancelled(true);
                sendDenyMessage(player);
                return;
            }
        }

        // Check armor stands, item frames (via block click with item in hand to place)
        Material itemType = player.getInventory().getItemInMainHand().getType();
        if (itemType == Material.ARMOR_STAND
                || itemType == Material.ITEM_FRAME
                || itemType == Material.GLOW_ITEM_FRAME
                || itemType == Material.PAINTING) {
            if (!hasClaimPermissionWithClaim(player, claim, ClaimPermission.PLACE_BLOCKS)) {
                event.setCancelled(true);
                sendDenyMessage(player);
            }
        }
    }

    // ==================== ENTITY INTERACTION EVENTS ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        Player player = event.getPlayer();
        Chunk chunk = entity.getLocation().getChunk();

        if (!hasClaimPermission(player, chunk, ClaimPermission.INTERACT_ENTITIES)) {
            event.setCancelled(true);
            sendDenyMessage(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        Player player = event.getPlayer();
        Chunk chunk = event.getRightClicked().getLocation().getChunk();

        if (!hasClaimPermission(player, chunk, ClaimPermission.INTERACT_ENTITIES)) {
            event.setCancelled(true);
            sendDenyMessage(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getEntered() instanceof Player player)) return;

        Vehicle vehicle = event.getVehicle();
        Chunk chunk = vehicle.getLocation().getChunk();

        if (!hasClaimPermission(player, chunk, ClaimPermission.RIDE_VEHICLES)) {
            event.setCancelled(true);
            sendDenyMessage(player);
        }
    }

    // ==================== HANGING ENTITY EVENTS (Item frames, paintings) ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        Chunk chunk = event.getEntity().getLocation().getChunk();

        if (!hasClaimPermission(player, chunk, ClaimPermission.PLACE_BLOCKS)) {
            event.setCancelled(true);
            sendDenyMessage(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Player player = getPlayerFromEntity(event.getRemover());
        if (player == null) return;

        Chunk chunk = event.getEntity().getLocation().getChunk();

        if (!hasClaimPermission(player, chunk, ClaimPermission.BREAK_BLOCKS)) {
            event.setCancelled(true);
            sendDenyMessage(player);
        }
    }

    // ==================== DAMAGE EVENTS ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Player attacker = getPlayerAttacker(event.getDamager());
        if (attacker == null) return;
        if (attacker.hasPermission("serverclaim.bypass")) return;

        Entity victim = event.getEntity();

        // Single claim lookup for the entire event
        Claim claim = plugin.getClaimManager().getClaimAt(victim.getLocation().getChunk());

        // PvP check (player vs player)
        if (victim instanceof Player) {
            if (claim != null) {
                // First check if this is a war zone - war PvP overrides claim settings
                if (plugin.getWarManager() != null
                        && plugin.getWarManager().isWarPvpEnabled(claim)) {
                    return;
                }

                // Normal PvP check based on claim settings
                ClaimSettings settings = claim.getSettings();
                if (settings != null && !settings.isPvpEnabled()) {
                    event.setCancelled(true);
                    TextUtil.send(attacker, "<red>PvP is disabled in this claim!");
                    return;
                }
            }
        }

        if (claim == null) return; // Wilderness — all damage allowed

        // Armor stands and item frames count as entities but should use BREAK_BLOCKS
        if (victim instanceof ArmorStand || victim instanceof ItemFrame) {
            if (!hasClaimPermissionWithClaim(attacker, claim, ClaimPermission.BREAK_BLOCKS)) {
                event.setCancelled(true);
                sendDenyMessage(attacker);
                return;
            }
        }

        // Check permission for damaging passive mobs
        if (victim instanceof Animals
                || victim instanceof Ambient
                || victim instanceof WaterMob
                || victim instanceof Golem
                || victim instanceof Villager
                || victim instanceof Allay) {
            if (!hasClaimPermissionWithClaim(attacker, claim, ClaimPermission.DAMAGE_PASSIVE)) {
                event.setCancelled(true);
                sendDenyMessage(attacker);
                return;
            }
        }

        // Check permission for damaging hostile mobs
        if (victim instanceof Monster
                || victim instanceof Slime
                || victim instanceof Phantom
                || victim instanceof EnderDragon
                || victim instanceof Wither
                || victim instanceof Hoglin
                || victim instanceof Shulker
                || victim instanceof Ghast) {
            if (!hasClaimPermissionWithClaim(attacker, claim, ClaimPermission.DAMAGE_HOSTILE)) {
                event.setCancelled(true);
                sendDenyMessage(attacker);
            }
        }
    }

    // ==================== ITEM EVENTS ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Chunk chunk = player.getLocation().getChunk();

        if (!hasClaimPermission(player, chunk, ClaimPermission.DROP_ITEMS)) {
            event.setCancelled(true);
            sendDenyMessage(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        Chunk chunk = event.getItem().getLocation().getChunk();

        if (!hasClaimPermission(player, chunk, ClaimPermission.PICKUP_ITEMS)) {
            event.setCancelled(true);
        }
    }

    // ==================== BUCKET EVENTS ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!hasClaimPermission(
                event.getPlayer(), event.getBlock().getChunk(), ClaimPermission.USE_BUCKETS)) {
            event.setCancelled(true);
            sendDenyMessage(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerBucketFill(PlayerBucketFillEvent event) {
        if (!hasClaimPermission(
                event.getPlayer(), event.getBlock().getChunk(), ClaimPermission.USE_BUCKETS)) {
            event.setCancelled(true);
            sendDenyMessage(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerBucketEntity(PlayerBucketEntityEvent event) {
        if (!hasClaimPermission(
                event.getPlayer(),
                event.getEntity().getLocation().getChunk(),
                ClaimPermission.USE_BUCKETS)) {
            event.setCancelled(true);
            sendDenyMessage(event.getPlayer());
        }
    }

    // ==================== PROJECTILE EVENTS ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player player)) return;

        Chunk chunk = player.getLocation().getChunk();

        if (!hasClaimPermission(player, chunk, ClaimPermission.SHOOT_PROJECTILES)) {
            event.setCancelled(true);
            sendDenyMessage(player);
        }
    }

    // ==================== LEASH EVENTS ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerLeashEntity(PlayerLeashEntityEvent event) {
        Player player = event.getPlayer();
        Chunk chunk = event.getEntity().getLocation().getChunk();

        if (!hasClaimPermission(player, chunk, ClaimPermission.INTERACT_ENTITIES)) {
            event.setCancelled(true);
            sendDenyMessage(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerUnleashEntity(PlayerUnleashEntityEvent event) {
        Player player = event.getPlayer();
        Chunk chunk = event.getEntity().getLocation().getChunk();

        if (!hasClaimPermission(player, chunk, ClaimPermission.INTERACT_ENTITIES)) {
            event.setCancelled(true);
            sendDenyMessage(player);
        }
    }

    // ==================== SHEAR EVENTS ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerShearEntity(PlayerShearEntityEvent event) {
        Player player = event.getPlayer();
        Chunk chunk = event.getEntity().getLocation().getChunk();

        if (!hasClaimPermission(player, chunk, ClaimPermission.INTERACT_ENTITIES)) {
            event.setCancelled(true);
            sendDenyMessage(player);
        }
    }

    // ==================== DEATH EVENTS ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Chunk chunk = player.getLocation().getChunk();

        Claim claim = plugin.getClaimManager().getClaimAt(chunk);
        if (claim == null) return;

        // Check if keep inventory is enabled and claim owner has the permission
        if (claim.isKeepInventory()) {
            // Always verify the owner has the permission, even if offline
            // We check by looking up if the owner UUID would have permission
            // For offline players, we check if the setting was validly enabled
            // (setting can only be enabled if owner had permission at the time)

            // Check if dying player is owner or trusted - they get keepinventory
            if (claim.isOwner(player.getUniqueId()) || claim.isTrusted(player.getUniqueId())) {
                event.setKeepInventory(true);
                event.setKeepLevel(true);
                event.getDrops().clear();
                event.setDroppedExp(0);
            }
            // Non-trusted players don't get keep inventory benefit even if setting is on
        }
    }

    // ==================== CLAIM SETTINGS EVENTS (Non-player) ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList()
                .removeIf(
                        block -> {
                            Claim claim = plugin.getClaimManager().getClaimAt(block.getChunk());
                            if (claim == null) return false;

                            ClaimSettings settings = claim.getSettings();
                            return settings != null && !settings.isExplosions();
                        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(org.bukkit.event.block.BlockExplodeEvent event) {
        event.blockList()
                .removeIf(
                        block -> {
                            Claim claim = plugin.getClaimManager().getClaimAt(block.getChunk());
                            if (claim == null) return false;

                            ClaimSettings settings = claim.getSettings();
                            return settings != null && !settings.isExplosions();
                        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (event.getCause() != BlockIgniteEvent.IgniteCause.SPREAD) return;

        Claim claim = plugin.getClaimManager().getClaimAt(event.getBlock().getChunk());
        if (claim == null) return;

        ClaimSettings settings = claim.getSettings();
        if (settings != null && !settings.isFireSpread()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        Claim claim = plugin.getClaimManager().getClaimAt(event.getBlock().getChunk());
        if (claim == null) return;

        ClaimSettings settings = claim.getSettings();
        if (settings != null && !settings.isFireSpread()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeavesDecay(org.bukkit.event.block.LeavesDecayEvent event) {
        Claim claim = plugin.getClaimManager().getClaimAt(event.getBlock().getChunk());
        if (claim == null) return;

        ClaimSettings settings = claim.getSettings();
        if (settings != null && !settings.isLeafDecay()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM
                || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG
                || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
            return;
        }

        Claim claim = plugin.getClaimManager().getClaimAt(event.getLocation().getChunk());
        if (claim == null) return;

        ClaimSettings settings = claim.getSettings();
        if (settings == null) return;

        if (event.getEntity() instanceof Monster) {
            if (!settings.isHostileSpawns()) {
                event.setCancelled(true);
            }
        } else {
            if (!settings.isPassiveSpawns()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Claim claim = plugin.getClaimManager().getClaimAt(event.getBlock().getChunk());
        if (claim == null) return;

        ClaimSettings settings = claim.getSettings();
        if (settings == null) return;

        // Check mob griefing (endermen, withers, etc.)
        if (!settings.isMobGriefing()) {
            if (!(event.getEntity() instanceof Player)) {
                event.setCancelled(true);
                return;
            }
        }

        // Check crop trampling by any entity (including players and mobs)
        if (event.getBlock().getType() == Material.FARMLAND) {
            if (!settings.isCropTrampling()) {
                event.setCancelled(true);
            }
        }
    }

    // ==================== HELPER METHODS ====================

    private boolean hasClaimPermission(Player player, Chunk chunk, ClaimPermission permission) {
        if (player.hasPermission("serverclaim.bypass")) return true;

        Claim claim = plugin.getClaimManager().getClaimAt(chunk);
        if (claim == null) return true; // Unclaimed land = allowed

        return claim.hasPermission(player.getUniqueId(), permission);
    }

    /** Check permission with a pre-fetched claim to avoid redundant lookups. */
    private boolean hasClaimPermissionWithClaim(
            Player player, Claim claim, ClaimPermission permission) {
        if (player.hasPermission("serverclaim.bypass")) return true;
        if (claim == null) return true; // Unclaimed land = allowed

        return claim.hasPermission(player.getUniqueId(), permission);
    }

    private Player getPlayerAttacker(Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        }
        if (damager instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player shooter) {
                return shooter;
            }
        }
        if (damager instanceof TNTPrimed tnt) {
            if (tnt.getSource() instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private Player getPlayerFromEntity(Entity entity) {
        if (entity instanceof Player) {
            return (Player) entity;
        }
        if (entity instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player shooter) {
                return shooter;
            }
        }
        return null;
    }

    private void sendDenyMessage(Player player) {
        String message = plugin.getClaimConfig().getMessage("no-permission");
        TextUtil.send(player, message);
    }
}
