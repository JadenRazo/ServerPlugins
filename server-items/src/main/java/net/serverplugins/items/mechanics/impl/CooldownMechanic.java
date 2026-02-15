package net.serverplugins.items.mechanics.impl;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.items.mechanics.Mechanic;
import net.serverplugins.items.models.CustomItem;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class CooldownMechanic extends Mechanic {

    private final int cooldownTicks;
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    public CooldownMechanic(ConfigurationSection config) {
        this.cooldownTicks = config.getInt("ticks", 20);
    }

    @Override
    public String getId() {
        return "cooldown";
    }

    @Override
    public void onRightClick(
            Player player, CustomItem item, ItemStack stack, PlayerInteractEvent event) {
        if (isOnCooldown(player.getUniqueId(), item.getId())) {
            long remaining = getRemainingTicks(player.getUniqueId(), item.getId());
            double seconds = remaining / 20.0;
            TextUtil.sendError(
                    player,
                    "This item is on cooldown for <white>"
                            + String.format("%.1f", seconds)
                            + "s</white>.");
            event.setCancelled(true);
        }
    }

    public void applyCooldown(UUID playerId, String itemId) {
        String key = playerId + ":" + itemId;
        cooldowns.put(key, System.currentTimeMillis() + (cooldownTicks * 50L));
    }

    public boolean isOnCooldown(UUID playerId, String itemId) {
        String key = playerId + ":" + itemId;
        Long expiry = cooldowns.get(key);
        if (expiry == null) return false;
        if (System.currentTimeMillis() >= expiry) {
            cooldowns.remove(key);
            return false;
        }
        return true;
    }

    public long getRemainingTicks(UUID playerId, String itemId) {
        String key = playerId + ":" + itemId;
        Long expiry = cooldowns.get(key);
        if (expiry == null) return 0;
        long remaining = expiry - System.currentTimeMillis();
        return remaining > 0 ? remaining / 50 : 0;
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }
}
