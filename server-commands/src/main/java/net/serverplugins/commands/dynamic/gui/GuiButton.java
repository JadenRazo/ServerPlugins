package net.serverplugins.commands.dynamic.gui;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.serverplugins.api.effects.CustomSound;
import net.serverplugins.api.handlers.PlaceholderHandler;
import net.serverplugins.commands.ServerCommands;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Represents a clickable button in a configurable GUI. Handles item display, click sounds, and
 * action execution.
 */
public class GuiButton {

    private final ItemStack item;
    private final String action;
    private final CustomSound clickSound;

    public GuiButton(ItemStack item, String action, CustomSound clickSound) {
        this.item = item;
        this.action = action;
        this.clickSound = clickSound != null ? clickSound : CustomSound.NONE;
    }

    /**
     * Execute the button's action when clicked.
     *
     * @param player The player who clicked the button
     */
    public void execute(Player player) {
        Bukkit.getLogger()
                .info(
                        "[GuiButton] execute() called for player "
                                + player.getName()
                                + ", action: "
                                + action);

        // Play click sound
        clickSound.playSound(player);

        // Execute action based on type
        if (action == null || action.isEmpty()) {
            Bukkit.getLogger().info("[GuiButton] No action configured");
            return;
        }

        // Parse action string with placeholders
        String parsedAction = PlaceholderHandler.parse(player, action);
        Bukkit.getLogger().info("[GuiButton] Parsed action: " + parsedAction);

        if (parsedAction.startsWith("command:")) {
            // Execute command as player
            String command = parsedAction.substring(8).trim();
            Bukkit.getLogger().info("[GuiButton] Executing command as player: " + command);
            player.closeInventory(); // Close GUI first

            // Check if it's a server transfer command (for Velocity/BungeeCord)
            if (command.startsWith("server ")) {
                String serverName = command.substring(7).trim();
                Bukkit.getLogger().info("[GuiButton] Transferring player to server: " + serverName);
                transferToServer(player, serverName);
            } else {
                Bukkit.dispatchCommand(player, command);
            }
        } else if (parsedAction.equals("close")) {
            // Close the GUI
            player.closeInventory();
        } else if (parsedAction.startsWith("console:")) {
            // Execute command as console
            String command = parsedAction.substring(8).trim();
            // Replace player-specific placeholders
            command = command.replace("{player}", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        } else if (parsedAction.startsWith("open:")) {
            // Open another GUI (handled by GuiRegistry)
            String guiId = parsedAction.substring(5).trim();
            ConfigurableGui gui = GuiRegistry.getInstance().createGui(guiId, player);
            if (gui != null) {
                gui.open(player);
            }
        }
    }

    public ItemStack getItem() {
        return item;
    }

    public String getAction() {
        return action;
    }

    public CustomSound getClickSound() {
        return clickSound;
    }

    /** Transfer a player to another server using BungeeCord/Velocity plugin messaging. */
    private void transferToServer(Player player, String serverName) {
        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(serverName);

            // Send via BungeeCord channel (works with both BungeeCord and Velocity)
            player.sendPluginMessage(ServerCommands.getInstance(), "BungeeCord", out.toByteArray());
            Bukkit.getLogger()
                    .info(
                            "[GuiButton] Sent transfer request for "
                                    + player.getName()
                                    + " to server: "
                                    + serverName);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[GuiButton] Failed to transfer player: " + e.getMessage());
            player.sendMessage(
                    net.kyori.adventure.text.Component.text(
                                    "Failed to connect to server. Please try again.")
                            .color(net.kyori.adventure.text.format.NamedTextColor.RED));
        }
    }
}
