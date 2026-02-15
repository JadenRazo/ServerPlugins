package net.serverplugins.items.models;

public class CustomFurniture {

    private final CustomItem item;
    private final float hitboxWidth;
    private final float hitboxHeight;
    private final float[] scale;
    private final float[] translation;
    private final boolean useBarrier;
    private final boolean sittable;
    private final float sitHeight;
    private final RotationType rotationType;

    public CustomFurniture(
            CustomItem item,
            float hitboxWidth,
            float hitboxHeight,
            float[] scale,
            float[] translation,
            boolean useBarrier,
            boolean sittable,
            float sitHeight,
            RotationType rotationType) {
        this.item = item;
        this.hitboxWidth = hitboxWidth;
        this.hitboxHeight = hitboxHeight;
        this.scale = scale;
        this.translation = translation;
        this.useBarrier = useBarrier;
        this.sittable = sittable;
        this.sitHeight = sitHeight;
        this.rotationType = rotationType;
    }

    public CustomItem getItem() {
        return item;
    }

    public String getId() {
        return item.getId();
    }

    public float getHitboxWidth() {
        return hitboxWidth;
    }

    public float getHitboxHeight() {
        return hitboxHeight;
    }

    public float[] getScale() {
        return scale;
    }

    public float[] getTranslation() {
        return translation;
    }

    public boolean isUseBarrier() {
        return useBarrier;
    }

    public boolean isSittable() {
        return sittable;
    }

    public float getSitHeight() {
        return sitHeight;
    }

    public RotationType getRotationType() {
        return rotationType;
    }

    public enum RotationType {
        NONE,
        PLAYER_YAW,
        FIXED
    }
}
