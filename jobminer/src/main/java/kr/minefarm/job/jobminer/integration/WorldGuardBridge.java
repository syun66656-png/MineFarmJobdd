package kr.minefarm.job.jobminer.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.logging.Logger;

/**
 * WorldGuard 리전 검사.
 * <p>
 * WorldGuard 의존성 없이 Reflection으로 호출하므로 미설치 환경에서도 빌드/실행 가능.
 * 검사 실패 시 모든 위치를 "허용"으로 처리(서비스 마비 방지).
 */
public final class WorldGuardBridge {

    private final Logger logger;
    private final WorldGuardChecker checker; // null이면 미연동

    public WorldGuardBridge(Logger logger) {
        this.logger = logger;
        Plugin wg = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (wg == null || !wg.isEnabled()) {
            logger.info("[JobMiner] WorldGuard 미설치 — 리전 검사 비활성");
            this.checker = null;
            return;
        }
        WorldGuardChecker c = WorldGuardChecker.tryCreate();
        if (c == null) {
            logger.warning("[JobMiner] WorldGuard 발견했지만 API 로딩 실패 — 리전 검사 비활성");
        } else {
            logger.info("[JobMiner] WorldGuard 연동 OK (v" + wg.getDescription().getVersion() + ")");
        }
        this.checker = c;
    }

    public boolean isAvailable() {
        return checker != null;
    }

    /**
     * 해당 위치가 allowedRegionIds 중 하나에 속하는지 검사.
     * allowedRegionIds가 비어 있으면 항상 true (제한 없음).
     * WorldGuard 미연동 시 항상 true.
     */
    public boolean isInAnyRegion(Location loc, Set<String> allowedRegionIds) {
        if (allowedRegionIds == null || allowedRegionIds.isEmpty()) return true;
        if (checker == null || loc == null) return true;
        try {
            return checker.isInside(loc, allowedRegionIds);
        } catch (Throwable t) {
            logger.warning("[JobMiner] WG 리전 검사 실패: " + t.getMessage());
            return true;
        }
    }
}
