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

class ContactSnapshotV2MigrationTest {
    private static final String MIGRATION =
            "db/migration/V5__create_contact_snapshot_v2.sql";
    private static final String C3_MIGRATION =
            "db/migration/V6__add_contact_snapshot_idempotency.sql";

    @Test
    void migrationCreatesV2SchemaOnCleanDatabase() throws Exception {
        try (Connection connection = openDatabase("v2-clean")) {
            executeMigration(connection);
            executeMigration(connection, C3_MIGRATION);

            assertThat(tableExists(connection, "CONTACTS_V2")).isTrue();
            assertThat(tableExists(connection, "CONTACT_SYNC_STATE_V2")).isTrue();
            assertThat(tableExists(connection, "CONTACT_SNAPSHOT_OWNER_LOCK_V2")).isTrue();
            assertThat(tableExists(connection, "CONTACT_SNAPSHOT_REQUEST_V2")).isTrue();
            assertThat(indexExists(connection, "CONTACTS_V2", "IDX_CONTACTS_V2_OWNER")).isTrue();
            assertThat(indexExists(connection, "CONTACTS_V2", "IDX_CONTACTS_V2_TARGET")).isTrue();
            assertThat(indexExists(connection, "CONTACT_SNAPSHOT_REQUEST_V2",
                    "IDX_SNAPSHOT_REQUEST_V2_OWNER_CREATED")).isTrue();
            assertThat(indexExists(connection, "CONTACT_SNAPSHOT_REQUEST_V2",
                    "IDX_SNAPSHOT_REQUEST_V2_OWNER_REVISION")).isTrue();
        }
    }

    @Test
    void migrationPreservesExistingV1DataAndEnforcesV2Uniqueness() throws Exception {
        try (Connection connection = openDatabase("v2-upgrade");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE contacts (contact_id VARCHAR(36) PRIMARY KEY,"
                    + " owner_user_id VARCHAR(36) NOT NULL,"
                    + " target_global_phone_hash VARCHAR(64) NOT NULL,"
                    + " saved_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL)");
            statement.execute("INSERT INTO contacts VALUES"
                    + " ('legacy-id','59','legacy-hash',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");

            executeMigration(connection);
            statement.execute("INSERT INTO contacts_v2"
                    + " (contact_id,owner_user_id,target_global_phone_hash)"
                    + " VALUES ('existing-v2','59','existing-v2-hash')");
            statement.execute("INSERT INTO contact_sync_state_v2"
                    + " (owner_user_id,current_revision,last_snapshot_id)"
                    + " VALUES ('59',7,'550e8400-e29b-41d4-a716-446655440000')");

            executeMigration(connection, C3_MIGRATION);

            assertThat(count(connection, "contacts")).isOne();
            assertThat(count(connection, "contacts_v2")).isOne();
            assertThat(singleLong(connection,
                    "SELECT current_revision FROM contact_sync_state_v2 WHERE owner_user_id='59'"))
                    .isEqualTo(7);
            statement.execute("INSERT INTO contacts_v2"
                    + " (contact_id,owner_user_id,target_global_phone_hash)"
                    + " VALUES ('v2-id-1','59','target-hash')");
            assertThatThrownBy(() -> statement.execute("INSERT INTO contacts_v2"
                    + " (contact_id,owner_user_id,target_global_phone_hash)"
                    + " VALUES ('v2-id-2','59','target-hash')"))
                    .isInstanceOf(SQLException.class);
            statement.execute("INSERT INTO contact_snapshot_request_v2"
                    + " (owner_user_id,snapshot_id,payload_digest,base_revision,"
                    + " committed_revision,status) VALUES"
                    + " ('59','550e8400-e29b-41d4-a716-446655440001',"
                    + " 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',"
                    + " 7,8,'COMMITTED')");
            assertThatThrownBy(() -> statement.execute(
                    "INSERT INTO contact_snapshot_request_v2"
                            + " (owner_user_id,snapshot_id,payload_digest,base_revision,"
                            + " committed_revision,status) VALUES"
                            + " ('59','550e8400-e29b-41d4-a716-446655440001',"
                            + " 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',"
                            + " 7,8,'COMMITTED')"))
                    .isInstanceOf(SQLException.class);
        }
    }

    private Connection openDatabase(String name) throws SQLException {
        return DriverManager.getConnection(
                "jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    private void executeMigration(Connection connection) throws Exception {
        executeMigration(connection, MIGRATION);
    }

    private void executeMigration(Connection connection, String migration) throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(migration)) {
            assertThat(stream).isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("(?m)^--.*$", "");
            try (Statement statement = connection.createStatement()) {
                for (String command : sql.split(";")) {
                    if (!command.trim().isEmpty()) {
                        statement.execute(command);
                    }
                }
            }
        }
    }

    private boolean tableExists(Connection connection, String table) throws SQLException {
        try (ResultSet result = connection.getMetaData()
                .getTables(null, null, table, new String[]{"TABLE"})) {
            return result.next();
        }
    }

    private boolean indexExists(Connection connection, String table, String index)
            throws SQLException {
        try (ResultSet result = connection.getMetaData()
                .getIndexInfo(null, null, table, false, false)) {
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

    private long singleLong(Connection connection, String query) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(query)) {
            result.next();
            return result.getLong(1);
        }
    }
}
