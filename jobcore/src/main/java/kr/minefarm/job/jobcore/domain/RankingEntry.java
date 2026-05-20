package kr.minefarm.job.jobcore.domain;

import java.util.UUID;

/**
 * 직업별 랭킹 보드 한 줄.
 */
public final class RankingEntry {

    private final int rank;
    private final UUID uuid;
    private final JobId jobId;
    private final int level;
    private final long experience;

    public RankingEntry(int rank, UUID uuid, JobId jobId, int level, long experience) {
        this.rank = rank;
        this.uuid = uuid;
        this.jobId = jobId;
        this.level = level;
        this.experience = experience;
    }

    public int getRank() {
        return rank;
    }

    public UUID getUuid() {
        return uuid;
    }

    public JobId getJobId() {
        return jobId;
    }

    public int getLevel() {
        return level;
    }

    public long getExperience() {
        return experience;
    }
}
