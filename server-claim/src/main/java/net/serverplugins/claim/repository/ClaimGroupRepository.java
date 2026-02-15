package net.serverplugins.claim.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.serverplugins.api.database.Database;
import net.serverplugins.claim.models.ClaimGroup;
import net.serverplugins.claim.models.CustomGroup;
import org.bukkit.Material;

/** Repository for managing CustomGroup database operations. */
public class ClaimGroupRepository {

    private final Database database;

    public ClaimGroupRepository(Database database) {
        this.database = database;
    }

    /** Gets all custom groups for a claim. */
    public List<CustomGroup> getGroupsForClaim(int claimId) {
        return database.query(
                "SELECT * FROM server_claim_groups WHERE claim_id = ? ORDER BY priority DESC",
                rs -> {
                    List<CustomGroup> groups = new ArrayList<>();
                    while (rs.next()) {
                        groups.add(mapGroup(rs));
                    }
                    return groups;
                },
                claimId);
    }

    /** Gets a specific group by ID. */
    public CustomGroup getGroupById(int groupId) {
        return database.query(
                "SELECT * FROM server_claim_groups WHERE id = ?",
                rs -> {
                    if (rs.next()) {
                        return mapGroup(rs);
                    }
                    return null;
                },
                groupId);
    }

    /** Gets a group by name within a claim. */
    public CustomGroup getGroupByName(int claimId, String name) {
        return database.query(
                "SELECT * FROM server_claim_groups WHERE claim_id = ? AND name = ?",
                rs -> {
                    if (rs.next()) {
                        return mapGroup(rs);
                    }
                    return null;
                },
                claimId,
                name);
    }

    /** Gets the default visitor group for a claim (lowest priority default group). */
    public CustomGroup getVisitorGroup(int claimId) {
        return database.query(
                "SELECT * FROM server_claim_groups WHERE claim_id = ? AND name = 'Visitor'",
                rs -> {
                    if (rs.next()) {
                        return mapGroup(rs);
                    }
                    return null;
                },
                claimId);
    }

    /** Saves or updates a custom group. */
    public boolean saveGroup(CustomGroup group) {
        if (group.getId() > 0) {
            // Update existing group
            try {
                int affected =
                        database.executeUpdate(
                                "UPDATE server_claim_groups SET "
                                        + "name = ?, color_tag = ?, icon = ?, priority = ?, "
                                        + "permissions = ?, management_permissions = ?, is_default = ? "
                                        + "WHERE id = ?",
                                group.getName(),
                                group.getColorTag(),
                                group.getIcon().name(),
                                group.getPriority(),
                                group.serializePermissions(),
                                group.serializeManagementPermissions(),
                                group.isDefault(),
                                group.getId());
                return affected > 0;
            } catch (SQLException e) {
                return false;
            }
        } else {
            // Insert new group with generated key retrieval
            String sql =
                    "INSERT INTO server_claim_groups "
                            + "(claim_id, name, color_tag, icon, priority, permissions, management_permissions, is_default) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

            try (Connection conn = database.getConnection();
                    PreparedStatement stmt =
                            conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                stmt.setInt(1, group.getClaimId());
                stmt.setString(2, group.getName());
                stmt.setString(3, group.getColorTag());
                stmt.setString(4, group.getIcon().name());
                stmt.setInt(5, group.getPriority());
                stmt.setString(6, group.serializePermissions());
                stmt.setString(7, group.serializeManagementPermissions());
                stmt.setBoolean(8, group.isDefault());

                stmt.executeUpdate();

                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        group.setId(keys.getInt(1));
                        return true;
                    }
                }
                return false;
            } catch (SQLException e) {
                return false;
            }
        }
    }

    /** Creates all default groups for a new claim. */
    public boolean createDefaultGroups(int claimId) {
        List<CustomGroup> defaultGroups = CustomGroup.createDefaultGroups(claimId);
        boolean allSuccess = true;
        for (CustomGroup group : defaultGroups) {
            if (!saveGroup(group)) {
                allSuccess = false;
            }
        }
        return allSuccess;
    }

    /** Deletes a custom group. Note: Members assigned to this group should be reassigned first. */
    public boolean deleteGroup(int groupId) {
        try {
            int affected =
                    database.executeUpdate(
                            "DELETE FROM server_claim_groups WHERE id = ? AND is_default = FALSE",
                            groupId);
            return affected > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    /** Deletes all groups for a claim. */
    public boolean deleteGroupsForClaim(int claimId) {
        try {
            database.executeUpdate("DELETE FROM server_claim_groups WHERE claim_id = ?", claimId);
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Migrates legacy ClaimGroup enum assignments to custom groups. This finds members with
     * group_name set and assigns them to the corresponding CustomGroup.
     */
    public CompletableFuture<Integer> migrateFromLegacyGroups(int claimId) {
        return CompletableFuture.supplyAsync(
                () -> {
                    int migrated = 0;

                    // Get all custom groups for this claim
                    List<CustomGroup> groups = getGroupsForClaim(claimId);
                    if (groups.isEmpty()) {
                        // Create default groups if they don't exist
                        createDefaultGroups(claimId);
                        groups = getGroupsForClaim(claimId);
                    }

                    // For each legacy group name, find the matching custom group and update members
                    for (ClaimGroup legacyGroup : ClaimGroup.values()) {
                        CustomGroup matchingGroup =
                                groups.stream()
                                        .filter(
                                                g ->
                                                        g.getName()
                                                                .equalsIgnoreCase(
                                                                        legacyGroup
                                                                                .getDisplayName()))
                                        .findFirst()
                                        .orElse(null);

                        if (matchingGroup != null) {
                            try {
                                int affected =
                                        database.executeUpdate(
                                                "UPDATE server_claim_members SET group_id = ? "
                                                        + "WHERE claim_id = ? AND group_name = ? AND group_id IS NULL",
                                                matchingGroup.getId(),
                                                claimId,
                                                legacyGroup.name());
                                migrated += affected;
                            } catch (SQLException e) {
                                // Log error but continue with other groups
                            }
                        }
                    }

                    return migrated;
                });
    }

    /** Check if a claim has custom groups set up. */
    public boolean hasCustomGroups(int claimId) {
        Integer count =
                database.query(
                        "SELECT COUNT(*) FROM server_claim_groups WHERE claim_id = ?",
                        rs -> {
                            if (rs.next()) {
                                return rs.getInt(1);
                            }
                            return 0;
                        },
                        claimId);
        return count != null && count > 0;
    }

    /** Maps a database row to a CustomGroup object. */
    private CustomGroup mapGroup(ResultSet rs) throws SQLException {
        CustomGroup group = new CustomGroup();
        group.setId(rs.getInt("id"));
        group.setClaimId(rs.getInt("claim_id"));
        group.setName(rs.getString("name"));
        group.setColorTag(rs.getString("color_tag"));

        String iconName = rs.getString("icon");
        try {
            group.setIcon(Material.valueOf(iconName));
        } catch (IllegalArgumentException e) {
            group.setIcon(Material.WHITE_STAINED_GLASS_PANE);
        }

        group.setPriority(rs.getInt("priority"));

        String permsData = rs.getString("permissions");
        group.setPermissions(CustomGroup.deserializePermissions(permsData));

        String mgmtPermsData = rs.getString("management_permissions");
        group.setManagementPermissions(CustomGroup.deserializeManagementPermissions(mgmtPermsData));

        group.setDefault(rs.getBoolean("is_default"));

        return group;
    }
}
