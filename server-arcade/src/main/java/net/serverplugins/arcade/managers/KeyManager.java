package net.serverplugins.arcade.managers;

import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.machines.Machine;
import org.bukkit.NamespacedKey;

/**
 * Provides access to all NamespacedKeys used by the arcade system. Keys are initialized when
 * Machine.initKeys() is called.
 */
public class KeyManager {

    /** Key for machine items (ItemStacks that represent machines). */
    public static NamespacedKey getMachineItemKey() {
        return Machine.MACHINE_ITEM_KEY;
    }

    /** Key for machine entities (ArmorStands that are part of machines). */
    public static NamespacedKey getMachineEntityKey() {
        return Machine.MACHINE_ENTITY_KEY;
    }

    /** Key for machine seats (ArmorStands that players sit on). */
    public static NamespacedKey getMachineSeatKey() {
        return Machine.MACHINE_SEAT_KEY;
    }

    /** Key for machine holograms (display entities for text/items). */
    public static NamespacedKey getMachineHologramKey() {
        return Machine.MACHINE_HOLOGRAM_KEY;
    }

    /** Initialize all keys. Called automatically by ServerArcade on enable. */
    public static void init(ServerArcade plugin) {
        Machine.initKeys(plugin);
    }
}
