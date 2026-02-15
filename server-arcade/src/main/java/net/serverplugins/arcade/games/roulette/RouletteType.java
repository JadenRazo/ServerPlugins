package net.serverplugins.arcade.games.roulette;

import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.games.GameType;
import net.serverplugins.arcade.gui.ArcadeFont;
import net.serverplugins.arcade.machines.Machine;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/** Roulette game type - classic casino roulette table. */
public class RouletteType extends GameType {

    public RouletteType(ServerArcade plugin) {
        super(plugin, "Roulette", "roulette");
    }

    @Override
    protected void onConfigLoad(ConfigurationSection config) {
        // Set GUI title with custom font (after config load to override YAML)
        this.guiTitle = ArcadeFont.createTitle(ArcadeFont.ROULETTE_SCREEN, "Roulette");
    }

    @Override
    public void open(Player player, Machine machine) {
        // Seat the player at the machine
        if (machine != null) {
            machine.seatPlayer(player, 1);
        }

        // Open roulette GUI
        TextUtil.send(player, "<gold>=== Roulette ===");
        TextUtil.send(
                player, "<gray>Roulette coming soon! Use <yellow>/roulette <gray>in the meantime.");
    }
}
