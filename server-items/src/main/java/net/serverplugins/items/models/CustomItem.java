package net.serverplugins.items.models;

import java.util.Collections;
import java.util.List;
import net.serverplugins.items.mechanics.Mechanic;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;

public final class CustomItem {

    private final String id;
    private final Material material;
    private final String displayName;
    private final List<String> lore;
    private final int customModelData;
    private final java.util.Map<Enchantment, Integer> enchantments;
    private final List<ItemFlag> itemFlags;
    private final List<Mechanic> mechanics;
    private final boolean unbreakable;
    private final boolean glow;

    public CustomItem(
            String id,
            Material material,
            String displayName,
            List<String> lore,
            int customModelData,
            java.util.Map<Enchantment, Integer> enchantments,
            List<ItemFlag> itemFlags,
            List<Mechanic> mechanics,
            boolean unbreakable,
            boolean glow) {
        this.id = id;
        this.material = material;
        this.displayName = displayName;
        this.lore = lore != null ? Collections.unmodifiableList(lore) : Collections.emptyList();
        this.customModelData = customModelData;
        this.enchantments =
                enchantments != null
                        ? Collections.unmodifiableMap(enchantments)
                        : Collections.emptyMap();
        this.itemFlags =
                itemFlags != null
                        ? Collections.unmodifiableList(itemFlags)
                        : Collections.emptyList();
        this.mechanics =
                mechanics != null
                        ? Collections.unmodifiableList(mechanics)
                        : Collections.emptyList();
        this.unbreakable = unbreakable;
        this.glow = glow;
    }

    public String getId() {
        return id;
    }

    public Material getMaterial() {
        return material;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getLore() {
        return lore;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public java.util.Map<Enchantment, Integer> getEnchantments() {
        return enchantments;
    }

    public List<ItemFlag> getItemFlags() {
        return itemFlags;
    }

    public List<Mechanic> getMechanics() {
        return mechanics;
    }

    public boolean isUnbreakable() {
        return unbreakable;
    }

    public boolean isGlow() {
        return glow;
    }

    @SuppressWarnings("unchecked")
    public <T extends Mechanic> T getMechanic(Class<T> type) {
        for (Mechanic mechanic : mechanics) {
            if (type.isInstance(mechanic)) {
                return (T) mechanic;
            }
        }
        return null;
    }

    public boolean hasMechanic(Class<? extends Mechanic> type) {
        return getMechanic(type) != null;
    }
}
