package kr.minefarm.job.jobminer.listener;

import kr.minefarm.job.jobcore.api.JobCoreAPI;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobminer.passive.MinerPassiveEffectsService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 광부 패시브가 WorldGuard 리전 안에서만 유지되도록 위치 변경을 감지.
 * <ul>
 *   <li>{@link PlayerChangedWorldEvent}: 월드 이동 시 무조건 재평가</li>
 *   <li>{@link PlayerTeleportEvent}: 텔레포트 시 재평가</li>
 *   <li>{@link PlayerMoveEvent}: 블록 좌표(int)가 변경된 경우에만 재평가 (성능)</li>
 * </ul>
 */
public final class MinerWorldChangeListener implements Listener {

    private final JobCoreAPI core;
    private final MinerPassiveEffectsService passiveEffects;

    /** 마지막으로 검사한 블록 좌표 (성능: 같은 블록 위 이동은 무시) */
    private final Map<UUID, Long> lastCheckedBlockKey = new ConcurrentHashMap<>();

    public MinerWorldChangeListener(JobCoreAPI core, MinerPassiveEffectsService passiveEffects) {
        this.core = core;
        this.passiveEffects = passiveEffects;
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        reEval(event.getPlayer());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        // 이동 후 위치 기준 재평가는 다음 tick에 (텔레포트 완료 후)
        reEval(event.getPlayer());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) return;
        long blockKey = packBlockKey(to.getBlockX(), to.getBlockY(), to.getBlockZ());
        Long prev = lastCheckedBlockKey.get(event.getPlayer().getUniqueId());
        if (prev != null && prev == blockKey) return; // 같은 블록 위 → skip
        lastCheckedBlockKey.put(event.getPlayer().getUniqueId(), blockKey);
        reEval(event.getPlayer());
    }

    private void reEval(Player player) {
        PlayerJobProfile profile = core.getPlayerProfiles().getCached(player.getUniqueId());
        passiveEffects.apply(player, profile);
    }

    private static long packBlockKey(int x, int y, int z) {
        // 충분히 큰 좌표를 압축 — 충돌 가능성 무시할 수준
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
