package net.serverplugins.admin.inspect;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

public class InspectManager {

    private final ServerAdmin plugin;
    private final Map<UUID, InspectSession> activeSessions;
    private final OfflinePlayerDataHandler offlineHandler;
    private BukkitTask syncTask;
    private final boolean offlineSupported;

    public InspectManager(ServerAdmin plugin) {
        this.plugin = plugin;
        this.activeSessions = new HashMap<>();

        // Only initialize offline handler if NBTAPI is available
        if (Bukkit.getPluginManager().getPlugin("NBTAPI") != null) {
            this.offlineHandler = new OfflinePlayerDataHandler(plugin);
            this.offlineSupported = true;
            plugin.getLogger().info("NBTAPI found - offline player inspection enabled.");
        } else {
            this.offlineHandler = null;
            this.offlineSupported = false;
            plugin.getLogger().info("NBTAPI not found - offline player inspection disabled.");
        }

        startSyncTask();
    }

    public boolean isOfflineSupported() {
        return offlineSupported;
    }

    private void startSyncTask() {
        // Sync inventories every 10 ticks (500ms) for live updates
        syncTask = Bukkit.getScheduler().runTaskTimer(plugin, this::syncAllInventories, 1L, 10L);
    }

    private void syncAllInventories() {
        for (Map.Entry<UUID, InspectSession> entry : activeSessions.entrySet()) {
            InspectSession session = entry.getValue();

            // Skip offline sessions - they don't need live sync
            if (session.isOffline()) {
                continue;
            }

            Player viewer = Bukkit.getPlayer(entry.getKey());
            Player target = Bukkit.getPlayer(session.getTargetId());

            if (viewer == null || !viewer.isOnline() || target == null || !target.isOnline()) {
                continue;
            }

            if (session.getType() == InspectSession.Type.INVSEE) {
                syncInvSee(viewer, target, session.getInventory());
            }
            // EcSee doesn't need manual sync - it's the actual ender chest
        }
    }

    private void syncInvSee(Player viewer, Player target, Inventory displayInv) {
        if (displayInv == null) return;

        // Only sync if the viewer has this inventory open
        if (viewer.getOpenInventory().getTopInventory() != displayInv) {
            return;
        }

        // Skip sync if viewer has cursor item (actively editing inventory)
        if (viewer.getItemOnCursor() != null
                && viewer.getItemOnCursor().getType() != org.bukkit.Material.AIR) {
            return;
        }

        // Compute hash of target's inventory
        int currentHash = computeInventoryHash(target.getInventory());

        // Get session to check last hash
        InspectSession session = activeSessions.get(viewer.getUniqueId());
        if (session != null) {
            // If hash matches, skip the sync
            if (session.getLastContentHash() == currentHash) {
                return;
            }
            // Update stored hash
            session.setLastContentHash(currentHash);
        }

        // Sync main inventory
        ItemStack[] contents = target.getInventory().getContents();
        for (int i = 0; i < Math.min(36, contents.length); i++) {
            ItemStack current = displayInv.getItem(i);
            ItemStack actual = contents[i];

            if (!itemsEqual(current, actual)) {
                displayInv.setItem(i, actual);
            }
        }

        // Sync armor
        ItemStack[] armor = target.getInventory().getArmorContents();
        syncSlot(displayInv, 45, armor[3]); // Helmet
        syncSlot(displayInv, 46, armor[2]); // Chestplate
        syncSlot(displayInv, 47, armor[1]); // Leggings
        syncSlot(displayInv, 48, armor[0]); // Boots

        // Sync offhand
        syncSlot(displayInv, 53, target.getInventory().getItemInOffHand());
    }

    private void syncSlot(Inventory inv, int slot, ItemStack item) {
        ItemStack current = inv.getItem(slot);
        if (!itemsEqual(current, item)) {
            inv.setItem(slot, item);
        }
    }

    private boolean itemsEqual(ItemStack a, ItemStack b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    /**
     * Computes a hash code for all inventory contents including main inventory, armor, and offhand.
     * This provides a quick way to detect if the inventory has changed without comparing each item.
     */
    private int computeInventoryHash(org.bukkit.inventory.PlayerInventory inventory) {
        int hash = 1;

        // Hash main inventory contents
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < Math.min(36, contents.length); i++) {
            ItemStack item = contents[i];
            hash = 31 * hash + (item != null ? item.hashCode() : 0);
        }

        // Hash armor contents
        ItemStack[] armor = inventory.getArmorContents();
        for (ItemStack item : armor) {
            hash = 31 * hash + (item != null ? item.hashCode() : 0);
        }

        // Hash offhand
        ItemStack offhand = inventory.getItemInOffHand();
        hash = 31 * hash + (offhand != null ? offhand.hashCode() : 0);

        return hash;
    }

    public void openInvSee(Player viewer, Player target, Inventory inventory) {
        openInvSee(viewer, target, inventory, true); // Default to editable
    }

    public void openInvSee(Player viewer, Player target, Inventory inventory, boolean canEdit) {
        InspectSession session =
                new InspectSession(
                        target.getUniqueId(), InspectSession.Type.INVSEE, inventory, canEdit);
        activeSessions.put(viewer.getUniqueId(), session);
    }

    public void openEcSee(Player viewer, Player target) {
        openEcSee(viewer, target, true); // Default to editable
    }

    public void openEcSee(Player viewer, Player target, boolean canEdit) {
        InspectSession session =
                new InspectSession(target.getUniqueId(), InspectSession.Type.ECSEE, null, canEdit);
        activeSessions.put(viewer.getUniqueId(), session);
    }

    public void closeInspect(Player viewer) {
        activeSessions.remove(viewer.getUniqueId());
    }

    public InspectSession getSession(UUID viewerId) {
        return activeSessions.get(viewerId);
    }

    public boolean hasActiveSession(UUID viewerId) {
        return activeSessions.containsKey(viewerId);
    }

    /** Handle inventory changes from staff member back to target player */
    public void handleInventoryChange(Player viewer, int slot, ItemStack item) {
        InspectSession session = activeSessions.get(viewer.getUniqueId());
        if (session == null) return;

        // Check permission to modify
        if (!viewer.hasPermission("serveradmin.invsee.modify")) {
            return;
        }

        Player target = Bukkit.getPlayer(session.getTargetId());
        if (target == null) return;

        if (session.getType() == InspectSession.Type.INVSEE) {
            if (slot < 36) {
                // Main inventory
                target.getInventory().setItem(slot, item);
            } else if (slot == 45) {
                target.getInventory().setHelmet(item);
            } else if (slot == 46) {
                target.getInventory().setChestplate(item);
            } else if (slot == 47) {
                target.getInventory().setLeggings(item);
            } else if (slot == 48) {
                target.getInventory().setBoots(item);
            } else if (slot == 53) {
                target.getInventory().setItemInOffHand(item);
            }
        }
        // EcSee changes are automatically saved since it's the actual ender chest
    }

    public OfflinePlayerDataHandler getOfflineHandler() {
        return offlineHandler;
    }

    public void openOfflineInvSee(
            Player viewer, UUID targetId, String targetName, Inventory inventory, boolean canEdit) {
        InspectSession session =
                new InspectSession(
                        targetId, targetName, InspectSession.Type.INVSEE, inventory, canEdit, true);
        activeSessions.put(viewer.getUniqueId(), session);
    }

    public void openOfflineEcSee(
            Player viewer, UUID targetId, String targetName, Inventory inventory, boolean canEdit) {
        InspectSession session =
                new InspectSession(
                        targetId, targetName, InspectSession.Type.ECSEE, inventory, canEdit, true);
        activeSessions.put(viewer.getUniqueId(), session);
    }

    public void saveOfflineSession(Player viewer) {
        InspectSession session = activeSessions.get(viewer.getUniqueId());
        if (session == null || !session.isOffline()) {
            return;
        }

        Inventory inv = session.getInventory();
        if (inv == null) {
            return;
        }

        // Check if any changes were made
        if (!hasChanges(inv.getContents(), session.getOriginalContents())) {
            return;
        }

        UUID targetId = session.getTargetId();
        String targetName =
                session.getTargetName() != null ? session.getTargetName() : targetId.toString();

        if (session.getType() == InspectSession.Type.INVSEE) {
            // Extract inventory components from the 54-slot display
            ItemStack[] main = new ItemStack[36];
            ItemStack[] armor = new ItemStack[4];
            ItemStack offhand = null;

            // Slots 0-35: main inventory
            for (int i = 0; i < 36; i++) {
                main[i] = inv.getItem(i);
            }

            // Slots 45-48: armor (helmet, chest, legs, boots -> index 3,2,1,0)
            armor[3] = inv.getItem(45); // Helmet
            armor[2] = inv.getItem(46); // Chestplate
            armor[1] = inv.getItem(47); // Leggings
            armor[0] = inv.getItem(48); // Boots

            // Slot 53: offhand
            offhand = inv.getItem(53);

            offlineHandler
                    .saveInventoryAsync(targetId, main, armor, offhand)
                    .thenAccept(
                            success -> {
                                Bukkit.getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    if (success) {
                                                        TextUtil.send(
                                                                viewer,
                                                                plugin.getAdminConfig().getPrefix()
                                                                        + "<green>Saved offline inventory for <white>"
                                                                        + targetName);
                                                    } else {
                                                        TextUtil.send(
                                                                viewer,
                                                                plugin.getAdminConfig().getPrefix()
                                                                        + "<red>Failed to save offline inventory for <white>"
                                                                        + targetName);
                                                    }
                                                });
                            });

        } else if (session.getType() == InspectSession.Type.ECSEE) {
            ItemStack[] contents = inv.getContents();
            offlineHandler
                    .saveEnderChestAsync(targetId, contents)
                    .thenAccept(
                            success -> {
                                Bukkit.getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    if (success) {
                                                        TextUtil.send(
                                                                viewer,
                                                                plugin.getAdminConfig().getPrefix()
                                                                        + "<green>Saved offline ender chest for <white>"
                                                                        + targetName);
                                                    } else {
                                                        TextUtil.send(
                                                                viewer,
                                                                plugin.getAdminConfig().getPrefix()
                                                                        + "<red>Failed to save offline ender chest for <white>"
                                                                        + targetName);
                                                    }
                                                });
                            });
        }
    }

    private boolean hasChanges(ItemStack[] current, ItemStack[] original) {
        if (original == null) return true;
        if (current.length != original.length) return true;
        for (int i = 0; i < current.length; i++) {
            if (!itemsEqual(current[i], original[i])) {
                return true;
            }
        }
        return false;
    }

    public Map<UUID, InspectSession> getActiveSessions() {
        return activeSessions;
    }

    public void shutdown() {
        if (syncTask != null) {
            syncTask.cancel();
        }
        activeSessions.clear();
    }
}
