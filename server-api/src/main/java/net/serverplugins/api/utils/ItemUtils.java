package net.serverplugins.api.utils;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.serverplugins.api.ServerType;
import net.serverplugins.api.messages.Message;
import net.serverplugins.api.messages.types.EmptyMessage;
import net.serverplugins.api.messages.types.LegacyMessage;
import net.serverplugins.api.messages.types.ModernMessage;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Utility class for ItemStack operations with Message support. */
public final class ItemUtils {

    private ItemUtils() {}

    /**
     * Set the display name of an item using a Message.
     *
     * @param meta The item meta to modify
     * @param name The name Message
     */
    public static void setItemName(@Nonnull ItemMeta meta, @Nonnull Message name) {
        if (name.getType().isEmpty()) {
            meta.setDisplayName("");
            return;
        }

        if (ServerType.isPaper()) {
            meta.displayName(((ModernMessage) name).getMessage());
        } else {
            meta.setDisplayName(name.toString());
        }
    }

    /**
     * Get the display name of an item as a Message.
     *
     * @param meta The item meta
     * @return The name Message
     */
    @Nonnull
    public static Message getItemName(@Nonnull ItemMeta meta) {
        if (ServerType.isPaper()) {
            Component name = meta.displayName();
            if (name == null) {
                return EmptyMessage.getInstance();
            }
            return new ModernMessage(name);
        }

        String name = meta.getDisplayName();
        if (name == null || name.isEmpty()) {
            return EmptyMessage.getInstance();
        }
        return new LegacyMessage(name, false);
    }

    /**
     * Set the lore of an item using Messages.
     *
     * @param meta The item meta to modify
     * @param lore The list of lore Messages
     */
    public static void setItemLore(@Nonnull ItemMeta meta, @Nonnull List<Message> lore) {
        if (ServerType.isPaper()) {
            setModernItemLore(meta, lore);
        } else {
            setLegacyItemLore(meta, lore);
        }
    }

    private static void setLegacyItemLore(@Nonnull ItemMeta meta, @Nonnull List<Message> lore) {
        List<String> newLore = new ArrayList<>();
        for (Message line : lore) {
            newLore.add(line.toString());
        }
        meta.setLore(newLore);
    }

    @SuppressWarnings("unchecked")
    private static void setModernItemLore(@Nonnull ItemMeta meta, @Nonnull List<Message> lore) {
        List<Component> newLore = new ArrayList<>();
        for (Message line : lore) {
            if (line.getType().isEmpty()) {
                newLore.add(Component.empty());
            } else {
                newLore.add(((ModernMessage) line).getMessage());
            }
        }
        meta.lore(newLore);
    }

    /**
     * Get the lore of an item as Messages.
     *
     * @param meta The item meta
     * @return The list of lore Messages
     */
    @Nonnull
    public static List<Message> getItemLore(@Nonnull ItemMeta meta) {
        if (ServerType.isPaper()) {
            return getModernItemLore(meta);
        }
        return getLegacyItemLore(meta);
    }

    @Nonnull
    private static List<Message> getLegacyItemLore(@Nonnull ItemMeta meta) {
        List<Message> newLore = new ArrayList<>();
        List<String> lore = meta.getLore();
        if (lore == null) {
            return newLore;
        }
        for (String line : lore) {
            newLore.add(new LegacyMessage(line, false));
        }
        return newLore;
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    private static List<Message> getModernItemLore(@Nonnull ItemMeta meta) {
        List<Message> newLore = new ArrayList<>();
        List<Component> lore = meta.lore();
        if (lore == null) {
            return newLore;
        }
        for (Component line : lore) {
            newLore.add(new ModernMessage(line));
        }
        return newLore;
    }

    /**
     * Replace placeholders in an ItemStack's name and lore.
     *
     * @param itemStack The item to modify
     * @param placeholders Map of placeholder keys to replacement values
     * @return A new ItemStack with placeholders replaced
     */
    @Nonnull
    public static ItemStack replacePlaceholders(
            @Nonnull ItemStack itemStack, @Nonnull Map<String, String> placeholders) {
        ItemStack newItem = itemStack.clone();
        ItemMeta meta = newItem.getItemMeta();

        if (meta == null) {
            return newItem;
        }

        // Replace in name
        if (meta.hasDisplayName()) {
            Message name = getItemName(meta);
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                name = name.replaceText(entry.getKey(), entry.getValue());
            }
            setItemName(meta, name);
        }

        // Replace in lore
        if (meta.hasLore()) {
            List<Message> lore = getItemLore(meta);
            for (int i = 0; i < lore.size(); i++) {
                Message line = lore.get(i);
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    line = line.replaceText(entry.getKey(), entry.getValue());
                }
                lore.set(i, line);
            }
            setItemLore(meta, lore);
        }

        newItem.setItemMeta(meta);
        return newItem;
    }

    /**
     * Extract the texture URL from a base64 encoded skull texture.
     *
     * @param base64 The base64 encoded texture data
     * @return The texture URL, or null if parsing fails
     */
    @Nullable
    public static String getTextureURL(@Nonnull String base64) {
        try {
            String decoded = new String(Base64.getDecoder().decode(base64));
            // Format: {"textures":{"SKIN":{"url":"..."}}}
            String[] parts = decoded.split("\"SKIN\":\\{\"url\":\"");
            if (parts.length > 1) {
                return parts[1].split("\"")[0];
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Encode a texture URL to base64 format for skull textures.
     *
     * @param url The texture URL
     * @return The base64 encoded texture data
     */
    @Nonnull
    public static String encodeTextureURL(@Nonnull String url) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes());
    }
}
