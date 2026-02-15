package net.serverplugins.core.features;

import net.serverplugins.core.ServerCore;
import net.serverplugins.core.data.PlayerDataManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class DoubleDoorFeature extends Feature implements Listener, PerPlayerFeature {

    private static final String FEATURE_KEY = "double-door";

    public DoubleDoorFeature(ServerCore plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "Double Door";
    }

    @Override
    public String getDescription() {
        return "Opens adjacent doors together";
    }

    @Override
    public String getFeatureKey() {
        return FEATURE_KEY;
    }

    @Override
    public boolean isEnabledForPlayer(Player player) {
        if (!isEnabled()) return false;

        try {
            PlayerDataManager dataManager = plugin.getPlayerDataManager();
            PlayerDataManager.PlayerData data = dataManager.loadPlayerData(player.getUniqueId());

            // If player has a preference, use it; otherwise use global state
            if (data.hasFeaturePreference(FEATURE_KEY)) {
                return data.isFeatureEnabled(FEATURE_KEY);
            }
        } catch (IllegalStateException e) {
            // Player data not preloaded (e.g. after plugin reload) — fall back to global state
        }

        return isEnabled();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!isEnabled()) return;

        Player player = event.getPlayer();
        if (!player.hasPermission("servercore.doubledoor")) return;
        if (!isEnabledForPlayer(player)) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;
        if (!isDoor(clickedBlock.getType())) return;

        Block adjacentDoor = findAdjacentDoor(clickedBlock);
        if (adjacentDoor != null) {
            plugin.getServer()
                    .getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                Door adjacentDoorData = (Door) adjacentDoor.getBlockData();
                                adjacentDoorData.setOpen(!adjacentDoorData.isOpen());
                                adjacentDoor.setBlockData(adjacentDoorData);
                            });
        }
    }

    private Block findAdjacentDoor(Block door) {
        Door doorData = (Door) door.getBlockData();
        Block bottomBlock =
                doorData.getHalf() == Bisected.Half.TOP ? door.getRelative(BlockFace.DOWN) : door;

        int maxDistance = plugin.getCoreConfig().getDoubleDoorMaxDistance();
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

        for (BlockFace face : faces) {
            for (int i = 1; i <= maxDistance; i++) {
                Block relative = bottomBlock.getRelative(face, i);
                if (isDoor(relative.getType())) {
                    Door relativeData = (Door) relative.getBlockData();
                    if (relativeData.getHalf() == Bisected.Half.BOTTOM) {
                        if (isAligned(doorData, relativeData)) return relative;
                    }
                }
            }
        }
        return null;
    }

    private boolean isAligned(Door door1, Door door2) {
        BlockFace facing1 = door1.getFacing();
        BlockFace facing2 = door2.getFacing();
        return facing1 == facing2 || facing1.getOppositeFace() == facing2;
    }

    private boolean isDoor(Material material) {
        return material.name().endsWith("_DOOR") && material != Material.IRON_DOOR;
    }
}
