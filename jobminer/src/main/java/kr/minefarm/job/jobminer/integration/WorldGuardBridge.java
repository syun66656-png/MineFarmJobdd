package kr.minefarm.job.jobminer.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.logging.Logger;

/**
 * WorldGuard 리전 검사.
 * <p>
 * WorldGuard가 서버에 없으면 {@link #available} = false → 모든 위치를 "허용"으로 처리.
 * WorldGuard 클래스 참조는 {@link WorldGuardChecker}에 격리되어 있어 미설치 환경에서도
 * NoClassDefFoundError 없이 동작한다.
 */
public final class WorldGuardBridge {

    private final Logger logger;
    private final boolean available;

    public WorldGuardBridge(Logger logger) {
        this.logger = logger;
        Plugin wg = Bukkit.getPluginManager().getPlugin("WorldGuard");
        boolean ok = false;
        if (wg != null && wg.isEnabled()) {
            try {
                // 클래스 로딩 가능 여부 확인 (실제 API 호출은 WorldGuardChecker에서)
                Class.forName("com.sk89q.worldguard.WorldGuard");
                ok = true;
                logger.info("[JobMiner] WorldGuard 연동 OK (v" + wg.getDescription().getVersion() + ")");
            } catch (Throwable t) {
                logger.warning("[JobMiner] WorldGuard 발견했지만 API 로딩 실패: " + t.getMessage());
            }
        } else {
            logger.info("[JobMiner] WorldGuard 미설치 — 리전 검사 비활성");
        }
        this.available = ok;
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * 해당 위치가 allowedRegionIds 중 하나에 속하는지 검사.
     * allowedRegionIds 비어 있으면 true(제한 없음).
     * WorldGuard 미설치 시 항상 true (모든 곳에서 효과 적용).
     */
    public boolean isInAnyRegion(Location loc, Set<String> allowedRegionIds) {
        if (allowedRegionIds == null || allowedRegionIds.isEmpty()) return true;
        if (!available || loc == null) return true;
        try {
            return WorldGuardChecker.isInside(loc, allowedRegionIds);
        } catch (Throwable t) {
            logger.warning("[JobMiner] WG 리전 검사 실패: " + t.getMessage());
            return true; // 실패 시 통과(서비스 마비 방지)
        }
    }
}
