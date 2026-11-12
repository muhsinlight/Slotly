package com.slotly.api;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("integration-test")
class MigrationPreservationIntegrationTests {
    @Autowired
    private DataSource dataSource;

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
