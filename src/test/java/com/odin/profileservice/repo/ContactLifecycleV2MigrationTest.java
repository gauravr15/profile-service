package com.odin.profileservice.repo;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class ContactLifecycleV2MigrationTest {
    private static final String[] MIGRATIONS = {
            "db/migration/V5__create_contact_snapshot_v2.sql",
            "db/migration/V6__add_contact_snapshot_idempotency.sql",
            "db/migration/V7__add_contact_snapshot_upload_sessions.sql",
            "db/migration/V8__add_contact_lifecycle_epoch.sql"
    };

    @Test
    void cleanAndPopulatedC4SchemasUpgradeWithoutChangingExistingRows() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:c6-upgrade;MODE=MySQL;DATABASE_TO_UPPER=true")) {
            execute(connection, MIGRATIONS[0]);
            execute(connection, MIGRATIONS[1]);
            execute(connection, MIGRATIONS[2]);
            try (Statement statement = connection.createStatement()) {
                statement.execute("INSERT INTO contacts_v2 "
                        + "(contact_id,owner_user_id,target_global_phone_hash) "
                        + "VALUES ('contact-a','59','target-a')");
                statement.execute("INSERT INTO contact_snapshot_owner_lock_v2 "
                        + "(owner_user_id) VALUES ('59')");
                statement.execute("INSERT INTO contact_snapshot_request_v2 "
                        + "(owner_user_id,snapshot_id,payload_digest,base_revision,"
                        + "committed_revision,status) VALUES "
                        + "('59','snapshot-a',REPEAT('a',64),0,1,'COMMITTED')");
                statement.execute("INSERT INTO contact_snapshot_upload_session_v2 "
                        + "(session_id,owner_user_id,snapshot_id,base_revision,country_code,"
                        + "declared_total_contacts,declared_total_chunks,state,expires_at) "
                        + "VALUES ('session-a','59','snapshot-b',1,'IN',1,1,'OPEN',"
                        + "CURRENT_TIMESTAMP)");
            }

            execute(connection, MIGRATIONS[3]);

            assertThat(value(connection, "SELECT contact_lifecycle_epoch FROM "
                    + "contact_snapshot_owner_lock_v2 WHERE owner_user_id='59'")).isZero();
            assertThat(value(connection, "SELECT contact_lifecycle_epoch FROM "
                    + "contact_snapshot_request_v2 WHERE owner_user_id='59'")).isZero();
            assertThat(value(connection, "SELECT contact_lifecycle_epoch FROM "
                    + "contact_snapshot_upload_session_v2 WHERE owner_user_id='59'")).isZero();
            assertThat(count(connection, "contacts_v2")).isOne();
            assertThat(indexExists(connection, "CONTACT_SNAPSHOT_REQUEST_V2",
                    "IDX_SNAPSHOT_REQUEST_V2_OWNER_EPOCH")).isTrue();
            assertThat(indexExists(connection, "CONTACT_SNAPSHOT_UPLOAD_SESSION_V2",
                    "IDX_SNAPSHOT_UPLOAD_V2_OWNER_EPOCH")).isTrue();
        }
    }

    private void execute(Connection connection, String resource) throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(stream).isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            for (String command : sql.split(";")) {
                if (!command.trim().isEmpty()) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute(command);
                    }
                }
            }
        }
    }

    private long value(Connection connection, String query) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(query)) {
            result.next();
            return result.getLong(1);
        }
    }

    private long count(Connection connection, String table) throws Exception {
        return value(connection, "SELECT COUNT(*) FROM " + table);
    }

    private boolean indexExists(Connection connection, String table, String index)
            throws Exception {
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
}
