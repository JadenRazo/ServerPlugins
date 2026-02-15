package net.serverplugins.api.configuration.parsers;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.serverplugins.api.messages.Message;
import net.serverplugins.api.utils.ColorUtils;
import net.serverplugins.api.utils.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

/**
 * Parser for ItemStack objects with full metadata support.
 *
 * <p>Config format: material: DIAMOND_SWORD amount: 1 name: "&aSuper Sword" lore: - "&7A powerful
 * weapon" - "&7Damage: &c+10" enchants: sharpness: 5 unbreaking: 3 custom_model_data: 12345
 * item_flags: - HIDE_ENCHANTS - HIDE_ATTRIBUTES color: "#FF0000" # For leather armor or potions
 * texture: "base64_or_url" # For player heads owner: "PlayerName" # For player heads
 */
public class ItemStackParser extends Parser<ItemStack> {

    private static final ItemStackParser INSTANCE = new ItemStackParser();

    private ItemStackParser() {}

    public static ItemStackParser getInstance() {
        return INSTANCE;
    }

    @Override
    public ItemStack loadFromConfig(ConfigurationSection config, String path) {
        if (config == null) return null;

        ConfigurationSection section = path != null ? config.getConfigurationSection(path) : config;
        if (section == null) return null;

        // Material
        String rawMaterial = section.getString("material", "STONE");
        Material material;
        try {
            material = Material.valueOf(rawMaterial.toUpperCase());
        } catch (IllegalArgumentException e) {
            warning("Invalid material: " + rawMaterial);
            material = Material.STONE;
        }

        ItemStack item = new ItemStack(material);

        // Amount
        if (section.contains("amount")) {
            item.setAmount(section.getInt("amount", 1));
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Display name
        if (section.contains("name")) {
            String name = section.getString("name");
            if (name != null && !name.isEmpty()) {
                ItemUtils.setItemName(meta, Message.fromText(name, false));
            }
        }

        // Lore
        if (section.contains("lore")) {
            List<String> rawLore = section.getStringList("lore");
            List<Message> lore = new ArrayList<>();
            for (String line : rawLore) {
                lore.add(Message.fromText(line, false));
            }
            ItemUtils.setItemLore(meta, lore);
        }

        // Enchantments
        if (section.contains("enchants")) {
            ConfigurationSection enchants = section.getConfigurationSection("enchants");
            if (enchants != null) {
                Set<String> keys = enchants.getKeys(false);
                for (String enchantName : keys) {
                    Enchantment enchantment =
                            Enchantment.getByKey(
                                    NamespacedKey.minecraft(enchantName.toLowerCase()));
                    if (enchantment != null) {
                        int level = enchants.getInt(enchantName, 1);
                        meta.addEnchant(enchantment, level, true);
                    } else {
                        warning("Invalid enchantment: " + enchantName);
                    }
                }
            }
        }

        // Custom model data
        if (section.contains("custom_model_data")) {
            meta.setCustomModelData(section.getInt("custom_model_data"));
        }

        // Item flags
        if (section.contains("item_flags")) {
            List<String> flags = section.getStringList("item_flags");
            for (String flagName : flags) {
                try {
                    ItemFlag flag = ItemFlag.valueOf(flagName.toUpperCase());
                    meta.addItemFlags(flag);
                } catch (IllegalArgumentException e) {
                    warning("Invalid item flag: " + flagName);
                }
            }
        }

        // Color (for leather armor and potions)
        if (section.contains("color")) {
            String colorHex = section.getString("color");
            if (colorHex != null) {
                org.bukkit.Color color = ColorUtils.colorFromHex(colorHex);
                if (meta instanceof LeatherArmorMeta leatherMeta) {
                    leatherMeta.setColor(color);
                } else if (meta instanceof PotionMeta potionMeta) {
                    potionMeta.setColor(color);
                }
            }
        }

        // Player head texture/owner
        if (material == Material.PLAYER_HEAD && meta instanceof SkullMeta skullMeta) {
            if (section.contains("owner")) {
                String owner = section.getString("owner");
                if (owner != null) {
                    PlayerProfile profile = Bukkit.getServer().createPlayerProfile(owner);
                    skullMeta.setOwnerProfile(profile);
                }
            } else if (section.contains("texture")) {
                String texture = section.getString("texture");
                if (texture != null) {
                    applySkullTexture(skullMeta, texture);
                }
            }
        }

        item.setItemMeta(meta);
        return item;
    }

    private void applySkullTexture(SkullMeta skullMeta, String texture) {
        try {
            PlayerProfile profile = Bukkit.getServer().createPlayerProfile("CustomHead");
            PlayerTextures textures = profile.getTextures();

            String url;
            if (texture.startsWith("http://textures.minecraft.net/texture/")) {
                url = texture;
            } else if (texture.startsWith("eyJ")) {
                // Base64 encoded texture
                url = ItemUtils.getTextureURL(texture);
            } else {
                warning(
                        "Invalid skull texture. Use either a texture URL or base64 encoded texture.");
                return;
            }

            if (url != null) {
                textures.setSkin(new URL(url));
                // No need to call update().join() - texture URL is already set directly
                // Calling update() would block the main thread waiting for Mojang API
                skullMeta.setOwnerProfile(profile);
            }
        } catch (MalformedURLException e) {
            warning("Invalid texture URL: " + texture);
        } catch (Exception e) {
            warning("Failed to apply skull texture: " + e.getMessage());
        }
    }

    @Override
    public void saveToConfig(ConfigurationSection config, String path, ItemStack item) {
        if (config == null || item == null) return;

        ConfigurationSection section = config.createSection(path);

        section.set("material", item.getType().name());

        if (item.getAmount() > 1) {
            section.set("amount", item.getAmount());
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        if (meta.hasDisplayName()) {
            section.set("name", meta.getDisplayName());
        }

        if (meta.hasLore()) {
            section.set("lore", meta.getLore());
        }

        if (meta.hasEnchants()) {
            ConfigurationSection enchants = section.createSection("enchants");
            meta.getEnchants()
                    .forEach(
                            (enchant, level) -> {
                                enchants.set(enchant.getKey().getKey(), level);
                            });
        }

        if (meta.hasCustomModelData()) {
            section.set("custom_model_data", meta.getCustomModelData());
        }

        if (!meta.getItemFlags().isEmpty()) {
            List<String> flags = new ArrayList<>();
            for (ItemFlag flag : meta.getItemFlags()) {
                flags.add(flag.name());
            }
            section.set("item_flags", flags);
        }
    }
}
