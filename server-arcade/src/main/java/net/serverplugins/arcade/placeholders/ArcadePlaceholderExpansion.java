package net.serverplugins.arcade.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.machines.Machine;
import net.serverplugins.arcade.machines.MachineManager;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion for ServerArcade.
 *
 * <p>Provides placeholders in the format: %serverarcade_<machineId>_<property>%
 *
 * <p>Examples: - %serverarcade_slot1_status% - %serverarcade_blackjack1_player% -
 * %serverarcade_crash1_active%
 *
 * <p>Properties: - status: Current status of the machine (Available, In Use, etc.) - player:
 * Current player(s) using the machine - game: Game type name - active: Whether the machine is
 * active (true/false)
 */
public class ArcadePlaceholderExpansion extends PlaceholderExpansion {

    private final ServerArcade plugin;

    public ArcadePlaceholderExpansion(ServerArcade plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "serverarcade";
    }

    @Override
    public @NotNull String getAuthor() {
        return "ServerPlugins";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // Required to survive server reloads
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (plugin.getMachineManager() == null) {
            return "No machines";
        }

        // Parse params as: <machineId>_<property>
        String[] parts = params.split("_", 2);
        if (parts.length < 2) {
            return "Invalid format";
        }

        String machineId = parts[0];
        String property = parts[1].toLowerCase();

        // Get the machine
        MachineManager manager = plugin.getMachineManager();
        Machine machine = manager.getMachine(machineId);

        if (machine == null) {
            return "Unknown Machine";
        }

        // Delegate to machine's getPlaceholder method
        return machine.getPlaceholder(property);
    }
}
