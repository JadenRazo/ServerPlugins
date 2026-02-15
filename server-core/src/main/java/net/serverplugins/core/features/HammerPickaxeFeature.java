package net.serverplugins.core.features;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.core.ServerCore;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;

public class HammerPickaxeFeature extends Feature implements Listener {

    private static final String USE_PERMISSION = "servercore.hammerpick.use";

    private final NamespacedKey hammerKey;
    private final NamespacedKey tierKey;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Set<UUID> activeHammerMiners = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BlockFace> lastHitFace = new ConcurrentHashMap<>();

    public HammerPickaxeFeature(ServerCore plugin) {
        super(plugin);
        this.hammerKey = new NamespacedKey(plugin, "hammer_pick");
        this.tierKey = new NamespacedKey(plugin, "hammer_tier");
    }

    @Override
    public String getName() {
        return "Hammer Pickaxe";
    }

    @Override
    public String getDescription() {
        return "Allows radius mining with tiered hammer pickaxes";
    }

    @Override
    protected void onEnable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("Hammer Pickaxe enabled - radius mining available");
    }

    @Override
    protected void onDisable() {
        HandlerList.unregisterAll(this);
        cooldowns.clear();
        activeHammerMiners.clear();
        lastHitFace.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!isEnabled()) return;
        if (event.getAction() != org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null || event.getBlockFace() == null) return;

        Player player = event.getPlayer();
        if (isHammerPickaxe(player.getInventory().getItemInMainHand())) {
            lastHitFace.put(player.getUniqueId(), event.getBlockFace());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isEnabled()) return;

        Player player = event.getPlayer();

        // Skip if this is a recursive call from our own radius mining
        if (activeHammerMiners.contains(player.getUniqueId())) return;

        if (player.getGameMode() == GameMode.CREATIVE) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!isHammerPickaxe(tool)) return;

        if (!player.hasPermission(USE_PERMISSION)) return;

        // Check cooldown
        long cooldownMs = plugin.getConfig().getLong("settings.hammer-pickaxe.cooldown-ms", 1000);
        long now = System.currentTimeMillis();
        Long lastUse = cooldowns.get(player.getUniqueId());
        if (lastUse != null && (now - lastUse) < cooldownMs) {
            return;
        }
        cooldowns.put(player.getUniqueId(), now);

        int tier = getTier(tool);
        int radius =
                plugin.getConfig().getInt("settings.hammer-pickaxe.tiers." + tier + ".radius", 1);

        // Determine the mining plane from the cached hit face or fallback to rayTrace
        Block center = event.getBlock();
        BlockFace face = getTargetBlockFace(player, center);
        if (face == null) return;

        List<String> excludedBlocks =
                plugin.getConfig().getStringList("settings.hammer-pickaxe.excluded-blocks");

        activeHammerMiners.add(player.getUniqueId());
        try {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    if (dx == 0 && dy == 0) continue;

                    Block target = getOffsetBlock(center, face, dx, dy);
                    if (target == null) continue;
                    if (target.getType().isAir()) continue;
                    if (target.getType() == Material.BEDROCK) continue;
                    if (target.getType().getHardness() < 0) continue;

                    // Skip container blocks programmatically (all chests, shulkers, furnaces, etc.)
                    if (target.getState(false) instanceof Container) continue;

                    // Check config-based excluded blocks (for non-container special blocks)
                    if (excludedBlocks.contains(target.getType().name())) continue;

                    // Check if tool is still valid
                    ItemStack currentTool = player.getInventory().getItemInMainHand();
                    if (currentTool.getType() == Material.AIR || !isHammerPickaxe(currentTool))
                        break;

                    // Fire a simulated BlockBreakEvent for protection plugin compatibility
                    BlockBreakEvent simulated = new BlockBreakEvent(target, player);
                    plugin.getServer().getPluginManager().callEvent(simulated);
                    if (simulated.isCancelled()) continue;

                    // Break the block naturally (respects Fortune/Silk Touch)
                    target.breakNaturally(currentTool);

                    // Apply durability damage with Unbreaking enchant probability
                    if (!applyDurabilityDamage(player, currentTool)) {
                        break;
                    }
                }
            }
        } finally {
            activeHammerMiners.remove(player.getUniqueId());
        }
    }

    private BlockFace getTargetBlockFace(Player player, Block targetBlock) {
        // Prefer the cached face from PlayerInteractEvent (more reliable)
        BlockFace cached = lastHitFace.remove(player.getUniqueId());
        if (cached != null) return cached;

        // Fallback to rayTrace
        RayTraceResult result = player.rayTraceBlocks(5.0);
        if (result == null || result.getHitBlock() == null) return BlockFace.UP;

        // Verify we're looking at the correct block
        if (!result.getHitBlock().equals(targetBlock)) {
            return BlockFace.UP;
        }

        return result.getHitBlockFace() != null ? result.getHitBlockFace() : BlockFace.UP;
    }

    private Block getOffsetBlock(Block center, BlockFace face, int dx, int dy) {
        return switch (face) {
            case UP, DOWN ->
                    // Mining floor/ceiling - horizontal plane (X/Z)
                    center.getRelative(dx, 0, dy);
            case NORTH, SOUTH ->
                    // Mining a wall facing north/south - vertical plane (X/Y)
                    center.getRelative(dx, dy, 0);
            case EAST, WEST ->
                    // Mining a wall facing east/west - vertical plane (Z/Y)
                    center.getRelative(0, dy, dx);
            default -> null;
        };
    }

    private boolean applyDurabilityDamage(Player player, ItemStack tool) {
        if (!(tool.getItemMeta() instanceof Damageable damageable)) return true;

        // Unbreaking: chance to consume durability = 1/(level+1)
        int unbreakingLevel = tool.getEnchantmentLevel(Enchantment.UNBREAKING);
        if (unbreakingLevel > 0) {
            double consumeChance = 1.0 / (unbreakingLevel + 1);
            if (Math.random() >= consumeChance) {
                return true; // Unbreaking saved durability
            }
        }

        int newDamage = damageable.getDamage() + 1;
        if (newDamage >= tool.getType().getMaxDurability()) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            player.getWorld()
                    .playSound(
                            player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            return false;
        }

        damageable.setDamage(newDamage);
        tool.setItemMeta(damageable);
        return true;
    }

    public boolean isHammerPickaxe(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(hammerKey, PersistentDataType.BYTE);
    }

    private int getTier(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return 1;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 1;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer tier = pdc.get(tierKey, PersistentDataType.INTEGER);
        return tier != null ? tier : 1;
    }

    public ItemStack createHammerPickaxe(int tier) {
        String name =
                plugin.getConfig()
                        .getString(
                                "settings.hammer-pickaxe.tiers." + tier + ".name",
                                "<gradient:#FF6B35:#FFB347>Hammer Pickaxe</gradient>");
        List<String> loreLines =
                plugin.getConfig().getStringList("settings.hammer-pickaxe.tiers." + tier + ".lore");

        ItemStack item =
                new ItemBuilder(Material.NETHERITE_PICKAXE)
                        .name(name)
                        .lore(loreLines.stream().map(TextUtil::parse).toList())
                        .build();

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(hammerKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(tierKey, PersistentDataType.INTEGER, tier);
        item.setItemMeta(meta);

        return item;
    }

    public NamespacedKey getHammerKey() {
        return hammerKey;
    }

    public NamespacedKey getTierKey() {
        return tierKey;
    }
}
