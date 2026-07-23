package com.odin.profileservice.repo;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountDeletionOutboxMigrationTest {

    private static final String MIGRATION =
            "db/migration/V9__create_account_deletion_outbox.sql";

    @Test
    void createsOutboxWithPendingAndRetryIndexes() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:c6-outbox-clean;MODE=MySQL;DATABASE_TO_UPPER=true")) {
            execute(connection, MIGRATION);

            assertThat(tableExists(connection, "ACCOUNT_DELETION_OUTBOX")).isTrue();
            assertThat(indexExists(connection, "ACCOUNT_DELETION_OUTBOX",
                    "IDX_ACCOUNT_DELETION_OUTBOX_PENDING")).isTrue();
            assertThat(indexExists(connection, "ACCOUNT_DELETION_OUTBOX",
                    "IDX_ACCOUNT_DELETION_OUTBOX_RETRY")).isTrue();
        }
    }

    @Test
    void v8UpgradePreservesExistingLifecycleDataAndEnforcesLogicalUniqueness()
            throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:c6-outbox-upgrade;MODE=MySQL;DATABASE_TO_UPPER=true")) {
            execute(connection, "db/migration/V5__create_contact_snapshot_v2.sql");
            execute(connection, "db/migration/V6__add_contact_snapshot_idempotency.sql");
            execute(connection, "db/migration/V7__add_contact_snapshot_upload_sessions.sql");
            execute(connection, "db/migration/V8__add_contact_lifecycle_epoch.sql");
            try (Statement statement = connection.createStatement()) {
                statement.execute("INSERT INTO contacts_v2 "
                        + "(contact_id,owner_user_id,target_global_phone_hash) "
                        + "VALUES ('contact-a','59','target-a')");
            }

            execute(connection, MIGRATION);
            insertPending(connection, "event-a", "59:event-a");

            assertThat(count(connection, "contacts_v2")).isOne();
            assertThatThrownBy(() ->
                    insertPending(connection, "event-b", "59:event-a"))
                    .hasMessageContaining("Unique index or primary key violation");
        }
    }

    private void insertPending(Connection connection, String eventId, String aggregateId)
            throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO account_deletion_outbox "
                    + "(event_id,aggregate_type,aggregate_id,event_type,payload,status,"
                    + "attempt_count,next_attempt_at,created_at) VALUES ('"
                    + eventId + "','ACCOUNT','" + aggregateId
                    + "','ACCOUNT_DELETED','{}','PENDING',0,CURRENT_TIMESTAMP,"
                    + "CURRENT_TIMESTAMP)");
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

    private long count(Connection connection, String table) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getLong(1);
        }
    }

    private boolean tableExists(Connection connection, String table) throws Exception {
        try (ResultSet result = connection.getMetaData().getTables(
                null, null, table, new String[]{"TABLE"})) {
            return result.next();
        }
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
