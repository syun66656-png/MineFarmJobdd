package kr.minefarm.job.jobcore.service;

import kr.minefarm.job.jobcore.config.JobCoreConfig;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;

import java.util.Map;

/**
 * 레벨별 필요 경험치 관리.
 * <p>
 * {@code config.yml}의 {@code leveling.exp-per-level} 섹션에서
 * 레벨별 경험치를 개별 설정할 수 있다. 예시:
 * <pre>
 * leveling:
 *   exp-per-level:
 *     1: 100
 *     2: 250
 *     3: 500
 *     ...
 *     100: 500000
 * </pre>
 * 설정되지 않은 레벨은 {@code base-exp-required × 레벨} 공식으로 폴백한다.
 */
public final class ExperienceProgression {

    /** 레벨 → 필요 경험치 테이블 (key = 레벨, value = 해당 레벨에서 필요한 exp) */
    private Map<Integer, Long> expTable;
    private long baseExpRequired;
    private int maxLevel;

    public ExperienceProgression(JobCoreConfig config) {
        applyConfig(config);
    }

    public void applyConfig(JobCoreConfig config) {
        this.expTable = config.getExpPerLevel();
        this.baseExpRequired = config.getBaseExpRequired();
        this.maxLevel = config.getMaxLevel();
    }

    /**
     * 지정 레벨에서 다음 레벨로 올라가기 위해 필요한 경험치.
     * <ol>
     *   <li>config의 exp-per-level 테이블에 해당 레벨 항목이 있으면 그 값 사용</li>
     *   <li>없으면 {@code base-exp-required × 레벨} 공식으로 계산</li>
     * </ol>
     *
     * @param level 현재 레벨 (1 이상)
     * @return 다음 레벨까지 필요한 경험치
     */
    public long getRequiredForLevel(int level) {
        int clamped = Math.max(1, level);

        // 테이블 우선 조회
        if (expTable != null) {
            Long tableValue = expTable.get(clamped);
            if (tableValue != null && tableValue > 0) {
                return tableValue;
            }
        }

        // 폴백: 공식 계산
        return baseExpRequired * clamped;
    }

    public long getRequiredForNextLevel(PlayerJobProfile profile) {
        return getRequiredForLevel(profile.getLevel());
    }

    public long getExpUntilNextLevel(PlayerJobProfile profile) {
        long required = getRequiredForNextLevel(profile);
        long remaining = required - profile.getExperience();
        return Math.max(0L, remaining);
    }

    public int getMaxLevel() {
        return maxLevel;
    }
}
