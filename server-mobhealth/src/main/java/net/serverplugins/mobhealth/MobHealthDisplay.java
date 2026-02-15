package net.serverplugins.mobhealth;

import java.util.UUID;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;

public class MobHealthDisplay {

    private final UUID entityUUID;
    private final TextDisplay textDisplay;
    private BukkitTask expiryTask;

    public MobHealthDisplay(UUID entityUUID, TextDisplay textDisplay, BukkitTask expiryTask) {
        this.entityUUID = entityUUID;
        this.textDisplay = textDisplay;
        this.expiryTask = expiryTask;
    }

    public UUID getEntityUUID() {
        return entityUUID;
    }

    public TextDisplay getTextDisplay() {
        return textDisplay;
    }

    public BukkitTask getExpiryTask() {
        return expiryTask;
    }

    public void setExpiryTask(BukkitTask expiryTask) {
        this.expiryTask = expiryTask;
    }
}
