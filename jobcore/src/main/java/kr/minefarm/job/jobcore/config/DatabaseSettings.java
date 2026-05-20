package kr.minefarm.job.jobcore.config;

/**
 * config.yml database 섹션 매핑.
 */
public record DatabaseSettings(
        String host,
        int port,
        String database,
        String username,
        String password,
        int poolSize,
        long connectionTimeoutMs
) {
}
