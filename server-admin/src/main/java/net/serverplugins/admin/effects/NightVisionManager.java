package net.serverplugins.admin.effects;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.serverplugins.admin.ServerAdmin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class NightVisionManager {

    private final ServerAdmin plugin;
    private final Set<UUID> nightVisionEnabled;

    private static final int INFINITE_DURATION = Integer.MAX_VALUE;

    public NightVisionManager(ServerAdmin plugin) {
        this.plugin = plugin;
        this.nightVisionEnabled = ConcurrentHashMap.newKeySet();
    }

    public boolean toggle(Player player) {
        if (isEnabled(player)) {
            disable(player);
            return false;
        } else {
            enable(player);
            return true;
        }
    }

    public void enable(Player player) {
        nightVisionEnabled.add(player.getUniqueId());
        player.addPotionEffect(
                new PotionEffect(
                        PotionEffectType.NIGHT_VISION, INFINITE_DURATION, 0, false, false, true));
    }

    public void disable(Player player) {
        nightVisionEnabled.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
    }

    public boolean isEnabled(Player player) {
        return nightVisionEnabled.contains(player.getUniqueId());
    }

    public void handleQuit(Player player) {
        nightVisionEnabled.remove(player.getUniqueId());
    }

    public void shutdown() {
        for (UUID uuid : nightVisionEnabled) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            }
        }
        nightVisionEnabled.clear();
    }
}
