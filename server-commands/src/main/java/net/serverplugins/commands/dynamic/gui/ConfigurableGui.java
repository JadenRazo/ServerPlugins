package net.serverplugins.commands.dynamic.gui;

import java.util.Map;
import net.serverplugins.api.effects.CustomSound;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import org.bukkit.entity.Player;

/**
 * A GUI that is configured from YAML configuration. Supports custom sounds, buttons with actions,
 * and dynamic content.
 */
public class ConfigurableGui extends Gui {

    private final Map<Integer, GuiButton> buttons;
    private final CustomSound openSound;
    private final CustomSound clickSound;

    public ConfigurableGui(
            String title,
            int size,
            CustomSound openSound,
            CustomSound clickSound,
            Map<Integer, GuiButton> buttons) {
        super(null, title, size); // null plugin is OK for simple GUIs
        this.buttons = buttons;
        this.openSound = openSound != null ? openSound : CustomSound.NONE;
        this.clickSound = clickSound != null ? clickSound : CustomSound.NONE;
    }

    @Override
    protected void initializeItems() {
        // Clear existing items
        clearItems();

        // Add all configured buttons
        for (Map.Entry<Integer, GuiButton> entry : buttons.entrySet()) {
            int slot = entry.getKey();
            GuiButton button = entry.getValue();

            // Skip invalid slots
            if (slot < 0 || slot >= size) {
                continue;
            }

            // Create GuiItem with click handler using context
            GuiItem guiItem =
                    GuiItem.withContext(
                            button.getItem(),
                            context -> {
                                Player player = context.getPlayer();

                                // Play global click sound if no button-specific sound
                                if (button.getClickSound().isSilent() && !clickSound.isSilent()) {
                                    clickSound.playSound(player);
                                }

                                // Execute button action
                                button.execute(player);
                            });

            setItem(slot, guiItem);
        }
    }

    @Override
    public void open(Player player) {
        // Play open sound
        openSound.playSound(player);

        // Open the GUI
        super.open(player);
    }

    /** Get the buttons configured for this GUI. */
    public Map<Integer, GuiButton> getButtons() {
        return buttons;
    }

    /** Get the open sound for this GUI. */
    public CustomSound getOpenSound() {
        return openSound;
    }

    /** Get the global click sound for this GUI. */
    public CustomSound getGlobalClickSound() {
        return clickSound;
    }
}
