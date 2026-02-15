package net.serverplugins.api.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

public class GuiClickContext {

    private final Player player;
    private final ClickType clickType;
    private final int slot;

    public GuiClickContext(Player player, ClickType clickType, int slot) {
        this.player = player;
        this.clickType = clickType;
        this.slot = slot;
    }

    public Player getPlayer() {
        return player;
    }

    public ClickType getClickType() {
        return clickType;
    }

    public int getSlot() {
        return slot;
    }

    public boolean isLeftClick() {
        return clickType == ClickType.LEFT || clickType == ClickType.SHIFT_LEFT;
    }

    public boolean isRightClick() {
        return clickType == ClickType.RIGHT || clickType == ClickType.SHIFT_RIGHT;
    }

    public boolean isShiftClick() {
        return clickType == ClickType.SHIFT_LEFT || clickType == ClickType.SHIFT_RIGHT;
    }

    public boolean isDropClick() {
        return clickType == ClickType.DROP || clickType == ClickType.CONTROL_DROP;
    }

    public boolean isMiddleClick() {
        return clickType == ClickType.MIDDLE;
    }
}
