package net.serverplugins.api.gui;

import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

public class GuiItem {

    private final ItemStack itemStack;
    private final Consumer<Player> onClick;
    private final Consumer<GuiClickContext> onClickContext;
    private final boolean clickable;

    public GuiItem(ItemStack itemStack, Consumer<Player> onClick) {
        this.itemStack = itemStack;
        this.onClick = onClick;
        this.onClickContext = null;
        this.clickable = true;
    }

    public GuiItem(ItemStack itemStack) {
        this(itemStack, (Consumer<Player>) null);
    }

    public GuiItem(
            ItemStack itemStack, Consumer<GuiClickContext> onClickContext, boolean useContext) {
        this.itemStack = itemStack;
        this.onClick = null;
        this.onClickContext = onClickContext;
        this.clickable = true;
    }

    public GuiItem(ItemStack itemStack, boolean clickable) {
        this.itemStack = itemStack;
        this.onClick = null;
        this.onClickContext = null;
        this.clickable = clickable;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public boolean isClickable() {
        return clickable;
    }

    public void onClick(Player player) {
        onClick(player, ClickType.LEFT, 0);
    }

    public void onClick(Player player, ClickType clickType, int slot) {
        if (onClickContext != null) {
            onClickContext.accept(new GuiClickContext(player, clickType, slot));
        } else if (onClick != null) {
            onClick.accept(player);
        }
    }

    public static GuiItem of(ItemStack itemStack) {
        return new GuiItem(itemStack);
    }

    public static GuiItem of(ItemStack itemStack, Consumer<Player> onClick) {
        return new GuiItem(itemStack, onClick);
    }

    public static GuiItem withContext(
            ItemStack itemStack, Consumer<GuiClickContext> onClickContext) {
        return new GuiItem(itemStack, onClickContext, true);
    }

    public static GuiItem closeButton(ItemStack itemStack) {
        return new GuiItem(itemStack, Player::closeInventory);
    }
}
