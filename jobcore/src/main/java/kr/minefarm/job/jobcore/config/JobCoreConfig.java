package kr.minefarm.job.jobcore.config;

import org.bukkit.configuration.file.FileConfiguration;

public final class JobCoreConfig {

    private final DatabaseSettings database;
    private final int jobChangeCooldownDays;
    private final long asyncFlushIntervalTicks;
    private final int asyncShutdownTimeoutSeconds;
    private final long baseExpRequired;
    private final int maxLevel;
    private final int statPointsPerLevel;
    private final boolean blockVanillaExpOrbs;
    private final int maxStatLevel;
    private final boolean velocitySupport;

    public JobCoreConfig(FileConfiguration config) {
        this.database = new DatabaseSettings(
                config.getString("database.host", "localhost"),
                config.getInt("database.port", 3306),
                config.getString("database.database", "minefarm_job"),
                config.getString("database.username", "root"),
                config.getString("database.password", ""),
                config.getInt("database.pool-size", 10),
                config.getLong("database.connection-timeout-ms", 30_000L)
        );
        this.jobChangeCooldownDays = config.getInt("job-change-cooldown-days", 10);
        this.asyncFlushIntervalTicks = config.getLong("async-save.flush-interval-ticks", 100L);
        this.asyncShutdownTimeoutSeconds = config.getInt("async-save.shutdown-timeout-seconds", 30);
        this.baseExpRequired = config.getLong("leveling.base-exp-required", 100L);
        this.maxLevel = config.getInt("leveling.max-level", 100);
        this.statPointsPerLevel = config.getInt("leveling.stat-points-per-level", 1);
        this.blockVanillaExpOrbs = config.getBoolean("experience.block-vanilla-orbs", true);
        this.maxStatLevel = config.getInt("stats.max-level", 25);
        this.velocitySupport = config.getBoolean("velocity-support", false);
    }

    public DatabaseSettings getDatabase() { return database; }
    public int getJobChangeCooldownDays() { return jobChangeCooldownDays; }
    public long getAsyncFlushIntervalTicks() { return asyncFlushIntervalTicks; }
    public int getAsyncShutdownTimeoutSeconds() { return asyncShutdownTimeoutSeconds; }
    public long getBaseExpRequired() { return baseExpRequired; }
    public int getMaxLevel() { return maxLevel; }
    public int getStatPointsPerLevel() { return statPointsPerLevel; }
    public boolean isBlockVanillaExpOrbs() { return blockVanillaExpOrbs; }
    public int getMaxStatLevel() { return maxStatLevel; }
    public boolean isVelocitySupport() { return velocitySupport; }
}
