package com.skyrealms.database;

import com.skyrealms.platform.database.DatabaseService;
import com.skyrealms.platform.database.TransactionCallback;
import com.skyrealms.platform.database.VoidTransactionCallback;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Robust HikariCP-backed implementation of {@link DatabaseService}.
 */
public final class HikariDatabaseService implements DatabaseService {

    private static final Logger log = LoggerFactory.getLogger(HikariDatabaseService.class);

    private final HikariDataSource dataSource;

    public HikariDatabaseService(@NotNull HikariConfig config) {
        Objects.requireNonNull(config, "config cannot be null");
        this.dataSource = new HikariDataSource(config);
        log.info("Initialized HikariCP database pool: {}", config.getPoolName());
    }

    public HikariDatabaseService(@NotNull HikariDataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource cannot be null");
    }

    @NotNull
    public HikariDataSource getDataSource() {
        return dataSource;
    }

    @Override
    @NotNull
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public boolean isHealthy() {
        if (dataSource.isClosed()) {
            return false;
        }
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(1);
        } catch (SQLException e) {
            log.warn("Database health check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public <T> T executeTransaction(@NotNull TransactionCallback<T> action) throws SQLException {
        Objects.requireNonNull(action, "action cannot be null");
        try (Connection conn = getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                T result = action.doInTransaction(conn);
                conn.commit();
                return result;
            } catch (Throwable t) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    log.error("Failed to rollback database transaction: {}", rollbackEx.getMessage(), rollbackEx);
                }
                if (t instanceof SQLException sqle) {
                    throw sqle;
                }
                throw new SQLException("Transaction aborted due to error: " + t.getMessage(), t);
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        }
    }

    @Override
    public void executeTransactionVoid(@NotNull VoidTransactionCallback action) throws SQLException {
        Objects.requireNonNull(action, "action cannot be null");
        executeTransaction(conn -> {
            action.doInTransaction(conn);
            return null;
        });
    }

    @Override
    public void close() {
        if (!dataSource.isClosed()) {
            log.info("Closing HikariCP database pool...");
            dataSource.close();
            log.info("HikariCP database pool closed.");
        }
    }
}
