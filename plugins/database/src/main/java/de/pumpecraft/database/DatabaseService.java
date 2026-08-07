package de.pumpecraft.database;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

public final class DatabaseService {
    private final DataSource dataSource;

    DatabaseService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public <T> T withConnection(SqlFunction<Connection, T> action) {
        try (Connection connection = dataSource.getConnection()) {
            return action.apply(connection);
        } catch (SQLException exception) {
            throw new DatabaseException("Database operation failed.", exception);
        }
    }

    public <T> T inTransaction(SqlFunction<Connection, T> action) {
        return withConnection(connection -> {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = action.apply(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        });
    }
}
