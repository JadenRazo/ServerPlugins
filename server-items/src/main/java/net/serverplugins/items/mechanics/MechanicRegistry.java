package net.serverplugins.items.mechanics;

import java.util.HashMap;
import java.util.Map;
import net.serverplugins.items.mechanics.impl.CommandMechanic;
import net.serverplugins.items.mechanics.impl.ConsumableMechanic;
import net.serverplugins.items.mechanics.impl.CooldownMechanic;
import net.serverplugins.items.mechanics.impl.DurabilityMechanic;
import net.serverplugins.items.mechanics.impl.ParticleMechanic;
import net.serverplugins.items.mechanics.impl.SoundMechanic;
import net.serverplugins.items.mechanics.impl.SpeedMechanic;
import org.bukkit.configuration.ConfigurationSection;

public class MechanicRegistry {

    private final Map<String, MechanicFactory> factories = new HashMap<>();

    public MechanicRegistry() {
        registerDefaults();
    }

    private void registerDefaults() {
        register("durability", DurabilityMechanic::new);
        register("cooldown", CooldownMechanic::new);
        register("command", CommandMechanic::new);
        register("consumable", ConsumableMechanic::new);
        register("particle", ParticleMechanic::new);
        register("sound", SoundMechanic::new);
        register("speed", SpeedMechanic::new);
    }

    public void register(String id, MechanicFactory factory) {
        factories.put(id.toLowerCase(), factory);
    }

    public Mechanic create(String id, ConfigurationSection config) {
        MechanicFactory factory = factories.get(id.toLowerCase());
        if (factory == null) {
            return null;
        }
        return factory.create(config);
    }

    public boolean has(String id) {
        return factories.containsKey(id.toLowerCase());
    }
}
