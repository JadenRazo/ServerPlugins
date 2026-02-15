package net.serverplugins.deathbuyback.serialization;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

public class InventorySerializer {

    private static final Logger LOGGER = Logger.getLogger("ServerDeathBuyback");

    /** Serialize an array of ItemStacks to a Base64 string. */
    public static String serialize(ItemStack[] items) {
        if (items == null) return null;

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            BukkitObjectOutputStream oos = new BukkitObjectOutputStream(baos);

            oos.writeInt(items.length);
            for (ItemStack item : items) {
                oos.writeObject(item);
            }

            oos.close();
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to serialize inventory", e);
            return null;
        }
    }

    /** Serialize a single ItemStack to a Base64 string. */
    public static String serializeSingle(ItemStack item) {
        if (item == null) return null;
        return serialize(new ItemStack[] {item});
    }

    /** Deserialize a Base64 string to an array of ItemStacks. */
    public static ItemStack[] deserialize(String base64, int expectedSize) {
        if (base64 == null || base64.isEmpty()) {
            return new ItemStack[expectedSize];
        }

        try {
            byte[] data = Base64.getDecoder().decode(base64);
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            BukkitObjectInputStream ois = new BukkitObjectInputStream(bais);

            int length = ois.readInt();
            ItemStack[] items = new ItemStack[expectedSize];

            for (int i = 0; i < length && i < expectedSize; i++) {
                items[i] = (ItemStack) ois.readObject();
            }

            ois.close();
            return items;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to deserialize inventory", e);
            return new ItemStack[expectedSize];
        }
    }

    /** Deserialize a Base64 string to a single ItemStack. */
    public static ItemStack deserializeSingle(String base64) {
        if (base64 == null || base64.isEmpty()) return null;

        ItemStack[] items = deserialize(base64, 1);
        return items.length > 0 ? items[0] : null;
    }

    /** Count non-null items in an array. */
    public static int countItems(ItemStack[] items) {
        if (items == null) return 0;

        int count = 0;
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /** Count non-null item stacks in an array. */
    public static int countStacks(ItemStack[] items) {
        if (items == null) return 0;

        int count = 0;
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                count++;
            }
        }
        return count;
    }
}
