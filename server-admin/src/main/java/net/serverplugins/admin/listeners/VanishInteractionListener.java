package net.serverplugins.admin.listeners;

import net.serverplugins.admin.ServerAdmin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.raid.RaidTriggerEvent;
import org.bukkit.event.world.GenericGameEvent;

public class VanishInteractionListener implements Listener {

    private final ServerAdmin plugin;

    public VanishInteractionListener(ServerAdmin plugin) {
        this.plugin = plugin;
    }

    /** Block pressure plate activation for vanished players */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPressurePlate(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL) {
            return;
        }

        Player player = event.getPlayer();
        if (!plugin.getVanishManager().isVanished(player)) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        Material type = block.getType();

        // Block pressure plates
        if (plugin.getAdminConfig().vanishBlockPressurePlates()) {
            if (type.name().contains("PRESSURE_PLATE")) {
                event.setCancelled(true);
                return;
            }
        }

        // Block tripwire
        if (plugin.getAdminConfig().vanishBlockTripwire()) {
            if (type == Material.TRIPWIRE || type == Material.STRING) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** Block turtle egg trampling for vanished players */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTurtleEggTrample(EntityChangeBlockEvent event) {
        if (!plugin.getAdminConfig().vanishBlockTurtleEggs()) {
            return;
        }

        Entity entity = event.getEntity();
        if (!(entity instanceof Player player)) {
            return;
        }

        if (!plugin.getVanishManager().isVanished(player)) {
            return;
        }

        Block block = event.getBlock();
        if (block.getType() == Material.TURTLE_EGG) {
            event.setCancelled(true);
        }
    }

    /** Block dripleaf tilting for vanished players */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDripleafTilt(EntityChangeBlockEvent event) {
        if (!plugin.getAdminConfig().vanishBlockDripleaf()) {
            return;
        }

        Entity entity = event.getEntity();
        if (!(entity instanceof Player player)) {
            return;
        }

        if (!plugin.getVanishManager().isVanished(player)) {
            return;
        }

        Block block = event.getBlock();
        if (block.getType() == Material.BIG_DRIPLEAF) {
            event.setCancelled(true);
        }
    }

    /** Block sculk sensor activation and other game events for vanished players */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGenericGameEvent(GenericGameEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player player)) {
            return;
        }

        if (!plugin.getVanishManager().isVanished(player)) {
            return;
        }

        // Block ALL game events from vanished players to prevent detection
        // This includes sculk sensors, warden activation, and any other vibration-based detection
        event.setCancelled(true);
    }

    /** Block raid triggering for vanished players */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRaidTrigger(RaidTriggerEvent event) {
        Player player = event.getPlayer();
        if (plugin.getVanishManager().isVanished(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Silent container interaction - Opens without animation/sound to others Uses VanishManager to
     * track and ProtocolLib to block packets
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onContainerInteract(PlayerInteractEvent event) {
        if (!plugin.getAdminConfig().vanishSilentChest()) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        if (!plugin.getVanishManager().isVanished(player)) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        Material type = block.getType();
        if (isContainer(type)) {
            // Register this container open for silent operation
            // VanishManager will track this and block animation packets to other players
            plugin.getVanishManager().registerSilentContainerOpen(player, block);
        }
    }

    private boolean isContainer(Material type) {
        return type == Material.CHEST
                || type == Material.TRAPPED_CHEST
                || type == Material.BARREL
                || type == Material.SHULKER_BOX
                || type == Material.ENDER_CHEST
                || type.name().contains("SHULKER_BOX");
    }
}
