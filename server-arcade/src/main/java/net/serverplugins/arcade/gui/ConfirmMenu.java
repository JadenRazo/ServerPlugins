package net.serverplugins.arcade.gui;

import java.util.List;
import net.serverplugins.arcade.ServerArcade;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Confirmation menu for joining games or confirming actions. */
public class ConfirmMenu extends ArcadeGui {

    private static final int CONFIRM_SLOT = 20;
    private static final int CANCEL_SLOT = 24;
    private static final int INFO_SLOT = 13;

    private final Runnable onConfirm;
    private final Runnable onCancel;
    private String infoName = "§e§lConfirm Action";
    private List<String> infoLore =
            List.of("§7Are you sure you want to proceed?", "", "§aConfirm §7or §cCancel");

    public ConfirmMenu(int size, String title, Runnable onConfirm, Runnable onCancel) {
        super(ServerArcade.getInstance(), title, size);
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    @Override
    protected void build() {
        // No glass pane fillers - clean GUI with inventory hiding via packets

        // Confirm button
        ItemStack confirmItem =
                createItem(
                        Material.LIME_CONCRETE,
                        "§a§lCONFIRM",
                        List.of("§7Click to confirm", "", "§eClick to proceed!"));
        setItem(
                CONFIRM_SLOT,
                confirmItem,
                e -> {
                    if (onConfirm != null) onConfirm.run();
                });

        // Cancel button
        ItemStack cancelItem =
                createItem(
                        Material.RED_CONCRETE,
                        "§c§lCANCEL",
                        List.of("§7Click to cancel", "", "§eClick to go back!"));
        setItem(
                CANCEL_SLOT,
                cancelItem,
                e -> {
                    if (onCancel != null) onCancel.run();
                });

        // Info item
        ItemStack infoItem = createItem(Material.PAPER, infoName, infoLore);
        inventory.setItem(INFO_SLOT, infoItem);
    }

    /** Set the info item with custom information. */
    public void setInfo(String name, List<String> lore) {
        this.infoName = name;
        this.infoLore = lore;
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createItem(Material material, String name) {
        return createItem(material, name, null);
    }
}
