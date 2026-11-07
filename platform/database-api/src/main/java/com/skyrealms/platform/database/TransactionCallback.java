package com.skyrealms.platform.database;

import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Functional interface for database transactions returning a value.
 *
 * @param <T> result type.
 */
@FunctionalInterface
public interface TransactionCallback<T> {
    T doInTransaction(@NotNull Connection connection) throws SQLException;
}
