package kr.minefarm.job.jobminer.listener;

import kr.minefarm.job.jobcore.api.JobCoreAPI;
import kr.minefarm.job.jobcore.domain.JobId;
import kr.minefarm.job.jobcore.domain.PlayerJobProfile;
import kr.minefarm.job.jobminer.mining.RegenBlockEntry;
import kr.minefarm.job.jobminer.mining.RegenBlockRegistry;
import kr.minefarm.job.jobminer.mining.RegenMineRewardService;
import kr.minefarm.job.jobminer.config.JobMinerConfig;
import kr.minefarm.job.jobminer.integration.WorldGuardBridge;
import kr.minefarm.job.jobminer.tool.PickaxeValidator;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * 리젠 광산 채굴: 자동판매·커스텀 드롭·리젠 복구.
 * <p>
 * {@link RegenProtectionListener}(LOWEST)에서 직업·곡괭이 검증 후 취소된 이벤트는
 * ignoreCancelled=true 이므로 여기까지 오지 않는다.
 * 방어적 profile null·jobId 체크는 유지하되, 중복 곡괭이 검사는 제거한다.
 */
public final class MiningListener implements Listener {

    private final JobCoreAPI core;
    private final RegenBlockRegistry regenBlockRegistry;
    private final PickaxeValidator pickaxeValidator;
    private final RegenMineRewardService rewardService;
    private final JobMinerConfig config;
    private final WorldGuardBridge worldGuard;

    public MiningListener(
            JobCoreAPI core,
            RegenBlockRegistry regenBlockRegistry,
            PickaxeValidator pickaxeValidator,
            RegenMineRewardService rewardService,
            JobMinerConfig config,
            WorldGuardBridge worldGuard
    ) {
        this.core = core;
        this.regenBlockRegistry = regenBlockRegistry;
        this.pickaxeValidator = pickaxeValidator;
        this.rewardService = rewardService;
        this.config = config;
        this.worldGuard = worldGuard;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!regenBlockRegistry.isRegenBlock(block)) return;
        // ★ 리전 밖이면 보상 지급하지 않음 (등록은 되어 있더라도)
        if (!worldGuard.isInAnyRegion(block.getLocation(), config.getMineAllowedRegions())) return;

        Player player = event.getPlayer();
        // 방어적 체크: 캐시 미스(예: 초기화 경쟁) 또는 비광부 처리
        PlayerJobProfile profile = core.getPlayerProfiles().getCached(player.getUniqueId());
        if (profile == null || profile.getJobId() != JobId.MINER) return;
        // 곡괭이 검증은 RegenProtectionListener(LOWEST)에서 이미 완료됨 — 중복 제거

        RegenBlockEntry entry = regenBlockRegistry.getEntry(block);
        if (entry == null) return;

        event.setDropItems(false);
        rewardService.deliverRewards(player, profile, block, entry);
    }
}
