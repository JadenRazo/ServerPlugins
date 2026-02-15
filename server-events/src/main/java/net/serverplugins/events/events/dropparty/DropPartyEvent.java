package net.serverplugins.events.events.dropparty;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.api.messages.MessageBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.events.EventsConfig;
import net.serverplugins.events.ServerEvents;
import net.serverplugins.events.events.ServerEvent;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/** Drop party event - items rain from the sky for players to collect. */
public class DropPartyEvent implements ServerEvent {

    private final ServerEvents plugin;
    private final EventsConfig config;
    private final Random random = new Random();
    private final NamespacedKey dropPartyKey;

    private boolean active;
    private BukkitTask dropTask;
    private int dropsRemaining;
    private final List<Item> droppedItems = new ArrayList<>();
    private final Set<Chunk> forceLoadedChunks = new HashSet<>();

    public DropPartyEvent(ServerEvents plugin) {
        this.plugin = plugin;
        this.config = plugin.getEventsConfig();
        this.dropPartyKey = new NamespacedKey(plugin, "drop_party_item");
    }

    /** Force load chunks around spawn to ensure items persist even when no players are online. */
    private void forceLoadSpawnChunks() {
        Location spawnLoc = config.getSpawnLocation();
        if (spawnLoc == null || spawnLoc.getWorld() == null) return;

        World world = spawnLoc.getWorld();
        // Use larger radius to account for items bouncing/rolling far from spawn
        double radius = Math.max(config.getDropPartyRadius() * 3, 50);
        int chunkRadius = (int) Math.ceil(radius / 16) + 2;
        int centerChunkX = spawnLoc.getBlockX() >> 4;
        int centerChunkZ = spawnLoc.getBlockZ() >> 4;

        forceLoadedChunks.clear();

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                Chunk chunk = world.getChunkAt(centerChunkX + dx, centerChunkZ + dz);
                if (!chunk.isForceLoaded()) {
                    chunk.setForceLoaded(true);
                    forceLoadedChunks.add(chunk);
                }
            }
        }

        plugin.getLogger()
                .info("Force loaded " + forceLoadedChunks.size() + " chunks for drop party event");
    }

    /** Remove force loading from chunks that were loaded for this event. */
    private void unforceLoadChunks() {
        for (Chunk chunk : forceLoadedChunks) {
            if (chunk.isForceLoaded()) {
                chunk.setForceLoaded(false);
            }
        }
        plugin.getLogger().info("Unloaded " + forceLoadedChunks.size() + " force-loaded chunks");
        forceLoadedChunks.clear();
    }

    @Override
    public EventType getType() {
        return EventType.DROP_PARTY;
    }

    @Override
    public String getDisplayName() {
        return "Drop Party";
    }

    @Override
    public void start() {
        if (active) return;

        active = true;
        dropsRemaining = config.getDropPartyTotalDrops();
        droppedItems.clear();

        // Force load spawn chunks to ensure items persist even if all players leave
        forceLoadSpawnChunks();

        // Announce the event
        announceEvent();

        // Start dropping items
        int intervalTicks = config.getDropPartyInterval();
        dropTask =
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!active || dropsRemaining <= 0) {
                            endDropParty();
                            return;
                        }

                        dropItems();
                    }
                }.runTaskTimer(plugin, 20L, intervalTicks); // 1 second delay, then interval
    }

    private void announceEvent() {
        String prefix = config.getPrefix();
        String message = config.getDropPartyMessage("start");

        broadcastWithTeleport(prefix + message);

        // Play sound
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
        }
    }

    /** Broadcast a message with a clickable [TELEPORT] button */
    private void broadcastWithTeleport(String message) {
        Component textComponent =
                TextUtil.parse(message)
                        .append(Component.text(" "))
                        .append(
                                Component.text("[TELEPORT TO SPAWN]")
                                        .color(NamedTextColor.GREEN)
                                        .decorate(TextDecoration.BOLD)
                                        .clickEvent(ClickEvent.runCommand("/spawn"))
                                        .hoverEvent(
                                                HoverEvent.showText(
                                                        Component.text(
                                                                        "Click to teleport to spawn!")
                                                                .color(NamedTextColor.YELLOW))));

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(textComponent);
        }
    }

    private void dropItems() {
        Location spawnLoc = config.getSpawnLocation();
        int itemsPerDrop = config.getDropPartyItemsPerDrop();
        double radius = config.getDropPartyRadius();
        double height = config.getDropPartyHeight();

        List<ItemStack> dropPool = config.getDropPartyItems();
        if (dropPool.isEmpty()) return;

        for (int i = 0; i < itemsPerDrop && dropsRemaining > 0; i++) {
            // Random position within radius
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * radius;
            double x = spawnLoc.getX() + Math.cos(angle) * distance;
            double z = spawnLoc.getZ() + Math.sin(angle) * distance;
            Location dropLoc = new Location(spawnLoc.getWorld(), x, spawnLoc.getY() + height, z);

            // Pick random item from pool
            ItemStack item = dropPool.get(random.nextInt(dropPool.size())).clone();

            // Drop the item
            Item droppedItem = dropLoc.getWorld().dropItem(dropLoc, item);
            droppedItem.setVelocity(
                    new Vector(
                            (random.nextDouble() - 0.5) * 0.2,
                            -0.2,
                            (random.nextDouble() - 0.5) * 0.2));
            droppedItem.setPickupDelay(10); // Small delay to prevent instant pickup
            droppedItem.setGlowing(true);

            // Tag with PersistentDataContainer for reliable identification during cleanup
            droppedItem
                    .getPersistentDataContainer()
                    .set(dropPartyKey, PersistentDataType.BYTE, (byte) 1);

            droppedItems.add(droppedItem);
            dropsRemaining--;
        }

        // Play drop sound at spawn
        spawnLoc.getWorld().playSound(spawnLoc, Sound.ENTITY_CHICKEN_EGG, 1.0f, 1.5f);

        // Spawn particles
        spawnLoc.getWorld()
                .spawnParticle(
                        Particle.FIREWORK,
                        spawnLoc.clone().add(0, height, 0),
                        20,
                        radius,
                        1,
                        radius,
                        0.1);
    }

    private void endDropParty() {
        // Announce end
        String message = config.getDropPartyMessage("end");
        TextUtil.broadcastRaw(config.getPrefix() + message);

        // Play sound
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(
                    player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.0f, 1.0f);
        }

        // Capture items NOW before the list can be modified by a new event
        final List<Item> itemsToCleanup = new ArrayList<>(droppedItems);
        final Location cleanupLocation = config.getSpawnLocation();
        final Set<Chunk> chunksToUnload = new HashSet<>(forceLoadedChunks);

        // Schedule cleanup of uncollected items after a delay
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupItemsFromList(itemsToCleanup, cleanupLocation, chunksToUnload);
            }
        }.runTaskLater(plugin, 200L); // 10 seconds later

        // Clear our references (cleanup task has its own copies)
        droppedItems.clear();
        forceLoadedChunks.clear();

        // Stop the event
        stop();
    }

    /**
     * Clean up items from a captured list - called with delay after event ends. Uses captured
     * references so a new event starting won't interfere.
     */
    private void cleanupItemsFromList(
            List<Item> itemsToCleanup, Location spawnLoc, Set<Chunk> chunksToUnload) {
        int removed = 0;

        // First pass: Remove all tracked items directly using our captured references
        // This works even if chunks are unloaded because we have direct entity references
        for (Item item : itemsToCleanup) {
            if (item != null && item.isValid() && !item.isDead()) {
                item.remove();
                removed++;
            }
        }

        // Second pass: Scan chunks for any items tagged with our PDC key
        // This catches any items we might have missed tracking
        if (spawnLoc != null && spawnLoc.getWorld() != null) {
            World world = spawnLoc.getWorld();

            // Make sure chunks are loaded for scanning
            double searchRadius = Math.max(config.getDropPartyRadius() * 3, 50);
            int chunkRadius = (int) Math.ceil(searchRadius / 16) + 2;
            int centerChunkX = spawnLoc.getBlockX() >> 4;
            int centerChunkZ = spawnLoc.getBlockZ() >> 4;

            for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                    Chunk chunk = world.getChunkAt(centerChunkX + dx, centerChunkZ + dz);
                    // Force load the chunk temporarily if needed to scan entities
                    boolean wasLoaded = chunk.isLoaded();
                    if (!wasLoaded) {
                        chunk.load();
                    }

                    for (Entity entity : chunk.getEntities()) {
                        if (entity instanceof Item item) {
                            // Check PersistentDataContainer tag
                            if (item.getPersistentDataContainer()
                                    .has(dropPartyKey, PersistentDataType.BYTE)) {
                                if (!item.isDead()) {
                                    item.remove();
                                    removed++;
                                }
                            }
                        }
                    }
                }
            }
        }

        if (removed > 0) {
            String message =
                    MessageBuilder.create()
                            .prefix(config.getMessenger().getPrefix())
                            .append(
                                    ColorScheme.SECONDARY
                                            + removed
                                            + " uncollected items have despawned.")
                            .build();
            TextUtil.broadcastRaw(message);
        }

        // Release the force-loaded chunks that were captured when this cleanup was scheduled
        for (Chunk chunk : chunksToUnload) {
            if (chunk.isForceLoaded()) {
                chunk.setForceLoaded(false);
            }
        }

        plugin.getLogger()
                .info(
                        "Drop party cleanup complete: removed "
                                + removed
                                + " items, unloaded "
                                + chunksToUnload.size()
                                + " chunks");
    }

    @Override
    public void stop() {
        if (!active) return;

        active = false;

        // Cancel drop task
        if (dropTask != null) {
            dropTask.cancel();
            dropTask = null;
        }

        // Clear event reference
        plugin.getEventManager().clearActiveEvent();
    }

    @Override
    public boolean isActive() {
        return active;
    }

    public int getDropsRemaining() {
        return dropsRemaining;
    }
}
