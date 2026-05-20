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
 */
final class RankingLeaderboardLoader {

    private static final String SELECT_LEADERBOARD = """
            SELECT uuid, job_id, level, experience
            FROM job_player_profiles
            WHERE job_id IS NOT NULL AND job_id != 'none'
            ORDER BY job_id ASC, level DESC, experience DESC
            """;

    Map<JobId, List<RankingEntry>> load(Connection connection) throws SQLException {
        Map<JobId, List<RankingEntry>> grouped = new EnumMap<>(JobId.class);
        for (JobId jobId : JobId.values()) {
            if (jobId.hasJob()) {
                grouped.put(jobId, new ArrayList<>());
            }
        }

        try (PreparedStatement statement = connection.prepareStatement(SELECT_LEADERBOARD);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                JobId jobId = JobId.fromKey(resultSet.getString("job_id")).orElse(null);
                if (jobId == null || !jobId.hasJob()) {
                    continue;
                }
                UUID uuid = UUID.fromString(resultSet.getString("uuid"));
                int level = resultSet.getInt("level");
                long experience = resultSet.getLong("experience");
                grouped.computeIfAbsent(jobId, ignored -> new ArrayList<>())
                        .add(new RankingEntry(0, uuid, jobId, level, experience));
            }
        }

        for (Map.Entry<JobId, List<RankingEntry>> entry : grouped.entrySet()) {
            List<RankingEntry> board = entry.getValue();
            for (int i = 0; i < board.size(); i++) {
                RankingEntry old = board.get(i);
                board.set(i, new RankingEntry(i + 1, old.getUuid(), old.getJobId(), old.getLevel(), old.getExperience()));
            }
        }
        return grouped;
    }
}
