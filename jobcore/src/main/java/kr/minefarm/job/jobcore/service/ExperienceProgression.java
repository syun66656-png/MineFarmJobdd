package kr.minefarm.job.jobcore.service;

import kr.minefarm.job.jobcore.config.JobCoreConfig;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;

/**
 * 레벨별 필요 경험치 계산 (%jobcore_exp_max% 등에 사용).
 */
public final class ExperienceProgression {

    private long baseExpRequired;

    public ExperienceProgression(JobCoreConfig config) {
        applyConfig(config);
    }

    public void applyConfig(JobCoreConfig config) {
        this.baseExpRequired = config.getBaseExpRequired();
    }

    /** 현재 레벨에서 다음 레벨까지 필요한 총 경험치. */
    public long getRequiredForLevel(int level) {
        if (level < 1) {
            return baseExpRequired;
        }
        return baseExpRequired * level;
    }

    public long getRequiredForNextLevel(PlayerJobProfile profile) {
        return getRequiredForLevel(profile.getLevel());
    }

    public long getExpUntilNextLevel(PlayerJobProfile profile) {
        long required = getRequiredForNextLevel(profile);
        long remaining = required - profile.getExperience();
        return Math.max(0L, remaining);
    }
}
