package net.serverplugins.items.mechanics.impl;

import net.serverplugins.items.mechanics.Mechanic;
import net.serverplugins.items.models.CustomItem;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class SoundMechanic extends Mechanic {

    private final Sound sound;
    private final float volume;
    private final float pitch;

    public SoundMechanic(ConfigurationSection config) {
        Sound parsed;
        try {
            parsed =
                    Sound.valueOf(
                            config.getString("sound", "ENTITY_EXPERIENCE_ORB_PICKUP")
                                    .toUpperCase()
                                    .replace('.', '_'));
        } catch (IllegalArgumentException e) {
            parsed = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
        }
        this.sound = parsed;
        this.volume = (float) config.getDouble("volume", 1.0);
        this.pitch = (float) config.getDouble("pitch", 1.0);
    }

    @Override
    public String getId() {
        return "sound";
    }

    @Override
    public void onRightClick(
            Player player, CustomItem item, ItemStack stack, PlayerInteractEvent event) {
        player.playSound(player.getLocation(), sound, volume, pitch);
    }
}
