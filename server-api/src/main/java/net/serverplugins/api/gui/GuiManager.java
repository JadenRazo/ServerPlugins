package net.serverplugins.api.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;

public class GuiManager {

    private final Map<UUID, Gui> openGuis = new HashMap<>();

    public void openGui(Player player, Gui gui) {
        if (hasGuiOpen(player)) closeGui(player);
        openGuis.put(player.getUniqueId(), gui);
        gui.open(player);
    }

    public void registerGui(Player player, Gui gui) {
        openGuis.put(player.getUniqueId(), gui);
    }

    public void closeGui(Player player) {
        Gui gui = openGuis.remove(player.getUniqueId());
        if (gui != null) gui.close(player);
    }

    public Gui getOpenGui(Player player) {
        return openGuis.get(player.getUniqueId());
    }

    public boolean hasGuiOpen(Player player) {
        return openGuis.containsKey(player.getUniqueId());
    }

    public void removePlayer(Player player) {
        openGuis.remove(player.getUniqueId());
    }

    public void closeAll() {
        openGuis.clear();
    }

    public int getOpenGuiCount() {
        return openGuis.size();
    }
}
