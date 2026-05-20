package kr.minefarm.job.jobcore.bootstrap;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import kr.minefarm.job.jobcore.api.DatabaseCallback;
import kr.minefarm.job.jobcore.api.DatabaseManager;
import kr.minefarm.job.jobcore.config.DatabaseSettings;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * HikariCP 기반 {@link DatabaseManager} 구현.
 */
public final class HikariDatabaseManager implements DatabaseManager {

    /** maven-shade relocation: org.mariadb → kr.minefarm.jobcore.lib.mariadb */
    private static final String SHADED_MARIADB_DRIVER = "kr.minefarm.jobcore.lib.mariadb.jdbc.Driver";

    private final JavaPlugin plugin;
    private final Executor asyncExecutor;
    private HikariDataSource dataSource;

    public HikariDatabaseManager(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.asyncExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public void connect(DatabaseSettings settings) throws SQLException {
        if (isConnected()) {
            return;
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(buildJdbcUrl(settings));
        config.setUsername(settings.username());
        config.setPassword(settings.password());
        config.setMaximumPoolSize(settings.poolSize());
        config.setConnectionTimeout(settings.connectionTimeoutMs());
        config.setPoolName("MineFarmJob-Hikari");
        config.setDriverClassName(SHADED_MARIADB_DRIVER);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        // 운영 안정성 옵션
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("socketTimeout", "30000");
        config.addDataSourceProperty("tcpKeepAlive", "true");
        config.addDataSourceProperty("characterEncoding", "utf8");
        config.addDataSourceProperty("useUnicode", "true");
        config.addDataSourceProperty("serverTimezone", "UTC");

        dataSource = new HikariDataSource(config);

        try (Connection connection = getConnection()) {
            if (!connection.isValid(3)) {
                throw new SQLException("Database connection validation failed");
            }
        }

        plugin.getLogger().info("MariaDB connected: " + settings.host() + ":" + settings.port()
                + "/" + settings.database());
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (!isConnected()) {
            throw new SQLException("Database is not connected");
        }
        return dataSource.getConnection();
    }

    @Override
    public <T> CompletableFuture<T> supplyAsync(DatabaseCallback.SqlSupplier<T> callback) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection()) {
                return callback.execute(connection);
            } catch (SQLException exception) {
                throw new DatabaseOperationException(exception);
            }
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<Void> runAsync(DatabaseCallback.SqlRunnable callback) {
        return supplyAsync(connection -> {
            callback.execute(connection);
            return null;
        }).thenAccept(ignored -> {
        });
    }

    @Override
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("MariaDB connection pool closed.");
        }
    }

    private static String buildJdbcUrl(DatabaseSettings settings) {
        return "jdbc:mariadb://" + settings.host() + ":" + settings.port() + "/"
                + settings.database() + "?useUnicode=true&characterEncoding=UTF-8";
    }

    public static final class DatabaseOperationException extends RuntimeException {
        public DatabaseOperationException(SQLException cause) {
            super(cause);
        }

        public void log(JavaPlugin plugin, String message) {
            plugin.getLogger().log(Level.SEVERE, message, getCause());
        }
    }
}
