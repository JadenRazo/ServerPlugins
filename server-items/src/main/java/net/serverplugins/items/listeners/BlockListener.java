package net.serverplugins.items.listeners;

import net.serverplugins.items.ServerItems;
import net.serverplugins.items.managers.BlockManager;
import net.serverplugins.items.managers.ItemManager;
import net.serverplugins.items.models.CustomBlock;
import net.serverplugins.items.models.PlacedBlock;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;

public class BlockListener implements Listener {

    private final ServerItems plugin;
    private final BlockManager blockManager;
    private final ItemManager itemManager;

    public BlockListener(ServerItems plugin) {
        this.plugin = plugin;
        this.blockManager = plugin.getBlockManager();
        this.itemManager = plugin.getItemManager();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack hand = event.getItemInHand();

        String itemId = itemManager.getItemId(hand);
        if (itemId == null) return;

        CustomBlock block = blockManager.getBlock(itemId);
        if (block == null) return;

        // Cancel the vanilla placement and do our custom placement
        event.setCancelled(true);

        Block placed = event.getBlockPlaced();
        blockManager.placeBlock(placed.getLocation(), block, player.getUniqueId());

        // Consume item in survival
        if (player.getGameMode() == GameMode.SURVIVAL
                || player.getGameMode() == GameMode.ADVENTURE) {
            hand.setAmount(hand.getAmount() - 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.NOTE_BLOCK) return;

        if (!blockManager.isCustomBlock(block.getLocation())) return;

        event.setDropItems(false);
        PlacedBlock placed = blockManager.removeBlock(block.getLocation());

        if (placed != null && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            // Drop the custom item
            CustomBlock customBlock = blockManager.getBlock(placed.blockId());
            if (customBlock != null) {
                String dropId = customBlock.getDropItemId();
                net.serverplugins.items.models.CustomItem dropItem = itemManager.getItem(dropId);
                if (dropItem != null) {
                    ItemStack drop = itemManager.buildItemStack(dropItem, 1);
                    block.getWorld().dropItemNaturally(block.getLocation(), drop);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        // Prevent noteblock state changes from redstone/physics
        if (event.getBlock().getType() == Material.NOTE_BLOCK) {
            if (blockManager.isCustomBlock(event.getBlock().getLocation())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        String world = event.getWorld().getName();
        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(plugin, () -> blockManager.loadChunk(world, chunkX, chunkZ));
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        String world = event.getWorld().getName();
        blockManager.unloadChunk(world, event.getChunk().getX(), event.getChunk().getZ());
    }
}
