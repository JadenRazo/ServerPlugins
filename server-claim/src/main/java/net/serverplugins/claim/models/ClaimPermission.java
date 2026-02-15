package net.serverplugins.claim.models;

import org.bukkit.Material;

public enum ClaimPermission {
    SHOOT_PROJECTILES("Shoot Projectiles", Material.BOW, "Fire arrows, throw potions, etc."),
    USE_EXPLOSIVES("Use Explosives", Material.TNT, "Use TNT, end crystals, etc."),
    DROP_ITEMS("Drop Items", Material.DROPPER, "Drop items on the ground"),
    ENTER_CLAIM("Enter Claim", Material.OAK_DOOR, "Walk into the claim"),
    OPEN_CONTAINERS("Open Containers", Material.CHEST, "Open chests, barrels, etc."),
    DAMAGE_PASSIVE("Damage Passive Mobs", Material.WHEAT, "Hurt animals and passive mobs"),
    INTERACT_ENTITIES("Interact with Entities", Material.LEAD, "Right-click entities"),
    USE_DOORS("Use Doors", Material.OAK_DOOR, "Open/close doors and trapdoors"),
    USE_BUCKETS("Fill/Empty Buckets", Material.BUCKET, "Pick up or place liquids"),
    SET_HOME("Set Home", Material.RED_BED, "Set a home in the claim"),
    BREAK_BLOCKS("Break Blocks", Material.IRON_PICKAXE, "Mine and destroy blocks"),
    PLACE_BLOCKS("Place Blocks", Material.GRASS_BLOCK, "Place blocks"),
    CHEST_SHOPS("Create Chest Shops", Material.OAK_SIGN, "Create shop signs"),
    EDIT_SIGNS("Edit Signs", Material.OAK_SIGN, "Change sign text"),
    DAMAGE_HOSTILE("Damage Hostile Mobs", Material.DIAMOND_SWORD, "Fight monsters"),
    USE_REDSTONE("Use Redstone", Material.REDSTONE, "Interact with buttons, levers, etc."),
    PICKUP_ITEMS("Pick Up Items", Material.HOPPER, "Collect items from ground"),
    SPAWNERS("Place/Break Spawners", Material.SPAWNER, "Modify spawner blocks"),
    USE_FENCE_GATES("Use Fence Gates", Material.OAK_FENCE_GATE, "Open/close fence gates"),
    USE_BREWING_STANDS("Use Brewing Stands", Material.BREWING_STAND, "Access brewing stands"),
    USE_ANVILS("Use Anvils", Material.ANVIL, "Use anvils and grindstones"),
    RIDE_VEHICLES("Ride Vehicles", Material.SADDLE, "Mount horses, boats, minecarts, etc.");

    private final String displayName;
    private final Material icon;
    private final String description;

    ClaimPermission(String displayName, Material icon, String description) {
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public String getDescription() {
        return description;
    }
}
