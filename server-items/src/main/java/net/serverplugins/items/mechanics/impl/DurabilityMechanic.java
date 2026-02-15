package net.serverplugins.items.mechanics.impl;

import net.serverplugins.items.mechanics.Mechanic;
import net.serverplugins.items.models.CustomItem;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class DurabilityMechanic extends Mechanic {

    private static final NamespacedKey DURABILITY_KEY =
            new NamespacedKey("serveritems", "custom_durability");
    private static final NamespacedKey MAX_DURABILITY_KEY =
            new NamespacedKey("serveritems", "max_durability");

    private final int maxDurability;

    public DurabilityMechanic(ConfigurationSection config) {
        this.maxDurability = config.getInt("max", 500);
    }

    @Override
    public String getId() {
        return "durability";
    }

    @Override
    public void onBlockBreak(
            Player player, CustomItem item, ItemStack stack, BlockBreakEvent event) {
        decrementDurability(player, stack, 1);
    }

    @Override
    public void onEntityHit(
            Player player, CustomItem item, ItemStack stack, EntityDamageByEntityEvent event) {
        decrementDurability(player, stack, 1);
    }

    public int getMaxDurability() {
        return maxDurability;
    }

    public static int getCurrentDurability(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return -1;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(DURABILITY_KEY, PersistentDataType.INTEGER)) return -1;
        return pdc.getOrDefault(DURABILITY_KEY, PersistentDataType.INTEGER, 0);
    }

    public void initializeDurability(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(DURABILITY_KEY, PersistentDataType.INTEGER, maxDurability);
        pdc.set(MAX_DURABILITY_KEY, PersistentDataType.INTEGER, maxDurability);
        stack.setItemMeta(meta);
        updateDurabilityBar(stack, maxDurability, maxDurability);
    }

    private void decrementDurability(Player player, ItemStack stack, int amount) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int current = pdc.getOrDefault(DURABILITY_KEY, PersistentDataType.INTEGER, maxDurability);
        int max = pdc.getOrDefault(MAX_DURABILITY_KEY, PersistentDataType.INTEGER, maxDurability);

        current = Math.max(0, current - amount);
        pdc.set(DURABILITY_KEY, PersistentDataType.INTEGER, current);
        stack.setItemMeta(meta);

        updateDurabilityBar(stack, current, max);

        if (current <= 0) {
            player.getInventory().removeItem(stack);
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
        }
    }

    private void updateDurabilityBar(ItemStack stack, int current, int max) {
        if (!(stack.getItemMeta() instanceof Damageable damageable)) return;
        int vanillaMax = stack.getType().getMaxDurability();
        if (vanillaMax <= 0) return;

        double ratio = (double) current / max;
        int vanillaDamage = (int) ((1.0 - ratio) * vanillaMax);
        damageable.setDamage(Math.min(vanillaDamage, vanillaMax - 1));
        stack.setItemMeta(damageable);
    }
}
