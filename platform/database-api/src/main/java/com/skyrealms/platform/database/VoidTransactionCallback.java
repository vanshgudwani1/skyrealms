package com.skyrealms.platform.database;

import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Functional interface for database transactions without a return value.
 */
@FunctionalInterface
public interface VoidTransactionCallback {
    void doInTransaction(@NotNull Connection connection) throws SQLException;
}
