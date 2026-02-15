package net.serverplugins.claim.repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.serverplugins.api.database.Database;
import net.serverplugins.claim.models.War;
import net.serverplugins.claim.models.WarCapture;
import net.serverplugins.claim.models.WarShield;
import net.serverplugins.claim.models.WarTribute;

public class WarRepository {

    private final Database database;

    public WarRepository(Database database) {
        this.database = database;
    }

    // ==================== WAR CRUD ====================

    public War getWar(int warId) {
        return database.query(
                "SELECT * FROM server_wars WHERE id = ?",
                rs -> {
                    if (rs.next()) {
                        return mapWar(rs);
                    }
                    return null;
                },
                warId);
    }

    public List<War> getActiveWars() {
        return database.query(
                "SELECT * FROM server_wars WHERE war_state IN ('DECLARED', 'ACTIVE', 'CEASEFIRE') "
                        + "ORDER BY declared_at DESC",
                rs -> {
                    List<War> wars = new ArrayList<>();
                    while (rs.next()) {
                        wars.add(mapWar(rs));
                    }
                    return wars;
                });
    }

    public List<War> getWarsInvolvingNation(int nationId) {
        return database.query(
                "SELECT * FROM server_wars WHERE attacker_nation_id = ? OR defender_nation_id = ? "
                        + "ORDER BY declared_at DESC",
                rs -> {
                    List<War> wars = new ArrayList<>();
                    while (rs.next()) {
                        wars.add(mapWar(rs));
                    }
                    return wars;
                },
                nationId,
                nationId);
    }

    public List<War> getActiveWarsForNation(int nationId) {
        return database.query(
                "SELECT * FROM server_wars WHERE (attacker_nation_id = ? OR defender_nation_id = ?) "
                        + "AND war_state IN ('DECLARED', 'ACTIVE', 'CEASEFIRE') "
                        + "ORDER BY declared_at DESC",
                rs -> {
                    List<War> wars = new ArrayList<>();
                    while (rs.next()) {
                        wars.add(mapWar(rs));
                    }
                    return wars;
                },
                nationId,
                nationId);
    }

    public War getActiveWarBetween(int nationId1, int nationId2) {
        return database.query(
                "SELECT * FROM server_wars "
                        + "WHERE ((attacker_nation_id = ? AND defender_nation_id = ?) "
                        + "    OR (attacker_nation_id = ? AND defender_nation_id = ?)) "
                        + "AND war_state IN ('DECLARED', 'ACTIVE', 'CEASEFIRE')",
                rs -> {
                    if (rs.next()) {
                        return mapWar(rs);
                    }
                    return null;
                },
                nationId1,
                nationId2,
                nationId2,
                nationId1);
    }

    public void saveWar(War war) {
        if (war.getId() == 0) {
            database.execute(
                    "INSERT INTO server_wars (attacker_nation_id, attacker_claim_id, defender_nation_id, "
                            + "defender_claim_id, war_state, declaration_reason, active_at, ended_at, outcome) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    war.getAttackerNationId(),
                    war.getAttackerClaimId(),
                    war.getDefenderNationId(),
                    war.getDefenderClaimId(),
                    war.getWarState().name(),
                    war.getDeclarationReason(),
                    war.getActiveAt() != null ? Timestamp.from(war.getActiveAt()) : null,
                    war.getEndedAt() != null ? Timestamp.from(war.getEndedAt()) : null,
                    war.getOutcome() != null ? war.getOutcome().name() : null);

            database.query(
                    "SELECT MAX(id) as id FROM server_wars",
                    rs -> {
                        if (rs.next()) war.setId(rs.getInt("id"));
                        return null;
                    });
        } else {
            database.execute(
                    "UPDATE server_wars SET war_state = ?, active_at = ?, ended_at = ?, outcome = ? WHERE id = ?",
                    war.getWarState().name(),
                    war.getActiveAt() != null ? Timestamp.from(war.getActiveAt()) : null,
                    war.getEndedAt() != null ? Timestamp.from(war.getEndedAt()) : null,
                    war.getOutcome() != null ? war.getOutcome().name() : null,
                    war.getId());
        }
    }

    private War mapWar(java.sql.ResultSet rs) throws java.sql.SQLException {
        War war = new War();
        war.setId(rs.getInt("id"));

        int attackerNationId = rs.getInt("attacker_nation_id");
        war.setAttackerNationId(rs.wasNull() ? null : attackerNationId);

        int attackerClaimId = rs.getInt("attacker_claim_id");
        war.setAttackerClaimId(rs.wasNull() ? null : attackerClaimId);

        int defenderNationId = rs.getInt("defender_nation_id");
        war.setDefenderNationId(rs.wasNull() ? null : defenderNationId);

        int defenderClaimId = rs.getInt("defender_claim_id");
        war.setDefenderClaimId(rs.wasNull() ? null : defenderClaimId);

        war.setWarState(War.WarState.fromString(rs.getString("war_state")));
        war.setDeclarationReason(rs.getString("declaration_reason"));
        war.setDeclaredAt(rs.getTimestamp("declared_at").toInstant());

        Timestamp activeAt = rs.getTimestamp("active_at");
        war.setActiveAt(activeAt != null ? activeAt.toInstant() : null);

        Timestamp endedAt = rs.getTimestamp("ended_at");
        war.setEndedAt(endedAt != null ? endedAt.toInstant() : null);

        String outcome = rs.getString("outcome");
        war.setOutcome(outcome != null ? War.WarOutcome.fromString(outcome) : null);

        return war;
    }

    // ==================== CAPTURE PROGRESS ====================

    public WarCapture getCapture(int warId, String world, int chunkX, int chunkZ) {
        return database.query(
                "SELECT * FROM server_war_captures WHERE war_id = ? AND chunk_world = ? "
                        + "AND chunk_x = ? AND chunk_z = ?",
                rs -> {
                    if (rs.next()) {
                        return mapCapture(rs);
                    }
                    return null;
                },
                warId,
                world,
                chunkX,
                chunkZ);
    }

    public List<WarCapture> getCapturesForWar(int warId) {
        return database.query(
                "SELECT * FROM server_war_captures WHERE war_id = ?",
                rs -> {
                    List<WarCapture> captures = new ArrayList<>();
                    while (rs.next()) {
                        captures.add(mapCapture(rs));
                    }
                    return captures;
                },
                warId);
    }

    public List<WarCapture> getActiveCapturesForWar(int warId) {
        return database.query(
                "SELECT * FROM server_war_captures WHERE war_id = ? AND captured_at IS NULL "
                        + "AND capture_progress > 0",
                rs -> {
                    List<WarCapture> captures = new ArrayList<>();
                    while (rs.next()) {
                        captures.add(mapCapture(rs));
                    }
                    return captures;
                },
                warId);
    }

    public void saveCapture(WarCapture capture) {
        database.execute(
                "INSERT INTO server_war_captures (war_id, chunk_world, chunk_x, chunk_z, "
                        + "capturing_nation_id, capture_progress, last_progress_update, captured_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE capturing_nation_id = VALUES(capturing_nation_id), "
                        + "capture_progress = VALUES(capture_progress), last_progress_update = VALUES(last_progress_update), "
                        + "captured_at = VALUES(captured_at)",
                capture.getWarId(),
                capture.getChunkWorld(),
                capture.getChunkX(),
                capture.getChunkZ(),
                capture.getCapturingNationId(),
                capture.getCaptureProgress(),
                Timestamp.from(capture.getLastProgressUpdate()),
                capture.getCapturedAt() != null ? Timestamp.from(capture.getCapturedAt()) : null);
    }

    public void deleteCapture(int warId, String world, int chunkX, int chunkZ) {
        database.execute(
                "DELETE FROM server_war_captures WHERE war_id = ? AND chunk_world = ? "
                        + "AND chunk_x = ? AND chunk_z = ?",
                warId,
                world,
                chunkX,
                chunkZ);
    }

    private WarCapture mapCapture(java.sql.ResultSet rs) throws java.sql.SQLException {
        int capturingNationId = rs.getInt("capturing_nation_id");
        Timestamp capturedAt = rs.getTimestamp("captured_at");

        return new WarCapture(
                rs.getInt("id"),
                rs.getInt("war_id"),
                rs.getString("chunk_world"),
                rs.getInt("chunk_x"),
                rs.getInt("chunk_z"),
                rs.wasNull() ? null : capturingNationId,
                rs.getInt("capture_progress"),
                rs.getTimestamp("last_progress_update").toInstant(),
                capturedAt != null ? capturedAt.toInstant() : null);
    }

    // ==================== TRIBUTES ====================

    public WarTribute getTribute(int tributeId) {
        return database.query(
                "SELECT * FROM server_war_tributes WHERE id = ?",
                rs -> {
                    if (rs.next()) {
                        return mapTribute(rs);
                    }
                    return null;
                },
                tributeId);
    }

    public List<WarTribute> getPendingTributes(int warId) {
        return database.query(
                "SELECT * FROM server_war_tributes WHERE war_id = ? AND response = 'PENDING' "
                        + "ORDER BY created_at DESC",
                rs -> {
                    List<WarTribute> tributes = new ArrayList<>();
                    while (rs.next()) {
                        tributes.add(mapTribute(rs));
                    }
                    return tributes;
                },
                warId);
    }

    public void saveTribute(WarTribute tribute) {
        if (tribute.getId() == 0) {
            database.execute(
                    "INSERT INTO server_war_tributes (war_id, offering_side, tribute_type, "
                            + "money_amount, message, response) VALUES (?, ?, ?, ?, ?, ?)",
                    tribute.getWarId(),
                    tribute.getOfferingSide().name(),
                    tribute.getTributeType().name(),
                    tribute.getMoneyAmount(),
                    tribute.getMessage(),
                    tribute.getResponse().name());

            database.query(
                    "SELECT MAX(id) as id FROM server_war_tributes WHERE war_id = ?",
                    rs -> {
                        if (rs.next()) tribute.setId(rs.getInt("id"));
                        return null;
                    },
                    tribute.getWarId());
        } else {
            database.execute(
                    "UPDATE server_war_tributes SET response = ?, responded_at = ? WHERE id = ?",
                    tribute.getResponse().name(),
                    tribute.getRespondedAt() != null
                            ? Timestamp.from(tribute.getRespondedAt())
                            : null,
                    tribute.getId());
        }
    }

    private WarTribute mapTribute(java.sql.ResultSet rs) throws java.sql.SQLException {
        WarTribute tribute = new WarTribute();
        tribute.setId(rs.getInt("id"));
        tribute.setWarId(rs.getInt("war_id"));
        tribute.setOfferingSide(WarTribute.OfferingSide.valueOf(rs.getString("offering_side")));
        tribute.setTributeType(WarTribute.TributeType.fromString(rs.getString("tribute_type")));
        tribute.setMoneyAmount(rs.getDouble("money_amount"));
        tribute.setMessage(rs.getString("message"));
        tribute.setResponse(WarTribute.Response.fromString(rs.getString("response")));
        tribute.setCreatedAt(rs.getTimestamp("created_at").toInstant());

        Timestamp respondedAt = rs.getTimestamp("responded_at");
        tribute.setRespondedAt(respondedAt != null ? respondedAt.toInstant() : null);

        return tribute;
    }

    // ==================== WAR SHIELDS ====================

    public WarShield getActiveShieldForNation(int nationId) {
        return database.query(
                "SELECT * FROM server_war_shields WHERE nation_id = ? "
                        + "AND shield_end > CURRENT_TIMESTAMP ORDER BY shield_end DESC LIMIT 1",
                rs -> {
                    if (rs.next()) {
                        return mapShield(rs);
                    }
                    return null;
                },
                nationId);
    }

    public WarShield getActiveShieldForClaim(int claimId) {
        return database.query(
                "SELECT * FROM server_war_shields WHERE claim_id = ? "
                        + "AND shield_end > CURRENT_TIMESTAMP ORDER BY shield_end DESC LIMIT 1",
                rs -> {
                    if (rs.next()) {
                        return mapShield(rs);
                    }
                    return null;
                },
                claimId);
    }

    public boolean hasActiveShield(int nationId) {
        return getActiveShieldForNation(nationId) != null;
    }

    public void createShield(WarShield shield) {
        database.execute(
                "INSERT INTO server_war_shields (nation_id, claim_id, shield_end, reason) "
                        + "VALUES (?, ?, ?, ?)",
                shield.getNationId(),
                shield.getClaimId(),
                Timestamp.from(shield.getShieldEnd()),
                shield.getReason());
    }

    public void deleteExpiredShields() {
        database.execute("DELETE FROM server_war_shields WHERE shield_end < CURRENT_TIMESTAMP");
    }

    private WarShield mapShield(java.sql.ResultSet rs) throws java.sql.SQLException {
        int nationId = rs.getInt("nation_id");
        int claimId = rs.getInt("claim_id");

        return new WarShield(
                rs.getInt("id"),
                rs.wasNull() ? null : nationId,
                rs.wasNull() ? null : claimId,
                rs.getTimestamp("shield_end").toInstant(),
                rs.getString("reason"),
                rs.getTimestamp("created_at").toInstant());
    }

    // ==================== WAR EVENTS ====================

    public void recordEvent(int warId, String eventType, UUID playerUuid, String details) {
        database.execute(
                "INSERT INTO server_war_events (war_id, event_type, player_uuid, details) "
                        + "VALUES (?, ?, ?, ?)",
                warId,
                eventType,
                playerUuid != null ? playerUuid.toString() : null,
                details);
    }

    public List<String> getRecentEvents(int warId, int limit) {
        return database.query(
                "SELECT details FROM server_war_events WHERE war_id = ? "
                        + "ORDER BY occurred_at DESC LIMIT ?",
                rs -> {
                    List<String> events = new ArrayList<>();
                    while (rs.next()) {
                        events.add(rs.getString("details"));
                    }
                    return events;
                },
                warId,
                limit);
    }
}
