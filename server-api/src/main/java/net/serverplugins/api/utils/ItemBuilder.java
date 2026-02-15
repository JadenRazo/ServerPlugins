package net.serverplugins.api.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class ItemBuilder {

    private final ItemStack itemStack;
    private final ItemMeta itemMeta;

    public ItemBuilder(Material material) {
        this.itemStack = new ItemStack(material);
        this.itemMeta = itemStack.getItemMeta();
    }

    public ItemBuilder(ItemStack itemStack) {
        this.itemStack = itemStack.clone();
        this.itemMeta = this.itemStack.getItemMeta();
    }

    public ItemBuilder name(Component name) {
        itemMeta.displayName(name);
        return this;
    }

    public ItemBuilder name(String name) {
        return name(TextUtil.parse(name));
    }

    public ItemBuilder lore(Component... lore) {
        itemMeta.lore(Arrays.asList(lore));
        return this;
    }

    public ItemBuilder lore(String... lore) {
        List<Component> components = new ArrayList<>();
        for (String line : lore) components.add(TextUtil.parse(line));
        itemMeta.lore(components);
        return this;
    }

    public ItemBuilder lore(List<Component> lore) {
        itemMeta.lore(lore);
        return this;
    }

    public ItemBuilder addLoreLine(Component line) {
        List<Component> lore = itemMeta.lore();
        if (lore == null) lore = new ArrayList<>();
        lore.add(line);
        itemMeta.lore(lore);
        return this;
    }

    public ItemBuilder addLoreLine(String line) {
        return addLoreLine(TextUtil.parse(line));
    }

    public ItemBuilder amount(int amount) {
        itemStack.setAmount(amount);
        return this;
    }

    public ItemBuilder enchant(Enchantment enchantment, int level) {
        itemMeta.addEnchant(enchantment, level, true);
        return this;
    }

    public ItemBuilder flags(ItemFlag... flags) {
        itemMeta.addItemFlags(flags);
        return this;
    }

    public ItemBuilder glow() {
        itemMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
        itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        return this;
    }

    public ItemBuilder glow(boolean glow) {
        if (glow) {
            return glow();
        }
        return this;
    }

    public ItemBuilder unbreakable(boolean unbreakable) {
        itemMeta.setUnbreakable(unbreakable);
        return this;
    }

    public ItemBuilder hideAll() {
        itemMeta.addItemFlags(ItemFlag.values());
        return this;
    }

    public ItemBuilder customModelData(int data) {
        itemMeta.setCustomModelData(data);
        return this;
    }

    public ItemStack build() {
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material);
    }

    public static ItemBuilder of(ItemStack itemStack) {
        return new ItemBuilder(itemStack);
    }
}
