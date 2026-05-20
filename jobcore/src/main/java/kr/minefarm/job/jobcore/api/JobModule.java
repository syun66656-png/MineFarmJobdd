package kr.minefarm.job.jobcore.api;

import java.util.Collection;

/**
 * 직업 확장 모듈 계약 (JobMiner, JobFarmer, JobHunter …).
 * JobCore와 독립적으로 enable/disable 되며, {@link Job} 정의를 등록한다.
 */
public interface JobModule {

    String getModuleId();

    Collection<Job> getJobs();

    void onEnable(JobContext context);

    void onDisable();
}
