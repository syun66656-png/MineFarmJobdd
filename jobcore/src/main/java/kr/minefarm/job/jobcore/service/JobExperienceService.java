package kr.minefarm.job.jobcore.service;

import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobcore.registry.JobRegistry;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;

/**
 * 직업 전용 경험치 지급 (DB 프로필). 바닐라 구슬을 생성하지 않는다.
 */
public final class JobExperienceService {

    private final JavaPlugin plugin;
    private final PlayerProfileService profileService;
    private final JobRegistry jobRegistry;

    public JobExperienceService(
            JavaPlugin plugin,
            PlayerProfileService profileService,
            JobRegistry jobRegistry
    ) {
        this.plugin = plugin;
        this.profileService = profileService;
        this.jobRegistry = jobRegistry;
    }

    public CompletableFuture<PlayerJobProfile> grantExperience(Player player, long amount) {
        if (amount <= 0) {
            return profileService.loadOrCreate(player);
        }
        return profileService.loadOrCreate(player).thenCompose(profile -> {
            profile.setExperience(profile.getExperience() + amount);
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
                    })).thenApply(ignored -> profile);
        });
    }
}
