package com.slotly.api;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("integration-test")
class MigrationPreservationIntegrationTests {
    @Autowired
    private DataSource dataSource;

    @Test
    void bookingMigrationStopsForConflictsAndPreservesRowsAndOfferDeadlines() throws Exception {
        String schema = "booking_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
            sql.execute("CREATE SCHEMA " + schema);
            try {
                connection.setSchema(schema);
                var config = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema);
                config.target("6").load().migrate();
                UUID customer = UUID.randomUUID();
                UUID place = UUID.randomUUID();
                UUID offering = UUID.randomUUID();
                UUID conflict = UUID.randomUUID();
                UUID offer = UUID.randomUUID();
                sql.execute("""
                    INSERT INTO app_users(id, first_name, last_name, password_hash, role, username)
                    VALUES ('%s','Test','User','hash','CUSTOMER','legacy')
                    """.formatted(customer));
                sql.execute("""
                    INSERT INTO places(id, owner_id, name, address, capacity, active)
                    VALUES ('%s','%s','Place','Address',2,true)
                    """.formatted(place, customer));
                sql.execute("""
                    INSERT INTO offerings(id, place_id, name, price, kind, duration_minutes, active)
                    VALUES ('%s','%s','Service',10,'SERVICE',30,true)
                    """.formatted(offering, place));
                for (UUID id : java.util.List.of(UUID.randomUUID(), conflict)) {
                    sql.execute("""
                        INSERT INTO appointments(id, customer_id, place_id, offering_id, starts_at, ends_at, status)
                        VALUES ('%s','%s','%s','%s','2030-01-01T10:00Z','2030-01-01T10:30Z','CONFIRMED')
                        """.formatted(id, customer, place, offering));
                }
                sql.execute("""
                    INSERT INTO waitlist_entries(id, customer_id, place_id, offering_id, requested_starts_at,
                        requested_ends_at, status, created_at, offered_at, offer_expires_at)
                    VALUES ('%s','%s','%s','%s','2030-01-01T10:00Z','2030-01-01T10:30Z','OFFERED',
                        '2030-01-01T09:35Z','2030-01-01T09:55Z','2030-01-01T10:25Z')
                    """.formatted(offer, customer, place, offering));
                sql.execute("""
                    INSERT INTO email_outbox(id, user_id, recipient, subject, body, deduplication_key,
                        next_attempt_at, created_at, kind, reference_id, expires_at)
                    VALUES ('%s','%s','test@example.org','offer','Saat: 10:00
                    Son kabul: 2030-01-01T10:25:00Z
                    Teklifi incele: /waitlist','offer:test',now(),now(),'OFFER','%s','2030-01-01T10:25Z')
                    """.formatted(UUID.randomUUID(), customer, offer));

                Flyway upgrade = config.target("7").load();
                var error = assertThrows(FlywayException.class, upgrade::migrate);
                assertTrue(error.getMessage().contains("Duplicate confirmed appointments"));
                try (var rows = sql.executeQuery("SELECT count(*) FROM appointments WHERE status = 'CONFIRMED'")) {
                    rows.next();
                    assertEquals(2, rows.getInt(1));
                }

                sql.execute("UPDATE appointments SET status = 'CANCELLED' WHERE id = '" + conflict + "'");
                upgrade.migrate();
                try (var rows = sql.executeQuery("""
                    SELECT entry.offer_expires_at = entry.requested_starts_at,
                        message.expires_at = entry.requested_starts_at, message.body
                    FROM waitlist_entries entry JOIN email_outbox message ON message.reference_id = entry.id
                    """)) {
                    assertTrue(rows.next());
                    assertTrue(rows.getBoolean(1));
                    assertTrue(rows.getBoolean(2));
                    assertTrue(rows.getString(3).contains("Son kabul: 2030-01-01T10:00:00"));
                    assertTrue(rows.getString(3).contains("Teklifi incele: /waitlist"));
                }
            } finally {
                connection.setSchema("public");
                sql.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    @Test
    void baselinePreservesExistingHibernateSchemaAndRecords() throws Exception {
        String schema = "legacy_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
            try {
                connection.setSchema(schema);
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V1__create_slotly_schema.sql"));
                statement.execute("INSERT INTO app_users (id, first_name, last_name, password_hash, role, username) "
                    + "VALUES ('00000000-0000-0000-0000-000000000001', 'Existing', 'Customer', 'hash', 'CUSTOMER', 'existing')");
                Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .baselineOnMigrate(true)
                    .baselineVersion("1")
                    .load();
                flyway.migrate();
                flyway.migrate();
                try (var records = statement.executeQuery("SELECT username FROM app_users")) {
                    records.next();
                    assertEquals("existing", records.getString(1));
                }
                try (var history = statement.executeQuery("SELECT type FROM flyway_schema_history WHERE version = '1'")) {
                    history.next();
                    assertEquals("BASELINE", history.getString(1));
                }
            } finally {
                connection.setSchema("public");
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }
}
