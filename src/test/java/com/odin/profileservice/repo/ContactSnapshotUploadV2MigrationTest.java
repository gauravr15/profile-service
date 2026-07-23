package com.odin.profileservice.repo;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContactSnapshotUploadV2MigrationTest {
    private static final String V5 = "db/migration/V5__create_contact_snapshot_v2.sql";
    private static final String V6 = "db/migration/V6__add_contact_snapshot_idempotency.sql";
    private static final String V7 = "db/migration/V7__add_contact_snapshot_upload_sessions.sql";

    @Test
    void cleanMigrationCreatesConstrainedAndIndexedUploadSchema() throws Exception {
        try (Connection connection = openDatabase("c4-clean")) {
            execute(connection, V5);
            execute(connection, V6);
            execute(connection, V7);

            assertThat(tableExists(connection, "CONTACT_SNAPSHOT_UPLOAD_SESSION_V2")).isTrue();
            assertThat(tableExists(connection, "CONTACT_SNAPSHOT_UPLOAD_CHUNK_V2")).isTrue();
            assertThat(tableExists(connection, "CONTACT_SNAPSHOT_UPLOAD_TARGET_V2")).isTrue();
            assertThat(indexExists(connection, "CONTACT_SNAPSHOT_UPLOAD_SESSION_V2",
                    "IDX_SNAPSHOT_UPLOAD_V2_OWNER_STATE")).isTrue();
            assertThat(indexExists(connection, "CONTACT_SNAPSHOT_UPLOAD_SESSION_V2",
                    "IDX_SNAPSHOT_UPLOAD_V2_STATE_EXPIRY")).isTrue();

            insertSession(connection, "session-a", "59", "snapshot-a");
            assertThatThrownBy(() -> insertSession(
                    connection, "session-b", "59", "snapshot-a"))
                    .isInstanceOf(SQLException.class);
            insertChunk(connection, "session-a", 0);
            assertThatThrownBy(() -> insertChunk(connection, "session-a", 0))
                    .isInstanceOf(SQLException.class);
            insertTarget(connection, "session-a", "target-a");
            assertThatThrownBy(() -> insertTarget(connection, "session-a", "target-a"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void v6UpgradePreservesExistingRelationshipsRevisionAndReplayMetadata() throws Exception {
        try (Connection connection = openDatabase("c4-upgrade")) {
            execute(connection, V5);
            execute(connection, V6);
            try (Statement statement = connection.createStatement()) {
                statement.execute("INSERT INTO contacts_v2 "
                        + "(contact_id,owner_user_id,target_global_phone_hash) "
                        + "VALUES ('contact-a','59','target-a')");
                statement.execute("INSERT INTO contact_sync_state_v2 "
                        + "(owner_user_id,current_revision,last_snapshot_id) "
                        + "VALUES ('59',7,'snapshot-old')");
                statement.execute("INSERT INTO contact_snapshot_request_v2 "
                        + "(owner_user_id,snapshot_id,payload_digest,base_revision,"
                        + "committed_revision,status,created_at,updated_at) VALUES "
                        + "('59','snapshot-old',REPEAT('a',64),6,7,'COMMITTED',"
                        + "CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            }

            execute(connection, V7);

            assertThat(count(connection, "contacts_v2")).isOne();
            assertThat(value(connection,
                    "SELECT current_revision FROM contact_sync_state_v2 "
                            + "WHERE owner_user_id='59'")).isEqualTo("7");
            assertThat(count(connection, "contact_snapshot_request_v2")).isOne();
        }
    }

    private Connection openDatabase(String name) throws SQLException {
        return DriverManager.getConnection(
                "jdbc:h2:mem:" + name
                        + ";MODE=MySQL;DATABASE_TO_UPPER=true;DB_CLOSE_DELAY=-1");
    }

    private void execute(Connection connection, String resource) throws Exception {
        String sql;
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(stream).isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String statement : sql.split(";")) {
            String trimmed = statement.trim();
            if (!trimmed.isEmpty()) {
                try (Statement jdbc = connection.createStatement()) {
                    jdbc.execute(trimmed);
                }
            }
        }
    }

    private void insertSession(
            Connection connection,
            String sessionId,
            String owner,
            String snapshot) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO contact_snapshot_upload_session_v2 "
                    + "(session_id,owner_user_id,snapshot_id,base_revision,country_code,"
                    + "declared_total_contacts,declared_total_chunks,state,expires_at) VALUES "
                    + "('" + sessionId + "','" + owner + "','" + snapshot
                    + "',0,'IN',1,1,'OPEN',CURRENT_TIMESTAMP)");
        }
    }

    private void insertChunk(
            Connection connection,
            String sessionId,
            int index) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO contact_snapshot_upload_chunk_v2 "
                    + "(session_id,chunk_index,payload_digest,submitted_count,canonical_count) "
                    + "VALUES ('" + sessionId + "'," + index + ",REPEAT('b',64),1,1)");
        }
    }

    private void insertTarget(
            Connection connection,
            String sessionId,
            String target) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO contact_snapshot_upload_target_v2 "
                    + "(session_id,target_global_phone_hash,registered) VALUES "
                    + "('" + sessionId + "','" + target + "',FALSE)");
        }
    }

    private boolean tableExists(Connection connection, String table) throws SQLException {
        try (ResultSet result = connection.getMetaData().getTables(
                null, null, table, new String[]{"TABLE"})) {
            return result.next();
        }
    }

    private boolean indexExists(
            Connection connection,
            String table,
            String index) throws SQLException {
        try (ResultSet result = connection.getMetaData().getIndexInfo(
                null, null, table, false, false)) {
            while (result.next()) {
                if (index.equalsIgnoreCase(result.getString("INDEX_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private long count(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getLong(1);
        }
    }

    private String value(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }
}
