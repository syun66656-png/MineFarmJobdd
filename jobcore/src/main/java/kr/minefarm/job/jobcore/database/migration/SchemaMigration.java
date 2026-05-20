package kr.minefarm.job.jobcore.database.migration;

import kr.minefarm.job.jobcore.api.DatabaseManager;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

/**
 * 스키마 생성 및 컬럼 마이그레이션.
 */
public final class SchemaMigration {

    private static final String CREATE_PLAYER_PROFILES = """
            CREATE TABLE IF NOT EXISTS job_player_profiles (
                uuid                CHAR(36)     NOT NULL PRIMARY KEY,
                job_id              VARCHAR(32)  NOT NULL DEFAULT 'none',
                level               INT          NOT NULL DEFAULT 1,
                experience          BIGINT       NOT NULL DEFAULT 0,
                stat_points         INT          NOT NULL DEFAULT 0,
                invested_stats      INT          NOT NULL DEFAULT 0,
                stat_relic          INT          NOT NULL DEFAULT 0,
                stat_skill          INT          NOT NULL DEFAULT 0,
                stat_sell           INT          NOT NULL DEFAULT 0,
                stat_auto_sell      INT          NOT NULL DEFAULT 0,
                auto_sell_enabled   TINYINT(1)   NOT NULL DEFAULT 0,
                last_job_change     BIGINT       NULL,
                updated_at          BIGINT       NOT NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """;

    private static final String[][] LEGACY_COLUMNS = {
            {"stat_relic", "INT NOT NULL DEFAULT 0"},
            {"stat_skill", "INT NOT NULL DEFAULT 0"},
            {"stat_sell", "INT NOT NULL DEFAULT 0"},
            {"stat_auto_sell", "INT NOT NULL DEFAULT 0"},
            {"auto_sell_enabled", "TINYINT(1) NOT NULL DEFAULT 0"},
    };

    private final DatabaseManager database;
    private final Logger logger;

    public SchemaMigration(DatabaseManager database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    public void migrate() throws SQLException {
        try (Connection connection = database.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(CREATE_PLAYER_PROFILES);
            for (String[] column : LEGACY_COLUMNS) {
                addColumnIfMissing(connection, column[0], column[1]);
            }
        }
    }

    private void addColumnIfMissing(Connection connection, String column, String definition) throws SQLException {
        if (columnExists(connection, column)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE job_player_profiles ADD COLUMN "
                    + column + " " + definition);
            logger.info("[JobCore] Added column job_player_profiles." + column);
        }
    }

    private boolean columnExists(Connection connection, String column) throws SQLException {
        String sql = """
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'job_player_profiles'
                  AND COLUMN_NAME = ?
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, column);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }
}
