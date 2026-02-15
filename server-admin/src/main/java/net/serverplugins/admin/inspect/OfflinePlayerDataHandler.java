package net.serverplugins.admin.inspect;

import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.NBTFile;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBTCompoundList;
import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import net.serverplugins.admin.ServerAdmin;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

public class OfflinePlayerDataHandler {

    private final ServerAdmin plugin;
    private final Map<UUID, ReentrantLock> fileLocks = new ConcurrentHashMap<>();

    private static final String INVENTORY_TAG = "Inventory";
    private static final String ENDERITEMS_TAG = "EnderItems";
    private static final String SLOT_TAG = "Slot";

    public OfflinePlayerDataHandler(ServerAdmin plugin) {
        this.plugin = plugin;
    }

    public File getPlayerDataFile(UUID uuid) {
        File worldFolder = Bukkit.getWorlds().get(0).getWorldFolder();
        return new File(worldFolder, "playerdata/" + uuid.toString() + ".dat");
    }

    public CompletableFuture<OfflineInventoryData> loadInventoryAsync(UUID uuid) {
        return CompletableFuture.supplyAsync(
                () -> {
                    File file = getPlayerDataFile(uuid);
                    if (!file.exists()) {
                        return null;
                    }

                    ReentrantLock lock = fileLocks.computeIfAbsent(uuid, k -> new ReentrantLock());
                    lock.lock();
                    try {
                        NBTFile nbtFile = new NBTFile(file);
                        ReadWriteNBTCompoundList invList = nbtFile.getCompoundList(INVENTORY_TAG);

                        ItemStack[] main = new ItemStack[36];
                        ItemStack[] armor = new ItemStack[4];
                        ItemStack offhand = null;

                        for (ReadWriteNBT compound : invList) {
                            byte slot = compound.getByte(SLOT_TAG);
                            ItemStack item = NBT.itemStackFromNBT(compound);

                            if (slot >= 0 && slot <= 35) {
                                main[slot] = item;
                            } else if (slot >= 100 && slot <= 103) {
                                // 100=boots, 101=legs, 102=chest, 103=helmet
                                armor[slot - 100] = item;
                            } else if (slot == -106) {
                                offhand = item;
                            }
                        }

                        return new OfflineInventoryData(main, armor, offhand);
                    } catch (Exception e) {
                        plugin.getLogger()
                                .warning(
                                        "Failed to load offline inventory for "
                                                + uuid
                                                + ": "
                                                + e.getMessage());
                        return null;
                    } finally {
                        lock.unlock();
                    }
                });
    }

    public CompletableFuture<ItemStack[]> loadEnderChestAsync(UUID uuid) {
        return CompletableFuture.supplyAsync(
                () -> {
                    File file = getPlayerDataFile(uuid);
                    if (!file.exists()) {
                        return null;
                    }

                    ReentrantLock lock = fileLocks.computeIfAbsent(uuid, k -> new ReentrantLock());
                    lock.lock();
                    try {
                        NBTFile nbtFile = new NBTFile(file);
                        ReadWriteNBTCompoundList enderList =
                                nbtFile.getCompoundList(ENDERITEMS_TAG);

                        ItemStack[] contents = new ItemStack[27];

                        for (ReadWriteNBT compound : enderList) {
                            byte slot = compound.getByte(SLOT_TAG);
                            if (slot >= 0 && slot < 27) {
                                contents[slot] = NBT.itemStackFromNBT(compound);
                            }
                        }

                        return contents;
                    } catch (Exception e) {
                        plugin.getLogger()
                                .warning(
                                        "Failed to load offline ender chest for "
                                                + uuid
                                                + ": "
                                                + e.getMessage());
                        return null;
                    } finally {
                        lock.unlock();
                    }
                });
    }

    public CompletableFuture<Boolean> saveInventoryAsync(
            UUID uuid, ItemStack[] main, ItemStack[] armor, ItemStack offhand) {
        return CompletableFuture.supplyAsync(
                () -> {
                    File file = getPlayerDataFile(uuid);
                    if (!file.exists()) {
                        return false;
                    }

                    ReentrantLock lock = fileLocks.computeIfAbsent(uuid, k -> new ReentrantLock());
                    lock.lock();
                    try {
                        NBTFile nbtFile = new NBTFile(file);
                        ReadWriteNBTCompoundList invList = nbtFile.getCompoundList(INVENTORY_TAG);
                        invList.clear();

                        // Add main inventory items (slots 0-35)
                        for (int i = 0; i < main.length; i++) {
                            if (main[i] != null && !main[i].getType().isAir()) {
                                ReadWriteNBT itemNbt = NBT.itemStackToNBT(main[i]);
                                itemNbt.setByte(SLOT_TAG, (byte) i);
                                invList.addCompound(itemNbt);
                            }
                        }

                        // Add armor (slot 100=boots, 101=legs, 102=chest, 103=helmet)
                        for (int i = 0; i < armor.length; i++) {
                            if (armor[i] != null && !armor[i].getType().isAir()) {
                                ReadWriteNBT itemNbt = NBT.itemStackToNBT(armor[i]);
                                itemNbt.setByte(SLOT_TAG, (byte) (100 + i));
                                invList.addCompound(itemNbt);
                            }
                        }

                        // Add offhand (slot -106)
                        if (offhand != null && !offhand.getType().isAir()) {
                            ReadWriteNBT itemNbt = NBT.itemStackToNBT(offhand);
                            itemNbt.setByte(SLOT_TAG, (byte) -106);
                            invList.addCompound(itemNbt);
                        }

                        nbtFile.save();
                        return true;
                    } catch (Exception e) {
                        plugin.getLogger()
                                .warning(
                                        "Failed to save offline inventory for "
                                                + uuid
                                                + ": "
                                                + e.getMessage());
                        return false;
                    } finally {
                        lock.unlock();
                    }
                });
    }

    public CompletableFuture<Boolean> saveEnderChestAsync(UUID uuid, ItemStack[] contents) {
        return CompletableFuture.supplyAsync(
                () -> {
                    File file = getPlayerDataFile(uuid);
                    if (!file.exists()) {
                        return false;
                    }

                    ReentrantLock lock = fileLocks.computeIfAbsent(uuid, k -> new ReentrantLock());
                    lock.lock();
                    try {
                        NBTFile nbtFile = new NBTFile(file);
                        ReadWriteNBTCompoundList enderList =
                                nbtFile.getCompoundList(ENDERITEMS_TAG);
                        enderList.clear();

                        for (int i = 0; i < contents.length; i++) {
                            if (contents[i] != null && !contents[i].getType().isAir()) {
                                ReadWriteNBT itemNbt = NBT.itemStackToNBT(contents[i]);
                                itemNbt.setByte(SLOT_TAG, (byte) i);
                                enderList.addCompound(itemNbt);
                            }
                        }

                        nbtFile.save();
                        return true;
                    } catch (Exception e) {
                        plugin.getLogger()
                                .warning(
                                        "Failed to save offline ender chest for "
                                                + uuid
                                                + ": "
                                                + e.getMessage());
                        return false;
                    } finally {
                        lock.unlock();
                    }
                });
    }

    public record OfflineInventoryData(
            ItemStack[] mainInventory, ItemStack[] armorContents, ItemStack offhand) {}
}
