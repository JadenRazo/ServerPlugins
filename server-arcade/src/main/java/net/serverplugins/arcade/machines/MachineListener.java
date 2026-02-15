package net.serverplugins.arcade.machines;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.games.GameType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

/** Handles player interactions with arcade machines. */
public class MachineListener implements Listener {

    /** Record to track seat information including when the player sat down. */
    private record SeatInfo(ArmorStand seat, long seatedAt) {}

    private final ServerArcade plugin;
    private long lastSave = 0;
    private final Set<UUID> seatedPlayers = new HashSet<>();
    private final Map<UUID, SeatInfo> playerSeats = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<UUID> warnedPlayers = new HashSet<>(); // Track who got timeout warning
    private int actionBarTaskId = -1;
    private int seatEnforcementTaskId = -1;

    public MachineListener(ServerArcade plugin) {
        this.plugin = plugin;
        startActionBarTask();
        startSeatEnforcementTask();
    }

    /** Start the repeating task that shows action bar messages to seated players. */
    private void startActionBarTask() {
        actionBarTaskId =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                plugin,
                                () -> {
                                    for (UUID playerId : new HashSet<>(seatedPlayers)) {
                                        Player player = Bukkit.getPlayer(playerId);
                                        if (player != null
                                                && player.isOnline()
                                                && player.isInsideVehicle()) {
                                            Component message =
                                                    Component.text("Press ", NamedTextColor.YELLOW)
                                                            .append(
                                                                    Component.text(
                                                                            "SHIFT",
                                                                            NamedTextColor.GOLD,
                                                                            TextDecoration.BOLD))
                                                            .append(
                                                                    Component.text(
                                                                            " to exit",
                                                                            NamedTextColor.YELLOW));
                                            player.sendActionBar(message);
                                        }
                                    }
                                },
                                0L,
                                20L)
                        .getTaskId(); // Run every second
    }

    /**
     * Start the seat enforcement task that keeps players seated. This runs more frequently to catch
     * and prevent unwanted ejections. Also handles automatic timeout after configured duration.
     */
    private void startSeatEnforcementTask() {
        seatEnforcementTaskId =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                plugin,
                                () -> {
                                    int timeoutSeconds =
                                            plugin.getConfig()
                                                    .getInt("machines.seat_timeout_seconds", 300);
                                    long timeoutMillis = timeoutSeconds * 1000L;
                                    long warningMillis =
                                            (timeoutSeconds - 60)
                                                    * 1000L; // Warn 1 minute before timeout

                                    // Use a snapshot to avoid ConcurrentModificationException
                                    for (Map.Entry<UUID, SeatInfo> entry :
                                            new java.util.HashMap<>(playerSeats).entrySet()) {
                                        UUID playerId = entry.getKey();
                                        Player player = Bukkit.getPlayer(playerId);
                                        SeatInfo seatInfo = entry.getValue();
                                        ArmorStand seat = seatInfo.seat();
                                        long seatedAt = seatInfo.seatedAt();

                                        if (player == null || !player.isOnline()) {
                                            // Player offline, clean up
                                            synchronized (playerSeats) {
                                                playerSeats.remove(playerId);
                                                seatedPlayers.remove(playerId);
                                                warnedPlayers.remove(playerId);
                                            }
                                            continue;
                                        }

                                        if (seat == null || seat.isDead() || !seat.isValid()) {
                                            // Seat destroyed, clean up
                                            synchronized (playerSeats) {
                                                playerSeats.remove(playerId);
                                                seatedPlayers.remove(playerId);
                                                warnedPlayers.remove(playerId);
                                            }
                                            continue;
                                        }

                                        // Check timeout
                                        long elapsedMillis = System.currentTimeMillis() - seatedAt;
                                        if (elapsedMillis >= timeoutMillis) {
                                            // Timeout exceeded - force eject
                                            plugin.getLogger()
                                                    .info(
                                                            "Ejecting "
                                                                    + player.getName()
                                                                    + " from machine seat (timeout after "
                                                                    + timeoutSeconds
                                                                    + " seconds)");
                                            seat.removePassenger(player);
                                            synchronized (playerSeats) {
                                                playerSeats.remove(playerId);
                                                seatedPlayers.remove(playerId);
                                                warnedPlayers.remove(playerId);
                                            }
                                            TextUtil.sendError(
                                                    player,
                                                    "You have been automatically ejected from the machine after "
                                                            + timeoutSeconds
                                                            + " seconds.");
                                            continue;
                                        } else if (elapsedMillis >= warningMillis
                                                && !warnedPlayers.contains(playerId)) {
                                            // Send warning
                                            int remainingSeconds =
                                                    (int) ((timeoutMillis - elapsedMillis) / 1000);
                                            TextUtil.sendWarning(
                                                    player,
                                                    "You will be auto-ejected from this machine in "
                                                            + remainingSeconds
                                                            + " seconds.");
                                            warnedPlayers.add(playerId);
                                        }

                                        // Only re-seat if player was ejected by server (not
                                        // sneaking to exit)
                                        // Check if they're registered but not in vehicle and not
                                        // trying to exit
                                        if (!player.isInsideVehicle() && !player.isSneaking()) {
                                            if (plugin.getConfig()
                                                    .getBoolean("debug.machines", false)) {
                                                plugin.getLogger()
                                                        .info(
                                                                "[Machines] Re-seating "
                                                                        + player.getName()
                                                                        + " (was ejected without sneaking)");
                                            }
                                            seat.addPassenger(player);
                                        }
                                    }
                                },
                                1L,
                                1L)
                        .getTaskId(); // Run every tick for responsive re-seating
    }

    /** Register a player as seated at a machine. */
    public synchronized void registerSeatedPlayer(Player player, ArmorStand seat) {
        synchronized (playerSeats) {
            seatedPlayers.add(player.getUniqueId());
            playerSeats.put(player.getUniqueId(), new SeatInfo(seat, System.currentTimeMillis()));
            warnedPlayers.remove(player.getUniqueId()); // Reset warning status
        }

        if (plugin.getConfig().getBoolean("debug.machines", false)) {
            plugin.getLogger()
                    .info(
                            "[Machines] Registered "
                                    + player.getName()
                                    + " at seat "
                                    + seat.getUniqueId());
        }
    }

    /** Unregister a player from being seated. */
    public synchronized void unregisterSeatedPlayer(Player player) {
        synchronized (playerSeats) {
            seatedPlayers.remove(player.getUniqueId());
            playerSeats.remove(player.getUniqueId());
            warnedPlayers.remove(player.getUniqueId());
        }

        if (plugin.getConfig().getBoolean("debug.machines", false)) {
            plugin.getLogger().info("[Machines] Unregistered " + player.getName());
        }
    }

    /** Check if a player is seated at a machine. */
    public boolean isPlayerSeated(Player player) {
        return seatedPlayers.contains(player.getUniqueId());
    }

    /** Stop all tasks when the listener is disabled. */
    public void shutdown() {
        if (actionBarTaskId != -1) {
            Bukkit.getScheduler().cancelTask(actionBarTaskId);
        }
        if (seatEnforcementTaskId != -1) {
            Bukkit.getScheduler().cancelTask(seatEnforcementTaskId);
        }
        seatedPlayers.clear();
        playerSeats.clear();
        warnedPlayers.clear();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;

        MachineManager manager = plugin.getMachineManager();
        if (manager == null) return;

        Block block = event.getClickedBlock();

        // Check if player is placing a machine
        if (handleMachinePlacement(event)) {
            return;
        }

        // Check if clicking on a machine block
        Machine machine = manager.getMachineByBlock(block);
        if (machine != null) {
            event.setCancelled(true);

            Player player = event.getPlayer();

            // Check machine play permission
            if (!player.hasPermission("serverarcade.play")
                    && !player.hasPermission("serverarcade.admin")) {
                TextUtil.sendError(player, "You don't have permission to use arcade machines!");
                return;
            }

            // SECURITY: Check if player is self-excluded from gambling
            if (plugin.getExclusionManager() != null
                    && plugin.getExclusionManager().isExcluded(player.getUniqueId())) {
                var exclusion = plugin.getExclusionManager().getExclusion(player.getUniqueId());
                if (exclusion != null) {
                    TextUtil.sendError(player, "You are currently excluded from gambling.");
                    TextUtil.send(
                            player,
                            "<gray>Time remaining: <yellow>" + exclusion.getFormattedRemaining());
                    if (exclusion.getReason() != null && !exclusion.getReason().isEmpty()) {
                        TextUtil.send(player, "<gray>Reason: <yellow>" + exclusion.getReason());
                    }
                } else {
                    TextUtil.sendError(player, "You are currently excluded from gambling.");
                }
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> machine.interact(player));
        }
    }

    /** Handle placing a new machine with a placement item. */
    private boolean handleMachinePlacement(PlayerInteractEvent event) {
        if (event.getItem() == null) return false;

        var meta = event.getItem().getItemMeta();
        if (meta == null) return false;

        // Check if item has machine placement data
        if (!meta.getPersistentDataContainer()
                .has(Machine.MACHINE_ITEM_KEY, PersistentDataType.STRING)) {
            return false;
        }

        String gameTypeName =
                meta.getPersistentDataContainer()
                        .get(Machine.MACHINE_ITEM_KEY, PersistentDataType.STRING);
        if (gameTypeName == null) return false;

        // Cancel the event early to prevent any potential double-handling
        event.setCancelled(true);

        try {
            GameType gameType = plugin.getGameType(gameTypeName);
            if (gameType == null) return true;

            Player player = event.getPlayer();

            // Check category-specific placement permission
            MachineCategory category = gameType.getCategory();
            if (!player.hasPermission(category.getPlacePermission())
                    && !player.hasPermission("serverarcade.machine.admin")) {
                TextUtil.sendError(
                        player,
                        "You don't have permission to place "
                                + category.getConfigName()
                                + " machines.");
                return true;
            }

            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock == null) return true;
            BlockFace face = event.getBlockFace();
            Location placeLoc;

            // Calculate placement based on which face was clicked
            // Use getRelative() for clearer block-based positioning
            if (face == BlockFace.UP) {
                // Clicking top of block - place on the block above (surface)
                Block targetBlock = clickedBlock.getRelative(BlockFace.UP);
                placeLoc = targetBlock.getLocation().add(0.5, 0, 0.5);
            } else if (face == BlockFace.DOWN) {
                // Clicking bottom of block - place at clicked block level
                placeLoc = clickedBlock.getLocation().add(0.5, 0, 0.5);
            } else {
                // Clicking side face (wall) - place adjacent to wall, on the surface
                Block adjacentBlock = clickedBlock.getRelative(face);
                Block targetBlock = adjacentBlock.getRelative(BlockFace.UP);
                placeLoc = targetBlock.getLocation().add(0.5, 0, 0.5);
            }

            // Machine faces towards player based on relative positions (like DreamArcade)
            Direction direction = Direction.getDirectionFromPlayer(player.getLocation(), placeLoc);

            MachineStructure structure = gameType.getMachineStructure();
            if (structure != null && !structure.canPlace(placeLoc, direction)) {
                TextUtil.sendError(player, "Cannot place machine here - not enough space!");
                return true;
            }

            Machine machine =
                    plugin.getMachineManager().createMachine(gameType, placeLoc, direction, player);
            if (machine != null) {
                TextUtil.send(
                        player,
                        "<green>Placed "
                                + gameType.getName()
                                + " <green>(ID: "
                                + machine.getId()
                                + ")");
            } else {
                TextUtil.sendError(player, "Failed to place machine here!");
            }

            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Error placing machine: " + e.getMessage());
            return true;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onArmorStandInteract(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand stand)) return;
        if (!stand.getPersistentDataContainer()
                .has(Machine.MACHINE_ENTITY_KEY, PersistentDataType.STRING)) return;

        event.setCancelled(true);

        String machineId =
                stand.getPersistentDataContainer()
                        .get(Machine.MACHINE_ENTITY_KEY, PersistentDataType.STRING);
        if (machineId == null) return;

        MachineManager manager = plugin.getMachineManager();
        if (manager == null) return;

        Machine machine = manager.getMachine(machineId);
        if (machine != null) {
            Player player = event.getPlayer();

            // Check machine play permission
            if (!player.hasPermission("serverarcade.play")
                    && !player.hasPermission("serverarcade.admin")) {
                TextUtil.sendError(player, "You don't have permission to use arcade machines!");
                return;
            }

            // SECURITY: Check if player is self-excluded from gambling
            if (plugin.getExclusionManager() != null
                    && plugin.getExclusionManager().isExcluded(player.getUniqueId())) {
                var exclusion = plugin.getExclusionManager().getExclusion(player.getUniqueId());
                if (exclusion != null) {
                    TextUtil.sendError(player, "You are currently excluded from gambling.");
                    TextUtil.send(
                            player,
                            "<gray>Time remaining: <yellow>" + exclusion.getFormattedRemaining());
                    if (exclusion.getReason() != null && !exclusion.getReason().isEmpty()) {
                        TextUtil.send(player, "<gray>Reason: <yellow>" + exclusion.getReason());
                    }
                } else {
                    TextUtil.sendError(player, "You are currently excluded from gambling.");
                }
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> machine.interact(player));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        ArmorStand stand = event.getRightClicked();
        if (stand.getPersistentDataContainer()
                .has(Machine.MACHINE_ENTITY_KEY, PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntityType() != EntityType.ARMOR_STAND) return;

        ArmorStand stand = (ArmorStand) event.getEntity();
        if (stand.getPersistentDataContainer()
                .has(Machine.MACHINE_ENTITY_KEY, PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        MachineManager manager = plugin.getMachineManager();
        if (manager == null) return;

        Block block = event.getBlock();
        Machine machine = manager.getMachineByBlock(block);

        if (machine != null) {
            Player player = event.getPlayer();

            // Only allow the placer or admins to break it
            if (!machine.getPlacedBy().equals(player.getUniqueId())
                    && !player.hasPermission("serverarcade.machine.break.others")) {
                event.setCancelled(true);
                TextUtil.sendError(player, "You cannot break this arcade machine!");
                return;
            }

            // Require sneaking to break (like DreamArcade)
            if (!player.isSneaking()) {
                event.setCancelled(true);
                TextUtil.send(
                        player,
                        "<yellow>Sneak and break to remove machine <gray>(ID: "
                                + machine.getId()
                                + ")");
                return;
            }

            // Remove the machine
            manager.removeMachine(machine.getId());
            TextUtil.send(
                    player, "<green>Arcade machine removed. <gray>(ID: " + machine.getId() + ")");
            event.setCancelled(
                    true); // Cancel the natural break, machine.destroy() handles block removal
        }
    }

    @EventHandler
    public void onWorldSave(WorldSaveEvent event) {
        // Rate limit saves to once per 20 minutes
        long now = System.currentTimeMillis();
        if (now - lastSave < 20 * 60 * 1000) return;
        lastSave = now;

        MachineManager manager = plugin.getMachineManager();
        if (manager != null) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, manager::saveAllMachines);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onVehicleExit(VehicleExitEvent event) {
        boolean debugEnabled = plugin.getConfig().getBoolean("debug.machines", false);

        if (debugEnabled) {
            plugin.getLogger()
                    .info(
                            "[Machines] VehicleExitEvent fired! Vehicle: "
                                    + event.getVehicle().getType()
                                    + ", Exited: "
                                    + (event.getExited() instanceof Player
                                            ? ((Player) event.getExited()).getName()
                                            : "not a player"));
        }

        if (!(event.getExited() instanceof Player player)) return;
        if (!(event.getVehicle() instanceof ArmorStand stand)) return;

        if (debugEnabled) {
            plugin.getLogger()
                    .info(
                            "[Machines] VehicleExitEvent is armor stand! Checking for machine seat...");
        }

        // Check if this is a machine seat - use persistent data instead of scoreboard tag
        // This works for both old and new machines
        if (!stand.getPersistentDataContainer()
                .has(Machine.MACHINE_ENTITY_KEY, PersistentDataType.STRING)) {
            if (debugEnabled) {
                plugin.getLogger().info("[Machines] Not a machine entity");
            }
            return;
        }
        if (!stand.getPersistentDataContainer()
                .has(Machine.MACHINE_SEAT_KEY, PersistentDataType.INTEGER)) {
            if (debugEnabled) {
                plugin.getLogger().info("[Machines] Not a machine seat");
            }
            return;
        }

        if (debugEnabled) {
            plugin.getLogger()
                    .info(
                            "[Machines] Player "
                                    + player.getName()
                                    + " trying to exit arcade seat. Sneaking: "
                                    + player.isSneaking());
        }

        // Only allow dismount if player is sneaking
        if (!player.isSneaking()) {
            if (debugEnabled) {
                plugin.getLogger().info("[Machines] Blocking exit - player is not sneaking");
            }
            event.setCancelled(true);
        } else {
            // Player is dismounting with shift, unregister them
            if (debugEnabled) {
                plugin.getLogger().info("[Machines] Allowing exit - player is sneaking");
            }
            unregisterSeatedPlayer(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryOpen(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!seatedPlayers.contains(player.getUniqueId())) return;

        if (plugin.getConfig().getBoolean("debug.machines", false)) {
            plugin.getLogger()
                    .info(
                            "[Machines] Seated player "
                                    + player.getName()
                                    + " opened inventory. Still in vehicle: "
                                    + player.isInsideVehicle());

            // Monitor if opening inventory causes ejection
            Bukkit.getScheduler()
                    .runTaskLater(
                            plugin,
                            () -> {
                                plugin.getLogger()
                                        .info(
                                                "[Machines] [AFTER INVENTORY OPEN] Player in vehicle: "
                                                        + player.isInsideVehicle());
                            },
                            1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!seatedPlayers.contains(player.getUniqueId())) return;

        if (plugin.getConfig().getBoolean("debug.machines", false)) {
            plugin.getLogger()
                    .info(
                            "[Machines] Seated player "
                                    + player.getName()
                                    + " closed inventory. Still in vehicle: "
                                    + player.isInsideVehicle());

            // Monitor if closing inventory causes ejection
            Bukkit.getScheduler()
                    .runTaskLater(
                            plugin,
                            () -> {
                                plugin.getLogger()
                                        .info(
                                                "[Machines] [AFTER INVENTORY CLOSE] Player in vehicle: "
                                                        + player.isInsideVehicle());
                            },
                            1L);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerToggleSneak(org.bukkit.event.player.PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        boolean debugEnabled = plugin.getConfig().getBoolean("debug.machines", false);

        if (event.isSneaking() && player.isInsideVehicle()) {
            if (debugEnabled) {
                plugin.getLogger()
                        .info(
                                "[Machines] Player "
                                        + player.getName()
                                        + " started sneaking while in vehicle");
            }

            if (player.getVehicle() instanceof ArmorStand stand) {
                if (debugEnabled) {
                    plugin.getLogger().info("[Machines] Vehicle is armor stand");
                }

                if (stand.getPersistentDataContainer()
                        .has(Machine.MACHINE_SEAT_KEY, PersistentDataType.INTEGER)) {
                    if (debugEnabled) {
                        plugin.getLogger().info("[Machines] It's a machine seat! Allowing exit...");
                    }

                    // Unregister first so enforcement doesn't re-seat them
                    unregisterSeatedPlayer(player);

                    // Then eject
                    Bukkit.getScheduler()
                            .runTask(
                                    plugin,
                                    () -> {
                                        player.leaveVehicle();
                                        if (debugEnabled) {
                                            plugin.getLogger()
                                                    .info(
                                                            "[Machines] Player "
                                                                    + player.getName()
                                                                    + " manually left arcade seat");
                                        }
                                    });
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clean up seated player tracking when they disconnect
        seatedPlayers.remove(event.getPlayer().getUniqueId());
    }
}
