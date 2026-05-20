package kr.minefarm.job.jobcore.domain;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 플레이어 직업·레벨·스탯 등 영속 데이터 스냅샷.
 */
public final class PlayerJobProfile {

    private final UUID uuid;
    private JobId jobId;
    private int level;
    private long experience;
    private int statPoints;
    private int investedStats;
    private final Map<StatType, Integer> statLevels = new EnumMap<>(StatType.class);
    private boolean autoSellEnabled;
    private Instant lastJobChangeAt;
    private boolean dirty;

    public PlayerJobProfile(UUID uuid) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.jobId = JobId.NONE;
        this.level = 1;
        this.experience = 0L;
        this.statPoints = 0;
        this.investedStats = 0;
        for (StatType type : StatType.values()) {
            statLevels.put(type, 0);
        }
        this.autoSellEnabled = false;
        this.lastJobChangeAt = null;
        this.dirty = false;
    }

    public UUID getUuid() {
        return uuid;
    }

    public JobId getJobId() {
        return jobId;
    }

    public void setJobId(JobId jobId) {
        this.jobId = jobId != null ? jobId : JobId.NONE;
        markDirty();
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(1, level);
        markDirty();
    }

    public long getExperience() {
        return experience;
    }

    public void setExperience(long experience) {
        this.experience = Math.max(0L, experience);
        markDirty();
    }

    public int getStatPoints() {
        return statPoints;
    }

    public void setStatPoints(int statPoints) {
        this.statPoints = Math.max(0, statPoints);
        markDirty();
    }

    public int getInvestedStats() {
        return investedStats;
    }

    public void setInvestedStats(int investedStats) {
        this.investedStats = Math.max(0, investedStats);
        markDirty();
    }

    public int getStatLevel(StatType type) {
        return statLevels.getOrDefault(type, 0);
    }

    public void setStatLevel(StatType type, int level) {
        statLevels.put(type, Math.max(0, level));
        markDirty();
    }

    public boolean isAutoSellEnabled() {
        return autoSellEnabled;
    }

    public void setAutoSellEnabled(boolean autoSellEnabled) {
        this.autoSellEnabled = autoSellEnabled;
        markDirty();
    }

    public Instant getLastJobChangeAt() {
        return lastJobChangeAt;
    }

    public void setLastJobChangeAt(Instant lastJobChangeAt) {
        this.lastJobChangeAt = lastJobChangeAt;
        markDirty();
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void clearDirty() {
        this.dirty = false;
    }
}
