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
 * <p>
 * 비동기 저장은 {@link PlayerJobProfile#snapshot()} 으로 일관된 스냅샷을 먼저 획득한 뒤
 * PreparedStatement 에 바인딩한다. 저장 도중 메인 스레드의 수정이 끼어들어도
 * 스냅샷의 데이터가 온전히 저장되며, 실패 시 {@link PlayerJobProfile#markDirty()} 로
 * 복원하여 다음 flush 주기에 재시도된다.
 */
public final class JdbcPlayerJobRepository implements PlayerJobRepository {

    private static final String SELECT = """
            SELECT job_id, level, experience, stat_points, invested_stats,
                   stat_relic, stat_skill, stat_sell, stat_auto_sell, auto_sell_enabled,
                   last_job_change, boost_multiplier, boost_expiry_time
            FROM job_player_profiles WHERE uuid = ?
            """;

    private static final String UPSERT = """
            INSERT INTO job_player_profiles
                (uuid, job_id, level, experience, stat_points, invested_stats,
                 stat_relic, stat_skill, stat_sell, stat_auto_sell, auto_sell_enabled,
                 last_job_change, updated_at, boost_multiplier, boost_expiry_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                updated_at = VALUES(updated_at),
                boost_multiplier = VALUES(boost_multiplier),
                boost_expiry_time = VALUES(boost_expiry_time)
            """;

    private final DatabaseManager database;

    public JdbcPlayerJobRepository(DatabaseManager database) {
        this.database = database;
    }

    @Override
    public CompletableFuture<Optional<PlayerJobProfile>> loadAsync(UUID uuid) {
        return database.supplyAsync(connection -> load(connection, uuid));
    }

    /**
     * 저장 전 스냅샷을 atomic 하게 획득한다. 저장 실패 시 dirty 를 복원한다.
     */
    @Override
    public CompletableFuture<Void> saveAsync(PlayerJobProfile profile) {
        // 스냅샷 획득과 동시에 dirty 해제 (lost-update 방지)
        PlayerJobProfile.Snapshot snap = profile.snapshot();
        return database.runAsync(connection -> save(connection, snap))
                .exceptionally(throwable -> {
                    // 저장 실패 시 dirty 복원 → 다음 flush 주기에 재시도
                    profile.markDirty();
                    throw throwable instanceof RuntimeException re ? re
                            : new RuntimeException(throwable);
                });
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
                // 경험치 부스트 (마이그레이션으로 추가된 컬럼)
                profile.setBoostMultiplier(resultSet.getDouble("boost_multiplier"));
                profile.setBoostExpiryTime(resultSet.getLong("boost_expiry_time"));
                profile.clearDirty();
                return Optional.of(profile);
            }
        }
    }

    @Override
    public void save(Connection connection, PlayerJobProfile profile) throws SQLException {
        save(connection, profile.snapshot());
    }

    /** 스냅샷 기반 저장 — 저장 도중 프로필 변경의 영향을 받지 않는다. */
    private void save(Connection connection, PlayerJobProfile.Snapshot snap) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT)) {
            statement.setString(1, snap.uuid().toString());
            statement.setString(2, snap.jobId().getKey());
            statement.setInt(3, snap.level());
            statement.setLong(4, snap.experience());
            statement.setInt(5, snap.statPoints());
            statement.setInt(6, snap.investedStats());
            statement.setInt(7, snap.statRelic());
            statement.setInt(8, snap.statSkill());
            statement.setInt(9, snap.statSell());
            statement.setInt(10, snap.statAutoSell());
            statement.setInt(11, snap.autoSellEnabled() ? 1 : 0);
            if (snap.lastJobChangeAt() == null) {
                statement.setNull(12, Types.BIGINT);
            } else {
                statement.setLong(12, snap.lastJobChangeAt().toEpochMilli());
            }
            statement.setLong(13, Instant.now().toEpochMilli());
            statement.setDouble(14, snap.boostMultiplier());
            statement.setLong(15, snap.boostExpiryTime());
            statement.executeUpdate();
            // clearDirty는 snapshot() 내부에서 이미 처리됨
        }
    }
}
