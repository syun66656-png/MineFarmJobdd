package kr.minefarm.job.jobcore.bootstrap;

import kr.minefarm.job.jobcore.api.DatabaseManager;
import kr.minefarm.job.jobcore.api.JobCoreAPI;
import kr.minefarm.job.jobcore.api.VelocityProvider;
import kr.minefarm.job.jobcore.domain.JobId;
import kr.minefarm.job.jobcore.registry.JobRegistry;
import kr.minefarm.job.jobcore.service.JobExperienceService;
import kr.minefarm.job.jobcore.service.JobService;
import kr.minefarm.job.jobcore.service.PlayerProfileService;
import kr.minefarm.job.jobcore.service.RankingService;
import kr.minefarm.job.jobcore.service.StatService;

import java.util.UUID;

public final class JobCoreAPIImpl implements JobCoreAPI {

    private final DatabaseManager database;
    private final JobRegistry jobRegistry;
    private final PlayerProfileService playerProfiles;
    private final JobService jobService;
    private final JobExperienceService jobExperience;
    private final RankingService ranking;
    private final StatService statService;
    private final VelocityProvider velocity;

    public JobCoreAPIImpl(
            DatabaseManager database,
            JobRegistry jobRegistry,
            PlayerProfileService playerProfiles,
            JobService jobService,
            JobExperienceService jobExperience,
            RankingService ranking,
            StatService statService,
            VelocityProvider velocity
    ) {
        this.database = database;
        this.jobRegistry = jobRegistry;
        this.playerProfiles = playerProfiles;
        this.jobService = jobService;
        this.jobExperience = jobExperience;
        this.ranking = ranking;
        this.statService = statService;
        this.velocity = velocity;
    }

    @Override
    public DatabaseManager getDatabase() {
        return database;
    }

    @Override
    public JobRegistry getJobRegistry() {
        return jobRegistry;
    }

    @Override
    public PlayerProfileService getPlayerProfiles() {
        return playerProfiles;
    }

    @Override
    public JobService getJobService() {
        return jobService;
    }

    @Override
    public JobExperienceService getJobExperience() {
        return jobExperience;
    }

    @Override
    public RankingService getRanking() {
        return ranking;
    }

    @Override
    public int getRank(UUID playerId, JobId jobId) {
        return ranking.getRank(playerId, jobId);
    }

    @Override
    public StatService getStatService() {
        return statService;
    }

    @Override
    public VelocityProvider getVelocity() {
        return velocity;
    }
}
