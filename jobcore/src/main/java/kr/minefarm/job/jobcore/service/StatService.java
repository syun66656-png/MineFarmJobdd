package kr.minefarm.job.jobcore.service;

import kr.minefarm.job.jobcore.config.JobCoreConfig;
import kr.minefarm.job.jobcore.domain.JobId;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobcore.domain.StatType;
import kr.minefarm.job.jobcore.registry.JobRegistry;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;

/**
 * 스탯 투자·자동판매 토글 비즈니스 로직.
 * <p>
 * check-then-act 가 atomic 해야 하므로 메인 스레드 직렬화 패턴을 사용한다.
 * loadOrCreate 이후의 모든 mutation 은 {@code runTask} 블록 안에서만 실행된다.
 */
public final class StatService {

    public enum InvestResult { SUCCESS, NO_POINTS, MAX_LEVEL, NO_JOB }
    public enum ToggleResult { SUCCESS, NOT_MINER, NO_JOB }

    private final JavaPlugin plugin;
    private final JobRegistry jobRegistry;
    private final PlayerProfileService profileService;
    private int maxStatLevel;

    public StatService(
            JavaPlugin plugin,
            PlayerProfileService profileService,
            JobRegistry jobRegistry,
            JobCoreConfig config
    ) {
        this.plugin = plugin;
        this.profileService = profileService;
        this.jobRegistry = jobRegistry;
        applyConfig(config);
    }

    public void applyConfig(JobCoreConfig config) {
        this.maxStatLevel = config.getMaxStatLevel();
    }

    public int getMaxStatLevel() { return maxStatLevel; }
    public PlayerProfileService getProfileService() { return profileService; }

    /**
     * 스탯 투자. check-then-act 를 메인 스레드에서 직렬화하여 더블 클릭 레이스를 방지한다.
     */
    public CompletableFuture<InvestResult> investAsync(Player player, StatType statType) {
        return profileService.loadOrCreate(player).thenCompose(profile -> {
            CompletableFuture<InvestResult> done = new CompletableFuture<>();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!profile.getJobId().hasJob()) {
                    done.complete(InvestResult.NO_JOB);
                    return;
                }
                if (profile.getStatPoints() <= 0) {
                    done.complete(InvestResult.NO_POINTS);
                    return;
                }
                if (profile.getStatLevel(statType) >= maxStatLevel) {
                    done.complete(InvestResult.MAX_LEVEL);
                    return;
                }
                // 메인 스레드 직렬화 — 동시 클릭이 와도 순차 처리됨
                profile.setStatPoints(profile.getStatPoints() - 1);
                profile.setStatLevel(statType, profile.getStatLevel(statType) + 1);
                profile.setInvestedStats(profile.getInvestedStats() + 1);

                // 패시브 갱신
                jobRegistry.find(profile.getJobId()).ifPresent(job ->
                        job.refreshPassiveEffects(player, profile));

                // fire-and-forget 저장 (실패 시 markDirty 복원됨)
                profileService.saveAsync(profile);
                done.complete(InvestResult.SUCCESS);
            });
            return done;
        });
    }

    /**
     * 자동판매 토글. 메인 스레드에서 처리하여 동시 토글 레이스를 방지한다.
     */
    public CompletableFuture<ToggleResult> toggleAutoSellAsync(Player player) {
        return profileService.loadOrCreate(player).thenCompose(profile -> {
            CompletableFuture<ToggleResult> done = new CompletableFuture<>();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!profile.getJobId().hasJob()) {
                    done.complete(ToggleResult.NO_JOB);
                    return;
                }
                if (profile.getJobId() != JobId.MINER) {
                    done.complete(ToggleResult.NOT_MINER);
                    return;
                }
                profile.setAutoSellEnabled(!profile.isAutoSellEnabled());
                profileService.saveAsync(profile);
                done.complete(ToggleResult.SUCCESS);
            });
            return done;
        });
    }
}
