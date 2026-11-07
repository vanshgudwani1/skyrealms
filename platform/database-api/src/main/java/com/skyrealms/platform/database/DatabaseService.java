package com.skyrealms.platform.database;

import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Authoritative persistence service providing managed PostgreSQL connections and transactions.
 */
public interface DatabaseService extends AutoCloseable {

    /**
     * Obtains an active connection from the pool.
     *
     * @return a pooled JDBC connection.
     * @throws SQLException if a database access error occurs.
     */
    @NotNull
    Connection getConnection() throws SQLException;

    /**
     * Checks whether the underlying database pool is responsive.
     *
     * @return true if healthy.
     */
    boolean isHealthy();

    /**
     * Runs an atomic transaction within a single database connection.
     * Automatically handles commit and rollback on failure.
     *
     * @param action the transaction logic.
     * @param <T>    return type.
     * @return the result of the transaction action.
     * @throws SQLException if transaction fails.
     */
    <T> T executeTransaction(@NotNull TransactionCallback<T> action) throws SQLException;

    /**
     * Runs an atomic void transaction.
     *
     * @param action the transaction logic.
     * @throws SQLException if transaction fails.
     */
    void executeTransactionVoid(@NotNull VoidTransactionCallback action) throws SQLException;

    @Override
    void close();
}
