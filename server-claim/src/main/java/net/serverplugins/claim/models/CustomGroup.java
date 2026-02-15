package net.serverplugins.claim.models;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Material;

/**
 * Represents a customizable permission group for a claim. Unlike the ClaimGroup enum, CustomGroup
 * instances are stored in the database and can be renamed, have their permissions modified, and new
 * groups can be created.
 */
public class CustomGroup {
    private int id;
    private int claimId;
    private String name;
    private String colorTag;
    private Material icon;
    private int priority;
    private Set<ClaimPermission> permissions;
    private Set<ManagementPermission> managementPermissions;
    private boolean isDefault;

    public CustomGroup() {
        this.permissions = EnumSet.noneOf(ClaimPermission.class);
        this.managementPermissions = EnumSet.noneOf(ManagementPermission.class);
    }

    public CustomGroup(
            int id,
            int claimId,
            String name,
            String colorTag,
            Material icon,
            int priority,
            Set<ClaimPermission> permissions,
            Set<ManagementPermission> managementPermissions,
            boolean isDefault) {
        this.id = id;
        this.claimId = claimId;
        this.name = name;
        this.colorTag = colorTag;
        this.icon = icon;
        this.priority = priority;
        this.permissions =
                permissions != null
                        ? EnumSet.copyOf(permissions)
                        : EnumSet.noneOf(ClaimPermission.class);
        this.managementPermissions =
                managementPermissions != null
                        ? EnumSet.copyOf(managementPermissions)
                        : EnumSet.noneOf(ManagementPermission.class);
        this.isDefault = isDefault;
    }

    // Getters and setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getClaimId() {
        return claimId;
    }

    public void setClaimId(int claimId) {
        this.claimId = claimId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getColorTag() {
        return colorTag;
    }

    public void setColorTag(String colorTag) {
        this.colorTag = colorTag;
    }

    public Material getIcon() {
        return icon;
    }

    public void setIcon(Material icon) {
        this.icon = icon;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public Set<ClaimPermission> getPermissions() {
        return EnumSet.copyOf(permissions);
    }

    public void setPermissions(Set<ClaimPermission> permissions) {
        this.permissions =
                permissions != null
                        ? EnumSet.copyOf(permissions)
                        : EnumSet.noneOf(ClaimPermission.class);
    }

    public Set<ManagementPermission> getManagementPermissions() {
        return EnumSet.copyOf(managementPermissions);
    }

    public void setManagementPermissions(Set<ManagementPermission> managementPermissions) {
        this.managementPermissions =
                managementPermissions != null
                        ? EnumSet.copyOf(managementPermissions)
                        : EnumSet.noneOf(ManagementPermission.class);
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    // Permission checking
    public boolean hasPermission(ClaimPermission permission) {
        return permissions.contains(permission);
    }

    public boolean hasManagementPermission(ManagementPermission permission) {
        return managementPermissions.contains(permission);
    }

    public void addPermission(ClaimPermission permission) {
        permissions.add(permission);
    }

    public void removePermission(ClaimPermission permission) {
        permissions.remove(permission);
    }

    public void togglePermission(ClaimPermission permission) {
        if (permissions.contains(permission)) {
            permissions.remove(permission);
        } else {
            permissions.add(permission);
        }
    }

    public void addManagementPermission(ManagementPermission permission) {
        managementPermissions.add(permission);
    }

    public void removeManagementPermission(ManagementPermission permission) {
        managementPermissions.remove(permission);
    }

    // Serialization for database storage
    public String serializePermissions() {
        if (permissions.isEmpty()) return "";
        return permissions.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    public String serializeManagementPermissions() {
        if (managementPermissions.isEmpty()) return "";
        return managementPermissions.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    public static Set<ClaimPermission> deserializePermissions(String data) {
        if (data == null || data.isBlank()) {
            return EnumSet.noneOf(ClaimPermission.class);
        }
        Set<ClaimPermission> perms = EnumSet.noneOf(ClaimPermission.class);
        for (String name : data.split(",")) {
            try {
                perms.add(ClaimPermission.valueOf(name.trim()));
            } catch (IllegalArgumentException ignored) {
                // Skip invalid permission names (might be from old versions)
            }
        }
        return perms;
    }

    public static Set<ManagementPermission> deserializeManagementPermissions(String data) {
        if (data == null || data.isBlank()) {
            return EnumSet.noneOf(ManagementPermission.class);
        }
        Set<ManagementPermission> perms = EnumSet.noneOf(ManagementPermission.class);
        for (String name : data.split(",")) {
            try {
                perms.add(ManagementPermission.valueOf(name.trim()));
            } catch (IllegalArgumentException ignored) {
                // Skip invalid permission names
            }
        }
        return perms;
    }

    /**
     * Creates the default groups for a new claim. These groups match the original ClaimGroup enum
     * defaults plus management permissions.
     */
    public static List<CustomGroup> createDefaultGroups(int claimId) {
        List<CustomGroup> groups = new ArrayList<>();

        // Owner group - has all permissions, immutable
        CustomGroup owner = new CustomGroup();
        owner.setClaimId(claimId);
        owner.setName("Owner");
        owner.setColorTag("<gold>");
        owner.setIcon(Material.GOLDEN_HELMET);
        owner.setPriority(1000);
        owner.setPermissions(EnumSet.allOf(ClaimPermission.class));
        owner.setManagementPermissions(EnumSet.allOf(ManagementPermission.class));
        owner.setDefault(true);
        groups.add(owner);

        // Admin group - all gameplay perms, can manage members and flags
        CustomGroup admin = new CustomGroup();
        admin.setClaimId(claimId);
        admin.setName("Admin");
        admin.setColorTag("<light_purple>");
        admin.setIcon(Material.PURPLE_STAINED_GLASS_PANE);
        admin.setPriority(100);
        admin.setPermissions(EnumSet.allOf(ClaimPermission.class));
        admin.setManagementPermissions(
                EnumSet.of(ManagementPermission.MANAGE_MEMBERS, ManagementPermission.MANAGE_FLAGS));
        admin.setDefault(true);
        groups.add(admin);

        // Friend group - most gameplay perms, no management
        CustomGroup friend = new CustomGroup();
        friend.setClaimId(claimId);
        friend.setName("Friend");
        friend.setColorTag("<aqua>");
        friend.setIcon(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        friend.setPriority(50);
        friend.setPermissions(
                EnumSet.of(
                        ClaimPermission.ENTER_CLAIM,
                        ClaimPermission.USE_DOORS,
                        ClaimPermission.USE_FENCE_GATES,
                        ClaimPermission.DAMAGE_HOSTILE,
                        ClaimPermission.OPEN_CONTAINERS,
                        ClaimPermission.INTERACT_ENTITIES,
                        ClaimPermission.DAMAGE_PASSIVE,
                        ClaimPermission.USE_REDSTONE,
                        ClaimPermission.PICKUP_ITEMS,
                        ClaimPermission.DROP_ITEMS,
                        ClaimPermission.USE_BREWING_STANDS,
                        ClaimPermission.USE_ANVILS,
                        ClaimPermission.RIDE_VEHICLES));
        friend.setDefault(true);
        groups.add(friend);

        // Acquaintance group - basic interaction perms
        CustomGroup acquaintance = new CustomGroup();
        acquaintance.setClaimId(claimId);
        acquaintance.setName("Acquaintance");
        acquaintance.setColorTag("<green>");
        acquaintance.setIcon(Material.LIME_STAINED_GLASS_PANE);
        acquaintance.setPriority(25);
        acquaintance.setPermissions(
                EnumSet.of(
                        ClaimPermission.ENTER_CLAIM,
                        ClaimPermission.USE_DOORS,
                        ClaimPermission.USE_FENCE_GATES,
                        ClaimPermission.DAMAGE_HOSTILE,
                        ClaimPermission.RIDE_VEHICLES));
        acquaintance.setDefault(true);
        groups.add(acquaintance);

        // Visitor group - can only enter
        CustomGroup visitor = new CustomGroup();
        visitor.setClaimId(claimId);
        visitor.setName("Visitor");
        visitor.setColorTag("<yellow>");
        visitor.setIcon(Material.YELLOW_STAINED_GLASS_PANE);
        visitor.setPriority(10);
        visitor.setPermissions(EnumSet.of(ClaimPermission.ENTER_CLAIM));
        visitor.setDefault(true);
        groups.add(visitor);

        // Enemy group - no permissions
        CustomGroup enemy = new CustomGroup();
        enemy.setClaimId(claimId);
        enemy.setName("Enemy");
        enemy.setColorTag("<red>");
        enemy.setIcon(Material.RED_STAINED_GLASS_PANE);
        enemy.setPriority(0);
        enemy.setPermissions(EnumSet.noneOf(ClaimPermission.class));
        enemy.setDefault(true);
        groups.add(enemy);

        return groups;
    }

    /**
     * Converts a legacy ClaimGroup enum to a CustomGroup. Used for migration from the old system.
     */
    public static CustomGroup fromLegacyGroup(int claimId, ClaimGroup legacyGroup) {
        CustomGroup group = new CustomGroup();
        group.setClaimId(claimId);
        group.setName(legacyGroup.getDisplayName());
        group.setColorTag(legacyGroup.getColorTag());
        group.setIcon(legacyGroup.getIcon());
        group.setPermissions(legacyGroup.getDefaultPermissions());
        group.setDefault(true);

        // Set priority and management permissions based on legacy group
        switch (legacyGroup) {
            case ADMIN -> {
                group.setPriority(100);
                group.setManagementPermissions(
                        EnumSet.of(
                                ManagementPermission.MANAGE_MEMBERS,
                                ManagementPermission.MANAGE_FLAGS));
            }
            case FRIEND -> group.setPriority(50);
            case ACQUAINTANCE -> group.setPriority(25);
            case VISITOR -> group.setPriority(10);
            case ENEMY -> group.setPriority(0);
        }

        return group;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomGroup that = (CustomGroup) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    /**
     * Serializes a list of custom groups to CSV format for template storage. Format:
     * group1_name|group1_color|group1_icon|group1_priority|group1_perms|group1_mgmt_perms;group2...
     */
    public static String serializeToCSV(List<CustomGroup> groups) {
        if (groups == null || groups.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (CustomGroup group : groups) {
            if (sb.length() > 0) sb.append(";");

            sb.append(group.getName())
                    .append("|")
                    .append(group.getColorTag())
                    .append("|")
                    .append(group.getIcon() != null ? group.getIcon().name() : "")
                    .append("|")
                    .append(group.getPriority())
                    .append("|")
                    .append(group.serializePermissions())
                    .append("|")
                    .append(group.serializeManagementPermissions());
        }
        return sb.toString();
    }

    /** Deserializes custom groups from CSV format. Used when applying templates to claims. */
    public static List<CustomGroup> deserializeFromCSV(String csv, int claimId) {
        List<CustomGroup> groups = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return groups;
        }

        for (String groupEntry : csv.split(";")) {
            if (groupEntry.isBlank()) continue;

            String[] parts = groupEntry.split("\\|", 6);
            if (parts.length < 6) continue; // Skip malformed entries

            try {
                CustomGroup group = new CustomGroup();
                group.setClaimId(claimId);
                group.setName(parts[0]);
                group.setColorTag(parts[1]);
                group.setIcon(parts[2].isEmpty() ? null : Material.valueOf(parts[2]));
                group.setPriority(Integer.parseInt(parts[3]));
                group.setPermissions(deserializePermissions(parts[4]));
                group.setManagementPermissions(deserializeManagementPermissions(parts[5]));
                group.setDefault(false); // Template groups are not default groups

                groups.add(group);
            } catch (IllegalArgumentException e) {
                // Skip invalid groups
                continue;
            }
        }

        return groups;
    }

    @Override
    public String toString() {
        return "CustomGroup{id=" + id + ", name='" + name + "', priority=" + priority + "}";
    }
}
