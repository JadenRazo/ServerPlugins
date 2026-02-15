package net.serverplugins.admin.inspect;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class InspectSession {

    public enum Type {
        INVSEE,
        ECSEE
    }

    private final UUID targetId;
    private final Type type;
    private final Inventory inventory;
    private final boolean canEdit;
    private final boolean isOffline;
    private final String targetName;
    private boolean dirty;
    private ItemStack[] originalContents;
    private int lastContentHash;

    public InspectSession(UUID targetId, Type type, Inventory inventory) {
        this(targetId, type, inventory, true); // Default to editable for backwards compatibility
    }

    public InspectSession(UUID targetId, Type type, Inventory inventory, boolean canEdit) {
        this(targetId, null, type, inventory, canEdit, false);
    }

    public InspectSession(
            UUID targetId,
            String targetName,
            Type type,
            Inventory inventory,
            boolean canEdit,
            boolean isOffline) {
        this.targetId = targetId;
        this.targetName = targetName;
        this.type = type;
        this.inventory = inventory;
        this.canEdit = canEdit;
        this.isOffline = isOffline;
        this.dirty = false;

        if (inventory != null && isOffline) {
            this.originalContents = new ItemStack[inventory.getSize()];
            for (int i = 0; i < inventory.getSize(); i++) {
                ItemStack item = inventory.getItem(i);
                this.originalContents[i] = item != null ? item.clone() : null;
            }
        }
    }

    public UUID getTargetId() {
        return targetId;
    }

    public Type getType() {
        return type;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public boolean canEdit() {
        return canEdit;
    }

    public boolean isOffline() {
        return isOffline;
    }

    public String getTargetName() {
        return targetName;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public ItemStack[] getOriginalContents() {
        return originalContents;
    }

    public int getLastContentHash() {
        return lastContentHash;
    }

    public void setLastContentHash(int hash) {
        this.lastContentHash = hash;
    }
}
