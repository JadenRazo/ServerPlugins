package net.serverplugins.core.features;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.serverplugins.core.ServerCore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class AnvilColorsFeature extends Feature implements Listener {

    public AnvilColorsFeature(ServerCore plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "Anvil Colors";
    }

    @Override
    public String getDescription() {
        return "Allows color codes in anvil naming";
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!isEnabled()) return;

        AnvilInventory inventory = event.getInventory();
        Player player = (Player) event.getView().getPlayer();

        if (!player.hasPermission("servercore.anvilcolors")) return;

        ItemStack result = event.getResult();
        if (result == null) return;

        String renameText = inventory.getRenameText();
        if (renameText == null || renameText.isEmpty()) return;
        if (!renameText.contains("&")) return;

        ItemStack coloredResult = result.clone();
        ItemMeta meta = coloredResult.getItemMeta();

        if (meta != null) {
            String coloredName = applyColors(renameText);
            Component nameComponent =
                    LegacyComponentSerializer.legacyAmpersand().deserialize(coloredName);
            meta.displayName(nameComponent);
            coloredResult.setItemMeta(meta);
            event.setResult(coloredResult);
        }
    }

    private String applyColors(String text) {
        boolean allowFormatting = plugin.getCoreConfig().allowFormattingCodes();
        if (allowFormatting) return text;
        return text.replaceAll("&[k-or]", "");
    }
}
