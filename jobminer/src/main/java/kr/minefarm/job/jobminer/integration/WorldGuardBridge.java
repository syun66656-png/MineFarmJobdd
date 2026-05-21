package kr.minefarm.job.jobminer.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Set;
import java.util.logging.Logger;

/**
 * WorldGuard 리전(region) 검사 — Reflection 기반.
 * <p>
 * WorldGuard가 서버에 없어도 빌드/실행 가능하다. 클래스 로딩은 lazy로 시도하며,
 * 실패 시 모든 위치를 "허용"으로 간주(= WorldGuard 없는 환경에선 기존 동작 유지).
 */
public final class WorldGuardBridge {

    private final Logger logger;
    private final boolean available;
    // WorldGuard reflection 핸들
    private Object regionContainer;     // com.sk89q.worldguard.protection.regions.RegionContainer
    private Method getApplicableRegionsMethod;
    private Method bukkitAdaptLocation; // com.sk89q.worldedit.bukkit.BukkitAdapter#adapt(Location)

    public WorldGuardBridge(Logger logger) {
        this.logger = logger;
        this.available = init();
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * 해당 위치가 allowedRegionIds 중 하나에 포함되는지 검사.
     * allowedRegionIds 가 비어 있으면 true(제한 없음).
     */
    public boolean isInAnyRegion(Location loc, Set<String> allowedRegionIds) {
        if (allowedRegionIds == null || allowedRegionIds.isEmpty()) return true;
        if (!available || loc == null) return true; // WorldGuard 없으면 허용 (서버 운영자 책임)
        try {
            Object weLocation = bukkitAdaptLocation.invoke(null, loc);
            Object applicableSet = getApplicableRegionsMethod.invoke(regionContainer.getClass()
                    .getMethod("createQuery").invoke(regionContainer), weLocation);
            // applicableSet: ApplicableRegionSet — 반복하며 ProtectedRegion.getId() 검사
            Iterable<?> iterable = (Iterable<?>) applicableSet;
            for (Object region : iterable) {
                String id = (String) region.getClass().getMethod("getId").invoke(region);
                if (id != null && allowedRegionIds.contains(id)) return true;
            }
            return false;
        } catch (Throwable t) {
            // reflection 실패 시 로그 한 번만 — 일단 허용 (서버 마비 방지)
            return true;
        }
    }

    private boolean init() {
        Plugin wg = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (wg == null || !wg.isEnabled()) {
            logger.info("[JobMiner] WorldGuard 미설치 — 리전 검사 비활성화");
            return false;
        }
        try {
            Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object instance = wgClass.getMethod("getInstance").invoke(null);
            Object platform = wgClass.getMethod("getPlatform").invoke(instance);
            this.regionContainer = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            // createQuery() 와 getApplicableRegions(Location) 동적 탐색
            Class<?> queryClass = Class.forName("com.sk89q.worldguard.protection.regions.RegionQuery");
            Class<?> weLocClass = Class.forName("com.sk89q.worldedit.util.Location");
            this.getApplicableRegionsMethod = queryClass.getMethod("getApplicableRegions", weLocClass);
            Class<?> bukkitAdapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            this.bukkitAdaptLocation = bukkitAdapter.getMethod("adapt", Location.class);
            logger.info("[JobMiner] WorldGuard 연동 OK");
            return true;
        } catch (Throwable t) {
            logger.warning("[JobMiner] WorldGuard 발견했지만 연동 실패: " + t.getMessage());
            return false;
        }
    }
}
