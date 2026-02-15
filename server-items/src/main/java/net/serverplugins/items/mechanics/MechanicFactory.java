package net.serverplugins.items.mechanics;

import org.bukkit.configuration.ConfigurationSection;

@FunctionalInterface
public interface MechanicFactory {

    Mechanic create(ConfigurationSection config);
}
