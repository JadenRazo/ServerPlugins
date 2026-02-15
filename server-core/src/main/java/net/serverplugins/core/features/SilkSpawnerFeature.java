package net.serverplugins.core.features;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.serverplugins.api.utils.LegacyText;
import net.serverplugins.core.ServerCore;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

public class SilkSpawnerFeature extends Feature implements Listener {

    private static final String PERMISSION = "servercore.silkspawner";

    // Track recently processed spawner breaks to prevent duplicates
    private final Map<Location, Long> recentBreaks = new ConcurrentHashMap<>();
    private static final long DUPLICATE_WINDOW_MS = 500; // 500ms window

    public SilkSpawnerFeature(ServerCore plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "Silk Touch Spawners";
    }

    @Override
    public String getDescription() {
        return "Allows players to mine spawners with silk touch pickaxes";
    }

    @Override
    protected void onEnable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger()
                .info(
                        "Silk Touch Spawners enabled - players can now mine spawners with silk touch");
    }

    @Override
    protected void onDisable() {
        BlockBreakEvent.getHandlerList().unregister(this);
        BlockPlaceEvent.getHandlerList().unregister(this);
        recentBreaks.clear();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isEnabled()) return;

        Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER) return;

        Player player = event.getPlayer();

        // Don't process if in creative mode
        if (player.getGameMode() == GameMode.CREATIVE) return;

        // Check if player has permission (if required)
        if (isPermissionRequired() && !player.hasPermission(PERMISSION)) {
            return;
        }

        // Check if player is holding a silk touch tool
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!hasSilkTouch(tool)) return;

        // Must be a pickaxe
        if (!isPickaxe(tool.getType())) return;

        // Prevent duplicate processing - check if we already processed this location recently
        Location loc = block.getLocation();
        long now = System.currentTimeMillis();
        Long lastProcessed = recentBreaks.get(loc);
        if (lastProcessed != null && (now - lastProcessed) < DUPLICATE_WINDOW_MS) {
            // Already processed this spawner break recently, skip to prevent duplicate
            return;
        }
        recentBreaks.put(loc, now);

        // Cleanup old entries periodically
        if (recentBreaks.size() > 100) {
            recentBreaks.entrySet().removeIf(e -> (now - e.getValue()) > DUPLICATE_WINDOW_MS * 2);
        }

        // Get the spawner data before it's broken
        if (!(block.getState() instanceof CreatureSpawner spawner)) return;

        EntityType entityType = spawner.getSpawnedType();
        if (entityType == null) {
            entityType = EntityType.PIG; // Default fallback
        }

        // Cancel default drops
        event.setExpToDrop(0);
        event.setDropItems(false);

        // Create spawner item with correct mob type
        ItemStack spawnerItem = createSpawnerItem(entityType);

        // Drop the spawner
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), spawnerItem);

        // Send message if configured
        if (shouldSendMessages()) {
            String mobName = formatEntityName(entityType);
            plugin.getServer()
                    .getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                plugin.getCoreConfig()
                                        .getMessenger()
                                        .send(
                                                player,
                                                "silk-spawner.mined",
                                                net.serverplugins.api.messages.Placeholder.of(
                                                        "mob", mobName));
                            });
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isEnabled()) return;

        ItemStack item = event.getItemInHand();
        if (item.getType() != Material.SPAWNER) return;

        Block block = event.getBlock();
        if (!(block.getState() instanceof CreatureSpawner spawner)) return;

        // Check if the item has spawner metadata
        if (!(item.getItemMeta() instanceof BlockStateMeta meta)) return;
        if (!(meta.getBlockState() instanceof CreatureSpawner itemSpawner)) return;

        EntityType entityType = itemSpawner.getSpawnedType();
        if (entityType == null) return;

        // Apply the entity type to the placed spawner
        spawner.setSpawnedType(entityType);
        spawner.update();
    }

    private ItemStack createSpawnerItem(EntityType entityType) {
        ItemStack item = new ItemStack(Material.SPAWNER);
        BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();

        if (meta != null) {
            CreatureSpawner spawnerState = (CreatureSpawner) meta.getBlockState();
            spawnerState.setSpawnedType(entityType);
            meta.setBlockState(spawnerState);

            // Set display name
            String mobName = formatEntityName(entityType);
            meta.setDisplayName(LegacyText.colorize("&e" + mobName + " Spawner"));

            item.setItemMeta(meta);
        }

        return item;
    }

    private boolean hasSilkTouch(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return item.containsEnchantment(Enchantment.SILK_TOUCH);
    }

    private boolean isPickaxe(Material material) {
        return switch (material) {
            case WOODEN_PICKAXE,
                            STONE_PICKAXE,
                            IRON_PICKAXE,
                            GOLDEN_PICKAXE,
                            DIAMOND_PICKAXE,
                            NETHERITE_PICKAXE ->
                    true;
            default -> false;
        };
    }

    private String formatEntityName(EntityType type) {
        String name = type.name().toLowerCase().replace("_", " ");
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : name.toCharArray()) {
            if (c == ' ') {
                result.append(' ');
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    private boolean isPermissionRequired() {
        return plugin.getConfig().getBoolean("settings.silk-spawner.require-permission", false);
    }

    private boolean shouldSendMessages() {
        return plugin.getConfig().getBoolean("settings.silk-spawner.send-messages", true);
    }
}
