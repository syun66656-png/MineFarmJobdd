package kr.minefarm.job.jobminer.integration;

import org.bukkit.Location;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * WorldGuard API를 Reflection으로 호출.
 * <p>
 * WorldGuard 의존성을 maven에 추가하지 않고 동작하도록 reflection 사용.
 * Bridge에서 lazy 초기화하며, 실패 시 모든 위치를 "허용"으로 처리.
 */
final class WorldGuardChecker {

    private final Object regionContainer;             // RegionContainer instance
    private final Method bukkitAdapt;                 // BukkitAdapter#adapt(Location)
    private final Method createQuery;                 // RegionContainer#createQuery()
    private final Method getApplicableRegions;        // RegionQuery#getApplicableRegions(Location)
    private final Method getId;                       // ProtectedRegion#getId()

    private WorldGuardChecker(
            Object regionContainer,
            Method bukkitAdapt,
            Method createQuery,
            Method getApplicableRegions,
            Method getId
    ) {
        this.regionContainer = regionContainer;
        this.bukkitAdapt = bukkitAdapt;
        this.createQuery = createQuery;
        this.getApplicableRegions = getApplicableRegions;
        this.getId = getId;
    }

    /**
     * WorldGuard 클래스를 reflection으로 탐색.
     * 실패 시 null 반환.
     */
    static WorldGuardChecker tryCreate() {
        try {
            Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object wgInstance = wgClass.getMethod("getInstance").invoke(null);
            Object platform = wgClass.getMethod("getPlatform").invoke(wgInstance);
            Object regionContainer = platform.getClass().getMethod("getRegionContainer").invoke(platform);

            Class<?> bukkitAdapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Method bukkitAdapt = bukkitAdapterClass.getMethod("adapt", Location.class);

            Class<?> regionContainerClass = Class.forName("com.sk89q.worldguard.protection.regions.RegionContainer");
            Method createQuery = regionContainerClass.getMethod("createQuery");

            Class<?> regionQueryClass = Class.forName("com.sk89q.worldguard.protection.regions.RegionQuery");
            Class<?> worldEditLocation = Class.forName("com.sk89q.worldedit.util.Location");
            Method getApplicableRegions = regionQueryClass.getMethod("getApplicableRegions", worldEditLocation);

            Class<?> protectedRegionClass = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion");
            Method getId = protectedRegionClass.getMethod("getId");

            return new WorldGuardChecker(regionContainer, bukkitAdapt, createQuery, getApplicableRegions, getId);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 해당 Location이 allowedRegionIds 중 하나에 속하는지 검사. */
    boolean isInside(Location bukkitLoc, Set<String> allowedRegionIds) throws Throwable {
        Object weLocation = bukkitAdapt.invoke(null, bukkitLoc);
        Object query = createQuery.invoke(regionContainer);
        Object applicableSet = getApplicableRegions.invoke(query, weLocation);

        if (!(applicableSet instanceof Iterable<?> iterable)) return false;
        for (Object region : iterable) {
            Object id = getId.invoke(region);
            if (id instanceof String idStr && allowedRegionIds.contains(idStr)) {
                return true;
            }
        }
        return false;
    }
}
