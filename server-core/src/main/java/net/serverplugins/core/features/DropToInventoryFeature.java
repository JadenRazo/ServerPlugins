package net.serverplugins.core.features;

import java.util.Map;
import net.serverplugins.core.ServerCore;
import net.serverplugins.core.data.PlayerDataManager;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

public class DropToInventoryFeature extends Feature implements Listener, PerPlayerFeature {

    private static final String FEATURE_KEY = "drop-to-inventory";

    public DropToInventoryFeature(ServerCore plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "Drop to Inventory";
    }

    @Override
    public String getDescription() {
        return "Automatically picks up drops";
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (!isEnabled()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!player.hasPermission("servercore.autopickup")) return;
        if (!isEnabledForPlayer(player)) return;

        GameMode gameMode = player.getGameMode();
        if (gameMode != GameMode.SURVIVAL && gameMode != GameMode.ADVENTURE) return;

        Item item = event.getItem();
        ItemStack itemStack = item.getItemStack();

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(itemStack);

        if (leftover.isEmpty()) {
            event.setCancelled(true);
            item.remove();

            if (plugin.getCoreConfig().shouldPlayPickupSound()) {
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.2f, 1.8f);
            }
        } else {
            event.setCancelled(true);
            ItemStack remaining = leftover.values().iterator().next();
            item.setItemStack(remaining);
        }
    }
}
