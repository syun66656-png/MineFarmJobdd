package kr.minefarm.job.jobcore.service;

import kr.minefarm.job.jobcore.api.Job;
import kr.minefarm.job.jobcore.config.JobCoreConfig;
import kr.minefarm.job.jobcore.domain.JobId;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobcore.registry.JobRegistry;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * 직업 변경·쿨타임·모듈 Job 콜백 조율.
 * DB 로드는 비동기이며, Bukkit 엔티티/이벤트에 닿는 처리는 메인 스레드에서 수행한다.
 */
public final class JobService {

    private final JavaPlugin plugin;
    private final JobRegistry jobRegistry;
    private final PlayerProfileService profileService;
    private Duration changeCooldown;

    public JobService(
            JavaPlugin plugin,
            JobRegistry jobRegistry,
            PlayerProfileService profileService,
            JobCoreConfig config
    ) {
        this.plugin = plugin;
        this.jobRegistry = jobRegistry;
        this.profileService = profileService;
        applyConfig(config);
    }

    public void applyConfig(JobCoreConfig config) {
        this.changeCooldown = Duration.ofDays(config.getJobChangeCooldownDays());
    }

    public CompletableFuture<Boolean> changeJob(Player player, JobId newJobId) {
        return profileService.loadOrCreate(player).thenCompose(profile -> {
            CompletableFuture<Boolean> done = new CompletableFuture<>();
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    applyJobChangeOnMainThread(player, profile, newJobId, done));
            return done;
        });
    }

    private void applyJobChangeOnMainThread(
            Player player,
            PlayerJobProfile profile,
            JobId newJobId,
            CompletableFuture<Boolean> done
    ) {
        if (!player.isOnline()) {
            done.complete(false);
            return;
        }
        if (!canChangeJob(profile)) {
            done.complete(false);
            return;
        }
        if (!newJobId.hasJob() && profile.getJobId() == JobId.NONE) {
            done.complete(false);
            return;
        }
        if (newJobId.hasJob() && !jobRegistry.isRegistered(newJobId)) {
            done.complete(false);
            return;
        }

        JobId previousId = profile.getJobId();
        if (previousId == newJobId) {
            done.complete(true);
            return;
        }

        // onDeselect 실패 시에도 직업 데이터·onSelect·저장이 진행되도록 try / finally
        Optional<Job> previousJob = jobRegistry.find(previousId);
        try {
            previousJob.ifPresent(job -> {
                try {
                    job.onDeselect(player);
                } catch (Throwable throwable) {
                    plugin.getLogger().log(Level.WARNING,
                            "[JobCore] onDeselect failed (job=" + previousId + ", player=" + player.getName() + ")",
                            throwable);
                }
            });
        } finally {
            profile.setJobId(newJobId);
            profile.setLastJobChangeAt(Instant.now());

            if (newJobId.hasJob()) {
                try {
                    jobRegistry.find(newJobId).ifPresent(job -> job.onSelect(player));
                } catch (Throwable throwable) {
                    plugin.getLogger().log(Level.SEVERE,
                            "[JobCore] onSelect failed (job=" + newJobId + ", player=" + player.getName() + ")",
                            throwable);
                }
            }
        }

        profileService.saveAsync(profile).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                done.completeExceptionally(throwable);
            } else {
                done.complete(true);
            }
        });
    }

    public boolean canChangeJob(PlayerJobProfile profile) {
        Instant lastChange = profile.getLastJobChangeAt();
        if (lastChange == null) {
            return true;
        }
        return Duration.between(lastChange, Instant.now()).compareTo(changeCooldown) >= 0;
    }

    public Duration getRemainingCooldown(PlayerJobProfile profile) {
        Instant lastChange = profile.getLastJobChangeAt();
        if (lastChange == null) {
            return Duration.ZERO;
        }
        Duration elapsed = Duration.between(lastChange, Instant.now());
        Duration remaining = changeCooldown.minus(elapsed);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public Optional<Job> getActiveJob(Player player) {
        PlayerJobProfile profile = profileService.getCached(player.getUniqueId());
        if (profile == null) {
            return Optional.empty();
        }
        return jobRegistry.find(profile.getJobId());
    }
}
