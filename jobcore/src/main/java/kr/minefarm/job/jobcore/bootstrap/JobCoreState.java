package kr.minefarm.job.jobcore.bootstrap;

/**
 * JobCore 부트스트랩 진행 상태.
 */
public enum JobCoreState {
    CREATED,
    CONFIG_LOADED,
    DATABASE_READY,
    SERVICES_READY,
    INTEGRATIONS_READY,
    RUNNING,
    SHUTDOWN
}
