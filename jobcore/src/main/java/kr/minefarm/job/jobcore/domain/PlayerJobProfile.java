package kr.minefarm.job.jobcore.domain;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 플레이어 직업·레벨·스탯 등 영속 데이터.
 * <p>
 * 메인 스레드와 비동기 저장 스레드에서 동시에 접근하므로 모든 getter/setter 를
 * {@code synchronized} 로 보호한다. 비동기 저장은 {@link #snapshot()} 으로
 * 일관된 스냅샷을 획득한 뒤 처리해야 한다.
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

    // ── 식별자 ────────────────────────────────────────────────────────────────

    public UUID getUuid() {
        return uuid;
    }

    // ── 직업 ──────────────────────────────────────────────────────────────────

    public synchronized JobId getJobId() {
        return jobId;
    }

    public synchronized void setJobId(JobId jobId) {
        this.jobId = jobId != null ? jobId : JobId.NONE;
        markDirty();
    }

    // ── 레벨·경험치 ───────────────────────────────────────────────────────────

    public synchronized int getLevel() {
        return level;
    }

    public synchronized void setLevel(int level) {
        this.level = Math.max(1, level);
        markDirty();
    }

    public synchronized long getExperience() {
        return experience;
    }

    public synchronized void setExperience(long experience) {
        this.experience = Math.max(0L, experience);
        markDirty();
    }

    // ── 스탯 포인트 ───────────────────────────────────────────────────────────

    public synchronized int getStatPoints() {
        return statPoints;
    }

    public synchronized void setStatPoints(int statPoints) {
        this.statPoints = Math.max(0, statPoints);
        markDirty();
    }

    public synchronized int getInvestedStats() {
        return investedStats;
    }

    public synchronized void setInvestedStats(int investedStats) {
        this.investedStats = Math.max(0, investedStats);
        markDirty();
    }

    public synchronized int getStatLevel(StatType type) {
        return statLevels.getOrDefault(type, 0);
    }

    public synchronized void setStatLevel(StatType type, int level) {
        statLevels.put(type, Math.max(0, level));
        markDirty();
    }

    // ── 자동판매 ──────────────────────────────────────────────────────────────

    public synchronized boolean isAutoSellEnabled() {
        return autoSellEnabled;
    }

    public synchronized void setAutoSellEnabled(boolean autoSellEnabled) {
        this.autoSellEnabled = autoSellEnabled;
        markDirty();
    }

    // ── 직업 변경 쿨타임 ──────────────────────────────────────────────────────

    public synchronized Instant getLastJobChangeAt() {
        return lastJobChangeAt;
    }

    public synchronized void setLastJobChangeAt(Instant lastJobChangeAt) {
        this.lastJobChangeAt = lastJobChangeAt;
        markDirty();
    }

    // ── dirty 관리 ────────────────────────────────────────────────────────────

    public synchronized boolean isDirty() {
        return dirty;
    }

    public synchronized void markDirty() {
        this.dirty = true;
    }

    public synchronized void clearDirty() {
        this.dirty = false;
    }

    // ── 스냅샷 ────────────────────────────────────────────────────────────────

    /**
     * 현재 상태를 atomic 하게 캡처하고 dirty 플래그를 동시에 해제한다.
     * 비동기 저장 스레드는 profile 인스턴스 대신 이 스냅샷만 사용해야
     * lost-update 와 부분 저장 문제를 피할 수 있다.
     * <p>
     * 저장 실패 시 호출자는 {@link #markDirty()} 로 복원해야 한다.
     */
    public synchronized Snapshot snapshot() {
        Snapshot snap = new Snapshot(
                uuid, jobId, level, experience,
                statPoints, investedStats,
                statLevels.getOrDefault(StatType.RELIC, 0),
                statLevels.getOrDefault(StatType.SKILL, 0),
                statLevels.getOrDefault(StatType.SELL, 0),
                statLevels.getOrDefault(StatType.AUTO_SELL, 0),
                autoSellEnabled, lastJobChangeAt
        );
        this.dirty = false;   // atomic clearDirty
        return snap;
    }

    /**
     * 스냅샷을 profile 에 복원한다 (load 후 populate 용도).
     * dirty 플래그는 건드리지 않는다.
     */
    public synchronized void applySnapshot(Snapshot snap) {
        this.jobId = snap.jobId();
        this.level = snap.level();
        this.experience = snap.experience();
        this.statPoints = snap.statPoints();
        this.investedStats = snap.investedStats();
        statLevels.put(StatType.RELIC, snap.statRelic());
        statLevels.put(StatType.SKILL, snap.statSkill());
        statLevels.put(StatType.SELL, snap.statSell());
        statLevels.put(StatType.AUTO_SELL, snap.statAutoSell());
        this.autoSellEnabled = snap.autoSellEnabled();
        this.lastJobChangeAt = snap.lastJobChangeAt();
    }

    /**
     * 영속화에 필요한 불변 스냅샷.
     */
    public record Snapshot(
            UUID uuid,
            JobId jobId,
            int level,
            long experience,
            int statPoints,
            int investedStats,
            int statRelic,
            int statSkill,
            int statSell,
            int statAutoSell,
            boolean autoSellEnabled,
            Instant lastJobChangeAt
    ) {}
}
