package net.serverplugins.claim.models;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public class GroupPermissions {

    private final int profileId;
    private final Map<ClaimGroup, Set<ClaimPermission>> permissions;

    public GroupPermissions(int profileId) {
        this.profileId = profileId;
        this.permissions = new EnumMap<>(ClaimGroup.class);

        // Initialize with default permissions for each group
        for (ClaimGroup group : ClaimGroup.values()) {
            permissions.put(group, group.getDefaultPermissions());
        }
    }

    public int getProfileId() {
        return profileId;
    }

    public boolean hasPermission(ClaimGroup group, ClaimPermission permission) {
        Set<ClaimPermission> groupPerms = permissions.get(group);
        return groupPerms != null && groupPerms.contains(permission);
    }

    public void setPermission(ClaimGroup group, ClaimPermission permission, boolean enabled) {
        permissions.computeIfAbsent(group, k -> EnumSet.noneOf(ClaimPermission.class));
        if (enabled) {
            permissions.get(group).add(permission);
        } else {
            permissions.get(group).remove(permission);
        }
    }

    public void togglePermission(ClaimGroup group, ClaimPermission permission) {
        setPermission(group, permission, !hasPermission(group, permission));
    }

    public Set<ClaimPermission> getPermissions(ClaimGroup group) {
        return permissions.getOrDefault(group, EnumSet.noneOf(ClaimPermission.class));
    }

    public void setPermissions(ClaimGroup group, Set<ClaimPermission> perms) {
        permissions.put(group, EnumSet.copyOf(perms));
    }

    // Serialize permissions to a string for database storage
    // Includes all groups (even empty) so intentionally cleared groups are preserved
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        for (ClaimGroup group : ClaimGroup.values()) {
            if (sb.length() > 0) sb.append(";");
            sb.append(group.name()).append(":");

            Set<ClaimPermission> perms = permissions.get(group);
            if (perms != null && !perms.isEmpty()) {
                boolean first = true;
                for (ClaimPermission perm : perms) {
                    if (!first) sb.append(",");
                    sb.append(perm.name());
                    first = false;
                }
            }
            // Empty groups will have format "GROUP:" which deserializes as intentionally empty
        }
        return sb.toString();
    }

    // Deserialize permissions from database string
    public static GroupPermissions deserialize(int profileId, String data) {
        GroupPermissions gp = new GroupPermissions(profileId);
        // Start with defaults already set by constructor
        if (data == null || data.isEmpty()) return gp;

        // Only override permissions for groups explicitly listed in the data
        // Groups not in the data string keep their defaults
        for (String groupEntry : data.split(";")) {
            if (groupEntry.isEmpty()) continue;

            String[] parts = groupEntry.split(":", 2);
            if (parts.length < 1 || parts[0].isEmpty()) continue;

            try {
                ClaimGroup group = ClaimGroup.valueOf(parts[0]);
                // Clear this group's defaults since it's explicitly defined
                EnumSet<ClaimPermission> perms = EnumSet.noneOf(ClaimPermission.class);

                // Add permissions if any are listed (parts.length == 2 means there's content after
                // the colon)
                if (parts.length == 2 && !parts[1].isEmpty()) {
                    for (String permName : parts[1].split(",")) {
                        try {
                            ClaimPermission perm = ClaimPermission.valueOf(permName.trim());
                            perms.add(perm);
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                }
                gp.permissions.put(group, perms);
            } catch (IllegalArgumentException ignored) {
            }
        }

        return gp;
    }

    /**
     * Creates GroupPermissions from CSV string without requiring a profileId. Used when applying
     * templates to claims.
     */
    public static GroupPermissions fromCSV(String data) {
        return deserialize(0, data); // Use 0 as dummy profile ID for template application
    }
}
