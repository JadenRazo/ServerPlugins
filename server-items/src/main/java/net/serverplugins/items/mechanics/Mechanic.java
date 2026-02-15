package net.serverplugins.items.mechanics;

import net.serverplugins.items.models.CustomItem;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

public abstract class Mechanic {

    public void onRightClick(
            Player player, CustomItem item, ItemStack stack, PlayerInteractEvent event) {}

    public void onLeftClick(
            Player player, CustomItem item, ItemStack stack, PlayerInteractEvent event) {}

    public void onBlockBreak(
            Player player, CustomItem item, ItemStack stack, BlockBreakEvent event) {}

    public void onBlockPlace(
            Player player, CustomItem item, ItemStack stack, BlockPlaceEvent event) {}

    public void onEntityHit(
            Player player, CustomItem item, ItemStack stack, EntityDamageByEntityEvent event) {}

    public void onConsume(
            Player player, CustomItem item, ItemStack stack, PlayerItemConsumeEvent event) {}

    public abstract String getId();
}
