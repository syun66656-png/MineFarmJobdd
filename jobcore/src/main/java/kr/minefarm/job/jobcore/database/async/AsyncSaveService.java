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
            plugin.getLogger().log(Level.WARNING, "Interrupted while waiting for async saves", exception);
        }
    }

    private void flushDirtyProfiles() {
        List<PlayerJobProfile> dirty = new ArrayList<>();
        for (PlayerJobProfile profile : profileService.getCachedProfiles()) {
            if (profile.isDirty()) {
                dirty.add(profile);
            }
        }
        for (PlayerJobProfile profile : dirty) {
            repository.saveAsync(profile).exceptionally(throwable -> {
                plugin.getLogger().log(Level.SEVERE,
                        "Failed to save profile for " + profile.getUuid(), throwable);
                return null;
            });
        }
    }
}
