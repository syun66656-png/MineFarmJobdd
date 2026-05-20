package kr.minefarm.job.jobcore.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JobCoreConfig {

    private final DatabaseSettings database;
    private final int jobChangeCooldownDays;
    private final long asyncFlushIntervalTicks;
    private final int asyncShutdownTimeoutSeconds;
    private final long baseExpRequired;
    private final int maxLevel;
    private final int statPointsPerLevel;
    /** 레벨 → 필요 경험치. 비어 있으면 baseExpRequired × 레벨 공식 사용. */
    private final Map<Integer, Long> expPerLevel;
    private final boolean blockVanillaExpOrbs;
    private final int maxStatLevel;
    private final boolean velocitySupport;
    private final int rankingTopN;

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
        this.expPerLevel = loadExpPerLevel(config);
        this.blockVanillaExpOrbs = config.getBoolean("experience.block-vanilla-orbs", true);
        this.maxStatLevel = config.getInt("stats.max-level", 25);
        this.velocitySupport = config.getBoolean("velocity-support", false);
        this.rankingTopN = config.getInt("ranking.top-n", 100);
    }

    /**
     * {@code leveling.exp-per-level} 섹션을 읽어 레벨→경험치 맵을 구성한다.
     * 섹션이 없거나 비어 있으면 빈 맵을 반환하여 폴백 공식을 사용하게 한다.
     */
    private static Map<Integer, Long> loadExpPerLevel(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("leveling.exp-per-level");
        if (section == null) {
            return Collections.emptyMap();
        }
        Map<Integer, Long> map = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            try {
                int level = Integer.parseInt(key);
                long exp = section.getLong(key, 0L);
                if (level >= 1 && exp > 0) {
                    map.put(level, exp);
                }
            } catch (NumberFormatException ignored) {
                // 숫자가 아닌 키는 무시
            }
        }
        return Collections.unmodifiableMap(map);
    }

    public DatabaseSettings getDatabase() { return database; }
    public int getJobChangeCooldownDays() { return jobChangeCooldownDays; }
    public long getAsyncFlushIntervalTicks() { return asyncFlushIntervalTicks; }
    public int getAsyncShutdownTimeoutSeconds() { return asyncShutdownTimeoutSeconds; }
    public long getBaseExpRequired() { return baseExpRequired; }
    public int getMaxLevel() { return maxLevel; }
    public int getStatPointsPerLevel() { return statPointsPerLevel; }
    public Map<Integer, Long> getExpPerLevel() { return expPerLevel; }
    public boolean isBlockVanillaExpOrbs() { return blockVanillaExpOrbs; }
    public int getMaxStatLevel() { return maxStatLevel; }
    public boolean isVelocitySupport() { return velocitySupport; }
    public int getRankingTopN() { return rankingTopN; }
}
