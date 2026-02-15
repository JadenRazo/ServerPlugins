package net.serverplugins.core.features;

import net.serverplugins.core.ServerCore;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class EditableSignsFeature extends Feature implements Listener {

    public EditableSignsFeature(ServerCore plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "Editable Signs";
    }

    @Override
    public String getDescription() {
        return "Right-click signs to edit them";
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!isEnabled()) return;

        Player player = event.getPlayer();
        if (!player.hasPermission("servercore.editsigns")) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;
        if (!isSign(clickedBlock.getType())) return;

        if (!player.isSneaking()) return;
        if (player.getInventory().getItemInMainHand().getType() != Material.AIR) return;

        Sign sign = (Sign) clickedBlock.getState();

        if (!canEditSign(player, sign)) {
            plugin.getCoreConfig().getMessenger().send(player, "editable-signs.no-permission");
            return;
        }

        event.setCancelled(true);
        player.openSign(sign);
    }

    private boolean canEditSign(Player player, Sign sign) {
        if (player.hasPermission("servercore.admin")) return true;
        return plugin.getCoreConfig().allowEditingOtherSigns();
    }

    private boolean isSign(Material material) {
        String name = material.name();
        return name.contains("SIGN") && !name.contains("HANGING");
    }
}
