package net.serverplugins.items.listeners;

import net.serverplugins.items.ServerItems;
import net.serverplugins.items.managers.ItemManager;
import net.serverplugins.items.mechanics.Mechanic;
import net.serverplugins.items.mechanics.impl.CooldownMechanic;
import net.serverplugins.items.models.CustomItem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class ItemListener implements Listener {

    private final ServerItems plugin;
    private final ItemManager itemManager;

    public ItemListener(ServerItems plugin) {
        this.plugin = plugin;
        this.itemManager = plugin.getItemManager();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack stack = player.getInventory().getItemInMainHand();
        CustomItem item = itemManager.getCustomItem(stack);
        if (item == null) return;

        // Check cooldown first
        CooldownMechanic cooldown = item.getMechanic(CooldownMechanic.class);
        if (cooldown != null && cooldown.isOnCooldown(player.getUniqueId(), item.getId())) {
            for (Mechanic mechanic : item.getMechanics()) {
                if (mechanic instanceof CooldownMechanic) {
                    mechanic.onRightClick(player, item, stack, event);
                    return;
                }
            }
        }

        boolean isRight =
                event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                        || event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK;
        boolean isLeft =
                event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_AIR
                        || event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK;

        for (Mechanic mechanic : item.getMechanics()) {
            if (isRight) {
                mechanic.onRightClick(player, item, stack, event);
            } else if (isLeft) {
                mechanic.onLeftClick(player, item, stack, event);
            }
            if (event.isCancelled()) return;
        }

        // Apply cooldown after successful use
        if (cooldown != null && isRight) {
            cooldown.applyCooldown(player.getUniqueId(), item.getId());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack stack = player.getInventory().getItemInMainHand();
        CustomItem item = itemManager.getCustomItem(stack);
        if (item == null) return;

        for (Mechanic mechanic : item.getMechanics()) {
            mechanic.onBlockBreak(player, item, stack, event);
            if (event.isCancelled()) return;
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack stack = event.getItemInHand();
        CustomItem item = itemManager.getCustomItem(stack);
        if (item == null) return;

        for (Mechanic mechanic : item.getMechanics()) {
            mechanic.onBlockPlace(player, item, stack, event);
            if (event.isCancelled()) return;
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;

        ItemStack stack = player.getInventory().getItemInMainHand();
        CustomItem item = itemManager.getCustomItem(stack);
        if (item == null) return;

        for (Mechanic mechanic : item.getMechanics()) {
            mechanic.onEntityHit(player, item, stack, event);
            if (event.isCancelled()) return;
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack stack = event.getItem();
        CustomItem item = itemManager.getCustomItem(stack);
        if (item == null) return;

        for (Mechanic mechanic : item.getMechanics()) {
            mechanic.onConsume(player, item, stack, event);
            if (event.isCancelled()) return;
        }
    }
}
