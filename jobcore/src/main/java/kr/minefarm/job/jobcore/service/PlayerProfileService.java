package kr.minefarm.job.jobcore.service;

import kr.minefarm.job.jobcore.api.PlayerJobRepository;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 메모리 캐시 + 비동기 로드/저장.
 */
public final class PlayerProfileService {

    private final PlayerJobRepository repository;
    private final Map<UUID, PlayerJobProfile> cache = new ConcurrentHashMap<>();
    private final AtomicInteger pendingWrites = new AtomicInteger(0);

    public PlayerProfileService(PlayerJobRepository repository) {
        this.repository = repository;
    }

    public CompletableFuture<PlayerJobProfile> loadOrCreate(Player player) {
        return loadOrCreate(player.getUniqueId());
    }

    public CompletableFuture<PlayerJobProfile> loadOrCreate(UUID uuid) {
        PlayerJobProfile cached = cache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return repository.loadAsync(uuid).thenApply(optional -> {
            PlayerJobProfile profile = optional.orElseGet(() -> new PlayerJobProfile(uuid));
            cache.put(uuid, profile);
            return profile;
        });
    }

    public PlayerJobProfile getCached(UUID uuid) {
        return cache.get(uuid);
    }

    public Collection<PlayerJobProfile> getCachedProfiles() {
        return cache.values();
    }

    public CompletableFuture<Void> saveAsync(PlayerJobProfile profile) {
        pendingWrites.incrementAndGet();
        return repository.saveAsync(profile).whenComplete((ignored, throwable) ->
                pendingWrites.decrementAndGet());
    }

    public void unload(UUID uuid) {
        PlayerJobProfile profile = cache.remove(uuid);
        if (profile != null && profile.isDirty()) {
            saveAsync(profile);
        }
    }

    public void awaitPendingWrites(int timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (pendingWrites.get() > 0) {
            if (System.nanoTime() > deadline) {
                break;
            }
            Thread.sleep(50L);
        }
    }
}
