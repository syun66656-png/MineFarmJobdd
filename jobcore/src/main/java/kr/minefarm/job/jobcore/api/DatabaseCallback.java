package kr.minefarm.job.jobcore.api;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * {@link DatabaseManager} 비동기 SQL 콜백.
 */
public final class DatabaseCallback {

    private DatabaseCallback() {
    }

    @FunctionalInterface
    public interface SqlSupplier<T> {
        T execute(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlRunnable {
        void execute(Connection connection) throws SQLException;
    }
}
