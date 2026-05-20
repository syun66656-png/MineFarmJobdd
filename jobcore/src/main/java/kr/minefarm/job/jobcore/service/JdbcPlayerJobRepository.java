package kr.minefarm.job.jobcore.service;

import kr.minefarm.job.jobcore.api.DatabaseManager;
import kr.minefarm.job.jobcore.api.PlayerJobRepository;
import kr.minefarm.job.jobcore.domain.JobId;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobcore.domain.StatType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * MariaDB JDBC 기반 {@link PlayerJobRepository} 구현.
 */
public final class JdbcPlayerJobRepository implements PlayerJobRepository {

    private static final String SELECT = """
            SELECT job_id, level, experience, stat_points, invested_stats,
                   stat_relic, stat_skill, stat_sell, stat_auto_sell, auto_sell_enabled,
                   last_job_change
            FROM job_player_profiles WHERE uuid = ?
            """;

    private static final String UPSERT = """
            INSERT INTO job_player_profiles
                (uuid, job_id, level, experience, stat_points, invested_stats,
                 stat_relic, stat_skill, stat_sell, stat_auto_sell, auto_sell_enabled,
                 last_job_change, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                job_id = VALUES(job_id),
                level = VALUES(level),
                experience = VALUES(experience),
                stat_points = VALUES(stat_points),
                invested_stats = VALUES(invested_stats),
                stat_relic = VALUES(stat_relic),
                stat_skill = VALUES(stat_skill),
                stat_sell = VALUES(stat_sell),
                stat_auto_sell = VALUES(stat_auto_sell),
                auto_sell_enabled = VALUES(auto_sell_enabled),
                last_job_change = VALUES(last_job_change),
                updated_at = VALUES(updated_at)
            """;

    private final DatabaseManager database;

    public JdbcPlayerJobRepository(DatabaseManager database) {
        this.database = database;
    }

    @Override
    public CompletableFuture<Optional<PlayerJobProfile>> loadAsync(UUID uuid) {
        return database.supplyAsync(connection -> load(connection, uuid));
    }

    @Override
    public CompletableFuture<Void> saveAsync(PlayerJobProfile profile) {
        return database.runAsync(connection -> save(connection, profile));
    }

    @Override
    public Optional<PlayerJobProfile> load(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT)) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                PlayerJobProfile profile = new PlayerJobProfile(uuid);
                profile.setJobId(JobId.fromKey(resultSet.getString("job_id")).orElse(JobId.NONE));
                profile.setLevel(resultSet.getInt("level"));
                profile.setExperience(resultSet.getLong("experience"));
                profile.setStatPoints(resultSet.getInt("stat_points"));
                profile.setInvestedStats(resultSet.getInt("invested_stats"));
                profile.setStatLevel(StatType.RELIC, resultSet.getInt("stat_relic"));
                profile.setStatLevel(StatType.SKILL, resultSet.getInt("stat_skill"));
                profile.setStatLevel(StatType.SELL, resultSet.getInt("stat_sell"));
                profile.setStatLevel(StatType.AUTO_SELL, resultSet.getInt("stat_auto_sell"));
                profile.setAutoSellEnabled(resultSet.getBoolean("auto_sell_enabled"));
                long lastChange = resultSet.getLong("last_job_change");
                if (!resultSet.wasNull() && lastChange > 0) {
                    profile.setLastJobChangeAt(Instant.ofEpochMilli(lastChange));
                }
                profile.clearDirty();
                return Optional.of(profile);
            }
        }
    }

    @Override
    public void save(Connection connection, PlayerJobProfile profile) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT)) {
            statement.setString(1, profile.getUuid().toString());
            statement.setString(2, profile.getJobId().getKey());
            statement.setInt(3, profile.getLevel());
            statement.setLong(4, profile.getExperience());
            statement.setInt(5, profile.getStatPoints());
            statement.setInt(6, profile.getInvestedStats());
            statement.setInt(7, profile.getStatLevel(StatType.RELIC));
            statement.setInt(8, profile.getStatLevel(StatType.SKILL));
            statement.setInt(9, profile.getStatLevel(StatType.SELL));
            statement.setInt(10, profile.getStatLevel(StatType.AUTO_SELL));
            statement.setInt(11, profile.isAutoSellEnabled() ? 1 : 0);
            Instant lastChange = profile.getLastJobChangeAt();
            if (lastChange == null) {
                statement.setNull(12, Types.BIGINT);
            } else {
                statement.setLong(12, lastChange.toEpochMilli());
            }
            statement.setLong(13, Instant.now().toEpochMilli());
            statement.executeUpdate();
            profile.clearDirty();
        }
    }
}
