package kr.minefarm.job.jobminer.integration;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;

import java.util.Set;

/**
 * WorldGuard API 직접 호출. WorldGuard 미설치 환경에서는 절대 로드되지 않아야 한다.
 * {@link WorldGuardBridge}에서 try/catch로 감싸 호출.
 */
final class WorldGuardChecker {

    private WorldGuardChecker() {}

    static boolean isInside(Location bukkitLoc, Set<String> allowedRegionIds) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(bukkitLoc));
        for (ProtectedRegion region : set) {
            if (allowedRegionIds.contains(region.getId())) return true;
        }
        return false;
    }
}
