package kr.minefarm.job.jobminer.listener;

import kr.minefarm.job.jobcore.api.JobCoreAPI;
import kr.minefarm.job.jobcore.domain.JobId;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobminer.mining.RegenBlockEntry;
import kr.minefarm.job.jobminer.config.JobMinerConfig;
import kr.minefarm.job.jobminer.integration.WorldGuardBridge;
import kr.minefarm.job.jobminer.mining.RegenBlockRegistry;
import kr.minefarm.job.jobminer.tool.PickaxeValidator;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 리젠 블록 좌표 보호 (일반 유저 파괴·설치 차단).
 * <p>
 * 차단 시 1초 쿨다운으로 메시지를 표시하여 UX를 개선한다.
 */
public final class RegenProtectionListener implements Listener {

    private static final long WARN_COOLDOWN_MS = 1_000L;

    private final RegenBlockRegistry regenBlockRegistry;
    private final JobCoreAPI core;
    private final PickaxeValidator pickaxeValidator;
    private final JobMinerConfig config;
    private final WorldGuardBridge worldGuard;

    /** 메시지 스팸 방지용 마지막 경고 시각 */
    private final Map<UUID, Long> lastWarnAt = new ConcurrentHashMap<>();

    public RegenProtectionListener(
            RegenBlockRegistry regenBlockRegistry,
            JobCoreAPI core,
            PickaxeValidator pickaxeValidator,
            JobMinerConfig config,
            WorldGuardBridge worldGuard
    ) {
        this.regenBlockRegistry = regenBlockRegistry;
        this.core = core;
        this.pickaxeValidator = pickaxeValidator;
        this.config = config;
        this.worldGuard = worldGuard;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!regenBlockRegistry.isRegenBlock(block)) return;
        // ★ 리전 밖이면 광산 시스템 비활성 — 일반 블록처럼 취급 (보호도, 보상도 X)
        if (!worldGuard.isInAnyRegion(block.getLocation(), config.getMineAllowedRegions())) return;

        // ★ 대체 블록(조약돌 등) 상태 = 복구 대기 중 → 누가 캐든 차단 (드롭 중복 방지)
        RegenBlockEntry entry = regenBlockRegistry.getEntry(block);
        if (entry != null && block.getType() != entry.getMaterial()) {
            event.setCancelled(true);
            sendWarnOnce(event.getPlayer(),
                    "§c[광산] §f광물이 복구 중입니다. 잠시 후 다시 시도하세요.");
            return;
        }

        if (canMineRegenBlock(event.getPlayer())) return;

        event.setCancelled(true);
        sendWarnOnce(event.getPlayer(),
                "§c[광산] §f광부 직업과 허용된 곡괭이가 있어야 채굴할 수 있습니다.");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (!regenBlockRegistry.isRegenBlock(block)) return;
        if (!worldGuard.isInAnyRegion(block.getLocation(), config.getMineAllowedRegions())) return;
        event.setCancelled(true);
        sendWarnOnce(event.getPlayer(),
                "§c[광산] §f리젠 블록 위치에는 블록을 설치할 수 없습니다.");
    }

    private boolean canMineRegenBlock(Player player) {
        PlayerJobProfile profile = core.getPlayerProfiles().getCached(player.getUniqueId());
        if (profile == null || profile.getJobId() != JobId.MINER) return false;
        return pickaxeValidator.isValidPickaxe(player);
    }

    private void sendWarnOnce(Player player, String message) {
        long now = System.currentTimeMillis();
        Long last = lastWarnAt.get(player.getUniqueId());
        if (last != null && now - last < WARN_COOLDOWN_MS) return;
        lastWarnAt.put(player.getUniqueId(), now);
        player.sendMessage(message);
    }
}
