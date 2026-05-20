package kr.minefarm.job.jobcore.api;

import kr.minefarm.job.jobcore.domain.PlayerJobProfile;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 플레이어 직업 프로필 영속화 계약.
 * 구현체: {@link kr.minefarm.job.jobcore.service.JdbcPlayerJobRepository}
 */
public interface PlayerJobRepository {

    CompletableFuture<Optional<PlayerJobProfile>> loadAsync(UUID uuid);

    CompletableFuture<Void> saveAsync(PlayerJobProfile profile);

    Optional<PlayerJobProfile> load(Connection connection, UUID uuid) throws SQLException;

    void save(Connection connection, PlayerJobProfile profile) throws SQLException;
}
