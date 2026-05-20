package kr.minefarm.job.jobcore.api;

import kr.minefarm.job.jobcore.config.DatabaseSettings;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

/**
 * 데이터베이스 연결·비동기 실행 계약.
 * 구현체: {@link kr.minefarm.job.jobcore.bootstrap.HikariDatabaseManager}
 */
public interface DatabaseManager extends AutoCloseable {

    void connect(DatabaseSettings settings) throws SQLException;

    Connection getConnection() throws SQLException;

    <T> CompletableFuture<T> supplyAsync(DatabaseCallback.SqlSupplier<T> callback);

    CompletableFuture<Void> runAsync(DatabaseCallback.SqlRunnable callback);

    boolean isConnected();

    @Override
    void close();
}
