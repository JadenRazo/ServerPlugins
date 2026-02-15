package net.serverplugins.claim.gui;

import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * GUI for saving a claim as a template. Shows current claim settings and provides information about
 * the template save command.
 */
public class TemplateSaveGui extends Gui {

    private final Claim claim;

    public TemplateSaveGui(ServerClaim plugin, Player player, Claim claim) {
        super(plugin, player, "Save Claim Template", 27);
        this.claim = claim;
    }

    @Override
    protected void initializeItems() {
        // Preview of current settings
        setItem(
                13,
                new GuiItem(
                        new ItemBuilder(Material.WRITABLE_BOOK)
                                .name("<gradient:#00c6ff:#0072ff>Current Settings</gradient>")
                                .lore(
                                        "",
                                        "<gray>PVP: "
                                                + (claim.getSettings().isPvpEnabled()
                                                        ? "<green>Enabled"
                                                        : "<red>Disabled"),
                                        "<gray>Fire Spread: "
                                                + (claim.getSettings().isFireSpread()
                                                        ? "<green>Enabled"
                                                        : "<red>Disabled"),
                                        "<gray>Mob Spawning: "
                                                + (claim.getSettings().isHostileSpawns()
                                                        ? "<green>Enabled"
                                                        : "<red>Disabled"),
                                        "<gray>Explosions: "
                                                + (claim.getSettings().isExplosions()
                                                        ? "<green>Enabled"
                                                        : "<red>Disabled"),
                                        "<gray>Mob Griefing: "
                                                + (claim.getSettings().isMobGriefing()
                                                        ? "<green>Enabled"
                                                        : "<red>Disabled"),
                                        "<gray>Passive Spawns: "
                                                + (claim.getSettings().isPassiveSpawns()
                                                        ? "<green>Enabled"
                                                        : "<red>Disabled"),
                                        "<gray>Leaf Decay: "
                                                + (claim.getSettings().isLeafDecay()
                                                        ? "<green>Enabled"
                                                        : "<red>Disabled"),
                                        "<gray>Crop Trampling: "
                                                + (claim.getSettings().isCropTrampling()
                                                        ? "<green>Enabled"
                                                        : "<red>Disabled"),
                                        "",
                                        "<gold>Custom Groups: <white>"
                                                + claim.getCustomGroups().size(),
                                        "")
                                .build()));

        // Info message
        setItem(
                11,
                new GuiItem(
                        new ItemBuilder(Material.BOOK)
                                .name("<gradient:#ffd700:#ffed4e>How to Save</gradient>")
                                .lore(
                                        "",
                                        "<gray>Use the command to save:",
                                        "<gold>/claim template save <name>",
                                        "",
                                        "<gray>This will save all current",
                                        "<gray>settings and permissions",
                                        "<gray>from this claim.",
                                        "")
                                .build()));

        // Close button
        setItem(
                15,
                new GuiItem(
                        new ItemBuilder(Material.BARRIER).name("<red>Close").build(),
                        context -> context.getPlayer().closeInventory()));
    }
}
