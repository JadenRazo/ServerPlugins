package net.serverplugins.items.mechanics.impl;

import net.serverplugins.items.mechanics.Mechanic;
import net.serverplugins.items.models.CustomItem;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class ParticleMechanic extends Mechanic {

    private final Particle particle;
    private final int count;
    private final double offsetX;
    private final double offsetY;
    private final double offsetZ;
    private final double speed;

    public ParticleMechanic(ConfigurationSection config) {
        Particle parsed;
        try {
            parsed = Particle.valueOf(config.getString("type", "FLAME").toUpperCase());
        } catch (IllegalArgumentException e) {
            parsed = Particle.FLAME;
        }
        this.particle = parsed;
        this.count = config.getInt("count", 10);
        this.offsetX = config.getDouble("offset_x", 0.5);
        this.offsetY = config.getDouble("offset_y", 0.5);
        this.offsetZ = config.getDouble("offset_z", 0.5);
        this.speed = config.getDouble("speed", 0.1);
    }

    @Override
    public String getId() {
        return "particle";
    }

    @Override
    public void onRightClick(
            Player player, CustomItem item, ItemStack stack, PlayerInteractEvent event) {
        player.getWorld()
                .spawnParticle(
                        particle,
                        player.getLocation().add(0, 1, 0),
                        count,
                        offsetX,
                        offsetY,
                        offsetZ,
                        speed);
    }
}
