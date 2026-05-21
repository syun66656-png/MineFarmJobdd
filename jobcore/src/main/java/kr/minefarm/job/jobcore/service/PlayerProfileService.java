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
 * <p>
 * {@link #loadOrCreate} 는 {@link ConcurrentHashMap#computeIfAbsent} 기반으로
 * 동일 UUID 에 대한 동시 호출이 두 개의 인스턴스를 만드는 레이스를 방지한다.
 */
public final class PlayerProfileService {

    private final PlayerJobRepository repository;

    /**
     * 로드 완료된 프로필 캐시 (직접 조회용).
     */
    private final Map<UUID, PlayerJobProfile> cache = new ConcurrentHashMap<>();

    /**
     * 진행 중인 로드 Future 캐시 — 같은 UUID 로 동시 요청이 와도 하나만 실행됨.
     */
    private final Map<UUID, CompletableFuture<PlayerJobProfile>> futureCache = new ConcurrentHashMap<>();

    private final AtomicInteger pendingWrites = new AtomicInteger(0);

    public PlayerProfileService(PlayerJobRepository repository) {
        this.repository = repository;
    }

    public CompletableFuture<PlayerJobProfile> loadOrCreate(Player player) {
        return loadOrCreate(player.getUniqueId());
    }

    public CompletableFuture<PlayerJobProfile> loadOrCreate(UUID uuid) {
        // futureCache.computeIfAbsent 로 동시 로드 레이스 제거
        return futureCache.computeIfAbsent(uuid, id ->
                repository.loadAsync(id).thenApply(optional -> {
                    PlayerJobProfile profile = optional.orElseGet(() -> new PlayerJobProfile(id));
                    cache.put(id, profile);
                    return profile;
                })
        );
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

    /**
     * 관리자용: 플레이어 프로필을 초기 상태로 리셋한다.
     * - 캐시·futureCache 제거
     * - 새 빈 PlayerJobProfile 생성 후 저장
     * - 결과: 직업/레벨/경험치/스탯 모두 초기값 (JobId.NONE, level 1, exp 0, stats 0)
     */
    public CompletableFuture<Void> resetAsync(UUID uuid) {
        cache.remove(uuid);
        futureCache.remove(uuid);
        PlayerJobProfile fresh = new PlayerJobProfile(uuid);
        cache.put(uuid, fresh);
        pendingWrites.incrementAndGet();
        return repository.saveAsync(fresh).whenComplete((ignored, throwable) ->
                pendingWrites.decrementAndGet());
    }

    public void unload(UUID uuid) {
        futureCache.remove(uuid);
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
