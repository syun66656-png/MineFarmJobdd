package kr.minefarm.job.jobcore.api;

import kr.minefarm.job.jobcore.domain.JobId;
import kr.minefarm.job.jobcore.registry.JobRegistry;
import kr.minefarm.job.jobcore.service.JobExperienceService;
import kr.minefarm.job.jobcore.service.JobService;
import kr.minefarm.job.jobcore.service.PlayerProfileService;
import kr.minefarm.job.jobcore.service.RankingService;
import kr.minefarm.job.jobcore.service.StatService;

import java.util.UUID;

/**
 * JobCore가 직업 모듈에 노출하는 공개 API.
 */
public interface JobCoreAPI {

    DatabaseManager getDatabase();

    JobRegistry getJobRegistry();

    PlayerProfileService getPlayerProfiles();

    JobService getJobService();

    JobExperienceService getJobExperience();

    RankingService getRanking();

    StatService getStatService();

    VelocityProvider getVelocity();

    /**
     * 특정 직업 보드에서의 순위 (1부터). 없으면 {@code -1}.
     */
    default int getRank(UUID playerId, JobId jobId) {
        return getRanking().getRank(playerId, jobId);
    }
}
