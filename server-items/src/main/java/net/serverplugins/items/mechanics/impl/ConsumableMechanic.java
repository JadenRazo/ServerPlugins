package net.serverplugins.items.mechanics.impl;

import java.util.ArrayList;
import java.util.List;
import net.serverplugins.items.mechanics.Mechanic;
import net.serverplugins.items.models.CustomItem;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class ConsumableMechanic extends Mechanic {

    private final List<PotionEffect> effects;
    private final int healAmount;
    private final int foodAmount;
    private final float saturationAmount;

    public ConsumableMechanic(ConfigurationSection config) {
        this.healAmount = config.getInt("heal", 0);
        this.foodAmount = config.getInt("food", 0);
        this.saturationAmount = (float) config.getDouble("saturation", 0);
        this.effects = new ArrayList<>();

        ConfigurationSection effectsSection = config.getConfigurationSection("effects");
        if (effectsSection != null) {
            for (String key : effectsSection.getKeys(false)) {
                ConfigurationSection effectConfig = effectsSection.getConfigurationSection(key);
                if (effectConfig == null) continue;

                PotionEffectType type =
                        PotionEffectType.getByKey(
                                org.bukkit.NamespacedKey.minecraft(key.toLowerCase()));
                if (type == null) continue;

                int duration = effectConfig.getInt("duration", 200);
                int amplifier = effectConfig.getInt("amplifier", 0);
                boolean ambient = effectConfig.getBoolean("ambient", false);
                boolean particles = effectConfig.getBoolean("particles", true);

                effects.add(new PotionEffect(type, duration, amplifier, ambient, particles));
            }
        }
    }

    @Override
    public String getId() {
        return "consumable";
    }

    @Override
    public void onConsume(
            Player player, CustomItem item, ItemStack stack, PlayerItemConsumeEvent event) {
        if (healAmount > 0) {
            double newHealth =
                    Math.min(
                            player.getHealth() + healAmount,
                            player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)
                                    .getValue());
            player.setHealth(newHealth);
        }

        if (foodAmount > 0) {
            player.setFoodLevel(Math.min(player.getFoodLevel() + foodAmount, 20));
        }

        if (saturationAmount > 0) {
            player.setSaturation(Math.min(player.getSaturation() + saturationAmount, 20f));
        }

        for (PotionEffect effect : effects) {
            player.addPotionEffect(effect);
        }
    }
}
