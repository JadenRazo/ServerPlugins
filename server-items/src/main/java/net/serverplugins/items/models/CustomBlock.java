package net.serverplugins.items.models;

import org.bukkit.Sound;

public class CustomBlock {

    private final CustomItem item;
    private final float hardness;
    private final String dropItemId;
    private final String breakTool;
    private final Sound placeSound;
    private final Sound breakSound;
    private BlockStateMapping stateMapping;

    public CustomBlock(
            CustomItem item,
            float hardness,
            String dropItemId,
            String breakTool,
            Sound placeSound,
            Sound breakSound) {
        this.item = item;
        this.hardness = hardness;
        this.dropItemId = dropItemId;
        this.breakTool = breakTool;
        this.placeSound = placeSound;
        this.breakSound = breakSound;
    }

    public CustomItem getItem() {
        return item;
    }

    public String getId() {
        return item.getId();
    }

    public float getHardness() {
        return hardness;
    }

    public String getDropItemId() {
        return dropItemId;
    }

    public String getBreakTool() {
        return breakTool;
    }

    public Sound getPlaceSound() {
        return placeSound;
    }

    public Sound getBreakSound() {
        return breakSound;
    }

    public BlockStateMapping getStateMapping() {
        return stateMapping;
    }

    public void setStateMapping(BlockStateMapping stateMapping) {
        this.stateMapping = stateMapping;
    }
}
