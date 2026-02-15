package net.serverplugins.arcade.games.slots;

import java.util.HashSet;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Represents a slot machine symbol/item. */
public class SlotItem {

    private final String id;
    private final ItemStack itemStack;
    private final int weight;
    private Set<SlotItem> equivalents = new HashSet<>();

    public SlotItem(String id, ItemStack itemStack, int weight) {
        this.id = id;
        this.itemStack = itemStack;
        this.weight = weight;
    }

    public SlotItem(String id, Material material, int customModelData, int weight) {
        this.id = id;
        this.weight = weight;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(customModelData);
            meta.setDisplayName("§f");
            item.setItemMeta(meta);
        }
        this.itemStack = item;
    }

    /**
     * Check if this item matches another (including equivalents). Wildcards are unidirectional: if
     * star is in seven's equivalents, then seven.matches(star) = true, but star.matches(seven) =
     * false.
     */
    public boolean matches(SlotItem other) {
        if (other == null) return false;
        if (this.equals(other)) return true;

        // Check if other is an equivalent of this (unidirectional)
        // This allows wildcards to substitute for this item
        return equivalents.contains(other);
    }

    /** Check if all items in an array match this item (including equivalents). */
    public boolean matchesAll(SlotItem[] items) {
        for (SlotItem item : items) {
            if (!matches(item)) return false;
        }
        return true;
    }

    /** Count how many items in an array match this item. */
    public int countMatches(SlotItem[] items) {
        int count = 0;
        for (SlotItem item : items) {
            if (matches(item)) count++;
        }
        return count;
    }

    public void setEquivalents(Set<SlotItem> equivalents) {
        this.equivalents = equivalents;
    }

    public void addEquivalent(SlotItem item) {
        equivalents.add(item);
    }

    public String getId() {
        return id;
    }

    public ItemStack getItemStack() {
        return itemStack.clone();
    }

    public int getWeight() {
        return weight;
    }

    public Set<SlotItem> getEquivalents() {
        return equivalents;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        SlotItem slotItem = (SlotItem) obj;
        return id.equals(slotItem.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
