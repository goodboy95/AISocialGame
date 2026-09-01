package com.aisocialgame.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class ProductionSocialMigrationExternalMySqlTest {

    @Test
    void signedSqlPlanIsIdempotentAndCheckpointFullyRestoresIsolatedMysql() throws Exception {
        String url = System.getenv("AIENIE_SOCIAL_MIGRATION_TEST_URL");
        String username = System.getenv("AIENIE_SOCIAL_MIGRATION_TEST_USERNAME");
        String password = System.getenv("AIENIE_SOCIAL_MIGRATION_TEST_PASSWORD");
        Assumptions.assumeTrue(url != null && username != null && password != null);
        assertTrue(url.matches(
                "jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/aienie_social_migration_test_[A-Za-z0-9_]+\\?.*"));

        Path schema = Path.of("sql/schema.sql").toAbsolutePath().normalize();
        String checksum = "sha256:" + java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(schema)));
        ProductionSocialMigrationMain.MigrationEntry entry =
                new ProductionSocialMigrationMain.MigrationEntry(1, schema, checksum);
        ProductionSocialMigrationMain.MigrationPlan plan =
                new ProductionSocialMigrationMain.MigrationPlan(
                        "fresh-empty-schema", "sha256:" + "a".repeat(64), List.of(entry), Map.of(1, entry));

        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            List<ProductionSocialMigrationMain.MigrationEntry> pending =
                    ProductionSocialMigrationMain.pendingEntries(connection, plan);
            assertEquals(List.of(entry), pending);
            ProductionSocialMigrationMain.applyPending(connection, plan, pending);
            assertTrue(ProductionSocialMigrationMain.pendingEntries(connection, plan).isEmpty());
            ProductionSocialMigrationMain.applyPending(connection, plan, List.of());
            ProductionSocialMigrationMain.validateSchema(connection, plan);

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO users(id,email,password,nickname,coins,level)
                        VALUES('checkpoint-user','checkpoint@example.invalid','unused','Checkpoint',7,2)
                        """);
            }
            Path checkpointDirectory = Files.createTempDirectory("aienie-social-checkpoint-").toAbsolutePath();
            Path checkpoint = checkpointDirectory.resolve("checkpoint.sql");
            try {
                ProductionSocialMigrationMain.writeCheckpoint(connection, checkpoint);
                assertTrue(Files.size(checkpoint) > 0);
                assertEquals(
                        Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                        Files.getPosixFilePermissions(checkpoint));

                connection.setReadOnly(false);
                connection.setAutoCommit(true);
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("DELETE FROM users WHERE id='checkpoint-user'");
                    statement.execute("CREATE TABLE post_checkpoint_table(id INT PRIMARY KEY) ENGINE=InnoDB");
                }
                executeRestoreScript(connection, checkpoint);
                assertEquals(1, scalar(connection,
                        "SELECT COUNT(*) FROM users WHERE id='checkpoint-user' AND coins=7 AND level=2"));
                assertEquals(0, scalar(connection, """
                        SELECT COUNT(*) FROM information_schema.tables
                         WHERE table_schema=DATABASE() AND table_name='post_checkpoint_table'
                        """));
                assertTrue(ProductionSocialMigrationMain.pendingEntries(connection, plan).isEmpty());
                ProductionSocialMigrationMain.validateSchema(connection, plan);
            } finally {
                Files.deleteIfExists(checkpoint);
                Files.deleteIfExists(checkpointDirectory);
            }
        }
    }

    private static int scalar(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            int value = result.getInt(1);
            assertFalse(result.next());
            return value;
        }
    }

    private static void executeRestoreScript(Connection connection, Path checkpoint) throws Exception {
        StringBuilder statementText = new StringBuilder();
        for (String line : Files.readAllLines(checkpoint, StandardCharsets.UTF_8)) {
            if (line.startsWith("--")) {
                continue;
            }
            statementText.append(line).append('\n');
            if (!line.stripTrailing().endsWith(";")) {
                continue;
            }
            String sql = statementText.toString().strip();
            sql = sql.substring(0, sql.length() - 1);
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
            statementText.setLength(0);
        }
        assertTrue(statementText.toString().isBlank());
    }
}
