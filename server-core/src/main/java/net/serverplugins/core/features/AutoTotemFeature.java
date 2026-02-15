package net.serverplugins.core.features;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.serverplugins.core.ServerCore;
import net.serverplugins.core.data.PlayerDataManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class AutoTotemFeature extends Feature implements Listener, PerPlayerFeature {

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final String FEATURE_KEY = "auto-totem";

    public AutoTotemFeature(ServerCore plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "Auto Totem";
    }

    @Override
    public String getDescription() {
        return "Automatically swaps totem to offhand";
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityResurrect(EntityResurrectEvent event) {
        if (!isEnabled()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!player.hasPermission("servercore.autototem")) return;
        if (!isEnabledForPlayer(player)) return;

        long cooldown = plugin.getCoreConfig().getAutoTotemCooldown();
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        if (cooldowns.containsKey(playerId)) {
            if (currentTime - cooldowns.get(playerId) < cooldown) return;
        }

        plugin.getServer()
                .getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            swapTotemToOffhand(player);
                            cooldowns.put(playerId, currentTime);
                        });
    }

    private void swapTotemToOffhand(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack offhand = inventory.getItemInOffHand();

        if (offhand.getType() == Material.TOTEM_OF_UNDYING) return;

        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() == Material.TOTEM_OF_UNDYING) {
                inventory.setItem(i, offhand);
                inventory.setItemInOffHand(item);
                return;
            }
        }
    }

    @Override
    protected void onDisable() {
        cooldowns.clear();
    }
}
