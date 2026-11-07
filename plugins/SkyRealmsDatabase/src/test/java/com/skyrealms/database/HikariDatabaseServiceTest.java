package com.skyrealms.database;

import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HikariDatabaseServiceTest {

    private HikariDatabaseService databaseService;

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setPoolName("Test-HikariPool-" + UUID.randomUUID());
        config.setJdbcUrl("jdbc:h2:mem:testdb_" + UUID.randomUUID().toString().replace('-', '_') + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        config.setUsername("sa");
        config.setPassword("");
        config.setDriverClassName("org.h2.Driver");
        config.setMaximumPoolSize(3);

        databaseService = new HikariDatabaseService(config);
    }

    @AfterEach
    void tearDown() {
        databaseService.close();
    }

    @Test
    @DisplayName("Should report healthy status when pool is connected")
    void shouldReportHealthy() {
        assertThat(databaseService.isHealthy()).isTrue();
    }

    @Test
    @DisplayName("Should commit transaction on successful completion")
    void shouldCommitTransaction() throws SQLException {
        databaseService.executeTransactionVoid(conn -> {
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE test_tx (id INT PRIMARY KEY, val VARCHAR(50))");
                st.execute("INSERT INTO test_tx VALUES (1, 'committed')");
            }
        });

        try (Connection conn = databaseService.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT val FROM test_tx WHERE id = 1")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("val")).isEqualTo("committed");
        }
    }

    @Test
    @DisplayName("Should rollback transaction on exception")
    void shouldRollbackTransaction() throws SQLException {
        databaseService.executeTransactionVoid(conn -> {
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE test_rb (id INT PRIMARY KEY, val VARCHAR(50))");
                st.execute("INSERT INTO test_rb VALUES (1, 'original')");
            }
        });

        assertThatThrownBy(() -> {
            databaseService.executeTransactionVoid(conn -> {
                try (Statement st = conn.createStatement()) {
                    st.execute("INSERT INTO test_rb VALUES (2, 'should_rollback')");
                }
                throw new RuntimeException("Simulated error inside transaction");
            });
        }).isInstanceOf(SQLException.class);

        try (Connection conn = databaseService.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM test_rb WHERE id = 2")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(0);
        }
    }
}
