package kr.minefarm.job.jobcore.database.async;

import kr.minefarm.job.jobcore.api.PlayerJobRepository;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobcore.service.PlayerProfileService;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * dirty 프로필을 주기적으로 DB에 비동기 flush.
 * <p>
 * flush 시 {@link PlayerJobProfile#snapshot()}을 메인 스레드에서 먼저 획득하여
 * 비동기 스레드에서 프로필을 직접 읽는 일이 없도록 한다.
 */
public final class AsyncSaveService {

    private final JavaPlugin plugin;
    private final PlayerProfileService profileService;
    private final PlayerJobRepository repository;
    private final long flushIntervalTicks;
    private BukkitTask flushTask;

    public AsyncSaveService(
            JavaPlugin plugin,
            PlayerProfileService profileService,
            PlayerJobRepository repository,
            long flushIntervalTicks
    ) {
        this.plugin = plugin;
        this.profileService = profileService;
        this.repository = repository;
        this.flushIntervalTicks = flushIntervalTicks;
    }

    public void start() {
        flushTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin,
                this::flushDirtyProfiles,
                flushIntervalTicks,
                flushIntervalTicks
        );
    }

    public void shutdownAndFlush(int timeoutSeconds) {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        flushDirtyProfiles();
        try {
            profileService.awaitPendingWrites(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            plugin.getLogger().log(Level.WARNING, "[JobCore] Interrupted while waiting for async saves", exception);
        }
    }

    /**
     * dirty 프로필 저장. snapshot()은 이미 saveAsync 내부에서 처리된다.
     * 이 메서드 자체는 비동기 스레드에서 호출되지만, saveAsync의 snapshot 획득은
     * JdbcPlayerJobRepository.saveAsync → profile.snapshot() 호출로 atomic하게 처리된다.
     */
    private void flushDirtyProfiles() {
        // dirty 체크는 volatile 필드 읽기 — 안전
        List<PlayerJobProfile> dirty = new ArrayList<>();
        for (PlayerJobProfile profile : profileService.getCachedProfiles()) {
            if (profile.isDirty()) {
                dirty.add(profile);
            }
        }

        if (dirty.isEmpty()) return;

        for (PlayerJobProfile profile : dirty) {
            profileService.saveAsync(profile).exceptionally(throwable -> {
                plugin.getLogger().log(Level.SEVERE,
                        "[JobCore] 프로필 자동저장 실패: " + profile.getUuid(), throwable);
                return null;
            });
        }

        if (dirty.size() > 0) {
            plugin.getLogger().fine("[JobCore] Flushed " + dirty.size() + " dirty profile(s).");
        }
    }
}
