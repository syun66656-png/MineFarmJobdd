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

    /**
     * 직업 변경. 반환 Boolean:
     * - true  : 변경 성공
     * - false : 쿨타임·미등록·이미 같은 직업 등으로 변경 불가
     * 예외 전파 대신 handle() 로 처리하여 호출부가 항상 thenAccept 를 받을 수 있다.
     */
    public CompletableFuture<Boolean> changeJob(Player player, JobId newJobId) {
        return profileService.loadOrCreate(player).thenCompose(profile -> {
            CompletableFuture<Boolean> done = new CompletableFuture<>();
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    applyJobChangeOnMainThread(player, profile, newJobId, done));
            return done;
        });
    }

    /**
     * UUID 기반 직업 변경 — 오프라인 플레이어 포함.
     * onSelect/onDeselect 훅은 플레이어 온라인일 때만 호출됨.
     * 관리자 도구 전용. 반환값은 changeJob과 동일 (true=성공/false=쿨타임 등).
     */
    public CompletableFuture<Boolean> changeJobByUuid(java.util.UUID targetUuid, JobId newJobId) {
        return profileService.loadOrCreate(targetUuid).thenCompose(profile -> {
            CompletableFuture<Boolean> done = new CompletableFuture<>();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Player online = plugin.getServer().getPlayer(targetUuid);
                if (online != null) {
                    // 온라인 — 일반 플로우와 동일 (onDeselect/onSelect 호출)
                    applyJobChangeOnMainThread(online, profile, newJobId, done);
                } else {
                    // 오프라인 — DB만 업데이트
                    applyJobChangeOffline(profile, newJobId, done);
                }
            });
            return done;
        });
    }

    private void applyJobChangeOffline(
            PlayerJobProfile profile,
            JobId newJobId,
            CompletableFuture<Boolean> done
    ) {
        if (!canChangeJob(profile)) { done.complete(false); return; }
        if (!newJobId.hasJob() && profile.getJobId() == JobId.NONE) { done.complete(false); return; }
        if (newJobId.hasJob() && !jobRegistry.isRegistered(newJobId)) { done.complete(false); return; }
        if (profile.getJobId() == newJobId) { done.complete(true); return; }

        profile.setJobId(newJobId);
        profile.setLastJobChangeAt(Instant.now());

        profileService.saveAsync(profile).handle((ignored, throwable) -> {
            if (throwable != null) {
                plugin.getLogger().log(Level.WARNING,
                        "[JobCore] saveAsync(offline) failed for " + profile.getUuid(),
                        throwable);
                done.complete(false);
            } else {
                done.complete(true);
            }
            return null;
        });
    }

    private void applyJobChangeOnMainThread(
            Player player,
            PlayerJobProfile profile,
            JobId newJobId,
            CompletableFuture<Boolean> done
    ) {
        if (!player.isOnline()) { done.complete(false); return; }
        if (!canChangeJob(profile)) { done.complete(false); return; }
        if (!newJobId.hasJob() && profile.getJobId() == JobId.NONE) { done.complete(false); return; }
        if (newJobId.hasJob() && !jobRegistry.isRegistered(newJobId)) { done.complete(false); return; }

        JobId previousId = profile.getJobId();
        if (previousId == newJobId) { done.complete(true); return; }

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

        // handle()로 예외 전파 대신 false 반환 — thenAccept가 항상 발화됨
        profileService.saveAsync(profile).handle((ignored, throwable) -> {
            if (throwable != null) {
                plugin.getLogger().log(Level.SEVERE,
                        "[JobCore] 직업 변경 저장 실패 (player=" + player.getName() + ")", throwable);
                done.complete(false);
            } else {
                done.complete(true);
            }
            return null;
        });
    }

    public boolean canChangeJob(PlayerJobProfile profile) {
        Instant lastChange = profile.getLastJobChangeAt();
        if (lastChange == null) return true;
        return Duration.between(lastChange, Instant.now()).compareTo(changeCooldown) >= 0;
    }

    public Duration getRemainingCooldown(PlayerJobProfile profile) {
        Instant lastChange = profile.getLastJobChangeAt();
        if (lastChange == null) return Duration.ZERO;
        Duration remaining = changeCooldown.minus(Duration.between(lastChange, Instant.now()));
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public Optional<Job> getActiveJob(Player player) {
        PlayerJobProfile profile = profileService.getCached(player.getUniqueId());
        if (profile == null) return Optional.empty();
        return jobRegistry.find(profile.getJobId());
    }
}
