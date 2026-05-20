package kr.minefarm.job.jobcore.service;

import kr.minefarm.job.jobcore.api.DatabaseManager;
import kr.minefarm.job.jobcore.domain.JobId;
import kr.minefarm.job.jobcore.domain.RankingEntry;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * 직업별 랭킹 메모리 캐시. 매시 정각·30분에 MariaDB 비동기 갱신.
 */
public final class RankingService {

    private static final long THIRTY_MINUTES_TICKS = 30L * 60L * 20L;

    private final JavaPlugin plugin;
    private final DatabaseManager databaseManager;
    private final RankingLeaderboardLoader loader;
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    private volatile Map<JobId, List<RankingEntry>> leaderboardCache = Map.of();
    private volatile Map<JobId, Map<UUID, Integer>> rankIndexCache = Map.of();
    private volatile Instant lastRefreshedAt;
    private BukkitTask scheduledTask;

    public RankingService(JavaPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.loader = new RankingLeaderboardLoader();
    }

    /**
     * 서버 기동 시 1회 즉시 갱신 + 정각/30분 스케줄 등록.
     */
    public void start() {
        refreshAsync();
        long initialDelay = ticksUntilNextHalfHour();
        scheduledTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin,
                this::refreshAsync,
                initialDelay,
                THIRTY_MINUTES_TICKS
        );
        plugin.getLogger().info("[JobCore] Ranking scheduler started (next in "
                + (initialDelay / 20) + "s, every 30 min).");
    }

    public void shutdown() {
        if (scheduledTask != null) {
            scheduledTask.cancel();
            scheduledTask = null;
        }
    }

    public CompletableFuture<Void> refreshAsync() {
        if (!refreshing.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }
        return databaseManager
                .supplyAsync(loader::load)
                .thenAccept(this::applyCache)
                .whenComplete((ignored, throwable) -> {
                    refreshing.set(false);
                    if (throwable != null) {
                        plugin.getLogger().log(Level.SEVERE, "[JobCore] Ranking refresh failed.", throwable);
                    } else {
                        plugin.getLogger().info("[JobCore] Ranking cache updated at " + lastRefreshedAt);
                    }
                });
    }

    /**
     * 직업 보드 내 순위 (1부터). 없으면 {@code -1}.
     */
    public int getRank(UUID playerId, JobId jobId) {
        if (playerId == null || jobId == null || !jobId.hasJob()) {
            return -1;
        }
        Map<UUID, Integer> index = rankIndexCache.get(jobId);
        if (index == null) {
            return -1;
        }
        return index.getOrDefault(playerId, -1);
    }

    public List<RankingEntry> getLeaderboard(JobId jobId) {
        if (jobId == null || !jobId.hasJob()) {
            return List.of();
        }
        return leaderboardCache.getOrDefault(jobId, List.of());
    }

    public Instant getLastRefreshedAt() {
        return lastRefreshedAt;
    }

    public boolean hasCache() {
        return lastRefreshedAt != null;
    }

    public String formatRank(int rank) {
        return rank > 0 ? String.valueOf(rank) : "-";
    }

    public String formatRank(UUID playerId, JobId jobId) {
        return formatRank(getRank(playerId, jobId));
    }

    private void applyCache(Map<JobId, List<RankingEntry>> loaded) {
        Map<JobId, List<RankingEntry>> boards = new EnumMap<>(JobId.class);
        Map<JobId, Map<UUID, Integer>> index = new EnumMap<>(JobId.class);

        for (Map.Entry<JobId, List<RankingEntry>> entry : loaded.entrySet()) {
            List<RankingEntry> copy = List.copyOf(entry.getValue());
            boards.put(entry.getKey(), copy);

            Map<UUID, Integer> jobIndex = new java.util.HashMap<>();
            for (RankingEntry row : copy) {
                jobIndex.put(row.getUuid(), row.getRank());
            }
            index.put(entry.getKey(), Collections.unmodifiableMap(jobIndex));
        }

        this.leaderboardCache = Collections.unmodifiableMap(boards);
        this.rankIndexCache = Collections.unmodifiableMap(index);
        this.lastRefreshedAt = Instant.now();
    }

    private static long ticksUntilNextHalfHour() {
        LocalTime now = LocalTime.now();
        LocalTime next;
        if (now.getMinute() < 30) {
            next = now.withMinute(30).withSecond(0).withNano(0);
        } else {
            next = now.plusHours(1).withMinute(0).withSecond(0).withNano(0);
        }
        long seconds = Duration.between(now, next).getSeconds();
        return Math.max(20L, seconds * 20L);
    }
}
