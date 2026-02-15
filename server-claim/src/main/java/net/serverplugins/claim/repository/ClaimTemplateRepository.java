package net.serverplugins.claim.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.serverplugins.api.database.Database;
import net.serverplugins.claim.models.ClaimTemplate;

/**
 * Repository for managing claim templates in the database. Handles CRUD operations for claim
 * configuration templates.
 */
public class ClaimTemplateRepository {

    private final Database database;

    public ClaimTemplateRepository(Database database) {
        this.database = database;
    }

    /** Saves a template to the database (insert or update). */
    public void saveTemplate(ClaimTemplate template) {
        String sql =
                "INSERT INTO server_claim_templates "
                        + "(owner_uuid, template_name, description, pvp_enabled, fire_spread, mob_spawning, "
                        + "explosion_damage, piston_push, fluid_flow, leaf_decay, crop_growth, "
                        + "group_permissions, custom_groups_csv, times_used, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
                        + "ON DUPLICATE KEY UPDATE "
                        + "description = VALUES(description), "
                        + "pvp_enabled = VALUES(pvp_enabled), "
                        + "fire_spread = VALUES(fire_spread), "
                        + "mob_spawning = VALUES(mob_spawning), "
                        + "explosion_damage = VALUES(explosion_damage), "
                        + "piston_push = VALUES(piston_push), "
                        + "fluid_flow = VALUES(fluid_flow), "
                        + "leaf_decay = VALUES(leaf_decay), "
                        + "crop_growth = VALUES(crop_growth), "
                        + "group_permissions = VALUES(group_permissions), "
                        + "custom_groups_csv = VALUES(custom_groups_csv), "
                        + "times_used = VALUES(times_used), "
                        + "updated_at = CURRENT_TIMESTAMP";

        database.execute(
                sql,
                template.getOwnerUuid().toString(),
                template.getTemplateName(),
                template.getDescription(),
                template.isPvpEnabled(),
                template.isFireSpread(),
                template.isMobSpawning(),
                template.isExplosionDamage(),
                template.isPistonPush(),
                template.isFluidFlow(),
                template.isLeafDecay(),
                template.isCropGrowth(),
                template.getGroupPermissions(),
                template.getCustomGroupsCsv(),
                template.getTimesUsed());
    }

    /** Loads a specific template by owner and name. */
    public ClaimTemplate getTemplate(UUID ownerUuid, String templateName) {
        String sql =
                "SELECT * FROM server_claim_templates WHERE owner_uuid = ? AND template_name = ?";

        return database.query(
                sql,
                rs -> {
                    if (rs.next()) {
                        return mapTemplate(rs);
                    }
                    return null;
                },
                ownerUuid.toString(),
                templateName);
    }

    /** Loads a template by its ID. */
    public ClaimTemplate getTemplateById(int id) {
        String sql = "SELECT * FROM server_claim_templates WHERE id = ?";

        return database.query(
                sql,
                rs -> {
                    if (rs.next()) {
                        return mapTemplate(rs);
                    }
                    return null;
                },
                id);
    }

    /** Gets all templates owned by a player. */
    public List<ClaimTemplate> getPlayerTemplates(UUID ownerUuid) {
        String sql =
                "SELECT * FROM server_claim_templates WHERE owner_uuid = ? ORDER BY created_at DESC";

        return database.query(
                sql,
                rs -> {
                    List<ClaimTemplate> templates = new ArrayList<>();
                    while (rs.next()) {
                        templates.add(mapTemplate(rs));
                    }
                    return templates;
                },
                ownerUuid.toString());
    }

    /** Deletes a template. */
    public boolean deleteTemplate(UUID ownerUuid, String templateName) {
        String sql =
                "DELETE FROM server_claim_templates WHERE owner_uuid = ? AND template_name = ?";
        try {
            int rowsAffected = database.executeUpdate(sql, ownerUuid.toString(), templateName);
            return rowsAffected > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete template", e);
        }
    }

    /** Renames a template. */
    public boolean renameTemplate(UUID ownerUuid, String oldName, String newName) {
        String sql =
                "UPDATE server_claim_templates SET template_name = ?, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE owner_uuid = ? AND template_name = ?";
        try {
            int rowsAffected = database.executeUpdate(sql, newName, ownerUuid.toString(), oldName);
            return rowsAffected > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to rename template", e);
        }
    }

    /** Increments the usage counter for a template. */
    public void incrementUsageCount(int templateId) {
        String sql =
                "UPDATE server_claim_templates SET times_used = times_used + 1, "
                        + "updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        database.execute(sql, templateId);
    }

    /** Checks if a template name exists for a player. */
    public boolean templateExists(UUID ownerUuid, String templateName) {
        String sql =
                "SELECT COUNT(*) FROM server_claim_templates WHERE owner_uuid = ? AND template_name = ?";

        return database.query(
                sql,
                rs -> {
                    if (rs.next()) {
                        return rs.getInt(1) > 0;
                    }
                    return false;
                },
                ownerUuid.toString(),
                templateName);
    }

    /** Gets the count of templates owned by a player. */
    public int getTemplateCount(UUID ownerUuid) {
        String sql = "SELECT COUNT(*) FROM server_claim_templates WHERE owner_uuid = ?";

        return database.query(
                sql,
                rs -> {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                    return 0;
                },
                ownerUuid.toString());
    }

    /** Maps a ResultSet row to a ClaimTemplate object. */
    private ClaimTemplate mapTemplate(ResultSet rs) throws SQLException {
        ClaimTemplate template = new ClaimTemplate();
        template.setId(rs.getInt("id"));
        template.setOwnerUuid(UUID.fromString(rs.getString("owner_uuid")));
        template.setTemplateName(rs.getString("template_name"));
        template.setDescription(rs.getString("description"));
        template.setPvpEnabled(rs.getBoolean("pvp_enabled"));
        template.setFireSpread(rs.getBoolean("fire_spread"));
        template.setMobSpawning(rs.getBoolean("mob_spawning"));
        template.setExplosionDamage(rs.getBoolean("explosion_damage"));
        template.setPistonPush(rs.getBoolean("piston_push"));
        template.setFluidFlow(rs.getBoolean("fluid_flow"));
        template.setLeafDecay(rs.getBoolean("leaf_decay"));
        template.setCropGrowth(rs.getBoolean("crop_growth"));
        template.setGroupPermissions(rs.getString("group_permissions"));
        template.setCustomGroupsCsv(rs.getString("custom_groups_csv"));
        template.setTimesUsed(rs.getInt("times_used"));
        template.setCreatedAt(rs.getTimestamp("created_at"));
        template.setUpdatedAt(rs.getTimestamp("updated_at"));
        return template;
    }
}
