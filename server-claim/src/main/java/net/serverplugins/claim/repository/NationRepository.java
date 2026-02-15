package net.serverplugins.claim.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.serverplugins.api.database.Database;
import net.serverplugins.claim.models.Nation;
import net.serverplugins.claim.models.NationInvite;
import net.serverplugins.claim.models.NationMember;
import net.serverplugins.claim.models.NationRelation;
import net.serverplugins.claim.models.ProfileColor;

public class NationRepository {

    private final Database database;

    public NationRepository(Database database) {
        this.database = database;
    }

    // ==================== NATION CRUD ====================

    public Nation getNation(int nationId) {
        return database.query(
                "SELECT * FROM server_nations WHERE id = ?",
                rs -> {
                    if (rs.next()) {
                        return mapNation(rs);
                    }
                    return null;
                },
                nationId);
    }

    public Nation getNationByName(String name) {
        return database.query(
                "SELECT * FROM server_nations WHERE LOWER(name) = LOWER(?)",
                rs -> {
                    if (rs.next()) {
                        return mapNation(rs);
                    }
                    return null;
                },
                name);
    }

    public Nation getNationByTag(String tag) {
        return database.query(
                "SELECT * FROM server_nations WHERE UPPER(tag) = UPPER(?)",
                rs -> {
                    if (rs.next()) {
                        return mapNation(rs);
                    }
                    return null;
                },
                tag);
    }

    public Nation getNationByLeader(UUID leaderUuid) {
        return database.query(
                "SELECT * FROM server_nations WHERE leader_uuid = ?",
                rs -> {
                    if (rs.next()) {
                        return mapNation(rs);
                    }
                    return null;
                },
                leaderUuid.toString());
    }

    public Nation getNationByClaim(int claimId) {
        return database.query(
                "SELECT n.* FROM server_nations n "
                        + "INNER JOIN server_nation_members m ON n.id = m.nation_id "
                        + "WHERE m.claim_id = ?",
                rs -> {
                    if (rs.next()) {
                        return mapNation(rs);
                    }
                    return null;
                },
                claimId);
    }

    public List<Nation> getAllNations() {
        return database.query(
                "SELECT * FROM server_nations ORDER BY member_count DESC",
                rs -> {
                    List<Nation> nations = new ArrayList<>();
                    while (rs.next()) {
                        nations.add(mapNation(rs));
                    }
                    return nations;
                });
    }

    public void saveNation(Nation nation) {
        if (nation.getId() == 0) {
            database.execute(
                    "INSERT INTO server_nations (name, tag, leader_uuid, description, color, "
                            + "total_chunks, member_count, level, tax_rate) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    nation.getName(),
                    nation.getTag(),
                    nation.getLeaderUuid().toString(),
                    nation.getDescription(),
                    nation.getColor().name(),
                    nation.getTotalChunks(),
                    nation.getMemberCount(),
                    nation.getLevel(),
                    nation.getTaxRate());

            database.query(
                    "SELECT MAX(id) as id FROM server_nations WHERE name = ?",
                    rs -> {
                        if (rs.next()) nation.setId(rs.getInt("id"));
                        return null;
                    },
                    nation.getName());

            // Create nation bank
            database.execute(
                    "INSERT INTO server_nation_banks (nation_id, balance, tax_rate) VALUES (?, 0.00, 0.00)",
                    nation.getId());
        } else {
            database.execute(
                    "UPDATE server_nations SET name = ?, tag = ?, leader_uuid = ?, description = ?, "
                            + "color = ?, total_chunks = ?, member_count = ?, level = ?, tax_rate = ? WHERE id = ?",
                    nation.getName(),
                    nation.getTag(),
                    nation.getLeaderUuid().toString(),
                    nation.getDescription(),
                    nation.getColor().name(),
                    nation.getTotalChunks(),
                    nation.getMemberCount(),
                    nation.getLevel(),
                    nation.getTaxRate(),
                    nation.getId());
        }
    }

    public void deleteNation(int nationId) {
        database.execute(
                "DELETE FROM server_nation_bank_transactions WHERE nation_id = ?", nationId);
        database.execute("DELETE FROM server_nation_banks WHERE nation_id = ?", nationId);
        database.execute("DELETE FROM server_nation_invites WHERE nation_id = ?", nationId);
        database.execute("DELETE FROM server_nation_members WHERE nation_id = ?", nationId);
        database.execute(
                "DELETE FROM server_nation_relations WHERE nation_id = ? OR target_nation_id = ?",
                nationId,
                nationId);
        database.execute("DELETE FROM server_nations WHERE id = ?", nationId);
    }

    private Nation mapNation(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Nation(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("tag"),
                UUID.fromString(rs.getString("leader_uuid")),
                rs.getString("description"),
                ProfileColor.fromString(rs.getString("color")),
                rs.getTimestamp("founded_at").toInstant(),
                rs.getInt("total_chunks"),
                rs.getInt("member_count"),
                rs.getInt("level"),
                rs.getDouble("tax_rate"));
    }

    // ==================== MEMBER MANAGEMENT ====================

    public NationMember getMember(int claimId) {
        return database.query(
                "SELECT * FROM server_nation_members WHERE claim_id = ?",
                rs -> {
                    if (rs.next()) {
                        return new NationMember(
                                rs.getInt("id"),
                                rs.getInt("nation_id"),
                                rs.getInt("claim_id"),
                                NationMember.NationRole.fromString(rs.getString("role")),
                                rs.getTimestamp("joined_at").toInstant(),
                                rs.getDouble("contributed_amount"));
                    }
                    return null;
                },
                claimId);
    }

    public List<NationMember> getMembersByNation(int nationId) {
        return database.query(
                "SELECT * FROM server_nation_members WHERE nation_id = ? ORDER BY role, joined_at",
                rs -> {
                    List<NationMember> members = new ArrayList<>();
                    while (rs.next()) {
                        members.add(
                                new NationMember(
                                        rs.getInt("id"),
                                        rs.getInt("nation_id"),
                                        rs.getInt("claim_id"),
                                        NationMember.NationRole.fromString(rs.getString("role")),
                                        rs.getTimestamp("joined_at").toInstant(),
                                        rs.getDouble("contributed_amount")));
                    }
                    return members;
                },
                nationId);
    }

    public void saveMember(NationMember member) {
        database.execute(
                "INSERT INTO server_nation_members (nation_id, claim_id, role, contributed_amount) "
                        + "VALUES (?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE role = VALUES(role), contributed_amount = VALUES(contributed_amount)",
                member.getNationId(),
                member.getClaimId(),
                member.getRole().name(),
                member.getContributedAmount());
    }

    public void removeMember(int claimId) {
        database.execute("DELETE FROM server_nation_members WHERE claim_id = ?", claimId);
    }

    public void updateMemberRole(int claimId, NationMember.NationRole newRole) {
        database.execute(
                "UPDATE server_nation_members SET role = ? WHERE claim_id = ?",
                newRole.name(),
                claimId);
    }

    // ==================== INVITES (Player-Based) ====================

    /**
     * Create or update a nation invite for a player.
     *
     * @param nationId The nation sending the invite
     * @param playerUuid The player being invited
     * @param invitedBy The UUID of the player who sent the invite
     * @param expiresAt When the invite expires (null for no expiration)
     */
    public void createInvite(int nationId, UUID playerUuid, UUID invitedBy, Instant expiresAt) {
        database.execute(
                "INSERT INTO server_nation_invites (nation_id, player_uuid, inviter_uuid, expires_at) "
                        + "VALUES (?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE inviter_uuid = VALUES(inviter_uuid), expires_at = VALUES(expires_at), "
                        + "invited_at = CURRENT_TIMESTAMP",
                nationId,
                playerUuid.toString(),
                invitedBy.toString(),
                expiresAt != null ? Timestamp.from(expiresAt) : null);
    }

    /**
     * Check if a player has a valid invite to a nation.
     *
     * @param nationId The nation to check
     * @param playerUuid The player to check
     * @return true if valid invite exists
     */
    public boolean hasInvite(int nationId, UUID playerUuid) {
        return database.query(
                "SELECT 1 FROM server_nation_invites WHERE nation_id = ? AND player_uuid = ? "
                        + "AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)",
                rs -> rs.next(),
                nationId,
                playerUuid.toString());
    }

    /**
     * Get all pending invites for a player.
     *
     * @param playerUuid The player to check
     * @return List of NationInvite objects
     */
    public List<NationInvite> getPendingInvitesForPlayer(UUID playerUuid) {
        return database.query(
                "SELECT * FROM server_nation_invites WHERE player_uuid = ? "
                        + "AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)",
                rs -> {
                    List<NationInvite> invites = new ArrayList<>();
                    while (rs.next()) {
                        NationInvite invite =
                                new NationInvite(
                                        rs.getInt("id"),
                                        rs.getInt("nation_id"),
                                        UUID.fromString(rs.getString("player_uuid")),
                                        UUID.fromString(rs.getString("inviter_uuid")),
                                        rs.getTimestamp("invited_at").toInstant(),
                                        rs.getTimestamp("expires_at") != null
                                                ? rs.getTimestamp("expires_at").toInstant()
                                                : null);
                        invites.add(invite);
                    }
                    return invites;
                },
                playerUuid.toString());
    }

    /**
     * Get pending nation IDs for a claim (legacy support for existing code). This checks if the
     * claim owner has any pending invites.
     *
     * @param claimId The claim to check
     * @return List of nation IDs
     */
    @Deprecated
    public List<Integer> getPendingInvites(int claimId) {
        // This method is deprecated but maintained for backward compatibility
        return database.query(
                "SELECT DISTINCT ni.nation_id FROM server_nation_invites ni "
                        + "INNER JOIN server_claims c ON c.owner_uuid = ni.player_uuid "
                        + "WHERE c.id = ? AND (ni.expires_at IS NULL OR ni.expires_at > CURRENT_TIMESTAMP)",
                rs -> {
                    List<Integer> nationIds = new ArrayList<>();
                    while (rs.next()) {
                        nationIds.add(rs.getInt("nation_id"));
                    }
                    return nationIds;
                },
                claimId);
    }

    /**
     * Delete a specific invite.
     *
     * @param inviteId The ID of the invite to delete
     */
    public void deleteInvite(int inviteId) {
        database.execute("DELETE FROM server_nation_invites WHERE id = ?", inviteId);
    }

    /**
     * Delete an invite by nation and player.
     *
     * @param nationId The nation
     * @param playerUuid The player
     */
    public void deleteInviteByNationAndPlayer(int nationId, UUID playerUuid) {
        database.execute(
                "DELETE FROM server_nation_invites WHERE nation_id = ? AND player_uuid = ?",
                nationId,
                playerUuid.toString());
    }

    /** Delete all expired invites (cleanup task). */
    public void deleteExpiredInvites() {
        database.execute(
                "DELETE FROM server_nation_invites WHERE expires_at IS NOT NULL AND expires_at < CURRENT_TIMESTAMP");
    }

    // ==================== RELATIONS ====================

    public NationRelation getRelation(int nationId, int targetNationId) {
        return database.query(
                "SELECT * FROM server_nation_relations WHERE nation_id = ? AND target_nation_id = ?",
                rs -> {
                    if (rs.next()) {
                        return new NationRelation(
                                rs.getInt("nation_id"),
                                rs.getInt("target_nation_id"),
                                NationRelation.RelationType.fromString(
                                        rs.getString("relation_type")),
                                rs.getTimestamp("established_at").toInstant());
                    }
                    return null;
                },
                nationId,
                targetNationId);
    }

    public List<NationRelation> getRelationsFor(int nationId) {
        return database.query(
                "SELECT * FROM server_nation_relations WHERE nation_id = ?",
                rs -> {
                    List<NationRelation> relations = new ArrayList<>();
                    while (rs.next()) {
                        relations.add(
                                new NationRelation(
                                        rs.getInt("nation_id"),
                                        rs.getInt("target_nation_id"),
                                        NationRelation.RelationType.fromString(
                                                rs.getString("relation_type")),
                                        rs.getTimestamp("established_at").toInstant()));
                    }
                    return relations;
                },
                nationId);
    }

    public void setRelation(int nationId, int targetNationId, NationRelation.RelationType type) {
        database.execute(
                "INSERT INTO server_nation_relations (nation_id, target_nation_id, relation_type) "
                        + "VALUES (?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE relation_type = VALUES(relation_type), established_at = CURRENT_TIMESTAMP",
                nationId,
                targetNationId,
                type.name());
    }

    public void setMutualRelation(int nationId1, int nationId2, NationRelation.RelationType type) {
        setRelation(nationId1, nationId2, type);
        setRelation(nationId2, nationId1, type);
    }

    // ==================== NATION BANK ====================

    public double getNationBalance(int nationId) {
        return database.query(
                "SELECT balance FROM server_nation_banks WHERE nation_id = ?",
                rs -> rs.next() ? rs.getDouble("balance") : 0.0,
                nationId);
    }

    public void updateNationBalance(int nationId, double newBalance) {
        database.execute(
                "UPDATE server_nation_banks SET balance = ? WHERE nation_id = ?",
                newBalance,
                nationId);
    }

    public double getNationTaxRate(int nationId) {
        return database.query(
                "SELECT tax_rate FROM server_nation_banks WHERE nation_id = ?",
                rs -> rs.next() ? rs.getDouble("tax_rate") : 0.0,
                nationId);
    }

    public void setNationTaxRate(int nationId, double taxRate) {
        database.execute(
                "UPDATE server_nation_banks SET tax_rate = ? WHERE nation_id = ?",
                taxRate,
                nationId);
    }

    public void recordNationTransaction(
            int nationId,
            UUID playerUuid,
            Integer claimId,
            String type,
            double amount,
            double balanceAfter,
            String description) {
        database.execute(
                "INSERT INTO server_nation_bank_transactions "
                        + "(nation_id, player_uuid, claim_id, transaction_type, amount, balance_after, description) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                nationId,
                playerUuid != null ? playerUuid.toString() : null,
                claimId,
                type,
                amount,
                balanceAfter,
                description);
    }
}
