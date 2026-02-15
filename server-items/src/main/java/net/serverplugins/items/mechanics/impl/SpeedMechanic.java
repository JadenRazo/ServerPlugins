package net.serverplugins.items.mechanics.impl;

import net.serverplugins.items.mechanics.Mechanic;
import net.serverplugins.items.models.CustomItem;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class SpeedMechanic extends Mechanic {

    private final int duration;
    private final int amplifier;

    public SpeedMechanic(ConfigurationSection config) {
        this.duration = config.getInt("duration", 200);
        this.amplifier = config.getInt("amplifier", 1);
    }

    @Override
    public String getId() {
        return "speed";
    }

    @Override
    public void onRightClick(
            Player player, CustomItem item, ItemStack stack, PlayerInteractEvent event) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, amplifier));
    }
}
