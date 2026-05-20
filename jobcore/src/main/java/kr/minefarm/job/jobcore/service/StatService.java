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
 */
public final class StatService {

    public enum InvestResult {
        SUCCESS,
        NO_POINTS,
        MAX_LEVEL,
        NO_JOB
    }

    public enum ToggleResult {
        SUCCESS,
        NOT_MINER,
        NO_JOB
    }

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

    public int getMaxStatLevel() {
        return maxStatLevel;
    }

    public PlayerProfileService getProfileService() {
        return profileService;
    }

    public CompletableFuture<InvestResult> investAsync(Player player, StatType statType) {
        return profileService.loadOrCreate(player).thenCompose(profile -> {
            if (!profile.getJobId().hasJob()) {
                return CompletableFuture.completedFuture(InvestResult.NO_JOB);
            }
            if (profile.getStatPoints() <= 0) {
                return CompletableFuture.completedFuture(InvestResult.NO_POINTS);
            }
            if (profile.getStatLevel(statType) >= maxStatLevel) {
                return CompletableFuture.completedFuture(InvestResult.MAX_LEVEL);
            }

            profile.setStatPoints(profile.getStatPoints() - 1);
            profile.setStatLevel(statType, profile.getStatLevel(statType) + 1);
            profile.setInvestedStats(profile.getInvestedStats() + 1);

            return profileService.saveAsync(profile).whenComplete((ignored, throwable) ->
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline() || throwable != null) {
                            return;
                        }
                        PlayerJobProfile cached = profileService.getCached(player.getUniqueId());
                        if (cached == null) {
                            return;
                        }
                        jobRegistry.find(cached.getJobId()).ifPresent(job ->
                                job.refreshPassiveEffects(player, cached));
                    })).thenApply(ignored -> InvestResult.SUCCESS);
        });
    }

    public CompletableFuture<ToggleResult> toggleAutoSellAsync(Player player) {
        return profileService.loadOrCreate(player).thenCompose(profile -> {
            if (!profile.getJobId().hasJob()) {
                return CompletableFuture.completedFuture(ToggleResult.NO_JOB);
            }
            if (profile.getJobId() != JobId.MINER) {
                return CompletableFuture.completedFuture(ToggleResult.NOT_MINER);
            }
            profile.setAutoSellEnabled(!profile.isAutoSellEnabled());
            return profileService.saveAsync(profile).thenApply(ignored -> ToggleResult.SUCCESS);
        });
    }
}
