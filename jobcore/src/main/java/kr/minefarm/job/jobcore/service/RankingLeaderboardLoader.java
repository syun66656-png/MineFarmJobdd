package kr.minefarm.job.jobcore.service;

import kr.minefarm.job.jobcore.domain.JobId;
import kr.minefarm.job.jobcore.domain.RankingEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MariaDB에서 직업별 랭킹 원본 데이터를 읽는다.
 * <p>
 * 전체 플레이어를 모두 로드하던 방식을 개선하여 직업별 LIMIT 를 적용한다.
 * {@code (job_id, level DESC, experience DESC)} 인덱스를 활용하면 성능이 크게 향상된다.
 */
final class RankingLeaderboardLoader {

    private static final String SELECT_LEADERBOARD = """
            SELECT uuid, job_id, level, experience
            FROM job_player_profiles
            WHERE job_id = ? AND job_id != 'none'
            ORDER BY level DESC, experience DESC
            LIMIT ?
            """;

    private final int topN;

    RankingLeaderboardLoader(int topN) {
        this.topN = Math.max(1, topN);
    }

    Map<JobId, List<RankingEntry>> load(Connection connection) throws SQLException {
        Map<JobId, List<RankingEntry>> grouped = new EnumMap<>(JobId.class);

        for (JobId jobId : JobId.values()) {
            if (!jobId.hasJob()) continue;

            List<RankingEntry> board = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(SELECT_LEADERBOARD)) {
                statement.setString(1, jobId.getKey());
                statement.setInt(2, topN);
                try (ResultSet resultSet = statement.executeQuery()) {
                    int rank = 1;
                    while (resultSet.next()) {
                        UUID uuid = UUID.fromString(resultSet.getString("uuid"));
                        int level = resultSet.getInt("level");
                        long experience = resultSet.getLong("experience");
                        board.add(new RankingEntry(rank++, uuid, jobId, level, experience));
                    }
                }
            }
            grouped.put(jobId, board);
        }
        return grouped;
    }
}
